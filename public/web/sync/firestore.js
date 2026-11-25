/*
 * The Firestore side of account sync (Firebase compat SDK), the same documents as Android WorkFirestoreTransport:
 * users/{uid}/records_v1/{syncId}. Every write stamps updatedAt with the server time, so a device only reads
 * what changed since its last read. A document that fails validation is skipped, never applied.
 */
(function (root) {
'use strict';
const P = () => root.WorkSyncProtocol || require('./protocol.js');

/** firebase: the compat namespace (for Timestamp and FieldValue); app: the initialized app, the default one unless given. */
function firestoreTransport(firebase, app = firebase.app()) {
  const db = app.firestore(), auth = app.auth();
  const records = uid => db.collection('users').doc(uid).collection('records_v1');
  const owner = uid => { if (auth.currentUser?.uid !== uid) throw Error('Account changed'); };
  function decode(doc) {
    const d = doc.data();
    if (d.schema !== 1) throw Error('Unknown sync schema');
    return P().validate({ syncId: doc.id, type: d.type, version: d.version, operation: d.operation, deleted: d.deleted, payload: d.payload ?? null });
  }
  return {
    /** Records changed at or after `cursor` (server time in ms), and the new cursor. null reads everything. */
    async read(uid, cursor) {
      owner(uid);
      const query = cursor == null ? records(uid) : records(uid).where('updatedAt', '>=', firebase.firestore.Timestamp.fromMillis(cursor));
      const snap = await query.get({ source: 'server' });
      owner(uid);
      let next = cursor, out = [];
      for (const doc of snap.docs) {
        const at = doc.get('updatedAt');
        if (at?.toMillis) next = Math.max(next ?? 0, at.toMillis());
        try { out.push(decode(doc)); } catch (e) { console.warn('Skipped an unreadable sync record', doc.id, e.message); }
      }
      return { records: out, cursor: next };
    },
    /** Compare-and-set in a transaction: the proposed record when the account still holds `base`, otherwise what it holds. */
    async exchange(uid, base, proposed) {
      owner(uid);
      if (proposed.version !== base + 1) throw Error('Invalid sync version');
      const ref = records(uid).doc(proposed.syncId);
      return db.runTransaction(async t => {
        owner(uid);
        const snap = await t.get(ref), current = snap.exists ? decode(snap) : null;
        if (current?.operation === proposed.operation) return current;
        if ((current?.version ?? 0) !== base) { if (!current) throw Error('Remote record removed unexpectedly'); return current; }
        t.set(ref, { schema: 1, type: proposed.type, version: proposed.version, operation: proposed.operation, deleted: proposed.deleted,
          payload: proposed.payload, updatedAt: firebase.firestore.FieldValue.serverTimestamp() });
        return proposed;
      });
    },
    /** Calls back when another device or the assistant server writes; returns the stop function. */
    listen(uid, since, onChange) {
      const start = firebase.firestore.Timestamp.fromMillis(since ?? Date.now());
      return records(uid).where('updatedAt', '>=', start).onSnapshot(snap => {
        if (!snap.metadata.hasPendingWrites && snap.docChanges().length) onChange();
      }, () => {});
    },
  };
}

const api = { firestoreTransport };
if (typeof module !== 'undefined') module.exports = api; else root.WorkSyncFirestore = api;
})(globalThis);
