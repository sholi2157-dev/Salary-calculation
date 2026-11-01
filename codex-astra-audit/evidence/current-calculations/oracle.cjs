const fs=require('fs');const root='/workspace/salary-audit/sources/web-latest';
const R=require(root+'/web/v2/rules.js'),P=require(root+'/web/v2/pay.js'),Plan=require(root+'/web/v2/plan.js'),S=require(root+'/web/v2/summary.js');
global.KisRules=R;global.KisPay=P;global.KisUi={startOfDay:ms=>{let d=new Date(ms);d.setHours(0,0,0,0);return +d}};require(root+'/web/kis/editor.js');const E=global.KisEditor;
const draft=(x={})=>({mode:'HOURS',category:'בדיקה',day:+new Date(2026,9,20),hours:'1:40',rate:'60',bonus:'',bonusTarget:'ME',amount:'',amountUnknown:false,rateUnknown:false,notes:'',workers:[],start:null,end:'17:00',breakMin:0,currency:'₪',...x});
const out={environment:{node:process.version,TZ:process.env.TZ||'system',source:root},checks:[],generated:{}};function check(name,expected,actual){out.checks.push({name,expected,actual,pass:JSON.stringify(expected)===JSON.stringify(actual)})}
for(const input of ['1.40','1,40','1:40']){const d=draft({hours:E.durationEdit(input)});check('100 minutes '+input,100,E.toEntry(d).totalEarnings)}
for(const h of ['0:00','1:75','24:01'])check('invalid duration '+h,false,E.evaluate(draft({hours:h})).canSave);
check('24h',1440,E.evaluate(draft({hours:'24:00'})).minutes);
check('overnight break',210,R.span('22:00','02:00',30).minutes);
check('zero clock',true,R.span('08:00','08:00').invalid);
const imported={...E.toEntry(draft()),id:'x',hours:1.234567,hourlyRate:33.3333,totalEarnings:42.12345};
check('metadata preserves historical financial precision', [1.234567,33.3333,42.12345], ((r)=>[r.hours,r.hourlyRate,r.totalEarnings])(E.toEntry({...E.fromEntry(imported),notes:'new'},imported)));
const totals=P.totals([{currency:'₪',payKnown:true,totalEarnings:100},{currency:'$',payKnown:true,totalEarnings:50},{currency:'₪',payKnown:false,totalEarnings:999}]);check('currency and unknown',{'₪':100,'$':50},totals);
const gd=draft({mode:'GROUP',hours:'2:00',rate:'50',bonus:'20',bonusTarget:'OTHERS',team:true,workers:[{name:'בדיקה',hours:'2:00',bonus:'',source:{}}]});
const ge=E.toEntry(gd);check('group OTHERS displayed equals persisted pay',ge.totalEarnings,E.evaluate(gd).total);check('group OTHERS worker bonus',20,P.workers(ge)[0].bonus);
const rec={id:'x',isJob:false,start:null,end:null,hours:1.234567,rate:null,bonus:0};check('bulk preview equals persisted amount',P.amount(rec.hours,100),R.bulkRate([rec],100).updated[0].total);
const previous={isTimeRange:true,date:Plan.startMillis('2026-10-19','22:00'),startTime:'22:00',endTime:'02:00'};
check('plan overnight clash previous day',['2026-10-20'],Plan.preview([2],'2026-10-20','2026-10-20','01:00','03:00',[previous],[]).clashes);
let centsTests=0,centsMismatches=[];for(let m=1;m<=1440;m++)for(const cents of [1,3,7,29,57,101,205,333,999,1234,3333,5000,7501,9999]){centsTests++;const expected=Number((BigInt(m)*BigInt(cents)*2n+60n)/120n)/100,actual=P.amount(m/60,cents/100);if(actual!==expected)centsMismatches.push({minutes:m,rate:cents/100,expected,actual})}
out.generated.cents={cases:centsTests,mismatches:centsMismatches.length,examples:centsMismatches.slice(0,20)};
let spans=0,spanFailures=0;for(let a=0;a<1440;a+=7)for(let b=0;b<1440;b+=11)for(const br of [0,1,30,120]){spans++;const gross=(b-a+1440)%1440,expected=Math.max(0,gross-br),actual=R.span(R.fromMinutes(a),R.fromMinutes(b),br);if(actual.minutes!==expected||actual.invalid!==(expected===0))spanFailures++}out.generated.spans={cases:spans,mismatches:spanFailures};
let groupMismatch;outer:for(let m=1;m<61;m++)for(let er=1;er<30;er++)for(let wr=0;wr<er;wr++){const e={totalEarnings:10,hourlyRate:50,payKnown:true,isGroupShift:true,currency:'₪',employerRate:er/100,workerRate:wr/100,groupWorkersJson:JSON.stringify([{name:'x',hours:m/60}])};const w=P.workers(e)[0],g=P.group(e),expected=Math.round((P.employerPay(w,e)-P.workerPay(w,e))*100)/100;if(g.profit!==expected){groupMismatch={minutes:m,employerRate:e.employerRate,workerRate:e.workerRate,employerPays:P.employerPay(w,e),workerReceives:P.workerPay(w,e),expectedProfit:expected,actual:g};break outer}}
out.groupRounding=groupMismatch;
const rows=[{date:'2026-10-20',start:'08:00',end:'10:00',hours:1,total:60,payKnown:true,paid:false,currency:'₪',category:'בדיקה',kind:'shift'}];out.summaryBreak={netMinutes:60,profileMinutes:S.summary(rows,'2026-10','2026-10-30').hourMinutes.reduce((a,b)=>a+b,0)};
const roundedDraft=draft({mode:'CLOCK',start:'08:00',end:'08:30',rate:'2.05'});check('clock preview equals stored',E.evaluate(roundedDraft).total,E.toEntry(roundedDraft).totalEarnings);
out.dst=[];for(const [zone,date,start,end] of [['Asia/Jerusalem','2026-03-27','01:00','04:00'],['Asia/Jerusalem','2026-10-25','00:00','03:00'],['America/New_York','2026-03-08','01:00','04:00'],['America/New_York','2026-11-01','00:00','03:00']]){process.env.TZ=zone;out.dst.push({zone,date,start,end,clockHours:R.span(start,end).minutes/60,elapsedHours:(Plan.endMillis(date,start,end)-Plan.startMillis(date,start))/3600000})}
console.log(JSON.stringify(out,null,2));fs.writeFileSync('/workspace/salary-audit/current-calculations/results.json',JSON.stringify(out,null,2));
