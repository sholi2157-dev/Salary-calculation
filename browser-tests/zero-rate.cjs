// A known zero keeps its stored amount until an explicit rate edit. Missing pay still uses bulk completion.
const assert = require('node:assert/strict'), http = require('node:http'), fs = require('node:fs'), path = require('node:path');
const { chromium } = require(process.env.PLAYWRIGHT_MODULE || 'playwright');
const ROOT = path.resolve('public');
const server = http.createServer((q, r) => {
  const p = new URL(q.url, 'http://x').pathname;
  if (p === '/api/config') { r.setHeader('content-type', 'application/json'); return r.end('{}'); }
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
    const ctx = await browser.newContext({ viewport: { width: 390, height: 844 }, serviceWorkers: 'block' });
    await ctx.route('**/*', r => new URL(r.request().url()).hostname === '127.0.0.1' ? r.continue() : r.abort());
    await ctx.addInitScript(() => {
      const row = { category: 'בדיקה', date: Date.now(), createdAt: Date.now(), isTimeRange: false, hours: 2, hourlyRate: 0, totalEarnings: 0, currency: '₪', isPaid: false, notes: '', isGroupShift: false, groupWorkersJson: '', startTime: null, endTime: null };
      localStorage.setItem('work_complete_backup', JSON.stringify({ entries: [{ ...row, id: 'known-zero', payKnown: true }, { ...row, id: 'missing', payKnown: false }], categories: [{ name: 'בדיקה', defaultRate: 0 }], workers: [] }));
      localStorage.setItem('kis_web_v1', JSON.stringify({ launched: 2, tourDone: true, whatsNewSeen: '2.4.2' })); localStorage.setItem('kis_signin_skip_v1', '1');
    });
    const page = await ctx.newPage(), errors = []; page.on('pageerror', e => errors.push(e.message));
    await page.goto(`http://127.0.0.1:${server.address().port}`);
    await page.evaluate(() => Kis.openDetail('known-zero'));
    await page.click('[data-testid=shift-detail] [data-a=rate]');
    await page.waitForSelector(tid('edit-page'));
    assert.equal(await page.evaluate(() => Kis.store.data.entries.find(e => e.id === 'known-zero').totalEarnings), 0);
    await page.fill(tid('rate-input'), '45');
    assert.match(await page.textContent(tid('editor-total')), /90/);
    await page.click(tid('save-shift'));
    await page.waitForSelector(tid('detail-amount'));
    assert.equal(await page.evaluate(() => JSON.parse(localStorage.work_complete_backup).entries.find(e => e.id === 'known-zero').totalEarnings), 90);
    await page.keyboard.press('Escape');
    await page.evaluate(() => Kis.openDetail('missing'));
    await page.click('[data-testid=shift-detail] [data-a=rate]');
    await page.waitForSelector(tid('rate-sheet'));
    await page.fill(tid('bulk-rate-input'), '25'); await page.click(tid('bulk-rate-apply'));
    assert.equal(await page.evaluate(() => Kis.store.data.entries.find(e => e.id === 'missing').totalEarnings), 50);
    assert.equal(await page.evaluate(() => Kis.store.data.entries.find(e => e.id === 'known-zero').totalEarnings), 90, 'completion never rewrites known pay');
    assert.deepEqual(errors, []); await ctx.close();
    console.log('zero-rate: known zero opens explicit rate editing; missing pay completes separately; stored amounts preserved until save');
  } finally { await browser.close(); server.close(); }
})().catch(e => { console.error(e); process.exit(1); });
