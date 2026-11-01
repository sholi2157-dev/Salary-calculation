/*
 * 2.3.0 Milestone 1: atomic multi-key saves with injected failures (audit D01–D04, CW-09), recovery of an interrupted
 * save, and compatibility with the previous web store (fixtures/compat/store-69e430e.js, the code before 2.3), which must
 * still read everything 2.3 writes. Synthetic data only.
 */
const { test } = require('node:test'), assert = require('node:assert/strict'), path = require('node:path'), fs = require('node:fs');
globalThis.WorkRuntime = require('../web/runtime.js');
globalThis.WorkCategories = require('../web/categories.js');
const S = require('../web/kis/store.js');
const OldStore = require('./fixtures/compat/store-69e430e.js');
const rich = JSON.parse(fs.readFileSync(path.join(__dirname, 'fixtures', 'synthetic', 'rich-v2.json'), 'utf8'));
const TIMER = WorkRuntime.timerKey(null);

/** A localStorage whose setItem/removeItem can be told to throw like a full quota on the n-th write of a key. */
function memory(init = {}) {
  const m = new Map(Object.entries(init).map(([k, v]) => [k, typeof v === 'string' ? v : JSON.stringify(v)]));
  const fail = new Map();
  return { m, failOn(key, nth = 1) { fail.set(key, nth); },
    getItem: k => (m.has(k) ? m.get(k) : null),
    setItem(k, v) {
      if (fail.has(k)) { const left = fail.get(k) - 1; if (left === 0) { fail.delete(k); const e = Error('The quota has been exceeded.'); e.name = 'QuotaExceededError'; throw e; } fail.set(k, left); }
      m.set(k, String(v)); },
    removeItem(k) { m.delete(k); }, key: i => [...m.keys()][i] ?? null, get length() { return m.size; } };
}
const clone = st => new Map(st.m);
const withSeries = () => { const d = JSON.parse(JSON.stringify(rich)); d.work_complete_backup.entries.find(e => e.id === 'r01').seriesKey = 'series-rich@2026-03-01'; return d; };

test('delete of a plan day: a failure writing the plans leaves both keys exactly as they were (D03)', () => {
  const st = memory(withSeries()), store = S.createStore(st); store.load();
  store.update(d => { d.entries[1].notes = 'warm-up write'; });          // snapshots taken, so only the commit writes next
  const before = clone(st);
  st.failOn('work_series_v1');
  assert.throws(() => store.deleteEntries(['r01']), /השמירה נכשלה ולא בוצע שום שינוי/);
  assert.deepEqual(clone(st), before, 'storage unchanged');
  assert.equal(st.getItem(S.JOURNAL), null, 'no journal left behind');
  assert.ok(store.data.entries.some(e => e.id === 'r01'), 'memory unchanged');
  const gone = store.deleteEntries(['r01']);                              // and it works once storage allows it
  assert.equal(gone.length, 1);
  assert.equal(JSON.parse(st.getItem('work_series_v1')).exceptions.find(x => x.date === '2026-03-01').cancelled, true);
});

test('a failure writing the journal itself changes nothing', () => {
  const st = memory(rich), store = S.createStore(st); store.load(); store.update(d => { d.entries[0].notes = 'x'; });
  const before = clone(st); st.failOn(S.JOURNAL);
  assert.throws(() => store.setPaid(['r02'], true), /אין מספיק מקום/);
  assert.deepEqual(clone(st), before);
});

test('an interrupted save is undone on the next load when not every key was written', () => {
  const st = memory(rich);
  S.createStore(st).load();
  const prevBackup = st.getItem('work_complete_backup'), prevPlans = st.getItem('work_series_v1');
  // Simulate a tab that wrote the journal and the first key, then closed.
  st.setItem(S.JOURNAL, JSON.stringify({ prev: { work_complete_backup: prevBackup, work_series_v1: prevPlans }, next: { work_complete_backup: 'x', work_series_v1: 'y' } }));
  st.setItem('work_complete_backup', JSON.stringify({ ...rich.work_complete_backup, entries: [] }));
  const store = S.createStore(st); store.load();
  assert.equal(store.recovered, 'undone');
  assert.equal(st.getItem('work_complete_backup'), prevBackup);
  assert.equal(store.data.entries.length, rich.work_complete_backup.entries.length);
  assert.equal(st.getItem(S.JOURNAL), null);
});

test('an interrupted save whose keys were all written is kept (finished, not undone)', () => {
  const st = memory(rich); const a = S.createStore(st); a.load(); a.update(d => { d.entries[0].notes = 'before'; });
  const prev = st.getItem('work_complete_backup');
  const next = JSON.stringify({ ...JSON.parse(prev), entries: JSON.parse(prev).entries.slice(1) });
  const h = t => { let x = 0x811c9dc5; for (let i = 0; i < t.length; i++) { x ^= t.charCodeAt(i); x = Math.imul(x, 0x01000193); } return (x >>> 0).toString(36) + ':' + t.length; };
  st.setItem(S.JOURNAL, JSON.stringify({ prev: { work_complete_backup: prev }, next: { work_complete_backup: h(next) } }));
  st.setItem('work_complete_backup', next);
  const b = S.createStore(st); b.load();
  assert.equal(b.recovered, 'finished'); assert.equal(st.getItem('work_complete_backup'), next);
});

