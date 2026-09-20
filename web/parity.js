let historyPeriod='all',historyCurrency='הכל',historySort='newest',displayedEntries=[];
const iconPaths={
 close:'m6 4 6 6 6-6 2 2-6 6 6 6-2 2-6-6-6 6-2-2 6-6-6-6z',
 plus:'M11 3h2v8h8v2h-8v8h-2v-8H3v-2h8z',
 share:'M18 2a3 3 0 1 1-2.7 4.3L8.7 10a3 3 0 0 1 0 4l6.6 3.7a3 3 0 1 1-1 1.7L7.7 15a3 3 0 1 1 0-6l6.6-4.4A3 3 0 0 1 18 2z',
 document:'M5 2h10l5 5v15H5zm2 2v16h11V8h-4V4zm2 7h7v2H9zm0 4h7v2H9z',
 play:'M8 5v14l11-7zm2 3.6 5.3 3.4-5.3 3.4z',
 stop:'M6 6h12v12H6zm2 2v8h8V8z',
 copy:'M16 1H4a2 2 0 0 0-2 2v14h2V3h12zm4 4H8a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V7a2 2 0 0 0-2-2m0 16H8V7h12z',
 sort:'M3 6h18v2H3zm0 5h12v2H3zm0 5h6v2H3z',
 label:'M17.6 5H3v14h14.6l5-7zM16.6 17H5V7h11.6l3.5 5z',
 cash:'M2 4h18v14H2zm2 2v10h14V6zm8 5a3 3 0 1 1-6 0 3 3 0 0 1 6 0m10-3v12H6v2h18V8z',
 timer:'M9 1h6v2H9zm3 3a9 9 0 1 0 6.3 2.6L20 5l-1.4-1.4L17 5.2A9 9 0 0 0 12 4m0 2a7 7 0 1 1 0 14 7 7 0 0 1 0-14m-1 2h2v6h-2z',
 down:'M7 9l5 5 5-5 1.4 1.4L12 16.8l-6.4-6.4z',
 edit:'M3 17.3V21h3.7L17.8 9.9l-3.7-3.7zM20.7 7c.4-.4.4-1 0-1.4l-2.3-2.3a1 1 0 0 0-1.4 0l-1.8 1.8 3.7 3.7z',
 trash:'M6 19a2 2 0 0 0 2 2h8a2 2 0 0 0 2-2V7H6zm2-10h8v10H8zm7-6-1-1h-4L9 3H4v2h16V3z',
 check:'M9 16.2 4.8 12 3.4 13.4 9 19 21 7l-1.4-1.4z'
};
function uiIcon(name){return '<svg class="ui-icon" viewBox="0 0 24 24" aria-hidden="true" focusable="false"><path d="'+iconPaths[name]+'"/></svg>';}
function initParity(){document.querySelectorAll('[data-icon]').forEach(el=>el.innerHTML=uiIcon(el.dataset.icon));document.querySelectorAll('#settings-dialog details').forEach(el=>el.addEventListener('toggle',()=>{if(el.open)document.querySelectorAll('#settings-dialog details').forEach(other=>{if(other!==el)other.open=false;});}));
 for(const dialog of document.querySelectorAll('dialog'))installDismiss(dialog);
 document.getElementById('modal-hours').addEventListener('input',updateGroupHours);
 document.getElementById('settings-dialog').addEventListener('close',()=>{settingsDraft=null;});
 if(window.visualViewport){const resize=()=>document.body.classList.toggle('keyboard-open',window.visualViewport.height<window.innerHeight*.78);window.visualViewport.addEventListener('resize',resize);}
}
function openHistorySearch(){showPage(1);document.getElementById('history-search').hidden=false;document.getElementById('search-box').focus();}
function setPeriod(period){historyPeriod=period;document.querySelectorAll('[data-period]').forEach(b=>b.classList.toggle('selected',b.dataset.period===period));document.getElementById('period-inputs').hidden=period==='all';document.getElementById('month-label').hidden=period!=='month';document.getElementById('from-label').hidden=period!=='range';document.getElementById('to-label').hidden=period!=='range';renderShifts();}
function setPayment(value){document.getElementById('payment-filter').value=value;document.querySelectorAll('[data-payment]').forEach(b=>b.classList.toggle('selected',b.dataset.payment===value));renderShifts();}
function cycleCurrency(){historyCurrency=historyCurrency==='הכל'?'₪':historyCurrency==='₪'?'$':'הכל';document.getElementById('currency-filter').textContent=historyCurrency;renderShifts();}
function setSort(value){historySort=value;document.getElementById('sort-dialog').close();renderShifts();}
function renderHistoryControls(entries){document.getElementById('journal-title').textContent='יומן עבודה ('+entries.length+')';document.getElementById('displayed-total').textContent=formatTotals(WorkTransfer.totals(entries));const select=document.getElementById('category-filter');select.replaceChildren();for(const name of ['הכל',...new Set([...categories.map(c=>c.name),...shifts.map(s=>s.category)])]){const o=document.createElement('option');o.value=name;o.textContent=name==='הכל'?'כל הקטגוריות':name;select.append(o);}select.value=selectedCategory;}
async function copyDisplayed(kind){const reportEntries=displayedEntries;if(!reportEntries.length){showMessage('אין משמרות להעתקה');return;}const text=kind==='table'?WorkTransfer.csv(reportEntries,'\t'):WorkSharing.summary(reportEntries,selectedCategory);try{await navigator.clipboard.writeText(text);document.getElementById('report-dialog').close();showMessage('הדוח הועתק');}catch{showMessage('הדפדפן לא אפשר העתקה. אפשר לייצא קובץ דרך ההגדרות.');}}
function createShiftCard(entry,compact=false){
 const card=document.createElement('details');card.className='journal-card'+(compact?' compact':'');card.dataset.shiftId=entry.id;
 const summary=document.createElement('summary');const date=new Date(entry.date).toLocaleDateString('he-IL',{weekday:'long',day:'2-digit',month:'2-digit',year:'numeric'});
 summary.innerHTML='<span class="category-avatar">'+escapeHtml(entry.category.slice(0,1))+'</span><span class="journal-info"><b>'+(!compact?uiIcon('label'):'')+escapeHtml(entry.category)+'</b><small>'+date+'</small></span><span class="journal-amount"><b>'+(!compact?uiIcon('cash'):'')+'<bdi>'+escapeHtml(entry.currency)+Number(entry.totalEarnings).toFixed(compact?1:2)+'</bdi></b><small>'+(!compact?uiIcon('timer'):'')+Number(entry.hours).toFixed(1)+' ש׳ <i class="status-dot '+(entry.isPaid?'paid':'')+'" title="'+(entry.isPaid?'שולם':'ממתין')+'"></i></small></span><span class="chevron">'+uiIcon('down')+'</span>';
 if(!compact){installLongPress(summary,entry.id);}
 if(!compact&&selectionMode){card.classList.toggle('selected',selectedShiftIds.has(entry.id));const mark=document.createElement('input');mark.type='checkbox';mark.checked=selectedShiftIds.has(entry.id);mark.setAttribute('aria-label','בחירת '+entry.category+' '+date);mark.onclick=event=>event.stopPropagation();mark.onchange=()=>toggleSelection(entry.id);summary.append(mark);summary.onclick=event=>{event.preventDefault();if(Date.now()>=ignoreSelectionClickUntil)toggleSelection(entry.id);};}
 const body=document.createElement('div');body.className='journal-details';const p=document.createElement('p');p.textContent=(entry.isTimeRange?entry.startTime+' – '+entry.endTime+' · ':'')+entry.hours+' שעות · '+entry.hourlyRate+' '+entry.currency+' לשעה';body.append(p);if(entry.notes){const notes=document.createElement('p');notes.textContent=entry.notes;body.append(notes);}
 if(entry.isGroupShift){try{const members=JSON.parse(entry.groupWorkersJson||'[]');const heading=document.createElement('h3');heading.textContent='עבודה קבוצתית';body.append(heading);let hours=0;for(const worker of members){hours+=Number(worker.hours);const line=document.createElement('p');line.textContent=worker.name+' · '+worker.hours+' שעות · '+entry.currency+Number(worker.hours*(entry.workerRate??entry.hourlyRate)).toFixed(2)+' · '+(worker.isPaid?'שולם':'ממתין');body.append(line);}const total=document.createElement('p');total.textContent='כולל עובדים לתשלום: '+entry.currency+' '+(entry.totalEarnings+hours*(entry.employerRate??entry.hourlyRate)).toFixed(2)+' · השכר שלי כולל הפרש תעריפים: '+entry.currency+' '+(entry.totalEarnings+hours*((entry.employerRate??entry.hourlyRate)-(entry.workerRate??entry.hourlyRate))).toFixed(2);body.append(total);}catch{const p=document.createElement('p');p.textContent='לא ניתן להציג את פרטי הקבוצה. הנתונים נשמרו ללא שינוי.';body.append(p);}}
 const actions=document.createElement('div');actions.className='journal-card-actions';for(const [name,label,action] of [['check',entry.isPaid?'סמן כממתין':'סמן כשולם',()=>togglePaid(entry.id)],['share','שיתוף החלק שלי',()=>shareText(WorkSharing.personal(entry))],...(entry.isGroupShift?[['document','דוח הקבוצה',()=>shareText(WorkSharing.group(entry))]]:[]),['edit','עריכה',()=>editShift(entry.id)],['trash','מחיקה',()=>deleteShift(entry.id)]]){const button=document.createElement('button');button.className='btn-mini';button.innerHTML=uiIcon(name)+'<span>'+label+'</span>';button.setAttribute('aria-label',label);button.title=label;button.onclick=action;actions.append(button);}body.append(actions);card.append(summary,body);return card;
}

