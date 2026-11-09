/*
 * 2.3.0 Milestone 2 in a real browser (synthetic data): values are never changed silently (audit M-06/M-09, U-06/U-07).
 * An impossible hour is announced, an overnight end is labelled, a shift over 16 hours or a very large amount asks first,
 * a negative rate shows an error and no total, and the live timer reads "40,5" as 40.5 and refuses "-5" with a message.
 */
const assert = require('node:assert/strict'), http = require('node:http'), fs = require('node:fs'), path = require('node:path');
const { chromium } = require(process.env.PLAYWRIGHT_MODULE || 'playwright');
const ROOT = path.resolve('public'), rich = JSON.parse(fs.readFileSync('web-tests/fixtures/synthetic/rich-v2.json', 'utf8'));
const types = { '.html': 'text/html; charset=utf-8', '.js': 'text/javascript', '.css': 'text/css', '.json': 'application/json', '.ttf': 'font/ttf' };
const server = http.createServer((q, r) => { let p = decodeURIComponent(new URL(q.url, 'http://x').pathname); if (p === '/') p = '/index.html';
  if (p === '/api/config') { r.writeHead(200, { 'content-type': 'application/json' }); return r.end('{}'); }
  const f = path.join(ROOT, p); if (!f.startsWith(ROOT) || !fs.existsSync(f) || fs.statSync(f).isDirectory()) { r.writeHead(404); return r.end(); }
  r.writeHead(200, { 'content-type': types[path.extname(f)] || 'application/octet-stream' }); fs.createReadStream(f).pipe(r); });
