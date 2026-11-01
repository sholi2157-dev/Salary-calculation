// Renders the recommended Heebo-only Kis type system next to the current one.
const { chromium } = require('playwright');
const fs = require('fs'), path = require('path');
const NM = path.join(__dirname, 'node_modules'), APPF = path.resolve(__dirname, '../../src/web/web/fonts');
const OUT = path.resolve(__dirname, '../../shots/agents');
const HEB = 'U+0307-0308,U+0590-05FF,U+200C-2010,U+20AA,U+25CC,U+FB1D-FB4F';
const faces = `
${['hebrew', 'latin'].map(s => `@font-face{font-family:Heebo;src:url(file://${NM}/@fontsource-variable/heebo/files/heebo-${s}-wght-normal.woff2) format('woff2');font-weight:100 900;${s === 'hebrew' ? `unicode-range:${HEB};` : ''}}`).join('\n')}
${[400, 500, 600, 700].map((w, i) => `@font-face{font-family:RubikApp;src:url(file://${APPF}/rubik_${['regular', 'medium', 'semibold', 'bold'][i]}.ttf);font-weight:${w}}`).join('\n')}
@font-face{font-family:SecularApp;src:url(file://${APPF}/secular_one.ttf)}`;

// Recommended tokens (px == sp)
const T = {
  hero: '700 52px/56px', page: '700 28px/34px', amount: '600 24px/30px', title: '700 20px/26px',
  body: '400 16px/24px', label: '500 14px/20px', caption: '400 13px/18px', micro: '500 11px/14px',
};
const css = `${faces}
*{box-sizing:border-box} body{margin:0;display:inline-flex;direction:rtl;background:#888}
.ph{width:390px;padding:18px;background:var(--bg);color:var(--ink);font-variant-numeric:tabular-nums}
.light{--bg:#EEF0F6;--s:#fff;--ink:#1B2335;--mu:#5A6478;--ln:#D2D8E3;--gl:#5145E8;--pe:#9A5000;--ps:#FFEBC7;--sk:#E4E8F0}
.dark{--bg:#0D1015;--s:#1A1D24;--ink:#EEF0F6;--mu:#A3ABBC;--ln:#2C313B;--gl:#8C82FF;--pe:#F2B66B;--ps:#3A2A12;--sk:#262A33}
.new{font-family:Heebo}
.new .hero{font:${T.hero} Heebo;letter-spacing:-.02em}.new .page{font:${T.page} Heebo;letter-spacing:-.01em}
.new .amount{font:${T.amount} Heebo;letter-spacing:-.01em}.new .title{font:${T.title} Heebo;letter-spacing:-.005em}
.new .body{font:${T.body} Heebo}.new .label{font:${T.label} Heebo}.new .cap{font:${T.caption} Heebo}.new .micro{font:${T.micro} Heebo;letter-spacing:.01em}
.new .rowt{font:600 16px/22px Heebo}.new .pay{font:600 20px/26px Heebo}.new .btn{font:600 16px/24px Heebo}
.new .cur{font-weight:500;font-size:.72em;margin-inline-end:.08em}
.new .small .cur{font-size:.82em}
.old{font-family:RubikApp}
.old .hero{font:60px/62px SecularApp;letter-spacing:-.5px}.old .page{font:30px/36px SecularApp}.old .amount{font:26px/29px SecularApp}
.old .title{font:22px/28px SecularApp}.old .body{font:400 16px/23px RubikApp}.old .label{font:600 14px/19px RubikApp}.old .cap{font:13px/17px RubikApp}
.old .micro{font:600 11px/13px RubikApp}.old .rowt{font:600 16px/22px RubikApp}.old .pay{font:19px/22px SecularApp}.old .btn{font:600 16px RubikApp}
.old .cur{font-family:RubikApp;font-weight:600;font-size:.62em;margin-inline-end:.06em} .old{font-variant-numeric:normal}
.tag{font:600 11px Heebo;direction:ltr;text-align:left;color:var(--gl)}
p,h1,h2{margin:0}.m{direction:ltr;unicode-bidi:isolate;display:inline-block;white-space:nowrap}
.tiles{display:grid;grid-template-columns:1fr 1fr;gap:10px;margin:8px 0 4px}.tile{background:var(--s);border:1px solid var(--ln);border-radius:18px;padding:12px 14px}
.row{display:flex;align-items:center;gap:12px;background:var(--s);border-radius:18px;padding:12px 14px;margin-top:8px}
.av{width:40px;height:40px;border-radius:12px;background:var(--gl);color:#fff;display:grid;place-items:center;font-weight:700}
.mid{flex:1}.chip{display:inline-block;padding:3px 7px;border-radius:7px;background:var(--ps);color:var(--pe);vertical-align:2px;margin-inline-start:4px}
.mu{color:var(--mu)}.btn{width:100%;min-height:52px;border:0;border-radius:16px;background:var(--gl);color:#fff;margin:14px 0 10px}
.seg{display:flex;gap:4px;background:var(--sk);border-radius:12px;padding:4px;margin:10px 0}.seg span{flex:1;text-align:center;padding:8px;border-radius:9px}.seg .on{background:var(--s);color:var(--ink)}
.time{font-weight:700;font-size:44px;line-height:48px}
.old .time{font:44px/48px SecularApp}
.scale div{display:flex;justify-content:space-between;align-items:baseline;border-top:1px solid var(--ln);padding:4px 0}
`;
const m = (c, n) => `<span class="m"><span class="cur">${c}</span>${n}</span>`;
const ph = (k, t, name) => `<section class="ph ${k} ${t}"><div class="tag">${name}</div>
<h1 class="page">היסטוריה</h1><p class="cap mu">מוצג: כל התאריכים · מהחדש לישן</p>
<div class="hero" style="margin-top:6px">${m('₪', '3,070.75')}</div>
<div class="tiles"><div class="tile"><div class="cap mu">היום</div><div class="amount">${m('₪', '300')}</div></div><div class="tile"><div class="cap mu">מכל הזמנים</div><div class="amount">${m('₪', '3,070.75')}</div><div class="amount small">${m('$', '480')}</div></div></div>
<div class="seg label mu"><span class="on">שבוע</span><span>חודש</span><span>הכל</span></div>
<h2 class="title" style="margin-top:12px">הבא בתור</h2>
<div class="row"><div class="av">מ</div><div class="mid"><div class="rowt">יום ד׳ 4.11 <span class="chip micro">לא שולם</span></div><div class="cap mu">5 שעות · מסעדה · <span class="m">20:30–00:30</span></div></div><b class="pay small">${m('₪', '250')}</b></div>
<div class="row"><div class="av" style="background:#7C3AED">T</div><div class="mid"><div class="rowt">יום ה׳ 29.10</div><div class="cap mu">4 שעות · Tech Support · $120</div></div><b class="pay small">${m('$', '120')}</b></div>
<div class="row"><div class="av" style="background:#B8326B">ה</div><div class="mid"><div class="rowt">יום ב׳ 26.10</div><div class="cap mu">8 שעות · הדרכת טיולים</div></div><b class="pay small">${m('₪', '1,111.11')}</b></div>
<div class="row"><div class="av" style="background:#0B7F60">ש</div><div class="mid"><div class="rowt">אתמול</div><div class="cap mu">2.5 שעות · שיעורים פרטיים</div></div><b class="pay small">${m('₪', '888.75')}</b></div>
<div class="time m" style="margin-top:10px">20:30</div>
<button class="btn">שמירה</button>
<p class="body">המשמרת נשמרה. הסכום המחושב נשאר כפי שנרשם, גם אם תשנו אחר כך את התעריף של מקום העבודה.</p></section>`;
const scale = t => `<section class="ph new ${t}"><div class="tag">Kis type scale · Heebo VF · tabular</div><div class="scale">
${Object.entries(T).map(([k, v]) => `<div><span class="${k === 'amount' || k === 'hero' ? k : k}">${k === 'hero' || k === 'amount' ? m('₪', '3,070.75') : 'חישוב שכר עבודה'}</span><span class="micro mu" style="direction:ltr">${k} ${v}</span></div>`).join('')}
</div></section>`;
(async () => {
  const b = await chromium.launch(); const p = await b.newPage({ deviceScaleFactor: 2 });
  const shot = async (body, name, w) => {
    const f = path.join(__dirname, 'shot.html');
    fs.writeFileSync(f, `<!doctype html><html lang="he" dir="rtl"><head><meta charset="utf-8"><style>${css}</style></head><body>${body}</body></html>`);
    await p.setViewportSize({ width: w, height: 900 }); await p.goto('file://' + f); await p.evaluate(() => document.fonts.ready);
    await p.screenshot({ path: path.join(OUT, name), fullPage: true });
  };
  await shot(ph('new', 'light', 'RECOMMENDED · Heebo only') + ph('new', 'dark', 'RECOMMENDED · Heebo only'), 'type-reco.png', 780);
  await shot(ph('old', 'light', 'CURRENT · Rubik + Secular One') + ph('new', 'light', 'RECOMMENDED · Heebo only'), 'type-reco-vs-current-light.png', 780);
  await shot(ph('old', 'dark', 'CURRENT · Rubik + Secular One') + ph('new', 'dark', 'RECOMMENDED · Heebo only'), 'type-reco-vs-current-dark.png', 780);
  await shot(scale('light') + scale('dark'), 'type-reco-scale.png', 780);
  await b.close();
})();
