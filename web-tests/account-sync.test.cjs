// Account sync between devices (web/sync): the same item is never lost, doubled or silently overwritten.
const {test}=require('node:test');const assert=require('node:assert/strict');
globalThis.WorkRuntime=require('../web/runtime.js');globalThis.WorkCategories=require('../web/categories.js');globalThis.WorkTransfer=require('../web/transfer.js');
const {createStore}=require('../web/kis/store.js');
const P=require('../web/sync/protocol.js');const {createSync}=require('../web/sync/engine.js');
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

test('a shift added on one device arrives on the other with the exact amount, then edits and deletes follow',async()=>{
 const s=server(),a=device(s),b=device(s);
 a.store.update(d=>{d.categories.push({name:'מסעדה',defaultRate:40.25});});a.store.addEntries([shift({totalEarnings:322.33})]);
 await a.sync.sync();await b.sync.sync();
 assert.equal(b.store.data.entries.length,1);assert.equal(b.store.data.entries[0].totalEarnings,322.33);assert.deepEqual(b.store.data.categories.map(c=>c.name),['מסעדה']);
 const id=b.store.data.entries[0].id;b.store.update(d=>{d.entries[0].notes='ערב';});await b.sync.sync();await a.sync.sync();
 assert.equal(a.store.data.entries[0].notes,'ערב');
 a.store.deleteEntries([a.store.data.entries[0].id]);await a.sync.sync();await b.sync.sync();
 assert.equal(b.store.data.entries.length,0);assert.ok(id);
 assert.equal(await a.sync.pending(),0);assert.equal(await b.sync.pending(),0);
});

test('first sign-in on two devices with the same data joins it instead of doubling',async()=>{
 const s=server(),a=device(s),b=device(s);
 for(const d of [a,b]){d.store.update(x=>{x.categories.push({name:'מסעדה',defaultRate:40});x.workers.push({name:'דני'});});d.store.addEntries([shift(),shift({date:1760086400000,createdAt:1760086400500})]);}
 b.store.addEntries([shift({notes:'רק בטלפון',createdAt:1})]);
 await a.sync.sync();await b.sync.sync();await a.sync.sync();
 assert.equal(s.count('owner','entry'),3);assert.equal(s.count('owner','category'),1);assert.equal(s.count('owner','worker'),1);
 assert.equal(a.store.data.entries.length,3);assert.equal(b.store.data.entries.length,3);
});

test('two genuinely repeated shifts stay two',async()=>{
 const s=server(),a=device(s),b=device(s);
 a.store.addEntries([shift(),shift()]);await a.sync.sync();await b.sync.sync();
 assert.equal(b.store.data.entries.length,2);assert.equal(s.count('owner','entry'),2);
});

test('offline, a lost answer and a reopened page never double a shift',async()=>{
 const s=server(),local=storage(),a=device(s,'owner',local);a.store.addEntries([shift()]);
 s.online=false;await assert.rejects(a.sync.sync());s.online=true;s.lose=true;await assert.rejects(a.sync.sync());
 const again=device(s,'owner',local);await again.sync.sync();await again.sync.sync();
 assert.equal(s.count('owner','entry'),1);assert.equal(again.store.data.entries.length,1);assert.equal(await again.sync.pending(),0);
});

test('the same shift changed on both devices is kept twice until the user chooses',async()=>{
 const s=server(),a=device(s),b=device(s);a.store.addEntries([shift()]);await a.sync.sync();await b.sync.sync();
 a.store.update(d=>{d.entries[0].notes='A';});b.store.update(d=>{d.entries[0].notes='B';});
 await a.sync.sync();assert.equal(await b.sync.sync(),1);
 assert.equal(b.store.data.entries[0].notes,'B','nothing is overwritten');
 const [c]=b.sync.conflicts();assert.equal(c.mine.notes,'B');assert.equal(c.theirs.notes,'A');
 b.sync.resolve(c.syncId,true);await b.sync.sync();await a.sync.sync();assert.equal(a.store.data.entries[0].notes,'B');
 a.store.update(d=>{d.entries[0].notes='A2';});b.store.update(d=>{d.entries[0].notes='B2';});await a.sync.sync();await b.sync.sync();
 b.sync.resolve(b.sync.conflicts()[0].syncId,false);assert.equal(b.store.data.entries[0].notes,'A2');await b.sync.sync();assert.equal(await b.sync.pending(),0);
});

test('weekly plans, plan days and settings sync; settings never synced take the account value',async()=>{
 const s=server(),a=device(s),b=device(s);
 a.store.update(d=>{d.categories.push({name:'מסעדה',defaultRate:40});d.webPreferences={mainCurrency:'$',defaultCategory:'מסעדה',categoryCurrencies:{'מסעדה':'$'}};});
 a.store.updatePlans(p=>{p.series.push({id:'P1',category:'מסעדה',daysMask:2,startTime:'09:00',endTime:'17:00',hourlyRate:null,currency:'$',fromDate:'2026-10-01',untilDate:'2026-12-31',createdAt:5});
  p.exceptions.push(a.store.exception('P1','2026-10-05',{cancelled:true}));});
 await a.sync.sync();await b.sync.sync();
 assert.equal(b.store.plans.series[0].id,'P1');assert.equal(b.store.plans.exceptions[0].cancelled,true);
 assert.equal(b.store.data.webPreferences.mainCurrency,'$');assert.equal(b.store.data.webPreferences.categoryCurrencies['מסעדה'],'$');
 assert.equal(b.sync.conflicts().length,0);
 b.store.updatePlans(p=>{p.series=[];p.exceptions=[];});await b.sync.sync();await a.sync.sync();assert.equal(a.store.plans.series.length,0);
});

test('a renamed place arrives under its new name; accounts never see each other',async()=>{
 const s=server(),a=device(s),b=device(s),other=device(s,'other');
 a.store.update(d=>{d.categories.push({name:'מסעדה',defaultRate:40});});await a.sync.sync();await b.sync.sync();
 a.store.update(d=>{d.categories[0].name='בית קפה';});await a.sync.sync();await b.sync.sync();
 assert.deepEqual(b.store.data.categories.map(c=>c.name),['בית קפה']);
 await other.sync.sync();assert.equal(other.store.data.categories.length,0);
});

test('the wire format matches Android: keyed ids, validation and formatting-independent comparison',async()=>{
 assert.equal(await P.keyedId('series','P1'),(await P.sha256('series|P1')).slice(0,32));
 const pretty=P.encode('entry',shift()),compact=JSON.stringify(JSON.parse(pretty));
 assert.equal(P.canonical('entry',pretty),P.canonical('entry',compact));
 assert.throws(()=>P.validate({syncId:'x',type:'entry',version:1,operation:'o',deleted:false,payload:pretty}));
 assert.throws(()=>P.decode('category',pretty),/shape/);
 assert.throws(()=>P.decode('preference',JSON.stringify({formatVersion:3,androidLocalPreferences:{token:'x'},entries:[],categories:[],workers:[]})));
});
