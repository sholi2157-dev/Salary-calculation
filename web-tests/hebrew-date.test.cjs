// Hebrew dates and weekly portions (owner feedback 2.3) match the shared fixture (same file as app/src/test/resources/hebrew-dates.json).
const test = require('node:test'), assert = require('node:assert/strict'), fs = require('fs'), path = require('path');
const H = require('../web/v2/hebdate.js'), doc = require('./fixtures/hebrew-dates.json');
const ms = iso => { const [y, m, d] = iso.split('-').map(Number); return new Date(y, m - 1, d, 12).getTime(); };
test('days match the fixture', () => { for (const [iso, short, long] of doc.days) { assert.equal(H.label(ms(iso), false), short, iso); assert.equal(H.label(ms(iso), true), long, iso); } });
test('weeks match the fixture', () => { for (const [iso, dia, il] of doc.weeks) { assert.equal(H.portion(ms(iso), false), dia, iso); assert.equal(H.portion(ms(iso), true), il, iso); } });
test('the owner example', () => { assert.equal(H.label(ms('2026-11-08'), false), 'כ״ח חשוון (8/11)'); assert.equal(H.gematria(15), 'ט״ו'); assert.equal(H.gematria(5787), 'תשפ״ז'); });
test('the fixture is the same file on both platforms', () => {
  const android = path.join(__dirname, '..', 'app', 'src', 'test', 'resources', 'hebrew-dates.json');
  if (fs.existsSync(android)) assert.equal(fs.readFileSync(android, 'utf8'), fs.readFileSync(path.join(__dirname, 'fixtures', 'hebrew-dates.json'), 'utf8'));
});
