const { chromium } = require(process.env.PLAYWRIGHT_MODULE);
(async () => { const b = await chromium.launch({ executablePath: process.env.CHROMIUM_PATH });
  const p = await b.newPage({ viewport: { width: 1080, height: 1350 }, deviceScaleFactor: 1 });
  await p.goto('file://' + process.cwd() + '/' + process.argv[2]); await p.evaluate(() => document.fonts.ready); await p.waitForTimeout(300);
  await p.screenshot({ path: process.argv[3] }); await b.close(); })();
