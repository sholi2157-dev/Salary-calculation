/*
 * /connect: the person lets an AI assistant into their shifts. Google sign-in (the same account as the app and the
 * website), then what the assistant may do and for how long. The server (lib/server/assistant.cjs) records the choice
 * as the person and hands the assistant a one-time code. ?personal=1 makes a personal key instead, for an assistant
 * without OAuth.
 */
(function () {
'use strict';
const $ = id => document.getElementById(id);
const esc = s => String(s ?? '').replace(/[&<>"']/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' })[c]);
const q = Object.fromEntries(new URLSearchParams(location.search));
const personal = q.personal === '1';
const main = $('connect');
const PERMS = [
  ['shifts.read', 'לראות משמרות וסכומים', 'כדי לענות על שאלות ולסכם. תמיד כלול.'],
  ['shifts.add', 'להוסיף משמרות', 'למשל "תוסיף משמרת מחר 9 עד 5 במסעדה".'],
  ['shifts.edit', 'לערוך משמרות', 'לתקן שעות, תעריף, הערה או סימון ששולם.'],
  ['shifts.delete', 'למחוק משמרות', 'אפשר לבטל כל מחיקה מתוך האפליקציה.'],
];
const TIMES = [['hour', 'שעה'], ['day', 'יום'], ['month', '30 יום'], ['always', 'עד שאנתק']];
let client = { name: 'עוזר AI' }, user = null, chosen = new Set(['shifts.read', ...String(q.scope || '').split(/\s+/).filter(s => PERMS.some(p => p[0] === s))]), time = 'month', busy = false;

function status(text, error) { main.innerHTML = `<p class="caption${error ? ' err' : ''}" id="status" role="${error ? 'alert' : 'status'}">${esc(text)}</p>`; }
function back(params) {
  const url = new URL(q.redirect_uri);
  for (const [k, v] of Object.entries(params)) url.searchParams.set(k, v);
  if (q.state) url.searchParams.set('state', q.state);
  location.href = url.toString();
}

function draw() {
  if (!user) {
    main.innerHTML = `<div class="logo-row"><span class="ai-mark" aria-hidden="true">AI</span></div>
      <h1 class="page-title">חיבור ${esc(client.name)}</h1>
      <p>${personal ? 'מפתח אישי מאפשר לעוזר AI לקרוא ולעדכן את המשמרות שלך.' : `${esc(client.name)} מבקש גישה למשמרות שלך באפליקציה "חישוב שכר".`}</p>
      <p class="caption">קודם מתחברים עם אותו חשבון Google של האפליקציה והאתר.</p>
      <p class="caption err" role="alert" id="err"></p>
      <button class="btn primary" data-a="google" data-testid="connect-google">המשך עם Google</button>`;
    main.querySelector('[data-a=google]').onclick = signIn;
    return;
  }
  main.innerHTML = `<h1 class="page-title">${personal ? 'מפתח אישי לעוזר AI' : `מה ${esc(client.name)} יוכל לעשות?`}</h1>
    <p class="caption">מחובר כ-${esc(user.email || user.displayName || '')}</p>
    <section class="card perms" aria-label="הרשאות">${PERMS.map(([id, title, hint]) => `
      <div class="perm"><div class="grow"><div class="label">${title}</div><div class="caption">${hint}</div></div>
      <button class="switch" role="switch" aria-checked="${chosen.has(id)}" aria-label="${title}" data-p="${id}" data-testid="perm-${id.split('.')[1]}"${id === 'shifts.read' ? ' disabled' : ''}></button></div>`).join('')}
    </section>
    <div class="label">לכמה זמן</div>
    <div class="seg" role="group" aria-label="לכמה זמן">${TIMES.map(([id, t]) => `<button data-t="${id}" aria-pressed="${time === id}" data-testid="time-${id}">${t}</button>`).join('')}</div>
    <section class="safe">
      <p><b>בטוח:</b> העוזר רואה רק את המשמרות שלך, דרך החשבון שלך. הוא לא מקבל את הסיסמה ולא נוגע בשום דבר אחר בחשבון Google.</p>
      <p>כל שינוי שהוא עושה נרשם, ואפשר לבטל אותו. מנתקים בכל רגע בהגדרות ← עוזרי AI.</p>
    </section>
    <p class="caption err" role="alert" id="err"></p>
    <div class="row"><button class="btn ghost grow" data-a="cancel" data-testid="connect-cancel">ביטול</button>
      <button class="btn primary grow" data-a="ok" data-testid="connect-ok">${personal ? 'יצירת מפתח' : 'אישור'}</button></div>
    <button class="link" data-a="other">להתחבר עם חשבון אחר</button>`;
  main.querySelectorAll('[data-p]').forEach(b => b.onclick = () => {
    const id = b.dataset.p; if (id === 'shifts.read') return;
    chosen.has(id) ? chosen.delete(id) : chosen.add(id); b.setAttribute('aria-checked', chosen.has(id));
  });
  main.querySelectorAll('[data-t]').forEach(b => b.onclick = () => { time = b.dataset.t; main.querySelectorAll('[data-t]').forEach(x => x.setAttribute('aria-pressed', x === b)); });
  main.querySelector('[data-a=cancel]').onclick = () => personal ? history.back() : back({ error: 'access_denied' });
  main.querySelector('[data-a=other]').onclick = async () => { await firebase.auth().signOut(); };
  main.querySelector('[data-a=ok]').onclick = approve;
}

async function signIn() {
  const provider = new firebase.auth.GoogleAuthProvider(); provider.setCustomParameters({ prompt: 'select_account' });
  try { await firebase.auth().signInWithPopup(provider); }
  catch (e) {
    if (e.code === 'auth/popup-blocked' || e.code === 'auth/operation-not-supported-in-this-environment') return firebase.auth().signInWithRedirect(provider);
    if (e.code !== 'auth/popup-closed-by-user' && e.code !== 'auth/cancelled-popup-request') $('err').textContent = 'ההתחברות לא הצליחה. נסה שוב.';
  }
}

async function approve() {
  if (busy) return; busy = true;
  const ok = main.querySelector('[data-a=ok]'); ok.disabled = true;
  try {
    const r = await fetch('/api/oauth?op=grant', { method: 'POST', headers: { 'content-type': 'application/json' }, body: JSON.stringify({
      refreshToken: firebase.auth().currentUser.refreshToken, scopes: [...chosen], duration: time, tz: Intl.DateTimeFormat().resolvedOptions().timeZone,
      ...(personal ? { personal: true, name: q.name || 'מפתח אישי' } : { client_id: q.client_id, redirect_uri: q.redirect_uri, code_challenge: q.code_challenge, state: q.state }) }) });
    const j = await r.json().catch(() => ({}));
    if (!r.ok) throw Error(j.error || r.status);
    if (j.redirect) { status('מחובר. חוזרים ל-' + client.name + '…'); location.href = j.redirect; return; }
    showKey(j.key);
  } catch (e) {
    $('err').textContent = e.message === 'not_configured' ? 'החיבור לעוזרים עוד לא הופעל באתר.' : 'האישור לא הושלם. נסה שוב.';
    ok.disabled = false;
  } finally { busy = false; }
}

function showKey(key) {
  main.innerHTML = `<h1 class="page-title">המפתח מוכן</h1>
    <p>מעתיקים אותו להגדרות העוזר, בשדה של Bearer token או API key, יחד עם הכתובת:</p>
    <p class="code" dir="ltr" data-testid="connect-url">${esc(location.origin)}/mcp</p>
    <p class="code key" dir="ltr" data-testid="connect-key">${esc(key)}</p>
    <p class="caption">הוא מוצג רק עכשיו. מי שמחזיק אותו יכול לפעול בהרשאות שבחרת, אז לא משתפים אותו עם אף אחד אחר.</p>
    <button class="btn primary" data-a="copy" data-testid="connect-copy">העתקת המפתח</button>`;
  main.querySelector('[data-a=copy]').onclick = async e => { try { await navigator.clipboard.writeText(key); e.target.textContent = 'הועתק ✓'; } catch { e.target.textContent = 'לא הועתק, סמן והעתק ידנית'; } };
}

async function start() {
  if (!personal) {
    if (q.response_type !== 'code' || !q.client_id || !q.redirect_uri || q.code_challenge_method !== 'S256' || !q.code_challenge) return status('הקישור לחיבור לא תקין. התחל מחדש מתוך העוזר.', true);
    const r = await fetch('/api/oauth?op=client&' + new URLSearchParams({ client_id: q.client_id, redirect_uri: q.redirect_uri }));
    if (!r.ok) return status('העוזר הזה לא מזוהה. התחל מחדש מתוך העוזר.', true);
    client = await r.json();
  }
  const config = (await (await fetch('/api/config')).json()).firebase;
  if (!config) return status('החיבור לעוזרים עוד לא הופעל באתר.', true);
  firebase.initializeApp(config);
  firebase.auth().onAuthStateChanged(u => { user = u; draw(); });
}
start().catch(() => status('לא ניתן לטעון את הדף. בדוק את החיבור לאינטרנט.', true));
})();
