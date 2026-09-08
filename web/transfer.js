(function (root) {
'use strict';
const clean = s => String(s ?? '').replace(/[\u200e\u200f\u202a-\u202e\ufeff]/g, '').trim();
const key = s => clean(s).toLowerCase().replace(/[\s"׳״'_:\/-]/g, '');
const aliases = {
 category:['קטגוריה','מעסיק','מעסיק/קטגוריה','category','employer'], date:['תאריך','date'],
 hours:['שעות','משך','משך שעות','hours','duration'], rate:['תעריף','תעריף שעתי','שכר לשעה','hourlyRate','rate'],
 total:['שכר לתשלום','סהכ רווח','סהכ','סך הכל','סכום','totalEarnings','total'],
 paid:['סטטוס','סטטוס תשלום','שולם','isPaid','paid','status'], notes:['הערות','notes'], currency:['מטבע','currency'],
 start:['שעת כניסה','התחלה','startTime','start'],end:['שעת יציאה','סיום','endTime','end']
};
for (const k in aliases) aliases[k] = aliases[k].map(key);
function check(condition, message) { if (!condition) throw Error(message); }
function number(text) {
 let s = clean(text).replace(/USD|ILS|₪|\$|\s|\u00a0/gi,'');
 if(s.includes(',') && s.includes('.')) {
  check(/^\d{1,3}(,\d{3})+\.\d+$/.test(s) || /^\d{1,3}(\.\d{3})+,\d+$/.test(s),'מפרידי מספר לא תקינים');
  s = s.lastIndexOf(',') > s.lastIndexOf('.') ? s.replace(/\./g,'').replace(',','.') : s.replace(/,/g,'');
 } else if(s.includes(',')) {
  check(!/^[+-]?\d{1,3},\d{3}$/.test(s),'מספר עמום — השתמש בנקודה עשרונית וללא מפריד אלפים'); s=s.replace(',','.');
 }
 check(s !== '' && /^[+-]?(?:\d+(?:\.\d*)?|\.\d+)(?:[eE][+-]?\d+)?$/.test(s) && Number.isFinite(Number(s)) && Number(s)>=0,'מספר לא תקין: '+text);
 return Number(s);
}
function table(text,delimiter) {
 const rows=[]; let row=[],cell='',quoted=false;
 const endCell=()=>{row.push(cell);cell='';};
 const endRow=()=>{endCell();if(row.some(s=>s.trim()))rows.push(row);row=[];};
 for(let i=0;i<text.length;i++) { const c=text[i];
  if(c==='"' && quoted && text[i+1]==='"'){cell+='"';i++;}
  else if(c==='"' && (quoted || !cell)) quoted=!quoted;
  else if(c===delimiter && !quoted)endCell();
  else if((c==='\r'||c==='\n') && !quoted){endRow();if(c==='\r'&&text[i+1]==='\n')i++;}
  else cell+=c;
 }
 check(!quoted,'מרכאות לא סגורות');endRow();return rows;
}
function date(text) {
 let y,m,d; let p;
 if((p=text.match(/^(\d{4})-(\d{1,2})-(\d{1,2})$/))) [,y,m,d]=p.map(Number);
 else if((p=text.match(/^(\d{1,2})([/.\-])(\d{1,2})\2(\d{4})$/))) {d=+p[1];m=+p[3];y=+p[4];}
 else if(/^\d{13}$/.test(text))return +text;
 else if(/^\d{5}$/.test(text)){check(+text>=20000&&+text<=100000,'תאריך אקסל מחוץ לטווח');const v=new Date(1899,11,30);v.setDate(v.getDate()+Number(text));return v.getTime();}
 else throw Error('תאריך לא מזוהה: '+text+' — השתמש ביום/חודש/שנה');
 const v=new Date(y,m-1,d);check(v.getFullYear()===y&&v.getMonth()===m-1&&v.getDate()===d,'תאריך לא תקין');return v.getTime();
}
function decodeTable(input) {
 const candidates=['\t',',',';','|'].flatMap(d=>{try{return [table(input.replace(/^\ufeff/,''),d)];}catch{return [];}}).filter(r=>r.length);
 const score=r=>r[0].filter(h=>Object.values(aliases).some(a=>a.includes(key(h)))).length;
 candidates.sort((a,b)=>score(b)-score(a));const rows=candidates[0];check(rows,'לא נמצאה טבלה תקינה');
 const header=rows[0].map(key),columns={};
 for(const k in aliases){check(header.filter(h=>aliases[k].includes(h)).length<=1,'כותרת מופיעה יותר מפעם אחת');columns[k]=header.findIndex(h=>aliases[k].includes(h));}
 check(['category','date','hours','rate'].every(k=>columns[k]>=0),'יש להעתיק גם כותרות: קטגוריה, תאריך, שעות ותעריף שעתי');check(rows.length>1,'הטבלה מכילה כותרות בלבד');
 const entries=rows.slice(1).map((row,i)=>{try{
  check(row.length===header.length,'מספר העמודות אינו תואם לכותרות');const cell=k=>clean(row[columns[k]]);
  check(cell('category'),'חסרה קטגוריה');const duration=cell('hours');let hours;
  if(/^\d+:\d{2}$/.test(duration)){const p=duration.split(':').map(Number);check(p[1]<60,'דקות לא תקינות');hours=p[0]+p[1]/60;}else hours=number(duration);
  const rate=number(cell('rate')),total=cell('total')?number(cell('total')):hours*rate;
  const currencies=[cell('currency'),cell('rate'),cell('total')].map(s=>/\$|USD/i.test(s)?'$':/₪|ILS|^שקל(?:ים)?$/i.test(s)?'₪':null).filter(Boolean);
  check(new Set(currencies).size<=1,'נמצאו מטבעות סותרים');check(!cell('currency')||['₪','$','ILS','USD','שקל','שקלים'].includes(cell('currency').toUpperCase()),'מטבע לא מזוהה');
  const status=cell('paid').toLowerCase(),yes=['שולם','כן','true','paid','yes','1'],no=['','ממתין','לא','לא שולם','false','unpaid','no','0'];check([...yes,...no].includes(status),'סטטוס תשלום לא מזוהה');
  const start=cell('start')||null,end=cell('end')||null;check(Boolean(start)===Boolean(end),'חסרה שעת התחלה או סיום');for(const t of [start,end].filter(Boolean))check(/^(?:[01]?\d|2[0-3]):[0-5]\d$/.test(t),'שעה לא תקינה');
  const timestamp=date(cell('date'));
  return normalize({category:cell('category'),date:timestamp,createdAt:timestamp,hours,hourlyRate:rate,totalEarnings:total,isPaid:yes.includes(status),notes:cell('notes'),currency:currencies[0]||'₪',startTime:start,endTime:end,isTimeRange:Boolean(start)});
 }catch(e){throw Error(`שורה ${i+2}: ${e.message}`);}});
 return {formatVersion:2,entries,categories:[...new Map(entries.map(e=>[e.category,{name:e.category,defaultRate:e.hourlyRate}])).values()],workers:[]};
}
function normalize(e) {
 check(e&&typeof e.category==='string'&&e.category.trim(),'חסרה קטגוריה');
 for(const k of ['date','hours','hourlyRate','totalEarnings'])check(typeof e[k]==='number'&&Number.isFinite(e[k])&&e[k]>=0,'ערך לא תקין: '+k);
 check(typeof e.isPaid==='boolean','סטטוס תשלום לא תקין');check(['₪','$'].includes(e.currency??'₪'),'מטבע לא מזוהה');
 const group=e.groupWorkersJson||'';if(group)check(Array.isArray(JSON.parse(group)),'רשימת עובדים לא תקינה');
 for(const k of ['employerRate','workerRate'])check(e[k]==null||(typeof e[k]==='number'&&Number.isFinite(e[k])&&e[k]>=0),'תעריף קבוצה לא תקין');
 return {category:e.category,date:e.date,createdAt:e.createdAt??e.date,isTimeRange:e.isTimeRange??false,startTime:e.startTime??null,endTime:e.endTime??null,hours:e.hours,hourlyRate:e.hourlyRate,totalEarnings:e.totalEarnings,isPaid:e.isPaid,notes:String(e.notes??''),currency:e.currency??'₪',isGroupShift:e.isGroupShift??false,employerRate:e.employerRate??null,workerRate:e.workerRate??null,groupWorkersJson:group};
}
function decode(text){
 if(!/^[\s\ufeff]*[\[{]/.test(text))return decodeTable(text);
 let root=JSON.parse(text.replace(/^\ufeff/,''));if(Array.isArray(root))root={entries:root};
 check([1,2].includes(root.formatVersion??1),'גרסת גיבוי לא נתמכת');check(Array.isArray(root.entries),'לא נמצאו משמרות בגיבוי');
 const categories=(root.categories||[]).map(c=>{check(typeof c.name==='string'&&c.name.trim(),'קטגוריה לא תקינה');return {name:c.name,defaultRate:number(c.defaultRate??40)};});
 const workers=(root.workers||[]).map(w=>{check(typeof w.name==='string','עובד לא תקין');return {name:w.name};});
 return {formatVersion:2,entries:root.entries.map(normalize),categories,workers};
}
function missing(existing,incoming){
 const signature=e=>JSON.stringify({...normalize(e),createdAt:0});const counts=new Map();
 for(const e of existing){const k=signature(e);counts.set(k,(counts.get(k)||0)+1);}
 return incoming.filter(e=>{const k=signature(e),n=counts.get(k)||0;if(n){counts.set(k,n-1);return false;}return true;});
}
function totals(entries){const t={};for(const e of entries)t[e.currency]=(t[e.currency]||0)+e.totalEarnings;return t;}
function csv(entries){const fields=['category','date','hours','hourlyRate','totalEarnings','isPaid','notes','currency','startTime','endTime'];const quote=s=>'"'+String(s??'').replace(/"/g,'""')+'"';return '\ufeff'+[fields.join(','),...entries.map(e=>fields.map(k=>quote(k==='date'?new Date(e.date).toLocaleDateString('en-GB'):e[k])).join(','))].join('\r\n');}
const api={decode,decodeTable,normalize,missing,totals,csv,number,table};if(typeof module!=='undefined')module.exports=api;else root.WorkTransfer=api;
})(globalThis);
