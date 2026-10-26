/* The story recap "החודש/השנה שלך בכיס" (stage D). Rules: web/v2/summary.js, the same as Android. */
(function (root) {
'use strict';
const U = () => root.KisUi;
const S = () => root.KisSummary;
const DAY_LONG = ['ראשון', 'שני', 'שלישי', 'רביעי', 'חמישי', 'שישי', 'שבת'];
const pad = n => String(n).padStart(2, '0');
const ymd = ms => { const d = new Date(ms); return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`; };
const scopeOf = month => `${Math.floor(month / 12)}-${pad(month % 12 + 1)}`;
const monthOf = scope => Number(scope.slice(0, 4)) * 12 + Number(scope.slice(5)) - 1;
const rowOf = e => ({ date: ymd(e.date), start: e.isTimeRange && e.startTime && e.endTime ? e.startTime : null, end: e.isTimeRange && e.startTime && e.endTime ? e.endTime : null,
  hours: e.hours, kind: e.kind === 'job' ? 'job' : 'shift', payKnown: !!e.payKnown, total: e.totalEarnings, currency: e.currency, paid: !!e.isPaid, category: e.category });
const summaryOf = (store, scope) => S().summary(store.data.entries.filter(e => !U().upcoming(e)).map(rowOf), scope, ymd(Date.now()));
const today = () => ymd(Date.now());
/** Whether the recap of `scope` may be seen yet (never before its 30th, never before the first one). */
const isOpen = scope => S().recapOpen(scope, today());
/** This person's own seed for the story's words (made once, kept on this device). */
function seedOf(store) {
  let seed = store.local.voiceSeed;
  if (!seed) { seed = Math.random().toString(36).slice(2, 10); store.setLocal('voiceSeed', seed, true); }
  return seed;
}
const doneIn = (store, scope) => store.data.entries.filter(e => !U().upcoming(e) && ymd(e.date).startsWith(scope));
/** A recap that was opened is no longer new in the bell. */
function markSeen(store, scope) {
  const list = store.recent();
  if (list.some(n => n.scope === scope && !n.opened)) store.setRecent(list.map(n => n.scope === scope ? { ...n, opened: true } : n));
}
/** A length, never mistaken for a time of day: "7 ש׳", "6.5 ש׳" (Android: longLabel). */
const longLabel = h => `${U().hours(h)} ש׳`;
const PLACE = i => U().PLACES[i % 8].from;

function insightText(i) {
  if (i.kind === 'unpaid') return i.amounts.map(a => U().money(a.amount, a.currency)).join(' · ') + ' עדיין לא סומנו כשולמו';
  if (i.kind === 'pending') return i.count === 1 ? 'משמרת אחת ממתינה לתעריף' : `${i.count} משמרות ממתינות לתעריף`;
  if (i.kind === 'trend') return `${Math.abs(i.percent)}% ${i.percent > 0 ? 'יותר' : 'פחות'} שעות מ${U().MONTHS[Number(i.previousMonth.slice(5)) - 1]}`;
  return `יום ${DAY_LONG[i.day]} הוא היום העמוס שלכם`;
}

/** The story: moves on by itself; a tap on the left moves forward, on the right back; holding pauses. Amounts are
 * hidden until asked for and are included on the shared card. */
function story(app, scope) {
  const { store } = app, { esc } = U(), s = summaryOf(store, scope);
  const isYear = scope.length === 4, whose = isYear ? 'השנה' : 'החודש';
  const label = isYear ? `השנה ${scope}` : `${U().MONTHS[Number(scope.slice(5)) - 1]} ${scope.slice(0, 4)}`;
  const top = s.places[0], per = s.persona && S().PERSONAS[s.persona];
  const rows = doneIn(store, scope);
  const fileSharing = canShareImage(); let preparedFile = null, preparing = null;
  // The words differ between people and between recaps (KisSummary.voice, the same picks as Android).
  const v = S().voice(scope, seedOf(store)), say = (key, values = {}) => esc(S().fill(v[key], { whose, ...values }));
  markSeen(store, scope);
  const perLine = per && per.line.replace('#', s.persona === 'marathon' ? U().hours(s.hours / Math.max(1, s.count)) : String(s.places.length));
  // Only the slides with something real to say (KisSummary.slides, the same rule as Android).
  const shows = new Set(S().slides(s)), showPer = shows.has('persona') ? per : null;
  const placeColor = name => U().accent(U().placeOf(name, store));
  let hidden = true, i = 0, paused = false, t0 = 0, elapsed = 0, timer = null;
  const DUR = 6000;
  const slides = [
    { bg: '#121212', a: 0, b: 2, tag: 'intro', html: () => `<div class="kick">שכר עבודות אישי · ${esc(label)}</div><h3 class="big">${say('intro').replace('\n', '<br>')}</h3><p>${say('introLine')}</p>` },
    { bg: PLACE(0), a: 3, b: 4, tag: 'hours', html: () => `<div class="kick">${say('hoursKick')}</div>${s.hours < 10 ? `<div class="huge" data-testid="story-hours-value">${U().hours(s.hours)}</div>` : `<div class="huge" data-count="${Math.round(s.hours)}" data-testid="story-hours-value">0</div>`}<h3>${s.hours === 1 ? 'שעת עבודה' : 'שעות עבודה'}${s.count === 1 ? ' במשמרת אחת.' : ` ב-${s.count} משמרות.`}</h3>${s.fun ? `<p>זה בערך <b>${s.fun.count}</b> ${esc(S().FUN[s.fun.key])}. ${say('funTail')}</p>` : ''}` },
    shows.has('places') && { bg: '#121212', a: 1, b: 0, tag: 'places', html: () => `<div class="kick">${say('placesKick')}</div><ol class="tops">${s.places.map((p, j) => `<li style="--d:${j * 120}ms"><b>${j + 1}</b><span class="dot" style="background:${placeColor(p.name)}"></span><span class="nm">${esc(p.name)}</span><span class="hh">${U().hours(p.hours)} ש׳</span></li>`).join('')}</ol>${top ? `<p>${say('placesLine', { p: Math.round(top.hours / Math.max(.01, s.hours) * 100), name: top.name })}</p>` : ''}` },
    showPer && { bg: PLACE(s.persona === 'owl' ? 4 : s.persona === 'bird' ? 3 : 2), a: 4, b: 3, tag: 'persona', html: () => `<div class="kick">${say('personaKick')}</div><h3 class="big" data-testid="story-persona-name">${esc(per.name)}</h3><p>${esc(perLine)}</p>` },
    shows.has('longest') && { bg: PLACE(2), a: 4, b: 3, tag: 'longest', html: () => { const l = s.longest;
      const [y, m, d] = l.date.split('-').map(Number);
      return `<div class="kick">${say('longestKick')}</div><div class="huge">${U().hours(l.hours)}</div><h3>שעות, ${esc(U().dayLabel(new Date(y, m - 1, d).getTime()))}</h3><p>${l.start ? `⁦${l.start}–${l.end}⁩. ` : ''}${say('longestTail')}${s.nights === 1 ? ' ופעם אחת סיימתם אחרי 23:00.' : s.nights ? ` ו-${s.nights} פעמים סיימתם אחרי 23:00.` : ''}</p>`; } },
    shows.has('days') && { bg: '#121212', a: 5, b: 1, tag: 'days', html: () => { const mx = Math.max(1e-9, ...s.days);
      return `<div class="kick">${say('daysKick')}</div><h3>${say('daysLine', { day: DAY_LONG[s.bestDay] })}</h3><div class="wbars">${s.days.map((h, j) => `<div><i style="--h:${(h / mx).toFixed(3)};--d:${j * 80}ms;background:${j === s.bestDay ? PLACE(3) : '#3A3A3A'}"></i><span>${U().DAY_SHORT[j]}</span></div>`).join('')}</div>`; } },
    shows.has('hour') && { bg: PLACE(5), a: 3, b: 4, tag: 'hour', html: () => { const p = s.hourMinutes, mx = Math.max(1, ...p), h = s.peakHour;
      const joke = h >= 22 || h < 5 ? 'בזמן שכולם רואים סדרה במיטה, אתם בשטח.' : h < 11 ? 'קפה ראשון? אתם כבר באמצע העבודה.' : h < 17 ? 'שעות השיא של העולם. וגם שלכם.' : 'השעה שבה כולם חוזרים הביתה, ואתם בדיוק בשיא.';
      return `<div class="kick">${say('hourKick')}</div><div class="huge">${pad(h)}:00</div><div class="hbars">${p.map((m, j) => `<i style="--h:${(m / mx).toFixed(3)};--d:${j * 25}ms;${j === h ? 'background:#fff' : ''}"></i>`).join('')}</div><p>${joke}</p>`; } },
    { bg: PLACE(1), a: 3, b: 0, tag: 'money', html: () => `<div class="kick">${say('moneyKick')}</div>${s.currencies.length ? s.currencies.map(c => `<div class="huge money" data-testid="${hidden ? 'story-money-hidden' : 'story-money-' + esc(c.currency)}">${hidden ? `<span class="cur">${esc(c.currency)}</span>•••` : U().moneyHtml(c.total, c.currency)}</div>`).join('') : '<h3>עוד חסר תעריף.</h3>'}
      <p>${s.pending ? `ועוד ${s.pending === 1 ? 'משמרת אחת שמחכה' : s.pending + ' משמרות שמחכות'} לתעריף. ` : ''}הסכומים מופיעים גם בכרטיס לשיתוף.</p>${s.currencies.length ? `<button class="reveal" data-rev data-testid="story-reveal">${hidden ? 'להציג' : 'להסתיר'}</button>` : ''}` },
    { bg: '#121212', a: 0, b: 3, card: true, tag: 'card', html: () => `<div class="kick">${whose} שלך בכיס</div>
      <div class="poster" data-testid="story-poster"><div class="pt"><span>${esc(label)}</span>${U().logo(36)}</div>
        <div class="pg">${posterCells().map(([k, v]) => `<div><small>${k}</small><b>${esc(v)}</b></div>`).join('')}</div></div>
      ${fileSharing ? '<button class="reveal solid" data-sharew data-testid="story-share" disabled>מכין תמונה…</button>' : ''}<button class="reveal" data-copyw data-testid="story-copy">העתקת סיכום</button>` },
  ].filter(Boolean);
  /** What the card says: the amounts per currency, then only the facts the story showed, never a "—" (Android: posterCells). */
  function posterCells() {
    return [...s.currencies.map(c => ['סכום (' + c.currency + ')', U().money(c.total, c.currency)]), ...(s.pending ? [['סכום חסר', s.pending === 1 ? 'משמרת אחת' : s.pending + ' משמרות']] : []),
      ['שעות', U().hours(s.hours < 10 ? s.hours : Math.round(s.hours))], ['משמרות', String(s.count)], top && ['מקום מוביל', top.name], showPer && ['טיפוס', showPer.name],
      shows.has('days') && ['היום החזק', `יום ${DAY_LONG[s.bestDay]}`], shows.has('longest') && ['הכי ארוכה', longLabel(s.longest.hours)]].filter(Boolean);
  }

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
      <div class="top"><span>${U().logo(26)}${esc(label)}</span><span class="acts">${fileSharing && !sl.card ? `<button data-shares aria-label="שיתוף המסך כתמונה" data-testid="story-share-slide" disabled>${U().ICON.share}</button>` : ''}<button data-x aria-label="סגירה" data-testid="story-close">✕</button></span></div>
      <div class="stage"><span class="blob b1" style="background:${PLACE(sl.a)}"></span><span class="blob b2" style="background:${PLACE(sl.b)}"></span><span class="ring" style="border-color:${PLACE(sl.b)}"></span>
        <div class="slide2" data-testid="story-${sl.tag}">${sl.html()}</div></div>
      <div class="story-nav"><button data-go="-1" aria-label="הקודם" data-testid="story-prev">›</button><button data-go="1" aria-label="הבא" data-testid="story-next">‹</button></div>`;
    el.querySelectorAll('[data-count]').forEach(n => { const to = +n.dataset.count, s0 = performance.now(); const f = now => { const p = Math.min(1, (now - s0) / 900); n.textContent = Math.round(to * (1 - Math.pow(1 - p, 3))); if (p < 1 && n.isConnected) requestAnimationFrame(f); }; requestAnimationFrame(f); });
    if (sl.card && fileSharing) {
      preparing ||= cardImage(label, posterCells(), `${whose} שלי בכיס`).then(blob => new File([blob], 'kis-card.png', {type:'image/png'}));
      preparing.then(file => { preparedFile = file; const b=el.querySelector('[data-sharew]'); if(b) { if(navigator.canShare({files:[file]})) { b.disabled=false; b.textContent='שיתוף הכרטיס'; } else b.remove(); } }).catch(() => el.querySelector('[data-sharew]')?.remove());
    }
    // Each slide can go out as a picture of itself; it is drawn once the slide has settled (counting, bars).
    slideFile = null; const at = i; clearTimeout(slideT);
    if (fileSharing && !sl.card) slideT = setTimeout(() => prepareSlide(at), 1200);
    t0 = Date.now(); elapsed = 0; U().haptic('tick');
  };
  let slideFile = null, slideT = null;
  const slideName = () => `kis-${scope}-${slides[i].tag}.png`;
  function prepareSlide(at) {
    if (at !== i || !el.isConnected) return null;
    return slideImage(el).then(blob => { if (at !== i) return null;
      slideFile = new File([blob], slideName(), { type: 'image/png' });
      const b = el.querySelector('[data-shares]'); if (b) b.disabled = false;
      return slideFile; }).catch(() => { el.querySelector('[data-shares]')?.remove(); return null; });
  }
  async function shareSlide() {
    paused = true; el.classList.add('paused');
    const file = slideFile || await prepareSlide(i);
    if (file) await shareCard(file);
    paused = false; el.classList.remove('paused'); t0 = Date.now() - elapsed;
  }
  const go = d => { const n = i + d; if (n < 0) return; if (n >= slides.length) return close(); i = n; draw(); };
  const close = () => { clearInterval(timer); clearTimeout(slideT); others.forEach(n => { n.inert = false; }); el.remove(); document.removeEventListener('keydown', keys, true); };
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
    if (b.dataset.sharew !== undefined && preparedFile) shareCard(preparedFile);
    if (b.dataset.shares !== undefined) shareSlide();
    if (b.dataset.copyw !== undefined) { const text=root.KisShare.copy(rows); U().copy(text).then(ok=>ok?U().toast('הועתק ✓'):U().showText(text)); }
  });
  function keys(e) { if (e.key === 'ArrowLeft') go(1); else if (e.key === 'ArrowRight') go(-1); else if (e.key === 'Escape') { e.stopPropagation(); close(); } }
  document.addEventListener('keydown', keys, true);
  draw(); timer = setInterval(tick, 60);
  el.querySelector('[data-testid=story-next]').focus({ preventScroll: true });
  return { close, go };
}

