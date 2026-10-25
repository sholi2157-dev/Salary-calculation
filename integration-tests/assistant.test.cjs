// Personal AI assistants (lib/server/assistant.cjs) against the Firebase emulator: OAuth with PKCE, MCP tools as the
// person, permissions, the sync format the app reads, the log, and cutting an assistant off. Synthetic data only.
const {test}=require('node:test');const assert=require('node:assert/strict');const crypto=require('node:crypto');
const env={MCP_SECRET:'synthetic-test-secret-'+'x'.repeat(32),FIRESTORE_EMULATOR_HOST:'127.0.0.1:8080',FIREBASE_AUTH_EMULATOR_HOST:'127.0.0.1:9099',FIREBASE_PROJECT_ID:'demo-salary-sync',FIREBASE_WEB_API_KEY:'fake-emulator-key'};
const A=require('../lib/server/assistant.cjs');const P=require('../web/sync/protocol.js');
const req=(token)=>({headers:{host:'kis.test','x-forwarded-proto':'https',...(token?{authorization:'Bearer '+token}:{})}});
async function person(){
 const r=await fetch('http://127.0.0.1:9099/identitytoolkit.googleapis.com/v1/accounts:signUp?key=fake',{method:'POST',headers:{'content-type':'application/json'},
  body:JSON.stringify({email:crypto.randomUUID()+'@example.invalid',password:'Synthetic-test-password-123!',returnSecureToken:true})});
 const j=await r.json();return {uid:j.localId,refreshToken:j.refreshToken};
}
async function connect(p,scopes,duration='month'){
 const [,client]=A.register(env,{redirect_uris:['https://assistant.example/callback'],client_name:'Test assistant'});
 const verifier=crypto.randomBytes(32).toString('base64url'),challenge=crypto.createHash('sha256').update(verifier).digest('base64url');
 const [s,g]=await A.grant(env,{refreshToken:p.refreshToken,scopes,duration,tz:'Asia/Jerusalem',client_id:client.client_id,redirect_uri:'https://assistant.example/callback',code_challenge:challenge,state:'s1'});
 assert.equal(s,200);const url=new URL(g.redirect);assert.equal(url.searchParams.get('state'),'s1');
 const code=url.searchParams.get('code');
 assert.equal((await A.token(env,{grant_type:'authorization_code',code,code_verifier:'wrong'+verifier.slice(5),client_id:client.client_id}))[0],400,'PKCE is checked');
 const [ts,t]=await A.token(env,{grant_type:'authorization_code',code,code_verifier:verifier,client_id:client.client_id,redirect_uri:'https://assistant.example/callback'});
 assert.equal(ts,200);
 assert.equal((await A.token(env,{grant_type:'authorization_code',code,code_verifier:verifier,client_id:client.client_id}))[0],400,'a code works once');
 return t;
}
const call=async(token,name,args)=>{const [s,b]=await A.mcp(env,req(token),{jsonrpc:'2.0',id:1,method:'tools/call',params:{name,arguments:args}});assert.equal(s,200);return b.result;};
const json=r=>JSON.parse(r.content[0].text);

