/* RC13 browser translation. Android-only mechanisms stay outside product data. */
const WEB_AI_ENABLED=false; // No provider credential is requested or shipped to the browser.
const el=id=>document.getElementById(id);
function reportError(text){el('report-error').textContent=text;el('report-error').hidden=!text;}
function renderPickerLabels(){for(const id of ['modal-date','modal-start','modal-end']){const control=el(id),label=control?.parentElement.querySelector('.picker-label');if(label){const value=control.value;label.textContent=id==='modal-date'&&value?value.slice(8,10)+'.'+value.slice(5,7):value;}}}
function syncFormLayout(){
 renderPickerLabels();
 const fields=el('report-fields');fields.classList.toggle('clock-mode',el('modal-range').checked);fields.classList.toggle('group-mode',el('modal-group').checked);
 document.body.classList.toggle('report-expanded',!el('report-content').hidden&&currentPage===0);
}
function collapseReport(){el('report-content').hidden=true;document.querySelector('.card-heading').setAttribute('aria-expanded','false');syncFormLayout();renderLiveShift();}
function updateFilterBadge(){
 const count=(selectedCategory!=='הכל'?1:0)+(historyPeriod!=='all'?1:0)+(el('payment-filter').value!=='all'?1:0)+(historyCurrency!=='הכל'?1:0);
 const button=el('history-filters-button');button.dataset.count=count||'';button.setAttribute('aria-label',count?'סינון · '+count+' פעילים':'סינון');
}
function resetHistoryFilters(){selectedCategory='הכל';historyCurrency='הכל';el('currency-filter').textContent='הכל';setPayment('all');setPeriod('all');renderShifts();}
function shareSelected(){const rows=shifts.filter(e=>selectedShiftIds.has(e.id));if(rows.length)shareText(WorkSharing.summary(rows,'הכל'));}

