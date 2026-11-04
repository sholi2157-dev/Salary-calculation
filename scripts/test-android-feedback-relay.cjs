// Android-only contract test of the existing Apps Script; no mail or network requests.
const {readFileSync} = require('node:fs');
const vm = require('node:vm');
const assert = require('node:assert/strict');
let mails = [], properties = {};
const sandbox = {
  MailApp: {sendEmail: mail => mails.push(mail)},
  PropertiesService: {getScriptProperties: () => ({getProperty: k => properties[k], setProperties: values => Object.assign(properties, values)})},
  Utilities: {formatDate: () => '2026-11-03'},
  LockService: {getScriptLock: () => ({waitLock() {}, releaseLock() {}})},
  ContentService: {MimeType: {JSON: 'json'}, createTextOutput: text => ({text, setMimeType() {return this;}})}
};
vm.createContext(sandbox);
vm.runInContext(readFileSync('docs/feedback-relay/Code.gs', 'utf8'), sandbox);
const input = () => ({schema:'android-feedback-v1', kind:'תקלה', message:'hello\r\nBcc: extra@example.com\u0000',
  sentAt:1793700000000, language:'he-IL', app:{platform:'android',version:'2.3.0',code:50,buildType:'release'},
  token:'must-not-mail', state:{wages:999}, events:['private'], screenshot:{base64:'private'},
  to:'extra@example.com', contactEmail:'typed@example.com\r\nBcc: extra@example.com'});
const send = value => JSON.parse(sandbox.androidFeedback_(value).text);
assert.equal(send(input()).ok, true);
assert.equal(mails.length, 1);
assert.equal(mails[0].to, 'israelshimon1313-vzptcg@mail.grokbot.com');
assert.deepEqual(Object.keys(mails[0]).sort(), ['body','subject','to']);
assert.equal(mails[0].subject, 'Android feedback');
const payload = JSON.parse(mails[0].body);
assert.deepEqual(Object.keys(payload).sort(), ['app','contactEmail','kind','language','message','schema','screen','sentAt']);
assert.deepEqual(payload.app, {platform:'android',version:'2.3.0',code:50,buildType:'release'});
assert.equal(payload.message, 'hello\nBcc: extra@example.com');
assert.equal(payload.screen, 'settings/feedback');
assert.equal(payload.language, 'he-IL');
assert.equal(payload.sentAt, 1793700000000);
assert.equal(payload.contactEmail, 'typed@example.comBcc: extra@example.com');
assert(!mails[0].body.includes('private'));
assert(!mails[0].body.includes('must-not-mail'));
assert.equal(send({...input(),message:'x'.repeat(4001)}).ok, false);
assert.equal(send({...input(),message:'\r\u0000'}).ok, false);
assert.equal(mails.length, 1);
assert.equal(send({...input(),message:'x'.repeat(4000),contactEmail:''}).ok, true);
assert(!Object.hasOwn(JSON.parse(mails[1].body), 'contactEmail'));
properties.ANDROID_FEEDBACK_TO = 'inbox@example.com\r\nBcc: extra@example.com';
assert.equal(send(input()).ok, false);
assert.equal(mails.length, 2);
delete properties.ANDROID_FEEDBACK_TO;
const post = value => JSON.parse(sandbox.doPost({postData:{contents:JSON.stringify(value)}}).text);
const valid = {...input(), token:vm.runInContext('TOKEN', sandbox)};
assert.equal(post(valid).ok, true);
assert.equal(mails[2].to, 'israelshimon1313-vzptcg@mail.grokbot.com');
properties.day = '2026-11-03'; properties.count = '40';
assert.equal(post(valid).note, 'limit');
assert.equal(mails.length, 3);
assert.equal(post({...valid,token:'invalid'}).note, 'token');
assert.equal(mails.length, 3);
console.log('Android feedback relay: all assertions passed (recipient, JSON context, privacy, limits, header injection).');
