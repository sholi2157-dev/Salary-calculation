// F12 (2.3.1): the tour opens once per new Google account, on its first successful connect, and never for an account
// that already existed. Same rule and the same opening and closing words as the Android app (WorkOnboarding.kt).
const { test } = require('node:test'), assert = require('node:assert/strict'), fs = require('node:fs'), path = require('node:path');
require('../web/kis/launch.js');
const L = globalThis.KisLaunch;
const memory = () => { const m = new Map(); return { getItem: k => (m.has(k) ? m.get(k) : null), setItem: (k, v) => m.set(k, String(v)), removeItem: k => m.delete(k) }; };

test('a new account: both times stamped together (within two minutes)', () => {
  assert.ok(L.isNewAccount(5000, 5000));
  assert.ok(L.isNewAccount(5000, 5000 + 120000));
  assert.ok(!L.isNewAccount(5000, 5001 + 120000));
  assert.ok(!L.isNewAccount(NaN, 5000)); assert.ok(!L.isNewAccount(5000, NaN));
});
test('a new account gets the tour, and again after a reload until it is finished', () => {
  const s = memory();
  assert.equal(L.claimAccount(s, 'new', 10000, 10400), true);
  assert.equal(L.claimAccount(s, 'new', 10000, 10400), true, 'still pending');
  s.removeItem('kis_tour_pending_v1'); // the tour ended (finished or skipped)
  assert.equal(L.claimAccount(s, 'new', 10000, 10400), false);
  assert.equal(L.claimAccount(s, 'new', 10000, 9e12), false, 'a later sign-in never brings it back');
});
test('an account that already existed never gets it; a second new account here gets its own once', () => {
  const s = memory();
  assert.equal(L.claimAccount(s, 'old', 10000, 5e10), false);
  assert.equal(L.claimAccount(s, 'old', 10000, 5e10), false);
  assert.equal(L.claimAccount(s, 'second', 6e10, 6e10), true);
  s.removeItem('kis_tour_pending_v1');
  assert.equal(L.claimAccount(s, 'second', 6e10, 6e10), false);
});
test('clear, short steps; opening and closing words are the same in the app', () => {
  assert.ok(L.STEPS.length >= 5 && L.STEPS.length <= 8);
  for (const s of L.STEPS) { assert.ok(s.body.length <= 140, s.title); assert.doesNotMatch(s.body, /(^|[^א-ת])(הזן|הוסף|בחר|סמן|לחץ|הקלד) /); }
  const kt = path.join(__dirname, '..', 'app', 'src', 'main', 'java', 'com', 'example', 'ui', 'WorkOnboarding.kt');
  if (fs.existsSync(kt)) { const src = fs.readFileSync(kt, 'utf8'); for (const s of [L.STEPS[0], L.STEPS[L.STEPS.length - 1]]) { assert.ok(src.includes(s.title), 'Android has: ' + s.title); assert.ok(src.includes(s.body), 'Android has: ' + s.body); } }
});
