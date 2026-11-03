const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const F = require('../web/feedback.js');
const destination = 'israelshimon1313-vzptcg@mail.grokbot.com';
const now = Date.parse('2026-11-03T12:34:56Z');
const plain = x => JSON.parse(JSON.stringify(x));
function relay(config = {}) {
  const sent = [], props = { ...config };
  const ctx = vm.createContext({
    PropertiesService: { getScriptProperties: () => ({ getProperty: k => props[k], setProperties: values => Object.assign(props, values) }) },
    LockService: { getScriptLock: () => ({ waitLock() {}, releaseLock() {} }) },
    Utilities: { formatDate: () => '2026-11-03', newBlob: (body, mime, name) => ({ body, mime, name }) },
    MailApp: { sendEmail: value => sent.push(plain(value)) },
    ContentService: { MimeType: { JSON: 'json' }, createTextOutput: s => ({ setMimeType: () => JSON.parse(s) }) }
  });
  vm.runInContext(fs.readFileSync('docs/feedback-relay/Code.gs', 'utf8'), ctx);
  return { sent, post: value => ctx.doPost({ postData: { contents: JSON.stringify({ ...value, token: 'salary-feedback-v1' }) } }) };
}
test('feedback payload has delimited text and required context, with contact only when typed', () => {
  global.WorkBuild = { buildId: 'synthetic-build-sha-digest' };
  global.document = { documentElement: { lang: 'he' } };
  const r = F.report('הצעה', 'שורה ראשונה\nשורה שנייה', '', now);
  assert.deepEqual(r, { schema: 'salary-feedback-v1', kind: 'הצעה', message: 'שורה ראשונה\nשורה שנייה',
    timestamp: '2026-11-03T12:34:56.000Z', app: { platform: 'web', version: 'synthetic-build-sha-digest' }, screen: 'settings/feedback', language: 'he' });
  assert.equal(F.report('הערה', 'hello', 'typed@example.com', now).contactEmail, 'typed@example.com');
  delete global.WorkBuild; delete global.document;
});
test('relay and mail draft use only the requested destination and the same JSON body', () => {
  const r = F.report('הערה', 'synthetic feedback', '', now), server = relay();
  assert.equal(server.post(r).ok, true);
  assert.equal(server.sent[0].to, destination);
  assert.deepEqual(JSON.parse(server.sent[0].body), r);
  assert.deepEqual(JSON.parse(server.sent[0].attachments[0].body), r);
  const url = new URL(F.mailto(r));
  assert.equal(url.pathname, destination);
  assert.deepEqual(JSON.parse(url.searchParams.get('body')), r);
  assert.deepEqual([...url.searchParams.keys()], ['subject', 'body']);
});
test('web mail excludes records, tokens, diagnostics, screenshots and unknown personal metadata', () => {
  const r = F.report('תקלה', 'synthetic feedback', '', now);
  const server = relay();
  server.post({ ...r, contactEmail: '', to: 'extra@example.com', cc: 'extra@example.com', token: 'secret',
    state: { entries: 22, categories: 8, wages: 12345 }, events: [{ d: 'private' }], screenshot: { base64: 'private' },
    user: { email: 'account@example.com' }, shifts: [{ totalEarnings: 999 }], app: { ...r.app, secret: 'private' } });
  assert.deepEqual(JSON.parse(server.sent[0].body), r);
  assert.doesNotMatch(JSON.stringify(server.sent), /private|wages|totalEarnings|entries|categories|account@example|secret|token|screenshot/);
});
test('client and relay enforce length, remove controls, and reject empty messages', () => {
  const r = F.report('תקלה', '\u0000\u0007hello\t\u007f\u0085\r\n' + 'a'.repeat(2200), '', now);
  assert.equal(r.message.length, 2000); assert.ok(r.message.startsWith('hello\n'));
  assert.doesNotMatch(r.message, /[\u0000-\u0009\u000b-\u001f\u007f-\u009f]/);
  assert.throws(() => F.report('תקלה', '\u0000\t', '', now));
  const server = relay(); server.post({ ...r, message: '\u0000' + 'b'.repeat(4000) });
  assert.equal(JSON.parse(server.sent[0].body).message, 'b'.repeat(2000));
  assert.equal(server.post({ ...r, message: '\t\u0000' }).ok, false);
});
test('header and recipient injection stays JSON text and cannot change email headers', () => {
  const attack = 'hello\r\nBcc: other@example.com\n&cc=third@example.com\n"},"to":"fourth@example.com"';
  const r = F.report('תקלה\r\nBcc: other@example.com', attack, '', now), server = relay();
  assert.equal(server.post({ ...r, kind: attack }).ok, true);
  assert.equal(server.sent[0].to, destination);
  assert.equal(server.sent[0].subject, 'משוב — חישוב שכר Web');
  assert.deepEqual(Object.keys(server.sent[0]).sort(), ['attachments', 'body', 'subject', 'to']);
  const url = new URL(F.mailto(r));
  assert.deepEqual([...url.searchParams.keys()], ['subject', 'body']);
  assert.equal(JSON.parse(url.searchParams.get('body')).message, F.clean(attack));
  assert.throws(() => F.report('תקלה', 'hello', 'a@example.com\r\nBcc: b@example.com', now));
  assert.equal(server.post({ ...r, contactEmail: 'a@example.com\r\nBcc: b@example.com' }).ok, false);
});
test('server-side destination config and existing daily cap are enforced', () => {
  const r = F.report('תקלה', 'synthetic', '', now);
  const configured = relay({ FEEDBACK_TO: destination }); configured.post(r);
  assert.equal(configured.sent[0].to, destination);
  const bad = relay({ FEEDBACK_TO: destination + '\r\nBcc: extra@example.com' });
  assert.equal(bad.post(r).ok, false); assert.equal(bad.sent.length, 0);
  const server = relay();
  for (let i = 0; i < 40; i++) assert.equal(server.post(r).ok, true);
  assert.equal(server.post(r).note, 'limit'); assert.equal(server.sent.length, 40);
});

