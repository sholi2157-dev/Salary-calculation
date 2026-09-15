(function(root,factory){if(typeof module==='object'&&module.exports)module.exports=factory();else root.PersonalAi=factory();})(typeof globalThis!=='undefined'?globalThis:this,function(){
  function createClient(fetcher){
    let key='';
    return {
      setKey(value){const clean=String(value||'').trim();if(!clean||clean.length>512||/\s/.test(clean))throw Error('יש להזין מפתח אישי תקין');key=clean;},
      clearKey(){key='';},
      async generate(prompt){
        if(!key)throw Error('יש להוסיף מפתח ג׳מיני אישי');
        if(typeof prompt!=='string'||!prompt.trim()||prompt.length>60000)throw Error('הטקסט ריק או ארוך מדי');
        let response;
        try{response=await fetcher('https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash:generateContent',{method:'POST',headers:{'Content-Type':'application/json','x-goog-api-key':key},body:JSON.stringify({contents:[{parts:[{text:prompt}]}],generationConfig:{responseMimeType:'application/json'}}),signal:AbortSignal.timeout(90000)});}catch{throw Error('לא ניתן להתחבר לג׳מיני. בדוק את החיבור ונסה שוב');}
        if(!response.ok)throw Error('ג׳מיני דחה את הבקשה ('+response.status+'). בדוק את המפתח האישי, ההרשאות והמכסה');
        const data=await response.json(),candidate=data.candidates?.[0];
        if(candidate?.finishReason!=='STOP')throw Error('ג׳מיני לא השלים את הפענוח');
        const text=candidate.content?.parts?.filter(p=>!p.thought).map(p=>p.text||'').join('');
        if(!text)throw Error('ג׳מיני לא החזיר נתונים');
        return {text};
      }
    };
  }
  return {createClient};
});
