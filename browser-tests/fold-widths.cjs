// Folding phones (Galaxy Z Fold) and every other width: a 280–344px cover screen, a normal phone, an unfolded inner
// screen (~690–884px) and a computer. Run after npm run build. Synthetic data only.
//  - Nothing on Home, History, the shift editor, a shift's details or Settings reaches past the screen edge, except inside
//    the rows that are meant to scroll sideways (places, days, chips). History's cards used to be pushed 11–60px past the
//    left edge at 280–344px, and the editor's clocks 59px.
//  - Page titles and the navigation's words stay on one line ("היסטוריה" never breaks).
//  - On an unfolded phone or a tablet (600–1023px) the page is the phone layout scaled up: the whole width (no empty
//    bands at the sides), one card per row, everything larger than on a phone.
const assert = require('node:assert/strict'), http = require('node:http'), fs = require('node:fs'), path = require('node:path');
const { chromium } = require(process.env.PLAYWRIGHT_MODULE || 'playwright');
const ROOT = path.resolve('public');
const types = { '.html': 'text/html; charset=utf-8', '.js': 'text/javascript', '.css': 'text/css', '.json': 'application/json', '.ttf': 'font/ttf' };
const server = http.createServer((q, r) => {
  let p = decodeURIComponent(new URL(q.url, 'http://x').pathname); if (p === '/') p = '/index.html';
  if (p === '/api/config') { r.writeHead(200, { 'content-type': 'application/json' }); return r.end('{}'); }
  const f = path.join(ROOT, p); if (!f.startsWith(ROOT) || !fs.existsSync(f) || fs.statSync(f).isDirectory()) { r.writeHead(404); return r.end(); }
  r.writeHead(200, { 'content-type': types[path.extname(f)] || 'application/octet-stream', 'cache-control': 'no-store' }); fs.createReadStream(f).pipe(r);
});
const day = d => { const t = new Date(); t.setDate(t.getDate() - d); t.setHours(12, 0, 0, 0); return t.getTime(); };
const cats = [['מסעדה בשכונה', 45, '₪'], ['שמירה בלילות', 52.5, '₪'], ['Babysitting', 18, '$']];
const entries = Array.from({ length: 12 }, (_, i) => { const [category, rate, currency] = cats[i % 3], hours = 3 + (i % 5);
  return { id: 'e' + i, category, date: day(i * 2 + 1), createdAt: day(i * 2 + 1), isTimeRange: false, startTime: null, endTime: null, hours, hourlyRate: rate,
    totalEarnings: Math.round(hours * rate * 100) / 100, isPaid: i > 8, notes: i % 4 ? '' : 'הערה סינתטית', currency, isGroupShift: false, employerRate: null, workerRate: null,
    groupWorkersJson: '', customExtension: 'keep' }; });
const seed = { entries, categories: cats.map(([name, defaultRate]) => ({ name, defaultRate })), workers: [],
  webPreferences: { mainCurrency: '₪', defaultCategory: cats[0][0], categoryCurrencies: { Babysitting: '$' } } };
