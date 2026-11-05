// X-1: "Access-Control-Allow-Origin: *" is sent only on the public MCP surface that third-party assistants call from
// their own origin: /mcp, /.well-known/* (OAuth metadata) and the advertised /oauth/register and /oauth/token. The app,
// its /api/* functions (config, admin, the /connect approval grant, downloads) and its files are same-origin only.
const { test } = require('node:test'), assert = require('node:assert/strict');
const fs = require('node:fs'), path = require('node:path');
const ROOT = path.join(__dirname, '..');
const config = JSON.parse(fs.readFileSync(path.join(ROOT, 'vercel.json'), 'utf8'));
// Vercel `source` patterns used here: literal segments plus "(…)" regex groups, matched against the whole request path.
const matches = (source, p) => new RegExp('^' + source.replace(/[.](?![*])/g, '\\.') + '$').test(p);
const headersFor = p => Object.fromEntries(config.headers.filter(h => matches(h.source, p)).flatMap(h => h.headers.map(x => [x.key.toLowerCase(), x.value])));
const ACAO = 'access-control-allow-origin';

test('X-1: the MCP and OAuth discovery/registration/token paths allow any origin', () => {
  for (const p of ['/mcp', '/.well-known/oauth-protected-resource', '/.well-known/oauth-protected-resource/mcp',
    '/.well-known/oauth-authorization-server', '/.well-known/openid-configuration', '/oauth/register', '/oauth/token']) {
    assert.equal(headersFor(p)[ACAO], '*', p);
  }
});

test('X-1: the web app, its API functions and its files get no wildcard CORS', () => {
  for (const p of ['/', '/index.html', '/sw.js', '/build-info.json', '/web/kis/app.js', '/web/kis/kis.css', '/connect', '/connect.html',
    '/admin', '/api/config', '/api/admin', '/api/oauth', '/api/mcp', '/api/download', '/api/android-release', '/api/ai',
    '/oauth/grant', '/oauth/client', '/mcpx', '/mcp/x', '/x/.well-known/a']) {
    assert.equal(headersFor(p)[ACAO], undefined, p);
  }
  // The other security headers still cover every path.
  for (const p of ['/', '/mcp', '/api/config']) assert.equal(headersFor(p)['cross-origin-opener-policy'], 'same-origin-allow-popups', p);
});

test('X-1: every public rewrite into the MCP/OAuth functions that assistants call keeps CORS; nothing else is rewritten there', () => {
  for (const r of config.rewrites) {
    const assistant = r.destination === '/api/mcp' || /^\/api\/oauth\?(meta=|op=(register|token)$)/.test(r.destination);
    assert.ok(assistant, `unexpected rewrite ${r.source} → ${r.destination}`);
    assert.equal(headersFor(r.source)[ACAO], '*', r.source);
  }
  // The metadata advertises exactly these endpoints (lib/server/assistant.cjs).
  const A = require('../lib/server/assistant.cjs');
  const meta = A.metadata({ headers: { host: 'example.invalid', 'x-forwarded-proto': 'https' } }, 'server');
  for (const key of ['token_endpoint', 'registration_endpoint']) assert.equal(headersFor(new URL(meta[key]).pathname)[ACAO], '*', key);
});

test('X-1: the app itself only calls its own origin with relative URLs (it does not rely on CORS)', () => {
  const files = [];
  (function walk(d) { for (const f of fs.readdirSync(d, { withFileTypes: true })) {
    const p = path.join(d, f.name); if (f.isDirectory()) walk(p); else if (p.endsWith('.js')) files.push(p); } })(path.join(ROOT, 'web'));
  for (const f of files) {
    const src = fs.readFileSync(f, 'utf8');
    assert.doesNotMatch(src, /fetch\(\s*['"`]https?:\/\/[^'"`]*vercel\.app/, path.relative(ROOT, f));
  }
});
