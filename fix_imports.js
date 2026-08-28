const fs = require('fs');
const path = 'app/src/main/java/com/example/MainActivity.kt';
let content = fs.readFileSync(path, 'utf8');

content = content.replace('import androidx.compose.material.icons.filled.*', 'import androidx.compose.material.icons.filled.*\nimport androidx.compose.material.icons.outlined.*');

fs.writeFileSync(path, content, 'utf8');
console.log('Imports fixed');
