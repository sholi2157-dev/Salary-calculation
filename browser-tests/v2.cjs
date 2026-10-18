/*
 * 2.0 web ("כיס") in a real browser, on the built public/ folder, with synthetic data only.
 * Proves: existing stored data is shown unchanged; add/edit; future shifts are planned and never summed; the one
 * filter/sort panel; weekly plans recorded once; an Android 2.0 backup (v3) imports; the live shift and a typed draft
 * survive a reload; light/dark at 360/390/768/1440 without horizontal scrolling or page errors.
 */
const assert = require('node:assert/strict'), http = require('node:http'), fs = require('node:fs'), path = require('node:path');
const { chromium } = require(process.env.PLAYWRIGHT_MODULE || 'playwright');
const ROOT = path.resolve('public'), OUT = process.env.SCREENSHOT_DIR || 'browser-evidence';
fs.mkdirSync(OUT, { recursive: true });
const types = { '.html': 'text/html; charset=utf-8', '.js': 'text/javascript', '.css': 'text/css', '.json': 'application/json', '.ttf': 'font/ttf' };
const server = http.createServer((q, r) => {
  let p = decodeURIComponent(new URL(q.url, 'http://x').pathname); if (p === '/') p = '/index.html';
  const f = path.join(ROOT, p); if (!f.startsWith(ROOT) || !fs.existsSync(f) || fs.statSync(f).isDirectory()) { r.writeHead(404); return r.end(); }
  r.writeHead(200, { 'content-type': types[path.extname(f)] || 'application/octet-stream', 'cache-control': 'no-store' }); fs.createReadStream(f).pipe(r);
});
const day = (d, h = 17) => { const t = new Date(); t.setDate(t.getDate() - d); t.setHours(h, 0, 0, 0); return t.getTime(); };
const legacyRow = (id, category, date, hours, rate, currency = '₪', extra = {}) => ({ id, category, date, createdAt: date, isTimeRange: false, startTime: null, endTime: null, hours,
  hourlyRate: rate, totalEarnings: Math.round(hours * rate * 100) / 100, isPaid: false, notes: '', currency, isGroupShift: false, employerRate: null, workerRate: null, groupWorkersJson: '', ...extra });
const seed = {
  entries: [legacyRow('a', 'מסעדה', day(0), 6, 50, '₪', { isTimeRange: true, startTime: '11:00', endTime: '17:00' }), legacyRow('b', 'שיעורים', day(2), 2.5, 120),
    legacyRow('c', 'הובלות', day(40), 3, 60, '$'), legacyRow('f', 'מסעדה', day(-3, 12), 5, 50)],
  categories: [{ name: 'מסעדה', defaultRate: 50 }, { name: 'שיעורים', defaultRate: 120 }, { name: 'הובלות', defaultRate: 60 }], workers: [],
  webPreferences: { mainCurrency: '₪', defaultCategory: 'מסעדה', categoryCurrencies: { 'הובלות': '$' } },
};

