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

// Settings → תאריכים (owner feedback 2.3.1); mirrors HebrewDateTest.theDateChoice.
test('the date choice: civil, Hebrew or both', () => {
  const H = require('../web/v2/hebdate.js'), S = require('../web/v2/share.js');
  const ms = new Date(2026, 10, 8, 12).getTime();
  try {
    H.setMode('GREG'); S.setTextChoices('GREG', []);
    assert.equal(H.show(ms, false), '8/11'); assert.equal(S.date(ms), '08/11/2026');
    H.setMode('HEB'); S.setTextChoices('HEB', []);
    assert.equal(H.show(ms, false), 'כ״ח חשוון'); assert.equal(H.show(ms, true), 'כ״ח חשוון תשפ״ז'); assert.equal(S.date(ms), 'כ״ח חשוון תשפ״ז');
    H.setMode('BOTH'); S.setTextChoices('BOTH', []);
    assert.equal(H.show(ms, false), 'כ״ח חשוון (8/11)'); assert.equal(S.date(ms), 'כ״ח חשוון (8/11)');
    assert.equal(S.civilDate(ms), '08/11/2026');
  } finally { H.setMode('BOTH'); S.setTextChoices('GREG', []); }
});

// A report to a worker asks for what the user gets paid with; mirrors WorkPaymentTextTest.theAskFollowsTheProfile.
test('the ask follows the profile', () => {
  const S = require('../web/v2/share.js');
  assert.equal(S.askPayment([]), S.ASK_PAYMENT);
  assert.equal(S.askPayment(['zelle', 'bit', 'bank']), S.ASK_PAYMENT);
  assert.equal(S.askPayment(['zelle']), 'אפשר לשלוח לי את ה-Zelle שלך, ואעביר את התשלום.');
  assert.equal(S.askPayment(['bank', 'bit']), 'אפשר לשלוח לי את הביט או פרטי החשבון שלך, ואעביר את התשלום.');
});
