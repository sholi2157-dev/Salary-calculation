// Drive the same four selectable digits used by the clock; no product state injection.
async function setDuration(page, text) {
 const parts=text.replace(/[.,]/g, ':').split(':');
 const digits=parts[0].padStart(2,'0')+(parts[1]||'00').padStart(2,'0');
 await page.click('[data-testid=duration-hours]');
 for(const key of digits) await page.click('[data-testid=key-'+key+']');
}
async function durationValue(page) { return page.textContent('[data-testid=hours-input]'); }
module.exports={setDuration,durationValue};
