// M-30 (components, D-3): one "selected" recipe in both themes, 3 button heights (56/48/44, touch targets stay ≥44px),
// 4 radii plus pills, disabled colours instead of opacity, a filled delete button with its own text colour, and SVG icons
// instead of font arrows/checks in the interface.
const { test } = require('node:test'), assert = require('node:assert/strict'), fs = require('node:fs'), path = require('node:path');
const dir = path.join(__dirname, '..', 'web', 'kis'), css = fs.readFileSync(path.join(dir, 'kis.css'), 'utf8');
const block = sel => { const i = css.indexOf(sel + ' {'); assert.ok(i >= 0, sel); return css.slice(i, css.indexOf('\n}', i)); };
test('selected: chips, days, tabs and segments all use the selected-* tokens; dark never paints a solid white tile', () => {
  for (const sel of [':root', ':root[data-theme="dark"]', ':root:not([data-theme="light"])']) {
    const b = block(sel);
    assert.match(b, /--pick-bg: var\(--selected-bg\)/, sel); assert.match(b, /--tab-on: var\(--selected-bg\)/, sel);
  }
  assert.match(block(':root[data-theme="dark"]'), /--selected-bg: #424853; --selected-ink: #EDF0F6; --selected-edge: #EDF0F6;/);
  assert.match(css, /\.chip\[aria-pressed="true"\], \.chip\.on \{ background: var\(--selected-bg\); color: var\(--selected-ink\); border: 2px solid var\(--selected-edge\); \}/);
  assert.match(css, /\.seg button\[aria-pressed="true"\] \{ background: var\(--selected-bg\);[^}]*2px var\(--selected-edge\)/);
});
test('buttons: three heights, none below 44px', () => {
  assert.match(css, /--btn-lg: 56px; --btn-md: 48px; --btn-sm: 44px;/);
  assert.match(css, /\.btn \{ min-height: var\(--btn-lg\)/);
  assert.match(css, /\.btn\.small \{ min-height: var\(--btn-md\)/);
  assert.match(css, /\.btn\.compact \{ min-height: var\(--btn-sm\)/);
});
test('radii: only 8/12/16/24 and pills (hairline bars of 2-3px and circles aside)', () => {
  const used = new Set([...css.matchAll(/border-radius: ([0-9.]+)px/g)].map(m => +m[1]));
  for (const r of used) assert.ok([2, 3, 8, 12, 16, 24, 99].includes(r), 'radius ' + r);
});
test('disabled uses its own colours, not opacity; delete is filled with on-danger text', () => {
  assert.match(css, /\.btn\[disabled\][^{]*\{ background: var\(--disabled-bg\); color: var\(--disabled-ink\);/);
  assert.doesNotMatch(css, /\.btn\[disabled\] \{ opacity/);
  assert.match(css, /\.btn\.danger \{ background: var\(--danger\); color: var\(--on-danger\); \}/);
});
test('icons: no font arrows or check marks drawn as icons in the interface', () => {
  for (const f of ['home.js', 'history.js', 'download.js', 'launch.js', 'summary.js']) {
    const src = fs.readFileSync(path.join(dir, f), 'utf8');
    assert.doesNotMatch(src, /←'|←<|>↓|↓<\/span>|>‹<|>›</, f);
  }
  const ui = fs.readFileSync(path.join(dir, 'ui.js'), 'utf8');
  assert.match(ui, /const sign = \{ ok: ICON\.check, undo: ICON\.undo, error: ICON\.alert \}\[kind\]/);
  for (const k of ['download', 'check', 'undo', 'alert', 'next', 'prev']) assert.match(ui, new RegExp(`\\n  ${k}: '<svg `), k);
});
test('settings: an action in a row is an action chip (chevron), not a filter chip', () => {
  const src = fs.readFileSync(path.join(dir, 'settings.js'), 'utf8');
  // FB23-F1: feedback is the feedback card's own form, so it has no action chip any more.
  for (const a of ['places', 'series', 'recaps', 'android-download']) assert.match(src, new RegExp(`class="chip act" data-a="${a}"`), a);
  assert.match(css, /\.chip\.act::after \{/);
});
