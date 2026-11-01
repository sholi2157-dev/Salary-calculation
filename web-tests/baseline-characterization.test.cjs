/*
 * Stage 0 of the 2.3.0 remediation: records how the CURRENT code treats the synthetic datasets, so any later change in
 * stored data, money or totals is visible and intentional. Known audit bugs are recorded as they are today (marked
 * "knownBug") and are expected to change only in the commit that fixes them, with this golden file updated in that commit.
 * Regenerate deliberately: UPDATE_GOLDEN=1 node --test web-tests/baseline-characterization.test.cjs
 */
const { test } = require('node:test'), assert = require('node:assert/strict'), fs = require('node:fs'), path = require('node:path');
globalThis.WorkRuntime = require('../web/runtime.js');
globalThis.WorkCategories = require('../web/categories.js');
globalThis.KisPay = require('../web/v2/pay.js');
const { createStore } = require('../web/kis/store.js');
const DIR = path.join(__dirname, 'fixtures', 'synthetic');
const GOLDEN = path.join(DIR, 'baseline-golden.json');
const rich = JSON.parse(fs.readFileSync(path.join(DIR, 'rich-v2.json'), 'utf8'));
const legacy = JSON.parse(fs.readFileSync(path.join(DIR, 'legacy-noid.json'), 'utf8'));
const memory = init => { const m = new Map(Object.entries(init).map(([k, v]) => [k, JSON.stringify(v)])); return { m,
  getItem: k => (m.has(k) ? m.get(k) : null), setItem: (k, v) => m.set(k, String(v)), removeItem: k => m.delete(k), key: i => [...m.keys()][i] ?? null, get length() { return m.size; } }; };

function observe() {
  const out = {};
  const s1 = memory(rich), store = createStore(s1); store.load();
  out.richLoadWritesNothing = s1.getItem('work_complete_backup') === JSON.stringify(rich.work_complete_backup);
  out.richEntries = store.data.entries.map(e => ({ id: e.id, pay: KisPay.pay(e), known: KisPay.payKnown(e), stored: e.totalEarnings, hours: e.hours,
    ...(e.isGroupShift ? { group: KisPay.group(e) } : {}) }));
  out.richTotals = KisPay.totals(store.data.entries);
  out.plans = store.plans;
  const s2 = memory(legacy), ls = createStore(s2); ls.load();
  const legacyIds = ls.data.entries.map(e => e.id);
  // The ids themselves are content hashes that depend on the runtime in use; what matters is how many are distinct.
  out.legacyRows = legacyIds.length; out.legacyDistinctIds = new Set(legacyIds).size;
  out.legacyDeleteOneRemoves = (() => { const before = ls.data.entries.length; ls.deleteEntries([ls.data.entries[0].id]); return before - ls.data.entries.length; })();
  out.knownBug = { D01_identicalLegacyRowsShareId: legacyIds[0] === legacyIds[1], CAL02_r11_storedNotHalfUp: out.richEntries.find(e => e.id === 'r11').stored };
  return JSON.parse(JSON.stringify(out));
}

test('current behaviour on the synthetic datasets matches the recorded baseline', () => {
  const now = observe();
  if (process.env.UPDATE_GOLDEN === '1' || !fs.existsSync(GOLDEN)) fs.writeFileSync(GOLDEN, JSON.stringify(now, null, 1) + '\n');
  assert.deepEqual(now, JSON.parse(fs.readFileSync(GOLDEN, 'utf8')));
});

test('loading never rewrites stored data and stored amounts are never recalculated', () => {
  const now = observe();
  assert.equal(now.richLoadWritesNothing, true);
  for (const e of now.richEntries) { const src = rich.work_complete_backup.entries.find(x => x.id === e.id); assert.equal(e.stored, src.totalEarnings, e.id); assert.equal(e.hours, src.hours, e.id); }
});

test('1,000 synthetic shifts load and total without error', () => {
  const big = require('./fixtures/synthetic/make.cjs').big(1000);
  const st = createStore(memory({ work_complete_backup: big })); st.load();
  assert.equal(st.data.entries.length, 1000);
  const t = KisPay.totals(st.data.entries); assert.ok(t);
});
