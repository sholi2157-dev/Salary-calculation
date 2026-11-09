// Release-critical flows on the current site (Web 2.0, web/kis): disposable browser contexts, synthetic data only.
// The RC13 version drove the old screens (#tutorial-dialog with 9 steps, #add-modal, #settings-dialog, #history hash
// routes), which index.html has not loaded since aa0b6b5 (Web 2.0). The same guarantees, on today's screens:
//  A. At 360/390/768/1440, a fresh browser: the launch, then the tour step by step (RTL, nothing sideways at every step),
//     and nothing is written to the work data by onboarding; every page and the editor stay inside the width; a live
//     shift in $ survives a reload 2.5 hours in, shows $63.75 and saves exactly that, in $.
//  B. Existing data, 22 shifts in 8 places (a group shift with its worker, unknown fields), from the current key and from
//     the oldest keys: opening the site writes nothing; the 8 places are listed; editing one note and reloading keeps
//     every other shift, amount, worker and unknown field; a one-time copy of the original keys is kept first.
// No sideways scroll, RTL, and no JavaScript error anywhere. (Back/forward hash routes were RC13's; the site has none now.)
const assert = require('node:assert/strict'), http = require('node:http'), fs = require('node:fs'), path = require('node:path');
const { chromium } = require(process.env.PLAYWRIGHT_MODULE || 'playwright');
const ROOT = path.resolve('public'), OUT = process.env.SCREENSHOT_DIR || 'browser-evidence'; fs.mkdirSync(OUT, { recursive: true });
const types = { '.html': 'text/html; charset=utf-8', '.js': 'text/javascript', '.css': 'text/css', '.json': 'application/json', '.ttf': 'font/ttf' };
const server = http.createServer((q, r) => {
  let p = decodeURIComponent(new URL(q.url, 'http://x').pathname); if (p === '/') p = '/index.html';
  if (p === '/api/config') { r.writeHead(200, { 'content-type': 'application/json' }); return r.end('{}'); }
  const f = path.join(ROOT, p); if (!f.startsWith(ROOT) || !fs.existsSync(f) || fs.statSync(f).isDirectory()) { r.writeHead(404); return r.end(); }
  r.writeHead(200, { 'content-type': types[path.extname(f)] || 'application/octet-stream', 'cache-control': 'no-store' }); fs.createReadStream(f).pipe(r);
});
const tid = t => `[data-testid="${t}"]`;
const shoot = (p, name, width) => [360, 390, 1440].includes(width) ? p.screenshot({ path: path.join(OUT, `${name}-${width}.png`) }) : null;
(async () => {
 await new Promise(r => server.listen(0, '127.0.0.1', r));
 const url = `http://127.0.0.1:${server.address().port}`;
 const browser = await chromium.launch({ executablePath: process.env.CHROMIUM_PATH || undefined, args: ['--no-sandbox', '--disable-dev-shm-usage', '--disable-gpu'] });
 const errors = [];
 const context = async (width, height, seed) => {
  const c = await browser.newContext({ serviceWorkers: 'block', viewport: { width, height }, locale: 'he-IL', hasTouch: width < 600, isMobile: width < 600 });
  await c.route('**/*', route => new URL(route.request().url()).hostname === '127.0.0.1' ? route.continue() : route.abort());
  // Only the site's own pages (an opaque-origin frame has no storage to seed).
  if (seed) await c.addInitScript(data => { if (location.protocol !== 'http:') return; try { if (sessionStorage.getItem('seeded')) return; } catch { return; } sessionStorage.setItem('seeded', 'yes'); for (const [k, v] of Object.entries(data)) localStorage.setItem(k, v); }, seed);
  c.on('page', p => p.on('pageerror', e => errors.push(e.message)));
  return c;
 };
 const bounds = async (p, where) => {
  assert.equal(await p.evaluate(() => document.documentElement.scrollWidth <= innerWidth), true, `${where}: sideways scroll`);
  assert.equal(await p.evaluate(() => document.documentElement.dir), 'rtl', `${where}: not RTL`);
 };
 const work = p => p.evaluate(() => localStorage.getItem('work_complete_backup'));
 try {
  // ---- A. A fresh browser at four widths ----
  for (const width of [360, 390, 768, 1440]) {
   const c = await context(width, 844), p = await c.newPage();
   await p.goto(url); await p.waitForSelector(tid('launch-line-1')); await bounds(p, `${width} launch`);
   await shoot(p, 'launch', width);
   await p.click(tid('launch-next')); await p.waitForSelector(tid('launch-first')); await p.click(tid('launch-later'));
   await p.waitForSelector(tid('tour-progress'));
   const steps = Number((await p.textContent(tid('tour-progress'))).match(/מתוך (\d+)/)[1]);
   assert.ok(steps >= 4, 'a short tour');
   for (let i = 1; i <= steps; i++) {
    assert.match(await p.textContent(tid('tour-progress')), new RegExp(`^${i} מתוך ${steps}`)); await bounds(p, `${width} tour ${i}`);
    if (i === 2 && width === 390) await p.screenshot({ path: path.join(OUT, 'tour-390.png') });
    await p.click(tid('tour-next'));
   }
   await p.waitForSelector(tid('tour'), { state: 'detached' }); await p.waitForSelector(tid('home-hero'));
   await bounds(p, `${width} home`); await shoot(p, 'home', width);
   for (const page of ['history', 'settings']) { await p.click(tid('tab-' + page)); await bounds(p, `${width} ${page}`); await shoot(p, page, width); }
   // With no place yet, "+" asks for the place first ($ here), then opens the editor on it.
   await p.click(tid('add-shift')); await p.waitForSelector(tid('place-sheet'));
   assert.equal(await work(p), null, 'onboarding wrote work data');
   await p.fill(`${tid('place-sheet')} ${tid('place-name')}`, 'בדיקה'); await p.fill(`${tid('place-sheet')} ${tid('place-rate')}`, '25.5');
   await p.click(`${tid('place-sheet')} [data-c="$"]`); await p.click(`${tid('place-sheet')} ${tid('place-save')}`);
   await p.waitForSelector(tid('save-shift')); await bounds(p, `${width} editor`); await shoot(p, 'form', width);
   assert.equal(await p.getAttribute('.editor-in [data-cur="$"]', 'aria-pressed'), 'true');
   await p.click(tid('editor-close')); if (await p.isVisible(tid('confirm-ok'))) await p.click(tid('confirm-ok'));
   await p.waitForSelector(tid('save-shift'), { state: 'detached' });
   assert.equal(JSON.parse(await work(p)).entries.length, 0, 'closing the editor saved a shift');

   // A live shift in $ on that place.
   await p.click(tid('tab-home')); await p.click(tid('live-start'));
   await p.waitForSelector(tid('live-start-sheet')); await p.click(tid('live-go')); await p.waitForSelector(tid('live-card'));
   await p.evaluate(() => { const k = WorkRuntime.timerKey(null), t = JSON.parse(localStorage.getItem(k)); localStorage.setItem(k, JSON.stringify({ ...t, startedAt: Date.now() - 2.5 * 3600000 })); });
   await p.reload(); await p.waitForSelector(tid('live-card'));
   assert.match(await p.textContent(tid('live-clock')), /^02:30:/);
   assert.match(await p.textContent(tid('live-money')), /\$\s*63\.75/);
   await bounds(p, `${width} live`);
   await p.click(tid('live-stop'));
   await p.waitForFunction(() => (JSON.parse(localStorage.getItem('work_complete_backup')) || { entries: [] }).entries.length === 1);
   const saved = JSON.parse(await work(p)).entries[0];
   assert.equal(saved.currency, '$'); assert.equal(saved.category, 'בדיקה'); assert.ok(Math.abs(saved.totalEarnings - 63.75) < 0.03, `saved ${saved.totalEarnings}`);
   assert.equal(await p.evaluate(() => WorkRuntime.readTimer(localStorage, null)), null);
   await c.close(); console.log(`PASS: fresh launch/tour/pages/editor/live $ shift/reload ${width}px`);
  }

  // ---- B. Existing data: 22 shifts, 8 places, a worker and unknown fields; the current key and the oldest keys ----
  const entries = Array.from({ length: 22 }, (_, i) => ({ id: 'existing-' + i, category: 'קטגוריה ' + (i % 8), date: new Date(2026, 8, 1 + i, 12).getTime(), createdAt: 100 + i,
    hours: 2.25, hourlyRate: 35.5, totalEarnings: 90.17 + i, currency: i % 2 ? '$' : '₪', isPaid: i % 2 === 0, notes: 'מקורי ' + i, isGroupShift: i === 0,
    groupWorkersJson: i === 0 ? '[{"name":"בדיקה","hours":3,"workerRate":17.25,"employerRate":29,"isPaid":true}]' : '', customExtension: 'preserve-' + i }));
  const cats = Array.from({ length: 8 }, (_, i) => ({ name: 'קטגוריה ' + i, defaultRate: 35.5 }));
  const snapshot = JSON.stringify({ entries, categories: cats, workers: [{ name: 'בדיקה' }], webPreferences: { mainCurrency: '$', defaultCategory: 'קטגוריה 3', categoryCurrencies: { 'קטגוריה 3': '$' } } });
  const seen = { kis_web_v1: JSON.stringify({ launched: 2, tourDone: true }), kis_ai_invited_v1: '1', kis_signin_skip_v1: '1' };
  for (const [label, seed] of [['current key', { work_complete_backup: snapshot }],
   ['oldest keys', { user_work_shifts: JSON.stringify(entries), work_transfer_meta: JSON.stringify({ categories: cats, workers: [{ name: 'בדיקה' }] }), work_default_currency: '$' }]]) {
   const c = await context(390, 844, { ...seed, ...seen }), p = await c.newPage();
   await p.goto(url); await p.waitForSelector(tid('home-hero'));
   // Opening writes nothing.
   for (const [k, v] of Object.entries(seed)) assert.equal(await p.evaluate(key => localStorage.getItem(key), k), v, `${label}: ${k} changed on open`);
   const loaded = await p.evaluate(() => window.Kis.store.data.entries.map(e => [e.id, e.totalEarnings, e.currency, e.groupWorkersJson, e.customExtension]));
   assert.deepEqual(loaded.sort(), entries.map(e => [e.id, e.totalEarnings, e.currency, e.groupWorkersJson, e.customExtension]).sort());
   // The 8 places are listed.
   await p.click(tid('tab-settings')); if (await p.isVisible(tid('settings-open-עבודה'))) await p.click(tid('settings-open-עבודה'));
   await p.click(tid('places-button')); await p.waitForSelector(tid('places-panel'));
   assert.equal(await p.locator(`${tid('places-panel')} [data-drag]`).count(), 8); await bounds(p, `${label} places`);
   if (label === 'current key') await p.screenshot({ path: path.join(OUT, 'categories-existing-390.png') });
   await p.keyboard.press('Escape'); await p.waitForSelector(tid('places-sheet'), { state: 'detached' });
   // One note edited; after a reload everything else is as it was.
   await p.click(tid('tab-history')); await p.click(tid('shift-existing-1')); await p.click(tid('detail-edit')); await p.waitForSelector(tid('save-shift'));
   if (!await p.isVisible(tid('notes-input'))) await p.click(tid('editor-note'));
   await p.fill(tid('notes-input'), 'שינוי מטאדאטה'); await p.click(tid('save-shift')); await p.waitForSelector(tid('save-shift'), { state: 'detached' });
   await p.reload(); await p.waitForSelector(tid('home-hero'));
   const after = JSON.parse(await work(p));
   assert.equal(after.entries.length, 22); assert.equal(after.categories.length, 8);
   for (const e of entries) {
    const a = after.entries.find(x => x.id === e.id); assert.ok(a, `${label}: ${e.id} lost`);
    assert.equal(a.totalEarnings, e.totalEarnings); assert.equal(a.currency, e.currency); assert.equal(a.hourlyRate, e.hourlyRate); assert.equal(a.isPaid, e.isPaid);
    assert.equal(a.groupWorkersJson, e.groupWorkersJson); assert.equal(a.customExtension, e.customExtension);
    assert.equal(a.notes, e.id === 'existing-1' ? 'שינוי מטאדאטה' : e.notes);
   }
   assert.deepEqual(after.workers, [{ name: 'בדיקה' }]);
   // The one-time copy taken before the first write holds the original keys exactly.
   const copy = JSON.parse(await p.evaluate(() => localStorage.getItem('work_pre_v23_snapshot_v1')));
   for (const [k, v] of Object.entries(seed)) assert.equal(copy.values[k], v, `${label}: the copy of ${k} differs`);
   await c.close(); console.log(`PASS: existing 22/8 data, worker and unknown fields kept through open and edit/reload (${label})`);
  }
  assert.deepEqual(errors, []);
  console.log('PASS: no application JavaScript errors; RTL, mobile/tablet/desktop; synthetic data only.');
 } finally { await browser.close(); server.close(); }
})().catch(e => { console.error(e); process.exitCode = 1; });
