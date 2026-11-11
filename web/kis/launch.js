/* The 2.0 launch (once per browser, new or returning): a few words in the new look, then the name. The short tour from Home opens once per new account (see claimAccount). */
(function (root) {
'use strict';
const U = () => root.KisUi;
const LAUNCH_VERSION = 2;

const STEPS = [
  { title: 'ברוכים הבאים לחישוב שכר', body: 'כאן רושמים משמרות, רואים כמה הרווחת ושולחים דוח מסודר למעסיק. דקה אחת, ומכירים את העיקר.' },
  { target: '[data-testid=add-shift]', title: 'רישום משמרת', body: 'עבדת היום? לוחצים על + ורושמים. בטופס, בכפתור + מוסיפים בונוס, החזר הוצאות או הפסקה, והסכום מתעדכן מיד.' },
  { target: '[data-testid=live-start]', title: 'שעון משמרת', body: 'מתחילים לעבוד עכשיו? "אני מתחיל עכשיו" מפעיל שעון, ובסוף עוצרים אותו והמשמרת נרשמת.' },
  { target: '[data-testid=home-bell]', title: 'התראות', body: 'כאן מחכים הסיכום החודשי ותזכורות. את ההתראות עצמן מפעילים בהגדרות.' },
  { page: 'history', target: '[data-testid=tab-history]', title: 'כל המשמרות', body: 'מסודרות לפי חודש ושבוע, עם חיפוש וסינון. לחיצה ארוכה בוחרת כמה משמרות יחד: לשתף, לסמן ששולם או להשלים תעריף.' },
  { page: 'settings', target: '[data-testid=tab-settings]', title: 'הגדרות', body: 'מקומות עבודה עם תעריף, מטבע וצבע, מראה, התראות, גיבוי והחשבון שלך. גם ההדרכה הזו נמצאת שם, תחת עזרה.' },
  { page: 'home', title: 'זהו, אפשר להתחיל', body: 'רושמים את המשמרת הראשונה, והחשבון עלינו.' },
];

/*
 * F12 (2.3.1): with Google accounts the tour belongs to the account, not the browser. It opens once, on a new account's
 * first successful connect (Firebase stamps its creation and last sign-in together), after the launch and the sign-in
 * screen are gone, and never again except from "הצגת ההדרכה מחדש". An account that already existed (an update,
 * another browser, the app) never gets it. Until it is finished or skipped it stays pending, so a reload brings it back.
 * Same rule as the app (WorkOnboarding.kt). Without accounts on this site, the tour follows the browser, as before.
 */
const TOUR_ACCOUNTS = 'kis_tour_accounts_v1', TOUR_PENDING = 'kis_tour_pending_v1', NEW_ACCOUNT_WINDOW_MS = 120000;
function isNewAccount(created, last) {
  return Number.isFinite(created) && Number.isFinite(last) && created > 0 && Math.abs(last - created) <= NEW_ACCOUNT_WINDOW_MS;
}
/** isNewUser: Firebase's own answer from this browser's sign-in, when known. An account made on another device a moment
 * ago passes the time window but is not new here (2.4 task 03). */
function claimAccount(storage, uid, created, last, isNewUser = null) {
  let seen = [];
  try { seen = JSON.parse(storage.getItem(TOUR_ACCOUNTS) || '[]'); if (!Array.isArray(seen)) seen = []; } catch { seen = []; }
  if (seen.includes(uid)) return storage.getItem(TOUR_PENDING) === uid;
  const fresh = isNewAccount(created, last) && isNewUser !== false;
  storage.setItem(TOUR_ACCOUNTS, JSON.stringify([...seen, uid]));
  if (fresh) storage.setItem(TOUR_PENDING, uid);
  return fresh;
}
let wanted = false, touring = false;
/** The tour as soon as nothing else covers the page (the launch, the sign-in screen). */
function want(app) { wanted = true; maybeTour(app); }
function maybeTour(app) {
  if (!wanted || touring || document.querySelector('[data-testid=launch], [data-testid=signin]')) return;
  wanted = false; tour(app);
}
/** This site has no accounts (or sign-in is skipped): the browser's own first time decides, as before. */
function deviceTour(app) { if (!app.store.local.tourDone) want(app); }

/**
 * The launch already seen in this browser: by this account's own copy, or by the browser's own data (a second account
 * signed in here has a copy of its own, which never saw the launch; this browser did).
 */
function launched(app) {
  let browser = 0;
  try { browser = (globalThis.localStorage && JSON.parse(localStorage.getItem('kis_web_v1') || '{}').launched) || 0; } catch { /* unreadable: the copy decides */ }
  return Math.max(app.store.local.launched || 0, browser);
}
/** On open: the launch when it was not seen yet. */
function maybeStart(app) {
  if (launched(app) >= LAUNCH_VERSION) return false;
  launch(app);
  return true;
}

function launch(app) {
  const { store } = app, { esc } = U();
  const el = document.createElement('div');
  el.className = 'launch'; el.dataset.testid = 'launch'; el.setAttribute('role', 'dialog'); el.setAttribute('aria-modal', 'true');
  document.body.append(el);
  const others = [...document.body.children].filter(n => n !== el && !n.inert);
  others.forEach(n => { n.inert = true; });
  const finish = () => { others.forEach(n => { n.inert = false; }); el.remove(); store.setLocal('launched', LAUNCH_VERSION, true); app.go('home'); maybeTour(app); };
  const slogan = () => {
    el.innerHTML = `<div class="launch-in"><div class="grow"></div><h1 class="launch-words"><span style="--d:.15s" data-testid="launch-line-1">עיצוב חדש.</span><span style="--d:.7s" data-testid="launch-line-2">התחלה חדשה.</span></h1>
      <div class="grow"></div><button class="btn primary" data-a="next" data-testid="launch-next">הבא</button><button class="btn ghost" data-a="android" data-testid="launch-android-download"><span class="ic" aria-hidden="true">${U().ICON.download}</span> הורדת האפליקציה לאנדרואיד</button></div>`;
    el.setAttribute('aria-label', 'עיצוב חדש. התחלה חדשה.');
    el.querySelector('[data-a=android]').onclick = () => { el.hidden = true; root.KisDownload.open(() => { el.hidden = false; el.querySelector('[data-a=android]').focus({ preventScroll: true }); }); };
    el.querySelector('[data-a=next]').onclick = name;
    el.querySelector('[data-a=next]').focus({ preventScroll: true });
  };
  const name = () => {
    el.innerHTML = `<div class="launch-in"><div class="grow"></div><h1 class="launch-ask" id="launch-ask">איך קוראים לך?</h1>
      <input class="input" data-f="first" placeholder="שם פרטי" aria-label="שם פרטי" maxlength="40" value="${esc(store.local.name || '')}" data-testid="launch-first">
      <input class="input" data-f="last" placeholder="שם משפחה" aria-label="שם משפחה" maxlength="40" value="${esc(store.local.lastName || '')}" data-testid="launch-last">
      <div class="grow"></div><button class="btn primary" data-a="done" data-testid="launch-done">המשך</button><button class="btn ghost" data-a="later" data-testid="launch-later">אחר כך</button></div>`;
    el.setAttribute('aria-labelledby', 'launch-ask'); el.removeAttribute('aria-label');
    const first = el.querySelector('[data-f=first]'), last = el.querySelector('[data-f=last]'), done = el.querySelector('[data-a=done]');
    const sync = () => { done.disabled = !first.value.trim(); };
    first.addEventListener('input', sync); sync();
    done.onclick = () => { store.setLocal('name', first.value.trim().slice(0, 40), true); store.setLocal('lastName', last.value.trim().slice(0, 40), true); finish(); };
    el.querySelector('[data-a=later]').onclick = finish;
    first.focus();
  };
  slogan();
}

/** The short tour on the real pages (nothing is saved). Starts at Home. */
function tour(app) {
  let i = 0;
  const el = document.createElement('div');
  el.className = 'coach'; el.dataset.testid = 'tour';
  document.body.append(el);
  touring = true;
  const end = () => { touring = false; el.remove(); window.removeEventListener('resize', draw); app.store.setLocal('tourDone', true, true); try { localStorage.removeItem(TOUR_PENDING); } catch { /* storage closed */ } app.go('home'); };
  function draw() {
    const s = STEPS[i];
    const target = s.target && document.querySelector(s.target);
    const r = target?.getBoundingClientRect();
    el.innerHTML = `${r ? `<div class="coach-hole" style="top:${r.top - 4}px;left:${r.left - 4}px;width:${r.width + 8}px;height:${r.height + 8}px"></div>` : '<div class="coach-dim"></div>'}
      <div class="coach-card${r ? '' : ' center'}" role="dialog" aria-modal="true" aria-labelledby="coach-title"${r && r.top < innerHeight / 2 ? ' style="bottom:auto;top:' + (r.bottom + 16) + 'px"' : ''}>
        <div class="row" style="align-items:center"><span class="caption grow" data-testid="tour-progress">${i + 1} מתוך ${STEPS.length}</span><button class="plain caption" data-a="skip" data-testid="tour-skip" style="min-height:44px;padding:0 8px">דלג</button></div>
        <h2 id="coach-title">${U().esc(s.title)}</h2><p style="margin:0">${U().esc(s.body)}</p>
        <div class="row"><button class="btn primary grow" data-a="next" data-testid="tour-next">${i === STEPS.length - 1 ? 'יאללה, מתחילים' : 'הבא'}</button>${i ? '<button class="btn ghost" data-a="back" data-testid="tour-back">הקודם</button>' : ''}</div></div>`;
    el.querySelector('[data-a=skip]').onclick = end;
    el.querySelector('[data-a=next]').onclick = () => { if (i === STEPS.length - 1) end(); else { i++; show(); } };
    el.querySelector('[data-a=back]')?.addEventListener('click', () => { i--; show(); });
    el.querySelector('[data-a=next]').focus({ preventScroll: true });
  }
  function show() { const s = STEPS[i]; if (s.page && s.page !== app.page) app.go(s.page); draw(); }
  window.addEventListener('resize', draw);
  show();
}

root.KisLaunch = { maybeStart, launch, tour, launched, want, maybeTour, deviceTour, claimAccount, isNewAccount, STEPS, LAUNCH_VERSION };
})(globalThis);
