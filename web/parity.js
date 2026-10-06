let historyPeriod='all',historyCurrency='הכל',historySort='newest',displayedEntries=[];
function initParity(){document.querySelectorAll('[data-icon]').forEach(el=>el.innerHTML=uiIcon(el.dataset.icon));document.querySelectorAll('#settings-dialog details').forEach(el=>el.addEventListener('toggle',()=>{if(el.open)document.querySelectorAll('#settings-dialog details').forEach(other=>{if(other!==el)other.open=false;});}));
 // A long touch can move the toolbar under the finger. Block its release click
 // even when the original summary node has been replaced by selection rendering.
 document.addEventListener('click',event=>{if(Date.now()<ignoreSelectionClickUntil&&event.pointerType!=='mouse'&&event.detail!==0){event.preventDefault();event.stopImmediatePropagation();}},true);
 for(const dialog of document.querySelectorAll('dialog'))installDismiss(dialog);
 document.getElementById('modal-hours').addEventListener('input',updateGroupHours);
 document.getElementById('settings-dialog').addEventListener('close',()=>{settingsDraft=null;document.getElementById('settings-import-text').value='';});
 if(window.visualViewport){const resize=()=>document.body.classList.toggle('keyboard-open',window.visualViewport.height<window.innerHeight*.78);window.visualViewport.addEventListener('resize',resize);}
}
function openHistorySearch(){showPage(1);document.getElementById('history-search').hidden=false;document.getElementById('search-box').focus();}
function setPeriod(period){historyPeriod=period;document.querySelectorAll('[data-period]').forEach(b=>b.classList.toggle('selected',b.dataset.period===period));document.getElementById('period-inputs').hidden=period==='all';document.getElementById('month-label').hidden=period!=='month';document.getElementById('from-label').hidden=period!=='range';document.getElementById('to-label').hidden=period!=='range';renderShifts();}
function setPayment(value){document.getElementById('payment-filter').value=value;document.querySelectorAll('[data-payment]').forEach(b=>b.classList.toggle('selected',b.dataset.payment===value));renderShifts();}
function cycleCurrency(){historyCurrency=historyCurrency==='הכל'?'₪':historyCurrency==='₪'?'$':'הכל';document.getElementById('currency-filter').textContent=historyCurrency;renderShifts();}
function setSort(value){historySort=value;document.getElementById('sort-dialog').close();renderShifts();}
function renderHistoryControls(entries){document.getElementById('displayed-total').textContent=entries.length?formatTotals(WorkTransfer.totals(entries)):'0.00';updateFilterBadge();const select=document.getElementById('category-filter');select.replaceChildren();for(const name of ['הכל',...new Set([...categories.map(c=>c.name),...shifts.map(s=>s.category)])]){const o=document.createElement('option');o.value=name;o.textContent=name==='הכל'?'כל הקטגוריות':name;select.append(o);}select.value=selectedCategory;}
async function copyDisplayed(kind){const reportEntries=displayedEntries;if(!reportEntries.length){showMessage('אין משמרות להעתקה');return;}const text=kind==='table'?WorkTransfer.csv(reportEntries,'\t'):WorkSharing.summary(reportEntries,selectedCategory);try{await navigator.clipboard.writeText(text);document.getElementById('report-dialog').close();showMessage('הדוח הועתק');}catch{showMessage('הדפדפן לא אפשר העתקה. אפשר לייצא קובץ דרך ההגדרות.');}}

