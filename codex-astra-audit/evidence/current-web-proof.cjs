const fs=require('fs'),vm=require('vm');const base='/workspace/salary-audit/sources/web-latest/';
const c={console,Date,Set,Map,JSON,Math,Number,String,BigInt,document:{addEventListener(){}}};vm.createContext(c);
for(const f of ['web/categories.js','web/v2/rules.js','web/v2/pay.js','web/v2/plan.js','web/kis/ui.js','web/kis/editor.js','web/kis/plans.js'])vm.runInContext(fs.readFileSync(base+f,'utf8'),c,{filename:f});
const out=[];function test(id,input){const d={id:null,mode:'GROUP',category:'A',day:new Date(2026,9,1).getTime(),start:'09:00',end:'10:00',breakMin:0,hours:'',rate:'60',rateUnknown:false,currency:'₪',bonus:'30',bonusTarget:'OTHERS',amount:'',amountUnknown:false,notes:'',workers:[{name:'worker',hours:'1',isPaid:false,bonus:'',source:{}}],separateRates:false,employerRate:'',workerRate:'',isPaid:false,repeat:false,...input};const ev=c.KisEditor.evaluate(d),row=c.KisEditor.toEntry(d,null);out.push({id,canSave:ev.canSave,preview:ev.total,storedOwn:row.totalEarnings,bonus:row.bonus,workers:c.KisPay.workers(row),group:c.KisPay.group(row)});}
test('group_bonus_others',{});test('group_bonus_all',{bonusTarget:'ALL'});test('negative_group_rate',{separateRates:true,workerRate:'-10',employerRate:'60'});test('invalid_bonus',{mode:'CLOCK',bonus:'abc'});test('negative_bonus',{mode:'CLOCK',bonus:'-100'});test('infinite_job',{mode:'JOB',amount:'Infinity',hours:'0',workers:[]});
const s={id:'s',category:'A',daysMask:127,startTime:'09:00',endTime:'10:00',hourlyRate:60,currency:'₪',fromDate:'2026-01-01',untilDate:'2026-10-30',createdAt:0};
const store={plans:{series:[s],exceptions:[]},data:{entries:[]},local:{}};const now=new Date(2026,9,30,12).getTime();out.push({id:'lookback',all:c.KisPlan.occurrences([s],[],new Set(),s.fromDate,s.untilDate,now).length,visible:c.KisPlans.occurrences(store,now).length});
const day='2026-10-30',at=new Date(2026,9,30,8).getTime();out.push({id:'end_today',stillPlanned:c.KisPlan.occurrences([{...s,untilDate:day}],[],new Set(),day,day,at)});
out.push({id:'overnight_overlap',preview:c.KisPlan.preview([6],'2026-10-31','2026-10-31','00:00','02:00',[{date:new Date(2026,9,30,23).getTime(),isTimeRange:true,startTime:'23:00',endTime:'01:00'}],[])});
console.log(JSON.stringify(out,null,2));
vm.runInContext(fs.readFileSync(base+'web/kis/home.js','utf8'),c);vm.runInContext(fs.readFileSync(base+'web/v2/summary.js','utf8'),c);
for(const name of ['constructor','__proto__','toString']){try{const data={categories:[],entries:[],workers:[],webPreferences:c.WorkCategories.preferences({})};c.WorkCategories.rename(data,'',name,60,'₪');const x=c.KisSummary.summary([{date:'2026-10-01',hours:1,kind:'shift',payKnown:true,total:60,currency:'₪',paid:false,category:name}],'2026-10','2026-10-30');console.log('PROTOTYPE',name,JSON.stringify(x.places));const fake={data:{entries:[{category:name,hours:1,date:now,createdAt:now,currency:'₪',totalEarnings:60,payKnown:true}],categories:[]},local:{},timer:()=>null};c.KisHome.render({}, {store:fake,occurrences:()=>[]});}catch(e){console.log('PROTOTYPE_ERROR',name,e.message)}}
// Latest-source second pass: current date in Home, and account-scoped storage versus global draft.
for(const name of ['constructor','__proto__','toString']) {
 const fake={data:{entries:[{id:'audit',category:name,hours:1,date:Date.now(),createdAt:Date.now(),currency:'₪',totalEarnings:60,payKnown:true}],categories:[]},local:{},timer:()=>null};
 try {c.KisHome.render({}, {store:fake,occurrences:()=>[]});} catch(e) {console.log('CURRENT_HOME',name,e.message)}
}
const mem=new Map([['work_account_v2',JSON.stringify({bound:'account-A',current:'account-B'})],['kis_web_draft_v1',JSON.stringify({mode:'CLOCK',notes:'synthetic private draft A',category:'A-only'})]]);
c.localStorage={getItem:k=>mem.get(k)||null,setItem:(k,v)=>mem.set(k,v),removeItem:k=>mem.delete(k)};
vm.runInContext(fs.readFileSync(base+'web/kis/account.js','utf8'),c);
console.log('ACCOUNT_DRAFT',JSON.stringify({scopedDraft:c.KisAccount.storageFor(c.localStorage).getItem(c.KisEditor.DRAFT),actualRestore:c.KisEditor.pendingDraft()}));
const dd={id:null,mode:'CLOCK',category:'A',day:Date.now(),start:'09:00',end:'10:00',breakMin:0,hours:'',rate:'60',rateUnknown:false,currency:'₪',bonus:'',bonusTarget:'ME',amount:'',amountUnknown:false,notes:'',workers:[],separateRates:false,employerRate:'',workerRate:'',isPaid:false,repeat:false,passThrough:'abc'};
console.log('INVALID_PASS_THROUGH',JSON.stringify({evaluation:c.KisEditor.evaluate(dd),stored:c.KisEditor.toEntry(dd,null)}));
