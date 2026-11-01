// 2.3.0 money contract (audit CAL-02, CAL-05), the same fixture as Android app/src/test/resources/money-contract.json.
const { test } = require('node:test'), assert = require('node:assert/strict');
const R = require('../web/v2/rules.js'), P = require('../web/v2/pay.js'), doc = require('./fixtures/money-contract.json');
test('every new amount follows the shared contract', () => {
  for (const c of doc.cases) { assert.equal(R.money(c.hours, c.rate, c.bonus), c.expected, JSON.stringify(c)); assert.equal(P.money(c.hours, c.rate, c.bonus), c.expected); }
});
test('preview and save agree: clock 08:00–08:30 at 2.05 and a missing rate completed for 1.234567 hours', () => {
  const ev = R.evaluate({ mode: 'CLOCK', start: '08:00', end: '08:30', breakMinutes: 0, rate: 2.05, bonus: 0 });
  assert.equal(ev.total, 1.03); assert.equal(P.money(30 / 60, 2.05), 1.03);
  const bulk = R.bulkRate([{ id: 'x', isJob: false, start: null, end: null, breakMinutes: 0, hours: 1.234567, rate: null, bonus: 0 }], 100);
  assert.equal(bulk.updated[0].total, P.money(1.234567, 100)); assert.equal(bulk.updated[0].total, 123.46);
});
test('derived group amounts keep their 2.0 rounding (commission semantics unchanged pending review)', () => {
  assert.equal(P.amount(1.5, 99.99), 149.98);
});
