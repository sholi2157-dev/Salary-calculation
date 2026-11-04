// FB-08: editing an existing workplace's rate with "שינוי רטרואקטיבי" on updates the stored amounts of its unpaid shifts
// at the previous rate, with the same money function as every new amount; off (the default) changes nothing stored.
// Paid shifts are never touched. Synthetic data only.
const { test } = require('node:test'), assert = require('node:assert/strict'), path = require('node:path'), fs = require('node:fs');
globalThis.WorkRuntime = require('../web/runtime.js');
globalThis.WorkCategories = require('../web/categories.js');
const R = require('../web/v2/rules.js'), P = require('../web/v2/pay.js'), S = require('../web/kis/store.js');
const rich = JSON.parse(fs.readFileSync(path.join(__dirname, 'fixtures', 'synthetic', 'rich-v2.json'), 'utf8'));
function memory(init = {}) {
  const m = new Map(Object.entries(init).map(([k, v]) => [k, typeof v === 'string' ? v : JSON.stringify(v)]));
  return { m, getItem: k => (m.has(k) ? m.get(k) : null), setItem(k, v) { m.set(k, String(v)); }, removeItem(k) { m.delete(k); },
    key: i => [...m.keys()][i] ?? null, get length() { return m.size; } };
}
const shift = (id, x = {}) => ({ id, category: 'מסעדה', hours: 2, hourlyRate: 50, totalEarnings: 100, isPaid: false, currency: '₪', isGroupShift: false, ...x });

test('only unpaid plain shifts of the place at its previous rate and currency change', () => {
  const rows = [shift('a'), shift('paid', { isPaid: true }), shift('other', { category: 'עצמאי' }), shift('own', { hourlyRate: 60, totalEarnings: 120 }),
    shift('pending', { payKnown: false, hourlyRate: 0, totalEarnings: 0 }), shift('job', { kind: 'job', hourlyRate: 0, totalEarnings: 300 }),
    shift('team', { isGroupShift: true }), shift('usd', { currency: '$' }), shift('bonus', { bonus: 15, totalEarnings: 115 })];
  assert.deepEqual(R.retroRate(rows, 'מסעדה', 50, 55, '₪'), [{ id: 'a', total: 110 }, { id: 'bonus', total: 125 }]);
});

test('no change when the rate is the same or invalid', () => {
  const rows = [shift('a')];
  assert.deepEqual(R.retroRate(rows, 'מסעדה', 50, 50, '₪'), []);
  for (const bad of [NaN, -1, Infinity]) assert.deepEqual(R.retroRate(rows, 'מסעדה', 50, bad, '₪'), []);
});

test('amounts use the one money function (M-08: exact, half up once, whole agorot)', () => {
  // 50 minutes × 33.33 = 27.775 → 27.78; a 1.5 h × 99.99 = 149.985 → 149.99; typed decimal hours.
  const rows = [shift('m', { hours: 50 / 60, hourlyRate: 30, totalEarnings: 25 }), shift('h', { hours: 1.5, hourlyRate: 30, totalEarnings: 45 }),
    shift('b', { hours: 1.234567, hourlyRate: 30, bonus: 0.005, totalEarnings: 37.04 })];
  const out = R.retroRate(rows, 'מסעדה', 30, 33.33, '₪');
  assert.deepEqual(out.map(x => x.total), [27.78, 50, 41.15]);
  const out2 = R.retroRate(rows, 'מסעדה', 30, 99.99, '₪');
  assert.equal(out2[1].total, 149.99);
  rows.forEach((e, i) => assert.equal(out[i].total, P.money(e.hours, 33.33, e.bonus || 0)));
});

test('on the realistic synthetic data: מסעדה 50 → 55 changes r01, r02, r15 only; paid, other places and own rates stay', () => {
  const d = rich.work_complete_backup;
  const out = R.retroRate(d.entries.map(e => ({ payKnown: true, ...e })), 'מסעדה', 50, 55, '₪');
  assert.deepEqual(out, [{ id: 'r01', total: 330 }, { id: 'r02', total: 192.5 }, { id: 'r15', total: 275 }]);
  assert.ok(!out.some(x => d.entries.find(e => e.id === x.id).isPaid));
});

test('store: restoreRates undoes only shifts that still hold what was written and are unpaid, in one commit', () => {
  const st = memory(rich), store = S.createStore(st); store.load();
  const changed = [];
  store.update(d => { for (const x of R.retroRate(d.entries, 'מסעדה', 50, 55, '₪')) { const e = d.entries.find(r => r.id === x.id);
    changed.push({ id: e.id, hourlyRate: e.hourlyRate, totalEarnings: e.totalEarnings, wrote: { hourlyRate: 55, totalEarnings: x.total } }); e.hourlyRate = 55; e.totalEarnings = x.total; } });
  assert.equal(changed.length, 3);
  const reloaded = S.createStore(st); reloaded.load();
  assert.equal(reloaded.data.entries.find(e => e.id === 'r01').totalEarnings, 330, 'saved');
  // Meanwhile r02 got paid and r15 was edited: undo leaves both alone.
  store.update(d => { d.entries.find(e => e.id === 'r02').isPaid = true; d.entries.find(e => e.id === 'r15').totalEarnings = 280; });
  assert.equal(store.restoreRates(changed), 1);
  const e = id => store.data.entries.find(x => x.id === id);
  assert.deepEqual([e('r01').hourlyRate, e('r01').totalEarnings], [50, 300]);
  assert.deepEqual([e('r02').hourlyRate, e('r02').totalEarnings, e('r02').isPaid], [55, 192.5, true]);
  assert.deepEqual([e('r15').hourlyRate, e('r15').totalEarnings], [55, 280]);
  // A full undo with nothing changed in between gives back exactly the stored data.
  const st2 = memory(rich), s2 = S.createStore(st2); s2.load(); const orig = JSON.stringify(s2.data.entries), ch = [];
  s2.update(d => { for (const x of R.retroRate(d.entries, 'מסעדה', 50, 55, '₪')) { const r = d.entries.find(q => q.id === x.id);
    ch.push({ id: r.id, hourlyRate: r.hourlyRate, totalEarnings: r.totalEarnings, wrote: { hourlyRate: 55, totalEarnings: x.total } }); r.hourlyRate = 55; r.totalEarnings = x.total; } });
  s2.restoreRates(ch);
  assert.equal(JSON.stringify(s2.data.entries), orig);
});
