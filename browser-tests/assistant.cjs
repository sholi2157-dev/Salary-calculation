/*
 * (Shared setup copied from account-sync.cjs.) Google account and sync on the built public/ folder, in a real browser, against the Firebase emulator
 * (run inside `firebase emulators:exec --only auth,firestore`). Synthetic data only.
 * Proves: the sign-in screen covers the site until sign-in; the data already in the browser stays and goes to the
 * account; a second browser of the same account gets it; a change on one appears on the other by itself;
 * signing out brings the screen back; another account starts empty and never sees or touches the first one's data.
 * The emulator signs in with email/password inside the page: the same onAuthStateChanged path as the Google popup.
 */
const assert = require('node:assert/strict'), http = require('node:http'), fs = require('node:fs'), path = require('node:path');
const { chromium } = require(process.env.PLAYWRIGHT_MODULE || 'playwright');
const ROOT = path.resolve('public'), OUT = process.env.SCREENSHOT_DIR || 'browser-evidence';
const SDK = path.dirname(require.resolve('firebase/package.json'));
fs.mkdirSync(OUT, { recursive: true });
const types = { '.html': 'text/html; charset=utf-8', '.js': 'text/javascript', '.css': 'text/css', '.json': 'application/json', '.ttf': 'font/ttf' };
const server = http.createServer((q, r) => {
  let p = decodeURIComponent(new URL(q.url, 'http://x').pathname); if (p === '/') p = '/index.html';
  if (p === '/api/config') { r.writeHead(200, { 'content-type': 'application/json' }); return r.end(JSON.stringify({ firebase: { apiKey: 'fake-emulator-key', projectId: 'demo-salary-sync', authDomain: '127.0.0.1' }, accountSyncEnabled: true })); }
  const f = path.join(ROOT, p); if (!f.startsWith(ROOT) || !fs.existsSync(f) || fs.statSync(f).isDirectory()) { r.writeHead(404); return r.end(); }
  r.writeHead(200, { 'content-type': types[path.extname(f)] || 'application/octet-stream', 'cache-control': 'no-store' }); fs.createReadStream(f).pipe(r);
});
const tid = t => `[data-testid="${t}"]`;
/** The 2.1 intro comes once per browser before the sign-in: the animated words, then how it works. */
async function pastIntro(p, shots) {
  await p.waitForSelector(tid('intro-line-3')); await p.waitForTimeout(2600);
  if (shots) await p.screenshot({ path: path.join(OUT, 'intro-1-390.png') });
  await p.click(tid('intro-next')); await p.waitForSelector(tid('intro-next-2')); await p.waitForTimeout(900);
  if (shots) await p.screenshot({ path: path.join(OUT, 'intro-2-390.png') });
  await p.click(tid('intro-next-2')); await p.waitForSelector(tid('signin-google'));
}
const seed = { entries: [
  { id: 'local-1', category: 'מסעדה', date: 1760000000000, createdAt: 1760000000500, isTimeRange: true, startTime: '09:00', endTime: '17:00', hours: 8, hourlyRate: 40.25, totalEarnings: 322, isPaid: false, notes: 'ראשונה', currency: '₪', isGroupShift: false, employerRate: null, workerRate: null, groupWorkersJson: '' },
  { id: 'local-2', category: 'מסעדה', date: 1760086400000, createdAt: 1760086400500, isTimeRange: false, startTime: null, endTime: null, hours: 5, hourlyRate: 40.25, totalEarnings: 201.25, isPaid: true, notes: '', currency: '₪', isGroupShift: false, employerRate: null, workerRate: null, groupWorkersJson: '' }],
  categories: [{ name: 'מסעדה', defaultRate: 40.25 }], workers: [], webPreferences: { mainCurrency: '₪', defaultCategory: 'מסעדה', categoryCurrencies: {} } };

/** The page's Firebase talks to the emulator; the SDK files come from the installed package (no network). */
async function context(browser, opts = {}) {
  const ctx = await browser.newContext({ viewport: { width: 390, height: 844 }, locale: 'he-IL', serviceWorkers: 'block', ...opts });
  await ctx.route('https://www.gstatic.com/firebasejs/**', route => {
    const name = route.request().url().split('/').pop();
    route.fulfill({ status: 200, contentType: 'text/javascript', body: fs.readFileSync(path.join(SDK, name)) });
  });
  await ctx.addInitScript(() => {
    let fb; Object.defineProperty(window, 'firebase', { configurable: true, get: () => fb, set: v => { fb = v;
      if (v && v.initializeApp && !v.__emulated) { v.__emulated = true; const init = v.initializeApp.bind(v);
        v.initializeApp = (...a) => { const app = init(...a); app.auth().useEmulator('http://127.0.0.1:9099', { disableWarnings: true }); app.firestore().useEmulator('127.0.0.1', 8080); return app; }; } } });
  });
  return ctx;
}
const signIn = (page, email, create) => page.evaluate(([e, c]) => c ? firebase.auth().createUserWithEmailAndPassword(e, 'Synthetic-test-password-123!').then(() => 1)
  : firebase.auth().signInWithEmailAndPassword(e, 'Synthetic-test-password-123!').then(() => 1), [email, create]);
const entries = page => page.evaluate(() => window.Kis.store.data.entries.map(e => ({ notes: e.notes, total: e.totalEarnings })).sort((a, b) => a.total - b.total));
async function until(fn, what, ms = 15000) { const end = Date.now() + ms; for (;;) { try { const v = await fn(); if (v) return v; } catch {} if (Date.now() > end) throw Error('timed out: ' + what); await new Promise(r => setTimeout(r, 200)); } }


