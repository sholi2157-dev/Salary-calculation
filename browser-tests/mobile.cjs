// Disposable browser context, synthetic data only. Run after npm run build.
const assert=require('node:assert/strict'),http=require('node:http'),fs=require('node:fs'),path=require('node:path');
const {chromium}=require(process.env.PLAYWRIGHT_MODULE||'playwright');
(async()=>{
 const root=path.resolve('public');
 const server=http.createServer((req,res)=>{const target=path.join(root,req.url==='/'?'index.html':req.url.split('?')[0]);if(!target.startsWith(root)||!fs.existsSync(target)){res.writeHead(404);res.end();return;}res.setHeader('Content-Type',target.endsWith('.js')?'application/javascript':target.endsWith('.css')?'text/css':'text/html');res.end(fs.readFileSync(target));});
 await new Promise(resolve=>server.listen(0,'127.0.0.1',resolve));
 const browser=await chromium.launch({executablePath:process.env.CHROMIUM_PATH||undefined,args:['--no-sandbox','--disable-dev-shm-usage','--disable-gpu'],headless:true});
 const context=await browser.newContext({viewport:{width:390,height:844},isMobile:true,hasTouch:true,locale:'he-IL'}),page=await context.newPage(),errors=[];
 page.on('pageerror',e=>errors.push(e.message));page.on('dialog',d=>{errors.push('Unexpected native dialog: '+d.message());d.dismiss();});
 await page.route('https://www.gstatic.com/**',r=>r.abort()); // Local guest-only fixture: never access live Firebase.
 await page.addInitScript(()=>{navigator.share=async data=>{window.sharedText=data.text;};navigator.clipboard.writeText=async text=>{window.copiedText=text;};});
 const url='http://127.0.0.1:'+server.address().port;
 const click=(name)=>page.getByRole('button',{name,exact:true}).click();
 const state=()=>page.evaluate(()=>JSON.parse(localStorage.getItem('work_complete_backup')));
 const settings=async()=>{await click('ניהול וקטגוריות');await page.locator('#settings-dialog summary').filter({hasText:'ניהול עבודה וקטגוריות'}).click();};
 const category=async(name,rate,currency,def)=>{await click('הוספת קטגוריה');await page.locator('#category-name').fill(name);await page.locator('#category-rate').fill(rate);await page.locator('#category-currency').selectOption(currency);if(def)await page.locator('#category-default').check();await page.locator('#category-dialog').getByRole('button',{name:'שמירה',exact:true}).click();};
 try{
 await page.goto(url,{waitUntil:'domcontentloaded'});
 await settings();await category('דולר בדיקה','25.5','$',true);await page.locator('#settings-dialog').getByRole('button',{name:'ביטול',exact:true}).click();assert.equal(await page.locator('#modal-category option').count(),1);
 await settings();await category('דולר בדיקה','25.5','$',true);await page.locator('#settings-dialog').getByRole('button',{name:'שמור',exact:true}).click();
 await page.reload({waitUntil:'domcontentloaded'});assert.equal(await page.locator('#modal-category').inputValue(),'דולר בדיקה');assert.equal(await page.locator('#modal-currency').inputValue(),'$');
 // ILS manual and dollar group, both using explicit saved category defaults.
 await page.getByRole('tab',{name:'ידני',exact:true}).click();await page.locator('#modal-category').selectOption('עצמאי');await page.locator('#modal-hours').fill('1.5');await page.locator('#modal-rate').fill('40.25');await page.locator('#modal-notes').fill('שקל סינתטי');await click('שמירה');
 await page.getByRole('tab',{name:'קבוצה',exact:true}).click();await page.locator('#modal-category').selectOption('דולר בדיקה');await page.locator('#modal-hours').fill('2');await page.locator('#modal-notes').fill('דולר סינתטי');await page.locator('#group-employer-rate').fill('50');await click('הוספת עובד');await page.locator('[data-field=name]').fill('עובד בדיקה');await page.locator('[data-field=hours]').fill('3');assert.match(await page.locator('#group-total-hours').innerText(),/5 שעות/);await click('שמירה');
 let data=await state();assert.equal(data.entries.length,2);assert.equal(data.entries[0].currency,'$');assert.equal(data.entries[0].totalEarnings,51);assert.equal(data.entries[1].totalEarnings,60.38);
 await page.reload({waitUntil:'domcontentloaded'});await click('היסטוריה');
 await page.waitForFunction(()=>innerWidth===390);if(process.env.SCREENSHOT_DIR)await page.screenshot({path:path.join(process.env.SCREENSHOT_DIR,'mobile-history.png'),fullPage:true});
 const first=page.locator('#shifts-list .journal-card').filter({hasText:'דולר בדיקה'});await first.locator('summary').click();await first.getByRole('button',{name:'שיתוף החלק שלי',exact:true}).click();assert.match(await page.evaluate(()=>window.sharedText),/\$51.00/);await first.getByRole('button',{name:'דוח הקבוצה',exact:true}).click();assert.match(await page.evaluate(()=>window.sharedText),/\$201.00/);
 await first.getByRole('button',{name:'עריכה',exact:true}).click();await page.locator('#modal-notes').fill('שינוי לביטול');await page.mouse.click(2,2);assert.equal(await page.locator('#add-modal').isVisible(),false);assert.equal((await state()).entries[0].notes,'דולר סינתטי');
 await first.getByRole('button',{name:'עריכה',exact:true}).click();await page.locator('#modal-notes').fill('עודכן');await page.locator('#add-modal').getByRole('button',{name:'שמירה',exact:true}).click();await page.reload({waitUntil:'domcontentloaded'});assert.equal((await state()).entries[0].notes,'עודכן');assert.equal((await state()).entries[0].currency,'$');
 await click('היסטוריה');await click('חיפוש בהיסטוריה');await page.locator('#search-box').fill('עודכן');assert.equal(await page.locator('#shifts-list .journal-card').count(),1);assert.equal(await page.locator('#search-box').evaluate(el=>el===document.activeElement),true);
 await page.setViewportSize({width:390,height:420});await click('ניקוי החיפוש');assert.equal(await page.locator('#history-search').isVisible(),true);assert.equal(await page.locator('#search-box').inputValue(),'');await click('סיום חיפוש');assert.equal(await page.locator('#history-search').isVisible(),false);assert.equal(await page.locator('#shifts-list .journal-card').count(),2);await page.setViewportSize({width:390,height:844});
 await settings();await click('עריכת דולר בדיקה');await page.locator('#category-name').fill('דולר חדש');await page.locator('#category-rate').fill('99');await page.locator('#category-currency').selectOption('₪');await page.locator('#category-dialog').getByRole('button',{name:'שמירה',exact:true}).click();await page.locator('#settings-dialog').getByRole('button',{name:'שמור',exact:true}).click();data=await state();assert.equal(data.entries[0].currency,'$');assert.equal(data.entries[0].hourlyRate,25.5);assert.equal(data.entries[0].category,'דולר חדש');
 await settings();await click('מחיקת דולר חדש');assert.equal(await page.locator('#confirm-dialog').isVisible(),false);
 await click('מחיקת עצמאי');await page.locator('#confirm-cancel').click();assert.equal(await page.locator('#settings-categories .category-setting').count(),2);
 await click('מחיקת עצמאי');await page.locator('#confirm-dialog').getByRole('button',{name:'מחיקה',exact:true}).click();await page.locator('#settings-dialog').getByRole('button',{name:'שמור',exact:true}).click();data=await state();assert.equal(data.entries.length,2);assert.equal(data.entries[1].currency,'₪');assert.equal(data.entries[1].totalEarnings,60.38);assert.equal(data.entries[1].category,'דולר חדש');
 const touch=await context.newCDPSession(page),bounds=await page.locator('#shifts-list summary').first().boundingBox();await touch.send('Input.dispatchTouchEvent',{type:'touchStart',touchPoints:[{x:bounds.x+50,y:bounds.y+25}]});await page.waitForTimeout(600);await touch.send('Input.dispatchTouchEvent',{type:'touchEnd',touchPoints:[]});assert.match(await page.locator('#selection-count').innerText(),/נבחרו 1/);await click('סיום בחירה');
 await click('בחירת משמרות');await click('בחר הכל');await page.locator('#selection-actions').getByRole('button',{name:'סמן כשולם',exact:true}).click();assert.ok((await state()).entries.every(e=>e.isPaid));
 await click('בחירת משמרות');await click('בחר הכל');await page.locator('#selection-actions').getByRole('button',{name:'מחיקה',exact:true}).click();await page.locator('#confirm-cancel').click();assert.equal((await state()).entries.length,2);
 await page.locator('#selection-actions').getByRole('button',{name:'מחיקה',exact:true}).click();await page.locator('#confirm-dialog').getByRole('button',{name:'מחיקה',exact:true}).click();await page.waitForFunction(()=>JSON.parse(localStorage.getItem('work_complete_backup')).entries.length===0);assert.equal((await state()).entries.length,0);
 await page.reload({waitUntil:'domcontentloaded'});assert.equal((await state()).entries.length,0);

 await page.getByRole('tab',{name:'שעון',exact:true}).click();await page.locator('#modal-start').fill('22:00');await page.locator('#modal-end').fill('02:00');await page.locator('#modal-break').fill('30');await page.locator('#modal-currency').selectOption('$');await page.locator('#modal-rate').fill('20');await click('שמירה');data=await state();assert.equal(data.entries[0].hours,3.5);assert.equal(data.entries[0].totalEarnings,70);assert.equal(data.entries[0].currency,'$');
 await click('ניהול וקטגוריות');assert.equal(await page.locator('#account-section').getAttribute('open'),null);await page.locator('#settings-dialog summary').filter({hasText:'תחזוקה וגיבוי נתונים'}).click();
 const downloadPromise=page.waitForEvent('download');await click('גיבוי מלא');const download=await downloadPromise;const backup=JSON.parse(fs.readFileSync(await download.path(),'utf8'));assert.equal(backup.entries[0].currency,'$');assert.equal(backup.webPreferences.defaultCategory,'דולר חדש');
 await click('הדבקת נתונים');await page.locator('#transfer-text').fill('קטגוריה\tתאריך\tשעות\tתעריף שעתי\nשגיאה\tלא תאריך\t2\t40');await click('בדיקת הנתונים');assert.equal(await page.locator('#transfer-save').isDisabled(),true);
 await page.locator('#transfer-text').fill(JSON.stringify(backup));await click('בדיקת הנתונים');assert.match(await page.locator('#transfer-summary').innerText(),/חדשות: 0/);await click('שמירת המשמרות');assert.equal((await state()).entries.length,1);
 await click('היסטוריה');await click('שתף דוח');await click('העתקת טבלה לאקסל');assert.match(await page.evaluate(()=>window.copiedText),/\$/);
 await page.setViewportSize({width:360,height:740});assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth),true);
 assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth),true);assert.deepEqual(errors,[]);
 console.log('PASS: phone 390px; settings save/cancel/reload; both currencies; group/share; edit/backdrop/reload; search focus/420px keyboard simulation; rename/delete/default protection; bulk pay/delete accept/cancel.');
 if(process.env.SCREENSHOT_DIR){fs.mkdirSync(process.env.SCREENSHOT_DIR,{recursive:true});await page.screenshot({path:path.join(process.env.SCREENSHOT_DIR,'mobile-home.png'),fullPage:true});}
 }catch(e){if(process.env.SCREENSHOT_DIR)await page.screenshot({path:path.join(process.env.SCREENSHOT_DIR,'failure.png'),fullPage:true});throw e;}
 finally{await browser.close();server.close();}
})().catch(e=>{console.error(e);process.exitCode=1});
