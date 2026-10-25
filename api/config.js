module.exports = function handler(req, res) {
  if (req.method !== 'GET') return res.status(405).end();
  res.setHeader('Cache-Control', 'no-store');
  const features=require('../web/features.js');
  if(!features.accountsEnabled&&!features.accountSyncEnabled)return res.status(200).json({firebase:null,webAccountsEnabled:false,cloudSyncEnabled:false,webAiEnabled:false});
  // These are public Firebase web registration fields, never provider or service-account keys.
  const client=require('../app/src/debug/google-services.json');
  const projectId=process.env.FIREBASE_PROJECT_ID || client.project_info.project_id;
  const apiKey=process.env.FIREBASE_WEB_API_KEY || client.client[0].api_key[0].current_key;
  const authDomain=process.env.FIREBASE_AUTH_DOMAIN || projectId+'.firebaseapp.com';
  const appId=process.env.FIREBASE_WEB_APP_ID;
  res.status(200).json({ firebase: projectId&&apiKey ? {projectId,apiKey,authDomain,...(appId?{appId}:{})} : null, webAccountsEnabled:features.accountsEnabled, accountSyncEnabled:features.accountSyncEnabled, cloudSyncEnabled:true, webAiEnabled:false });
};
