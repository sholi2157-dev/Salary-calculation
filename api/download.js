'use strict';
const { latestRelease } = require('../lib/server/android-release.cjs');
const { recordDownload, browserId } = require('../lib/server/download-measurement.cjs');
const { countDownload } = require('../lib/server/download-count.cjs');
module.exports = async function handler(req, res) {
  if (req.method !== 'GET' && req.method !== 'HEAD') return res.status(405).end();
  const release = await latestRelease();
  res.setHeader('Cache-Control', 'no-store');
  res.setHeader('Referrer-Policy', 'no-referrer');
  const id = browserId(req);
  await Promise.all([recordDownload(req, res, release, { id }), countDownload(req, release, id)]);
  return res.redirect(302, release.url);
};
