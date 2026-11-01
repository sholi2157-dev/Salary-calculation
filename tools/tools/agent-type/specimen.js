// Typography specimen: renders the same Kis UI snippets in candidate pairings and screenshots them.
const { chromium } = require('playwright');
const fs = require('fs');
const path = require('path');

const NM = path.join(__dirname, 'node_modules');
const APPF = path.resolve(__dirname, '../../src/web/web/fonts');
const OUT = path.resolve(__dirname, '../../shots/agents');
fs.mkdirSync(OUT, { recursive: true });

const u = p => 'file://' + p;
const HEB = 'U+0307-0308,U+0590-05FF,U+200C-2010,U+20AA,U+25CC,U+FB1D-FB4F';
function varFace(fam, pkg, base) {
  return ['hebrew', 'latin'].map(s => `@font-face{font-family:'${fam}';src:url(${u(`${NM}/@fontsource-variable/${pkg}/files/${base}-${s}-wght-normal.woff2`)}) format('woff2');font-weight:100 900;font-display:swap;${s === 'hebrew' ? `unicode-range:${HEB};` : ''}}`).join('\n');
}
let css = `
@font-face{font-family:'RubikApp';src:url(${u(APPF + '/rubik_regular.ttf')});font-weight:400}
@font-face{font-family:'RubikApp';src:url(${u(APPF + '/rubik_medium.ttf')});font-weight:500}
@font-face{font-family:'RubikApp';src:url(${u(APPF + '/rubik_semibold.ttf')});font-weight:600}
@font-face{font-family:'RubikApp';src:url(${u(APPF + '/rubik_bold.ttf')});font-weight:700}
@font-face{font-family:'SecularApp';src:url(${u(APPF + '/secular_one.ttf')})}
${varFace('HeeboV', 'heebo', 'heebo')}
${varFace('AssistantV', 'assistant', 'assistant')}
${varFace('RubikV', 'rubik', 'rubik')}
${varFace('NotoHebV', 'noto-sans-hebrew', 'noto-sans-hebrew')}
${varFace('OpenSansV', 'open-sans', 'open-sans')}
${varFace('FrankV', 'frank-ruhl-libre', 'frank-ruhl-libre')}
`;
for (const w of [400, 500, 600, 700]) for (const s of ['hebrew', 'latin'])
  css += `@font-face{font-family:'PlexHeb';src:url(${u(`${NM}/@fontsource/ibm-plex-sans-hebrew/files/ibm-plex-sans-hebrew-${s}-${w}-normal.woff2`)}) format('woff2');font-weight:${w};${s === 'hebrew' ? `unicode-range:${HEB};` : ''}}\n`;

// Each candidate: text family, display family, display weight, display tracking, label/emphasis weights
const C = [
  { id: 'a', name: 'a · נוכחי: Rubik + Secular One', text: 'RubikApp', disp: 'SecularApp', dw: 400, ls: '-0.5px', lw: 600, curScale: .62, tnum: false },
  { id: 'b', name: 'b · Heebo בלבד', text: 'HeeboV', disp: 'HeeboV', dw: 700, ls: '-0.02em', lw: 500, curScale: .7, tnum: true },
  { id: 'c', name: 'c · Assistant בלבד', text: 'AssistantV', disp: 'AssistantV', dw: 700, ls: '-0.01em', lw: 600, curScale: .7, tnum: true },
  { id: 'd', name: 'd · Rubik בלבד (משקלים)', text: 'RubikV', disp: 'RubikV', dw: 600, ls: '-0.02em', lw: 500, curScale: .7, tnum: true },
  { id: 'e', name: 'e · Noto Sans Hebrew', text: 'NotoHebV', disp: 'NotoHebV', dw: 700, ls: '-0.02em', lw: 500, curScale: .7, tnum: true },
  { id: 'f', name: 'f · IBM Plex Sans Hebrew', text: 'PlexHeb', disp: 'PlexHeb', dw: 600, ls: '-0.01em', lw: 500, curScale: .7, tnum: true },
  { id: 'g', name: 'g · Heebo טקסט + Rubik תצוגה', text: 'HeeboV', disp: 'RubikV', dw: 600, ls: '-0.02em', lw: 500, curScale: .7, tnum: true },
  { id: 'h', name: 'h · Open Sans (עברית)', text: 'OpenSansV', disp: 'OpenSansV', dw: 700, ls: '-0.02em', lw: 600, curScale: .7, tnum: true },
  { id: 'i', name: 'i · Heebo + Frank Ruhl Libre לסכומים', text: 'HeeboV', disp: 'FrankV', dw: 600, ls: '-0.01em', lw: 500, curScale: .7, tnum: true },
];

