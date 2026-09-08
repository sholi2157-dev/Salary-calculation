// Provider credentials stay on the server. Shared by Android and web clients.
const MODEL = 'gemini-3.5-flash';
async function jsonFetch(url, options = {}) {
  const response = await fetch(url, { ...options, signal: AbortSignal.timeout(90000) });
  if (!response.ok) throw new Error(`שירות המודל החזיר שגיאה ${response.status}. בדוק הרשאה, מכסה וזמינות המודל.`);
  return response.json();
}
async function authorize(req) {
  const apiKey = process.env.FIREBASE_WEB_API_KEY;
  const allowed = (process.env.AI_ALLOWED_UIDS || '').split(',').map(x => x.trim()).filter(Boolean);
  if (!apiKey || !allowed.length) throw Object.assign(new Error('שירות הבינה המלאכותית עדיין לא הוגדר בשרת'), { status: 503 });
  const token = (req.headers.authorization || '').match(/^Bearer (\S+)$/)?.[1];
  if (!token) throw Object.assign(new Error('נדרשת התחברות לחשבון לשימוש במודלים שבשרת'), { status: 401 });
  // Firebase's project-scoped API verifies the ID token; never trust a decoded UID.
  const result = await jsonFetch(`https://identitytoolkit.googleapis.com/v1/accounts:lookup?key=${encodeURIComponent(apiKey)}`, {
    method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ idToken: token })
  });
  if (!result.users?.some(u => allowed.includes(u.localId) && !u.disabled)) throw Object.assign(new Error('לחשבון זה אין גישה לשירות'), { status: 403 });
}
async function generate(prompt) {
  if (!process.env.GEMINI_API_KEY) throw Object.assign(new Error('שירות הפענוח עדיין לא הוגדר'), {status:503});
  if (typeof prompt !== 'string' || !prompt.trim() || prompt.length > 60000) throw Object.assign(new Error('הטקסט ריק או ארוך מדי'), {status:400});
  const data = await jsonFetch(`https://generativelanguage.googleapis.com/v1beta/models/${MODEL}:generateContent`, { method: 'POST', headers: {
    'x-goog-api-key': process.env.GEMINI_API_KEY, 'Content-Type': 'application/json'
  }, body: JSON.stringify({ contents: [{ parts: [{ text: prompt }] }], generationConfig: { responseMimeType: 'application/json' } }) });
  const candidate = data.candidates?.[0];
  if (candidate?.finishReason !== 'STOP') throw new Error('המודל לא השלים את הפענוח');
  const text = candidate.content?.parts?.filter(p => !p.thought).map(p => p.text || '').join('');
  if (!text) throw new Error('המודל לא החזיר נתונים לפענוח');
  return text;
}
module.exports = { authorize, generate };
