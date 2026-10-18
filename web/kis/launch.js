/* The 2.0 launch (once per browser, new or returning): a few words in the new look, then the name, then a short tour from Home. */
(function (root) {
'use strict';
const U = () => root.KisUi;
const LAUNCH_VERSION = 2;

const STEPS = [
  { title: 'ברוכים הבאים', body: 'רושמים משמרות, רואים כמה הרווחתם ומשתפים בקלות. בוא נכיר את המקומות החשובים.' },
  { target: '[data-testid=add-shift]', title: 'כאן מתחילים', body: 'הכפתור + מוסיף משמרת שכבר עבדת. "אני מתחיל עכשיו" בבית מפעיל טיימר בזמן העבודה.' },
  { target: '[data-testid=home-bell]', title: 'התראות', body: 'כאן מופיעים הסיכום החודשי ותזכורות. את ההתראות עצמן מפעילים בהגדרות.' },
  { page: 'history', target: '[data-testid=tab-history]', title: 'כל המשמרות במקום אחד', body: 'עבדתי ומתוכנן, חיפוש, סינון ומיון. לחיצה ארוכה בוחרת כמה משמרות: שיתוף, סימון תשלום או השלמת תעריף.' },
  { page: 'settings', target: '[data-testid=tab-settings]', title: 'הגדרות', body: 'מקומות עבודה עם תעריף, מטבע וצבע, התראות, גיבוי והשם שלך.' },
  { page: 'home', title: 'זה הכול. אתה מוכן.', body: 'מוסיפים משמרת, והחישובים עלינו. ההדרכה תמיד זמינה שוב בהגדרות.' },
];

/** On open: the launch when it was not seen yet. */
function maybeStart(app) {
  if ((app.store.local.launched || 0) >= LAUNCH_VERSION) return false;
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
  const finish = () => { others.forEach(n => { n.inert = false; }); el.remove(); store.setLocal('launched', LAUNCH_VERSION, true); app.go('home'); tour(app); };
  const slogan = () => {
    el.innerHTML = `<div class="launch-in"><div class="grow"></div><h1 class="launch-words"><span style="--d:.15s" data-testid="launch-line-1">עיצוב חדש.</span><span style="--d:.7s" data-testid="launch-line-2">התחלה חדשה.</span></h1>
      <div class="grow"></div><button class="btn primary" data-a="next" data-testid="launch-next">הבא</button></div>`;
    el.setAttribute('aria-label', 'עיצוב חדש. התחלה חדשה.');
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
  const end = () => { el.remove(); window.removeEventListener('resize', draw); app.store.setLocal('tourDone', true, true); app.go('home'); };
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

root.KisLaunch = { maybeStart, launch, tour, STEPS, LAUNCH_VERSION };
})(globalThis);
