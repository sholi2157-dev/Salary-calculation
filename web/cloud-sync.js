(function(root){
'use strict';
const types={entry:'entries',category:'categories',worker:'workers'};
const copy=x=>JSON.parse(JSON.stringify(x));
const uuid=()=>crypto.randomUUID().replace(/-/g,'');
function validate(r){
 if(!r||!/^[a-f0-9]{32}$/.test(r.syncId)||!types[r.type]||!Number.isSafeInteger(r.version)||r.version<1||typeof r.operation!=='string'||!r.operation||r.operation.length>256||typeof r.deleted!=='boolean')throw Error('Invalid sync record');
 if(r.deleted){if(r.payload!==null)throw Error('Invalid tombstone');}
 else {
  if(typeof r.payload!=='string'||new TextEncoder().encode(r.payload).length>500000)throw Error('Invalid sync payload');
  const p=JSON.parse(r.payload);
  if(p.formatVersion!==2)throw Error('Unsupported version');
  for(const [t,k] of Object.entries(types))if(!Array.isArray(p[k])||p[k].length!==(t===r.type?1:0))throw Error('Invalid record shape');
  const item=p[types[r.type]][0];
  if(r.type==='entry'){
   if(!['₪','$'].includes(item.currency)||!['hours','hourlyRate','totalEarnings','date','createdAt'].every(k=>Number.isFinite(item[k])&&item[k]>=0)||typeof item.category!=='string'||typeof item.isPaid!=='boolean')throw Error('Invalid shift');
  }else if(typeof item.name!=='string'||r.type==='category'&&(!Number.isFinite(item.defaultRate)||item.defaultRate<0))throw Error('Invalid metadata');
 }
 return r;
}
class Store{
 constructor(storage,uid,activeUid){
  if(!uid)throw Error('Account required');this.storage=storage;this.uid=uid;this.activeUid=activeUid;
  this.key='work_account_v1:'+uid;this.busy=false;
  this.state=JSON.parse(storage.getItem(this.key)||'null')||{entries:[],categories:[],workers:[],records:{}};
 }
 guard(){if(this.activeUid()!==this.uid)throw Error('Account changed');}
 save(state){this.guard();this.storage.setItem(this.key,JSON.stringify(state));this.state=state;}
 data(){return copy({entries:this.state.entries,categories:this.state.categories,workers:this.state.workers,webPreferences:this.state.webPreferences||{}});}
 edit(data){
  this.guard();const state=copy(this.state);
  if(data.webPreferences!==undefined)state.webPreferences=copy(data.webPreferences);
  for(const [type,key] of Object.entries(types)){
   const seen=new Set();
   state[key]=data[key].map(input=>{
    const row=copy(input);
    // Existing web metadata editors identify categories/workers by name.
    const old=this.state[key].find(x=>row._syncId ? x._syncId===row._syncId : type==='entry'?x.id===row.id&&row.id!=null:x.name===row.name);
    const id=old?old._syncId:uuid();row._syncId=id;
    if(type==='entry'&&row.id==null)row.id=id;
    seen.add(id);
    const body=copy(row);delete body._syncId;delete body.id;
    const envelope={formatVersion:2,entries:[],categories:[],workers:[]};envelope[key]=[body];
    const payload=JSON.stringify(envelope),previous=state.records[id];
    if(!previous||previous.deleted||previous.payload!==payload)state.records[id]={
     syncId:id,type,version:previous?.version||0,revision:(previous?.revision||0)+1,
     ack:previous?.ack||0,deleted:false,payload,operation:uuid(),conflict:previous?.conflict||null};
    return row;
   });
   for(const r of Object.values(state.records))if(r.type===type&&!r.deleted&&!seen.has(r.syncId)){
    r.deleted=true;r.payload=null;r.revision++;r.operation=uuid();
   }
  }
  this.save(state);
 }
 apply(state,r){
  const key=types[r.type],old=state[key].find(x=>x._syncId===r.syncId);
  state[key]=state[key].filter(x=>x._syncId!==r.syncId);
  if(!r.deleted){const row=JSON.parse(r.payload)[key][0];row._syncId=r.syncId;if(r.type==='entry')row.id=old?.id??r.syncId;state[key].push(row);}
  state.records[r.syncId]={...r,revision:0,ack:0,conflict:null};
 }
 receive(r){
  validate(r);const state=copy(this.state),local=state.records[r.syncId];
  if(local){if(local.type!==r.type)throw Error('Record type changed');if(r.version<=local.version)return;
   if(local.revision>local.ack){local.conflict=r;this.save(state);return;}}
  this.apply(state,r);this.save(state);
 }
 conflicts(){return copy(Object.values(this.state.records).filter(r=>r.conflict));}
 resolve(id,keepLocal){
  const state=copy(this.state),local=state.records[id];if(!local?.conflict)throw Error('Conflict missing');
  if(keepLocal){local.version=local.conflict.version;local.revision++;local.operation=uuid();local.conflict=null;}
  else this.apply(state,local.conflict);
  this.save(state);
 }
 async sync(transport){
  this.guard();if(this.busy)return;this.busy=true;
  try{
   const pending=copy(Object.values(this.state.records).filter(r=>r.revision>r.ack&&!r.conflict));
   for(const sent of pending){
    this.guard();const proposed=validate({syncId:sent.syncId,type:sent.type,version:sent.version+1,operation:sent.operation,deleted:sent.deleted,payload:sent.payload});
    const response=validate(await transport.exchange(this.uid,sent.version,proposed));this.guard();
    if(response.syncId!==sent.syncId||response.type!==sent.type)throw Error('Unexpected response');
    if(response.operation===sent.operation){
     const state=copy(this.state),now=state.records[sent.syncId];
     if(now.version===sent.version&&!now.conflict){now.version=response.version;now.ack=sent.revision;this.save(state);}
    }else this.receive(response);
   }
   const records=await transport.readAll(this.uid);this.guard();for(const r of records)this.receive(r);
   return this.conflicts().length;
  }finally{this.busy=false;}
 }
}
function firestoreTransport(db,auth){
 const guard=uid=>{if(auth.currentUser?.uid!==uid)throw Error('Account changed');};
 const collection=uid=>db.collection('users').doc(uid).collection('records_v1');
 const decode=d=>{const data=d.data();if(data.schema!==1)throw Error('Unsupported schema');return validate({syncId:d.id,...data});};
 return {
  async readAll(uid){guard(uid);const snapshot=await collection(uid).get({source:'server'});guard(uid);return snapshot.docs.map(decode);},
  async exchange(uid,base,r){guard(uid);validate(r);return db.runTransaction(async t=>{
   guard(uid);const ref=collection(uid).doc(r.syncId),snap=await t.get(ref),old=snap.exists?decode(snap):null;
   if(old?.operation===r.operation)return old;
   if((old?.version||0)!==base){if(!old)throw Error('Remote document removed');return old;}
   const data={schema:1,type:r.type,version:r.version,operation:r.operation,deleted:r.deleted,payload:r.payload};t.set(ref,data);return r;
  });}
 };
}
const api={Store,validate,firestoreTransport};if(typeof module!=='undefined')module.exports=api;else root.WorkCloud=api;
})(typeof window==='undefined'?globalThis:window);
