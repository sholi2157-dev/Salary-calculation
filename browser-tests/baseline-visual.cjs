/*
 * Stage 0 visual baseline (2.3.0 remediation): the newest web build with the synthetic rich dataset, light and dark,
 * phone/tablet/desktop. Saves screenshots to SCREENSHOT_DIR (default browser-evidence/baseline) for before/after review
 * and fails on page errors or horizontal scrolling at 360px and wider. 320px is recorded, not asserted (audit M-33).
 * Synthetic data only; never points at the production site unless PUBLIC_SITE_URL is set on purpose.
 */
const assert = require('node:assert/strict'), http = require('node:http'), fs = require('node:fs'), path = require('node:path');
const { chromium } = require(process.env.PLAYWRIGHT_MODULE || 'playwright');
const ROOT = path.resolve('public'), OUT = process.env.SCREENSHOT_DIR || 'browser-evidence/baseline';
const rich = JSON.parse(fs.readFileSync(path.join('web-tests', 'fixtures', 'synthetic', 'rich-v2.json'), 'utf8'));
fs.mkdirSync(OUT, { recursive: true });
const types = { '.html': 'text/html; charset=utf-8', '.js': 'text/javascript', '.css': 'text/css', '.json': 'application/json', '.ttf': 'font/ttf' };
const server = http.createServer((q, r) => {
  let p = decodeURIComponent(new URL(q.url, 'http://x').pathname); if (p === '/') p = '/index.html';
  if (p === '/api/config') { r.writeHead(200, { 'content-type': 'application/json' }); return r.end('{}'); }
  const f = path.join(ROOT, p); if (!f.startsWith(ROOT) || !fs.existsSync(f) || fs.statSync(f).isDirectory()) { r.writeHead(404); return r.end(); }
  r.writeHead(200, { 'content-type': types[path.extname(f)] || 'application/octet-stream', 'cache-control': 'no-store' }); fs.createReadStream(f).pipe(r);
});
(async () => {
  await new Promise(r => server.listen(0, r));
  const base = process.env.PUBLIC_SITE_URL || `http://127.0.0.1:${server.address().port}/`;
  const browser = await chromium.launch(process.env.CHROMIUM_PATH ? { executablePath: process.env.CHROMIUM_PATH } : {});
  const report = [];
  for (const [w, h] of [[320, 700], [360, 760], [390, 844], [768, 1024], [1440, 900]]) for (const scheme of ['light', 'dark']) {
    const ctx = await browser.newContext({ viewport: { width: w, height: h }, colorScheme: scheme, locale: 'he-IL', serviceWorkers: 'block', isMobile: w < 900, hasTouch: w < 900 });
    await ctx.addInitScript(d => { if (!sessionStorage.getItem('seeded')) { sessionStorage.setItem('seeded', '1'); localStorage.clear();
      for (const [k, v] of Object.entries(d)) localStorage.setItem(k, JSON.stringify(v));
      localStorage.setItem('kis_web_v1', JSON.stringify({ launched: 2, tourDone: true })); localStorage.setItem('kis_ai_invited_v1', '1'); localStorage.setItem('kis_signin_skip_v1', '1'); } }, rich);
    const page = await ctx.newPage(); const errors = [];
    page.on('pageerror', e => errors.push(e.message)); page.on('console', m => { if (m.type() === 'error' && !/api\/config|favicon/.test(m.text())) errors.push(m.text()); });
    await page.goto(base); await page.waitForSelector('[data-testid=home-hero]');
    for (const [name, go] of [['home', 'home'], ['history', 'history'], ['settings', 'settings'], ['editor', null]]) {
      if (go) await page.evaluate(p => window.Kis.go(p), go); else await page.evaluate(() => window.Kis.openNew());
      await page.waitForTimeout(350);
      const o = await page.evaluate(() => ({ sw: document.documentElement.scrollWidth, cw: document.documentElement.clientWidth }));
      report.push({ w, scheme, name, overflow: o.sw - o.cw });
      await page.screenshot({ path: path.join(OUT, `${w}-${scheme}-${name}.png`) });
      if (w >= 360) assert.ok(o.sw <= o.cw, `${w} ${scheme} ${name}: horizontal scroll ${o.sw}>${o.cw}`);
    }
    assert.deepEqual(errors, [], `${w} ${scheme}: page errors`);
    await ctx.close();
  }
  fs.writeFileSync(path.join(OUT, 'baseline-report.json'), JSON.stringify(report, null, 1));
  console.log('baseline screens:', report.length, '· 320px overflow (recorded):', report.filter(r => r.w === 320 && r.overflow > 0).map(r => `${r.scheme}/${r.name}+${r.overflow}`).join(', ') || 'none');
  await browser.close(); server.close();
})().catch(e => { console.error(e); process.exit(1); });
