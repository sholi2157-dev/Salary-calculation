/*
 * 2.3.0 M-15 in a real browser (synthetic data): a weekly plan whose days ended while the site was not opened is not
 * recorded in bulk; Home shows how many wait, and the review records the chosen days and skips the rest.
 */
const assert = require('node:assert/strict'), http = require('node:http'), fs = require('node:fs'), path = require('node:path');
const { chromium } = require(process.env.PLAYWRIGHT_MODULE || 'playwright');
const ROOT = path.resolve('public'), rich = JSON.parse(fs.readFileSync('web-tests/fixtures/synthetic/rich-v2.json', 'utf8'));
const types = { '.html': 'text/html; charset=utf-8', '.js': 'text/javascript', '.css': 'text/css', '.json': 'application/json', '.ttf': 'font/ttf' };
const server = http.createServer((q, r) => { let p = decodeURIComponent(new URL(q.url, 'http://x').pathname); if (p === '/') p = '/index.html';
  if (p === '/api/config') { r.writeHead(200, { 'content-type': 'application/json' }); return r.end('{}'); }
  const f = path.join(ROOT, p); if (!f.startsWith(ROOT) || !fs.existsSync(f) || fs.statSync(f).isDirectory()) { r.writeHead(404); return r.end(); }
  r.writeHead(200, { 'content-type': types[path.extname(f)] || 'application/octet-stream' }); fs.createReadStream(f).pipe(r); });
(async () => {
  await new Promise(r => server.listen(0, r));
  const base = `http://127.0.0.1:${server.address().port}/`, browser = await chromium.launch();
  const ctx = await browser.newContext({ viewport: { width: 390, height: 844 }, locale: 'he-IL', serviceWorkers: 'block' });
  const now = new Date(), from = new Date(now.getFullYear(), now.getMonth(), now.getDate() - 20), iso = d => d.toISOString().slice(0, 10);
  const data = { ...rich, work_series_v1: { series: [{ ...rich.work_series_v1.series[0], daysMask: 127, fromDate: iso(from), untilDate: iso(new Date(now.getFullYear() + 1, 0, 1)) }], exceptions: [] } };
  await ctx.addInitScript(d => { if (!sessionStorage.getItem('seeded')) { sessionStorage.setItem('seeded', '1'); localStorage.clear();
    for (const [k, v] of Object.entries(d)) localStorage.setItem(k, JSON.stringify(v));
    localStorage.setItem('kis_web_v1', JSON.stringify({ launched: 2, tourDone: true })); localStorage.setItem('kis_ai_invited_v1', '1'); localStorage.setItem('kis_signin_skip_v1', '1'); } }, data);
  const page = await ctx.newPage(), errors = []; page.on('pageerror', e => errors.push(e.message));
  const tid = t => `[data-testid="${t}"]`, count = () => page.evaluate(() => JSON.parse(localStorage.getItem('work_complete_backup')).entries.filter(e => e.seriesKey).length);
  await page.goto(base); await page.waitForSelector(tid('home-hero')); await page.waitForTimeout(500);
  assert.equal(await count(), 0, 'no plan days recorded in bulk on open');
  assert.match(await page.textContent(tid('plan-review-flag')), /משמרות קבועות מחכות לאישור/);
  await page.click(tid('plan-review-flag'));
  const boxes = page.locator(`${tid('plan-review-list')} input[type=checkbox]`), n = await boxes.count();
  assert.ok(n >= 19, 'about 20 days wait: ' + n);
  // Nothing is ticked at first, so one tap cannot record days that were not worked.
  for (let i = 0; i < n; i++) assert.equal(await boxes.nth(i).isChecked(), false, 'day ' + i + ' starts unticked');
  assert.equal(await page.isDisabled(tid('plan-review-record')), true, 'nothing to record until a day is marked');
  await boxes.nth(0).check(); await boxes.nth(1).check();
  assert.match(await page.textContent(tid('plan-review-record')), /רישום 2 משמרות/);
  await page.click(tid('plan-review-skip'));
  await page.click(tid('plan-review-record')); await page.waitForTimeout(300);
  assert.equal(await count(), 2);
  const skipped = await page.evaluate(() => JSON.parse(localStorage.getItem('work_series_v1')).exceptions.filter(x => x.cancelled).length);
  assert.equal(skipped, n - 2);
  await page.reload(); await page.waitForSelector(tid('home-hero')); await page.waitForTimeout(300);
  assert.equal(await page.locator(tid('plan-review-flag')).count(), 0, 'nothing waits anymore');
  assert.deepEqual(errors, []);
  console.log('m3-plan-review: no bulk recording; review recorded 2 and skipped ' + (n - 2));
  await browser.close(); server.close();
})().catch(e => { console.error(e); process.exit(1); });
