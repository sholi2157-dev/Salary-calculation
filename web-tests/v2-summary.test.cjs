// Mirrors Android app/src/test/java/com/example/V2SummaryTest.kt against the shared fixture.
const { test } = require('node:test'); const assert = require('node:assert/strict');
const S = require('../web/v2/summary.js'); const doc = require('./fixtures/v2-summary.json');

test('summaries match the contract', () => {
  for (const c of doc.cases) {
    const { slides, ...expected } = c.expected, got = S.summary(c.rows, c.scope, c.today);
    assert.deepEqual(got, expected, c.name);
    assert.deepEqual(S.slides(got), slides, c.name + ' slides');
  }
});
test('every persona and comparison has its words', () => {
  for (const c of doc.cases) {
    if (c.expected.persona) assert.ok(S.PERSONAS[c.expected.persona], c.name);
    if (c.expected.fun) assert.ok(S.FUN[c.expected.fun.key], c.name);
  }
});
test('the hour profile follows a shift across midnight', () => {
  const p = S.hourProfile([{ start: '22:30', end: '01:15' }]);
  assert.equal(p[22], 30); assert.equal(p[23], 60); assert.equal(p[0], 60); assert.equal(p[1], 15);
  assert.equal(p.reduce((a, b) => a + b, 0), 165);
});
