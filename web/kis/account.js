/*
 * Google account for the 2.0 site: the sign-in screen, which local data belongs to which account, and running
 * account sync (web/sync) whenever something changes here or in the account.
 * - The data already in this browser belongs to the first account that signs in here; it is never deleted.
 *   Another account gets its own copy beside it ("acct:<uid>:" keys), so accounts never mix.
 * - Without internet (or when Google cannot be reached) the site keeps working on this browser and asks again later.
 */
(function (root) {
'use strict';
const U = () => root.KisUi;
const STATE = 'work_account_v2', DEVICE = 'work_device_id_v1', SDK = 'https://www.gstatic.com/firebasejs/10.8.0/firebase-';

function readState(storage) { try { return JSON.parse(storage.getItem(STATE) || '{}') || {}; } catch { return {}; } }
function writeState(storage, s) { storage.setItem(STATE, JSON.stringify(s)); }
function scoped(storage, prefix) {
  return { getItem: k => storage.getItem(prefix + k), setItem: (k, v) => storage.setItem(prefix + k, v), removeItem: k => storage.removeItem(prefix + k) };
}
/** The storage holding the signed-in account's data: the browser's own keys for the first account, a separate copy for any other. */
function storageFor(storage) {
  const s = readState(storage);
  return s.current && s.bound && s.current !== s.bound ? scoped(storage, 'acct:' + s.current + ':') : storage;
}
function deviceId(storage) {
  let id = storage.getItem(DEVICE);
  if (!id) { id = root.WorkSyncProtocol.randomId(); storage.setItem(DEVICE, id); }
  return id;
}

let firebaseReady = null;
function loadFirebase() {
  if (firebaseReady) return firebaseReady;
  const script = name => new Promise((ok, fail) => { const s = document.createElement('script'); s.src = SDK + name + '-compat.js'; s.onload = ok; s.onerror = fail; document.head.append(s); });
  firebaseReady = (async () => {
    // The configuration first: a site without accounts never downloads Google's code.
    const response = await fetch('/api/config', { cache: 'no-store' });
    const config = response.ok ? await response.json() : null;
    if (!config?.firebase) throw Error('config');
    if (typeof root.firebase === 'undefined') { await script('app'); await Promise.all([script('auth'), script('firestore')]); }
    if (!root.firebase.apps.length) root.firebase.initializeApp(config.firebase);
    return root.firebase;
  })();
  firebaseReady.catch(() => { firebaseReady = null; });
  return firebaseReady;
}

const account = { user: null, sync: null, status: 'local', error: null, ready: false };
let app = null, gate = null, stopListen = null, timer = null, unsubscribe = null;

function init(theApp) {
  app = theApp;
  connect();
  window.addEventListener('online', () => { if (!account.user) connect(); else schedule(0); });
}

async function connect() {
  let fb;
  // Google cannot be reached: keep working here; the sign-in screen comes when it can.
  try { fb = await loadFirebase(); }
  catch { account.status = 'offline'; return; }
  if (account.listening) return;
  account.listening = true;
  fb.auth().onAuthStateChanged(user => user ? signedIn(fb, user) : signedOut());
}

function signedIn(fb, user) {
  const s = readState(localStorage), before = s.current;
  if (!s.bound) s.bound = user.uid;
  s.current = user.uid; s.email = user.email || ''; s.name = user.displayName || '';
  writeState(localStorage, s);
  // The page holds another account's data: start again on this account's own copy.
  if (before !== user.uid && storageFor(localStorage) !== app.storage) { location.reload(); return; }
  account.user = user; account.status = 'syncing'; hideGate();
  if (!app.store.local.name && user.displayName) app.store.setLocal('name', user.displayName.split(' ')[0].slice(0, 40), true);
  // 2.1: once per browser, the bell invites to connect a personal AI assistant.
  if (localStorage.getItem('kis_ai_invited_v1') !== '1') {
    localStorage.setItem('kis_ai_invited_v1', '1');
    app.store.addRecent({ kind: 'ai', title: 'מחברים את ה-AI שלך', text: 'כמה דקות, ואפשר לשאול את ChatGPT או Claude על המשמרות. נגיעה להסבר', time: Date.now(), month: null });
    app.refresh?.();
  }
  const transport = root.WorkSyncFirestore.firestoreTransport(fb);
  account.sync = root.WorkSync.createSync({ store: app.store, storage: app.storage, uid: user.uid, deviceId: deviceId(localStorage), transport,
    currentUid: () => fb.auth().currentUser?.uid || null });
  unsubscribe?.(); unsubscribe = app.store.subscribe(() => schedule(1500));
  stopListen?.(); stopListen = transport.listen(user.uid, Date.now(), () => schedule(300));
  document.addEventListener('visibilitychange', () => { if (document.visibilityState === 'visible') schedule(0); });
  schedule(0);
}

function signedOut() {
  account.user = null; account.sync = null; stopListen?.(); stopListen = null; unsubscribe?.(); unsubscribe = null;
  const s = readState(localStorage);
  if (s.current) { s.current = null; writeState(localStorage, s); if (app.storage !== localStorage) { location.reload(); return; } }
  showGate();
}

let applying = false;
function schedule(ms) {
  if (!account.sync) return;
  clearTimeout(timer);
  timer = setTimeout(async () => {
    if (applying) return schedule(500);
    applying = true; account.status = 'syncing'; changed();
    try { const conflicts = await account.sync.sync(); account.status = navigator.onLine === false ? 'offline' : 'ok'; account.error = null;
      if (conflicts && !account.toldConflicts) { account.toldConflicts = true; U().toast('משמרת השתנתה גם במכשיר אחר', 'לבחירה', () => conflictsSheet()); } }
    catch (e) { account.status = navigator.onLine === false ? 'offline' : 'error'; account.error = e.message; if (navigator.onLine !== false) schedule(30000); }
    finally { applying = false; changed(); }
  }, ms);
}
const changed = () => document.dispatchEvent(new CustomEvent('kis-account'));

// ---- the sign-in screen, the first time after the 2.1 intro ----
const INTRO = 'kis_ai_intro_v1';
let introStep = null;
function showGate() {
  // Automated checks of the public site run without an account.
  if (localStorage.getItem('kis_signin_skip_v1') === '1') return;
  if (gate) return draw();
  gate = document.createElement('div');
  gate.className = 'launch signin'; gate.dataset.testid = 'signin'; gate.setAttribute('role', 'dialog'); gate.setAttribute('aria-modal', 'true'); gate.setAttribute('aria-labelledby', 'signin-title');
  document.body.append(gate);
  if (introStep == null) introStep = localStorage.getItem(INTRO) === '1' ? 2 : 0;
  draw();
}
/** What it is, how to connect, the permissions and why it is safe: in the intro and behind the bell's invitation. */
function explainCards(withHow = true) {
  return [['מה זה', 'מדברים עם העוזר בשפה רגילה: "כמה הרווחתי החודש?" או "תוסיף משמרת מחר מ-9 עד 5 במסעדה". הוא עונה ומעדכן לפי המשמרות שלך.'],
    ['איך מחברים', 'בהגדרות העוזר מוסיפים חיבור (Connector) עם הכתובת שמופיעה בהגדרות ← עוזרי AI. נפתח דף, מתחברים עם Google ובוחרים מה מותר לו.'],
    ['הרשאות', 'לראות משמרות מותר תמיד. להוסיף, לערוך או למחוק רק אם הפעלת. בוחרים לכמה זמן: שעה, יום, 30 יום או עד שתנתק.'],
    ['בטוח', 'העוזר רואה רק את המשמרות שלך ולא מקבל את הסיסמה. כל שינוי שלו נרשם ואפשר לבטל אותו, ומנתקים אותו בכל רגע.']]
    .filter(([t]) => withHow || t !== 'איך מחברים').map(([t, b], i) => `<div class="card intro-card" style="--d:${(i * 0.12).toFixed(2)}s"><b>${t}</b><p style="margin:4px 0 0">${b}</p></div>`).join('');
}
function draw() {
  const offline = account.status === 'offline';
  if (introStep < 2) {
    const updated = (app?.store?.local?.launched || 0) >= 2;
    gate.innerHTML = introStep === 0
      ? `<div class="launch-in"><div class="grow"></div>
        <p class="intro-label" style="--d:.1s" data-testid="intro-line-1">חישוב שכר</p><p class="intro-label muted" style="--d:.3s">${updated ? 'גרסה 2.1' : 'ברוכים הבאים'}</p>
        <h1 class="intro-head" id="signin-title" style="--d:.6s" data-testid="intro-line-3">כעת סוכן ה-AI שלך מכיר אותך ואת העבודה שלך.</h1>
        <p class="intro-label" style="--d:1.1s">בקשו מהסוכן שלכם:</p>
        <div class="intro-chat small"><p class="mine" style="--d:1.4s">כמה הרווחתי החודש?</p><p class="mine" style="--d:1.8s">עבדתי אתמול בכינוס השלוחים 15 שעות, ושניאור נגר ביקש ממני לרשום בהערה סידור כסאות. תוסיף לי בבקשה.</p></div>
        <p class="intro-done" style="--d:2.3s">והוא יעשה זאת בשבילכם!</p>
        <div class="intro-ais" style="--d:2.7s"><span class="caption">מחובר עם</span><div>${['ChatGPT', 'Claude', 'Grok', 'Muse', 'Instinct'].map(n => `<b dir="ltr">${n}</b>`).join('')}</div></div>
        <div class="grow"></div><button class="btn primary" data-a="next" data-testid="intro-next">הבא</button></div>`
      : `<div class="launch-in"><div class="grow"></div><h1 class="launch-words" id="signin-title"><span style="--d:.1s">פשוט מבקשים.</span></h1>
        <div class="intro-chat"><p class="mine" style="--d:.55s">כמה הרווחתי החודש?</p><p style="--d:1s">2,140 ₪ על 15 משמרות.</p><p class="mine" style="--d:1.5s">תרשום מחר 9 עד 5.</p><p style="--d:1.95s">רשמתי ✓</p></div>
        <p class="caption intro-sub" style="--d:2.45s">מתחברים פעם אחת, וזהו.</p>
        <div class="grow"></div><button class="btn primary" data-a="next" data-testid="intro-next-2">הבא</button></div>`;
    gate.querySelector('[data-a=next]').onclick = () => { introStep++; if (introStep === 2) localStorage.setItem(INTRO, '1'); draw(); };
    gate.querySelector('[data-a=next]').focus({ preventScroll: true });
    return;
  }
  gate.innerHTML = `<div class="launch-in"><div class="grow"></div>${U().logo(56)}
    <h1 class="launch-ask" id="signin-title">מחברים את חשבון Google</h1>
    <p style="margin:0">פעם אחת, וכך האתר, האפליקציה והעוזר שלך רואים את אותן משמרות.</p>
    <p class="caption" style="margin:0">מה שכבר רשום כאן נשאר ועובר לחשבון.</p>
    <div class="grow"></div>
    ${offline ? '<p class="caption" role="status" style="margin:0" data-testid="signin-offline">אין חיבור לאינטרנט. אפשר להמשיך לעבוד כאן, וכשהחיבור יחזור נבקש להתחבר.</p>' : ''}
    <p class="caption" role="alert" style="margin:0;color:var(--danger)" data-testid="signin-error">${U().esc(account.error || '')}</p>
    <button class="btn primary" data-a="google" data-testid="signin-google"${offline ? ' disabled' : ''}>${GOOGLE}המשך עם Google</button>
    ${offline ? '<button class="btn ghost" data-a="later" data-testid="signin-later">להמשיך בינתיים</button>' : ''}</div>`;
  gate.querySelector('[data-a=google]').onclick = signIn;
  gate.querySelector('[data-a=later]')?.addEventListener('click', hideGate);
  gate.querySelector('[data-a=google]').focus({ preventScroll: true });
}
function hideGate() { gate?.remove(); gate = null; }

async function signIn() {
  account.error = null;
  try {
    const fb = await loadFirebase(), provider = new fb.auth.GoogleAuthProvider();
    provider.setCustomParameters({ prompt: 'select_account' });
    try { await fb.auth().signInWithPopup(provider); }
    catch (e) {
      if (e.code === 'auth/popup-blocked' || e.code === 'auth/operation-not-supported-in-this-environment') return fb.auth().signInWithRedirect(provider);
      if (e.code === 'auth/popup-closed-by-user' || e.code === 'auth/cancelled-popup-request') return;
      throw e;
    }
  } catch (e) {
    account.error = e.code === 'auth/network-request-failed' || !navigator.onLine ? 'אין חיבור לאינטרנט. נסה שוב כשהחיבור יחזור.' : 'ההתחברות לא הושלמה. אפשר לנסות שוב.';
    if (gate) draw();
  }
}

async function signOut() {
  const fb = await loadFirebase();
  if (account.sync) try { await account.sync.sync(); } catch {}
  await fb.auth().signOut();
}

// ---- settings: the account card and the conflicts ----
function settingsHtml() {
  const { esc } = U(), s = readState(localStorage);
  if (!account.user) return `<div class="row" style="align-items:center;min-height:58px"><div class="grow"><b>לא מחובר</b><div class="caption">הנתונים שמורים רק בדפדפן הזה</div></div><button class="chip" data-acc="signin" data-testid="account-signin">התחברות</button></div>`;
  const st = account.sync?.status() || {}, when = st.lastSync ? new Date(st.lastSync).toLocaleTimeString('he-IL', { hour: '2-digit', minute: '2-digit' }) : null;
  const line = account.status === 'syncing' ? 'מסנכרן…' : account.status === 'offline' ? 'אין חיבור. השינויים יישלחו כשהחיבור יחזור' : account.status === 'error' ? 'הסנכרון לא הצליח. ננסה שוב בקרוב' : when ? `מסונכרן · ${when}` : 'מסונכרן';
  return `<div class="row" style="align-items:center;min-height:58px"><div class="grow" style="min-width:0"><b style="overflow-wrap:anywhere">${esc(s.email || account.user.email || '')}</b><div class="caption" data-testid="account-status">${esc(line)}</div></div><button class="chip" data-acc="now">סנכרון</button></div>
    ${st.conflicts ? `<div class="divider"></div><div class="row" style="align-items:center;min-height:58px"><div class="grow"><b>${st.conflicts === 1 ? 'שינוי אחד ממתין לבחירה' : st.conflicts + ' שינויים ממתינים לבחירה'}</b><div class="caption">אותה משמרת השתנתה כאן ובמכשיר אחר</div></div><button class="chip" data-acc="conflicts" data-testid="account-conflicts">בחירה</button></div>` : ''}
    <div class="divider"></div><div class="row" style="align-items:center;min-height:58px"><div class="grow"><b>עוזרי AI</b><div class="caption">ChatGPT, Claude ועוד: לשאול, לסכם ולהוסיף משמרות</div></div><button class="chip" data-acc="assistants" data-testid="account-assistants">ניהול</button></div>
    <div class="divider"></div><div class="row" style="padding:10px 0 6px"><button class="btn small ghost grow" data-acc="signout" data-testid="account-signout">יציאה מהחשבון</button></div>`;
}
function bindSettings(el) {
  el.querySelector('[data-acc=signin]')?.addEventListener('click', () => { account.status = navigator.onLine === false ? 'offline' : 'local'; connect(); showGate(); });
  el.querySelector('[data-acc=now]')?.addEventListener('click', () => schedule(0));
  el.querySelector('[data-acc=conflicts]')?.addEventListener('click', conflictsSheet);
  el.querySelector('[data-acc=assistants]')?.addEventListener('click', assistantsSheet);
  el.querySelector('[data-acc=signout]')?.addEventListener('click', () => U().confirmSheet('יציאה מהחשבון', 'הנתונים נשארים בחשבון. כדי להמשיך תצטרך להתחבר שוב.', 'יציאה', () => signOut().catch(() => U().toast('היציאה לא הושלמה'))));
}

function describe(type, item) {
  const { esc } = U();
  if (!item) return '<span class="caption">נמחק</span>';
  if (type === 'entry') return `<b>${esc(item.category)}</b> · ${esc(U().dayLabelLong(item.date))} · ${esc(U().timeLabel(item))}<div class="caption">${esc(U().money(item.totalEarnings, item.currency))}${item.notes ? ' · ' + esc(item.notes) : ''}${item.isPaid ? ' · שולם' : ''}</div>`;
  if (type === 'category') return `<b>${esc(item.name)}</b> · ${esc(String(item.defaultRate))} לשעה`;
  if (type === 'series') return `<b>${esc(item.category)}</b> · משמרת קבועה ${esc(item.startTime)}–${esc(item.endTime)}`;
  if (type === 'exception') return `יום בתוכנית ${esc(item.date)}${item.cancelled ? ' · בוטל' : ''}`;
  return esc(item.name || '');
}
function conflictsSheet() {
  if (!account.sync) return;
  U().sheet('conflicts-sheet', (el, close) => {
    const draw = () => {
      const list = account.sync.conflicts();
      if (!list.length) { close(); return; }
      el.innerHTML = `<h2>מה לשמור?</h2><p class="caption" style="margin:0">השינויים האלה נעשו גם כאן וגם במכשיר אחר. שום דבר לא נמחק עד שבוחרים.</p>
        ${list.map((c, i) => `<div class="card" style="display:grid;gap:8px;background:var(--sunk)"><div><div class="caption">כאן</div>${describe(c.type, c.mine)}</div><div><div class="caption">במכשיר האחר</div>${describe(c.type, c.theirs)}</div>
          <div class="row"><button class="btn small ghost grow" data-keep="${i}" data-testid="conflict-mine">להשאיר את של כאן</button><button class="btn small primary grow" data-take="${i}" data-testid="conflict-theirs">לקחת מהמכשיר האחר</button></div></div>`).join('')}`;
      el.querySelectorAll('[data-keep]').forEach(b => b.onclick = () => { account.sync.resolve(list[+b.dataset.keep].syncId, true); schedule(0); draw(); });
      el.querySelectorAll('[data-take]').forEach(b => b.onclick = () => { account.sync.resolve(list[+b.dataset.take].syncId, false); draw(); });
    };
    draw();
  });
}

// ---- personal AI assistants: who is connected, what they did, cutting off and undoing ----
const PERM_LABEL = { 'shifts.read': 'קריאה', 'shifts.add': 'הוספה', 'shifts.edit': 'עריכה', 'shifts.delete': 'מחיקה' };
const TOOL_LABEL = { add_shift: 'הוסיף', update_shift: 'ערך', delete_shift: 'מחק' };
async function assistantsSheet(opts = {}) {
  if (!account.user) return;
  const fb = await loadFirebase(), uid = account.user.uid, me = fb.firestore().collection('users').doc(uid);
  const { esc } = U(), mcpUrl = location.origin + '/mcp';
  U().sheet('assistants-sheet', (el, close) => {
    const draw = async () => {
      let grants = [], log = [];
      try {
        [grants, log] = await Promise.all([me.collection('assistants').get({ source: 'server' }).then(q => q.docs),
          me.collection('assistant_log').orderBy('at', 'desc').limit(20).get({ source: 'server' }).then(q => q.docs)]);
      } catch { el.innerHTML = '<h2>עוזרי AI</h2><p class="caption">אין חיבור כרגע. נסה שוב בעוד רגע.</p>'; return; }
      const live = grants.filter(g => !g.get('until') || Date.parse(g.get('until')) > Date.now());
      el.innerHTML = `<h2>עוזרי AI</h2>
        ${opts.explain ? explainCards(false) : '<p class="caption" style="margin:0">עוזר מחובר יכול לענות על שאלות על המשמרות שלך, לסכם, ולפי מה שאישרת גם להוסיף, לערוך או למחוק. הוא רואה רק את המשמרות שלך.</p>'}
        <div class="card" style="display:grid;gap:8px;background:var(--sunk)"><b>איך מחברים</b>
          <div class="caption">בהגדרות העוזר, בחיבורים (Connectors), מוסיפים חיבור חדש עם הכתובת:</div>
          <div class="row" style="align-items:center"><code class="grow" dir="ltr" style="overflow-wrap:anywhere" data-testid="assistants-url">${esc(mcpUrl)}</code><button class="chip" data-x="copy">העתקה</button></div>
          <div class="caption">העוזר יפתח דף שבו מתחברים עם Google ובוחרים מה מותר לו. לעוזר בלי אפשרות כזו: <a href="/connect?personal=1" data-testid="assistants-key">מפתח אישי</a>.</div></div>
        <h3 style="margin:6px 0 0">מחוברים</h3>
        ${live.length ? live.map(g => `<div class="row" style="align-items:center;min-height:58px"><div class="grow" style="min-width:0"><b>${esc(g.get('name'))}</b>
          <div class="caption">${(g.get('scopes') || []).map(x => PERM_LABEL[x]).filter(Boolean).join(' · ')} · ${g.get('until') ? 'עד ' + esc(new Date(g.get('until')).toLocaleDateString('he-IL')) : 'עד שתנתק'}</div></div>
          <button class="chip warn" data-cut="${esc(g.id)}" data-testid="assistant-cut">ניתוק</button></div>`).join('<div class="divider"></div>') : '<p class="caption" data-testid="assistants-none">אין עוזרים מחוברים.</p>'}
        ${log.length ? `<h3 style="margin:6px 0 0">פעולות אחרונות</h3>${log.map(l => {
          const d = l.data(), item = d.after || d.before, type = d.type || 'entry';
          let shown = ''; try { shown = describe(type, P().decode(type, item)); } catch {}
          return `<div class="row" style="align-items:center;min-height:58px"><div class="grow" style="min-width:0"><div class="caption">${esc(d.client)} ${TOOL_LABEL[d.tool] || ''} · ${esc(new Date(d.at).toLocaleString('he-IL', { day: 'numeric', month: 'numeric', hour: '2-digit', minute: '2-digit' }))}</div>${shown}</div>
            ${d.undone ? '<span class="caption">בוטל</span>' : `<button class="chip" data-undo="${esc(l.id)}" data-testid="assistant-undo">ביטול</button>`}</div>`; }).join('<div class="divider"></div>')}` : ''}`;
      el.querySelector('[data-x=copy]').onclick = async e => { try { await navigator.clipboard.writeText(mcpUrl); e.target.textContent = 'הועתק'; } catch {} };
      el.querySelectorAll('[data-cut]').forEach(b => b.onclick = () => U().confirmSheet('לנתק את העוזר?', 'הוא לא יוכל יותר לראות או לשנות משמרות. מה שכבר שינה נשאר, ואפשר לבטל אותו כאן.', 'ניתוק', async () => {
        try { await me.collection('assistants').doc(b.dataset.cut).delete(); } catch { U().toast('הניתוק לא הושלם. נסה שוב'); } draw(); }));
      el.querySelectorAll('[data-undo]').forEach(b => b.onclick = async () => {
        b.disabled = true;
        try { await undo(fb, uid, me.collection('assistant_log').doc(b.dataset.undo)); U().toast('בוטל'); schedule(0); }
        catch { U().toast('הביטול לא הושלם. אולי המשמרת השתנתה מאז'); }
        draw();
      });
    };
    el.innerHTML = '<h2>עוזרי AI</h2><p class="caption">טוען…</p>';
    draw();
  });
}
const P = () => root.WorkSyncProtocol;
/** Puts the item back as it was before the assistant's action, as a new version (it reaches every device by sync). */
async function undo(fb, uid, ref) {
  const d = (await ref.get({ source: 'server' })).data();
  if (!d || d.undone) return;
  const t = root.WorkSyncFirestore.firestoreTransport(fb), doc = await fb.firestore().collection('users').doc(uid).collection('records_v1').doc(d.syncId).get({ source: 'server' });
  const version = doc.exists ? doc.get('version') : 0;
  // Only while the item is still what the assistant left; a later change by the person wins.
  if ((doc.exists ? doc.get('payload') ?? null : null) !== (d.after ?? null)) throw Error('changed');
  const proposed = { syncId: d.syncId, type: d.type || 'entry', version: version + 1, operation: d.syncId + ':undo-' + P().randomId(), deleted: d.before == null, payload: d.before ?? null };
  const accepted = await t.exchange(uid, version, proposed);
  if (accepted.operation !== proposed.operation) throw Error('changed');
  await ref.update({ undone: true });
}

const GOOGLE = '<svg width="20" height="20" viewBox="0 0 48 48" aria-hidden="true"><path fill="#FFC107" d="M43.6 20.5H42V20H24v8h11.3C33.7 32.7 29.2 36 24 36c-6.6 0-12-5.4-12-12s5.4-12 12-12c3.1 0 5.8 1.2 7.9 3.1l5.7-5.7C34 6.1 29.3 4 24 4 12.9 4 4 12.9 4 24s8.9 20 20 20 20-8.9 20-20c0-1.3-.1-2.4-.4-3.5z"/><path fill="#FF3D00" d="m6.3 14.7 6.6 4.8C14.7 15.1 19 12 24 12c3.1 0 5.8 1.2 7.9 3.1l5.7-5.7C34 6.1 29.3 4 24 4 16.3 4 9.7 8.3 6.3 14.7z"/><path fill="#4CAF50" d="M24 44c5.2 0 9.9-2 13.4-5.2l-6.2-5.2C29.2 35.1 26.7 36 24 36c-5.2 0-9.6-3.3-11.3-7.9l-6.5 5C9.5 39.6 16.2 44 24 44z"/><path fill="#1976D2" d="M43.6 20.5H42V20H24v8h11.3c-.8 2.2-2.2 4.2-4.1 5.6l6.2 5.2C37 39.2 44 34 44 24c0-1.3-.1-2.4-.4-3.5z"/></svg>';

root.KisAccount = { init, storageFor, scoped, readState, settingsHtml, bindSettings, conflictsSheet, assistantsSheet, state: account };
})(globalThis);