/**
 * The slide on screen as a picture, 1080 px wide: its colors, shapes, bars and words exactly where they stand (the
 * buttons, progress bars and arrows are left out), signed with the app's name at the bottom.
 */
async function slideImage(storyEl) {
  await document.fonts?.ready;
  const box = storyEl.getBoundingClientRect(), k = 1080 / box.width, W = 1080, H = Math.round(box.height * k);
  const c = document.createElement('canvas'); c.width = W; c.height = H;
  const x = c.getContext('2d'); x.scale(k, k); x.translate(-box.left, -box.top);
  const css = getComputedStyle(storyEl);
  x.fillStyle = css.backgroundColor; x.fillRect(box.left, box.top, box.width, box.height);
  const clear = col => !col || col === 'transparent' || /rgba?\([^)]*,\s*0\)$/.test(col);
  const skip = n => !!n.closest?.('button, .sbars, .story-nav');
  const alphaOf = n => { let a = 1; for (let p = n; p && p !== storyEl.parentElement; p = p.parentElement) a *= Number(getComputedStyle(p).opacity); return a; };
  const shape = (r, radius) => { x.beginPath(); x.roundRect ? x.roundRect(r.left, r.top, r.width, r.height, Math.min(radius, r.width / 2, r.height / 2)) : x.rect(r.left, r.top, r.width, r.height); };
  // The soft shapes first, everything else over them (as on screen).
  const deco = n => !!n.closest?.('.blob, .ring');
  for (const back of [true, false]) {
  const walker = document.createTreeWalker(storyEl, NodeFilter.SHOW_ELEMENT | NodeFilter.SHOW_TEXT);
  for (let n = walker.nextNode(); n; n = walker.nextNode()) {
    const elOf = n.nodeType === 3 ? n.parentElement : n;
    if (skip(elOf) || deco(elOf) !== back) continue;
    const cs = getComputedStyle(elOf);
    if (cs.display === 'none' || cs.visibility === 'hidden') continue;
    x.globalAlpha = alphaOf(elOf);
    if (n.nodeType === 1) {
      const r = n.getBoundingClientRect(); if (!r.width || !r.height) continue;
      const radius = parseFloat(cs.borderTopLeftRadius) || 0;
      if (n.tagName.toLowerCase() === 'svg') { await drawSvg(x, n, r); continue; }
      if (!clear(cs.backgroundColor)) { x.fillStyle = cs.backgroundColor; shape(r, cs.borderTopLeftRadius.endsWith('%') ? r.width : radius); x.fill(); }
      const bw = parseFloat(cs.borderTopWidth) || 0;
      if (bw && !clear(cs.borderTopColor) && cs.borderTopStyle !== 'none') {
        x.strokeStyle = cs.borderTopColor; x.lineWidth = bw;
        shape({ left: r.left + bw / 2, top: r.top + bw / 2, width: r.width - bw, height: r.height - bw }, cs.borderTopLeftRadius.endsWith('%') ? r.width : radius); x.stroke();
      }
      continue;
    }
    if (n.parentElement.closest('svg')) continue;
    // Words one by one, each at its own place on screen, so line breaks and mixed directions stay as they are.
    x.font = `${cs.fontStyle} ${cs.fontWeight} ${cs.fontSize} ${cs.fontFamily}`;
    if ('letterSpacing' in x) x.letterSpacing = cs.letterSpacing === 'normal' ? '0px' : cs.letterSpacing;
    x.fillStyle = cs.color; x.direction = cs.direction; x.textAlign = 'right'; x.textBaseline = 'alphabetic';
    const m = x.measureText('אבג187'), asc = m.fontBoundingBoxAscent ?? parseFloat(cs.fontSize) * .8, desc = m.fontBoundingBoxDescent ?? parseFloat(cs.fontSize) * .2;
    const range = document.createRange();
    for (const w of n.textContent.matchAll(/\S+/g)) {
      range.setStart(n, w.index); range.setEnd(n, w.index + w[0].length);
      const rs = [...range.getClientRects()]; if (!rs.length) continue;
      const left = Math.min(...rs.map(r => r.left)), right = Math.max(...rs.map(r => r.right)), top = Math.min(...rs.map(r => r.top)), bottom = Math.max(...rs.map(r => r.bottom));
      if (right - left > 1.5 * x.measureText(w[0]).width + 4) continue; // a word split across lines; rare, skipped rather than misplaced
      x.fillText(w[0], right, top + (bottom - top - asc - desc) / 2 + asc);
    }
  }
  }
  // Signed where the arrows were.
  x.setTransform(1, 0, 0, 1, 0, 0); x.globalAlpha = .8; x.fillStyle = css.color; x.direction = 'rtl'; x.textAlign = 'right';
  x.font = "34px Rubik, sans-serif"; if ('letterSpacing' in x) x.letterSpacing = '0px';
  x.fillText('שכר עבודות אישי', W - 64, H - 64);
  return new Promise((res, rej) => c.toBlob(b => b ? res(b) : rej(new Error('no image')), 'image/png'));
}
/** An inline icon (the logo) as a picture; it has no outside references, so the canvas stays shareable. */
function drawSvg(x, svg, r) {
  const copy = svg.cloneNode(true); copy.setAttribute('xmlns', 'http://www.w3.org/2000/svg');
  const img = new Image();
  return new Promise(res => { img.onload = () => { x.drawImage(img, r.left, r.top, r.width, r.height); res(); }; img.onerror = res;
    img.src = 'data:image/svg+xml;charset=utf-8,' + encodeURIComponent(copy.outerHTML); });
}

