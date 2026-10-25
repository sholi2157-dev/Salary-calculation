// Type with the device's keyboard into the duration, like the clock (a tap on it selects all of it); no product state injection.
async function setDuration(page, text) {
 const parts=text.replace(/[.,]/g, ':').split(':');
 const digits=parts[0].padStart(2,'0')+(parts[1]||'00').padStart(2,'0');
 await page.click('[data-testid=hours-input]');
 await page.keyboard.type(digits);
 await page.keyboard.press('Enter');
}
/** "01:40" on screen reads as "1:40". */
async function durationValue(page) { return (await page.textContent('[data-testid=hours-input]')).replace(/^0(\d)/, '$1'); }
module.exports={setDuration,durationValue};
