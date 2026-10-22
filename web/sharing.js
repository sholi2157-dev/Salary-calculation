(function(root){
'use strict';
const amount=(currency,value)=>currency+Number(value).toFixed(2);
const date=e=>new Date(e.date).toLocaleDateString('en-GB');
const workerRate=(worker,entry)=>worker.workerRate??entry.workerRate??entry.hourlyRate;
const employerRate=(worker,entry)=>worker.employerRate??entry.employerRate??entry.hourlyRate;
function personal(e){return `היי, להלן פרטי המשמרת שלי מיום ${date(e)}:\nקטגוריה: ${e.category}\nשעות עבודה: ${e.hours} שעות\nתעריף שעתי: ${amount(e.currency,e.hourlyRate)}\nסה"כ לתשלום: ${amount(e.currency,e.totalEarnings)}`;}
function group(e){const members=JSON.parse(e.groupWorkersJson||'[]');const total=e.totalEarnings+members.reduce((n,w)=>n+w.hours*employerRate(w,e),0);return `היי, להלן סיכום שעות עבודה ליום ${date(e)}:\n**סה"כ לתשלום (כולל כולם): ${amount(e.currency,total)}**\n---\nפירוט:\nהחלק שלי: ${e.hours} שעות (${amount(e.currency,e.totalEarnings)})\n`+members.map(w=>`${w.name}: ${w.hours} שעות (${amount(e.currency,w.hours*employerRate(w,e))})`).join('\n')+'\n---';}
function summary(entries){const groups=new Map();for(const e of entries){if(!groups.has(e.category))groups.set(e.category,[]);groups.get(e.category).push(e);}return [...groups].map(([name,rows])=>{const totals={};for(const e of rows)totals[e.currency]=(totals[e.currency]||0)+e.totalEarnings;return `${name} | ${Number(rows.reduce((n,e)=>n+e.hours,0).toFixed(4))} שעות\n`+Object.entries(totals).map(([c,n])=>amount(c,n)).join('\n');}).join('\n\n');}
const api={personal,group,summary,workerRate,employerRate};if(typeof module!=='undefined')module.exports=api;else root.WorkSharing=api;
})(globalThis);
