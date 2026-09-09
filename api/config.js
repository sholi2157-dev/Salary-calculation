module.exports = function handler(req, res) {
  if (req.method !== 'GET') return res.status(405).end();
  res.setHeader('Cache-Control', 'no-store');
  // These are public Firebase web registration fields, never provider or service-account keys.
  const projectId=process.env.FIREBASE_PROJECT_ID;
  const apiKey=process.env.FIREBASE_WEB_API_KEY;
  const authDomain=process.env.FIREBASE_AUTH_DOMAIN;
  const appId=process.env.FIREBASE_WEB_APP_ID;
  res.status(200).json({ firebase: projectId&&apiKey&&authDomain&&appId ? {projectId,apiKey,authDomain,appId} : null, cloudSyncEnabled:false });
};
