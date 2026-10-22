// Backup formatVersion 3 (Salary Calculation 2.0) — rules ported from Android data/WorkBackup.kt.
const {test}=require('node:test');const assert=require('node:assert/strict');const T=require('../web/transfer.js');
const base={category:'מסעדה',date:1760000000000,createdAt:1760000000500,isTimeRange:true,startTime:'22:00',endTime:'06:00',hours:8,hourlyRate:40,totalEarnings:320,isPaid:false,notes:'n',currency:'₪',isGroupShift:false,employerRate:null,workerRate:null,groupWorkersJson:''};
const PRE_2_KEYS=Object.keys(base);
const series={id:'P',category:'מסעדה',daysMask:2,startTime:'09:00',endTime:'17:00',hourlyRate:null,currency:'$',fromDate:'2026-10-01',untilDate:'2026-12-31',createdAt:5};
const exception={seriesId:'P',date:'2026-10-05',cancelled:true,hold:false,startTime:null,endTime:null,rateOverridden:false,hourlyRate:null};
// Exactly what web/app.js backupText() writes today.
const backupText=(shifts,categories,workers,webPreferences,androidLocalPreferences)=>JSON.stringify({formatVersion:2,androidLocalPreferences,entries:shifts.map(T.normalize),categories:categories.map(({name,defaultRate})=>({name,defaultRate})),workers:workers.map(({name})=>({name})),webPreferences},null,2);

