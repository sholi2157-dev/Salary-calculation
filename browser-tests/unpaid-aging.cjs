// 2.4 task 07 (idea C): Home shows "N משמרות לא שולמו מעל 30 יום · ₪X" per workplace and currency, the numbers are the sums
// of the saved amounts, tapping opens History filtered (ordinary filters) to exactly those shifts, nothing is written, and
// nothing shows when no unpaid shift is that old. Synthetic data only (web-tests/fixtures/unpaid-aging.json). 360px light/dark.
const assert = require('node:assert/strict'), http = require('node:http'), fs = require('node:fs'), path = require('node:path');
const { chromium } = require(process.env.PLAYWRIGHT_MODULE || 'playwright');
const ROOT = path.resolve('public'), SHOTS = process.env.SHOTS_DIR || '';
const doc = JSON.parse(fs.readFileSync('web-tests/fixtures/unpaid-aging.json', 'utf8'));
const types = { '.html': 'text/html; charset=utf-8', '.js': 'text/javascript', '.css': 'text/css', '.json': 'application/json', '.ttf': 'font/ttf' };
const server = http.createServer((q, r) => {
  let p = decodeURIComponent(new URL(q.url, 'http://x').pathname); if (p === '/') p = '/index.html';
  if (p === '/api/config') { r.writeHead(200, { 'content-type': 'application/json' }); return r.end('{}'); }
  const f = path.join(ROOT, p); if (!f.startsWith(ROOT) || !fs.existsSync(f) || fs.statSync(f).isDirectory()) { r.writeHead(404); return r.end(); }
  r.writeHead(200, { 'content-type': types[path.extname(f)] || 'application/octet-stream', 'cache-control': 'no-store' }); fs.createReadStream(f).pipe(r);
});
const local = (day, time) => { const [y, m, d] = day.split('-').map(Number), [h, mi] = time.split(':').map(Number); return new Date(y, m - 1, d, h, mi); };
const backup = () => ({ work_complete_backup: {
  entries: doc.entries.map((e, i) => ({ id: e.id, category: e.category, date: local(e.day, e.time).getTime(), createdAt: local(e.day, e.time).getTime() + i,
    isTimeRange: false, startTime: null, endTime: null, hours: e.hours, hourlyRate: e.hourlyRate, totalEarnings: e.totalEarnings, payKnown: e.payKnown,
    isPaid: e.isPaid, notes: '', currency: e.currency, isGroupShift: e.isGroupShift, employerRate: e.isGroupShift ? 60 : null, workerRate: e.isGroupShift ? 40 : null,
    groupWorkersJson: e.groupWorkersJson, kind: e.kind })),
  categories: doc.categories.map(name => ({ name, defaultRate: 50 })), workers: [], webPreferences: { mainCurrency: '₪', defaultCategory: doc.categories[0] } } });

async function open(browser, now, scheme) {
  const ctx = await browser.newContext({ serviceWorkers: 'block', viewport: { width: 360, height: 780 }, locale: 'he-IL', colorScheme: scheme });
  await ctx.route('**/*', route => new URL(route.request().url()).hostname === '127.0.0.1' ? route.continue() : route.abort());
  await ctx.addInitScript(data => {
    if (sessionStorage.getItem('seeded')) return; sessionStorage.setItem('seeded', 'yes');
    for (const [key, value] of Object.entries(data)) localStorage.setItem(key, JSON.stringify(value));
    localStorage.setItem('kis_web_v1', JSON.stringify({ launched: 2, tourDone: true }));
    localStorage.setItem('kis_ai_invited_v1', '1'); localStorage.setItem('kis_signin_skip_v1', '1');
  }, backup());
  const page = await ctx.newPage(), errors = [];
  await page.clock.setFixedTime(now);
  page.on('pageerror', e => errors.push(e.message));
  await page.goto(`http://127.0.0.1:${server.address().port}`);
  await page.waitForSelector('[data-testid=home-hero]');
  return { ctx, page, errors };
}

(async () => {
 await new Promise(r => server.listen(0, '127.0.0.1', r));
 const browser = await chromium.launch({ executablePath: process.env.CHROMIUM_PATH || undefined, args: ['--no-sandbox', '--disable-dev-shm-usage', '--disable-gpu'] });
 try {
  for (const scheme of ['light', 'dark']) {
   const { ctx, page, errors } = await open(browser, local(doc.now.day, doc.now.time), scheme);
   const storeBefore = await page.evaluate(() => localStorage.getItem('work_complete_backup'));
   const rows = await page.$$eval('[data-testid=aging-lines] [data-aging]', l => l.map(b => [b.querySelector('b').textContent.trim(), b.querySelector('[data-aging-line]').textContent.replace(/\s+/g, ' ').trim()]));
   assert.deepEqual(rows, doc.expected.map(g => [g.category, g.line]), 'one line per workplace and currency, oldest first, same text as Android');
   // The whole row stays inside 360px (no sideways overflow).
   assert.ok(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth), 'no horizontal overflow at 360px');
   if (SHOTS) {
    await page.locator('[data-testid=aging-lines]').scrollIntoViewIfNeeded();
    await page.evaluate(() => document.querySelector('[data-testid=aging-lines]').scrollIntoView({ block: 'center' }));
    await page.screenshot({ path: path.join(SHOTS, `web-home-aging-360-${scheme}.png`) });
   }
   if (scheme === 'light') {
    // Tap the restaurant line: History, "לא שולם" + place + currency + the date range, showing exactly its three shifts.
    const g = doc.expected.findIndex(x => x.category === 'מסעדה');
    await page.click(`[data-aging="${g}"]`);
    await page.waitForSelector('#page .shift[data-id]');
    const ids = await page.$$eval('#page .shift[data-id]', l => l.map(x => x.dataset.id).sort());
    assert.deepEqual(ids, ['a01', 'a02', 'a03']);
    const line = await page.evaluate(() => document.body.innerText);
    assert.ok(line.includes('לא שולם') && line.includes('מסעדה'), 'the ordinary filter line says what is shown');
    if (SHOTS) await page.screenshot({ path: path.join(SHOTS, 'web-history-from-aging-360-light.png') });
   }
   assert.equal(await page.evaluate(() => localStorage.getItem('work_complete_backup')), storeBefore, 'display only: nothing written');
   assert.deepEqual(errors, []);
   await ctx.close();
  }
  // Nothing older than 30 days: no line at all.
  const { ctx, page, errors } = await open(browser, local(doc.nothingOld.now.day, doc.nothingOld.now.time), 'light');
  assert.equal(await page.$('[data-testid=aging-lines]'), null);
  assert.deepEqual(errors, []); await ctx.close();
  console.log('unpaid-aging: lines match the fixture at 360px light/dark, tap opens History filtered to them, nothing written, nothing when not old');
 } finally { await browser.close(); server.close(); }
})().catch(e => { console.error(e); process.exit(1); });
