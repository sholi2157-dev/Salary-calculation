(function(root){
'use strict';
const amount=(currency,value)=>currency+Number(value).toFixed(2);
const date=e=>new Date(e.date).toLocaleDateString('en-GB');
function personal(e){return `היי, להלן פרטי המשמרת שלי מיום ${date(e)}:\nקטגוריה: ${e.category}\nשעות עבודה: ${e.hours} שעות\nתעריף שעתי: ${amount(e.currency,e.hourlyRate)}\nסה"כ לתשלום: ${amount(e.currency,e.totalEarnings)}`;}
function group(e){const members=JSON.parse(e.groupWorkersJson||'[]'),rate=e.employerRate??e.hourlyRate;const total=e.totalEarnings+members.reduce((n,w)=>n+w.hours*rate,0);return `היי, להלן סיכום שעות עבודה ליום ${date(e)}:\nסה"כ לתשלום (כולל כולם): ${amount(e.currency,total)}\n---\nפירוט:\nאני: ${e.hours} שעות (${amount(e.currency,e.totalEarnings)})\n`+members.map(w=>`${w.name}: ${w.hours} שעות (${amount(e.currency,w.hours*rate)})`).join('\n')+'\n---';}
function summary(entries,category){const totals={};for(const e of entries)totals[e.currency]=(totals[e.currency]||0)+e.totalEarnings;return `קטגוריה: ${category==='הכל'?'כל הקטגוריות':category} | סך שעות: ${Number(entries.reduce((n,e)=>n+e.hours,0).toFixed(4))} | סה"כ לתשלום: ${Object.entries(totals).map(([c,n])=>amount(c,n)).join(' · ')}.`;}
const api={personal,group,summary};if(typeof module!=='undefined')module.exports=api;else root.WorkSharing=api;
})(globalThis);
