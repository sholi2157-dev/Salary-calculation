const {test}=require('node:test'),assert=require('node:assert/strict'),R=require('../web/runtime.js'),T=require('../web/transfer.js');
const storage=seed=>{const data=new Map(Object.entries(seed));return {getItem:k=>data.get(k)??null,setItem:(k,v)=>data.set(k,v),removeItem:k=>data.delete(k)};};
test('existing 22/8 snapshot keeps exact amounts, IDs, metadata and optional preferences; checkpoint never overwrites',()=>{
 const entries=Array.from({length:22},(_,i)=>({id:i,category:'cat'+(i%8),currency:i%2?'$':'₪',totalEarnings:91.17+i,hours:1.75,groupWorkersJson:'[{"name":"synthetic","hours":2}]'}));
 const original=JSON.stringify({entries,categories:Array.from({length:8},(_,i)=>({name:'cat'+i,defaultRate:35.25})),workers:[],webPreferences:{defaultCategory:'cat3',categoryCurrencies:{cat3:'$'},mainCurrency:'$'}});
 const s=storage({work_complete_backup:original,user_work_shifts:'[]'});assert.deepEqual(R.loadGuest(s).entries,entries);R.checkpoint(s);const copy=s.getItem('work_pre_rc13_snapshot_v1');s.setItem('work_complete_backup','{}');R.checkpoint(s);assert.equal(s.getItem('work_pre_rc13_snapshot_v1'),copy);assert.equal(JSON.parse(copy).values.work_complete_backup,original);
});
test('legacy user_work_shifts and metadata are read without rewriting storage; corrupt current data does not fall back',()=>{const s=storage({user_work_shifts:'[{"id":5,"totalEarnings":22}]',work_transfer_meta:'{"categories":[{"name":"old","defaultRate":25}],"workers":[]}',work_default_currency:'$'});assert.equal(R.loadGuest(s).entries[0].id,5);assert.equal(R.loadGuest(s).webPreferences.mainCurrency,'$');assert.equal(s.getItem('work_complete_backup'),null);s.setItem('work_complete_backup','{bad');assert.throws(()=>R.loadGuest(s));});
test('active timer uses timestamp across sleep/reload, locks currency and stable completion ID, isolates owners',()=>{
 const timer={id:'stable',category:'old',currency:'$',rate:25.5,startedAt:100000};const s=storage({[R.timerKey(null)]:JSON.stringify(timer)});assert.deepEqual(R.readTimer(s,null),timer);assert.equal(R.readTimer(s,'other'),null);const entry=R.stopEntry(timer,100000+2.5*3600000);assert.equal(entry.hours,2.5);assert.equal(entry.currency,'$');assert.equal(entry.totalEarnings,63.75);assert.equal(entry.id,'stable');assert.equal(R.stopEntry(timer,entry.createdAt).id,entry.id);assert.throws(()=>R.stopEntry(timer,100000));
});
test('Android RC13 backup defaults/currencies translate to web and back without keys or unknown device settings',()=>{
 const local={defaultCategory:'new',default_currency:'$', 'categoryCurrency:new':'$', 'categoryCurrency:other':'₪',apiKey:'synthetic-not-exported',notificationEnabled:'true'};
 const prefs=R.preferencesFromAndroid(local);assert.deepEqual(R.androidPreferences(prefs),{defaultCategory:'new',default_currency:'$', 'categoryCurrency:new':'$', 'categoryCurrency:other':'₪'});
 const decoded=T.decode(JSON.stringify({formatVersion:2,entries:[],categories:[],workers:[],androidLocalPreferences:local}));assert.deepEqual(decoded.webPreferences,prefs);
});
