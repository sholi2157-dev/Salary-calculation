// M-37: in History, selecting shifts and marking them paid shows the one message with "ביטול", and undo restores each
// shift's own previous mark. Synthetic data only.
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
  await page.click('[data-testid=tab-history]');
  const cards = await page.$$eval('#page .shift[data-id]', l => l.slice(0, 3).map(x => x.dataset.id));
  assert.equal(cards.length, 3);
  const marks = () => page.evaluate(ids => ids.map(id => Kis.store.data.entries.find(e => String(e.id) === id).isPaid === true), cards);
  // Mixed marks first: one paid, two not.
  await page.evaluate(ids => Kis.store.update(d => d.entries.forEach(e => { if (ids.includes(String(e.id))) e.isPaid = String(e.id) === ids[0]; })), cards);
  await page.evaluate(() => Kis.refresh());
  const before = await marks();
  assert.deepEqual(before, [true, false, false]);
  for (const id of cards) await page.locator(`#page .shift[data-id="${id}"]`).dispatchEvent('contextmenu');
  await page.click('[data-s=paid]');
  assert.deepEqual(await marks(), [true, true, true]);
  await page.waitForSelector('[data-testid=toast-action]');
  assert.equal((await page.textContent('[data-testid=toast-action]')).trim(), 'ביטול');
  await page.click('[data-testid=toast-action]');
  assert.deepEqual(await marks(), before, 'undo restores each own previous mark');
  const saved = await page.evaluate(() => Object.keys(localStorage).length);
  assert.ok(saved > 0);
  assert.deepEqual(errors, []);
  console.log('m37-paid-undo: bulk paid shows the message with undo, and undo restores every own mark');
 } finally { await browser.close(); server.close(); }
})().catch(e => { console.error(e); process.exit(1); });
