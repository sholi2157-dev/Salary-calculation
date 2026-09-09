const fs = require('node:fs');
fs.mkdirSync('public/web', { recursive: true });
fs.copyFileSync('index.html', 'public/index.html');
fs.copyFileSync('web/transfer.js', 'public/web/transfer.js');
fs.copyFileSync('web/app.css', 'public/web/app.css');
fs.copyFileSync('web/history.js', 'public/web/history.js');
fs.copyFileSync('web/parity.js', 'public/web/parity.js');

fs.copyFileSync('web/selection.js', 'public/web/selection.js');
