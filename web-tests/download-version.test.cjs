const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const repoUrl = 'https://github.com/sholi2157-dev/Salary-calculation';
const source = path => fs.readFileSync(require.resolve(path), 'utf8');
function server(now = Date.parse('2026-11-01T00:00:00Z')) {
  let clock = now;
  const context = vm.createContext({ module: { exports: {} }, AbortSignal,
    Date: class extends Date { static now() { return clock; } } });
  vm.runInContext(source('../lib/server/android-release.cjs'), context);
  return { ...context.module.exports, advance(ms) { clock += ms; } };
}
function release(tag = 'android-v2.2.1-code46', version = '2.2.1') {
  const name = `Salary${version}.apk`;
  return { tag_name: tag, draft: false, prerelease: false, published_at: '2026-10-27T16:23:38Z',
    assets: [{ name, state: 'uploaded', size: 20000000, content_type: 'application/vnd.android.package-archive',
      browser_download_url: `${repoUrl}/releases/download/${tag}/${name}` }] };
}
const json = value => ({ ok: true, json: async () => value });
const plain = value => JSON.parse(JSON.stringify(value));

test('Android tags return a clean version and optional numeric versionCode', () => {
  const { parseTag, selectRelease } = server();
  assert.deepEqual(plain(parseTag('android-v2.2.1-code46')), { version: '2.2.1', versionCode: 46 });
  assert.deepEqual(plain(parseTag('android-v2.0.5')), { version: '2.0.5' });
  assert.deepEqual(plain(parseTag('android-v2.2-code45')), { version: '2.2', versionCode: 45 });
  const result = selectRelease([release()]);
  assert.equal(result.version, '2.2.1'); assert.equal(result.versionCode, 46);
  assert.equal(result.fallback, false); assert.equal(result.url, release().assets[0].browser_download_url);
});

test('malformed, prerelease-style and non-Android tags are rejected', () => {
  const { parseTag } = server();
  for (const tag of [null, 42, {}, '', 'web-v2.2.1', 'android-v2', 'android-v2..1',
    'android-v2.2.1-rc1', 'android-v2.2.1-code', 'android-v2.2.1-code0', 'android-v2.2.1-code-1',
    'android-v2.2.1-code46-extra', 'android-v2.2.1\n', 'android-v02.2.1', 'android-v2.2.1-code9007199254740992']) {
    assert.equal(parseTag(tag), null, String(tag));
  }
});

test('selection rejects drafts, prereleases, unpublished releases and wrong assets', () => {
  const s = server(), valid = release(), asset = valid.assets[0];
  const bad = [null, {}, { ...valid, draft: true }, { ...valid, prerelease: true },
    { ...valid, published_at: null }, { ...valid, published_at: 'not-a-date' },
    { ...valid, published_at: '2027-01-01T00:00:00Z' }, { ...valid, tag_name: 'web-v2.2.1' },
    { ...valid, tag_name: 'android-v2.2.1-rc1' }, { ...valid, assets: {} },
    ...[{ name: 'release.json' }, { name: 'Salary2.0.5.apk' }, { name: 'Salary2.2.1.aab' },
      { state: 'new' }, { content_type: 'text/plain' }, { size: -1 },
      { browser_download_url: 'https://example.org/Salary2.2.1.apk' },
      { browser_download_url: `${repoUrl}/releases/download/android-v2.0.5/Salary2.0.5.apk` },
      { browser_download_url: `${asset.browser_download_url}?redirect=elsewhere` },
    ].map(over => ({ ...valid, assets: [{ ...asset, ...over }] }))];
  for (const item of bad) assert.throws(() => s.selectRelease([item]));
  assert.equal(s.selectRelease([...bad, valid]).url, asset.browser_download_url);
  for (const malformed of [null, {}, 'unavailable']) assert.throws(() => s.selectRelease(malformed));
});

test('listing order cannot select an older version or lower version code', () => {
  const s = server(), older = release('android-v2.0.5', '2.0.5');
  older.published_at = '2026-10-31T00:00:00Z';
  assert.equal(s.selectRelease([older, release('android-v2.2.1-code45'), release()]).versionCode, 46);
  assert.equal(s.selectRelease([release(), release('android-v2.10.0', '2.10.0')]).version, '2.10.0');
});

for (const [name, fetcher] of [
  ['offline', async () => { throw Error('offline'); }],
  ['HTTP failure', async () => ({ ok: false })],
  ['invalid JSON', async () => ({ ok: true, json: async () => { throw Error('json'); } })],
  ['invalid body', async () => json({ message: 'rate limited' })],
  ['no eligible release', async () => json([{ ...release(), draft: true }])],
]) test(`unavailable release information uses a neutral safe fallback: ${name}`, async () => {
  const s = server(), result = await s.latestRelease(fetcher);
  assert.deepEqual(plain(result), plain(s.FALLBACK));
  assert.equal(result.url, `${repoUrl}/releases`); assert.equal(result.version, '');
  assert.equal(result.name, ''); assert.equal(result.size, 0); assert.equal(result.fallback, true);
});

test('an expired cache is not advertised after an outage, and lookup can recover', async () => {
  const s = server();
  assert.equal((await s.latestRelease(async () => json([release()]))).version, '2.2.1');
  s.advance(300001);
  assert.equal((await s.latestRelease(async () => { throw Error('offline'); })).fallback, true);
  assert.equal((await s.latestRelease(async () => json([release('android-v2.3.0-code49', '2.3.0')]))).version, '2.3.0');
});

