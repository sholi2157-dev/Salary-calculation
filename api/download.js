'use strict';
const { latestRelease } = require('../lib/server/android-release.cjs');
const { recordDownload } = require('../lib/server/download-measurement.cjs');
module.exports = async function handler(req, res) {
  if (req.method !== 'GET' && req.method !== 'HEAD') return res.status(405).end();
  const release = await latestRelease();
  res.setHeader('Cache-Control', 'no-store');
  res.setHeader('Referrer-Policy', 'no-referrer');
  await recordDownload(req, res, release);
  return res.redirect(302, release.url);
};
