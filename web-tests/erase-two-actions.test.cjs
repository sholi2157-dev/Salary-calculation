// M-13 (2.3.0): "delete from this device" and "delete from the account and every device", checked across devices
// with the same Firestore stand-in as account-sync.test.cjs. Synthetic data only.
const { test } = require('node:test'), assert = require('node:assert/strict');
globalThis.WorkRuntime = require('../web/runtime.js'); globalThis.WorkCategories = require('../web/categories.js'); globalThis.WorkTransfer = require('../web/transfer.js');
const { createStore } = require('../web/kis/store.js'), { createSync } = require('../web/sync/engine.js'), E = require('../web/sync/erase.js');
function storage() { const m = new Map(); return { getItem: k => m.has(k) ? m.get(k) : null, setItem: (k, v) => m.set(k, String(v)), removeItem: k => m.delete(k),
  get length() { return m.size; }, key: i => [...m.keys()][i] ?? null, map: m }; }
function server() {
  const docs = new Map(); let seq = 0;
  return { docs, online: true,
    async read(uid, cursor) { if (!this.online) throw Error('offline'); return { records: [...docs.values()].filter(d => d.uid === uid && (cursor == null || d.seq >= cursor)).map(d => d.r), cursor: seq }; },
    async exchange(uid, base, r) { if (!this.online) throw Error('offline'); const id = uid + '/' + r.syncId, old = docs.get(id);
      if (old?.r.operation === r.operation) return old.r; if ((old?.r.version || 0) !== base) return old.r; docs.set(id, { uid, r, seq: ++seq }); return r; },
    live(uid, type) { return [...docs.values()].filter(d => d.uid === uid && (!type || d.r.type === type) && !d.r.deleted).length; } };
}
function device(s, uid = 'owner', local = storage()) {
  const store = createStore(local); store.load();
  return { store, local, sync: createSync({ store, storage: local, uid, deviceId: 'd' + Math.random(), transport: s, currentUid: () => uid }) };
}
const shift = (o = {}) => ({ category: 'מסעדה', date: 1760000000000, createdAt: 1760000000500, isTimeRange: true, startTime: '09:00', endTime: '17:00', hours: 8,
  hourlyRate: 40.25, totalEarnings: 322, isPaid: false, notes: '', currency: '₪', isGroupShift: false, employerRate: null, workerRate: null, groupWorkersJson: '', ...o });
async function rich(d) {
  d.store.update(x => { x.categories.push({ name: 'מסעדה', defaultRate: 40.25 }); x.workers.push({ name: 'יוסי' }); });
  d.store.addEntries([shift(), shift({ date: 1760086400000, createdAt: 1760086400500, totalEarnings: 300.5 })]);
  d.store.updatePlans(p => { p.series.push({ id: 's1', category: 'מסעדה', daysMask: 2, startTime: '09:00', endTime: '17:00', hourlyRate: 40, currency: '₪', fromDate: '2025-06-01', untilDate: '2025-07-01', createdAt: 1 }); });
}

test('account: records another device added that this one never saw are deleted too; the other device empties', async () => {
  const s = server(), a = device(s), b = device(s);
  await rich(a); await a.sync.sync(); await b.sync.sync();
  b.store.addEntries([shift({ notes: 'רק בטלפון', createdAt: 9 })]); await b.sync.sync(); // A has not seen it
  const r = await E.eraseAccount({ store: a.store, sync: a.sync, transport: s, uid: 'owner' });
  assert.deepEqual(r, { ok: true, left: 0 });
  assert.equal(await E.remoteLeft(s, 'owner'), 0);
  for (const t of ['entry', 'worker', 'series']) assert.equal(s.live('owner', t), 0, t);
  await b.sync.sync();
  assert.equal(b.store.data.entries.length, 0); assert.equal(b.store.data.workers.length, 0); assert.equal(b.store.plans.series.length, 0);
  assert.deepEqual(b.store.data.categories.map(c => c.name), ['עצמאי']);
});

