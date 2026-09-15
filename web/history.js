(function(root){
'use strict';
function select(entries,f={}){
 const query=(f.search||'').trim().toLocaleLowerCase();
 const result=entries.filter(e=>{
  const d=new Date(e.date),date=d.getFullYear()+'-'+String(d.getMonth()+1).padStart(2,'0')+'-'+String(d.getDate()).padStart(2,'0');
  return (!f.category||f.category==='הכל'||e.category===f.category)&&(!query||[e.category,e.notes,d.toLocaleDateString('he-IL')].some(v=>String(v||'').toLocaleLowerCase().includes(query)))&&(!f.currency||f.currency==='הכל'||e.currency===f.currency)&&(!f.payment||f.payment==='all'||(f.payment==='paid'?e.isPaid:!e.isPaid))&&(f.period!=='month'||!f.month||date.slice(0,7)===f.month)&&(f.period!=='range'||((!f.from||date>=f.from)&&(!f.to||date<=f.to)));
 });
 return result.sort((a,b)=>f.sort==='oldest'?a.date-b.date:f.sort==='latest_added'?(b.createdAt??b.date)-(a.createdAt??a.date):b.date-a.date);
}
const api={select};if(typeof module!=='undefined')module.exports=api;else root.WorkHistory=api;
})(globalThis);
