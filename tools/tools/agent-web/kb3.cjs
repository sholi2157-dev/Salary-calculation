const { chromium } = require('playwright'); const L = require('../lib.cjs'); const SH = __dirname + '/../../shots/agents/';
(async () => { const { s, base } = await L.serve(); const b = await chromium.launch(); const { page } = await L.open(b, base, { viewport: { width: 1280, height: 900 }, mobile: false });
  const desc = () => page.evaluate(() => { const a = document.activeElement; return a === document.body ? 'BODY' : a.tagName + ' ' + (a.dataset.testid || a.textContent.trim().slice(0, 20)); });
  await page.evaluate(() => Kis.go('history')); await page.focus('[data-testid=history-filters]'); await page.keyboard.press('Enter'); await page.waitForTimeout(200);
  await page.focus('[data-testid=period-MONTH]'); await page.keyboard.press('Enter'); await page.waitForTimeout(150); console.log('filters after Enter on חודש:', await desc());
  await page.evaluate(() => document.querySelectorAll('.scrim').forEach(x => x.remove()));
  await page.evaluate(() => Kis.go('settings')); await page.click('[data-testid=import-button]'); await page.waitForTimeout(200);
  await page.focus('.sheet input[type=file]'); await page.waitForTimeout(100);
  const r = await page.evaluate(() => { const i = document.querySelector('.sheet input[type=file]'), l = i.closest('label'); const cs = getComputedStyle(i), ls = getComputedStyle(l); return { inputOutline: cs.outlineStyle + ' ' + cs.outlineWidth, inputOpacity: cs.opacity, labelOutline: ls.outlineStyle, labelHasFocusWithinRule: [...document.styleSheets].some(sh => { try { return [...sh.cssRules].some(r => r.cssText.includes('focus-within')); } catch { return false; } }) }; });
  console.log('import file input focus:', JSON.stringify(r)); await page.screenshot({ path: SH + 'web-kb-file-focus.png', clip: { x: 300, y: 300, width: 700, height: 600 } });
  await page.evaluate(() => document.querySelectorAll('.scrim').forEach(x => x.remove()));
  // detail: group worker paid chip closes the sheet?
  await page.evaluate(() => Kis.openDetail('l')); await page.waitForTimeout(200); await page.click('[data-wp="0"]'); await page.waitForTimeout(200);
  console.log('after worker paid chip: sheet open =', await page.locator('[data-testid=shift-detail]').count(), 'toast=', await page.evaluate(() => document.getElementById('toast').hidden ? 'none' : document.getElementById('toast').innerText));
  // bulk paid: undo offered?
  await page.evaluate(() => Kis.go('history')); await page.dispatchEvent('.shift[data-id]', 'contextmenu'); await page.waitForTimeout(150); await page.click('[data-s=paid]'); await page.waitForTimeout(150);
  console.log('bulk paid toast:', await page.evaluate(() => document.getElementById('toast').innerText.replace(/\n/g, ' | ')));
  await b.close(); s.close(); })();