const SCROLLERS = '.places, .days, .chips, .selection-bar .acts';
(async () => {
  await new Promise(r => server.listen(0, '127.0.0.1', r));
  const browser = await chromium.launch({ executablePath: process.env.CHROMIUM_PATH || undefined, args: ['--no-sandbox', '--disable-dev-shm-usage', '--disable-gpu'] });
  const errors = [];
  try {
    for (const [width, height] of [[280, 653], [320, 720], [344, 882], [412, 915], [690, 829], [884, 1104], [1280, 800]]) {
      const context = await browser.newContext({ serviceWorkers: 'block', viewport: { width, height }, locale: 'he-IL', hasTouch: width < 1024, isMobile: width < 1024 });
      await context.route('**/*', route => new URL(route.request().url()).hostname === '127.0.0.1' ? route.continue() : route.abort());
      await context.addInitScript(d => { localStorage.setItem('work_complete_backup', JSON.stringify(d)); localStorage.setItem('kis_web_v1', JSON.stringify({ launched: 2, tourDone: true }));
        localStorage.setItem('kis_ai_invited_v1', '1'); localStorage.setItem('kis_signin_skip_v1', '1'); }, seed);
      const page = await context.newPage();
      page.on('pageerror', e => errors.push(`${width}: ${e.message}`));
      await page.goto('http://127.0.0.1:' + server.address().port); await page.waitForSelector('[data-testid="home-hero"]');
      const outside = scope => page.evaluate(([scope, scrollers]) => {
        const out = [];
        for (const el of document.querySelectorAll(scope + ' *')) {
          if (el.closest(scrollers) || el.closest('[aria-hidden="true"]')) continue;
          const r = el.getBoundingClientRect(); if (!r.width || !r.height) continue;
          if (r.left < -1 || r.right > innerWidth + 1) out.push(`${el.tagName}.${el.className} ${Math.round(r.left)}..${Math.round(r.right)} "${el.textContent.trim().slice(0, 20)}"`);
        }
        return out.slice(0, 5);
      }, [scope, SCROLLERS]);
      const oneLine = sel => page.evaluate(sel => [...document.querySelectorAll(sel)].filter(el => {
        const range = document.createRange(); range.selectNodeContents(el);
        return new Set([...range.getClientRects()].filter(r => r.width).map(r => Math.round(r.top))).size > 1;
      }).map(el => el.textContent.trim()), sel);
      const check = async (what, scope) => assert.deepEqual(await outside(scope), [], `${width}px ${what}: nothing past the screen edge`);
      await check('home', '.kis-page');
      assert.deepEqual(await oneLine('.nav button[data-page]'), [], `${width}px: navigation words on one line`);
      await page.click('[data-testid="tab-history"]'); await page.waitForSelector('.list .shift');
      await check('history', '.kis-page');
      assert.deepEqual(await oneLine('.page-title'), [], `${width}px: History's title on one line`);
      if (width >= 600 && width < 1024) {
        const column = await page.evaluate(() => document.querySelector('.kis-page').getBoundingClientRect().width);
        assert.ok(column >= width - 1, `${width}px: the page uses the whole width (${column}px)`);
      }
      if (width >= 600 && width < 1024) {
        const { lefts, zoom } = await page.evaluate(() => ({ zoom: parseFloat(getComputedStyle(document.documentElement).zoom),
          lefts: [...new Set([...document.querySelectorAll('[data-testid="history-list"] > .shift')].map(c => Math.round(c.getBoundingClientRect().left)))] }));
        assert.equal(lefts.length, 1, `${width}px: History is the phone's one card per row (${lefts})`);
        assert.ok(zoom > 1, `${width}px: the phone layout is scaled up (${zoom})`);
      }
      await page.click('.list .shift'); await page.waitForSelector('.sheet[aria-modal=true]'); await page.waitForTimeout(250);
      await check('shift details', '.sheet[aria-modal=true]');
      await page.keyboard.press('Escape'); await page.waitForSelector('.sheet[aria-modal=true]', { state: 'detached' });
      await page.click('.nav .add'); await page.waitForSelector('[data-testid="save-shift"]');
      await check('editor', '.editor');
      await page.keyboard.press('Escape'); await page.waitForSelector('[data-testid="save-shift"]', { state: 'detached' });
      await page.click('[data-testid="tab-settings"]');
      const work = page.locator('[data-testid="settings-open-עבודה"]'); if (await work.isVisible()) await work.click();
      await check('settings', '.kis-page');
      assert.equal(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth), true, `${width}px: no sideways page scroll`);
      await context.close();
    }
    assert.deepEqual(errors, []);
    console.log('fold-widths: 280/320/344/412/690/884/1280 — nothing past the edge, one-line titles and navigation, the phone layout scaled to the whole width when unfolded: PASS');
  } finally { await browser.close(); server.close(); }
})().catch(e => { console.error(e); process.exit(1); });