test('account: another account on the same server is never touched', async () => {
  const s = server(), a = device(s, 'owner'), other = device(s, 'someone-else');
  await rich(a); await rich(other); await a.sync.sync(); await other.sync.sync();
  const before = s.live('someone-else');
  await E.eraseAccount({ store: a.store, sync: a.sync, transport: s, uid: 'owner' });
  assert.equal(s.live('someone-else'), before); assert.ok(before > 0);
});

test('account: offline nothing is deleted anywhere, and the error reaches the caller', async () => {
  const s = server(), a = device(s); await rich(a); await a.sync.sync();
  const live = s.live('owner'); s.online = false;
  await assert.rejects(E.eraseAccount({ store: a.store, sync: a.sync, transport: s, uid: 'owner' }));
  assert.equal(s.live('owner'), live); assert.equal(a.store.data.entries.length, 2, 'this device keeps its data too');
});

test('account: a device that edited a shift offline gets a choice instead of losing or silently restoring it', async () => {
  const s = server(), a = device(s), b = device(s); await rich(a); await a.sync.sync(); await b.sync.sync();
  b.store.update(d => { d.entries[0].notes = 'שינוי בלי חיבור'; });
  await E.eraseAccount({ store: a.store, sync: a.sync, transport: s, uid: 'owner' });
  await b.sync.sync();
  assert.equal(b.sync.conflicts().length, 1, 'the edited shift waits for a choice on that device');
  assert.equal(s.live('owner', 'entry'), 0, 'and is not back in the account by itself');
});

test('device: the account is untouched, signing in again brings everything back, and no deletion is ever sent', async () => {
  const s = server(), local = storage(), a = device(s, 'owner', local); await rich(a); await a.sync.sync();
  const live = s.live('owner'), docs = JSON.stringify([...s.docs.values()]);
  assert.deepEqual(await E.deviceReady(a.sync), { ok: true, unsent: 0, conflicts: 0 });
  // (the app signs out here)
  E.wipeDevice(local, '', 'owner');
  assert.equal(local.getItem('work_complete_backup'), null); assert.equal(local.getItem('work_sync_v1:owner'), null);
  assert.equal(local.getItem('work_pre_v23_snapshot_v1'), null, 'no hidden copy stays behind');
  const fresh = device(s, 'owner', local);
  assert.equal(fresh.store.data.entries.length, 0);
  assert.equal(JSON.stringify([...s.docs.values()]), docs, 'nothing written to the account');
  await fresh.sync.sync(); // the next sign-in
  assert.equal(s.live('owner'), live, 'the account still holds everything');
  assert.equal(fresh.store.data.entries.length, 2); assert.equal(fresh.store.plans.series.length, 1);
  assert.deepEqual(fresh.store.data.entries.map(e => e.totalEarnings).sort(), [300.5, 322], 'amounts exactly as saved');
});

test('device: why the sync memory is forgotten — keeping it would read the emptied device as deletions', async () => {
  const s = server(), local = storage(), a = device(s, 'owner', local); await rich(a); await a.sync.sync();
  E.emptyStore(a.store); await a.sync.sync();
  assert.equal(s.live('owner', 'entry'), 0, 'control: this is exactly what eraseDevice must never do');
});

test('device: refused while a change has not reached the account (offline), so nothing that exists only here is lost', async () => {
  const s = server(), a = device(s); await rich(a); await a.sync.sync();
  a.store.addEntries([shift({ notes: 'חדש', createdAt: 77 })]); s.online = false;
  const r = await E.deviceReady(a.sync);
  assert.equal(r.ok, false); assert.equal(r.unsent, 1);
});

test('device without an account: only this storage is emptied; device choices such as the theme stay', () => {
  const local = storage(), store = createStore(local); store.load(); store.addEntries([shift()]); store.setLocal('theme', 'dark'); store.setLocal('name', 'בודק');
  E.wipeDevice(local, '', null);
  const again = createStore(local); again.load();
  assert.equal(again.data.entries.length, 0); assert.equal(again.local.theme, 'dark'); assert.equal(again.local.name, undefined);
});
