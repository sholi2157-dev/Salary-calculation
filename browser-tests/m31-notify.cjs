// M-31: notifications per kind on the web, as in Android 2.2.3: the main switch turns all kinds on or off, each kind has
// its own switch, the main one shows on only when every kind is on, and the in-site reminders follow each kind. Synthetic data.
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
  const before = await page.evaluate(() => JSON.stringify(Kis.store.data));
  const open = () => page.evaluate(() => Kis.go('settings', { section: 'התראות' }));
  await open(); await page.waitForSelector('[data-testid=notify-rate]');
  const state = () => page.evaluate(() => ({ all: document.querySelector('[data-testid=notify-all]').getAttribute('aria-checked'),
    monthly: document.querySelector('[data-testid=notify-monthly]').getAttribute('aria-checked'), rate: document.querySelector('[data-testid=notify-rate]').getAttribute('aria-checked'),
    stored: [Kis.store.local.notifyMonthly === true, Kis.store.local.notifyRate === true] }));
  // The monthly summary is on unless switched off, as in the app (state-machine audit); the other reminders are off.
  assert.deepEqual(await state(), { all: 'false', monthly: 'true', rate: 'false', stored: [false, false] }, 'monthly on by default, the rest off');
  await page.click('[data-testid=notify-monthly]');
  assert.deepEqual(await state(), { all: 'false', monthly: 'false', rate: 'false', stored: [false, false] }, 'monthly switched off');
  await page.click('[data-testid=notify-rate]');
  assert.deepEqual(await state(), { all: 'false', monthly: 'false', rate: 'true', stored: [false, true] }, 'one kind alone; the main switch stays off');
  await page.click('[data-testid=notify-all]');
  assert.deepEqual(await state(), { all: 'true', monthly: 'true', rate: 'true', stored: [true, true] }, 'main on from partial enables every kind');
  await page.click('[data-testid=notify-all]');
  assert.deepEqual(await state(), { all: 'false', monthly: 'false', rate: 'false', stored: [false, false] }, 'main off turns every kind off');
  await page.click('[data-testid=notify-all]');
  assert.deepEqual(await state(), { all: 'true', monthly: 'true', rate: 'true', stored: [true, true] }, 'main on turns every kind on');
  await page.click('[data-testid=notify-monthly]');
  assert.deepEqual(await state(), { all: 'false', monthly: 'false', rate: 'true', stored: [false, true] });
  await page.reload(); await page.waitForSelector('[data-testid=home-hero]'); await open(); await page.waitForSelector('[data-testid=notify-rate]');
  assert.deepEqual(await state(), { all: 'false', monthly: 'false', rate: 'true', stored: [false, true] }, 'kept after reload');
  // The reminders follow each kind: with only "תעריף חסר" on, no monthly recap reminder is produced.
  const kinds = await page.evaluate(() => { Kis.store.setLocal('lastRate', 0, true); Kis.store.setLocal('lastMonthly', null, true); return KisSettings.dueReminders(Kis).map(n => n.kind); });
  assert.ok(!kinds.includes('monthly'), 'monthly is off: ' + kinds);
  await page.click('[data-testid=notify-rate]');
  await page.click('[data-testid=notify-monthly]');
  assert.deepEqual(await state(), { all: 'false', monthly: 'true', rate: 'false', stored: [true, false] }, 'monthly alone also leaves master off');
  assert.equal(await page.evaluate(() => JSON.stringify(Kis.store.data)), before, 'shifts and places are never touched');
  // The switches are labelled for screen readers.
  for (const t of ['notify-all', 'notify-monthly', 'notify-rate']) assert.ok(await page.getAttribute(`[data-testid=${t}]`, 'aria-label'));
  assert.deepEqual(errors, []);
  console.log('m31-notify: main switch, per-kind switches, persistence and reminders per kind passed');
 } finally { await browser.close(); server.close(); }
})().catch(e => { console.error(e); process.exit(1); });
