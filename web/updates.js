/* Controlled code updates. Financial stores are never read or rewritten here. */
(function(root){
'use strict';
const CHECK_INTERVAL=5*60*1000;
function identity(info){return typeof info?.buildId==='string'&&info.buildId.length>0&&info.buildId.length<200?info.buildId:typeof info?.commit==='string'&&/^[a-f0-9]{40}$/.test(info.commit)?info.commit:null;}
function createUpdater({current,fetchBuild,refreshWorker=async()=>{},shellNeedsActivation=async()=>false,activateWorker,preserve,reload,show,hide,now=Date.now}){
 const running=identity(current);let lastCheck=-Infinity,inFlight=null,available=null,dismissed=null,requested=false,reloaded=false,busy=false,activationTimer=null;
 const display=(message)=>show({build:available,busy,message});
 async function check(force=false){
  if(!running||requested||(!force&&now()-lastCheck<CHECK_INTERVAL))return;
  if(inFlight)return inFlight;
  lastCheck=now();
  inFlight=(async()=>{try{
   // Both endpoints bypass their ordinary caches; offline failures are silent.
   const [info]=await Promise.all([fetchBuild(),refreshWorker().catch(()=>{})]);
   const next=identity(info);if(!next)return;
   if(next===running&&!await shellNeedsActivation()){available=null;hide();return;}
   available=info;if(next!==dismissed)display();
  }catch{}finally{inFlight=null;}})();return inFlight;
 }
 function later(){if(busy)return;dismissed=identity(available);hide();}
 function controllerChanged(){
  if(!requested||reloaded)return;
  clearTimeout(activationTimer);
  // A user may open another edit while a slow worker install is finishing.
  if(!preserve()){requested=false;busy=false;display('סיים או בטל את העריכה הפתוחה, ואז עדכן.');return;}
  reloaded=true;reload();
 }
 async function updateNow(){
  if(busy||!available||reloaded)return;
  if(!preserve()){display('סיים או בטל את העריכה הפתוחה, ואז עדכן.');return;}
  busy=true;requested=true;display();
  try{
   const waitsForController=await activateWorker(available,preserve);
   if(!waitsForController)controllerChanged();
   else if(requested&&!reloaded){activationTimer=setTimeout(()=>{requested=false;busy=false;display('העדכון עדיין לא הופעל. הנתונים נשמרו; אפשר לנסות שוב.');},15000);activationTimer.unref?.();}
  }
  catch{requested=false;busy=false;display('העדכון לא הושלם. הנתונים נשמרו; אפשר לנסות שוב.');}
 }
 return {check,later,updateNow,controllerChanged};
}
const api={identity,createUpdater,CHECK_INTERVAL};
if(typeof module!=='undefined'){module.exports=api;return;}
root.WorkUpdates=api;
const notice=document.getElementById('web-update-notice'),body=document.getElementById('web-update-body'),button=document.getElementById('web-update-now'),later=document.getElementById('web-update-later');
document.querySelector('.header-bar').after(notice);
let registration=null,registering=null;
async function ensureRegistration(){
 if(registration)return registration;
 if(!registering)registering=navigator.serviceWorker.register('/sw.js',{updateViaCache:'none'}).then(r=>{
  registration=r;
  r.addEventListener('updatefound',()=>{const worker=r.installing;worker?.addEventListener('statechange',()=>{if(worker.state==='installed')manager.check(true);});});
  return r;
 }).finally(()=>{registering=null;});
 return registering;
}
function workerBuild(worker){return new Promise((resolve,reject)=>{
 if(!worker){reject(Error('No worker'));return;}const channel=new MessageChannel();const timer=setTimeout(()=>{channel.port1.close();reject(Error('Worker did not respond'));},3000);
 channel.port1.onmessage=e=>{clearTimeout(timer);channel.port1.close();resolve(e.data);};worker.postMessage({type:'GET_BUILD_INFO'},[channel.port2]);
});}
async function waitForCandidate(){
 await registration.update();if(registration.waiting)return registration.waiting;
 const installing=registration.installing;
 if(installing)await new Promise((resolve,reject)=>{const timer=setTimeout(done,15000);function done(){clearTimeout(timer);installing.removeEventListener('statechange',changed);resolve();}function changed(){if(['installed','activated','redundant'].includes(installing.state))done();}installing.addEventListener('statechange',changed);changed();});
 return registration.waiting;
}
const manager=createUpdater({
 current:root.WorkBuild,
 fetchBuild:async()=>{const abort=new AbortController(),timer=setTimeout(()=>abort.abort(),8000);try{const response=await fetch('/build-info.json?check='+Date.now(),{cache:'no-store',credentials:'omit',signal:abort.signal});if(!response.ok)throw Error('Build unavailable');return await response.json();}finally{clearTimeout(timer);}},
 refreshWorker:async()=>{if('serviceWorker' in navigator)await(await ensureRegistration()).update();},
 // The previous RC13 worker served network HTML with old cached JS. If a new
 // page identity arrives before its new worker, offer the one-time shell upgrade.
 shellNeedsActivation:async()=>Boolean(registration?.waiting&&navigator.serviceWorker.controller&&identity(await workerBuild(registration.waiting))===identity(root.WorkBuild)),
 preserve:()=>{
  if(root.prepareWebUpdate)return root.prepareWebUpdate()===true;
  // Compatibility with that cached RC13 experience.js (its draft saver returned
  // undefined). Verify the actual written draft before allowing activation.
  if(typeof editingShiftId==='undefined'||editingShiftId!==null||document.querySelector('dialog[open]')||typeof saveReportDraft!=='function')return false;
  try{saveReportDraft();const saved=JSON.parse(localStorage.getItem(ownerKey('work_report_draft_v1:')));return saved?.mode===reportMode&&saved.expanded===!document.getElementById('report-content').hidden&&[...document.querySelectorAll('#report-fields input[id],#report-fields textarea[id],#report-fields select[id]')].every(c=>saved.values?.[c.id]?.value===c.value&&(!('checked' in c)||saved.values[c.id].checked===c.checked))&&JSON.stringify(saved.group)===JSON.stringify([...document.getElementById('group-rows').children].map(row=>({...row.workerData,name:row.querySelector('[data-field=name]').value,hours:row.querySelector('[data-field=hours]').value,isPaid:row.querySelector('[data-field=paid]').checked})));}catch{return false;}
 },
 activateWorker:async(target,preserve)=>{
  if(!('serviceWorker' in navigator)){if(!preserve())throw Error('Open edit');return false;}
  await ensureRegistration();
  const waiting=await waitForCandidate();
  const worker=waiting||registration.active,info=await workerBuild(worker);
  if(identity(info)!==identity(target))throw Error('Target shell not ready');
  if(!preserve())throw Error('Open edit');
  if(waiting){waiting.postMessage({type:'SKIP_WAITING'});return true;}
  // Another tab may already have activated this version. A network-only reload
  // would be unsafe while an old controller still owns old assets.
  return false;
 },
 reload:()=>location.reload(),
 show:({busy,message})=>{notice.hidden=false;body.textContent=message||'יש עדכון חדש לאתר. הנתונים שלך יישמרו.';button.disabled=later.disabled=busy;button.textContent=busy?'מעדכן…':'עדכן עכשיו';},
 hide:()=>{notice.hidden=true;}
});
root.WebUpdateManager=manager;
button.addEventListener('click',()=>manager.updateNow());later.addEventListener('click',manager.later);
if('serviceWorker' in navigator)navigator.serviceWorker.addEventListener('controllerchange',manager.controllerChanged);
async function start(){
 if('serviceWorker' in navigator)try{await ensureRegistration();}catch{}
 manager.check(true);
 setInterval(()=>{if(document.visibilityState==='visible')manager.check();},CHECK_INTERVAL);
 document.addEventListener('visibilitychange',()=>{if(document.visibilityState==='visible')manager.check();});
 window.addEventListener('online',()=>manager.check());
}
if(document.readyState==='complete')start();else window.addEventListener('load',start,{once:true});
})(globalThis);
