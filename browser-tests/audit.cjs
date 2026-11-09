// Release-critical cross-tab flows on the current site (Web 2.0, web/kis), at four widths. Disposable browser contexts and
// synthetic data only. The RC13 version of this test drove the old screens (editShift, #add-modal), which index.html has
// not loaded since aa0b6b5 (Web 2.0); the behaviour it guarded is the same and is checked here on today's editor:
//  1. Saving an edit while another tab has a newer copy of the shift: nothing is overwritten, the editor stays open with
//     what was typed, and the reason is shown.
//  2. Saving an edit of a shift another tab deleted: the shift is not brought back, the draft stays, the reason is shown.
// And no sideways scroll at any width.
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
const seed = { entries: [{ id: 'audit', category: 'Work', date: Date.now() - 2 * 864e5, createdAt: 1, hours: 2, hourlyRate: 40, totalEarnings: 79.13, isPaid: false, currency: '$', notes: 'saved', isTimeRange: false, customExtension: 'keep' }],
  categories: [{ name: 'Work', defaultRate: 40 }], workers: [], webPreferences: { mainCurrency: '$', defaultCategory: 'Work', categoryCurrencies: { Work: '$' } } };
const stored = p => p.evaluate(() => JSON.parse(localStorage.getItem('work_complete_backup')).entries);
/** Another tab writes the shared data; this tab hears it through the browser's storage event, as with a real second tab. */
const elsewhere = (other, fn) => other.evaluate(fn);
async function openEdit(p) {
  await p.click(tid('tab-history')); await p.click(tid('shift-audit')); await p.click(tid('detail-edit'));
  await p.waitForSelector(tid('save-shift'));
  // A shift with a note opens with the extras shown; otherwise "הפסקה · הערה · בונוס" opens them.
  if (!await p.isVisible(tid('notes-input'))) await p.click(tid('editor-note'));
  await p.waitForSelector(tid('notes-input'));
}
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
     if (localStorage.getItem('work_complete_backup')) return; // the second tab shares the first one's data
     localStorage.setItem('work_complete_backup', JSON.stringify(d)); localStorage.setItem('kis_web_v1', JSON.stringify({ launched: 2, tourDone: true }));
     localStorage.setItem('kis_ai_invited_v1', '1'); localStorage.setItem('kis_signin_skip_v1', '1'); }, seed);
   const p = await context.newPage(); p.on('pageerror', e => errors.push(e.message));
   await p.goto(url); await p.waitForSelector(tid('home-hero'));
   const other = await context.newPage(); other.on('pageerror', e => errors.push(e.message));
   await other.goto(url); await other.waitForSelector(tid('home-hero'));

   // 1. Another tab has a newer copy of the shift: saving here overwrites nothing and keeps what was typed.
   await openEdit(p); await p.fill(tid('notes-input'), 'my unsaved edit');
   await elsewhere(other, () => { const d = JSON.parse(localStorage.getItem('work_complete_backup')); d.entries[0].notes = 'newer edit'; d.entries[0].totalEarnings = 91.23; localStorage.setItem('work_complete_backup', JSON.stringify(d)); });
   await p.click(tid('save-shift'));
   await p.waitForSelector('#toast:not([hidden])'); assert.match(await p.textContent('#toast'), /עודכנה בחלון אחר/);
   assert.ok(await p.isVisible(tid('save-shift')), `${width}: the editor stays open`);
   assert.equal(await p.inputValue(tid('notes-input')), 'my unsaved edit');
   let saved = (await stored(p))[0];
   assert.equal(saved.notes, 'newer edit'); assert.equal(saved.totalEarnings, 91.23); assert.equal(saved.customExtension, 'keep');
   assert.equal(await p.evaluate(() => document.documentElement.scrollWidth <= innerWidth), true, `${width}: sideways scroll`);
   await p.screenshot({ path: path.join(OUT, 'audit-conflict-' + width + '.png') });

   // 2. Reopened on the newer copy, then the other tab deletes it: saving does not bring it back, the draft stays.
   await p.click(tid('editor-close')); if (await p.isVisible(tid('confirm-ok'))) await p.click(tid('confirm-ok'));
   await p.waitForSelector(tid('save-shift'), { state: 'detached' });
   await openEdit(p); assert.equal(await p.inputValue(tid('notes-input')), 'newer edit');
   await p.fill(tid('notes-input'), 'another draft');
   await elsewhere(other, () => { const d = JSON.parse(localStorage.getItem('work_complete_backup')); d.entries = []; localStorage.setItem('work_complete_backup', JSON.stringify(d)); });
   await p.click(tid('save-shift'));
   await p.waitForFunction(() => /נמחקה בחלון אחר/.test(document.getElementById('toast').textContent));
   assert.ok(await p.isVisible(tid('save-shift')), `${width}: the editor stays open after the delete`);
   assert.equal(await p.inputValue(tid('notes-input')), 'another draft');
   assert.deepEqual(await stored(p), []);
   await context.close();
  }
  assert.deepEqual(errors, []);
  console.log('Audit: cross-tab edit/delete conflicts preserve drafts and stored data at four widths');
 } finally { await browser.close(); server.close(); }
})().catch(e => { console.error(e); process.exit(1); });