(async () => {
  await new Promise(r => server.listen(0, r));
  const base = `http://127.0.0.1:${server.address().port}/`, browser = await chromium.launch({ executablePath: process.env.CHROMIUM_PATH || undefined, args: ['--no-sandbox', '--disable-dev-shm-usage'] });
  const ctx = await browser.newContext({ viewport: { width: 390, height: 844 }, locale: 'he-IL', serviceWorkers: 'block' });
  await ctx.addInitScript(d => { if (!sessionStorage.getItem('seeded')) { sessionStorage.setItem('seeded', '1'); localStorage.clear();
    for (const [k, v] of Object.entries(d)) localStorage.setItem(k, JSON.stringify(v));
    localStorage.setItem('kis_web_v1', JSON.stringify({ launched: 2, tourDone: true })); localStorage.setItem('kis_ai_invited_v1', '1'); localStorage.setItem('kis_signin_skip_v1', '1'); } }, rich);
  const page = await ctx.newPage(), errors = []; page.on('pageerror', e => errors.push(e.message));
  const tid = t => `[data-testid="${t}"]`;
  await page.goto(base); await page.waitForSelector(tid('home-hero'));
  await page.evaluate(() => window.Kis.openNew()); await page.waitForTimeout(300);
  await page.click('[data-mode="CLOCK"]').catch(() => {});
  // 2 then 5 cannot be an hour: the app says so.
  await page.click(tid('clock-start')); await page.keyboard.type('25');
  await page.waitForSelector('#toast:has-text("אין שעה כזאת")');
  // 22:00 → 06:00 ends the next day.
  await page.click(tid('clock-start')); await page.keyboard.type('2200');
  await page.click(tid('clock-end')); await page.keyboard.type('0600'); await page.keyboard.press('Enter');
  assert.match(await page.textContent(tid('editor-meta')), /הסיום למחרת/);
  // 06:00 → 23:00 is 17 hours: saving asks first, and nothing is saved until confirmed.
  const before = await page.evaluate(() => JSON.parse(localStorage.getItem('work_complete_backup')).entries.length);
  await page.click(tid('clock-start')); await page.keyboard.type('0600');
  await page.click(tid('clock-end')); await page.keyboard.type('2300'); await page.keyboard.press('Enter');
  assert.match(await page.textContent(tid('editor-meta')), /ארוכה מ־16 שעות/);
  await page.click(tid('save-shift'));
  await page.waitForSelector('[data-testid="confirm-sheet"], .sheet:has-text("לשמור משמרת של 17:00 שעות")');
  assert.equal(await page.evaluate(() => JSON.parse(localStorage.getItem('work_complete_backup')).entries.length), before);
  await page.keyboard.press('Escape'); await page.waitForTimeout(200);
  // A negative rate: an error, and no total.
  await page.fill(tid('rate-input'), '-5'); await page.waitForTimeout(200);
  assert.equal(await page.locator(tid('editor-total')).count(), 0);
  assert.match(await page.textContent(tid('save-hint')), /שלילי|תעריף/);
  // M-06: these optional values previously passed because null >= 0 in JavaScript.
  await page.fill(tid('rate-input'), '60,5');
  await page.click('[data-a=plus]'); await page.click('[data-plus=bonus]');
  for (const value of ['abc', 'Infinity', '1e309', '-5', '1,2,3']) {
    await page.fill('[data-f=bonus]', value);
    assert.equal(await page.locator(tid('save-shift')).isDisabled(), true, value);
    assert.equal(await page.locator(tid('editor-total')).count(), 0, value);
  }
  await page.fill('[data-f=bonus]', '');
  await page.click('[data-a=plus]'); await page.click('[data-plus=break]');
  for (const [value, minutes] of [['601', 600], ['15', 15], ['1.5', 0], ['abc', 0], ['', 0]]) {
    await page.fill('[data-f=break]', value);
    assert.equal(await page.evaluate(() => JSON.parse(localStorage.getItem('kis_web_draft_v1')).breakMin), minutes);
  }
  // M-07: actual target buttons, both time modes, persisted worker pay, note-only edit and reload.
  for (const start of [null, '09:00']) for (const target of ['ME', 'OTHERS', 'ALL']) {
    await page.evaluate(start => {
      const d = window.KisEditor.newDraft(window.Kis.store);
      localStorage.setItem(window.KisEditor.DRAFT, JSON.stringify({ ...d, mode: 'GROUP', team: true, start, end: '10:30',
        hours: '1:30', rate: '60', bonus: '30', workers: [{ name: 'עובד בדיקה', hours: '1:30', bonus: '', source: { extra: 'keep' } }] }));
    }, start);
    await page.reload(); await page.waitForSelector(tid('add-page'));
    await page.click(`[data-bt="${target}"]`);
    assert.match(await page.textContent(tid('editor-total')), target === 'OTHERS' ? /90/ : /120/);
    await page.click(tid('save-shift')); await page.waitForSelector(tid('add-page'), { state: 'detached' });
    const entry = await page.evaluate(() => window.Kis.store.data.entries.at(-1));
    assert.equal(entry.totalEarnings, target === 'OTHERS' ? 90 : 120);
    assert.equal(JSON.parse(entry.groupWorkersJson)[0].bonus || 0, target === 'ME' ? 0 : 30);
    await page.reload(); await page.waitForSelector(tid('home-hero'));
    await page.evaluate(id => window.KisEditor.open(window.Kis, { entry: window.Kis.store.data.entries.find(e => e.id === id) }), entry.id);
    if (!await page.locator('[data-f=notes]').count()) await page.click('[data-a=note]');
    await page.fill('[data-f=notes]', 'הערה סינתטית'); await page.click(tid('save-shift'));
    await page.waitForSelector(tid('edit-page'), { state: 'detached' });
    const edited = await page.evaluate(id => window.Kis.store.data.entries.find(e => e.id === id), entry.id);
    assert.equal(edited.totalEarnings, entry.totalEarnings);
    assert.equal(edited.groupWorkersJson, entry.groupWorkersJson);
  }
  // A pasted negative worker amount stays invalid; it must never silently become positive.
  await page.evaluate(() => {
    const d = window.KisEditor.newDraft(window.Kis.store);
    window.KisEditor.open(window.Kis, { restore: { ...d, mode: 'JOB', team: true, teamSameRate: false, amount: '100',
      workers: [{ name: 'עובד בדיקה', hours: '', bonus: '', amount: '', source: {} }] } });
  });
  for (const value of ['-5', 'abc', 'Infinity', '1e309']) {
    await page.fill(tid('job-worker-amount-0'), value);
    assert.equal(await page.locator(tid('save-shift')).isDisabled(), true, value);
  }
  await page.fill(tid('job-worker-amount-0'), '40,5');
  assert.equal(await page.locator(tid('save-shift')).isDisabled(), false);
  // Leaving unsaved typing asks first (kept behaviour); this test drops the draft and reloads instead.
  await page.evaluate(() => localStorage.removeItem('kis_web_draft_v1')); await page.reload(); await page.waitForSelector(tid('home-hero'));
  // CW-14: completion cannot turn Infinity into a known null amount or retain an old valid preview.
  const missingId = await page.evaluate(() => {
    const d = window.KisEditor.newDraft(window.Kis.store);
    const [id] = window.Kis.store.addEntries([window.KisEditor.toEntry({ ...d, mode: 'HOURS', hours: '1:00', rateUnknown: true })]);
    window.KisHistory.state.selection = new Set([id]); window.KisHistory.bulkRate(window.Kis);
    return id;
  });
  await page.fill(tid('bulk-rate-input'), '40,5');
  assert.equal(await page.locator(tid('bulk-rate-apply')).isDisabled(), false);
  for (const value of ['', 'abc', 'Infinity', '1e309', '-5', '0x10']) {
    await page.fill(tid('bulk-rate-input'), value);
    assert.equal(await page.locator(tid('bulk-rate-apply')).isDisabled(), true, value);
  }
  await page.fill(tid('bulk-rate-input'), '40,5'); await page.click(tid('bulk-rate-apply'));
  assert.equal(await page.evaluate(id => window.Kis.store.data.entries.find(e => e.id === id).totalEarnings, missingId), 40.5);
  // The series editor uses the same parser; rejection leaves the series intact.
  await page.evaluate(() => {
    const store = window.Kis.store, today = window.KisPlan.dateOf(Date.now());
    store.updatePlans(p => p.series.push({ id: 'm06-series', category: store.data.categories[0].name, daysMask: 127,
      startTime: '09:00', endTime: '10:00', hourlyRate: 60, currency: '₪', fromDate: today, untilDate: today, createdAt: Date.now() }));
    window.KisPlans.seriesSheet(window.Kis);
  });
  await page.click('[data-rate="m06-series"]');
  for (const value of ['', 'abc', 'Infinity', '1e309', '-5', '0x10']) {
    await page.fill(`${tid('series-rate-sheet')} [data-f=r]`, value); await page.click(`${tid('series-rate-sheet')} [data-a=ok]`);
    assert.equal(await page.evaluate(() => window.Kis.store.plans.series.find(s => s.id === 'm06-series').hourlyRate), 60, value);
  }
  await page.fill(`${tid('series-rate-sheet')} [data-f=r]`, '40,5'); await page.click(`${tid('series-rate-sheet')} [data-a=ok]`);
  assert.equal(await page.evaluate(() => window.Kis.store.plans.series.find(s => s.id === 'm06-series').hourlyRate), 40.5);
  await page.reload(); await page.waitForSelector(tid('home-hero'));
  // Live timer: "-5" is refused with a message, "40,5" starts at 40.5.
  await page.click(tid('live-start'));
  for (const value of ['-5', 'Infinity', '1e309', '100000', '0x10']) {
    await page.fill('[data-f=r]', value); await page.click(tid('live-go'));
    assert.equal(await page.textContent(tid('live-error')), 'התעריף לא תקין');
  }
  await page.fill('[data-f=r]', '40,5'); await page.click(tid('live-go')); await page.waitForTimeout(200);
  const timer = await page.evaluate(() => JSON.parse(localStorage.getItem('work_active_shift_v1:guest')));
  assert.equal(timer.rate, 40.5);
  assert.deepEqual(errors, []);
  console.log('m2-input: time warnings, finite money validation, invalid worker amounts, clock/manual bonus targets, note edits/reload, timer comma');
  await browser.close(); server.close();
})().catch(e => { console.error(e); process.exit(1); });
