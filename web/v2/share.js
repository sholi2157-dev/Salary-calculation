(function (root) {
'use strict';
// Copy is technical; share is recipient-ready. Contract: docs/sharing-20261021.md.
const node = typeof module !== 'undefined';
const Pay = node ? require('./pay.js') : root.KisPay;
const Rules = node ? require('./rules.js') : root.KisRules;
const Plan = node ? require('./plan.js') : root.KisPlan;
const pad = n => String(n).padStart(2, '0');
const Heb = () => node ? require('./hebdate.js') : root.KisHebrew;
// The user's date choice for shared texts (owner feedback 2.3.1); the default keeps the shared fixtures civil. Spreadsheets always use civilDate.
let dateMode = 'GREG';
// What the user gets paid with (from the profile): a report to a worker asks for the same kinds. Empty: all three.
let askKinds = [];
const setTextChoices = (mode, kinds) => { dateMode = ['GREG','HEB','BOTH'].includes(mode) ? mode : 'GREG'; askKinds = Array.isArray(kinds) ? kinds.slice() : []; };
function civilDate(ms) { const d = new Date(ms); return pad(d.getDate()) + '/' + pad(d.getMonth()+1) + '/' + d.getFullYear(); }
function date(ms) {
  if (dateMode === 'HEB') { const H = Heb(); return H.day(ms) + ' ' + H.gematria(H.fromMillis(ms)[0]); }
  if (dateMode === 'BOTH') { const H = Heb(); return H.day(ms) + ' (' + H.civilShort(ms, false) + ')'; }
  return civilDate(ms);
}
const money = (v,c) => Pay.format(v,c), hours = Pay.hours;
const note = e => String(e.notes ?? '').trim();
// Owner request 2026-11-24: every shared text says what all shifts have in common once (place, rate, year, paid or not),
// and each shift line carries only what differs. Copy is the same text without the greeting and the thanks.
function time(e) {
 if (!e.isTimeRange || !e.startTime || !e.endTime) return '';
 return e.startTime + '–' + e.endTime + (e.endTime <= e.startTime ? ' (למחרת)' : '');
}
const pause = e => Pay.isJob(e) || !time(e) ? 0 : Math.max(0, Math.round(Rules.span(e.startTime,e.endTime).minutes - e.hours*60));
const yearOf = ms => dateMode === 'HEB' ? Heb().fromMillis(ms)[0] : new Date(ms).getFullYear();
/** A date without its year, for lines under a heading that already names the year. */
function shortDate(ms) {
 if (dateMode === 'HEB') return Heb().day(ms);
 if (dateMode === 'BOTH') return date(ms);
 const d = new Date(ms); return pad(d.getDate()) + '/' + pad(d.getMonth()+1);
}
const sameYear = rows => new Set(rows.map(e => yearOf(e.date))).size === 1;
function range(rows) {
 const a = rows[0].date, b = rows[rows.length-1].date;
 if (date(a) === date(b)) return date(a);
 return (sameYear(rows) ? shortDate(a) : date(a)) + '–' + date(b);
}
const rateText = e => Pay.pay(e) == null ? 'תעריף לא ידוע' : money(e.hourlyRate,e.currency) + ' לשעה';
/** The hourly rate when every shift with a known hourly rate has the same one (and currency); otherwise null. */
function commonRate(rows) {
 const r = rows.filter(e => !Pay.isJob(e) && Pay.pay(e) != null); if (!r.length) return null;
 return r.every(e => e.hourlyRate === r[0].hourlyRate && e.currency === r[0].currency) ? money(r[0].hourlyRate,r[0].currency) : null;
}
const hasHours = e => !Pay.isJob(e) || e.hours > 0;
/** One line per currency: the total and whether it was paid; "שולם"/"לא שולם" once when it is true of all of it. */
function moneyLines(rows, future) {
 const out = [], tot = Object.entries(Pay.totals(rows)), known = rows.filter(e => Pay.pay(e) != null);
 // Several currencies with one state: one line ("₪100.00 ו-$10.00 · לא שולם"); the amounts are never added together.
 if (tot.length > 1 && (future || known.every(e => e.isPaid) || known.every(e => !e.isPaid)))
  out.push((future ? 'צפי: ' : 'סכום: ') + tot.map(([c,v]) => money(v,c)).join(' ו-') + (future ? '' : known[0].isPaid ? ' · שולם' : ' · לא שולם'));
 else for (const [c,total] of Object.entries(Pay.totals(rows))) {
  if (future) { out.push('צפי: ' + money(total,c)); continue; }
  const known = rows.filter(e => e.currency === c && Pay.pay(e) != null);
  const paid = Pay.totals(known.filter(e => e.isPaid))[c] ?? 0, unpaid = Pay.totals(known.filter(e => !e.isPaid))[c] ?? 0;
  out.push('סכום: ' + money(total,c) + (known.every(e => e.isPaid) ? ' · שולם' : known.every(e => !e.isPaid) ? ' · לא שולם'
   : ' · שולם ' + money(paid,c) + ' · לא שולם ' + money(unpaid,c)));
 }
 const missing = Pay.pending(rows).length;
 if (missing) out.push(rows.length === 1 ? 'הסכום עוד לא ידוע.'
  : missing === 1 ? 'חסר סכום ל' + (Pay.isJob(Pay.pending(rows)[0]) ? 'עבודה אחת' : 'משמרת אחת') + '; היא לא נכללה בסכום.'
  : 'חסר סכום ל-' + missing + ' משמרות; הן לא נכללו בסכום.');
 return out;
}
/** Bonus, money passed through and the note of one shift on lines of their own. */
function extras(e) {
 const out = [];
 if (Pay.bonus(e)>0) out.push('בונוס: ' + money(Pay.bonus(e),e.currency) + ' (כלול בסכום)');
 if (Pay.passThrough(e)>0) out.push('החזר הוצאות: ' + money(Pay.passThrough(e),e.currency) + ' (לא כלול בסכום)');
 if (note(e)) out.push('הערה: ' + note(e));
 return out;
}
/** The facts of one shift that are not shared with the others: [date, time, break, hours (× rate), amount, paid, extras]. */
function shiftLine(e, o) {
 const p = [o.short ? shortDate(e.date) : date(e.date)];
 if (time(e)) p.push(time(e)); if (pause(e)) p.push('הפסקה ' + pause(e) + ' דקות');
 if (o.single) { if (hasHours(e)) p.push(hours(e.hours) + ' שעות'); if (!Pay.isJob(e)) p.push(rateText(e)); return p.join(' · '); }
 const showRate = !o.rate && !Pay.isJob(e) && Pay.pay(e) != null;
 if (hasHours(e)) p.push(hours(e.hours) + ' שעות' + (showRate ? ' × ' + money(e.hourlyRate,e.currency) : ''));
 p.push(Pay.pay(e) == null ? (Pay.isJob(e) ? 'סכום לא ידוע' : 'תעריף לא ידוע') : money(Pay.pay(e),e.currency));
 if (o.mixed) p.push(e.isPaid ? 'שולם' : 'לא שולם');
 if (Pay.bonus(e)>0) p.push('כולל בונוס ' + money(Pay.bonus(e),e.currency));
 if (Pay.passThrough(e)>0) p.push('החזר הוצאות ' + money(Pay.passThrough(e),e.currency) + ' (לא כלול)');
 if (note(e)) p.push('הערה: ' + note(e));
 return p.join(' · ');
}
/** The lines of one workplace (rows sorted by date), after its heading. */
function body(rows, future) {
 if (rows.length === 1) return [shiftLine(rows[0], { single: true }), ...moneyLines(rows, future), ...extras(rows[0])];
 const rate = commonRate(rows), out = [];
 if (rows.some(hasHours)) out.push('סה״כ ' + hours(rows.reduce((n,e)=>n+e.hours,0)) + ' שעות' + (rate ? ', ' + rate + ' לשעה' : '') + '.');
 out.push(...moneyLines(rows, future), 'פירוט:');
 const o = { rate, short: sameYear(rows), mixed: !future && rows.some(e => e.isPaid) && rows.some(e => !e.isPaid) };
 for (const e of rows) out.push(shiftLine(e, o));
 return out;
}
const ordered = rows => [...rows].sort((a,b)=>a.date-b.date);
function byPlace(rows) {
 const groups = new Map();
 for (const e of ordered(rows)) { if (!groups.has(e.category)) groups.set(e.category,[]); groups.get(e.category).push(e); }
 return [...groups];
}
/** "מסעדה · 21 משמרות (01/10–30/10/2026)": the heading of a workplace in a copy or in a report of several workplaces. */
const placeTitle = (place, es, future) => place + (future ? ' · מתוכנן' : '') + (es.length > 1 ? ' · ' + es.length + ' משמרות (' + range(es) + ')' : '');
function workerName(entries, query) {
 const q=String(query??'').trim(); if (!q) return null;
 return [...new Set(entries.flatMap(e=>Pay.workers(e).map(w=>w.name)))].find(n=>n.toLowerCase()===q.toLowerCase()) ?? null;
}
function workerRows(entries,name) {
 return entries.flatMap(e=>Pay.workers(e).filter(w=>w.name===name).map(w=>{
  const p=Pay.workerPay(w,e), r=Pay.workerRate(w,e);
  // The owner's time range and break are not the worker's facts; the shift note is attached.
  return {...e,hours:w.hours,hourlyRate:r??0,totalEarnings:p??0,payKnown:p!=null,isPaid:w.isPaid,bonus:w.bonus,passThrough:0,isTimeRange:false,startTime:null,endTime:null,isGroupShift:false};
 }));
}
/** The report text: [share] adds the greeting, the request for payment details (to a worker) and the thanks. */
function report(entries, name, share, future) {
 const rows = name ? workerRows(entries,name) : entries;
 if (!rows.length) return 'אין משמרות להצגה';
 const places = byPlace(rows), out = [];
 if (places.length === 1 && share) {
  const [place, es] = places[0], n = es.length, mine = name ? 'שלך' : 'שלי';
  const what = n === 1 ? (Pay.isJob(es[0]) ? 'העבודה' : 'המשמרת') + (future ? ' המתוכננת ' : ' ') + mine : n + ' המשמרות ' + mine;
  out.push((name ? 'היי ' + name : 'היי') + ', סיכמתי פה את ' + what + ' ב' + place + (n > 1 ? ' (' + range(es) + ')' : '') + ':', ...body(es, future));
 } else {
  if (share) out.push((name ? 'היי ' + name : 'היי') + ', סיכמתי פה את המשמרות ' + (name ? 'שלך' : 'שלי') + ':');
  else if (name) out.push('עובד: ' + name);
  places.forEach(([place, es], i) => { if (i || share) out.push(''); out.push(placeTitle(place, es, future), ...body(es, future)); });
  if (share && out[1] === '') out.splice(1, 1);
 }
 if (!share) return out.join('\n');
 // A report to someone the user pays asks for where to send the money.
 if (name && !future && rows.some(e=>!e.isPaid&&Pay.pay(e)!=null)) out.push(askPayment(askKinds));
 out.push('תודה!'); return out.join('\n');
}
const summary = (entries, name=null, future=false) => report(entries, name, true, future);
const copy = (entries, name=null, future=false) => report(entries, name, false, future);
const ASK_PAYMENT='אפשר לשלוח לי את ה-Zelle, הביט או פרטי החשבון שלך, ואעביר את התשלום.';
/** "אפשר לשלוח לי את ה-Zelle שלך…" for the kinds filled in ('zelle','bit','bank'); none or all three: ASK_PAYMENT. */
function askPayment(kinds){ const names=[['zelle','ה-Zelle'],['bit','הביט'],['bank','פרטי החשבון']].filter(([k])=>(kinds||[]).includes(k)).map(([,n])=>n);
 if(!names.length||names.length===3)return ASK_PAYMENT; return 'אפשר לשלוח לי את '+(names.length===1?names[0]:names[0]+' או '+names[1])+' שלך, ואעביר את התשלום.'; }
/** The user's own payment details (Zelle, Bit, bank), when they choose to add them to what they share. */
const paymentLines=(zelle,bit,bank)=>[[zelle,'Zelle: '],[bit,'ביט: '],[bank,'חשבון בנק: ']].map(([v,l])=>String(v??'').trim()?l+String(v).trim():null).filter(Boolean);
/** [text] with the payment details before its closing "תודה!" (or at its end). */
function withPayment(text,lines){ if(!lines.length)return text; const block=['לתשלום:',...lines].join('\n'); return text.endsWith('\nתודה!')?text.slice(0,-'תודה!'.length)+block+'\nתודה!':text+'\n'+block; }
const personal=e=>summary([e]);
const job=personal;
/** The team report of a group shift: what the employer pays in all, once, then each person's hours and amount. */
function employer(e,ownName=null,technical=false,future=false) {
 const own=Pay.pay(e), ws=Pay.workers(e), pays=ws.map(w=>Pay.employerPay(w,e)), isJob=Pay.isJob(e);
 const total=own==null||pays.some(p=>p==null)?null:Math.round((own+pays.reduce((a,b)=>a+b,0)+Pay.passThrough(e))*100)/100;
 const out=[technical?e.category+' · '+date(e.date)+(future?' · מתוכנן':''):'היי, סיכמתי פה את העבודה'+(future?' המתוכננת':'')+' ב'+e.category+' ב-'+date(e.date)+':'];
 out.push((future?'צפי לצוות: ':'סכום הצוות: ')+(total==null?'עוד לא ידוע (חסר תעריף)':money(total,e.currency)+(future?'':e.isPaid?' · שולם':' · לא שולם')),'פירוט:');
 out.push((String(ownName??'').trim()||'החלק שלי')+': '+(isJob&&e.hours===0?'':hours(e.hours)+' שעות · ')+(own==null?(isJob?'סכום לא ידוע':'תעריף לא ידוע'):money(own,e.currency)));
 // On a job team a fixed sum has no hours to show.
 ws.forEach((w,i)=>out.push(w.name+': '+(isJob&&w.amount!=null&&w.hours===0?'':hours(w.hours)+' שעות · ')+(pays[i]==null?'תעריף לא ידוע':money(pays[i],e.currency))));
 if(Pay.passThrough(e)>0)out.push('החזר הוצאות: '+money(Pay.passThrough(e),e.currency));
 if(note(e))out.push('הערה: '+note(e));
 if(!technical)out.push('תודה!');return out.join('\n');
}
function worker(e,w,technical=false,future=false) {
 // Pass one worker only, even if two workers happen to share a name.
 const single={...e,groupWorkersJson:JSON.stringify([w.source??w])};
 return report([single],w.name,!technical,future);
}
/** A weekly-plan day: the same shape as a saved planned shift. */
function planned(o,technical=false) {
 const h=Rules.span(o.start,o.end).minutes/60, at=Plan.startMillis(o.date,'12:00');
 const out=[technical?o.category+' · מתוכנן':'היי, סיכמתי פה את המשמרת המתוכננת שלי ב'+o.category+':',
  [date(at),o.start+'–'+o.end+(o.end<=o.start?' (למחרת)':''),hours(h)+' שעות',o.rate==null?'תעריף לא ידוע':money(o.rate,o.currency)+' לשעה'].join(' · '),
  o.rate==null?'הסכום עוד לא ידוע.':'צפי: '+money(Pay.money(h,o.rate),o.currency)];
 if(!technical)out.push('תודה!');return out.join('\n');
}
// Saved future entries are also estimates, even though they are not weekly-plan occurrences.
const entry=(e,technical=false,future=false)=>report([e],null,!technical,future);
const excelHeaders=['עבודה','תאריך','שעת התחלה','שעת סיום','שעות עבודה','הפסקה בדקות','תעריף שעתי','סכום','מטבע','סטטוס תשלום','סוג','בונוס','הערה','שכר ידוע'];
// Prevent spreadsheet formula execution from user-entered text; numeric columns stay numeric.
const textCell=v=>{const s=String(v??'');return /^[\s\u0000-\u001f\u200e\u200f\u202a-\u202e\u2066-\u2069]*[=+\-@]/.test(s)?"'"+s:s;};
// Round table cells with the existing decimal money contract; never recalculate hours × rate or write back to history.
const numberCell=v=>typeof v==='number'&&Number.isFinite(v)?Pay.fixed2(Pay.money(1,v)):textCell(v);
// The exact duration, not hours rounded to 2 places (audit M-43): 1:40 is 1:40, a timer's seconds stay (8:00:12).
const exactDuration=h=>{const t=Math.round((Number(h)||0)*3600),s=t%60,m=Math.floor(t/60)%60;return Math.floor(t/3600)+':'+String(m).padStart(2,'0')+(s?':'+String(s).padStart(2,'0'):'');};
const validTime=t=>typeof t==='string'&&/^(?:[01]?\d|2[0-3]):[0-5]\d$/.test(t.trim());
function table(entries){return [excelHeaders,...ordered(entries).map(e=>{
 const known=Pay.payKnown(e), job=Pay.isJob(e);
 const pause=!job&&e.isTimeRange&&validTime(e.startTime)&&validTime(e.endTime)?Math.max(0,Math.round(Rules.span(e.startTime,e.endTime).minutes-e.hours*60)):null;
 return [textCell(e.category),civilDate(e.date),textCell(e.startTime),textCell(e.endTime),typeof e.hours==='number'&&e.hours>=0&&Number.isFinite(e.hours)?exactDuration(e.hours):numberCell(e.hours),pause==null?'':textCell(pause),
  !known||job?'':numberCell(e.hourlyRate),known?numberCell(e.totalEarnings):'',textCell(e.currency),e.isPaid?'שולם':'ממתין',
  job?'קבלנות':e.isGroupShift?'משמרת קבוצתית':'משמרת',Pay.bonus(e)>0?numberCell(Pay.bonus(e)):'',textCell(e.notes??''),known?'כן':'חסר'];
 })];}
const quote=(value,d)=>{const s=String(value??'');return s.includes(d)||/[\n\r"]/.test(s)?'"'+s.replaceAll('"','""')+'"':s;};
const excel=entries=>table(entries).map(row=>row.map(s=>quote(s,'\t')).join('\t')).join('\n');
const csv=entries=>'\ufeff'+table(entries).map(row=>row.map(s=>quote(s,',')).join(',')).join('\r\n');
const api={date,shortDate,civilDate,askPayment,setTextChoices,money,hours,personal,job,employer,worker,planned,summary,copy,entry,workerName,excel,csv,exactDuration,ASK_PAYMENT,paymentLines,withPayment};
if(node)module.exports=api;else root.KisShare=api;
})(globalThis);
