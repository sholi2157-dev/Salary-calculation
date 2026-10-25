const {test}=require('node:test'),assert=require('node:assert/strict'),fs=require('node:fs'),vm=require('node:vm');
const features=require('../web/features.js');
test('the old email accounts stay dormant; Google account sync gets only the public Firebase web fields',()=>{
 assert.equal(features.accountsEnabled,false);assert.equal(features.accountSyncEnabled,true);
 vm.runInNewContext(fs.readFileSync('web/accounts.js','utf8'),{window:{WorkFeatures:features}});
 const handler=require('../api/config.js');let result;const response={setHeader(){},status(n){assert.equal(n,200);return this;},json(v){result=v;}};handler({method:'GET'},response);
 assert.deepEqual(Object.keys(result.firebase).sort(),['apiKey','authDomain','projectId']);assert.equal(result.firebase.projectId,'workshiftsapp');
 assert.equal(result.webAccountsEnabled,false);assert.equal(result.accountSyncEnabled,true);assert.equal(result.webAiEnabled,false);
 const html=fs.readFileSync('index.html','utf8');/* 2.0 page: accounts are off and have no screen at all; their code must never load. */assert.doesNotMatch(html,/<script src="web\/(accounts|cloud-sync)\.js"/);
});

function worker({online=true,networkStatus=200,cachedAsset}={}) {
 const listeners={},calls=[];
 const cachedPage=new Response('complete offline shell');
 const cache={match:async path=>path==='/index.html'?cachedPage:undefined};
 vm.runInNewContext(fs.readFileSync('sw.js','utf8'),{
  self:{location:{origin:'https://test'},addEventListener:(name,fn)=>listeners[name]=fn},
  URL,Response,Request:class {constructor(request,options){this.url=typeof request==='string'?request:request.url;this.cache=options?.cache;}},
  caches:{open:async()=>cache,match:async request=>{calls.push(request.url);return cachedAsset;}},
  fetch:async request=>{calls.push(request.cache);if(!online)throw Error('offline');return new Response('fresh server shell',{status:networkStatus});}
 });
 return {calls,request:async(path,mode)=>{let response;listeners.fetch({request:{url:'https://test'+path,method:'GET',mode},respondWith:p=>response=p});return await response;}};
}
test('navigation gets fresh server HTML; offline and HTTP failure keep complete installed shell',async()=>{
 for(const [options,text] of [[{},'fresh server shell'],[{online:false},'complete offline shell'],[{networkStatus:503},'complete offline shell']]){
  // A browser Request can carry mode=navigate; the test supplies a portable clone.
  const w=worker(options);const response=await w.request('/','navigate');assert.equal(await response.text(),text);
 }
});
test('asset fallback looks up the full versioned URL, never another shell version',async()=>{
 const w=worker({cachedAsset:new Response('exact cached asset')});assert.equal(await(await w.request('/web/kis/app.js?v=B')).text(),'exact cached asset');assert.deepEqual(w.calls,['https://test/web/kis/app.js?v=B']);
 const offline=worker({online:false});await assert.rejects(offline.request('/web/kis/app.js?v=C'));assert.equal(offline.calls[0],'https://test/web/kis/app.js?v=C');
});
test('background installation registers without UI, reload, storage or account access',async()=>{
 const events={};let checks=0;
 vm.runInNewContext(fs.readFileSync('web/updates.js','utf8'),{
  navigator:{serviceWorker:{register:async(path,options)=>{assert.equal(path,'/sw.js');assert.equal(options.updateViaCache,'none');return {update:async()=>checks++};}}},
  document:{readyState:'complete',visibilityState:'visible',addEventListener:(n,f)=>events[n]=f},window:{addEventListener:(n,f)=>events[n]=f}
 });
 await new Promise(setImmediate);assert.equal(checks,1);await events.online();await events.visibilitychange();await new Promise(setImmediate);assert.equal(checks,3);
});
