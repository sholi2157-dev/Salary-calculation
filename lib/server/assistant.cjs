'use strict';
/*
 * Personal AI assistants (ChatGPT, Claude, Grok and others) reach a person's own shifts through MCP, with OAuth 2.1.
 * - The person signs in with Google on /connect and picks what the assistant may do (read, add, edit, delete) and for
 *   how long. That choice is a grant: users/{uid}/assistants/{grantId}. Removing the grant cuts the assistant off.
 * - The server keeps nothing of its own. Every token it hands out is sealed (AES-256-GCM with MCP_SECRET) and carries
 *   the person's Firebase session, so each request reads and writes Firestore as that person, and the same security
 *   rules as the app and the website decide what is reachable: never anyone else's data.
 * - Writes use the sync format (records_v1, see web/sync/protocol.js), so the app and the website receive them like a
 *   change from another device. Every write is logged in users/{uid}/assistant_log with the item before and after.
 */
const crypto = require('node:crypto');
globalThis.WorkRuntime ||= require('../../web/runtime.js');
globalThis.WorkCategories ||= require('../../web/categories.js');
globalThis.WorkTransfer ||= require('../../web/transfer.js');
const P = require('../../web/sync/protocol.js');
const { amount } = require('../../web/v2/pay.js');
const { FIREBASE_PROJECT } = require('./admin-dashboard.cjs');

const SCOPES = ['shifts.read', 'shifts.add', 'shifts.edit', 'shifts.delete'];
const DURATIONS = { hour: 3600, day: 86400, month: 30 * 86400, always: 0 };
const VERSION = '2.1';

// ---------- sealed tokens ----------
function key(env) {
  const secret = env.MCP_SECRET;
  if (!secret || secret.length < 32) throw Object.assign(Error('MCP_SECRET is not set'), { status: 503 });
  return crypto.createHash('sha256').update('kis-assistant|' + secret).digest();
}
/** kind: client, code, at (access), rt (refresh), key (personal key). ttl in seconds; 0 = no expiry of its own. */
function seal(env, kind, data, ttl = 0, now = Date.now()) {
  const iv = crypto.randomBytes(12), c = crypto.createCipheriv('aes-256-gcm', key(env), iv);
  c.setAAD(Buffer.from(kind));
  const body = Buffer.concat([c.update(JSON.stringify({ ...data, x: ttl ? Math.floor(now / 1000) + ttl : 0 })), c.final()]);
  return 'kis_' + kind + '_' + Buffer.concat([iv, c.getAuthTag(), body]).toString('base64url');
}
function open(env, kind, token, now = Date.now()) {
  try {
    const prefix = 'kis_' + kind + '_';
    if (typeof token !== 'string' || !token.startsWith(prefix) || token.length > 8000) return null;
    const raw = Buffer.from(token.slice(prefix.length), 'base64url');
    const d = crypto.createDecipheriv('aes-256-gcm', key(env), raw.subarray(0, 12));
    d.setAAD(Buffer.from(kind)); d.setAuthTag(raw.subarray(12, 28));
    const data = JSON.parse(Buffer.concat([d.update(raw.subarray(28)), d.final()]).toString('utf8'));
    if (data.x && data.x <= now / 1000) return null;
    return data;
  } catch (e) { if (e.status) throw e; return null; }
}

// ---------- Firebase as the person ----------
function hosts(env) {
  const fs = env.FIRESTORE_EMULATOR_HOST, auth = env.FIREBASE_AUTH_EMULATOR_HOST;
  return {
    firestore: (fs ? 'http://' + fs : 'https://firestore.googleapis.com') + '/v1/projects/' + (env.FIREBASE_PROJECT_ID || FIREBASE_PROJECT) + '/databases/(default)/documents',
    token: (auth ? 'http://' + auth + '/securetoken.googleapis.com' : 'https://securetoken.googleapis.com') + '/v1/token',
  };
}
function apiKey(env) { return env.FIREBASE_WEB_API_KEY || require('../../app/src/debug/google-services.json').client[0].api_key[0].current_key; }

