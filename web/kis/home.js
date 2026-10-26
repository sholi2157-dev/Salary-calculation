/* Home: the week, today and all time (per currency, never mixed), the live shift, what is next, the latest shifts, the month's pocket. */
(function (root) {
'use strict';
const U = () => root.KisUi;
const totalsOf = rows => { const t = root.KisPay.totals(rows); return t instanceof Map ? Object.fromEntries(t) : t; };
const sortedTotals = rows => Object.entries(totalsOf(rows)).sort((a, b) => b[1] - a[1]);
function greeting() { const h = new Date().getHours(); return h >= 5 && h <= 11 ? 'בוקר טוב' : h >= 12 && h <= 16 ? 'צהריים טובים' : h >= 17 && h <= 20 ? 'ערב טוב' : 'לילה טוב'; }
function weekStart(ms) { const d = new Date(U().startOfDay(ms)); d.setDate(d.getDate() - d.getDay()); return d.getTime(); }

function tile(label, rows, caption, testid) {
  const { moneyHtml, esc } = U(), t = sortedTotals(rows);
  const money = t.length ? t.map(([c, v]) => `<b class="amount" data-testid="${testid}-${c}" style="font-size:22px">${moneyHtml(v, c)}</b>`).join('')
    : rows.length ? `<b class="amount" style="font-size:20px;color:var(--pending)">עוד לא ידוע</b>` : `<b class="amount" style="font-size:22px">${moneyHtml(0, '₪')}</b>`;
  return `<div class="tile" data-testid="${testid}"><span class="caption">${esc(label)}</span>${money}${caption ? `<span class="caption">${esc(caption)}</span>` : ''}</div>`;
}

function render(el, app) {
  const { store } = app, { esc, moneyHtml, shiftCard, placeOf, accent, upcoming } = U();
  const now = Date.now(), entries = store.data.entries;
  const done = entries.filter(e => !upcoming(e, now));
  const week = done.filter(e => e.date >= weekStart(now)), today = done.filter(e => e.date >= U().startOfDay(now));
  const month = done.filter(e => U().monthKey(e.date) === U().monthKey(now));
  const byCat = Object.entries(week.reduce((m, e) => (m[e.category] = (m[e.category] || 0) + e.hours, m), {})).sort((a, b) => b[1] - a[1]);
  const totalH = byCat.reduce((s, [, h]) => s + h, 0);
  const weekTotals = sortedTotals(week);
  const pending = root.KisPay.pending(done).filter(e => e.kind !== 'job').length;
  const name = (store.local.name || '').trim();
  const plans = app.occurrences();
  const wFrom = root.KisPlan.dateOf(weekStart(now)), wTo = root.KisRules.civilFromDays(root.KisRules.daysFromCivil(wFrom) + 6);
  const plannedWeek = plans.filter(o => !root.KisPlans.isAwaiting(o) && o.date >= wFrom && o.date <= wTo).length;
  const t = store.timer();
  const hero = weekTotals.length ? weekTotals.map(([c, v], i) => `<b class="hero" data-testid="home-total-${c}" style="${i ? 'font-size:30px;color:var(--muted)' : ''}">${moneyHtml(v, c)}</b>`).join('')
    : week.length ? '<b class="hero" style="font-size:40px;color:var(--pending)" data-testid="home-total">עוד לא ידוע</b>' : `<b class="hero" data-testid="home-total">${moneyHtml(0, '₪')}</b>`;

  const nextItems = (() => {
    const waiting = plans.filter(root.KisPlans.isAwaiting).slice(-3).map(o => ({ o }));
    const next = [...plans.filter(o => !root.KisPlans.isAwaiting(o)).map(o => ({ at: root.KisPlan.startMillis(o.date, o.start), o })),
      ...entries.filter(e => upcoming(e, now)).map(e => ({ at: e.date, e }))].sort((a, b) => a.at - b.at).slice(0, 3);
    return [...waiting, ...next];
  })();
  const recent = [...entries].sort((a, b) => (b.createdAt || b.date) - (a.createdAt || a.date)).slice(0, 3);
  const wallet = Object.entries(month.reduce((m, e) => { (m[e.category] ||= []).push(e); return m; }, {})).sort((a, b) => b[1].length - a[1].length);

  const unread = root.KisSettings.unread(store);
  // A recap is on Home from its 30th for 4 more days; after that it is in Settings → "הסיכומים שלי".
  const fresh = root.KisSummaryUi.fresh(store);
  const me = U().PLACES[0];
  el.innerHTML = `
  <div class="hello">${U().logo()}<div><div class="caption" data-testid="home-greeting">${esc(name ? greeting() + ', ' + name : greeting())}</div><div class="label" style="font-size:16px">שכר עבודות אישי</div></div>
    <div class="hello-actions"><button class="apk-entry" data-act="android-download" data-testid="android-download-entry" aria-label="הורדת האפליקציה לאנדרואיד">↓ אפליקציה</button><button class="round-btn" data-act="bell" data-testid="home-bell" aria-label="${unread ? 'התראות · יש חדשות' : 'התראות'}">${U().ICON.bell}${unread ? `<span class="dot" style="background:${U().accent(me)}" data-testid="home-bell-dot"></span>` : ''}</button></div></div>
  <div class="home-top"><section class="hero-card" data-testid="home-hero">
    <div class="label" style="color:var(--muted);font-weight:500">השבוע</div>
    <div style="display:flex;gap:18px;align-items:flex-end;overflow-x:auto;margin-top:6px">${hero}</div>
    ${totalH > 0 ? `<div class="progress">${byCat.map(([c, h]) => `<span style="width:${Math.max(1, h / totalH * 100)}%;background:${accent(placeOf(c, store))};margin-inline-end:3px;border-radius:3px"></span>`).join('')}</div>` : ''}
    <div class="legend" style="${totalH > 0 ? '' : 'margin-top:8px'}"><span data-testid="home-week-count">${week.length === 1 ? 'משמרת אחת השבוע' : week.length + ' משמרות השבוע'}</span>
      ${plannedWeek ? `<span>עוד ${plannedWeek} מתוכננות</span>` : ''}${byCat.slice(0, 3).map(([c]) => `<span><i style="background:${accent(placeOf(c, store))}"></i>${esc(c)}</span>`).join('')}</div>
  </section>
  <div class="home-side"><div class="tiles">${tile('היום', today, null, 'home-today')}${tile('מכל הזמנים', done, done.length === 1 ? 'משמרת אחת' : done.length + ' משמרות', 'home-all')}</div>
    ${pending ? `<div><button class="pending-flag" data-act="pending" data-testid="pending-flag">${pending === 1 ? 'משמרת אחת מחכה לתעריף ←' : pending + ' משמרות מחכות לתעריף ←'}</button></div>` : ''}
    <section class="now-row" data-testid="now-row">${t ? liveCard(t) : '<button class="now" data-act="start" data-testid="live-start"><span class="dot"></span>אני מתחיל עכשיו</button>'}</section>
  ${fresh.map(scope => `<button class="story-entry home-recap" data-recap="${scope}" data-testid="home-recap-${scope}"><span><b>${esc(scope.length === 4 ? `השנה ${scope} שלך בכיס` : `${U().MONTHS[Number(scope.slice(5)) - 1]} שלך בכיס`)}</b><small>${scope.length === 4 ? 'הסיכום השנתי מוכן' : 'הסיכום החודשי מוכן'}</small></span>${U().logo(34)}</button>`).join('')}</div></div>
  <div class="home-cols">
  ${nextItems.length ? `<section class="home-block"><div class="section-title"><h2>הבא בתור</h2><button class="link" data-act="all-planned" data-testid="next-all">הכול</button></div>
    <div class="list" data-testid="next-up">${nextItems.map(x => x.e ? shiftCard(x.e, store, { tag: 'next' }) : root.KisPlans.card(x.o, store)).join('')}</div></section>` : ''}
  ${recent.length ? `<section class="home-block"><div class="section-title"><h2>אחרונות</h2><button class="link" data-act="history">הכול</button></div>
    <div class="list">${recent.map(e => shiftCard(e, store, { tag: 'recent' })).join('')}</div></section>` : ''}
  ${wallet.length ? `<section class="home-block"><div class="section-title"><h2>הכיס של החודש</h2></div><div class="list">${wallet.map(([c, rows]) => {
      const tt = sortedTotals(rows); return `<div class="shift" style="cursor:default">${U().avatar(c, store)}<span class="meta"><b class="label">${esc(c)}</b><span class="sub">${rows.length === 1 ? 'משמרת אחת' : rows.length + ' משמרות'} · ${U().hours(rows.reduce((s, e) => s + e.hours, 0))} ש׳</span></span><span class="pay">${tt.map(([cur, v]) => moneyHtml(v, cur)).join(' ') || '—'}</span></div>`; }).join('')}</div></section>` : ''}</div>
  ${entries.length ? '' : '<div class="empty"><h3>ברוך הבא</h3></div>'}`;

  el.querySelector('[data-act=android-download]').onclick = () => root.KisDownload.open();
  el.querySelector('[data-act=bell]').addEventListener('click', () => root.KisSettings.notificationsSheet(app));
  el.querySelector('[data-act=start]')?.addEventListener('click', () => app.startLive());
  el.querySelectorAll('[data-recap]').forEach(b => b.addEventListener('click', () => root.KisSummaryUi.story(app, b.dataset.recap)));
  el.querySelector('[data-act=pending]')?.addEventListener('click', () => app.completePending());
  el.querySelector('[data-act=all-planned]')?.addEventListener('click', () => app.go('history', { planned: true }));
  el.querySelector('[data-act=history]')?.addEventListener('click', () => app.go('history'));
  el.querySelectorAll('.shift[data-id]').forEach(b => b.addEventListener('click', () => app.openDetail(b.dataset.id)));
  root.KisPlans.bind(el, app);
  bindLive(el, app, t);
}

function liveCard(t) {
  return `<div class="live" data-testid="live-card"><div class="row" style="align-items:center"><span class="grow label">משמרת פעילה · ${U().esc(t.category)}</span></div>
    ${earning(t) ? `<div class="clock money" data-testid="live-money"><span class="cur">${U().esc(t.currency)}</span><span data-live-money>0.00</span></div>
    <div class="label" style="direction:ltr;text-align:right" data-live-clock data-testid="live-clock">00:00:00</div>` : '<div class="clock" data-live-clock data-testid="live-clock">00:00:00</div>'}
    <div class="row"><button class="btn grow" style="background:var(--on-ink);color:var(--ink)" data-act="stop" data-testid="live-stop">סיום ושמירה</button>
    <button class="btn" style="background:transparent;color:var(--on-ink);border:1.5px solid color-mix(in srgb,var(--on-ink) 40%,transparent)" data-act="cancel" data-testid="live-cancel">ביטול</button></div></div>`;
}
/** With a known rate the live card counts the money earned so far (to the agora, by the second); the clock goes below it. */
const earning = t => !t.rateUnknown && Number(t.rate) > 0;
const liveMoney = (t, seconds) => (Math.floor(seconds * Number(t.rate) / 36) / 100).toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 });

let tick = null;
function bindLive(el, app, t) {
  clearInterval(tick);
  if (!t) return;
  const clock = el.querySelector('[data-live-clock]'), money = el.querySelector('[data-live-money]');
  const draw = () => {
    const s = Math.max(0, Math.floor((Date.now() - t.startedAt) / 1000));
    clock.textContent = [s / 3600, s / 60 % 60, s % 60].map(n => String(Math.floor(n)).padStart(2, '0')).join(':');
    if (money) money.textContent = liveMoney(t, s);
  };
  draw(); tick = setInterval(() => { if (!clock.isConnected) clearInterval(tick); else draw(); }, 1000);
  el.querySelector('[data-act=stop]').onclick = () => app.stopLive();
  el.querySelector('[data-act=cancel]').onclick = () => U().confirmSheet('לבטל את המשמרת הפעילה?', 'הזמן שנצבר לא יישמר.', 'לבטל בלי לשמור', () => app.cancelLive());
}

root.KisHome = { render, weekStart, sortedTotals, totalsOf, liveMoney };
})(globalThis);
