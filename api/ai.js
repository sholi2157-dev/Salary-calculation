// No fallback to an owner/server credential, including for older clients.
module.exports = async function handler(req,res) {
  res.setHeader('Cache-Control','no-store');
  return res.status(410).json({error:'השירות המשותף הוחלף במפתח ג׳מיני אישי. יש לעדכן ולהגדיר מפתח אישי'});
};
