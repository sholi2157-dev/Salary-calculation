/*
 * Design decision "גלילה ומקלדת" in a real browser (synthetic data): when the keyboard takes the lower part of the screen
 * (simulated by shrinking the viewport, as Chrome on Android does), the focused field is scrolled into what is left and
 * the save bar / the sheet end above the keyboard. Safari only shrinks the visual viewport; that path needs a real phone.
 */
const assert = require('node:assert/strict'), http = require('node:http'), fs = require('node:fs'), path = require('node:path');
const { chromium } = require(process.env.PLAYWRIGHT_MODULE || 'playwright');
const ROOT = path.resolve('public'), rich = JSON.parse(fs.readFileSync('web-tests/fixtures/synthetic/rich-v2.json', 'utf8'));
const types = { '.html': 'text/html; charset=utf-8', '.js': 'text/javascript', '.css': 'text/css', '.json': 'application/json', '.ttf': 'font/ttf' };
const server = http.createServer((q, r) => { let p = decodeURIComponent(new URL(q.url, 'http://x').pathname); if (p === '/') p = '/index.html';
  if (p === '/api/config') { r.writeHead(200, { 'content-type': 'application/json' }); return r.end('{}'); }
  const f = path.join(ROOT, p); if (!f.startsWith(ROOT) || !fs.existsSync(f) || fs.statSync(f).isDirectory()) { r.writeHead(404); return r.end(); }
  r.writeHead(200, { 'content-type': types[path.extname(f)] || 'application/octet-stream' }); fs.createReadStream(f).pipe(r); });
(async () => {
  await new Promise(r => server.listen(0, r));
  const base = `http://127.0.0.1:${server.address().port}/`, browser = await chromium.launch();
  const ctx = await browser.newContext({ viewport: { width: 390, height: 844 }, locale: 'he-IL', serviceWorkers: 'block', hasTouch: true, isMobile: true });
  await ctx.addInitScript(d => { if (!sessionStorage.getItem('seeded')) { sessionStorage.setItem('seeded', '1'); localStorage.clear();
    for (const [k, v] of Object.entries(d)) localStorage.setItem(k, JSON.stringify(v));
    localStorage.setItem('kis_web_v1', JSON.stringify({ launched: 2, tourDone: true })); localStorage.setItem('kis_ai_invited_v1', '1'); localStorage.setItem('kis_signin_skip_v1', '1'); } }, rich);
  const page = await ctx.newPage(), errors = []; page.on('pageerror', e => errors.push(e.message));
  const tid = t => `[data-testid="${t}"]`, KB = 400; // what the keyboard leaves of an 844px screen
  const inView = async sel => page.evaluate(([s, h]) => { const r = document.querySelector(s).getBoundingClientRect(); return { top: r.top, bottom: r.bottom, ok: r.top >= 0 && r.bottom <= h + 1 }; }, [sel, KB]);
  await page.goto(base); await page.waitForSelector(tid('home-hero'));
  // Editor: the note at the bottom, then the keyboard opens.
  await page.evaluate(() => window.Kis.openNew()); await page.waitForTimeout(300);
  await page.click(tid('editor-note')); await page.waitForTimeout(200);
  await page.focus(tid('notes-input')); await page.setViewportSize({ width: 390, height: KB }); await page.waitForTimeout(900);
  const note = await inView(tid('notes-input')), save = await inView(tid('save-shift'));
  assert.ok(note.ok, 'the note stays visible above the keyboard: ' + JSON.stringify(note));
  assert.ok(save.ok, 'the save button stays above the keyboard: ' + JSON.stringify(save));
  await page.setViewportSize({ width: 390, height: 844 }); await page.keyboard.press('Escape'); await page.waitForTimeout(300);
  await page.evaluate(() => { localStorage.removeItem('kis_web_draft_v1'); }); await page.reload(); await page.waitForSelector(tid('home-hero'));
  // A sheet with a field: a new workplace.
  await page.evaluate(() => { window.Kis.go('settings'); window.KisSettings.placesSheet(window.Kis); }); await page.waitForTimeout(300);
  await page.click('text=מקום עבודה חדש'); await page.waitForTimeout(300);
  const field = await page.evaluate(() => { const i = [...document.querySelectorAll('.sheet input')].find(x => x.offsetParent && getComputedStyle(x).opacity !== '0'); i.setAttribute('data-kbtest', '1'); return !!i; });
  assert.ok(field);
  await page.focus('[data-kbtest]'); await page.setViewportSize({ width: 390, height: KB }); await page.waitForTimeout(900);
  const sheet = await page.evaluate(h => { const r = [...document.querySelectorAll('.sheet')].pop().getBoundingClientRect(); return { bottom: r.bottom, top: r.top, ok: r.bottom <= h + 1 && r.top >= 0 }; }, KB);
  const input = await inView('[data-kbtest]');
  assert.ok(sheet.ok, 'the sheet ends above the keyboard: ' + JSON.stringify(sheet));
  assert.ok(input.ok, 'the focused field is visible: ' + JSON.stringify(input));
  assert.deepEqual(errors, []);
  console.log('editor-keyboard: note and save bar above the keyboard; sheet and its field too');
  await browser.close(); server.close();
})().catch(e => { console.error(e); process.exit(1); });
