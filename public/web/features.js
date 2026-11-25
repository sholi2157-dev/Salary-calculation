/* Release switches. Disabled features retain their code and private stores. */
(function(root){
 'use strict';
 // The pre-2.0 email/password accounts (web/accounts.js) stay off.
 const WEB_ACCOUNTS_ENABLED=false;
 // Google sign-in and account sync for the 2.0 site (web/kis/account.js, web/sync).
 const ACCOUNT_SYNC_ENABLED=true;
 const features=Object.freeze({accountsEnabled:WEB_ACCOUNTS_ENABLED,accountSyncEnabled:ACCOUNT_SYNC_ENABLED});
 if(typeof module!=='undefined')module.exports=features;
 else root.WorkFeatures=features;
})(globalThis);
