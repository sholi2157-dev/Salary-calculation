'use strict';
// Anonymous download count for the owner's dashboard: one create-only Firestore record per browser and version.
// The record holds only the version and the server time; its id is a keyed hash, never the browser identifier.
const { createHmac } = require('node:crypto');
const PROJECT = 'workshiftsapp';
function recordId(browserId, version, key) { return createHmac('sha256', key).update(browserId + '|' + version).digest('hex').slice(0, 32); }
function body(id, version) {
  return JSON.stringify({ writes: [{ update: { name: 'projects/' + PROJECT + '/databases/(default)/documents/downloads/' + id, fields: { version: { stringValue: version } } },
    currentDocument: { exists: false }, updateTransforms: [{ fieldPath: 'at', setToServerValue: 'REQUEST_TIME' }] }] });
}
async function countDownload(req, release, browserId, { env = process.env, fetcher = fetch } = {}) {
  if (req.method !== 'GET' || env.VERCEL_ENV !== 'production' || !env.DOWNLOAD_HASH_KEY) return false;
  if (/bot|crawler|spider|headless|preview/i.test(req.headers?.['user-agent'] || '')) return false;
  try {
    const r = await fetcher('https://firestore.googleapis.com/v1/projects/' + PROJECT + '/databases/(default)/documents:commit', { method: 'POST',
      headers: { 'Content-Type': 'application/json' }, body: body(recordId(browserId, release.version, env.DOWNLOAD_HASH_KEY), release.version), signal: AbortSignal.timeout(1800) });
    // A repeat download of the same version by the same browser already exists: counted once, not an error.
    return r.ok || r.status === 409 || (r.status === 400 && /ALREADY_EXISTS|FAILED_PRECONDITION/.test(await r.text()));
  } catch { return false; }
}
module.exports = { countDownload, recordId, body };
