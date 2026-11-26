// Real Chromium quota plus injected failures: no user records or external services.
const assert = require('node:assert/strict'), http = require('node:http'), fs = require('node:fs'), path = require('node:path');
const { chromium } = require(process.env.PLAYWRIGHT_MODULE || 'playwright');
const ROOT = path.resolve('public');
const server = http.createServer((q, r) => {
  const p = new URL(q.url, 'http://x').pathname;
  if (p === '/api/config') { r.setHeader('content-type', 'application/json'); return r.end('{}'); }
  const f = path.join(ROOT, p === '/' ? 'index.html' : p);
  if (!f.startsWith(ROOT) || !fs.existsSync(f) || fs.statSync(f).isDirectory()) { r.writeHead(404); return r.end(); }
  r.setHeader('content-type', ({ '.html': 'text/html; charset=utf-8', '.js': 'text/javascript', '.css': 'text/css', '.json': 'application/json' })[path.extname(f)] || 'application/octet-stream');
  fs.createReadStream(f).pipe(r);
});
const tid = t => `[data-testid="${t}"]`;
(async () => {
  await new Promise(r => server.listen(0, '127.0.0.1', r));
  const browser = await chromium.launch({ executablePath: process.env.CHROMIUM_PATH || undefined, args: ['--no-sandbox'] });
  try {
    const ctx = await browser.newContext({ viewport: { width: 390, height: 844 }, serviceWorkers: 'block', acceptDownloads: true });
    await ctx.route('**/*', r => new URL(r.request().url()).hostname === '127.0.0.1' ? r.continue() : r.abort());
    await ctx.addInitScript(() => {
      const date = new Date(); date.setDate(date.getDate() - 1); date.setHours(0, 0, 0, 0);
      const entries = Array.from({ length: 4000 }, (_, i) => ({ id: 'quota-' + i, category: 'בדיקה', date: +date - i * 86400000,
        createdAt: +date, isTimeRange: false, hours: 2, hourlyRate: 40, totalEarnings: 80, currency: '₪', isPaid: false, notes: 'בדיקת אחסון '.repeat(8),
        isGroupShift: false, groupWorkersJson: '', startTime: null, endTime: null }));
      localStorage.setItem('work_complete_backup', JSON.stringify({ entries, categories: [{ name: 'בדיקה', defaultRate: 40 }], workers: [] }));
      localStorage.setItem('kis_web_v1', JSON.stringify({ launched: 2, tourDone: true, whatsNewSeen: '2.4.2' }));
      localStorage.setItem('kis_signin_skip_v1', '1'); localStorage.setItem('kis_ai_invited_v1', '1');
      // One due plan makes startup attempt a migration checkpoint before an automatic write.
      const day = [date.getFullYear(), String(date.getMonth() + 1).padStart(2, '0'), String(date.getDate()).padStart(2, '0')].join('-');
      localStorage.setItem('work_series_v1', JSON.stringify({ series: [{ id: 'quota-series', category: 'בדיקה', daysMask: 127, startTime: '08:00', endTime: '09:00', hourlyRate: 40, currency: '₪', fromDate: day, untilDate: day }], exceptions: [] }));
      // Fill only disposable synthetic storage, down to less than 1KB free.
      let size = 1024 * 1024, n = 0;
      while (size >= 1024) { try { localStorage.setItem('quota-fixture-' + n++, 'x'.repeat(size)); } catch { size = Math.floor(size / 2); } }
    });
    const page = await ctx.newPage(), errors = []; page.on('pageerror', e => errors.push(e.message));
    await page.goto(`http://127.0.0.1:${server.address().port}`);
    await page.waitForSelector(tid('home-hero'));
    await page.waitForSelector('#toast [data-kind=error]');
    assert.match(await page.textContent('#toast'), /מומלץ להוריד גיבוי/);
    assert.equal(await page.evaluate(() => Kis.store.data.entries.length), 4000, 'startup continues with all rows');
    await page.click(tid('tab-history')); await page.click(tid('shift-quota-0')); await page.click(tid('detail-paid'));
    assert.equal(await page.locator(tid('detail-paid')).count(), 1, 'failed save leaves detail open for retry');
    assert.equal(await page.evaluate(() => Kis.store.data.entries[0].isPaid), false);
    assert.equal(await page.evaluate(() => JSON.parse(localStorage.work_complete_backup).entries[0].isPaid), false);
    if (process.env.SCREENSHOT_DIR) {
      fs.mkdirSync(process.env.SCREENSHOT_DIR, { recursive: true });
      await page.screenshot({ path: path.join(process.env.SCREENSHOT_DIR, 'storage-full-paid-390.png') });
    }
    const [download] = await Promise.all([page.waitForEvent('download'), page.click(tid('toast-action'))]);
    const backup = JSON.parse(fs.readFileSync(await download.path(), 'utf8'));
    assert.equal(backup.entries.length, 4000, 'export works even at full quota');
    assert.deepEqual(errors, []);
    await ctx.close();
    // Separate small fixture: journal failure after checkpoints exist. No snapshots are removed to free space.
    const small = await browser.newContext({ viewport: { width: 390, height: 844 }, serviceWorkers: 'block' });
    await small.route('**/*', r => new URL(r.request().url()).hostname === '127.0.0.1' ? r.continue() : r.abort());
    await small.addInitScript(data => {
      for (const [k, v] of Object.entries(data)) localStorage.setItem(k, JSON.stringify(v));
      localStorage.setItem('kis_web_v1', JSON.stringify({ launched: 2, tourDone: true, whatsNewSeen: '2.4.2' }));
      localStorage.setItem('kis_signin_skip_v1', '1');
    }, JSON.parse(fs.readFileSync('web-tests/fixtures/synthetic/rich-v2.json', 'utf8')));
    const edit = await small.newPage(); edit.on('pageerror', e => errors.push(e.message));
    await edit.goto(`http://127.0.0.1:${server.address().port}`);
    await edit.evaluate(() => {
      Kis.store.setPaid(['r02'], true);
      const set = Storage.prototype.setItem;
      Storage.prototype.setItem = function (k, v) { if (k === 'kis_commit_journal_v1') throw new DOMException('Synthetic quota', 'QuotaExceededError'); return set.call(this, k, v); };
    });
    await edit.evaluate(() => Kis.openDetail('r02'));
    await edit.click(tid('detail-edit'));
    if (!await edit.locator(tid('notes-input')).count()) await edit.click(tid('editor-plus')).then(() => edit.click(tid('plus-note')));
    await edit.locator(tid('notes-input')).fill('עריכה סינתטית שנשארת');
    await edit.click(tid('save-shift'));
    assert.equal(await edit.locator(tid('notes-input')).inputValue(), 'עריכה סינתטית שנשארת');
    assert.match(await edit.textContent('#toast'), /מומלץ להוריד גיבוי/);
    if (process.env.SCREENSHOT_DIR) await edit.screenshot({ path: path.join(process.env.SCREENSHOT_DIR, 'storage-full-editor-390.png') });
    assert.deepEqual(errors, []);
    await small.close();
    console.log('storage-full: startup/checkpoints, paid retry, export and retained editor input passed (4000 synthetic rows)');
  } finally { await browser.close(); server.close(); }
})().catch(e => { console.error(e); process.exit(1); });
