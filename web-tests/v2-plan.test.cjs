// Mirrors Android app/src/test/java/com/example/V2PlanTest.kt (pure parts) against the shared fixture.
const {test}=require('node:test');const assert=require('node:assert/strict');
const P=require('../web/v2/plan.js');const doc=require('./fixtures/v2-plan.json');
const rate=(o,k='rate')=>o[k]===undefined||o[k]===null?null:o[k];
const local=text=>{const [d,t]=text.split(' ');return P.startMillis(d,t);};
const series=o=>({id:o.id,category:o.category,daysMask:P.mask(o.days),startTime:o.start,endTime:o.end,hourlyRate:rate(o),currency:o.currency,fromDate:o.from,untilDate:o.until,createdAt:0});
const exception=o=>({seriesId:o.seriesId,date:o.date,cancelled:o.cancelled??false,hold:o.hold??false,startTime:o.start||null,endTime:o.end||null,rateOverridden:o.rateOverridden??false,hourlyRate:rate(o)});
const occurrencesOf=c=>P.occurrences(c.series.map(series),c.exceptions.map(exception),new Set(c.confirmed),c.range[0],c.range[1],local(c.now));

test('occurrences match the contract',()=>{
 for(const c of doc.occurrences){const got=occurrencesOf(c);assert.equal(got.length,c.expected.length,c.name);
  c.expected.forEach((w,i)=>{const g=got[i];assert.equal(g.key,w.key,c.name);assert.equal(g.start,w.start);assert.equal(g.end,w.end);assert.equal(g.rate,rate(w));assert.equal(g.status,w.status);assert.equal(g.edited,w.edited);});}
 for(const c of doc.toConfirm)assert.deepEqual(P.toConfirm(occurrencesOf(c)).map(o=>o.key),c.expected,c.name);
});
test('recorded shifts match the contract',()=>{
 for(const c of doc.toEntry){const o=c.occurrence;
  const e=P.toEntry({key:o.key,seriesId:o.seriesId,category:o.category,date:o.date,start:o.start,end:o.end,rate:rate(o),currency:o.currency,edited:false,hold:false,status:P.AWAITING},c.auto);
  const w=c.expected;assert.ok(Math.abs(e.hours-w.hours)<1e-9,c.name);assert.equal(e.hourlyRate,w.hourlyRate);assert.equal(e.totalEarnings,w.totalEarnings);assert.equal(e.payKnown,w.payKnown);
  assert.equal(e.seriesKey,w.seriesKey);assert.equal(e.autoConfirmed,w.autoConfirmed);assert.equal(e.startTime,w.startTime);assert.equal(e.endTime,w.endTime);
  assert.equal(e.currency,w.currency);assert.equal(e.date,local(w.dateLocal));assert.equal(e.isTimeRange,w.isTimeRange);assert.equal(e.category,w.category);}
});
test('preview, split and rate match the contract',()=>{
 for(const c of doc.preview){
  const saved=c.saved.map(s=>({category:'x',date:local(s.dateLocal),isTimeRange:true,startTime:s.start,endTime:s.end,hours:1,hourlyRate:1,totalEarnings:1}));
  const p=P.preview(new Set(c.days),c.from,c.until,c.start,c.end,saved,[]);
  assert.deepEqual(p.dates,c.expected.dates);assert.deepEqual(p.clashes,c.expected.clashes);
  assert.deepEqual(p.created,c.expected.dates.filter(d=>!c.expected.clashes.includes(d)));}
 for(const c of doc.split){
  const r=P.split(series(c.series),c.exceptions.map(exception),c.date,c.newId,{start:c.start,end:c.end,now:0});const w=c.expected;
  assert.equal(r.old?.untilDate??null,w.oldUntil,c.name);assert.equal(r.new.fromDate,w.newFrom);assert.equal(r.new.startTime,w.newStart);assert.equal(r.new.endTime,w.newEnd);
  assert.deepEqual(r.moved.map(x=>[x.seriesId,x.date,x.cancelled]),w.moved.map(x=>[x.seriesId,x.date,x.cancelled]));
  assert.deepEqual(r.dropped.map(x=>x.date),w.dropped);}
 for(const c of doc.applyRate){const s=series(c.series);
  const waiting=c.waiting.map(d=>({key:P.key(s.id,d),seriesId:s.id,category:s.category,date:d,start:s.startTime,end:s.endTime,rate:s.hourlyRate,currency:s.currency,edited:false,hold:false,status:P.AWAITING}));
  const {series:updated,changes}=P.applyRateFromToday(s,c.exceptions.map(exception),rate(c),c.today,waiting);
  assert.equal(updated.hourlyRate,rate(c.expected));
  assert.deepEqual(changes.map(x=>[x.date,x.rateOverridden,x.hourlyRate]),c.expected.changes.map(x=>[x.date,x.rateOverridden,rate(x)]));}
});
test('split with recorded days cancels them under the new plan',()=>{
 // Mirrors recordedOrDeletedDaysAreNeverRecordedAgain: a day recorded ahead must not return under the new keys.
 const s={id:'P',category:'מסעדה',daysMask:P.mask([1]),startTime:'09:00',endTime:'17:00',hourlyRate:50,currency:'₪',fromDate:'2026-10-05',untilDate:'2026-10-31',createdAt:0};
 const r=P.split(s,[],'2026-10-05','N',{start:'08:00',end:'16:00',now:0,recorded:new Set(['P@2026-10-12','Q@2026-10-19'])});
 assert.equal(r.old,null);assert.equal(r.new.id,'N');
 assert.deepEqual(r.moved,[{seriesId:'N',date:'2026-10-12',cancelled:true,hold:false,startTime:null,endTime:null,rateOverridden:false,hourlyRate:null}]);
 const open=P.occurrences([r.new],r.moved,new Set(),'2026-10-01','2026-10-31',P.startMillis('2026-10-20','00:00'));
 assert.ok(open.every(o=>o.date!=='2026-10-12'));
});
test('mask and days round trip',()=>{assert.equal(P.mask([0,1,2,3,4,5,6]),127);assert.deepEqual(P.days(0b1000101),[0,2,6]);assert.equal(P.mask([7,-1,3]),8);assert.equal(P.dateOfKey('a@b@2026-10-01'),'2026-10-01');});
test('overnight end is wall clock across daylight saving (Asia/Jerusalem)',()=>{
 const saved=process.env.TZ;
 try{
  process.env.TZ='Asia/Jerusalem';
  assert.equal(new Date(2026,6,1).getTimezoneOffset(),-180,'TZ change must take effect');
  for(const date of ['2026-03-26','2026-10-24']){
   const end=new Date(P.endMillis(date,'22:00','06:00'));
   assert.equal(end.getHours(),6);assert.equal(end.getMinutes(),0);
   assert.equal(P.dateOf(end.getTime()),P.dateOf(P.startMillis(date,'12:00')+86400000));
  }
  // java.util.Calendar resolves an ambiguous fall-back wall time to the later (standard) instant.
  assert.equal(P.startMillis('2026-10-25','01:30'),1792884600000);
  // A time in the spring-forward gap moves forward, like a lenient Calendar.
  assert.equal(P.startMillis('2026-03-27','02:30'),1774571400000);
 }finally{if(saved===undefined)delete process.env.TZ;else process.env.TZ=saved;}
});
