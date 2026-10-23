const {test}=require('node:test'),assert=require('node:assert/strict'),crypto=require('node:crypto');
const {verifyIdToken,isOwner,summarizeInstalls,traffic,hourOf}=require('../lib/server/admin-dashboard.cjs');
const {publicKey,privateKey}=crypto.generateKeyPairSync('rsa',{modulusLength:2048});
const keys={k1:publicKey.export({type:'spki',format:'pem'})};
const b64=o=>Buffer.from(JSON.stringify(o)).toString('base64url');
function sign(claims,kid='k1'){const h=b64({alg:'RS256',kid}),p=b64(claims);return h+'.'+p+'.'+crypto.sign('RSA-SHA256',Buffer.from(h+'.'+p),privateKey).toString('base64url');}
const now=1800000000,base={aud:'workshiftsapp',iss:'https://securetoken.google.com/workshiftsapp',sub:'u1',iat:now-10,exp:now+3000,email:'sholi2157@gmail.com',email_verified:true};
test('only a valid token of the verified owner opens the dashboard',async()=>{
 assert.ok(isOwner(await verifyIdToken(sign(base),{keys,now})));
 assert.equal(isOwner(await verifyIdToken(sign({...base,email:'someone@gmail.com'}),{keys,now})),false);
 assert.equal(isOwner(await verifyIdToken(sign({...base,email_verified:false}),{keys,now})),false);
 for(const bad of [sign({...base,aud:'other'}),sign({...base,exp:now-1}),sign(base,'k2'),sign(base).slice(0,-4)+'AAAA','',null])assert.equal(await verifyIdToken(bad,{keys,now}),null);
});
test('install records are summarized by kind, version and New York day',()=>{
 const at=new Date('2026-10-23T20:00:00Z');
 const s=summarizeInstalls([{version:'2.0.7',kind:'new',at:'2026-10-23T03:00:00Z'},{version:'2.0.7',kind:'update',at:'2026-10-22T12:00:00Z'},{version:'2.0.10',kind:'update',at:'2026-09-01T12:00:00Z'}],at,30);
 assert.equal(s.total,3);assert.equal(s.new,1);assert.equal(s.update,2);assert.equal(s.week,2);
 assert.deepEqual(s.versions.map(v=>v.version),['2.0.10','2.0.7']);
 assert.equal(s.daily.length,30);assert.deepEqual(s.daily.at(-2),{day:'2026-10-22',new:1,update:1});assert.equal(s.daily.at(-1).new,0);
});
test('traffic without a Vercel token says so instead of failing',async()=>{assert.deepEqual(await traffic(30,{}),{error:'missing-token'});});
test('traffic is fetched for the project and hours are counted in New York time',async()=>{
 const urls=[];const fetcher=async url=>{urls.push(url);const by=new URL(url).searchParams.get('by');
  const data=url.includes('/count?')?{visitors:2,pageviews:5}:by==='hour'?[{timestamp:'2026-10-23T14:00:00.000Z',visitors:1,pageviews:3}]:by==='day'?[{timestamp:'2026-10-23T00:00:00.000Z',visitors:2,pageviews:5}]:[{[by]:'x',visitors:2,pageviews:5}];
  return {ok:true,json:async()=>({data})};};
 const t=await traffic(7,{token:'t',fetcher,now:new Date('2026-10-23T20:00:00Z')});
 assert.equal(t.total.pageviews,5);assert.equal(t.hours[10],3);assert.equal(hourOf(new Date('2026-10-23T14:00:00Z')),10);
 assert.ok(urls.every(u=>u.includes('projectId=prj_W8eD12XnlqJ5wVEsnTm43yIHgYnu')));
 assert.equal(new URL(urls[0]).searchParams.get('until'),'2026-10-24');
 assert.deepEqual(await traffic(7,{token:'t',fetcher:async()=>({ok:false,status:403})}),{error:'bad-token'});
});
