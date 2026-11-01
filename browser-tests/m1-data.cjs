/*
 * 2.3.0 Milestone 1 in a real browser (built public/, synthetic data): recovery from unreadable data (CW-15) and a
 * damaged plans file (D04), importing settings without overwriting (D05), and places named like object properties
 * (CW-11). Fails on page errors.
 */
const assert = require('node:assert/strict'), http = require('node:http'), fs = require('node:fs'), path = require('node:path');
const { chromium } = require(process.env.PLAYWRIGHT_MODULE || 'playwright');
const ROOT = path.resolve('public');
const rich = JSON.parse(fs.readFileSync('web-tests/fixtures/synthetic/rich-v2.json', 'utf8'));
const types = { '.html': 'text/html; charset=utf-8', '.js': 'text/javascript', '.css': 'text/css', '.json': 'application/json', '.ttf': 'font/ttf' };
const server = http.createServer((q, r) => {
  let p = decodeURIComponent(new URL(q.url, 'http://x').pathname); if (p === '/') p = '/index.html';
  if (p === '/api/config') { r.writeHead(200, { 'content-type': 'application/json' }); return r.end('{}'); }
  const f = path.join(ROOT, p); if (!f.startsWith(ROOT) || !fs.existsSync(f) || fs.statSync(f).isDirectory()) { r.writeHead(404); return r.end(); }
  r.writeHead(200, { 'content-type': types[path.extname(f)] || 'application/octet-stream', 'cache-control': 'no-store' }); fs.createReadStream(f).pipe(r);
});
(async () => {
  await new Promise(r => server.listen(0, r));
  const base = `http://127.0.0.1:${server.address().port}/`, browser = await chromium.launch();
  async function open(store) {
    const ctx = await browser.newContext({ viewport: { width: 390, height: 844 }, locale: 'he-IL', serviceWorkers: 'block' });
    await ctx.addInitScript(d => { if (!sessionStorage.getItem('seeded')) { sessionStorage.setItem('seeded', '1'); localStorage.clear();
      for (const [k, v] of Object.entries(d)) localStorage.setItem(k, typeof v === 'string' ? v : JSON.stringify(v));
      localStorage.setItem('kis_web_v1', JSON.stringify({ launched: 2, tourDone: true })); localStorage.setItem('kis_ai_invited_v1', '1'); localStorage.setItem('kis_signin_skip_v1', '1'); } }, store);
    const page = await ctx.newPage(), errors = [];
    page.on('pageerror', e => errors.push(e.message)); page.on('console', m => { if (m.type() === 'error' && !/api\/config|favicon/.test(m.text())) errors.push(m.text()); });
    await page.goto(base); await page.waitForTimeout(600);
    return { ctx, page, errors };
  }
  const tid = t => `[data-testid="${t}"]`;

  { // Unreadable data: recovery on the same screen, the damaged text kept first.
    const { ctx, page, errors } = await open({ work_complete_backup: '{"entries":[' });
    await page.waitForSelector(tid('recovery-panel'));
    await page.click(tid('recovery-restore'));
    await page.fill(tid('recovery-text'), JSON.stringify({ formatVersion: 2, ...rich.work_complete_backup }));
    assert.match(await page.textContent(tid('recovery-summary')), /15 משמרות/);
    await page.click(tid('recovery-confirm'));
    await page.waitForSelector('[data-testid=home-hero]');
    const kept = await page.evaluate(() => Object.keys(localStorage).filter(k => k.startsWith('work_damaged_copy_')).map(k => JSON.parse(localStorage.getItem(k)).values.work_complete_backup));
    assert.deepEqual(kept, ['{"entries":[']);
    assert.equal(await page.evaluate(() => JSON.parse(localStorage.getItem('work_complete_backup')).entries.length), 15);
    assert.deepEqual(errors, []); await ctx.close();
  }
  { // Damaged plans file: shifts show, the panel explains, the raw file is untouched.
    const { ctx, page, errors } = await open({ ...rich, work_series_v1: '{"series":' });
    await page.waitForSelector(tid('recovery-panel'));
    assert.ok(await page.locator('[data-testid=home-hero]').count());
    assert.equal(await page.evaluate(() => localStorage.getItem('work_series_v1')), '{"series":');
    assert.deepEqual(errors, []); await ctx.close();
  }
  { // Import of places and settings only: nothing overwritten unless asked.
    const { ctx, page, errors } = await open(rich);
    await page.evaluate(() => window.KisSettings.importSheet(window.Kis));
    const backup = { formatVersion: 2, entries: [], categories: [{ name: 'מקום מגיבוי', defaultRate: 77 }], workers: [{ name: 'עובד מגיבוי' }],
      webPreferences: { mainCurrency: '$', defaultCategory: 'מקום מגיבוי', categoryCurrencies: { 'מקום מגיבוי': '$', 'מסעדה': '$' } } };
    await page.fill(tid('import-text'), JSON.stringify(backup)); await page.click(tid('import-check'));
    assert.equal(await page.isDisabled(tid('import-save')), false);
    assert.equal(await page.isChecked(tid('import-prefs')), false);
    await page.click(tid('import-save')); await page.waitForTimeout(300);
    const d = await page.evaluate(() => JSON.parse(localStorage.getItem('work_complete_backup')));
    assert.ok(d.categories.some(c => c.name === 'מקום מגיבוי')); assert.ok(d.workers.some(w => w.name === 'עובד מגיבוי'));
    assert.equal(d.webPreferences.mainCurrency, '₪', 'default currency kept');
    assert.equal(d.webPreferences.categoryCurrencies['מקום מגיבוי'], '$');
    assert.equal(d.webPreferences.categoryCurrencies['מסעדה'], undefined, 'a place that already uses the main currency is not changed');
    assert.deepEqual(errors, []); await ctx.close();
  }
  { // Places named like object properties.
    const data = JSON.parse(JSON.stringify(rich));
    for (const [i, n] of ['constructor', '__proto__', 'toString'].entries()) {
      data.work_complete_backup.categories.push({ name: n, defaultRate: 40 });
      data.work_complete_backup.entries.push({ ...data.work_complete_backup.entries[0], id: 'p' + i, category: n, date: Date.now() - i * 3600e3 });
    }
    const { ctx, page, errors } = await open(data);
    await page.waitForSelector('[data-testid=home-hero]');
    for (const p of ['history', 'settings', 'home']) { await page.evaluate(x => window.Kis.go(x), p); await page.waitForTimeout(200); }
    assert.deepEqual(errors, []); await ctx.close();
  }
  console.log('m1-data: recovery, damaged plans, settings import and reserved place names passed');
  await browser.close(); server.close();
})().catch(e => { console.error(e); process.exit(1); });
