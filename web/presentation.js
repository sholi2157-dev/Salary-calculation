// DOM rendering only. Persistence, account ownership and event orchestration remain in parity.js.
const iconPaths={
 upload:'M11 16h2V7l3 3 1.4-1.4L12 3 6.6 8.6 8 10l3-3zM4 17v4h16v-4h-2v2H6v-2z',
 download:'M11 3h2v9l3-3 1.4 1.4L12 16l-5.4-5.6L8 9l3 3zM4 17v4h16v-4h-2v2H6v-2z',
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


function createShiftCard(entry,compact=false){
 const card=document.createElement('details');card.className='journal-card'+(compact?' compact':'');card.dataset.shiftId=entry.id;
 const summary=document.createElement('summary');const date=new Date(entry.date).toLocaleDateString('he-IL',{weekday:'long',day:'2-digit',month:'2-digit',year:'numeric'});
 summary.innerHTML='<span class="category-avatar">'+escapeHtml(entry.category.slice(0,1))+'</span><span class="journal-info"><b>'+(!compact?uiIcon('label'):'')+escapeHtml(entry.category)+'</b><small>'+date+'</small></span><span class="journal-amount"><b>'+(!compact?uiIcon('cash'):'')+'<bdi>'+escapeHtml(entry.currency)+Number(entry.totalEarnings).toFixed(2)+'</bdi></b><small>'+(!compact?uiIcon('timer'):'')+Number(entry.hours).toFixed(1)+' ש׳ <i class="status-dot '+(entry.isPaid?'paid':'')+'" title="'+(entry.isPaid?'שולם':'ממתין')+'"></i></small></span><span class="chevron">'+uiIcon('down')+'</span>';
 if(!compact){installLongPress(summary,entry.id);}
 if(!compact&&selectionMode){card.classList.toggle('selected',selectedShiftIds.has(entry.id));const mark=document.createElement('input');mark.type='checkbox';mark.checked=selectedShiftIds.has(entry.id);mark.setAttribute('aria-label','בחירת '+entry.category+' '+date);mark.onclick=event=>event.stopPropagation();mark.onchange=()=>toggleSelection(entry.id);summary.append(mark);summary.onclick=event=>{event.preventDefault();if(Date.now()>=ignoreSelectionClickUntil)toggleSelection(entry.id);};}
 const body=document.createElement('div');body.className='journal-details';const p=document.createElement('p');p.className='shift-work-facts';p.textContent=(entry.isTimeRange?entry.startTime+' – '+entry.endTime+' · ':'')+entry.hours+' שעות · '+entry.hourlyRate+' '+entry.currency+' לשעה';body.append(p);if(entry.notes){const notes=document.createElement('p');notes.className='shift-note';notes.textContent='הערות: '+entry.notes;body.append(notes);}
 if(entry.isGroupShift){try{
  const members=JSON.parse(entry.groupWorkersJson||'[]'),hours=members.reduce((n,w)=>n+Number(w.hours),0);
  const totals=document.createElement('div');totals.className='group-card-totals';
  const total=document.createElement('strong');total.textContent='סה״כ לתשלום (כולל כולם): '+entry.currency+(entry.totalEarnings+hours*(entry.employerRate??entry.hourlyRate)).toFixed(2);
  const mine=document.createElement('small');mine.textContent='החלק שלי (כולל הפרש תעריפים): '+entry.currency+(entry.totalEarnings+hours*((entry.employerRate??entry.hourlyRate)-(entry.workerRate??entry.hourlyRate))).toFixed(2);
  totals.append(total,mine);body.append(totals);
  const roster=document.createElement('div');roster.className='group-card-workers';
  for(const [index,worker] of members.entries()){
   const row=document.createElement('div');row.className='group-card-worker'+(worker.isPaid?' is-paid':'');
   const info=document.createElement('div'),name=document.createElement('strong'),detail=document.createElement('small');name.textContent=worker.name;detail.textContent=worker.hours+' שעות · '+entry.currency+Number(worker.hours*(entry.workerRate??entry.hourlyRate)).toFixed(2);info.append(name,detail);
   const status=document.createElement('label');status.className='worker-payment';const check=document.createElement('input');check.type='checkbox';check.checked=Boolean(worker.isPaid);check.setAttribute('aria-label','שולם ל'+worker.name);check.onchange=()=>setWorkerPaid(entry.id,index,check.checked);status.append(check,document.createTextNode(worker.isPaid?'שולם':'ממתין'));
   row.append(info,status);roster.append(row);
  }body.append(roster);
 }catch{const p=document.createElement('p');p.textContent='לא ניתן להציג את פרטי הקבוצה. הנתונים נשמרו ללא שינוי.';body.append(p);}}
 const actions=document.createElement('div');actions.className='journal-card-actions';for(const [name,label,action] of [['check',entry.isPaid?'סמן כממתין':'סמן כשולם',()=>togglePaid(entry.id)],['share','שיתוף החלק שלי',()=>shareText(WorkSharing.personal(entry))],...(entry.isGroupShift?[['document','דוח הקבוצה',()=>shareText(WorkSharing.group(entry))]]:[]),['edit','עריכה',()=>editShift(entry.id)],['trash','מחיקה',()=>deleteShift(entry.id)]]){const button=document.createElement('button');button.className='btn-mini'+(name==='check'?' payment-action '+(entry.isPaid?'is-paid':'is-unpaid'):'');button.innerHTML=uiIcon(name)+'<span>'+label+'</span>';button.setAttribute('aria-label',label);button.title=label;button.onclick=action;actions.append(button);}body.append(actions);card.append(summary,body);return card;
}


function renderCategorySettings(){
 if(!settingsDraft)return;const box=document.getElementById('settings-categories');box.replaceChildren();
 const def=WorkCategories.defaultName(settingsDraft.categories,settingsDraft.webPreferences);
 for(const c of settingsDraft.categories){const row=document.createElement('div');row.className='category-setting';const text=document.createElement('div');const name=document.createElement('strong');name.textContent=c.name;const detail=document.createElement('small');detail.textContent=WorkCategories.currency(c.name,settingsDraft.webPreferences)+Number(c.defaultRate).toFixed(2)+' / שעה'+(c.name===def?' · ברירת המחדל':'');text.append(name,detail);row.append(text);
  for(const [icon,label,action] of [['edit','עריכת '+c.name,()=>openCategoryDialog(c.name)],['trash','מחיקת '+c.name,()=>deleteCategory(c.name)]]){const b=document.createElement('button');b.className='icon-btn';b.innerHTML=uiIcon(icon);b.setAttribute('aria-label',label);b.onclick=action;row.append(b);}box.append(row);
 }
}