/*
 * A job (קבלנות) with a team, in a real browser on the built public/ folder, with synthetic data only.
 * Proves: a solo job is unchanged; people get a fixed sum or hours × their own rate; only the user's share is stored
 * and counted; reopening shows the full price; an edit that touches nothing keeps the stored row exactly.
 */
const assert = require('node:assert/strict'), http = require('node:http'), fs = require('node:fs'), path = require('node:path');
const { chromium } = require(process.env.PLAYWRIGHT_MODULE || 'playwright');
const ROOT = path.resolve('public'), OUT = process.env.SCREENSHOT_DIR || 'browser-evidence';
fs.mkdirSync(OUT, { recursive: true });
const types = { '.html': 'text/html; charset=utf-8', '.js': 'text/javascript', '.css': 'text/css', '.json': 'application/json', '.ttf': 'font/ttf' };
const server = http.createServer((q, r) => {
  let p = decodeURIComponent(new URL(q.url, 'http://x').pathname); if (p === '/') p = '/index.html';
  const f = path.join(ROOT, p); if (!f.startsWith(ROOT) || !fs.existsSync(f) || fs.statSync(f).isDirectory()) { r.writeHead(404); return r.end(); }
  r.writeHead(200, { 'content-type': types[path.extname(f)] || 'application/octet-stream', 'cache-control': 'no-store' }); fs.createReadStream(f).pipe(r);
});
const seed = { entries: [], categories: [{ name: 'שיפוצים', defaultRate: 50 }], workers: [{ name: 'דני' }],
  webPreferences: { mainCurrency: '₪', defaultCategory: 'שיפוצים', categoryCurrencies: {} } };
const tid = t => `[data-testid="${t}"]`;

(async () => {
  await new Promise(r => server.listen(0, r));
  const base = `http://127.0.0.1:${server.address().port}/`;
  const browser = await chromium.launch(process.env.CHROMIUM_PATH ? { executablePath: process.env.CHROMIUM_PATH } : {});
  const errors = [];
  try {
    for (const colorScheme of ['light', 'dark']) {
      const ctx = await browser.newContext({ viewport: { width: 360, height: 780 }, colorScheme, locale: 'he-IL', serviceWorkers: 'block' });
      await ctx.addInitScript(d => { if (!sessionStorage.getItem('seeded')) { sessionStorage.setItem('seeded', '1'); localStorage.clear();
        localStorage.setItem('work_complete_backup', JSON.stringify(d)); localStorage.setItem('kis_web_v1', JSON.stringify({ launched: 2, tourDone: true })); } }, seed);
      const page = await ctx.newPage();
      page.on('pageerror', e => errors.push(e.message)); page.on('console', m => { if (m.type() === 'error') errors.push(m.text()); });
      await page.goto(base); await page.waitForSelector(tid('home-hero'));
      const stored = () => page.evaluate(() => JSON.parse(localStorage.getItem('work_complete_backup')));

      await page.click(tid('add-shift')); await page.click(tid('mode-job'));
      await page.fill(tid('job-amount'), '1000'); await page.dispatchEvent(tid('job-amount'), 'change');
      // A solo job shows only the small opener.
      assert.equal(await page.locator(tid('job-team-block')).count(), 0);
      await page.click(tid('job-team-open'));
      await page.click('[data-jwname="דני"]');
      await page.fill(tid('job-worker-amount-0'), '300');
      await page.fill(tid('job-worker-name'), 'יוסי'); await page.click(tid('job-worker-add'));
      await page.click(tid('job-worker-hourly-1'));
      await page.fill(tid('job-worker-hours-1'), '5:30'); await page.fill(tid('job-worker-rate-1'), '60');
      assert.match(await page.textContent(tid('job-team-pay')), /630/);
      assert.match(await page.textContent(tid('job-my-share')), /370/);
      assert.match(await page.textContent(tid('editor-total')), /370/);
      await page.screenshot({ path: path.join(OUT, `job-team-${colorScheme}-360.png`), fullPage: true });
      const width = await page.evaluate(() => document.documentElement.scrollWidth); assert.ok(width <= 360, 'no horizontal scroll: ' + width);
      // The team cannot take more than the price.
      await page.fill(tid('job-worker-amount-0'), '900');
      assert.match(await page.textContent(tid('job-team-error')), /יותר מסכום העבודה/);
      await page.fill(tid('job-worker-amount-0'), '300');
      await page.click(tid('save-shift'));
      const row = (await stored()).entries[0];
      assert.equal(row.kind, 'job'); assert.equal(row.isGroupShift, true); assert.equal(row.totalEarnings, 370);
      const ws = JSON.parse(row.groupWorkersJson);
      assert.deepEqual(ws.map(w => [w.name, w.amount ?? null, w.workerRate ?? null, w.hours]), [['דני', 300, null, 0], ['יוסי', null, 60, 5.5]]);
      assert.ok((await stored()).workers.some(w => w.name === 'יוסי'), 'new name joins the list');

      // Reopen: the full price comes back, and saving an untouched note keeps every stored value.
      // Same defaults the store applies when it loads a row.
      const load = id => { const e = JSON.parse(localStorage.getItem('work_complete_backup')).entries.find(x => x.id === id); return { kind: 'shift', payKnown: true, bonus: 0, seriesKey: null, autoConfirmed: false, ...e }; };
      const fromEntry = await page.evaluate(([id, f]) => window.KisEditor.fromEntry(eval(f)(id)), [row.id, load.toString()]);
      assert.equal(fromEntry.amount, '1000'); assert.equal(fromEntry.workers[0].amount, '300'); assert.equal(fromEntry.workers[1].byHours, true);
      const [same, before] = await page.evaluate(([id, f]) => { const e = eval(f)(id);
        return [JSON.stringify(window.KisEditor.toEntry({ ...window.KisEditor.fromEntry(e), notes: 'מטבח' }, e)), JSON.stringify({ ...e, notes: 'מטבח' })]; }, [row.id, load.toString()]);
      assert.equal(same, before);
      await ctx.close();
    }
    assert.deepEqual(errors, []);
    console.log('job team: ok');
  } finally { await browser.close(); server.close(); }
})().catch(e => { console.error(e); process.exit(1); });
