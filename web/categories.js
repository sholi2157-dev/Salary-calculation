(function(root){
'use strict';
const clone=x=>JSON.parse(JSON.stringify(x));
function preferences(value={}) {
 const result={mainCurrency:['₪','$'].includes(value.mainCurrency)?value.mainCurrency:'₪',defaultCategory:typeof value.defaultCategory==='string'?value.defaultCategory:'עצמאי',categoryCurrencies:{}};
 for(const [name,currency] of Object.entries(value.categoryCurrencies||{}))if(['₪','$'].includes(currency))Object.defineProperty(result.categoryCurrencies,name,{value:currency,enumerable:true,writable:true,configurable:true});
 return result;
}
function defaultName(categories,prefs){return categories.some(c=>c.name===prefs.defaultCategory)?prefs.defaultCategory:categories.find(c=>c.name==='עצמאי')?.name||categories[0]?.name||'עצמאי';}
function currency(name,prefs){return Object.hasOwn(prefs.categoryCurrencies,name)?prefs.categoryCurrencies[name]:'₪';}
function rename(data,oldName,newName,rate,money){
 const next=clone(data),name=newName.trim();
 if(!name)throw Error('יש להזין שם קטגוריה');
 if(next.categories.some(c=>c.name===name&&c.name!==oldName))throw Error('כבר קיימת קטגוריה בשם זה');
 if(!Number.isFinite(rate)||rate<0||!['₪','$'].includes(money))throw Error('תעריף או מטבע לא תקינים');
 const old=next.categories.find(c=>c.name===oldName);
 if(oldName&&!old)throw Error('הקטגוריה השתנתה. פתח את ההגדרות מחדש');
 next.categories=old?next.categories.map(c=>c===old?{...c,name,defaultRate:rate}:c):[...next.categories,{name,defaultRate:rate}];
 next.entries=next.entries.map(e=>oldName&&e.category===oldName?{...e,category:name}:e);
 next.webPreferences=preferences(next.webPreferences);
 if(oldName===next.webPreferences.defaultCategory)next.webPreferences.defaultCategory=name;
 if(oldName!==name)delete next.webPreferences.categoryCurrencies[oldName];
 Object.defineProperty(next.webPreferences.categoryCurrencies,name,{value:money,enumerable:true,writable:true,configurable:true});
 return next;
}
function remove(data,name){
 const next=clone(data),target=defaultName(next.categories,next.webPreferences);
 if(name===target)throw Error('לפני מחיקת קטגוריית ברירת המחדל יש לבחור קטגוריה אחרת');
 if(!next.categories.some(c=>c.name===name))throw Error('הקטגוריה לא נמצאה');
 next.categories=next.categories.filter(c=>c.name!==name);
 next.entries=next.entries.map(e=>e.category===name?{...e,category:target}:e);
 delete next.webPreferences.categoryCurrencies[name];
 return next;
}
const api={preferences,defaultName,currency,rename,remove};if(typeof module!=='undefined')module.exports=api;else root.WorkCategories=api;
})(globalThis);
