// M-46 (web): content that appeared twice on a screen appears once, and the control that stays still works.
//  1. Home: a future shift in "הבא בתור" is not repeated under "אחרונות"; "אחרונות" still shows 3 cards and opens a shift.
//  2. History: a search with no result shows the empty message without a "0 משמרות · —" total card above it; clearing
//     the search brings the total back.
//  3. A computer: the side bar has the app's logo and name, so Home's header and the Settings footer don't repeat them
//     (Home's name stays for screen readers); a phone, with no side bar, keeps both.
// Synthetic data only (web-tests/fixtures/synthetic/rich-v2.json plus one synthetic future shift).
const assert = require('node:assert/strict'), http = require('node:http'), fs = require('node:fs'), path = require('node:path');
const { chromium } = require(process.env.PLAYWRIGHT_MODULE || 'playwright');
const ROOT = path.resolve('public');
const rich = JSON.parse(fs.readFileSync('web-tests/fixtures/synthetic/rich-v2.json', 'utf8'));
const now = Date.now(), day = 864e5, base = rich.work_complete_backup.entries[0];
// Two days ahead and added just now: the newest record, so before the fix it led "אחרונות" as well as "הבא בתור".
rich.work_complete_backup.entries.push({ ...base, id: 'm46-future', date: new Date(new Date(now + 2 * day).toDateString()).getTime(), createdAt: now,
  startTime: '09:00', endTime: '13:00', hours: 4, totalEarnings: 200, notes: '' });
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
 const shown = (page, sel) => page.locator(sel).evaluate(x => { const r = x.getBoundingClientRect(); return r.width > 1 && r.height > 1 && getComputedStyle(x).display !== 'none'; });
 try {
  // ---- a phone ----
  const { ctx, page, errors } = await open({ width: 390, height: 844 });
  // 1. Home: once, in "הבא בתור".
  assert.equal(await page.locator('[data-testid=next-up] [data-id="m46-future"]').count(), 1, 'the future shift is in "הבא בתור"');
  assert.equal(await page.locator('[data-testid=recent] [data-id="m46-future"]').count(), 0, 'the future shift is repeated under "אחרונות"');
  assert.equal(await page.locator('[data-id="m46-future"]').count(), 1, 'the future shift appears more than once on Home');
  const recent = page.locator('[data-testid=recent] .shift[data-id]');
  assert.equal(await recent.count(), 3, '"אחרונות" still shows three shifts');
  const ids = await recent.evaluateAll(l => l.map(x => x.dataset.id));
  const nextIds = await page.locator('[data-testid=next-up] .shift[data-id]').evaluateAll(l => l.map(x => x.dataset.id));
  assert.deepEqual(ids.filter(id => nextIds.includes(id)), [], 'a shift is in both lists');
  // The remaining controls still open their shift.
  await recent.first().click();
  await page.waitForSelector('[data-testid=shift-detail]'); await page.keyboard.press('Escape'); await page.waitForSelector('[data-testid=shift-detail]', { state: 'detached' });
  await page.click('[data-testid=next-up] [data-id="m46-future"]');
  await page.waitForSelector('[data-testid=shift-detail]'); await page.keyboard.press('Escape'); await page.waitForSelector('[data-testid=shift-detail]', { state: 'detached' });
  // 3 (phone). No side bar: Home's header keeps the logo and the app's name, Settings keeps its footer.
  assert.ok(await shown(page, '.hello > .logo'), 'Home logo hidden on a phone');
  assert.ok(await shown(page, '.hello .caption + .label'), 'Home app name hidden on a phone');

  // 2. History: an empty search says "nothing" once.
  await page.click('[data-testid=tab-history]');
  assert.ok(await page.isVisible('[data-testid=history-total]'), 'the total shows with shifts');
  await page.click('[data-testid=history-search]'); await page.fill('[data-testid=history-search-input]', 'zz-no-such-shift');
  await page.waitForSelector('#page .empty');
  assert.match(await page.textContent('#page .empty'), /אין משמרות להצגה/);
  assert.equal(await page.locator('[data-testid=history-total]').count(), 0, 'the "0 משמרות · —" card shows above the empty message');
  // The search's own clear still works, and the total comes back.
  await page.click('[data-testid=history-search-clear]');
  await page.waitForSelector('[data-testid=history-total]');
  assert.match(await page.textContent('[data-testid=history-total]'), /משמרות/);
  assert.equal(await page.inputValue('[data-testid=history-search-input]'), '');

  await page.click('[data-testid=tab-settings]');
  assert.ok(await shown(page, '.set-brand'), 'Settings footer hidden on a phone');
  assert.deepEqual(errors, []);
  await ctx.close();

  // ---- a computer ----
  const d = await open({ width: 1440, height: 900 });
  assert.ok(await shown(d.page, '.nav-brand'), 'the side bar shows the brand');
  assert.match(await d.page.textContent('.nav-brand'), /שכר עבודות אישי/);
  assert.equal(await shown(d.page, '.hello > .logo'), false, 'Home repeats the logo beside the side bar');
  assert.equal(await shown(d.page, '.hello .caption + .label'), false, 'Home repeats the app name beside the side bar');
  // Still in the page for screen readers (the side bar's copy is aria-hidden), with the greeting and the actions beside it.
  assert.match(await d.page.locator('.hello').evaluate(x => x.innerText + '|' + x.textContent), /שכר עבודות אישי/);
  assert.ok(await shown(d.page, '[data-testid=home-greeting]'));
  await d.page.click('[data-testid=home-bell]'); await d.page.waitForSelector('[data-testid=notifications-sheet]'); await d.page.keyboard.press('Escape');
  await d.page.click('[data-testid=tab-settings]');
  assert.equal(await shown(d.page, '.set-brand'), false, 'Settings repeats the brand beside the side bar');
  assert.deepEqual(d.errors, []);
  await d.ctx.close();
  console.log('m46-dupes: Home next/recent once, empty History without the 0 card, one brand on a computer: PASS');
 } finally { await browser.close(); server.close(); }
})().catch(e => { console.error(e); process.exit(1); });
