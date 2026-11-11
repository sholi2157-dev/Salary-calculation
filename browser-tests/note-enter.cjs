// FB-01c (decided Oct 9): Enter in a shift's note is a new line. It never closes the note or the editor, and a note-only
// edit keeps the saved amount exactly as it was. Same check as Android WorkNoteEnterTest. Synthetic data only.
const { chromium } = require(process.env.PLAYWRIGHT_MODULE || 'playwright'); const http=require('http'),fs=require('fs'),path=require('path'),assert=require('assert/strict');
const ROOT=path.resolve('public'), rich=JSON.parse(fs.readFileSync('web-tests/fixtures/synthetic/rich-v2.json','utf8'));
const s=http.createServer((q,r)=>{let p=decodeURIComponent(new URL(q.url,'http://x').pathname);if(p==='/')p='/index.html';if(p==='/api/config'){r.end('{}');return;}const f=path.join(ROOT,p);if(!fs.existsSync(f)||fs.statSync(f).isDirectory()){r.writeHead(404);return r.end();}r.writeHead(200,{'content-type':{'.html':'text/html; charset=utf-8','.js':'text/javascript','.css':'text/css','.ttf':'font/ttf'}[path.extname(f)]||'application/octet-stream'});fs.createReadStream(f).pipe(r);});
s.listen(0,async()=>{const b=await chromium.launch(process.env.CHROMIUM_PATH?{executablePath:process.env.CHROMIUM_PATH}:{});
 try { for (const width of [320, 412]) {
  const c=await b.newContext({viewport:{width,height:740},locale:'he-IL',serviceWorkers:'block',hasTouch:true});
  await c.addInitScript(d=>{if(!sessionStorage.getItem('s')){sessionStorage.setItem('s',1);localStorage.clear();for(const[k,v]of Object.entries(d))localStorage.setItem(k,JSON.stringify(v));localStorage.setItem('kis_web_v1',JSON.stringify({launched:2,tourDone:true}));localStorage.setItem('kis_ai_invited_v1','1');localStorage.setItem('kis_signin_skip_v1','1');}},rich);
  const p=await c.newPage(); const errs=[]; p.on('pageerror',e=>errs.push(e.message));
  await p.goto('http://127.0.0.1:'+s.address().port+'/');await p.waitForSelector('[data-testid=home-hero]');
  const stored=()=>p.evaluate(()=>JSON.parse(localStorage.getItem('work_complete_backup')).entries.find(e=>e.id==='r01'));
  const before=await stored();
  await p.evaluate(()=>window.Kis.openDetail('r01')); await p.click('[data-testid=detail-edit]'); await p.waitForSelector('[data-testid=save-shift]');
  await p.click('[data-testid=editor-note]'); const note='[data-testid=notes-input]';
  await p.locator(note).fill('שורה ראשונה'); await p.locator(note).press('End');
  await p.keyboard.press('Enter');
  // Still open, the editor and its note, with the focus in the note.
  assert.equal(await p.locator(note).count(), 1, 'Enter keeps the note open');
  assert.equal(await p.locator('[data-testid=save-shift]').count(), 1, 'Enter keeps the editor open');
  assert.equal(await p.evaluate(()=>document.activeElement?.dataset?.testid), 'notes-input', 'focus stays in the note');
  await p.keyboard.type('שורה שנייה');
  assert.equal(await p.locator(note).inputValue(), 'שורה ראשונה\nשורה שנייה');
  await p.click('[data-testid=save-shift]'); await p.waitForFunction(()=>!window.Kis.editorOpen);
  const kept=await stored();
  assert.equal(kept.notes, 'שורה ראשונה\nשורה שנייה');
  // Only the note changed: the stored amount, hours, rate, date and times are exactly as saved before.
  for (const k of ['totalEarnings','hours','hourlyRate','date','startTime','endTime','currency','isPaid']) assert.deepEqual(kept[k], before[k], k);
  assert.deepEqual(errs,[]); await c.close();
 } console.log('note-enter ok'); } finally { await b.close(); s.close(); } });
