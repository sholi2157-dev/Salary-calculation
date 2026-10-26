(function (root) {
'use strict';
const clean = s => String(s ?? '').replace(/[\u200e\u200f\u202a-\u202e\ufeff]/g, '').trim();
const key = s => clean(s).toLowerCase().replace(/[\s"׳״'_:\/-]/g, '');
const aliases = {
 category:['עבודה','קטגוריה','מעסיק','מעסיק/קטגוריה','category','employer'], date:['תאריך','date'],
 hours:['שעות עבודה','שעות','משך','משך שעות','hours','duration'], rate:['תעריף','תעריף שעתי','שכר לשעה','hourlyRate','rate'],
 total:['שכר לתשלום','סהכ רווח','סהכ','סך הכל','סכום','totalEarnings','total'],
 paid:['סטטוס','סטטוס תשלום','שולם','isPaid','paid','status'], notes:['הערה','הערות','notes'], currency:['מטבע','currency'],
 start:['שעת התחלה','שעת כניסה','התחלה','startTime','start'],end:['שעת סיום','שעת יציאה','סיום','endTime','end'], kind:['סוג','kind'], bonus:['בונוס','bonus']
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
  const job=['קבלנות','job'].includes(cell('kind')), modern=columns.kind>=0;
  const bonus=cell('bonus')?number(cell('bonus')):0;
  const payKnown=!modern || (job?!!cell('total'):!!cell('rate')||!!cell('total'));
  const rate=job||!payKnown?0:number(cell('rate')),total=!payKnown?0:cell('total')?number(cell('total')):hours*rate+bonus;
  const currencies=[cell('currency'),cell('rate'),cell('total')].map(s=>/\$|USD/i.test(s)?'$':/₪|ILS|^שקל(?:ים)?$/i.test(s)?'₪':null).filter(Boolean);
  check(new Set(currencies).size<=1,'נמצאו מטבעות סותרים');check(!cell('currency')||['₪','$','ILS','USD','שקל','שקלים'].includes(cell('currency').toUpperCase()),'מטבע לא מזוהה');
  const status=cell('paid').toLowerCase(),yes=['שולם','כן','true','paid','yes','1'],no=['','ממתין','לא','לא שולם','false','unpaid','no','0'];check([...yes,...no].includes(status),'סטטוס תשלום לא מזוהה');
  const start=cell('start')||null,end=cell('end')||null;check(Boolean(start)===Boolean(end),'חסרה שעת התחלה או סיום');for(const t of [start,end].filter(Boolean))check(/^(?:[01]?\d|2[0-3]):[0-5]\d$/.test(t),'שעה לא תקינה');
  const timestamp=date(cell('date'));
  return normalize({category:cell('category'),date:timestamp,createdAt:timestamp,hours,hourlyRate:rate,totalEarnings:total,isPaid:yes.includes(status),notes:cell('notes'),currency:currencies[0]||'₪',startTime:start,endTime:end,isTimeRange:Boolean(start),kind:job?'job':'shift',payKnown,bonus:job?0:bonus});
 }catch(e){throw Error(`שורה ${i+2}: ${e.message}`);}});
 return {formatVersion:2,entries,categories:[...new Map(entries.map(e=>[e.category,{name:e.category,defaultRate:e.hourlyRate}])).values()],workers:[]};
}
// ---- 2.0 (backup formatVersion 3), ported from the Android data/WorkBackup.kt ----
// Android org.json reading rules for the 2.0 fields: optBoolean accepts true/false (or "true"/"false"),
// anything else falls back to the default; optDouble accepts numbers or numeric strings.
const KIND_SHIFT='shift',KIND_JOB='job';
function optBoolean(v,fallback){if(typeof v==='boolean')return v;if(typeof v==='string'){const l=v.toLowerCase();if(l==='true')return true;if(l==='false')return false;}return fallback;}
function jsonDouble(v){if(typeof v==='number')return v;if(typeof v==='string'){const t=v.trim();if(/^[+-]?(NaN|Infinity|(?:\d+\.?\d*|\.\d+)(?:[eE][+-]?\d+)?)[fFdD]?$/.test(t))return t.includes('NaN')?NaN:Number(t.replace(/[fFdD]$/,''));}return null;}
function optDouble(v,fallback){const d=jsonDouble(v);return d===null?fallback:d;}
function nullableRate(v,message){if(v==null)return null;const d=jsonDouble(v);check(d!==null&&Number.isFinite(d)&&d>=0,message);return d;}
const isTime=t=>typeof t==='string'&&/^\d{1,2}:\d{2}$/.test(t.trim())&&Number(t.trim().split(':')[0])<24&&Number(t.trim().split(':')[1])<60;
function civil(date){
 // Proleptic Gregorian round trip (V2Rules.daysFromCivil/civilFromDays) without the Date object, so time zones never matter.
 const [y,m,d]=date.split('-').map(Number);const dim=[31,(y%4===0&&y%100!==0)||y%400===0?29:28,31,30,31,30,31,31,30,31,30,31];
 return m>=1&&m<=12&&d>=1&&d<=dim[m-1];
}
/** A real calendar day from 2000 on (2026-02-30 or 1900-01-01 would make no sense as a plan day). */
const planDate=d=>typeof d==='string'&&/^\d{4}-\d{2}-\d{2}$/.test(d)&&d>='2000-01-01'&&civil(d);
const text=v=>typeof v==='string'&&v.trim()!=='';
/** The 2.0 entry fields with Android decode defaults: kind, payKnown, bonus, seriesKey, autoConfirmed. */
function v2Fields(e){
 const kind=e.kind==null?KIND_SHIFT:e.kind;check(kind===KIND_SHIFT||kind===KIND_JOB,'סוג רשומה לא מוכר');
 const bonus=optDouble(e.bonus,0);check(Number.isFinite(bonus)&&bonus>=0,'בונוס לא תקין');
 const seriesKey=e.seriesKey==null?null:String(e.seriesKey).trim()?String(e.seriesKey):null;
 return {kind,payKnown:optBoolean(e.payKnown,true),bonus,seriesKey,autoConfirmed:optBoolean(e.autoConfirmed,false)};
}
const V2_DEFAULTS={kind:KIND_SHIFT,payKnown:true,bonus:0,seriesKey:null,autoConfirmed:false};
function passThroughOf(e){const v=optDouble(e.passThrough,0);check(Number.isFinite(v)&&v>=0,'החזר הוצאות לא תקין');return v;}
function workerBonus(w){const b=optDouble(w?.bonus,0);return Number.isFinite(b)&&b>0?b:0;}
function normalize(e) {
 check(e&&typeof e.category==='string'&&e.category.trim(),'חסרה קטגוריה');
 for(const k of ['date','hours','hourlyRate','totalEarnings'])check(typeof e[k]==='number'&&Number.isFinite(e[k])&&e[k]>=0,'ערך לא תקין: '+k);
 check(e.createdAt==null||(typeof e.createdAt==='number'&&Number.isFinite(e.createdAt)&&e.createdAt>=0),'זמן יצירה לא תקין');for(const field of ['isTimeRange','isGroupShift'])check(e[field]==null||typeof e[field]==='boolean','סוג דיווח לא תקין');
 check(typeof e.isPaid==='boolean','סטטוס תשלום לא תקין');check(['₪','$'].includes(e.currency??'₪'),'מטבע לא מזוהה');
 const group=e.groupWorkersJson||'';if(group){const members=JSON.parse(group);check(Array.isArray(members),'רשימת עובדים לא תקינה');for(const w of members){check(w&&typeof w.name==='string'&&w.name.trim()&&typeof w.hours==='number'&&Number.isFinite(w.hours)&&w.hours>=0&&(w.isPaid===undefined||typeof w.isPaid==='boolean'),'פרטי עובד בקבוצה לא תקינים');for(const k of ['employerRate','workerRate','bonus'])check(w[k]==null||(typeof w[k]==='number'&&Number.isFinite(w[k])&&w[k]>=0),'תעריף עובד בקבוצה לא תקין');}}
 for(const k of ['employerRate','workerRate'])check(e[k]==null||(typeof e[k]==='number'&&Number.isFinite(e[k])&&e[k]>=0),'תעריף קבוצה לא תקין');
 const out={category:e.category,date:e.date,createdAt:e.createdAt??e.date,isTimeRange:e.isTimeRange??false,startTime:e.startTime??null,endTime:e.endTime??null,hours:e.hours,hourlyRate:e.hourlyRate,totalEarnings:e.totalEarnings,isPaid:e.isPaid,notes:String(e.notes??''),currency:e.currency??'₪',isGroupShift:e.isGroupShift??false,employerRate:e.employerRate??null,workerRate:e.workerRate??null,groupWorkersJson:group};
 // Backward compatibility: a 2.0 field is added only when it differs from its default, so an entry without
 // 2.0 data normalizes to exactly the pre-2.0 object (same keys, same order) and stored data/duplicate
 // signatures stay unchanged. Readers treat a missing field as its default. A worker's bonus stays in groupWorkersJson.
 const v2=v2Fields(e);for(const k of Object.keys(V2_DEFAULTS))if(v2[k]!==V2_DEFAULTS[k])out[k]=v2[k];
 // Money passed through the user (2.2): owed by the employer, never income. Present only when used.
 const pass=passThroughOf(e);if(pass>0)out.passThrough=pass;
 return out;
}
/** Version 3 only when a 2.0 feature is actually used (unknown pay, bonus, fixed-price job, series, worker bonus). */
function needsVersion3(entries){return entries.some(e=>{const v2=v2Fields(e);if(Object.keys(V2_DEFAULTS).some(k=>v2[k]!==V2_DEFAULTS[k])||passThroughOf(e)>0)return true;
 if(!String(e.groupWorkersJson||'').trim())return false;try{const m=JSON.parse(e.groupWorkersJson);return Array.isArray(m)&&m.some(w=>workerBonus(w)>0);}catch{return false;}});}
function seriesOf(s){
 check(s&&typeof s==='object','תוכנית לא תקינה');
 const days=typeof s.daysMask==='number'?Math.trunc(s.daysMask):NaN;
 check(text(s.id)&&days>=1&&days<=127,'תוכנית לא תקינה');
 check(isTime(s.startTime)&&isTime(s.endTime),'שעת תוכנית לא תקינה');
 check(planDate(s.fromDate)&&planDate(s.untilDate)&&s.fromDate<=s.untilDate,'תאריכי תוכנית לא תקינים');
 const currency=s.currency??'₪';check(currency==='₪'||currency==='$','מטבע לא מזוהה');
 check(text(s.category),'חסרה קטגוריה');
 const createdAt=typeof s.createdAt==='number'&&Number.isFinite(s.createdAt)?Math.trunc(s.createdAt):0;
 return {id:s.id,category:s.category,daysMask:days,startTime:s.startTime,endTime:s.endTime,hourlyRate:nullableRate(s.hourlyRate,'תעריף תוכנית לא תקין'),currency,fromDate:s.fromDate,untilDate:s.untilDate,createdAt};
}
function exceptionOf(x){
 check(x&&typeof x==='object'&&text(x.seriesId)&&planDate(x.date),'תאריך חריגה לא תקין');
 for(const t of [x.startTime,x.endTime])check(t==null||isTime(t),'שעת חריגה לא תקינה');
 return {seriesId:x.seriesId,date:x.date,cancelled:optBoolean(x.cancelled,false),hold:optBoolean(x.hold,false),startTime:x.startTime??null,endTime:x.endTime??null,rateOverridden:optBoolean(x.rateOverridden,false),hourlyRate:nullableRate(x.hourlyRate,'תעריף חריגה לא תקין')};
}
function androidPreferences(local){const currencies={};for(const [k,v] of Object.entries(local))if(k.startsWith('categoryCurrency:')&&['₪','$'].includes(v))Object.defineProperty(currencies,k.slice(17),{value:v,enumerable:true});return {mainCurrency:local.default_currency||'₪',defaultCategory:local.defaultCategory||'עצמאי',categoryCurrencies:currencies};}
function decode(text){
 if(!/^[\s\ufeff]*[\[{]/.test(text))return decodeTable(text);
 let root=JSON.parse(text.replace(/^\ufeff/,''));if(Array.isArray(root))root={entries:root};
 check([1,2,3].includes(root.formatVersion??1),'גרסת גיבוי לא נתמכת');check(Array.isArray(root.entries),'לא נמצאו משמרות בגיבוי');
 const categories=(root.categories||[]).map(c=>{check(typeof c.name==='string'&&c.name.trim(),'קטגוריה לא תקינה');return {name:c.name,defaultRate:number(c.defaultRate??40)};});
 const workers=(root.workers||[]).map(w=>{check(typeof w.name==='string','עובד לא תקין');return {name:w.name};});
 const entries=root.entries.map(normalize);
 // As Android (optJSONArray): a missing or non-array value means none.
 const series=(Array.isArray(root.series)?root.series:[]).map(seriesOf),exceptions=(Array.isArray(root.seriesExceptions)?root.seriesExceptions:[]).map(exceptionOf);
 // Like Android encode: version 3 only when 2.0 data is present; then (and only then) `series` and `exceptions` are returned.
 const v3=needsVersion3(entries)||series.length>0||exceptions.length>0;
 return {formatVersion:v3?3:2,entries,categories,workers,...(root.webPreferences?{webPreferences:root.webPreferences}:root.androidLocalPreferences?{webPreferences:androidPreferences(root.androidLocalPreferences)}:{}),...(v3?{series,exceptions}:{})};
}
function missing(existing,incoming){
 const signature=e=>JSON.stringify({...normalize(e),createdAt:0});const counts=new Map();
 for(const e of existing){const k=signature(e);counts.set(k,(counts.get(k)||0)+1);}
 // Like Android importBackup: an occurrence already recorded here (same seriesKey) is the same shift and is never added twice.
 const seriesKeys=new Set(existing.map(e=>v2Fields(e).seriesKey).filter(Boolean));
 return incoming.filter(e=>{const k=signature(e),n=counts.get(k)||0;if(n){counts.set(k,n-1);return false;}return true;})
  .filter(e=>{const key=v2Fields(e).seriesKey;if(key==null)return true;if(seriesKeys.has(key))return false;seriesKeys.add(key);return true;});
}
/**
 * Plans from an import: existing plans win (same id is never duplicated or overwritten) and only plans this
 * import adds take their day exceptions, as Android importBackup does. Returns the plans/exceptions to add.
 */
function missingPlans(existingSeries,existingExceptions,incomingSeries,incomingExceptions){
 const known=new Set(existingSeries.map(s=>s.id)),added=new Set(),series=[];
 for(const s of incomingSeries)if(!known.has(s.id)){known.add(s.id);added.add(s.id);series.push(s);}
 const knownX=new Set(existingExceptions.map(x=>x.seriesId+'@'+x.date)),exceptions=[];
 for(const x of incomingExceptions){const k=x.seriesId+'@'+x.date;if(added.has(x.seriesId)&&!knownX.has(k)){knownX.add(k);exceptions.push(x);}}
 return {series,exceptions};
}
/**
 * Backup JSON text. Without 2.0 data this is formatVersion 2 with exactly the structure web/app.js backupText()
 * writes (formatVersion, androidLocalPreferences, entries, categories, workers, webPreferences; 2-space indent).
 * With 2.0 data (needsVersion3, or any series/exception) it is formatVersion 3: every entry also carries
 * kind, payKnown, bonus, seriesKey, autoConfirmed, and "series"/"seriesExceptions" follow, as Android WorkBackup.encode writes them.
 * data: {entries, categories, workers, webPreferences?, androidLocalPreferences?, series?, exceptions?}
 */
function encode(data){
 const entries=(data.entries||[]).map(normalize),series=(data.series||[]).map(seriesOf),exceptions=(data.exceptions||[]).map(exceptionOf);
 const v3=needsVersion3(entries)||series.length>0||exceptions.length>0;
 const root={formatVersion:v3?3:2,androidLocalPreferences:data.androidLocalPreferences,
  // Same order as Android: the 2.0 fields, then passThrough only when used.
  entries:v3?entries.map(e=>{const row={};for(const [k,v] of Object.entries(e))if(!(k in V2_DEFAULTS)&&k!=='passThrough')row[k]=v;Object.assign(row,v2Fields(e));if(e.passThrough>0)row.passThrough=e.passThrough;return row;}):entries,
  categories:(data.categories||[]).map(({name,defaultRate})=>({name,defaultRate})),workers:(data.workers||[]).map(({name})=>({name})),webPreferences:data.webPreferences};
 if(v3){root.series=series;root.seriesExceptions=exceptions;}
 return JSON.stringify(root,null,2);
}
// Preserve imported net hours when only non-time fields are edited.
function rangeDuration(start,end){
 const parse=s=>{check(/^([01]\d|2[0-3]):[0-5]\d$/.test(s),'יש להזין שעת התחלה וסיום תקינות');const [h,m]=s.split(':').map(Number);return h*60+m;};
 let minutes=parse(end)-parse(start);if(minutes<0)minutes+=1440;return minutes;
}
function inferredBreak(entry){if(!entry?.isTimeRange)return 0;try{return Math.max(0,Math.round((rangeDuration(entry.startTime,entry.endTime)-entry.hours*60)*1000000)/1000000);}catch{return 0;}}
function rangeHours(start,end,breakMinutes=0,original=null){
 const minutes=rangeDuration(start,end),pause=number(breakMinutes);
 if(original?.isTimeRange && start===original.startTime && end===original.endTime && Math.abs(pause-inferredBreak(original))<0.000001)return original.hours;
 check(pause<minutes,'ההפסקה חייבת להיות קצרה ממשך המשמרת');
 return (minutes-pause)/60;
}
function totals(entries){const t={};for(const e of entries)t[e.currency]=(t[e.currency]||0)+e.totalEarnings;return t;}
function csv(entries,delimiter=','){const fields=['category','date','hours','hourlyRate','totalEarnings','isPaid','notes','currency','startTime','endTime'];const quote=s=>'"'+String(s??'').replace(/"/g,'""')+'"';return '\ufeff'+[fields.join(delimiter),...entries.map(e=>fields.map(k=>quote(k==='date'?new Date(e.date).toLocaleDateString('en-GB'):e[k])).join(delimiter))].join('\r\n');}
const api={decode,decodeTable,normalize,missing,missingPlans,encode,needsVersion3,planDate,totals,csv,number,table,rangeHours,inferredBreak};if(typeof module!=='undefined')module.exports=api;else root.WorkTransfer=api;
})(globalThis);
