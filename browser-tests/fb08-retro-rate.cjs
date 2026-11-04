// FB-08: in the places panel, editing מסעדה's rate leaves stored amounts alone with "שינוי רטרואקטיבי" off (the default);
// with it on, the unpaid shifts at the previous rate take the new rate and amount, a paid one never changes, and the
// message's "ביטול" gives back the previous amounts. Synthetic data only.
const assert = require('node:assert/strict'), http = require('node:http'), fs = require('node:fs'), path = require('node:path');
const { chromium } = require(process.env.PLAYWRIGHT_MODULE || 'playwright');
const ROOT = path.resolve('public');
const rich = JSON.parse(fs.readFileSync('web-tests/fixtures/synthetic/rich-v2.json', 'utf8'));
// One paid shift at the place's rate, to prove it is never touched.
rich.work_complete_backup.entries.find(e => e.id === 'r02').isPaid = true;
const types = { '.html': 'text/html; charset=utf-8', '.js': 'text/javascript', '.css': 'text/css', '.json': 'application/json', '.ttf': 'font/ttf' };
const server = http.createServer((q, r) => {
  let p = decodeURIComponent(new URL(q.url, 'http://x').pathname); if (p === '/') p = '/index.html';
  if (p === '/api/config') { r.writeHead(200, { 'content-type': 'application/json' }); return r.end('{}'); }
  const f = path.join(ROOT, p); if (!f.startsWith(ROOT) || !fs.existsSync(f) || fs.statSync(f).isDirectory()) { r.writeHead(404); return r.end(); }
  r.writeHead(200, { 'content-type': types[path.extname(f)] || 'application/octet-stream', 'cache-control': 'no-store' }); fs.createReadStream(f).pipe(r);
});
const tid = t => `[data-testid="${t}"]`;
(async () => {
 await new Promise(r => server.listen(0, '127.0.0.1', r));
 const browser = await chromium.launch({ executablePath: process.env.CHROMIUM_PATH || undefined, args: ['--no-sandbox', '--disable-dev-shm-usage', '--disable-gpu'] });
 try {
  const ctx = await browser.newContext({ serviceWorkers: 'block', viewport: { width: 390, height: 844 }, locale: 'he-IL' });
  await ctx.route('**/*', route => new URL(route.request().url()).hostname === '127.0.0.1' ? route.continue() : route.abort());
  await ctx.addInitScript(data => {
   if (sessionStorage.getItem('seeded')) return; sessionStorage.setItem('seeded', 'yes');
   for (const [key, value] of Object.entries(data)) localStorage.setItem(key, JSON.stringify(value));
   localStorage.setItem('kis_web_v1', JSON.stringify({ launched: 2, tourDone: true }));
   localStorage.setItem('kis_ai_invited_v1', '1'); localStorage.setItem('kis_signin_skip_v1', '1');
  }, rich);
  const page = await ctx.newPage(), errors = [];
  page.on('pageerror', e => errors.push(e.message));
  await page.goto(`http://127.0.0.1:${server.address().port}`);
  await page.waitForSelector('[data-testid=home-hero]');
  const amounts = () => page.evaluate(() => Object.fromEntries(Kis.store.data.entries.filter(e => e.category === 'מסעדה').map(e => [e.id, [e.hourlyRate, e.totalEarnings]])));
  const others = () => page.evaluate(() => JSON.stringify(Kis.store.data.entries.filter(e => !['r01', 'r15'].includes(e.id))));
  const places = async () => { await page.click(tid('tab-settings')); if (await page.isVisible(tid('settings-open-עבודה'))) await page.click(tid('settings-open-עבודה'));
    await page.click(tid('places-button')); await page.waitForSelector(tid('places-panel')); };
  const edit = async (rate, retro) => {
   await page.click(tid('place-edit-מסעדה'));
   const sw = page.locator(`[data-inline-edit] ${tid('place-retro')}`);
   assert.equal(await sw.getAttribute('aria-checked'), 'false', 'off by default');
   assert.equal(await sw.getAttribute('role'), 'switch');
   await page.fill(`[data-inline-edit] ${tid('place-rate')}`, rate);
   if (retro) { await sw.click(); assert.equal(await sw.getAttribute('aria-checked'), 'true');
     assert.match(await page.textContent(`[data-inline-edit] ${tid('place-retro-hint')}`), /ששולמו לא משתנות/); }
   await page.click(`[data-inline-edit] ${tid('place-save')}`);
   await page.waitForSelector('[data-inline-edit]', { state: 'detached' });
  };
  const start = await amounts(), rest = await others();
  // A new place has no switch.
  await places(); await page.click(tid('place-add')); await page.waitForSelector(tid('place-sheet'));
  assert.equal(await page.locator(`${tid('place-sheet')} ${tid('place-retro')}`).count(), 0, 'a new place shows the switch');
  await page.keyboard.press('Escape'); await page.waitForSelector(tid('place-sheet'), { state: 'detached' });

  // Off: only the place's rate changes.
  await edit('52', false);
  assert.equal(await page.evaluate(() => Kis.store.data.categories.find(c => c.name === 'מסעדה').defaultRate), 52);
  assert.deepEqual(await amounts(), start, 'off changed a stored amount');

  // Back to 50 (off), then 50 → 55 with the switch on.
  await edit('50', false);
  await edit('55', true);
  const after = await amounts();
  assert.deepEqual(after.r01, [55, 330]); assert.deepEqual(after.r15, [55, 275]);
  assert.deepEqual(after.r02, start.r02, 'a paid shift changed');
  assert.deepEqual(after.r09, start.r09, 'a shift with its own rate changed'); assert.deepEqual(after.r11, start.r11); assert.deepEqual(after.r05, start.r05);
  assert.equal(await others(), rest, 'another shift changed');
  // Saved, and the message offers undo.
  const saved = await page.evaluate(() => JSON.parse(localStorage.getItem('work_complete_backup')).entries.find(e => e.id === 'r01').totalEarnings);
  assert.equal(saved, 330);
  await page.waitForSelector(tid('toast-action'));
  assert.match(await page.textContent('#toast'), /2 משמרות שלא שולמו/);
  await page.click(tid('toast-action'));
  assert.deepEqual(await amounts(), start, 'undo did not restore');
  assert.equal(await page.evaluate(() => Kis.store.data.categories.find(c => c.name === 'מסעדה').defaultRate), 55, 'undo keeps the new place rate');
  assert.deepEqual(errors, []);
  console.log('fb08-retro-rate: off keeps stored amounts, on updates unpaid shifts only, undo restores them');
 } finally { await browser.close(); server.close(); }
})().catch(e => { console.error(e); process.exit(1); });
