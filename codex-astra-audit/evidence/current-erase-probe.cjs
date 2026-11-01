const base='/workspace/salary-audit/sources/web-latest/';
global.WorkRuntime=require(base+'web/runtime.js');global.WorkCategories=require(base+'web/categories.js');
const {createStore}=require(base+'web/kis/store.js');require(base+'web/kis/account.js');
class Storage{constructor(){this.m=new Map()}getItem(k){return this.m.get(k)??null}setItem(k,v){this.m.set(k,String(v))}removeItem(k){this.m.delete(k)}}
(async()=>{const st=new Storage(),s=createStore(st);s.load();s.startTimer('סינתטי',60,'₪');s.setLocal('name','סינתטי');const before=s.timer();await KisAccount.eraseAll(s);console.log(JSON.stringify({timerBefore:before,timerAfter:s.timer(),nameAfter:s.local.name,entries:s.data.entries.length},null,2));})()
