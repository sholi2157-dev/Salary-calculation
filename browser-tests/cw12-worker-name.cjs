// 2.3.0 (audit CW-12): typing a group worker's name updates the save button at once, and focus stays in the field. Synthetic data.
const { chromium } = require(process.env.PLAYWRIGHT_MODULE || 'playwright'); const http=require('http'),fs=require('fs'),path=require('path'),assert=require('assert/strict');
const ROOT=path.resolve('public'), rich=JSON.parse(fs.readFileSync('web-tests/fixtures/synthetic/rich-v2.json','utf8'));
const s=http.createServer((q,r)=>{let p=decodeURIComponent(new URL(q.url,'http://x').pathname);if(p==='/')p='/index.html';if(p==='/api/config'){r.end('{}');return;}const f=path.join(ROOT,p);if(!fs.existsSync(f)||fs.statSync(f).isDirectory()){r.writeHead(404);return r.end();}r.writeHead(200,{'content-type':{'.html':'text/html; charset=utf-8','.js':'text/javascript','.css':'text/css','.ttf':'font/ttf'}[path.extname(f)]||'application/octet-stream'});fs.createReadStream(f).pipe(r);});
s.listen(0,async()=>{const b=await chromium.launch();const c=await b.newContext({viewport:{width:390,height:844},locale:'he-IL',serviceWorkers:'block'});
await c.addInitScript(d=>{if(!sessionStorage.getItem('s')){sessionStorage.setItem('s',1);localStorage.clear();for(const[k,v]of Object.entries(d))localStorage.setItem(k,JSON.stringify(v));localStorage.setItem('kis_web_v1',JSON.stringify({launched:2,tourDone:true}));localStorage.setItem('kis_ai_invited_v1','1');localStorage.setItem('kis_signin_skip_v1','1');}},rich);
const p=await c.newPage(); const errs=[]; p.on('pageerror',e=>errs.push(e.message));
await p.goto('http://127.0.0.1:'+s.address().port+'/');await p.waitForSelector('[data-testid=home-hero]');
await p.evaluate(()=>window.Kis.openNew()); await p.waitForTimeout(300);
await p.click('[data-mode="HOURS"]'); await p.getByText('קבוצה').first().click(); await p.waitForTimeout(200);
await p.click('[data-testid=add-worker]'); await p.waitForTimeout(200);
const save='[data-testid=save-shift]'; assert.equal(await p.isDisabled(save), true, 'nameless worker blocks save');
await p.locator('[data-w$=":name"]').first().type('דני'); await p.waitForTimeout(200);
assert.equal(await p.isDisabled(save), false, 'typing the name enables save without another field change');
assert.equal(await p.evaluate(()=>document.activeElement?.dataset?.w), '0:name', 'focus stays in the name');
assert.deepEqual(errs,[]); console.log('cw12 ok'); await b.close(); s.close();});
