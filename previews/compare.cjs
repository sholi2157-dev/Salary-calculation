/* Before/after screenshots of the real web app (synthetic data): current design vs the preview layer (overlay.css + the
   proposed workplace palette). Output: ../../shots/preview/<theme>-<screen>-{current,proposed}.png */
const { chromium } = require('/opt/node22/lib/node_modules/playwright');
const http = require('node:http'), fs = require('node:fs'), path = require('node:path');
const ROOT = '/home/user/wt/web/public', OUT = path.join(__dirname, '../../shots/preview');
fs.mkdirSync(OUT, { recursive: true });
const rich = JSON.parse(fs.readFileSync('/home/user/wt/web/web-tests/fixtures/synthetic/rich-v2.json', 'utf8'));
const overlay = fs.readFileSync(path.join(__dirname, 'overlay.css'), 'utf8');
const PALETTE = [['#393ECA', '#150A6B', '#FFFFFF', '#7D9BFF', '#0E142A'], ['#117D15', '#013E03', '#FFFFFF', '#44E994', '#021C0D'], ['#C50E70', '#690039', '#FFFFFF', '#FA74A5', '#260C16'],
  ['#F5B82E', '#D18F1F', '#1A1200', '#FFC732', '#1E1400'], ['#9F4AE4', '#5B2187', '#FFFFFF', '#C874C8', '#200E21'], ['#0777A9', '#003D59', '#FFFFFF', '#8BC7FE', '#031729'],
  ['#065E63', '#012527', '#FFFFFF', '#18AFA4', '#001B19'], ['#8B032A', '#33000A', '#FFFFFF', '#F05963', '#270C0D']];
const types = { '.html': 'text/html; charset=utf-8', '.js': 'text/javascript', '.css': 'text/css', '.json': 'application/json', '.ttf': 'font/ttf', '.woff2': 'font/woff2' };
const server = http.createServer((q, r) => { let p = decodeURIComponent(new URL(q.url, 'http://x').pathname); if (p === '/') p = '/index.html';
  if (p === '/api/config') { r.writeHead(200, { 'content-type': 'application/json' }); return r.end('{}'); }
  const f = p.startsWith('/__preview/') ? path.join(__dirname, p.slice(11)) : path.join(ROOT, p);
  if (!fs.existsSync(f) || fs.statSync(f).isDirectory()) { r.writeHead(404); return r.end(); }
  r.writeHead(200, { 'content-type': types[path.extname(f)] || 'application/octet-stream' }); fs.createReadStream(f).pipe(r); });
(async () => {
  await new Promise(r => server.listen(0, r)); const base = `http://127.0.0.1:${server.address().port}/`;
  const browser = await chromium.launch();
  for (const scheme of ['light', 'dark']) for (const variant of ['current', 'proposed']) {
    const ctx = await browser.newContext({ viewport: { width: 390, height: 844 }, deviceScaleFactor: 2, colorScheme: scheme, locale: 'he-IL', serviceWorkers: 'block' });
    await ctx.addInitScript(([d, css, pal]) => { if (!sessionStorage.getItem('seeded')) { sessionStorage.setItem('seeded', '1'); localStorage.clear();
      for (const [k, v] of Object.entries(d)) localStorage.setItem(k, JSON.stringify(v));
      localStorage.setItem('kis_web_v1', JSON.stringify({ launched: 2, tourDone: true })); localStorage.setItem('kis_ai_invited_v1', '1'); localStorage.setItem('kis_signin_skip_v1', '1'); }
      if (css) document.addEventListener('DOMContentLoaded', () => { const s = document.createElement('style'); s.textContent = css; document.head.appendChild(s);
        window.KisUi?.PLACES.forEach((p, i) => Object.assign(p, { from: pal[i][0], to: pal[i][1], on: pal[i][2], accentDark: pal[i][3], accentDarkInk: pal[i][4] })); window.Kis?.refresh(); });
    }, [rich, variant === 'proposed' ? overlay : null, PALETTE]);
    const page = await ctx.newPage(); await page.goto(base); await page.waitForSelector('[data-testid=home-hero]'); await page.waitForTimeout(700);
    const shot = async name => { await page.waitForTimeout(450); await page.screenshot({ path: path.join(OUT, `${scheme}-${name}-${variant}.png`) }); };
    const esc = async () => { for (let i = 0; i < 2; i++) { await page.keyboard.press('Escape'); await page.waitForTimeout(150); } };
    await page.evaluate(() => window.Kis.refresh()); await shot('home');
    await page.evaluate(() => window.Kis.openDetail('r08')); await shot('detail'); await esc();
    await page.evaluate(() => window.Kis.go('history')); await shot('history');
    await page.click('[data-testid=history-filters]'); await shot('filters'); await esc();
    await page.evaluate(() => window.Kis.go('settings')); await shot('settings');
    await page.evaluate(() => window.KisSettings.placesSheet(window.Kis)); await shot('places'); await esc();
    await page.evaluate(() => { localStorage.setItem('work_active_shift_v1:guest', JSON.stringify({ id: 'p', category: 'מסעדה', rate: 50, currency: '₪', startedAt: Date.now() - 5400e3 })); window.Kis.go('home'); });
    await shot('live');
    await page.evaluate(() => { localStorage.removeItem('work_active_shift_v1:guest'); window.Kis.openNew(); }); await shot('editor');
    await ctx.close();
  }
  await browser.close(); server.close(); console.log('preview shots in', OUT);
})().catch(e => { console.error(e); process.exit(1); });
