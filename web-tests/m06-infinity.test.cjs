// M-06 regressions for the exact inputs from the 2.3.0 audit: bonus and reimbursement ("החזר הוצאות") text such as
// 'abc', '20₪' and '1e400' is refused with a message (never read as 0 or as a number), and no non-finite value reaches
// KisRules.money(), whose BigInt arithmetic would throw. Synthetic drafts only.
const { test } = require('node:test'), assert = require('node:assert/strict');
const R = globalThis.KisRules = require('../web/v2/rules.js');
const P = globalThis.KisPay = require('../web/v2/pay.js');
require('../web/kis/editor.js');
const E = globalThis.KisEditor;
globalThis.KisUi = { startOfDay: ms => { const d = new Date(ms); d.setHours(0, 0, 0, 0); return +d; } };
const draft = (o = {}) => ({ mode: 'HOURS', category: 'בדיקה', day: 1760000000000, hours: '2:00', rate: '40', rateUnknown: false,
  bonus: '', bonusTarget: 'ME', amount: '', amountUnknown: false, notes: '', workers: [], start: null, end: '17:00', breakMin: 0,
  separateRates: false, employerRate: '', workerRate: '', currency: '₪', isPaid: false, passThrough: '', ...o });
const BAD = ['abc', '20₪', '₪20', '1e400', '-1e400', 'Infinity', '20 ש"ח'];

test('M-06: bonus and reimbursement refuse abc / 20₪ / 1e400 with a message, in every mode', () => {
  const modes = [{}, { mode: 'CLOCK', start: '08:00', end: '10:00' }, { mode: 'GROUP', workers: [{ name: 'א', hours: '2:00', bonus: '', source: {} }] },
    { mode: 'JOB', hours: '', amount: '100' }];
  for (const m of modes) for (const value of BAD) {
    const bonus = E.evaluate(draft({ ...m, bonus: value }));
    assert.equal(bonus.canSave, false, `${m.mode || 'HOURS'} bonus ${value}`);
    assert.ok(bonus.errors.includes('הבונוס לא תקין'), `${m.mode || 'HOURS'} bonus ${value}: ${bonus.errors}`);
    const refund = E.evaluate(draft({ ...m, passThrough: value }));
    assert.equal(refund.canSave, false, `${m.mode || 'HOURS'} passThrough ${value}`);
    assert.ok(refund.errors.includes('סכום ההחזר לא תקין'), `${m.mode || 'HOURS'} passThrough ${value}: ${refund.errors}`);
  }
  // Valid text keeps working: comma decimal, surrounding spaces, an explicit zero, an empty field.
  assert.equal(E.evaluate(draft({ bonus: '20,5' })).total, 100.5);
  assert.equal(E.evaluate(draft({ bonus: ' 20 ' })).total, 100);
  assert.equal(E.evaluate(draft({ bonus: '0', passThrough: '0' })).canSave, true);
  assert.equal(E.evaluate(draft({ passThrough: '12,5' })).canSave, true);
  assert.equal(E.toEntry(draft({ passThrough: '12,5' })).passThrough, 12.5);
});

test('M-06: a contractor (קבלנות) total, team amount or worker bonus of 1e400 is refused and never throws', () => {
  for (const amount of ['1e400', '-1e400', 'Infinity', '20₪']) {
    const d = draft({ mode: 'JOB', hours: '', amount });
    assert.doesNotThrow(() => E.evaluate(d)); assert.equal(E.evaluate(d).canSave, false, amount);
    assert.equal(E.evaluate(d).total, null);
    assert.doesNotThrow(() => R.evaluate({ mode: 'JOB', amount }));
    const team = draft({ mode: 'JOB', amount: '100', team: true, teamSameRate: false, workers: [{ name: 'א', hours: '', amount, source: {} }] });
    assert.doesNotThrow(() => E.evaluate(team)); assert.equal(E.evaluate(team).canSave, false, `team ${amount}`);
    const group = draft({ mode: 'GROUP', workers: [{ name: 'א', hours: '2:00', bonus: amount, source: {} }] });
    assert.doesNotThrow(() => E.evaluate(group)); assert.equal(E.evaluate(group).canSave, false, `worker bonus ${amount}`);
  }
  for (const rate of ['1e400', '20₪']) for (const field of ['rate', 'employerRate', 'workerRate']) {
    const d = draft({ mode: 'GROUP', separateRates: true, employerRate: '50', workerRate: '40', workers: [{ name: 'א', hours: '2:00', bonus: '', source: {} }], [field]: rate });
    assert.doesNotThrow(() => E.evaluate(d)); assert.equal(E.evaluate(d).canSave, false, `${field} ${rate}`);
  }
});

test('M-06: KisRules.evaluate stops non-finite money before money(); money() itself refuses it', () => {
  for (const [rate, bonus] of [[Infinity, 0], [-Infinity, 0], [NaN, 0], [40, Infinity], [40, NaN], [1e308, 0]]) {
    assert.doesNotThrow(() => R.evaluate({ mode: 'HOURS', hours: '24', rate, bonus }));
    assert.equal(R.evaluate({ mode: 'HOURS', hours: '24', rate, bonus }).canSave, false, `${rate} ${bonus}`);
  }
  assert.throws(() => R.money(1, Infinity)); // Never silently Infinity or 0: callers must validate first.
});

test('M-06: a stored team rate that parses to Infinity (JSON 1e400) is unknown pay, not a crash', () => {
  const base = { id: 'synthetic', isGroupShift: true, payKnown: true, hourlyRate: 50, totalEarnings: 100, hours: 2, currency: '₪', isPaid: false };
  for (const key of ['workerRate', 'employerRate']) {
    const e = { ...base, groupWorkersJson: `[{"name":"א","hours":1,"${key}":1e400}]` };
    const [w] = P.workers(e);
    assert.equal(w[key], Infinity);
    assert.doesNotThrow(() => P.group(e));
    assert.equal(P.group(e), null);
    assert.equal(P.employerDue(e), null);
    assert.equal(key === 'workerRate' ? P.workerPay(w, e) : P.employerPay(w, e), null);
    assert.equal(P.pay(e), 100); // The saved amount itself is untouched.
  }
  // A finite rate still pays as before.
  const ok = { ...base, groupWorkersJson: '[{"name":"א","hours":1,"workerRate":30}]' };
  assert.equal(P.workerPay(P.workers(ok)[0], ok), 30);
  assert.deepEqual(P.group(ok), { employerTotal: 150, profit: 20, myNet: 120 });
});
