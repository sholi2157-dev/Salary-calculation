const {test}=require('node:test');
const assert=require('node:assert/strict');
const T=require('../web/transfer.js'), S=require('../web/v2/share.js');
const base={category:'בדיקה סינתטית',date:1770000000000,createdAt:1770000000000,hours:7.123456789,hourlyRate:43.21,totalEarnings:301.17,isPaid:false,currency:'₪',isTimeRange:true,startTime:'08:00',endTime:'16:00',notes:'בדיקה'};
const formulas=['=1+1','+SUM(1,2)','-1+2','@SUM(1,2)',' \t=1+1','\r\n=1+1','\u200f=1+1','\u0000=1+1','\u2066=1+1'];
const bad=[...formulas,0,900,2359,false,{},[],['09:00'],'','24:00','23:60','-1:00','09:0','009:00','12:30:00','09:00\n=1+1'];
test('backup imports reject invalid times in both fields with Hebrew record/field errors',()=>{
 for(const field of ['startTime','endTime'])for(const value of bad)for(const formatVersion of [1,2,3]){
  const entries=[base,{...base,[field]:value}];const before=structuredClone(entries);
  assert.throws(()=>T.decode(JSON.stringify({formatVersion,entries})),new RegExp('רשומה 2: '+(field==='startTime'?'שעת התחלה':'שעת סיום')+' לא תקינה'));
  assert.deepEqual(entries,before);
 }
});
test('all clock minutes and legacy H:mm/outer whitespace survive backup round trips exactly',()=>{
 const times=[null,'9:05',' 09:05 ','\t9:05\n'];
 for(let h=0;h<24;h++)for(let m=0;m<60;m++)times.push(`${String(h).padStart(2,'0')}:${String(m).padStart(2,'0')}`);
 for(const version3 of [false,true]){
  const entries=times.map(t=>T.normalize({...base,startTime:t,endTime:t,...(version3?{bonus:1.23,seriesKey:'synthetic-'+String(t)}:{})}));
  const data={entries,categories:[{name:base.category,defaultRate:43.21}],workers:[]};
  const before=structuredClone(data),encoded=T.encode(data),decoded=T.decode(encoded);
  assert.deepEqual(decoded.entries,entries);assert.equal(T.encode(decoded),encoded);assert.deepEqual(data,before);
 }
 const old={...base};delete old.startTime;delete old.endTime;
 const imported=T.decode(JSON.stringify([old])).entries[0];assert.equal(imported.startTime,null);assert.equal(imported.endTime,null);
});
test('CSV/TSV imports reject malformed time columns before returning any entries',()=>{
 for(const delimiter of [',','\t'])for(const value of [...formulas,'24:00','23:60','900']){
  const cell=v=>'"'+String(v).replaceAll('"','""')+'"';
  const rows=[['category','date','hours','rate','startTime','endTime'],['בדיקה','2026-02-02',2,40,'09:00','11:00'],['בדיקה','2026-02-02',2,40,value,'11:00']];
  assert.throws(()=>T.decode(rows.map(r=>r.map(cell).join(delimiter)).join('\n')),/שורה 3: שעה לא תקינה/);
 }
});
test('every user-controlled spreadsheet text cell, including both times, is formula-safe',()=>{
 for(const value of formulas){
  const row={...base,category:value,notes:value,currency:value,startTime:value,endTime:value};const before=structuredClone(row);
  for(const [exporter,delimiter,indices] of [[T.csv,',',[0,6,7,8,9]],[r=>T.csv(r,'\t'),'\t',[0,6,7,8,9]],[S.csv,',',[0,2,3,8,12]],[S.excel,'\t',[0,2,3,8,12]]]){
   const cells=T.table(exporter([row]).replace(/^\ufeff/,''),delimiter)[1];
   for(const i of indices)assert.equal(cells[i],"'"+value);
  }
  assert.deepEqual(row,before);
 }
});
test('unexpected non-string cells export without crashes or source mutation',()=>{
 for(const value of [null,undefined,0,930,false,true,{},[],['=1+1']]){
  const row={...base,category:value,notes:value,currency:value,startTime:value,endTime:value,hours:value,hourlyRate:value,totalEarnings:value};
  const before=structuredClone(row);
  for(const exporter of [T.csv,r=>T.csv(r,'\t'),S.csv,S.excel])assert.equal(typeof exporter([row]),'string');
  assert.deepEqual(row,before);
 }
 for(const value of formulas){
  const row={...base,hours:value,hourlyRate:value,totalEarnings:value,isTimeRange:false};
  for(const exporter of [S.csv,S.excel])assert.ok(exporter([row]).includes("'"+value.replaceAll('"','""')));
 }
});
test('numeric/null times become literal cells and invalid ranges do not invent breaks',()=>{
 for(const value of [null,undefined,0,930,false,{},['=1+1']]){
  const row={...base,startTime:value,endTime:value};
  const literal=String(value??'');const expected=literal.startsWith('=')?"'"+literal:literal;
  for(const [exporter,delimiter,indices] of [[T.csv,',',[8,9]],[r=>T.csv(r,'\t'),'\t',[8,9]],[S.csv,',',[2,3]],[S.excel,'\t',[2,3]]]){
   const cells=T.table(exporter([row]).replace(/^\ufeff/,''),delimiter)[1];
   for(const i of indices)assert.equal(cells[i],expected);
   if(indices[0]===2)assert.equal(cells[5],'');
  }
 }
 const cells=T.table(S.csv([{...base,hours:-0,hourlyRate:43.21,totalEarnings:301.17}]).slice(1),',')[1];
 assert.equal(cells[4],'0:00','the exact duration (M-43); -0 hours is no time');assert.equal(cells[6],'43.21');assert.equal(cells[7],'301.17');
});
test('export remains read-only for historical malformed times; JSON backup retains original fields',()=>{
 const row={...base,startTime:930,endTime:'=1+1'};
 assert.deepEqual(JSON.parse(T.encode({entries:[row]})).entries[0],T.normalize(row));
 assert.equal(T.missing([row],[row]).length,0);
});
