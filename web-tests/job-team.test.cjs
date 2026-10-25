// A job (קבלנות) with a team: same results as Android V2JobTeamTest.
const test = require('node:test'), assert = require('node:assert/strict');
const Pay = require('../web/v2/pay.js'), S = require('../web/v2/share.js');
const job = { kind: 'job', category: 'שיפוצים', date: 1_760_000_000_000, isTimeRange: false, hours: 0, hourlyRate: 0, totalEarnings: 370, payKnown: true, isPaid: false, notes: '',
  currency: '₪', isGroupShift: true, groupWorkersJson: JSON.stringify([{ name: 'דני', hours: 0, isPaid: false, amount: 300 }, { name: 'יוסי', hours: 5.5, isPaid: false, workerRate: 60 }]) };
test('a team job counts only the user share and pays each worker their own way', () => {
  const ws = Pay.workers(job);
  assert.equal(Pay.workerPay(ws[0], job), 300); assert.equal(Pay.workerPay(ws[1], job), 330);
  assert.equal(Pay.teamPay(job), 630); assert.deepEqual(Pay.group(job), { employerTotal: 1000, profit: 0, myNet: 370 });
  assert.deepEqual(Pay.totals([job]), { '₪': 370 });
});
test('the team report names the fixed sum and the user share', () => {
  const one = { ...job, totalEarnings: 700, groupWorkersJson: JSON.stringify([{ name: 'דני', hours: 0, isPaid: false, amount: 300 }]) };
  const r = S.employer(one, 'שולי');
  assert.match(r, /דני: סכום קבוע \| ₪300\.00/); assert.match(r, /שולי: ₪700\.00/); assert.match(r, /סכום הצוות: ₪1,000\.00/);
  const w = S.worker(one, Pay.workers(one)[0]);
  assert.match(w, /₪300\.00/); assert.doesNotMatch(w, /₪700/);
});
test('a group shift worker without a rate still follows the shift rate', () => {
  const shift = { category: 'מסעדה', date: 1, hours: 4, hourlyRate: 50, totalEarnings: 200, payKnown: true, isGroupShift: true, groupWorkersJson: '[{"name":"נועה","hours":4,"isPaid":false}]' };
  assert.equal(Pay.workerPay(Pay.workers(shift)[0], shift), 200);
});
