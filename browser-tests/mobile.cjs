// A phone (390px) on the current site (Web 2.0, web/kis): disposable browser context, synthetic data only. Run after
// npm run build. The RC13 version drove the old screens (#settings-dialog, #add-modal, #modal-*), which index.html has
// not loaded since aa0b6b5 (Web 2.0). The same guarantees, on today's screens:
//  - a new place with its own currency, made the default: a new shift opens on it, in its currency, after a reload;
//  - closing the editor saves nothing; saving an edit keeps the shift's currency and amount, after a reload too;
//  - search: one result, the field keeps the focus while typing; with the keyboard up (420px tall) clear keeps the search
//    open and empty; close shows every shift again;
//  - renaming a place (with a new rate and currency) renames its shifts and never changes their stored rate, amount or
//    currency; the default place cannot be deleted; deleting another asks first (cancel keeps it), then its shifts move
//    to the default place with their amounts unchanged;
//  - a long press selects a shift; select all → paid; delete asks first (cancel keeps), then deletes, also after reload;
//  - no sideways scroll at 390 and 360, no native dialog, no JavaScript error.
// Group shifts, sharing and backups have their own tests (job-team, group-detail, sharing, m1-data).
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
const tid = t => `[data-testid="${t}"]`;
const day = d => { const t = new Date(); t.setDate(t.getDate() - d); t.setHours(12, 0, 0, 0); return t.getTime(); };
const row = (id, category, date, hours, rate, currency, notes) => ({ id, category, date, createdAt: date, isTimeRange: false, startTime: null, endTime: null, hours, hourlyRate: rate,
  totalEarnings: Math.round(hours * rate * 100) / 100, isPaid: false, notes, currency, isGroupShift: false, employerRate: null, workerRate: null, groupWorkersJson: '', customExtension: 'keep' });
const seed = { entries: [row('ils', 'עצמאי', day(1), 1.5, 40.25, '₪', 'שקל סינתטי'), row('usd', 'דולר בדיקה', day(2), 2, 25.5, '$', 'דולר סינתטי')],
  categories: [{ name: 'עצמאי', defaultRate: 40.25 }, { name: 'דולר בדיקה', defaultRate: 25.5 }], workers: [],
  webPreferences: { mainCurrency: '₪', defaultCategory: 'עצמאי', categoryCurrencies: { 'דולר בדיקה': '$' } } };
