/* The 2.0 web shell: three pages (home, history, settings) with the floating pill navigation, the editor, details and messages. */
(function (root) {
'use strict';
const U = () => root.KisUi;
// The signed-in account's own copy (web/kis/account.js); the browser's existing data for the first account.
const storage = root.KisAccount.storageFor(localStorage);
const store = root.KisStoreFactory.createStore(storage);
const app = {
  store, storage, page: 'home', editorOpen: false, closeEditor: null,
  occurrences: () => root.KisPlans.occurrences(store),
  go(page, opts = {}) { root.KisSettings.event('page', page); if (page === 'settings') root.KisSettings.open(opts.section); this.page = page; this.pageOpts = opts; render(); window.scrollTo({ top: 0 }); },
  refresh() { render(); },
  applyTheme() { const t = store.local.theme || 'system'; if (t === 'system') delete document.documentElement.dataset.theme; else document.documentElement.dataset.theme = t;
    document.querySelector('meta[name=theme-color]')?.setAttribute('content', U().isDark() ? '#121212' : '#F1F3F8'); },
  openNew() { if (!store.data.categories.length) return root.KisSettings.placeSheet(app, null, () => root.KisEditor.open(app)); root.KisEditor.open(app); },
  openDetail(id) { const e = store.data.entries.find(x => x.id === id); if (e) detail(e); },
  startLive, stopLive, cancelLive(id) {
    const live = store.timer();
    if (!live || live.id !== id) { app.refresh(); U().toast('המשמרת הפעילה השתנתה, לא בוטלה'); return; }
    const t = store.cancelTimer(); U().toast('המשמרת הפעילה בוטלה', 'ביטול', () => store.restoreTimer(t));
  },
  completePending() { app.go('history', { selectPending: true }); setTimeout(() => root.KisHistory.bulkRate(app, () => render()), 0); },
};
root.Kis = app;

function render() {
  if (app.editorOpen) return; // the editor owns the screen; the pages redraw when it closes
  const main = document.getElementById('page');
  const opts = app.pageOpts || {}; app.pageOpts = {};
  main.dataset.page = app.page;
  // The same control keeps the keyboard focus across the redraw (audit M-33); a page change starts at the page.
  const mark = main.dataset.drawn === app.page ? U().focusMark(main) : null; main.dataset.drawn = app.page;
  // Unreadable stored data: the recovery is on this screen itself, since no other page can open (audit CW-15).
  if (store.broken) { main.innerHTML = root.KisSettings.recoveryPanel(app); root.KisSettings.bindRecovery(main, app); }
  else if (app.page === 'history') root.KisHistory.render(main, app, opts);
  else if (app.page === 'settings') root.KisSettings.render(main, app);
  else {
    root.KisHome.render(main, app);
    // FB23-F11: once after a new release, the main highlights, under the greeting; closing it records that it was seen.
    if (app.whatsNew) {
      main.querySelector('.hello')?.insertAdjacentHTML('afterend', root.KisWhatsNew.cardHtml(app.whatsNew));
      main.querySelector('[data-a=whats-new-ok]')?.addEventListener('click', () => { root.KisWhatsNew.seen(store); app.whatsNew = null; render(); });
    }
  }
  document.querySelectorAll('.nav [data-page]').forEach(b => b.setAttribute('aria-current', b.dataset.page === app.page ? 'page' : 'false'));
  U().focusRestore(main, mark);
  document.querySelector('.nav-wrap').hidden = app.page === 'history' && root.KisHistory.state.selection.size > 0;
}

// ---- details of one shift: edit, share, copy, delete, paid; group reports ----
function detail(e) {
  const { esc, moneyHtml } = U(), pay = root.KisPay.pay(e), group = e.isGroupShift ? root.KisPay.workers(e) : [];
  U().sheet('shift-detail', (el, close) => {
    const sum = e.isGroupShift ? root.KisPay.group(e) : null;
    if (e.isGroupShift) el.innerHTML = groupDetailHtml(e, sum, group); else el.innerHTML = `<div class="row" style="align-items:center">${U().avatar(e.category, store, U().upcoming(e))}<div class="grow"><b class="label">${esc(e.category)}</b><div class="caption">${esc(U().dayLabelLong(e.date))} · ${esc(U().timeLabel(e))}</div></div></div>
      <div style="display:flex;align-items:baseline;gap:10px">${pay != null ? `<b class="hero" style="font-size:2.75rem" data-testid="detail-amount">${moneyHtml(pay, e.currency)}</b>` : `<span class="tag warn">${e.kind === 'job' ? 'סכום חסר' : 'תעריף חסר'}</span>`}
        <span class="caption">${e.kind === 'job' ? 'קבלנות' : `${U().duration(e.hours)} שעות${e.payKnown ? ' · ' + esc(U().money(e.hourlyRate, e.currency)) + ' לשעה' : ''}`}${e.bonus > 0 ? ' · בונוס ' + esc(U().money(e.bonus, e.currency)) : ''}</span></div>
      ${e.notes ? `<p style="margin:0">${esc(e.notes)}</p>` : ''}
      ${e.passThrough > 0 ? `<p class="caption" style="margin:0" data-testid="detail-pass-through">החזר הוצאות ${esc(U().money(e.passThrough, e.currency))} · לא שלך${!e.isGroupShift && root.KisPay.employerDue(e) != null ? ` · <b>לקבל מהמעסיק ${esc(U().money(root.KisPay.employerDue(e), e.currency))}</b>` : ''}</p>` : ''}
      ${!pay && e.kind !== 'job' ? '<button class="btn ghost small" data-a="rate">השלמת תעריף</button>' : ''}
      <div style="display:grid;grid-template-columns:1fr 1fr;gap:8px"><button class="btn ghost small" data-a="edit" data-testid="detail-edit">עריכה</button><button class="btn ghost small" data-a="share">${e.isGroupShift ? 'החלק שלי' : 'שיתוף'}</button><button class="btn ghost small" data-a="copy">העתקה</button><button class="btn ghost small" data-a="del" style="color:var(--danger)">מחיקה</button></div>
      <button class="btn small ${e.isPaid ? 'ghost' : 'primary'}" data-a="paid" data-testid="detail-paid">${e.isPaid ? 'שולם ✓ · לחיצה לביטול' : 'סימון כשולם'}</button>
      ${group.length ? `<div class="divider"></div><b class="label">${e.kind === 'job' ? 'הצוות' : 'הקבוצה'}</b>${sum ? `<div class="team-sum" data-testid="group-summary">
          <div class="row"><span class="grow">${e.kind === 'job' ? 'שלי' : 'ההכנסה שלי (כולל עמלה)'}</span><b>${moneyHtml(sum.myNet, e.currency)}</b></div>
          ${e.kind === 'job' ? `<div class="row"><span class="grow">לצוות</span><b data-testid="group-team-pay">${moneyHtml(Math.round((sum.employerTotal - sum.myNet - (e.passThrough || 0)) * 100) / 100, e.currency)}</b></div>` : ''}
          ${e.passThrough > 0 ? `<div class="row"><span class="grow">החזר הוצאות</span><b>${moneyHtml(e.passThrough, e.currency)}</b></div>` : ''}
          <div class="row strong"><span class="grow">לקבל מהמעסיק</span><b data-testid="group-employer-due">${moneyHtml(sum.employerTotal, e.currency)}</b></div></div>`
          : `<p class="caption" style="margin:0">${e.kind === 'job' ? 'הסכומים יופיעו אחרי שיושלם הסכום שלך' : 'חסר תעריף לחלק מהקבוצה'}</p>`}
        <div class="row"><button class="btn ghost small grow" data-a="employer">דוח למעסיק</button><button class="btn ghost small grow" data-a="employer-copy">העתקת דוח הצוות</button></div>
        ${group.map((w, i) => `<div style="display:grid;gap:6px"><div class="row" style="align-items:center"><span class="grow">${esc(w.name)}${e.kind === 'job' && w.amount != null ? '' : ' · ' + U().duration(w.hours) + ' ש׳'}${root.KisPay.workerPay(w, e) != null ? ' · ' + esc(U().money(root.KisPay.workerPay(w, e), e.currency)) : ''}</span><button class="chip" data-wp="${i}">${w.isPaid ? 'שולם ✓' : 'לא שולם'}</button></div><div class="row"><button class="btn ghost small grow" data-w="${i}">שיתוף לעובד</button><button class="btn ghost small grow" data-wc="${i}">העתקה</button></div></div>`).join('')}` : ''}`;
    const q = s => el.querySelector(s);
    q('[data-a=edit]').onclick = () => { close(); root.KisEditor.open(app, { entry: e }); };
    const shareMine = () => U().shareOrCopy(root.KisShare.entry(e, false, U().upcoming(e)), 'שיתוף משמרת');
    const copyMine = async () => { const t = root.KisShare.entry(e, true, U().upcoming(e)); if (await U().copy(t)) U().toast('הועתק ✓'); else U().showText(t); };
    const shareEmployer = () => U().shareOrCopy(root.KisShare.employer(e, (store.local.name || '').trim() || null, false, U().upcoming(e)), 'סיכום לקבלן');
    const copyEmployer = async () => { const t = root.KisShare.employer(e, store.local.name, true, U().upcoming(e)); if (await U().copy(t)) U().toast('הדוח הועתק ✓'); else U().showText(t); };
    q('[data-a=share]')?.addEventListener('click', shareMine);
    q('[data-a=copy]')?.addEventListener('click', copyMine);
    // Group B: one "שיתוף" with every report, and one ⋯ per worker.
    q('[data-a=reports]')?.addEventListener('click', () => U().actionSheet('שיתוף', [
      { label: 'דוח למעסיק', onClick: shareEmployer, testid: 'report-employer' }, { label: 'העתקת דוח הצוות', onClick: copyEmployer, testid: 'report-employer-copy' },
      { label: 'החלק שלי', onClick: shareMine, testid: 'report-mine' }, { label: 'העתקת החלק שלי', onClick: copyMine, testid: 'report-mine-copy' }], 'reports-sheet'));
    el.querySelectorAll('[data-more]').forEach(b => b.onclick = () => { const i = +b.dataset.more, w = group[i];
      U().actionSheet(w.name, [
        { label: `שיתוף ל${w.name}`, onClick: () => U().shareOrCopy(root.KisShare.worker(e, w, false, U().upcoming(e)), 'שיתוף לעובד'), testid: 'worker-share' },
        { label: 'העתקה', onClick: async () => { const t = root.KisShare.worker(e, w, true, U().upcoming(e)); if (await U().copy(t)) U().toast('הועתק ✓'); else U().showText(t); }, testid: 'worker-copy' },
        { label: w.isPaid ? 'סימון כלא שולם' : 'סימון כשולם', onClick: () => markWorkerPaid(i), testid: 'worker-paid' }], 'worker-sheet'); });
    q('[data-a=del]').onclick = () => { close(); const gone = store.deleteEntries([e.id]); U().toast('המשמרת נמחקה', 'ביטול', () => store.restoreEntries(gone)); };
    q('[data-a=paid]').onclick = () => { store.setPaid([e.id], !e.isPaid); close(); };
    q('[data-a=rate]')?.addEventListener('click', () => { close(); root.KisHistory.state.selection = new Set([e.id]); root.KisHistory.bulkRate(app, render); });
    q('[data-a=employer]')?.addEventListener('click', shareEmployer);
    el.querySelectorAll('[data-w]').forEach(b => b.onclick = () => U().shareOrCopy(root.KisShare.worker(e, group[+b.dataset.w], false, U().upcoming(e)), 'שיתוף לעובד'));
    q('[data-a=employer-copy]')?.addEventListener('click', copyEmployer);
    el.querySelectorAll('[data-wc]').forEach(b => b.onclick = async () => { const t = root.KisShare.worker(e, group[+b.dataset.wc], true, U().upcoming(e)); if (await U().copy(t)) U().toast('הועתק ✓'); else U().showText(t); });
    function markWorkerPaid(i) { store.update(d => { const row = d.entries.find(x => x.id === e.id); if (!row) return;
        const list = JSON.parse(row.groupWorkersJson || '[]'); if (list[i]) list[i].isPaid = !list[i].isPaid; row.groupWorkersJson = JSON.stringify(list); }); close(); }
    el.querySelectorAll('[data-wp]').forEach(b => b.onclick = () => markWorkerPaid(+b.dataset.wp));
  });
}

/**
 * Group shift details, design decision B: the main amount is the user's own part, the same amount the lists and totals
 * show, labelled as such. Commission, the team's pay, money passed through and what the employer owes are one block
 * (all from KisPay, reconciled in whole agorot). Every report sits under one "שיתוף"; each worker is one row with ⋯.
 */
function groupDetailHtml(e, sum, group) {
  const { esc, moneyHtml } = U(), P = root.KisPay, pay = P.pay(e), job = e.kind === 'job', m = v => moneyHtml(v, e.currency);
  const team = job ? P.teamPay(e) : (() => { let c = 0; for (const w of group) { const p = P.workerPay(w, e); if (p == null) return null; c += Math.round(p * 100); } return c / 100; })();
  const parts = job ? 'קבלנות' : `${U().duration(e.hours)} שעות${e.payKnown ? ' · ' + esc(U().money(e.hourlyRate, e.currency)) + ' לשעה' : ''}${e.bonus > 0 ? ' · בונוס ' + esc(U().money(e.bonus, e.currency)) : ''}`;
  return `<div class="row" style="align-items:center">${U().avatar(e.category, store, U().upcoming(e))}<div class="grow"><b class="label">${esc(e.category)} · ${job ? 'צוות' : 'קבוצה'}</b><div class="caption">${esc(U().dayLabelLong(e.date))} · ${esc(U().timeLabel(e))}</div></div></div>
    <div class="gd-hero"><span class="caption">החלק שלי · ${parts}</span>${pay != null ? `<b class="gd-amount" data-testid="detail-amount">${m(pay)}</b>` : `<span class="tag warn">${job ? 'סכום חסר' : 'תעריף חסר'}</span>`}</div>
    ${e.notes ? `<p style="margin:0">${esc(e.notes)}</p>` : ''}
    ${sum ? `<div class="gd-lines" data-testid="group-summary">
      ${job ? '' : `<div class="row"><span class="grow">עמלה מהצוות</span><b data-testid="group-profit">${m(sum.profit)}</b></div>
      <div class="row"><span class="grow">ההכנסה שלי כולל עמלה</span><b data-testid="group-my-net">${m(sum.myNet)}</b></div>`}
      ${team != null ? `<div class="row"><span class="grow">לצוות (${group.length})</span><b data-testid="group-team-pay">${m(team)}</b></div>` : ''}
      ${e.passThrough > 0 ? `<div class="row"><span class="grow">החזר הוצאות · לא שלך</span><b data-testid="detail-pass-through">${m(e.passThrough)}</b></div>` : ''}
      <div class="row strong"><span class="grow">לקבל מהמעסיק</span><b data-testid="group-employer-due">${m(sum.employerTotal)}</b></div></div>`
      : `<p class="caption" style="margin:0" data-testid="group-summary-missing">${job ? 'הסכומים יופיעו אחרי שיושלם הסכום שלך' : 'חסר תעריף לחלק מהקבוצה'}</p>`}
    ${!pay && !job ? '<button class="btn ghost small" data-a="rate">השלמת תעריף</button>' : ''}
    <div class="gd-actions"><button class="btn ghost small" data-a="edit" data-testid="detail-edit">עריכה</button><button class="btn ghost small" data-a="reports" data-testid="detail-reports" aria-haspopup="dialog">שיתוף ▾</button><button class="btn ghost small danger-text" data-a="del" data-testid="detail-delete">מחיקה</button></div>
    <button class="btn small ${e.isPaid ? 'ghost' : 'primary'}" data-a="paid" data-testid="detail-paid">${e.isPaid ? 'שולם ✓ · לחיצה לביטול' : 'סימון כשולם'}</button>
    <div class="divider"></div><b class="label">${job ? 'הצוות' : 'הקבוצה'} · ${group.length}</b>
    <div class="gd-workers">${group.map((w, i) => { const p = P.workerPay(w, e);
      return `<div class="gd-w" data-testid="worker-row-${i}"><span class="nm"><b>${esc(w.name)}</b><small>${job && w.amount != null ? 'סכום קבוע' : U().duration(w.hours) + ' שעות'}${w.bonus > 0 ? ' · בונוס ' + esc(U().money(w.bonus, e.currency)) : ''}</small></span>
        <button class="paid-chip${w.isPaid ? ' on' : ''}" data-wp="${i}" aria-pressed="${!!w.isPaid}" aria-label="${esc(w.name)}: ${w.isPaid ? 'שולם' : 'לא שולם'}"><span>${w.isPaid ? 'שולם ✓' : 'לא שולם'}</span></button>
        <span class="pay">${p != null ? m(p) : '<span class="tag warn">חסר</span>'}</span>
        <button class="more" data-more="${i}" aria-label="פעולות ל${esc(w.name)}" data-testid="worker-more-${i}">⋯</button></div>`; }).join('')}</div>`;
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
<p class="caption" data-f="err" role="alert" data-testid="live-error" style="margin:0;color:var(--danger)"></p><button class="btn primary" data-a="go" data-testid="live-go">התחלה</button>`;
      el.querySelectorAll('[data-c]').forEach(b => b.onclick = () => { cat = b.dataset.c; draw(); });
      // A decimal comma means a decimal point (40,5 = 40.5); anything else that is not a rate is said, not ignored (audit M-09).
      el.querySelector('[data-a=go]').onclick = () => { const raw = el.querySelector('[data-f=r]').value.trim(), r = root.KisRules.inputNumber(raw);
        if (raw && !(r != null && r >= 0 && r < 100000)) { el.querySelector('[data-f=err]').textContent = 'התעריף לא תקין'; U().haptic('warn'); return; }
        store.startTimer(cat, r, root.WorkCategories.currency(cat, store.data.webPreferences)); close(); U().haptic('success'); render(); };
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
      hourlyRate: unknown ? 0 : t.rate, totalEarnings: unknown ? 0 : root.KisPay.money(row.hours, t.rate) };
    const [id] = store.addEntries([entry]); store.cancelTimer(); U().haptic('success');
    U().toast('המשמרת נשמרה ✓', 'ביטול', () => { store.update(d => { d.entries = d.entries.filter(e => e.id !== id); }); store.restoreTimer(t); });
  };
  if (Date.now() - t.startedAt < 60000) U().confirmSheet('המשמרת נמשכה פחות מדקה', 'לשמור בכל זאת?', 'שמירה', finish, false); else finish();
}

// ---- start ----
function start() {
  document.body.insertAdjacentHTML('afterbegin', '<div class="kis-glow" aria-hidden="true"></div>');
  store.load(); app.applyTheme();
  // A browser that had this site before (its 2.0 launch is done) sees this release's highlights once (FB23-F11).
  app.whatsNew = store.broken ? null : root.KisWhatsNew.pending(store, (store.local.launched || 0) >= root.KisLaunch.LAUNCH_VERSION);
  matchMedia('(prefers-color-scheme: dark)').addEventListener?.('change', () => { app.applyTheme(); render(); });
  document.querySelectorAll('.nav [data-page]').forEach(b => b.onclick = () => { root.KisHistory.state.selection.clear(); app.go(b.dataset.page); });
  document.querySelector('.nav [data-a=add]').onclick = () => app.openNew();
  // On a computer the navigation is a side bar with the app's name (hidden on a phone).
  document.querySelector('.nav').insertAdjacentHTML('afterbegin', `<div class="nav-brand" aria-hidden="true">${U().logo(34)}<b>שכר עבודות אישי</b></div>`);
  store.subscribe(render);
  // Another tab changed the data: read it again (never overwrite it with an older copy).
  window.addEventListener('storage', e => { const k = e.key && e.key.replace(/^acct:[^:]+:/, ''); if (!k || ['work_complete_backup', 'work_series_v1', 'kis_web_v1'].includes(k) || k.startsWith('work_active_shift_v1')) { store.load(); render(); } });
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
  root.KisAccount.init(app);
  document.addEventListener('kis-account', () => { if (app.page === 'settings' && !app.editorOpen && !U().anySheet()) { const card = document.querySelector('[data-testid=account-card]');
    if (card) { card.innerHTML = root.KisAccount.settingsHtml(); root.KisAccount.bindSettings(card); } } });
  // Once: the 2.0 launch, the name, then the tour. Reminders and a restored draft wait for the next open.
  if (root.KisLaunch.maybeStart(app)) return;
  const notes = root.KisSettings.dueReminders(app);
  const monthly = notes.find(n => n.kind === 'monthly');
  if (monthly) U().toast(monthly.title, 'לצפייה', () => root.KisSummaryUi.story(app, monthly.scope));
  else if (notes[0]) U().toast(notes[0].title, 'להשלמה', () => app.completePending());
  // A new shift typed before a reload or an update comes back as it was.
  const draft = root.KisEditor.pendingDraft(app.storage);
  if (draft && !draft.id) { root.KisEditor.open(app, { restore: draft }); U().toast('הטיוטה שוחזרה'); }
}

if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', start); else start();
})(globalThis);
