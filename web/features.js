/* Release switches. Disabled features retain their code and private stores. */
(function(root){
 'use strict';
 const WEB_ACCOUNTS_ENABLED=false;
 const features=Object.freeze({accountsEnabled:WEB_ACCOUNTS_ENABLED});
 if(typeof module!=='undefined')module.exports=features;
 else root.WorkFeatures=features;
})(globalThis);
