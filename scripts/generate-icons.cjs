// Renders the home-screen icons from the existing app mark (the favicon in index.html; no new brand design).
// Run once when the mark changes: PLAYWRIGHT_MODULE=… CHROMIUM_PATH=… node scripts/generate-icons.cjs
const fs = require('node:fs'), path = require('node:path');
const { chromium } = require(process.env.PLAYWRIGHT_MODULE || 'playwright');
const BG = '#0D1015';
const MARK = '<g stroke-width="7" stroke-linecap="round"><path d="M19 44V30" stroke="#7C8CFF"/><path d="M32 44V20" stroke="#FFC54D"/><path d="M45 44V25" stroke="#34C79A"/><path d="M19 44h26" stroke="#EDF0F5"/></g>';
// any: the favicon's rounded tile. Full-bleed tiles (maskable, apple-touch) are cut by the system, so they fill the square.
const rounded = `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 64 64"><rect width="64" height="64" rx="19" fill="${BG}"/>${MARK}</svg>`;
const full = scale => `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 64 64"><rect width="64" height="64" fill="${BG}"/><g transform="translate(32 32) scale(${scale}) translate(-32 -32)">${MARK}</g></svg>`;
const icons = [['icon-192.png', 192, rounded], ['icon-512.png', 512, rounded], ['maskable-512.png', 512, full(1)], ['apple-touch-icon.png', 180, full(1.1)]];
(async () => {
  const browser = await chromium.launch({ executablePath: process.env.CHROMIUM_PATH || undefined, args: ['--no-sandbox'] });
  const page = await browser.newPage({ deviceScaleFactor: 1 });
  for (const [name, size, svg] of icons) {
    await page.setViewportSize({ width: size, height: size });
    await page.setContent(`<style>html,body{margin:0;background:transparent}img{display:block;width:${size}px;height:${size}px}</style><img src="data:image/svg+xml;base64,${Buffer.from(svg).toString('base64')}">`);
    await page.locator('img').screenshot({ path: path.join('web/icons', name), omitBackground: true });
  }
  await browser.close();
  console.log('icons:', fs.readdirSync('web/icons').join(', '));
})();
