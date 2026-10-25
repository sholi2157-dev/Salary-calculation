/* Settings, grouped like the app: work, look, notifications, backup & data, help, profile last. Home's bell lists what arrived. */
(function (root) {
'use strict';
const U = () => root.KisUi;
const group = (title, body, info) => `<div class="title-row" style="margin:22px 0 10px"><h2 class="section-title" style="margin:0;font:22px/28px var(--display)" data-testid="settings-${title}">${title}</h2>${info ? U().infoDot(title, info) : ''}</div><div class="card" style="display:grid;gap:4px;padding:6px 16px">${body}</div>`;
const rowHtml = (title, hint, control, info) => `<div class="row" style="align-items:center;min-height:58px;padding:8px 0"><div class="grow"><div class="title-row"><span style="font-weight:500">${title}</span>${info ? U().infoDot(title, info) : ''}</div>${hint ? `<div class="caption">${hint}</div>` : ''}</div>${control}</div>`;

function render(el, app) {
  const { store } = app, { esc } = U(), prefs = store.data.webPreferences, def = root.WorkCategories.defaultName(store.data.categories, prefs);
  const theme = store.local.theme || 'system';
  el.innerHTML = `<h1 class="page-title">הגדרות</h1>
  ${group('חשבון', `<div data-testid="account-card">${root.KisAccount.settingsHtml()}</div>`, 'המשמרות, המקומות, המשמרות הקבועות וההגדרות נשמרים בחשבון Google שלך ומסתנכרנים עם האפליקציה לאנדרואיד ועם כל דפדפן שמחובר אליו.')}
  ${group('עבודה', `${rowHtml('מקום ברירת מחדל', null, `<button class="chip" data-a="places" data-testid="places-button">${esc(def || 'הוספה')}</button>`, 'נבחר אוטומטית במשמרת חדשה.')}
    <div class="divider"></div>${rowHtml('מטבע ברירת מחדל', null, `<div class="seg">${['₪', '$'].map(c => `<button aria-pressed="${prefs.mainCurrency === c}" data-cur="${c}">${c}</button>`).join('')}</div>`, 'למקומות ולמשמרות חדשים. כל משמרת שומרת את המטבע שלה, ושקלים ודולרים לא מתחברים לסכום אחד.')}
    <div class="divider"></div>${rowHtml('משמרות קבועות', store.plans.series.length ? `${store.plans.series.length} פעילות` : null, '<button class="chip" data-a="series" data-testid="series-button">ניהול</button>', 'יוצרים במסך ההוספה: "חוזרת כל שבוע". כשהזמן נגמר המשמרת נרשמת לבד, ותמיד אפשר לבטל.')}`)}
  ${group('מראה ותחושה', `${rowHtml('ערכת צבעים', null, `<div class="seg">${[['system', 'אוטומטי'], ['light', 'בהיר'], ['dark', 'כהה']].map(([v, l]) => `<button aria-pressed="${theme === v}" data-theme="${v}" data-testid="theme-${v}">${l}</button>`).join('')}</div>`)}
    <div class="divider"></div>${rowHtml('רטט במגע', null, `<button class="switch" role="switch" aria-checked="${store.local.haptics !== false}" aria-label="רטט במגע" data-a="haptics"></button>`)}`)}
  ${group('הסיכומים שלי', rowHtml('כל הסיכומים', null, '<button class="chip" data-a="recaps" data-testid="recaps-button">פתיחה</button>', 'כל סיכום חודשי ושנתי שכבר נפתח נשמר כאן.'))}
  ${group('התראות', `${rowHtml('הסיכום החודשי', null, `<button class="switch" role="switch" aria-checked="${store.local.notifyMonthly === true}" aria-label="הסיכום החודשי" data-a="monthly" data-testid="notify-monthly"></button>`, 'ב-30 לחודש (ביום האחרון בחודש קצר יותר), כשנכנסים לאתר. באתר התזכורות מופיעות כשהוא פתוח; באפליקציה לאנדרואיד הן מגיעות גם כשהיא סגורה.')}
    <div class="divider"></div>${rowHtml('תעריף חסר', null, `<button class="switch" role="switch" aria-checked="${store.local.notifyRate === true}" aria-label="תעריף חסר" data-a="rate" data-testid="notify-rate"></button>`, 'לכל היותר פעם בשבוע, ורק כשיש משמרות בלי תעריף.')}`)}
  ${group('גיבוי ונתונים', `<div class="row" style="flex-wrap:wrap;padding:10px 0 6px"><button class="btn small ghost grow" data-a="download" data-testid="backup-download">הורדת גיבוי</button><button class="btn small ghost grow" data-a="copy-backup">העתקה</button><button class="btn small ghost grow" data-a="share-backup">שיתוף</button></div>
    <div class="row" style="padding-bottom:6px"><button class="btn small ghost grow" data-a="csv">אקסל (CSV)</button><button class="btn small primary grow" data-a="import" data-testid="import-button">ייבוא נתונים</button></div>`, 'כשמחוברים לחשבון, הנתונים נשמרים בו ובדפדפן הזה. גיבוי לקובץ הוא העתק נוסף בשבילך, וגיבוי מהאפליקציה עובר לכאן ולהפך.')}
  ${group('עזרה', `${rowHtml('האפליקציה לאנדרואיד', null, '<button class="chip" data-a="android-download">הורדה והסבר</button>')}<div class="divider"></div>${rowHtml('הצגת ההדרכה מחדש', null, '<button class="chip" data-a="tour" data-testid="replay-tour">הצגה</button>')}`)}
  ${group('פרופיל', `<div style="display:grid;gap:8px;padding:10px 0"><input class="input" data-f="name" value="${esc(store.local.name || '')}" placeholder="שם פרטי" aria-label="שם פרטי" maxlength="40" data-testid="profile-name">
    <input class="input" data-f="last" value="${esc(store.local.lastName || '')}" placeholder="שם משפחה" aria-label="שם משפחה" maxlength="40" data-testid="profile-last-name"></div>`)}
  <div class="row" style="align-items:center;margin-top:24px">${U().logo(28)}<span class="caption">שכר עבודות אישי</span></div>`;

  const q = s => el.querySelector(s);
  root.KisAccount.bindSettings(el);
  // Saved as typed, without redrawing the page (no lost taps or focus).
  q('[data-f=name]').addEventListener('input', e => store.setLocal('name', e.target.value.trim().slice(0, 40), true));
  q('[data-f=last]').addEventListener('input', e => store.setLocal('lastName', e.target.value.trim().slice(0, 40), true));
  q('[data-a=monthly]').onclick = e => { const on = store.local.notifyMonthly !== true; store.setLocal('notifyMonthly', on, true); e.currentTarget.setAttribute('aria-checked', on); };
  q('[data-a=rate]').onclick = e => { const on = store.local.notifyRate !== true; store.setLocal('notifyRate', on, true); e.currentTarget.setAttribute('aria-checked', on); };
  q('[data-a=android-download]').onclick = () => root.KisDownload.open();
  q('[data-a=tour]').onclick = () => root.KisLaunch.tour(app);
  q('[data-a=places]').onclick = () => placesSheet(app);
  q('[data-a=recaps]').onclick = () => root.KisSummaryUi.archive(app);
  el.querySelectorAll('[data-cur]').forEach(b => b.onclick = () => store.update(d => { d.webPreferences = root.WorkCategories.preferences({ ...d.webPreferences, mainCurrency: b.dataset.cur }); }));
  q('[data-a=series]').onclick = () => root.KisPlans.seriesSheet(app);
  el.querySelectorAll('[data-theme]').forEach(b => b.onclick = () => { store.setLocal('theme', b.dataset.theme); app.applyTheme(); });
  q('[data-a=haptics]').onclick = () => store.setLocal('haptics', store.local.haptics === false);
  q('[data-a=download]').onclick = () => download('salary-backup.json', backupText(store), 'application/json');
  q('[data-a=copy-backup]').onclick = async () => { if (await U().copy(backupText(store))) U().toast('הגיבוי הועתק ✓'); else U().showText(backupText(store), 'גיבוי'); };
  q('[data-a=share-backup]').onclick = () => U().shareOrCopy(backupText(store), 'גיבוי');
  q('[data-a=csv]').onclick = () => download('salary.csv', root.KisShare.csv(store.data.entries.filter(e => !U().upcoming(e))), 'text/csv');
  q('[data-a=import]').onclick = () => importSheet(app);
}

function download(name, text, type) { const url = URL.createObjectURL(new Blob([text], { type })); const a = document.createElement('a'); a.href = url; a.download = name; a.click(); setTimeout(() => URL.revokeObjectURL(url), 1000); }
/** The same backup file as the Android app: version 2 when nothing 2.0 is used, version 3 otherwise. */
function backupText(store) {
  const d = store.data;
  return root.WorkTransfer.encode({ entries: d.entries, categories: d.categories, workers: d.workers, webPreferences: d.webPreferences,
    androidLocalPreferences: root.WorkRuntime.androidPreferences(d.webPreferences), series: store.plans.series, exceptions: store.plans.exceptions });
}

function importSheet(app) {
  const { store } = app;
  U().sheet('import-sheet', (el, close) => {
    let pending = null;
    el.innerHTML = `<div class="title-row"><h2>ייבוא נתונים</h2>${U().infoDot('ייבוא נתונים', 'גיבוי מהאפליקציה או מהאתר, או שורות מאקסל עם כותרות. לפני השמירה תוצג בדיקה; משמרות שכבר קיימות לא יתווספו שוב.')}</div>
      <textarea class="input" rows="6" data-f="t" placeholder="הדבקת נתונים" data-testid="import-text"></textarea>
      <label class="btn ghost small" style="position:relative">בחירת קובץ<input type="file" accept=".json,.csv,.tsv,.txt" data-f="file" style="position:absolute;inset:0;opacity:0"></label>
      <button class="btn ghost" data-a="check" data-testid="import-check">בדיקה</button><p class="caption" data-f="sum" data-testid="import-summary" style="white-space:pre-line"></p>
      <button class="btn primary" data-a="save" disabled data-testid="import-save">שמירת הנתונים</button>`;
    const t = el.querySelector('[data-f=t]'), sum = el.querySelector('[data-f=sum]'), save = el.querySelector('[data-a=save]');
    const check = () => {
      try {
        const data = root.WorkTransfer.decode(t.value);
        const keys = new Set(store.data.entries.map(e => e.seriesKey).filter(Boolean));
        const fresh = root.WorkTransfer.missing(store.data.entries, data.entries).filter(e => !e.seriesKey || !keys.has(e.seriesKey));
        const series = (data.series || []).filter(s => !store.plans.series.some(x => x.id === s.id));
        pending = { data, fresh, series };
        const tt = root.KisHome.sortedTotals(fresh);
        sum.textContent = `נקראו ${data.entries.length} משמרות. חדשות: ${fresh.length}. קיימות: ${data.entries.length - fresh.length}.` +
          (tt.length ? `\nיתווסף: ${tt.map(([c, v]) => U().money(v, c)).join(' · ')}` : '') + (series.length ? `\nמשמרות קבועות חדשות: ${series.length}` : '');
        save.disabled = !(fresh.length || series.length);
      } catch (e) { pending = null; sum.textContent = e.message; save.disabled = true; }
    };
    t.oninput = () => { pending = null; save.disabled = true; sum.textContent = ''; };
    el.querySelector('[data-f=file]').onchange = async e => { const f = e.target.files[0]; if (f) { t.value = await f.text(); check(); } };
    el.querySelector('[data-a=check]').onclick = check;
    save.onclick = () => {
      if (!pending) return;
      const { data } = pending;
      try {
        // Decided again against what is stored right now (another tab may have imported meanwhile), in one write.
        store.load();
        const keys = new Set(store.data.entries.map(e => e.seriesKey).filter(Boolean));
        const fresh = root.WorkTransfer.missing(store.data.entries, data.entries).filter(e => !e.seriesKey || !keys.has(e.seriesKey));
        const add = fresh.map(e => ({ ...e, id: store.uid(), kind: e.kind ?? 'shift', payKnown: e.payKnown ?? true, bonus: e.bonus ?? 0, seriesKey: e.seriesKey ?? null,
          autoConfirmed: e.autoConfirmed ?? false, createdAt: e.createdAt ?? Date.now() }));
        store.update(d => {
          for (const c of data.categories) if (!d.categories.some(x => x.name === c.name)) d.categories.push(c);
          for (const e of add) if (!d.categories.some(x => x.name === e.category)) d.categories.push({ name: e.category, defaultRate: e.hourlyRate });
          for (const w of data.workers || []) if (!d.workers.some(x => x.name === w.name)) d.workers.push(w);
          d.entries.push(...add);
        });
        const plans = root.WorkTransfer.missingPlans(store.plans.series, store.plans.exceptions, data.series || [], data.exceptions || []);
        if (plans.series.length) store.updatePlans(p => { p.series.push(...plans.series); p.exceptions.push(...plans.exceptions); });
        close(); U().toast(`נוספו ${add.length} משמרות ✓`);
      } catch (e) { sum.textContent = 'השמירה נכשלה. הנתונים הקיימים נשמרו. ' + e.message; }
    };
  });
}

function placesSheet(app) {
  const { store } = app, { esc } = U();
  U().sheet('places-sheet', (el, close) => {
    const draw = () => {
      const def = root.WorkCategories.defaultName(store.data.categories, store.data.webPreferences);
      el.innerHTML = `<div class="title-row"><h2>מקומות עבודה</h2>${U().infoDot('מקומות עבודה', 'נגיעה במקום הופכת אותו לברירת המחדל. "עריכה" לשם, תעריף, מטבע וצבע.')}</div>
        <div class="list">${store.data.categories.map(c => `<div class="row" style="align-items:center"><button class="shift grow" data-def="${esc(c.name)}" aria-pressed="${c.name === def}">${U().avatar(c.name, store, false, 32)}
          <span class="meta"><b class="label">${esc(c.name)}${c.name === def ? ' · ברירת מחדל' : ''}</b><span class="sub">${esc(U().money(c.defaultRate, root.WorkCategories.currency(c.name, store.data.webPreferences)))} לשעה</span></span></button>
          <button class="chip" data-edit="${esc(c.name)}">עריכה</button></div>`).join('')}</div>
        <button class="btn ghost" data-a="add" data-testid="place-add">+ מקום עבודה חדש</button>`;
      el.querySelectorAll('[data-def]').forEach(b => b.onclick = () => { store.update(d => { d.webPreferences = root.WorkCategories.preferences({ ...d.webPreferences, defaultCategory: b.dataset.def }); }); draw(); });
      el.querySelectorAll('[data-edit]').forEach(b => b.onclick = () => placeSheet(app, b.dataset.edit, draw));
      el.querySelector('[data-a=add]').onclick = () => placeSheet(app, null, draw);
    };
    draw();
  });
}
/** Add or edit one workplace. Renaming moves its shifts and plans; stored amounts never change. */
function placeSheet(app, oldName, done) {
  const { store } = app, { esc } = U(), cat = store.data.categories.find(c => c.name === oldName);
  const cur = oldName ? root.WorkCategories.currency(oldName, store.data.webPreferences) : store.data.webPreferences.mainCurrency;
  let color = oldName ? U().PLACES.indexOf(U().placeOf(oldName, store)) : store.data.categories.length % U().PLACES.length;
  U().sheet('place-sheet', (el, close) => {
    el.innerHTML = `<h2>${oldName ? 'עריכת מקום' : 'מקום עבודה חדש'}</h2>
      <label class="field">שם<input class="input" data-f="n" value="${esc(oldName || '')}" data-testid="place-name"></label>
      <div class="row"><label class="field grow">תעריף לשעה<input class="input" inputmode="decimal" data-f="r" value="${cat ? cat.defaultRate : ''}" data-testid="place-rate"></label>
        <div class="field"><span>מטבע</span><div class="seg">${['₪', '$'].map(c => `<button aria-pressed="${cur === c}" data-c="${c}">${c}</button>`).join('')}</div></div></div>
      <span class="caption">צבע</span><div class="chips">${U().PLACES.map((p, i) => `<button class="chip" aria-pressed="${i === color}" aria-label="${p.name}" data-col="${i}" style="width:44px;padding:0;background:${p.from}"></button>`).join('')}</div>
      <p class="caption" data-f="err" role="status"></p>
      <div class="row">${oldName && oldName !== root.WorkCategories.defaultName(store.data.categories, store.data.webPreferences) ? '<button class="btn ghost grow" data-a="del">מחיקה</button>' : ''}<button class="btn primary grow" data-a="ok" data-testid="place-save">שמירה</button></div>`;
    let money = cur;
    el.querySelectorAll('[data-c]').forEach(b => b.onclick = () => { money = b.dataset.c; el.querySelectorAll('[data-c]').forEach(x => x.setAttribute('aria-pressed', String(x === b))); });
    el.querySelectorAll('[data-col]').forEach(b => b.onclick = () => { color = +b.dataset.col; el.querySelectorAll('[data-col]').forEach(x => x.setAttribute('aria-pressed', String(x === b))); });
    el.querySelector('[data-a=ok]').onclick = () => {
      const name = el.querySelector('[data-f=n]').value.trim(), rate = Number((el.querySelector('[data-f=r]').value || '0').trim().replace(',', '.'));
      try {
        const next = root.WorkCategories.rename(store.data, oldName || '', name, rate, money);
        store.update(d => { Object.assign(d, next); });
        if (oldName && oldName !== name) store.updatePlans(p => p.series.forEach(s => { if (s.category === oldName) s.category = name; }));
        if (oldName && oldName !== name) { store.setLocal('color:' + oldName, undefined);
          const t = store.timer(); if (t && t.category === oldName) store.restoreTimer({ ...t, category: name }); }
        store.setLocal('color:' + name, color);
        close(); done?.(name);
      } catch (e) { el.querySelector('[data-f=err]').textContent = e.message; }
    };
    el.querySelector('[data-a=del]')?.addEventListener('click', () => U().confirmSheet(`למחוק את "${oldName}"?`, 'המשמרות שלו עוברות למקום ברירת המחדל. סכומים שמורים לא משתנים.', 'מחיקה', () => {
      try { const next = root.WorkCategories.remove(store.data, oldName); const target = root.WorkCategories.defaultName(next.categories, next.webPreferences);
        store.update(d => { Object.assign(d, next); }); store.updatePlans(p => p.series.forEach(s => { if (s.category === oldName) s.category = target; })); close(); done?.(); }
      catch (e) { el.querySelector('[data-f=err]').textContent = e.message; }
    }));
  });
}

/** "החודש שלך": done shifts of one month, money per currency, the leading place, the change from the month before. */
/** "החודש שלך" lives in web/kis/summary.js; kept here for the existing callers. */
function monthSummary(app, month) { root.KisSummaryUi.monthSummary(app, month); }

/** What arrived (a monthly summary opens again). The switches are in Settings. */
/**
 * The notes to show. Nothing stays forever: a note goes after 14 days; a recap shows only once it opened (and stays
 * while it was not watched). Same rules as the app (PlanJobs.notes).
 */
const NOTE_DAYS = 14;
function notes(store, now = Date.now()) {
  return store.recent().filter(n => n.kind === 'monthly' ? root.KisSummaryUi.isOpen(n.scope || root.KisSummaryUi.scopeOf(n.month)) : now - n.time < NOTE_DAYS * 864e5);
}
/** The bell's dot: anything new since the bell was opened, and a recap until it is actually watched. */
function unread(store) {
  return notes(store).some(n => n.time > (store.local.seenAt || 0) || (n.kind === 'monthly' && n.scope && !n.opened));
}
function notificationsSheet(app) {
  const { store } = app, list = notes(store), { esc } = U();
  store.setLocal('seenAt', Date.now(), true);
  U().sheet('notifications-sheet', (el, close) => {
    el.innerHTML = `<h2>התראות</h2>${list.length ? '' : '<p class="caption" data-testid="notifications-empty">אין התראות</p>'}
      <div class="list">${list.map((n, i) => `<button class="shift" data-i="${i}" data-testid="recent-note-${i}" ${n.month == null && n.kind !== 'ai' ? 'style="cursor:default"' : ''}><span class="meta"><b class="label">${esc(n.title)}${n.kind === 'monthly' && n.scope && !n.opened ? ' <span class="chip" style="min-height:0;padding:2px 8px;font-size:12px">חדש</span>' : ''}</b><span class="sub">${esc(n.text)}</span><span class="caption">${U().shortDate(n.time)}</span></span></button>`).join('')}</div>
      <button class="btn ghost" data-a="settings" data-testid="notifications-settings">הגדרות התראות</button>`;
    el.querySelectorAll('[data-i]').forEach(b => b.onclick = () => { const n = list[+b.dataset.i];
      if (n.kind === 'ai') { close(); root.KisAccount.assistantsSheet({ explain: true }); return; }
      if (n.month == null) return; close();
      if (n.scope) root.KisSummaryUi.story(app, n.scope); else monthSummary(app, n.month); });
    el.querySelector('[data-a=settings]').onclick = () => { close(); app.go('settings'); };
  }, { onClose: () => app.refresh() });
}

/** In-site reminders, due when the site is opened (the Android app sends them as notifications). */
function dueReminders(app) {
  const { store } = app, now = Date.now(), out = [];
  const done = store.data.entries.filter(e => !U().upcoming(e));
  // The recap of a month, once, from its 30th for 4 more days (KisSummary.recapNew; never before the first one).
  const scope = root.KisSummary.recapNew(root.KisPlan.dateOf(now)).month;
  if (store.local.notifyMonthly === true && scope && store.local.lastMonthly !== scope) {
    const rows = done.filter(e => root.KisPlan.dateOf(e.date).startsWith(scope)), month = root.KisSummaryUi.monthOf(scope);
    if (rows.length) out.push({ kind: 'monthly', title: `${U().MONTHS[month % 12]} שלך בכיס מוכן`, text: rows.length === 1 ? 'משמרת אחת · לצפייה בסיכום' : `${rows.length} משמרות · לצפייה בסיכום`, time: now, month, scope });
    store.setLocal('lastMonthly', scope);
  }
  const missing = done.filter(e => !e.payKnown).length;
  if (store.local.notifyRate === true && missing && now - (store.local.lastRate || 0) >= 7 * 864e5) {
    out.push({ kind: 'rate', title: missing === 1 ? 'משמרת אחת מחכה לתעריף' : `${missing} משמרות מחכות לתעריף`, text: 'אפשר להשלים את כולן יחד מההיסטוריה', time: now, month: null });
    store.setLocal('lastRate', now);
  }
  for (const n of out) store.addRecent(n);
  return out;
}

root.KisSettings = { render, monthSummary, placeSheet, placesSheet, backupText, dueReminders, importSheet, notificationsSheet, notes, unread };
})(globalThis);
