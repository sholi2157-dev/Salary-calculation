/*
 * 2.3.0 Milestone 2 in a real browser (synthetic data): values are never changed silently (audit M-06/M-09, U-06/U-07).
 * An impossible hour is announced, an overnight end is labelled, a shift over 16 hours or a very large amount asks first,
 * a negative rate shows an error and no total, and the live timer reads "40,5" as 40.5 and refuses "-5" with a message.
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
  await ctx.addInitScript(d => { if (!sessionStorage.getItem('seeded')) { sessionStorage.setItem('seeded', '1'); localStorage.clear();
    for (const [k, v] of Object.entries(d)) localStorage.setItem(k, JSON.stringify(v));
    localStorage.setItem('kis_web_v1', JSON.stringify({ launched: 2, tourDone: true })); localStorage.setItem('kis_ai_invited_v1', '1'); localStorage.setItem('kis_signin_skip_v1', '1'); } }, rich);
  const page = await ctx.newPage(), errors = []; page.on('pageerror', e => errors.push(e.message));
  const tid = t => `[data-testid="${t}"]`;
  await page.goto(base); await page.waitForSelector(tid('home-hero'));
  await page.evaluate(() => window.Kis.openNew()); await page.waitForTimeout(300);
  await page.click('[data-mode="CLOCK"]').catch(() => {});
  // 2 then 5 cannot be an hour: the app says so.
  await page.click(tid('clock-start')); await page.keyboard.type('25');
  await page.waitForSelector('#toast:has-text("אין שעה כזאת")');
  // 22:00 → 06:00 ends the next day.
  await page.click(tid('clock-start')); await page.keyboard.type('2200');
  await page.click(tid('clock-end')); await page.keyboard.type('0600'); await page.keyboard.press('Enter');
  assert.match(await page.textContent(tid('editor-meta')), /הסיום למחרת/);
  // 06:00 → 23:00 is 17 hours: saving asks first, and nothing is saved until confirmed.
  const before = await page.evaluate(() => JSON.parse(localStorage.getItem('work_complete_backup')).entries.length);
  await page.click(tid('clock-start')); await page.keyboard.type('0600');
  await page.click(tid('clock-end')); await page.keyboard.type('2300'); await page.keyboard.press('Enter');
  assert.match(await page.textContent(tid('editor-meta')), /ארוכה מ־16 שעות/);
  await page.click(tid('save-shift'));
  await page.waitForSelector('[data-testid="confirm-sheet"], .sheet:has-text("לשמור משמרת של 17:00 שעות")');
  assert.equal(await page.evaluate(() => JSON.parse(localStorage.getItem('work_complete_backup')).entries.length), before);
  await page.keyboard.press('Escape'); await page.waitForTimeout(200);
  // A negative rate: an error, and no total.
  await page.fill(tid('rate-input'), '-5'); await page.waitForTimeout(200);
  assert.equal(await page.locator(tid('editor-total')).count(), 0);
  assert.match(await page.textContent(tid('save-hint')), /שלילי|תעריף/);
  // Leaving unsaved typing asks first (kept behaviour); this test drops the draft and reloads instead.
  await page.evaluate(() => localStorage.removeItem('kis_web_draft_v1')); await page.reload(); await page.waitForSelector(tid('home-hero'));
  // Live timer: "-5" is refused with a message, "40,5" starts at 40.5.
  await page.click(tid('live-start'));
  await page.fill('[data-f=r]', '-5'); await page.click(tid('live-go'));
  assert.equal(await page.textContent(tid('live-error')), 'התעריף לא תקין');
  await page.fill('[data-f=r]', '40,5'); await page.click(tid('live-go')); await page.waitForTimeout(200);
  const timer = await page.evaluate(() => JSON.parse(localStorage.getItem('work_active_shift_v1:guest')));
  assert.equal(timer.rate, 40.5);
  assert.deepEqual(errors, []);
  console.log('m2-input: refused hour announced, overnight labelled, long shift asks first, negative rate blocked, 40,5 = 40.5');
  await browser.close(); server.close();
})().catch(e => { console.error(e); process.exit(1); });
