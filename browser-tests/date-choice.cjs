// Owner feedback 2.3.1 (web): Settings → תאריכים switches dates between civil, Hebrew and both, on screen, in History's weeks
// and in shared reports; with several payment details the user picks which to add. Synthetic data only.
const assert = require('node:assert/strict'), http = require('node:http'), fs = require('node:fs'), path = require('node:path');
const { chromium } = require(process.env.PLAYWRIGHT_MODULE || 'playwright');
const ROOT = path.resolve('public');
const SHOTS = process.env.SCREENSHOT_DIR || '/tmp/date-choice-shots'; fs.mkdirSync(SHOTS, { recursive: true });
const rich = JSON.parse(fs.readFileSync('web-tests/fixtures/synthetic/rich-v2.json', 'utf8'));
const types = { '.html': 'text/html; charset=utf-8', '.js': 'text/javascript', '.css': 'text/css', '.json': 'application/json', '.ttf': 'font/ttf' };
const server = http.createServer((q, r) => {
  let p = decodeURIComponent(new URL(q.url, 'http://x').pathname); if (p === '/') p = '/index.html';
  if (p === '/api/config') { r.writeHead(200, { 'content-type': 'application/json' }); return r.end('{}'); }
  const f = path.join(ROOT, p); if (!f.startsWith(ROOT) || !fs.existsSync(f) || fs.statSync(f).isDirectory()) { r.writeHead(404); return r.end(); }
  r.writeHead(200, { 'content-type': types[path.extname(f)] || 'application/octet-stream', 'cache-control': 'no-store' }); fs.createReadStream(f).pipe(r);
});
(async () => {
 await new Promise(r => server.listen(0, '127.0.0.1', r));
 const browser = await chromium.launch({ executablePath: process.env.CHROMIUM_PATH || undefined, args: ['--no-sandbox', '--disable-dev-shm-usage', '--disable-gpu'] });
 try {
  const ctx = await browser.newContext({ serviceWorkers: 'block', viewport: { width: 390, height: 844 }, locale: 'he-IL' });
  await ctx.route('**/*', route => new URL(route.request().url()).hostname === '127.0.0.1' ? route.continue() : route.abort());
  await ctx.addInitScript(data => {
   if (sessionStorage.getItem('seeded')) return; sessionStorage.setItem('seeded', 'yes');
   for (const [key, value] of Object.entries(data)) localStorage.setItem(key, JSON.stringify(value));
   localStorage.setItem('kis_web_v1', JSON.stringify({ launched: 2, tourDone: true, whatsNewSeen: '2.4.0', dateMode: 'GREG', payZelle: 'dana@example.com', payBit: '050-0000000' }));
   localStorage.setItem('kis_ai_invited_v1', '1'); localStorage.setItem('kis_signin_skip_v1', '1');
   Object.defineProperty(navigator, 'share', { value: undefined, configurable: true });
  }, rich);
  await ctx.grantPermissions(['clipboard-read', 'clipboard-write']);
  const page = await ctx.newPage(), errors = [];
  page.on('pageerror', e => errors.push(e.message));
  await page.goto(`http://127.0.0.1:${server.address().port}`);
  await page.waitForSelector('[data-testid=home-hero]');
  const dates = async () => (await page.locator('.shift .top b, .shift .top strong').allInnerTexts()).filter(d => !/^(היום|אתמול|מחר)$/.test(d));
  // Civil only: "יום ד׳ 4/11", History names weeks by their first day.
  for (const d of await dates()) assert.match(d, /^יום [אבגדהוש]׳ \d{1,2}\/\d{1,2}(\/\d{4})?$/, 'civil ' + d);
  await page.evaluate(() => Kis.go('history')); await page.waitForSelector('.week-head');
  for (const w of await page.locator('.week-head span:first-child').allInnerTexts()) assert.match(w, /^(השבוע|שבוע של \d{1,2}\/\d{1,2}(\/\d{4})?)$/, 'week ' + w);
  await page.screenshot({ path: path.join(SHOTS, 'history-civil.png') });
  // Hebrew only, chosen in Settings.
  await page.evaluate(() => Kis.go('settings', { section: 'מראה ותחושה' }));
  await page.locator('[data-testid=date-mode-heb]').click();
  assert.equal(await page.locator('[data-testid=date-mode-heb]').getAttribute('aria-pressed'), 'true');
  await page.screenshot({ path: path.join(SHOTS, 'settings-dates.png') });
  await page.evaluate(() => Kis.go('history')); await page.waitForSelector('.week-head');
  const weeks = await page.locator('.week-head span:first-child').allInnerTexts();
  assert.ok(weeks.some(w => /פרשת|שבוע (פסח|סוכות|שבועות)/.test(w)), 'portions: ' + weeks.join(' | '));
  await page.evaluate(() => Kis.go('home')); await page.waitForSelector('[data-testid=home-hero]');
  for (const d of await dates()) assert.match(d, /^יום [אבגדהוש]׳ [א-ת״׳]+ [א-ת״׳ ]+$/, 'hebrew ' + d);
  assert.match(await page.evaluate(() => KisShare.date(new Date(2026, 10, 8, 12).getTime())), /^כ״ח חשוון תשפ״ז$/);
  // Two payment details: both picked at first; dropping one leaves the other in the text.
  await page.evaluate(() => KisUi.owedShare('היי\nסכום\nתודה!', 'בדיקה'));
  await page.waitForSelector('[data-testid=pay-pick-1]');
  assert.equal(await page.locator('[data-testid=pay-pick-0]').getAttribute('aria-pressed'), 'true');
  await page.screenshot({ path: path.join(SHOTS, 'pay-pick.png') });
  await page.locator('[data-testid=pay-pick-0]').click();
  assert.equal(await page.locator('[data-testid=pay-pick-0]').getAttribute('aria-pressed'), 'false');
  await page.locator('[data-testid=pay-pick-1]').click();
  assert.equal(await page.locator('[data-testid=pay-with]').isDisabled(), true, 'nothing picked: nothing to add');
  await page.locator('[data-testid=pay-pick-1]').click();
  await page.locator('[data-testid=pay-with]').click();
  const shared = await page.evaluate(() => navigator.clipboard.readText());
  assert.equal(shared, 'היי\nסכום\nלתשלום:\nביט: 050-0000000\nתודה!');
  // A worker's report asks for the kinds filled in.
  assert.equal(await page.evaluate(() => KisShare.askPayment(['zelle', 'bit'])), 'אפשר לשלוח לי את ה-Zelle או הביט שלך, ואעביר את התשלום.');
  assert.deepEqual(errors, []);
  console.log('date choice and payment pick: ok');
 } finally { await browser.close(); server.close(); }
})().catch(e => { console.error(e); process.exit(1); });
