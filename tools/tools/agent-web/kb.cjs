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
  // landmarks/headings/skip link
  for (const p of ['home', 'history', 'settings']) { await page.evaluate(p => window.Kis.go(p), p); await page.waitForTimeout(200);
    log('struct-' + p, await page.evaluate(() => ({ h: [...document.querySelectorAll('h1,h2,h3,h4')].filter(h => h.offsetParent).map(h => h.tagName + ':' + h.textContent.trim().slice(0, 25)),
      skip: !!document.querySelector('a[href^="#"]'), landmarks: [...document.querySelectorAll('main,nav,header,footer,[role=banner],[role=main],[role=navigation],[role=contentinfo]')].map(e => e.tagName + (e.getAttribute('aria-label') ? ':' + e.getAttribute('aria-label') : '')),
      title: document.title, shiftAriaPressed: [...document.querySelectorAll('.shift[aria-pressed]')].length })));
  }
  // Home tab order
  await page.evaluate(() => window.Kis.go('home')); await page.waitForTimeout(200); await page.mouse.click(5, 5);
  log('tab-home', await tabs(22));
  // Refresh steals focus? focus a recent shift, then refresh (what the 60s interval does)
  await page.focus('[data-testid^=recent-]'); const before = await desc(); await page.evaluate(() => window.Kis.refresh()); log('refresh-focus', { before, after: await desc() });
  // Sheet: open places via keyboard on settings
  await page.evaluate(() => window.Kis.go('settings')); await page.waitForTimeout(200);
  await page.focus('[data-testid=places-button]'); await page.keyboard.press('Enter'); await page.waitForTimeout(300);
  log('places-first-focus', await desc());
  log('places-tab40', await tabs(40));
  await page.keyboard.press('Escape'); await page.waitForTimeout(200); log('places-esc-return', await desc());
  // info dot sheet
  await page.focus('.info-dot'); await page.keyboard.press('Enter'); await page.waitForTimeout(200); log('info-first', await desc());
  log('info-tab', await tabs(4)); await page.keyboard.press('Escape'); await page.waitForTimeout(200); log('info-return', await desc());
  // theme segment via keyboard: focus kept?
  await page.focus('[data-testid=theme-dark]'); await page.keyboard.press('Enter'); await page.waitForTimeout(200); log('theme-after-enter', await desc());
  await page.focus('[data-testid=theme-system]'); await page.keyboard.press('Enter'); await page.waitForTimeout(200);
  await page.focus('[data-cur="$"]'); await page.keyboard.press('Enter'); await page.waitForTimeout(200); log('settings-currency-after-enter', await desc());
  await page.focus('[data-cur="₪"]'); await page.keyboard.press('Enter');
  // Editor
  await page.evaluate(() => window.Kis.go('home')); await page.focus('[data-testid=add-shift]'); await page.keyboard.press('Enter'); await page.waitForTimeout(400);
  log('editor-first-focus', await desc());
  log('editor-tab45', await tabs(45));
  // currency + day via keyboard
  await page.focus('.editor [data-cur="$"]'); await page.keyboard.press('Enter'); await page.waitForTimeout(200); log('editor-cur-after-enter', await desc());
  await page.focus('.editor .days button:nth-child(2)'); await page.keyboard.press('Enter'); await page.waitForTimeout(200); log('editor-day-after-enter', await desc());
  await page.focus('[data-testid=mode-hours]'); await page.keyboard.press('ArrowLeft'); await page.waitForTimeout(150); log('editor-tab-arrowkey', await desc());
  log('editor-day-names', await page.evaluate(() => [...document.querySelectorAll('.editor .days button')].map(b => b.innerText.replace(/\s+/g, ' ') + '|' + (b.getAttribute('aria-label') || ''))));
  log('editor-place-chip-names', await page.evaluate(() => [...document.querySelectorAll('.editor .places button')].slice(0, 3).map(b => b.textContent + ' pressed=' + b.getAttribute('aria-pressed'))));
  // time digits keyboard: focus start digit, type 0930
  await page.focus('[data-testid=clock-start-d0]'); await page.keyboard.press('Enter'); await page.waitForTimeout(150); log('digit-enter-focus', await desc());
  await page.keyboard.type('0930'); await page.waitForTimeout(150); log('start-after-type', await page.evaluate(() => document.querySelector('[data-testid=clock-start]').innerText.replace(/\s/g, '')));
  await page.keyboard.press('Escape'); await page.waitForTimeout(150); log('after-esc1', await desc());
  await page.keyboard.press('Escape'); await page.waitForTimeout(250); log('after-esc2-confirm', await desc() + ' sheets=' + await page.locator('.sheet').count());
  log('confirm-tab', await tabs(6));
  await page.keyboard.press('Escape'); await page.waitForTimeout(200); log('after-esc3', await desc());
  // ruler semantics
  log('ruler', await page.evaluate(() => { const r = document.querySelector('.editor .ruler, .editor [class*=ruler]'); return r ? { html: r.outerHTML.slice(0, 300), role: r.getAttribute('role'), label: r.getAttribute('aria-label'), hidden: r.getAttribute('aria-hidden') } : null; }));
  log('sink', await page.evaluate(() => { const s = document.querySelector('[data-testid=key-sink]'); const cs = getComputedStyle(s); return { tab: s.tabIndex, op: cs.opacity, pos: cs.position, label: s.getAttribute('aria-label') }; }));
  await page.evaluate(() => window.Kis.closeEditor && window.Kis.closeEditor()); await page.evaluate(() => document.querySelectorAll('.scrim').forEach(x => x.remove()));
  await page.evaluate(() => { const c = document.querySelector('[data-testid=confirm-ok]'); c && c.click(); });
  // Detail sheet
  await page.evaluate(() => window.Kis.go('home')); await page.waitForTimeout(200);
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
