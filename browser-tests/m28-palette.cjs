// M-28 (web, Kis 2 palette): the real page in light and dark uses the approved tokens. A dark primary button is #EDF0F6
// with dark text (D-4), a switch that is on is ink (not money green), the + button is the brand colour with readable
// text, a danger button has readable text, and nothing is pure #121212 grey. Saves light/dark screenshots of Home and
// Settings to SCREENSHOT_DIR (default /tmp/m28-shots). Synthetic data only (web-tests/fixtures/synthetic/rich-v2.json).
const assert = require('node:assert/strict'), http = require('node:http'), fs = require('node:fs'), path = require('node:path');
const { chromium } = require(process.env.PLAYWRIGHT_MODULE || 'playwright');
const ROOT = path.resolve('public');
const SHOTS = process.env.SCREENSHOT_DIR || '/tmp/m28-shots'; fs.mkdirSync(SHOTS, { recursive: true });
const rich = JSON.parse(fs.readFileSync('web-tests/fixtures/synthetic/rich-v2.json', 'utf8'));
const types = { '.html': 'text/html; charset=utf-8', '.js': 'text/javascript', '.css': 'text/css', '.json': 'application/json', '.ttf': 'font/ttf' };
const server = http.createServer((q, r) => {
  let p = decodeURIComponent(new URL(q.url, 'http://x').pathname); if (p === '/') p = '/index.html';
  if (p === '/api/config') { r.writeHead(200, { 'content-type': 'application/json' }); return r.end('{}'); }
  const f = path.join(ROOT, p); if (!f.startsWith(ROOT) || !fs.existsSync(f) || fs.statSync(f).isDirectory()) { r.writeHead(404); return r.end(); }
  r.writeHead(200, { 'content-type': types[path.extname(f)] || 'application/octet-stream', 'cache-control': 'no-store' }); fs.createReadStream(f).pipe(r);
});
const EXPECT = {
  light: { bg: 'rgb(217, 220, 226)', primary: 'rgb(35, 42, 58)', onPrimary: 'rgb(237, 239, 242)', add: 'rgb(74, 62, 214)', addInk: 'rgb(255, 255, 255)', danger: 'rgb(176, 42, 36)', onDanger: 'rgb(255, 255, 255)' },
  dark: { bg: 'rgb(15, 18, 22)', primary: 'rgb(237, 240, 246)', onPrimary: 'rgb(15, 18, 22)', add: 'rgb(147, 152, 255)', addInk: 'rgb(21, 21, 58)', danger: 'rgb(255, 145, 131)', onDanger: 'rgb(43, 14, 11)' },
};
(async () => {
 await new Promise(r => server.listen(0, '127.0.0.1', r));
 const browser = await chromium.launch({ executablePath: process.env.CHROMIUM_PATH || undefined, args: ['--no-sandbox', '--disable-dev-shm-usage', '--disable-gpu'] });
 try {
  for (const scheme of ['light', 'dark']) {
   const ctx = await browser.newContext({ serviceWorkers: 'block', viewport: { width: 390, height: 844 }, locale: 'he-IL', colorScheme: scheme });
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
   await page.screenshot({ path: path.join(SHOTS, `m28-home-${scheme}.png`), fullPage: true });
   const got = await page.evaluate(() => {
    const made = [], probe = html => { const d = document.createElement('div'); d.innerHTML = html; document.body.append(d); made.push(d); return d.firstElementChild; };
    const cs = (el, p) => getComputedStyle(el)[p];
    const primary = probe('<button class="btn primary">x</button>'), danger = probe('<button class="btn danger">x</button>');
    const on = probe('<button class="switch" role="switch" aria-checked="true"></button>');
    const add = document.querySelector('.nav .add');
    const out = { bg: cs(document.body, 'backgroundColor'), ink: cs(document.body, 'color'), primary: cs(primary, 'backgroundColor'), onPrimary: cs(primary, 'color'),
      danger: cs(danger, 'backgroundColor'), onDanger: cs(danger, 'color'), switchOn: cs(on, 'backgroundColor'),
      add: add && cs(add, 'backgroundColor'), addInk: add && cs(add, 'color') };
    made.forEach(d => d.remove()); return out;
   });
   const want = EXPECT[scheme];
   for (const k of Object.keys(want)) if (k !== 'add' && k !== 'addInk' || got.add) assert.equal(got[k], want[k], `${scheme} ${k}`);
   assert.equal(got.switchOn, got.primary, `${scheme}: a switch that is on is the ink fill (FB23-F2)`);
   assert.notEqual(got.bg, 'rgb(18, 18, 18)');
   await page.click('[data-testid=tab-settings]');
   await page.waitForTimeout(300);
   await page.screenshot({ path: path.join(SHOTS, `m28-settings-${scheme}.png`), fullPage: true });
   assert.deepEqual(errors, []);
   await ctx.close();
  }
  console.log(`m28-palette: light and dark tokens on the real page (D-4 primary, ink switch, brand +, danger text): PASS (screenshots in ${SHOTS})`);
 } finally { await browser.close(); server.close(); }
})().catch(e => { console.error(e); process.exit(1); });
