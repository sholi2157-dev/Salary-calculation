'use strict';
// The owner's private dashboard: site traffic (Vercel Web Analytics), anonymous Android install records and the
// published version. Only the owner's verified Google account may read it; nothing here exposes personal data.
const crypto = require('node:crypto');
const { latestRelease } = require('./android-release.cjs');

const OWNER = 'sholi2157@gmail.com';
const FIREBASE_PROJECT = 'workshiftsapp';
const VERCEL_TEAM = 'team_QTwKc5N0aMaEPiMCaAWcZRXx';
const VERCEL_PROJECT = 'prj_W8eD12XnlqJ5wVEsnTm43yIHgYnu';
const CERTS = 'https://www.googleapis.com/robot/v1/metadata/x509/securetoken@system.gserviceaccount.com';
const ZONE = 'America/New_York';

let certs, certsExpire = 0;
async function googleCerts(fetcher) {
  if (certs && certsExpire > Date.now()) return certs;
  const r = await fetcher(CERTS, { signal: AbortSignal.timeout(5000) });
  if (!r.ok) throw Error('Certificates unavailable');
  const age = /max-age=(\d+)/.exec(r.headers.get('cache-control') || '');
  certs = await r.json(); certsExpire = Date.now() + (age ? +age[1] * 1000 : 3600000);
  return certs;
}

const part = s => JSON.parse(Buffer.from(s, 'base64url').toString('utf8'));
/** Verifies a Firebase ID token with Google's public certificates and returns its claims, or null. */
async function verifyIdToken(token, { fetcher = fetch, now = Date.now() / 1000, keys } = {}) {
  try {
    const [h, p, s] = String(token || '').split('.');
    const header = part(h), claims = part(p);
    if (header.alg !== 'RS256') return null;
    const pem = (keys || await googleCerts(fetcher))[header.kid];
    if (!pem || !crypto.verify('RSA-SHA256', Buffer.from(h + '.' + p), pem, Buffer.from(s, 'base64url'))) return null;
    if (claims.aud !== FIREBASE_PROJECT || claims.iss !== 'https://securetoken.google.com/' + FIREBASE_PROJECT) return null;
    if (!(claims.exp > now) || !(claims.iat <= now + 300) || !claims.sub) return null;
    return claims;
  } catch { return null; }
}
const isOwner = claims => !!claims && claims.email_verified === true && String(claims.email).toLowerCase() === OWNER;

const day = (d, zone = ZONE) => new Intl.DateTimeFormat('en-CA', { timeZone: zone, year: 'numeric', month: '2-digit', day: '2-digit' }).format(d);
const hourOf = (d, zone = ZONE) => +new Intl.DateTimeFormat('en-US', { timeZone: zone, hour: '2-digit', hourCycle: 'h23' }).format(d);

async function vercel(path, params, token, fetcher) {
  const q = new URLSearchParams({ projectId: VERCEL_PROJECT, teamId: VERCEL_TEAM, ...params });
  const r = await fetcher('https://api.vercel.com/v1/query/web-analytics/visits/' + path + '?' + q, { headers: { Authorization: 'Bearer ' + token }, signal: AbortSignal.timeout(8000) });
  if (!r.ok) throw Error('Vercel ' + r.status);
  return (await r.json()).data;
}

/** Traffic for the last `days` days, with hours of day in New York time. */
async function traffic(days, { token, fetcher = fetch, now = new Date() } = {}) {
  if (!token) return { error: 'missing-token' };
  const until = new Date(now.getTime() + 86400000).toISOString().slice(0, 10);
  const since = new Date(now.getTime() - (days - 1) * 86400000).toISOString().slice(0, 10);
  const range = { since, until }, top = by => vercel('aggregate', { ...range, by, limit: '10' }, token, fetcher);
  try {
    const [total, daily, hourly, country, deviceType, osName, browserName, referrerHostname, requestPath] = await Promise.all([
      vercel('count', range, token, fetcher), top('day'),
      vercel('aggregate', { since: new Date(now.getTime() - Math.min(days, 31) * 86400000).toISOString(), until: now.toISOString(), by: 'hour' }, token, fetcher).catch(() => []),
      top('country'), top('deviceType'), top('osName'), top('browserName'), top('referrerHostname'), top('requestPath')]);
    const hours = Array.from({ length: 24 }, () => 0);
    for (const row of hourly) hours[hourOf(new Date(row.timestamp))] += row.pageviews;
    return { since, total, daily: daily.map(r => ({ day: r.timestamp.slice(0, 10), visitors: r.visitors, pageviews: r.pageviews })),
      hours, country, deviceType, osName, browserName, referrerHostname, requestPath };
  } catch (e) {
    return { error: /40[13]/.test(e.message) ? 'bad-token' : 'unavailable' };
  }
}

/** Every install record, read with the owner's own token so Firestore rules decide access. */
async function installRecords(idToken, fetcher = fetch) {
  const rows = []; let pageToken = '';
  do {
    const url = 'https://firestore.googleapis.com/v1/projects/' + FIREBASE_PROJECT + '/databases/(default)/documents/installs?pageSize=300' + (pageToken ? '&pageToken=' + encodeURIComponent(pageToken) : '');
    const r = await fetcher(url, { headers: { Authorization: 'Bearer ' + idToken }, signal: AbortSignal.timeout(8000) });
    if (r.status === 403) return { error: 'rules' };
    if (!r.ok) return { error: 'unavailable' };
    const page = await r.json();
    for (const d of page.documents || []) rows.push({ version: d.fields?.version?.stringValue || '?', kind: d.fields?.kind?.stringValue || 'update', at: d.fields?.at?.timestampValue || d.createTime });
    pageToken = page.nextPageToken || '';
  } while (pageToken && rows.length < 30000);
  return { rows };
}

const compareVersions = (a, b) => b.localeCompare(a, 'en', { numeric: true });
function summarizeInstalls(rows, now = new Date(), days = 30) {
  const versions = {}, daily = {};
  for (let i = days - 1; i >= 0; i--) daily[day(new Date(now.getTime() - i * 86400000))] = { new: 0, update: 0 };
  let fresh = 0, week = 0, last = null;
  for (const r of rows) {
    const kind = r.kind === 'new' ? 'new' : 'update', at = new Date(r.at);
    if (kind === 'new') fresh++;
    (versions[r.version] ||= { new: 0, update: 0 })[kind]++;
    const d = daily[day(at)]; if (d) d[kind]++;
    if (now - at < 7 * 86400000) week++;
    if (!last || at > last) last = at;
  }
  return { total: rows.length, new: fresh, update: rows.length - fresh, week, last: last && last.toISOString(),
    versions: Object.keys(versions).sort(compareVersions).map(v => ({ version: v, ...versions[v] })),
    daily: Object.entries(daily).map(([d, v]) => ({ day: d, ...v })) };
}

async function dashboard({ idToken, days = 30, env = process.env, fetcher = fetch, now = new Date() }) {
  const [site, installs, release] = await Promise.all([
    traffic(days, { token: env.VERCEL_ANALYTICS_TOKEN, fetcher, now }),
    installRecords(idToken, fetcher).catch(() => ({ error: 'unavailable' })),
    latestRelease(fetcher)]);
  return { generatedAt: now.toISOString(), days, site,
    app: installs.error ? { error: installs.error } : summarizeInstalls(installs.rows, now, days),
    release: { version: release.version, size: release.size, fallback: !!release.fallback } };
}

module.exports = { OWNER, FIREBASE_PROJECT, verifyIdToken, isOwner, traffic, summarizeInstalls, dashboard, hourOf };
