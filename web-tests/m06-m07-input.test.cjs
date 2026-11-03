const { test } = require('node:test'), assert = require('node:assert/strict');
const R = globalThis.KisRules = require('../web/v2/rules.js');
const P = globalThis.KisPay = require('../web/v2/pay.js');
require('../web/kis/editor.js');
const E = globalThis.KisEditor;
globalThis.KisUi = { startOfDay: ms => { const d = new Date(ms); d.setHours(0, 0, 0, 0); return +d; } };
const draft = (o = {}) => ({ mode: 'HOURS', category: 'בדיקה', day: 1760000000000, hours: '1:30', rate: '99.99', rateUnknown: false,
  bonus: '', bonusTarget: 'ME', amount: '', amountUnknown: false, notes: '', workers: [], start: null, end: '17:00', breakMin: 0,
  separateRates: false, employerRate: '', workerRate: '', currency: '₪', isPaid: false, ...o });

test('M-06: dot/comma rates, blank optional fields, zero and unknown pay retain their meanings', () => {
  for (const rate of ['99.99', '99,99', ' 99,99 ', '9.999e1', '99.99d']) {
    const d = draft({ rate });
    assert.equal(E.evaluate(d).total, 149.99, rate);
    assert.equal(E.toEntry(d).totalEarnings, 149.99, rate);
  }
  assert.equal(E.evaluate(draft({ rate: '0' })).total, 0);
  assert.equal(E.evaluate(draft({ rate: '' })).canSave, false);
  assert.equal(E.evaluate(draft({ rate: 'garbage', rateUnknown: true })).total, null);
  assert.equal(E.toEntry(draft({ rateUnknown: true })).payKnown, false);
  assert.equal(E.evaluate(draft({ bonus: '0', passThrough: '0' })).canSave, true);
});

test('M-06: invalid money never becomes zero, a positive hexadecimal number, or a default', () => {
  for (const value of ['abc', '.', '1,2,3', '1.2.3', 'NaN', 'Infinity', '-Infinity', '1e309', '-5', '0x10', '0b10']) {
    for (const field of ['rate', 'bonus', 'passThrough']) {
      const ev = E.evaluate(draft({ [field]: value }));
      assert.equal(ev.canSave, false, `${field}: ${value}`);
      assert.equal(ev.total, null);
    }
    for (const field of ['employerRate', 'workerRate']) {
      assert.equal(E.evaluate(draft({ mode: 'GROUP', separateRates: true, [field]: value })).canSave, false, `${field}: ${value}`);
    }
  }
});

test('M-06: job amounts follow Android V2Rules decimal syntax; invalid/non-finite amounts do not throw', () => {
  for (const amount of ['', 'abc', 'NaN', 'Infinity', '1e309', '-5', '0', '40,5', '0x10']) {
    assert.equal(E.evaluate(draft({ mode: 'JOB', hours: '', amount })).canSave, false, amount);
    assert.equal(R.evaluate({ mode: 'JOB', amount }).canSave, false, amount);
  }
  assert.equal(E.evaluate(draft({ mode: 'JOB', hours: '', amount: '40.5' })).total, 40.5);
  assert.equal(E.evaluate(draft({ mode: 'JOB', hours: '', amount: 'bad', amountUnknown: true })).total, null);
});

test('M-06: finite large amounts do not crash toFixed/BigInt and overflow is rejected', () => {
  for (const rate of [1e21, 1e100, 1e307]) {
    assert.equal(R.money(1, rate), rate);
    assert.equal(E.evaluate(draft({ hours: '1:00', rate: String(rate) })).total, rate);
  }
  for (const rate of [Infinity, NaN]) assert.equal(R.evaluate({ mode: 'HOURS', hours: '1', rate }).canSave, false);
  assert.equal(R.evaluate({ mode: 'HOURS', hours: '24', rate: 1e308 }).canSave, false);
  assert.equal(R.evaluate({ mode: 'HOURS', hours: '1', rate: 60, bonus: Infinity }).canSave, false);
  assert.equal(R.money(1, 0.005), 0.01);
  assert.equal(R.money(1, -0.005), -0.01);
  assert.equal(Object.is(R.money(1, -0.001), -0), false);
});

test('M-06: duration separators, invalid/empty input and 24-hour limits match DurationInput', () => {
  for (const text of ['1.30', '1,30', '1:30']) assert.equal(E.evaluate(draft({ hours: E.durationEdit(text) })).minutes, 90);
  for (const hours of ['', 'abc', 'Infinity', '-1', '0:00', '1:60', '24:01', '25:00', '99:99']) {
    assert.equal(E.evaluate(draft({ hours })).canSave, false, hours);
  }
  assert.equal(E.evaluate(draft({ hours: '24:00' })).minutes, 1440);
  assert.equal(E.durationHours('1.5'), 1.5); // Old drafts remain decimal, no migration.
  assert.equal(E.parseTime('2500'), null);
  assert.equal(E.parseTime('9999'), null);
  assert.equal(E.typeTimeOrNull('2000', 1, '5'), null);
  const overnight = R.span('22:00', '06:00');
  assert.equal(overnight.minutes, 480); assert.equal(overnight.overnight, true);
  assert.equal(R.span('09:00', '08:00').long, true);
  assert.equal(R.span('09:00', '09:00').invalid, true);
  assert.equal(R.span('09:00', '10:00', 60).invalid, true);
});

