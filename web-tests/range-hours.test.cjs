const {test}=require('node:test');
const assert=require('node:assert/strict');
const {rangeHours,inferredBreak}=require('../web/transfer.js');
test('clock entry subtracts break once and supports overnight shifts',()=>{
 assert.equal(rangeHours('08:00','16:00',30),7.5);
 assert.equal(rangeHours('22:00','06:00',60),7);
 assert.throws(()=>rangeHours('08:00','09:00',60));
 assert.throws(()=>rangeHours('25:00','09:00',0));
 assert.throws(()=>rangeHours('08:00','09:00',-1));
});
test('editing imported notes preserves saved net hours and inferred break',()=>{
 const original={isTimeRange:true,startTime:'08:00',endTime:'16:00',hours:7.5};
 assert.equal(inferredBreak(original),30);
 assert.equal(rangeHours('08:00','16:00',30,original),7.5);
 assert.equal(rangeHours('08:00','17:00',30,original),8.5);
 assert.equal(rangeHours('08:00','16:00',60,original),7);
});
test('unchanged imported hours exceeding clock interval are not silently overwritten',()=>{
 const original={isTimeRange:true,startTime:'08:00',endTime:'16:00',hours:9};
 assert.equal(inferredBreak(original),0);
 assert.equal(rangeHours('08:00','16:00',0,original),9);
 assert.equal(rangeHours('08:00','17:00',0,original),9);
});