// Exercise the real /api/download handler with isolated dependencies; never send count or production requests.
async function download(fetcher, method = 'GET') {
  const s = server(), headers = {}; let status, target;
  const res = { setHeader(k, v) { headers[k] = v; }, redirect(code, url) { status = code; target = url; } };
  const context = vm.createContext({ module: { exports: {} }, require(path) {
    if (path.endsWith('android-release.cjs')) return { latestRelease: () => s.latestRelease(fetcher) };
    if (path.endsWith('download-measurement.cjs')) return { browserId: () => 'synthetic', recordDownload: async () => false };
    if (path.endsWith('download-count.cjs')) return { countDownload: async () => false };
    throw Error(`Unexpected dependency ${path}`);
  } });
  vm.runInContext(source('../api/download.js'), context);
  await context.module.exports({ method }, res);
  return { status, target, headers };
}

test('real download handler selects only the eligible public Android APK', async () => {
  const valid = release();
  const list = [{ ...release('android-v9.0.0', '9.0.0'), draft: true },
    { ...release('android-v8.0.0', '8.0.0'), prerelease: true },
    { ...release('android-v7.0.0', '7.0.0'), published_at: null },
    release('web-v6.0.0', '6.0.0'), { ...release('android-v5.0.0', '5.0.0'), assets: [{ name: 'web.zip' }] }, valid];
  for (const method of ['GET', 'HEAD']) {
    const result = await download(async () => json(list), method);
    assert.equal(result.status, 302); assert.equal(result.target, valid.assets[0].browser_download_url);
    assert.equal(result.headers['Cache-Control'], 'no-store');
  }
});

test('real download handler outage redirects to the official releases page, not an obsolete APK', async () => {
  const result = await download(async () => { throw Error('offline'); });
  assert.equal(result.status, 302); assert.equal(result.target, `${repoUrl}/releases`);
});

function sheet(fetcher) {
  const label = { textContent: '' }, progress = { textContent: '' }, link = { href: '/api/download' };
  const el = { isConnected: true, classList: { add() {} },
    set innerHTML(html) { label.textContent = /data-release[^>]*>([^<]*)</.exec(html)[1]; },
    querySelectorAll: () => [], querySelector: selector => selector.includes('apk-download') ? link : selector === '[data-release]' ? label : progress };
  const context = vm.createContext({ KisUi: { ICON: { close: '' }, sheet(id, draw) { draw(el, () => {}); } },
    navigator: { userAgent: 'Synthetic Android' }, AbortSignal, fetch: fetcher });
  vm.runInContext(source('../web/kis/download.js'), context); context.KisDownload.open();
  return { label, progress, link, el };
}
const settle = () => new Promise(resolve => setImmediate(resolve));

test('download sheet shows checking until verified metadata arrives and keeps the stable endpoint', async () => {
  let resolve; const pending = new Promise(r => { resolve = r; }); const ui = sheet(() => pending);
  assert.equal(ui.label.textContent, 'בודק גרסה…'); assert.equal(ui.link.href, '/api/download');
  resolve(json({ version: '2.2.1', versionCode: 46, size: 20971520, fallback: false })); await settle();
  assert.equal(ui.label.textContent, 'גרסה 2.2.1 · 20.0 MB'); assert.equal(ui.link.href, '/api/download');
});

test('download sheet supports older API code suffixes without exposing them as version labels', async () => {
  const ui = sheet(async () => json({ version: '2.2.1-code46', size: 20971520 })); await settle();
  assert.equal(ui.label.textContent, 'גרסה 2.2.1 · 20.0 MB');
});

for (const [name, fetcher] of [
  ['network error', async () => { throw Error('offline'); }],
  ['HTTP error', async () => ({ ok: false })],
  ['JSON error', async () => ({ ok: true, json: async () => { throw Error('bad json'); } })],
  ['null body', async () => json(null)],
  ['malformed version', async () => json({ version: '2.2.1-code46-extra' })],
  ['old fallback response', async () => json({ version: '2.0.5', size: 19485124, fallback: true })],
  ['neutral fallback response', async () => json(server().FALLBACK)],
]) test(`download sheet uses neutral wording and a safe link: ${name}`, async () => {
  const ui = sheet(fetcher); await settle();
  assert.match(ui.label.textContent, /לא ניתן לבדוק את הגרסה/); assert.doesNotMatch(ui.label.textContent, /\d+\.\d+|בודק גרסה/);
  assert.equal(ui.link.href, `${repoUrl}/releases`);
  ui.link.onclick(); assert.equal(ui.progress.textContent, 'עמוד ההורדות הרשמי נפתח בדפדפן.');
});

test('missing or invalid size never produces NaN or a fabricated file size', async () => {
  for (const size of [undefined, null, -1, '20000000']) {
    const ui = sheet(async () => json({ version: '2.2.1', size })); await settle();
    assert.equal(ui.label.textContent, 'גרסה 2.2.1');
  }
});

test('late metadata does not update a closed sheet', async () => {
  let resolve; const pending = new Promise(r => { resolve = r; }); const ui = sheet(() => pending);
  ui.el.isConnected = false; resolve(json({ version: '2.2.1', size: 1 })); await settle();
  assert.equal(ui.label.textContent, 'בודק גרסה…');
});
