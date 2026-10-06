const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const path = require('node:path');

test('home/history navigation remains independent of timer state', () => {
  const html = fs.readFileSync(path.join(__dirname, '../index.html'), 'utf8');
  assert.equal(html, fs.readFileSync(path.join(__dirname, '../public/index.html'), 'utf8'));
  const app=fs.readFileSync(path.join(__dirname, '../web/app.js'),'utf8');
  const source = app.match(/function showPage\(page\)\{[^\n]+/)[0];
  const nodes = Object.fromEntries(['page-home', 'page-history', 'nav-home', 'nav-history'].map(id =>
    [id, {hidden:false, offsetWidth:100, classList:{remove(){},add(){}}, setAttribute(){},removeAttribute(){}}]));
  const context = vm.createContext({currentPage:0, pageScroll:[0,0], selectionMode:true, selectionCleared:false,
    window:{scrollY:25,scrollTo(){}}, document:{getElementById:id=>nodes[id],
      querySelector(){throw new Error('navigation must not depend on removed timer controls');}}});
  vm.runInContext('function exitSelection(){selectionMode=false;selectionCleared=true;}'+source + ';showPage(1);', context);
  assert.equal(nodes['page-home'].hidden, true);
  assert.equal(nodes['page-history'].hidden, false);
  vm.runInContext('showPage(0);', context);
  assert.equal(nodes['page-home'].hidden, false);
  assert.equal(nodes['page-history'].hidden, true);
  assert.equal(context.selectionMode,false);
  assert.equal(context.selectionCleared,true);
});
