const {test}=require('node:test');const assert=require('node:assert/strict');const {Store}=require('../web/cloud-sync.js');
function storage(){const m=new Map();return {getItem:k=>m.get(k),setItem:(k,v)=>m.set(k,v)};}
function server(){const users=new Map();return {online:true,lose:false,async readAll(uid){if(!this.online)throw Error('offline');return [...(users.get(uid)||new Map()).values()];},async exchange(uid,base,r){if(!this.online)throw Error('offline');const records=users.get(uid)||new Map();users.set(uid,records);const old=records.get(r.syncId);const result=old?.operation===r.operation||(old?.version||0)!==base?old:r;records.set(r.syncId,result);if(this.lose){this.lose=false;throw Error('lost acknowledgement');}return result;}};}
const data=note=>({entries:[{id:1,category:'test',date:1234,createdAt:1234,hours:1.5,hourlyRate:40.25,totalEarnings:60.38,currency:'₪',isPaid:false,notes:note}],categories:[],workers:[]});
test('web devices synchronize edits, exact amounts and tombstones',async()=>{
 const a=new Store(storage(),'owner',()=> 'owner'),b=new Store(storage(),'owner',()=> 'owner'),s=server();
 a.edit(data('base'));await a.sync(s);await b.sync(s);assert.equal(b.data().entries[0].totalEarnings,60.38);
 const changed=b.data();changed.entries[0].notes='edited';b.edit(changed);await b.sync(s);await a.sync(s);assert.equal(a.data().entries[0].notes,'edited');
 b.edit({entries:[],categories:[],workers:[]});await b.sync(s);await a.sync(s);assert.equal(a.data().entries.length,0);
});
test('offline persistence, retry and lost ACK do not duplicate rows',async()=>{
 const local=storage(),a=new Store(local,'owner',()=> 'owner'),s=server();a.edit(data('offline'));s.online=false;await assert.rejects(a.sync(s));
 s.online=true;s.lose=true;await assert.rejects(a.sync(s));const reopened=new Store(local,'owner',()=> 'owner');await reopened.sync(s);await reopened.sync(s);assert.equal((await s.readAll('owner')).length,1);
});
test('conflicts preserve both versions and require a choice',async()=>{
 const a=new Store(storage(),'owner',()=> 'owner'),b=new Store(storage(),'owner',()=> 'owner'),s=server();a.edit(data('base'));await a.sync(s);await b.sync(s);
 const da=a.data(),db=b.data();da.entries[0].notes='A';db.entries[0].notes='B';a.edit(da);b.edit(db);await a.sync(s);assert.equal(await b.sync(s),1);assert.equal(b.data().entries[0].notes,'B');
 b.resolve(b.conflicts()[0].syncId,true);await b.sync(s);await a.sync(s);assert.equal(a.data().entries[0].notes,'B');
});
test('account switch rejects delayed work and keeps account stores separate',async()=>{
 const local=storage();let current='A';const a=new Store(local,'A',()=>current);a.edit(data('A'));current='B';assert.throws(()=>a.edit(data('late')));const b=new Store(local,'B',()=>current);assert.equal(b.data().entries.length,0);
});
test('renaming category retains syncId; local preferences survive Android-style metadata round trip and account isolation',async()=>{
 const local=storage(),a=new Store(local,'owner',()=> 'owner'),b=new Store(storage(),'owner',()=> 'owner'),s=server();
 a.edit({...data('base'),categories:[{name:'test',defaultRate:40}],webPreferences:{defaultCategory:'test',categoryCurrencies:{test:'$'}}});await a.sync(s);await b.sync(s);
 const before=a.data().categories[0]._syncId,changed=a.data();changed.categories[0].name='renamed';changed.entries[0].category='renamed';a.edit(changed);await a.sync(s);await b.sync(s);
 assert.equal(a.data().categories[0]._syncId,before);assert.equal(b.data().categories[0]._syncId,before);assert.equal((await s.readAll('owner')).filter(r=>r.type==='category').length,1);
 const android=b.data();android.categories[0].defaultRate=55;b.edit(android);await b.sync(s);await a.sync(s);
 assert.equal(a.data().webPreferences.categoryCurrencies.test,'$');assert.ok(!JSON.parse((await s.readAll('owner')).find(r=>r.type==='category').payload).webPreferences);
 assert.deepEqual(new Store(local,'other',()=> 'other').data().webPreferences,{});
});
