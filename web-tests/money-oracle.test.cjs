// 2.3.0 money oracle (audit M-08, decision D-5): a shift of whole minutes (or timer seconds) pays exactly
// minutes × rate / 60 + bonus, rounded once, half up, to whole agorot. The oracle uses integer arithmetic on the
// typed decimal rate, independent of KisRules.money. Android runs the same grid in WorkMoneyOracleTest.kt.
const { test } = require('node:test'), assert = require('node:assert/strict');
const R = require('../web/v2/rules.js'), P = require('../web/v2/pay.js');
const RATES = ['0.01', '0.3', '2.05', '29.12', '30.61', '32.3', '33.33', '35.4', '40', '45.55', '50.01', '66.67', '99.99', '850.005'];
/** Exact cents of units × rateText / per + bonusText, half up. */
function oracle(units, per, rateText, bonusText = '0') {
  const dec = t => { const [i, f = ''] = t.split('.'); return BigInt(i + f.padEnd(10, '0').slice(0, 10)); }; // 10^-10 units
  const num = (BigInt(units) * dec(rateText) + BigInt(per) * dec(bonusText)) * 100n, den = BigInt(per) * 10000000000n;
  const c = (2n * num + den) / (2n * den);
  return Number(c / 100n + '.' + String(c % 100n).padStart(2, '0'));
}
test('M-08: 50 minutes at 33.33 is 27.775, rounded once half up to 27.78 (not 27.77)', () => {
  assert.equal(R.money(50 / 60, 33.33), 27.78);
  assert.equal(P.money(50 / 60, 33.33), 27.78);
  assert.equal(R.evaluate({ mode: 'CLOCK', start: '08:00', end: '08:50', breakMinutes: 0, rate: 33.33, bonus: 0 }).total, 27.78);
});
test('M-08: every whole-minute shift of a day × 14 rates (20,160 combinations) matches the exact oracle, 0 mismatches', () => {
  const bad = [];
  for (let m = 1; m <= 1440; m++) for (const r of RATES) {
    const want = oracle(m, 60, r), got = R.money(m / 60, Number(r));
    if (got !== want) bad.push(`${m}min×${r}: ${got}≠${want}`);
  }
  assert.equal(bad.length, 0, bad.slice(0, 10).join(', '));
});
test('M-08: the editor preview (evaluate) and the bulk rate completion give the same oracle amount as the save', () => {
  for (const [m, r, b] of [[50, '33.33', '0'], [10, '33.33', '0.5'], [100, '29.12', '0'], [1, '0.3', '0'], [725, '45.55', '12.5']]) {
    const want = oracle(m, 60, r, b);
    const end = R.fromMinutes(8 * 60 + m);
    assert.equal(R.evaluate({ mode: 'CLOCK', start: '08:00', end, breakMinutes: 0, rate: Number(r), bonus: Number(b) }).total, want, `${m} ${r}`);
    const bulk = R.bulkRate([{ id: 'x', isJob: false, start: '08:00', end, breakMinutes: 0, hours: m / 60, rate: null, bonus: Number(b) }], Number(r));
    assert.equal(bulk.updated[0].total, want);
  }
});
test('M-08: timer seconds are exact too (1 hour 0 min 1 s at 36.01 = 36.020002… → 36.02; 7 s at 0.3 → 0.000583 → 0.00)', () => {
  for (const [s, r] of [[3601, '36.01'], [7, '0.3'], [5999, '33.33'], [30, '0.3'], [86399, '99.99']]) assert.equal(R.money(s / 3600, Number(r)), oracle(s, 3600, r), `${s}s×${r}`);
});
test('M-08: typed decimal hours keep the exact decimal contract (2.675 h × 1 = 2.68, 1.234567 h × 100 = 123.46)', () => {
  assert.equal(R.money(2.675, 1), 2.68); assert.equal(R.money(1.234567, 100), 123.46); assert.equal(R.money(1.5, 99.99), 149.99);
});
