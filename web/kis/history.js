/*
 * History, as on Android after rc5: all dates by default; one "סינון ומיון" button opens one panel (dates, order,
 * optional filters, live count); a line says what is shown. "עבדתי" holds done shifts only (totals, copy, Excel,
 * selection); "מתוכנן" holds future shifts and weekly-plan days, never summed.
 */
(function (root) {
'use strict';
const U = () => root.KisUi;
const PERIODS = [['ALL', 'כל התאריכים'], ['MONTH', 'חודש'], ['RANGE', 'טווח']];
const SORTS = [['NEWEST', 'החדש קודם', 'מהחדש לישן'], ['OLDEST', 'הישן קודם', 'מהישן לחדש'], ['ADDED', 'לפי הוספה', 'לפי סדר ההוספה']];
const PAYS = [['ALL', 'הכול'], ['UNPAID', 'ממתין'], ['PAID', 'שולם'], ['PENDING', 'בלי תעריף']];
const defaults = () => ({ period: 'ALL', month: U().monthKey(Date.now()), from: U().startOfDay(Date.now() - 7 * 864e5), to: U().startOfDay(Date.now()),
  pay: 'ALL', category: null, currency: null, sort: 'NEWEST' });
const state = { filter: null, query: '', searching: false, planned: false, selection: new Set() };
const activeCount = f => [f.period !== 'ALL', f.pay !== 'ALL', f.category != null, f.currency != null, f.sort !== 'NEWEST'].filter(Boolean).length;
function describe(f) {
  const parts = [f.period === 'ALL' ? 'כל התאריכים' : f.period === 'MONTH' ? U().monthName(f.month) : `${U().shortDate(f.from)}–${U().shortDate(f.to)}`,
    SORTS.find(s => s[0] === f.sort)[2]];
  if (f.pay !== 'ALL') parts.push(PAYS.find(p => p[0] === f.pay)[1]);
  if (f.category) parts.push(f.category); if (f.currency) parts.push(f.currency);
  return parts.join(' · ');
}
function matches(e, q) {
  q = q.trim(); if (!q) return true;
  if (e.category.includes(q) || (e.notes || '').includes(q)) return true;
  if (root.KisPay.workers(e).some(w => w.name.includes(q))) return true;
  const pay = root.KisPay.pay(e); return pay != null && (pay.toFixed(2).includes(q) || String(pay).includes(q));
}
function apply(entries, f, q) {
  const toEnd = f.to + 864e5;
  const rows = entries.filter(e => {
    const inPeriod = f.period === 'ALL' || (f.period === 'MONTH' ? U().monthKey(e.date) === f.month : U().startOfDay(e.date) >= f.from && U().startOfDay(e.date) < toEnd);
    const payOk = f.pay === 'ALL' || (f.pay === 'UNPAID' ? !e.isPaid : f.pay === 'PAID' ? e.isPaid : !e.payKnown);
    return inPeriod && payOk && (!f.category || e.category === f.category) && (!f.currency || e.currency === f.currency) && matches(e, q);
  });
  if (f.sort === 'ADDED') return rows.sort((a, b) => (b.createdAt || 0) - (a.createdAt || 0));
  return rows.sort((a, b) => f.sort === 'OLDEST' ? a.date - b.date || (a.createdAt || 0) - (b.createdAt || 0) : b.date - a.date || (b.createdAt || 0) - (a.createdAt || 0));
}
function weekLabel(start) { return start === root.KisHome.weekStart(Date.now()) ? 'השבוע' : 'שבוע של ' + U().shortDate(start) + (new Date(start).getFullYear() !== new Date().getFullYear() ? '.' + new Date(start).getFullYear() : ''); }

function render(el, app, opts = {}) {
  const { store } = app, { esc, moneyHtml, shiftCard, ICON, upcoming } = U();
  if (!state.filter) state.filter = defaults();
  if (opts.planned != null) state.planned = opts.planned;
  if (opts.month != null) state.filter = { ...state.filter, period: 'MONTH', month: opts.month };
  if (opts.selectPending) { state.planned = false; state.selection = new Set(store.data.entries.filter(e => !upcoming(e) && !e.payKnown && e.kind !== 'job').map(e => e.id)); }
  const f = state.filter, all = store.data.entries;
  const done = all.filter(e => !upcoming(e)), future = all.filter(e => upcoming(e)).sort((a, b) => a.date - b.date);
  const plans = root.KisPlans.occurrences(store);
  const rows = apply(done, f, state.query);
  for (const id of [...state.selection]) if (!rows.some(e => e.id === id)) state.selection.delete(id);
  const sel = rows.filter(e => state.selection.has(e.id));
  const totals = root.KisHome.sortedTotals(rows), pending = rows.filter(e => !e.payKnown && e.kind !== 'job').length;
  const n = activeCount(f), plannedCount = future.length + plans.length;

  let list = '';
  if (state.planned) {
    const items = [...future.map(e => ({ at: e.date, e })), ...plans.map(o => ({ at: root.KisPlan.startMillis(o.date, o.start), o }))].sort((a, b) => a.at - b.at);
    list = items.length ? items.map(x => x.e ? shiftCard(x.e, store, { tag: 'planned-entry' }) : root.KisPlans.card(x.o, store)).join('')
      : '<div class="empty" data-testid="planned-empty"><h3>אין משמרות מתוכננות</h3></div>';
  } else if (!rows.length) {
    list = `<div class="empty"><h3>${done.length ? 'אין משמרות להצגה' : 'עוד אין משמרות'}</h3><p class="caption">${done.length ? 'אפשר לשנות חיפוש או סינון' : 'משמרת ראשונה מוסיפים בכפתור +'}</p></div>`;
  } else if (f.sort === 'ADDED') list = rows.map(e => shiftCard(e, store, { selected: state.selection.has(e.id) })).join('');
  else {
    const weeks = []; for (const e of rows) { const w = root.KisHome.weekStart(e.date); if (weeks.at(-1)?.[0] === w) weeks.at(-1)[1].push(e); else weeks.push([w, [e]]); }
    list = weeks.map(([w, l]) => `<div class="week-head"><span>${esc(weekLabel(w))}</span><span>${U().hours(l.reduce((s, e) => s + e.hours, 0))} ש׳</span></div>${l.map(e => shiftCard(e, store, { selected: state.selection.has(e.id) })).join('')}`).join('');
  }

  el.innerHTML = `
  <div class="title-row"><h1 class="page-title">היסטוריה</h1>${state.planned ? '' : `
    <button class="icon-btn history-tool" aria-label="חיפוש" data-a="search" data-testid="history-search">${ICON.search}</button>
    `}<button class="chip${n ? ' on' : ''}" data-a="filters" data-testid="history-filters">${ICON.filter}${n ? 'סינון ומיון · ' + n : 'סינון ומיון'}</button>${state.planned ? '' : `
    <button class="icon-btn history-tool" aria-label="העתקת דוח" data-a="copy" data-testid="history-copy">${ICON.copy}</button>`}</div>
  ${state.planned ? '' : `
    ${state.searching ? `<div class="row" style="align-items:center;margin-bottom:8px"><input class="input grow" type="search" placeholder="חיפוש: מקום, הערה, עובד, סכום" value="${esc(state.query)}" data-f="q" aria-label="חיפוש" data-testid="history-search-input">
      <button class="icon-btn" aria-label="ניקוי" data-a="clear" data-testid="history-search-clear">${ICON.close}</button><button class="chip" data-a="close-search" data-testid="history-search-close">סגירה</button></div>`
      : state.query ? `<div class="row" style="margin-bottom:8px"><button class="chip on" data-a="search" data-testid="history-query-chip">חיפוש: ${esc(state.query)}</button><button class="icon-btn" aria-label="ניקוי החיפוש" data-a="clear">${ICON.close}</button></div>` : ''}
    <div class="row" style="align-items:center"><button class="plain caption grow" style="flex:1;color:var(--muted);font-size:13px;min-height:44px" data-a="filters" data-testid="history-scope">מוצג: ${esc(describe(f))}</button>
      ${n ? '<button class="chip" data-a="reset" data-testid="history-reset">הצגת הכול</button>' : ''}</div>
    ${f.period === 'MONTH' ? `<div class="row" style="align-items:center"><b class="grow" style="font:22px/28px var(--display)" data-testid="history-month">${esc(U().monthName(f.month))}</b>
      <button class="icon-btn" aria-label="חודש קודם" data-a="prev">${ICON.prev}</button><button class="icon-btn" aria-label="חודש הבא" data-a="next">${ICON.next}</button></div>` : ''}
    <div class="card" style="margin-top:10px" data-testid="history-total"><div class="caption">${rows.length} משמרות · ${U().hours(rows.reduce((s, e) => s + e.hours, 0))} שעות</div>
      <div style="display:flex;gap:14px">${totals.length ? totals.map(([c, v]) => `<b class="amount">${moneyHtml(v, c)}</b>`).join('') : '<b class="amount">—</b>'}</div>
      ${pending ? `<button class="pending-flag" data-a="pending" data-testid="history-pending">${pending === 1 ? 'משמרת אחת מחכה לתעריף ←' : pending + ' משמרות מחכות לתעריף ←'}</button>` : ''}</div>`}
  <div class="list" style="margin-top:8px;${sel.length ? 'padding-bottom:150px' : ''}" data-testid="history-list">${list}</div>

  ${sel.length ? selectionBar(sel, rows) : ''}`;

  const q = s => el.querySelector(s), redraw = () => app.refresh();
  el.querySelectorAll('[data-a=search]').forEach(b => b.onclick = () => { state.searching = true; redraw(); q('[data-f=q]')?.focus(); });
  // Hebrew keyboards compose words: never rebuild the field in the middle of a composition.
  q('[data-f=q]')?.addEventListener('compositionend', e => e.target.dispatchEvent(new Event('input')));
  q('[data-f=q]')?.addEventListener('input', e => { if (e.isComposing) return; state.query = e.target.value; const pos = e.target.selectionStart; redraw(); const i = q('[data-f=q]'); i.focus(); i.setSelectionRange(pos, pos); });
  el.querySelectorAll('[data-a=clear]').forEach(b => b.onclick = () => { state.query = ''; redraw(); q('[data-f=q]')?.focus(); });
  q('[data-a=close-search]')?.addEventListener('click', () => { state.searching = false; redraw(); });
  el.querySelectorAll('[data-a=filters]').forEach(b => b.onclick = () => filtersSheet(app, done, redraw));
  q('[data-a=reset]')?.addEventListener('click', () => { state.filter = defaults(); redraw(); });
  q('[data-a=prev]')?.addEventListener('click', () => { state.filter = { ...f, month: f.month - 1 }; redraw(); });
  q('[data-a=next]')?.addEventListener('click', () => { state.filter = { ...f, month: f.month + 1 }; redraw(); });
  q('[data-a=copy]')?.addEventListener('click', () => copySheet(rows, f));
  q('[data-a=pending]')?.addEventListener('click', () => { state.selection = new Set(rows.filter(e => !e.payKnown && e.kind !== 'job').map(e => e.id)); redraw(); bulkRate(app, redraw); });
  el.querySelectorAll('.shift[data-id]').forEach(b => {
    let timer = null, long = false;
    // Touch/primary press only; a right click selects through contextmenu, and a redrawn (detached) card never fires.
    const start = e => { long = false; if (e.button !== 0) return; timer = setTimeout(() => { if (!b.isConnected) return; long = true; if (!state.planned) { state.selection.add(b.dataset.id); U().haptic('tick'); redraw(); } }, 480); };
    const stop = () => clearTimeout(timer);
    b.addEventListener('pointerdown', start); ['pointerup', 'pointerleave', 'pointercancel'].forEach(ev => b.addEventListener(ev, stop));
    b.addEventListener('contextmenu', e => { e.preventDefault(); stop(); if (!state.planned) { state.selection.add(b.dataset.id); redraw(); } });
    b.onclick = () => { if (long) return; if (state.selection.size) { state.selection.has(b.dataset.id) ? state.selection.delete(b.dataset.id) : state.selection.add(b.dataset.id); redraw(); } else app.openDetail(b.dataset.id); };
  });
  root.KisPlans.bind(el, app);
  bindSelection(el, app, sel, redraw);
}

function selectionBar(sel, rows) {
  const missing = sel.filter(e => !e.payKnown && e.kind !== 'job').length, t = root.KisHome.sortedTotals(sel);
  return `<div class="selection-bar" data-testid="selection-bar"><div class="row" style="align-items:center"><b class="grow" data-testid="selection-count">נבחרו ${sel.length}</b>
    <span>${t.map(([c, v]) => U().moneyHtml(v, c)).join(' ')}</span><button class="icon-btn" aria-label="ביטול הבחירה" data-s="cancel">${U().ICON.close}</button></div>
    <div class="acts"><button data-s="all">${sel.length === rows.length ? 'ניקוי' : 'בחירת הכול'}</button><button data-s="share">שיתוף</button><button data-s="copy">העתקה</button>
      <button data-s="paid">שולם</button><button data-s="unpaid">לא שולם</button>${missing ? `<button data-s="rate" data-testid="selection-rate">השלמת תעריף · ${missing}</button>` : ''}<button data-s="delete" style="color:var(--danger)">מחיקה</button></div></div>`;
}
function bindSelection(el, app, sel, redraw) {
  const { store } = app, bar = el.querySelector('[data-testid=selection-bar]'); if (!bar) return;
  const b = s => bar.querySelector(`[data-s=${s}]`), ids = sel.map(e => e.id), clear = () => { state.selection.clear(); redraw(); };
  b('cancel').onclick = clear;
  b('all').onclick = () => { const rows = [...el.querySelectorAll('.shift[data-id]')].map(x => x.dataset.id); if (sel.length === rows.length) state.selection.clear(); else state.selection = new Set(rows); redraw(); };
  b('share').onclick = () => { U().shareOrCopy(root.KisShare.summary(sel, root.KisShare.workerName(sel, state.query)), 'שיתוף משמרות'); clear(); };
  b('copy').onclick = async () => { const text = root.KisShare.copy(sel, root.KisShare.workerName(sel, state.query)); if (await U().copy(text)) U().toast('הועתק ✓ · מוכן להדבקה בוואטסאפ'); else U().showText(text); clear(); };
  b('paid').onclick = () => { store.setPaid(ids, true); U().toast('סומן כשולם ✓'); clear(); };
  b('unpaid').onclick = () => { store.setPaid(ids, false); U().toast('סומן כלא שולם'); clear(); };
  b('rate')?.addEventListener('click', () => bulkRate(app, redraw));
  b('delete').onclick = () => U().confirmSheet(sel.length === 1 ? 'למחוק משמרת אחת?' : `למחוק ${sel.length} משמרות?`, 'אפשר לבטל מיד אחרי המחיקה.', 'מחיקה', () => {
    const gone = store.deleteEntries(ids); state.selection.clear(); U().haptic('warn');
    U().toast(gone.length === 1 ? 'המשמרת נמחקה' : gone.length + ' משמרות נמחקו', 'ביטול', () => store.restoreEntries(gone));
  });
}

/** One rate for every selected shift that has none. Known amounts and fixed-price jobs never change. */
function bulkRate(app, after) {
  const { store } = app, targets = store.data.entries.filter(e => state.selection.has(e.id) && !e.payKnown && e.kind !== 'job');
  if (!targets.length) return;
  U().sheet('rate-sheet', (el, close) => {
    el.innerHTML = `<h2>השלמת תעריף</h2><p class="caption">${targets.length === 1 ? 'למשמרת אחת בלי תעריף' : `ל-${targets.length} משמרות בלי תעריף`}. משמרות עם סכום ידוע לא משתנות.</p>
      <label class="field">תעריף לשעה<input class="input" inputmode="decimal" data-f="r" data-testid="bulk-rate-input"></label><p class="caption" data-f="p"></p><button class="btn primary" data-a="ok" data-testid="bulk-rate-apply" disabled>החלה</button>`;
    const input = el.querySelector('[data-f=r]'), ok = el.querySelector('[data-a=ok]'), p = el.querySelector('[data-f=p]');
    const records = targets.map(e => ({ id: e.id, isJob: false, start: e.isTimeRange ? e.startTime : null, end: e.isTimeRange ? e.endTime : null,
      breakMinutes: e.isTimeRange && e.startTime && e.endTime ? Math.max(0, Math.round(root.KisRules.span(e.startTime, e.endTime).minutes - e.hours * 60)) : 0, hours: e.hours, rate: null, bonus: e.bonus || 0 }));
    const rateOf = () => Number(input.value.trim().replace(',', '.'));
    input.oninput = () => { const r = rateOf(); ok.disabled = !(input.value.trim() && r >= 0);
      if (!ok.disabled) { // ₪ and $ are never added together
        const res = root.KisRules.bulkRate(records, r), cur = new Map(targets.map(e => [e.id, e.currency])), by = {};
        for (const x of res.updated) by[cur.get(x.id)] = (by[cur.get(x.id)] || 0) + x.total;
        p.textContent = 'יתווסף: ' + Object.entries(by).map(([c, v]) => U().money(Math.round(v * 100) / 100, c)).join(' · '); } };
    ok.onclick = () => {
      const rate = rateOf(), byId = new Map(targets.map(e => [e.id, e])), before = [];
      store.update(d => d.entries.forEach(e => { const t = byId.get(e.id); if (!t || e.payKnown || e.kind === 'job') return; before.push({ ...e });
        e.hourlyRate = rate; e.totalEarnings = root.KisPay.amount(e.hours, rate, e.bonus || 0); e.payKnown = true; }));
      state.selection.clear(); close(); U().haptic('success');
      U().toast(before.length === 1 ? 'תעריף הושלם למשמרת אחת ✓' : `תעריף הושלם ל-${before.length} משמרות ✓`, 'ביטול', () =>
        store.update(d => d.entries.forEach((e, i) => { const old = before.find(b => b.id === e.id); if (old) d.entries[i] = old; }))); after?.();
    };
  });
}

function copySheet(rows, f) {
  U().sheet('copy-sheet', (el, close) => {
    el.innerHTML = `<h2>העתקת הדוח המוצג</h2><p class="caption">${rows.length} משמרות לפי מה שמוצג עכשיו.</p>
      <button class="btn ghost" data-a="xl" data-testid="copy-excel">Excel (טבלה)</button><button class="btn ghost" data-a="wa" data-testid="copy-whatsapp">סיכום טכני</button>`;
    el.querySelector('[data-a=xl]').onclick = async () => { close(); if (!rows.length) return U().toast('אין שורות להעתקה'); const t = root.KisShare.excel(rows); if (await U().copy(t)) U().toast('הטבלה הועתקה ✓ · מוכנה להדבקה באקסל'); else U().showText(t, 'טבלה לאקסל'); };
    el.querySelector('[data-a=wa]').onclick = async () => { close(); const t = root.KisShare.copy(rows, root.KisShare.workerName(rows, state.query)); if (await U().copy(t)) U().toast('הדוח הועתק ✓'); else U().showText(t); };
  });
}

function filtersSheet(app, done, after) {
  const { store } = app, { esc } = U();
  let f = { ...state.filter }, planned = state.planned;
  const plannedCount = store.data.entries.filter(U().upcoming).length + root.KisPlans.occurrences(store).length;
  U().sheet('filters-sheet', (el, close) => {
    const seg = (key, opts, tag) => `<div class="seg">${opts.map(([v, l]) => `<button aria-pressed="${f[key] === v}" data-k="${key}" data-v="${v}" data-testid="${tag}-${v}">${l}</button>`).join('')}</div>`;
    const draw = () => {
      const count = planned ? plannedCount : apply(done, f, state.query).length;
      el.innerHTML = `<h2>סינון ומיון</h2>
        <span class="caption">מצב המשמרות</span><div class="seg"><button aria-pressed="${!planned}" data-plan="false" data-testid="history-worked">עבדתי</button><button aria-pressed="${planned}" data-plan="true" data-testid="history-planned">מתוכנן · ${plannedCount}</button></div>
        ${planned ? '' : `<div><b class="label" style="font-size:15px">אילו תאריכים להציג</b></div>${seg('period', PERIODS, 'period')}
        ${f.period === 'MONTH' ? `<div class="row" style="align-items:center"><b class="grow">${esc(U().monthName(f.month))}</b><button class="icon-btn" aria-label="חודש קודם" data-m="-1">${U().ICON.prev}</button><button class="icon-btn" aria-label="חודש הבא" data-m="1">${U().ICON.next}</button></div>` : ''}
        ${f.period === 'RANGE' ? `<div class="row"><label class="field grow">מתאריך<input type="date" class="input" data-d="from" value="${iso(f.from)}"></label><label class="field grow">עד תאריך<input type="date" class="input" data-d="to" value="${iso(f.to)}"></label></div>` : ''}
        <div class="divider"></div><div><b class="label" style="font-size:15px">סדר</b></div>${seg('sort', SORTS, 'sort')}
        <div class="divider"></div><div><b class="label" style="font-size:15px">סינון</b></div>
        <span class="caption">תשלום</span>${seg('pay', PAYS, 'pay')}
        <span class="caption">מקום עבודה</span><div class="chips"><button class="chip" aria-pressed="${!f.category}" data-c="">הכול</button>${store.data.categories.map(c => `<button class="chip" aria-pressed="${f.category === c.name}" data-c="${esc(c.name)}">${esc(c.name)}</button>`).join('')}</div>
        <span class="caption">מטבע</span>${seg('currency', [[null, 'הכול'], ['₪', '₪ שקל'], ['$', '$ דולר']], 'currency')}`}
        <div class="row"><button class="btn ghost grow" data-a="reset" data-testid="filters-reset">איפוס</button><button class="btn primary" style="flex:2" data-a="apply" data-testid="filters-apply">${count === 0 ? 'אין משמרות להצגה' : count === 1 ? 'הצגת משמרת אחת' : `הצגת ${count} משמרות`}</button></div>`;
      el.querySelectorAll('[data-plan]').forEach(b => b.onclick = () => { planned = b.dataset.plan === 'true'; draw(); });
      el.querySelectorAll('[data-k]').forEach(b => b.onclick = () => { f = { ...f, [b.dataset.k]: b.dataset.v === 'null' ? null : b.dataset.v }; draw(); });
      el.querySelectorAll('[data-m]').forEach(b => b.onclick = () => { f = { ...f, month: f.month + Number(b.dataset.m) }; draw(); });
      el.querySelectorAll('[data-d]').forEach(i => i.onchange = () => { if (i.value) { const [y, m, d] = i.value.split('-').map(Number); f = { ...f, [i.dataset.d]: new Date(y, m - 1, d).getTime() }; draw(); } });
      el.querySelectorAll('[data-c]').forEach(b => b.onclick = () => { f = { ...f, category: b.dataset.c || null }; draw(); });
      el.querySelector('[data-a=reset]').onclick = () => { f = defaults(); planned = false; draw(); };
      el.querySelector('[data-a=apply]').onclick = () => { state.filter = f; state.planned = planned; state.selection.clear(); state.searching = false; close(); after(); };
    };
    draw();
  });
}
const iso = ms => { const d = new Date(ms); return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`; };

root.KisHistory = { render, state, apply, describe, defaults, activeCount, bulkRate, iso };
})(globalThis);
