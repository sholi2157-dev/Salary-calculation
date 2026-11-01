// 2.3.0 (audit CW-05, owner decision): a few days that just ended are recorded by themselves; more, or older, wait for review.
const { test } = require('node:test'), assert = require('node:assert/strict');
globalThis.KisRules = require('../web/v2/rules.js'); globalThis.KisPay = require('../web/v2/pay.js');
const P = require('../web/v2/plan.js');
const day = (date, start = '09:00', end = '17:00') => ({ key: 's@' + date, seriesId: 's', date, start, end, rate: 50, currency: '₪' });
const now = P.startMillis('2026-03-10', '20:00');
test('up to 3 days that ended within 3 days are recorded automatically', () => {
  const due = [day('2026-03-08'), day('2026-03-09'), day('2026-03-10')];
  assert.deepEqual(P.splitCatchUp(due, now), { auto: due, review: [] });
});
test('a 4th day, or one that ended more than 3 days ago, sends all of them to review', () => {
  const four = [day('2026-03-07'), day('2026-03-08'), day('2026-03-09'), day('2026-03-10')];
  assert.equal(P.splitCatchUp(four, now).auto.length, 0); assert.equal(P.splitCatchUp(four, now).review.length, 4);
  const old = [day('2026-03-01')];
  assert.deepEqual(P.splitCatchUp(old, now), { auto: [], review: old });
});
test('nothing due, nothing to do', () => assert.deepEqual(P.splitCatchUp([], now), { auto: [], review: [] }));
