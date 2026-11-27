// Owner 2026-11-27: one new shift saved on several days picked in the month calendar (same hours and details). Synthetic data.
const { chromium } = require(process.env.PLAYWRIGHT_MODULE || 'playwright'); const http=require('http'),fs=require('fs'),path=require('path'),assert=require('assert/strict');
const ROOT=path.resolve('public'), rich=JSON.parse(fs.readFileSync('web-tests/fixtures/synthetic/rich-v2.json','utf8'));
const s=http.createServer((q,r)=>{let p=decodeURIComponent(new URL(q.url,'http://x').pathname);if(p==='/')p='/index.html';if(p==='/api/config'){r.end('{}');return;}const f=path.join(ROOT,p);if(!fs.existsSync(f)||fs.statSync(f).isDirectory()){r.writeHead(404);return r.end();}r.writeHead(200,{'content-type':{'.html':'text/html; charset=utf-8','.js':'text/javascript','.css':'text/css','.ttf':'font/ttf'}[path.extname(f)]||'application/octet-stream'});fs.createReadStream(f).pipe(r);});
s.listen(0,async()=>{const b=await chromium.launch();const c=await b.newContext({viewport:{width:390,height:844},locale:'he-IL',serviceWorkers:'block'});
await c.addInitScript(d=>{if(!sessionStorage.getItem('s')){sessionStorage.setItem('s',1);localStorage.clear();for(const[k,v]of Object.entries(d))localStorage.setItem(k,JSON.stringify(v));localStorage.setItem('kis_web_v1',JSON.stringify({launched:2,tourDone:true}));localStorage.setItem('kis_ai_invited_v1','1');localStorage.setItem('kis_signin_skip_v1','1');}},rich);
const p=await c.newPage(); const errs=[]; p.on('pageerror',e=>errs.push(e.message));
await p.goto('http://127.0.0.1:'+s.address().port+'/');await p.waitForSelector('[data-testid=home-hero]');
await p.evaluate(()=>window.Kis.openNew()); await p.waitForTimeout(300);
await p.click('[data-mode="CLOCK"]').catch(()=>{});
await p.click('.time-box[data-t=START] > .caption'); await p.keyboard.type('0900'); await p.click('.time-box[data-t=END] > .caption'); await p.keyboard.type('1700'); await p.keyboard.press('Enter');

const shot=process.env.SHOTS?n=>p.screenshot({path:path.join(process.env.SHOTS,n+'.png')}):async()=>{};
await p.click('[data-testid=editor-other-date]'); await p.waitForSelector('[data-testid=days-sheet]');
const today=new Date().getDate(), last=new Date(new Date().getFullYear(),new Date().getMonth()+1,0).getDate();
const others=[1,2,3,4,5].map(k=>((today-1+k*5)%last)+1).filter(x=>x!==today).slice(0,2);
for(const x of others) await p.click(`[data-testid=month-day-${x}]`);
assert.match(await p.textContent('[data-testid=days-done]'),/3 ימים/);
await shot('web-several-days-sheet');
assert.ok(await p.$('[data-testid=month-title-heb]'),'both: Hebrew months under the title');
await p.click('[data-testid=days-done]');
assert.match(await p.textContent('[data-testid=editor-other-date]'),/3 ימים/);
assert.equal(await p.$('[data-testid=editor-repeat]'),null,'no weekly repeat with several days');
assert.match(await p.textContent('[data-testid=save-shift]'),/שמירת 3 משמרות/);
await shot('web-several-days');
const before=await p.evaluate(()=>window.Kis.store.data.entries.length);
await p.click('[data-testid=save-shift]'); await p.waitForTimeout(300);
const after=await p.evaluate(()=>window.Kis.store.data.entries.length);
assert.equal(after-before,3,'three shifts saved');
const fresh=await p.evaluate(n=>window.Kis.store.data.entries.filter(e=>new Date(e.date).getHours()===9),before);
const mine=fresh.slice(-3);
assert.equal(new Set(mine.map(e=>new Date(e.date).getDate())).size,3,'three different days');
for(const e of mine){assert.equal(e.hours,8); assert.equal(e.hourlyRate,mine[0].hourlyRate); assert.equal(e.category,mine[0].category);}
await p.waitForSelector('text=נשמרו 3 משמרות');
await p.click('.toast button, [data-testid=toast-action]').catch(()=>{});
await p.waitForTimeout(300);
assert.equal(await p.evaluate(()=>window.Kis.store.data.entries.length),before,'one undo removes all three');
// Hebrew only (Settings): Hebrew months and Hebrew day numbers.
await p.evaluate(()=>{window.KisHebrew.setMode('HEB'); window.Kis.openNew();}); await p.waitForTimeout(300);
await p.click('[data-testid=editor-other-date]'); await p.waitForSelector('[data-testid=days-sheet]');
const [hy,hm,hd]=await p.evaluate(()=>window.KisHebrew.fromMillis(Date.now()));
const want=await p.evaluate(([y,m])=>window.KisHebrew.monthName(y,m)+' '+window.KisHebrew.gematria(y),[hy,hm]);
assert.equal((await p.textContent('[data-testid=month-title]')).trim(),want);
assert.equal(await p.getAttribute(`[data-testid=month-day-${hd}]`,'aria-pressed'),'true');
assert.equal(await p.$('[data-testid=month-title-heb]'),null);
await shot('web-days-hebrew');
await p.click('[data-testid=month-next]'); await p.click('[data-testid=month-prev]');
assert.equal((await p.textContent('[data-testid=month-title]')).trim(),want,'month arrows go and come back');
assert.deepEqual(errs,[]); console.log('multi-day ok'); await b.close(); s.close();});
