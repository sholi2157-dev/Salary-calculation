// RC15 guarantees on the current site (Web 2.0, web/kis) at 360/390/768/1440: real DOM and keyboard, disposable synthetic
// local data. The RC15 version drove the old screens (#settings-dialog, #default-category-button, #live-shift-dialog),
// which index.html has not loaded since aa0b6b5 (Web 2.0). The same guarantees, on today's screens:
//  - the default place: shown in Settings, changed in the places panel, kept; a new place starts in the main currency;
//  - a place without its own currency follows the main one ($), an explicit one keeps it (₪), in the live-shift sheet too;
//  - a live shift: kept across a reload with its currency; cancel asks first, keeping it when the question is dismissed;
//    confirmed, it is cancelled with an undo that brings back the same shift;
//  - stopping: a stop that waits for confirmation never adds a row once another tab replaced the timer (stale guard);
//    a normal stop saves one row with the timer's own id, currency and rate;
//  - feedback: Enter makes a new line and the sheet stays open;
//  - no sideways scroll and no JavaScript error.
// Not checked here, reported instead (the fix discards a timer, out of this test-only change): a confirmed "cancel" of a
// live shift that another tab replaced meanwhile cancels the replacement (app.cancelLive has no id check, unlike stopLive).
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
const seed = { formatVersion: 2, entries: [], workers: [], categories: [{ name: 'ללא מטבע', defaultRate: 37 }, { name: 'שקל מפורש', defaultRate: 20 }],
  webPreferences: { mainCurrency: '$', defaultCategory: 'ללא מטבע', categoryCurrencies: { 'שקל מפורש': '₪' } } };
