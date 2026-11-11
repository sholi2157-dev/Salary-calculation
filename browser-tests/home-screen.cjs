// 2.4 task 05: home-screen install. The manifest is valid and installable in Chromium, its shortcut URLs open the
// matching screen (start sheet / new-shift editor) once, and the iPhone-only hint in Settings → עזרה is dismissed for good.
// Light and dark at 390 px. Synthetic data only.
const assert = require('node:assert/strict'), http = require('node:http'), fs = require('node:fs'), path = require('node:path');
const { chromium } = require(process.env.PLAYWRIGHT_MODULE || 'playwright');
const ROOT = path.resolve('public');
const SHOTS = process.env.SCREENSHOT_DIR || '/tmp/home-screen-shots'; fs.mkdirSync(SHOTS, { recursive: true });
const rich = JSON.parse(fs.readFileSync('web-tests/fixtures/synthetic/rich-v2.json', 'utf8'));
const types = { '.html': 'text/html; charset=utf-8', '.js': 'text/javascript', '.css': 'text/css', '.json': 'application/json', '.ttf': 'font/ttf', '.png': 'image/png', '.webmanifest': 'application/manifest+json' };
const server = http.createServer((q, r) => {
  let p = decodeURIComponent(new URL(q.url, 'http://x').pathname); if (p === '/') p = '/index.html';
  if (p === '/api/config') { r.writeHead(200, { 'content-type': 'application/json' }); return r.end('{}'); }
  const f = path.join(ROOT, p); if (!f.startsWith(ROOT) || !fs.existsSync(f) || fs.statSync(f).isDirectory()) { r.writeHead(404); return r.end(); }
  r.writeHead(200, { 'content-type': types[path.extname(f)] || 'application/octet-stream', 'cache-control': 'no-store' }); fs.createReadStream(f).pipe(r);
});
const IPHONE = 'Mozilla/5.0 (iPhone; CPU iPhone OS 17_5 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.5 Mobile/15E148 Safari/604.1';
(async () => {
 await new Promise(r => server.listen(0, '127.0.0.1', r));
 const base = `http://127.0.0.1:${server.address().port}`;
 const browser = await chromium.launch({ executablePath: process.env.CHROMIUM_PATH || undefined, args: ['--no-sandbox', '--disable-dev-shm-usage', '--disable-gpu'] });
 const errors = [];
 const open = async ({ theme = 'light', ua, sw = 'block' } = {}) => {
  const ctx = await browser.newContext({ serviceWorkers: sw, viewport: { width: 390, height: 844 }, locale: 'he-IL', colorScheme: theme, userAgent: ua, hasTouch: !!ua, isMobile: !!ua });
  await ctx.route('**/*', route => new URL(route.request().url()).hostname === '127.0.0.1' ? route.continue() : route.abort());
  await ctx.addInitScript(([data]) => {
   if (sessionStorage.getItem('seeded')) return; sessionStorage.setItem('seeded', 'yes');
   for (const [key, value] of Object.entries(data)) localStorage.setItem(key, JSON.stringify(value));
   localStorage.setItem('kis_web_v1', JSON.stringify({ launched: 2, tourDone: true, whatsNewSeen: '2.4.0', notifyPending: false, notifyMonthly: false }));
   localStorage.setItem('kis_ai_invited_v1', '1'); localStorage.setItem('kis_signin_skip_v1', '1');
  }, [rich]);
  const page = await ctx.newPage(); page.on('pageerror', e => errors.push(e.message));
  return { ctx, page };
 };
 try {
  // 1. Manifest parses without errors and Chromium finds the site installable.
  { const ctx = await chromium.launchPersistentContext(fs.mkdtempSync('/tmp/home-screen-profile-'), { executablePath: process.env.CHROMIUM_PATH || undefined,
      args: ['--no-sandbox', '--disable-dev-shm-usage', '--disable-gpu'], viewport: { width: 390, height: 844 }, locale: 'he-IL' });
    await ctx.route('**/*', route => new URL(route.request().url()).hostname === '127.0.0.1' ? route.continue() : route.abort());
    const page = ctx.pages()[0] || await ctx.newPage(); page.on('pageerror', e => errors.push(e.message));
    await page.goto(base + '/'); await page.waitForSelector('[data-testid=home-hero]');
    await page.evaluate(() => navigator.serviceWorker.ready);
    const cdp = await ctx.newCDPSession(page);
    const m = await cdp.send('Page.getAppManifest');
    assert.match(m.url, /\/web\/manifest\.webmanifest\?v=/); assert.deepEqual(m.errors, [], JSON.stringify(m.errors));
    const parsed = JSON.parse(m.data); assert.equal(parsed.short_name, 'כיס'); assert.equal(parsed.dir, 'rtl');
    const inst = await cdp.send('Page.getInstallabilityErrors');
    assert.deepEqual(inst.installabilityErrors, [], JSON.stringify(inst.installabilityErrors));
    console.log('installability:', JSON.stringify(inst.installabilityErrors));
    await ctx.close(); }

  for (const theme of ['light', 'dark']) {
   // 2. "התחלת משמרת": the start sheet with the default place chosen; the address loses the action.
   { const { ctx, page } = await open({ theme });
     await page.goto(base + '/?action=start-shift');
     await page.waitForSelector('[data-testid=live-go]');
     assert.equal(await page.evaluate(() => location.search), '');
     assert.equal(await page.locator('.sheet .chip[aria-pressed=true]').count(), 1);
     await page.screenshot({ path: path.join(SHOTS, `shortcut-start-shift-${theme}.png`) });
     await page.reload(); await page.waitForSelector('[data-testid=home-hero]'); await page.waitForTimeout(300);
     assert.equal(await page.locator('[data-testid=live-go]').count(), 0, 'a reload does not reopen the start sheet');
     await ctx.close(); }
   // 3. "הוספת משמרת": the new-shift editor.
   { const { ctx, page } = await open({ theme });
     await page.goto(base + '/?action=add-shift');
     await page.waitForSelector('.editor');
     assert.equal(await page.evaluate(() => location.search), '');
     assert.equal(await page.evaluate(() => Kis.editorOpen), true);
     await page.screenshot({ path: path.join(SHOTS, `shortcut-add-shift-${theme}.png`) });
     await ctx.close(); }
   // 4. Plain launch (start_url) as from the home screen.
   { const { ctx, page } = await open({ theme, ua: IPHONE });
     await page.goto(base + '/'); await page.waitForSelector('[data-testid=home-hero]');
     assert.equal(await page.locator('.sheet, .editor').count(), 0);
     const top = await page.evaluate(() => getComputedStyle(document.getElementById('page')).paddingTop);
     assert.equal(top, '12px', 'no extra top space in a browser tab');
     await page.screenshot({ path: path.join(SHOTS, `launch-home-${theme}.png`) });
     // 5. iPhone Safari tab: the one-time hint in Settings → עזרה; "הבנתי" removes it for good.
     await page.evaluate(() => Kis.go('settings', { section: 'עזרה' }));
     const ok = page.locator('[data-testid=home-hint-ok]'); await ok.waitFor();
     assert.ok((await page.locator('.set-groups').innerText()).includes('בספארי: שיתוף ← ״הוספה למסך הבית״'));
     await ok.scrollIntoViewIfNeeded(); await page.screenshot({ path: path.join(SHOTS, `settings-help-hint-${theme}.png`) });
     await ok.click(); await page.waitForTimeout(150);
     assert.equal(await page.locator('[data-testid=home-hint-ok]').count(), 0);
     await page.reload(); await page.waitForSelector('[data-testid=home-hero]');
     await page.evaluate(() => Kis.go('settings', { section: 'עזרה' })); await page.waitForSelector('[data-testid=replay-tour]');
     assert.equal(await page.locator('[data-testid=home-hint-ok]').count(), 0, 'dismissed once, gone for good');
     await ctx.close(); }
  }
  // 6. A shift already running: "התחלת משמרת" stays on Home with the running shift, no second start sheet.
  { const { ctx, page } = await open();
    await page.goto(base + '/'); await page.waitForSelector('[data-testid=home-hero]');
    assert.equal(await page.evaluate(() => Kis.store.startTimer(Kis.store.data.categories[0].name, 50, '₪')), true);
    await page.goto(base + '/?action=start-shift'); await page.waitForSelector('[data-testid=home-hero]'); await page.waitForTimeout(300);
    assert.equal(await page.locator('[data-testid=live-go]').count(), 0);
    await ctx.close(); }
  // 7. Other browsers (Android/desktop Chrome) offer installing themselves: no hint there.
  { const { ctx, page } = await open();
    await page.goto(base + '/'); await page.waitForSelector('[data-testid=home-hero]');
    await page.evaluate(() => Kis.go('settings', { section: 'עזרה' })); await page.waitForSelector('[data-testid=replay-tour]');
    assert.equal(await page.locator('[data-testid=home-hint-ok]').count(), 0);
    await ctx.close(); }
  // 8. The icons as a home screen shows them (rendered from the shipped files), light and dark wallpaper.
  { const { ctx, page } = await open();
    for (const theme of ['light', 'dark']) {
      const bg = theme === 'light' ? '#D9DCE2' : '#0F1216', ink = theme === 'light' ? '#141925' : '#EDF0F6';
      await page.setContent(`<body dir="rtl" style="margin:0;background:${bg};font:13px sans-serif;color:${ink};display:flex;gap:28px;justify-content:center;padding:60px 0">
        ${[['apple-touch-icon.png', 'iPhone', '22%'], ['maskable-512.png', 'Android', '50%'], ['icon-192.png', 'Chrome', '0']].map(([f, l, r]) => `<figure style="margin:0;text-align:center"><img src="data:image/png;base64,${fs.readFileSync(path.join(ROOT, 'web/icons', f)).toString('base64')}" style="width:64px;height:64px;border-radius:${r}"><figcaption style="margin-top:6px">כיס</figcaption><small style="opacity:.6">${l}</small></figure>`).join('')}</body>`);
      await page.waitForFunction(() => [...document.images].every(i => i.complete && i.naturalWidth));
      await page.screenshot({ path: path.join(SHOTS, `icon-${theme}.png`), clip: { x: 0, y: 0, width: 390, height: 220 } });
    }
    await ctx.close(); }
  assert.deepEqual(errors, []);
  console.log('home-screen: PASS, screenshots in', SHOTS);
 } finally { await browser.close(); server.close(); }
})().catch(e => { console.error(e); process.exit(1); });
