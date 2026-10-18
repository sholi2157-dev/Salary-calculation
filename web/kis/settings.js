/* Settings, grouped like the app: work, look, notifications, backup & data, help & updates, profile last. Home's bell lists what arrived. */
(function (root) {
'use strict';
const U = () => root.KisUi;
const group = (title, body, info) => `<div class="title-row" style="margin:22px 0 10px"><h2 class="section-title" style="margin:0;font:22px/28px var(--display)" data-testid="settings-${title}">${title}</h2>${info ? U().infoDot(title, info) : ''}</div><div class="card" style="display:grid;gap:4px;padding:6px 16px">${body}</div>`;
const rowHtml = (title, hint, control, info) => `<div class="row" style="align-items:center;min-height:58px;padding:8px 0"><div class="grow"><div class="title-row"><span style="font-weight:500">${title}</span>${info ? U().infoDot(title, info) : ''}</div>${hint ? `<div class="caption">${hint}</div>` : ''}</div>${control}</div>`;

function render(el, app) {
  const { store } = app, { esc } = U(), prefs = store.data.webPreferences, def = root.WorkCategories.defaultName(store.data.categories, prefs);
  const theme = store.local.theme || 'system';
  el.innerHTML = `<h1 class="page-title">הגדרות</h1>
  ${group('עבודה', `${rowHtml('מקום ברירת מחדל', null, `<button class="chip" data-a="places" data-testid="places-button">${esc(def || 'הוספה')}</button>`, 'נבחר אוטומטית במשמרת חדשה. נגיעה בכפתור פותחת את רשימת המקומות: הוספה, עריכה ובחירת ברירת מחדל.')}
    <div class="divider"></div>${rowHtml('מטבע ברירת מחדל', null, `<div class="seg">${['₪', '$'].map(c => `<button aria-pressed="${prefs.mainCurrency === c}" data-cur="${c}">${c}</button>`).join('')}</div>`, 'למקומות ולמשמרות חדשים. כל משמרת שומרת את המטבע שלה, ושקלים ודולרים לא מתחברים לסכום אחד.')}
    <div class="divider"></div>${rowHtml('משמרות קבועות', store.plans.series.length ? `${store.plans.series.length} פעילות` : null, '<button class="chip" data-a="series" data-testid="series-button">ניהול</button>', 'יוצרים במסך ההוספה: "חוזרת כל שבוע". כשהזמן נגמר המשמרת נרשמת לבד, ותמיד אפשר לבטל.')}`)}
  ${group('מראה ותחושה', `${rowHtml('ערכת צבעים', null, `<div class="seg">${[['system', 'אוטומטי'], ['light', 'בהיר'], ['dark', 'כהה']].map(([v, l]) => `<button aria-pressed="${theme === v}" data-theme="${v}" data-testid="theme-${v}">${l}</button>`).join('')}</div>`)}
    <div class="divider"></div>${rowHtml('רטט במגע', null, `<button class="switch" role="switch" aria-checked="${store.local.haptics !== false}" aria-label="רטט במגע" data-a="haptics"></button>`)}`)}
  ${group('התראות', `${rowHtml('הסיכום החודשי', null, `<button class="switch" role="switch" aria-checked="${store.local.notifyMonthly === true}" aria-label="הסיכום החודשי" data-a="monthly" data-testid="notify-monthly"></button>`, 'בתחילת החודש, כשנכנסים לאתר: כמה עבדת וכמה הרווחת. נגיעה בו פותחת את "החודש שלך". באתר התזכורות מופיעות כשהוא פתוח; באפליקציה לאנדרואיד הן מגיעות גם כשהיא סגורה.')}
    <div class="divider"></div>${rowHtml('תעריף חסר', null, `<button class="switch" role="switch" aria-checked="${store.local.notifyRate === true}" aria-label="תעריף חסר" data-a="rate" data-testid="notify-rate"></button>`, 'לכל היותר פעם בשבוע, ורק כשיש משמרות בלי תעריף.')}`)}
  ${group('גיבוי ונתונים', `<div class="row" style="flex-wrap:wrap;padding:10px 0 6px"><button class="btn small ghost grow" data-a="download" data-testid="backup-download">הורדת גיבוי</button><button class="btn small ghost grow" data-a="copy-backup">העתקה</button><button class="btn small ghost grow" data-a="share-backup">שיתוף</button></div>
    <div class="row" style="padding-bottom:6px"><button class="btn small ghost grow" data-a="csv">אקסל (CSV)</button><button class="btn small primary grow" data-a="import" data-testid="import-button">ייבוא נתונים</button></div>`, 'הנתונים נשמרים בדפדפן הזה בלבד. כדאי לשמור גיבוי גם מחוץ לאתר. גיבוי מהאפליקציה עובר לכאן, וגיבוי מכאן עובר לאפליקציה.')}
  ${group('עזרה ועדכונים', `${rowHtml('הצגת ההדרכה מחדש', null, '<button class="chip" data-a="tour" data-testid="replay-tour">הצגה</button>')}
    <div class="divider"></div>${rowHtml('בדיקת עדכון', null, '<button class="chip" data-a="update">בדיקה</button>', 'האתר מתעדכן לבד ברגע בטוח, בלי לאבד טיוטה או משמרת פעילה.')}`)}
  ${group('פרופיל', `<div style="display:grid;gap:8px;padding:10px 0"><input class="input" data-f="name" value="${esc(store.local.name || '')}" placeholder="שם פרטי" aria-label="שם פרטי" maxlength="40" data-testid="profile-name">
    <input class="input" data-f="last" value="${esc(store.local.lastName || '')}" placeholder="שם משפחה" aria-label="שם משפחה" maxlength="40" data-testid="profile-last-name"></div>`)}
  <div class="row" style="align-items:center;margin-top:24px">${U().logo(28)}<span class="caption">שכר עבודות אישי · 2.0 · ${esc((root.WorkBuild?.commit || 'local').slice(0, 7))}</span></div>`;

  const q = s => el.querySelector(s);
  // Saved as typed, without redrawing the page (no lost taps or focus).
  q('[data-f=name]').addEventListener('input', e => store.setLocal('name', e.target.value.trim().slice(0, 40), true));
  q('[data-f=last]').addEventListener('input', e => store.setLocal('lastName', e.target.value.trim().slice(0, 40), true));
  q('[data-a=monthly]').onclick = e => { const on = store.local.notifyMonthly !== true; store.setLocal('notifyMonthly', on, true); e.currentTarget.setAttribute('aria-checked', on); };
  q('[data-a=rate]').onclick = e => { const on = store.local.notifyRate !== true; store.setLocal('notifyRate', on, true); e.currentTarget.setAttribute('aria-checked', on); };
  q('[data-a=tour]').onclick = () => root.KisLaunch.tour(app);
  q('[data-a=places]').onclick = () => placesSheet(app);
  el.querySelectorAll('[data-cur]').forEach(b => b.onclick = () => store.update(d => { d.webPreferences = root.WorkCategories.preferences({ ...d.webPreferences, mainCurrency: b.dataset.cur }); }));
  q('[data-a=series]').onclick = () => root.KisPlans.seriesSheet(app);
  el.querySelectorAll('[data-theme]').forEach(b => b.onclick = () => { store.setLocal('theme', b.dataset.theme); app.applyTheme(); });
  q('[data-a=haptics]').onclick = () => store.setLocal('haptics', store.local.haptics === false);
  q('[data-a=download]').onclick = () => download('salary-backup.json', backupText(store), 'application/json');
  q('[data-a=copy-backup]').onclick = async () => { if (await U().copy(backupText(store))) U().toast('הגיבוי הועתק ✓'); else U().showText(backupText(store), 'גיבוי'); };
  q('[data-a=share-backup]').onclick = () => U().shareOrCopy(backupText(store), 'גיבוי');
  q('[data-a=csv]').onclick = () => download('salary.csv', root.WorkTransfer.csv(store.data.entries.map(root.WorkTransfer.normalize)), 'text/csv');
  q('[data-a=import]').onclick = () => importSheet(app);
  q('[data-a=update]').onclick = () => { root.WebUpdateManager?.check(true); U().toast('בודקים עדכון…'); };
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
function monthSummary(app, month) {
  const { store } = app, { esc, moneyHtml, upcoming } = U();
  const done = store.data.entries.filter(e => !upcoming(e)), rows = done.filter(e => U().monthKey(e.date) === month), before = done.filter(e => U().monthKey(e.date) === month - 1);
  const totals = root.KisHome.sortedTotals(rows), h = rows.reduce((s, e) => s + e.hours, 0);
  const lead = Object.entries(rows.reduce((m, e) => (m[e.category] = (m[e.category] || 0) + e.hours, m), {})).sort((a, b) => b[1] - a[1])[0];
  const pending = rows.filter(e => !e.payKnown).length;
  U().sheet('month-summary-sheet', (el, close) => {
    const diff = rows.length - before.length, hd = h - before.reduce((s, e) => s + e.hours, 0);
    el.innerHTML = `<h2>החודש שלך · ${esc(U().monthName(month))}</h2>${rows.length ? `
      <div class="card" style="background:var(--bg);display:grid;gap:4px" data-testid="month-summary-money">${totals.length ? totals.map(([c, v]) => `<b class="hero" style="font-size:40px;line-height:44px">${moneyHtml(v, c)}</b>`).join('') : '<b class="amount">עוד חסר תעריף</b>'}
        <span class="label" style="color:var(--muted)">${rows.length} משמרות · ${U().hours(h)} שעות</span>${pending ? `<span class="caption" style="color:var(--pending)">${pending === 1 ? 'משמרת אחת עוד בלי סכום ידוע (לא נספרת)' : pending + ' משמרות עוד בלי סכום ידוע (לא נספרות)'}</span>` : ''}</div>
      ${lead ? `<div class="row" style="align-items:center">${U().avatar(lead[0], store, false, 36)}<div><b class="label">הכי הרבה ב${esc(lead[0])}</b><div class="caption">${U().hours(lead[1])} שעות</div></div></div>` : ''}
      ${before.length ? `<p style="margin:0">לעומת ${esc(U().monthName(month - 1))}: ${diff === 0 ? 'אותו מספר משמרות' : diff > 0 ? diff + ' משמרות יותר' : -diff + ' משמרות פחות'} · ${Math.abs(hd) < .01 ? 'אותן שעות' : (hd > 0 ? '+' : '−') + U().hours(Math.abs(hd)) + ' שעות'}</p>` : ''}`
      : '<p class="caption">לא נרשמו משמרות בחודש הזה.</p>'}
      <div class="row"><button class="btn ghost grow" data-a="h">להיסטוריה</button><button class="btn primary grow" data-a="s" ${rows.length ? '' : 'disabled'}>שיתוף</button></div>`;
    el.querySelector('[data-a=h]').onclick = () => { close(); app.go('history', { planned: false, month }); };
    el.querySelector('[data-a=s]').onclick = () => U().shareOrCopy(root.KisShare.summary(rows), 'הסיכום של ' + U().monthName(month));
  });
}

/** What arrived (a monthly summary opens again). The switches are in Settings. */
function notificationsSheet(app) {
  const { store } = app, list = store.recent(), { esc } = U();
  store.setLocal('seenAt', Date.now(), true);
  U().sheet('notifications-sheet', (el, close) => {
    el.innerHTML = `<h2>התראות</h2>${list.length ? '' : '<p class="caption" data-testid="notifications-empty">אין התראות</p>'}
      <div class="list">${list.map((n, i) => `<button class="shift" data-i="${i}" data-testid="recent-note-${i}" ${n.month == null ? 'style="cursor:default"' : ''}><span class="meta"><b class="label">${esc(n.title)}</b><span class="sub">${esc(n.text)}</span><span class="caption">${U().shortDate(n.time)}</span></span></button>`).join('')}</div>
      <button class="btn ghost" data-a="settings" data-testid="notifications-settings">הגדרות התראות</button>`;
    el.querySelectorAll('[data-i]').forEach(b => b.onclick = () => { const n = list[+b.dataset.i]; if (n.month != null) { close(); monthSummary(app, n.month); } });
    el.querySelector('[data-a=settings]').onclick = () => { close(); app.go('settings'); };
  }, { onClose: () => app.refresh() });
}

/** In-site reminders, due when the site is opened (the Android app sends them as notifications). */
function dueReminders(app) {
  const { store } = app, now = Date.now(), out = [];
  const last = U().monthKey(now) - 1, done = store.data.entries.filter(e => !U().upcoming(e));
  if (store.local.notifyMonthly === true && store.local.lastMonthly !== last) {
    const rows = done.filter(e => U().monthKey(e.date) === last);
    if (rows.length) { const money = root.KisHome.sortedTotals(rows).map(([c, v]) => U().money(v, c)).join(' · ');
      out.push({ kind: 'monthly', title: `הסיכום של ${U().MONTHS[last % 12]} מוכן`, text: `${rows.length} משמרות · ${U().hours(rows.reduce((s, e) => s + e.hours, 0))} שעות${money ? ' · ' + money : ''}`, time: now, month: last }); }
    store.setLocal('lastMonthly', last);
  }
  const missing = done.filter(e => !e.payKnown).length;
  if (store.local.notifyRate === true && missing && now - (store.local.lastRate || 0) >= 7 * 864e5) {
    out.push({ kind: 'rate', title: missing === 1 ? 'משמרת אחת מחכה לתעריף' : `${missing} משמרות מחכות לתעריף`, text: 'אפשר להשלים את כולן יחד מההיסטוריה', time: now, month: null });
    store.setLocal('lastRate', now);
  }
  for (const n of out) store.addRecent(n);
  return out;
}

root.KisSettings = { render, monthSummary, placeSheet, placesSheet, backupText, dueReminders, importSheet, notificationsSheet };
})(globalThis);
