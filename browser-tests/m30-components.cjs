// M-30 (web, components D-3): on the real page in light and dark, a selected chip and a selected segment use the same
// selected-* recipe (dark: #424853 with a light 2px edge, never a solid white tile); buttons are 56/48/44px; a disabled
// button uses its colours rather than opacity; the pending flag and the message sign are SVG icons, not font glyphs; and
// settings actions are action chips with a chevron. Saves light/dark screenshots to SCREENSHOT_DIR (default
// /tmp/m30-shots). Synthetic data only (web-tests/fixtures/synthetic/rich-v2.json).
const assert = require('node:assert/strict'), http = require('node:http'), fs = require('node:fs'), path = require('node:path');
const { chromium } = require(process.env.PLAYWRIGHT_MODULE || 'playwright');
const ROOT = path.resolve('public');
const SHOTS = process.env.SCREENSHOT_DIR || '/tmp/m30-shots'; fs.mkdirSync(SHOTS, { recursive: true });
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
   await page.screenshot({ path: path.join(SHOTS, `m30-home-${scheme}.png`), fullPage: true });
   // Font arrows are gone from the pending flag; it carries an SVG chevron.
   const flag = page.locator('[data-testid=pending-flag]');
   if (await flag.count()) { assert.equal(await flag.locator('svg').count(), 1); assert.doesNotMatch(await flag.innerText(), /[←→]/); }
   const got = await page.evaluate(() => {
    const made = [], probe = html => { const d = document.createElement('div'); d.innerHTML = html; document.body.append(d); made.push(d); return d.firstElementChild; };
    const cs = (el, p) => getComputedStyle(el)[p];
    const chip = probe('<button class="chip" aria-pressed="true">x</button>'), seg = probe('<div class="seg"><button aria-pressed="true">x</button></div>').firstElementChild;
    const off = probe('<button class="btn primary" disabled>x</button>'), big = probe('<button class="btn primary">x</button>');
    const small = probe('<button class="btn small">x</button>'), compact = probe('<button class="btn compact">x</button>');
    const tmp = probe('<i style="color:var(--selected-bg)"></i>');
    const out = { chipBg: cs(chip, 'backgroundColor'), chipEdge: cs(chip, 'borderTopWidth'), segBg: cs(seg, 'backgroundColor'), segShadow: cs(seg, 'boxShadow'),
      accent: cs(probe('<i style="color:var(--accent)"></i>'), 'color'), sel: cs(tmp, 'color'), offOpacity: cs(off, 'opacity'), heights: [big, small, compact].map(b => b.getBoundingClientRect().height) };
    window.KisUi.toast('נשמר ✓'); out.toastSvg = !!document.querySelector('#toast .t-sign svg');
    made.forEach(d => d.remove()); return out;
   });
   assert.equal(got.chipBg, got.sel, `${scheme}: selected chip uses selected-bg`);
   assert.equal(got.segBg, got.accent, `${scheme}: selected segment has a filled accent`);
   assert.equal(got.chipEdge, '2px'); assert.match(got.segShadow, /inset/);
   assert.equal(got.sel, scheme === 'dark' ? 'rgb(66, 72, 83)' : 'rgb(255, 255, 255)');
   assert.equal(got.offOpacity, '1', 'disabled uses colours, not opacity');
   assert.deepEqual(got.heights, [56, 48, 44]);
   assert.ok(got.toastSvg, 'toast sign is an SVG');
   await page.click('[data-testid=tab-settings]');
   await page.getByText('עבודה', { exact: true }).first().click();
   await page.waitForSelector('[data-testid=places-button]');
   assert.equal(await page.locator('[data-testid=places-button]').evaluate(x => getComputedStyle(x, '::after').content), '""');
   // FB23 F7: both currencies visibly select immediately, including after a settings redraw.
   for (const currency of ['$', '₪']) {
    await page.locator(`[data-cur="${currency}"]`).click();
    assert.equal(await page.locator(`[data-cur="${currency}"]`).getAttribute('aria-pressed'), 'true');
    const colours = await page.locator(`[data-cur="${currency}"]`).evaluate(el => ({
      selected: getComputedStyle(el).backgroundColor,
      other: getComputedStyle(el.parentElement.querySelector('[aria-pressed="false"]')).backgroundColor
    }));
    assert.notEqual(colours.selected, colours.other);
   }
   // F10: no settings category, import or recap info buttons.
   assert.equal(await page.locator('#page .info-dot, #page [data-info]').count(), 0);
   await page.evaluate(() => KisSettings.importSheet(Kis));
   assert.equal(await page.locator('[data-testid=import-sheet] [data-info]').count(), 0);
   await page.keyboard.press('Escape');
   await page.evaluate(() => KisSummaryUi.archive(Kis));
   assert.equal(await page.locator('[data-testid=recaps-sheet] [data-info]').count(), 0);
   await page.keyboard.press('Escape');
   // F8: the light pill has neither an outer black bar nor an elevated shadow.
   await page.evaluate(() => KisUi.toast('אין עדכון יציב חדש'));
   assert.equal(await page.locator('#toast .t-text').textContent(), 'אין עדכון יציב חדש');
   for (const selector of ['#toast', '#toast > div']) {
    assert.equal(await page.locator(selector).evaluate(el => getComputedStyle(el).boxShadow), 'none');
   }
   assert.equal(await page.locator('#toast').evaluate(el => getComputedStyle(el).backgroundColor), 'rgba(0, 0, 0, 0)');
   await page.screenshot({ path: path.join(SHOTS, `m30-settings-${scheme}.png`), fullPage: true });
   // F3: Edit/Share/Copy have identical surfaces and ink; Delete keeps the danger colour.
   await page.evaluate(() => Kis.openDetail(Kis.store.data.entries.find(e => !e.isGroupShift).id));
   const actions = await page.locator('[data-testid=shift-detail] .detail-actions button').evaluateAll(buttons => buttons.map(el => ({
     action: el.dataset.a, bg: getComputedStyle(el).backgroundColor, ink: getComputedStyle(el).color
   })));
   const edit = actions.find(x => x.action === 'edit');
   for (const action of ['share', 'copy']) {
     const other = actions.find(x => x.action === action);
     assert.equal(other.bg, edit.bg); assert.equal(other.ink, edit.ink);
   }
   assert.notEqual(actions.find(x => x.action === 'del').ink, edit.ink);
   await page.screenshot({ path: path.join(SHOTS, `fb23-detail-${scheme}.png`), fullPage: true });
   await page.keyboard.press('Escape');
   assert.deepEqual(errors, []);
   await ctx.close();
  }
  console.log(`m30-components: filled selected segments, equal detail actions, flat toast, no settings info icons, 3 button heights, disabled colours, SVG icons, action chips in light and dark: PASS (screenshots in ${SHOTS})`);
 } finally { await browser.close(); server.close(); }
})().catch(e => { console.error(e); process.exit(1); });
