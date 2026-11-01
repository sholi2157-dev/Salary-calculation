// Reflow / zoom / text-spacing / desktop+tablet layout / reduced-motion probes.
const { chromium } = require('playwright'); const L = require('../lib.cjs'); const crypto = require('node:crypto');
const SH = __dirname + '/../../shots/agents/'; const out = {};
const issues = page => page.evaluate(() => {
  const vw = document.documentElement.clientWidth, res = { docScrollW: document.documentElement.scrollWidth, vw, clipped: [], offscreen: [] };
  for (const el of document.querySelectorAll('body *')) { const r = el.getBoundingClientRect(); if (!r.width || !r.height) continue; const s = getComputedStyle(el); if (s.display === 'none' || s.visibility === 'hidden') continue;
    const txt = [...el.childNodes].some(n => n.nodeType === 3 && n.textContent.trim());
    if (txt && (el.scrollWidth > el.clientWidth + 1) && (s.overflowX === 'hidden' || s.textOverflow === 'ellipsis' || s.overflow === 'hidden')) res.clipped.push(`${el.tagName.toLowerCase()}.${el.className} "${el.textContent.trim().slice(0, 28)}" ${el.scrollWidth}>${el.clientWidth}`);
    if (txt && (r.right > vw + 2 || r.left < -2) && !el.closest('[style*="overflow-x:auto"],.days,.places,.chips,.acts,.weekdays')) res.offscreen.push(`${el.tagName.toLowerCase()}.${el.className} "${el.textContent.trim().slice(0, 24)}" L${Math.round(r.left)} R${Math.round(r.right)}`);
  } res.clipped = [...new Set(res.clipped)].slice(0, 15); res.offscreen = [...new Set(res.offscreen)].slice(0, 15); return res; });
(async () => {
  const { s, base } = await L.serve(); const browser = await chromium.launch();
  const run = async (tag, vp, mobile, steps) => { const { ctx, page } = await L.open(browser, base, { viewport: vp, mobile });
    for (const [name, fn] of steps) { try { await fn(page); await page.waitForTimeout(350); await page.screenshot({ path: `${SH}web-${tag}-${name}.png`, fullPage: !!vp.full });
        out[`${tag}-${name}`] = await issues(page); } catch (e) { out[`${tag}-${name}`] = 'ERR ' + e.message.split('\n')[0]; }
      await page.evaluate(() => { document.querySelectorAll('.scrim').forEach(x => x.remove()); }); }
    if (page.errors.length) out[tag + '-errors'] = page.errors; await ctx.close(); };
  const go = p => async page => page.evaluate(p => window.Kis.go(p), p);
  const editor = async page => { await page.evaluate(() => window.Kis.go('home')); await page.evaluate(() => window.Kis.openNew()); };
  const closeEd = async page => page.evaluate(() => { if (window.Kis.editorOpen) { document.querySelector('.editor')?.remove(); window.Kis.editorOpen = false; document.querySelectorAll('[inert]').forEach(n => n.inert = false); } });
  const std = [['home', go('home')], ['history', go('history')], ['settings', go('settings')], ['editor', editor], ['places', async p => { await closeEd(p); await p.evaluate(() => window.Kis.go('settings')); await p.click('[data-testid=places-button]'); }],
    ['detail-group', async p => { await p.evaluate(() => window.Kis.openDetail('l')); }]];
  await run('w320', { width: 320, height: 640 }, true, std);
  await run('z200-390', { width: 195, height: 422 }, true, std.slice(0, 4));
  await run('z200-1280', { width: 640, height: 450 }, false, std.slice(0, 5));
  await run('t768', { width: 768, height: 1024 }, true, std.slice(0, 4));
  await run('d1440', { width: 1440, height: 900 }, false, [...std.slice(0, 4), ['places', std[4][1]]]);
  // text spacing (WCAG 1.4.12) at 390
  const ts = async p => p.addStyleTag({ content: '*{line-height:1.5!important;letter-spacing:.12em!important;word-spacing:.16em!important} p{margin-bottom:2em!important}' });
  await run('ts390', { width: 390, height: 844 }, true, [['home', async p => { await ts(p); }], ['editor', async p => { await editor(p); await ts(p); }], ['settings', async p => { await closeEd(p); await go('settings')(p); await ts(p); }]]);
  // root font size: does the UI scale at all?
  { const { ctx, page } = await L.open(browser, base, {}); const a = crypto.createHash('md5').update(await page.screenshot()).digest('hex');
    await page.addStyleTag({ content: 'html{font-size:24px!important}' }); await page.waitForTimeout(200); const b = crypto.createHash('md5').update(await page.screenshot()).digest('hex');
    const fs = await page.evaluate(() => getComputedStyle(document.querySelector('.hero, .label')).fontSize);
    out['rootfont'] = { identicalScreenshot: a === b, sampleFontSize: fs }; await ctx.close(); }
  // reduced motion
  { const ctx = await browser.newContext({ viewport: { width: 390, height: 844 }, reducedMotion: 'reduce', locale: 'he-IL', serviceWorkers: 'block' }); await ctx.close();
    const { ctx: c2, page } = await L.open(browser, base, {}); await page.emulateMedia({ reducedMotion: 'reduce' });
    const m = (sel) => page.evaluate(sel => [...document.querySelectorAll(sel)].slice(0, 3).map(e => { const s = getComputedStyle(e); return `${e.className}: anim=${s.animationName}/${s.animationDuration} trans=${s.transitionDuration}`; }), sel);
    await page.evaluate(() => window.KisSummaryUi.story(window.Kis, (() => { const d = new Date(); d.setMonth(d.getMonth() - 1); return d.getFullYear() + '-' + String(d.getMonth() + 1).padStart(2, '0'); })()));
    await page.waitForTimeout(300); const s1 = await page.evaluate(() => document.querySelector('.story .slide2').dataset.testid); await page.waitForTimeout(6300);
    out['reduced-motion'] = { story: await m('.story .slide2 > *, .story .blob, .story .ring'), autoAdvanceStill: s1 + ' -> ' + await page.evaluate(() => document.querySelector('.story .slide2').dataset.testid),
      countUp: await page.evaluate(() => !!document.querySelector('[data-count]')) };
    await page.keyboard.press('Escape'); await page.evaluate(() => window.Kis.openNew()); await page.waitForTimeout(200);
    out['reduced-motion'].editor = await m('.editor, .editor-in, .kis-glow, .sheet, .scrim');
    await c2.close(); }
  console.log(JSON.stringify(out, null, 1)); await browser.close(); s.close();
})().catch(e => { console.error(e); console.log(JSON.stringify(out, null, 1)); process.exit(1); });