(async () => {
  await new Promise(r => server.listen(0, r));
  const base = `http://127.0.0.1:${server.address().port}/`;
  const browser = await chromium.launch(process.env.CHROMIUM_PATH ? { executablePath: process.env.CHROMIUM_PATH } : {});
  const errors = [];
  async function open(viewport = { width: 390, height: 844 }, colorScheme = 'light', data = seed) {
    const ctx = await browser.newContext({ viewport, colorScheme, locale: 'he-IL', serviceWorkers: 'block' });
    await ctx.addInitScript(d => { if (!sessionStorage.getItem('seeded')) { sessionStorage.setItem('seeded', '1'); localStorage.clear(); if (d) localStorage.setItem('work_complete_backup', JSON.stringify(d)); } }, data);
    const page = await ctx.newPage();
    page.on('pageerror', e => errors.push(e.message)); page.on('console', m => { if (m.type() === 'error') errors.push(m.text()); });
    await page.goto(base); await page.waitForSelector('[data-testid=home-hero]');
    return { ctx, page };
  }
  const shot = (page, name) => page.screenshot({ path: path.join(OUT, `v2-${name}.png`), fullPage: true });
  const stored = page => page.evaluate(() => JSON.parse(localStorage.getItem('work_complete_backup')));
  const tid = t => `[data-testid="${t}"]`;

  // 1. Existing data: shown, totals per currency, future shift not earned; nothing rewritten just by opening.
  {
    const { ctx, page } = await open();
    assert.equal(await page.evaluate(() => localStorage.getItem('work_complete_backup')), JSON.stringify(seed), 'opening never rewrites stored data');
    assert.match(await page.textContent(tid('home-all')), /₪600/); assert.match(await page.textContent(tid('home-all')), /\$180/);
    assert.ok(await page.isVisible(tid('next-f')), 'future shift waits under הבא בתור');
    await shot(page, 'home-light-390');
    await page.click(tid('tab-history'));
    assert.match(await page.textContent(tid('history-scope')), /כל התאריכים/);
    assert.match(await page.textContent(tid('history-total')), /₪600/);
    assert.equal(await page.locator('[data-testid="shift-f"]').count(), 0, 'future shift is not in "עבדתי"');
    await page.click(tid('history-planned'));
    assert.ok(await page.isVisible(tid('planned-entry-f')));
    await page.click(tid('history-worked'));
    // One panel: a month, with the count it will show.
    await page.click(tid('history-filters')); await page.click(tid('period-MONTH'));
    assert.match(await page.textContent(tid('filters-apply')), /הצגת/); await shot(page, 'filters-panel');
    await page.click(tid('sort-OLDEST')); await page.click(tid('filters-apply'));
    assert.match(await page.textContent(tid('history-scope')), /מהישן לחדש/);
    await page.click(tid('history-reset'));
    assert.match(await page.textContent(tid('history-scope')), /כל התאריכים · מהחדש לישן/);
    await shot(page, 'history-light-390');
    await ctx.close();
  }

  // 2. Add a clock shift with the keypad, unknown rate never 0, then complete the rate for it; edit keeps the stored amount.
  {
    const { ctx, page } = await open();
    await page.click(tid('add-shift')); await page.click(tid('clock-start'));
    for (const k of '0900') await page.click(tid('key-' + k));
    for (const k of '1730') await page.click(tid('key-' + k));
    assert.match(await page.textContent(tid('editor-meta')), /8:30 שעות/);
    assert.match(await page.textContent(tid('editor-total')), /₪425/);
    await page.click(tid('nudge-15')); assert.match(await page.textContent(tid('editor-total')), /₪437\.50/);
    await page.click(tid('rate-unknown'));
    assert.match(await page.textContent(tid('editor-meta')), /תעריף חסר/);
    await shot(page, 'editor-light-390');
    await page.click(tid('save-shift'));
    let rows = (await stored(page)).entries; const added = rows.find(e => e.startTime === '09:00');
    assert.ok(added && added.payKnown === false && added.totalEarnings === 0 && added.hours === 8.75 && added.endTime === '17:45');
    await page.click(tid('pending-flag'));
    await page.fill(tid('bulk-rate-input'), '40'); await page.click(tid('bulk-rate-apply'));
    rows = (await stored(page)).entries; assert.equal(rows.find(e => e.id === added.id).totalEarnings, 350);
    // Edit only the note: the stored amount of an old row stays exactly as it was.
    await page.click(tid('tab-history')); await page.click(tid('shift-b')); await page.click(tid('detail-edit'));
    await page.click(tid('editor-more')); await page.fill(tid('notes-input'), 'הערה'); await page.click(tid('save-shift'));
    const b = (await stored(page)).entries.find(e => e.id === 'b'); assert.equal(b.totalEarnings, 300); assert.equal(b.notes, 'הערה'); assert.equal(b.hours, 2.5);
    await ctx.close();
  }

  // 3. A weekly plan that started 2 days ago: days already ended are recorded once (also after a reload), shown as "אוטו׳".
  {
    const { ctx, page } = await open({ width: 390, height: 844 }, 'dark');
    await page.click(tid('add-shift')); await page.click(tid('clock-start'));
    for (const k of '0600') await page.click(tid('key-' + k)); for (const k of '0630') await page.click(tid('key-' + k));
    await page.locator('[data-day]').nth(4).click(); // two days ago
    await page.click(tid('repeat-switch'));
    for (let i = 0; i < 7; i++) { const b = page.locator(tid('repeat-day-' + i)); if ((await b.getAttribute('aria-pressed')) !== 'true') await b.click(); }
    assert.match(await page.textContent(tid('repeat-preview')), /ייכנסו ללוח/);
    await shot(page, 'editor-repeat-dark');
    await page.click(tid('save-shift'));
    const keys = rows => rows.filter(e => e.seriesKey).map(e => e.seriesKey);
    const first = keys((await stored(page)).entries);
    assert.ok(first.length >= 2, 'ended plan days were recorded');
    await page.reload(); await page.waitForSelector(tid('home-hero'));
    const second = keys((await stored(page)).entries);
    assert.deepEqual(second.sort(), first.sort(), 'never recorded twice');
    assert.equal(new Set(second).size, second.length);
    await shot(page, 'home-dark-plan');
    await ctx.close();
  }

  // 4. An Android 2.0 backup (format 3, job + unknown rate + bonus + plan) imports, and the site's backup carries it back.
  {
    const { ctx, page } = await open(undefined, 'light', null);
    const android = { formatVersion: 3, androidLocalPreferences: { default_currency: '₪' }, categories: [{ name: 'מסעדה', defaultRate: 50 }], workers: [],
      entries: [legacyRow('x', 'מסעדה', day(1), 4, 50, '₪', { kind: 'shift', payKnown: true, bonus: 20, seriesKey: null, autoConfirmed: false, totalEarnings: 220 }),
        legacyRow('y', 'מסעדה', day(1), 0, 0, '₪', { kind: 'job', payKnown: false, totalEarnings: 0, notes: 'הובלה' })].map(({ id, ...e }) => e),
      series: [{ id: 'S1', category: 'מסעדה', daysMask: 2, startTime: '09:00', endTime: '17:00', hourlyRate: 50, currency: '₪', fromDate: '2030-01-01', untilDate: '2030-03-01', createdAt: 0 }],
      seriesExceptions: [] };
    await page.click(tid('tab-settings')); await page.click(tid('import-button'));
    await page.fill(tid('import-text'), JSON.stringify(android)); await page.click(tid('import-check'));
    assert.match(await page.textContent(tid('import-summary')), /חדשות: 2/);
    await page.click(tid('import-save'));
    const d = await stored(page); assert.equal(d.entries.length, 2);
    assert.ok(d.entries.some(e => e.kind === 'job' && e.payKnown === false) && d.entries.some(e => e.bonus === 20 && e.totalEarnings === 220));
    const back = await page.evaluate(() => window.KisSettings.backupText(window.Kis.store));
    const parsed = JSON.parse(back); assert.equal(parsed.formatVersion, 3); assert.equal(parsed.series[0].id, 'S1');
    await ctx.close();
  }

  // 5. Live shift and a typed new shift both survive a reload.
  {
    const { ctx, page } = await open();
    await page.click(tid('live-start')); await page.click(tid('live-go'));
    assert.ok(await page.isVisible(tid('live-card')));
    await page.click(tid('add-shift')); await page.click(tid('mode-hours')); await page.fill(tid('hours-input'), '7');
    await page.reload(); await page.waitForSelector(tid('add-page'));
    assert.equal(await page.inputValue(tid('hours-input')), '7', 'draft restored');
    await page.click(tid('editor-close')); await page.click(tid('confirm-ok'));
    assert.ok(await page.isVisible(tid('live-card')), 'timer survived the reload');
    await ctx.close();
  }

  // 7. Review fixes: selection bar is on top and the navigation returns; an early numeric id opens; no lost taps or focus.
  {
    const old = { ...seed, entries: [...seed.entries, legacyRow(1700000000000, 'הובלות', day(5), 2, 60, '$', { payKnown: false, totalEarnings: 0, hourlyRate: 0 }),
      legacyRow('p', 'מסעדה', day(6), 4, 0, '₪', { payKnown: false, totalEarnings: 0 })] };
    const { ctx, page } = await open({ width: 360, height: 740 }, 'light', old);
    await page.click(tid('tab-history'));
    await page.click(tid('shift-b'), { button: 'right' });
    const onTop = await page.evaluate(() => { const b = document.querySelector('[data-s=paid]').getBoundingClientRect();
      return document.elementFromPoint(b.left + b.width / 2, b.top + b.height / 2)?.closest('[data-testid=selection-bar]') != null; });
    assert.ok(onTop, 'selection actions are not covered by the navigation');
    await page.click('[data-s=paid]');
    assert.ok(await page.isVisible('.nav-wrap'), 'navigation is back after the selection');
    await page.click(tid('shift-1700000000000')); assert.ok(await page.isVisible(tid('detail-paid')), 'an early numeric id opens');
    await page.click(tid('detail-paid'));
    assert.equal((await stored(page)).entries.find(e => String(e.id) === '1700000000000').isPaid, true);
    // Rate completion never adds ₪ and $ together.
    await page.click(tid('shift-p'), { button: 'right' }); await page.click(tid('shift-1700000000000'));
    await page.click(tid('selection-rate')); await page.fill(tid('bulk-rate-input'), '10');
    const preview = await page.textContent('[data-testid=rate-sheet] [data-f=p]');
    assert.match(preview, /₪40/); assert.match(preview, /\$20/); await page.keyboard.press('Escape');
    await page.click('[data-s=cancel]'); assert.ok(await page.isVisible('.nav-wrap'), 'navigation returns when the selection is cancelled');
    // Typing a number keeps the field; a tap on save right after typing is not lost.
    await page.click(tid('add-shift')); await page.click(tid('mode-hours'));
    await page.fill(tid('hours-input'), ''); await page.type(tid('hours-input'), '85');
    assert.equal(await page.inputValue(tid('hours-input')), '85');
    assert.equal(await page.evaluate(() => document.activeElement?.dataset.f), 'hours');
    await page.fill(tid('hours-input'), '6'); await page.fill(tid('rate-input'), '45');
    const before = (await stored(page)).entries.length;
    await page.click(tid('save-shift'));
    assert.equal((await stored(page)).entries.length, before + 1, 'first tap saves');
    await ctx.close();
  }

  // 8. Home's bell and profile; Settings holds neither; explanations only behind ⓘ.
  {
    const { ctx, page } = await open();
    await page.evaluate(() => localStorage.setItem('kis_web_recent_v1', JSON.stringify([{ kind: 'monthly', title: 'הסיכום מוכן', text: '3 משמרות', time: Date.now(), month: 2026 * 12 + 8 }])));
    await page.reload(); await page.waitForSelector(tid('home-hero'));
    assert.ok(await page.isVisible(tid('home-bell-dot')), 'something new is marked on the bell');
    await page.click(tid('home-bell')); assert.ok(await page.isVisible(tid('recent-note-0')));
    await page.click(tid('notify-monthly'));
    assert.equal(await page.getAttribute(tid('notify-monthly'), 'aria-checked'), 'true');
    await shot(page, 'notifications'); await page.keyboard.press('Escape');
    assert.equal(await page.locator(tid('home-bell-dot')).count(), 0, 'opened means seen');
    await page.click(tid('home-profile')); await page.fill(tid('profile-name'), 'שולי'); await page.click(tid('profile-save'));
    assert.match(await page.textContent(tid('home-greeting')), /שולי/);
    await page.click(tid('home-profile')); assert.equal(await page.textContent(tid('profile-shown')), 'שולי');
    assert.equal(await page.locator(tid('profile-name')).count(), 0, 'the name is shown, not asked again');
    await page.click(tid('profile-edit')); assert.ok(await page.isVisible(tid('profile-name')));
    await shot(page, 'profile'); await page.keyboard.press('Escape');
    await page.click(tid('tab-settings'));
    assert.equal(await page.locator(`${tid('settings-פרופיל')}, ${tid('settings-התראות')}`).count(), 0);
    assert.equal(await page.locator('.caption:visible').filter({ hasText: 'נשמרים בדפדפן' }).count(), 0, 'no explanation text on the page');
    await page.click('[data-info-title="גיבוי ונתונים"]');
    assert.match(await page.textContent(tid('info-sheet')), /נשמרים בדפדפן/);
    await shot(page, 'settings-info'); await page.keyboard.press('Escape');
    await ctx.close();
  }

  // 6. Layout: every width, both themes, no horizontal scrolling.
  for (const [w, h] of [[360, 760], [390, 844], [768, 1024], [1440, 900]]) for (const scheme of ['light', 'dark']) {
    const { ctx, page } = await open({ width: w, height: h }, scheme);
    for (const tab of ['home', 'history', 'settings']) {
      await page.click(tid('tab-' + tab));
      const over = await page.evaluate(() => document.documentElement.scrollWidth - document.documentElement.clientWidth);
      assert.ok(over <= 1, `${tab} ${w}px ${scheme}: no horizontal scroll (${over})`);
      if (w === 360 || w === 1440) await shot(page, `${tab}-${scheme}-${w}`);
    }
    await page.click(tid('add-shift'));
    assert.ok(await page.isVisible(tid('save-shift')), `${w}px: save stays visible`);
    await ctx.close();
  }

  assert.deepEqual(errors, [], 'no page errors');
  await browser.close(); server.close();
  console.log('v2 web browser flows: OK');
})().catch(e => { console.error(e); process.exit(1); });