function renderSummaries(){
 const carousel=document.querySelector('.summary-carousel'),page=[...document.querySelectorAll('.pager-dots button')].findIndex(b=>b.classList.contains('active'));
 const now=new Date(),today=shifts.filter(e=>new Date(e.date).toDateString()===now.toDateString());
 const hours=rows=>rows.reduce((n,e)=>n+Number(e.hours),0).toFixed(2);
 const metric=(label,value)=>'<div><span>'+label+'</span><strong><bdi>'+escapeHtml(value)+'</bdi></strong></div>';
 const pages=[{title:'סיכום כללי',body:metric('משמרות',shifts.length)+metric('שעות עבודה',hours(shifts))+metric('משמרות היום',today.length)+metric('שעות היום',hours(today))}];
 const startOfWeek=new Date(now.getFullYear(),now.getMonth(),now.getDate()-now.getDay()),endOfWeek=new Date(startOfWeek);endOfWeek.setDate(endOfWeek.getDate()+7);
 for(const c of ['₪','$'].filter(c=>shifts.some(e=>e.currency===c))){
  const rows=shifts.filter(e=>e.currency===c),money=rows=>c+rows.reduce((n,e)=>n+e.totalEarnings,0).toLocaleString('en-US',{minimumFractionDigits:2,maximumFractionDigits:2});
  const week=rows.filter(e=>e.date>=startOfWeek.getTime()&&e.date<endOfWeek.getTime()),month=rows.filter(e=>{const d=new Date(e.date);return d.getFullYear()===now.getFullYear()&&d.getMonth()===now.getMonth();});
  pages.push({title:c==='₪'?'סיכום בשקלים':'סיכום בדולרים',body:metric('סה״כ הכנסות',money(rows))+metric('שולם',money(rows.filter(e=>e.isPaid)))+metric('ממתין לתשלום',money(rows.filter(e=>!e.isPaid)))+metric('שעות עבודה',hours(rows))+metric('השבוע',money(week))+metric('החודש',money(month))});
 }
 const signature=JSON.stringify(pages);if(carousel.dataset.content===signature)return;carousel.dataset.content=signature;
 carousel.innerHTML=pages.map(p=>'<article class="summary-card"><h2>'+p.title+'</h2><div class="summary-grid">'+p.body+'</div></article>').join('');
 const dots=document.querySelector('.pager-dots');dots.replaceChildren(...pages.map((p,i)=>{const b=document.createElement('button');b.setAttribute('aria-label',p.title);b.className=i===Math.max(0,Math.min(page,pages.length-1))?'active':'';b.onclick=()=>summaryPage(i);return b;}));
 const selected=Math.max(0,Math.min(page,pages.length-1));carousel.scrollLeft=-selected*(carousel.clientWidth+12);
}
function ownerKey(prefix){return prefix+encodeURIComponent(currentUserId||'guest');}
function renderLiveShift(){
 try{
  const t=WorkRuntime.readTimer(localStorage,currentUserId);el('active-shift-card').hidden=!t;
  el('live-shift-label').textContent=t?'סיים משמרת פעילה':'התחל משמרת פעילה';el('live-shift-action').firstElementChild.innerHTML=uiIcon(t?'stop':'play');
  el('live-shift-action').hidden=!el('report-content').hidden;
  if(!t)return;
  const seconds=Math.max(0,Math.floor((Date.now()-t.startedAt)/1000));el('active-shift-category').textContent='משמרת פעילה · '+t.category;
  el('active-shift-timer').textContent=[Math.floor(seconds/3600),Math.floor(seconds/60)%60,seconds%60].map(n=>String(n).padStart(2,'0')).join(':');
  el('active-shift-amount').textContent=t.currency+(seconds*t.rate/3600).toFixed(2);
  el('active-shift-currency').value=t.currency;
 }catch{el('active-shift-card').hidden=false;el('active-shift-category').textContent='לא ניתן לקרוא את המשמרת הפעילה. הנתונים נשמרו ללא שינוי.';el('live-shift-action').disabled=true;}
}
function changeLiveCurrency(currency){
 try{if(!storageHealthy)throw Error('לא ניתן לשמור כרגע');const t=WorkRuntime.readTimer(localStorage,currentUserId);if(!t)return;
  if(!['₪','$'].includes(currency))return;
  localStorage.setItem(WorkRuntime.timerKey(currentUserId),JSON.stringify({...t,currency}));renderLiveShift();
 }catch(e){showMessage('לא ניתן לשנות מטבע: '+e.message);renderLiveShift();}
}
async function cancelLiveShift(){
 const owner=currentUserId,t=WorkRuntime.readTimer(localStorage,owner);if(!t)return;
 if(!await confirmAction('ביטול משמרת פעילה','לבטל את המשמרת הפעילה? שעות העבודה של המשמרת הזו לא יישמרו.','כן, בטל משמרת'))return;
 if(owner!==currentUserId)return;
 try{const live=WorkRuntime.readTimer(localStorage,owner);if(!live||live.id!==t.id)return;localStorage.removeItem(WorkRuntime.timerKey(owner));renderLiveShift();showMessage('המשמרת בוטלה');}
 catch(e){showMessage('לא ניתן לבטל את המשמרת: '+e.message);}
}
function applyLiveCategory(){const name=el('live-category').value;el('live-rate').value=categories.find(c=>c.name===name)?.defaultRate??40;el('live-currency').value=WorkCategories.currency(name,webPreferences);}
async function openLiveShift(){
 try{if(WorkRuntime.readTimer(localStorage,currentUserId))return stopLiveShift();}catch{showMessage('לא ניתן לקרוא את המשמרת הפעילה');return;}
 el('live-category').replaceChildren(...categories.map(c=>{const o=document.createElement('option');o.value=c.name;o.textContent=c.name;return o;}));el('live-category').value=WorkCategories.defaultName(categories,webPreferences);applyLiveCategory();el('live-error').textContent='';el('live-shift-dialog').showModal();
}
function startLiveShift(){
 try{
  if(!storageHealthy)throw Error('הנתונים המקומיים דורשים שחזור');
  if(WorkRuntime.readTimer(localStorage,currentUserId))throw Error('כבר קיימת משמרת פעילה');
  const category=el('live-category').value,rate=WorkTransfer.number(el('live-rate').value),currency=el('live-currency').value;
  if(!categories.some(c=>c.name===category))throw Error('יש לבחור קטגוריה');
  localStorage.setItem(WorkRuntime.timerKey(currentUserId),JSON.stringify({id:crypto.randomUUID(),category,rate,currency,startedAt:Date.now()}));
  el('live-shift-dialog').close();renderLiveShift();showMessage('המשמרת התחילה');
 }catch(e){el('live-error').textContent=e.message;}
}
async function stopLiveShift(){
 const owner=currentUserId,t=WorkRuntime.readTimer(localStorage,owner);if(!t)return;
 if(!await confirmAction('סיום משמרת','האם אתה בטוח שברצונך לסיים את המשמרת הפעילה?','כן, סיים'))return;
 if(owner!==currentUserId)return;
 try{
  const live=WorkRuntime.readTimer(localStorage,owner);if(!live||live.id!==t.id)return;
  // Reload guest state after the confirmation: another tab may have saved a shift.
  if(!owner){const current=WorkRuntime.loadGuest(localStorage);shifts=current.entries;categories=current.categories.length?current.categories:categories;workers=current.workers;}
  if(!shifts.some(e=>e.id===t.id)){const entry=WorkRuntime.stopEntry(live,Date.now());persistAll([entry,...shifts]);}
  // Persist first, remove only afterward. Stable entry ID makes interrupted stops idempotent.
  localStorage.removeItem(WorkRuntime.timerKey(owner));renderShifts();renderLiveShift();syncToCloud();showMessage('הדיווח נשמר בהצלחה');
 }catch(e){showMessage('לא ניתן לסיים את המשמרת: '+e.message);}
}
function openFormCategory(){el('form-category-name').value='';el('form-category-rate').value='40';el('form-category-error').textContent='';el('form-category-dialog').showModal();}
function saveFormCategory(){
 try{const name=el('form-category-name').value.trim(),rate=WorkTransfer.number(el('form-category-rate').value);const next=WorkCategories.rename({entries:shifts,categories,webPreferences},'',name,rate,webPreferences.mainCurrency);persistAll(shifts,next.categories,workers,next.webPreferences);renderCategoryOptions();el('modal-category').value=name;applyCategoryRate();el('form-category-dialog').close();renderShifts();syncToCloud();}
 catch(e){el('form-category-error').textContent=e.message;}
}
async function deleteFormCategory(){
 const owner=currentUserId,name=el('modal-category').value,def=WorkCategories.defaultName(categories,webPreferences);
 if(name===def){showMessage('לפני מחיקת ברירת המחדל יש לבחור קטגוריה אחרת בהגדרות');return;}
 if(!await confirmAction('מחיקת קטגוריה','המשמרות יועברו לקטגוריה „'+def+'” ללא שינוי שעות, תעריף, מטבע או סכום.'))return;
 if(owner!==currentUserId)return;
 try{const next=WorkCategories.remove({entries:shifts,categories,webPreferences},name);persistAll(next.entries,next.categories,workers,next.webPreferences);renderShifts();el('modal-category').value=def;applyCategoryRate();syncToCloud();}catch(e){showMessage(e.message);}
}
function saveReportDraft(){
 if(editingShiftId!==null||!storageHealthy)return false;
 try{const values=Object.fromEntries([...el('report-fields').querySelectorAll('input[id],textarea[id],select[id]')].map(c=>[c.id,{value:c.value,checked:c.checked}]));const group=[...el('group-rows').children].map(row=>({...row.workerData,name:row.querySelector('[data-field=name]').value,hours:row.querySelector('[data-field=hours]').value,isPaid:row.querySelector('[data-field=paid]').checked}));localStorage.setItem(ownerKey('work_report_draft_v1:'),JSON.stringify({mode:reportMode,expanded:!el('report-content').hidden,values,group}));return true;}catch{return false;}
}
function clearReportDraft(){localStorage.removeItem(ownerKey('work_report_draft_v1:'));}
function restoreReportDraft(){
 try{const d=JSON.parse(localStorage.getItem(ownerKey('work_report_draft_v1:'))||'null');if(!d)return;setReportMode(['clock','manual','group'].includes(d.mode)?d.mode:'clock');for(const [id,v] of Object.entries(d.values||{})){const c=el(id);if(c&&el('report-fields').contains(c)){c.value=v.value;if('checked' in c)c.checked=Boolean(v.checked);}}el('group-rows').replaceChildren();for(const worker of d.group||[])addGroupRow(worker);toggleRange();el('group-fields').hidden=!el('modal-group').checked;el('report-content').hidden=!d.expanded;document.querySelector('.card-heading').setAttribute('aria-expanded',String(d.expanded));syncFormLayout();}catch{ /* Leave invalid draft bytes intact; never reset financial storage. */ }
}