test('entries without 2.0 data normalize to exactly the pre-2.0 object',()=>{
 assert.deepEqual(Object.keys(T.normalize(base)),PRE_2_KEYS);
 assert.deepEqual(Object.keys(T.normalize({...base,kind:'shift',payKnown:true,bonus:0,seriesKey:null,autoConfirmed:false})),PRE_2_KEYS);
 assert.deepEqual(T.normalize({...base,payKnown:'yes',bonus:'x',seriesKey:'  '}),T.normalize(base),'Android optBoolean/optDouble fall back to defaults; a blank seriesKey is none');
});
test('version 2 stays byte-identical to the current web backup and round trips',()=>{
 const shifts=[base,{...base,isGroupShift:true,employerRate:60,workerRate:35,groupWorkersJson:'[{"name":"דני","hours":5,"isPaid":false}]'}];
 const categories=[{name:'מסעדה',defaultRate:40,extra:1}],workers=[{name:'דני',id:'x'}],prefs={mainCurrency:'₪',defaultCategory:'מסעדה',categoryCurrencies:{}},local={default_currency:'₪',defaultCategory:'מסעדה'};
 const text=T.encode({entries:shifts,categories,workers,webPreferences:prefs,androidLocalPreferences:local});
 assert.equal(text,backupText(shifts,categories,workers,prefs,local));
 const back=T.decode(text);
 assert.equal(back.formatVersion,2);assert.ok(!('series' in back)&&!('exceptions' in back));
 assert.deepEqual(back.entries,shifts.map(T.normalize));assert.deepEqual(back.webPreferences,prefs);
 assert.equal(T.missing(shifts,back.entries).length,0);
});
test('version 3 round trips kind, payKnown, bonus, seriesKey, autoConfirmed, worker bonus, series and exceptions',()=>{
 const shifts=[base,
  {...base,kind:'job',hours:0,hourlyRate:0,totalEarnings:0,payKnown:false,isTimeRange:false,startTime:null,endTime:null},
  {...base,bonus:25,totalEarnings:345,seriesKey:'P@2026-10-06',autoConfirmed:true},
  {...base,isGroupShift:true,groupWorkersJson:'[{"name":"יוסי","hours":4,"isPaid":true,"bonus":20,"note":"kept"}]'}];
 const text=T.encode({entries:shifts,categories:[{name:'מסעדה',defaultRate:40}],workers:[],series:[series],exceptions:[exception]});
 const raw=JSON.parse(text);
 assert.equal(raw.formatVersion,3);
 assert.deepEqual(Object.keys(raw.entries[0]),[...PRE_2_KEYS,'kind','payKnown','bonus','seriesKey','autoConfirmed']);
 assert.deepEqual(Object.keys(raw.entries[2]),[...PRE_2_KEYS,'kind','payKnown','bonus','seriesKey','autoConfirmed']);
 assert.deepEqual(raw.series,[series]);assert.deepEqual(raw.seriesExceptions,[exception]);
 const back=T.decode(text);
 assert.equal(back.formatVersion,3);assert.deepEqual(back.entries,shifts.map(T.normalize));
 assert.deepEqual(back.series,[series]);assert.deepEqual(back.exceptions,[exception]);
 assert.equal(back.entries[1].kind,'job');assert.equal(back.entries[1].payKnown,false);assert.equal(back.entries[2].bonus,25);
 assert.equal(back.entries[2].seriesKey,'P@2026-10-06');assert.equal(back.entries[2].autoConfirmed,true);
 assert.deepEqual(JSON.parse(back.entries[3].groupWorkersJson)[0],{name:'יוסי',hours:4,isPaid:true,bonus:20,note:'kept'});
 assert.deepEqual(Object.keys(back.entries[0]),PRE_2_KEYS);
});
test('a worker bonus alone, or a plan alone, makes the file version 3',()=>{
 assert.equal(JSON.parse(T.encode({entries:[{...base,isGroupShift:true,groupWorkersJson:'[{"name":"A","hours":1,"bonus":5}]'}]})).formatVersion,3);
 assert.equal(JSON.parse(T.encode({entries:[],series:[series]})).formatVersion,3);
 assert.equal(JSON.parse(T.encode({entries:[{...base,isGroupShift:true,groupWorkersJson:'[{"name":"A","hours":1,"bonus":0}]'}]})).formatVersion,2);
});
test('invalid 2.0 values and plan dates are rejected',()=>{
 const v3=(extra)=>JSON.stringify({formatVersion:3,entries:[],...extra});
 for(const bad of ['2026-02-30','1999-12-31','2026-13-01','2026-1-05','20261005'])
  {assert.throws(()=>T.decode(v3({series:[{...series,fromDate:bad}]})),bad);assert.throws(()=>T.decode(v3({seriesExceptions:[{...exception,date:bad}]})),bad);}
 assert.throws(()=>T.decode(v3({series:[{...series,fromDate:'2026-12-31',untilDate:'2026-10-01'}]})));
 for(const daysMask of [0,128])assert.throws(()=>T.decode(v3({series:[{...series,daysMask}]})));
 assert.throws(()=>T.decode(v3({series:[{...series,startTime:'24:00'}]})));
 assert.throws(()=>T.decode(v3({series:[{...series,currency:'€'}]})));
 assert.throws(()=>T.decode(v3({series:[{...series,id:' '}]})));
 assert.throws(()=>T.decode(v3({series:[{...series,category:''}]})));
 assert.throws(()=>T.decode(v3({series:[{...series,hourlyRate:-1}]})));
 assert.throws(()=>T.decode(v3({seriesExceptions:[{...exception,startTime:'9:99'}]})));
 assert.throws(()=>T.decode(v3({entries:[{...base,kind:'other'}]})));
 assert.throws(()=>T.decode(v3({entries:[{...base,bonus:-1}]})));
 assert.throws(()=>T.decode(v3({entries:[{...base,groupWorkersJson:'[{"name":"A","hours":1,"bonus":-2}]'}]})));
 assert.throws(()=>T.decode(JSON.stringify({formatVersion:4,entries:[]})));
 assert.ok(T.planDate('2024-02-29'));assert.ok(!T.planDate('2026-02-29'));
});
test('an Android WorkBackup.encode version 3 file decodes',()=>{
 // Built the way Kotlin encode writes it: id first, categories/workers before entries, every 2.0 field on every entry.
 const text=JSON.stringify({formatVersion:3,androidLocalPreferences:{defaultCategory:'מסעדה',default_currency:'$'},
  categories:[{name:'מסעדה',defaultRate:50}],workers:[{name:'דני'}],
  entries:[{id:7,category:'מסעדה',date:1760000000000,createdAt:1760000000001,isTimeRange:false,startTime:null,endTime:null,hours:2,hourlyRate:50,totalEarnings:100,isPaid:false,notes:'',currency:'₪',isGroupShift:false,employerRate:null,workerRate:null,groupWorkersJson:'',kind:'shift',payKnown:true,bonus:0,seriesKey:null,autoConfirmed:false},
   {id:8,category:'מסעדה',date:1760086400000,createdAt:1760086400000,isTimeRange:true,startTime:'09:00',endTime:'17:00',hours:8,hourlyRate:0,totalEarnings:0,isPaid:false,notes:'',currency:'$',isGroupShift:false,employerRate:null,workerRate:null,groupWorkersJson:'',kind:'shift',payKnown:false,bonus:0,seriesKey:'P@2026-10-05',autoConfirmed:true}],
  series:[{id:'P',category:'מסעדה',daysMask:2,startTime:'09:00',endTime:'17:00',hourlyRate:null,currency:'$',fromDate:'2026-10-01',untilDate:'2026-12-31',createdAt:5}],
  seriesExceptions:[{seriesId:'P',date:'2026-10-12',cancelled:true,hold:false,startTime:null,endTime:null,rateOverridden:false,hourlyRate:null}]},null,2);
 const back=T.decode(text);
 assert.equal(back.formatVersion,3);assert.equal(back.entries.length,2);
 assert.deepEqual(Object.keys(back.entries[0]),PRE_2_KEYS);
 assert.deepEqual(back.entries[1],{category:'מסעדה',date:1760086400000,createdAt:1760086400000,isTimeRange:true,startTime:'09:00',endTime:'17:00',hours:8,hourlyRate:0,totalEarnings:0,isPaid:false,notes:'',currency:'$',isGroupShift:false,employerRate:null,workerRate:null,groupWorkersJson:'',payKnown:false,seriesKey:'P@2026-10-05',autoConfirmed:true});
 assert.equal(back.series[0].id,'P');assert.equal(back.exceptions[0].date,'2026-10-12');
 assert.deepEqual(back.webPreferences,{mainCurrency:'$',defaultCategory:'מסעדה',categoryCurrencies:{}});
 // Re-encoding gives the same content back.
 assert.deepEqual(T.decode(T.encode({...back})).entries,back.entries);
});
test('import skips duplicates and occurrences already recorded, and keeps existing plans',()=>{
 const recorded={...base,seriesKey:'P@2026-10-05',autoConfirmed:true};
 const incoming=[recorded,{...recorded,notes:'edited elsewhere'},{...base,seriesKey:'P@2026-10-12'},{...base,seriesKey:'P@2026-10-12',notes:'twice in file'},base,base];
 const added=T.missing([recorded,base],incoming);
 assert.deepEqual(added.map(e=>[e.seriesKey??null,e.notes]),[['P@2026-10-12','n'],[null,'n']]);
 const plans=T.missingPlans([series],[],[series,{...series,id:'Q'}],[exception,{...exception,seriesId:'Q'},{...exception,seriesId:'Q'}]);
 assert.deepEqual(plans.series.map(s=>s.id),['Q']);assert.deepEqual(plans.exceptions,[{...exception,seriesId:'Q'}]);
});
