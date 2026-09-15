(function(){
'use strict';
let account=null,transport=null,ready=false,cloudEnabled=false;
// Use the reviewed guest state already loaded by the existing app; never adopt implicitly.
const guest=()=>JSON.parse(localStorage.getItem('work_complete_backup')||JSON.stringify({entries:shifts,categories,workers}));
let guestData=guest();
const panel=document.createElement('dialog');panel.id='account-dialog';
panel.innerHTML='<form><h2>חשבון משתמש</h2><p>הנתונים המקומיים נשארים בנפרד. בכניסה לחשבון טפסים שלא נשמרו ייסגרו.</p><label>דוא״ל<input name="email" type="email" autocomplete="username" required class="form-input"></label><label>סיסמה<input name="password" type="password" autocomplete="current-password" required class="form-input"></label><label><input name="register" type="checkbox">יצירת חשבון חדש</label><label id="confirm-password-label" hidden>אישור סיסמה<input name="confirmation" type="password" autocomplete="new-password" class="form-input"></label><p role="status"></p><button class="btn-primary" type="submit">המשך</button><button type="button" data-action="reset" class="btn-secondary">שכחתי סיסמה</button><button type="button" data-action="close" class="btn-secondary">סגור</button></form>';
document.body.append(panel);const form=panel.querySelector('form'),message=panel.querySelector('[role="status"]');
form.elements.register.onchange=()=>{const on=form.elements.register.checked;document.getElementById('confirm-password-label').hidden=!on;form.elements.confirmation.required=on;};
function lock(on){for(const control of form.elements)control.disabled=on;}
form.onsubmit=async event=>{
 event.preventDefault();if(!ready){message.textContent='החיבור לחשבון אינו זמין כעת';return;}
 if(form.elements.register.checked&&form.elements.password.value!==form.elements.confirmation.value){message.textContent='הסיסמאות אינן זהות';return;}
 const email=form.elements.email.value.trim(),password=form.elements.password.value,register=form.elements.register.checked;lock(true);
 try{if(register)await firebase.auth().createUserWithEmailAndPassword(email,password);else await firebase.auth().signInWithEmailAndPassword(email,password);panel.close();}
 catch{message.textContent='ההתחברות לא הושלמה. בדוק את הפרטים והחיבור ונסה שוב.';}
 finally{form.elements.password.value='';form.elements.confirmation.value='';lock(false);}
};
panel.querySelector('[data-action="close"]').onclick=()=>panel.close();
panel.addEventListener('close',()=>{form.elements.password.value='';form.elements.confirmation.value='';});
panel.querySelector('[data-action="reset"]').onclick=async()=>{
 if(!ready||!form.elements.email.reportValidity())return;lock(true);
 try{await firebase.auth().sendPasswordResetEmail(form.elements.email.value.trim());message.textContent='אם ניתן לאפס סיסמה לכתובת הזו, יישלח אליה קישור.';}
 catch{message.textContent='לא ניתן לשלוח בקשת איפוס כעת';}finally{lock(false);}
};
const actions=document.createElement('div');actions.hidden=true;
for(const [label,action] of [['סנכרון עכשיו',()=>sync()],['סקירת שינויים מתנגשים',review],['העתקת הנתונים המקומיים לחשבון',adopt]]){const button=document.createElement('button');button.className='btn-secondary';button.textContent=label;button.onclick=action;actions.append(button);}
document.querySelector('.account-row').after(actions);
function status(text){document.getElementById('user-status-text').textContent=text;}
function display(data){shifts=data.entries;categories=data.categories;workers=data.workers;renderShifts();}
async function sync(){
 const source=account;if(!cloudEnabled||!source||source.busy||editingShiftId!==null)return;
 try{status('מסנכרן…');const conflicts=await source.sync(transport);if(account!==source)return;display(source.data());status(conflicts?conflicts+' שינויים דורשים בחירה':'הסנכרון הושלם');}
 catch{if(account===source)status('ממתין לחיבור. השינויים נשמרו בדפדפן');}
}
function describe(r){if(r.deleted)return 'הרשומה נמחקה';const data=JSON.parse(r.payload);if(r.type==='entry'){const e=data.entries[0];return e.category+' · '+e.totalEarnings+' '+e.currency+'\n'+(e.notes||'');}return (data.categories[0]||data.workers[0]).name;}
function review(){
 const source=account;if(!source)return;const row=source.conflicts()[0];if(!row){alert('אין שינויים מתנגשים');return;}
 const choice=prompt('במכשיר:\n'+describe(row)+'\n\nבענן:\n'+describe(row.conflict)+'\n\nכתוב 1 לשמירת גרסת המכשיר, 2 לקבלת גרסת הענן. ביטול ישאיר את שתי הגרסאות.');
 if(account!==source||!['1','2'].includes(choice))return;source.resolve(row.syncId,choice==='1');display(source.data());sync();
}
function adopt(){
 if(!account)return;const source=account;const added=WorkTransfer.missing(shifts,guestData.entries);
 if(!confirm('להעתיק לחשבון '+added.length+' משמרות חדשות? הנתונים המקומיים יישארו במקומם.'))return;
 if(account!==source)return;persistAll([...shifts,...added], [...categories,...guestData.categories.filter(c=>!categories.some(x=>x.name===c.name))], [...workers,...guestData.workers.filter(w=>!workers.some(x=>x.name===w.name))]);renderShifts();sync();
}
async function open(){
 if(currentUserId){if(confirm('להתנתק? הנתונים נשארים בחשבון בדפדפן. טפסים שלא נשמרו ייסגרו.'))await firebase.auth().signOut();return;}
 message.textContent=ready?'':'שירות החשבון עדיין נטען';panel.showModal();
}
window.WorkAccounts={open,sync,store:()=>account};
(async()=>{try{
 if(typeof firebase==='undefined')throw Error('SDK unavailable');
 const response=await fetch('/api/config');if(!response.ok)throw Error('Config unavailable');const config=await response.json();if(!config.firebase)throw Error('Config missing');
 if(!firebase.apps.length)firebase.initializeApp(config.firebase);
 cloudEnabled=config.cloudSyncEnabled===true;transport=WorkCloud.firestoreTransport(firebase.firestore(),firebase.auth());
 firebase.auth().onAuthStateChanged(user=>{
  if(!currentUserId)guestData=guest();
  currentUserId=user?.uid||null;account=currentUserId?new WorkCloud.Store(localStorage,currentUserId,()=>firebase.auth().currentUser?.uid):null;
  pendingTransfer=null;editingShiftId=null;if(typeof exitSelection==='function')exitSelection();
  for(const dialog of document.querySelectorAll('dialog[open]'))dialog.close();
  form.reset();document.getElementById('confirm-password-label').hidden=true;
  document.getElementById('modal-notes').value='';document.getElementById('transfer-text').value='';
  display(account?account.data():guestData);actions.hidden=!account;
  document.getElementById('auth-btn').textContent=account?'התנתקות':'התחברות';
  status(account?(cloudEnabled?'מחובר · ממתין לסנכרון':'מחובר · סנכרון הענן עדיין אינו פעיל'):'שימוש מקומי — ללא סנכרון');
  ready=true;sync();
 });
 }catch{status('שימוש מקומי · שירות החשבון אינו זמין כעת');}
})();
window.addEventListener('online',sync);setInterval(sync,15000);
})();
