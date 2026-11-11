// F12 (2.3.1): on the site with Google accounts, the tour opens once on a new account's first connect (after the sign-in
// screen and the launch), comes back after a reload only while unfinished, and never for an account that already existed.
// Google's code is replaced by a stand-in; nothing reaches the network.
const assert = require('node:assert/strict'), http = require('node:http'), fs = require('node:fs'), path = require('node:path');
const { chromium } = require(process.env.PLAYWRIGHT_MODULE || 'playwright');
const ROOT = path.join(__dirname, '..', 'public');
const types = { '.html': 'text/html', '.js': 'text/javascript', '.css': 'text/css', '.json': 'application/json', '.svg': 'image/svg+xml', '.png': 'image/png', '.woff2': 'font/woff2' };
const server = http.createServer((q, r) => {
  const u = decodeURIComponent(new URL(q.url, 'http://x').pathname);
  if (u === '/api/config') { r.writeHead(200, { 'content-type': 'application/json' }); return r.end('{"firebase":{"apiKey":"x"}}'); }
  let f = path.join(ROOT, u === '/' ? 'index.html' : u);
  if (!f.startsWith(ROOT) || !fs.existsSync(f) || fs.statSync(f).isDirectory()) f = path.join(ROOT, 'index.html');
  r.writeHead(200, { 'content-type': types[path.extname(f)] || 'application/octet-stream' }); fs.createReadStream(f).pipe(r);
});
const tid = t => `[data-testid="${t}"]`;
(async () => {
  await new Promise(r => server.listen(0, r));
  const base = `http://127.0.0.1:${server.address().port}/`;
  const browser = await chromium.launch(process.env.CHROMIUM_PATH ? { executablePath: process.env.CHROMIUM_PATH } : {});
  const errors = [];
  async function open(created, last, seen, isNewUser = null) {
    const ctx = await browser.newContext({ viewport: { width: 390, height: 844 }, locale: 'he-IL', serviceWorkers: 'block' });
    await ctx.route('https://www.gstatic.com/**', r => r.fulfill({ status: 200, contentType: 'text/javascript', body: '' }));
    await ctx.addInitScript(([created, last, seen, isNewUser]) => {
      if (!sessionStorage.getItem('seeded')) { sessionStorage.setItem('seeded', '1'); localStorage.clear();
        if (seen) { localStorage.setItem('kis_web_v1', JSON.stringify({ launched: 2 })); localStorage.setItem('kis_ai_intro_v1', '1'); } localStorage.setItem('kis_ai_invited_v1', '1'); }
      const user = { uid: 'u1', email: 'a@b.c', displayName: 'שולי', metadata: { creationTime: new Date(created).toUTCString(), lastSignInTime: new Date(last).toUTCString() } };
      let listener = null, signed = sessionStorage.getItem('signed') === '1';
      const auth = () => ({ currentUser: signed ? user : null, onAuthStateChanged: f => { listener = f; setTimeout(() => f(signed ? user : null), 0); },
        // Like Firebase: the session appears first, the sign-in's own result (isNewUser) a moment later.
        signInWithPopup: async () => { signed = true; sessionStorage.setItem('signed', '1'); setTimeout(() => listener?.(user), 0); await new Promise(r => setTimeout(r, 300));
          return { user, additionalUserInfo: isNewUser === null ? null : { isNewUser } }; },
        signOut: async () => { signed = false; sessionStorage.removeItem('signed'); listener?.(null); } });
      auth.GoogleAuthProvider = function () { this.setCustomParameters = () => {}; };
      const doc = () => ({ collection: () => ({ doc, get: async () => ({ docs: [] }), where: () => ({ get: async () => ({ docs: [] }) }) }), get: async () => ({ exists: false, data: () => ({}) }), set: async () => {}, onSnapshot: () => () => {} });
      window.firebase = { apps: [1], initializeApp() {}, auth, firestore: Object.assign(() => ({ collection: () => ({ doc }), runTransaction: async f => f({ get: async () => ({ exists: false }), set() {} }), batch: () => ({ set() {}, commit: async () => {} }) }), { FieldValue: { serverTimestamp: () => 0 } }) };
    }, [created, last, seen, isNewUser]);
    const page = await ctx.newPage();
    page.on('pageerror', e => errors.push(e.message));
    await page.goto(base);
    return { ctx, page };
  }
  const signIn = async page => {
    await page.waitForSelector(tid('signin'));
    for (const t of ['intro-next', 'intro-next-2']) if (await page.locator(tid(t)).count()) await page.click(tid(t));
    await page.click('[data-a=google]');
  };
  // 1. A brand-new account in a browser that already had the site: the tour right after connecting; a reload mid-way brings it back; once finished, never again.
  {
    const now = Date.now(), { ctx, page } = await open(now, now + 800, true, true);
    await signIn(page);
    await page.waitForSelector(tid('tour-progress'));
    assert.match(await page.textContent(tid('tour-progress')), /^1 מתוך 7/);
    await page.reload(); await page.waitForSelector(tid('tour-progress'));
    for (let i = 0; i < 7; i++) await page.click(tid('tour-next'));
    await page.waitForSelector(tid('tour'), { state: 'detached' });
    await page.reload(); await page.waitForSelector(tid('home-hero')); await page.waitForTimeout(800);
    assert.equal(await page.locator(tid('tour')).count(), 0, 'finished: never again by itself');
    // Log out and connect again a minute later (still inside the creation window): no tour.
    await page.evaluate(() => firebase.auth().signOut());
    await signIn(page); await page.waitForSelector(tid('home-hero')); await page.waitForTimeout(800);
    assert.equal(await page.locator(tid('tour')).count(), 0, 'reconnect: never again');
    // Replay from settings, any time.
    await page.click(tid('tab-settings')); await page.click(tid('settings-open-עזרה'));
    await page.click(tid('replay-tour')); await page.waitForSelector(tid('tour-progress'));
    assert.match(await page.textContent(tid('tour-progress')), /^1 מתוך 7/);
    await page.click(tid('tour-skip'));
    await ctx.close();
  }
  // 2. A brand-new account on a first visit: the sign-in, then the launch, then the tour.
  {
    const now = Date.now(), { ctx, page } = await open(now, now, false);
    await signIn(page);
    await page.waitForSelector(tid('launch-next')); assert.equal(await page.locator(tid('tour')).count(), 0, 'not over the launch');
    await page.click(tid('launch-next')); await page.click(tid('launch-later'));
    await page.waitForSelector(tid('tour-progress'));
    await page.click(tid('tour-skip')); await page.reload(); await page.waitForSelector(tid('home-hero')); await page.waitForTimeout(800);
    assert.equal(await page.locator(tid('tour')).count(), 0, 'skipped: never again by itself');
    await ctx.close();
  }
  // 3. An account made long ago (an update, another browser, the app): no tour, even on its first sign-in here.
  {
    const { ctx, page } = await open(Date.parse('2026-01-01'), Date.now(), true);
    await signIn(page);
    await page.waitForSelector(tid('home-hero')); await page.waitForTimeout(1200);
    assert.equal(await page.locator(tid('tour')).count(), 0);
    await ctx.close();
  }
  // 4. An account made on another device 40 s ago, first sign-in here: inside the window, but Firebase says it existed.
  {
    const now = Date.now(), { ctx, page } = await open(now - 40000, now, true, false);
    await signIn(page);
    await page.waitForSelector(tid('home-hero')); await page.waitForTimeout(1200);
    assert.equal(await page.locator(tid('tour')).count(), 0, 'second device: no tour');
    await page.reload(); await page.waitForSelector(tid('home-hero')); await page.waitForTimeout(800);
    assert.equal(await page.locator(tid('tour')).count(), 0, 'second device: decided once');
    await ctx.close();
  }
  // Account sync runs against the stand-in too and fails there; only errors of the page itself count.
  assert.deepEqual(errors.filter(m => !/firebase\./.test(m)), []);
  await browser.close(); server.close();
  console.log('PASS: tour once per new account (after sign-in and launch, pending until finished, never on reconnect, another device or an existing account; replay from settings)');
})().catch(e => { console.error(e); process.exit(1); });
