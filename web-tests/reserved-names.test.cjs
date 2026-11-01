// 2.3.0 (audit CW-11): a place may be called anything, including names that exist on every JavaScript object.
const { test } = require('node:test'), assert = require('node:assert/strict');
const S = require('../web/v2/summary.js');
const NAMES = ['__proto__', 'constructor', 'toString', 'hasOwnProperty', 'valueOf'];
const row = (category, date, hours) => ({ date, start: null, end: null, hours, kind: 'shift', payKnown: true, total: hours * 50, currency: '₪', paid: false, category });

test('monthly summary counts places with reserved names like any other place', () => {
  const rows = NAMES.flatMap((n, i) => [row(n, '2026-03-0' + (i + 1), 2), row(n, '2026-03-1' + i, 1)]);
  const s = S.summary(rows, '2026-03', '2026-03-31');
  assert.equal(s.hours, 15);
  assert.ok(Number.isFinite(s.hours));
  const places = (s.places || []).map(p => p.name).sort();
  assert.deepEqual(places, [...NAMES].sort());
  for (const p of s.places) assert.equal(p.hours, 3, p.name);
});

