/* "החודש שלך" and the story recap "החודש/השנה שלך בכיס" (stage D). Rules: web/v2/summary.js, the same as Android. */
(function (root) {
'use strict';
const U = () => root.KisUi;
const S = () => root.KisSummary;
const DAY_LONG = ['ראשון', 'שני', 'שלישי', 'רביעי', 'חמישי', 'שישי', 'שבת'];
const pad = n => String(n).padStart(2, '0');
const ymd = ms => { const d = new Date(ms); return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`; };
const scopeOf = month => `${Math.floor(month / 12)}-${pad(month % 12 + 1)}`;
const rowOf = e => ({ date: ymd(e.date), start: e.isTimeRange && e.startTime && e.endTime ? e.startTime : null, end: e.isTimeRange && e.startTime && e.endTime ? e.endTime : null,
  hours: e.hours, kind: e.kind === 'job' ? 'job' : 'shift', payKnown: !!e.payKnown, total: e.totalEarnings, currency: e.currency, paid: !!e.isPaid, category: e.category });
const summaryOf = (store, scope) => S().summary(store.data.entries.filter(e => !U().upcoming(e)).map(rowOf), scope, ymd(Date.now()));
const hm = h => { const m = Math.round(h * 60); return `${Math.floor(m / 60)}:${pad(m % 60)}`; };
const PLACE = i => U().PLACES[i % 8].from;

function insightText(i) {
  if (i.kind === 'unpaid') return i.amounts.map(a => U().money(a.amount, a.currency)).join(' · ') + ' עדיין לא סומנו כשולמו';
  if (i.kind === 'pending') return i.count === 1 ? 'משמרת אחת ממתינה לתעריף' : `${i.count} משמרות ממתינות לתעריף`;
  if (i.kind === 'trend') return `${Math.abs(i.percent)}% ${i.percent > 0 ? 'יותר' : 'פחות'} שעות מ${U().MONTHS[Number(i.previousMonth.slice(5)) - 1]}`;
  return `יום ${DAY_LONG[i.day]} הוא היום העמוס שלך`;
}

function monthSummary(app, month) {
  const { store } = app, { esc, moneyHtml, upcoming } = U();
  const rows = store.data.entries.filter(e => !upcoming(e) && U().monthKey(e.date) === month);
  const scope = scopeOf(month), s = summaryOf(store, scope);
  const maxDay = Math.max(1e-9, ...s.days);
  U().sheet('month-summary-sheet', (el, close) => {
    el.innerHTML = `<h2>החודש שלך · ${esc(U().monthName(month))}</h2>${rows.length ? `
      <button class="story-entry" data-a="story" data-testid="month-story"><span><b>${esc(U().MONTHS[month % 12])} שלך בכיס</b><small>הסיכום החודשי</small></span>${U().logo(34)}</button>
      <div class="card" style="background:var(--bg);display:grid;gap:4px" data-testid="month-summary-money">${s.currencies.length ? s.currencies.map(c => `<b class="hero" style="font-size:40px;line-height:44px">${moneyHtml(c.total, c.currency)}</b>`).join('') : '<b class="amount">עוד חסר תעריף</b>'}
        <span class="label" style="color:var(--muted)" data-testid="month-summary-counts">${rows.length} משמרות · ${U().hours(s.hours)} שעות</span></div>
      ${s.currencies.map(c => `<div class="row"><div class="mini-tile grow" data-testid="month-rate-${esc(c.currency)}"><span class="caption">ממוצע לשעה</span><b>${c.perHour != null ? esc(U().money(c.perHour, c.currency)) : '—'}</b></div>
        <div class="mini-tile grow"><span class="caption">לא שולם</span><b>${esc(U().money(c.unpaid, c.currency))}</b></div></div>`).join('')}
      ${s.insights.map((i, n) => `<div class="insight" data-testid="month-insight-${n}">${esc(insightText(i))}</div>`).join('')}
      ${s.bestDay != null ? `<div class="week-bars" data-testid="month-days">${s.days.map((h, i) => `<div><i style="height:${Math.max(4, h / maxDay * 80)}%;${i === s.bestDay ? 'background:var(--glow)' : ''}"></i><span>${U().DAY_SHORT[i]}</span></div>`).join('')}</div>` : ''}
      <button class="btn ghost" data-a="year" data-testid="year-story">${scope.slice(0, 4)} שלך בכיס</button>`
      : '<p class="caption">לא נרשמו משמרות בחודש הזה.</p>'}
      <div class="row"><button class="btn ghost grow" data-a="h">להיסטוריה</button><button class="btn primary grow" data-a="s" ${rows.length ? '' : 'disabled'}>שיתוף</button></div>`;
    el.querySelector('[data-a=h]').onclick = () => { close(); app.go('history', { planned: false, month }); };
    el.querySelector('[data-a=s]').onclick = () => U().shareOrCopy(root.KisShare.summary(rows), 'הסיכום של ' + U().monthName(month));
    el.querySelector('[data-a=story]')?.addEventListener('click', () => { close(); story(app, scope); });
    el.querySelector('[data-a=year]')?.addEventListener('click', () => { close(); story(app, scope.slice(0, 4)); });
  });
}

/** The story: moves on by itself; a tap on the left moves forward, on the right back; holding pauses. Amounts are
 * hidden until asked for and are never on the shared card. */
function story(app, scope) {
  const { store } = app, { esc } = U(), s = summaryOf(store, scope);
  const isYear = scope.length === 4, whose = isYear ? 'השנה' : 'החודש';
  const label = isYear ? `השנה ${scope}` : `${U().MONTHS[Number(scope.slice(5)) - 1]} ${scope.slice(0, 4)}`;
  const top = s.places[0], per = s.persona && S().PERSONAS[s.persona];
  const perLine = per && per.line.replace('#', s.persona === 'marathon' ? (s.hours / Math.max(1, s.count)).toFixed(1) : String(s.places.length));
  const placeColor = name => U().accent(U().placeOf(name, store));
  let hidden = true, i = 0, paused = false, t0 = 0, elapsed = 0, timer = null;
  const DUR = 6000;
  const slides = [
    { bg: '#121212', a: 0, b: 2, tag: 'intro', html: () => `<div class="kick">שכר עבודות אישי · ${esc(label)}</div><h3 class="big">${whose} שלך<br>בכיס.</h3><p>מוכן? יש לנו כמה מספרים שאתה לא יודע על עצמך.</p>` },
    { bg: PLACE(0), a: 3, b: 4, tag: 'hours', html: () => `<div class="kick">הזמן שלך</div><div class="huge" data-count="${Math.round(s.hours)}" data-testid="story-hours-value">0</div><h3>שעות עבודה ב-${s.count} משמרות.</h3>${s.fun ? `<p>זה בערך <b>${s.fun.count}</b> ${esc(S().FUN[s.fun.key])}. סתם שתדע.</p>` : ''}` },
    s.places.length && { bg: '#121212', a: 1, b: 0, tag: 'places', html: () => `<div class="kick">המקומות המובילים שלך</div><ol class="tops">${s.places.map((p, j) => `<li style="--d:${j * 120}ms"><b>${j + 1}</b><span class="dot" style="background:${placeColor(p.name)}"></span><span class="nm">${esc(p.name)}</span><span class="hh">${U().hours(p.hours)} ש׳</span></li>`).join('')}</ol>${top ? `<p>${esc(top.name)} קיבל ממך ${Math.round(top.hours / Math.max(.01, s.hours) * 100)}% מהזמן. שיהיה ברור מי האהוב.</p>` : ''}` },
    per && { bg: PLACE(s.persona === 'owl' ? 4 : s.persona === 'bird' ? 3 : 2), a: 4, b: 3, tag: 'persona', html: () => `<div class="kick">טיפוס העבודה שלך</div><h3 class="big" data-testid="story-persona-name">${esc(per.name)}</h3><p>${esc(perLine)}</p>` },
    { bg: PLACE(2), a: 4, b: 3, tag: 'longest', html: () => { const l = s.longest; if (!l) return `<h3 class="big">${whose} רגוע.</h3>`;
      const [y, m, d] = l.date.split('-').map(Number);
      return `<div class="kick">המשמרת הארוכה</div><div class="huge">${hm(l.hours)}</div><h3>${esc(U().dayLabel(new Date(y, m - 1, d).getTime()))}${l.start ? `, ⁦${l.start}–${l.end}⁩` : ''}.</h3><p>מגיע לך קפה. אולי שניים.${s.nights ? ` ועוד ${s.nights} פעמים סיימת אחרי 23:00.` : ''}</p>`; } },
    s.bestDay != null && { bg: '#121212', a: 5, b: 1, tag: 'days', html: () => { const mx = Math.max(1e-9, ...s.days);
      return `<div class="kick">הקצב שלך</div><h3>יום ${DAY_LONG[s.bestDay]} הוא היום שלך.</h3><div class="wbars">${s.days.map((h, j) => `<div><i style="--h:${(h / mx).toFixed(3)};--d:${j * 80}ms;background:${j === s.bestDay ? PLACE(3) : '#3A3A3A'}"></i><span>${U().DAY_SHORT[j]}</span></div>`).join('')}</div>`; } },
    s.peakHour != null && { bg: PLACE(5), a: 3, b: 4, tag: 'hour', html: () => { const p = s.hourMinutes, mx = Math.max(1, ...p), h = s.peakHour;
      const joke = h >= 22 || h < 5 ? 'בזמן שכולם רואים סדרה במיטה, אתה בשטח.' : h < 11 ? 'קפה ראשון? אתה כבר באמצע העבודה.' : h < 17 ? 'שעות השיא של העולם. וגם שלך.' : 'השעה שבה כולם חוזרים הביתה, ואתה בדיוק בשיא.';
      return `<div class="kick">השעה הכי פעילה שלך</div><div class="huge">${pad(h)}:00</div><div class="hbars">${p.map((m, j) => `<i style="--h:${(m / mx).toFixed(3)};--d:${j * 25}ms;${j === h ? 'background:#fff' : ''}"></i>`).join('')}</div><p>${joke}</p>`; } },
    { bg: PLACE(1), a: 3, b: 0, tag: 'money', html: () => `<div class="kick">השורה התחתונה</div>${s.currencies.length ? s.currencies.map(c => `<div class="huge money" data-testid="${hidden ? 'story-money-hidden' : 'story-money-' + esc(c.currency)}">${hidden ? `<span class="cur">${esc(c.currency)}</span>•••` : U().moneyHtml(c.total, c.currency)}</div>`).join('') : '<h3>עוד חסר תעריף.</h3>'}
      <p>${s.pending ? `ועוד ${s.pending === 1 ? 'משמרת אחת' : s.pending + ' משמרות'} שמחכות לתעריף. ` : ''}הסכום רק בשבילך, הוא לא מופיע בשיתוף.</p>${s.currencies.length ? `<button class="reveal" data-rev data-testid="story-reveal">${hidden ? 'הצג לי' : 'הסתר'}</button>` : ''}` },
    { bg: '#121212', a: 0, b: 3, card: true, tag: 'card', html: () => `<div class="kick">${whose} שלך בכיס</div>
      <div class="poster" data-testid="story-poster"><div class="pt"><span>${esc(label)}</span>${U().logo(36)}</div>
        <div class="pg">${posterCells().map(([k, v]) => `<div><small>${k}</small><b>${esc(v)}</b></div>`).join('')}</div></div>
      <button class="reveal solid" data-sharew data-testid="story-share">שיתוף הכרטיס</button>` },
  ].filter(Boolean);
  function posterCells() {
    return [['שעות', String(Math.round(s.hours))], ['משמרות', String(s.count)], ['מקום מוביל', top ? top.name : '—'], ['טיפוס', per ? per.name : '—'],
      ['היום שלך', s.bestDay != null ? DAY_LONG[s.bestDay] : '—'], ['הכי ארוכה', s.longest ? hm(s.longest.hours) : '—']];
  }
  const shareText = () => [`${whose} שלי בכיס · ${label}`, `⁦${Math.round(s.hours)}⁩ שעות · ${s.count} משמרות`, top ? `המקום המוביל: ${top.name}` : '',
    per ? `טיפוס: ${per.name}` : '', s.bestDay != null ? `היום שלי: ${DAY_LONG[s.bestDay]}` : ''].filter(Boolean).join('\n');

  const el = document.createElement('div');
  el.className = 'story'; el.setAttribute('role', 'dialog'); el.setAttribute('aria-modal', 'true'); el.setAttribute('aria-label', `${whose} שלך בכיס`); el.dataset.testid = 'story';
  document.body.append(el);
  const others = [...document.body.children].filter(n => n !== el && !n.inert); others.forEach(n => { n.inert = true; });
  const tick = () => {
    if (paused) return;
    elapsed = Date.now() - t0;
    const bar = el.querySelector('.sbars i.cur b'); if (bar) bar.style.width = Math.min(100, elapsed / DUR * 100) + '%';
    if (elapsed >= DUR && !slides[i].card) go(1);
  };
  const draw = () => {
    const sl = slides[i], light = sl.bg === PLACE(3);
    el.style.setProperty('--sbg', sl.bg); el.classList.toggle('on-light', light);
    el.innerHTML = `<div class="sbars">${slides.map((_, j) => `<i class="${j < i ? 'done' : j === i ? 'cur' : ''}"><b></b></i>`).join('')}</div>
      <div class="top"><span>${U().logo(26)}${esc(label)}</span><button data-x aria-label="סגירה" data-testid="story-close">✕</button></div>
      <div class="stage"><span class="blob b1" style="background:${PLACE(sl.a)}"></span><span class="blob b2" style="background:${PLACE(sl.b)}"></span><span class="ring" style="border-color:${PLACE(sl.b)}"></span>
        <div class="slide2" data-testid="story-${sl.tag}">${sl.html()}</div></div>
      <div class="story-nav"><button data-go="-1" aria-label="הקודם" data-testid="story-prev">›</button><button data-go="1" aria-label="הבא" data-testid="story-next">‹</button></div>`;
    el.querySelectorAll('[data-count]').forEach(n => { const to = +n.dataset.count, s0 = performance.now(); const f = now => { const p = Math.min(1, (now - s0) / 900); n.textContent = Math.round(to * (1 - Math.pow(1 - p, 3))); if (p < 1 && n.isConnected) requestAnimationFrame(f); }; requestAnimationFrame(f); });
    t0 = Date.now(); elapsed = 0; U().haptic('tick');
  };
  const go = d => { const n = i + d; if (n < 0) return; if (n >= slides.length) return close(); i = n; draw(); };
  const close = () => { clearInterval(timer); others.forEach(n => { n.inert = false; }); el.remove(); document.removeEventListener('keydown', keys, true); };
  let downAt = 0, holdT = null;
  el.addEventListener('pointerdown', e => { if (e.target.closest('button')) return; downAt = Date.now(); holdT = setTimeout(() => { paused = true; el.classList.add('paused'); }, 220); });
  el.addEventListener('pointerup', e => {
    clearTimeout(holdT);
    if (e.target.closest('button')) return;
    if (paused) { paused = false; el.classList.remove('paused'); t0 = Date.now() - elapsed; return; }
    if (Date.now() - downAt > 400) return;
    const r = el.getBoundingClientRect();
    go(e.clientX < r.left + r.width / 2 ? 1 : -1); // RTL: the left side moves forward
  });
  el.addEventListener('click', e => {
    const b = e.target.closest('button'); if (!b) return;
    if (b.dataset.x !== undefined) return close();
    if (b.dataset.go) return go(Number(b.dataset.go));
    if (b.dataset.rev !== undefined) { hidden = !hidden; const keep = elapsed; draw(); t0 = Date.now() - keep; return; }
    if (b.dataset.sharew !== undefined) shareCard(label, posterCells(), shareText());
  });
  function keys(e) { if (e.key === 'ArrowLeft') go(1); else if (e.key === 'ArrowRight') go(-1); else if (e.key === 'Escape') { e.stopPropagation(); close(); } }
  document.addEventListener('keydown', keys, true);
  draw(); timer = setInterval(tick, 60);
  el.querySelector('[data-testid=story-next]').focus({ preventScroll: true });
  return { close, go };
}

/** The card as a picture: share sheet with the image when the browser can, else a download; the text as a last resort. */
async function shareCard(label, cells, text) {
  try {
    const blob = await cardImage(label, cells);
    const file = new File([blob], 'kis-card.png', { type: 'image/png' });
    if (navigator.canShare?.({ files: [file] })) { try { await navigator.share({ files: [file], text }); return; } catch (e) { if (e?.name === 'AbortError') return; } }
    const url = URL.createObjectURL(blob); const a = document.createElement('a'); a.href = url; a.download = 'kis-card.png'; a.click(); setTimeout(() => URL.revokeObjectURL(url), 1000);
    U().toast('הכרטיס נשמר כתמונה ✓');
  } catch { U().shareOrCopy(text, 'שיתוף הכרטיס'); }
}

async function cardImage(label, cells) {
  await document.fonts?.ready;
  const W = 1080, H = 1080, c = document.createElement('canvas'); c.width = W; c.height = H;
  const x = c.getContext('2d');
  const g = x.createLinearGradient(0, 0, W, H); g.addColorStop(0, PLACE(0)); g.addColorStop(1, PLACE(4));
  x.fillStyle = g; x.beginPath(); x.roundRect ? x.roundRect(0, 0, W, H, 64) : x.rect(0, 0, W, H); x.fill();
  x.direction = 'rtl'; x.textAlign = 'right'; x.fillStyle = '#fff';
  x.font = "64px 'Secular One', Rubik, sans-serif"; x.fillText(label, W - 80, 170);
  x.font = "44px 'Secular One', Rubik, sans-serif"; x.fillText('בכיס', W - 80, 240);
  cells.forEach(([k, v], n) => {
    const col = n % 2, row = Math.floor(n / 2), right = col === 0 ? W - 80 : W / 2 - 20, top = 380 + row * 220;
    x.globalAlpha = .8; x.font = "36px Rubik, sans-serif"; x.fillText(k, right, top);
    x.globalAlpha = 1; x.font = "72px 'Secular One', Rubik, sans-serif"; x.fillText(v, right, top + 86, W / 2 - 120);
  });
  return new Promise((res, rej) => c.toBlob(b => b ? res(b) : rej(new Error('no image')), 'image/png'));
}

root.KisSummaryUi = { monthSummary, story, insightText, rowOf, scopeOf, summaryOf };
})(globalThis);
