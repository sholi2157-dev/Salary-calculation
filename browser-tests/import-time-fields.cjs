// C-4: actual import handlers, disposable synthetic storage only.
const assert = require('node:assert/strict'), http = require('node:http'), fs = require('node:fs'), path = require('node:path');
const { chromium } = require(process.env.PLAYWRIGHT_MODULE || 'playwright');
const ROOT = path.resolve('public');
const rich = JSON.parse(fs.readFileSync('web-tests/fixtures/synthetic/rich-v2.json', 'utf8'));
const types = { '.html': 'text/html; charset=utf-8', '.js': 'text/javascript', '.css': 'text/css', '.json': 'application/json', '.ttf': 'font/ttf' };
const server = http.createServer((q, r) => {
  let p = decodeURIComponent(new URL(q.url, 'http://x').pathname); if (p === '/') p = '/index.html';
  if (p === '/api/config') { r.writeHead(200, { 'content-type': 'application/json' }); return r.end('{}'); }
  const f = path.join(ROOT, p); if (!f.startsWith(ROOT) || !fs.existsSync(f) || fs.statSync(f).isDirectory()) { r.writeHead(404); return r.end(); }
  r.writeHead(200, { 'content-type': types[path.extname(f)] || 'application/octet-stream', 'cache-control': 'no-store' }); fs.createReadStream(f).pipe(r);
});
(async () => {
 await new Promise(r => server.listen(0, '127.0.0.1', r));
 const browser = await chromium.launch({executablePath:process.env.CHROMIUM_PATH || undefined});
 try {
  const ctx = await browser.newContext({serviceWorkers:'block'});
  await ctx.route('**/*', route => new URL(route.request().url()).hostname === '127.0.0.1' ? route.continue() : route.abort());
  await ctx.addInitScript(data => {
   if (sessionStorage.getItem('seeded')) return;
   sessionStorage.setItem('seeded','yes');
   for (const [key,value] of Object.entries(data)) localStorage.setItem(key,JSON.stringify(value));
   localStorage.setItem('kis_web_v1',JSON.stringify({launched:2,tourDone:true}));
   localStorage.setItem('kis_ai_invited_v1','1');localStorage.setItem('kis_signin_skip_v1','1');
  },rich);
  const page = await ctx.newPage(), errors=[];
  page.on('pageerror',e=>errors.push(e.message));
  await page.goto(`http://127.0.0.1:${server.address().port}`);
  await page.waitForSelector('[data-testid=home-hero]');
  const snapshot=()=>page.evaluate(()=>({storage:{...localStorage},data:Kis.store.data,plans:Kis.store.plans}));
  const before=await snapshot();
  await page.evaluate(()=>KisSettings.importSheet(Kis));
  const entry={...rich.work_complete_backup.entries[0],category:'ייבוא סינתטי חדש',startTime:'00:00',endTime:'23:59',hours:7.123456789,totalEarnings:301.17};
  const valid={formatVersion:2,entries:[entry],categories:[{name:entry.category,defaultRate:43.21}],workers:[{name:'סינתטי חדש'}]};
  const fill=async data=>{await page.fill('[data-testid=import-text]',JSON.stringify(data));await page.click('[data-testid=import-check]');};
  // A previously validated preview must not survive a subsequent invalid input.
  await fill(valid);assert.equal(await page.isDisabled('[data-testid=import-save]'),false);
  for (const field of ['startTime','endTime']) for (const value of ['=1+1',930,'24:00','23:60',{}]) {
   await fill({...valid,entries:[entry,{...entry,[field]:value}]});
   assert.equal(await page.isDisabled('[data-testid=import-save]'),true);
   assert.match(await page.textContent('[data-testid=import-summary]'),/רשומה 2: שעת .* לא תקינה/);
   // Exercise the actual save handler as well as its disabled button.
   await page.evaluate(()=>document.querySelector('[data-testid=import-save]').onclick());
   assert.deepEqual(await snapshot(),before);
  }
  await page.reload();await page.waitForSelector('[data-testid=home-hero]');
  assert.deepEqual(await snapshot(),before);
  await page.evaluate(()=>KisSettings.importSheet(Kis));await fill(valid);await page.click('[data-testid=import-save]');
  const after=await snapshot();
  assert.deepEqual(after.data.entries.slice(0,before.data.entries.length),before.data.entries);
  assert.equal(after.data.entries.length,before.data.entries.length+1);
  const added=after.data.entries.at(-1);
  for(const key of ['category','startTime','endTime','hours','totalEarnings'])assert.equal(added[key],entry[key]);
  assert.deepEqual(errors,[]);
  console.log('C-4: invalid imports preserve all storage and memory; stale preview blocked; valid import preserves history and exact amounts.');
  await ctx.close();
 } finally {await browser.close();server.close();}
})().catch(e=>{console.error(e);server.close();process.exitCode=1;});
