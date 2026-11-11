// 2.4 task 05: the site can be added to the home screen (iPhone + Android browsers) with its own icon, name and shortcuts.
const test = require('node:test'), assert = require('node:assert/strict'), fs = require('node:fs');
const manifest = JSON.parse(fs.readFileSync('web/manifest.webmanifest', 'utf8'));
const png = f => { const b = fs.readFileSync(f); assert.equal(b.toString('latin1', 1, 4), 'PNG', f); return [b.readUInt32BE(16), b.readUInt32BE(20)]; };
const local = src => (assert.match(src, /^\/web\/icons\/[\w-]+\.png$/), src.slice(1));

test('manifest: Hebrew RTL name, standalone, same-origin scope, smoke-grey colours', () => {
  assert.equal(manifest.name, 'כיס'); assert.equal(manifest.short_name, 'כיס');
  assert.equal(manifest.lang, 'he'); assert.equal(manifest.dir, 'rtl');
  assert.equal(manifest.display, 'standalone'); assert.equal(manifest.start_url, '/'); assert.equal(manifest.scope, '/'); assert.equal(manifest.id, '/');
  const css = fs.readFileSync('web/kis/kis.css', 'utf8');
  assert.ok(css.includes(`--bg: ${manifest.background_color};`), 'background is the light page colour');
  assert.equal(manifest.theme_color, manifest.background_color);
});

test('manifest icons exist at their declared sizes, incl. 192/512 "any" and a 512 maskable', () => {
  for (const i of manifest.icons) { const [w, h] = png(local(i.src)); assert.equal(`${w}x${h}`, i.sizes, i.src); assert.equal(i.type, 'image/png'); }
  for (const s of ['192x192', '512x512']) assert.ok(manifest.icons.some(i => i.sizes === s && i.purpose === 'any'), s);
  assert.ok(manifest.icons.some(i => i.sizes === '512x512' && i.purpose === 'maskable'));
  assert.deepEqual(png('web/icons/apple-touch-icon.png'), [180, 180]);
});

test('shortcuts mirror Android (start a shift, add a shift) and the app handles exactly those actions', () => {
  const app = fs.readFileSync('web/kis/app.js', 'utf8');
  const handled = JSON.parse(app.match(/const SHORTCUTS = (\[[^\]]*\])/)[1].replace(/'/g, '"'));
  assert.deepEqual(manifest.shortcuts.map(s => s.url), handled.map(a => '/?action=' + a));
  assert.deepEqual(manifest.shortcuts.map(s => s.name), ['התחלת משמרת', 'הוספת משמרת']);
  for (const s of manifest.shortcuts) for (const i of s.icons) png(local(i.src));
});

test('index.html links the manifest and the apple-touch icon; the build copies them with the shell', () => {
  const html = fs.readFileSync('index.html', 'utf8');
  assert.match(html, /<link rel="manifest" href="web\/manifest\.webmanifest">/);
  assert.match(html, /<link rel="apple-touch-icon" href="web\/icons\/apple-touch-icon\.png">/);
  assert.match(html, /viewport-fit=cover/);
  // Everything under web/ is part of the build digest, so a changed icon or manifest makes a new service-worker cache.
  assert.match(fs.readFileSync('scripts/build-web.cjs', 'utf8'), /hashTree\('web'\)/);
});
