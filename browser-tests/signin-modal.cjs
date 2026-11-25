// Returning/fresh visitors, all intro steps, native inert and its aria-hidden/focus fallback. No real sign-in.
const assert = require('node:assert/strict'), http = require('node:http'), fs = require('node:fs'), path = require('node:path');
const { chromium } = require(process.env.PLAYWRIGHT_MODULE || 'playwright');
const ROOT = path.resolve('public');
const server = http.createServer((q, r) => {
  const p = new URL(q.url, 'http://x').pathname;
  if (p === '/api/config') { r.setHeader('content-type', 'application/json'); return r.end('{"firebase":{}}'); }
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
    for (const width of [360, 390]) for (const theme of ['light', 'dark']) for (const fresh of [false, true]) {
      const ctx = await browser.newContext({ viewport: { width, height: 844 }, colorScheme: theme, serviceWorkers: 'block', reducedMotion: 'reduce' });
      await ctx.route('**/*', r => new URL(r.request().url()).hostname === '127.0.0.1' ? r.continue() : r.abort());
      await ctx.addInitScript(fresh => {
        localStorage.setItem('kis_web_v1', JSON.stringify({ launched: fresh ? 0 : 2, tourDone: true, whatsNewSeen: '2.4.1' }));
        // Hold the auth callback so the test can record the opener and pre-existing attribute values.
        window.firebase = { apps: [1], auth: () => ({ onAuthStateChanged: fn => { window.testSignedOut = () => fn(null); } }) };
      }, fresh);
      const page = await ctx.newPage(), errors = []; page.on('pageerror', e => errors.push(e.message));
      await page.goto(`http://127.0.0.1:${server.address().port}`);
      await page.waitForFunction(() => typeof testSignedOut === 'function');
      await page.evaluate(() => {
        const keep = document.createElement('div'); keep.id = 'pre-hidden'; keep.setAttribute('inert', 'existing'); keep.setAttribute('aria-hidden', 'false'); document.body.append(keep);
        const opener = document.querySelector('[data-testid=launch-next]') || document.querySelector('[data-testid=tab-settings]'); opener.focus();
        window.testOpener = opener;
        window.testBefore = [...document.body.children].map(n => [n, n.getAttribute('inert'), n.getAttribute('aria-hidden')]);
        testSignedOut();
      });
      await page.waitForSelector(tid('signin'));
      if (!fresh) {
        await page.evaluate(() => { KisWhatsNew.VERSION = '9.8.7'; testSignedOut(); });
        assert.equal(await page.textContent('[data-testid=signin] .intro-label.muted'), 'גרסה 9.8.7', 'version comes from the existing release constant');
        await page.evaluate(() => { KisWhatsNew.VERSION = '2.4.1'; testSignedOut(); });
      }
      const boundaries = await page.$$eval('.intro-chat .mine, .intro-ais b', nodes => {
        const rgb = c => c.match(/[\d.]+/g).slice(0, 3).map(Number);
        const lum = c => rgb(c).map(x => { x /= 255; return x <= .04045 ? x / 12.92 : ((x + .055) / 1.055) ** 2.4; }).reduce((s, x, i) => s + x * [.2126, .7152, .0722][i], 0);
        return nodes.map(n => { const c = getComputedStyle(n), border = lum(c.borderTopColor), bg = lum(getComputedStyle(document.querySelector('[data-testid=signin]')).backgroundColor);
          return { width: parseFloat(c.borderTopWidth), contrast: (Math.max(border, bg) + .05) / (Math.min(border, bg) + .05) }; });
      });
      assert.equal(boundaries.length, 6);
      assert.ok(boundaries.every(b => b.width >= 1 && b.contrast >= 1.5), JSON.stringify(boundaries));
      assert.equal(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth), true);
      if (process.env.SCREENSHOT_DIR && !fresh) {
        fs.mkdirSync(process.env.SCREENSHOT_DIR, { recursive: true });
        await page.screenshot({ path: path.join(process.env.SCREENSHOT_DIR, `welcome-${width}-${theme}.png`) });
      }
      for (const next of ['intro-next', 'intro-next-2', null]) {
        assert.equal(await page.evaluate(() => [...document.body.children].filter(n => n !== document.querySelector('[data-testid=signin]')).every(n => n.inert && n.getAttribute('aria-hidden') === 'true')), true);
        for (const key of ['Tab', 'Shift+Tab']) for (let i = 0; i < 10; i++) {
          await page.keyboard.press(key);
          assert.equal(await page.evaluate(() => document.querySelector('[data-testid=signin]').contains(document.activeElement)), true);
        }
        await page.evaluate(() => document.querySelector('[data-testid=tab-home]').focus());
        await page.keyboard.press('Enter');
        assert.equal(await page.evaluate(() => Kis.page), 'home', 'background navigation cannot run');
        if (next && await page.locator(tid(next)).count()) await page.click(tid(next));
      }
      // Emulate a browser without inert. The shared dialog trap must still reject background focus/keyboard actions.
      await page.evaluate(() => { document.querySelector('#page').removeAttribute('inert'); document.querySelector('.nav-wrap').removeAttribute('inert'); document.querySelector('[data-testid=tab-settings]').focus(); });
      await page.keyboard.press('Enter');
      assert.equal(await page.evaluate(() => Kis.page), 'home');
      await page.evaluate(() => { const n = document.createElement('button'); n.id = 'late-background'; document.body.append(n); });
      assert.equal(await page.$eval('#late-background', n => n.inert && n.getAttribute('aria-hidden') === 'true'), true);
      // Offline continuation exercises the actual close path and restores exact attributes and the original opener.
      await page.evaluate(() => { KisAccount.state.status = 'offline'; testSignedOut(); });
      await page.click(tid('signin-later'));
      assert.equal(await page.evaluate(() => testBefore.every(([n, inert, aria]) => n.getAttribute('inert') === inert && n.getAttribute('aria-hidden') === aria)), true);
      assert.equal(await page.evaluate(() => document.activeElement === testOpener), true);
      assert.equal(await page.$eval('#late-background', n => n.getAttribute('inert') === null && n.getAttribute('aria-hidden') === null), true);
      assert.equal(await page.$eval('#toast', n => n.parentNode === document.body), true);
      if (fresh) { await page.click(tid('launch-next')); await page.click(tid('launch-later')); }
      await page.click(tid('tab-settings'));
      assert.equal(await page.evaluate(() => Kis.page), 'settings', 'navigation works again');
      assert.deepEqual(errors, []); await ctx.close();
    }
    console.log('signin-modal: 360/390 light/dark, version source, bubble/chip boundary contrast >=1.5, fresh/returning modal isolation and exact restoration passed');
  } finally { await browser.close(); server.close(); }
})().catch(e => { console.error(e); process.exit(1); });
