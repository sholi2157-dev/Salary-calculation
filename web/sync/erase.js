/*
 * The two separate "delete data" actions (audit M-13, 2.3.0). Same rules as Android (WorkErase.kt):
 *
 * 1. eraseDevice: this device only. The account copy is never touched. When signed in, everything waiting is sent first
 *    (refused while anything is still unsent or waiting for a choice, so nothing that exists only here is lost), then the
 *    app's session signs out and this account's sync memory is forgotten. Without that memory the next sign-in is a fresh
 *    join that downloads the account's data; it can never read the emptied device as deletions. The Google account itself
 *    is not signed out of the browser or the phone.
 * 2. eraseAccount: the Salary Calculation records of the signed-in account only (users/{uid}/records_v1), on every
 *    device. Everything in the account is read first, so records this device had not seen yet are deleted too. The
 *    deletion is written as ordinary tombstones, which every 2.x client (also the public Android 2.2.1) applies. Success
 *    is reported only after the account itself was read again and holds no live record.
 */
(function (root) {
'use strict';

const STARTER = { name: 'עצמאי', defaultRate: 40 };
/** Work data keys of one storage (the browser's own or an account copy); device choices such as theme stay. */
const DATA_KEYS = ['work_complete_backup', 'user_work_shifts', 'work_transfer_meta', 'work_default_currency', 'work_series_v1',
  'kis_web_draft_v1', 'kis_commit_journal_v1', 'work_pre_v2_snapshot_v1', 'work_pre_v23_snapshot_v1', 'kis_web_recent_v1'];
const PERSONAL = ['name', 'lastName', 'placeOrder', 'lastRate'];

/** Empties the work data of a store in one commit (as a new browser starts, with the starter place). */
function emptyStore(store) {
  store.transact(n => {
    n.data.entries = []; n.data.workers = []; n.data.categories = [{ ...STARTER }];
    n.data.webPreferences = (root.WorkCategories || require('../categories.js')).preferences({});
    n.plans.series = []; n.plans.exceptions = [];
  });
}

/** Live Salary Calculation records still in the account, read from the server (the starter place and settings do not count). */
async function remoteLeft(transport, uid) {
  const { records } = await transport.read(uid, null);
  return records.filter(r => !r.deleted && r.type !== 'preference' &&
    !(r.type === 'category' && (root.WorkSyncProtocol || require('./protocol.js')).decode(r.type, r.payload).name === STARTER.name)).length;
}

/**
 * Deletes the account's records everywhere. {ok, left}: ok only when a fresh read of the account shows nothing left.
 * A running timer must be finished first (the caller checks). Throws while offline (nothing is changed then).
 */
async function eraseAccount({ store, sync, transport, uid, attempts = 3 }) {
  await sync.sync(); // everything in the account reaches this device first
  let left = null;
  for (let i = 0; i < attempts; i++) {
    emptyStore(store);
    for (const c of sync.conflicts()) sync.resolve(c.syncId, true); // the deletion wins over an older choice
    await sync.sync();
    left = await remoteLeft(transport, uid);
    if (left === 0) { emptyStore(store); return { ok: true, left: 0 }; }
  }
  return { ok: false, left };
}

/**
 * Checks that deleting from this device loses nothing that exists only here. {ok} or {ok:false, unsent, conflicts}.
 * Without an account there is nothing to check.
 */
async function deviceReady(sync) {
  if (!sync) return { ok: true, unsent: 0, conflicts: 0 };
  try { await sync.sync(); } catch {}
  const unsent = await sync.pending().catch(() => 1), conflicts = sync.conflicts().length;
  return { ok: unsent === 0 && conflicts === 0, unsent, conflicts };
}

/**
 * Removes this device's work data: the data keys under `prefix` ('' for the browser's own data, 'acct:<uid>:' for an
 * account copy), any damaged-data copies, this account's sync memory, its running-timer key and the personal fields of
 * the device choices (theme and the like stay). Call only after the app signed out.
 */
function wipeDevice(storage, prefix, uid) {
  const keys = [];
  for (let i = 0; i < storage.length; i++) keys.push(storage.key(i));
  for (const k of DATA_KEYS) storage.removeItem(prefix + k);
  for (const k of keys) if (k && k.startsWith(prefix + 'work_damaged_copy_')) storage.removeItem(k);
  storage.removeItem(prefix + 'work_active_shift_v1:guest');
  if (uid) { storage.removeItem('work_sync_v1:' + uid); storage.removeItem(prefix + 'work_active_shift_v1:' + encodeURIComponent(uid)); }
  try {
    const local = JSON.parse(storage.getItem(prefix + 'kis_web_v1') || '{}');
    for (const k of PERSONAL) delete local[k];
    storage.setItem(prefix + 'kis_web_v1', JSON.stringify(local));
  } catch { storage.removeItem(prefix + 'kis_web_v1'); }
}

const api = { emptyStore, remoteLeft, eraseAccount, deviceReady, wipeDevice, DATA_KEYS, STARTER };
if (typeof module !== 'undefined') module.exports = api; else root.WorkErase = api;
})(globalThis);
