const fs = require('fs');

const path = 'app/src/main/java/com/example/MainActivity.kt';
let content = fs.readFileSync(path, 'utf8');

content = content.replace(/0xFF7C3AED/g, '0xFF5C6BC0');
content = content.replace(/0xFF27272A/g, '0xFF1C1C1E');
content = content.replace(/0xFF1A1A1A/g, '0xFF121212');
content = content.replace(/0xFF94A3B8/g, '0xFF8E8E93');
content = content.replace(/0xFF000000/g, '0xFF000000'); 

fs.writeFileSync(path, content, 'utf8');
console.log('Colors replaced successfully');