const THEMES = {
  light: { bg: '#EEF0F6', surface: '#fff', ink: '#1B2335', muted: '#5A6478', line: '#D2D8E3', glow: '#5145E8', pend: '#9A5000', pendSoft: '#FFEBC7', sunk: '#E4E8F0' },
  dark: { bg: '#0D1015', surface: '#1A1D24', ink: '#EEF0F6', muted: '#A3ABBC', line: '#2C313B', glow: '#8C82FF', pend: '#F2B66B', pendSoft: '#3A2A12', sunk: '#262A33' },
};

function money(c, cur, n) { return `<span class="money"><span class="cur">${cur}</span>${n}</span>`; }
function card(c, t) {
  const th = THEMES[t];
  return `<section class="phone ${t}" style="--text:'${c.text}';--disp:'${c.disp}';--dw:${c.dw};--ls:${c.ls};--lw:${c.lw};--cs:${c.curScale};--num:${c.tnum ? 'tabular-nums' : 'normal'};
  --bg:${th.bg};--surface:${th.surface};--ink:${th.ink};--muted:${th.muted};--line:${th.line};--glow:${th.glow};--pend:${th.pend};--pend-soft:${th.pendSoft};--sunk:${th.sunk}">
  <div class="tagname">${c.name}</div>
  <h1 class="page">היסטוריה</h1>
  <p class="cap">מוצג: כל התאריכים · מהחדש לישן</p>
  <div class="hero">${money(c, '₪', '3,070.75')}</div>
  <div class="tiles"><div class="tile"><span class="cap">היום</span><b class="amount">${money(c, '₪', '300')}</b></div>
  <div class="tile"><span class="cap">מכל הזמנים</span><b class="amount">${money(c, '$', '480')}</b></div></div>
  <h2 class="sec">הבא בתור</h2>
  <div class="row"><div class="av">מ</div><div class="mid"><div class="t1">יום ד׳ 4.11 <span class="chip">לא שולם</span></div>
  <div class="sub">5 שעות · מסעדה · <span class="time">20:30–00:30</span></div></div><b class="pay">${money(c, '₪', '250')}</b></div>
  <div class="row"><div class="av" style="background:#7C3AED">T</div><div class="mid"><div class="t1">יום ה׳ 29.10</div>
  <div class="sub">4 שעות · Tech Support · $120</div></div><b class="pay">${money(c, '$', '120')}</b></div>
  <div class="nums">${['1,111.11', '8,888.88', '3,070.75', '168.75', '11.00'].map(n => `<div>${money(c, '₪', n)}</div>`).join('')}</div>
  <button class="btn">שמירה</button>
  <p class="body">המשמרת נשמרה. הסכום המחושב נשאר כפי שנרשם, גם אם תשנו אחר כך את התעריף של מקום העבודה. אפשר לייצא דוח חודשי ל־PDF ולשתף.</p>
  <div class="small"><span style="font-size:11px">11px · מתוכנן 13 משמרות</span><span style="font-size:12px">12px · שבוע של 25.10</span><span style="font-size:13px">13px · 2.5 שעות · שיעורים</span></div>
</section>`;
}