test('renaming a place from a stale tab never drops a shift another tab just added (D02)', () => {
  const st = memory(rich), A = S.createStore(st), B = S.createStore(st); A.load(); B.load();
  B.addEntries([{ category: 'מסעדה', date: 1774000000000, createdAt: 1774000000000, isTimeRange: false, startTime: null, endTime: null, hours: 2, hourlyRate: 50, totalEarnings: 100, isPaid: false, notes: 'from B', currency: '₪', isGroupShift: false, employerRate: null, workerRate: null, groupWorkersJson: '' }]);
  // A never received the storage event; its rename is computed inside the commit on what is stored now.
  A.transact(n => Object.assign(n.data, WorkCategories.rename(n.data, 'מסעדה', 'מסעדה חדשה', 50, '₪')));
  const saved = JSON.parse(st.getItem('work_complete_backup')).entries;
  assert.equal(saved.length, rich.work_complete_backup.entries.length + 1);
  assert.ok(saved.some(e => e.notes === 'from B' && e.category === 'מסעדה חדשה'));
});

test('deleting a place moves a running timer with the shifts, keeping its rate and currency (CW-09)', () => {
  const st = memory(rich), store = S.createStore(st); store.load();
  store.startTimer('Tech Support', 30, '$');
  store.transact(n => { Object.assign(n.data, WorkCategories.remove(n.data, 'Tech Support'));
    const target = WorkCategories.defaultName(n.data.categories, n.data.webPreferences);
    if (n.timer?.category === 'Tech Support') n.timer = { ...n.timer, category: target }; });
  const t = JSON.parse(st.getItem(TIMER));
  assert.equal(t.category, 'מסעדה'); assert.equal(t.rate, 30); assert.equal(t.currency, '$');
});

test('two identical old rows without id stay two rows; deleting one removes exactly one (D01)', () => {
  const legacy = JSON.parse(fs.readFileSync(path.join(__dirname, 'fixtures', 'synthetic', 'legacy-noid.json'), 'utf8'));
  const st = memory(legacy), store = S.createStore(st); store.load();
  const ids = store.data.entries.map(e => e.id);
  assert.equal(new Set(ids).size, 3);
  store.deleteEntries([ids[1]]);
  assert.equal(store.data.entries.length, 2);
  const again = S.createStore(st); again.load();
  assert.deepEqual(again.data.entries.map(e => e.id), [ids[0], ids[2]], 'ids are stable across reloads');
  // Stored ids that already repeat (written by an older version) are told apart the same way.
  assert.deepEqual(S.uniqueIds([{ id: 'a' }, { id: 'a' }, { id: 'b' }]).map(e => e.id), ['a', 'a~2', 'b']);
});

test('a damaged plans file is never overwritten; shifts still save (D04)', () => {
  for (const bad of ['{"series":[', 'null', '{}', '[]', '{"series":{},"exceptions":[]}']) {
    const st = memory({ ...rich, work_series_v1: bad }), store = S.createStore(st); store.load();
    assert.ok(store.plansBroken, bad); assert.equal(store.rawPlans(), bad);
    assert.throws(() => store.updatePlans(p => p.series.push({ id: 'n' })), /פגום/);
    store.setPaid(['r02'], true);
    assert.equal(st.getItem('work_series_v1'), bad, 'raw plans kept: ' + bad);
    assert.equal(JSON.parse(st.getItem('work_complete_backup')).entries.find(e => e.id === 'r02').isPaid, true);
  }
});

test('the first 2.3 write keeps a one-time copy of everything, plans included', () => {
  const st = memory(rich), store = S.createStore(st); store.load();
  store.setPaid(['r02'], true); store.setPaid(['r03'], true);
  const snap = JSON.parse(st.getItem('work_pre_v23_snapshot_v1'));
  assert.equal(snap.values.work_complete_backup, JSON.stringify(rich.work_complete_backup));
  assert.equal(snap.values.work_series_v1, JSON.stringify(rich.work_series_v1));
});

test('the previous web store reads everything 2.3 writes, amounts unchanged (rollback compatibility)', () => {
  const legacy = JSON.parse(fs.readFileSync(path.join(__dirname, 'fixtures', 'synthetic', 'legacy-noid.json'), 'utf8'));
  for (const init of [rich, legacy]) {
    const st = memory(init), store = S.createStore(st); store.load();
    store.setPaid([store.data.entries[0].id], true);
    store.deleteEntries([store.data.entries[store.data.entries.length - 1].id]);
    const old = OldStore.createStore(st); old.load();
    assert.equal(old.broken, null);
    assert.deepEqual(old.data.entries.map(e => [e.totalEarnings, e.hours, e.isPaid]), store.data.entries.map(e => [e.totalEarnings, e.hours, e.isPaid]));
    assert.deepEqual(old.plans, store.plans);
    assert.equal(st.getItem(S.JOURNAL), null);
  }
});
