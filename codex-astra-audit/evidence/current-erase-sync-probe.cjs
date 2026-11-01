// Account sync between devices (web/sync): the same item is never lost, doubled or silently overwritten.
const {test}=require('node:test');const assert=require('node:assert/strict');
globalThis.WorkRuntime=require('/workspace/salary-audit/sources/web-latest/web/runtime.js');globalThis.WorkCategories=require('/workspace/salary-audit/sources/web-latest/web/categories.js');globalThis.WorkTransfer=require('/workspace/salary-audit/sources/web-latest/web/transfer.js');
const {createStore}=require('/workspace/salary-audit/sources/web-latest/web/kis/store.js');
const P=require('/workspace/salary-audit/sources/web-latest/web/sync/protocol.js');const {createSync}=require('/workspace/salary-audit/sources/web-latest/web/sync/engine.js');
function storage(){const m=new Map();return {getItem:k=>m.has(k)?m.get(k):null,setItem:(k,v)=>m.set(k,String(v)),removeItem:k=>m.delete(k)};}
/** Firestore stand-in: compare-and-set per document and an update counter as the read cursor. */
function server(){
 const docs=new Map();let seq=0;
 return {docs,online:true,lose:false,
  async read(uid,cursor){if(!this.online)throw Error('offline');const all=[...docs.values()].filter(d=>d.uid===uid&&(cursor==null||d.seq>=cursor));return {records:all.map(d=>d.r),cursor:seq};},
  async exchange(uid,base,r){if(!this.online)throw Error('offline');const id=uid+'/'+r.syncId,old=docs.get(id);
   let result;if(old?.r.operation===r.operation)result=old.r;else if((old?.r.version||0)!==base)result=old.r;else{docs.set(id,{uid,r,seq:++seq});result=r;}
   if(this.lose){this.lose=false;throw Error('lost answer');}return result;},
  count(uid,type){return [...docs.values()].filter(d=>d.uid===uid&&d.r.type===type&&!d.r.deleted).length;}};
}
function device(s,uid='owner',local=storage()){
 const store=createStore(local);store.load();
 const sync=createSync({store,storage:local,uid,deviceId:'d'+Math.random(),transport:s,currentUid:()=>uid});
 return {store,sync,local};
}
const shift=(over={})=>({category:'מסעדה',date:1760000000000,createdAt:1760000000500,isTimeRange:true,startTime:'09:00',endTime:'17:00',hours:8,hourlyRate:40.25,totalEarnings:322,isPaid:false,notes:'',currency:'₪',isGroupShift:false,employerRate:null,workerRate:null,groupWorkersJson:'',...over});


(async()=>{const srv=server(),a=device(srv),b=device(srv);a.store.addEntries([shift()]);await a.sync.sync();await b.sync.sync();a.store.addEntries([shift({notes:'new not yet received on B'})]);await a.sync.sync();b.store.update(d=>{d.entries=[];d.categories=[{name:'עצמאי',defaultRate:40}];d.workers=[];d.webPreferences=WorkCategories.preferences({});});b.store.updatePlans(p=>{p.series=[];p.exceptions=[];});await b.sync.sync();console.log(JSON.stringify({localAfterDeleteAllThenSync:b.store.data.entries.map(e=>e.notes),remoteStillPresent:srv.count('owner','entry'),pending:await b.sync.pending()},null,2));})().catch(e=>{console.error(e);process.exitCode=1});
