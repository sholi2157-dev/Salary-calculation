const fs = require('node:fs');
fs.mkdirSync('public/web', { recursive: true });
fs.copyFileSync('index.html', 'public/index.html');
fs.copyFileSync('web/transfer.js', 'public/web/transfer.js');
