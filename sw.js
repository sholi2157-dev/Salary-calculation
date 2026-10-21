/* A build owns a complete shell. Never mix a new HTML file with old JS/CSS. */
const BUILD='__WEB_BUILD_ID__';
const CACHE='salary-web--automatic-20261021-'+BUILD;
const versioned=path=>path.startsWith('/web/')&&!path.includes('/fonts/')?path+'?v='+BUILD:path;
// Navigation requests reject redirected responses supplied by a worker.
function navigationResponse(response){return response?.redirected?new Response(response.body,{status:response.status,statusText:response.statusText,headers:response.headers}):response;}
const SHELL=['/','/index.html','/web/kis/kis.css','/web/fonts/heebo.ttf','/web/fonts/rubik_regular.ttf','/web/fonts/rubik_medium.ttf','/web/fonts/rubik_semibold.ttf','/web/fonts/rubik_bold.ttf','/web/fonts/secular_one.ttf','/web/features.js','/web/build.js','/web/updates.js','/web/transfer.js','/web/categories.js','/web/runtime.js','/web/v2/rules.js','/web/v2/pay.js','/web/v2/plan.js','/web/v2/share.js','/web/v2/summary.js','/web/kis/ui.js','/web/kis/store.js','/web/kis/plans.js','/web/kis/download.js','/web/kis/home.js','/web/kis/history.js','/web/kis/editor.js','/web/kis/settings.js','/web/kis/summary.js','/web/kis/launch.js','/web/kis/app.js'];
self.addEventListener('install',event=>event.waitUntil((async()=>{
 const cache=await caches.open(CACHE);
 try{
  await cache.addAll(SHELL.map(url=>new Request(versioned(url),{cache:'reload'})));
  for(const path of ['/', '/index.html']){const response=await cache.match(path);if(response?.redirected)await cache.put(path,navigationResponse(response));}
  await self.skipWaiting(); // Activation never navigates/reloads an open document.
 }
 catch(error){await caches.delete(CACHE);throw error;} // Never activate a partial offline shell.
})()));
self.addEventListener('activate',event=>event.waitUntil((async()=>{
 // Keep the preceding complete shell for already open documents. Data stores
 // and unrelated caches are never read or deleted.
 const older=(await caches.keys()).filter(k=>k.startsWith('salary-web-')&&k!==CACHE);
 await Promise.all(older.slice(0,-1).map(k=>caches.delete(k)));
 await self.clients.claim();
})()));
self.addEventListener('message',event=>{
 if(event.data?.type==='GET_BUILD_INFO')event.ports[0]?.postMessage({buildId:BUILD});
 if(event.data?.type==='SKIP_WAITING')event.waitUntil(self.skipWaiting());
});
self.addEventListener('fetch',event=>{
 const url=new URL(event.request.url);
 if(event.request.method!=='GET'||url.origin!==self.location.origin||url.pathname.startsWith('/api/'))return;
 if(url.pathname==='/build-info.json'||url.pathname==='/sw.js'){
  event.respondWith(fetch(new Request(event.request,{cache:'no-store'})));return;
 }
 if(event.request.mode==='navigate'&&(url.pathname==='/'||url.pathname==='/index.html')){
  // Ordinary online navigation always asks the server. Only offline failures
  // use the complete installed shell; never overwrite it with a partial build.
  event.respondWith((async()=>{
   try { const response=await fetch(new Request(event.request,{cache:'no-store'}));if(response.ok)return navigationResponse(response); } catch {}
   return navigationResponse(await(await caches.open(CACHE)).match('/index.html'));
  })());return;
 }
 if(SHELL.includes(url.pathname))event.respondWith((async()=>{
  // A new document may arrive before its worker finishes installing. Query
  // identities prevent its JS/CSS falling back to another document's version.
  const cached=await caches.match(event.request);
  if(cached)return cached;
  return fetch(new Request(event.request,{cache:'no-cache'}));
 })());
});
