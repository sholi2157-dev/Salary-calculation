'use strict';
const {randomUUID,createHmac}=require('node:crypto');
const COOKIE='salary_download_browser';
function browserId(req){const raw=(req.headers?.cookie||'').split(';').map(p=>p.trim()).find(p=>p.startsWith(COOKIE+'='))?.slice(COOKIE.length+1);return /^[a-f0-9-]{36}$/i.test(raw||'')?raw:randomUUID()}
async function recordDownload(req,res,release,{env=process.env,fetcher=fetch}={}){
 if(req.method!=='GET'||env.VERCEL_ENV!=='production')return false;
 if(/bot|crawler|spider|headless|preview/i.test(req.headers?.['user-agent']||''))return false;
 if(!env.DOWNLOAD_HASH_KEY||!env.DOWNLOAD_RECORDER_KEY||!env.DOWNLOAD_STATS_SITE_TOKEN||env.DOWNLOAD_STATS_SITE_URL!=='https://salary-calculation-management.sholi2157.chatgpt.site')return false;
 const id=browserId(req);
 res.setHeader('Set-Cookie',COOKIE+'='+id+'; Path=/; Max-Age=31536000; Secure; HttpOnly; SameSite=Lax');
 const browserHash=createHmac('sha256',env.DOWNLOAD_HASH_KEY).update(id).digest('hex');
 try{
  const r=await fetcher(env.DOWNLOAD_STATS_SITE_URL+'/api/record-download',{method:'POST',headers:{'Content-Type':'application/json','OAI-Sites-Authorization':'Bearer '+env.DOWNLOAD_STATS_SITE_TOKEN,'X-Download-Recorder':env.DOWNLOAD_RECORDER_KEY},body:JSON.stringify({id:randomUUID(),browserHash,version:release.version}),signal:AbortSignal.timeout(1800),redirect:'error'});
  if(!r.ok)console.warn('Download statistics unavailable: '+r.status);
  return r.ok;
 }catch{console.warn('Download statistics unavailable; continuing download');return false}
}
module.exports={recordDownload,browserId};
