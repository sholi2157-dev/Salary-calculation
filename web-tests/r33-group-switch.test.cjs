// R-33 (web): "קבוצה" in the editor's header read as an action (an edit has "מחיקה" in the same place). A group shift is
// now turned on and off with a "משמרת קבוצתית" switch on its own line under the tabs. Same draft rules as before.
const { test } = require('node:test'), assert = require('node:assert/strict');
globalThis.KisRules = require('../web/v2/rules.js'); globalThis.KisPay = require('../web/v2/pay.js');
globalThis.KisUi = { infoDot: (title, text) => `<i data-info-title="${title}" data-info="${text}"></i>` };
require('../web/kis/editor.js');
const { teamSwitch, withTeam, tabOf } = globalThis.KisEditor;
const draft = () => ({ id: null, mode: 'CLOCK', category: 'מקום', start: '09:00', end: '13:00', breakMin: 0, hours: '', rate: '50', rateUnknown: false,
  currency: '₪', bonus: '', bonusTarget: 'ME', amount: '', amountUnknown: false, notes: '', workers: [], separateRates: false, employerRate: '', workerRate: '',
  isPaid: false, repeat: false, repeatDays: null, repeatUntil: null, passThrough: '', teamSameRate: true, team: false });

test('the switch is a labelled switch, off for a shift of the user\'s own', () => {
  const html = teamSwitch({ team: false });
  assert.match(html, /role="switch"/);
  assert.match(html, /aria-checked="false"/);
  assert.match(html, /aria-label="משמרת קבוצתית"/);
  assert.match(html, /data-a="team"/);
  assert.match(html, /data-testid="editor-team"/);
  assert.match(html, /<b class="label">משמרת קבוצתית<\/b>/);
  assert.match(html, /data-info="רושמים גם את מי שעבד איתך\."/);
  assert.doesNotMatch(html, />קבוצה</, 'no bare "קבוצה" label');
});
test('on for a group shift', () => {
  assert.match(teamSwitch({ team: true }), /aria-checked="true"/);
});
test('the switch drives the same draft rule: tab kept on, people dropped off', () => {
  const d0 = draft();
  const on = withTeam(d0, true);
  assert.equal(on.team, true);
  assert.equal(tabOf(on), tabOf(d0), 'the tab is kept');
  const off = withTeam({ ...on, workers: [{ name: 'סינתטי', hours: '4' }] }, false);
  assert.equal(off.team, false);
  assert.deepEqual(off.workers, []);
  assert.equal(tabOf(off), tabOf(d0));
  assert.equal(withTeam(on, true), on, 'turning on what is on changes nothing');
});
