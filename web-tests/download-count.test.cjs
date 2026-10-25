const {test}=require('node:test'),assert=require('node:assert/strict');
const {countDownload,recordId}=require('../lib/server/download-count.cjs');
const {summarizeDownloads}=require('../lib/server/admin-dashboard.cjs');
const env={VERCEL_ENV:'production',DOWNLOAD_HASH_KEY:'synthetic-hash-key'};
test('a download writes one create-only record keyed by a hash of browser and version',async()=>{
 let url,body;const ok=await countDownload({method:'GET',headers:{}},{version:'2.0.7'},'b-1',{env,fetcher:async(u,o)=>{url=u;body=JSON.parse(o.body);return{ok:true}}});
 assert.ok(ok);assert.match(url,/documents:commit$/);const w=body.writes[0];
 assert.ok(w.update.name.endsWith('/downloads/'+recordId('b-1','2.0.7',env.DOWNLOAD_HASH_KEY)));assert.match(w.update.name,/[a-f0-9]{32}$/);
 assert.deepEqual(Object.keys(w.update.fields),['version']);assert.equal(w.currentDocument.exists,false);assert.ok(!JSON.stringify(body).includes('b-1'));
 assert.notEqual(recordId('b-1','2.0.7','k'),recordId('b-1','2.0.8','k'));
});
test('repeat downloads count as done; previews, bots, HEAD and outages never fail the download',async()=>{
 assert.ok(await countDownload({method:'GET',headers:{}},{version:'2.0.7'},'b',{env,fetcher:async()=>({ok:false,status:409,text:async()=>''})}));
 let calls=0;const f=async()=>{calls++;return{ok:true}};
 await countDownload({method:'HEAD',headers:{}},{version:'1'},'b',{env,fetcher:f});await countDownload({method:'GET',headers:{'user-agent':'Googlebot'}},{version:'1'},'b',{env,fetcher:f});
 await countDownload({method:'GET',headers:{}},{version:'1'},'b',{env:{...env,VERCEL_ENV:'preview'},fetcher:f});assert.equal(calls,0);
 assert.equal(await countDownload({method:'GET',headers:{}},{version:'1'},'b',{env,fetcher:async()=>{throw Error('offline')}}),false);
});
test('downloads are summarized by day and version',()=>{
 const s=summarizeDownloads([{version:'2.0.7',at:'2026-10-25T15:00:00Z'},{version:'2.0.7',at:'2026-10-24T15:00:00Z'},{version:'2.0.8',at:'2026-10-25T16:00:00Z'}],new Date('2026-10-25T20:00:00Z'),7);
 assert.equal(s.total,3);assert.equal(s.week,3);assert.deepEqual(s.versions,[{version:'2.0.8',downloads:1},{version:'2.0.7',downloads:2}]);assert.equal(s.daily.at(-1).downloads,2);
});
