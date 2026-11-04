// M-46 (web): smaller items from the 2.3 design audit, checked in the source (the page-level checks are in
// browser-tests/m46-ui.cjs): settings rows without info dots, feedback not first, import not primary, a keyboard way to
// move a place, no auto-record switch without a series, no copy of an empty report, break chips that wrap, one page width
// on a computer, and the message's action in the bar's ink.
const { test } = require('node:test'), assert = require('node:assert/strict'), fs = require('node:fs'), path = require('node:path');
const read = f => fs.readFileSync(path.join(__dirname, '..', 'web', 'kis', f), 'utf8');
const settings = read('settings.js'), css = read('kis.css');
test('settings: rows explain themselves without an info dot; feedback is not first; import is not primary', () => {
  const row = settings.slice(settings.indexOf('const rowHtml'), settings.indexOf('\n', settings.indexOf('const rowHtml')));
  assert.doesNotMatch(row, /infoDot/);
  assert.ok(settings.indexOf("['עבודה'") < settings.indexOf("['משוב ותקלות'"), 'categories start with work');
  assert.ok(settings.indexOf("group('משוב ותקלות'") > settings.indexOf("group('עזרה'"));
  assert.match(settings, /class="btn small outline grow" data-a="import"/);
});
test('places: the handle is a button and arrow keys move a place', () => {
  assert.match(settings, /<button type="button" class="grip" data-grip data-move=/);
  assert.match(settings, /e\.key === 'ArrowUp' \? -1 : e\.key === 'ArrowDown' \? 1 : 0/);
});
test('empty states: no auto-record switch without a series; no copy of 0 shifts', () => {
  assert.match(read('plans.js'), /\$\{store\.plans\.series\.length \? `<div class="row" style="align-items:center"><div class="grow title-row"><div class="label">רישום אוטומטי/);
  assert.match(read('history.js'), /data-testid="history-copy" \$\{rows\.length \? '' : 'disabled'\}/);
});
test('editor breaks wrap; one page width on a computer; message action is not the brand colour', () => {
  assert.match(read('editor.js'), /<b class="label">הפסקה<\/b><div class="chips wrap">/);
  assert.match(css, /\.chips\.wrap \{ flex-wrap: wrap; overflow: visible; \}/);
  assert.doesNotMatch(css, /\.kis-page\[data-page="history"\] \{ max-width: 860px; \}/);
  assert.match(css, /\.toast button \{[^}]*color: var\(--on-bar\)/);
  assert.match(css, /\.pending-flag \{ display: flex; width: 100%;/);
});
