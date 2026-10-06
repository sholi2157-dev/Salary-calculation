const fs = require('node:fs');
fs.mkdirSync('public/web', { recursive: true });
fs.copyFileSync('index.html','public/index.html');
fs.cpSync('web','public/web',{recursive:true,filter:source=>!source.endsWith('personal-ai.js')});
fs.copyFileSync('sw.js','public/sw.js');

// Public provenance contains no configuration or credentials. Preview QA uses
// the deployed app in a real 390px browsing context, not a scaled desktop image.
const crypto = require('node:crypto');
const commit = process.env.VERCEL_GIT_COMMIT_SHA || process.env.GITHUB_SHA || require('node:child_process').execSync('git rev-parse HEAD').toString().trim();
const cssSha256 = crypto.createHash('sha256').update(fs.readFileSync('public/web/app.css')).digest('hex');
fs.writeFileSync('public/build-info.json', JSON.stringify({commit, cssSha256}, null, 2));
if (process.env.VERCEL_ENV === 'preview') {
  fs.writeFileSync('public/preview-check.html', `<!doctype html><html lang="en"><meta charset="utf-8"><meta name="robots" content="noindex"><title>Preview phone check</title><style>body{margin:0;background:#202124;color:#fff;font:14px sans-serif;display:grid;justify-content:center}p{max-width:390px;overflow-wrap:anywhere}iframe{width:390px;height:844px;border:0;background:#090b10}</style><p>Preview · 390 × 844 · ${commit}</p><iframe title="Phone preview" src="/" allow="clipboard-write; web-share"></iframe></html>`);
} else if (fs.existsSync('public/preview-check.html')) {
  fs.unlinkSync('public/preview-check.html');
}