let selectionMode=false,selectedShiftIds=new Set(),ignoreSelectionClickUntil=0;
function enterSelection(id){selectionMode=true;if(id!==undefined)selectedShiftIds.add(id);renderShifts();}
function exitSelection(){selectionMode=false;selectedShiftIds.clear();renderShifts();}
function toggleSelection(id){if(selectedShiftIds.has(id))selectedShiftIds.delete(id);else selectedShiftIds.add(id);renderShifts();}
function selectAllVisible(){selectedShiftIds=new Set(displayedEntries.map(e=>e.id));renderShifts();}
function clearSelection(){selectedShiftIds.clear();renderShifts();}
function renderSelectionControls(){
 const visible=new Set(displayedEntries.map(e=>e.id));selectedShiftIds=new Set([...selectedShiftIds].filter(id=>visible.has(id)));
 document.querySelector('.journal-toolbar').classList.toggle('selecting',selectionMode);
 document.getElementById('page-history').classList.toggle('history-selecting',selectionMode);
 document.getElementById('selection-share').disabled=!selectedShiftIds.size;
 document.getElementById('selection-toolbar').hidden=!selectionMode;
 document.getElementById('selection-actions').hidden=!selectionMode;
 document.getElementById('selection-count').textContent='נבחרו '+selectedShiftIds.size+' משמרות';document.getElementById('selection-total').textContent=formatTotals(WorkTransfer.totals(shifts.filter(e=>selectedShiftIds.has(e.id))));
 document.querySelectorAll('#selection-actions button').forEach(b=>b.disabled=!selectedShiftIds.size);
}
async function applySelection(action){
 if(!selectedShiftIds.size)return;const ids=new Set(selectedShiftIds),owner=currentUserId;
 if(action==='delete'&&!await confirmAction('מחיקת משמרות','למחוק את '+ids.size+' המשמרות שנבחרו? פעולה זו אינה ניתנת לביטול.'))return;
 if(currentUserId!==owner)return;
 try{persistAll(WorkSelection.apply(shifts,ids,action));exitSelection();syncToCloud();showMessage(action==='delete'?'המשמרות שנבחרו נמחקו':'מצב התשלום עודכן');}catch{showMessage('השמירה נכשלה. הנתונים לא שונו.');}
}

// Preferences stay local to the active owner until Android supports category currency/default.
let settingsDraft=null,settingsBase=null,settingsOwner=null,settingsOriginalCategories=null,editingCategoryName='';
function ensureCategoryCatalog(){
 if(!categories.length){const names=[...new Set(shifts.map(e=>e.category))];categories=(names.length?names:['עצמאי']).map(name=>({name,defaultRate:40}));}
 webPreferences=WorkCategories.preferences(webPreferences);webPreferences.defaultCategory=WorkCategories.defaultName(categories,webPreferences);
}
function renderCategoryOptions(){
 ensureCategoryCatalog();const select=document.getElementById('modal-category'),previous=select.value;
 if(JSON.stringify([...select.options].map(o=>o.value))!==JSON.stringify(categories.map(c=>c.name))){select.replaceChildren(...categories.map(c=>{const o=document.createElement('option');o.value=c.name;o.textContent=c.name;return o;}));}
 select.value=categories.some(c=>c.name===previous)?previous:WorkCategories.defaultName(categories,webPreferences);
}
function openWebSettings(){
 ensureCategoryCatalog();settingsOwner=currentUserId;settingsBase=JSON.stringify({categories,webPreferences});
 settingsDraft=JSON.parse(JSON.stringify({entries:shifts,categories,webPreferences}));settingsOriginalCategories=new Map(shifts.map(e=>[e.id,e.category]));
 document.getElementById('default-currency').value=webPreferences.mainCurrency||localStorage.getItem('work_default_currency')||'₪';
 selectMainCurrency(document.getElementById('default-currency').value);
 document.getElementById('new-category-name').value='';document.getElementById('new-category-error').textContent='';
 document.querySelectorAll('#settings-dialog details').forEach(d=>d.open=false); renderCategorySettings();document.getElementById('settings-dialog').showModal();
}
function saveWebSettings(){
 if(!settingsDraft||settingsOwner!==currentUserId)return;
 if(settingsBase!==JSON.stringify({categories,webPreferences})){showMessage('הקטגוריות השתנו בזמן שההגדרות פתוחות. פתח אותן מחדש לפני השמירה');return;}
 try{
  // Only reassign categories: use live shifts, preserving edits arriving while settings were open.
  const assignments=new Map(settingsDraft.entries.map(e=>[e.id,e.category]));
  const next=shifts.map(e=>{const intended=assignments.get(e.id),original=settingsOriginalCategories.get(e.id);if(intended!==undefined&&intended!==original){if(e.category!==original)throw Error('קטגוריית המשמרת השתנתה');return {...e,category:intended};}return {...e};});
  // New remote shifts may still refer to a renamed/deleted category.
  for(const e of next){const change=Object.hasOwn(settingsDraft.reassignments||{},e.category)?settingsDraft.reassignments[e.category]:null;if(change)e.category=change;}
  const main=document.getElementById('default-currency').value;settingsDraft.webPreferences.mainCurrency=main;
  persistAll(next,settingsDraft.categories,workers,settingsDraft.webPreferences);
  document.getElementById('settings-dialog').close();renderShifts();if(editingShiftId===null)applyCategoryRate();syncToCloud();showMessage('ההגדרות נשמרו');
 }catch{showMessage('לא ניתן לשמור את ההגדרות. השינויים עדיין פתוחים');}
}