const sessions = new Map();
/** A fresh Firebase ID token from the person's refresh token (kept for a while per warm function). */
async function idTokenFor(env, refreshToken, fetcher) {
  const hit = sessions.get(refreshToken);
  if (hit && hit.until > Date.now()) return hit;
  const r = await fetcher(hosts(env).token + '?key=' + encodeURIComponent(apiKey(env)), {
    method: 'POST', headers: { 'content-type': 'application/x-www-form-urlencoded' },
    body: new URLSearchParams({ grant_type: 'refresh_token', refresh_token: refreshToken }), signal: AbortSignal.timeout(8000) });
  if (!r.ok) throw Object.assign(Error('Session ended'), { status: 401 });
  const j = await r.json(), s = { idToken: j.id_token, uid: j.user_id, until: Date.now() + (Math.min(+j.expires_in || 3600, 3600) - 300) * 1000 };
  if (sessions.size > 500) sessions.clear();
  sessions.set(refreshToken, s);
  return s;
}

const toValue = v => v === null || v === undefined ? { nullValue: null } : typeof v === 'boolean' ? { booleanValue: v }
  : typeof v === 'number' ? (Number.isInteger(v) ? { integerValue: String(v) } : { doubleValue: v })
  : Array.isArray(v) ? { arrayValue: { values: v.map(toValue) } } : typeof v === 'object' ? { mapValue: { fields: toFields(v) } } : { stringValue: String(v) };
const toFields = o => Object.fromEntries(Object.entries(o).map(([k, v]) => [k, toValue(v)]));
function fromValue(v) {
  if ('nullValue' in v) return null;
  if ('booleanValue' in v) return v.booleanValue;
  if ('integerValue' in v) return Number(v.integerValue);
  if ('doubleValue' in v) return v.doubleValue;
  if ('timestampValue' in v) return v.timestampValue;
  if ('arrayValue' in v) return (v.arrayValue.values || []).map(fromValue);
  if ('mapValue' in v) return fromFields(v.mapValue.fields || {});
  return v.stringValue;
}
const fromFields = f => Object.fromEntries(Object.entries(f || {}).map(([k, v]) => [k, fromValue(v)]));

function store(env, session, fetcher) {
  const base = hosts(env).firestore, user = base + '/users/' + session.uid;
  const name = path => 'projects/' + (env.FIREBASE_PROJECT_ID || FIREBASE_PROJECT) + '/databases/(default)/documents/users/' + session.uid + '/' + path;
  const call = async (url, init = {}) => {
    const r = await fetcher(url, { ...init, headers: { authorization: 'Bearer ' + session.idToken, 'content-type': 'application/json' }, signal: AbortSignal.timeout(10000) });
    if (r.status === 404) return null;
    if (r.status === 409 || r.status === 412 || (r.status === 400 && /FAILED_PRECONDITION|ALREADY_EXISTS/.test(await r.clone().text()))) return { conflict: true };
    if (!r.ok) throw Object.assign(Error('Firestore ' + r.status), { status: r.status === 403 ? 403 : 502 });
    return r.json();
  };
  const commit = (write, precondition) => call(base.replace(/\/documents$/, '/documents:commit'), { method: 'POST',
    body: JSON.stringify({ writes: [{ ...write, ...(precondition ? { currentDocument: precondition } : {}) }] }) });
  return {
    async get(path) { const d = await call(user + '/' + path); return d && !d.conflict ? { data: fromFields(d.fields), updateTime: d.updateTime } : null; },
    async list(path) {
      const out = []; let page = '';
      do {
        const d = await call(user + '/' + path + '?pageSize=300' + (page ? '&pageToken=' + encodeURIComponent(page) : ''));
        for (const doc of d?.documents || []) out.push({ id: doc.name.split('/').pop(), data: fromFields(doc.fields), updateTime: doc.updateTime });
        page = d?.nextPageToken || '';
      } while (page && out.length < 50000);
      return out;
    },
    /** Writes the document (with updatedAt = server time when asked); precondition false = must not exist yet. */
    async put(path, data, { exists, updateTime, stamp } = {}) {
      const r = await commit({ update: { name: name(path), fields: toFields(data) }, ...(stamp ? { updateTransforms: [{ fieldPath: stamp, setToServerValue: 'REQUEST_TIME' }] } : {}) },
        exists === false ? { exists: false } : updateTime ? { updateTime } : exists === true ? { exists: true } : null);
      return !r?.conflict;
    },
    async remove(path) { return !(await commit({ delete: name(path) }))?.conflict; },
  };
}

