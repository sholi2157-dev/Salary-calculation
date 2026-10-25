const {setDuration,durationValue}=require('./duration.cjs');
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
  // The site asks for its account settings first; none here (the static site without accounts).
  if (p === '/api/config') { r.writeHead(200, { 'content-type': 'application/json' }); return r.end('{}'); }
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
  // PUBLIC_SITE_URL checks the published site itself (each test still runs in its own empty browser profile).
  const base = process.env.PUBLIC_SITE_URL || `http://127.0.0.1:${server.address().port}/`;
  const browser = await chromium.launch(process.env.CHROMIUM_PATH ? { executablePath: process.env.CHROMIUM_PATH } : {});
  const errors = [];
  async function open(viewport = { width: 390, height: 844 }, colorScheme = 'light', data = seed, launched = true) {
    const ctx = await browser.newContext({ viewport, colorScheme, locale: 'he-IL', serviceWorkers: 'block' });
    await ctx.addInitScript(([d, l]) => { if (!sessionStorage.getItem('seeded')) { sessionStorage.setItem('seeded', '1'); localStorage.clear(); if (d) localStorage.setItem('work_complete_backup', JSON.stringify(d));
      if (l) localStorage.setItem('kis_web_v1', JSON.stringify({ launched: 2, tourDone: true })); } }, [data, launched]);
    const page = await ctx.newPage();
    page.on('pageerror', e => errors.push(e.message)); page.on('console', m => { if (m.type() === 'error') errors.push(m.text()); });
    await page.goto(base); await page.waitForSelector('[data-testid=home-hero]');
    return { ctx, page };
  }
  const shot = (page, name) => page.screenshot({ path: path.join(OUT, `v2-${name}.png`), fullPage: true });
  const stored = page => page.evaluate(() => JSON.parse(localStorage.getItem('work_complete_backup')));
  const tid = t => `[data-testid="${t}"]`;

  // Reproduced duration bug, centered dates and matched history controls.
  {
    const {ctx,page}=await open({width:360,height:800}, 'dark');
    await page.click(tid('add-shift'));
    const days=await page.locator('.days button').evaluateAll(es=>es.map(e=>({day:+e.dataset.day,text:e.textContent})));
    assert.equal(days.length,7);assert.match(days[3].text,/היום/);
    for(let i=0;i<7;i++) {const d=new Date(days[3].day);d.setDate(d.getDate()+i-3);assert.equal(days[i].day,+d);}
    assert.equal(await page.getByText('התחלה נפוצה:').count(),0);
    await page.click(tid('mode-hours'));await setDuration(page,'1.40');await page.fill(tid('rate-input'),'60');
    assert.match(await page.textContent(tid('editor-meta')),/1:40/);
    // The device's keyboard; a tap on one digit changes only that digit.
    assert.equal(await page.locator(tid('keypad')).count(),0,'no keypad of the site\'s own');
    await page.click(tid('hours-input-d2'));
    assert.equal(await page.getAttribute(tid('hours-input-d2'),'aria-pressed'),'true');
    await page.keyboard.type('5');
    assert.equal(await durationValue(page),'1:50');
    await page.click(tid('hours-input-d2'));await page.keyboard.type('4');
    assert.equal(await durationValue(page),'1:40');
    await page.click(tid('hours-input-d2'));await page.click('[data-q="4"]');await page.click(tid('entry-done'));
    assert.equal(await durationValue(page),'4:00', 'quick hours replace the value being typed');
    await setDuration(page,'1:40');
    assert.equal(await page.getByText('לחיצה על ספרה משנה רק אותה',{exact:true}).count(),0);

    assert.match(await page.textContent(tid('editor-total')),/100/);
    await shot(page,'manual-100-minutes-dark-360');await page.click(tid('save-shift'));
    const saved=(await stored(page)).entries.at(-1);assert.equal(saved.hours,100/60);assert.equal(saved.totalEarnings,100);
    await page.click(tid('tab-history'));
    assert.equal(await page.locator(tid('history-planned')).count(),0);
    const styles=await page.locator('[data-testid=history-search],[data-testid=history-copy],[data-testid=history-filters]').evaluateAll(es=>es.map(e=>{const c=getComputedStyle(e);return [c.backgroundColor,c.borderColor,c.borderRadius,c.height]}));
    assert.deepEqual(styles[0],styles[1]);assert.deepEqual(styles[0],styles[2]);
    await shot(page,'history-tools-dark-360');
    await page.click(tid('history-filters'));await page.click(tid('history-planned'));await shot(page,'planned-filter-dark-360');await page.click(tid('filters-apply'));
    assert.equal(await page.locator(tid('history-total')).count(),0);assert.ok(await page.isVisible(tid('history-filters')));
    await ctx.close();
  }

  // A metadata edit shows the actual persisted imported amount, matching Android.
  {
    const imported={...seed,entries:[{...seed.entries[1],hours:1.234567,totalEarnings:99.91}]};
    const {ctx,page}=await open(undefined,'light',imported);
    await page.click(tid('tab-history'));await page.click(tid('shift-b'));await page.click(tid('detail-edit'));
    assert.match(await page.textContent(tid('editor-total')),/99\.91/);
    await page.click(tid('hours-input-d2'));await page.click(tid('entry-done'));
    assert.ok(await page.isDisabled(tid('save-shift')), 'opening a rounded display does not change imported hours');
    await page.click(tid('editor-more'));await page.fill(tid('notes-input'),'הערה בלבד');
    assert.match(await page.textContent(tid('editor-total')),/99\.91/);
    await page.click(tid('save-shift'));const row=(await stored(page)).entries[0];assert.equal(row.hours,1.234567);assert.equal(row.totalEarnings,99.91);
    await ctx.close();
  }

  // 1. Existing data: shown, totals per currency, future shift not earned; nothing rewritten just by opening.
  {
    const { ctx, page } = await open();
    assert.equal(await page.evaluate(() => localStorage.getItem('work_complete_backup')), JSON.stringify(seed), 'opening never rewrites stored data');
    assert.match(await page.textContent(tid('home-all')), /₪600/); assert.match(await page.textContent(tid('home-all')), /\$180/);
    assert.ok(await page.isVisible(tid('next-f')), 'future shift waits under הבא בתור');
    // A planned shift's letter takes the place colour (it was white on the light page).
    const av = await page.$eval(`${tid('next-f')} .avatar`, el => ({ color: getComputedStyle(el).color, accent: getComputedStyle(el).getPropertyValue('--av').trim() }));
    assert.notEqual(av.color, 'rgb(255, 255, 255)', 'planned avatar letter is visible in light mode');
    await shot(page, 'home-light-390');
    await page.click(tid('tab-history'));
    assert.match(await page.textContent(tid('history-scope')), /כל התאריכים/);
    assert.match(await page.textContent(tid('history-total')), /₪600/);
    assert.equal(await page.locator('[data-testid="shift-f"]').count(), 0, 'future shift is not in "עבדתי"');
    await page.click(tid('history-filters')); await page.click(tid('history-planned')); await page.click(tid('filters-apply'));
    assert.ok(await page.isVisible(tid('planned-entry-f')));
    await page.click(tid('history-filters')); await page.click(tid('history-worked')); await page.click(tid('filters-apply'));
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

  // 2. Add a clock shift with the device's keyboard, unknown rate never 0, then complete the rate for it; edit keeps the stored amount.
  {
    const { ctx, page } = await open();
    await page.click(tid('add-shift')); await page.click(tid('clock-start'));
    assert.match(await page.textContent(tid('entry-label')), /שעת התחלה/);
    assert.equal(await page.locator(tid('save-shift')).count(), 0, 'the typing bar never saves');
    const endBefore = await page.textContent(tid('clock-end'));
    await page.keyboard.type('0900'); assert.equal(await page.textContent(tid('clock-start')), '09:00');
    // A full time stays put: one more digit changes only its last digit, never the end time.
    await page.keyboard.type('5'); assert.equal(await page.textContent(tid('clock-start')), '09:05');
    assert.equal(await page.textContent(tid('clock-end')), endBefore);
    await page.click(tid('clock-start-d3')); await page.keyboard.type('0'); assert.equal(await page.textContent(tid('clock-start')), '09:00');
    await page.click(tid('entry-next')); assert.match(await page.textContent(tid('entry-label')), /שעת סיום/);
    await page.keyboard.type('1730');
    assert.match(await page.textContent(tid('editor-meta')), /8:30 שעות/);
    assert.match(await page.textContent(tid('editor-total')), /₪425/);
    await shot(page, 'editor-typing-light-390');
    await page.click(tid('entry-done'));
    await page.click(tid('nudge-end-15')); assert.match(await page.textContent(tid('editor-total')), /₪437\.50/);
    // Each time has its own ±15.
    await page.click(tid('nudge-start--15')); assert.equal(await page.textContent(tid('clock-start')), '08:45');
    await page.click(tid('nudge-start-15')); assert.equal(await page.textContent(tid('clock-start')), '09:00');
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

  // 2b. A new shift shows both times, 4 hours apart; one digit changes in place (05:30 → 05:35), then the end.
  {
    const { ctx, page } = await open();
    await page.click(tid('add-shift'));
    const t = w => page.textContent(tid('clock-' + w));
    const min = s => +s.slice(0, 2) * 60 + +s.slice(3);
    assert.equal((min(await t('end')) - min(await t('start')) + 1440) % 1440, 240, 'start is 4 hours before the end');
    await page.click(tid('clock-start'));
    await page.keyboard.type('0530');
    assert.equal(await t('start'), '05:30');
    assert.match(await page.textContent(tid('entry-label')), /שעת התחלה/, 'a full start time does not jump to the end');
    await page.click(tid('clock-start-d3'));
    assert.equal(await page.getAttribute(tid('clock-start-d3'), 'aria-pressed'), 'true');
    await page.keyboard.type('5');
    assert.equal(await t('start'), '05:35');
    await page.click(tid('clock-end-d0')); assert.match(await page.textContent(tid('entry-label')), /שעת סיום/);
    await page.keyboard.type('10');
    // An hour that cannot be (2 then 5) is refused; the digit stays as it was.
    await page.click(tid('clock-end-d0')); await page.keyboard.type('25'); assert.equal((await t('end')).slice(0, 2), '20');
    await page.click(tid('clock-end-d0')); await page.keyboard.type('10');
    await page.keyboard.press('Enter');
    assert.equal(await t('end').then(v => v.slice(0, 2)), '10');
    await shot(page, 'editor-digits-390');
    await ctx.close();
  }

  // 3. A weekly plan that started 2 days ago: days already ended are recorded once (also after a reload), shown as "אוטו׳".
  {
    const { ctx, page } = await open({ width: 390, height: 844 }, 'dark');
    await page.click(tid('add-shift')); await page.click(tid('clock-start'));
    await page.keyboard.type('0600'); await page.keyboard.press('Enter'); await page.keyboard.type('0630'); await page.keyboard.press('Enter');
    await page.locator('[data-day]').nth(1).click(); // today is index 3; two days ago is index 1
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
    // With the place's rate the card counts the money earned so far, to the agora; the clock sits below it.
    assert.match(await page.textContent(tid('live-money')), /^[₪$][\d,]+\.\d\d$/);
    assert.match(await page.textContent(tid('live-clock')), /^\d\d:\d\d:\d\d$/);
    await shot(page, 'live-money-390');
    await page.click(tid('add-shift')); await page.click(tid('mode-hours')); await setDuration(page,'7');
    await page.reload(); await page.waitForSelector(tid('add-page'));
    assert.equal(await durationValue(page), '7:00', 'draft restored');
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
    // Duration is typed like the clock; typing a rate still saves on the first tap.
    await page.click(tid('add-shift')); await page.click(tid('mode-hours'));
    await setDuration(page, '6'); await page.fill(tid('rate-input'), '45');
    const before = (await stored(page)).entries.length;
    await page.click(tid('save-shift'));
    assert.equal((await stored(page)).entries.length, before + 1, 'first tap saves');
    await ctx.close();
  }

  // 8. The 2.0 launch (once): words, then first and last name, then the tour from Home. Bell lists what arrived;
  //    notification switches and the profile are in Settings; explanations only behind ⓘ.
  {
    const { ctx, page } = await open(undefined, 'light', seed, false);
    await page.waitForSelector(tid('launch-line-2')); await page.waitForTimeout(1500);
    assert.equal(await page.evaluate(() => localStorage.getItem('work_complete_backup')), JSON.stringify(seed), 'the launch never touches stored data');
    await shot(page, 'launch');
    await page.click(tid('launch-next'));
    assert.ok(await page.isDisabled(tid('launch-done')), 'a first name is needed to continue (or "later")');
    await page.fill(tid('launch-first'), 'שולי'); await page.fill(tid('launch-last'), 'כהן');
    await shot(page, 'launch-name'); await page.click(tid('launch-done'));
    assert.equal(await page.textContent(tid('tour-progress')), '1 מתוך 6');
    for (let i = 1; i < 6; i++) { await page.click(tid('tour-next')); assert.equal(await page.textContent(tid('tour-progress')), `${i + 1} מתוך 6`); if (i === 3) await shot(page, 'tour-history'); }
    await page.click(tid('tour-next'));
    assert.equal(await page.locator(tid('tour')).count(), 0);
    assert.match(await page.textContent(tid('home-greeting')), /שולי/);
    assert.doesNotMatch(await page.textContent(tid('home-greeting')), /כהן/, 'only the first name is shown');
    await page.reload(); await page.waitForSelector(tid('home-hero'));
    assert.equal(await page.locator(tid('launch')).count(), 0, 'the launch shows once');
    // Bell: only what arrived, with a way to its settings. A recap from before the first one (September) never shows.
    await page.evaluate(() => localStorage.setItem('kis_web_recent_v1', JSON.stringify([{ kind: 'rate', title: 'משמרת אחת מחכה לתעריף', text: 'אפשר להשלים', time: Date.now(), month: null },
      { kind: 'monthly', title: 'הסיכום מוכן', text: '3 משמרות', time: Date.now(), month: 2026 * 12 + 8 }])));
    await page.reload(); await page.waitForSelector(tid('home-hero'));
    assert.ok(await page.isVisible(tid('home-bell-dot')), 'something new is marked on the bell');
    await page.click(tid('home-bell')); assert.ok(await page.isVisible(tid('recent-note-0')));
    assert.equal(await page.locator(tid('recent-note-1')).count(), 0, 'no September recap');
    assert.equal(await page.locator(`${tid('notifications-sheet')} [role=switch]`).count(), 0, 'no switches in the bell');
    await shot(page, 'notifications'); await page.click(tid('notifications-settings'));
    assert.equal(await page.locator(tid('home-bell-dot')).count(), 0, 'opened means seen');
    // Settings: notifications group with switches, profile last with both names.
    assert.ok(await page.isVisible(tid('settings-התראות')));
    await page.click(tid('notify-monthly')); assert.equal(await page.getAttribute(tid('notify-monthly'), 'aria-checked'), 'true');
    assert.equal(await page.inputValue(tid('profile-name')), 'שולי'); assert.equal(await page.inputValue(tid('profile-last-name')), 'כהן');
    const titles = await page.$$eval('.section-title', n => n.map(x => x.textContent));
    assert.equal(titles[titles.length - 1], 'פרופיל', 'the profile is last');
    assert.equal(await page.locator('.caption:visible').filter({ hasText: 'ובדפדפן הזה' }).count(), 0, 'no explanation text on the page');
    await page.click('[data-info-title="גיבוי ונתונים"]');
    assert.match(await page.textContent(tid('info-sheet')), /נשמרים בו ובדפדפן הזה/);
    await page.keyboard.press('Escape');
    await page.click(tid('replay-tour')); assert.equal(await page.textContent(tid('tour-progress')), '1 מתוך 6'); await page.click(tid('tour-skip'));
    await ctx.close();
  }

  // 9. "החודש שלך" and the story recap: insights, amounts hidden until asked, a card without amounts, year too.
  // Before the 30th there is no story at all (no spoilers); from the 30th it is on Home, then in Settings.
  {
    const { ctx, page } = await open();
    const real = new Date(); const recapDay = Math.min(30, new Date(real.getFullYear(), real.getMonth() + 1, 0).getDate());
    if (real.getDate() < recapDay) {
      await page.click(tid('home-month-summary'));
      assert.equal(await page.locator(tid('month-story')).count(), 0, 'no story before the 30th');
      assert.equal(await page.locator('[data-testid^=home-recap-]').count(), 0);
      await page.keyboard.press('Escape');
      await page.clock.install({ time: new Date(real.getFullYear(), real.getMonth(), recapDay, 12) });
      await page.reload(); await page.waitForSelector(tid('home-hero'));
    }
    const scope = await page.evaluate(() => KisSummary.recapNew(KisPlan.dateOf(Date.now())).month);
    assert.ok(await page.isVisible(tid('home-recap-' + scope)), 'the new recap is on Home');
    await page.click(tid('tab-settings')); await page.click(tid('recaps-button'));
    assert.ok(await page.isVisible(tid('recap-' + scope)), 'and in the archive');
    await page.keyboard.press('Escape'); await page.click(tid('tab-home'));
    await page.click(tid('home-month-summary'));
    assert.ok(await page.isVisible(tid('month-insight-0')));
    assert.match(await page.textContent(tid('month-rate-₪')), /₪\d/);
    await shot(page, 'month-summary');
    await page.click(tid('month-story'));
    await page.waitForSelector(tid('story-intro'));
    await page.waitForTimeout(700); await page.screenshot({ path: path.join(OUT, 'v2-story-intro.png') });
    for (let n = 0; n < 10 && !(await page.locator(tid('story-money')).count()); n++) { await page.click(tid('story-next')); if (await page.locator(tid('story-persona')).count()) await page.screenshot({ path: path.join(OUT, 'v2-story-persona.png') }); }
    assert.ok(await page.locator(tid('story-money-hidden')).count() >= 1, 'the amount waits behind "הצג לי"');
    assert.doesNotMatch(await page.textContent(tid('story-money')), /\d/);
    await page.click(tid('story-reveal')); assert.match(await page.textContent(tid('story-money')), /₪\d/);
    await page.click(tid('story-next')); await page.waitForSelector(tid('story-card'));
    assert.match(await page.textContent(tid('story-poster')), /₪/, 'the shared card includes amounts');
    assert.equal(await page.locator(tid('story-share')).count(), 0, 'unsupported file sharing has no image button');
    assert.equal(await page.locator(tid('story-copy')).count(), 1);
    await page.waitForTimeout(700); await page.screenshot({ path: path.join(OUT, 'v2-story-card.png') });
    await page.keyboard.press('Escape'); assert.equal(await page.locator(tid('story')).count(), 0);
    // It moves on by itself.
    // The year's story opens with December's; it is not offered before that.
    if (!scope.endsWith('-12')) { await page.click(tid('home-month-summary')); assert.equal(await page.locator(tid('year-story')).count(), 0); await page.keyboard.press('Escape'); }
    await page.evaluate(() => KisSummaryUi.story(Kis, String(new Date().getFullYear())));
    await page.waitForSelector(tid('story-intro')); await page.waitForSelector(tid('story-hours'), { timeout: 9000 });
    await page.click(tid('story-close'));
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
