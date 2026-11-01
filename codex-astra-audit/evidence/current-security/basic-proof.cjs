const fs=require('node:fs'),vm=require('node:vm'),assert=require('node:assert/strict');
const repo='/workspace/salary-audit/sources/web-latest';
const transfer=require(repo+'/web/transfer.js'),share=require(repo+'/web/v2/share.js');
const e={category:'synthetic',date:1700000000000,hours:1,hourlyRate:40,totalEarnings:40,isPaid:false,notes:'',currency:'₪',isTimeRange:false,startTime:'=1+1',endTime:null};
const parsed=transfer.decode(JSON.stringify({entries:[e]})).entries;
const csv=share.csv(parsed),tsv=share.excel(parsed);
assert(csv.includes(',=1+1,'));assert(tsv.includes('\t=1+1\t'));
let numericError;
try {share.csv(transfer.decode(JSON.stringify({entries:[{...e,startTime:123}]})).entries);}catch(error){numericError=error.message;}
assert(numericError?.includes('includes'));
(async()=>{
 const anchor={href:'/api/download'},release={textContent:''},progress={textContent:''};
 const element={isConnected:true,classList:{add(){}},querySelectorAll:()=>[],querySelector:q=>q==='[data-testid=apk-download]'?anchor:q==='[data-release]'?release:progress};
 const context={KisUi:{ICON:{close:''},sheet:(id,render)=>render(element,()=>{})},navigator:{userAgent:'audit'},fetch:async()=>{throw Error('synthetic metadata outage')},AbortSignal};
 vm.runInNewContext(fs.readFileSync(repo+'/web/kis/download.js','utf8'),context);context.KisDownload.open();await new Promise(setImmediate);
 assert(anchor.href.includes('2.0.3'));
 const result={csvFormula:{accepted:true,csv:csv.split('\r\n')[1],tsv:tsv.split('\n')[1],spreadsheetExecuted:false},numericTimeExport:{accepted:true,error:numericError},metadataOutage:{href:anchor.href,label:release.textContent,serverFallback:require(repo+'/lib/server/android-release.cjs').FALLBACK.version}};
 fs.writeFileSync('/workspace/salary-audit/current-security/basic-proof.json',JSON.stringify(result,null,2));console.log(JSON.stringify(result,null,2));
})();
