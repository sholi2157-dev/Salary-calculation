const {test}=require('node:test');const assert=require('node:assert/strict');const A=require('../server/ai.cjs');
test('unconfigured auth fails closed without a network call',async()=>{const old=process.env.AI_ALLOWED_UIDS;delete process.env.AI_ALLOWED_UIDS;try{await assert.rejects(()=>A.authorize({headers:{}}),{status:503});}finally{if(old!==undefined)process.env.AI_ALLOWED_UIDS=old;}});
test('single Gemini model uses server credential and rejects incomplete responses',async()=>{
 const oldFetch=global.fetch,oldKey=process.env.GEMINI_API_KEY;process.env.GEMINI_API_KEY='synthetic-test-key';
 global.fetch=async(url,options)=>{assert.equal(url,'https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash:generateContent');assert.equal(options.headers['x-goog-api-key'],'synthetic-test-key');assert.equal(JSON.parse(options.body).contents[0].parts[0].text,'synthetic shift');return {ok:true,json:async()=>({candidates:[{finishReason:'STOP',content:{parts:[{text:'[]'}]}}]})};};
 try{assert.equal(await A.generate('synthetic shift'),'[]');await assert.rejects(()=>A.generate(''),{status:400});global.fetch=async()=>({ok:true,json:async()=>({candidates:[{finishReason:'MAX_TOKENS'}]})});await assert.rejects(()=>A.generate('synthetic shift'));}finally{global.fetch=oldFetch;if(oldKey===undefined)delete process.env.GEMINI_API_KEY;else process.env.GEMINI_API_KEY=oldKey;}
});
test('valid Firebase token for a different user is rejected',async()=>{
 const f=global.fetch,k=process.env.FIREBASE_WEB_API_KEY,u=process.env.AI_ALLOWED_UIDS;
 process.env.FIREBASE_WEB_API_KEY='synthetic';process.env.AI_ALLOWED_UIDS='owner';global.fetch=async()=>({ok:true,json:async()=>({users:[{localId:'stranger'}]})});
 try{await assert.rejects(()=>A.authorize({headers:{authorization:'Bearer synthetic'}}),{status:403});}finally{global.fetch=f;for(const [key,value]of [['FIREBASE_WEB_API_KEY',k],['AI_ALLOWED_UIDS',u]])if(value===undefined)delete process.env[key];else process.env[key]=value;}
});
test('unapproved friend never reaches provider through API',async()=>{
 const handler=require('../api/ai.js');const oldFetch=global.fetch;
 const previous=Object.fromEntries(['FIREBASE_WEB_API_KEY','AI_ALLOWED_UIDS','GEMINI_API_KEY'].map(k=>[k,process.env[k]]));
 Object.assign(process.env,{FIREBASE_WEB_API_KEY:'synthetic',AI_ALLOWED_UIDS:'owner',GEMINI_API_KEY:'synthetic'});
 let calls=0;global.fetch=async(url)=>{calls++;assert.ok(url.startsWith('https://identitytoolkit.googleapis.com/'));return {ok:true,json:async()=>({users:[{localId:'friend'}]})};};
 const res={setHeader(){},status(code){this.code=code;return this;},json(body){this.body=body;return this;}};
 try{await handler({method:'POST',headers:{authorization:'Bearer synthetic'},body:{prompt:'shift',model:'openai:any'}},res);assert.equal(res.code,403);assert.equal(calls,1);}finally{global.fetch=oldFetch;for(const [k,v] of Object.entries(previous))if(v===undefined)delete process.env[k];else process.env[k]=v;}
});