/** Only native image sharing. Never substitutes a download or a text share. */
function canShareImage() {
  try { return !!navigator.share && !!navigator.canShare?.({files:[new File(['image'], 'kis-card.png', {type:'image/png'})]}); } catch { return false; }
}
async function shareCard(file) {
  // The picture only: no list of shifts or any other text goes with it.
  try { await navigator.share({files:[file]}); }
  catch(e) { if(e?.name !== 'AbortError') U().toast('שיתוף התמונה לא הצליח. אפשר לנסות שוב.'); }
}

async function cardImage(label, cells, subtitle = 'החודש שלי בכיס') {
  await document.fonts?.ready;
  // Room for every row of cells and the signature line under them.
  const W = 1080, H = Math.max(1080, 350 + Math.ceil(cells.length / 2) * 180 + 120), c = document.createElement('canvas'); c.width = W; c.height = H;
  const x = c.getContext('2d');
  const g = x.createLinearGradient(0, 0, W, H); g.addColorStop(0, PLACE(0)); g.addColorStop(1, PLACE(4));
  x.fillStyle = g; x.beginPath(); x.roundRect ? x.roundRect(0, 0, W, H, 64) : x.rect(0, 0, W, H); x.fill();
  x.direction = 'rtl'; x.textAlign = 'right'; x.fillStyle = '#fff';
  x.font = "64px 'Secular One', Rubik, sans-serif"; x.fillText(label, W - 80, 170);
  x.font = "44px 'Secular One', Rubik, sans-serif"; x.fillText(subtitle, W - 80, 240);
  cells.forEach(([k, v], n) => {
    const col = n % 2, row = Math.floor(n / 2), right = col === 0 ? W - 80 : W / 2 - 20, top = 330 + row * 180;
    x.globalAlpha = .8; x.font = "36px Rubik, sans-serif"; x.fillText(k, right, top);
    x.globalAlpha = 1; x.font = "56px Rubik, sans-serif"; x.fillText(v, right, top + 86, W / 2 - 120);
  });
  // Signed at the bottom: the app's mark (three rising bars on a base line) and its name (as on Android).
  x.globalAlpha = 1; x.fillStyle = '#fff';
  const base = H - 80;
  [26, 44, 62].forEach((h, i) => { x.beginPath(); x.roundRect ? x.roundRect(80 + i * 22, base - h, 16, h, 6) : x.rect(80 + i * 22, base - h, 16, h); x.fill(); });
  x.fillRect(76, base + 6, 72, 6);
  x.globalAlpha = .86; x.font = "34px Rubik, sans-serif"; x.fillText('שכר עבודות אישי', W - 80, base + 4);
  return new Promise((res, rej) => c.toBlob(b => b ? res(b) : rej(new Error('no image')), 'image/png'));
}

