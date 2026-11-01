const { chromium } = require('playwright'); const AxeBuilder = require('@axe-core/playwright').default; const L = require('./lib.cjs'); const fs=require('fs');
const OUT = __dirname + '/../shots/live'; fs.mkdirSync(OUT, { recursive: true });
(async () => { const b = await chromium.launch(); const base = 'https://salary-calculation-eta.vercel.app/';
  for (const scheme of ['light', 'dark']) { const { ctx, page } = await L.open(b, base, { scheme });
    await page.waitForTimeout(1500); await page.screenshot({ path: `${OUT}/${scheme}-home.png` });
    const r = await new AxeBuilder({ page }).withTags(['wcag2a','wcag2aa']).analyze(); console.log(scheme, 'axe', r.violations.map(v=>v.id+':'+v.nodes.length).join(',')||'none');
    await page.evaluate(() => window.KisDownload.open()); await page.waitForTimeout(2500);
    console.log(scheme, 'release text:', await page.locator('[data-release]').first().textContent());
    await page.screenshot({ path: `${OUT}/${scheme}-download.png` });
    const perf = await page.evaluate(() => performance.getEntriesByType('resource').filter(e=>/fonts|\.css|\.js/.test(e.name)).reduce((a,e)=>a+(e.transferSize||0),0));
    console.log(scheme,'transfer KB fonts+css+js', Math.round(perf/1024), 'errors', page.errors.slice(0,3));
    await ctx.close(); }
  await b.close(); })();