// Read-only clones of actual components. Replay never touches normal draft/selection/payment state.
const tutorialSteps=[
 ['home',null,'ברוכים הבאים לחישוב שכר','רושמים משמרות, מחשבים שכר ומשתפים בקלות. בוא נכיר את המקומות החשובים.'],
 ['home','.report-card','כאן מתחילים','״דיווח חדש״ לרישום שעות שכבר עבדת. ״התחל משמרת פעילה״ מפעיל טיימר בזמן העבודה.'],
 ['form','#report-fields','הפרטים שלך, החישוב שלנו','תאריך ושעות, הפסקה, תעריף ומטבע. מוסיפים קטגוריה והערה אם צריך — והסכום מחושב לבד.'],
 ['form','.mode-switch','בוחרים איך לדווח','שעון לפי כניסה ויציאה, ידני לפי מספר שעות, או קבוצה עם עובדים ותעריפים נפרדים.'],
 ['history','.journal-toolbar','כל המשמרות במקום אחד','כאן רואים סכומים, מחפשים ומסננים. לחיצה ארוכה בוחרת כמה משמרות לפעולה משותפת.'],
 ['share','.journal-card-actions','שולחים סיכום מסודר','פותחים משמרת ולוחצים על שיתוף. השעות והסכום מוכנים לשליחה, למשל בוואטסאפ. בהדרכה לא נשלח דבר.'],
 ['settings','#category-settings','עובד בכמה מקומות?','בהגדרות יוצרים קטגוריה לכל עבודה, עם תעריף ומטבע ברירת מחדל: שקל או דולר. אפשר לבחור מטבע גם בכל משמרת.'],
 ['settings','#maintenance-section','הנתונים שלך, גם בגיבוי','מעתיקים או מורידים גיבוי מלא. בייבוא בודקים את המשמרות לפני השמירה, בלי למחוק נתונים קיימים.'],
 ['home',null,'זה הכול. אתה מוכן.','מוסיפים משמרת — והחישובים עלינו.']
];
let tutorialIndex=0;
function startTutorial(){tutorialIndex=0;renderTutorial();el('tutorial-dialog').showModal();}
function finishTutorial(){try{localStorage.setItem('work_onboarding_version','1');}catch{showMessage('לא ניתן לשמור את השלמת ההדרכה');}el('tutorial-dialog').close();el('tutorial-demo').replaceChildren();}
function tutorialStep(delta){if(delta>0&&tutorialIndex===tutorialSteps.length-1)return finishTutorial();tutorialIndex=Math.max(0,tutorialIndex+delta);renderTutorial();}
function renderTutorial(){
 const [screen,selector,title,body]=tutorialSteps[tutorialIndex];el('tutorial-title').textContent=title;el('tutorial-body').textContent=body;el('tutorial-progress').textContent=(tutorialIndex+1)+' מתוך '+tutorialSteps.length;el('tutorial-back').disabled=tutorialIndex===0;el('tutorial-next').textContent=tutorialIndex===tutorialSteps.length-1?'מתחילים':'הבא';
 const demo=el('tutorial-demo');demo.replaceChildren();
 let clone;
 if(screen==='settings'){clone=el('settings-dialog').cloneNode(true);clone.removeAttribute('open');clone=Object.assign(document.createElement('div'),{className:'tutorial-settings',innerHTML:clone.innerHTML});clone.querySelectorAll('details').forEach(d=>d.open=d.matches(selector));clone.querySelector('#settings-categories').replaceChildren(...categories.map(c=>createCategorySetting(c,webPreferences).cloneNode(true)));}
 else if(screen==='share'){
  clone=document.createElement('div');clone.className='tutorial-share';const sample=shifts[0]||{id:'tutorial-only',category:'עבודה לדוגמה',date:new Date().setHours(0,0,0,0),hours:8,hourlyRate:40,totalEarnings:320,currency:'₪',isPaid:false,isTimeRange:true,startTime:'09:00',endTime:'17:00',notes:''};
  // Clone drops handlers; demonstration is never inserted into persisted records.
  const card=createShiftCard({...sample,isGroupShift:false});card.open=true;clone.append(card.cloneNode(true));
 }else{clone=document.querySelector('.app-container').cloneNode(true);clone.querySelector('#page-home').hidden=screen==='history';clone.querySelector('#page-history').hidden=screen!=='history';if(screen==='form'){clone.querySelector('#report-content').hidden=false;clone.querySelector('.card-heading').setAttribute('aria-expanded','true');clone.querySelector('#live-shift-action').hidden=true;}}
 const target=selector?clone.querySelector(selector):null;if(target)target.classList.add('coach-highlight');
 clone.querySelectorAll('input,textarea,select').forEach(c=>{const original=c.id?el(c.id):null;if(original)c.value=original.value;});
 clone.querySelectorAll('*').forEach(n=>{if(n.id)n.dataset.coachId=n.id;n.removeAttribute('id');for(const a of [...n.attributes])if(a.name.startsWith('on'))n.removeAttribute(a.name);});demo.append(clone);
 if(target)requestAnimationFrame(()=>target.scrollIntoView({block:'center',behavior:'instant'}));
}

