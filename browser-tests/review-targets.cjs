// Actual hit testing, boundary dimensions and delete text contrast at phone widths. Synthetic fixture only.
const assert = require('node:assert/strict'), http = require('node:http'), fs = require('node:fs'), path = require('node:path');
const { chromium } = require(process.env.PLAYWRIGHT_MODULE || 'playwright');
const ROOT = path.resolve('public'), rich = JSON.parse(fs.readFileSync('web-tests/fixtures/synthetic/rich-v2.json', 'utf8'));
const server = http.createServer((q, r) => {
  const p = new URL(q.url, 'http://x').pathname;
  if (p === '/api/config') { r.setHeader('content-type', 'application/json'); return r.end('{}'); }
  const f = path.join(ROOT, p === '/' ? 'index.html' : p);
  if (!f.startsWith(ROOT) || !fs.existsSync(f) || fs.statSync(f).isDirectory()) { r.writeHead(404); return r.end(); }
  r.setHeader('content-type', ({ '.html': 'text/html; charset=utf-8', '.js': 'text/javascript', '.css': 'text/css' })[path.extname(f)] || 'application/octet-stream');
  fs.createReadStream(f).pipe(r);
});
const tid = t => `[data-testid="${t}"]`;
(async () => {
  await new Promise(r => server.listen(0, '127.0.0.1', r));
  const browser = await chromium.launch({ executablePath: process.env.CHROMIUM_PATH || undefined, args: ['--no-sandbox'] });
  try {
    for (const width of [320, 360, 390, 412]) for (const theme of ['light', 'dark']) {
      const ctx = await browser.newContext({ viewport: { width, height: 844 }, colorScheme: theme, serviceWorkers: 'block', reducedMotion: 'reduce' });
      await ctx.route('**/*', r => new URL(r.request().url()).hostname === '127.0.0.1' ? r.continue() : r.abort());
      await ctx.addInitScript(data => {
        for (const [k, v] of Object.entries(data)) localStorage.setItem(k, JSON.stringify(v));
        localStorage.setItem('kis_web_v1', JSON.stringify({ launched: 2, tourDone: true, whatsNewSeen: '2.4.0' })); localStorage.setItem('kis_signin_skip_v1', '1');
      }, rich);
      const page = await ctx.newPage(), errors = []; page.on('pageerror', e => errors.push(e.message));
      const shot = async name => {
        if (!process.env.SCREENSHOT_DIR || ![360, 390].includes(width)) return;
        fs.mkdirSync(process.env.SCREENSHOT_DIR, { recursive: true });
        await page.screenshot({ path: path.join(process.env.SCREENSHOT_DIR, `${name}-${width}-${theme}.png`) });
      };
      const fit = async () => assert.deepEqual(await page.evaluate(() => [...document.querySelectorAll('html, .editor, .editor-in, .editor-top, .editor-body')]
        .filter(n => n.scrollWidth > n.clientWidth + 1).map(n => [n.className || n.tagName, n.scrollWidth, n.clientWidth])), [], `overflow at ${width}/${theme}`);
      const sizes = async selector => {
        const small = await page.$$eval(selector, nodes => nodes.filter(n => n.getClientRects().length && !n.closest('[hidden]')).map(n => {
          const r = n.getBoundingClientRect(); return { id: n.dataset.testid || n.textContent, w: r.width, h: r.height };
        }).filter(r => r.w < 43.99 || r.h < 43.99));
        assert.deepEqual(small, [], `${width}/${theme}: undersized controls`);
      };
      // Validate the full 44px effective area, including transparent pseudo-element extensions.
      const hit = async selector => {
        await page.locator(selector).first().scrollIntoViewIfNeeded();
        const result = await page.locator(selector).first().evaluate(n => {
          const r = n.getBoundingClientRect(), p = getComputedStyle(n, '::before');
          const b = getComputedStyle(n), x = r.left + parseFloat(b.borderLeftWidth) + parseFloat(p.left), y = r.top + parseFloat(b.borderTopWidth) + parseFloat(p.top);
          const w = parseFloat(p.width), h = parseFloat(p.height);
          const points = [[x + 1, y + 1], [x + w - 1, y + 1], [x + 1, y + h - 1], [x + w - 1, y + h - 1]];
          return { w, h, hits: points.map(([x, y]) => n.contains(document.elementFromPoint(x, y))) };
        });
        assert.ok(result.w >= 44 && result.h >= 44 && result.hits.every(Boolean), `${selector}: ${JSON.stringify(result)}`);
      };
      await page.goto(`http://127.0.0.1:${server.address().port}`);
      for (const section of ['עבודה', 'מראה ותחושה', 'התראות']) {
        await page.evaluate(s => Kis.go('settings', { section: s }), section);
        await sizes('.set-group.on .seg button');
        await hit('.set-group.on .switch');
        if (section === 'מראה ותחושה') await shot('settings-targets');
        await fit();
      }
      // A click above the visual switch track really toggles it, rather than a nearby label/control.
      const sw = tid('notify-all'), before = await page.locator(sw).getAttribute('aria-checked');
      const b = await page.locator(sw).boundingBox(); await page.mouse.click(b.x + b.width / 2, b.y - 5);
      assert.notEqual(await page.locator(sw).getAttribute('aria-checked'), before);
      await page.evaluate(() => Kis.openNew()); await page.waitForSelector(tid('add-page'));
      await sizes('.editor .modes button, .editor .places button, .editor .seg button, .editor .digits-row button, .editor .nudges button, [data-testid=rate-unknown], .editor .info-dot');
      await fit(); await shot('editor-targets');
      await hit(tid('ruler-start')); await hit(tid('ruler-end'));
      // The enlarged last digit still selects just that digit and keeps the native keyboard path.
      await page.click(tid('clock-end-d3')); await page.keyboard.type('5'); await page.click(tid('entry-done'));
      assert.match(await page.textContent(tid('clock-end')), /5$/);
      await page.click(tid('mode-hours')); await sizes('.editor .digits-row button'); await fit();
      await page.click(tid('mode-job')); await sizes('.editor .digits-row button'); await fit(); await shot('job-targets');
      await page.evaluate(() => Kis.closeEditor());
      if (await page.locator(tid('confirm-ok')).count()) await page.click(tid('confirm-ok'));
      await page.evaluate(() => Kis.openDetail('r02'));
      const contrast = await page.locator('[data-testid=shift-detail] [data-a=del]').evaluate(n => {
        const lum = color => color.match(/[\d.]+/g).slice(0, 3).map(Number).map(v => { v /= 255; return v <= .04045 ? v / 12.92 : ((v + .055) / 1.055) ** 2.4; }).reduce((s, x, i) => s + x * [.2126, .7152, .0722][i], 0);
        const s = getComputedStyle(n), a = lum(s.color), b = lum(s.backgroundColor);
        return (Math.max(a, b) + .05) / (Math.min(a, b) + .05);
      });
      assert.ok(contrast >= 4.5, `delete contrast ${contrast} at ${width}/${theme}`);
      await shot('delete-contrast'); assert.deepEqual(errors, []); await ctx.close();
    }
    console.log('review-targets: 320/360/390/412 light/dark, >=44px hit areas, actual switch/clock taps, no overflow and delete contrast >=4.5 passed');
  } finally { await browser.close(); server.close(); }
})().catch(e => { console.error(e); process.exit(1); });
