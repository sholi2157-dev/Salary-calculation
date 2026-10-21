const {setDuration,durationValue}=require('./duration.cjs');
// Real workers, same URL: legacy migration, A → B → C, offline, no forced reload.
const assert=require('node:assert/strict'),http=require('node:http'),fs=require('node:fs'),path=require('node:path'),os=require('node:os'),cp=require('node:child_process');
const {chromium}=require(process.env.PLAYWRIGHT_MODULE||'playwright');
(async()=>{
 const temporary=fs.mkdtempSync(path.join(os.tmpdir(),'salary-auto-')),versions={};
 const out=process.env.SCREENSHOT_DIR||'browser-evidence';fs.mkdirSync(out,{recursive:true});
 for(const version of ['A','B','C']){
  cp.execFileSync(process.execPath,['scripts/build-web.cjs'],{env:{...process.env,VERCEL_GIT_COMMIT_SHA:version.repeat(40)},stdio:'pipe'});
  const dir=path.join(temporary,version);fs.cpSync('public',dir,{recursive:true});versions[version]=dir;
 }
 // Exact deployed cache-first worker, with the current app as a synthetic legacy shell.
 const legacy=path.join(temporary,'legacy');fs.cpSync(versions.A,legacy,{recursive:true});versions.legacy=legacy;
 fs.writeFileSync(path.join(legacy,'sw.js'),cp.execFileSync('git',['show','96e3e4bb52a6a693fb728c682295934b724fa7c3:sw.js']).toString().replace('__WEB_BUILD_ID__','legacy'));
 // The shipped worker cached unversioned URLs. Model that document faithfully.
 fs.writeFileSync(path.join(legacy,'index.html'),fs.readFileSync(path.join(legacy,'index.html'),'utf8').replace(/\?v=[^"]+/g,''));
 fs.writeFileSync(path.join(legacy,'web/build.js'),'window.WorkBuild={buildId:"legacy",commit:"legacy"};');
 cp.execFileSync(process.execPath,['scripts/build-web.cjs'],{stdio:'pipe'});
 let release='legacy',broken=false,serverOffline=false,failedAssets=0;
 const server=http.createServer((req,res)=>{
  if(serverOffline){req.socket.destroy();return;}
  const pathname=new URL(req.url,'http://test').pathname;
  if(pathname==='/index.html'){res.writeHead(308,{Location:'/'});return res.end();}
  const file=path.resolve(versions[release],'.'+(pathname==='/'?'/index.html':pathname));
  if(broken&&pathname==='/web/kis/editor.js')failedAssets++;
  if(!file.startsWith(versions[release]+path.sep)||!fs.existsSync(file)||(broken&&pathname==='/web/kis/editor.js')){res.writeHead(503);return res.end();}
  res.setHeader('Content-Type',file.endsWith('.js')?'application/javascript':file.endsWith('.css')?'text/css':file.endsWith('.json')?'application/json':file.endsWith('.ttf')?'font/ttf':'text/html');
  res.setHeader('Cache-Control','no-store');res.end(fs.readFileSync(file));
 });
 await new Promise(r=>server.listen(0,'127.0.0.1',r));const url='http://127.0.0.1:'+server.address().port;
 const browser=await chromium.launch({executablePath:process.env.CHROMIUM_PATH||undefined,args:['--no-sandbox'],headless:true});
 const errors=[];
 const entries=Array.from({length:22},(_,i)=>({id:'kept-'+i,date:new Date(2026,8,1+i).getTime(),createdAt:100+i,category:'קטגוריה '+i%8,hours:2,hourlyRate:50,totalEarnings:100+i,currency:i%2?'₪':'$',isPaid:false,notes:'שמור '+i,groupWorkersJson:'',customExtension:'kept'}));
 const snapshot=JSON.stringify({entries,categories:Array.from({length:8},(_,i)=>({name:'קטגוריה '+i,defaultRate:50})),workers:[],webPreferences:{mainCurrency:'₪',defaultCategory:'קטגוריה 0',categoryCurrencies:{}}});
 async function waitCheck(page, predicate, arg) {
  const deadline=Date.now()+30000;
  while(Date.now()<deadline){if(await page.evaluate(predicate,arg))return;await page.waitForTimeout(50);}
  throw Error('Worker condition timed out');
 }
 async function ready(page,version){await waitCheck(page,async expected=>{const worker=navigator.serviceWorker.controller;if(!worker||worker.state!=='activated')return false;return new Promise(resolve=>{const c=new MessageChannel();c.port1.onmessage=e=>{c.port1.close();resolve(e.data.buildId.startsWith(expected));};worker.postMessage({type:'GET_BUILD_INFO'},[c.port2]);});},version);}

 try{
 const context=await browser.newContext({viewport:{width:390,height:844},locale:'he-IL'});
 await context.addInitScript(snapshot=>{if(location.protocol==='http:'&&!localStorage.getItem('work_complete_backup')){localStorage.setItem('work_complete_backup',snapshot);localStorage.setItem('kis_web_v1',JSON.stringify({launched:2,tourDone:true}));localStorage.setItem('work_account_v1:dormant','keep');}},snapshot);
 const page=await context.newPage();page.on('pageerror',e=>errors.push(e.message));await page.goto(url);await ready(page,'legacy');await page.waitForFunction(()=>navigator.serviceWorker.controller);
 // Finish the first registration job before simulating a subsequent deployment.
 await page.evaluate(async()=>{await navigator.serviceWorker.register('/sw.js',{updateViaCache:'none'});});
 const before=await page.evaluate(()=>({...localStorage}));
 // Migration automatically activates, without clicking a legacy update button.
 release='A';await page.evaluate(async()=>{await(await navigator.serviceWorker.getRegistration()).update();});await ready(page,'A');
 assert.equal(await page.evaluate(()=>WorkBuild.buildId),'legacy');
 await page.reload();
 assert.equal(await page.evaluate(()=>WorkBuild.commit),'A'.repeat(40));assert.deepEqual(await page.evaluate(()=>({...localStorage})),before);
 await page.click('[data-testid=tab-settings]');assert.doesNotMatch(await page.locator('#page').innerText(),/בדיקת עדכון|גרסה|2\.0|AAAAAAA|עזרה ועדכונים/);assert.equal(await page.locator('#web-update-notice').count(),0);
 await page.click('[data-testid=tab-home]');await page.click('[data-testid=live-start]');await page.click('[data-testid=live-go]');
 await page.click('[data-testid=add-shift]');await page.click('[data-testid=mode-hours]');await setDuration(page,'1:40');
 const draft=await page.evaluate(()=>localStorage.getItem('kis_web_draft_v1'));assert.ok(draft);
 let navigations=0;page.on('framenavigated',f=>{if(f===page.mainFrame())navigations++;});
 release='B';const background=await context.newPage();await background.goto(url);await ready(background,'B');await background.close();
 assert.equal(navigations,0);assert.equal(await durationValue(page),'1:40');assert.equal(await page.evaluate(()=>WorkBuild.commit),'A'.repeat(40));
 await page.reload();assert.equal(await page.evaluate(()=>WorkBuild.commit),'B'.repeat(40));assert.equal(navigations,1);assert.equal(await durationValue(page),'1:40');assert.equal(await page.evaluate(()=>localStorage.getItem('work_complete_backup')),snapshot);
 serverOffline=true;await context.setOffline(true);await page.reload();assert.equal(await page.evaluate(()=>WorkBuild.commit),'B'.repeat(40));assert.equal(await durationValue(page),'1:40');serverOffline=false;await context.setOffline(false);
 // Finish the current page's registration before testing a separate failed install.
 await page.evaluate(async()=>{await navigator.serviceWorker.register('/sw.js',{updateViaCache:'none'});});
 // Failed partial installation cannot replace the complete offline shell.
 release='C';broken=true;await page.evaluate(async()=>{try{await(await navigator.serviceWorker.getRegistration()).update();}catch{}});
 const failedDeadline=Date.now()+10000;while(!failedAssets&&Date.now()<failedDeadline)await page.waitForTimeout(50);assert.ok(failedAssets,'C installation actually requested the unavailable asset');
 await waitCheck(page,async()=>!(await navigator.serviceWorker.getRegistration()).installing);await ready(page,'B');
 serverOffline=true;await context.setOffline(true);await page.reload();assert.equal(await page.evaluate(()=>WorkBuild.commit),'B'.repeat(40));serverOffline=false;await context.setOffline(false);broken=false;
 // Next ordinary opening sees C even if B is still its controller.
 const reopened=await context.newPage();await reopened.goto(url);assert.equal(await reopened.evaluate(()=>WorkBuild.commit),'C'.repeat(40));await ready(reopened,'C');
 assert.equal(await durationValue(reopened),'1:40');assert.equal(await reopened.evaluate(()=>localStorage.getItem('work_complete_backup')),snapshot);
 await reopened.click('[data-testid=editor-close]');await reopened.click('[data-testid=confirm-ok]');assert.ok(await reopened.isVisible('[data-testid=live-card]'));
 await reopened.click('[data-testid=tab-settings]');await reopened.screenshot({path:path.join(out,'automatic-updates-settings.png'),fullPage:true});
 // Activation also leaves an unsaved edit in another open tab alone.
 await setDuration(page,'2:35');assert.equal(await durationValue(page),'2:35');assert.equal(await page.evaluate(()=>localStorage.getItem('work_account_v1:dormant')),'keep');
 assert.deepEqual(errors,[]);await context.close();console.log('PASS automatic same-origin updates: exact shipped worker migration, A/B/C, no forced reload or update/version UI, 22/8 saved amounts, draft/timer/dormant data, offline and failed install.');
 }finally{await browser.close();server.close();fs.rmSync(temporary,{recursive:true,force:true});}
})().catch(e=>{console.error(e);process.exitCode=1;});
