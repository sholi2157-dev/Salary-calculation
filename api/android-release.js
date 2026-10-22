'use strict';
const { latestRelease } = require('../lib/server/android-release.cjs');
module.exports = async function handler(req, res) {
  if (req.method !== 'GET') return res.status(405).end();
  res.setHeader('Cache-Control', 'no-store');
  return res.status(200).json(await latestRelease());
};
