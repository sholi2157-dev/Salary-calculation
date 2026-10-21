/* Code refreshes on the next navigation. Never reload an open form or touch data. */
(function () {
'use strict';
if (!('serviceWorker' in navigator)) return;
async function check() {
 try {
  const registration = await navigator.serviceWorker.register('/sw.js', { updateViaCache: 'none' });
  await registration.update();
 } catch { /* Offline browsing keeps the last complete shell. */ }
}
if (document.readyState === 'complete') check();
else window.addEventListener('load', check, { once: true });
window.addEventListener('online', check);
// Returning to an existing tab prepares its next navigation without interrupting it.
document.addEventListener('visibilitychange', () => { if (document.visibilityState === 'visible') check(); });
})();
