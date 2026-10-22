const {test}=require('node:test'),assert=require('node:assert/strict');
const R=require('../web/runtime.js'),T=require('../web/transfer.js');
const entry={id:'row',category:'Work',date:1000,createdAt:900,hours:2,hourlyRate:40,totalEarnings:79.13,isPaid:false,currency:'$',notes:'saved',isGroupShift:true};
test('stale or deleted edits are blocked; unchanged edits remain valid',()=>{
 assert.doesNotThrow(()=>R.assertUnchangedEdit(entry,{...entry}));
 for(const changed of [{...entry,totalEarnings:90},{...entry,notes:'new'},{...entry,isPaid:true},undefined])assert.throws(()=>R.assertUnchangedEdit(entry,changed));
 assert.equal(entry.notes,'saved');
});
test('worker rate overrides reject negative, text and non-finite values before importing',()=>{
 for(const field of ['workerRate','employerRate'])for(const rate of [-1,'25','NaN',Infinity]){
  const groupWorkersJson=JSON.stringify([{name:'worker',hours:1,[field]:rate}]).replace(':null',':1e999');
  assert.throws(()=>T.decode(JSON.stringify({entries:[{...entry,groupWorkersJson}]})),/תעריף עובד/);
 }
});
test('valid imported group override and unknown metadata survive without recalculation',()=>{
 const groupWorkersJson=JSON.stringify([{name:'worker',hours:1,workerRate:0,employerRate:31.125,isPaid:true,custom:'retained'}]);
 const decoded=T.decode(JSON.stringify({entries:[{...entry,groupWorkersJson}]})).entries[0];
 assert.equal(decoded.groupWorkersJson,groupWorkersJson);assert.equal(decoded.totalEarnings,79.13);
});
