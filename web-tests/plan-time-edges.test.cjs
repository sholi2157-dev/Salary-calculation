// 2.3.0 (audit CW-06, CW-07): ending a series today, and overlaps across midnight. Same cases as Android V2PlanEdgesTest.
const { test } = require('node:test'), assert = require('node:assert/strict');
globalThis.KisRules = require('../web/v2/rules.js'); globalThis.KisPay = require('../web/v2/pay.js');
const P = require('../web/v2/plan.js');
const at = (date, hm) => P.startMillis(date, hm);
const s = { id: 's', category: 'מסעדה', daysMask: 127, startTime: '18:00', endTime: '23:00', hourlyRate: 50, currency: '₪', fromDate: '2026-03-01', untilDate: '2026-12-31' };

test('ending a series before today’s shift starts drops today; after it started, today stays', () => {
  assert.equal(P.endUntil(s, at('2026-03-10', '12:00')), '2026-03-09');
  assert.equal(P.endUntil(s, at('2026-03-10', '18:30')), '2026-03-10');
  assert.equal(P.endUntil({ ...s, fromDate: '2026-03-10' }, at('2026-03-10', '12:00')), null, 'a series that only had today is removed');
  assert.equal(P.endUntil({ ...s, untilDate: '2026-03-05' }, at('2026-03-10', '12:00')), '2026-03-05', 'an ended series keeps its end');
});

test('a shift from the evening before that runs past midnight blocks an early plan the next day', () => {
  const saved = [{ isTimeRange: true, startTime: '22:00', endTime: '06:00', date: at('2026-03-09', '22:00') }];
  const p = P.preview(new Set([2]), '2026-03-10', '2026-03-10', '05:00', '09:00', saved, []);  // 2026-03-10 is a Tuesday
  assert.deepEqual(p.clashes, ['2026-03-10']);
  const ok = P.preview(new Set([2]), '2026-03-10', '2026-03-10', '06:00', '09:00', saved, []);  // touching ends do not clash
  assert.deepEqual(ok.clashes, []);
  const plans = [{ date: '2026-03-10', start: '01:00', end: '03:00' }];
  assert.deepEqual(P.preview(new Set([1]), '2026-03-09', '2026-03-09', '23:00', '02:00', [], plans).clashes, ['2026-03-09'], 'and a late plan that runs into the next day’s plan');
});