// ---------- the person's data, read from records_v1 ----------
async function load(db) {
  const docs = await db.list('records_v1'), entries = [], categories = [], prefs = {}, records = {};
  for (const d of docs) {
    try {
      if (d.data.schema !== 1) continue;
      const r = P.validate({ syncId: d.id, type: d.data.type, version: d.data.version, operation: d.data.operation, deleted: d.data.deleted, payload: d.data.payload ?? null });
      records[d.id] = { ...r, updateTime: d.updateTime };
      if (r.deleted) continue;
      const item = P.decode(r.type, r.payload);
      if (r.type === 'entry') entries.push({ ...item, id: r.syncId });
      else if (r.type === 'category') categories.push(item);
      else if (r.type === 'preference') prefs[item.name] = item.value;
    } catch { /* an unreadable record is skipped, never guessed */ }
  }
  return { entries, categories, prefs, records };
}

function zoneParts(ms, tz) {
  const p = Object.fromEntries(new Intl.DateTimeFormat('en-CA', { timeZone: tz, year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit', hourCycle: 'h23' })
    .formatToParts(new Date(ms)).map(x => [x.type, x.value]));
  return { date: p.year + '-' + p.month + '-' + p.day, time: p.hour + ':' + p.minute };
}
/** Milliseconds for a local date and time in the person's time zone. */
function zonedMs(date, time, tz) {
  const [y, m, d] = date.split('-').map(Number), [h, mi] = time.split(':').map(Number);
  let guess = Date.UTC(y, m - 1, d, h, mi);
  for (let i = 0; i < 2; i++) {
    const p = zoneParts(guess, tz), [py, pm, pd] = p.date.split('-').map(Number), [ph, pmi] = p.time.split(':').map(Number);
    guess += Date.UTC(y, m - 1, d, h, mi) - Date.UTC(py, pm - 1, pd, ph, pmi);
  }
  return guess;
}
const DATE = /^\d{4}-\d{2}-\d{2}$/, TIME = /^([01]\d|2[0-3]):[0-5]\d$/;
const currencyOf = (s, place) => s.prefs['categoryCurrency:' + place] || s.prefs.default_currency || '₪';

function view(e, tz) {
  const job = e.kind === 'job';
  return { id: e.id, date: zoneParts(e.date, tz).date, workplace: e.category, kind: job ? 'job' : 'shift',
    ...(e.isTimeRange ? { start: e.startTime, end: e.endTime } : {}), hours: Math.round(e.hours * 100) / 100,
    ...(job ? {} : { rate: e.payKnown === false ? null : e.hourlyRate }), total: e.payKnown === false ? null : e.totalEarnings,
    currency: e.currency, paid: !!e.isPaid, ...(e.notes ? { notes: e.notes } : {}), ...(e.isGroupShift ? { team: true } : {}), ...(e.bonus ? { bonus: e.bonus } : {}) };
}
function inRange(e, a, tz) {
  const d = zoneParts(e.date, tz).date;
  return (!a.from || d >= a.from) && (!a.to || d <= a.to) && (!a.workplace || e.category === a.workplace);
}
function summary(s, a, tz) {
  const rows = s.entries.filter(e => inRange(e, a, tz)), totals = {}, unpaid = {}, places = {};
  let hours = 0, missing = 0;
  for (const e of rows) {
    hours += e.hours || 0;
    const p = places[e.category] ||= { workplace: e.category, shifts: 0, hours: 0, totals: {} };
    p.shifts++; p.hours += e.hours || 0;
    if (e.payKnown === false) { missing++; continue; }
    totals[e.currency] = Math.round(((totals[e.currency] || 0) + e.totalEarnings) * 100) / 100;
    p.totals[e.currency] = Math.round(((p.totals[e.currency] || 0) + e.totalEarnings) * 100) / 100;
    if (!e.isPaid) unpaid[e.currency] = Math.round(((unpaid[e.currency] || 0) + e.totalEarnings) * 100) / 100;
  }
  return { from: a.from || null, to: a.to || null, shifts: rows.length, hours: Math.round(hours * 100) / 100, totals, unpaid,
    ...(missing ? { withoutKnownPay: missing } : {}), byWorkplace: Object.values(places).map(p => ({ ...p, hours: Math.round(p.hours * 100) / 100 })) };
}

// ---------- tools ----------
const range = { from: { type: 'string', description: 'First day, YYYY-MM-DD (inclusive)' }, to: { type: 'string', description: 'Last day, YYYY-MM-DD (inclusive)' },
  workplace: { type: 'string', description: 'Only this workplace (exact name from list_workplaces)' } };
const shiftFields = {
  date: { type: 'string', description: 'Day of the shift, YYYY-MM-DD' }, workplace: { type: 'string', description: 'Exact workplace name from list_workplaces' },
  start: { type: 'string', description: 'Start time HH:MM (24h). Give start and end, or hours.' }, end: { type: 'string', description: 'End time HH:MM; earlier than start means past midnight' },
  hours: { type: 'number', description: 'Worked hours when no start/end times' }, rate: { type: 'number', description: 'Hourly rate; default is the workplace rate' },
  paid: { type: 'boolean', description: 'Already paid' }, notes: { type: 'string', description: 'Short note' } };
const TOOLS = [
  { name: 'list_workplaces', scope: 'shifts.read', title: 'Workplaces', description: 'The workplaces with their hourly rate and currency.', inputSchema: { type: 'object', properties: {} }, annotations: { readOnlyHint: true } },
  { name: 'list_shifts', scope: 'shifts.read', title: 'List shifts', description: 'Shifts and jobs in a date range (the person\'s own time zone), newest first. Amounts are exactly as saved in the app.',
    inputSchema: { type: 'object', properties: { ...range, limit: { type: 'integer', minimum: 1, maximum: 500, description: 'At most this many (default 100)' } } }, annotations: { readOnlyHint: true } },
  { name: 'get_summary', scope: 'shifts.read', title: 'Summary', description: 'Totals for a date range: shifts, hours, earnings per currency, unpaid amounts and a breakdown by workplace.',
    inputSchema: { type: 'object', properties: range }, annotations: { readOnlyHint: true } },
  { name: 'add_shift', scope: 'shifts.add', title: 'Add a shift', description: 'Adds one shift to the person\'s app and website. The total is hours × rate, as the app computes it.',
    inputSchema: { type: 'object', properties: shiftFields, required: ['date', 'workplace'] }, annotations: { readOnlyHint: false, destructiveHint: false } },
  { name: 'update_shift', scope: 'shifts.edit', title: 'Edit a shift', description: 'Changes fields of one shift (id from list_shifts). Team shifts and jobs only take notes and paid.',
    inputSchema: { type: 'object', properties: { id: { type: 'string' }, ...shiftFields }, required: ['id'] }, annotations: { readOnlyHint: false, destructiveHint: true } },
  { name: 'delete_shift', scope: 'shifts.delete', title: 'Delete a shift', description: 'Deletes one shift (id from list_shifts). The person can undo it in the app.',
    inputSchema: { type: 'object', properties: { id: { type: 'string' } }, required: ['id'] }, annotations: { readOnlyHint: false, destructiveHint: true } },
];
const fail = text => { throw Object.assign(Error(text), { user: true }); };

function hoursOf(a) {
  if (a.start != null || a.end != null) {
    if (!TIME.test(a.start || '') || !TIME.test(a.end || '')) fail('start and end must both be HH:MM');
    const m = t => +t.slice(0, 2) * 60 + +t.slice(3);
    let d = m(a.end) - m(a.start); if (d <= 0) d += 1440;
    return d / 60;
  }
  if (!(typeof a.hours === 'number' && a.hours > 0 && a.hours <= 24)) fail('Give start and end, or hours between 0 and 24');
  return a.hours;
}

async function write(ctx, s, syncId, type, before, after) {
  const rec = s.records[syncId], base = rec?.version || 0;
  if (base === 0 && after === null) fail('Not found');
  const payload = after === null ? null : P.encode(type, after);
  const doc = { schema: 1, type, version: base + 1, operation: syncId + ':assistant-' + crypto.randomBytes(12).toString('hex'), deleted: after === null, payload };
  P.validate({ syncId, ...doc });
  const ok = await ctx.db.put('records_v1/' + syncId, doc, base ? { updateTime: rec.updateTime, stamp: 'updatedAt' } : { exists: false, stamp: 'updatedAt' });
  if (!ok) fail('This shift just changed on another device. Read it again and retry.');
  await ctx.db.put('assistant_log/' + crypto.randomBytes(10).toString('hex'), { grant: ctx.grant.g, client: ctx.grant.n || '', tool: ctx.tool, syncId, type,
    before: before === null ? null : P.encode(type, before), after: payload, at: new Date().toISOString() }).catch(() => {});
}

async function run(ctx, name, a = {}) {
  const tool = TOOLS.find(t => t.name === name);
  if (!tool) fail('Unknown tool');
  if (!ctx.grant.s.includes(tool.scope)) fail('The person did not allow this (' + tool.scope + '). They can change it in the app: Settings → Assistants.');
  for (const k of ['from', 'to', 'date']) if (a[k] != null && !DATE.test(a[k])) fail(k + ' must be YYYY-MM-DD');
  ctx.tool = name;
  const tz = ctx.grant.z || 'Asia/Jerusalem', s = await load(ctx.db);
  if (name === 'list_workplaces') return s.categories.map(c => ({ workplace: c.name, rate: c.defaultRate, currency: currencyOf(s, c.name) }));
  if (name === 'list_shifts') return s.entries.filter(e => inRange(e, a, tz)).sort((x, y) => y.date - x.date).slice(0, Math.min(Math.max(+a.limit || 100, 1), 500)).map(e => view(e, tz));
  if (name === 'get_summary') return summary(s, a, tz);
  if (name === 'add_shift') {
    const place = s.categories.find(c => c.name === a.workplace);
    if (!place) fail('Unknown workplace. Existing: ' + s.categories.map(c => c.name).join(', '));
    const hours = hoursOf(a), rate = a.rate ?? place.defaultRate, clock = a.start != null;
    if (!(typeof rate === 'number' && rate >= 0 && rate < 100000)) fail('rate must be a number');
    const entry = { category: place.name, date: zonedMs(a.date, clock ? a.start : '12:00', tz), createdAt: Date.now(), isTimeRange: clock, startTime: clock ? a.start : null, endTime: clock ? a.end : null,
      hours, hourlyRate: rate, totalEarnings: amount(hours, rate), isPaid: !!a.paid, notes: String(a.notes || '').trim().slice(0, 500), currency: currencyOf(s, place.name),
      isGroupShift: false, employerRate: null, workerRate: null, groupWorkersJson: '', kind: 'shift', payKnown: true, bonus: 0, seriesKey: null, autoConfirmed: false };
    const id = P.randomId();
    await write(ctx, s, id, 'entry', null, entry);
    return { added: view({ ...entry, id }, tz) };
  }
  const old = s.entries.find(e => e.id === a.id);
  if (!old) fail('No shift with this id. Use list_shifts.');
  const { id, ...before } = old;
  if (name === 'delete_shift') { await write(ctx, s, id, 'entry', before, null); return { deleted: view(old, tz) }; }
  // update_shift
  const simple = old.kind !== 'job' && !old.isGroupShift;
  const touched = ['date', 'workplace', 'start', 'end', 'hours', 'rate'].filter(k => a[k] != null);
  if (!simple && touched.length) fail('Team shifts and jobs can only change notes and paid here. Open the app for the rest.');
  const next = { ...before };
  if (a.workplace != null) { if (!s.categories.some(c => c.name === a.workplace)) fail('Unknown workplace'); next.category = a.workplace; }
  if (a.start != null || a.end != null || a.hours != null) {
    const clock = a.start != null || (a.hours == null && old.isTimeRange);
    const t = clock ? { start: a.start ?? old.startTime, end: a.end ?? old.endTime } : { hours: a.hours };
    next.hours = hoursOf(t); next.isTimeRange = clock; next.startTime = clock ? t.start : null; next.endTime = clock ? t.end : null;
  }
  if (a.rate != null) { if (!(typeof a.rate === 'number' && a.rate >= 0)) fail('rate must be a number'); next.hourlyRate = a.rate; next.payKnown = true; }
  if (a.date != null || next.isTimeRange !== old.isTimeRange || next.startTime !== old.startTime)
    next.date = zonedMs(a.date || zoneParts(old.date, tz).date, next.isTimeRange ? next.startTime : '12:00', tz);
  if (a.paid != null) next.isPaid = !!a.paid;
  if (a.notes != null) next.notes = String(a.notes).trim().slice(0, 500);
  if (simple && (next.hours !== old.hours || next.hourlyRate !== old.hourlyRate) && next.payKnown !== false) next.totalEarnings = amount(next.hours, next.hourlyRate, next.bonus || 0);
  await write(ctx, s, id, 'entry', before, next);
  return { updated: view({ ...next, id }, tz) };
}

// ---------- OAuth ----------
const origin = req => (req.headers['x-forwarded-proto'] || 'https') + '://' + (req.headers['x-forwarded-host'] || req.headers.host);
function metadata(req, which) {
  const o = origin(req);
  if (which === 'resource') return { resource: o + '/mcp', authorization_servers: [o], scopes_supported: SCOPES, bearer_methods_supported: ['header'], resource_name: 'חישוב שכר' };
  return { issuer: o, authorization_endpoint: o + '/connect', token_endpoint: o + '/oauth/token', registration_endpoint: o + '/oauth/register',
    scopes_supported: SCOPES, response_types_supported: ['code'], grant_types_supported: ['authorization_code', 'refresh_token'],
    code_challenge_methods_supported: ['S256'], token_endpoint_auth_methods_supported: ['none'] };
}
function redirectOk(u) {
  try {
    const url = new URL(u);
    if (url.hash || url.username || url.password) return false;
    return url.protocol === 'https:' || (url.protocol === 'http:' && ['localhost', '127.0.0.1', '[::1]'].includes(url.hostname));
  } catch { return false; }
}
function register(env, body) {
  const uris = Array.isArray(body?.redirect_uris) ? body.redirect_uris.map(String) : [];
  if (!uris.length || uris.length > 10 || !uris.every(redirectOk)) return [400, { error: 'invalid_redirect_uri' }];
  const name = String(body.client_name || 'עוזר AI').slice(0, 80);
  return [201, { client_id: seal(env, 'client', { r: uris, n: name }), client_name: name, redirect_uris: uris, token_endpoint_auth_method: 'none',
    grant_types: ['authorization_code', 'refresh_token'], response_types: ['code'], client_id_issued_at: Math.floor(Date.now() / 1000) }];
}
// Assistants that only take a fixed client ID (Meta's Muse asks for one) instead of registering: the ID is public, so the
// return address must be on that company's own domains.
const FIXED = { 'muse': { n: 'Muse', hosts: ['meta.ai', 'meta.com', 'facebook.com', 'messenger.com', 'instagram.com', 'whatsapp.com'] } };
function fixedOk(f, redirect) {
  try {
    const url = new URL(redirect);
    return url.protocol === 'https:' && !url.hash && !url.username && !url.password
      && f.hosts.some(h => url.hostname === h || url.hostname.endsWith('.' + h));
  } catch { return false; }
}
/** The registered assistant behind [id], if [redirect] is one of its return addresses. */
function clientFor(env, id, redirect) {
  const f = Object.prototype.hasOwnProperty.call(FIXED, id) ? FIXED[id] : null;
  if (f) {
    if (fixedOk(f, redirect)) return { n: f.n };
    console.warn('fixed client', id, 'refused return address host', (() => { try { return new URL(redirect).hostname; } catch { return '?'; } })());
    return null;
  }
  const c = open(env, 'client', id);
  return c && c.r.includes(redirect) ? c : null;
}
/** For /connect: who is asking, and whether the return address is theirs. */
function client(env, q) {
  const c = clientFor(env, q.client_id, q.redirect_uri);
  if (!c) return [400, { error: 'invalid_client' }];
  return [200, { name: c.n }];
}

/** The person approved on /connect: record the grant (as them) and send the assistant a one-time code. */
async function grant(env, body, fetcher = fetch) {
  // The person's own Firebase session (from /connect, right after their Google sign-in) is the proof of who they are.
  const session = await idTokenFor(env, String(body.refreshToken || ''), fetcher).catch(() => null);
  if (!session?.uid) return [401, { error: 'signin' }];
  const scopes = SCOPES.filter(s => s === 'shifts.read' || (body.scopes || []).includes(s));
  if (!(body.duration in DURATIONS)) return [400, { error: 'duration' }];
  const tz = typeof body.tz === 'string' && body.tz.length < 64 ? body.tz : 'Asia/Jerusalem';
  try { new Intl.DateTimeFormat('en', { timeZone: tz }); } catch { return [400, { error: 'tz' }]; }
  const ttl = DURATIONS[body.duration], now = Date.now(), until = ttl ? now + ttl * 1000 : 0;
  let name, redirect = null;
  if (body.personal) name = String(body.name || 'מפתח אישי').slice(0, 60);
  else {
    const c = clientFor(env, body.client_id, body.redirect_uri);
    if (!c) return [400, { error: 'invalid_client' }];
    if (!/^[A-Za-z0-9_-]{43}$/.test(body.code_challenge || '')) return [400, { error: 'pkce' }];
    name = c.n; redirect = body.redirect_uri;
  }
  const g = crypto.randomBytes(12).toString('hex'), db = store(env, session, fetcher);
  await db.put('assistants/' + g, { name, scopes, until: until ? new Date(until).toISOString() : null, createdAt: new Date(now).toISOString(), tz, personal: !!body.personal, codeUsed: false }, { exists: false });
  const data = { u: session.uid, g, f: body.refreshToken, s: scopes, z: tz, n: name, until };
  if (body.personal) return [200, { key: seal(env, 'key', data, ttl), name }];
  const code = seal(env, 'code', { ...data, c: body.client_id, r: redirect, ch: body.code_challenge }, 300);
  const url = new URL(redirect); url.searchParams.set('code', code); if (body.state) url.searchParams.set('state', String(body.state));
  return [200, { redirect: url.toString() }];
}

async function live(env, data, fetcher) {
  if (data.until && data.until <= Date.now()) return null;
  const session = await idTokenFor(env, data.f, fetcher).catch(() => null);
  if (!session || session.uid !== data.u) return null;
  const db = store(env, session, fetcher), doc = await db.get('assistants/' + data.g);
  // Removed in the app, or its time is up (the app may shorten it): cut off.
  if (!doc || (doc.data.until && Date.parse(doc.data.until) <= Date.now())) return null;
  return { db, doc };
}

async function token(env, body, fetcher = fetch) {
  let data;
  if (body.grant_type === 'authorization_code') {
    const c = open(env, 'code', body.code);
    if (!c || c.c !== body.client_id || (body.redirect_uri && c.r !== body.redirect_uri)) return [400, { error: 'invalid_grant' }];
    const check = crypto.createHash('sha256').update(String(body.code_verifier || '')).digest('base64url');
    if (check !== c.ch) return [400, { error: 'invalid_grant' }];
    const l = await live(env, c, fetcher);
    if (!l || l.doc.data.codeUsed) return [400, { error: 'invalid_grant' }];
    // One code, one exchange.
    if (!await l.db.put('assistants/' + c.g, { ...l.doc.data, codeUsed: true }, { updateTime: l.doc.updateTime })) return [400, { error: 'invalid_grant' }];
    data = c;
  } else if (body.grant_type === 'refresh_token') {
    data = open(env, 'rt', body.refresh_token);
    if (!data || !await live(env, data, fetcher)) return [400, { error: 'invalid_grant' }];
  } else return [400, { error: 'unsupported_grant_type' }];
  const { c, r, ch, x, ...keep } = data, left = keep.until ? Math.floor((keep.until - Date.now()) / 1000) : 0;
  const expires = left ? Math.min(3600, left) : 3600;
  return [200, { access_token: seal(env, 'at', keep, expires), token_type: 'Bearer', expires_in: expires, scope: keep.s.join(' '),
    refresh_token: seal(env, 'rt', keep, left || 0) }];
}

// ---------- MCP (Streamable HTTP, stateless JSON) ----------
const INSTRUCTIONS = 'Work shifts and pay of one person, from the Hebrew app "חישוב שכר". Dates are YYYY-MM-DD in the person\'s time zone. '
  + 'Amounts are exactly as saved; do not recompute them. Before editing or deleting, confirm with the person which shift you mean.';
async function mcp(env, req, body, fetcher = fetch) {
  const bearer = String(req.headers.authorization || '').replace(/^Bearer\s+/i, '');
  const data = open(env, 'at', bearer) || open(env, 'key', bearer);
  const l = data && await live(env, data, fetcher);
  if (!l) return [401, { error: 'invalid_token' }, { 'WWW-Authenticate': 'Bearer resource_metadata="' + origin(req) + '/.well-known/oauth-protected-resource"' }];
  const ctx = { db: l.db, grant: { ...data, s: l.doc.data.scopes || data.s } };
  const one = async m => {
    if (!m || m.jsonrpc !== '2.0' || typeof m.method !== 'string') return { jsonrpc: '2.0', id: m?.id ?? null, error: { code: -32600, message: 'Invalid request' } };
    if (m.id === undefined) return null; // a notification
    const ok = result => ({ jsonrpc: '2.0', id: m.id, result });
    switch (m.method) {
      case 'initialize': return ok({ protocolVersion: m.params?.protocolVersion || '2025-06-18', capabilities: { tools: { listChanged: false } },
        serverInfo: { name: 'kis-shifts', title: 'חישוב שכר', version: VERSION }, instructions: INSTRUCTIONS });
      case 'ping': return ok({});
      case 'tools/list': return ok({ tools: TOOLS.filter(t => ctx.grant.s.includes(t.scope)).map(({ scope, ...t }) => t) });
      case 'tools/call':
        try {
          const result = await run(ctx, m.params?.name, m.params?.arguments || {});
          return ok({ content: [{ type: 'text', text: JSON.stringify(result) }], structuredContent: Array.isArray(result) ? { items: result } : result });
        } catch (e) {
          if (!e.user) console.warn('assistant tool failed', e.message);
          return ok({ content: [{ type: 'text', text: e.user ? e.message : 'Something went wrong. Try again.' }], isError: true });
        }
      default: return { jsonrpc: '2.0', id: m.id, error: { code: -32601, message: 'Method not found' } };
    }
  };
  if (Array.isArray(body)) { const out = (await Promise.all(body.map(one))).filter(Boolean); return out.length ? [200, out] : [202, null]; }
  const out = await one(body);
  return out ? [200, out] : [202, null];
}

module.exports = { SCOPES, DURATIONS, TOOLS, seal, open, metadata, register, client, grant, token, mcp, run, zonedMs, zoneParts, redirectOk, store, idTokenFor };
