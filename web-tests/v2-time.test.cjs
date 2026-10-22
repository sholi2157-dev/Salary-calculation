// Mirrors Android KisUiTest.aTimeIsEditedDigitByDigit…: a time is edited digit by digit, and a new shift starts 4 hours before its end.
const {test}=require('node:test');const assert=require('node:assert/strict');
globalThis.KisRules=require('../web/v2/rules.js');require('../web/kis/editor.js');
const E=globalThis.KisEditor;
const type=(digits,cursor,keys)=>[...keys].reduce(([d,c],k)=>E.typeTime(d,c,k),[digits,cursor]);

test('one digit changes in place',()=>{
 assert.deepEqual(type('0530',3,'5'),['0535',4]);
 assert.deepEqual(type('0530',2,'4'),['0540',3]);
 assert.deepEqual(type('0530',0,'1'),['1530',1]);
});
test('a whole time can still be typed from the start',()=>{assert.deepEqual(type('····',0,'0900'),['0900',4]);assert.deepEqual(type('1700',0,'1730'),['1730',4]);});
test('a digit that can only start a one-digit hour or minute jumps on',()=>{
 assert.deepEqual(type('1200',0,'9'),['0900',2]);
 assert.deepEqual(type('1200',2,'7'),['1207',4]);
 assert.deepEqual(type('1900',0,'2'),['2000',1],'2 then a high second digit would be invalid');
});
test('backspace clears the previous digit and "00" stops at the end',()=>{
 assert.deepEqual(E.typeTime('0530',3,'del'),['05·0',2]);
 assert.deepEqual(E.typeTime('0530',3,'00'),['0530',4]);
});
test('parse and digits',()=>{
 assert.equal(E.parseTime('0535'),'05:35');assert.equal(E.parseTime('05··'),'05:00');assert.equal(E.parseTime('2460'),null);
 assert.equal(E.timeDigits('7:05'),'0705');assert.equal(E.timeDigits(null),'····');
});
test('a new shift starts 4 hours before its end, across midnight too',()=>{assert.equal(E.defaultStart('22:30'),'18:30');assert.equal(E.defaultStart('02:00'),'22:00');});