const data = p => p.evaluate(() => JSON.parse(localStorage.getItem('work_complete_backup')));
const timer = p => p.evaluate(() => WorkRuntime.readTimer(localStorage, null));
/** A phone's Settings is a list of categories (M-31); a wider window shows every group. */
async function section(p, name) { await p.click(tid('tab-settings')); const b = p.locator(tid('settings-open-' + name)); if (await b.isVisible()) await b.click(); }
(async () => {
 await new Promise(r => server.listen(0, '127.0.0.1', r));
 const url = `http://127.0.0.1:${server.address().port}`;
 const browser = await chromium.launch({ executablePath: process.env.CHROMIUM_PATH || undefined, args: ['--no-sandbox', '--disable-dev-shm-usage', '--disable-gpu'] });
 const errors = [];
 try {
  for (const width of [360, 390, 768, 1440]) {
   const context = await browser.newContext({ serviceWorkers: 'block', viewport: { width, height: 844 }, locale: 'he-IL' });
   await context.route('**/*', route => new URL(route.request().url()).hostname === '127.0.0.1' ? route.continue() : route.abort());
   await context.addInitScript(d => { if (sessionStorage.getItem('seeded')) return; sessionStorage.setItem('seeded', '1');
     localStorage.setItem('work_complete_backup', JSON.stringify(d)); localStorage.setItem('kis_web_v1', JSON.stringify({ launched: 2, tourDone: true }));
     localStorage.setItem('kis_ai_invited_v1', '1'); localStorage.setItem('kis_signin_skip_v1', '1'); }, seed);
   const p = await context.newPage(); p.on('pageerror', e => errors.push(e.message));
   await p.goto(url); await p.waitForSelector(tid('home-hero'));
   const wide = () => p.evaluate(() => document.documentElement.scrollWidth <= innerWidth);

   // Default place: shown in Settings, changed by a tap in the places panel, kept.
   await section(p, 'עבודה');
   assert.match(await p.textContent(tid('places-button')), /ללא מטבע/);
   await p.click(tid('places-button')); await p.waitForSelector(tid('places-panel'));
   await p.click('[data-def="שקל מפורש"]');
   await p.waitForFunction(() => document.querySelector('[data-def="שקל מפורש"]')?.getAttribute('aria-pressed') === 'true');
   assert.equal((await data(p)).webPreferences.defaultCategory, 'שקל מפורש');
   // A new place starts in the main currency ($); adding it changes nothing else.
   await p.click(tid('place-add')); await p.waitForSelector(tid('place-sheet'));
   assert.equal(await p.locator(`${tid('place-sheet')} [data-c="$"]`).getAttribute('aria-pressed'), 'true');
   await p.fill(`${tid('place-sheet')} ${tid('place-name')}`, 'חדש ' + width); await p.fill(`${tid('place-sheet')} ${tid('place-rate')}`, '25');
   await p.click(`${tid('place-sheet')} ${tid('place-save')}`); await p.waitForSelector(tid('place-sheet'), { state: 'detached' });
   let d = await data(p);
   assert.deepEqual(d.categories.map(c => c.name), ['ללא מטבע', 'שקל מפורש', 'חדש ' + width]);
   assert.equal(d.webPreferences.defaultCategory, 'שקל מפורש');
   assert.ok(await wide(), `${width}: places sideways scroll`);
   await p.screenshot({ path: path.join(OUT, 'rc15-default-' + width + '.png') });
   await p.keyboard.press('Escape'); await p.waitForSelector(tid('places-sheet'), { state: 'detached' });
   assert.match(await p.textContent(tid('places-button')), /שקל מפורש/);

   // The live-shift sheet: the default (explicit ₪) and a place without its own currency ($, the main one).
   await p.click(tid('tab-home')); await p.click(tid('live-start')); await p.waitForSelector(tid('live-start-sheet'));
   const pressed = () => p.locator(`${tid('live-start-sheet')} .seg [aria-pressed="true"]`).textContent();
   assert.equal(await pressed(), '₪');
   await p.click(`${tid('live-start-sheet')} [data-c="ללא מטבע"]`); assert.equal(await pressed(), '$');
   await p.click(tid('live-go')); await p.waitForSelector(tid('live-card'));
   const started = await timer(p);
   assert.equal(started.category, 'ללא מטבע'); assert.equal(started.currency, '$'); assert.equal(started.rate, 37);
   // Kept across a reload, with its currency.
   await p.reload(); await p.waitForSelector(tid('live-card'));
   assert.deepEqual(await timer(p), started); assert.match(await p.textContent(tid('live-card')), /ללא מטבע/);
   // Cancel asks first; dismissing the question keeps the shift.
   await p.click(tid('live-cancel')); await p.waitForSelector(tid('confirm-ok'));
   await p.keyboard.press('Escape'); await p.waitForSelector(tid('confirm-ok'), { state: 'detached' });
   assert.deepEqual(await timer(p), started);
   // Confirmed: cancelled, no row, and the undo brings back the same shift.
   await p.click(tid('live-cancel')); await p.click(tid('confirm-ok'));
   await p.waitForFunction(() => WorkRuntime.readTimer(localStorage, null) === null);
   assert.equal((await data(p)).entries.length, 0);
   await p.click(tid('toast-action')); await p.waitForSelector(tid('live-card'));
   assert.deepEqual(await timer(p), started);

   // Stale guard: a stop waiting for its confirmation (under a minute) adds nothing once another tab replaced the timer.
   await p.click(tid('live-stop')); await p.waitForSelector(tid('confirm-ok'));
   await p.evaluate(() => { const k = WorkRuntime.timerKey(null), t = JSON.parse(localStorage.getItem(k)); localStorage.setItem(k, JSON.stringify({ ...t, id: 'replacement', currency: '₪', startedAt: Date.now() - 3600000 })); });
   await p.click(tid('confirm-ok'));
   assert.equal((await data(p)).entries.length, 0, `${width}: a stale stop added a row`);
   assert.equal((await timer(p)).id, 'replacement');
   // A normal stop (an hour in) saves one row with the timer's id, currency and rate; the timer is gone.
   await p.reload(); await p.waitForSelector(tid('live-card'));
   await p.click(tid('live-stop'));
   await p.waitForFunction(() => JSON.parse(localStorage.getItem('work_complete_backup')).entries.some(e => e.id === 'replacement'));
   d = await data(p);
   assert.equal(d.entries.length, 1);
   const row = d.entries[0];
   assert.equal(row.currency, '₪'); assert.equal(row.hourlyRate, 37); assert.equal(row.category, 'ללא מטבע'); assert.ok(Math.abs(row.hours - 1) < 0.05, `hours ${row.hours}`);
   assert.equal(await timer(p), null);

   // Feedback: the card shows the form at once (FB23-F1); Enter makes a new line and the form stays.
   await section(p, 'משוב ותקלות');
   const msg = p.locator(tid('feedback-message'));
   await msg.fill('שורה ראשונה'); await msg.press('End'); await msg.press('Enter'); await msg.pressSequentially('שורה שנייה');
   assert.equal(await msg.inputValue(), 'שורה ראשונה\nשורה שנייה');
   assert.ok(await msg.isVisible());
   assert.ok(await wide(), `${width}: feedback sideways scroll`);
   await p.screenshot({ path: path.join(OUT, 'rc15-feedback-' + width + '.png') });
   await context.close();
  }
  assert.deepEqual(errors, []);
  console.log('RC15 default place/new place currency, $ fallback/explicit ₪, live reload/cancel question/undo/stale stop guard/finish, feedback Enter and 4 widths passed');
 } finally { await browser.close(); server.close(); }
})().catch(e => { console.error(e); process.exit(1); });
