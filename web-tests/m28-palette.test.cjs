// M-28 (Kis 2 palette): every text/control pair on the light and dark tokens passes contrast, the dark primary button is
// #EDF0F6 with dark text (D-4), the switch is ink (not money green) and workplace colours keep their order and names.
const { test } = require('node:test'), assert = require('node:assert/strict'), fs = require('node:fs'), path = require('node:path');
const css = fs.readFileSync(path.join(__dirname, '..', 'web', 'kis', 'kis.css'), 'utf8');
function block(sel) {
  const i = css.indexOf(sel + ' {'); assert.ok(i >= 0, sel);
  const body = css.slice(i, css.indexOf('\n}', i)), out = {};
  for (const m of body.matchAll(/--([\w-]+):\s*([^;]+);/g)) out[m[1]] = m[2].trim();
  return out;
}
const light = block(':root'), dark = { ...light, ...block(':root[data-theme="dark"]') };
const hex = (t, v) => { let x = t[v]; for (let i = 0; i < 4 && /^var\(--/.test(x); i++) x = t[x.slice(6, -1)]; assert.match(x, /^#[0-9A-F]{6}$/i, v + '=' + x); return x; };
const lum = h => { const c = [1, 3, 5].map(i => parseInt(h.slice(i, i + 2), 16) / 255).map(v => v <= 0.03928 ? v / 12.92 : ((v + 0.055) / 1.055) ** 2.4); return 0.2126 * c[0] + 0.7152 * c[1] + 0.0722 * c[2]; };
const ratio = (a, b) => { const [x, y] = [lum(a), lum(b)].sort((p, q) => q - p); return (x + 0.05) / (y + 0.05); };
const TEXT = [['ink', 'bg'], ['ink', 'surface'], ['muted', 'surface'], ['muted', 'bg'], ['ink', 'sunk'], ['paid', 'bg'], ['paid', 'surface'],
  ['pending', 'surface'], ['pending', 'pending-soft'], ['danger', 'surface'], ['danger', 'bg'], ['on-danger', 'danger'], ['accent-ink', 'accent'],
  ['on-paid', 'paid'], ['on-ink', 'ink'], ['control-knob', 'control-on'], ['nav-on-ink', 'nav-on']];
for (const [name, t] of [['light', light], ['dark', dark]]) {
  test(`${name}: text pairs reach 4.5:1`, () => {
    for (const [fg, bg] of TEXT) assert.ok(ratio(hex(t, fg), hex(t, bg)) >= 4.5, `${fg} on ${bg}: ${ratio(hex(t, fg), hex(t, bg)).toFixed(2)}`);
  });
  test(`${name}: control edges (line-strong) reach 3:1`, () => {
    for (const bg of ['bg', 'surface']) assert.ok(ratio(hex(t, 'line-strong'), hex(t, bg)) >= 3, bg);
  });
}
test('dark primary button is #EDF0F6 with dark text (D-4), and the dark prefers-color-scheme block matches', () => {
  assert.equal(hex(dark, 'ink').toUpperCase(), '#EDF0F6');
  assert.equal(hex(dark, 'on-ink').toUpperCase(), '#0F1216');
  assert.match(css, /\.btn\.primary \{ background: var\(--ink\); color: var\(--on-ink\); \}/);
  const media = block(':root:not([data-theme="light"])');
  for (const k of Object.keys(block(':root[data-theme="dark"]'))) assert.equal(media[k], block(':root[data-theme="dark"]')[k], k);
});
test('switch on is ink, never the money green; danger text uses on-danger', () => {
  assert.match(css, /\.switch\[aria-checked="true"\] \{ background: var\(--control-on\)/);
  assert.doesNotMatch(css, /\.switch\[aria-checked="true"\] \{ background: var\(--paid\)/);
  assert.match(css, /\.btn\.danger \{ background: var\(--danger\); color: var\(--on-danger\); \}/);
});
test('workplace colours: same 8 names in the same order, white/ink on each passes, brand is not a workplace colour', () => {
  const src = fs.readFileSync(path.join(__dirname, '..', 'web', 'kis', 'ui.js'), 'utf8');
  const P = [...src.slice(src.indexOf('const PLACES'), src.indexOf('].map(')).matchAll(/\['([^']+)', '(#\w+)', '(#\w+)', '(#\w+)', '(#\w+)', '(#\w+)'\]/g)]
    .map(([, name, from, to, on, accentDark, accentDarkInk]) => ({ name, from, to, on, accentDark, accentDarkInk }));
  assert.deepEqual(P.map(p => p.name), ['אינדיגו', 'ירוק', 'ורד', 'חרדל', 'סגול', 'תכלת', 'טורקיז', 'בורדו']);
  for (const p of P) {
    assert.ok(ratio(p.on, p.to) >= 4.5, p.name + ' on/to');
    assert.ok(ratio(p.accentDarkInk, p.accentDark) >= 4.5, p.name + ' dark');
    assert.notEqual(p.from.toUpperCase(), hex(light, 'accent').toUpperCase());
  }
});