const baseCss = `${css}
*{box-sizing:border-box} body{margin:0;background:#888;display:inline-flex;flex-wrap:wrap;gap:0;direction:rtl}
.phone{width:390px;padding:18px 18px 22px;background:var(--bg);color:var(--ink);font:400 16px/1.5 var(--text);font-variant-numeric:var(--num)}
.tagname{font:600 11px/1.2 system-ui, sans-serif;direction:ltr;text-align:left;color:var(--glow);margin-bottom:6px;font-family:'HeeboV'}
.page{font-family:var(--disp);font-weight:var(--dw);font-size:30px;line-height:36px;letter-spacing:var(--ls);margin:0 0 2px}
.cap{font-size:13px;line-height:18px;color:var(--muted);margin:0}
.money{direction:ltr;unicode-bidi:isolate;display:inline-block;white-space:nowrap}
.money .cur{font-family:var(--text);font-weight:500;font-size:calc(var(--cs) * 1em);margin-inline-end:.06em}
.hero{font-family:var(--disp);font-weight:var(--dw);font-size:56px;line-height:60px;letter-spacing:var(--ls);margin:6px 0 10px}
.tiles{display:grid;grid-template-columns:1fr 1fr;gap:10px}
.tile{background:var(--surface);border:1px solid var(--line);border-radius:18px;padding:12px 14px;display:grid;gap:2px}
.amount{font-family:var(--disp);font-weight:var(--dw);font-size:26px;line-height:30px;letter-spacing:var(--ls)}
.sec{font-family:var(--disp);font-weight:var(--dw);font-size:22px;line-height:28px;letter-spacing:var(--ls);margin:16px 0 8px}
.row{display:flex;align-items:center;gap:12px;background:var(--surface);border-radius:18px;padding:12px 14px;margin-bottom:8px}
.av{width:40px;height:40px;border-radius:12px;background:var(--glow);color:#fff;display:grid;place-items:center;font-weight:700}
.mid{flex:1;min-width:0}.t1{font-weight:var(--lw);font-size:16px;line-height:22px}
.chip{display:inline-block;font-size:11px;line-height:13px;font-weight:500;padding:3px 7px;border-radius:7px;background:var(--pend-soft);color:var(--pend);vertical-align:2px;margin-inline-start:4px}
.sub{font-size:13px;line-height:18px;color:var(--muted)}
.time{direction:ltr;unicode-bidi:isolate;display:inline-block}
.pay{font-family:var(--disp);font-weight:var(--dw);font-size:19px;line-height:22px}
.nums{margin:10px 0;background:var(--surface);border-radius:14px;padding:8px 14px;font-family:var(--disp);font-weight:var(--dw);font-size:20px;line-height:26px;text-align:left}
.btn{width:100%;min-height:52px;border:0;border-radius:16px;background:var(--glow);color:#fff;font:var(--lw) 16px var(--text);margin:4px 0 10px}
.body{font-size:15px;line-height:24px;margin:0 0 8px;color:var(--ink)}
.small{display:grid;gap:2px;color:var(--muted)}
`;

function strip(t) {
  const th = THEMES[t];
  return `<div class="strip" style="background:${th.bg};color:${th.ink};padding:16px;display:grid;grid-template-columns:repeat(3,1fr);gap:14px;width:1440px">
  ${C.map(c => `<div style="background:${th.surface};border-radius:18px;padding:16px;--cs:${c.curScale};font-family:'${c.text}';font-variant-numeric:${c.tnum ? 'tabular-nums' : 'normal'}">
   <div style="font:600 11px 'HeeboV';color:${th.glow};direction:ltr;text-align:left">${c.name}</div>
   <div style="display:flex;justify-content:space-between;align-items:baseline">
    <div style="font-family:'${c.disp}';font-weight:${c.dw};letter-spacing:${c.ls};font-size:30px">היסטוריה</div>
    <div style="font-family:'${c.disp}';font-weight:${c.dw};letter-spacing:${c.ls};font-size:44px">${money(c, '₪', '3,070.75')}</div></div>
   <div style="font-size:14px;color:${th.muted}">יום ד׳ 4.11 · 5 שעות · מסעדה · <span class="time">20:30–00:30</span> · Tech Support · $120</div></div>`).join('')}
  </div>`;
}

