// 2.3.0 (audit M-23): assistant refresh tokens are bound to their client, rotate on every use, and a reused one cuts the
// whole grant off. Synthetic Firebase session and Firestore (an in-memory fetcher), no network.
const { test } = require('node:test'), assert = require('node:assert/strict'), crypto = require('node:crypto');
const A = require('../lib/server/assistant.cjs');
const env = { MCP_SECRET: 'synthetic-test-secret-' + 'r'.repeat(32), FIREBASE_WEB_API_KEY: 'synthetic-key', FIREBASE_PROJECT_ID: 'synthetic' };
const REDIRECT = 'https://assistant.example/cb';

/** Firestore REST + securetoken, enough for grant() and token(): documents keep their raw fields and a revision. */
function fakeFirebase() {
  const docs = new Map(); let rev = 0;
  const fetcher = async (url, init = {}) => {
    const res = (status, body) => ({ ok: status < 300, status, json: async () => body, text: async () => JSON.stringify(body), clone() { return this; } });
    if (url.includes('/v1/token')) return res(200, { id_token: 'synthetic-id', user_id: 'synthetic-user', expires_in: '3600' });
    if (url.endsWith('/documents:commit')) {
      const w = JSON.parse(init.body).writes[0], name = w.update.name, cur = docs.get(name), pre = w.currentDocument;
      if (pre && ((pre.exists === false && cur) || (pre.updateTime && (!cur || cur.updateTime !== pre.updateTime)))) return res(409, {});
      docs.set(name, { fields: w.update.fields, updateTime: 'rev-' + ++rev }); return res(200, {});
    }
    const name = url.slice(url.indexOf('projects/'));
    const d = docs.get(name); return d ? res(200, { name, ...d }) : res(404, {});
  };
  const grantDoc = () => [...docs].find(([k]) => k.includes('/assistants/'))[1];
  return { docs, fetcher, grantDoc };
}
const field = (doc, k) => { const v = doc.fields[k]; return v && (v.stringValue ?? v.booleanValue ?? (v.nullValue === null ? null : undefined)); };

async function connect(fb) {
  const [, c] = A.register(env, { redirect_uris: [REDIRECT], client_name: 'Synthetic' });
  const verifier = 'v'.repeat(50), challenge = crypto.createHash('sha256').update(verifier).digest('base64url');
  const [s, g] = await A.grant(env, { refreshToken: 'synthetic-firebase-refresh', scopes: ['shifts.read'], duration: 'always', tz: 'UTC',
    client_id: c.client_id, redirect_uri: REDIRECT, code_challenge: challenge }, fb.fetcher);
  assert.equal(s, 200);
  const code = new URL(g.redirect).searchParams.get('code');
  const [ts, t] = await A.token(env, { grant_type: 'authorization_code', code, client_id: c.client_id, redirect_uri: REDIRECT, code_verifier: verifier }, fb.fetcher);
  assert.equal(ts, 200);
  return { client: c.client_id, t };
}
const refresh = (fb, rt, client_id) => A.token(env, { grant_type: 'refresh_token', refresh_token: rt, client_id }, fb.fetcher);

test('the refresh token carries its client and is refused for another client (or none), without cutting the owner off', async () => {
  const fb = fakeFirebase(), { client, t } = await connect(fb);
  const [, other] = A.register(env, { redirect_uris: ['https://other.example/cb'] });
  assert.equal(A.open(env, 'rt', t.refresh_token).c, client);
  assert.equal(A.open(env, 'at', t.access_token).c, undefined, 'the access token does not need the client');
  assert.deepEqual(await refresh(fb, t.refresh_token, other.client_id), [400, { error: 'invalid_grant' }]);
  assert.deepEqual(await refresh(fb, t.refresh_token, undefined), [400, { error: 'invalid_grant' }]);
  assert.equal(field(fb.grantDoc(), 'revoked'), undefined);
  const [s] = await refresh(fb, t.refresh_token, client);
  assert.equal(s, 200, 'the right client still refreshes');
});

