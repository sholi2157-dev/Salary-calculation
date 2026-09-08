const { authorize, generate } = require('../server/ai.cjs');
module.exports = async function handler(req, res) {
  res.setHeader('Cache-Control', 'no-store');
  if (req.method !== 'POST') return res.status(405).json({ error: 'Method not allowed' });
  try {
    await authorize(req);
    const body = typeof req.body === 'string' ? JSON.parse(req.body) : req.body;
    const text = await generate(body?.prompt);
    return res.status(200).json({ text });
  } catch (error) {
    return res.status(error.status || 502).json({ error: error.message || 'שירות המודלים אינו זמין כרגע' });
  }
};
