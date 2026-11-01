const { chromium } = require('playwright'); const AxeBuilder = require('@axe-core/playwright').default;
const fs = require('node:fs'); const L = require('./lib.cjs');
const OUT = __dirname + '/../shots/web2'; fs.mkdirSync(OUT, { recursive: true });
const log = [];
(async () => {
  const { s, base } = await L.serve(); const browser = await chromium.launch();
  const shot = async (page, name, full = false) => { await page.waitForTimeout(500); await page.screenshot({ path: `${OUT}/${name}.png`, fullPage: full }); };
  const overflow = page => page.evaluate(() => ({ sw: document.documentElement.scrollWidth, cw: document.documentElement.clientWidth }));
  // viewports
  for (const [vw, vh, mobile] of [[360, 740, true], [768, 1024, true], [1440, 900, false]]) for (const scheme of ['light', 'dark']) {
    const { ctx, page } = await L.open(browser, base, { viewport: { width: vw, height: vh }, scheme, mobile });
    await shot(page, `vp${vw}-${scheme}-home`); log.push(`vp${vw} ${scheme} home overflow ${JSON.stringify(await overflow(page))}`);
    await page.evaluate(() => window.Kis.go('history')); await shot(page, `vp${vw}-${scheme}-history`);
    await page.evaluate(() => window.Kis.openNew()); await shot(page, `vp${vw}-${scheme}-editor`); log.push(`vp${vw} ${scheme} editor overflow ${JSON.stringify(await overflow(page))}`);
    await ctx.close();
  }
  // large dataset
  { const { ctx, page } = await L.open(browser, base, { data: L.big(400) }); const t0 = Date.now();
    await page.evaluate(() => window.Kis.go('history')); await page.waitForTimeout(300); log.push('big history render ms ' + (Date.now() - t0) + ' rows ' + await page.locator('.shift[data-id]').count());
    await shot(page, 'big-history'); await page.evaluate(() => window.Kis.go('home')); await shot(page, 'big-home', true); await ctx.close(); }
  // empty, first run
  for (const scheme of ['light', 'dark']) {
    const { ctx, page } = await L.open(browser, base, { data: null, launched: false, scheme });
    await shot(page, `first-${scheme}-1`);
    for (let i = 2; i <= 7; i++) { const n = page.locator('[data-testid=launch-next],[data-testid=intro-next],[data-testid=intro-next-2],[data-testid=tour-next],[data-testid=launch-done],[data-testid=launch-first]').first(); if (!(await n.count())) break; await n.click().catch(() => {}); await shot(page, `first-${scheme}-${i}`); }
    await ctx.close();
    const e = await L.open(browser, base, { data: null, launched: true, scheme }); await shot(e.page, `empty-${scheme}-home`);
    await e.page.evaluate(() => window.Kis.go('history')); await shot(e.page, `empty-${scheme}-history`);
    await e.page.evaluate(() => window.Kis.openNew()); await shot(e.page, `empty-${scheme}-add`);
    const r = await new AxeBuilder({ page: e.page }).withTags(['wcag2a', 'wcag2aa']).analyze(); log.push(`empty-${scheme}-add axe ${r.violations.map(v => v.id + ':' + v.nodes.length).join(',')}`);
    await e.ctx.close();
  }
  // editor deep: manual mode, job mode, more-options scroll, group
  for (const scheme of ['light']) {
    const { ctx, page } = await L.open(browser, base, { scheme });
    await page.evaluate(() => window.Kis.openNew());
    await page.click('button[data-mode="HOURS"]'); await shot(page, 'ed-manual');
    await page.click('button[data-mode="JOB"]'); await shot(page, 'ed-job');
    await page.click('button[data-mode="CLOCK"]');
    const more = page.locator('[data-testid=editor-more]'); if (await more.count()) await more.click();
    await page.evaluate(() => { const sc = [...document.querySelectorAll('*')].filter(e => e.scrollHeight > e.clientHeight + 20 && getComputedStyle(e).overflowY.match(/auto|scroll/)); sc.forEach(e => e.scrollTop = e.scrollHeight); });
    await shot(page, 'ed-more-scrolled');
    const g = page.getByText('קבוצה').first(); await g.click().catch(() => {}); await shot(page, 'ed-group');
    await page.evaluate(() => { const sc = [...document.querySelectorAll('*')].filter(e => e.scrollHeight > e.clientHeight + 20 && getComputedStyle(e).overflowY.match(/auto|scroll/)); sc.forEach(e => e.scrollTop = e.scrollHeight); });
    await shot(page, 'ed-group-scrolled');
    log.push('editor errors ' + page.errors.join(' | ').slice(0, 300));
    await ctx.close();
  }
  // story / recap
  { const { ctx, page } = await L.open(browser, base, {}); const b = page.locator('.recap, [data-testid=story-reveal], [data-a=recap]').first();
    const banner = page.getByText('אוקטובר שלך בכיס').first(); await banner.click().catch(e => log.push('story ' + e.message.slice(0, 80)));
    await shot(page, 'story-1'); for (let i = 2; i <= 6; i++) { const n = page.locator('[data-testid=story-next],[data-testid=story-reveal]').first(); if (!(await n.count())) break; await n.click().catch(() => {}); await shot(page, 'story-' + i); }
    await ctx.close(); }
  // history selection via long-press (touch)
  { const { ctx, page } = await L.open(browser, base, {}); await page.evaluate(() => window.Kis.go('history'));
    const rowEl = page.locator('.shift[data-id]').nth(1); const box = await rowEl.boundingBox();
    await page.touchscreen.tap(box.x + 50, box.y + 20).catch(()=>{}); await page.waitForTimeout(300); await page.keyboard.press('Escape');
    await rowEl.dispatchEvent('contextmenu').catch(() => {}); await page.waitForTimeout(300); await shot(page, 'select-1');
    log.push('selection bar ' + await page.locator('[data-testid=selection-bar]').count());
    await page.locator('.shift[data-id]').nth(2).click().catch(() => {}); await shot(page, 'select-2');
    await ctx.close(); }
  await browser.close(); s.close(); fs.writeFileSync(__dirname + '/../notes/explore2.log', log.join('\n')); console.log(log.join('\n'));
})();
