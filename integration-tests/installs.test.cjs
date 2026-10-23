const {test}=require('node:test');
const assert=require('node:assert/strict');
// The same REST commit that Android InstallCount.body sends, against the emulator and firestore.rules.
const base='http://127.0.0.1:8080/v1/projects/demo-salary-sync/databases/(default)/documents';
const hex=()=>crypto.randomUUID().replace(/-/g,'');
function body(id,fields,{time=true}={}){return {writes:[{update:{name:`projects/demo-salary-sync/databases/(default)/documents/installs/${id}`,fields:Object.fromEntries(Object.entries(fields).map(([k,v])=>[k,{stringValue:v}]))},currentDocument:{exists:false},...(time?{updateTransforms:[{fieldPath:'at',setToServerValue:'REQUEST_TIME'}]}:{})}]};}
const commit=b=>fetch(base+':commit?key=fake',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify(b)});
test('Firebase emulator: anonymous install count is create-only with exact fields',{timeout:30000},async()=>{
 const id=hex();
 assert.equal((await commit(body(id,{version:'2.0.7',kind:'new'}))).status,200);
 const again=await commit(body(id,{version:'2.0.7',kind:'new'}));
 assert.notEqual(again.status,200); // A device is counted once.
 for(const bad of [body(hex(),{version:'2.0.7',kind:'new',name:'x'}),body(hex(),{version:'2.0.7',kind:'other'}),body(hex(),{version:'2.0.7',kind:'new'},{time:false}),body('not-hex',{version:'2.0.7',kind:'update'})])
  assert.equal((await commit(bad)).status,403);
 assert.equal((await fetch(base+'/installs/'+id+'?key=fake')).status,403); // Nobody reads the list from a client.
 assert.equal((await fetch(base+'/installs/'+id+'?key=fake',{method:'DELETE'})).status,403);
});
