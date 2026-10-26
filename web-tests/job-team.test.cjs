// A job (קבלנות) with a team: same results as Android V2JobTeamTest.
const test = require('node:test'), assert = require('node:assert/strict');
const Pay = require('../web/v2/pay.js'), S = require('../web/v2/share.js'), T = require('../web/transfer.js');
const job = { kind: 'job', category: 'שיפוצים', date: 1_760_000_000_000, isTimeRange: false, hours: 0, hourlyRate: 0, totalEarnings: 75, payKnown: true, isPaid: false, notes: '',
  currency: '₪', isGroupShift: true, groupWorkersJson: JSON.stringify([{ name: 'דני', hours: 0, isPaid: false, amount: 50 }, { name: 'יוסי', hours: 0, isPaid: false, amount: 50 }]) };
test('the user amount is theirs and the team is paid on top', () => {
  const ws = Pay.workers(job);
  assert.equal(Pay.workerPay(ws[0], job), 50); assert.equal(Pay.teamPay(job), 100);
  assert.deepEqual(Pay.group(job), { employerTotal: 175, profit: 0, myNet: 75 });
  assert.deepEqual(Pay.totals([job]), { '₪': 75 });
});
test('a worker saved as hours × rate is still paid that', () => {
  const old = { ...job, groupWorkersJson: JSON.stringify([{ name: 'יוסי', hours: 5.5, isPaid: false, workerRate: 60 }]) };
  assert.equal(Pay.teamPay(old), 330);
});
test('the team report names the fixed sum and the user share', () => {
  const one = { ...job, totalEarnings: 700, groupWorkersJson: JSON.stringify([{ name: 'דני', hours: 0, isPaid: false, amount: 300 }]) };
  const r = S.employer(one, 'שולי');
  assert.match(r, /דני: סכום קבוע \| ₪300\.00/); assert.match(r, /שולי: ₪700\.00/); assert.match(r, /סכום הצוות: ₪1,000\.00/);
  const w = S.worker(one, Pay.workers(one)[0]);
  assert.match(w, /₪300\.00/); assert.doesNotMatch(w, /₪700/);
});
test('money passed through is owed by the employer but is not income', () => {
  const solo = { kind: 'job', category: 'שיפוצים', date: 1, isTimeRange: false, hours: 0, hourlyRate: 0, totalEarnings: 25, payKnown: true, isPaid: false, notes: '', currency: '₪', passThrough: 15 };
  assert.deepEqual(Pay.totals([solo]), { '₪': 25 }); assert.equal(Pay.employerDue(solo), 40);
  const team = { ...job, passThrough: 30, groupWorkersJson: JSON.stringify([{ name: 'דני', hours: 0, isPaid: false, amount: 50 }]) };
  assert.equal(Pay.group(team).employerTotal, 155);
  assert.match(S.personal(solo), /החזר הוצאות: ₪15\.00 \(לא כלול בסכום\)/);
  const r = S.employer(team, 'שולי'); assert.match(r, /החזר הוצאות: ₪30\.00/); assert.match(r, /סכום הצוות: ₪155\.00/);
  assert.doesNotMatch(S.worker(team, Pay.workers(team)[0]), /החזר/);
  // It travels in backups (so in sync), after the 2.0 fields as Android writes it; a file without it is unchanged.
  const text = T.encode({ entries: [solo] }), back = T.decode(text).entries[0];
  assert.equal(back.passThrough, 15); assert.ok(text.indexOf('"passThrough"') > text.indexOf('"autoConfirmed"'));
  assert.doesNotMatch(T.encode({ entries: [{ ...solo, passThrough: 0 }] }), /passThrough/);
  assert.throws(() => T.decode(T.encode({ entries: [solo] }).replace('"passThrough": 15', '"passThrough": -1')));
});
test('a group shift worker without a rate still follows the shift rate', () => {
  const shift = { category: 'מסעדה', date: 1, hours: 4, hourlyRate: 50, totalEarnings: 200, payKnown: true, isGroupShift: true, groupWorkersJson: '[{"name":"נועה","hours":4,"isPaid":false}]' };
  assert.equal(Pay.workerPay(Pay.workers(shift)[0], shift), 200);
});
