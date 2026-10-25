// Account sync (web/sync) against the Firebase emulator: the real SDK, the deployed rules, two devices of one account.
const {test}=require('node:test');const assert=require('node:assert/strict');
const firebase=require('firebase/compat/app');require('firebase/compat/auth');require('firebase/compat/firestore');
globalThis.WorkRuntime=require('../web/runtime.js');globalThis.WorkCategories=require('../web/categories.js');globalThis.WorkTransfer=require('../web/transfer.js');
const {createStore}=require('../web/kis/store.js');const {createSync}=require('../web/sync/engine.js');const {firestoreTransport}=require('../web/sync/firestore.js');
function memory(){const m=new Map();return {getItem:k=>m.has(k)?m.get(k):null,setItem:(k,v)=>m.set(k,String(v)),removeItem:k=>m.delete(k)};}
const shift=(over={})=>({category:'מסעדה',date:1760000000000,createdAt:1760000000500,isTimeRange:true,startTime:'09:00',endTime:'17:00',hours:8,hourlyRate:40.25,totalEarnings:322.33,isPaid:false,notes:'',currency:'₪',isGroupShift:false,employerRate:null,workerRate:null,groupWorkersJson:'',...over});
test('Firebase emulator: two devices of one account sync through records_v1; other accounts are refused',{timeout:90000},async()=>{
 assert.equal(process.env.GCLOUD_PROJECT,'demo-salary-sync');
 const apps=[];
 function client(name){const app=firebase.initializeApp({apiKey:'fake-emulator-key',projectId:'demo-salary-sync'},name);apps.push(app);app.auth().useEmulator('http://127.0.0.1:9099',{disableWarnings:true});app.firestore().useEmulator('127.0.0.1',8080);return app;}
 try{
  const a=client('sync-A'),b=client('sync-B'),other=client('sync-other');
  const email=crypto.randomUUID()+'@example.invalid',password='Synthetic-test-password-123!';
  await a.auth().createUserWithEmailAndPassword(email,password);await b.auth().signInWithEmailAndPassword(email,password);
  await other.auth().createUserWithEmailAndPassword(crypto.randomUUID()+'@example.invalid',password);
  const uid=a.auth().currentUser.uid;
  const device=app=>{const local=memory(),store=createStore(local);store.load();return {store,sync:createSync({store,storage:local,uid,deviceId:app.name,transport:firestoreTransport(firebase,app),currentUid:()=>app.auth().currentUser?.uid})};};
  const A=device(a),B=device(b);
  A.store.update(d=>{d.categories.push({name:'מסעדה',defaultRate:40.25});});A.store.addEntries([shift()]);
  A.store.updatePlans(p=>{p.series.push({id:'P1',category:'מסעדה',daysMask:2,startTime:'09:00',endTime:'17:00',hourlyRate:null,currency:'₪',fromDate:'2026-10-01',untilDate:'2026-12-31',createdAt:5});});
  await A.sync.sync();await B.sync.sync();
  assert.equal(B.store.data.entries[0].totalEarnings,322.33);assert.equal(B.store.plans.series[0].id,'P1');
  B.store.update(d=>{d.entries[0].notes='ערב';});await B.sync.sync();await A.sync.sync();assert.equal(A.store.data.entries[0].notes,'ערב');
  // Only what changed since the last read is read again.
  const docs=await a.firestore().collection('users').doc(uid).collection('records_v1').get({source:'server'});
  assert.ok(docs.docs.every(d=>d.get('updatedAt')?.toMillis&&d.get('schema')===1));
  const t=firestoreTransport(firebase,a),{cursor}=await t.read(uid,null),{records}=await t.read(uid,cursor+1);assert.equal(records.length,0);
  await assert.rejects(other.firestore().collection('users').doc(uid).collection('records_v1').get({source:'server'}));
  await assert.rejects(other.firestore().collection('users').doc(uid).collection('records_v1').doc('0'.repeat(32)).set({payload:'x'}));
  A.store.deleteEntries([A.store.data.entries[0].id]);await A.sync.sync();await B.sync.sync();assert.equal(B.store.data.entries.length,0);
 } finally { await Promise.all(apps.map(app=>app.delete())); }
});