test('actual feedback send handler posts the minimal report and never reads app/account records', async () => {
  const controls = new Map();
  const control = selector => {
    if (!controls.has(selector)) controls.set(selector, { value: '', validity: { valid: true }, addEventListener(type, fn) { this[type] = fn; } });
    return controls.get(selector);
  };
  let html = '', request, closed = false;
  const el = { set innerHTML(value) { html = value; }, querySelector: control, querySelectorAll: () => [] };
  const ctx = vm.createContext({
    WorkBuild: { buildId: 'synthetic-web-build' }, document: { documentElement: { lang: 'he' } },
    localStorage: { getItem: () => null },
    KisUi: { sheet: (id, render) => render(el, () => { closed = true; }), toast() {} },
    fetch: async (url, options) => { request = { url, ...options }; return { json: async () => ({ ok: true }) }; }
  });
  vm.runInContext(fs.readFileSync('web/feedback.js', 'utf8'), ctx);
  vm.runInContext(fs.readFileSync('web/kis/settings.js', 'utf8'), ctx);
  const app = { get store() { throw Error('feedback must not access payroll'); } };
  ctx.KisSettings.feedbackSheet(app);
  assert.doesNotMatch(html, /mail\.grokbot\.com|type="file"|צילום מסך/);
  assert.match(html, /maxlength="2000"/); assert.match(html, /maxlength="254"/);
  const msg = control('[data-f=msg]'); msg.value = 'typed feedback'; msg.input();
  const email = control('[data-f=email]'); email.value = 'typed@example.com'; email.input();
  await control('[data-a=send]').onclick();
  const posted = JSON.parse(request.body);
  assert.equal(posted.message, 'typed feedback'); assert.equal(posted.contactEmail, 'typed@example.com');
  assert.equal(posted.app.version, 'synthetic-web-build'); assert.equal(posted.screen, 'settings/feedback');
  assert.deepEqual(Object.keys(posted).sort(), ['app', 'contactEmail', 'kind', 'language', 'message', 'schema', 'screen', 'timestamp', 'token']);
  assert.equal(request.method, 'POST'); assert.ok(request.url.startsWith('https://script.google.com/'));
  assert.equal(closed, true);
  const server = relay(); server.post(posted);
  assert.equal(server.sent[0].to, destination);
  assert.equal(JSON.parse(server.sent[0].body).contactEmail, 'typed@example.com');
  assert.equal('token' in JSON.parse(server.sent[0].body), false);
});

test('legacy feedback draft uses the shared destination and structured context', () => {
  let href;
  const ctx = vm.createContext({ WorkFeedback: F,
    document: { querySelector: () => ({ textContent: 'הצעה' }), getElementById: () => ({ value: 'legacy synthetic' }) },
    location: { set href(value) { href = value; } }
  });
  vm.runInContext(fs.readFileSync('web/presentation.js', 'utf8'), ctx);
  ctx.openFeedbackDraft();
  const url = new URL(href);
  assert.equal(url.pathname, destination);
  assert.equal(JSON.parse(url.searchParams.get('body')).message, 'legacy synthetic');
});
