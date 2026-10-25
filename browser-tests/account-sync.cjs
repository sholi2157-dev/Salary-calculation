/*
 * Google account and sync on the built public/ folder, in a real browser, against the Firebase emulator
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

(async () => {
  await new Promise(r => server.listen(0, r));
  const base = `http://127.0.0.1:${server.address().port}/`;
  const browser = await chromium.launch(process.env.CHROMIUM_PATH ? { executablePath: process.env.CHROMIUM_PATH } : {});
  const errors = [], email = crypto.randomUUID() + '@example.invalid';
  const watch = page => { page.on('pageerror', e => errors.push(e.message)); page.on('console', m => { if (m.type() === 'error' && !/PERMISSION_DENIED|ERR_|favicon|_vercel/.test(m.text())) errors.push(m.text()); }); };
  try {
    // 1. A browser with data from before the update: the sign-in screen, then the same data, now in the account.
    const ctxA = await context(browser);
    await ctxA.addInitScript(d => { if (!sessionStorage.getItem('seeded')) { sessionStorage.setItem('seeded', '1'); localStorage.clear();
      localStorage.setItem('work_complete_backup', JSON.stringify(d)); localStorage.setItem('kis_web_v1', JSON.stringify({ launched: 2, tourDone: true })); } }, seed);
    const A = await ctxA.newPage(); watch(A);
    await A.goto(base); await pastIntro(A, true);
    for (const scheme of ['light', 'dark']) { await A.emulateMedia({ colorScheme: scheme }); await A.screenshot({ path: path.join(OUT, `signin-${scheme}-390.png`) }); }
    await A.setViewportSize({ width: 1280, height: 800 }); await A.screenshot({ path: path.join(OUT, 'signin-light-1280.png') }); await A.setViewportSize({ width: 390, height: 844 });
    assert.equal(await A.evaluate(() => document.documentElement.scrollWidth), 390, 'no horizontal scroll');
    await signIn(A, email, true);
    await A.waitForSelector(tid('signin'), { state: 'detached' });
    assert.deepEqual(await entries(A), [{ notes: '', total: 201.25 }, { notes: 'ראשונה', total: 322 }], 'nothing lost or changed');
    await A.click(tid('tab-settings')); await until(async () => /מסונכרן/.test(await A.textContent(tid('account-status'))), 'first sync');
    await A.screenshot({ path: path.join(OUT, 'account-settings-390.png') });

    // 2. A second browser of the same account, empty before: everything arrives, amounts exact.
    const ctxB = await context(browser);
    await ctxB.addInitScript(() => { if (!sessionStorage.getItem('seeded')) { sessionStorage.setItem('seeded', '1'); localStorage.setItem('kis_web_v1', JSON.stringify({ launched: 2, tourDone: true })); } });
    const B = await ctxB.newPage(); watch(B);
    await B.goto(base); await pastIntro(B); await B.waitForSelector(tid('signin-google')); await signIn(B, email, false);
    await until(async () => (await entries(B)).length === 2, 'second browser receives the shifts');
    assert.deepEqual(await entries(B), await entries(A));

    // 3. A change on one browser appears on the other by itself (no reload, no button).
    await B.evaluate(() => window.Kis.store.update(d => { d.entries.find(e => e.totalEarnings === 322).notes = 'נערך בדפדפן השני'; }));
    await until(async () => (await entries(A)).some(e => e.notes === 'נערך בדפדפן השני'), 'live change arrives');
    await A.evaluate(() => window.Kis.store.addEntries([{ category: 'מסעדה', date: 1760172800000, createdAt: 1760172800500, isTimeRange: false, startTime: null, endTime: null, hours: 2, hourlyRate: 40.25, totalEarnings: 80.5, isPaid: false, notes: 'חדשה', currency: '₪', isGroupShift: false, employerRate: null, workerRate: null, groupWorkersJson: '' }]));
    await until(async () => (await entries(B)).length === 3, 'new shift arrives');

    // 4. Signing out brings the screen back; the data stays in the account.
    await A.click(tid('account-signout')); await A.click(tid('confirm-ok'));
    await A.waitForSelector(tid('signin-google'));

    // 5. Another account on the same browser starts empty, and the first account's data is untouched.
    await signIn(A, crypto.randomUUID() + '@example.invalid', true);
    await A.waitForLoadState('load'); await until(async () => A.evaluate(() => !!window.Kis && !document.querySelector('[data-testid=signin]')), 'other account opens');
    assert.equal((await entries(A)).length, 0, 'another account sees nothing of the first');
    const raw = await A.evaluate(() => JSON.parse(localStorage.getItem('work_complete_backup')).entries.length);
    assert.equal(raw, 3, "the first account's copy in this browser is kept");
    assert.deepEqual(errors, []);
    console.log('account sync browser checks passed');
  } finally { await browser.close(); server.close(); }
})().catch(e => { console.error(e); process.exit(1); });
