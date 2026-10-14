// RC13 release-critical flows, disposable browser contexts and synthetic fixtures only.
const assert=require('node:assert/strict'),http=require('node:http'),fs=require('node:fs'),path=require('node:path');
const {chromium}=require(process.env.PLAYWRIGHT_MODULE||'playwright');
(async()=>{
 const root=path.resolve('public'),out=process.env.SCREENSHOT_DIR||'browser-evidence';fs.mkdirSync(out,{recursive:true});
 const server=http.createServer((req,res)=>{const target=path.resolve(root,'.'+(req.url.split('?')[0]==='/'?'/index.html':req.url.split('?')[0]));if(!target.startsWith(root+path.sep)||!fs.existsSync(target)){res.writeHead(404);res.end();return;}res.setHeader('Content-Type',target.endsWith('.js')?'application/javascript':target.endsWith('.css')?'text/css':target.endsWith('.ttf')?'font/ttf':'text/html');res.end(fs.readFileSync(target));});
 await new Promise(resolve=>server.listen(0,'127.0.0.1',resolve));const url='http://127.0.0.1:'+server.address().port;
 const browser=await chromium.launch({executablePath:process.env.CHROMIUM_PATH||undefined,args:['--no-sandbox','--disable-dev-shm-usage','--disable-gpu'],headless:true});
 const errors=[];
 try{
 for(const width of [360,390,768,1440]){
  const context=await browser.newContext({viewport:{width,height:844},locale:'he-IL'});
  await context.route('https://www.gstatic.com/**',r=>r.abort());
  await context.route('**/_vercel/**',r=>r.abort());
  const p=await context.newPage();p.on('pageerror',e=>errors.push(e.message));
  await p.goto(url,{waitUntil:'domcontentloaded'});
  await p.evaluate(()=>{localStorage.setItem('work_onboarding_version','1');localStorage.setItem('work_complete_backup',JSON.stringify({entries:[{id:'audit',category:'Work',date:Date.now(),createdAt:1,hours:2,hourlyRate:40,totalEarnings:79.13,isPaid:false,currency:'$',notes:'saved',isTimeRange:false,isGroupShift:false,groupWorkersJson:''}],categories:[{name:'Work',defaultRate:40}],workers:[],webPreferences:{mainCurrency:'$',defaultCategory:'Work',categoryCurrencies:{}}}));});
  await p.reload({waitUntil:'domcontentloaded'});
  await p.evaluate(()=>editShift('audit'));await p.locator('#modal-notes').fill('my unsaved edit');
  const other=await context.newPage();await other.goto(url,{waitUntil:'domcontentloaded'});
  await other.evaluate(()=>{const d=JSON.parse(localStorage.getItem('work_complete_backup'));d.entries[0].notes='newer edit';d.entries[0].totalEarnings=91.23;localStorage.setItem('work_complete_backup',JSON.stringify(d));});
  // Save while another tab owns the newer row. Draft stays open and persisted values stay exact.
  await p.locator('#add-modal').getByRole('button',{name:'שמירה',exact:true}).click();
  assert.equal(await p.locator('#add-modal').isVisible(),true);assert.equal(await p.locator('#modal-notes').inputValue(),'my unsaved edit');
  let saved=await p.evaluate(()=>JSON.parse(localStorage.getItem('work_complete_backup')).entries[0]);assert.equal(saved.notes,'newer edit');assert.equal(saved.totalEarnings,91.23);
  assert.equal(await p.evaluate(()=>document.documentElement.scrollWidth<=innerWidth),true);
  await p.screenshot({path:path.join(out,'audit-conflict-'+width+'.png')});
  await p.evaluate(()=>closeAddModal());await p.evaluate(()=>editShift('audit'));await p.locator('#modal-notes').fill('another draft');
  await other.evaluate(()=>{const d=JSON.parse(localStorage.getItem('work_complete_backup'));d.entries=[];localStorage.setItem('work_complete_backup',JSON.stringify(d));});
  await p.locator('#add-modal').getByRole('button',{name:'שמירה',exact:true}).click();assert.equal(await p.locator('#add-modal').isVisible(),true);assert.equal(await p.locator('#modal-notes').inputValue(),'another draft');
  assert.deepEqual(await p.evaluate(()=>JSON.parse(localStorage.getItem('work_complete_backup')).entries),[]);
  await context.close();
 }
 assert.deepEqual(errors,[]);console.log('Audit: cross-tab edit/delete conflicts preserve drafts and stored data at four widths');
 }finally{await browser.close();await new Promise(r=>server.close(r));}
})().catch(e=>{console.error(e);process.exit(1);});
