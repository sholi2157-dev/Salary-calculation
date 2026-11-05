// R-33 (web): a night shift on the editor's ruler is one span. A shift that crosses midnight inside noon→noon is drawn on a
// noon-to-noon window (12 18 00 06 12), every other shift keeps 00→24. Only the drawing changes; the times are the same.
const { test } = require('node:test'), assert = require('node:assert/strict');
globalThis.KisRules = require('../web/v2/rules.js'); globalThis.KisPay = require('../web/v2/pay.js');
require('../web/kis/editor.js');
const { rulerLayout } = globalThis.KisEditor;
const labels = L => L.ticks.map(t => t.label).join(' ');
test('20:30–00:30 is one span on a noon-to-noon ruler', () => {
  const L = rulerLayout('20:30', '00:30');
  assert.equal(L.origin, 720);
  assert.deepEqual(L.spans, [[510, 750]]);
  assert.equal(labels(L), '12 18 00 06 12');
  assert.equal(L.at(0), 720, 'midnight sits in the middle');
});
test('22:00–06:00 and 12:00–11:45 are one span too', () => {
  assert.deepEqual(rulerLayout('22:00', '06:00').spans, [[600, 1080]]);
  assert.deepEqual(rulerLayout('12:00', '11:45').spans, [[0, 1425]]);
});
test('a day shift keeps the 00→24 ruler, unchanged', () => {
  const L = rulerLayout('09:00', '17:30');
  assert.equal(L.origin, 0);
  assert.deepEqual(L.spans, [[540, 1050]]);
  assert.equal(labels(L), '00 06 12 18 24');
});
test('a crossing that does not fit noon→noon keeps the old two pieces (nothing hidden)', () => {
  assert.equal(rulerLayout('08:00', '02:00').origin, 0);
  assert.deepEqual(rulerLayout('08:00', '02:00').spans, [[480, 1440], [0, 120]]);
  assert.equal(rulerLayout('22:00', '13:00').origin, 0);
  assert.deepEqual(rulerLayout('22:00', '22:00').spans, [[1320, 1440], [0, 1320]], 'start = end is still a full day');
});
test('no start time: the end handle alone on the 00→24 ruler', () => {
  const L = rulerLayout(null, '02:00');
  assert.equal(L.origin, 0); assert.deepEqual(L.spans, []);
});
test('a drag on the noon-to-noon ruler gives the real time back', () => {
  const L = rulerLayout('20:30', '00:30');
  assert.equal(L.fromWindow(0), 720); assert.equal(L.fromWindow(720), 0); assert.equal(L.fromWindow(1425), 705);
  for (const m of [0, 30, 510, 750, 1425]) assert.equal(L.at(L.fromWindow(m)), m);
});