/** Every recap so far (Settings): months and years whose recap has opened and that have shifts, newest first. */
function archive(app) {
  const { store } = app, { esc } = U();
  const list = S().recapArchive(today()).map(scope => ({ scope, s: summaryOf(store, scope) })).filter(x => x.s.count > 0);
  U().sheet('recaps-sheet', (el, close) => {
    el.innerHTML = `<div class="title-row"><h2>הסיכומים שלי</h2>${U().infoDot('הסיכומים שלי', 'סיכום חודשי נפתח ב-30 לחודש (ביום האחרון בחודש קצר יותר), מופיע בדף הבית עוד 4 ימים ואז נשמר כאן. הסיכום השנתי נפתח יחד עם דצמבר.')}</div>
      ${list.length ? `<div class="list">${list.map(({ scope, s }) => `<button class="story-entry recap-row" data-scope="${scope}" data-testid="recap-${scope}"><span><b>${esc(recapTitle(scope))}</b><small>${s.count === 1 ? 'משמרת אחת' : s.count + ' משמרות'} · ${U().hours(s.hours)} שעות</small></span>${U().logo(30)}</button>`).join('')}</div>`
        : `<p class="caption" data-testid="recaps-empty">הסיכום הראשון יגיע ב-${Number(S().recapDay(S().FIRST_RECAP).slice(8))} ב${U().MONTHS[Number(S().FIRST_RECAP.slice(5)) - 1]}.</p>`}`;
    el.querySelectorAll('[data-scope]').forEach(b => b.onclick = () => { close(); story(app, b.dataset.scope); });
  });
}
const recapTitle = scope => scope.length === 4 ? `השנה ${scope} בכיס` : `${U().MONTHS[Number(scope.slice(5)) - 1]} ${scope.slice(0, 4)} בכיס`;
/** The recaps that are new today and have shifts (Home shows them for the 30th and 4 more days). */
function fresh(store) {
  const { month, year } = S().recapNew(today());
  return [year, month].filter(scope => scope && doneIn(store, scope).length);
}

root.KisSummaryUi = { monthOf, story, archive, fresh, recapTitle, isOpen, markSeen, insightText, rowOf, scopeOf, summaryOf, canShareImage, cardImage, slideImage };
})(globalThis);