(async () => {
 await new Promise(r => server.listen(0, '127.0.0.1', r));
 const browser = await chromium.launch({ executablePath: process.env.CHROMIUM_PATH || undefined, args: ['--no-sandbox', '--disable-dev-shm-usage', '--disable-gpu'] });
 const context = await browser.newContext({ serviceWorkers: 'block', viewport: { width: 390, height: 844 }, locale: 'he-IL', hasTouch: true, isMobile: true });
 await context.route('**/*', route => new URL(route.request().url()).hostname === '127.0.0.1' ? route.continue() : route.abort());
 await context.addInitScript(d => { if (sessionStorage.getItem('seeded')) return; sessionStorage.setItem('seeded', '1');
   localStorage.setItem('work_complete_backup', JSON.stringify(d)); localStorage.setItem('kis_web_v1', JSON.stringify({ launched: 2, tourDone: true }));
   localStorage.setItem('kis_ai_invited_v1', '1'); localStorage.setItem('kis_signin_skip_v1', '1'); }, seed);
 const page = await context.newPage(), errors = [];
 page.on('pageerror', e => errors.push(e.message)); page.on('dialog', d => { errors.push('Unexpected native dialog: ' + d.message()); d.dismiss(); });
 const state = () => page.evaluate(() => JSON.parse(localStorage.getItem('work_complete_backup')));
 const entry = async id => (await state()).entries.find(e => e.id === id);
 const wide = () => page.evaluate(() => document.documentElement.scrollWidth <= innerWidth);
 const places = async () => { await page.click(tid('tab-settings')); if (await page.isVisible(tid('settings-open-עבודה'))) await page.click(tid('settings-open-עבודה'));
   await page.click(tid('places-button')); await page.waitForSelector(tid('places-panel')); };
 const closeSheets = async () => { while (await page.locator('.sheet[aria-modal=true]').count()) { await page.keyboard.press('Escape'); await page.waitForTimeout(150); } };
 const editorOff = () => page.waitForSelector(tid('save-shift'), { state: 'detached' });
 try {
  await page.goto('http://127.0.0.1:' + server.address().port); await page.waitForSelector(tid('home-hero'));

  // A new place in $, made the default: a new shift opens on it, in $, after a reload.
  await places(); await page.click(tid('place-add')); await page.waitForSelector(tid('place-sheet'));
  const sheet = s => `${tid('place-sheet')} ${s}`;
  await page.fill(sheet(tid('place-name')), 'דולר חדש'); await page.fill(sheet(tid('place-rate')), '30'); await page.click(sheet('[data-c="$"]'));
  await page.click(sheet(tid('place-save'))); await page.waitForSelector(tid('place-sheet'), { state: 'detached' });
  await page.click('[data-def="דולר חדש"]');
  await page.waitForFunction(() => document.querySelector('[data-def="דולר חדש"]')?.getAttribute('aria-pressed') === 'true');
  let data = await state();
  assert.equal(data.webPreferences.defaultCategory, 'דולר חדש'); assert.equal(data.webPreferences.categoryCurrencies['דולר חדש'], '$');
  assert.equal(data.entries.length, 2, 'a place adds no shift');
  await page.reload(); await page.waitForSelector(tid('home-hero'));
  await page.click(tid('add-shift')); await page.waitForSelector(tid('save-shift'));
  assert.equal(await page.getAttribute(tid('place-דולר חדש'), 'aria-pressed'), 'true');
  assert.equal(await page.getAttribute('.editor-in [data-cur="$"]', 'aria-pressed'), 'true');
  await page.click(tid('editor-close')); if (await page.isVisible(tid('confirm-ok'))) await page.click(tid('confirm-ok')); await editorOff();

  // Closing the editor saves nothing; saving keeps the currency and the stored amount, after a reload too.
  const openUsd = async () => { await page.click(tid('tab-history')); await page.click(tid('shift-usd')); await page.click(tid('detail-edit')); await page.waitForSelector(tid('save-shift'));
    if (!await page.isVisible(tid('notes-input'))) await page.click(tid('editor-note')); };
  await openUsd(); await page.fill(tid('notes-input'), 'שינוי לביטול');
  await page.click(tid('editor-close')); if (await page.isVisible(tid('confirm-ok'))) await page.click(tid('confirm-ok')); await editorOff();
  assert.equal((await entry('usd')).notes, 'דולר סינתטי');
  await openUsd(); await page.fill(tid('notes-input'), 'עודכן'); await page.click(tid('save-shift')); await editorOff();
  await page.waitForSelector(tid('shift-detail'));
  assert.ok((await page.textContent(tid('shift-detail'))).includes('עודכן'), 'saved notes appear immediately in detail');
  await page.keyboard.press('Escape');
  await page.reload(); await page.waitForSelector(tid('home-hero'));
  let usd = await entry('usd');
  assert.equal(usd.notes, 'עודכן'); assert.equal(usd.currency, '$'); assert.equal(usd.totalEarnings, 51); assert.equal(usd.customExtension, 'keep');

  // Search: one result, focus kept while typing; with the keyboard up, clear keeps the search open; close shows all.
  await page.click(tid('tab-history')); await page.click(tid('history-search'));
  await page.locator(tid('history-search-input')).pressSequentially('עודכן');
  assert.equal(await page.locator(`${tid('history-list')} .shift[data-id]`).count(), 1);
  assert.equal(await page.locator(tid('history-search-input')).evaluate(el => el === document.activeElement), true, 'the search field lost the focus');
  await page.setViewportSize({ width: 390, height: 420 });
  await page.click(tid('history-search-clear'));
  assert.ok(await page.isVisible(tid('history-search-input'))); assert.equal(await page.inputValue(tid('history-search-input')), '');
  await page.click(tid('history-search-close'));
  assert.equal(await page.locator(tid('history-search-input')).count(), 0);
  assert.equal(await page.locator(`${tid('history-list')} .shift[data-id]`).count(), 2);
  await page.setViewportSize({ width: 390, height: 844 });

  // Rename a place with a new rate and currency: its shift is renamed, its stored rate, amount and currency stay.
  await places(); await page.click(tid('place-edit-דולר בדיקה'));
  await page.fill(`[data-inline-edit] ${tid('place-name')}`, 'דולר שונה'); await page.fill(`[data-inline-edit] ${tid('place-rate')}`, '99');
  await page.click('[data-inline-edit] [data-c="₪"]'); await page.click(`[data-inline-edit] ${tid('place-save')}`);
  await page.waitForSelector('[data-def="דולר שונה"]');
  usd = await entry('usd');
  assert.equal(usd.category, 'דולר שונה'); assert.equal(usd.currency, '$'); assert.equal(usd.hourlyRate, 25.5); assert.equal(usd.totalEarnings, 51);
  // The default place has no delete; another one asks first, and cancel keeps it.
  await page.click(tid('place-edit-דולר חדש'));
  assert.equal(await page.locator('[data-inline-edit] [data-a=del]').count(), 0, 'the default place can be deleted');
  await page.click(tid('place-edit-עצמאי'));
  await page.click('[data-inline-edit] [data-a=del]'); await page.waitForSelector(tid('confirm-ok'));
  await page.keyboard.press('Escape'); await page.waitForSelector(tid('confirm-ok'), { state: 'detached' });
  assert.ok((await state()).categories.some(c => c.name === 'עצמאי'));
  // Confirmed: its shift moves to the default place; amount and currency unchanged.
  if (!await page.isVisible('[data-inline-edit] [data-a=del]')) await page.click(tid('place-edit-עצמאי'));
  await page.click('[data-inline-edit] [data-a=del]'); await page.click(tid('confirm-ok'));
  await page.waitForFunction(() => !JSON.parse(localStorage.getItem('work_complete_backup')).categories.some(c => c.name === 'עצמאי'));
  data = await state(); const ils = data.entries.find(e => e.id === 'ils');
  assert.equal(data.entries.length, 2); assert.equal(ils.category, 'דולר חדש'); assert.equal(ils.currency, '₪'); assert.equal(ils.totalEarnings, 60.38); assert.equal(ils.hourlyRate, 40.25);
  await closeSheets();

  // A long press selects; select all → paid; delete asks first (cancel keeps), then deletes, also after a reload.
  await page.click(tid('tab-history'));
  // A finger held still on the card (the pointer events a touch screen sends; nothing in between cancels the press).
  const held = page.locator(tid('shift-ils'));
  await held.dispatchEvent('pointerdown', { button: 0, pointerType: 'touch', isPrimary: true });
  await page.waitForTimeout(650); await held.dispatchEvent('pointerup', { button: 0, pointerType: 'touch', isPrimary: true });
  await page.waitForSelector(tid('selection-bar')); assert.match(await page.textContent(tid('selection-count')), /נבחרו 1/);
  const act = s => page.click(`${tid('selection-bar')} [data-s=${s}]`);
  await act('all'); assert.match(await page.textContent(tid('selection-count')), /נבחרו 2/);
  await act('paid'); await page.waitForFunction(() => JSON.parse(localStorage.getItem('work_complete_backup')).entries.every(e => e.isPaid));
  if (!await page.isVisible(tid('selection-bar'))) { await page.click(`${tid('shift-ils')}`, { button: 'right' }); await act('all'); }
  await act('delete'); await page.waitForSelector(tid('confirm-ok'));
  await page.keyboard.press('Escape'); await page.waitForSelector(tid('confirm-ok'), { state: 'detached' });
  assert.equal((await state()).entries.length, 2, 'cancel deleted');
  if (!await page.isVisible(tid('selection-bar'))) { await page.click(`${tid('shift-ils')}`, { button: 'right' }); await act('all'); }
  await act('delete'); await page.click(tid('confirm-ok'));
  await page.waitForFunction(() => JSON.parse(localStorage.getItem('work_complete_backup')).entries.length === 0);
  await page.reload(); await page.waitForSelector(tid('home-hero'));
  assert.equal((await state()).entries.length, 0);

  assert.ok(await wide(), '390 sideways scroll');
  await page.setViewportSize({ width: 360, height: 740 }); assert.ok(await wide(), '360 sideways scroll');
  assert.deepEqual(errors, []);
  if (process.env.SCREENSHOT_DIR) { fs.mkdirSync(process.env.SCREENSHOT_DIR, { recursive: true }); await page.screenshot({ path: path.join(process.env.SCREENSHOT_DIR, 'mobile-home.png'), fullPage: true }); }
  console.log('PASS: phone 390px; new default place and its currency after reload; close/save edit after reload; search focus/420px keyboard; rename/delete/default protection with stored amounts unchanged; long press, bulk paid and delete accept/cancel.');
 } catch (e) { if (process.env.SCREENSHOT_DIR) await page.screenshot({ path: path.join(process.env.SCREENSHOT_DIR, 'failure.png'), fullPage: true }); throw e; }
 finally { await browser.close(); server.close(); }
})().catch(e => { console.error(e); process.exitCode = 1; });
