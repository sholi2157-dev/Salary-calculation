const http = require('node:http'), fs = require('node:fs'), path = require('node:path');
const { chromium } = require(process.env.PLAYWRIGHT_MODULE);
const ROOT = path.resolve('/home/user/salary-web/public'), OUT = process.argv[2];
const types = { '.html': 'text/html; charset=utf-8', '.js': 'text/javascript', '.css': 'text/css', '.json': 'application/json', '.ttf': 'font/ttf' };
const server = http.createServer((q, r) => { let p = decodeURIComponent(new URL(q.url, 'http://x').pathname); if (p === '/') p = '/index.html';
  const f = path.join(ROOT, p); if (!fs.existsSync(f) || fs.statSync(f).isDirectory()) { r.writeHead(404); return r.end(); }
  r.writeHead(200, { 'content-type': types[path.extname(f)] || 'application/octet-stream' }); fs.createReadStream(f).pipe(r); });
const NOW = new Date('2026-10-22T20:30:00');
const day = (d, h = 17, m = 0) => { const t = new Date(NOW); t.setDate(t.getDate() - d); t.setHours(h, m, 0, 0); return t.getTime(); };
let n = 0;
const row = (d, category, start, end, hours, rate, cur = '₪', extra = {}) => ({ id: 'a' + (n++), category, date: day(d), createdAt: day(d) + 1, isTimeRange: !!start, startTime: start, endTime: end, hours,
  hourlyRate: rate, totalEarnings: Math.round(hours * rate * 100) / 100, isPaid: d > 6, notes: '', currency: cur, isGroupShift: false, employerRate: null, workerRate: null, groupWorkersJson: '', ...extra });
const entries = [
  row(0, 'מסעדה', '17:00', '23:00', 6, 52), row(1, 'שיעורים פרטיים', '16:00', '19:00', 3, 120), row(2, 'מסעדה', '18:00', '23:30', 5.5, 52),
  row(3, 'הפקות אירועים', '20:00', '02:00', 6, 60), row(5, 'מסעדה', '12:00', '18:00', 6, 52), row(6, 'שיעורים פרטיים', '15:00', '17:00', 2, 120),
  row(8, 'מסעדה', '17:00', '23:00', 6, 52), row(9, 'הפקות אירועים', '19:00', '01:00', 6, 60), row(11, 'שיעורים פרטיים', '16:00', '18:30', 2.5, 120),
  row(12, 'מסעדה', '17:00', '22:00', 5, 52), row(-2, 'מסעדה', '17:00', '23:00', 6, 52), row(4, 'הפקות אירועים', '19:00', '00:30', 5.5, 60), row(10, 'הפקות אירועים', '20:00', '01:00', 5, 60),
];
const data = { entries, categories: [{ name: 'מסעדה', defaultRate: 52 }, { name: 'שיעורים פרטיים', defaultRate: 120 }, { name: 'הפקות אירועים', defaultRate: 60 }], workers: [],
  webPreferences: { mainCurrency: '₪', defaultCategory: 'מסעדה', categoryCurrencies: {} } };
(async () => {
  await new Promise(r => server.listen(0, r));
  const base = `http://127.0.0.1:${server.address().port}/`;
  const browser = await chromium.launch({ executablePath: process.env.CHROMIUM_PATH });
  for (const scheme of ['dark', 'light']) {
    const ctx = await browser.newContext({ viewport: { width: 390, height: 844 }, deviceScaleFactor: 3, colorScheme: scheme, locale: 'he-IL', serviceWorkers: 'block' });
    await ctx.addInitScript(d => { localStorage.clear(); localStorage.setItem('work_complete_backup', JSON.stringify(d)); localStorage.setItem('kis_web_v1', JSON.stringify({ launched: 2, tourDone: true, name: 'נועה' })); }, data);
    const page = await ctx.newPage();
    await page.clock.install({ time: NOW });
    await page.goto(base); await page.waitForSelector('[data-testid=home-hero]'); await page.waitForTimeout(500);
    await page.screenshot({ path: `${OUT}/home-${scheme}.png` });
    await page.click('[data-testid=add-shift]'); await page.waitForTimeout(500);
    await page.screenshot({ path: `${OUT}/add-${scheme}.png` });
    await page.keyboard.press('Escape'); await page.goto(base); await page.waitForSelector('[data-testid=home-hero]');
    await page.click('[data-testid=home-month-summary]'); await page.click('[data-testid=month-story]');
    await page.waitForSelector('[data-testid=story-intro]');
    for (let i = 0; i < 3; i++) { await page.click('[data-testid=story-next]'); }
    await page.waitForTimeout(1200); await page.evaluate(() => document.querySelector('.story').classList.add('paused'));
    await page.screenshot({ path: `${OUT}/story-${scheme}.png` });
    await ctx.close();
  }
  await browser.close(); server.close();
})().catch(e => { console.error(e); process.exit(1); });
