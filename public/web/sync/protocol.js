/*
 * The account sync wire format, shared with Android (data/WorkSyncProtocol.kt) and the assistant server.
 * One Firestore document per item at users/{uid}/records_v1/{syncId}:
 *   { schema: 1, type, version, operation, deleted, payload, updatedAt }
 * payload is a backup file (WorkTransfer.encode / WorkBackup.encode) holding exactly one item of `type`,
 * or null for a deleted item. Amounts travel as saved; nobody recalculates them on the way.
 * Entries, places and workers get a random syncId when first sent. Plans, plan days and settings have a
 * natural key, so their syncId is derived from it and every device arrives at the same document.
 */
(function (root) {
'use strict';
const T = () => root.WorkTransfer || require('../transfer.js');
const TYPES = ['entry', 'category', 'worker', 'series', 'exception', 'preference'];
const KEYED = ['series', 'exception', 'preference'];
const PREFERENCE = /^(defaultCategory|default_currency|categoryCurrency:.+)$/;
const ENTRY_DEFAULTS = { kind: 'shift', payKnown: true, bonus: 0, seriesKey: null, autoConfirmed: false };
const SYNC_ID = /^[a-f0-9]{32}$/;
const MAX_PAYLOAD = 500000;

function check(ok, message) { if (!ok) throw Error(message || 'Invalid sync record'); }
const sorted = o => Object.fromEntries(Object.keys(o).sort().map(k => [k, o[k]]));

async function sha256(text) {
  const subtle = root.crypto?.subtle || require('node:crypto').webcrypto.subtle;
  const bytes = new Uint8Array(await subtle.digest('SHA-256', new TextEncoder().encode(text)));
  return [...bytes].map(b => b.toString(16).padStart(2, '0')).join('');
}
/** The fixed syncId of a plan, a plan day or a setting: the first 32 hex digits of SHA-256("type|key"). */
async function keyedId(type, key) { check(KEYED.includes(type), 'Not a keyed type'); return (await sha256(type + '|' + key)).slice(0, 32); }
function randomId() {
  const bytes = new Uint8Array(16); (root.crypto || require('node:crypto').webcrypto).getRandomValues(bytes);
  return [...bytes].map(b => b.toString(16).padStart(2, '0')).join('');
}

/** The local identity of an item: what a device uses to find it again. */
function keyOf(type, item) {
  if (type === 'entry') return String(item.id);
  if (type === 'category' || type === 'worker') return item.name;
  if (type === 'series') return item.id;
  if (type === 'exception') return item.seriesId + '@' + item.date;
  if (type === 'preference') return item.name;
  throw Error('Unknown sync type');
}

/** One item as the payload text. A setting is {name, value}. */
function encode(type, item) {
  if (type === 'entry') return T().encode({ entries: [item] });
  if (type === 'category') return T().encode({ categories: [item] });
  if (type === 'worker') return T().encode({ workers: [item] });
  if (type === 'series') return T().encode({ series: [item] });
  if (type === 'exception') return T().encode({ exceptions: [item] });
  if (type === 'preference') {
    check(PREFERENCE.test(item.name) && typeof item.value === 'string', 'Unknown setting');
    return JSON.stringify({ formatVersion: 3, androidLocalPreferences: { [item.name]: item.value }, entries: [], categories: [], workers: [] }, null, 2);
  }
  throw Error('Unknown sync type');
}

/** The single item in a payload, checked like an import; throws when the payload holds anything else. */
function decode(type, payload) {
  check(typeof payload === 'string' && new TextEncoder().encode(payload).length <= MAX_PAYLOAD, 'Invalid sync payload');
  const raw = JSON.parse(payload);
  check([2, 3].includes(raw.formatVersion), 'Unsupported version');
  if (type === 'preference') {
    const local = raw.androidLocalPreferences || {}, names = Object.keys(local);
    check(names.length === 1 && PREFERENCE.test(names[0]) && typeof local[names[0]] === 'string', 'Invalid setting');
    if (names[0] === 'defaultCategory') check(local[names[0]].trim(), 'Invalid setting');
    else check(['₪', '$'].includes(local[names[0]]), 'Invalid setting currency');
    check(!(raw.entries || []).length && !(raw.categories || []).length && !(raw.workers || []).length, 'Invalid record shape');
    return { name: names[0], value: local[names[0]] };
  }
  const d = T().decode(payload);
  const counts = { entry: d.entries.length, category: d.categories.length, worker: d.workers.length, series: (d.series || []).length, exception: (d.exceptions || []).length };
  for (const t of Object.keys(counts)) check(counts[t] === (t === type ? 1 : 0), 'Invalid record shape');
  if (type === 'entry') return { ...ENTRY_DEFAULTS, ...d.entries[0] };
  if (type === 'category') return d.categories[0];
  if (type === 'worker') return d.workers[0];
  if (type === 'series') return d.series[0];
  return d.exceptions[0];
}

/** Text that is equal exactly when two payloads mean the same item, whatever the formatting or which device wrote it. */
function canonical(type, payload) {
  if (payload == null) return 'deleted';
  const item = decode(type, payload);
  if (type === 'entry') { const e = { ...ENTRY_DEFAULTS, ...item }; delete e.id; return JSON.stringify(sorted(e)); }
  return JSON.stringify(sorted(item));
}
/** Two different 32-bit FNV-1a passes: a compact fingerprint of canonical text for the local sync state. */
function fingerprint(text) {
  let a = 0x811c9dc5, b = 0x01000193 ^ text.length;
  for (let i = 0; i < text.length; i++) { const c = text.charCodeAt(i); a = Math.imul(a ^ c, 0x01000193) >>> 0; b = Math.imul(b ^ c, 0x5bd1e995) >>> 0; b ^= b >>> 15; }
  return a.toString(16).padStart(8, '0') + b.toString(16).padStart(8, '0');
}

function validate(r) {
  check(r && SYNC_ID.test(r.syncId) && TYPES.includes(r.type), 'Invalid sync record');
  check(Number.isSafeInteger(r.version) && r.version >= 1, 'Invalid sync version');
  check(typeof r.operation === 'string' && r.operation && r.operation.length <= 256, 'Invalid sync operation');
  check(typeof r.deleted === 'boolean', 'Invalid sync record');
  if (r.deleted) check(r.payload == null, 'Invalid tombstone'); else decode(r.type, r.payload);
  return { syncId: r.syncId, type: r.type, version: r.version, operation: r.operation, deleted: r.deleted, payload: r.deleted ? null : r.payload };
}

const api = { TYPES, KEYED, SYNC_ID, keyedId, randomId, sha256, keyOf, encode, decode, canonical, fingerprint, validate };
if (typeof module !== 'undefined') module.exports = api; else root.WorkSyncProtocol = api;
})(globalThis);