function openCategoryDialog(name=''){
 if(!settingsDraft)return;editingCategoryName=name;const cat=settingsDraft.categories.find(c=>c.name===name);
 document.getElementById('category-name').value=name;document.getElementById('category-name').readOnly=false;
 document.getElementById('category-rate').value=cat?.defaultRate??40;
 document.getElementById('category-currency').value=WorkCategories.currency(name,settingsDraft.webPreferences);
 document.getElementById('category-default').checked=name===WorkCategories.defaultName(settingsDraft.categories,settingsDraft.webPreferences);
 document.getElementById('category-error').textContent='';document.getElementById('category-dialog').showModal();
}
function rememberAssignment(oldName,newName){
 if(!oldName||oldName===newName)return;
 settingsDraft.reassignments={...settingsDraft.reassignments};
 for(const [name,target] of Object.entries(settingsDraft.reassignments))if(target===oldName)settingsDraft.reassignments[name]=newName;
 Object.defineProperty(settingsDraft.reassignments,oldName,{value:newName,enumerable:true,writable:true,configurable:true});
}
function saveCategoryDialog(){
 try{const name=document.getElementById('category-name').value.trim();
 settingsDraft=WorkCategories.rename(settingsDraft,editingCategoryName,name,WorkTransfer.number(document.getElementById('category-rate').value),document.getElementById('category-currency').value);
 rememberAssignment(editingCategoryName,name);
 if(document.getElementById('category-default').checked)settingsDraft.webPreferences.defaultCategory=name;
 document.getElementById('category-dialog').close();renderCategorySettings();
 }catch(e){document.getElementById('category-error').textContent=e.message;}
}
async function deleteCategory(name){
 const def=WorkCategories.defaultName(settingsDraft.categories,settingsDraft.webPreferences);
 if(name===def){showMessage('לפני מחיקת ברירת המחדל יש לבחור קטגוריה אחרת באמצעות העריכה');return;}
 if(!await confirmAction('מחיקת קטגוריה','המשמרות לא יימחקו. הן יועברו לקטגוריה „'+def+'” ללא שינוי שעות, תעריף, מטבע או סכום. השינוי יישמר עם שמירת ההגדרות.'))return;
 if(!settingsDraft||settingsOwner!==currentUserId)return;
 settingsDraft=WorkCategories.remove(settingsDraft,name);rememberAssignment(name,def);renderCategorySettings();
}
function installDismiss(dialog){
 let outside=false;const isOutside=e=>{const r=dialog.getBoundingClientRect();return e.clientX<r.left||e.clientX>r.right||e.clientY<r.top||e.clientY>r.bottom;};
 dialog.addEventListener('pointerdown',e=>{outside=e.target===dialog&&isOutside(e);});
 dialog.addEventListener('click',e=>{if(outside&&e.target===dialog&&isOutside(e)&&dialog.id!=='confirm-dialog'){if(dialog.id==='add-modal')closeAddModal();else dialog.close();}outside=false;});
}
function confirmAction(title,text,label='מחיקה'){
 document.querySelector('#confirm-dialog button[value=confirm]').textContent=label;
 const dialog=document.getElementById('confirm-dialog');if(dialog.open)return Promise.resolve(false);
 document.getElementById('confirm-title').textContent=title;document.getElementById('confirm-text').textContent=text;
 return new Promise(resolve=>{dialog.returnValue='cancel';dialog.addEventListener('close',()=>resolve(dialog.returnValue==='confirm'),{once:true});dialog.showModal();document.getElementById('confirm-cancel').focus();});
}
function updateGroupHours(){const hours=Number(document.getElementById('modal-hours').value)||0;const others=[...document.querySelectorAll('#group-rows [data-field="hours"]')].reduce((sum,e)=>sum+(Number(e.value)||0),0);document.getElementById('group-total-hours').textContent='סה״כ בקבוצה: '+Number((hours+others).toFixed(4))+' שעות';}
function clearHistorySearch(){document.getElementById('search-box').value='';renderShifts();document.getElementById('search-box').focus({preventScroll:true});}
function closeHistorySearch(){document.getElementById('search-box').value='';document.getElementById('history-search').hidden=true;renderShifts();document.querySelector('[aria-label="חיפוש בהיסטוריה"]').focus({preventScroll:true});}
function installLongPress(summary,id){
 let timer,start,held=false;
 const cancel=()=>{clearTimeout(timer);start=null;};
 summary.addEventListener('pointerdown',e=>{if(selectionMode||e.pointerType==='mouse')return;held=false;start={x:e.clientX,y:e.clientY};timer=setTimeout(()=>{held=true;ignoreSelectionClickUntil=Date.now()+800;enterSelection(id);},500);});
 summary.addEventListener('pointermove',e=>{if(start&&Math.hypot(e.clientX-start.x,e.clientY-start.y)>10)cancel();});
 for(const event of ['pointerup','pointercancel','pointerleave'])summary.addEventListener(event,cancel);
 summary.addEventListener('contextmenu',e=>{if(held||selectionMode)e.preventDefault();});
 summary.addEventListener('click',e=>{if(held){e.preventDefault();e.stopImmediatePropagation();held=false;}},true);
}
async function shareText(text){
 try{if(navigator.share){await navigator.share({text});return;}await navigator.clipboard.writeText(text);showMessage('הטקסט הועתק לשיתוף');}
 catch(e){if(e.name==='AbortError')return;document.getElementById('share-text').value=text;document.getElementById('share-dialog').showModal();}
}
function shareDisplayed(){if(displayedEntries.length)shareText(WorkSharing.summary(displayedEntries,selectedCategory));else showMessage('אין משמרות לשיתוף');}
function exportDisplayed(){download('filtered-shifts.csv',WorkTransfer.csv(displayedEntries),'text/csv;charset=utf-8');}

