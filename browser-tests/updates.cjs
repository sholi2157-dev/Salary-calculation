// Real service-worker A → B → C → D releases on one unchanged origin.
// No user account, financial data, Firebase or production service is accessed.
const assert=require('node:assert/strict'),http=require('node:http'),fs=require('node:fs'),path=require('node:path'),os=require('node:os'),cp=require('node:child_process');
const {chromium}=require(process.env.PLAYWRIGHT_MODULE||'playwright');
(async()=>{
 const temporary=fs.mkdtempSync(path.join(os.tmpdir(),'salary-updates-')),versions={};
 const out=process.env.SCREENSHOT_DIR||'browser-evidence';fs.mkdirSync(out,{recursive:true});
 for(const version of ['A','B','C','D']){
  cp.execFileSync(process.execPath,['scripts/build-web.cjs'],{env:{...process.env,VERCEL_GIT_COMMIT_SHA:version.charCodeAt(0).toString(16).repeat(20)},stdio:'pipe'});
  const dir=path.join(temporary,version);fs.cpSync('public',dir,{recursive:true});versions[version]=dir;
  const html=path.join(dir,'index.html');fs.writeFileSync(html,fs.readFileSync(html,'utf8').replace('<body>','<body data-test-shell="'+version+'">'));
 }
 const legacy=path.join(temporary,'legacy');fs.mkdirSync(path.join(legacy,'web'),{recursive:true});versions.legacy=legacy;
 const baseline='0b914ea743c09b3b175ee7685bcd3eaa97eba080';
 for(const file of ['index.html','sw.js','scripts/build-web.cjs',...cp.execFileSync('git',['ls-tree','-r','--name-only',baseline,'web']).toString().trim().split('\n')]){
  const destination=path.join(legacy,file);fs.mkdirSync(path.dirname(destination),{recursive:true});fs.writeFileSync(destination,cp.execFileSync('git',['show',baseline+':'+file],{maxBuffer:2*1024*1024}));
 }
 cp.execFileSync(process.execPath,['scripts/build-web.cjs'],{cwd:legacy,env:{...process.env,VERCEL_GIT_COMMIT_SHA:baseline},stdio:'pipe'});versions.legacy=path.join(legacy,'public');
 cp.execFileSync(process.execPath,['scripts/build-web.cjs'],{stdio:'pipe'}); // Leave the actual repository's build intact.
 let release='A';const requests=[];
 const server=http.createServer((req,res)=>{
  const pathname=new URL(req.url,'http://test').pathname;requests.push({pathname,cache:req.headers['cache-control'],release});
  const file=path.resolve(versions[release],'.'+(pathname==='/'?'/index.html':pathname));
  if(!file.startsWith(versions[release]+path.sep)||!fs.existsSync(file)){res.writeHead(404);res.end();return;}
  res.setHeader('Content-Type',file.endsWith('.js')?'application/javascript':file.endsWith('.css')?'text/css':file.endsWith('.json')?'application/json':file.endsWith('.ttf')?'font/ttf':'text/html');
  res.setHeader('Cache-Control','no-store');res.end(fs.readFileSync(file));
 });
 await new Promise(r=>server.listen(0,'127.0.0.1',r));const url='http://127.0.0.1:'+server.address().port;
 const browser=await chromium.launch({executablePath:process.env.CHROMIUM_PATH||undefined,args:['--no-sandbox','--disable-dev-shm-usage','--disable-gpu'],headless:true});
 const errors=[],forbidden=[];
 const entries=Array.from({length:22},(_,i)=>({id:'kept-'+i,date:new Date(2026,8,1+i).getTime(),createdAt:100+i,category:'קטגוריה '+i%8,hours:2,hourlyRate:50,totalEarnings:100+i,currency:i%2?'₪':'$',isPaid:false,notes:'שמור '+i,groupWorkersJson:'',customExtension:'kept'}));
 const snapshot=JSON.stringify({entries,categories:Array.from({length:8},(_,i)=>({name:'קטגוריה '+i,defaultRate:50})),workers:[],webPreferences:{mainCurrency:'₪',defaultCategory:'קטגוריה 0',categoryCurrencies:{}}});
 try{
 for(const width of [360,390]){
  release='A';const context=await browser.newContext({viewport:{width,height:844},hasTouch:true,isMobile:true,locale:'he-IL'});
  context.on('request',r=>{if(/firebase|gstatic|googleapis|\/api\/config|\/web\/(accounts|cloud-sync)\.js/.test(r.url()))forbidden.push(r.url());});
  await context.addInitScript(({snapshot})=>{
   if(location.protocol==='http:'&&!sessionStorage.getItem('seeded')){
    localStorage.setItem('work_complete_backup',snapshot);localStorage.setItem('work_account_v1:old-owner','{"entries":[{"id":"account-only"}]}');localStorage.setItem('firebase:authUser:old:DEFAULT','synthetic-old-session');localStorage.setItem('work_pre_rc13_snapshot_v1','do-not-change');localStorage.setItem('work_onboarding_version','1');
    localStorage.setItem('work_active_shift_v1:guest',JSON.stringify({id:'ongoing',category:'קטגוריה 0',rate:50,currency:'₪',startedAt:Date.now()-3600000}));sessionStorage.setItem('seeded','yes');
   }
   // If dormant account code ever initializes, this poisons the visible dataset.
   window.firebase={get apps(){throw Error('Firebase initialized while disabled');},auth(){throw Error('Old session activated');},firestore(){throw Error('Firestore initialized');}};
  },{snapshot});
  const page=await context.newPage();page.on('pageerror',e=>errors.push(e.message));page.on('dialog',d=>{errors.push('Native alert');d.dismiss();});
  await page.goto(url,{waitUntil:'load'});await page.waitForFunction(()=>navigator.serviceWorker.controller&&window.WorkBuild);
  assert.equal(await page.locator('#account-section').count(),0);assert.equal(await page.locator('#account-dialog').count(),0);assert.equal(await page.evaluate(()=>currentUserId),null);assert.deepEqual(await page.evaluate(()=>shifts),entries);
  await page.getByRole('button',{name:'ניהול וקטגוריות',exact:true}).click();assert.doesNotMatch(await page.locator('#settings-dialog').innerText(),/חשבון משתמש|התחברות|סנכרון/);await page.locator('#settings-dialog').getByRole('button',{name:'ביטול',exact:true}).click();
  await page.getByRole('button',{name:'דיווח חדש',exact:true}).click();await page.locator('#modal-notes').fill('טיוטה לעדכון '+width);await page.locator('#modal-rate').fill('77');
  // Existing supported draft is stored before update; not a financial save.
  const before=await page.evaluate(()=>({...localStorage}));assert.equal(before.work_complete_backup,snapshot);
  assert.equal(await page.locator('#web-update-notice').isVisible(),false);
  release='B';await page.evaluate(()=>WebUpdateManager.check(true));await page.locator('#web-update-notice').waitFor({state:'visible'});
  assert.equal(await page.evaluate(()=>document.body.dataset.testShell),'A'); // No mixed network HTML / cached JS.
  assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth),true);
  const b=await page.locator('#web-update-now').boundingBox();assert.ok(b.width>=44&&b.height>=44);
  await page.screenshot({path:path.join(out,'update-'+width+'.png')});
  await page.getByRole('button',{name:'אחר כך',exact:true}).click();assert.equal(await page.locator('#web-update-notice').isVisible(),false);assert.deepEqual(await page.evaluate(()=>({...localStorage})),before);
  await page.evaluate(()=>WebUpdateManager.check(true));assert.equal(await page.locator('#web-update-notice').isVisible(),false);
  release='C';await page.evaluate(()=>WebUpdateManager.check(true));await page.locator('#web-update-notice').waitFor({state:'visible'});
  // Updating while Settings contains an unsubmitted change must not discard it.
  await page.getByRole('button',{name:'ניהול וקטגוריות',exact:true}).click();await page.getByRole('button',{name:'מטבע ראשי דולר',exact:true}).click();await page.evaluate(()=>WebUpdateManager.updateNow());assert.equal(await page.evaluate(()=>document.body.dataset.testShell),'A');assert.equal(await page.locator('#settings-dialog').isVisible(),true);assert.deepEqual(await page.evaluate(()=>({...localStorage})),before);await page.locator('#settings-dialog').getByRole('button',{name:'ביטול',exact:true}).click();
  let navigations=0;page.on('framenavigated',frame=>{if(frame===page.mainFrame())navigations++;});
  await page.getByRole('button',{name:'עדכן עכשיו',exact:true}).click();await page.waitForFunction(()=>document.body.dataset.testShell==='C');await page.waitForFunction(()=>navigator.serviceWorker.controller);await page.waitForTimeout(700);
  assert.equal(navigations,1);assert.equal(await page.locator('#modal-notes').inputValue(),'טיוטה לעדכון '+width);assert.equal(await page.locator('#modal-rate').inputValue(),'77');assert.match(await page.locator('#active-shift-timer').innerText(),/01:00:/);assert.deepEqual(await page.evaluate(()=>({...localStorage})),before);assert.equal(await page.locator('#web-update-notice').isVisible(),false);
  // A second deployed shell must replace C; proving the updater isn't a one-off.
  release='D';await page.evaluate(()=>WebUpdateManager.check(true));await page.locator('#web-update-notice').waitFor({state:'visible'});await page.getByRole('button',{name:'עדכן עכשיו',exact:true}).click();await page.waitForFunction(()=>document.body.dataset.testShell==='D');await page.waitForTimeout(700);assert.equal(navigations,2);assert.deepEqual(await page.evaluate(()=>({...localStorage})),before);
  const cached=await page.evaluate(async()=>{const names=await caches.keys();return {names,urls:await Promise.all(names.filter(k=>k.startsWith('salary-web-')).map(async k=>(await(await caches.open(k)).keys()).map(r=>new URL(r.url).pathname)))};});
  assert.equal(cached.names.filter(k=>k.startsWith('salary-web-')).length,1);assert.ok(cached.urls.flat().includes('/web/build.js'));assert.ok(!cached.urls.flat().includes('/build-info.json'));assert.ok(!cached.urls.flat().some(u=>u.startsWith('/api/')));
  await context.setOffline(true);await page.reload({waitUntil:'load'});assert.equal(await page.evaluate(()=>document.body.dataset.testShell),'D');assert.deepEqual(await page.evaluate(()=>shifts),entries);assert.deepEqual(await page.evaluate(()=>({...localStorage})),before);assert.equal(await page.locator('#web-update-notice').isVisible(),false);await context.setOffline(false);
  await context.close();console.log('PASS: disabled accounts/old session, exact 22/8, later, edit delay, A→C→D exactly one reload per update, draft/timer/storage, offline latest shell, RTL '+width+'px');
 }
 // Bridge from the exact previous cache-first-JS/network-HTML RC13 worker.
 release='legacy';const old=await browser.newContext({viewport:{width:390,height:844}});await old.route('https://www.gstatic.com/**',r=>r.abort());
 await old.addInitScript(({snapshot})=>{if(location.protocol==='http:'&&!sessionStorage.getItem('seeded')){localStorage.setItem('work_complete_backup',snapshot);localStorage.setItem('work_onboarding_version','1');sessionStorage.setItem('seeded','1');}},{snapshot});
 const oldPage=await old.newPage();oldPage.on('pageerror',e=>errors.push(e.message));await oldPage.goto(url,{waitUntil:'load'});await oldPage.waitForFunction(()=>navigator.serviceWorker.controller);
 release='A';await oldPage.reload({waitUntil:'load'});await oldPage.locator('#web-update-notice').waitFor({state:'visible'});assert.equal(await oldPage.evaluate(()=>typeof prepareWebUpdate),'undefined'); // old experience.js really came from v2 cache
 await oldPage.getByRole('button',{name:'דיווח חדש',exact:true}).click();await oldPage.locator('#modal-notes').fill('טיוטת מעבר RC13');await oldPage.getByRole('button',{name:'עדכן עכשיו',exact:true}).click();await oldPage.waitForFunction(()=>document.body.dataset.testShell==='A'&&typeof prepareWebUpdate==='function');assert.equal(await oldPage.locator('#modal-notes').inputValue(),'טיוטת מעבר RC13');assert.equal(await oldPage.evaluate(()=>localStorage.getItem('work_complete_backup')),snapshot);await old.close();console.log('PASS: exact completed RC13 worker upgrades safely into controlled shell without losing draft/data.');
 assert.deepEqual(forbidden,[]);assert.deepEqual(errors,[]);assert.ok(requests.some(r=>r.pathname==='/build-info.json'&&r.cache?.includes('no-cache')));console.log('PASS: no account SDK/config/sync requests, no application errors, build info bypasses caches.');
 }finally{await browser.close();server.close();fs.rmSync(temporary,{recursive:true,force:true});}
})().catch(e=>{console.error(e);process.exitCode=1;});
