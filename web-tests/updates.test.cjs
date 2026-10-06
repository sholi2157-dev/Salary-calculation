const {test}=require('node:test'),assert=require('node:assert/strict'),fs=require('node:fs'),vm=require('node:vm');
const {createUpdater,identity,CHECK_INTERVAL}=require('../web/updates.js');
const features=require('../web/features.js');
function fixture(){let remote={buildId:'A'},time=0,fetches=0,reloads=0,shown=null,hidden=0,safe=true,activations=0;
 const manager=createUpdater({current:{buildId:'A'},fetchBuild:async()=>{fetches++;return remote;},refreshWorker:async()=>{},activateWorker:async()=>{activations++;return true;},preserve:()=>safe,reload:()=>reloads++,show:s=>shown=s,hide:()=>{shown=null;hidden++;},now:()=>time});
 return {manager,setRemote:v=>remote=v,setTime:v=>time=v,setSafe:v=>safe=v,state:()=>({fetches,reloads,shown,hidden,activations})};}
test('public account switch disables config and protects dormant module before any DOM/SDK/storage access',()=>{
 assert.equal(features.accountsEnabled,false);
 vm.runInNewContext(fs.readFileSync('web/accounts.js','utf8'),{window:{WorkFeatures:features}});
 const handler=require('../api/config.js');let result;const response={setHeader(){},status(n){assert.equal(n,200);return this;},json(v){result=v;}};handler({method:'GET'},response);
 assert.deepEqual(result,{firebase:null,webAccountsEnabled:false,cloudSyncEnabled:false,webAiEnabled:false});
 const html=fs.readFileSync('index.html','utf8');assert.match(html,/<template id="account-template">/);assert.doesNotMatch(html,/<script src="web\/(accounts|cloud-sync)\.js"/);
});
test('no notice for same/invalid builds; checks throttle for five minutes; newer build and later are side-effect free',async()=>{
 const f=fixture();await f.manager.check();assert.equal(f.state().shown,null);await f.manager.check();assert.equal(f.state().fetches,1);
 f.setRemote({buildId:'B'});f.setTime(CHECK_INTERVAL);await f.manager.check();assert.equal(f.state().shown.build.buildId,'B');f.manager.later();assert.equal(f.state().shown,null);assert.equal(f.state().reloads,0);await f.manager.check(true);assert.equal(f.state().shown,null);
 f.setRemote({buildId:'C'});await f.manager.check(true);assert.equal(f.state().shown.build.buildId,'C');assert.equal(identity({commit:'bad'}),null);
});
test('explicit update activates worker, reloads exactly once; normal controller change never reloads',async()=>{
 const f=fixture();f.manager.controllerChanged();assert.equal(f.state().reloads,0);f.setRemote({buildId:'B'});await f.manager.check();await f.manager.updateNow();assert.equal(f.state().activations,1);assert.equal(f.state().reloads,0);
 f.manager.controllerChanged();f.manager.controllerChanged();await f.manager.updateNow();assert.equal(f.state().reloads,1);assert.equal(f.state().activations,1);
});
test('open edits and storage failures defer updates without activation or reload; late edits also defer',async()=>{
 const f=fixture();f.setRemote({buildId:'B'});await f.manager.check();f.setSafe(false);await f.manager.updateNow();assert.equal(f.state().activations,0);assert.equal(f.state().reloads,0);
 f.setSafe(true);await f.manager.updateNow();f.setSafe(false);f.manager.controllerChanged();assert.equal(f.state().reloads,0);assert.equal(f.state().shown.busy,false);
});
test('offline check/failed activation preserves usable UI and allows retry',async()=>{
 let shown,reloads=0,attempt=0;const m=createUpdater({current:{buildId:'A'},fetchBuild:async()=>({buildId:'B'}),refreshWorker:async()=>{throw Error('offline');},activateWorker:async()=>{if(!attempt++)throw Error('install failed');return false;},preserve:()=>true,reload:()=>reloads++,show:s=>shown=s,hide(){}});
 await m.check();await m.updateNow();assert.equal(reloads,0);assert.equal(shown.busy,false);await m.updateNow();assert.equal(reloads,1);
});

test('one-time cached RC13 shell upgrade is offered even if network HTML already reports the new identity',async()=>{
 let shown;const m=createUpdater({current:{buildId:'A'},fetchBuild:async()=>({buildId:'A'}),shellNeedsActivation:async()=>true,activateWorker:async()=>true,preserve:()=>true,reload(){},show:s=>shown=s,hide(){}});await m.check();assert.equal(shown.build.buildId,'A');
});
