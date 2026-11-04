/* Settings, grouped like the app: feedback first, then work, look, notifications, backup & data, help, profile last. Home's bell lists what arrived. */
(function (root) {
'use strict';
const U = () => root.KisUi;
/** The open category on a phone (M-31, as in Android 2.2.3): null shows the list of categories. */
let section = null;
const group = (title, body, info) => `<section class="set-group${section === title ? ' on' : ''}" data-group="${title}"><div class="title-row" style="margin:22px 0 10px"><h2 class="section-title" style="margin:0;font:22px/28px var(--display)" data-testid="settings-${title}">${title}</h2>${info ? U().infoDot(title, info) : ''}</div><div class="card" style="display:grid;gap:4px;padding:6px 16px">${body}</div></section>`;
/** The kinds of in-site reminder (M-31), titles and explanations as in the Android app: [stored key, title, info, testid]. */
const NOTIFY_KINDS = [
  ['notifyMonthly', 'הסיכום החודשי', 'מה-30 לכל חודש (בפברואר מהיום האחרון), כשנכנסים לאתר: כמה עבדת וכמה הרווחת החודש. רק כשיש משמרות בחודש.', 'notify-monthly'],
  ['notifyRate', 'תעריף חסר', 'לכל היותר פעם בשבוע, ורק כשיש משמרות בלי תעריף.', 'notify-rate'],
];
const rowHtml = (title, hint, control, info) => `<div class="row" style="align-items:center;min-height:58px;padding:8px 0"><div class="grow"><div class="title-row"><span style="font-weight:500">${title}</span>${info ? U().infoDot(title, info) : ''}</div>${hint ? `<div class="caption">${hint}</div>` : ''}</div>${control}</div>`;

function render(el, app) {
  const { store } = app, { esc } = U(), prefs = store.data.webPreferences, def = root.WorkCategories.defaultName(store.data.categories, prefs);
  const theme = store.local.theme || 'system';
  const acct = root.KisAccount.state.user;
  // A phone shows the categories as a list; a tap opens one category with a way back. A computer shows them all.
  const cats = [['משוב ותקלות', 'דיווח על תקלה או הצעה'], ['עבודה', def ? `מקום ברירת מחדל: ${def}` : 'מקומות, מטבע ומשמרות קבועות'],
    ['מראה ותחושה', ({ system: 'אוטומטי', light: 'בהיר', dark: 'כהה' })[theme] || 'אוטומטי'], ['הסיכומים שלי', 'כל הסיכומים החודשיים והשנתיים'],
    ['התראות', NOTIFY_KINDS.some(([k]) => store.local[k] === true) ? 'פועלות' : 'כבויות'], ['גיבוי ונתונים', 'גיבוי, ייבוא ואקסל'],
    ['עזרה', 'האפליקציה לאנדרואיד וההדרכה'], ['חשבון', acct ? (acct.email || 'מחובר') : 'השם שלך · לא מחובר']];
  el.innerHTML = `<h1 class="page-title">הגדרות</h1>
  ${section ? '' : `<nav class="set-list" aria-label="קטגוריות ההגדרות">${cats.map(([t, sub]) => `<button class="set-row" data-section="${t}" data-testid="settings-open-${t}"><span class="grow"><b>${t}</b><small>${esc(sub)}</small></span>${U().ICON.next}</button>`).join('')}</nav>`}
  <div class="set-groups" data-open="${section ? esc(section) : ''}">
  ${section ? `<button class="set-back" data-a="settings-back" data-testid="settings-back">${U().ICON.prev}<span>הגדרות</span></button>` : ''}
  ${group('משוב ותקלות', rowHtml('דיווח על תקלה או הצעה', null, '<button class="chip" data-a="feedback" data-testid="feedback-button">כתיבה</button>'))}
  ${group('עבודה', `${rowHtml('מקום ברירת מחדל', null, `<button class="chip" data-a="places" data-testid="places-button">${esc(def || 'הוספה')}</button>`, 'נבחר אוטומטית במשמרת חדשה.')}
    <div class="divider"></div>${rowHtml('מטבע ברירת מחדל', null, `<div class="seg">${['₪', '$'].map(c => `<button aria-pressed="${prefs.mainCurrency === c}" data-cur="${c}">${c}</button>`).join('')}</div>`, 'למקומות ולמשמרות חדשים. כל משמרת שומרת את המטבע שלה, ושקלים ודולרים לא מתחברים לסכום אחד.')}
    <div class="divider"></div>${rowHtml('משמרות קבועות', store.plans.series.length ? `${store.plans.series.length} פעילות` : null, '<button class="chip" data-a="series" data-testid="series-button">ניהול</button>', 'יוצרים במסך ההוספה: "חוזרת כל שבוע". כשהזמן נגמר המשמרת נרשמת לבד, ותמיד אפשר לבטל.')}`)}
  ${group('מראה ותחושה', `${rowHtml('ערכת צבעים', null, `<div class="seg">${[['system', 'אוטומטי'], ['light', 'בהיר'], ['dark', 'כהה']].map(([v, l]) => `<button aria-pressed="${theme === v}" data-theme="${v}" data-testid="theme-${v}">${l}</button>`).join('')}</div>`)}
    <div class="divider"></div>${rowHtml('רטט במגע', null, `<button class="switch" role="switch" aria-checked="${store.local.haptics !== false}" aria-label="רטט במגע" data-a="haptics"></button>`)}`)}
  ${group('הסיכומים שלי', rowHtml('כל הסיכומים', null, '<button class="chip" data-a="recaps" data-testid="recaps-button">פתיחה</button>', 'כל סיכום חודשי ושנתי שכבר נפתח נשמר כאן.'))}
  ${group('התראות', rowHtml('כל ההתראות', null, `<button class="switch" role="switch" aria-checked="${NOTIFY_KINDS.some(([k]) => store.local[k] === true)}" aria-label="כל ההתראות" data-a="notify" data-testid="notify-all"></button>`, 'מפעיל או מכבה את כולן יחד. אפשר לבחור כל סוג בנפרד כאן למטה.')
    + NOTIFY_KINDS.map(([k, title, info, testid]) => `<div class="divider"></div>${rowHtml(title, null, `<button class="switch" role="switch" aria-checked="${store.local[k] === true}" aria-label="${title}" data-notify="${k}" data-testid="${testid}"></button>`, info)}`).join(''),
    'הסיכום החודשי ותעריף חסר, כשנכנסים לאתר. באפליקציה לאנדרואיד הן מגיעות גם כשהיא סגורה, וגם משמרת פעילה ומשמרת קבועה שהסתיימה.')}
  ${group('גיבוי ונתונים', `<div class="row" style="flex-wrap:wrap;padding:10px 0 6px"><button class="btn small ghost grow" data-a="download" data-testid="backup-download">הורדת גיבוי</button><button class="btn small ghost grow" data-a="copy-backup">העתקה</button><button class="btn small ghost grow" data-a="share-backup">שיתוף</button></div>
    <div class="row" style="padding-bottom:6px"><button class="btn small ghost grow" data-a="csv" data-testid="csv-export">טבלה לאקסל (CSV)</button><button class="btn small primary grow" data-a="import" data-testid="import-button">ייבוא נתונים</button></div>`, 'כשמחוברים לחשבון, הנתונים נשמרים בו ובדפדפן הזה. גיבוי לקובץ הוא העתק נוסף בשבילך, וגיבוי מהאפליקציה עובר לכאן ולהפך. טבלה לאקסל היא לקריאה ולחישובים, לא גיבוי: היא לא שומרת הכול (למשל עובדי קבוצה).')}
  ${group('עזרה', `${rowHtml('האפליקציה לאנדרואיד', null, '<button class="chip" data-a="android-download">הורדה והסבר</button>')}<div class="divider"></div>${rowHtml('הצגת ההדרכה מחדש', null, '<button class="chip" data-a="tour" data-testid="replay-tour">הצגה</button>')}`)}
  ${group('חשבון', `<div class="row" style="padding:10px 0 4px"><input class="input grow" style="min-width:0" data-f="name" value="${esc(store.local.name || '')}" placeholder="שם פרטי" aria-label="שם פרטי" maxlength="40" data-testid="profile-name">
    <input class="input grow" style="min-width:0" data-f="last" value="${esc(store.local.lastName || '')}" placeholder="שם משפחה" aria-label="שם משפחה" maxlength="40" data-testid="profile-last-name"></div>
    <div data-testid="account-card">${root.KisAccount.settingsHtml()}</div>
    <div style="display:grid;justify-items:start;padding:4px 0"><button class="link" style="color:var(--danger,#c2362b);padding-inline:0;min-height:44px" data-a="erase" data-testid="account-erase">מחיקה מהמכשיר הזה</button>
    ${root.KisAccount.state.user ? '<button class="link" style="color:var(--danger,#c2362b);padding-inline:0;min-height:44px" data-a="erase-account" data-testid="account-erase-all">מחיקה מהחשבון ומכל המכשירים</button>' : ''}</div>`, 'המשמרות, המקומות, המשמרות הקבועות וההגדרות נשמרים בחשבון Google שלך ומסתנכרנים עם האפליקציה לאנדרואיד ועם כל דפדפן שמחובר אליו.')}
  </div><div class="row" style="align-items:center;margin-top:24px">${U().logo(28)}<span class="caption">שכר עבודות אישי</span></div>`;

  const q = s => el.querySelector(s);
  el.querySelectorAll('[data-section]').forEach(b => b.onclick = () => { section = b.dataset.section; render(el, app); window.scrollTo({ top: 0 });
    el.querySelector('.set-group.on h2')?.setAttribute('tabindex', '-1'); el.querySelector('.set-group.on h2')?.focus({ preventScroll: true }); });
  q('[data-a=settings-back]')?.addEventListener('click', () => { const was = section; section = null; render(el, app); el.querySelector(`[data-section="${was}"]`)?.focus(); });
  root.KisAccount.bindSettings(el);
  // Saved as typed, without redrawing the page (no lost taps or focus).
  q('[data-f=name]').addEventListener('input', e => store.setLocal('name', e.target.value.trim().slice(0, 40), true));
  q('[data-f=last]').addEventListener('input', e => store.setLocal('lastName', e.target.value.trim().slice(0, 40), true));
  // One switch for every reminder.
  // As Android 2.2.3 (M-31): the main switch turns every kind on or off together; each kind keeps its own switch, and the
  // main one shows on while any kind is on. The same stored keys as before (notifyMonthly, notifyRate).
  const notifySync = () => {
    q('[data-a=notify]').setAttribute('aria-checked', NOTIFY_KINDS.some(([k]) => store.local[k] === true));
    el.querySelectorAll('[data-notify]').forEach(b => b.setAttribute('aria-checked', store.local[b.dataset.notify] === true));
  };
  q('[data-a=notify]').onclick = e => { const on = e.currentTarget.getAttribute('aria-checked') !== 'true';
    for (const [k] of NOTIFY_KINDS) store.setLocal(k, on, true); notifySync(); };
  el.querySelectorAll('[data-notify]').forEach(b => b.onclick = () => { store.setLocal(b.dataset.notify, store.local[b.dataset.notify] !== true, true); notifySync(); });
  q('[data-a=erase]').onclick = () => eraseSheet(app);
  q('[data-a=erase-account]')?.addEventListener('click', () => eraseAccountSheet(app));
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
  q('[data-a=feedback]').onclick = () => feedbackSheet(app);
}

function download(name, text, type) { const url = URL.createObjectURL(new Blob([text], { type })); const a = document.createElement('a'); a.href = url; a.download = name; a.click(); setTimeout(() => URL.revokeObjectURL(url), 1000); }
/** The same backup file as the Android app: version 2 when nothing 2.0 is used, version 3 otherwise. */
function backupText(store) {
  const d = store.data;
  return root.WorkTransfer.encode({ entries: d.entries, categories: d.categories, workers: d.workers, webPreferences: d.webPreferences,
    androidLocalPreferences: root.WorkRuntime.androidPreferences(d.webPreferences), series: store.plans.series, exceptions: store.plans.exceptions });
}

/**
 * Recovery when stored data cannot be read (audit CW-15, D04). Reachable from the screen that reports the problem, not
 * only from Settings. The unreadable text can be downloaded as it is; a checked backup replaces it only after
 * confirmation, and the unreadable text is kept on the device under a key of its own first.
 */
function recoveryPanel(app) {
  const { store } = app, which = store.broken ? 'הנתונים השמורים' : 'המשמרות הקבועות';
  return `<div class="card" role="alert" data-testid="recovery-panel" style="display:grid;gap:10px">
    <h2 style="margin:0">אי אפשר לקרוא את ${which}</h2>
    <p class="caption" style="margin:0">${U().esc(store.broken || store.plansBroken)}. שום דבר לא נמחק ולא שונה.${store.broken ? '' : ' המשמרות עצמן לא נפגעו.'}</p>
    <button class="btn ghost small" data-recover="download" data-testid="recovery-download">הורדת הנתונים כמו שהם</button>
    <button class="btn primary small" data-recover="restore" data-testid="recovery-restore">שחזור מקובץ גיבוי</button></div>`;
}
function bindRecovery(el, app) {
  const { store } = app;
  el.querySelector('[data-recover=download]')?.addEventListener('click', () => {
    const raw = store.broken ? app.storage.getItem('work_complete_backup') : store.rawPlans();
    download(store.broken ? 'salary-data-as-stored.txt' : 'salary-plans-as-stored.txt', raw || '', 'text/plain');
  });
  el.querySelector('[data-recover=restore]')?.addEventListener('click', () => recoverySheet(app));
}
function recoverySheet(app) {
  const { store } = app;
  U().sheet('recovery-sheet', (el, close) => {
    let decoded = null;
    el.innerHTML = `<h2>שחזור מקובץ גיבוי</h2>
      <p class="caption" style="margin:0">הנתונים שאי אפשר לקרוא יישמרו בצד במכשיר הזה לפני השחזור.</p>
      <label class="btn ghost small" style="position:relative">בחירת קובץ<input type="file" accept=".json,.txt" data-f="file" style="position:absolute;inset:0;opacity:0"></label>
      <textarea class="input" rows="4" data-f="t" placeholder="או הדבקת תוכן הגיבוי" data-testid="recovery-text"></textarea>
      <p class="caption" data-f="sum" data-testid="recovery-summary" aria-live="polite"></p>
      <button class="btn primary" data-a="ok" disabled data-testid="recovery-confirm">שחזור</button>`;
    const t = el.querySelector('[data-f=t]'), sum = el.querySelector('[data-f=sum]'), ok = el.querySelector('[data-a=ok]');
    const check = () => {
      try { decoded = root.WorkTransfer.decode(t.value);
        sum.textContent = `בגיבוי ${decoded.entries.length} משמרות, ${decoded.categories.length} מקומות` + ((decoded.series || []).length ? ` ו־${decoded.series.length} משמרות קבועות` : '') + '.';
        ok.disabled = false; }
      catch (e) { decoded = null; ok.disabled = true; sum.textContent = t.value.trim() ? 'הקובץ לא נקרא: ' + e.message : ''; }
    };
    t.oninput = check;
    el.querySelector('[data-f=file]').onchange = async e => { const f = e.target.files[0]; if (f) { t.value = await f.text(); check(); } };
    ok.onclick = () => {
      if (!decoded) return;
      try { store.restoreDamaged(decoded); close(); app.refresh(); U().toast('השחזור הושלם'); }
      catch (e) { sum.textContent = e.message; }
    };
  });
}

function importSheet(app) {
  const { store } = app;
  U().sheet('import-sheet', (el, close) => {
    let pending = null;
    el.innerHTML = `<div class="title-row"><h2>ייבוא נתונים</h2>${U().infoDot('ייבוא נתונים', 'גיבוי מהאפליקציה או מהאתר, או שורות מאקסל עם כותרות. לפני השמירה תוצג בדיקה; משמרות שכבר קיימות לא יתווספו שוב. לשחזור מלא משתמשים בגיבוי, לא בטבלה לאקסל.')}</div>
      <textarea class="input" rows="6" data-f="t" placeholder="הדבקת נתונים" data-testid="import-text"></textarea>
      <label class="btn ghost small" style="position:relative">בחירת קובץ<input type="file" accept=".json,.csv,.tsv,.txt" data-f="file" style="position:absolute;inset:0;opacity:0"></label>
      <button class="btn ghost" data-a="check" data-testid="import-check">בדיקה</button><p class="caption" data-f="sum" data-testid="import-summary" style="white-space:pre-line"></p>
      <label class="row" data-f="prefsRow" hidden style="align-items:center;gap:10px;min-height:44px"><input type="checkbox" data-f="prefs" data-testid="import-prefs"><span data-f="prefsText"></span></label>
      <button class="btn primary" data-a="save" disabled data-testid="import-save">שמירת הנתונים</button>`;
    const t = el.querySelector('[data-f=t]'), sum = el.querySelector('[data-f=sum]'), save = el.querySelector('[data-a=save]');
    const prefsRow = el.querySelector('[data-f=prefsRow]'), prefsText = el.querySelector('[data-f=prefsText]'), prefsBox = el.querySelector('[data-f=prefs]');
    const check = () => {
      try {
        const data = root.WorkTransfer.decode(t.value);
        const keys = new Set(store.data.entries.map(e => e.seriesKey).filter(Boolean));
        const fresh = root.WorkTransfer.missing(store.data.entries, data.entries, { table: data.fromTable === true }).filter(e => !e.seriesKey || !keys.has(e.seriesKey));
        const series = (data.series || []).filter(s => !store.plans.series.some(x => x.id === s.id));
        // Places, workers and settings count too (audit D05): a backup of only those can be imported.
        const cats = data.categories.filter(c => !store.data.categories.some(x => x.name === c.name));
        const workers = (data.workers || []).filter(w => !store.data.workers.some(x => x.name === w.name));
        const p = data.webPreferences ? root.WorkCategories.preferences(data.webPreferences) : null, here = store.data.webPreferences;
        // A place that already exists here keeps its currency (explicit or the default); only new places take theirs.
        const currencies = p ? Object.entries(p.categoryCurrencies).filter(([n]) => cats.some(c => c.name === n)) : [];
        const main = p && (p.mainCurrency !== here.mainCurrency || p.defaultCategory !== here.defaultCategory) ? p : null;
        pending = { data, fresh, series, cats, workers, currencies, main };
        const tt = root.KisHome.sortedTotals(fresh);
        sum.textContent = `נקראו ${data.entries.length} משמרות. חדשות: ${fresh.length}. קיימות: ${data.entries.length - fresh.length}.` +
          (data.fromTable ? '\nזו טבלה מאקסל, לא גיבוי: משמרת שכבר קיימת (אותו מקום, יום, שעות וסכום) לא תתווסף שוב, וטבלה לא משנה משמרות קיימות.' : '') +
          (tt.length ? `\nיתווסף: ${tt.map(([c, v]) => U().money(v, c)).join(' · ')}` : '') + (series.length ? `\nמשמרות קבועות חדשות: ${series.length}` : '') +
          (cats.length ? `\nמקומות חדשים: ${cats.length}` : '') + (workers.length ? `\nעובדים חדשים: ${workers.length}` : '') + (currencies.length ? `\nמטבע למקומות החדשים: ${currencies.length}` : '');
        prefsRow.hidden = !main;
        if (main) { prefsText.textContent = `גם הגדרות ברירת המחדל מהגיבוי (מטבע ${main.mainCurrency}, מקום: ${main.defaultCategory})`; prefsBox.checked = false; }
        save.disabled = !(fresh.length || series.length || cats.length || workers.length || currencies.length || main);
      } catch (e) { pending = null; sum.textContent = e.message; save.disabled = true; }
    };
    t.oninput = () => { pending = null; save.disabled = true; sum.textContent = ''; };
    el.querySelector('[data-f=file]').onchange = async e => { const f = e.target.files[0]; if (f) { t.value = await f.text(); check(); } };
    el.querySelector('[data-a=check]').onclick = check;
    save.onclick = () => {
      if (!pending) return;
      const { data } = pending;
      try {
        // Decided again against what is stored right now (another tab may have imported meanwhile): the shifts and the
        // plans are written as one commit, so a failure leaves nothing half imported (audit D03).
        const add = store.transact(n => {
          const d = n.data, keys = new Set(d.entries.map(e => e.seriesKey).filter(Boolean)), existing = new Set(d.categories.map(c => c.name));
          const rows = root.WorkTransfer.missing(d.entries, data.entries, { table: data.fromTable === true }).filter(e => !e.seriesKey || !keys.has(e.seriesKey))
            .map(e => ({ ...e, id: store.uid(), kind: e.kind ?? 'shift', payKnown: e.payKnown ?? true, bonus: e.bonus ?? 0, seriesKey: e.seriesKey ?? null,
              autoConfirmed: e.autoConfirmed ?? false, createdAt: e.createdAt ?? Date.now() }));
          for (const c of data.categories) if (!d.categories.some(x => x.name === c.name)) d.categories.push(c);
          for (const e of rows) if (!d.categories.some(x => x.name === e.category)) d.categories.push({ name: e.category, defaultRate: e.hourlyRate });
          for (const w of data.workers || []) if (!d.workers.some(x => x.name === w.name)) d.workers.push(w);
          d.entries.push(...rows);
          const plans = root.WorkTransfer.missingPlans(n.plans.series, n.plans.exceptions, data.series || [], data.exceptions || []);
          n.plans.series.push(...plans.series); n.plans.exceptions.push(...plans.exceptions);
          // Settings never overwrite what is set here: a currency only for a place without one; the defaults only when asked.
          if (data.webPreferences) {
            const p = root.WorkCategories.preferences(data.webPreferences), cur = d.webPreferences;
            const merged = { ...cur, categoryCurrencies: { ...cur.categoryCurrencies } };
            for (const [name, c] of Object.entries(p.categoryCurrencies)) if (!existing.has(name) && !Object.hasOwn(cur.categoryCurrencies, name)) Object.defineProperty(merged.categoryCurrencies, name, { value: c, enumerable: true, writable: true, configurable: true });
            if (prefsBox.checked) { merged.mainCurrency = p.mainCurrency; if (d.categories.some(c => c.name === p.defaultCategory)) merged.defaultCategory = p.defaultCategory; }
            d.webPreferences = root.WorkCategories.preferences(merged);
          }
          return rows;
        });
        close(); U().toast(`נוספו ${add.length} משמרות ✓`);
      } catch (e) { sum.textContent = 'השמירה נכשלה. הנתונים הקיימים נשמרו. ' + e.message; }
    };
  });
}

/**
 * One panel for the places: a tap makes one the default, "עריכה" opens its name, rate and colour under it, and a long
 * press on the two lines at its side moves it in the list (the add page follows the same order).
 */
function placesSheet(app) {
  const { store } = app, { esc } = U();
  let open = null;
  U().sheet('places-sheet', (el, close) => {
    const draw = () => {
      const def = root.WorkCategories.defaultName(store.data.categories, store.data.webPreferences), places = U().orderedPlaces(store);
      el.innerHTML = `<h2>מקומות עבודה</h2>
        <div class="list" data-testid="places-panel" style="display:grid;gap:4px">${places.map(c => `<div class="place-drag" data-drag="${esc(c.name)}" data-testid="place-row-${esc(c.name)}">
          <button class="shift grow" style="background:none;border:0;padding:0" data-def="${esc(c.name)}" aria-pressed="${c.name === def}">${U().avatar(c.name, store, false, 32)}
          <span class="meta"><b class="label">${esc(c.name)}${c.name === def ? ' · ברירת מחדל' : ''}</b><span class="sub">${esc(U().money(c.defaultRate, root.WorkCategories.currency(c.name, store.data.webPreferences)))} לשעה</span></span></button>
          <button class="icon-btn place-edit" data-edit="${esc(c.name)}" aria-expanded="${open === c.name}" aria-label="עריכת ${esc(c.name)}" data-testid="place-edit-${esc(c.name)}">${U().ICON.edit}</button>
          <span class="grip" data-grip aria-label="הזזת ${esc(c.name)}" data-testid="place-drag-${esc(c.name)}">${U().ICON.grip}</span></div>
          ${open === c.name ? '<div class="card" data-inline-edit style="display:grid;gap:10px"></div>' : ''}`).join('')}</div>
        <button class="btn ghost" data-a="add" data-testid="place-add">+ מקום עבודה חדש</button>`;
      el.querySelectorAll('[data-def]').forEach(b => b.onclick = () => { store.update(d => { d.webPreferences = root.WorkCategories.preferences({ ...d.webPreferences, defaultCategory: b.dataset.def }); }); draw(); });
      el.querySelectorAll('[data-edit]').forEach(b => b.onclick = () => { open = open === b.dataset.edit ? null : b.dataset.edit; draw(); });
      const inline = el.querySelector('[data-inline-edit]');
      if (inline) placeForm(app, open, inline, () => { open = null; }, () => { open = null; draw(); });
      el.querySelector('[data-a=add]').onclick = () => placeSheet(app, null, draw);
      dragToReorder(el.querySelector('[data-testid=places-panel]'), names => store.setLocal('placeOrder', names, true), () => { if (open) { open = null; el.querySelector('[data-inline-edit]')?.remove(); } });
    };
    draw();
  });
}
/**
 * A long press on a row's two lines (a short vibration) picks it up; moving the finger moves it among the others; letting go keeps
 * the order. Like a music queue: no buttons, no explanation.
 */
function dragToReorder(list, onOrder, onLift = () => {}) {
  let row = null, timer = null, startY = 0, lastY = 0;
  const rows = () => [...list.querySelectorAll('[data-drag]')];
  const drop = () => { clearTimeout(timer); timer = null; if (!row) return; row.classList.remove('lifted'); row = null; onOrder(rows().map(r => r.dataset.drag)); };
  list.addEventListener('contextmenu', e => e.preventDefault());
  list.addEventListener('pointerdown', e => {
    if (!e.target.closest('[data-grip]')) return;
    const r = e.target.closest('[data-drag]'); if (!r) return;
    startY = lastY = e.clientY;
    timer = setTimeout(() => { onLift(); row = r; row.classList.add('lifted'); try { r.setPointerCapture(e.pointerId); } catch {} navigator.vibrate?.(12); }, 350);
  });
  list.addEventListener('pointermove', e => {
    if (!row) { if (timer && Math.abs(e.clientY - startY) > 8) { clearTimeout(timer); timer = null; } return; }
    e.preventDefault(); lastY = e.clientY;
    const all = rows(), i = all.indexOf(row);
    const next = all[i + 1], prev = all[i - 1];
    if (next && lastY > next.getBoundingClientRect().top + next.offsetHeight / 2) { next.after(row); navigator.vibrate?.(6); }
    else if (prev && lastY < prev.getBoundingClientRect().top + prev.offsetHeight / 2) { prev.before(row); navigator.vibrate?.(6); }
  });
  // On the whole page: a row moved among the others can lose the pointer, and letting go anywhere ends the drag.
  addEventListener('pointerup', drop); addEventListener('pointercancel', drop);
}

/** Shared by both delete sheets: a backup first, then the word "מחיקה" typed. */
function eraseForm(el, store, { title, body, okLabel, okTestid }, onOk, close) {
  const { esc } = U();
  el.innerHTML = `<h2>${esc(title)}</h2>${body.map(p => `<p style="margin:0">${p}</p>`).join('')}
    <button class="btn ghost" data-a="backup" data-testid="erase-backup">הורדת גיבוי לפני המחיקה</button>
    <label class="field">כדי למחוק, יש להקליד: מחיקה<input class="input" data-testid="erase-word" autocomplete="off"></label>
    <div class="row"><button class="btn ghost grow" data-a="cancel">ביטול</button><button class="btn danger grow" data-a="ok" data-testid="${okTestid}" disabled>${esc(okLabel)}</button></div>`;
  const ok = el.querySelector('[data-a=ok]');
  el.querySelector('[data-testid=erase-word]').addEventListener('input', e => { ok.disabled = e.target.value.trim() !== 'מחיקה'; });
  el.querySelector('[data-a=backup]').onclick = () => download('salary-backup.json', backupText(store), 'application/json');
  el.querySelector('[data-a=cancel]').onclick = () => close();
  ok.onclick = () => { ok.disabled = true; onOk(); };
}
const RUNNING = 'יש משמרת שרצה עכשיו. צריך לסיים אותה לפני המחיקה';

/** "מחיקה מהמכשיר הזה": the account copy stays; when signed in, the app signs out so sync does not bring it straight back. */
function eraseSheet(app) {
  const { store } = app, signedIn = !!root.KisAccount.state.user;
  if (store.timer()) { U().toast(RUNNING); return; }
  U().sheet('erase-sheet', (el, close) => eraseForm(el, store, {
    title: 'למחוק את הנתונים מהמכשיר הזה?',
    body: ['יימחקו מהמכשיר הזה: המשמרות, מקומות העבודה, המשמרות הקבועות, העובדים, הגדרות העבודה, טיוטה שלא נשמרה והשם שלך.',
      signedIn ? 'הנתונים בחשבון לא יימחקו. כדי שהסנכרון לא יחזיר אותם מיד, האפליקציה תתנתק מהחשבון במכשיר הזה. כשתתחבר שוב, הם יחזרו מהחשבון. חשבון Google עצמו לא נמחק ולא מתנתק מהמכשיר.'
        : 'אין עותק בחשבון. אחרי המחיקה אפשר לשחזר רק מקובץ גיבוי.'],
    okLabel: 'מחיקה מהמכשיר', okTestid: 'erase-ok' }, async () => {
    const r = await root.KisAccount.eraseDevice(store).catch(() => ({ ok: false, failed: true }));
    if (r.ok) return; // the page starts again
    close();
    U().toast(r.failed ? 'המחיקה לא בוצעה. שום דבר לא נמחק' : r.conflicts ? 'יש שינויים שממתינים לבחירה. אחרי הבחירה אפשר למחוק'
      : `יש ${r.unsent === 1 ? 'שינוי אחד' : r.unsent + ' שינויים'} שעוד לא הגיע לחשבון. כדי לא לאבד אותו, צריך חיבור לאינטרנט. שום דבר לא נמחק`);
  }, close));
}

/** "מחיקה מהחשבון ומכל המכשירים": only this account's Salary Calculation data; the Google account itself is not touched. */
function eraseAccountSheet(app) {
  const { store } = app, email = root.KisAccount.state.user?.email || '';
  if (store.timer()) { U().toast(RUNNING); return; }
  U().sheet('erase-account-sheet', (el, close) => eraseForm(el, store, {
    title: 'למחוק את נתוני חישוב שכר מהחשבון?',
    body: [`יימחקו כל נתוני חישוב שכר שנשמרו בחשבון <b dir="ltr">${U().esc(email)}</b>: המשמרות, מקומות העבודה, המשמרות הקבועות, העובדים והגדרות העבודה. הם יימחקו גם מהמכשיר הזה ומכל מכשיר ודפדפן שמחוברים לחשבון.`,
      'חשבון Google עצמו לא נמחק, ושום דבר אחר בו לא מושפע. חשבונות אחרים לא מושפעים.',
      'מכשיר שלא מחובר עכשיו לאינטרנט ימחק את הנתונים כשיתחבר. אם שינו בו משמרת בלי חיבור, הוא ישאל מה לעשות בה. משמרת שנוספה בו בלי חיבור תגיע לחשבון כשיתחבר, כי לא הייתה בו בזמן המחיקה.',
      '<b>אי אפשר לבטל.</b> כדי לשחזר בעתיד צריך קובץ גיבוי.'],
    okLabel: 'מחיקה מהחשבון', okTestid: 'erase-account-ok' }, async () => {
    el.querySelector('[data-a=ok]').textContent = 'מוחק מהחשבון…';
    const r = await root.KisAccount.eraseAccount(store).catch(() => null);
    close(); app.refresh?.();
    U().toast(r?.ok ? 'נתוני חישוב שכר נמחקו מהחשבון ומהמכשיר הזה'
      : r ? `המחיקה מהחשבון לא הושלמה: ${r.left === 1 ? 'פריט אחד נשאר' : r.left + ' פריטים נשארו'}. מה שנמחק לא שוחזר. אפשר לנסות שוב`
      : 'אין חיבור לחשבון. שום דבר לא נמחק. אפשר לנסות שוב כשיהיה חיבור', r?.ok ? undefined : 'ניסיון נוסף', r?.ok ? undefined : () => eraseAccountSheet(app));
  }, close));
}

/** Add or edit one workplace. Renaming moves its shifts and plans; stored amounts never change. */
function placeSheet(app, oldName, done) {
  const { store } = app, { esc } = U(), cat = store.data.categories.find(c => c.name === oldName);
  const cur = oldName ? root.WorkCategories.currency(oldName, store.data.webPreferences) : store.data.webPreferences.mainCurrency;
  let color = oldName ? U().PLACES.indexOf(U().placeOf(oldName, store)) : store.data.categories.length % U().PLACES.length;
  U().sheet('place-sheet', (el, close) => placeForm(app, oldName, el, close, done, `<h2>${oldName ? 'עריכת מקום' : 'מקום עבודה חדש'}</h2>`));
}
/** The place's name, rate, currency and colour, in [el]: a sheet of its own, or opened under its row in the places panel. */
function placeForm(app, oldName, el, close, done, title = '') {
  const { store } = app, { esc } = U(), cat = store.data.categories.find(c => c.name === oldName);
  const cur = oldName ? root.WorkCategories.currency(oldName, store.data.webPreferences) : store.data.webPreferences.mainCurrency;
  let color = oldName ? U().PLACES.indexOf(U().placeOf(oldName, store)) : store.data.categories.length % U().PLACES.length;
  {
    el.innerHTML = `${title}
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
        // Computed on what is stored now and written as one commit with the plans and a running timer (audit D02, D03).
        store.transact(n => {
          Object.assign(n.data, root.WorkCategories.rename(n.data, oldName || '', name, rate, money));
          if (oldName && oldName !== name) {
            n.plans.series.forEach(s => { if (s.category === oldName) s.category = name; });
            if (n.timer && n.timer.category === oldName) n.timer = { ...n.timer, category: name };
          }
        });
        if (oldName && oldName !== name && Array.isArray(store.local.placeOrder)) store.setLocal('placeOrder', store.local.placeOrder.map(n => n === oldName ? name : n), true);
        if (oldName && oldName !== name) store.setLocal('color:' + oldName, undefined);
        store.setLocal('color:' + name, color);
        close(); done?.(name);
      } catch (e) { el.querySelector('[data-f=err]').textContent = e.message; }
    };
    el.querySelector('[data-a=del]')?.addEventListener('click', () => U().confirmSheet(`למחוק את "${oldName}"?`, 'המשמרות שלו עוברות למקום ברירת המחדל. סכומים שמורים לא משתנים.', 'מחיקה', () => {
      // The shifts, the plans and a running timer move to the default place together (audit D02, D03, CW-09); the timer
      // keeps its own rate and currency, and no stored amount changes.
      try { store.transact(n => { Object.assign(n.data, root.WorkCategories.remove(n.data, oldName));
          const target = root.WorkCategories.defaultName(n.data.categories, n.data.webPreferences);
          n.plans.series.forEach(s => { if (s.category === oldName) s.category = target; });
          if (n.timer && n.timer.category === oldName) n.timer = { ...n.timer, category: target }; });
        close(); done?.(); }
      catch (e) { el.querySelector('[data-f=err]').textContent = e.message; }
    }));
  }
}

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
      if (n.scope) root.KisSummaryUi.story(app, n.scope); else app.go('history', { planned: false, month: n.month }); });
    el.querySelector('[data-a=settings]').onclick = () => { close(); app.go('settings', { section: 'התראות' }); };
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

/* Feedback goes as one JSON report to the owner's Google Apps Script relay, which mails it from the owner's account
   (docs/feedback-relay.md). Until FEEDBACK_URL is set the sheet opens a mail draft. The trail keeps fixed event names
   and code positions only: never typed text, amounts, workplaces, names, emails or keys. */
const FEEDBACK_URL = 'https://script.google.com/macros/s/AKfycbwDfq5iA_2NSGYj57js0uwAuoSjErBWN-mB7jkjse4HKS476TPyqEIKAqitrVD47PmR/exec';
const FEEDBACK_TOKEN = 'salary-feedback-v1';
const TRAIL_KEY = 'kis_diagnostics_v1', TRAIL_MAX = 80;
const trail = (() => { try { return JSON.parse(localStorage.getItem(TRAIL_KEY) || '[]').slice(-TRAIL_MAX); } catch { return []; } })();
function event(e, d) {
  trail.push(d ? { at: Date.now(), e, d: String(d).slice(0, 80) } : { at: Date.now(), e });
  while (trail.length > TRAIL_MAX) trail.shift();
  try { localStorage.setItem(TRAIL_KEY, JSON.stringify(trail)); } catch { /* private mode */ }
}
const file = name => String(name || '').split('/').pop().split('?')[0];
if (typeof addEventListener === 'function') {
  addEventListener('error', ev => event('error', `${ev.error?.name || 'Error'} ${file(ev.filename)}:${ev.lineno || 0}`));
  addEventListener('unhandledrejection', ev => event('rejection', ev.reason?.name || 'Error'));
}
const feedbackDraft = { kind: 'תקלה', message: '', image: null };

/** The report without the screenshot. Only the listed fields leave the browser. */
function feedbackReport(app, kind, message, now = Date.now()) {
  const { store } = app, nav = globalThis.navigator || {}, scr = globalThis.screen || {};
  return {
    token: FEEDBACK_TOKEN, kind, message: String(message).trim(), sentAt: now,
    app: { platform: 'web', version: root.WorkBuild?.buildId || '' },
    device: { userAgent: nav.userAgent || '', language: nav.language || '', timeZone: Intl.DateTimeFormat().resolvedOptions().timeZone,
      screen: `${scr.width || 0}x${scr.height || 0}`, viewport: `${globalThis.innerWidth || 0}x${globalThis.innerHeight || 0}`, pixelRatio: globalThis.devicePixelRatio || 1,
      standalone: !!globalThis.matchMedia?.('(display-mode: standalone)').matches, online: nav.onLine !== false },
    state: { page: app.page || '', theme: store.local.theme || 'system', haptics: store.local.haptics !== false, signedIn: !!root.KisAccount?.state.user,
      sync: root.KisAccount?.state.status || '', entries: store.data.entries.length, categories: store.data.categories.length, weeklySeries: store.plans.series.length },
    events: trail.slice()
  };
}

/** The picked image as JPEG of at most 1600 px (re-encoding also drops its location data). */
async function shrink(blob) {
  const bmp = await createImageBitmap(blob), k = Math.min(1, 1600 / Math.max(bmp.width, bmp.height));
  const c = document.createElement('canvas'); c.width = Math.round(bmp.width * k); c.height = Math.round(bmp.height * k);
  c.getContext('2d').drawImage(bmp, 0, 0, c.width, c.height);
  return { name: 'screenshot.jpg', mime: 'image/jpeg', base64: c.toDataURL('image/jpeg', 0.82).split(',')[1] };
}

function feedbackSheet(app) {
  const d = feedbackDraft;
  U().sheet('feedback-sheet', (el, close) => {
    const relay = !!FEEDBACK_URL;
    el.innerHTML = `<h2>משוב ותקלות</h2>
      <div class="row" style="gap:6px">${['תקלה', 'הצעה', 'הערה'].map(k => `<button class="chip" aria-pressed="${d.kind === k}" data-kind="${k}">${k}</button>`).join('')}</div>
      <textarea class="input" rows="4" data-f="msg" placeholder="מה קרה, או מה תרצה להציע?" aria-label="תיאור" data-testid="feedback-message"></textarea>
      ${relay ? `<label class="btn ghost small" style="position:relative" data-f="pick">צירוף צילום מסך<input type="file" accept="image/*" data-f="img" style="position:absolute;inset:0;opacity:0" aria-label="צירוף צילום מסך"></label>
      <div class="row" data-f="att" hidden style="align-items:center"><span class="grow">צילום מסך מצורף</span><button class="btn small ghost" data-a="unattach">הסרה</button></div>` : ''}
      <p class="caption">${relay ? 'יישלחו התיאור, הגרסה, פרטי הדפדפן ויומן פעולות קצר. סכומים, מקומות עבודה, שמות ומפתחות לא נשלחים.' : 'תיפתח טיוטת מייל עם התיאור ופרטי הדפדפן. נתוני העבודה לא מצורפים.'}</p>
      <button class="btn primary" data-a="send" data-testid="feedback-send" disabled>${relay ? 'שליחה' : 'פתיחת טיוטת מייל'}</button>
      <p class="caption" data-f="err" hidden data-testid="feedback-failed">השליחה לא הצליחה. אפשר לנסות שוב, או <a href="#" data-a="mail">לשלוח כטיוטת מייל</a>.</p>`;
    const q = s => el.querySelector(s), msg = q('[data-f=msg]'), send = q('[data-a=send]');
    const sync = () => { send.disabled = !msg.value.trim(); if (relay) { q('[data-f=pick]').hidden = !!d.image; q('[data-f=att]').hidden = !d.image; } };
    msg.value = d.message; sync();
    msg.addEventListener('input', () => { d.message = msg.value; q('[data-f=err]').hidden = true; sync(); });
    el.querySelectorAll('[data-kind]').forEach(b => b.onclick = () => { d.kind = b.dataset.kind; el.querySelectorAll('[data-kind]').forEach(x => x.setAttribute('aria-pressed', x === b)); });
    const mail = () => {
      const r = feedbackReport(app, d.kind, d.message);
      location.href = `mailto:sholi2157+salaryfeedback@gmail.com?subject=${encodeURIComponent(`[${d.kind}] שכר עבודות אתר`)}&body=${encodeURIComponent(`סוג: ${d.kind}\nדפדפן: ${r.device.userAgent}\n\n${r.message}`)}`;
    };
    q('[data-a=mail]').onclick = e => { e.preventDefault(); mail(); };
    if (relay) {
      q('[data-f=img]').onchange = e => { d.image = e.target.files[0] || null; e.target.value = ''; sync(); };
      q('[data-a=unattach]').onclick = () => { d.image = null; sync(); };
    }
    send.onclick = async () => {
      if (!relay) { mail(); return; }
      send.disabled = true; send.textContent = 'שולח…';
      let ok = false;
      try {
        const report = feedbackReport(app, d.kind, d.message);
        if (d.image) report.screenshot = await shrink(d.image).catch(() => null);
        // text/plain keeps it a simple request, so Apps Script answers without a CORS preflight.
        const res = await fetch(FEEDBACK_URL, { method: 'POST', headers: { 'Content-Type': 'text/plain;charset=utf-8' }, body: JSON.stringify(report) });
        ok = (await res.json()).ok === true;
      } catch { ok = false; }
      event(ok ? 'feedback_sent' : 'feedback_failed');
      send.textContent = 'שליחה'; sync();
      if (ok) { d.message = ''; d.image = null; close(); U().toast('המשוב נשלח. תודה!'); } else q('[data-f=err]').hidden = false;
    };
  });
}

root.KisSettings = { open: name => { section = name || null; }, render, recoveryPanel, bindRecovery, recoverySheet, feedbackSheet, feedbackReport, event, placeSheet, placesSheet, backupText, dueReminders, importSheet, notificationsSheet, notes, unread };
})(globalThis);