let selectionMode=false,selectedShiftIds=new Set(),ignoreSelectionClickUntil=0;
function enterSelection(id){selectionMode=true;if(id!==undefined)selectedShiftIds.add(id);renderShifts();}
function exitSelection(){selectionMode=false;selectedShiftIds.clear();renderShifts();}
function toggleSelection(id){if(selectedShiftIds.has(id))selectedShiftIds.delete(id);else selectedShiftIds.add(id);renderShifts();}
function selectAllVisible(){selectedShiftIds=new Set(displayedEntries.map(e=>e.id));renderShifts();}
function clearSelection(){selectedShiftIds.clear();renderShifts();}
function renderSelectionControls(){
 const visible=new Set(displayedEntries.map(e=>e.id));selectedShiftIds=new Set([...selectedShiftIds].filter(id=>visible.has(id)));
 document.querySelector('.journal-toolbar').classList.toggle('selecting',selectionMode);
 document.getElementById('selection-toolbar').hidden=!selectionMode;
 document.getElementById('selection-actions').hidden=!selectionMode;
 document.getElementById('selection-count').textContent='נבחרו '+selectedShiftIds.size+' משמרות';
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
 document.getElementById('settings-currency-icon').textContent=document.getElementById('default-currency').value;
 document.querySelectorAll('#settings-dialog details').forEach(d=>d.open=false);renderCategorySettings();document.getElementById('settings-dialog').showModal();
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
function renderCategorySettings(){
 if(!settingsDraft)return;const box=document.getElementById('settings-categories');box.replaceChildren();
 const def=WorkCategories.defaultName(settingsDraft.categories,settingsDraft.webPreferences);
 for(const c of settingsDraft.categories){const row=document.createElement('div');row.className='category-setting';const text=document.createElement('div');const name=document.createElement('strong');name.textContent=c.name;const detail=document.createElement('small');detail.textContent=WorkCategories.currency(c.name,settingsDraft.webPreferences)+Number(c.defaultRate).toFixed(2)+' / שעה'+(c.name===def?' · ברירת המחדל':'');text.append(name,detail);row.append(text);
  for(const [icon,label,action] of [['edit','עריכת '+c.name,()=>openCategoryDialog(c.name)],['trash','מחיקת '+c.name,()=>deleteCategory(c.name)]]){const b=document.createElement('button');b.className='icon-btn';b.innerHTML=uiIcon(icon);b.setAttribute('aria-label',label);b.onclick=action;row.append(b);}box.append(row);
 }
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
function confirmAction(title,text){
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