// Browser route state preserves the Android two-tab mental model and native Back/Forward.
const navigateScreen=showPage;
showPage=function(page){const changed=page!==currentPage;navigateScreen(page);if(changed&&location.hash!==(page?'#history':'#home'))history.pushState({screen:page},'',page?'#history':'#home');syncFormLayout();renderLiveShift();};
window.addEventListener('popstate',()=>{const dialog=[...document.querySelectorAll('dialog[open]')].at(-1);if(dialog){if(dialog.id==='add-modal')closeAddModal();else dialog.close();}navigateScreen(location.hash==='#history'?1:0);syncFormLayout();renderLiveShift();});
window.addEventListener('storage',event=>{if(event.key===WorkRuntime.timerKey(currentUserId))renderLiveShift();if(!currentUserId&&event.key==='work_complete_backup'){try{const saved=WorkRuntime.loadGuest(localStorage);shifts=saved.entries;categories=saved.categories;workers=saved.workers;webPreferences=WorkCategories.preferences(saved.webPreferences);renderShifts();}catch{showMessage('הנתונים השתנו אך לא ניתן לקרוא אותם');}}});
el('report-fields').addEventListener('input',()=>{renderPickerLabels();saveReportDraft();});el('report-fields').addEventListener('change',()=>{syncFormLayout();saveReportDraft();});document.querySelector('.mode-switch').addEventListener('click',saveReportDraft);
el('tutorial-dialog').addEventListener('cancel',e=>{e.preventDefault();if(tutorialIndex) tutorialStep(-1);else finishTutorial();});
initializeApp();restoreReportDraft();syncFormLayout();renderLiveShift();setInterval(renderLiveShift,1000);document.addEventListener('visibilitychange',renderLiveShift);
if(location.hash==='#history')navigateScreen(1);
const established=shifts.length>0||localStorage.getItem('work_pre_rc13_snapshot_v1')!==null;
if(!localStorage.getItem('work_onboarding_version')){if(established)localStorage.setItem('work_onboarding_version','1');else startTutorial();}
// Updates never submit an edit or close an unsaved settings/import/category dialog.
window.prepareWebUpdate=()=>{
 if(editingShiftId!==null||document.querySelector('dialog[open]'))return false;
 return saveReportDraft();
};
