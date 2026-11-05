// R-33 (web): the editor's header "קבוצה" button read as an action (an edit has "מחיקה" in that place). A group shift is
// now a "משמרת קבוצתית" switch (the editor's "חוזרת כל שבוע" switch) on its own line under the tabs, in view without scrolling.
// Screenshots to SCREENSHOT_DIR (default /tmp/r33-group-shots). Synthetic data only (web-tests/fixtures/synthetic/rich-v2.json).
const assert = require('node:assert/strict'), http = require('node:http'), fs = require('node:fs'), path = require('node:path');
const { chromium } = require(process.env.PLAYWRIGHT_MODULE || 'playwright');
const ROOT = path.resolve('public');
const SHOTS = process.env.SCREENSHOT_DIR || '/tmp/r33-group-shots'; fs.mkdirSync(SHOTS, { recursive: true });
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
 const open = async (viewport, colorScheme) => {
  const ctx = await browser.newContext({ serviceWorkers: 'block', viewport, colorScheme, locale: 'he-IL' });
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


 const tid = t => `[data-testid="${t}"]`;
 const sw = page => page.getAttribute(tid('editor-team'), 'aria-checked');
 const title = page => page.textContent('.editor .top-row h1');
 const pressed = page => page.$$eval('.editor [role=tab]', b => b.filter(x => x.getAttribute('aria-selected') === 'true').map(x => x.dataset.mode));
 try {
  for (const colorScheme of ['light', 'dark']) for (const viewport of [{ width: 390, height: 844 }, { width: 320, height: 700 }]) {
   const { ctx, page, errors } = await open(viewport, colorScheme);
   await page.click(tid('add-shift')); await page.waitForSelector(tid('save-shift'));
   // The header has only "ביטול" and the title; nothing there reads "קבוצה".
   const top = await page.$$eval('.editor .top-row button', b => b.map(x => x.textContent.trim()));
   assert.deepEqual(top, ['ביטול'], 'header buttons: ' + JSON.stringify(top));
   // The title stays in the middle (an invisible twin of "ביטול" balances the other side).
   const c = await page.evaluate(() => { const r = document.querySelector('.editor .top-row').getBoundingClientRect(), h = document.querySelector('.editor .top-row h1').getBoundingClientRect();
     return Math.abs((r.left + r.right) / 2 - (h.left + h.right) / 2); });
   assert.ok(c <= 1.5, 'title centred, off by ' + c);
   // The switch: one line under the tabs, its own label, off, in view without scrolling.
   const pos = await page.evaluate(() => { const t = document.querySelector('.editor .modes').getBoundingClientRect(), s = document.querySelector('[data-testid=team-card]').getBoundingClientRect();
     return { below: s.top >= t.bottom - 1, near: s.top - t.bottom < 24, inView: s.bottom <= innerHeight }; });
   assert.deepEqual(pos, { below: true, near: true, inView: true });
   assert.equal(await sw(page), 'false');
   assert.equal(await page.getAttribute(tid('editor-team'), 'role'), 'switch');
   assert.match(await page.textContent(tid('team-card')), /משמרת קבוצתית/);
   assert.equal(await title(page), 'משמרת חדשה');
   // On in "ידני": the same tab, the people right below, the page framed.
   await page.click(tid('mode-hours'));
   await page.click(tid('editor-team'));
   assert.equal(await sw(page), 'true');
   assert.equal(await title(page), 'משמרת קבוצתית');
   assert.deepEqual(await pressed(page), ['HOURS']);
   assert.ok(await page.isVisible(tid('group-block')));
   assert.ok(await page.evaluate(() => document.querySelector('.editor').classList.contains('team')));
   await page.screenshot({ path: path.join(SHOTS, `r33-group-on-${colorScheme}-${viewport.width}.png`), fullPage: true });
   // A keyboard turns it off too; back to the user's own shift, same tab.
   await page.focus(tid('editor-team')); await page.keyboard.press('Space');
   assert.equal(await sw(page), 'false');
   assert.equal(await title(page), 'משמרת חדשה');
   assert.deepEqual(await pressed(page), ['HOURS']);
   assert.equal(await page.locator(tid('group-block')).count(), 0);
   // A job with a team: the same switch.
   await page.click(tid('mode-job')); await page.click(tid('editor-team'));
   assert.ok(await page.isVisible(tid('job-team-block')));
   await page.click(tid('editor-team'));
   const width = await page.evaluate(() => document.documentElement.scrollWidth); assert.ok(width <= viewport.width, 'no horizontal scroll: ' + width);
   await page.click(tid('editor-close')); if (await page.isVisible(tid('confirm-ok'))) await page.click(tid('confirm-ok'));
   // An edit keeps "מחיקה" in the header and has no switch (as before: a stored shift does not change kind here).
   await page.click(tid('tab-history')); await page.click('button[data-id]'); await page.click(tid('detail-edit'));
   await page.waitForSelector(tid('editor-delete'));
   assert.equal(await page.locator(tid('editor-team')).count(), 0);
   assert.deepEqual(errors, []);
   await ctx.close();
  }
  console.log('PASS: R-33 group switch: "משמרת קבוצתית" switch replaces the header "קבוצה" button (light/dark, 390/320px); tab kept; keyboard; edits unchanged.');
 } finally { await browser.close(); server.close(); }
})().catch(e => { console.error(e); process.exit(1); });