test('assistant server: connect, read, add, edit, permissions, sync format, log and cut off',{timeout:90000},async()=>{
 const me=await person(),other=await person();
 const session=await A.idTokenFor(env,me.refreshToken,fetch),db=A.store(env,session,fetch);
 // The person's existing data, as the app writes it: one workplace, one shift, a currency setting.
 const place=P.randomId(),old=P.randomId(),pref=await P.keyedId('preference','default_currency');
 const put=(id,type,item)=>db.put('records_v1/'+id,{schema:1,type,version:1,operation:id+':seed',deleted:false,payload:P.encode(type,item)},{exists:false,stamp:'updatedAt'});
 await put(place,'category',{name:'מסעדה',defaultRate:40.25});
 await put(old,'entry',{category:'מסעדה',date:Date.parse('2026-10-20T09:00:00+03:00'),createdAt:1,isTimeRange:true,startTime:'09:00',endTime:'17:00',hours:8,hourlyRate:40.25,totalEarnings:322,isPaid:false,notes:'',currency:'₪',isGroupShift:false,employerRate:null,workerRate:null,groupWorkersJson:''});
 await put(pref,'preference',{name:'default_currency',value:'₪'});

 // Unauthenticated: the assistant is told where to sign in.
 const [s401,,h]=await A.mcp(env,req(),{jsonrpc:'2.0',id:1,method:'initialize'});
 assert.equal(s401,401);assert.match(h['WWW-Authenticate'],/resource_metadata="https:\/\/kis\.test\/\.well-known\/oauth-protected-resource"/);
 assert.equal(A.metadata(req(),'server').token_endpoint,'https://kis.test/oauth/token');

 const t=await connect(me,['shifts.add','shifts.edit']);
 assert.equal(t.scope,'shifts.read shifts.add shifts.edit');
 const [,init]=await A.mcp(env,req(t.access_token),{jsonrpc:'2.0',id:1,method:'initialize',params:{protocolVersion:'2025-06-18'}});
 assert.equal(init.result.serverInfo.name,'kis-shifts');
 const [,list]=await A.mcp(env,req(t.access_token),{jsonrpc:'2.0',id:2,method:'tools/list'});
 assert.deepEqual(list.result.tools.map(x=>x.name),['list_workplaces','list_shifts','get_summary','add_shift','update_shift'],'no delete without permission');

 assert.deepEqual(json(await call(t.access_token,'list_workplaces',{})),[{workplace:'מסעדה',rate:40.25,currency:'₪'}]);
 const added=json(await call(t.access_token,'add_shift',{date:'2026-10-24',start:'22:00',end:'02:30',workplace:'מסעדה',notes:'לילה'})).added;
 assert.deepEqual([added.date,added.hours,added.total,added.start,added.end],['2026-10-24',4.5,181.13,'22:00','02:30'],'past midnight, hours × rate as the app');
 const bad=await call(t.access_token,'add_shift',{date:'2026-10-24',hours:3,workplace:'אין כזה'});assert.equal(bad.isError,true);assert.match(bad.content[0].text,/מסעדה/);

 const edited=json(await call(t.access_token,'update_shift',{id:added.id,end:'03:00',paid:true})).updated;
 assert.deepEqual([edited.hours,edited.total,edited.paid],[5,201.25,true]);
 const refused=await call(t.access_token,'delete_shift',{id:added.id});assert.equal(refused.isError,true,'delete is not allowed');

 const sum=json(await call(t.access_token,'get_summary',{from:'2026-10-01',to:'2026-10-31'}));
 assert.deepEqual([sum.shifts,sum.hours,sum.totals['₪'],sum.unpaid['₪']],[2,13,523.25,322]);
 const listed=json(await call(t.access_token,'list_shifts',{from:'2026-10-21'}));assert.deepEqual(listed.map(x=>x.id),[added.id]);

 // What the app and the website receive: a normal records_v1 change, version 2, readable by the sync format.
 const doc=(await db.get('records_v1/'+added.id)).data;
 assert.equal(doc.version,2);const entry=P.decode('entry',doc.payload);assert.equal(entry.totalEarnings,201.25);assert.equal(entry.endTime,'03:00');
 const log=await db.list('assistant_log');assert.equal(log.length,2);assert.ok(log.every(l=>l.data.client==='Test assistant'));
 assert.ok(log.some(l=>l.data.before===null&&P.decode('entry',l.data.after).notes==='לילה'),'the add is logged with its result');

 // Another person's account is never reachable, even with a token of theirs for the same assistant.
 const t2=await connect(other,[]);
 assert.deepEqual(json(await call(t2.access_token,'list_shifts',{})),[]);

 // Refresh works while the grant lives; removing it in the app cuts the assistant off at once.
 const [rs,r]=await A.token(env,{grant_type:'refresh_token',refresh_token:t.refresh_token});assert.equal(rs,200);
 const grants=await db.list('assistants');assert.equal(grants.length,1);assert.equal(grants[0].data.name,'Test assistant');
 assert.ok(await db.remove('assistants/'+grants[0].id));
 assert.equal((await A.mcp(env,req(r.access_token),{jsonrpc:'2.0',id:1,method:'tools/list'}))[0],401);
 assert.equal((await A.token(env,{grant_type:'refresh_token',refresh_token:t.refresh_token}))[0],400);

 // A personal key (for an assistant without OAuth) works the same way.
 const [ks,k]=await A.grant(env,{refreshToken:me.refreshToken,personal:true,name:'Grok',scopes:['shifts.delete'],duration:'hour',tz:'Asia/Jerusalem'});
 assert.equal(ks,200);const del=json(await call(k.key,'delete_shift',{id:added.id}));assert.equal(del.deleted.id,added.id);
 assert.equal((await db.get('records_v1/'+added.id)).data.deleted,true);
 // A tampered or foreign token is refused.
 assert.equal((await A.mcp(env,req(k.key.slice(0,-2)+'AA'),{jsonrpc:'2.0',id:1,method:'ping'}))[0],401);
});