test('every refresh rotates: a new refresh token, and the grant remembers only the newest', async () => {
  const fb = fakeFirebase(), { client, t } = await connect(fb);
  const [s1, t1] = await refresh(fb, t.refresh_token, client);
  assert.equal(s1, 200); assert.notEqual(t1.refresh_token, t.refresh_token);
  assert.equal(field(fb.grantDoc(), 'rt'), A.open(env, 'rt', t1.refresh_token).j);
  const [s2, t2] = await refresh(fb, t1.refresh_token, client);
  assert.equal(s2, 200);
  const [s3] = await refresh(fb, t2.refresh_token, client);
  assert.equal(s3, 200);
  assert.equal(field(fb.grantDoc(), 'codeUsed'), true, 'the code stays used');
});

test('reusing an old refresh token revokes the whole grant: the newest refresh token and the access token stop working', async () => {
  const fb = fakeFirebase(), { client, t } = await connect(fb);
  const [, t1] = await refresh(fb, t.refresh_token, client);
  assert.deepEqual(await refresh(fb, t.refresh_token, client), [400, { error: 'invalid_grant' }], 'the rotated token is reuse');
  const doc = fb.grantDoc();
  assert.equal(field(doc, 'revoked'), 'refresh_reuse');
  assert.ok(Date.parse(field(doc, 'until')) <= Date.now(), 'ended, as in the app\'s own ניתוק');
  assert.deepEqual(await refresh(fb, t1.refresh_token, client), [400, { error: 'invalid_grant' }], 'the family is revoked');
  const [ms] = await A.mcp(env, { headers: { authorization: 'Bearer ' + t1.access_token, host: 'x' } }, { jsonrpc: '2.0', id: 1, method: 'ping' }, fb.fetcher);
  assert.equal(ms, 401);
});

test('a refresh token issued before 2.3.0 (no id, no client) is accepted once from a real client, then bound and rotated', async () => {
  const fb = fakeFirebase(), { client, t } = await connect(fb);
  // Simulate a grant connected before 2.3.0: its document has no current id and its refresh token neither.
  const name = [...fb.docs.keys()].find(k => k.includes('/assistants/')); delete fb.docs.get(name).fields.rt;
  const { c, j, x, ...old } = A.open(env, 'rt', t.refresh_token), legacy = A.seal(env, 'rt', old);
  assert.deepEqual(await refresh(fb, legacy, 'not-a-client'), [400, { error: 'invalid_grant' }]);
  const [s, t1] = await refresh(fb, legacy, client);
  assert.equal(s, 200); assert.equal(A.open(env, 'rt', t1.refresh_token).c, client);
  assert.deepEqual(await refresh(fb, legacy, client), [400, { error: 'invalid_grant' }], 'a second use of the old one is reuse');
  assert.equal(field(fb.grantDoc(), 'revoked'), 'refresh_reuse');
});

test('one code, one exchange still holds', async () => {
  const fb = fakeFirebase();
  const [, c] = A.register(env, { redirect_uris: [REDIRECT] });
  const verifier = 'w'.repeat(50), challenge = crypto.createHash('sha256').update(verifier).digest('base64url');
  const [, g] = await A.grant(env, { refreshToken: 'synthetic-firebase-refresh', duration: 'day', tz: 'UTC', client_id: c.client_id, redirect_uri: REDIRECT, code_challenge: challenge }, fb.fetcher);
  const body = { grant_type: 'authorization_code', code: new URL(g.redirect).searchParams.get('code'), client_id: c.client_id, redirect_uri: REDIRECT, code_verifier: verifier };
  assert.equal((await A.token(env, body, fb.fetcher))[0], 200);
  assert.deepEqual(await A.token(env, body, fb.fetcher), [400, { error: 'invalid_grant' }]);
});
