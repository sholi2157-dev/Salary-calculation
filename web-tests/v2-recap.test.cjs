// Mirrors Android app/src/test/java/com/example/V2RecapTest.kt against the shared fixture.
const { test } = require('node:test'); const assert = require('node:assert/strict');
const S = require('../web/v2/summary.js'); const doc = require('./fixtures/v2-recap.json');

test('recaps open on the 30th, are new for 4 more days, and never before the first one', () => {
  assert.equal(S.FIRST_RECAP, doc.first); assert.equal(S.RECAP_NEW_DAYS, doc.newDays);
  for (const c of doc.windows) {
    assert.deepEqual(S.recapNew(c.today), { month: c.month, year: c.year }, c.today);
    assert.deepEqual(S.recapArchive(c.today), c.archive, c.today + ' archive');
    assert.deepEqual(['2026-09', '2026-10', '2026-11', '2026', '2027-02'].filter(s => S.recapOpen(s, c.today)), c.open, c.today + ' open');
  }
});
test('the words are picked the same way on both platforms, and differ between people and recaps', () => {
  for (const c of doc.voices) { assert.equal(S.hash(`${c.seed}|${c.scope}`), c.hash); assert.deepEqual(S.voice(c.scope, c.seed), c.voice, c.seed + ' ' + c.scope); }
  const distinct = new Set(doc.voices.map(c => JSON.stringify(c.voice)));
  assert.ok(distinct.size >= doc.voices.length * .8, 'most recaps read differently');
});
test('a year uses its own lines', () => {
  assert.ok(S.VOICE.introLineYear.includes(S.voice('2026', 'x').introLine));
  assert.ok(S.VOICE.introLine.includes(S.voice('2026-10', 'x').introLine));
});
