// M-46 (web, smaller items from the 2.3 design audit), on the real page:
//  R-28 Home's pending flag is a full-width row.  R-30 Settings rows explain themselves in a visible line (no info dot per
//  row), feedback is no longer the first category, import is not the one primary button in backup.  R-31 a place's
//  handle is a button and the arrow keys move the place.  R-34 an empty History cannot copy a report of 0 shifts.
//  R-35 History and Home have the same width on a computer.  R-36 on a computer the message stays inside the content
//  column (not over the side bar) and its action is the bar's ink, not the brand colour.
// Screenshots to SCREENSHOT_DIR (default /tmp/m46-shots). Synthetic data only (web-tests/fixtures/synthetic/rich-v2.json).
const assert = require('node:assert/strict'), http = require('node:http'), fs = require('node:fs'), path = require('node:path');
const { chromium } = require(process.env.PLAYWRIGHT_MODULE || 'playwright');
const ROOT = path.resolve('public');
const SHOTS = process.env.SCREENSHOT_DIR || '/tmp/m46-shots'; fs.mkdirSync(SHOTS, { recursive: true });
const rich = JSON.parse(fs.readFileSync('web-tests/fixtures/synthetic/rich-v2.json', 'utf8'));
const types = { '.html': 'text/html; charset=utf-8', '.js': 'text/javascript', '.css': 'text/css', '.json': 'application/json', '.ttf': 'font/ttf' };
const server = http.createServer((q, r) => {
  let p = decodeURIComponent(new URL(q.url, 'http://x').pathname); if (p === '/') p = '/index.html';
  if (p === '/api/config') { r.writeHead(200, { 'content-type': 'application/json' }); return r.end('{}'); }
  const f = path.join(ROOT, p); if (!f.startsWith(ROOT) || !fs.existsSync(f) || fs.statSync(f).isDirectory()) { r.writeHead(404); return r.end(); }
  r.writeHead(200, { 'content-type': types[path.extname(f)] || 'application/octet-stream', 'cache-control': 'no-store' }); fs.createReadStream(f).pipe(r);
});
(async () => {
 await new Promise(r => server.listen(0, '127.0.0.1', r));
 const browser = await chromium.launch({ executablePath: process.env.CHROMIUM_PATH || undefined, args: ['--no-sandbox', '--disable-dev-shm-usage', '--disable-gpu'] });
 const open = async viewport => {
  const ctx = await browser.newContext({ serviceWorkers: 'block', viewport, locale: 'he-IL' });
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
  return { ctx, page, errors };
 };
 const box = (page, sel) => page.locator(sel).first().evaluate(x => { const r = x.getBoundingClientRect(); return { left: r.left, right: r.right, width: r.width }; });
 try {
  // ---- a phone ----
  const { ctx, page, errors } = await open({ width: 390, height: 844 });
  const flag = await box(page, '[data-testid=pending-flag]'), hero = await box(page, '[data-testid=home-hero]');
  assert.ok(Math.abs(flag.width - hero.width) <= 2, `pending flag is full width (${flag.width} vs ${hero.width})`);
  await page.screenshot({ path: path.join(SHOTS, 'm46-home-phone.png'), fullPage: true });
  await page.click('[data-testid=tab-settings]');
  const order = await page.locator('.set-head b').allInnerTexts();
  assert.notEqual(order[0], 'משוב ותקלות', 'feedback is not the first category');
  assert.equal(order[order.length - 1], 'חשבון');
  await page.click('[data-testid="settings-open-עבודה"]');
  assert.equal(await page.locator('.set-group.on .row .info-dot').count(), 0, 'no info dot on a row');
  assert.ok(await page.locator('.set-group.on .row .caption').count() >= 3, 'rows explain themselves in a visible line');
  await page.screenshot({ path: path.join(SHOTS, 'm46-settings-work.png'), fullPage: true });
  // Places: the handle is a button and the arrow keys move a place.
  await page.click('[data-testid=places-button]'); await page.waitForSelector('[data-testid=places-panel]');
  const names = () => page.locator('[data-testid=places-panel] [data-drag]').evaluateAll(l => l.map(x => x.dataset.drag));
  const before = await names();
  const grip = page.locator(`[data-testid="place-drag-${before[0]}"]`);
  assert.equal(await grip.evaluate(x => x.tagName), 'BUTTON');
  await grip.focus(); await page.keyboard.press('ArrowDown');
  const after = await names();
  assert.deepEqual(after.slice(0, 2), [before[1], before[0]], 'ArrowDown moves the place one down');
  assert.equal(await page.evaluate(() => document.activeElement?.dataset.move), before[0], 'focus stays on the moved place');
  await page.keyboard.press('ArrowUp');
  assert.deepEqual(await names(), before, 'ArrowUp moves it back');
  await page.screenshot({ path: path.join(SHOTS, 'm46-places.png') });
  await page.keyboard.press('Escape');
  await page.click('[data-testid="settings-open-גיבוי ונתונים"]');
  assert.equal(await page.locator('[data-testid=import-button]').evaluate(x => x.classList.contains('primary')), false, 'import is not the primary action');
  const dl = await page.locator('[data-testid=backup-download]').evaluate(x => x.getBoundingClientRect().height);
  assert.ok(dl <= 50, 'backup download stays on one line');
  // History: an empty report cannot be copied.
  await page.click('[data-testid=tab-history]');
  await page.click('[data-testid=history-search]');
  await page.fill('input[type=search]', 'zzzz-nothing');
  await page.waitForTimeout(400);
  assert.equal(await page.locator('[data-testid=history-copy]').isDisabled(), true, 'copy is disabled for 0 shifts');
  assert.deepEqual(errors, []);
  await ctx.close();
  // ---- a computer ----
  const d = await open({ width: 1440, height: 900 });
  const homeW = (await box(d.page, '.kis-page')).width;
  await d.page.click('[data-testid=tab-history]'); await d.page.waitForTimeout(300);
  const histW = (await box(d.page, '.kis-page')).width;
  assert.ok(Math.abs(homeW - histW) <= 2, `same width on a computer (home ${homeW}, history ${histW})`);
  await d.page.evaluate(() => window.KisUi.toast('נמחקה', 'ביטול', () => {}));
  const t = await box(d.page, '#toast > div'), side = await box(d.page, '.nav');
  assert.ok(t.right <= side.left + 1, `message stays out of the side bar (toast right ${t.right}, side bar left ${side.left})`);
  const colors = await d.page.evaluate(() => ({ action: getComputedStyle(document.querySelector('#toast button')).color, ink: getComputedStyle(document.querySelector('#toast > div')).color }));
  assert.equal(colors.action, colors.ink, 'undo is the bar ink, not the brand');
  await d.page.screenshot({ path: path.join(SHOTS, 'm46-computer-toast.png') });
  assert.deepEqual(d.errors, []);
  await d.ctx.close();
  console.log(`m46-ui: full-width flag, settings lines/order, keyboard place order, empty copy, one width, message placement: PASS (screenshots in ${SHOTS})`);
 } finally { await browser.close(); server.close(); }
})().catch(e => { console.error(e); process.exit(1); });
