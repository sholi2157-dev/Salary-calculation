// FB23-F5 (web): R-33 is reverted at the owner's request. A group shift is the "קבוצה" button at the top-left of the editor
// header again (the side where an edit has "מחיקה"), not a switch under the tabs. File name kept so the CI step still runs it.
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
 const pressedTeam = page => page.getAttribute(tid('editor-team'), 'aria-pressed');
 const title = page => page.textContent('.editor .top-row h1');
 const pressed = page => page.$$eval('.editor [role=tab]', b => b.filter(x => x.getAttribute('aria-selected') === 'true').map(x => x.dataset.mode));
 try {
  for (const colorScheme of ['light', 'dark']) for (const viewport of [{ width: 390, height: 844 }, { width: 320, height: 700 }]) {
   const { ctx, page, errors } = await open(viewport, colorScheme);
   await page.click(tid('add-shift')); await page.waitForSelector(tid('save-shift'));
   // The header: "ביטול", the title, then "קבוצה" at the far (left, in RTL) end; no switch under the tabs.
   const top = await page.$$eval('.editor .top-row button', b => b.map(x => x.textContent.trim()));
   assert.deepEqual(top, ['ביטול', 'קבוצה'], 'header buttons: ' + JSON.stringify(top));
   const side = await page.evaluate(() => { const t = document.querySelector('[data-testid=editor-team]').getBoundingClientRect(), c = document.querySelector('[data-testid=editor-close]').getBoundingClientRect();
     return t.right <= c.left; });
   assert.ok(side, '"קבוצה" is on the left of "ביטול" (RTL)');
   assert.equal(await page.locator(tid('team-card')).count(), 0);
   assert.equal(await page.locator('.editor [role=switch][data-a=team]').count(), 0);
   assert.equal(await pressedTeam(page), 'false');
   assert.equal(await title(page), 'משמרת חדשה');
   // On in "ידני": the same tab, the people right below, the page framed.
   await page.click(tid('mode-hours'));
   await page.click(tid('editor-team'));
   assert.equal(await pressedTeam(page), 'true');
   assert.equal(await title(page), 'משמרת קבוצתית');
   assert.deepEqual(await pressed(page), ['HOURS']);
   assert.ok(await page.isVisible(tid('group-block')));
   assert.ok(await page.evaluate(() => document.querySelector('.editor').classList.contains('team')));
   await page.screenshot({ path: path.join(SHOTS, `f5-group-on-${colorScheme}-${viewport.width}.png`), fullPage: true });
   // Off again: back to the user's own shift, same tab, people dropped.
   await page.click(tid('editor-team'));
   assert.equal(await pressedTeam(page), 'false');
   assert.equal(await title(page), 'משמרת חדשה');
   assert.deepEqual(await pressed(page), ['HOURS']);
   assert.equal(await page.locator(tid('group-block')).count(), 0);
   // A job with a team: the same button.
   await page.click(tid('mode-job')); await page.click(tid('editor-team'));
   assert.ok(await page.isVisible(tid('job-team-block')));
   await page.click(tid('editor-team'));
   const width = await page.evaluate(() => document.documentElement.scrollWidth); assert.ok(width <= viewport.width, 'no horizontal scroll: ' + width);
   await page.click(tid('editor-close')); if (await page.isVisible(tid('confirm-ok'))) await page.click(tid('confirm-ok'));
   // An edit keeps "מחיקה" in that place and has no group button (a stored shift does not change kind here).
   await page.click(tid('tab-history')); await page.click('button[data-id]'); await page.click(tid('detail-edit'));
   await page.waitForSelector(tid('editor-delete'));
   assert.equal(await page.locator(tid('editor-team')).count(), 0);
   assert.deepEqual(errors, []);
   await ctx.close();
  }
  console.log('PASS: FB23-F5: the group shift is the header "קבוצה" button again (light/dark, 390/320px); tab kept; edits unchanged.');
 } finally { await browser.close(); server.close(); }
})().catch(e => { console.error(e); process.exit(1); });
