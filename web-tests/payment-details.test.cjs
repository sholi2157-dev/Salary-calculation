// Payment details (Zelle, Bit, bank) added to a shared report only when the user chooses; mirrors Android WorkPaymentTextTest.
const { test } = require('node:test'); const assert = require('node:assert/strict');
const S = require('../web/v2/share.js');
test('only the filled details, in a fixed order', () => {
  assert.deepEqual(S.paymentLines(' a@b.co ', '', '  '), ['Zelle: a@b.co']);
  assert.deepEqual(S.paymentLines('a@b.co', '050-1234567', 'לאומי 800 12345'), ['Zelle: a@b.co', 'ביט: 050-1234567', 'חשבון בנק: לאומי 800 12345']);
});
test('the details go before the closing thanks, or at the end', () => {
  assert.equal(S.withPayment('היי\nסכום\nתודה!', ['Zelle: x']), 'היי\nסכום\nלתשלום:\nZelle: x\nתודה!');
  assert.equal(S.withPayment('עבודה: א', ['ביט: 1']), 'עבודה: א\nלתשלום:\nביט: 1');
  assert.equal(S.withPayment('היי\nתודה!', []), 'היי\nתודה!');
});
