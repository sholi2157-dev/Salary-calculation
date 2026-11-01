'use strict';
const REPO = 'sholi2157-dev/Salary-calculation';
// Keep the existing response field types; empty metadata means no version was verified.
const FALLBACK = { version: '', name: '', size: 0, url: 'https://github.com/' + REPO + '/releases', fallback: true };
let cached, expires = 0;
function parseTag(tag) {
  if (typeof tag !== 'string') return null;
  const match = /^android-v((?:0|[1-9]\d*)\.(?:0|[1-9]\d*)(?:\.(?:0|[1-9]\d*))?)(?:-code([1-9]\d*))?$/.exec(tag);
  if (!match || !match[1].split('.').every(n => Number.isSafeInteger(Number(n)))) return null;
  const versionCode = match[2] ? Number(match[2]) : null;
  if (versionCode !== null && !Number.isSafeInteger(versionCode)) return null;
  return { version: match[1], ...(versionCode === null ? {} : { versionCode }) };
}
function selectRelease(releases) {
  if (!Array.isArray(releases)) throw new Error('Invalid releases response');
  const candidates = [];
  for (const release of releases) {
    if (!release || release.draft !== false || release.prerelease !== false) continue;
    const parsed = parseTag(release.tag_name), published = Date.parse(release.published_at);
    if (!parsed || !Number.isFinite(published) || published > Date.now()) continue;
    const name = 'Salary' + parsed.version + '.apk';
    const url = 'https://github.com/' + REPO + '/releases/download/' + release.tag_name + '/' + name;
    const apk = Array.isArray(release.assets) && release.assets.find(a => a && a.state === 'uploaded' && a.name === name &&
      a.browser_download_url === url && Number.isSafeInteger(a.size) && a.size > 0 &&
      (!a.content_type || ['application/vnd.android.package-archive', 'application/octet-stream'].includes(a.content_type)));
    if (apk) candidates.push({ ...parsed, name, size: apk.size, url, fallback: false, published });
  }
  // GitHub's listing order is not a version guarantee (e.g. an older release republished later).
  candidates.sort((a, b) => {
    const av = a.version.split('.').map(Number), bv = b.version.split('.').map(Number);
    for (let i = 0; i < 3; i++) if ((av[i] || 0) !== (bv[i] || 0)) return (bv[i] || 0) - (av[i] || 0);
    return (b.versionCode || 0) - (a.versionCode || 0) || b.published - a.published;
  });
  if (!candidates.length) throw new Error('No published Android APK');
  const { published, ...selected } = candidates[0];
  return selected;
}
async function latestRelease(fetcher = fetch) {
  if (cached && expires > Date.now()) return cached;
  try {
    const response = await fetcher('https://api.github.com/repos/' + REPO + '/releases?per_page=30', { headers: { Accept: 'application/vnd.github+json', 'User-Agent': 'Salary-Calculation-Downloads' }, signal: AbortSignal.timeout(6000) });
    if (!response.ok) throw new Error('Release lookup unavailable');
    cached = selectRelease(await response.json()); expires = Date.now() + 300000;
    return cached;
  } catch {
    // An expired cached version must not be advertised as the current download after a failed lookup.
    cached = undefined; expires = 0;
    return FALLBACK;
  }
}
module.exports = { latestRelease, selectRelease, parseTag, FALLBACK };
