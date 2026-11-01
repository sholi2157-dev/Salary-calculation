/*
 * 2.3.0 M-13 in a real browser (synthetic data, not signed in): "מחיקה מהמכשיר הזה" empties this browser's work data
 * and every hidden copy, keeps device choices (theme), is refused while a shift is running, and the account-wide action
 * is not offered without an account. The signed-in and cross-device rules are covered by web-tests/erase-two-actions.
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
  const ctx = await browser.newContext({ viewport: { width: 390, height: 844 }, locale: 'he-IL', serviceWorkers: 'block' });
  await ctx.addInitScript(d => { if (!sessionStorage.getItem('seeded')) { sessionStorage.setItem('seeded', '1'); localStorage.clear();
    for (const [k, v] of Object.entries(d)) localStorage.setItem(k, JSON.stringify(v));
    localStorage.setItem('kis_web_v1', JSON.stringify({ launched: 2, tourDone: true, theme: 'dark', name: 'בודק' })); localStorage.setItem('kis_ai_invited_v1', '1'); localStorage.setItem('kis_signin_skip_v1', '1'); } }, rich);
  const page = await ctx.newPage(), errors = []; page.on('pageerror', e => { errors.push(e.message); console.error(e.stack); });
  const tid = t => `[data-testid="${t}"]`;
  await page.goto(base + '#settings'); await page.waitForTimeout(600);
  if (!(await page.locator(tid('account-erase')).count())) { await page.click('[data-nav=settings], [data-page=settings]').catch(() => {}); await page.waitForTimeout(400); }
  await page.click(tid('settings-open-חשבון')); // M-31: one category at a time on a phone
  assert.equal(await page.locator(tid('account-erase-all')).count(), 0, 'no account-wide delete without an account');
  // A running shift blocks the delete.
  await page.evaluate(() => localStorage.setItem('work_active_shift_v1:guest', JSON.stringify({ id: 't1', category: 'מסעדה', currency: '₪', startedAt: Date.now() - 60000, rate: 40 })));
  await page.click(tid('account-erase')); await page.waitForTimeout(300);
  assert.equal(await page.locator(tid('erase-ok')).count(), 0, 'refused while a shift is running');
  await page.evaluate(() => localStorage.removeItem('work_active_shift_v1:guest'));
  await page.evaluate(() => localStorage.setItem('kis_web_draft_v1', '{"mode":"CLOCK"}')); // an unsaved draft (read only when the editor opens)
  await page.click(tid('account-erase'));
  assert.equal(await page.isDisabled(tid('erase-ok')), true);
  await page.fill(tid('erase-word'), 'מחיקה');
  await Promise.all([page.waitForEvent('load'), page.click(tid('erase-ok'))]); await page.waitForTimeout(500);
  const after = await page.evaluate(() => ({ data: JSON.parse(localStorage.getItem('work_complete_backup') || '{"entries":[]}'), keys: Object.keys(localStorage), local: JSON.parse(localStorage.getItem('kis_web_v1')) }));
  assert.equal(after.data.entries.length, 0);
  for (const k of ['work_pre_v23_snapshot_v1', 'work_pre_v2_snapshot_v1', 'kis_web_draft_v1', 'work_series_v1']) assert.ok(!after.keys.includes(k) || k === 'work_series_v1' && !JSON.parse(await page.evaluate(() => localStorage.getItem('work_series_v1'))).series.length, k + ' removed');
  assert.equal(after.local.theme, 'dark'); assert.equal(after.local.name, undefined);
  assert.deepEqual(errors, []);
  console.log('m13-erase: device data removed, theme kept, refused while running, no account action without an account');
  await browser.close(); server.close();
})().catch(e => { console.error(e); process.exit(1); });
