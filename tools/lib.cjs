const http = require('node:http'), fs = require('node:fs'), path = require('node:path');
const ROOT = path.resolve(__dirname, '../src/web/public');
const types = { '.html': 'text/html; charset=utf-8', '.js': 'text/javascript', '.css': 'text/css', '.json': 'application/json', '.ttf': 'font/ttf', '.svg':'image/svg+xml', '.png':'image/png', '.webmanifest':'application/manifest+json' };
function serve() { return new Promise(res => { const s = http.createServer((q, r) => {
  let p = decodeURIComponent(new URL(q.url, 'http://x').pathname); if (p === '/') p = '/index.html';
  if (p === '/api/config') { r.writeHead(200, { 'content-type': 'application/json' }); return r.end('{}'); }
  const f = path.join(ROOT, p); if (!f.startsWith(ROOT) || !fs.existsSync(f) || fs.statSync(f).isDirectory()) { r.writeHead(404); return r.end(); }
  r.writeHead(200, { 'content-type': types[path.extname(f)] || 'application/octet-stream', 'cache-control': 'no-store' }); fs.createReadStream(f).pipe(r);
}); s.listen(0, () => res({ s, base: `http://127.0.0.1:${s.address().port}/` })); }); }
const day = (d, h = 17) => { const t = new Date(); t.setDate(t.getDate() - d); t.setHours(h, 0, 0, 0); return t.getTime(); };
const row = (id, category, d, hours, rate, cur = '₪', x = {}) => ({ id, category, date: day(d, x.h || 17), createdAt: day(d), isTimeRange: false, startTime: null, endTime: null, hours,
  hourlyRate: rate, totalEarnings: Math.round(hours * rate * 100) / 100, isPaid: false, notes: '', currency: cur, isGroupShift: false, employerRate: null, workerRate: null, groupWorkersJson: '', ...x });
const places = [['מסעדה', 50], ['שיעורים פרטיים במתמטיקה ופיזיקה לתיכון', 120], ['הובלות', 60], ['בייביסיטר', 45], ['Tech Support', 30], ['הדרכת טיולים', 80], ['גינון', 55], ['אירועים', 70], ['מוקד לילה', 48]];
const seed = { entries: [
  row('a', 'מסעדה', 0, 6, 50, '₪', { isTimeRange: true, startTime: '11:00', endTime: '17:00' }),
  row('b', 'שיעורים פרטיים במתמטיקה ופיזיקה לתיכון', 1, 2.5, 120, '₪', { notes: 'שיעור כפול עם הכנה למבחן מתכונת, כולל דפי עבודה ונסיעה ארוכה במיוחד מעבר לעיר' }),
  row('c', 'הובלות', 2, 3, 60, '$', { isPaid: true }), row('d', 'Tech Support', 3, 4, 30, '$'),
  row('e', 'בייביסיטר', 4, 3.75, 45, '₪', { isPaid: true }), row('f', 'מסעדה', -3, 5, 50, '₪', { h: 12 }),
  row('g', 'הדרכת טיולים', 6, 8, 80, '₪'), row('h', 'גינון', 8, 2, 55, '₪', { isPaid: true }), row('i', 'אירועים', 10, 6, 70, '₪'),
  row('j', 'מוקד לילה', 12, 9, 48, '₪', { isTimeRange: true, startTime: '22:00', endTime: '07:00' }),
  row('k', 'מסעדה', 15, 6, 0, '₪', { payKnown: false, hourlyRate: 0, totalEarnings: 0 }),
  row('l', 'אירועים', 20, 5, 70, '₪', { isGroupShift: true, employerRate: 70, workerRate: 50, groupWorkersJson: JSON.stringify([{ name: 'יוסי', hours: 5, rate: 50 }, { name: 'מנחם מענדל', hours: 4, rate: 50 }]) }),
  row('m', 'הובלות', 40, 3, 60, '$'), row('n', 'מסעדה', 70, 7, 50, '₪', { isPaid: true }),
], categories: places.map(([name, defaultRate]) => ({ name, defaultRate })), workers: [],
  webPreferences: { mainCurrency: '₪', defaultCategory: 'מסעדה', categoryCurrencies: { 'הובלות': '$', 'Tech Support': '$' } } };
function big(n = 400) { const e = []; for (let i = 0; i < n; i++) { const p = places[i % places.length]; e.push(row('z' + i, p[0], Math.floor(i / 2), 2 + (i % 7), p[1], i % 5 === 0 ? '$' : '₪', { isPaid: i % 3 === 0 })); } return { ...seed, entries: e }; }
async function open(browser, base, { viewport = { width: 390, height: 844 }, scheme = 'light', data = seed, launched = true, mobile = true } = {}) {
  const ctx = await browser.newContext({ viewport, colorScheme: scheme, locale: 'he-IL', serviceWorkers: 'block', deviceScaleFactor: 2, isMobile: mobile, hasTouch: mobile });
  await ctx.addInitScript(([d, l]) => { if (!sessionStorage.getItem('seeded')) { sessionStorage.setItem('seeded', '1'); localStorage.clear(); if (d) localStorage.setItem('work_complete_backup', JSON.stringify(d));
    if (l) localStorage.setItem('kis_web_v1', JSON.stringify({ launched: 2, tourDone: true, aiInvited: true })); localStorage.setItem('kis_ai_invited_v1','1'); localStorage.setItem('kis_signin_skip_v1','1'); } }, [data, launched]);
  const page = await ctx.newPage(); page.errors = [];
  page.on('pageerror', e => page.errors.push(e.message)); page.on('console', m => { if (m.type() === 'error') page.errors.push(m.text()); });
  await page.goto(base); await page.waitForTimeout(900);
  return { ctx, page };
}
// Census: every visible element's colors and type.
async function census(page) { return page.evaluate(() => {
  const out = { color: {}, bg: {}, border: {}, font: {}, size: {}, weight: {}, radius: {}, small: [] };
  const inc = (o, k) => { o[k] = (o[k] || 0) + 1; };
  for (const el of document.querySelectorAll('body *')) { const r = el.getBoundingClientRect(); if (!r.width || !r.height) continue; const s = getComputedStyle(el); if (s.visibility === 'hidden' || s.display === 'none' || +s.opacity === 0) continue;
    const hasText = [...el.childNodes].some(n => n.nodeType === 3 && n.textContent.trim());
    if (hasText) { inc(out.color, s.color); inc(out.font, s.fontFamily.split(',')[0]); inc(out.size, s.fontSize); inc(out.weight, s.fontWeight); }
    if (s.backgroundColor !== 'rgba(0, 0, 0, 0)') inc(out.bg, s.backgroundColor);
    if (s.borderTopWidth !== '0px' && s.borderTopStyle !== 'none') inc(out.border, s.borderTopColor);
    if (s.borderTopLeftRadius !== '0px') inc(out.radius, s.borderTopLeftRadius);
    if ((el.matches('button,a,[role=button],input,[role=switch]')) && (r.height < 44 || r.width < 44)) out.small.push(`${el.tagName.toLowerCase()} ${Math.round(r.width)}x${Math.round(r.height)} "${(el.innerText || el.getAttribute('aria-label') || '').trim().slice(0, 24)}"`);
  } return out; }); }
module.exports = { serve, seed, big, open, census, day, row };
