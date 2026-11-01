/*
 * Design decisions "פרטי משמרת קבוצתית" B and "משוב והודעות" B in a real browser (synthetic data): the main amount is the
 * one the History list shows, labelled "החלק שלי"; the block adds up to the agora (own + commission = net; own + commission
 * + team + money passed through = what the employer owes); reports and worker actions open from one place; messages carry
 * their kind and an error is announced as an alert.
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
    localStorage.setItem('kis_web_v1', JSON.stringify({ launched: 2, tourDone: true })); localStorage.setItem('kis_ai_invited_v1', '1'); localStorage.setItem('kis_signin_skip_v1', '1'); } }, rich);
  const page = await ctx.newPage(), errors = []; page.on('pageerror', e => errors.push(e.message));
  const tid = t => `[data-testid="${t}"]`, num = t => Number(String(t).replace(/[^\d.]/g, ''));
  await page.goto(base); await page.waitForSelector(tid('home-hero'));
  await page.evaluate(() => window.Kis.go('history')); await page.waitForTimeout(300);
  const listed = num(await page.textContent(`${tid('shift-r08')} .pay`));
  await page.evaluate(() => window.Kis.openDetail('r08')); await page.waitForTimeout(200);
  const v = async t => num(await page.textContent(tid(t)));
  const own = await v('detail-amount'), profit = await v('group-profit'), net = await v('group-my-net'), team = await v('group-team-pay'), due = await v('group-employer-due');
  const pass = await page.locator(tid('detail-pass-through')).count() ? await v('detail-pass-through') : 0;
  assert.equal(own, listed, 'the main amount is the one the list shows');
  assert.match(await page.textContent('.gd-hero .caption'), /^החלק שלי/);
  const c = x => Math.round(x * 100);
  assert.equal(c(own) + c(profit), c(net), 'own + commission = net');
  assert.equal(c(own) + c(profit) + c(team) + c(pass), c(due), 'own + commission + team + pass-through = employer owes');
  // Reports from one place; a worker's actions behind ⋯.
  await page.click(tid('detail-reports')); assert.equal(await page.locator(`${tid('reports-sheet')} .action-item`).count(), 4); await page.keyboard.press('Escape');
  await page.click(tid('worker-more-0')); assert.ok(await page.isVisible(tid('worker-paid'))); await page.keyboard.press('Escape');
  for (const b of await page.$$('.gd-w button')) { const r = await b.boundingBox(); assert.ok(r.height >= 44, 'touch target ' + r.height); }
  await page.keyboard.press('Escape');
  // Messages: kind and announcement.
  await page.evaluate(() => window.KisUi.toast('הועתק ✓'));
  assert.equal(await page.getAttribute('#toast > div', 'data-kind'), 'ok'); assert.equal(await page.getAttribute('#toast', 'role'), 'status');
  assert.equal((await page.textContent('#toast .t-text')).trim(), 'הועתק');
  await page.evaluate(() => window.KisUi.toast('השמירה נכשלה ולא בוצע שום שינוי'));
  assert.equal(await page.getAttribute('#toast > div', 'data-kind'), 'error'); assert.equal(await page.getAttribute('#toast', 'role'), 'alert');
  await page.evaluate(() => window.KisUi.toast('המשמרת נמחקה', 'ביטול', () => {}));
  assert.equal(await page.getAttribute('#toast > div', 'data-kind'), 'undo'); assert.equal(await page.locator('#toast > div').count(), 1, 'one message at a time');
  assert.deepEqual(errors, []);
  console.log('group-detail: main amount = list, the block adds up, reports and worker menus, message kinds announced');
  await browser.close(); server.close();
})().catch(e => { console.error(e); process.exit(1); });