function selectMainCurrency(currency){
 document.getElementById('default-currency').value=currency;
 document.getElementById('settings-currency-icon').textContent=currency;
 document.querySelectorAll('[data-main-currency]').forEach(button=>button.setAttribute('aria-pressed',String(button.dataset.mainCurrency===currency)));
}
function addCategoryInline(){
 if(!settingsDraft)return;
 try{const name=document.getElementById('new-category-name').value.trim();
 settingsDraft=WorkCategories.rename(settingsDraft,'',name,WorkTransfer.number(document.getElementById('new-category-rate').value),document.getElementById('new-category-currency').value);
 document.getElementById('new-category-name').value='';document.getElementById('new-category-error').textContent='';renderCategorySettings();
 }catch(e){document.getElementById('new-category-error').textContent=e.message;}
}
function canImportFromSettings(){
 if(settingsDraft&&(JSON.stringify({categories:settingsDraft.categories,webPreferences:settingsDraft.webPreferences})!==settingsBase||document.getElementById('default-currency').value!==(webPreferences.mainCurrency||'₪'))){showMessage('יש לשמור או לבטל את שינויי ההגדרות לפני ייבוא נתונים');return false;}
 return true;
}
function reviewSettingsImport(){
 if(!canImportFromSettings())return;
 const text=document.getElementById('settings-import-text').value.trim();
 if(!text){showMessage('נא להדביק נתונים לפני הייבוא');document.getElementById('settings-import-text').focus();return;}
 openTransfer(text);
}
function chooseImportFile(){if(canImportFromSettings())document.getElementById('csv-file-input').click();}
async function copyBackup(){
 const text=backupText();
 try{await navigator.clipboard.writeText(text);showMessage('הגיבוי הועתק ללוח');}
 catch{document.getElementById('share-text').value=text;document.getElementById('share-dialog').showModal();}
}
function setWorkerPaid(id,index,paid){
 try{const next=shifts.map(entry=>{if(entry.id!==id)return entry;const members=JSON.parse(entry.groupWorkersJson||'[]');if(!members[index])throw Error('עובד לא נמצא');members[index]={...members[index],isPaid:paid};return {...entry,groupWorkersJson:JSON.stringify(members)};});persistAll(next);renderShifts();syncToCloud();showMessage('מצב התשלום לעובד עודכן');}
 catch{renderShifts();showMessage('השינוי לא נשמר. נסה שוב');}
}