(async () => {
  const b = await chromium.launch();
  const p = await b.newPage({ deviceScaleFactor: 2 });
  const html = t => `<!doctype html><html lang="he" dir="rtl"><head><meta charset="utf-8"><style>${baseCss}</style></head><body>${t}</body></html>`;
  const file = path.join(__dirname, 'specimen.html');
  const tmp = path.join(__dirname, 'shot.html');
  const show = async h => { fs.writeFileSync(tmp, h); await p.goto('file://' + tmp, { waitUntil: 'load' }); };
  // All candidates on one page (for the md / manual review)
  fs.writeFileSync(file, html(C.map(c => card(c, 'light') + card(c, 'dark')).join('')));
  for (const c of C) {
    await p.setViewportSize({ width: 780, height: 900 });
    await show(html(card(c, 'light') + card(c, 'dark')));
    await p.evaluate(() => document.fonts.ready);
    await p.screenshot({ path: path.join(OUT, `type-${c.id}.png`), fullPage: true });
  }
  for (const t of ['light', 'dark']) {
    await p.setViewportSize({ width: 1440, height: 600 });
    await show(html(strip(t)));
    await p.evaluate(() => document.fonts.ready);
    await p.screenshot({ path: path.join(OUT, `type-strip-${t}.png`), fullPage: true });
  }
  // Numeral metrics via canvas measureText
  await show(html(C.map(c => card(c, 'light')).join('')));
  await p.evaluate(() => document.fonts.ready);
  const fams = [...new Set(C.flatMap(c => [c.text, c.disp]))];
  const m = await p.evaluate(async fams => {
    const out = {};
    const cv = document.createElement('canvas').getContext('2d');
    for (const f of fams) {
      for (const w of [400, 600]) await document.fonts.load(`${w} 100px '${f}'`, '0123456789₪א');
      cv.font = `400 100px '${f}'`;
      const d = [...'0123456789'].map(ch => +cv.measureText(ch).width.toFixed(1));
      const sh = cv.measureText('₪'), z = cv.measureText('0'), al = cv.measureText('א');
      // tnum check in DOM: widths of 1111 vs 8888 with tabular-nums
      const span = document.createElement('span'); span.style.font = `400 100px '${f}'`; span.style.fontVariantNumeric = 'tabular-nums'; span.style.position = 'absolute';
      document.body.appendChild(span);
      span.textContent = '1111'; const w1 = span.getBoundingClientRect().width; span.textContent = '8888'; const w8 = span.getBoundingClientRect().width;
      span.style.fontVariantNumeric = 'normal'; span.textContent = '1111'; const p1 = span.getBoundingClientRect().width; span.textContent = '8888'; const p8 = span.getBoundingClientRect().width;
      span.remove();
      out[f] = { digitWidths: d, spread: +(Math.max(...d) - Math.min(...d)).toFixed(1), tnum_1111_vs_8888: [+w1.toFixed(1), +w8.toFixed(1)], default_1111_vs_8888: [+p1.toFixed(1), +p8.toFixed(1)],
        shekelAscent: +sh.actualBoundingBoxAscent.toFixed(1), zeroAscent: +z.actualBoundingBoxAscent.toFixed(1), alefAscent: +al.actualBoundingBoxAscent.toFixed(1), shekelWidth: +sh.width.toFixed(1) };
    }
    return out;
  }, fams);
  fs.writeFileSync(path.join(__dirname, 'metrics.json'), JSON.stringify(m, null, 1));
  console.log(JSON.stringify(m));
  await b.close();
})();
