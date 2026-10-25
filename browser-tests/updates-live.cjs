const {setDuration,durationValue}=require('./duration.cjs');
// Public origin, isolated profile and synthetic data only. Real service worker allowed.
const assert=require('node:assert/strict'),fs=require('node:fs');
const {chromium}=require(process.env.PLAYWRIGHT_MODULE||'playwright');
(async()=>{
 const base=process.env.PUBLIC_SITE_URL;if(!base)throw Error('PUBLIC_SITE_URL required');
 const expected=JSON.parse(fs.readFileSync('docs/rc15-web-live.json')).buildId;
 const browser=await chromium.launch();const context=await browser.newContext({viewport:{width:390,height:844},locale:'he-IL'});
 try{
  await context.addInitScript(()=>{if(!localStorage.getItem('work_complete_backup')){localStorage.setItem('work_complete_backup',JSON.stringify({entries:[{id:'live-update-test',category:'בדיקה',date:1760000000000,createdAt:1,hours:1.5,hourlyRate:40,totalEarnings:59.17,currency:'$',isPaid:false,notes:'שמור',customExtension:'keep'}],categories:[{name:'בדיקה',defaultRate:40}],workers:[],webPreferences:{mainCurrency:'$',defaultCategory:'בדיקה'}}));localStorage.setItem('kis_web_v1',JSON.stringify({launched:2,tourDone:true}));}});
  const page=await context.newPage(),errors=[];page.on('pageerror',e=>errors.push(e.message));
  await page.goto(base);await page.waitForFunction(()=>navigator.serviceWorker.controller);
  assert.equal(await page.evaluate(()=>WorkBuild.buildId),expected);
  await page.click('[data-testid=tab-settings]');assert.doesNotMatch(await page.locator('#page').innerText(),/בדיקת עדכון|גרסה|עזרה ועדכונים|2\.0/);assert.equal(await page.locator('#web-update-notice').count(),0);
  const before=await page.evaluate(()=>localStorage.getItem('work_complete_backup'));
  await page.click('[data-testid=tab-home]');await page.click('[data-testid=live-start]');await page.click('[data-testid=live-go]');
  await page.click('[data-testid=add-shift]');await page.click('[data-testid=mode-hours]');await setDuration(page,'1:40');
  await page.reload();await page.waitForSelector('[data-testid=hours-input]');assert.equal(await durationValue(page),'1:40');assert.equal(await page.evaluate(()=>WorkBuild.buildId),expected);
  await context.setOffline(true);await page.reload();await page.waitForSelector('[data-testid=hours-input]');assert.equal(await durationValue(page),'1:40');assert.equal(await page.evaluate(()=>WorkBuild.buildId),expected);assert.equal(await page.evaluate(()=>localStorage.getItem('work_complete_backup')),before);
  await page.click('[data-testid=editor-close]');await page.click('[data-testid=confirm-ok]');assert.ok(await page.isVisible('[data-testid=live-card]'));assert.deepEqual(errors,[]);
  await page.screenshot({path:(process.env.SCREENSHOT_DIR||'browser-evidence')+'/automatic-updates-public-offline.png',fullPage:true});
  console.log('PASS actual public origin: selected build, real worker, no update/version UI, fresh/offline reload, exact saved amount and extension, draft/timer.');
 }finally{await context.close();await browser.close();}
})().catch(e=>{console.error(e);process.exitCode=1;});
