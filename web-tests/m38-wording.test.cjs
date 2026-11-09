// M-38 (D-9, D-12): one neutral voice on screen ("מוסיפים", "בוחרים"), not singular commands ("הזן", "בחר") or plural
// address ("אתם", "סיימתם"); "דוח" without gershayim; "לא שולם", not "ממתין", for unpaid; "לחיצה", not "נגיעה" (the
// web runs on computers too); one date format ("יום ד׳ 4.11", year only when it is not this year). Shared texts and
// recaps (web/v2/share.js, web/v2/summary.js) are covered by the shared fixtures and are not part of this check.
const { test } = require('node:test'), assert = require('node:assert/strict'), fs = require('node:fs'), path = require('node:path');
const web = path.join(__dirname, '..', 'web');
const files = [...fs.readdirSync(path.join(web, 'kis')).filter(f => f.endsWith('.js')).map(f => path.join('kis', f)), path.join('v2', 'rules.js')];
// Code comments are not on screen.
const screenText = f => fs.readFileSync(path.join(web, f), 'utf8').split('\n').filter(l => !/^\s*(\/\/|\*|\/\*)/.test(l)).join('\n');
const NOT = [
  [/דו״ח|דו"ח/, 'דוח without gershayim'],
  [/נגיעה/, '"לחיצה", not "נגיעה"'],
  [/(^|[^א-ת])(הזן|הוסף|בחר|סמן|לחץ|נסה|הקלד) /, 'singular command'],
  [/(^|[^א-ת])(אתם|סיימתם|שלכם|לכם|בקשו|עצרו|בדקו|תמחקו|התקנתם|הורדתם|הרווחתם)([^א-ת]|$)/, 'plural address'],
  [/(^|[^א-ת])(אתה|תרצה|שתנתק|כשתתחבר|שהקלדת|שעבדת)([^א-ת]|$)/, 'singular address'],
  [/'UNPAID', 'ממתין'/, '"לא שולם" for unpaid'],
  [/toLocaleDateString/, 'dates through dayLabel/dayLabelLong ("יום ד׳ 4.11")'],
];
for (const f of files) test(`${f}: one neutral voice and one date format`, () => {
  const src = screenText(f);
  for (const [re, why] of NOT) { const m = src.match(re); assert.equal(m, null, `${why}: …${m && src.slice(Math.max(0, m.index - 30), m.index + 30)}…`); }
});
test('date format (owner feedback 2.3): "יום ד׳ כ״ח חשוון (8/11)", Hebrew first, the civil date beside it', () => {
  const ui = fs.readFileSync(path.join(web, 'kis', 'ui.js'), 'utf8');
  assert.match(ui, /`יום \$\{DAY_SHORT\[d\.getDay\(\)\]\} \$\{root\.KisHebrew\.label\(ms\)\}`/);
  assert.match(ui, /const shortDate = ms => root\.KisHebrew\.label\(ms\);/);
});
