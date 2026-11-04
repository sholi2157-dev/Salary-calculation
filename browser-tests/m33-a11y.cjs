// M-33 (web accessibility): focus stays in the top dialog, a redraw keeps the focused control, pages have headings, and
// axe-core finds no serious/critical violation (colour contrast is reported, not gated: the palette is the owner's open
// decision M-28). Synthetic data only. AXE_PATH (or require.resolve('axe-core')) points to axe.min.js; without it the axe
// part is skipped and says so.
const assert = require('node:assert/strict'), http = require('node:http'), fs = require('node:fs'), path = require('node:path');
const { chromium } = require(process.env.PLAYWRIGHT_MODULE || 'playwright');
const ROOT = path.resolve('public');
const rich = JSON.parse(fs.readFileSync('web-tests/fixtures/synthetic/rich-v2.json', 'utf8'));
const types = { '.html': 'text/html; charset=utf-8', '.js': 'text/javascript', '.css': 'text/css', '.json': 'application/json', '.ttf': 'font/ttf' };
const server = http.createServer((q, r) => {
  let p = decodeURIComponent(new URL(q.url, 'http://x').pathname); if (p === '/') p = '/index.html';
  if (p === '/api/config') { r.writeHead(200, { 'content-type': 'application/json' }); return r.end('{}'); }
  const f = path.join(ROOT, p); if (!f.startsWith(ROOT) || !fs.existsSync(f) || fs.statSync(f).isDirectory()) { r.writeHead(404); return r.end(); }
  r.writeHead(200, { 'content-type': types[path.extname(f)] || 'application/octet-stream', 'cache-control': 'no-store' }); fs.createReadStream(f).pipe(r);
});
let axePath = process.env.AXE_PATH; if (!axePath) try { axePath = require.resolve('axe-core/axe.min.js'); } catch {}
(async () => {
 await new Promise(r => server.listen(0, '127.0.0.1', r));
 const browser = await chromium.launch({ executablePath: process.env.CHROMIUM_PATH || undefined, args: ['--no-sandbox', '--disable-dev-shm-usage', '--disable-gpu'] });
 try {
  const ctx = await browser.newContext({ serviceWorkers: 'block', viewport: { width: 390, height: 844 }, locale: 'he-IL' });
  await ctx.route('**/*', route => new URL(route.request().url()).hostname === '127.0.0.1' ? route.continue() : route.abort());
  await ctx.addInitScript(data => {
   if (sessionStorage.getItem('seeded')) return; sessionStorage.setItem('seeded', 'yes');
   for (const [key, value] of Object.entries(data)) localStorage.setItem(key, JSON.stringify(value));
   localStorage.setItem('kis_web_v1', JSON.stringify({ launched: 2, tourDone: true }));
   localStorage.setItem('kis_ai_invited_v1', '1'); localStorage.setItem('kis_signin_skip_v1', '1');
  }, rich);
  const page = await ctx.newPage(), errors = [];
  page.on('pageerror', e => errors.push(e.message));
  await page.goto(`http://127.0.0.1:${server.address().port}`);
  await page.waitForSelector('[data-testid=home-hero]');
  const inside = sel => page.evaluate(s => { const d = document.querySelector(s), a = document.activeElement; return !!d && (d.contains(a) || !!a?.closest('#toast')); }, sel);

  // 1. A sheet: Tab and Shift+Tab never leave it.
  await page.click('[data-testid=home-bell]');
  const sheet = await page.waitForSelector('.sheet[aria-modal=true]');
  const sheetId = await sheet.getAttribute('data-testid');
  for (let i = 0; i < 25; i++) { await page.keyboard.press('Tab'); assert.ok(await inside(`[data-testid="${sheetId}"]`), `Tab ${i + 1} left the sheet`); }
  for (let i = 0; i < 25; i++) { await page.keyboard.press('Shift+Tab'); assert.ok(await inside(`[data-testid="${sheetId}"]`), `Shift+Tab ${i + 1} left the sheet`); }
  // Focus pushed behind the sheet comes back into it.
  await page.evaluate(() => document.querySelector('[data-testid=tab-history]').focus());
  assert.ok(await inside(`[data-testid="${sheetId}"]`), 'focus behind the sheet returns to it');
  await page.keyboard.press('Escape'); await page.waitForSelector('.sheet', { state: 'detached' });

  // 2. The editor (a full-screen dialog): Tab stays inside it.
  await page.click('[data-testid=add-shift]');
  await page.waitForSelector('[data-testid=add-page]');
  for (let i = 0; i < 40; i++) { await page.keyboard.press('Tab'); assert.ok(await inside('[data-testid=add-page]'), `editor Tab ${i + 1} left the editor`); }
  await page.keyboard.press('Escape'); await page.waitForTimeout(150);
  if (await page.$('[data-testid=add-page]')) { const c = await page.$('[data-testid=confirm-ok]'); if (c) await c.click(); }
  await page.waitForSelector('[data-testid=add-page]', { state: 'detached' });

  // 3. A redraw keeps the focused control (settings theme buttons redraw the page).
  await page.evaluate(() => Kis.go('settings', { section: 'מראה ותחושה' })); await page.waitForSelector('[data-testid=theme-dark]');
  await page.focus('[data-testid=theme-dark]'); await page.keyboard.press('Enter'); await page.waitForTimeout(100);
  assert.equal(await page.evaluate(() => document.activeElement?.dataset.testid), 'theme-dark', 'focus kept on the theme button after redraw');
  await page.focus('[data-testid=theme-light]'); await page.keyboard.press('Enter'); await page.waitForTimeout(100);
  assert.equal(await page.evaluate(() => document.activeElement?.dataset.testid), 'theme-light');
  await page.focus('[data-testid=theme-system]'); await page.keyboard.press('Enter');
  await page.evaluate(() => Kis.refresh());
  assert.equal(await page.evaluate(() => document.activeElement?.dataset.testid), 'theme-system', 'Kis.refresh() keeps the focus too');

  // 4. Headings: every page has one h1; the history month is a heading that announces changes.
  for (const tab of ['tab-home', 'tab-history', 'tab-settings']) {
   await page.click(`[data-testid=${tab}]`); await page.waitForTimeout(100);
   assert.equal(await page.evaluate(() => document.querySelectorAll('#page h1').length), 1, tab + ' has one h1');
  }
  await page.click('[data-testid=tab-history]');
  const month = await page.$('[data-testid=history-month]');
  if (month) { assert.equal(await month.evaluate(e => e.tagName), 'H2'); assert.equal(await month.getAttribute('aria-live'), 'polite'); }
  assert.equal(await page.evaluate(() => document.querySelector('[data-testid=history-total]')?.tagName), 'SECTION');

  // 5. Fonts follow the browser's text-size setting (rem): at a 32px default the body text doubles.
  const base = await page.evaluate(() => parseFloat(getComputedStyle(document.querySelector('.caption') || document.body).fontSize));
  await page.evaluate(() => { document.documentElement.style.fontSize = '200%'; });
  const big = await page.evaluate(() => parseFloat(getComputedStyle(document.querySelector('.caption') || document.body).fontSize));
  assert.ok(big >= base * 1.9, `text scales with the root size (${base} → ${big})`);
  for (const tab of ['tab-home', 'tab-history', 'tab-settings']) {
   await page.click(`[data-testid=${tab}]`); await page.waitForTimeout(150);
   const w = await page.evaluate(() => ({ s: document.documentElement.scrollWidth, v: innerWidth }));
   assert.ok(w.s <= w.v + 1, `${tab} at 200% text does not scroll sideways (${w.s} > ${w.v})`);
  }
  await page.evaluate(() => { document.documentElement.style.fontSize = ''; });

  // 6. axe-core on the three pages and an open sheet.
  if (!axePath) console.log('m33-a11y: axe-core not found (set AXE_PATH) — axe check skipped');
  else {
   const report = [];
   const scan = async name => {
    await page.addScriptTag({ path: axePath });
    const r = await page.evaluate(async () => (await axe.run(document, { runOnly: { type: 'tag', values: ['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa'] } }))
     .violations.map(v => ({ id: v.id, impact: v.impact, n: v.nodes.length, sample: v.nodes[0]?.target?.join(' ') })));
    for (const v of r) report.push({ page: name, ...v });
   };
   for (const tab of ['tab-home', 'tab-history', 'tab-settings']) { await page.click(`[data-testid=${tab}]`); await page.waitForTimeout(150); await scan(tab); }
   await page.click('[data-testid=tab-home]'); await page.click('[data-testid=home-bell]'); await page.waitForSelector('.sheet'); await scan('notifications-sheet');
   const gated = report.filter(v => ['serious', 'critical'].includes(v.impact) && v.id !== 'color-contrast');
   for (const v of report) console.log(`m33-a11y axe: ${v.page} ${v.impact} ${v.id} ×${v.n} (${v.sample})`);
   assert.deepEqual(gated, [], 'no serious/critical axe violations (colour contrast reported separately)');
  }
  assert.deepEqual(errors, []);
  console.log('m33-a11y: focus trap (sheet, editor), focus kept across redraw, headings, rem text scaling' + (axePath ? ', axe' : '') + ' passed');
 } finally { await browser.close(); server.close(); }
})().catch(e => { console.error(e); process.exit(1); });
