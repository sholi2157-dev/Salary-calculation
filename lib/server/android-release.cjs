'use strict';
const REPO = 'sholi2157-dev/Salary-calculation';
const FALLBACK = { version: '2.0.3', name: 'Salary2.0.3.apk', size: 19468736, url: 'https://github.com/' + REPO + '/releases/download/android-v2.0.3/Salary2.0.3.apk', fallback: true };
let cached, expires = 0;
function selectRelease(releases) {
  for (const release of releases) {
    if (release.draft || release.prerelease || !/^android-v\d/.test(release.tag_name)) continue;
    const apk = release.assets?.find(a => a.state === 'uploaded' && /^Salary[\d.]+\.apk$/.test(a.name));
    if (!apk || !apk.browser_download_url.startsWith('https://github.com/' + REPO + '/releases/download/')) continue;
    return { version: release.tag_name.replace('android-v', ''), name: apk.name, size: apk.size, url: apk.browser_download_url, fallback: false };
  }
  throw new Error('No published Android APK');
}
async function latestRelease(fetcher = fetch) {
  if (cached && expires > Date.now()) return cached;
  try {
    const response = await fetcher('https://api.github.com/repos/' + REPO + '/releases?per_page=30', { headers: { Accept: 'application/vnd.github+json', 'User-Agent': 'Salary-Calculation-Downloads' }, signal: AbortSignal.timeout(6000) });
    if (!response.ok) throw new Error('Release lookup unavailable');
    cached = selectRelease(await response.json()); expires = Date.now() + 300000;
    return cached;
  } catch { return cached || FALLBACK; }
}
module.exports = { latestRelease, selectRelease, FALLBACK };
