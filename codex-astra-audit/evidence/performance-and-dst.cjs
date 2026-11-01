const fs=require('fs'),vm=require('vm'),{performance}=require('perf_hooks');
const base='/workspace/salary-audit/sources/web-latest';
global.KisPay=require(base+'/web/v2/pay.js');
global.KisUi={monthKey:ms=>{let d=new Date(ms);return d.getFullYear()*12+d.getMonth()},startOfDay:ms=>{let d=new Date(ms);d.setHours(0,0,0,0);return +d}};
vm.runInThisContext(fs.readFileSync(base+'/web/kis/history.js','utf8'));
const summary=require(base+'/web/v2/summary.js');
let day=new Date(2026,2,27).getTime(),next=new Date(2026,2,28,12).getTime();
const f={period:'RANGE',from:day,to:day,pay:'ALL',sort:'NEWEST'};
const dst=KisHistory.apply([{id:'following-day',date:next,category:'בדיקה',hours:1,totalEarnings:60,currency:'₪',payKnown:true}],f,'');
let perf=[];for(const n of [0,10,100,1000,10000]){const rows=Array.from({length:n},(_,i)=>({id:String(i),date:Date.UTC(2026,9,1+i%28),createdAt:i,category:'מקום '+i%100,notes:'בדיקה '.repeat(50),hours:100/60,hourlyRate:60,totalEarnings:100,payKnown:true,isPaid:i%2===0,currency:i%2?'$':'₪',groupWorkersJson:''}));let totals,filtered,s;const times=[];for(let k=0;k<10;k++){let t=performance.now();totals=KisPay.totals(rows);filtered=KisHistory.apply(rows,{period:'ALL',pay:'ALL',sort:'NEWEST'},'בדיקה');s=summary.summary(rows.map(r=>({date:'2026-10-15',hours:r.hours,total:r.totalEarnings,payKnown:r.payKnown,paid:r.isPaid,kind:'shift',currency:r.currency,category:r.category,start:'09:00',end:'10:40'})),'2026-10','2026-11-01');times.push(performance.now()-t)}times.sort((a,b)=>a-b);perf.push({rows:n,notesCharsPerRow:300,categories:Math.min(n,100),combinedMedianMs:times[5],maxMs:times[9],filtered:filtered.length,totals,summaryCount:s.count,jsonBytes:Buffer.byteLength(JSON.stringify(rows))})}
console.log(JSON.stringify({environment:{node:process.version,TZ:process.env.TZ},dst:{range:'2026-03-27 only',expectedIds:[],actualIds:dst.map(x=>x.id)},performance:perf,limitation:'pure functions only; not browser rendering, device memory or storage quota'},null,2));
