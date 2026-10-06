/* Cache only the local application shell; never cache credentials, APIs or account responses. */
const CACHE='salary-web-rc13-v2';
const SHELL=['/','/index.html','/web/app.css','/web/fonts/heebo.ttf','/web/transfer.js','/web/history.js','/web/selection.js','/web/cloud-sync.js','/web/categories.js','/web/sharing.js','/web/presentation.js','/web/parity.js','/web/runtime.js','/web/app.js','/web/experience.js','/web/accounts.js'];
self.addEventListener('install',event=>event.waitUntil(caches.open(CACHE).then(cache=>cache.addAll(SHELL))));
self.addEventListener('activate',event=>event.waitUntil(caches.keys().then(keys=>Promise.all(keys.filter(k=>k.startsWith('salary-web-')&&k!==CACHE).map(k=>caches.delete(k)))).then(()=>self.clients.claim())));
self.addEventListener('fetch',event=>{
 const url=new URL(event.request.url);if(event.request.method!=='GET'||url.origin!==self.location.origin||url.pathname.startsWith('/api/'))return;
 if(event.request.mode==='navigate'){event.respondWith(fetch(event.request).catch(()=>caches.match('/index.html')));return;}
 if(SHELL.includes(url.pathname))event.respondWith(caches.match(url.pathname).then(cached=>cached||fetch(event.request)));
});
