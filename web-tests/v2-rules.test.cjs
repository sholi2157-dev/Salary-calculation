// Mirrors Android app/src/test/java/com/example/V2RulesFixtureTest.kt against the shared fixture.
const {test}=require('node:test');const assert=require('node:assert/strict');
const R=require('../web/v2/rules.js');const cases=require('./fixtures/v2-rules.json');
const orNull=(o,k)=>o[k]===undefined||o[k]===null?null:o[k];

test('roundedEnd matches prototype',()=>{assert.ok(cases.roundedEnd.length>0);for(const c of cases.roundedEnd)assert.equal(R.roundedEnd(c.nowMin),c.expected,JSON.stringify(c));});
test('span and overnight match prototype',()=>{for(const c of cases.span){const r=R.span(c.start,c.end,c.breakMin);for(const k of ['minutes','overnight','invalid','long'])assert.equal(r[k],c[k],JSON.stringify(c)+' '+k);}});
test('pay evaluation matches prototype for every entry type',()=>{
 assert.ok(cases.pay.length>=15);
 for(const c of cases.pay){const i=c.input,label=JSON.stringify(c);
  const r=R.evaluate({mode:i.mode.toUpperCase(),start:orNull(i,'start'),end:orNull(i,'end'),breakMinutes:i.breakMin??0,hours:orNull(i,'hours')??'',rate:orNull(i,'rate'),
   rateUnknown:i.rateUnknown??false,bonus:i.bonus??0,bonusTarget:(i.bonusTarget??'me').toUpperCase(),amount:orNull(i,'amount')??'',amountUnknown:i.amountUnknown??false});
  assert.equal(r.total,orNull(c,'total'),label);assert.equal(r.bonus,c.bonus,label);assert.equal(r.minutes,orNull(c,'minutes'),label);assert.equal(!r.canSave,c.hasErrors,label);}
});
test('unknown pay is never zero',()=>{const r=R.evaluate({mode:'CLOCK',start:'18:00',end:'01:00',rateUnknown:true,bonus:40});assert.equal(r.total,null);assert.ok(r.canSave);});
test('quiet hours delay instead of dropping',()=>{for(const c of cases.quiet)assert.equal(R.quietDelay(c.atMin,!(c.off??false),c.from,c.to),orNull(c,'deliverAt'),JSON.stringify(c));});
test('series dates match prototype',()=>{for(const c of cases.seriesDates)assert.deepEqual(R.seriesDates(new Set(c.days),c.from,c.until),c.dates,JSON.stringify(c));});
test('calendar arithmetic round trips',()=>{assert.equal(R.weekday('1970-01-01'),4);assert.equal(R.weekday('2026-10-15'),4);
 for(const d of ['1970-01-01','2000-02-29','2024-02-29','2026-12-31','2027-03-01','2100-03-01'])assert.equal(R.civilFromDays(R.daysFromCivil(d)),d);});
test('bulk rate never touches known amounts or jobs',()=>{const c=cases.bulkRate[0];
 const records=c.shifts.map(s=>({id:s.id,isJob:s.kind==='job',start:orNull(s,'start'),end:orNull(s,'end'),breakMinutes:s.breakMin??0,hours:orNull(s,'hours'),rate:orNull(s,'rate'),bonus:s.bonus??0}));
 const r=R.bulkRate(records,c.rate);assert.deepEqual(r.updated,c.updated.map(u=>({id:u.id,total:u.total})));assert.equal(r.skipped,c.skipped);});
test('Kotlin number and time edge cases',()=>{
 assert.equal(R.round2(1.005),1.01);assert.equal(R.round2(2.675),2.68);
 assert.equal(R.toMinutes('abc'),0);assert.equal(R.toMinutes(' 7 : 305'),450);assert.equal(R.toMinutes('9'),540);
 assert.ok(R.isTime(' 9:05 '));assert.ok(!R.isTime('24:00'));assert.ok(!R.isTime('9:60'));assert.ok(!R.isTime(null));
 assert.equal(R.fromMinutes(-30),'23:30');assert.equal(R.fromMinutes(1440+75),'01:15');
 assert.equal(R.evaluate({mode:'HOURS',hours:'abc',rate:10}).canSave,false);
 assert.equal(R.evaluate({mode:'HOURS',hours:'1.5d',rate:10}).total,15);
});
