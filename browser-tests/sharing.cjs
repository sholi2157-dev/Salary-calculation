// Actual UI handlers, synthetic records, mocked OS share/clipboard only. Never contacts WhatsApp.
const assert=require('node:assert/strict'),http=require('node:http'),fs=require('node:fs'),path=require('node:path');
const {chromium}=require(process.env.PLAYWRIGHT_MODULE||'playwright');
const root=path.resolve('public'),out=process.env.SCREENSHOT_DIR||'browser-evidence';fs.mkdirSync(out,{recursive:true});
const server=http.createServer((q,r)=>{const f=path.join(root,new URL(q.url,'http://local').pathname==='/'?'index.html':new URL(q.url,'http://local').pathname);if(!f.startsWith(root)||!fs.existsSync(f)||fs.statSync(f).isDirectory()){r.writeHead(404);return r.end();}r.setHeader('Content-Type',({'.html':'text/html','.js':'application/javascript','.css':'text/css','.ttf':'font/ttf'})[path.extname(f)]||'application/octet-stream');fs.createReadStream(f).pipe(r);});
const day=new Date();day.setHours(12,0,0,0);
const base={id:'a',category:'מסעדה',date:day.getTime(),createdAt:day.getTime(),hours:5,hourlyRate:40,totalEarnings:200,currency:'₪',isPaid:false,notes:'נסיעות כלולות',isTimeRange:true,startTime:'08:00',endTime:'13:30',payKnown:true,kind:'shift',bonus:0,isGroupShift:true,employerRate:60,workerRate:35,groupWorkersJson:JSON.stringify([{name:'דני',hours:5,isPaid:false},{name:'יוסי',hours:4,workerRate:38,employerRate:65,isPaid:true,bonus:20}])};
const seed={entries:[base,{...base,id:'b',category:'שיעורים',hours:1,totalEarnings:40,isGroupShift:false,groupWorkersJson:''}],categories:[{name:'מסעדה',defaultRate:40},{name:'שיעורים',defaultRate:40}],workers:[],webPreferences:{mainCurrency:'₪',defaultCategory:'מסעדה'}};
(async()=>{await new Promise(r=>server.listen(0,r));const browser=await chromium.launch(process.env.CHROMIUM_PATH?{executablePath:process.env.CHROMIUM_PATH}:{});const errors=[];
try{for(const supported of [false,true]){
 const ctx=await browser.newContext({viewport:{width:supported?390:360,height:844},colorScheme:supported?'light':'dark',serviceWorkers:'block'});
 await ctx.addInitScript(({seed,supported})=>{localStorage.setItem('work_complete_backup',JSON.stringify(seed));localStorage.setItem('kis_web_v1',JSON.stringify({launched:2,tourDone:true}));window.copied=[];window.shared=[];
 Object.defineProperty(navigator,'clipboard',{value:{writeText:async t=>window.copied.push(t)}});
 Object.defineProperty(navigator,'share',{value:async data=>{if(window.abortShare)throw new DOMException('cancelled','AbortError');window.shared.push({text:data.text,files:await Promise.all((data.files||[]).map(async f=>({size:f.size,type:f.type,bytes:Array.from(new Uint8Array(await f.arrayBuffer())),signature:Array.from(new Uint8Array(await f.arrayBuffer()).slice(0,8))})))})}});
 Object.defineProperty(navigator,'canShare',{value:()=>supported});
 },{seed,supported});
 const page=await ctx.newPage();page.on('pageerror',e=>errors.push(e.message));await page.goto(process.env.PUBLIC_SITE_URL || `http://127.0.0.1:${server.address().port}`);await page.waitForSelector('[data-testid=home-hero]');
 const before=await page.evaluate(()=>localStorage.getItem('work_complete_backup'));
 await page.evaluate(()=>Kis.openDetail('a'));
 await page.click('[data-a=copy]');assert.match(await page.evaluate(()=>copied.at(-1)),/^עבודה: מסעדה/);assert.doesNotMatch(await page.evaluate(()=>copied.at(-1)),/היי/);
 await page.click('[data-a=share]');assert.match(await page.evaluate(()=>shared.at(-1).text),/^היי, סיכמתי פה/);
 assert.doesNotMatch(await page.evaluate(()=>shared.at(-1).text),/דני|יוסי/);
 await page.click('[data-a=employer]');const employer=await page.evaluate(()=>shared.at(-1).text);assert.match(employer,/₪780.00/);assert.doesNotMatch(employer,/175.00|172.00|עמלה/);
 await page.click('[data-w="0"]');const worker=await page.evaluate(()=>shared.at(-1).text);assert.match(worker,/היי דני/);assert.match(worker,/₪175.00/);assert.doesNotMatch(worker,/יוסי|780.00|300.00/);
 await page.screenshot({path:path.join(out,`sharing-group-${supported}.png`)});
 await page.evaluate(()=>KisUi.closeTopSheet());await page.click('[data-testid=tab-history]');await page.click('[data-testid=history-search]');await page.fill('[data-testid=history-search-input]','דני');
 await page.click('[data-testid=history-copy]');await page.click('[data-testid=copy-whatsapp]');const searched=await page.evaluate(()=>copied.at(-1));assert.match(searched,/עובד: דני/);assert.match(searched,/₪175.00/);assert.doesNotMatch(searched,/₪200.00|יוסי/);
 await page.locator('[data-testid=shift-a]').dispatchEvent('contextmenu');await page.click('[data-s=share]');assert.match(await page.evaluate(()=>shared.at(-1).text),/^היי דני/);assert.equal(await page.locator('[data-testid=selection-bar]').count(),0);
 await page.evaluate(()=>KisSummaryUi.story(Kis,String(new Date().getFullYear())));
 for(let i=0;i<12;i++){if(await page.locator('[data-testid=story-card]').count())break;await page.click('[data-testid=story-next]');}
 assert.match(await page.textContent('[data-testid=story-poster]'),/₪240/);
 if(supported){await page.waitForFunction(()=>document.querySelector('[data-testid=story-share]')?.disabled===false);const countBefore=await page.evaluate(()=>shared.length);await page.click('[data-testid=story-share]');await page.waitForFunction(n=>shared.length>n,countBefore);const pic=await page.evaluate(()=>shared.at(-1));assert.match(pic.text,/סכום: ₪200.00/);assert.match(pic.text,/סכום: ₪40.00/);assert.equal(pic.files[0].type,'image/png');assert.ok(pic.files[0].size>1000);assert.deepEqual(pic.files[0].signature,[137,80,78,71,13,10,26,10]);fs.writeFileSync(path.join(out,'sharing-generated-card.png'),Buffer.from(pic.files[0].bytes));
  // Cancelling native share must not silently copy or download.
  const copied=await page.evaluate(()=>window.copied.length);await page.evaluate(()=>window.abortShare=true);await page.click('[data-testid=story-share]');assert.equal(await page.evaluate(()=>window.copied.length),copied);
 }else assert.equal(await page.locator('[data-testid=story-share]').count(),0);
 await page.waitForTimeout(800);await page.screenshot({path:path.join(out,`sharing-recap-${supported}.png`)});
 const bounds=await page.locator('[data-testid=story-copy]').boundingBox();assert.ok(bounds.y+bounds.height<=844,'recap controls fit the viewport');
 await page.click('[data-testid=story-copy]');assert.doesNotMatch(await page.evaluate(()=>copied.at(-1)),/היי/);assert.match(await page.evaluate(()=>copied.at(-1)),/סכום: ₪200.00/);
 assert.equal(await page.evaluate(()=>localStorage.getItem('work_complete_backup')),before,'sharing never changes financial data');await ctx.close();
}assert.deepEqual(errors,[]);console.log('sharing UI: copy/share, worker search, recipient privacy, money image, unsupported browser, cancel and preservation passed');}
finally{await browser.close();server.close();}})().catch(e=>{console.error(e);server.close();process.exitCode=1});
