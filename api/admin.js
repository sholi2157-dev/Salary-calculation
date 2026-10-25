'use strict';
const { verifyIdToken, isOwner, dashboard, FIREBASE_PROJECT } = require('../lib/server/admin-dashboard.cjs');
module.exports = async function handler(req, res) {
  if (req.method !== 'GET') return res.status(405).end();
  res.setHeader('Cache-Control', 'no-store');
  res.setHeader('X-Robots-Tag', 'noindex');
  if (req.query?.config !== undefined) {
    // Public Firebase web registration fields only, so the page can offer Google sign-in.
    const client = require('../app/src/debug/google-services.json');
    return res.status(200).json({ apiKey: process.env.FIREBASE_WEB_API_KEY || client.client[0].api_key[0].current_key,
      projectId: FIREBASE_PROJECT, authDomain: process.env.FIREBASE_AUTH_DOMAIN || FIREBASE_PROJECT + '.firebaseapp.com' });
  }
  const idToken = (req.headers.authorization || '').replace(/^Bearer /, '');
  const claims = await verifyIdToken(idToken);
  if (!claims) return res.status(401).json({ error: 'signin' });
  if (!isOwner(claims)) return res.status(403).json({ error: 'not-owner' });
  const days = [7, 14, 30].includes(+req.query?.days) ? +req.query.days : 30;
  return res.status(200).json(await dashboard({ idToken, days }));
};
