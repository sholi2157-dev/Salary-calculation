(function(root){
function apply(entries,ids,action){
 if(!['paid','unpaid','delete'].includes(action))throw new Error('Unknown selection action');
 const selected=new Set(ids);
 return action==='delete'?entries.filter(e=>!selected.has(e.id)):entries.map(e=>selected.has(e.id)?{...e,isPaid:action==='paid'}:e);
}
const api={apply};if(typeof module!=='undefined')module.exports=api;else root.WorkSelection=api;
})(globalThis);
