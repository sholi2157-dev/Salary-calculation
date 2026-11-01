const { chromium } = require('playwright'); const L = require('../lib.cjs'); const SH = __dirname + '/../../shots/agents/';
(async () => { const { s, base } = await L.serve(); const b = await chromium.launch(); const { page } = await L.open(b, base, {});
  const state = () => page.evaluate(() => ({ hint: document.querySelector('[data-testid=save-hint]')?.textContent || '', save: document.querySelector('[data-testid=save-shift]')?.disabled ? 'disabled' : 'enabled', meta: document.querySelector('[data-testid=editor-meta]')?.innerText.replace(/\s+/g, ' '), invalid: [...document.querySelectorAll('.editor [aria-invalid=true]')].length, describedby: [...document.querySelectorAll('.editor input[aria-describedby]')].length }));
  await page.evaluate(() => Kis.openNew()); await page.waitForTimeout(300);
  const rate = async v => { await page.fill('[data-testid=rate-input]', v); await page.waitForTimeout(120); return state(); };
  for (const v of ['0', '-50', 'abc', '99999.99', '1e9', '50,5', '']) console.log('rate', JSON.stringify(v), JSON.stringify(await rate(v)));
  await rate('50');
  await page.click('[data-testid=mode-hours]'); await page.waitForTimeout(150);
  for (const d of ['2400', '2500', '0000', '9999']) { await page.click('[data-testid=hours-input-d0]'); await page.keyboard.type(d); await page.keyboard.press('Enter'); await page.waitForTimeout(150); console.log('hours', d, JSON.stringify(await state()), await page.evaluate(() => document.getElementById('toast').hidden ? '' : document.getElementById('toast').innerText)); }
  await page.click('[data-testid=mode-clock]'); await page.waitForTimeout(150);
  await page.click('[data-testid=clock-start-d0]'); await page.keyboard.type('1000'); await page.keyboard.press('Enter'); await page.waitForTimeout(100);
  await page.click('[data-testid=clock-end-d0]'); await page.keyboard.type('1000'); await page.keyboard.press('Enter'); await page.waitForTimeout(150);
  console.log('start=end', JSON.stringify(await state()));
  await page.click('[data-testid=clock-end-d0]'); await page.keyboard.type('0900'); await page.keyboard.press('Enter'); await page.waitForTimeout(150);
  console.log('end<start (10->09)', JSON.stringify(await state()));
  await page.screenshot({ path: SH + 'web-err-overnight23h.png' });
  // big amount display
  await page.click('[data-testid=clock-end-d0]'); await page.keyboard.type('1800'); await page.keyboard.press('Enter'); await rate('99999.99'); await page.waitForTimeout(150);
  await page.screenshot({ path: SH + 'web-err-bigamount.png' });
  // long note + place
  await page.click('[data-testid=editor-more]'); await page.fill('[data-testid=notes-input]', 'א'.repeat(5) + ' ' + 'הערה ארוכה מאוד '.repeat(25)); await page.waitForTimeout(100);
  console.log('notes maxlength', await page.evaluate(() => document.querySelector('[data-testid=notes-input]').maxLength));
  await page.click('[data-testid=save-shift]'); await page.waitForTimeout(400);
  const id = await page.evaluate(() => Kis.store.data.entries.at(-1).id); console.log('saved', await page.evaluate(() => { const e = Kis.store.data.entries.at(-1); return [e.hours, e.hourlyRate, e.totalEarnings, e.notes.length]; }));
  await page.evaluate(() => Kis.go('home')); await page.waitForTimeout(200); await page.screenshot({ path: SH + 'web-err-home-big.png' });
  await page.evaluate(id => Kis.openDetail(id), id); await page.waitForTimeout(300); await page.screenshot({ path: SH + 'web-err-detail-big.png' });
  await page.evaluate(() => document.querySelectorAll('.scrim').forEach(x => x.remove()));
  // live-start negative rate: silent?
  await page.evaluate(() => Kis.startLive()); await page.waitForTimeout(200); await page.fill('[data-f=r]', '-5'); await page.click('[data-testid=live-go]'); await page.waitForTimeout(200);
  console.log('live -5', await page.evaluate(() => ({ sheetStill: !!document.querySelector('[data-testid=live-start-sheet]'), toast: document.getElementById('toast').hidden ? '' : document.getElementById('toast').innerText, invalid: document.querySelector('[data-f=r]').getAttribute('aria-invalid') })));
  await page.evaluate(() => document.querySelectorAll('.scrim').forEach(x => x.remove()));
  // place name 60 chars
  await page.evaluate(() => KisSettings.placeSheet(Kis, null, () => {})); await page.waitForTimeout(200);
  console.log('place name input', await page.evaluate(() => [...document.querySelectorAll('.sheet input')].map(i => `${i.getAttribute('aria-label') || i.placeholder}: maxlength=${i.maxLength}`)));
  await b.close(); s.close(); })();
