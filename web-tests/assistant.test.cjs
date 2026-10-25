// The assistant server's pieces that need no network: sealed tokens, registration, metadata and time zones.
const {test}=require('node:test');const assert=require('node:assert/strict');
const A=require('../lib/server/assistant.cjs');
const env={MCP_SECRET:'synthetic-test-secret-'+'y'.repeat(32)};
test('sealed tokens open only with the same secret and kind, and expire',()=>{
 const t=A.seal(env,'at',{u:'x'},60,Date.now());
 assert.equal(A.open(env,'at',t).u,'x');
 assert.equal(A.open(env,'rt',t),null,'kind is bound');
 assert.equal(A.open({MCP_SECRET:'another-secret-'+'z'.repeat(32)},'at',t),null);
 assert.equal(A.open(env,'at',t,Date.now()+61000),null,'expired');
 assert.throws(()=>A.seal({},'at',{}),/MCP_SECRET/);
});
test('registration accepts https and local return addresses only',()=>{
 assert.equal(A.register(env,{redirect_uris:['https://claude.ai/api/mcp/auth_callback'],client_name:'Claude'})[0],201);
 assert.equal(A.register(env,{redirect_uris:['http://127.0.0.1:3000/cb']})[0],201);
 for(const bad of [['http://evil.example/cb'],['javascript:alert(1)'],['https://a.example/cb#x'],[]]) assert.equal(A.register(env,{redirect_uris:bad})[0],400);
 const [,c]=A.register(env,{redirect_uris:['https://chatgpt.com/connector_platform_oauth_redirect'],client_name:'ChatGPT'});
 assert.deepEqual(A.client(env,{client_id:c.client_id,redirect_uri:'https://chatgpt.com/connector_platform_oauth_redirect'}),[200,{name:'ChatGPT'}]);
 assert.equal(A.client(env,{client_id:c.client_id,redirect_uri:'https://other.example/cb'})[0],400);
});
test('Muse has a fixed client ID that returns only to Meta\'s own domains',()=>{
 assert.deepEqual(A.client(env,{client_id:'muse',redirect_uri:'https://www.meta.ai/oauth/callback'}),[200,{name:'Muse'}]);
 assert.deepEqual(A.client(env,{client_id:'muse',redirect_uri:'https://developers.facebook.com/cb'}),[200,{name:'Muse'}]);
 for(const r of ['https://evil.example/cb','https://meta.ai.evil.example/cb','http://www.meta.ai/cb','https://notmeta.ai/cb'])
  assert.equal(A.client(env,{client_id:'muse',redirect_uri:r})[0],400,r);
 assert.equal(A.client(env,{client_id:'toString',redirect_uri:'https://www.meta.ai/cb'})[0],400);
});
test('metadata points assistants at this site',()=>{
 const req={headers:{host:'salary.example'}};
 assert.equal(A.metadata(req,'resource').resource,'https://salary.example/mcp');
 assert.deepEqual(A.metadata(req,'server').code_challenge_methods_supported,['S256']);
});
test('local dates and times use the person\'s time zone',()=>{
 assert.equal(A.zonedMs('2026-10-24','22:00','Asia/Jerusalem'),Date.parse('2026-10-24T22:00:00+03:00'));
 assert.equal(A.zonedMs('2026-11-01','09:00','Asia/Jerusalem'),Date.parse('2026-11-01T09:00:00+02:00'),'after the clock change');
 assert.equal(A.zonedMs('2026-10-24','09:00','America/New_York'),Date.parse('2026-10-24T09:00:00-04:00'));
 assert.equal(A.zoneParts(Date.parse('2026-10-24T22:30:00Z'),'Asia/Jerusalem').date,'2026-10-25');
});
