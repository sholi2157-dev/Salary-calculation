// FB23-F11: the release highlights show once in a browser that had the site before, never on a first visit, only for a
// version that has them, and the 2.3.0 draft is short and features only. Same wording as the Android app.
const { test } = require('node:test'), assert = require('node:assert/strict'), fs = require('node:fs'), path = require('node:path');
const W = require('../web/kis/whatsnew.js');
const notes = { '2.3.0': ['הוספנו X'] };

test('a returning browser without a record sees this version\'s highlights', () => assert.deepEqual(W.decide(null, '2.3.0', true, notes), ['הוספנו X']));
test('a first visit sees nothing', () => assert.equal(W.decide(null, '2.3.0', false, notes), null));
test('the same version again shows nothing', () => assert.equal(W.decide('2.3.0', '2.3.0', true, notes), null));
test('an older record shows the new version\'s highlights', () => assert.deepEqual(W.decide('2.2.1', '2.3.0', false, notes), ['הוספנו X']));
test('a version without highlights stays quiet', () => assert.equal(W.decide('2.3.0', '2.3.1', true, notes), null));
test('pending records at once when there is nothing to show; seen records after the card is closed', () => {
  const local = {}, store = { local, setLocal: (k, v) => { local[k] = v; } };
  assert.equal(W.pending(store, false), null); assert.equal(local[W.KEY], W.VERSION);
  delete local[W.KEY];
  assert.ok(W.pending(store, true)); assert.equal(local[W.KEY], undefined, 'not recorded until seen');
  W.seen(store); assert.equal(local[W.KEY], W.VERSION);
});
test('the 2.3.0 draft: 3–5 short lines, features only, same as Android', () => {
  const draft = W.NOTES['2.3.0'];
  assert.ok(draft.length >= 3 && draft.length <= 5);
  for (const line of draft) assert.doesNotMatch(line, /תקלה|באג|תיקון/);
  const kt = path.join(__dirname, '..', 'app', 'src', 'main', 'java', 'com', 'example', 'ui', 'kis', 'KisWhatsNew.kt');
  if (fs.existsSync(kt)) { const src = fs.readFileSync(kt, 'utf8'); for (const line of draft.slice(0, 3)) assert.ok(src.includes(line), 'Android has: ' + line); }
});
test('the script is in the page and in the offline shell', () => {
  const root = path.join(__dirname, '..');
  assert.match(fs.readFileSync(path.join(root, 'index.html'), 'utf8'), /web\/kis\/whatsnew\.js/);
  assert.match(fs.readFileSync(path.join(root, 'sw.js'), 'utf8'), /\/web\/kis\/whatsnew\.js/);
});
