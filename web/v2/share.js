(function (root) {
'use strict';
// Copy is technical; share is recipient-ready. Contract: docs/sharing-20261021.md.
const node = typeof module !== 'undefined';
const Pay = node ? require('./pay.js') : root.KisPay;
const Rules = node ? require('./rules.js') : root.KisRules;
const Plan = node ? require('./plan.js') : root.KisPlan;
const pad = n => String(n).padStart(2, '0');
function date(ms) { const d = new Date(ms); return pad(d.getDate()) + '/' + pad(d.getMonth()+1) + '/' + d.getFullYear(); }
const money = (v,c) => Pay.format(v,c), hours = Pay.hours;
const note = e => String(e.notes ?? '').trim();
const amount = e => Pay.pay(e) == null ? 'ממתין להשלמה' : money(Pay.pay(e),e.currency);
function time(e) {
 if (!e.isTimeRange || !e.startTime || !e.endTime) return '';
 return e.startTime + '–' + e.endTime + (e.endTime <= e.startTime ? ' (למחרת)' : '');
}
function details(e) {
 const out = [];
 if (time(e)) out.push('שעות: ' + time(e));
 if (!Pay.isJob(e) && e.isTimeRange && e.startTime && e.endTime) {
  const mins = Math.max(0, Math.round(Rules.span(e.startTime,e.endTime).minutes - e.hours*60));
  if (mins) out.push('הפסקה: ' + mins + ' דקות');
 }
 if (!Pay.isJob(e)) out.push('תעריף לשעה: ' + (Pay.pay(e) == null ? 'עדיין לא ידוע' : money(e.hourlyRate,e.currency)));
 if (Pay.bonus(e)>0) out.push('בונוס: ' + money(Pay.bonus(e),e.currency) + ' (כלול בסכום)');
 if (note(e)) out.push('הערה: ' + note(e));
 return out;
}
function amounts(rows) {
 const out=[];
 for (const [c,total] of Object.entries(Pay.totals(rows))) {
  out.push('סכום: ' + money(total,c));
  out.push('שולם: ' + money(Pay.totals(rows.filter(e=>e.isPaid))[c] ?? 0,c));
  out.push('יתרה לתשלום: ' + money(Pay.totals(rows.filter(e=>!e.isPaid))[c] ?? 0,c));
 }
 const missing=Pay.pending(rows).length;
 if (missing) out.push('סכום חסר: ' + missing + ' (לא נכלל בסכומים)');
 return out;
}
// Sharing reads like a message: no redundant zero-payment lines or technical count labels.
function humanAmounts(rows, worker=false) {
 const out=[];
 for(const [c,total] of Object.entries(Pay.totals(rows))) {
  const paid=Pay.totals(rows.filter(e=>e.isPaid))[c]??0, unpaid=Pay.totals(rows.filter(e=>!e.isPaid))[c]??0;
  if(!rows.some(e=>e.currency===c&&!e.isPaid&&Pay.pay(e)!=null)) out.push('השכר בסך '+money(total,c)+' שולם במלואו.');
  else if(paid===0) out.push((worker?'מגיע לך: ':'השכר לתשלום: ')+money(total,c)+'.');
  else out.push('השכר הכולל: '+money(total,c)+'. מתוכו שולם '+money(paid,c)+', ונותר לתשלום '+money(unpaid,c)+'.');
 }
 const missing=Pay.pending(rows).length;
 if(missing)out.push(missing===1?'סכום חסר עבור '+(rows.every(Pay.isJob)?'עבודה אחת':'משמרת אחת')+'; היא לא נכללה בסיכום הכספי.':'סכום חסר עבור '+missing+' משמרות; הן לא נכללו בסיכום הכספי.');
 return out;
}
const ordered = rows => [...rows].sort((a,b)=>a.date-b.date);
const dates = rows => [...new Set(ordered(rows).map(e=>date(e.date)))].join(', ');
function workerName(entries, query) {
 const q=String(query??'').trim(); if (!q) return null;
 return [...new Set(entries.flatMap(e=>Pay.workers(e).map(w=>w.name)))].find(n=>n.toLowerCase()===q.toLowerCase()) ?? null;
}
function workerRows(entries,name) {
 return entries.flatMap(e=>Pay.workers(e).filter(w=>w.name===name).map(w=>{
  const p=Pay.workerPay(w,e), r=Pay.workerRate(w,e);
  // The owner's time range and break are not the worker's facts; the shift note is attached.
  return {...e,hours:w.hours,hourlyRate:r??0,totalEarnings:p??0,payKnown:p!=null,isPaid:w.isPaid,bonus:w.bonus,isTimeRange:false,startTime:null,endTime:null,isGroupShift:false};
 }));
}
function copy(entries, name=null) {
 const rows=name?workerRows(entries,name):entries;
 if (!rows.length) return 'אין משמרות להצגה';
 const groups=new Map();
 for (const e of ordered(rows)) { if (!groups.has(e.category)) groups.set(e.category,[]); groups.get(e.category).push(e); }
 const text=[...groups].map(([place,es])=>{
  const out=['עבודה: '+place,'כמות משמרות: '+es.length,'כמות שעות: '+hours(es.reduce((n,e)=>n+e.hours,0)),...amounts(es),'תאריכים: '+dates(es)];
  if (es.length===1) out.push(...details(es[0]));
  else { out.push('פירוט:'); for (const e of es) out.push(date(e.date)+' | '+hours(e.hours)+' שעות | '+amount(e)+' | '+(e.isPaid?'שולם':'ממתין'),...details(e)); }
  return out.join('\n');
 }).join('\n\n');
 return (name?'עובד: '+name+'\n':'')+text;
}
function summary(entries,name=null) {
 const rows=name?workerRows(entries,name):entries;
 if (!rows.length) return 'אין משמרות להצגה';
 if (new Set(rows.map(e=>e.category)).size>1) return copy(entries,name);
 const place=rows[0].category, n=rows.length;
 const intro=name ? 'היי '+name+', סיכמתי פה את '+(n===1?'המשמרת שלך':'המשמרות שלך')+' ב'+place+':'
  : 'היי, סיכמתי פה את '+(n===1?(Pay.isJob(rows[0])?'העבודה שלי':'המשמרת שלי'):n+' המשמרות שלי')+' ב'+place+':';
 const totalHours=hours(rows.reduce((s,e)=>s+e.hours,0));
 const who=name?'עבדת':'עבדתי';
 const out=[intro, ...(n===1?['בתאריך '+dates(rows)+(rows.some(e=>!Pay.isJob(e)||e.hours>0)?' '+who+' '+totalHours+' שעות.':'.')]:['בתאריכים '+dates(rows)+'.','סה״כ '+who+' '+totalHours+' שעות.']),...humanAmounts(rows,!!name)];
 if (n===1) out.push(...details(rows[0]));
 else { out.push('פירוט המשמרות:');for(const e of ordered(rows)) out.push(date(e.date)+' | '+hours(e.hours)+' שעות | '+amount(e)+' | '+(e.isPaid?'שולם':'ממתין'),...details(e)); }
 out.push('תודה!');return out.join('\n');
}
const personal=e=>summary([e]);
const job=personal;
function employer(e,ownName=null,technical=false,future=false) {
 const own=Pay.pay(e), ws=Pay.workers(e), pays=ws.map(w=>Pay.employerPay(w,e));
 const total=own==null||pays.some(p=>p==null)?null:Math.round((own+pays.reduce((a,b)=>a+b,0))*100)/100;
 const out=[...(technical?['עבודה: '+e.category,'כמות משמרות: 1','כמות שעות: '+hours(e.hours+ws.reduce((n,w)=>n+w.hours,0))]:['היי, סיכמתי פה את עבודת הצוות ב'+e.category+':']),
  'תאריכים: '+date(e.date),'סכום הצוות: '+(total==null?'ממתין להשלמת תעריף':money(total,e.currency)),'פירוט:'];
 const job=Pay.isJob(e);
 out.push((String(ownName??'').trim()||'החלק שלי')+': '+(job&&e.hours===0?'':hours(e.hours)+' שעות | ')+(own==null?(job?'סכום חסר':'תעריף חסר'):money(own,e.currency)));
 // On a job team a fixed sum has no hours to show.
 ws.forEach((w,i)=>out.push(w.name+': '+(job&&w.amount!=null&&w.hours===0?'סכום קבוע':hours(w.hours)+' שעות')+' | '+(pays[i]==null?'תעריף חסר':money(pays[i],e.currency))));
 if(note(e))out.push('הערה: '+note(e));
 if(!technical)out.push('תודה!');return future?plannedText(out.join('\n')):out.join('\n');
}
function worker(e,w,technical=false,future=false) {
 // Pass one worker only, even if two workers happen to share a name.
 const single={...e,groupWorkersJson:JSON.stringify([w.source??w])};
 const text=technical?copy([single],w.name):summary([single],w.name);return future?plannedText(text):text;
}
function planned(o,technical=false) {
 const h=Rules.span(o.start,o.end).minutes/60;
 const out=[...(technical?['עבודה: '+o.category,'כמות משמרות: 1']:['היי, סיכמתי פה את המשמרת המתוכננת שלי ב'+o.category+':']),
  'מצב: מתוכנן — עוד לא בוצע','תאריכים: '+date(Plan.startMillis(o.date,'12:00')),
  'שעות: '+o.start+'–'+o.end+(o.end<=o.start?' (למחרת)':''),'כמות שעות: '+hours(h),
  'תעריף לשעה: '+(o.rate==null?'עדיין לא ידוע':money(o.rate,o.currency)),
  'צפי בלבד: '+(o.rate==null?'ממתין להשלמת תעריף':money(Pay.amount(h,o.rate),o.currency))];
 if(!technical)out.push('תודה!');return out.join('\n');
}
// Saved future entries are also estimates, even though they are not weekly-plan occurrences.
function entry(e,technical=false,future=false) {
 if(!future)return technical?copy([e]):personal(e);
 const out=[technical?copy([e]):personal(e)];
 return plannedText(out[0]);
}
function plannedText(text) { return 'מתוכנן — עוד לא בוצע\n'+text.replaceAll('יתרה לתשלום:','צפי שטרם בוצע:').replaceAll('שולם:','סומן בתכנון כשולם:').replaceAll('סכום:','צפי:').replaceAll('סכום הצוות:','צפי לצוות:').replaceAll('השכר לתשלום:','צפי השכר:').replaceAll('מגיע לך:','צפי השכר:').replaceAll('נותר לתשלום','צפי שטרם בוצע').replaceAll('השכר הכולל:','צפי השכר הכולל:').replaceAll('השכר בסך','צפי השכר בסך').replaceAll(' שולם במלואו.',' סומן בתכנון כשולם.').replaceAll('מתוכו שולם','סומן בתכנון כשולם');
}
const excelHeaders=['עבודה','תאריך','שעת התחלה','שעת סיום','שעות עבודה','הפסקה בדקות','תעריף שעתי','סכום','מטבע','סטטוס תשלום','סוג','בונוס','הערה','שכר ידוע'];
// Prevent spreadsheet formula execution from user-entered text; numeric columns stay numeric.
const textCell=v=>/^[\s]*[=+\-@]/.test(String(v))?"'"+v:String(v??'');
function table(entries){return [excelHeaders,...ordered(entries).map(e=>{
 const known=Pay.payKnown(e), job=Pay.isJob(e);
 const pause=!job&&time(e)?Math.max(0,Math.round(Rules.span(e.startTime,e.endTime).minutes-e.hours*60)):null;
 return [textCell(e.category),date(e.date),e.startTime??'',e.endTime??'',Pay.fixed2(e.hours),pause==null?'':String(pause),
  !known||job?'':Pay.fixed2(e.hourlyRate),known?Pay.fixed2(e.totalEarnings):'',textCell(e.currency),e.isPaid?'שולם':'ממתין',
  job?'קבלנות':e.isGroupShift?'משמרת קבוצתית':'משמרת',Pay.bonus(e)>0?Pay.fixed2(Pay.bonus(e)):'',textCell(e.notes??''),known?'כן':'חסר'];
 })];}
const quote=(s,d)=>s.includes(d)||/[\n\r"]/.test(s)?'"'+s.replaceAll('"','""')+'"':s;
const excel=entries=>table(entries).map(row=>row.map(s=>quote(s,'\t')).join('\t')).join('\n');
const csv=entries=>'\ufeff'+table(entries).map(row=>row.map(s=>quote(s,',')).join(',')).join('\r\n');
const api={date,money,hours,personal,job,employer,worker,planned,summary,copy,entry,workerName,excel,csv};
if(node)module.exports=api;else root.KisShare=api;
})(globalThis);
