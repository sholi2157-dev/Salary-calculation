/* Feedback has no access to application records, accounts, storage or diagnostics. */
(function (root) {
'use strict';
const destination = 'israelshimon1313-vzptcg@mail.grokbot.com';
const maxLength = 2000;
const clean = (value, max = maxLength) => String(value || '').replace(/\r\n?/g, '\n').replace(/[\u0000-\u0009\u000b-\u001f\u007f-\u009f]/g, '').trim().slice(0, max);
function report(kind, message, contactEmail = '', now = Date.now()) {
  const text = clean(message);
  if (!text) throw Error('message');
  const email = String(contactEmail || '').trim();
  if (email && (email.length > 254 || !/^[a-zA-Z0-9.!#$%&'*+/=?^_`{|}~-]+@[a-zA-Z0-9-]+(?:\.[a-zA-Z0-9-]+)+$/.test(email))) throw Error('email');
  const result = {
    schema: 'salary-feedback-v1', kind: ['תקלה', 'הצעה', 'הערה'].includes(kind) ? kind : 'הערה',
    message: text, timestamp: new Date(now).toISOString(),
    app: { platform: 'web', version: clean(root.WorkBuild?.buildId || 'unknown', 120) },
    screen: 'settings/feedback', language: clean(root.document?.documentElement?.lang || 'he', 20)
  };
  if (email) result.contactEmail = email;
  return result;
}
function mailto(value) {
  return 'mailto:' + destination + '?subject=' + encodeURIComponent('משוב — חישוב שכר Web') + '&body=' + encodeURIComponent(JSON.stringify(value, null, 2));
}
root.WorkFeedback = { maxLength, clean, report, mailto };
if (typeof module === 'object') module.exports = root.WorkFeedback;
})(globalThis);
