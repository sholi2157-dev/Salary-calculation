// M-07 (CW-01/CAL-01): a group bonus "for the workers" or "for everyone" reaches the workers, as on Android
// (EntryDraft.withBonusTarget); it never disappears from the saved shift.
const { test } = require('node:test'), assert = require('node:assert/strict');
globalThis.KisRules = require('../web/v2/rules.js');
globalThis.KisPay = require('../web/v2/pay.js'); require('../web/kis/editor.js');
const E = globalThis.KisEditor, P = globalThis.KisPay;
globalThis.KisUi = { startOfDay: ms => { const t = new Date(ms); t.setHours(0, 0, 0, 0); return +t; } };
const group = (o = {}) => ({ mode: 'GROUP', category: 'אירועים', day: 1760000000000, hours: '', rate: '70', rateUnknown: false, currency: '₪', bonus: '50', bonusTarget: 'ME', amount: '', notes: '',
  start: '18:00', end: '23:00', breakMin: 0, separateRates: true, employerRate: '70', workerRate: '50', team: false, isPaid: false,
  workers: [{ name: 'יוסי', hours: '5', isPaid: false, bonus: '', source: {} }, { name: 'מנחם', hours: '4', isPaid: false, bonus: '20', source: {} }], ...o });
const saved = d => { const e = E.toEntry(d); return { e, ws: P.workers(e) }; };

test('"העובדים": my pay has no bonus, every worker without one gets it, a worker with their own keeps it', () => {
  const { e, ws } = saved(E.withBonusTarget(group(), 'OTHERS'));
  assert.equal(e.bonus, 0); assert.equal(e.totalEarnings, 350);
  assert.deepEqual(ws.map(w => w.bonus), [50, 20]);
  assert.deepEqual(ws.map(w => P.workerPay(w, e)), [300, 220]);
});
test('"כולם": I and every worker get it', () => {
  const { e, ws } = saved(E.withBonusTarget(group(), 'ALL'));
  assert.equal(e.bonus, 50); assert.equal(e.totalEarnings, 400); assert.deepEqual(ws.map(w => w.bonus), [50, 20]);
});
test('back to "שלי" clears the workers\' bonuses', () => {
  const { e, ws } = saved(E.withBonusTarget(E.withBonusTarget(group(), 'OTHERS'), 'ME'));
  assert.equal(e.bonus, 50); assert.deepEqual(ws.map(w => w.bonus), [0, 0]);
});
test('a worker added after choosing "העובדים" also gets it at save', () => {
  const d = E.withBonusTarget(group(), 'OTHERS'); d.workers.push({ name: 'דני', hours: '3', isPaid: false, bonus: '', source: {} });
  assert.deepEqual(saved(d).ws.map(w => w.bonus), [50, 20, 50]);
});
test('opening an older group shift (target "שלי" by default) never changes its workers\' bonuses', () => {
  const { ws } = saved(group());
  assert.deepEqual(ws.map(w => w.bonus), [0, 20]);
});

// M-48 (D-7 / AND-C06): a job team with an amount per worker can be saved with own pay 0 (only organizing); same as Android.
test('organizer-only job team: own 0 is a known 0; one amount for all, or no team, still needs an amount', () => {
  const job = (o = {}) => ({ mode: 'JOB', category: 'שיפוצים', day: 1760000000000, hours: '', rate: '', rateUnknown: false, currency: '₪', bonus: '', bonusTarget: 'ME', amount: '0', amountUnknown: false,
    notes: '', start: null, end: null, breakMin: 0, team: true, teamSameRate: false, isPaid: false, workers: [{ name: 'דני', hours: '', isPaid: false, bonus: '', amount: '400', source: {} }, { name: 'יוסי', hours: '', isPaid: false, bonus: '', amount: '350', source: {} }], ...o });
  const ev = E.evaluate(job());
  assert.deepEqual(ev.errors, []); assert.equal(ev.total, 0);
  const e = E.toEntry(job());
  assert.equal(e.payKnown, true); assert.equal(e.totalEarnings, 0); assert.equal(P.group(e).employerTotal, 750);
  assert.ok(E.evaluate(job({ teamSameRate: true })).errors.length > 0);
  assert.ok(E.evaluate(job({ workers: [], team: false })).errors.length > 0);
});
