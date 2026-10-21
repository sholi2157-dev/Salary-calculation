/* A build owns a complete shell. Never mix a new HTML file with old JS/CSS. */
const BUILD='__WEB_BUILD_ID__';
const CACHE='salary-web--entry-20261021'+BUILD;
// Navigation requests reject redirected responses supplied by a worker.
function navigationResponse(response){return response?.redirected?new Response(response.body,{status:response.status,statusText:response.statusText,headers:response.headers}):response;}
const SHELL=['/','/index.html','/web/kis/kis.css','/web/fonts/heebo.ttf','/web/fonts/rubik_regular.ttf','/web/fonts/rubik_medium.ttf','/web/fonts/rubik_semibold.ttf','/web/fonts/rubik_bold.ttf','/web/fonts/secular_one.ttf','/web/features.js','/web/build.js','/web/updates.js','/web/transfer.js','/web/categories.js','/web/runtime.js','/web/v2/rules.js','/web/v2/pay.js','/web/v2/plan.js','/web/v2/share.js','/web/v2/summary.js','/web/kis/ui.js','/web/kis/store.js','/web/kis/plans.js','/web/kis/download.js','/web/kis/home.js','/web/kis/history.js','/web/kis/editor.js','/web/kis/settings.js','/web/kis/summary.js','/web/kis/launch.js','/web/kis/app.js'];
self.addEventListener('install',event=>event.waitUntil((async()=>{
 const cache=await caches.open(CACHE);
 try{
  await cache.addAll(SHELL.map(url=>new Request(url,{cache:'reload'})));
  for(const path of ['/', '/index.html']){const response=await cache.match(path);if(response?.redirected)await cache.put(path,navigationResponse(response));}

 }
 catch(error){await caches.delete(CACHE);throw error;} // Never activate a partial offline shell.
})()));
self.addEventListener('activate',event=>event.waitUntil((async()=>{
 // Delete only obsolete code caches. No product/account storage is touched.
 await Promise.all((await caches.keys()).filter(k=>k.startsWith('salary-web-')&&k!==CACHE).map(k=>caches.delete(k)));
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
  event.respondWith(caches.open(CACHE).then(cache=>cache.match('/index.html')).then(async cached=>navigationResponse(cached||await fetch(event.request))));return;
 }
 if(SHELL.includes(url.pathname))event.respondWith(caches.open(CACHE).then(cache=>cache.match(url.pathname)).then(cached=>cached||fetch(event.request)));
});
