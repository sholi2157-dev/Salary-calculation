/* The 2.0 web shell: three pages (home, history, settings) with the floating pill navigation, the editor, details and messages. */
(function (root) {
'use strict';
const U = () => root.KisUi;
const store = root.KisStoreFactory.createStore(localStorage);
const app = {
  store, page: 'home', editorOpen: false, closeEditor: null,
  occurrences: () => root.KisPlans.occurrences(store),
  go(page, opts = {}) { this.page = page; this.pageOpts = opts; render(); window.scrollTo({ top: 0 }); },
  refresh() { render(); },
  applyTheme() { const t = store.local.theme || 'system'; if (t === 'system') delete document.documentElement.dataset.theme; else document.documentElement.dataset.theme = t;
    document.querySelector('meta[name=theme-color]')?.setAttribute('content', U().isDark() ? '#121212' : '#EDF0F5'); },
  openNew() { if (!store.data.categories.length) return root.KisSettings.placeSheet(app, null, () => root.KisEditor.open(app)); root.KisEditor.open(app); },
  openDetail(id) { const e = store.data.entries.find(x => x.id === id); if (e) detail(e); },
  startLive, stopLive, cancelLive() { const t = store.cancelTimer(); U().toast('המשמרת הפעילה בוטלה', 'ביטול', () => store.restoreTimer(t)); },
  completePending() { app.go('history', { selectPending: true }); setTimeout(() => root.KisHistory.bulkRate(app, () => render()), 0); },
};
root.Kis = app;

function render() {
  if (app.editorOpen) return; // the editor owns the screen; the pages redraw when it closes
  const main = document.getElementById('page');
  const opts = app.pageOpts || {}; app.pageOpts = {};
  if (store.broken) { main.innerHTML = `<div class="card" role="alert"><h2>אי אפשר לקרוא את הנתונים השמורים</h2><p class="caption">${U().esc(store.broken)}. לא בוצע שום שינוי. אפשר לשחזר מגיבוי בהגדרות.</p></div>`; }
  else if (app.page === 'history') root.KisHistory.render(main, app, opts);
  else if (app.page === 'settings') root.KisSettings.render(main, app);
  else root.KisHome.render(main, app);
  document.querySelectorAll('.nav [data-page]').forEach(b => b.setAttribute('aria-current', b.dataset.page === app.page ? 'page' : 'false'));
  document.querySelector('.nav-wrap').hidden = app.page === 'history' && root.KisHistory.state.selection.size > 0;
}

// ---- details of one shift: edit, share, copy, delete, paid; group reports ----
function detail(e) {
  const { esc, moneyHtml } = U(), pay = root.KisPay.pay(e), group = e.isGroupShift ? root.KisPay.workers(e) : [];
  U().sheet('shift-detail', (el, close) => {
    const sum = e.isGroupShift ? root.KisPay.group(e) : null;
    el.innerHTML = `<div class="row" style="align-items:center">${U().avatar(e.category, store, U().upcoming(e))}<div class="grow"><b class="label">${esc(e.category)}</b><div class="caption">${esc(U().dayLabelLong(e.date))} · ${esc(U().timeLabel(e))}</div></div></div>
      <div style="display:flex;align-items:baseline;gap:10px">${pay != null ? `<b class="hero" style="font-size:44px" data-testid="detail-amount">${moneyHtml(pay, e.currency)}</b>` : `<span class="tag warn">${e.kind === 'job' ? 'סכום חסר' : 'תעריף חסר'}</span>`}
        <span class="caption">${e.kind === 'job' ? 'קבלנות' : `${U().hours(e.hours)} שעות${e.payKnown ? ' · ' + esc(U().money(e.hourlyRate, e.currency)) + ' לשעה' : ''}`}${e.bonus > 0 ? ' · בונוס ' + esc(U().money(e.bonus, e.currency)) : ''}</span></div>
      ${e.notes ? `<p style="margin:0">${esc(e.notes)}</p>` : ''}
      ${!pay && e.kind !== 'job' ? '<button class="btn ghost small" data-a="rate">השלמת תעריף</button>' : ''}
      <div style="display:grid;grid-template-columns:1fr 1fr;gap:8px"><button class="btn ghost small" data-a="edit" data-testid="detail-edit">עריכה</button><button class="btn ghost small" data-a="share">שיתוף</button><button class="btn ghost small" data-a="copy">העתקה</button><button class="btn ghost small" data-a="del" style="color:var(--danger)">מחיקה</button></div>
      <button class="btn small ${e.isPaid ? 'ghost' : 'primary'}" data-a="paid" data-testid="detail-paid">${e.isPaid ? 'שולם ✓ · נגיעה לבטל' : 'סימון כשולם'}</button>
      ${group.length ? `<div class="divider"></div><b class="label">הקבוצה</b>${sum ? `<p class="caption" style="margin:0">המעסיק משלם ${esc(U().money(sum.employerTotal, e.currency))} · נשאר לי ${esc(U().money(sum.myNet, e.currency))}</p>` : '<p class="caption" style="margin:0">חסר תעריף לחלק מהקבוצה</p>'}
        <button class="btn ghost small" data-a="employer">דו״ח לקבלן (כל הקבוצה)</button>
        ${group.map((w, i) => `<div class="row" style="align-items:center"><span class="grow">${esc(w.name)} · ${U().hours(w.hours)} ש׳</span><button class="chip" data-w="${i}">שיתוף לעובד</button><button class="chip" data-wp="${i}">${w.isPaid ? 'שולם ✓' : 'לא שולם'}</button></div>`).join('')}` : ''}`;
    const q = s => el.querySelector(s);
    q('[data-a=edit]').onclick = () => { close(); root.KisEditor.open(app, { entry: e }); };
    q('[data-a=share]').onclick = () => U().shareOrCopy(root.KisShare.personal(e), 'שיתוף משמרת');
    q('[data-a=copy]').onclick = async () => { const t = root.KisShare.personal(e); if (await U().copy(t)) U().toast('הועתק ✓'); else U().showText(t); };
    q('[data-a=del]').onclick = () => { close(); const gone = store.deleteEntries([e.id]); U().toast('המשמרת נמחקה', 'ביטול', () => store.restoreEntries(gone)); };
    q('[data-a=paid]').onclick = () => { store.setPaid([e.id], !e.isPaid); close(); };
    q('[data-a=rate]')?.addEventListener('click', () => { close(); root.KisHistory.state.selection = new Set([e.id]); root.KisHistory.bulkRate(app, render); });
    q('[data-a=employer]')?.addEventListener('click', () => U().shareOrCopy(root.KisShare.employer(e, (store.local.name || '').trim() || null), 'סיכום לקבלן'));
    el.querySelectorAll('[data-w]').forEach(b => b.onclick = () => U().shareOrCopy(root.KisShare.worker(e, group[+b.dataset.w]), 'שיתוף לעובד'));
    el.querySelectorAll('[data-wp]').forEach(b => b.onclick = () => {
      const i = +b.dataset.wp; store.update(d => { const row = d.entries.find(x => x.id === e.id); if (!row) return;
        const list = JSON.parse(row.groupWorkersJson || '[]'); if (list[i]) list[i].isPaid = !list[i].isPaid; row.groupWorkersJson = JSON.stringify(list); }); close(); });
  });
}

// ---- live shift ----
function startLive() {
  const cats = store.data.categories;
  if (!cats.length) return root.KisSettings.placeSheet(app, null, () => startLive());
  const def = root.WorkCategories.defaultName(cats, store.data.webPreferences);
  U().sheet('live-start-sheet', (el, close) => {
    let cat = def;
    const draw = () => { const c = cats.find(x => x.name === cat);
      el.innerHTML = `<h2>מתחילים משמרת</h2><div class="chips">${cats.map(x => `<button class="chip" aria-pressed="${x.name === cat}" data-c="${U().esc(x.name)}">${U().esc(x.name)}</button>`).join('')}</div>
        <div class="row" style="align-items:flex-end"><label class="field grow">תעריף לשעה<input class="input" inputmode="decimal" data-f="r" value="${c ? c.defaultRate : ''}" placeholder="לא חובה"></label>
        <div class="seg">${['₪', '$'].map(m => `<button aria-pressed="${root.WorkCategories.currency(cat, store.data.webPreferences) === m}" disabled>${m}</button>`).join('')}</div></div>
<button class="btn primary" data-a="go" data-testid="live-go">התחלה</button>`;
      el.querySelectorAll('[data-c]').forEach(b => b.onclick = () => { cat = b.dataset.c; draw(); });
      el.querySelector('[data-a=go]').onclick = () => { const raw = el.querySelector('[data-f=r]').value.trim(), r = raw === '' ? null : Number(raw);
        if (r != null && !(r >= 0)) return; store.startTimer(cat, r, root.WorkCategories.currency(cat, store.data.webPreferences)); close(); U().haptic('success'); render(); };
    };
    draw();
  });
}
function stopLive() {
  const t = store.timer(); if (!t) return;
  const finish = () => {
    // Stopped in another tab meanwhile: never add a second row.
    if (store.timer()?.id !== t.id) return;
    const now = Date.now();
    let row;
    try { row = root.WorkRuntime.stopEntry(t, now); } catch (e) { U().toast(e.message); return; }
    const start = new Date(t.startedAt), end = new Date(now), hh = d => String(d.getHours()).padStart(2, '0') + ':' + String(d.getMinutes()).padStart(2, '0');
    const unknown = t.rateUnknown === true;
    const entry = { ...row, keepId: t.id, isTimeRange: true, startTime: hh(start), endTime: hh(end), kind: 'shift', payKnown: !unknown, bonus: 0,
      hourlyRate: unknown ? 0 : t.rate, totalEarnings: unknown ? 0 : root.KisPay.amount(row.hours, t.rate) };
    const [id] = store.addEntries([entry]); store.cancelTimer(); U().haptic('success');
    U().toast('המשמרת נשמרה ✓', 'ביטול', () => { store.update(d => { d.entries = d.entries.filter(e => e.id !== id); }); store.restoreTimer(t); });
  };
  if (Date.now() - t.startedAt < 60000) U().confirmSheet('המשמרת נמשכה פחות מדקה', 'לשמור בכל זאת?', 'שמירה', finish, false); else finish();
}

// ---- start ----
function start() {
  document.body.insertAdjacentHTML('afterbegin', '<div class="kis-glow" aria-hidden="true"></div>');
  store.load(); app.applyTheme();
  matchMedia('(prefers-color-scheme: dark)').addEventListener?.('change', () => { app.applyTheme(); render(); });
  document.querySelectorAll('.nav [data-page]').forEach(b => b.onclick = () => { root.KisHistory.state.selection.clear(); app.go(b.dataset.page); });
  document.querySelector('.nav [data-a=add]').onclick = () => app.openNew();
  store.subscribe(render);
  // Another tab changed the data: read it again (never overwrite it with an older copy).
  window.addEventListener('storage', e => { if (!e.key || ['work_complete_backup', 'work_series_v1', 'kis_web_v1'].includes(e.key) || e.key.startsWith('work_active_shift_v1')) { store.load(); render(); } });
  document.addEventListener('keydown', e => { if (e.key === 'Escape') { if (!U().closeTopSheet() && app.editorOpen) app.editorBack?.(); } });
  // Ended weekly plans are recorded on open and on every return to the tab, with an undo.
  const catchUp = () => { const ids = root.KisPlans.catchUp(store);
    if (ids.length) U().toast(ids.length === 1 ? 'משמרת קבועה הסתיימה ונרשמה' : `${ids.length} משמרות קבועות הסתיימו ונרשמו`, 'ביטול', () => {
      const rows = store.data.entries.filter(e => ids.includes(e.id) && e.autoConfirmed);
      store.update(d => { d.entries = d.entries.filter(e => !rows.some(r => r.id === e.id)); });
      store.updatePlans(p => rows.forEach(r => { const k = r.seriesKey, sid = k.slice(0, k.lastIndexOf('@')), date = k.slice(k.lastIndexOf('@') + 1);
        const x = p.exceptions.find(e => e.seriesId === sid && e.date === date); if (x) x.hold = true; else p.exceptions.push(store.exception(sid, date, { hold: true })); }));
    }); };
  document.addEventListener('visibilitychange', () => { if (document.visibilityState === 'visible') { store.load(); catchUp(); render(); } });
  setInterval(() => { if (document.visibilityState === 'visible' && !app.editorOpen && !U().anySheet()) render(); }, 60000);
  render();
  catchUp();
  // Once: the 2.0 launch, the name, then the tour. Reminders and a restored draft wait for the next open.
  if (root.KisLaunch.maybeStart(app)) return;
  const notes = root.KisSettings.dueReminders(app);
  const monthly = notes.find(n => n.kind === 'monthly');
  if (monthly) U().toast(monthly.title, 'לסיכום', () => root.KisSettings.monthSummary(app, monthly.month));
  else if (notes[0]) U().toast(notes[0].title, 'להשלמה', () => app.completePending());
  // A new shift typed before a reload or an update comes back as it was.
  const draft = root.KisEditor.pendingDraft();
  if (draft && !draft.id) { root.KisEditor.open(app, { restore: draft }); U().toast('הטיוטה שוחזרה'); }
}
/** The update manager asks before activating a new version: drafts are already saved; an open edit must finish first. */
root.prepareWebUpdate = () => !(app.editorOpen && document.querySelector('[data-testid=edit-page]'));

if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', start); else start();
})(globalThis);
