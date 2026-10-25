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
    if (typeof root.firebase === 'undefined') { await script('app'); await Promise.all([script('auth'), script('firestore')]); }
    const response = await fetch('/api/config', { cache: 'no-store' });
    const config = response.ok ? await response.json() : null;
    if (!config?.firebase) throw Error('config');
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

// ---- the sign-in screen ----
function showGate() {
  // Automated checks of the public site run without an account.
  if (localStorage.getItem('kis_signin_skip_v1') === '1') return;
  if (gate) return draw();
  gate = document.createElement('div');
  gate.className = 'launch signin'; gate.dataset.testid = 'signin'; gate.setAttribute('role', 'dialog'); gate.setAttribute('aria-modal', 'true'); gate.setAttribute('aria-labelledby', 'signin-title');
  document.body.append(gate);
  draw();
}
function draw() {
  const offline = account.status === 'offline';
  gate.innerHTML = `<div class="launch-in"><div class="grow"></div>${U().logo(56)}
    <h1 class="launch-ask" id="signin-title">מתחברים עם Google</h1>
    <p style="margin:0">כך המשמרות נשמרות בחשבון שלך ומסתנכרנות בין האתר לאפליקציה.</p>
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
    <div class="divider"></div><div class="row" style="padding:10px 0 6px"><button class="btn small ghost grow" data-acc="signout" data-testid="account-signout">יציאה מהחשבון</button></div>`;
}
function bindSettings(el) {
  el.querySelector('[data-acc=signin]')?.addEventListener('click', () => { account.status = navigator.onLine === false ? 'offline' : 'local'; connect(); showGate(); });
  el.querySelector('[data-acc=now]')?.addEventListener('click', () => schedule(0));
  el.querySelector('[data-acc=conflicts]')?.addEventListener('click', conflictsSheet);
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

const GOOGLE = '<svg width="20" height="20" viewBox="0 0 48 48" aria-hidden="true"><path fill="#FFC107" d="M43.6 20.5H42V20H24v8h11.3C33.7 32.7 29.2 36 24 36c-6.6 0-12-5.4-12-12s5.4-12 12-12c3.1 0 5.8 1.2 7.9 3.1l5.7-5.7C34 6.1 29.3 4 24 4 12.9 4 4 12.9 4 24s8.9 20 20 20 20-8.9 20-20c0-1.3-.1-2.4-.4-3.5z"/><path fill="#FF3D00" d="m6.3 14.7 6.6 4.8C14.7 15.1 19 12 24 12c3.1 0 5.8 1.2 7.9 3.1l5.7-5.7C34 6.1 29.3 4 24 4 16.3 4 9.7 8.3 6.3 14.7z"/><path fill="#4CAF50" d="M24 44c5.2 0 9.9-2 13.4-5.2l-6.2-5.2C29.2 35.1 26.7 36 24 36c-5.2 0-9.6-3.3-11.3-7.9l-6.5 5C9.5 39.6 16.2 44 24 44z"/><path fill="#1976D2" d="M43.6 20.5H42V20H24v8h11.3c-.8 2.2-2.2 4.2-4.1 5.6l6.2 5.2C37 39.2 44 34 44 24c0-1.3-.1-2.4-.4-3.5z"/></svg>';

root.KisAccount = { init, storageFor, scoped, readState, settingsHtml, bindSettings, conflictsSheet, state: account };
})(globalThis);
