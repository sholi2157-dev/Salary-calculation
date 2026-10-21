/* Code refreshes on the next navigation. Never reload an open form or touch data. */
(function () {
'use strict';
if (!('serviceWorker' in navigator)) return;
let registrationPromise;
async function check() {
 try {
  if (!registrationPromise) registrationPromise = navigator.serviceWorker.register('/sw.js', { updateViaCache: 'none' });
  await (await registrationPromise).update(); // An existing registration alone may skip a fresh script check.
 } catch { registrationPromise = null; /* Offline browsing keeps the last complete shell. */ }
}
if (document.readyState === 'complete') check();
else window.addEventListener('load', check, { once: true });
window.addEventListener('online', check);
// Returning to an existing tab prepares its next navigation without interrupting it.
document.addEventListener('visibilitychange', () => { if (document.visibilityState === 'visible') check(); });
})();
