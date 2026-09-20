const fs = require('node:fs');
fs.mkdirSync('public/web', { recursive: true });
fs.copyFileSync('index.html', 'public/index.html');
fs.copyFileSync('web/transfer.js', 'public/web/transfer.js');
fs.copyFileSync('web/app.css', 'public/web/app.css');
fs.copyFileSync('web/history.js', 'public/web/history.js');
fs.copyFileSync('web/parity.js', 'public/web/parity.js');

fs.copyFileSync('web/selection.js', 'public/web/selection.js');
fs.copyFileSync('web/cloud-sync.js', 'public/web/cloud-sync.js');
fs.copyFileSync('web/accounts.js', 'public/web/accounts.js');

for (const file of ['categories.js','sharing.js']) fs.copyFileSync('web/'+file,'public/web/'+file);
