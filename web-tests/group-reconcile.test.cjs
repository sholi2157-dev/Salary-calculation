// CAL-03 (2.3.0): team amounts reconcile exactly in whole agorot; the user's saved amount is used as saved and never changed.
// The fixture is shared with Android (app/src/test/resources/group-reconcile.json).
const { test } = require('node:test'), assert = require('node:assert/strict');
const P = require('../web/v2/pay.js'), doc = require('./fixtures/group-reconcile.json');
const c = v => Math.round(v * 100);

test('every case matches the shared fixture', () => {
  for (const k of doc.cases) {
    const e = structuredClone(k.entry), g = P.group(e);
    assert.deepEqual({ ...g, workers: P.workers(e).map(w => ({ worker: P.workerPay(w, e), employer: P.employerPay(w, e) })) }, k.expected, k.name);
  }
});

test('profit = Σ employer pay − Σ worker pay; employer total = own + Σ employer pay + pass-through; net = own + profit', () => {
  // Seeded pseudo-random team shifts with awkward hours, rates and bonuses.
  let seed = 7; const rnd = () => (seed = (seed * 16807) % 2147483647) / 2147483647;
  const pick = (a, b, dp) => Number((a + rnd() * (b - a)).toFixed(dp));
  for (let i = 0; i < 500; i++) {
    const ws = Array.from({ length: 1 + Math.floor(rnd() * 4) }, (_, j) => ({ name: 'w' + j, hours: pick(0, 12, 6), isPaid: false,
      bonus: rnd() < 0.3 ? pick(0, 80, 2) : 0, workerRate: rnd() < 0.5 ? pick(20, 90, 3) : null, employerRate: rnd() < 0.5 ? pick(40, 140, 3) : null }));
    const e = { kind: 'shift', isGroupShift: true, payKnown: true, hours: 5, hourlyRate: pick(30, 120, 2), totalEarnings: pick(0, 900, 2),
      employerRate: pick(40, 140, 2), workerRate: pick(20, 90, 2), passThrough: rnd() < 0.3 ? pick(0, 200, 2) : 0, currency: '₪', groupWorkersJson: JSON.stringify(ws) };
    const before = JSON.stringify(e), g = P.group(e), list = P.workers(e);
    const emp = list.reduce((n, w) => n + c(P.employerPay(w, e)), 0), team = list.reduce((n, w) => n + c(P.workerPay(w, e)), 0);
    assert.equal(c(g.profit), emp - team);
    assert.equal(c(g.employerTotal), c(e.totalEarnings) + emp + c(e.passThrough));
    assert.equal(c(g.myNet), c(e.totalEarnings) + c(g.profit));
    for (const v of [g.profit, g.employerTotal, g.myNet]) assert.equal(v, c(v) / 100, 'whole agorot');
    assert.equal(JSON.stringify(e), before, 'the entry (and its saved amount) is never changed');
  }
});

test('saved amounts are used exactly as saved: totals and the employer due of a plain shift', () => {
  const e = { kind: 'shift', isGroupShift: false, payKnown: true, totalEarnings: 42.12345, passThrough: 10, currency: '₪' };
  assert.equal(P.totals([e])['₪'], 42.12345);
  assert.equal(P.employerDue(e), 52.12);
  assert.equal(e.totalEarnings, 42.12345);
});

test('a missing rate still leaves the team total unknown instead of partial', () => {
  const e = { kind: 'shift', isGroupShift: true, payKnown: false, totalEarnings: 0, hourlyRate: 0, employerRate: null, workerRate: null,
    groupWorkersJson: JSON.stringify([{ name: 'א', hours: 2, isPaid: false, bonus: 0, workerRate: null, employerRate: null }]) };
  assert.equal(P.group(e), null);
});
