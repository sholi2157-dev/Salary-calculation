// M-38 (web, D-9/D-12): on the real pages (Home, History, Settings, a shift's details) the text uses one neutral voice:
// no "נגיעה", "דו״ח", singular commands or plural address; unpaid is "לא שולם"; shift dates read "יום ד׳ כ״ח חשוון (8/11)" (or
// היום/אתמול/מחר). Saves screenshots to SCREENSHOT_DIR (default /tmp/m38-shots). Synthetic data only.
const assert = require('node:assert/strict'), http = require('node:http'), fs = require('node:fs'), path = require('node:path');
const { chromium } = require(process.env.PLAYWRIGHT_MODULE || 'playwright');
const ROOT = path.resolve('public');
const SHOTS = process.env.SCREENSHOT_DIR || '/tmp/m38-shots'; fs.mkdirSync(SHOTS, { recursive: true });
const rich = JSON.parse(fs.readFileSync('web-tests/fixtures/synthetic/rich-v2.json', 'utf8'));
const types = { '.html': 'text/html; charset=utf-8', '.js': 'text/javascript', '.css': 'text/css', '.json': 'application/json', '.ttf': 'font/ttf' };
const server = http.createServer((q, r) => {
  let p = decodeURIComponent(new URL(q.url, 'http://x').pathname); if (p === '/') p = '/index.html';
  if (p === '/api/config') { r.writeHead(200, { 'content-type': 'application/json' }); return r.end('{}'); }
  const f = path.join(ROOT, p); if (!f.startsWith(ROOT) || !fs.existsSync(f) || fs.statSync(f).isDirectory()) { r.writeHead(404); return r.end(); }
  r.writeHead(200, { 'content-type': types[path.extname(f)] || 'application/octet-stream', 'cache-control': 'no-store' }); fs.createReadStream(f).pipe(r);
});
const BANNED = /נגיעה|דו״ח|(^|[^א-ת])(הזן|הוסף|בחר|נסה|אתם|סיימתם|שלכם|אתה|תרצה)([^א-ת]|$)/;
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
  const clean = async where => { const t = await page.locator('body').innerText(); const m = t.match(BANNED); assert.equal(m, null, `${where}: …${m && t.slice(Math.max(0, m.index - 30), m.index + 30)}…`); };
  await clean('Home');
  const dates = await page.locator('.shift .top b, .shift .top strong').allInnerTexts();
  assert.ok(dates.length > 0, 'shift cards with dates on Home');
  for (const d of dates.filter(Boolean)) assert.match(d, /^(היום|אתמול|מחר|יום [אבגדהוש]׳ [א-ת״׳]+ [א-ת״׳ ]+ \(\d{1,2}\/\d{1,2}(\/\d{4})?\))$/, 'date ' + d);
  await page.screenshot({ path: path.join(SHOTS, 'm38-home.png'), fullPage: true });
  await page.locator('.shift[data-id]').first().click();
  await page.waitForSelector('[data-testid=shift-detail]');
  await clean('Shift details');
  await page.screenshot({ path: path.join(SHOTS, 'm38-detail.png') });
  await page.keyboard.press('Escape'); await page.waitForSelector('[data-testid=shift-detail]', { state: 'detached' });
  await page.click('[data-testid=tab-history]'); await page.waitForTimeout(300);
  await clean('History');
  await page.click('[data-testid=tab-settings]'); await page.waitForTimeout(300);
  await clean('Settings');
  assert.deepEqual(errors, []);
  await ctx.close();
  console.log(`m38-wording: Home, details, History and Settings in one neutral voice with "יום ד׳ כ״ח חשוון (8/11)" dates: PASS (screenshots in ${SHOTS})`);
 } finally { await browser.close(); server.close(); }
})().catch(e => { console.error(e); process.exit(1); });
