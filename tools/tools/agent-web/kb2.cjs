// Keyboard / focus / semantics probes.
const { chromium } = require('playwright'); const L = require('../lib.cjs');
const out = {}; const log = (k, v) => { out[k] = v; };
(async () => {
  const { s, base } = await L.serve(); const browser = await chromium.launch();
  const { ctx, page } = await L.open(browser, base, { viewport: { width: 1280, height: 900 }, mobile: false });
  const desc = () => page.evaluate(() => { const a = document.activeElement; if (!a || a === document.body) return 'BODY';
    const cs = getComputedStyle(a); const vis = cs.outlineStyle !== 'none' && parseFloat(cs.outlineWidth) > 0;
    const r = a.getBoundingClientRect(); const inSheet = !!a.closest('.sheet,.editor,.story');
    return `${a.tagName.toLowerCase()}${a.dataset.testid ? '#' + a.dataset.testid : ''} "${(a.getAttribute('aria-label') || a.innerText || a.value || '').trim().replace(/\s+/g, ' ').slice(0, 30)}" ${vis ? 'RING' : 'NO-RING'} op=${cs.opacity} ${Math.round(r.width)}x${Math.round(r.height)}${inSheet ? ' [modal]' : ''}`; });
  const tabs = async (n, shift = false) => { const seq = []; for (let i = 0; i < n; i++) { await page.keyboard.press(shift ? 'Shift+Tab' : 'Tab'); seq.push(await desc()); } return seq; };
  await page.evaluate(() => window.Kis.openNew()); await page.waitForTimeout(300);
  log('ruler-handles', await page.evaluate(() => [...document.querySelectorAll('.editor [role=slider]')].map(h => ({ label: h.getAttribute('aria-label'), now: h.getAttribute('aria-valuenow'), text: h.getAttribute('aria-valuetext'), min: h.getAttribute('aria-valuemin'), max: h.getAttribute('aria-valuemax'), tab: h.tabIndex }))));
  await page.focus('[data-testid=ruler-start]'); const v0 = await page.evaluate(() => document.querySelector('[data-testid=clock-start]').innerText.replace(/\s/g, ''));
  await page.keyboard.press('ArrowRight'); await page.keyboard.press('ArrowUp'); await page.waitForTimeout(150);
  log('ruler-arrow', { before: v0, after: await page.evaluate(() => document.querySelector('[data-testid=clock-start]').innerText.replace(/\s/g, '')), focus: await desc() });
  log('editor-tablist', await page.evaluate(() => [...document.querySelectorAll('.editor [role=tab]')].map(t => t.getAttribute('aria-controls') + ' ti=' + t.tabIndex)));
  await page.evaluate(() => { window.Kis.closeEditor(); document.querySelector('[data-testid=confirm-ok]')?.click(); });
  // Detail sheet
  await page.evaluate(() => window.Kis.go('home')); await page.waitForTimeout(200);
  log('hero-focusable', await page.evaluate(() => [...document.querySelectorAll('#page [tabindex]')].map(e => e.tagName + '.' + e.className + ' ti=' + e.tabIndex + ' ' + e.innerText.slice(0, 20))));
  await page.focus('[data-testid=recent-a], [data-testid^=recent-]'); await page.keyboard.press('Enter'); await page.waitForTimeout(300);
  log('detail-first', await desc()); log('detail-name', await page.evaluate(() => { const s = document.querySelector('.sheet'); return { role: s.getAttribute('role'), labelledby: s.getAttribute('aria-labelledby'), label: s.getAttribute('aria-label') }; }));
  // mark paid -> feedback?
  await page.click('[data-testid=detail-paid]'); await page.waitForTimeout(250);
  log('after-paid', { focus: await desc(), toast: await page.evaluate(() => { const t = document.getElementById('toast'); return t.hidden ? 'none' : t.innerText; }) });
  // toast live region: delete via detail, check markup sequence
  log('toast-region', await page.evaluate(() => { const t = document.getElementById('toast'); return { role: t.getAttribute('role'), live: t.getAttribute('aria-live'), hidden: t.hidden }; }));
  // Story via keyboard
  await page.evaluate(() => window.KisSummaryUi.story(window.Kis, (() => { const d = new Date(); d.setMonth(d.getMonth() - 1); return d.getFullYear() + '-' + String(d.getMonth() + 1).padStart(2, '0'); })()));
  await page.waitForTimeout(300); const f1 = await desc(); await page.waitForTimeout(6500); const f2 = await desc();
  log('story-focus-autoadvance', { start: f1, after6_5s: f2, slide: await page.evaluate(() => document.querySelector('.story .slide2')?.dataset.testid) });
  await page.keyboard.press('Space'); await page.waitForTimeout(6500); log('story-space-pauses?', await page.evaluate(() => document.querySelector('.story .slide2')?.dataset.testid));
  await page.keyboard.press('Escape'); await page.waitForTimeout(200); log('story-close-focus', await desc());
  // History: keyboard multi-select possible?
  await page.evaluate(() => window.Kis.go('history')); await page.waitForTimeout(200);
  await page.focus('.shift[data-id]'); await page.keyboard.press('Shift+F10'); await page.waitForTimeout(200);
  log('history-kb-select', await page.evaluate(() => document.querySelectorAll('.shift.selected').length));
  log('history-tab', await tabs(12));
  // search: typing redraws whole page
  await page.evaluate(() => window.Kis.go('history')); await page.click('[data-testid=history-search]'); 
  const n0 = await page.evaluate(() => { window.__m = 0; new MutationObserver(m => window.__m += m.length).observe(document.getElementById('page'), { childList: true, subtree: true }); return 0; });
  await page.keyboard.type('מסע', { delay: 50 }); await page.waitForTimeout(200);
  log('search-mutations', await page.evaluate(() => window.__m));
  log('search-native-cancel', await page.evaluate(() => getComputedStyle(document.querySelector('[data-testid=history-search-input]'), '::-webkit-search-cancel-button').display));
  console.log(JSON.stringify(out, null, 1)); await browser.close(); s.close();
})().catch(e => { console.error(e); console.log(JSON.stringify(out, null, 1)); process.exit(1); });
