const { chromium } = require('playwright'); const L = require('../lib.cjs');
(async () => { const { s, base } = await L.serve(); const b = await chromium.launch();
  const { page } = await L.open(b, base, { viewport: { width: 320, height: 640 } });
  const f = () => page.evaluate(() => { const vw = innerWidth; return [...document.querySelectorAll('body *')].filter(e => { const r = e.getBoundingClientRect(); return r.width > vw + 1 || r.left < -1; }).slice(0, 6).map(e => `${e.tagName}.${e.className} w=${Math.round(e.getBoundingClientRect().width)} minW=${getComputedStyle(e).minWidth} gtc=${getComputedStyle(e).gridTemplateColumns.slice(0,40)}`); });
  await page.evaluate(() => Kis.go('history')); await page.waitForTimeout(200); console.log('history', await f());
  await page.evaluate(() => Kis.openNew()); await page.waitForTimeout(300); console.log('editor', await f());
  console.log(await page.evaluate(() => { const r = document.querySelector('.clock-row'); return r && { w: r.scrollWidth, cw: r.clientWidth, gtc: getComputedStyle(r).gridTemplateColumns }; }));
  await b.close(); s.close(); })();
