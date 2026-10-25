'use strict';
// The MCP endpoint (/mcp) for personal AI assistants: stateless Streamable HTTP with JSON answers.
const A = require('../lib/server/assistant.cjs');
module.exports = async function handler(req, res) {
  res.setHeader('Cache-Control', 'no-store');
  res.setHeader('Access-Control-Expose-Headers', 'WWW-Authenticate, Mcp-Session-Id');
  if (req.method === 'OPTIONS') { res.setHeader('Access-Control-Allow-Headers', 'content-type, authorization, mcp-protocol-version, mcp-session-id'); return res.status(204).end(); }
  if (req.method !== 'POST') { res.setHeader('Allow', 'POST'); return res.status(405).json({ error: 'Use POST' }); }
  try {
    const [status, body, headers = {}] = await A.mcp(process.env, req, req.body);
    for (const [k, v] of Object.entries(headers)) res.setHeader(k, v);
    return body === null ? res.status(status).end() : res.status(status).json(body);
  } catch (e) {
    console.warn('mcp failed', e.message);
    return res.status(e.status === 503 ? 503 : 500).json({ jsonrpc: '2.0', id: null, error: { code: -32603, message: 'Server error' } });
  }
};
