const {test}=require('node:test');
const assert=require('node:assert/strict');
const firebase=require('firebase/compat/app');require('firebase/compat/auth');require('firebase/compat/firestore');
const {Store,firestoreTransport}=require('../web/cloud-sync.js');
const uid=()=>crypto.randomUUID().replace(/-/g,'');
function memory(){const m=new Map();return {getItem:k=>m.get(k),setItem:(k,v)=>m.set(k,v)};}
test('Firebase emulator: owner rules, two devices, offline retry, edits, conflicts and deletion', {timeout:90000},async()=>{
 assert.equal(process.env.GCLOUD_PROJECT,'demo-salary-sync');
 const apps=[];
 async function client(name){const app=firebase.initializeApp({apiKey:'fake-emulator-key',projectId:'demo-salary-sync'},name);apps.push(app);app.auth().useEmulator('http://127.0.0.1:9099',{disableWarnings:true});app.firestore().useEmulator('127.0.0.1',8080);return app;}
 try{
 const a=await client('A'),b=await client('B'),other=await client('other'),guest=await client('guest');
 const email=uid()+'@example.invalid',password='Synthetic-test-password-123!';
 await a.auth().createUserWithEmailAndPassword(email,password);await b.auth().signInWithEmailAndPassword(email,password);
 await other.auth().createUserWithEmailAndPassword(uid()+'@example.invalid',password);
 const owner=a.auth().currentUser.uid;
 const ta=firestoreTransport(a.firestore(),a.auth()),tb=firestoreTransport(b.firestore(),b.auth());
 const sa=new Store(memory(),owner,()=>a.auth().currentUser?.uid),sb=new Store(memory(),owner,()=>b.auth().currentUser?.uid);
 // This format is identical to Android WorkBackup.encode's single-entry envelope.
 const entry={category:'test',date:1000,createdAt:1000,isTimeRange:false,startTime:null,endTime:null,hours:1.5,hourlyRate:40.25,totalEarnings:60.38,isPaid:false,notes:'base',currency:'₪',isGroupShift:false,employerRate:null,workerRate:null,groupWorkersJson:''};
 sa.edit({entries:[entry],categories:[],workers:[]});await sa.sync(ta);await sb.sync(tb);
 assert.equal(sb.data().entries[0].totalEarnings,60.38);
 const record=(await ta.readAll(owner))[0];
 for(const app of [other,guest]){
  await assert.rejects(app.firestore().collection('users').doc(owner).collection('records_v1').get({source:'server'}));
  await assert.rejects(app.firestore().collection('users').doc(owner).collection('records_v1').doc(record.syncId).set({payload:'forbidden'}));
 }
 const da=sa.data(),db=sb.data();da.entries[0].notes='A';db.entries[0].notes='B';sa.edit(da);sb.edit(db);
 await sa.sync(ta);assert.equal(await sb.sync(tb),1);assert.equal(sb.data().entries[0].notes,'B');
 sb.resolve(sb.conflicts()[0].syncId,true);await sb.sync(tb);await sa.sync(ta);assert.equal(sa.data().entries[0].notes,'B');
 const offline=sa.data();offline.entries[0].isPaid=true;sa.edit(offline);
 await assert.rejects(sa.sync({exchange:async()=>{throw Error('disconnected');}}));
 // Commit succeeded remotely, but response disappeared: retry must not duplicate.
 let lost=true;await assert.rejects(sa.sync({exchange:async(...args)=>{const r=await ta.exchange(...args);if(lost){lost=false;throw Error('lost response');}return r;},readAll:uid=>ta.readAll(uid)}));
 await sa.sync(ta);await sb.sync(tb);assert.equal(sb.data().entries[0].isPaid,true);
 assert.equal((await ta.readAll(owner)).length,1);
 sb.edit({entries:[],categories:[],workers:[]});await sb.sync(tb);await sa.sync(ta);assert.equal(sa.data().entries.length,0);
 }finally{await Promise.all(apps.map(a=>a.delete()));}
});
