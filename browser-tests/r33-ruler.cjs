// R-33 (web): on the editor's ruler a night shift is one span, not two pieces at the edges. A shift that crosses
// midnight inside noon→noon is drawn on a noon-to-noon window (ticks 12 18 00 06 12); a day shift keeps 00→24.
// Screenshots to SCREENSHOT_DIR (default /tmp/r33-shots). Synthetic data only (web-tests/fixtures/synthetic/rich-v2.json).
const assert = require('node:assert/strict'), http = require('node:http'), fs = require('node:fs'), path = require('node:path');
const { chromium } = require(process.env.PLAYWRIGHT_MODULE || 'playwright');
const ROOT = path.resolve('public');
const SHOTS = process.env.SCREENSHOT_DIR || '/tmp/r33-shots'; fs.mkdirSync(SHOTS, { recursive: true });
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

 const setTimes = async (page, start, end) => {
  await page.click('[data-testid=clock-start]'); await page.keyboard.type(start.replace(':', ''));
  if (await page.locator('[data-testid=entry-next]').count()) await page.click('[data-testid=entry-next]'); else await page.click('[data-testid=clock-end]');
  await page.keyboard.type(end.replace(':', '')); await page.click('[data-testid=entry-done]');
  await page.waitForSelector('[data-testid=ruler]');
 };
 const read = page => page.locator('[data-testid=ruler]').evaluate(r => ({
  origin: r.dataset.origin, spans: [...r.querySelectorAll('.span')].map(s => s.getBoundingClientRect()).map(b => ({ left: b.left, right: b.right })),
  ticks: [...r.querySelectorAll('.ticks span')].map(s => s.textContent).join(' '),
  start: r.querySelector('[data-testid=ruler-start]').getBoundingClientRect(), end: r.querySelector('[data-testid=ruler-end]').getBoundingClientRect(),
  startText: r.querySelector('[data-testid=ruler-start]').getAttribute('aria-valuetext'), endText: r.querySelector('[data-testid=ruler-end]').getAttribute('aria-valuetext') }));
 try {
  for (const viewport of [{ width: 390, height: 844 }, { width: 320, height: 700 }]) {
   const { ctx, page, errors } = await open(viewport);
   await page.click('[data-testid=add-shift]'); await page.waitForSelector('[data-testid=ruler]');
   // A night shift: one span, 00 in the middle, start on the right of end (RTL), the times themselves unchanged.
   await setTimes(page, '20:30', '00:30');
   let r = await read(page);
   assert.equal(r.origin, '720'); assert.equal(r.spans.length, 1, 'one span for 20:30–00:30');
   assert.equal(r.ticks, '12 18 00 06 12');
   assert.ok(r.start.left > r.end.left, 'start is right of end (RTL)');
   assert.ok(r.spans[0].left <= r.end.left + r.end.width / 2 + 1 && r.spans[0].right >= r.start.left + r.start.width / 2 - 1, 'the span runs from handle to handle');
   assert.deepEqual([r.startText, r.endText], ['20:30', '00:30']);
   assert.match(await page.textContent('[data-testid=editor-meta]'), /4:00|4 ש/);
   await page.screenshot({ path: path.join(SHOTS, `r33-night-${viewport.width}.png`) });
   // A day shift: the 00→24 ruler as before.
   await setTimes(page, '09:00', '17:00');
   r = await read(page);
   assert.equal(r.origin, '0'); assert.equal(r.spans.length, 1); assert.equal(r.ticks, '00 06 12 18 24');
   // The keyboard still moves the real time by 15 minutes, across midnight too.
   await setTimes(page, '22:00', '23:45');
   await page.focus('[data-testid=ruler-end]'); await page.keyboard.press('ArrowLeft');
   r = await read(page);
   assert.equal(r.endText, '00:00'); assert.equal(r.origin, '720'); assert.equal(r.spans.length, 1);
   assert.deepEqual(errors, []);
   await ctx.close();
  }
  console.log('PASS: R-33 night ruler: 20:30–00:30 is one span on a noon-to-noon ruler (390 and 320px); day shifts keep 00→24; keys cross midnight.');
 } finally { await browser.close(); server.close(); }
})().catch(e => { console.error(e); process.exit(1); });
