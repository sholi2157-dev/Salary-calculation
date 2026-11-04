// ממסר משוב ל"חישוב שכר": מקבל דיווח מהאפליקציה או מהאתר ושולח מייל מהחשבון שלך.
// אין לשנות שום דבר למטה חוץ מ-TO אם רוצים כתובת אחרת.

const TO = 'sholi2157+salaryfeedback@gmail.com';
const TOKEN = 'salary-feedback-v1';      // אותו ערך נמצא באפליקציה ובאתר, מסנן בקשות אקראיות
const DAILY_LIMIT = 40;                   // הגנה מהצפה. Gmail רגיל מאפשר כ-100 מיילים ביום
const MAX_BYTES = 6 * 1024 * 1024;        // תמונה + JSON, עד 6MB

function doPost(e) {
  try {
    const raw = e && e.postData ? e.postData.contents : '';
    if (!raw || raw.length > MAX_BYTES) return reply_(false, 'size');
    const report = JSON.parse(raw);
    if (report.token !== TOKEN) return reply_(false, 'token');
    if (!underDailyLimit_()) return reply_(false, 'limit');
    // Android has its own recipient and strict payload; the website path below stays unchanged.
    if (report.app && report.app.platform === 'android') return androidFeedback_(report);

    const shot = report.screenshot;       // { name, mime, base64 } או null
    delete report.screenshot;
    delete report.token;

    const kind = String(report.kind || 'משוב').slice(0, 20);
    const platform = report.app ? String(report.app.platform || '') : '';
    const version = report.app ? String(report.app.version || '') : '';
    const message = String(report.message || '').slice(0, 5000);
    const stamp = Utilities.formatDate(new Date(), 'Asia/Jerusalem', 'yyyy-MM-dd_HH-mm');

    const attachments = [
      Utilities.newBlob(JSON.stringify(report, null, 2), 'application/json', 'report-' + stamp + '.json')
    ];
    if (shot && shot.base64) {
      const mime = /^image\/(png|jpeg|webp)$/.test(shot.mime) ? shot.mime : 'image/png';
      attachments.push(Utilities.newBlob(Utilities.base64Decode(shot.base64), mime, 'screenshot-' + stamp + '.' + mime.split('/')[1]));
    }

    MailApp.sendEmail({
      to: TO,
      subject: '[' + kind + '] חישוב שכר ' + platform + ' ' + version,
      body: message + '\n\n---\nסוג: ' + kind + '\nפלטפורמה: ' + platform + '\nגרסה: ' + version + '\nהדיווח המלא מצורף כקובץ JSON.',
      attachments: attachments
    });
    return reply_(true);
  } catch (err) {
    return reply_(false, 'error');
  }
}

// פתיחת הקישור בדפדפן מראה שהסקריפט חי
function doGet() { return reply_(true, 'alive'); }

function underDailyLimit_() {
  const props = PropertiesService.getScriptProperties();
  const day = Utilities.formatDate(new Date(), 'Asia/Jerusalem', 'yyyy-MM-dd');
  const lock = LockService.getScriptLock();
  lock.waitLock(5000);
  try {
    const count = props.getProperty('day') === day ? Number(props.getProperty('count') || 0) : 0;
    if (count >= DAILY_LIMIT) return false;
    props.setProperties({ day: day, count: String(count + 1) });
    return true;
  } finally { lock.releaseLock(); }
}

function reply_(ok, note) {
  return ContentService.createTextOutput(JSON.stringify({ ok: ok, note: note || '' }))
    .setMimeType(ContentService.MimeType.JSON);
}

// להרצה ידנית פעם אחת: מאשר הרשאת שליחת מייל ושולח מייל בדיקה
function testSend() {
  const fake = { postData: { contents: JSON.stringify({
    token: TOKEN, kind: 'בדיקה', message: 'מייל בדיקה מהממסר', app: { platform: 'test', version: '0' }, events: []
  }) } };
  Logger.log(doPost(fake).getContent());
}

// Set the Script Property ANDROID_FEEDBACK_TO on the existing deployment (no new secret/provider).
const ANDROID_FEEDBACK_DEFAULT_TO = 'israelshimon1313-vzptcg@mail.grokbot.com';
function androidFeedback_(input) {
  const clean = (value, max) => String(value || '').replace(/[\x00-\x09\x0b-\x1f\x7f-\x9f\p{Cf}]/gu, '').slice(0, max);
  // Validate before sending; never attach the raw request, logs, screenshot or transport token.
  if (input.schema !== 'android-feedback-v1' || typeof input.message !== 'string' ||
      input.message.length > 4000 || !clean(input.message, 4000).trim() ||
      !Number.isSafeInteger(input.sentAt) || !Number.isSafeInteger(input.app.code)) return reply_(false, 'payload');
  const report = {
    schema: 'android-feedback-v1', kind: clean(input.kind, 20).replace(/\n/g, ''),
    message: clean(input.message, 4000).trim(), sentAt: input.sentAt,
    screen: 'settings/feedback', language: clean(input.language, 40).replace(/\n/g, ''),
    app: {platform: 'android', version: clean(input.app.version, 80), code: input.app.code,
      buildType: clean(input.app.buildType, 40)}
  };
  const contact = clean(input.contactEmail, 254).replace(/\n/g, '').trim();
  if (contact) report.contactEmail = contact; // Body only: never a recipient, header or replyTo.
  const to = PropertiesService.getScriptProperties().getProperty('ANDROID_FEEDBACK_TO') || ANDROID_FEEDBACK_DEFAULT_TO;
  if (!/^[^\s@,;<>]+@[^\s@,;<>]+\.[^\s@,;<>]+$/.test(to)) return reply_(false, 'config');
  MailApp.sendEmail({to: to, subject: 'Android feedback', body: JSON.stringify(report, null, 2)});
  return reply_(true);
}
