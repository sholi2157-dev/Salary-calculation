const { test } = require('node:test'), assert = require('node:assert/strict');
globalThis.KisRules = require('../web/v2/rules.js');
globalThis.KisPay = require('../web/v2/pay.js'); require('../web/kis/editor.js');
const E = globalThis.KisEditor;
globalThis.KisUi = { startOfDay: ms => { const t=new Date(ms); t.setHours(0,0,0,0); return +t; } };
const draft = hours => ({ mode:'HOURS', category:'עבודה', day:1760000000000, hours, rate:'60', bonus:'', bonusTarget:'ME', amount:'', notes:'', workers:[], start:null, end:'17:00', breakMin:0 });
test('typed dot and comma mean minutes, historical decimals remain decimal', () => {
 for (const input of ['1.40','1,40','1:40']) {
  const d=draft(E.durationEdit(input)); assert.equal(d.hours,'1:40');
  assert.equal(E.evaluate(d).minutes,100); assert.equal(E.evaluate(d).total,100);
  assert.equal(E.toEntry(d).totalEarnings,100); assert.equal(E.toEntry(d).hours,100/60);
 }
 assert.equal(E.durationDisplay('1.4'),'1:24'); assert.equal(E.durationHours('1.4'),1.4);
 for (const bad of ['1:75','1:4','24:01','0:00','1::40']) assert.equal(E.evaluate(draft(bad)).canSave,false,bad);
 assert.equal(E.evaluate(draft('24:00')).canSave,true);
});
test('unrelated edits preserve imported hours and stored amounts exactly', () => {
 const e={...E.toEntry(draft('1:40')),id:'a',hours:1.234567,hourlyRate:33.3333,totalEarnings:42.12345};
 const updated=E.toEntry({...E.fromEntry(e),notes:'הערה'},e);
 assert.equal(updated.hours,e.hours);assert.equal(updated.totalEarnings,e.totalEarnings);
 const group={...e,isGroupShift:true,groupWorkersJson:JSON.stringify([{name:'דני',hours:1.234567,custom:'keep'}])};
 const d=E.fromEntry(group);d.workers[0].name='דן';
 const worker=JSON.parse(E.toEntry(d,group).groupWorkersJson)[0];
 assert.equal(worker.hours,1.234567);assert.equal(worker.custom,'keep');
});

test('mode switches carry the current net minutes and manual groups stay manual', () => {
 const clock={...draft('8'),mode:'CLOCK',start:'09:00',end:'10:55',breakMin:15};
 const manual=E.changeMode(clock,'HOURS');assert.equal(manual.hours,'1:40');assert.equal(E.toEntry(manual).totalEarnings,100);
 const group=E.changeMode(manual,'GROUP');assert.equal(group.start,null);assert.equal(E.evaluate(group).minutes,100);
 assert.equal(E.evaluate(E.changeMode(group,'CLOCK')).minutes,100);
 assert.equal(E.changeMode({...clock,end:'11:55'},'HOURS').hours,'2:40');
});

test('duration digit editing preserves hours and admits 24:00 without changing clock rules', () => {
 assert.equal(E.durationDigits('13:40'),'1340');
 assert.equal(E.parseDuration(E.typeTime('1340',3,'5',24)[0]),'13:45');
 assert.equal(E.parseDuration('2400'),'24:00');assert.equal(E.parseDuration('2401'),null);assert.equal(E.parseTime('2400'),null);
});
