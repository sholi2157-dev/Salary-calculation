/* Browser-only state contracts. Existing financial snapshot/wire formats stay intact. */
(function(root){
'use strict';
function preferencesFromAndroid(local={}){
 const categoryCurrencies={};
 for(const [key,value] of Object.entries(local))if(key.startsWith('categoryCurrency:')&&['₪','$'].includes(value))Object.defineProperty(categoryCurrencies,key.slice(17),{value,enumerable:true});
 return {mainCurrency:local.default_currency||'₪',defaultCategory:local.defaultCategory||'עצמאי',categoryCurrencies};
}
function androidPreferences(prefs){return Object.fromEntries([['default_currency',prefs.mainCurrency],['defaultCategory',prefs.defaultCategory],...Object.entries(prefs.categoryCurrencies||{}).map(([name,currency])=>['categoryCurrency:'+name,currency])]);}
function loadGuest(storage){
 const snapshot=storage.getItem('work_complete_backup');
 if(snapshot){const data=JSON.parse(snapshot);if(!Array.isArray(data.entries)||!Array.isArray(data.categories)||!Array.isArray(data.workers))throw Error('גיבוי מקומי לא תקין');return data;}
 const legacy=JSON.parse(storage.getItem('user_work_shifts')||'[]'),meta=JSON.parse(storage.getItem('work_transfer_meta')||'{}');
 if(!Array.isArray(legacy))throw Error('משמרות מקומיות לא תקינות');
 return {entries:legacy,categories:meta.categories||[],workers:meta.workers||[],webPreferences:{mainCurrency:storage.getItem('work_default_currency')||'₪',...(meta.webPreferences||{})}};
}
function checkpoint(storage){
 const key='work_pre_rc13_snapshot_v1';if(storage.getItem(key))return;
 const keys=['work_complete_backup','user_work_shifts','work_transfer_meta','work_default_currency'];
 const values=Object.fromEntries(keys.map(k=>[k,storage.getItem(k)]));
 if(keys.some(k=>values[k]!==null))storage.setItem(key,JSON.stringify({savedAt:Date.now(),values}));
}
function timerKey(owner){return 'work_active_shift_v1:'+encodeURIComponent(owner||'guest');}
function readTimer(storage,owner){const raw=storage.getItem(timerKey(owner));if(!raw)return null;const t=JSON.parse(raw);if(typeof t.id!=='string'||!t.category||!['₪','$'].includes(t.currency)||!Number.isFinite(t.startedAt)||!Number.isFinite(t.rate)||t.rate<0)throw Error('משמרת פעילה לא תקינה');return t;}
function stopEntry(timer,now){
 const hours=Math.max(0,(now-timer.startedAt)/3600000);
 if(!hours||!Number.isFinite(hours))throw Error('לא עבר זמן עבודה');
 return {id:timer.id,category:timer.category,date:timer.startedAt,createdAt:now,hours,hourlyRate:timer.rate,totalEarnings:Math.round((hours*timer.rate+Number.EPSILON)*100)/100,currency:timer.currency,isPaid:false,notes:'',isTimeRange:false,startTime:null,endTime:null,isGroupShift:false,employerRate:null,workerRate:null,groupWorkersJson:''};
}
function assertUnchangedEdit(base,current){
 if(!current)throw Error('המשמרת נמחקה בחלון אחר. השינויים שהקלדת לא נשמרו');
 if(!base||JSON.stringify(base)!==JSON.stringify(current))throw Error('המשמרת עודכנה בחלון אחר. העתק את השינויים שלך ופתח את העריכה מחדש');
}
const api={assertUnchangedEdit,preferencesFromAndroid,androidPreferences,loadGuest,checkpoint,timerKey,readTimer,stopEntry};if(typeof module!=='undefined')module.exports=api;else root.WorkRuntime=api;
})(globalThis);
