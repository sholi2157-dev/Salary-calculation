// 2.3.0 (audit CW-08): Home's "waiting for a rate" opens History with every such shift, whatever filter or search was left on.
const { test } = require('node:test'), assert = require('node:assert/strict');
const elStub = () => ({ innerHTML: '', querySelector: () => null, querySelectorAll: () => [], addEventListener() {}, dataset: {} });
test('selectPending clears a stale filter and search', () => {
  globalThis.KisUi = { esc: s => String(s), moneyHtml: () => '', shiftCard: () => '', ICON: {}, upcoming: () => false, money: () => '', shortDate: () => '', hours: () => '', monthKey: () => '2026-03', startOfDay: x => x };
  globalThis.KisHome = { weekStart: () => 0, sortedTotals: () => [] };
  globalThis.KisPlans = { occurrences: () => [], card: () => '' };
  require('../web/kis/history.js');
  const H = globalThis.KisHistory;
  H.state.filter = { ...H.defaults(), category: 'אחר', paid: 'PAID' }; H.state.query = 'zzz'; H.state.searching = true;
  const store = { data: { entries: [{ id: 'a', payKnown: false, kind: 'shift', date: 1, category: 'מסעדה' }], categories: [], webPreferences: {} }, plans: { series: [], exceptions: [] } };
  try { H.render(elStub(), { store }, { selectPending: true }); } catch { /* drawing needs a DOM; the state is set first */ }
  const strip = f => Object.fromEntries(Object.entries(f).filter(([, v]) => typeof v !== 'number')); // dates come from the clock
  assert.deepEqual(strip(H.state.filter), strip(H.defaults())); assert.equal(H.state.query, ''); assert.equal(H.state.searching, false);
  assert.deepEqual([...H.state.selection], ['a']);
});
