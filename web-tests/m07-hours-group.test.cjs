// M-07 regression, exact audit path: a group in hours mode (no start time) reached through changeMode, with a 20 bonus
// for "העובדים". Android KisDraft.groupByHours (mode == GROUP && start == null) evaluates it as HOURS with bonus 0 for
// OTHERS, so the preview, the saved amount and the edited-and-saved amount are the same number. Synthetic drafts only.
const { test } = require('node:test'), assert = require('node:assert/strict');
const R = globalThis.KisRules = require('../web/v2/rules.js');
const P = globalThis.KisPay = require('../web/v2/pay.js');
require('../web/kis/editor.js');
const E = globalThis.KisEditor;
globalThis.KisUi = { startOfDay: ms => { const d = new Date(ms); d.setHours(0, 0, 0, 0); return +d; } };
const draft = (o = {}) => ({ mode: 'HOURS', category: 'בדיקה', day: 1760000000000, hours: '2:00', rate: '50', rateUnknown: false,
  bonus: '20', bonusTarget: 'ME', amount: '', amountUnknown: false, notes: '', workers: [], start: null, end: '10:00', breakMin: 0,
  separateRates: false, employerRate: '', workerRate: '', currency: '₪', isPaid: false, ...o });
const worker = { name: 'א', hours: '2:00', bonus: '', source: { keep: 'unknown-field' } };

test('M-07: HOURS → changeMode(GROUP) has no start time (Android groupByHours)', () => {
  const g = E.changeMode(draft(), 'GROUP');
  assert.equal(g.mode, 'GROUP'); assert.equal(g.start, null);
  // And from a clock shift, back to hours then to group, the start is dropped too.
  const c = E.changeMode(E.changeMode(draft({ mode: 'CLOCK', start: '08:00' }), 'HOURS'), 'GROUP');
  assert.equal(c.start, null); assert.equal(c.hours, '2:00');
});

for (const [target, own, workerBonus] of [['OTHERS', 100, 20], ['ME', 120, 0], ['ALL', 120, 20]]) {
  test(`M-07: hours-mode group, 2 h × 50 + bonus 20 for ${target}: preview = saved = ${own}`, () => {
    const d = E.withBonusTarget({ ...E.changeMode(draft(), 'GROUP'), workers: [{ ...worker }] }, target);
    const ev = E.evaluate(d), entry = E.toEntry(d), [w] = P.workers(entry);
    assert.equal(ev.canSave, true);
    assert.equal(ev.total, own, 'preview');            // Was 120 for OTHERS before #24 while 100 was saved.
    assert.equal(entry.totalEarnings, own, 'saved');
    assert.equal(entry.bonus, target === 'OTHERS' ? 0 : 20);
    assert.equal(w.bonus, workerBonus);
    assert.equal(w.source.keep, 'unknown-field');
    // Same rule as the Android check() path: V2Rules.evaluate on HOURS with bonus 0 for OTHERS.
    assert.equal(R.evaluate({ mode: 'HOURS', hours: 2, rate: 50, bonus: target === 'OTHERS' ? 0 : 20 }).total, own);
    // Re-opening and saving without a money change keeps the saved amount and the workers byte-for-byte.
    entry.id = 'synthetic';
    const again = E.toEntry({ ...E.fromEntry(entry), notes: 'הערה' }, entry);
    assert.equal(again.totalEarnings, entry.totalEarnings);
    assert.equal(again.groupWorkersJson, entry.groupWorkersJson);
  });
}
