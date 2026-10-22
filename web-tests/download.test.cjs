const {test}=require('node:test'),assert=require('node:assert/strict');
const {selectRelease,latestRelease,FALLBACK}=require('../lib/server/android-release.cjs');
test('downloads select only public stable Android releases and trusted APK URLs',()=>{
 const apk={name:'Salary2.0.4.apk',state:'uploaded',size:20,browser_download_url:'https://github.com/sholi2157-dev/Salary-calculation/releases/download/android-v2.0.4/Salary2.0.4.apk'};
 const release={tag_name:'android-v2.0.4',assets:[apk]};
 assert.equal(selectRelease([{...release,prerelease:true},{...release,tag_name:'web-v2'},release]).version,'2.0.4');
 assert.throws(()=>selectRelease([{...release,assets:[{...apk,browser_download_url:'https://example.com/other.apk'}]}]));
});
test('release API failure retains a usable verified fallback',async()=>{assert.deepEqual(await latestRelease(async()=>{throw Error('offline')}),FALLBACK)});
test('download endpoint redirects to APK without streaming or exposing private data',async()=>{const handler=require('../api/download.js');let status,url;const res={setHeader(){},redirect(s,u){status=s;url=u;return this}};const old=global.fetch;global.fetch=async()=>{throw Error('offline')};try{await handler({method:'GET'},res)}finally{global.fetch=old}assert.equal(status,302);assert.equal(url,FALLBACK.url)});
