// FB-09: "how much did one place (e.g. a convention) earn across October and November". History answers it with
// "כל התאריכים" + a place filter; its total card is KisHome.sortedTotals(rows) over KisHistory.apply. This pins that
// the per-place total over all months is exactly the sum of the amounts already saved on each shift: never recomputed
// from hours × rate, other places and missing amounts left out, ₪ and $ never added together.
// Same synthetic rows and numbers as Android WorkFeedbackCategoryTotalTest.
const { test } = require('node:test'), assert = require('node:assert/strict');
const startOfDay = ms => { const d = new Date(ms); d.setHours(0, 0, 0, 0); return d.getTime(); };
const monthKey = ms => { const d = new Date(ms); return d.getFullYear() * 12 + d.getMonth(); };
globalThis.KisUi = { startOfDay, monthKey, esc: s => String(s), moneyHtml: () => '', money: () => '', shortDate: () => '', monthName: () => '' };
globalThis.KisPay = require('../web/v2/pay.js');
globalThis.KisPlans = { occurrences: () => [], card: () => '' };
require('../web/kis/home.js');
require('../web/kis/history.js');
const H = globalThis.KisHistory, totalsOf = rows => Object.fromEntries(globalThis.KisHome.sortedTotals(rows));

const place = 'כינוס';
const at = day => { const [y, m, d] = day.split('-').map(Number); return new Date(y, m - 1, d, 12).getTime(); };
const shift = (id, day, hours, rate, saved, o = {}) => ({ id, category: place, date: at(day), isTimeRange: false, hours, hourlyRate: rate,
  totalEarnings: saved, isPaid: false, currency: '₪', payKnown: true, kind: 'shift', createdAt: id, notes: '', groupWorkersJson: '', ...o });
// Saved amounts are taken as they are; #3 and #5 deliberately differ from hours × rate (older-version rounding).
const entries = [
  shift(1, '2026-10-12', 8, 40, 320.00),
  shift(2, '2026-10-20', 6.5, 40, 260.00, { isPaid: true }),
  shift(3, '2026-10-31', 3.33, 45, 149.99),
  shift(4, '2026-11-01', 7, 45, 315.00),
  shift(5, '2026-11-02', 2, 33.33, 66.67),
  shift(6, '2026-11-03', 4, 0, 0, { payKnown: false }),          // rate still missing
  shift(7, '2026-11-02', 5, 20, 100.00, { currency: '$' }),     // another currency, same place
  shift(8, '2026-10-15', 9, 50, 450.00, { category: 'מסעדה' }), // another place
  shift(9, '2026-11-02', 1, 40, 40.00, { category: 'כינוס ב' }), // similar name, another place
];
const shown = f => H.apply(entries.map(e => ({ ...e })), { ...H.defaults(), ...f }, '');
const cents = v => Math.round(v * 100); // whole cents, for comparing sums of saved two-decimal amounts

test('all dates, one place: the total is the sum of the saved amounts, per currency', () => {
  const rows = shown({ period: 'ALL', category: place });
  assert.deepEqual(rows.map(e => e.id).sort((a, b) => a - b), [1, 2, 3, 4, 5, 6, 7]);
  const t = totalsOf(rows);
  assert.deepEqual(Object.keys(t).sort(), ['$', '₪']);
  const saved = entries.filter(e => e.category === place && e.currency === '₪' && e.payKnown).reduce((s, e) => s + e.totalEarnings, 0);
  assert.equal(cents(t['₪']), cents(saved));
  assert.equal(cents(t['₪']), 111166); // 1111.66, not hours × rate
  assert.equal(cents(t['$']), 10000);
  assert.equal(globalThis.KisPay.pending(rows).length, 1);
  assert.equal(Math.round(rows.reduce((s, e) => s + e.hours, 0) * 100), 3583);
});

test('owed for the place is the sum of its unpaid saved amounts', () => {
  const t = totalsOf(shown({ period: 'ALL', category: place, pay: 'UNPAID' }));
  assert.equal(cents(t['₪']), 85166);
  assert.equal(cents(t['$']), 10000);
});

test('the months add up to the all-dates total', () => {
  const oct = shown({ period: 'MONTH', month: monthKey(at('2026-10-12')), category: place });
  const nov = shown({ period: 'MONTH', month: monthKey(at('2026-11-01')), category: place });
  assert.deepEqual(oct.map(e => e.id).sort(), [1, 2, 3]);
  assert.deepEqual(nov.map(e => e.id).sort(), [4, 5, 6, 7]);
  const all = totalsOf(shown({ period: 'ALL', category: place }))['₪'];
  assert.equal(cents(totalsOf(oct)['₪']) + cents(totalsOf(nov)['₪']), cents(all));
});

test('stored rows are not changed by filtering or totals', () => {
  const copy = JSON.stringify(entries);
  H.apply(entries, { ...H.defaults(), period: 'ALL', category: place }, ''); totalsOf(entries);
  assert.equal(JSON.stringify(entries), copy);
});
