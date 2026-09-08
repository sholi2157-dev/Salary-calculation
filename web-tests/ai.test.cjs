const {test}=require('node:test');const assert=require('node:assert/strict');const A=require('../server/ai.cjs');
test('unconfigured auth fails closed without a network call',async()=>{const old=process.env.AI_ALLOWED_UIDS;delete process.env.AI_ALLOWED_UIDS;try{await assert.rejects(()=>A.authorize({headers:{}}),{status:503});}finally{if(old!==undefined)process.env.AI_ALLOWED_UIDS=old;}});
test('unknown model cannot initiate a provider request',async()=>{await assert.rejects(()=>A.generate('openai:not-allowed','x'),{status:400});});
test('GPT Responses request uses server credential and disables storage',async()=>{
 const oldFetch=global.fetch,oldKey=process.env.OPENAI_API_KEY;process.env.OPENAI_API_KEY='synthetic-test-key';
 global.fetch=async(url,options)=>{assert.equal(url,'https://api.openai.com/v1/responses');const b=JSON.parse(options.body);assert.equal(b.store,false);assert.equal(b.model,'gpt-6-astra');return {ok:true,json:async()=>({status:'completed',output:[{type:'message',content:[{type:'output_text',text:'[]'}]}]})};};
 try{assert.equal(await A.generate('openai:gpt-6-astra','extract synthetic shift'),'[]');}finally{global.fetch=oldFetch;if(oldKey===undefined)delete process.env.OPENAI_API_KEY;else process.env.OPENAI_API_KEY=oldKey;}
});
test('valid Firebase token for a different user is rejected',async()=>{
 const f=global.fetch,k=process.env.FIREBASE_WEB_API_KEY,u=process.env.AI_ALLOWED_UIDS;
 process.env.FIREBASE_WEB_API_KEY='synthetic';process.env.AI_ALLOWED_UIDS='owner';global.fetch=async()=>({ok:true,json:async()=>({users:[{localId:'stranger'}]})});
 try{await assert.rejects(()=>A.authorize({headers:{authorization:'Bearer synthetic'}}),{status:403});}finally{global.fetch=f;for(const [key,value]of [['FIREBASE_WEB_API_KEY',k],['AI_ALLOWED_UIDS',u]])if(value===undefined)delete process.env[key];else process.env[key]=value;}
});