test('M-06: breaks use Android integer parsing and the 0–600 minute bounds', () => {
  for (const [text, expected] of [['', 0], ['abc', 0], ['Infinity', 0], ['1e309', 0], ['1.5', 0], ['1,5', 0],
    ['-5', 0], ['0', 0], ['15', 15], ['+15', 15], ['600', 600], ['601', 600], ['2147483648', 0]]) {
    assert.equal(E.breakInput(text), expected, text);
  }
});

test('M-06: worker amounts and bonuses reject invalid input; decimal commas remain amounts', () => {
  for (const value of ['abc', '-5', 'Infinity', '1e309', '1.2.3']) {
    assert.equal(E.evaluate(draft({ mode: 'GROUP', workers: [{ name: 'בדיקה', hours: '1:00', bonus: value }] })).canSave, false, value);
    assert.equal(E.evaluate(draft({ mode: 'JOB', amount: '100', team: true, teamSameRate: false,
      workers: [{ name: 'בדיקה', hours: '', amount: value }] })).canSave, false, value);
  }
  const d = draft({ mode: 'JOB', amount: '100', team: true, teamSameRate: false,
    workers: [{ name: 'בדיקה', hours: '', amount: '40,5', source: {} }] });
  assert.equal(E.evaluate(d).canSave, true);
  assert.equal(P.workers(E.toEntry(d))[0].amount, 40.5);
});

test('M-06/CW-14: bulk completion rejects invalid/overflow rates before preview or writes', () => {
  require('../web/kis/history.js');
  const H = globalThis.KisHistory, ui = globalThis.KisUi;
  const input = { value: '' }, ok = {}, preview = {};
  let writes = 0;
  const known = { id: 'known', hours: 1.234567, hourlyRate: 33.3333, totalEarnings: 42.12345, payKnown: true };
  const store = { data: { entries: [{ id: 'missing', hours: 2, bonus: 0, currency: '₪', payKnown: false }, known] },
    update: fn => { writes++; fn(store.data); } };
  H.state.selection = new Set(['missing', 'known']);
  globalThis.KisUi = { ...ui, money: String, haptic() {}, toast() {}, sheet: (id, render) => render({
    set innerHTML(v) {}, querySelector: q => q === '[data-f=r]' ? input : q === '[data-a=ok]' ? ok : preview,
  }, () => {}) };
  try {
    H.bulkRate({ store });
    input.value = '40,5'; input.oninput(); assert.equal(ok.disabled, false);
    for (const value of ['', 'abc', 'Infinity', '1e309', '-5', '0x10', '1e308']) {
      input.value = value; input.oninput();
      assert.equal(ok.disabled, true, value);
      assert.doesNotMatch(preview.textContent, /יתווסף/);
      ok.onclick(); assert.equal(writes, 0, value); // Recheck even when the click bypasses disabled UI.
    }
    input.value = '40,5'; input.oninput(); ok.onclick();
    assert.equal(writes, 1);
    assert.equal(store.data.entries[0].totalEarnings, 81);
    assert.equal(store.data.entries[0].hourlyRate, 40.5);
    assert.equal(store.data.entries[1], known);
    assert.equal(known.totalEarnings, 42.12345);
  } finally { globalThis.KisUi = ui; }
});

test('M-07: clock/manual groups with ME/OTHERS/ALL preview the same own pay that is saved', () => {
  for (const start of [null, '09:00']) for (const target of ['ME', 'OTHERS', 'ALL']) {
    const d = E.withBonusTarget(draft({ mode: 'GROUP', start, end: '10:30', rate: '60', bonus: '30',
      workers: [{ name: 'א', hours: '1:30', bonus: '', source: { custom: 'keep' } }, { name: 'ב', hours: '1:30', bonus: '20', source: {} }] }), target);
    const ev = E.evaluate(d), entry = E.toEntry(d), workers = P.workers(entry);
    assert.equal(ev.total, target === 'OTHERS' ? 90 : 120, `${start}/${target}`);
    assert.equal(entry.totalEarnings, ev.total);
    assert.deepEqual(workers.map(w => w.bonus), target === 'ME' ? [0, 0] : [30, 20]);
    assert.equal(workers[0].source.custom, 'keep');
    entry.id = 'synthetic';
    const edited = E.toEntry({ ...E.fromEntry(entry), notes: 'הערה' }, entry);
    assert.equal(edited.totalEarnings, entry.totalEarnings);
    assert.equal(edited.groupWorkersJson, entry.groupWorkersJson);
  }
});