// Personal AI assistants end to end: an assistant adds a shift through /mcp, it appears on the open page by itself,
// the Settings sheet shows the assistant and its action, undo removes the shift, and cutting off stops the assistant.
const A = require('../lib/server/assistant.cjs');
const env = { MCP_SECRET: 'synthetic-test-secret-' + 'q'.repeat(32), FIRESTORE_EMULATOR_HOST: '127.0.0.1:8080', FIREBASE_AUTH_EMULATOR_HOST: '127.0.0.1:9099', FIREBASE_PROJECT_ID: 'demo-salary-sync', FIREBASE_WEB_API_KEY: 'fake-emulator-key' };
(async () => {
  await new Promise(r => server.listen(0, r));
  const base = `http://127.0.0.1:${server.address().port}/`;
  const browser = await chromium.launch(process.env.CHROMIUM_PATH ? { executablePath: process.env.CHROMIUM_PATH } : {});
  const errors = [], email = crypto.randomUUID() + '@example.invalid';
  try {
    const ctx = await context(browser);
    await ctx.addInitScript(d => { if (!sessionStorage.getItem('seeded')) { sessionStorage.setItem('seeded', '1'); localStorage.clear();
      localStorage.setItem('work_complete_backup', JSON.stringify(d)); localStorage.setItem('kis_web_v1', JSON.stringify({ launched: 2, tourDone: true })); } }, seed);
    const page = await ctx.newPage(); page.on('pageerror', e => errors.push(e.message));
    await page.goto(base); await pastIntro(page);
    await signIn(page, email, true); await page.waitForSelector(tid('signin'), { state: 'detached' });
    await page.click(tid('tab-settings')); await until(async () => /מסונכרן/.test(await page.textContent(tid('account-status'))), 'first sync');
    const refreshToken = await page.evaluate(() => firebase.auth().currentUser.refreshToken);
    // The bell invites once to connect an assistant; the note opens the explanation with the address.
    await page.click(tid('tab-home')); await page.click(tid('home-bell'));
    assert.match(await page.textContent(tid('recent-note-0')), /מחברים את ה-AI שלך/);
    await page.click(tid('recent-note-0')); await page.waitForSelector(tid('assistants-url'));
    assert.match(await page.textContent(tid('assistants-sheet')), /הרשאות[\s\S]*בטוח/);
    await page.waitForTimeout(700); await page.screenshot({ path: path.join(OUT, 'assistants-explain-390.png') });
    await page.keyboard.press('Escape'); await page.waitForSelector(tid('assistants-sheet'), { state: 'detached' });
    await page.click(tid('tab-settings'));
    // The assistant connects (the approval the person gives on /connect) and adds a shift.
    const [, client] = A.register(env, { redirect_uris: ['https://assistant.example/cb'], client_name: 'ChatGPT' });
    const verifier = crypto.randomUUID() + crypto.randomUUID(), challenge = require('node:crypto').createHash('sha256').update(verifier).digest('base64url');
    const [, g] = await A.grant(env, { refreshToken, scopes: ['shifts.add'], duration: 'day', tz: 'Asia/Jerusalem', client_id: client.client_id, redirect_uri: 'https://assistant.example/cb', code_challenge: challenge });
    const [, t] = await A.token(env, { grant_type: 'authorization_code', code: new URL(g.redirect).searchParams.get('code'), code_verifier: verifier, client_id: client.client_id });
    const req = { headers: { host: 'kis.test', authorization: 'Bearer ' + t.access_token } };
    const [, r] = await A.mcp(env, req, { jsonrpc: '2.0', id: 1, method: 'tools/call', params: { name: 'add_shift', arguments: { date: '2026-10-24', start: '18:00', end: '22:00', workplace: 'מסעדה', notes: 'מהעוזר' } } });
    assert.ok(!r.result.isError, r.result.content[0].text);
    await until(async () => page.evaluate(() => window.Kis.store.data.entries.some(e => e.notes === 'מהעוזר' && e.totalEarnings === 161)), 'the assistant\'s shift arrives by itself');
    // Settings → assistants: the connection, its permission and its action.
    await page.click(tid('account-assistants')); await page.waitForSelector(tid('assistant-cut'));
    assert.match(await page.textContent(tid('assistants-sheet')), /ChatGPT[\s\S]*קריאה · הוספה/);
    await page.screenshot({ path: path.join(OUT, 'assistants-390.png') });
    await page.click(tid('assistant-undo'));
    await until(async () => page.evaluate(() => !window.Kis.store.data.entries.some(e => e.notes === 'מהעוזר')), 'undo removes the shift');
    await page.waitForSelector(tid('assistant-cut'));
    await page.click(tid('assistant-cut')); await page.click(tid('confirm-ok'));
    await page.waitForSelector(tid('assistants-none'));
    const [cut] = await A.mcp(env, req, { jsonrpc: '2.0', id: 2, method: 'tools/list' });
    assert.equal(cut, 401, 'cut off at once');
    assert.equal((await entries(page)).length, 2, 'the person\'s own shifts untouched');
    assert.deepEqual(errors, []);
    console.log('assistant browser checks passed');
  } finally { await browser.close(); server.close(); }
})().catch(e => { console.error(e); process.exit(1); });
