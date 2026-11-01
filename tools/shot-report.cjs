const { chromium } = require('playwright');
(async () => { const b = await chromium.launch(); const p = await b.newPage({ viewport: { width: 390, height: 900 }, colorScheme: 'dark' });
  await p.goto('file://' + __dirname + '/../plan/index.html'); await p.waitForTimeout(800);
  const o = await p.evaluate(() => ({ sw: document.documentElement.scrollWidth, cw: document.documentElement.clientWidth, h: document.body.scrollHeight }));
  console.log(JSON.stringify(o)); await p.screenshot({ path: __dirname + '/../shots/plan-check.png' }); await b.close(); })();
