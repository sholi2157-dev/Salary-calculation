// Mirrors Android app/src/test/java/com/example/V2ShareTest.kt against the shared fixture.
const {test}=require('node:test');const assert=require('node:assert/strict');
const S=require('../web/v2/share.js');const Pay=require('../web/v2/pay.js');const doc=require('./fixtures/v2-share-fixtures.json');
const day=iso=>{const [y,m,d]=iso.split('-').map(Number);return new Date(y,m-1,d,12,0).getTime();};
function entry(raw){
 const o={...doc.entryDefaults,...raw};const dbl=k=>o[k]===undefined||o[k]===null?null:o[k];
 return {category:o.category,date:day(o.date),isTimeRange:o.isTimeRange,startTime:o.start||null,endTime:o.end||null,hours:o.hours,hourlyRate:o.rate,totalEarnings:o.total,
  isPaid:o.isPaid,notes:o.notes,currency:o.currency,kind:o.kind,payKnown:o.payKnown,bonus:o.bonus,isGroupShift:o.isGroupShift,
  employerRate:dbl('employerRate'),workerRate:dbl('workerRate'),groupWorkersJson:Array.isArray(o.workers)?JSON.stringify(o.workers):''};
}
function produce(c,rows){
 switch(c.type){
  case 'personal':assert.equal(rows.length,1);return S.personal(rows[0]);
  case 'employer':assert.equal(rows.length,1);return S.employer(rows[0],c.ownName||null,c.technical===true);
  case 'worker':assert.equal(rows.length,1);return S.worker(rows[0],Pay.workers(rows[0])[c.worker],c.technical===true);
  case 'summary':return S.summary(rows,c.workerName||null);
  case 'excel':return S.excel(rows);
  case 'csv':return S.csv(rows);
  case 'copy':return S.copy(rows,c.workerName||null);
  case 'entry':return S.entry(rows[0],c.technical===true,c.future===true);
  case 'planned':{const o=c.occurrence;return S.planned({key:o.key,seriesId:o.seriesId,category:o.category,date:o.date,start:o.start,end:o.end,rate:o.rate??null,currency:o.currency,edited:false,hold:false,status:'PLANNED'},c.technical===true);}
  default:throw Error('unknown type '+c.type);
 }
}
test('every shared text matches the contract',()=>{
 assert.ok(doc.cases.length>=13);
 for(const c of doc.cases){const text=produce(c,c.entries.map(entry));assert.equal(text,c.expected,c.name);
  for(const f of c.forbidden||[])assert.ok(!text.includes(f),`${c.name} leaks ${f}`);}
});
test('texts have no hidden direction marks and never change data',()=>{
 for(const c of doc.cases){const rows=c.entries.map(entry),before=structuredClone(rows);const text=produce(c,rows);
  assert.ok(!/[⁦-⁩‏‎]/.test(text),c.name);assert.deepEqual(rows,before,c.name);}
});
test('hours are readable',()=>{assert.equal(S.hours(8),'8');assert.equal(S.hours(7.1+0.2),'7.3');assert.equal(S.hours(2.97),'2.97');
 // BigDecimal(double) rounds the exact binary value: 2.675 is 2.67499999… → "2.67"; 0.125 is exact → "0.13".
 assert.equal(S.hours(2.675),'2.67');assert.equal(S.hours(0.125),'0.13');assert.equal(S.hours(-0.001),'0');assert.equal(S.hours(100),'100');});
test('money follows Java %.2f / %,.2f (HALF_UP on the shortest decimal)',()=>{
 const java={2.675:'2.68',0.125:'0.13',1.005:'1.01',343.125:'343.13',1.115:'1.12',0.045:'0.05',2.665:'2.67',0.0049999999999999:'0.00',0.00001:'0.00'};
 for(const [v,t] of Object.entries(java))assert.equal(Pay.fixed2(Number(v)),t,v);
 assert.equal(Pay.fixed2(-0),'-0.00');assert.equal(Pay.format(1284.5,'₪'),'₪1,284.50');assert.equal(Pay.format(1e7+0.005,'$'),'$10,000,000.01');assert.equal(Pay.format(123456.785,'₪'),'₪123,456.79');
});

test('copy and share differ, mixed workplaces stay technical, paid amounts are not requested twice',()=>{
 const base=entry(doc.cases[0].entries[0]);
 assert.equal(S.copy([base]).split('\n')[0],'עבודה: מסעדה');
 assert.match(S.personal(base),/^היי, סיכמתי פה/);
 assert.doesNotMatch(S.copy([base]),/היי|תודה/);
 assert.equal(S.summary([base,{...base,category:'אחר'}]),S.copy([base,{...base,category:'אחר'}]));
 assert.match(S.personal({...base,isPaid:true}),/יתרה לתשלום: ₪0.00/);
 assert.match(S.personal({...base,isPaid:true}),/שולם: ₪320.00/);
 assert.equal(S.workerName([{...base,groupWorkersJson:JSON.stringify([{name:'דני',hours:2}])}],' דני '),'דני');
});
test('both modern spreadsheet formats can be read back without turning unknown pay into zero pay',()=>{
 const T=require('../web/transfer.js');
 const c=doc.cases.find(c=>c.name==='excel_complex_notes_job_bonus'),rows=c.entries.map(entry);
 for(const text of [S.excel(rows),S.csv(rows)]){
  const imported=T.decodeTable(text).entries;
  assert.equal(imported.find(e=>e.kind==='job').payKnown,false);
  assert.equal(imported.find(e=>e.bonus===50).totalEarnings,336);
  assert.equal(imported.find(e=>e.notes.includes('שורה')).notes,'שורה א\nשורה "ב"\tסוף');
 }
});
