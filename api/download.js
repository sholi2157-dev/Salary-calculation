'use strict';
const { latestRelease } = require('../lib/server/android-release.cjs');
module.exports = async function handler(req, res) {
  if (req.method !== 'GET' && req.method !== 'HEAD') return res.status(405).end();
  const release = await latestRelease();
  res.setHeader('Cache-Control', 'no-store');
  res.setHeader('Referrer-Policy', 'no-referrer');
  return res.redirect(302, release.url);
};
