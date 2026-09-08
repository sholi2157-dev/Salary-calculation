// Provider credentials stay on the server. Shared by Android and web clients.
const catalog = [
  { id: 'gemini:gemini-3.8-flash', name: 'Gemini 3.8 Flash', provider: 'gemini', model: 'gemini-3.8-flash' },
  { id: 'openai:gpt-6-astra', name: 'GPT-6 Astra', provider: 'openai', model: 'gpt-6-astra' },
  { id: 'gemini:gemini-3.5-flash', name: 'Gemini 3.5 Flash', provider: 'gemini', model: 'gemini-3.5-flash' },
  { id: 'gemini:gemini-3.1-flash-lite', name: 'Gemini 3.1 Flash-Lite', provider: 'gemini', model: 'gemini-3.1-flash-lite' }
];
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
function availableCatalog() {
  const configured = process.env.AI_MODELS_JSON ? JSON.parse(process.env.AI_MODELS_JSON) : catalog;
  if (!Array.isArray(configured) || configured.some(m => !['gemini','openai'].includes(m.provider) || !/^[a-zA-Z0-9._-]+$/.test(m.model) || m.id !== `${m.provider}:${m.model}` || typeof m.name !== 'string')) throw new Error('הגדרת רשימת המודלים אינה תקינה');
  return configured.map(m => ({ ...m, available: Boolean(process.env[m.provider === 'openai' ? 'OPENAI_API_KEY' : 'GEMINI_API_KEY']) }));
}
async function generate(modelId, prompt) {
  const model = availableCatalog().find(m => m.id === modelId);
  if (!model || !model.available) throw Object.assign(new Error('המודל אינו זמין. רענן את הרשימה ובחר מודל אחר.'), { status: 400 });
  if (typeof prompt !== 'string' || !prompt.trim() || prompt.length > 60000) throw Object.assign(new Error('הטקסט ריק או ארוך מדי'), { status: 400 });
  if (model.provider === 'openai') {
    const data = await jsonFetch('https://api.openai.com/v1/responses', { method: 'POST', headers: {
      Authorization: `Bearer ${process.env.OPENAI_API_KEY}`, 'Content-Type': 'application/json'
    }, body: JSON.stringify({ model: model.model, input: prompt, store: false, max_output_tokens: 12000 }) });
    if (data.status !== 'completed') throw new Error('המודל לא השלים את הפענוח. נסה טקסט קצר יותר.');
    const text = (data.output || []).filter(x => x.type === 'message').flatMap(x => x.content || []).filter(x => x.type === 'output_text').map(x => x.text).join('');
    if (!text) throw new Error('המודל לא החזיר נתונים לפענוח');
    return text;
  }
  const data = await jsonFetch(`https://generativelanguage.googleapis.com/v1beta/models/${model.model}:generateContent`, { method: 'POST', headers: {
    'x-goog-api-key': process.env.GEMINI_API_KEY, 'Content-Type': 'application/json'
  }, body: JSON.stringify({ contents: [{ parts: [{ text: prompt }] }], generationConfig: { responseMimeType: 'application/json' } }) });
  const candidate = data.candidates?.[0];
  if (candidate?.finishReason !== 'STOP') throw new Error('המודל לא השלים את הפענוח');
  const text = candidate.content?.parts?.filter(p => !p.thought).map(p => p.text || '').join('');
  if (!text) throw new Error('המודל לא החזיר נתונים לפענוח');
  return text;
}
module.exports = { authorize, availableCatalog, generate };
