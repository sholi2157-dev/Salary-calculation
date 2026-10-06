const fs = require('node:fs');
fs.mkdirSync('public/web', { recursive: true });
fs.copyFileSync('index.html','public/index.html');
fs.cpSync('web','public/web',{recursive:true,filter:source=>!source.endsWith('personal-ai.js')});

// Public provenance contains no configuration or credentials. Preview QA uses
// the deployed app in a real 390px browsing context, not a scaled desktop image.
const crypto = require('node:crypto');
const commit = process.env.VERCEL_GIT_COMMIT_SHA || process.env.GITHUB_SHA || require('node:child_process').execSync('git rev-parse HEAD').toString().trim();
const cssSha256 = crypto.createHash('sha256').update(fs.readFileSync('public/web/app.css')).digest('hex');
// Any changed shell file produces a new worker, including edits without a new commit.
const digest=crypto.createHash('sha256');
function hashTree(dir){for(const name of fs.readdirSync(dir).sort()){const p=dir+'/'+name;if(fs.statSync(p).isDirectory())hashTree(p);else{digest.update(p);digest.update(fs.readFileSync(p));}}}
digest.update(fs.readFileSync('index.html'));digest.update(fs.readFileSync('sw.js'));hashTree('web');
const buildId=commit+'-'+digest.digest('hex').slice(0,16);
fs.writeFileSync('public/web/build.js','window.WorkBuild='+JSON.stringify({commit,buildId})+';\n');
fs.writeFileSync('public/sw.js',fs.readFileSync('sw.js','utf8').replace('__WEB_BUILD_ID__',buildId));
fs.writeFileSync('public/build-info.json', JSON.stringify({commit, buildId, cssSha256}, null, 2));
if (process.env.VERCEL_ENV === 'preview') {
  fs.writeFileSync('public/preview-check.html', `<!doctype html><html lang="en"><meta charset="utf-8"><meta name="robots" content="noindex"><title>Preview phone check</title><style>body{margin:0;background:#202124;color:#fff;font:14px sans-serif;display:grid;justify-content:center}p{max-width:390px;overflow-wrap:anywhere}iframe{width:390px;height:844px;border:0;background:#090b10}</style><p>Preview · 390 × 844 · ${commit}</p><iframe title="Phone preview" src="/" allow="clipboard-write; web-share"></iframe></html>`);
} else if (fs.existsSync('public/preview-check.html')) {
  fs.unlinkSync('public/preview-check.html');
}
