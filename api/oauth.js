'use strict';
// OAuth 2.1 for personal AI assistants (lib/server/assistant.cjs): metadata, client registration, the person's
// approval from /connect, and tokens. Reached through the rewrites in vercel.json.
const A = require('../lib/server/assistant.cjs');
module.exports = async function handler(req, res) {
  res.setHeader('Cache-Control', 'no-store');
  res.setHeader('X-Robots-Tag', 'noindex');
  if (req.method === 'OPTIONS') { res.setHeader('Access-Control-Allow-Headers', 'content-type, authorization'); return res.status(204).end(); }
  const q = req.query || {}, body = req.body && typeof req.body === 'object' ? req.body : {};
  try {
    let out;
    if (q.meta === 'resource' || q.meta === 'server') out = [200, A.metadata(req, q.meta)];
    else if (q.op === 'client' && req.method === 'GET') out = A.client(process.env, q);
    else if (req.method !== 'POST') out = [405, { error: 'method' }];
    else if (q.op === 'register') out = A.register(process.env, body);
    else if (q.op === 'grant') out = await A.grant(process.env, body);
    else if (q.op === 'token') out = await A.token(process.env, body);
    else out = [404, { error: 'not_found' }];
    return res.status(out[0]).json(out[1]);
  } catch (e) {
    console.warn('oauth failed', e.message);
    return res.status(e.status === 503 ? 503 : 500).json({ error: e.status === 503 ? 'not_configured' : 'server_error' });
  }
};
