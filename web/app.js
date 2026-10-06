
    // State management
    let shifts = [];
    let currentUserId = null;
    let webPreferences=WorkCategories.preferences(), categories = [], workers = [], pendingTransfer = null, editingShiftId = null;
    try { const meta=JSON.parse(localStorage.getItem('work_transfer_meta')||'{}');categories=meta.categories||[];workers=meta.workers||[]; } catch {}
    let selectedCategory = 'הכל';

    // Set today as default in date picker
    const initialDate=new Date();document.getElementById('modal-date').value=initialDate.getFullYear()+'-'+String(initialDate.getMonth()+1).padStart(2,'0')+'-'+String(initialDate.getDate()).padStart(2,'0');

    // Local Storage Loading
    function loadLocalShifts() {
        try {
            const saved = localStorage.getItem('user_work_shifts');
            if (saved) {
                shifts = JSON.parse(saved);
            }
        } catch (e) {
            console.warn('Error loading local storage shifts:', e);
        }
    }

    function saveLocalShifts(){persistAll(shifts);}

    // Versioned sync is configured by WorkAccounts; live device E2E remains a release gate.
    function handleAuthClick() { if(window.WorkFeatures?.accountsEnabled) return window.WorkAccounts?.open(); }
    function syncToCloud() { if(window.WorkFeatures?.accountsEnabled) window.WorkAccounts?.sync(); }
    function download(name, text, type) {
        const url=URL.createObjectURL(new Blob([text],{type}));const a=document.createElement('a');a.href=url;a.download=name;a.click();setTimeout(()=>URL.revokeObjectURL(url),1000);
    }
    function backupText(){return JSON.stringify({formatVersion:2,androidLocalPreferences:WorkRuntime.androidPreferences(webPreferences),entries:shifts.map(WorkTransfer.normalize),categories:categories.map(({name,defaultRate})=>({name,defaultRate})),workers:workers.map(({name})=>({name})),webPreferences},null,2);}
    function exportJson(){download('work-backup.json',backupText(),'application/json');}
    function persistAll(next, cats=categories, people=workers, prefs=webPreferences){
        if(!storageHealthy)throw Error('הנתונים המקומיים דורשים שחזור; לא בוצע שינוי');
        // The complete backup is the authoritative atomic snapshot, including category rates.
        if(currentUserId){
            if(!window.WorkAccounts?.store())throw Error('החשבון עדיין בטעינה');
            const account=window.WorkAccounts.store();account.edit({entries:next,categories:cats,workers:people,webPreferences:prefs});
            const saved=account.data();shifts=saved.entries;categories=saved.categories;workers=saved.workers;webPreferences=WorkCategories.preferences(saved.webPreferences||{mainCurrency:localStorage.getItem('work_default_currency')});
        }else{
            localStorage.setItem('work_complete_backup',JSON.stringify({entries:next,categories:cats,workers:people,webPreferences:prefs}));
            shifts=next;categories=cats;workers=people;webPreferences=WorkCategories.preferences(prefs);
        }
    }
    function invalidateTransfer(){pendingTransfer=null;document.getElementById('transfer-save').disabled=true;}
    function openTransfer(text=''){document.getElementById('transfer-text').value=text;invalidateTransfer();document.getElementById('transfer-preferences').checked=false;document.getElementById('transfer-summary').textContent='';document.getElementById('transfer-rows').replaceChildren();document.getElementById('transfer-dialog').showModal();if(text)previewTransfer();}
    function previewTransfer(){
        invalidateTransfer();document.getElementById('transfer-rows').replaceChildren();
        try {
            pendingTransfer=WorkTransfer.decode(document.getElementById('transfer-text').value);
            const incoming=pendingTransfer.entries,added=WorkTransfer.missing(shifts,incoming);
            document.getElementById('transfer-summary').textContent=`נקראו ${incoming.length} משמרות. חדשות: ${added.length}. קיימות: ${incoming.length-added.length}.\nסכומי הקובץ: ${formatTotals(WorkTransfer.totals(incoming))}\nסכום שיתווסף: ${formatTotals(WorkTransfer.totals(added))}`;
            const table=document.createElement('table');
            for(const cells of [['קטגוריה','תאריך','שעות','תעריף','סכום','הערות'],...incoming.map(e=>[e.category,new Date(e.date).toLocaleDateString('he-IL'),e.hours,e.hourlyRate+' '+e.currency,e.totalEarnings+' '+e.currency,e.notes])]){
                const tr=document.createElement('tr');for(const value of cells){const td=document.createElement('td');td.textContent=value;tr.appendChild(td);}table.appendChild(tr);
            }
            document.getElementById('transfer-rows').appendChild(table);document.getElementById('transfer-save').disabled=false;
        } catch(e){pendingTransfer=null;document.getElementById('transfer-summary').textContent=e.message;}
    }
    function commitTransfer(){if(!pendingTransfer)return;try{
        const added=WorkTransfer.missing(shifts,pendingTransfer.entries).map(e=>({...e,id:crypto.randomUUID()}));
        const importedCats=[...pendingTransfer.categories];for(const e of pendingTransfer.entries)if(!importedCats.some(c=>c.name===e.category))importedCats.push({name:e.category,defaultRate:e.hourlyRate});
        const cats=[...categories,...importedCats.filter(c=>!categories.some(x=>x.name===c.name))];
        const people=[...workers,...pendingTransfer.workers.filter(w=>!workers.some(x=>x.name===w.name))];
        const prefs=document.getElementById('transfer-preferences').checked&&pendingTransfer.webPreferences?WorkCategories.preferences(pendingTransfer.webPreferences):webPreferences;
        persistAll([...shifts,...added],cats,people,prefs);invalidateTransfer();document.getElementById('transfer-dialog').close();if(document.getElementById('settings-dialog').open)document.getElementById('settings-dialog').close();renderShifts();syncToCloud();showMessage('הנתונים יובאו בהצלחה');
    }catch(e){document.getElementById('transfer-summary').textContent='השמירה נכשלה. הנתונים הקיימים נשמרו. '+e.message;}}
    function applyCategoryRate(){const c=categories.find(c=>c.name===document.getElementById('modal-category').value);document.getElementById('modal-rate').value=c?.defaultRate??40;document.getElementById('group-employer-rate').value=c?.defaultRate??40;document.getElementById('group-worker-rate').value=c?.defaultRate??40;document.getElementById('modal-currency').value=WorkCategories.currency(c?.name||'עצמאי',webPreferences);}
    function formatTotals(t){return Object.entries(t).map(([c,n])=>c+' '+n.toFixed(2)).join('\n')||(webPreferences.mainCurrency||localStorage.getItem('work_default_currency')||'₪')+' 0.00';}
    function setCategoryFilter(category) {
        selectedCategory = category;
        document.querySelectorAll('.chip').forEach(btn => {
            btn.classList.toggle('active', btn.innerText === category);
        });
        renderShifts();
    }

    let homeReportDraft=null;
    function openAddModal() {
        const dialog=document.getElementById('add-modal');
        if(dialog.open)return;
        // Keep the original row nodes: worker metadata and removal handlers belong to the draft.
        const form=document.getElementById('report-fields');
        homeReportDraft={
            controls:[...form.querySelectorAll('input,select,textarea')].map(control=>({control,value:control.value,checked:control.checked,readOnly:control.readOnly})),
            groupRows:[...document.getElementById('group-rows').childNodes],
            rangeHidden:document.getElementById('range-fields').hidden,
            groupHidden:document.getElementById('group-fields').hidden
        };
        editingShiftId=null;
        document.getElementById('modal-break').value=0;document.getElementById('modal-hours').value=8;document.getElementById('modal-notes').value='';
        const today=new Date();document.getElementById('modal-date').value=today.getFullYear()+'-'+String(today.getMonth()+1).padStart(2,'0')+'-'+String(today.getDate()).padStart(2,'0');
        document.getElementById('modal-range').checked=false;toggleRange();
        document.getElementById('modal-group').checked=false;document.getElementById('group-fields').hidden=true;document.getElementById('group-rows').replaceChildren();
        document.getElementById('edit-form-slot').append(document.getElementById('report-fields'));document.getElementById('add-modal').showModal();
        applyCategoryRate();
    }

    function closeAddModal() {
        document.getElementById('add-modal').close();
        if(homeReportDraft){
            document.getElementById('group-rows').replaceChildren(...homeReportDraft.groupRows);
            for(const {control,value,checked,readOnly} of homeReportDraft.controls){
                control.value=value;
                if(checked!==undefined)control.checked=checked;
                if(readOnly!==undefined)control.readOnly=readOnly;
            }
            document.getElementById('range-fields').hidden=homeReportDraft.rangeHidden;
            document.getElementById('group-fields').hidden=homeReportDraft.groupHidden;
            homeReportDraft=null;
        }
        document.getElementById('report-slot').append(document.getElementById('report-fields'));editingShiftId=null;syncFormLayout();updateGroupHours();
    }

    function saveNewShift() {
        reportError('');
        const category = document.getElementById('modal-category').value;
        if(!categories.some(c=>c.name===category)){showMessage('יש לבחור קטגוריה קיימת');return;}
        const dateVal = document.getElementById('modal-date').value;
        const date = dateVal ? new Date(dateVal+'T00:00:00').getTime() : NaN;
        if(!Number.isFinite(date)){showMessage('יש לבחור תאריך תקין');return;}
        const original=shifts.find(e=>e.id===editingShiftId);
        if(document.getElementById('modal-range').checked){try{document.getElementById('modal-hours').value=WorkTransfer.rangeHours(document.getElementById('modal-start').value,document.getElementById('modal-end').value,document.getElementById('modal-break').value,original);}catch(e){reportError(e.message);return;}}
        let hours,hourlyRate;try{hours=WorkTransfer.number(document.getElementById('modal-hours').value);hourlyRate=WorkTransfer.number(document.getElementById('modal-rate').value);}catch(e){showMessage(e.message);return;}
        if(!Number.isFinite(hours)||hours<=0||!Number.isFinite(hourlyRate)||hourlyRate<0||!Number.isFinite(hours*hourlyRate)){reportError('יש להזין שעות ותעריף תקינים');return;}
        try{WorkTransfer.number(document.getElementById('modal-break').value);}catch(e){reportError(e.message);return;}
        const notes = document.getElementById('modal-notes').value.trim();
        const isGroup=document.getElementById('modal-group').checked;let groupMembers=[],employerRate=null,workerRate=null;
        if(isGroup){try{
            employerRate=document.querySelector('.group-rates').open?WorkTransfer.number(document.getElementById('group-employer-rate').value):hourlyRate;workerRate=document.querySelector('.group-rates').open?WorkTransfer.number(document.getElementById('group-worker-rate').value):hourlyRate;
            groupMembers=[...document.querySelectorAll('#group-rows > div')].map(row=>{const name=row.querySelector('[data-field="name"]').value.trim();if(!name)throw Error('חסר שם עובד');return {...row.workerData,name,hours:WorkTransfer.number(row.querySelector('[data-field="hours"]').value),isPaid:row.querySelector('[data-field="paid"]').checked};});

        }catch(e){reportError(e.message);return;}}
        const totalEarnings = Math.round((hours * hourlyRate + Number.EPSILON) * 100) / 100;

        const newShift = {
            id: crypto.randomUUID(),
            category: category,
            date: date,
            hours: hours,
            hourlyRate: hourlyRate,
            totalEarnings: totalEarnings,
            isPaid: false,
            notes: notes,
            currency: document.getElementById('modal-currency').value,
            isTimeRange:document.getElementById('modal-range').checked,
            startTime:document.getElementById('modal-range').checked?document.getElementById('modal-start').value:null,
            endTime:document.getElementById('modal-range').checked?document.getElementById('modal-end').value:null,
            isGroupShift:isGroup,employerRate,workerRate,
            groupWorkersJson:isGroup?JSON.stringify(groupMembers):'',
            createdAt: Date.now()
        };

        try {persistAll(editingShiftId===null?[newShift,...shifts]:shifts.map(e=>e.id===editingShiftId?{...e,...newShift,id:e.id,createdAt:e.createdAt,isPaid:e.isPaid,totalEarnings:e.hours===hours&&e.hourlyRate===hourlyRate?e.totalEarnings:totalEarnings}:e));}
        catch(e){alert('השמירה נכשלה: '+e.message);return;}
        const wasEdit=editingShiftId!==null;closeAddModal();if(!wasEdit){document.getElementById('modal-notes').value='';document.getElementById('modal-break').value='0';document.getElementById('group-rows').replaceChildren();collapseReport();clearReportDraft();}syncToCloud();
        renderShifts();showMessage(wasEdit?'הדיווח עודכן בהצלחה':'הדיווח נשמר בהצלחה');
    }

    function togglePaid(id){try{persistAll(shifts.map(s=>s.id===id?{...s,isPaid:!s.isPaid}:s));renderShifts();syncToCloud();}catch(e){alert('השמירה נכשלה: '+e.message);}}
    async function deleteShift(id){const owner=currentUserId;if(await confirmAction('מחיקת משמרת','למחוק את המשמרת? פעולה זו אינה ניתנת לביטול.')){if(owner!==currentUserId)return;try{persistAll(shifts.filter(s=>s.id!==id));renderShifts();syncToCloud();showMessage('המשמרת נמחקה');}catch(e){showMessage('המחיקה לא נשמרה: '+e.message);}}}
    function editShift(id){const e=shifts.find(s=>s.id===id);if(!e)return;openAddModal();editingShiftId=id;document.getElementById('modal-category').value=e.category;const d=new Date(e.date);document.getElementById('modal-date').value=d.getFullYear()+'-'+String(d.getMonth()+1).padStart(2,'0')+'-'+String(d.getDate()).padStart(2,'0');document.getElementById('modal-hours').value=e.hours;document.getElementById('modal-rate').value=e.hourlyRate;document.getElementById('modal-currency').value=e.currency;document.getElementById('modal-notes').value=e.notes;document.getElementById('modal-break').value=WorkTransfer.inferredBreak(e);
        document.getElementById('modal-range').checked=e.isTimeRange||false;document.getElementById('range-fields').hidden=!e.isTimeRange;document.getElementById('modal-hours').readOnly=Boolean(e.isTimeRange);if(e.startTime)document.getElementById('modal-start').value=e.startTime;if(e.endTime)document.getElementById('modal-end').value=e.endTime;
        document.getElementById('modal-group').checked=e.isGroupShift||false;document.getElementById('group-fields').hidden=!e.isGroupShift;document.getElementById('group-employer-rate').value=e.employerRate??e.hourlyRate;document.getElementById('group-worker-rate').value=e.workerRate??e.hourlyRate;
        document.querySelector('.group-rates').open=Boolean(e.isGroupShift);for(const worker of JSON.parse(e.groupWorkersJson||'[]'))addGroupRow(worker);syncFormLayout();updateGroupHours();
    }
    function addGroupRow(worker={name:'',hours:0,isPaid:false}){
        const row=document.createElement('div');row.workerData=worker;row.className='group-worker-row';
        const name=document.createElement('input');name.dataset.field='name';name.value=worker.name;name.placeholder='שם עובד';name.setAttribute('aria-label','שם עובד');name.className='form-input';
        const hours=document.createElement('input');hours.type='number';hours.min=0;hours.step=0.25;hours.dataset.field='hours';hours.value=worker.hours;hours.setAttribute('aria-label','שעות העובד');hours.className='form-input';
        const label=document.createElement('label'),paid=document.createElement('input');paid.type='checkbox';paid.dataset.field='paid';paid.checked=worker.isPaid;label.append(paid,document.createTextNode('שולם לעובד'));
        const remove=document.createElement('button');remove.innerHTML=uiIcon('trash');remove.setAttribute('aria-label','הסרת עובד');remove.className='icon-btn';remove.onclick=()=>{row.remove();updateGroupHours();};hours.oninput=updateGroupHours;row.append(name,hours,label,remove);document.getElementById('group-rows').append(row);updateGroupHours();
    }
    function toggleRange(){const on=document.getElementById('modal-range').checked;document.getElementById('range-fields').hidden=!on;document.getElementById('modal-hours').readOnly=on;if(on)updateRangeHours();}
    function updateRangeHours(){if(!document.getElementById('modal-range').checked)return;try{document.getElementById('modal-hours').value=WorkTransfer.rangeHours(document.getElementById('modal-start').value,document.getElementById('modal-end').value,document.getElementById('modal-break').value);}catch{document.getElementById('modal-hours').value='';}}


    function exportCsv(){download('shifts.csv',WorkTransfer.csv(shifts),'text/csv;charset=utf-8');}
    function handleCsvImport(event){const file=event.target.files[0];if(file)file.text().then(openTransfer).catch(e=>alert(e.message));event.target.value='';}

    function escapeHtml(value){return String(value??'').replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));}
    function renderShifts() {
        const chips=document.getElementById('category-chips');chips.replaceChildren();
        for(const name of ['הכל',...new Set([...categories.map(c=>c.name),...shifts.map(s=>s.category)])]){const button=document.createElement('button');button.className='chip'+(name===selectedCategory?' active':'');button.textContent=name;button.onclick=()=>setCategoryFilter(name);chips.appendChild(button);}
        const search = document.getElementById('search-box').value.trim().toLowerCase();
        const list = document.getElementById('shifts-list');
        const expanded=new Set([...list.querySelectorAll('details[open]')].map(el=>el.dataset.shiftId));
        list.innerHTML = '';

        const now = new Date();
        const startOfMonth = new Date(now.getFullYear(), now.getMonth(), 1).getTime();
        const startOfWeek = new Date(now.getFullYear(), now.getMonth(), now.getDate() - now.getDay()).getTime();

        const totalHours=shifts.reduce((sum,e)=>sum+(Number(e.hours)||0),0);

        const filtered = WorkHistory.select(shifts,{category:selectedCategory,search,payment:document.getElementById('payment-filter').value,period:historyPeriod,month:document.getElementById('month-filter').value,from:document.getElementById('date-from').value,to:document.getElementById('date-to').value,currency:historyCurrency,sort:historySort});
        displayedEntries=filtered;


        renderCompanionViews(filtered);renderHistoryControls(filtered);renderSelectionControls();
        if (filtered.length === 0) {
            list.innerHTML = '<div class="empty-state">'+uiIcon('document')+'<strong>אין משמרות להצגה</strong><span>נסה לשנות את הסינון או להוסיף דיווח חדש</span></div>';
            return;
        }

        for(const entry of filtered){const card=createShiftCard(entry);card.open=!selectionMode&&expanded.has(String(entry.id));list.append(card);}
    }

    let currentPage=0,reportMode='clock';const pageScroll=[0,0];
    let messageTimer;function showMessage(text){const el=document.getElementById('app-message');const host=[...document.querySelectorAll('dialog[open]')].at(-1)||document.body;host.append(el);el.replaceChildren();const icon=document.querySelector('.clock-icon svg').cloneNode(true);el.append(icon,document.createTextNode(text));el.hidden=false;clearTimeout(messageTimer);messageTimer=setTimeout(()=>{el.hidden=true;},3500);}
    function showPage(page){if(page===currentPage)return;pageScroll[currentPage]=window.scrollY;const old=currentPage;currentPage=page;document.getElementById('page-home').hidden=page!==0;document.getElementById('page-history').hidden=page!==1;for(const [i,id] of ['nav-home','nav-history'].entries()){const b=document.getElementById(id);if(i===page)b.setAttribute('aria-current','page');else b.removeAttribute('aria-current');}const panel=document.getElementById(page?'page-history':'page-home');panel.classList.remove('slide-forward','slide-back');void panel.offsetWidth;panel.classList.add(page>old?'slide-forward':'slide-back');window.scrollTo({top:pageScroll[page],behavior:'instant'});}
    function summaryPage(page){const el=document.querySelector('.summary-carousel');el.children[page].scrollIntoView({behavior:matchMedia('(prefers-reduced-motion: reduce)').matches?'instant':'smooth',block:'nearest',inline:'start'});}
    function toggleReport(button){const content=document.getElementById('report-content');content.hidden=!content.hidden;button.setAttribute('aria-expanded',String(!content.hidden));syncFormLayout();renderLiveShift();saveReportDraft();}
    function setReportMode(mode){reportMode=mode;editingShiftId=null;document.getElementById('modal-range').checked=mode==='clock';toggleRange();document.getElementById('modal-group').checked=mode==='group';document.getElementById('group-fields').hidden=mode!=='group';document.querySelectorAll('.mode-switch button').forEach((b,i)=>b.setAttribute('aria-selected',String(['clock','manual','group'][i]===mode)));syncFormLayout();}

    function renderCompanionViews(filtered){
        renderSummaries();
        document.getElementById('history-summary').textContent=filtered.length+' משמרות · '+filtered.reduce((n,e)=>n+e.hours,0).toFixed(1)+' שעות · '+formatTotals(WorkTransfer.totals(filtered));
        const recent=document.getElementById('recent-list');recent.replaceChildren();const latest=[...shifts].sort((a,b)=>(b.createdAt??b.date)-(a.createdAt??a.date)).slice(0,3);
        if(!latest.length){const p=document.createElement('p');p.className='empty-state';p.textContent='המשמרת הראשונה שלך תופיע כאן';recent.append(p);}
        for(const entry of latest)recent.append(createShiftCard(entry,true));
        renderCategoryOptions();
    }
    function initAppViews(){document.getElementById('report-slot').append(document.getElementById('report-fields'));setReportMode('clock');const dialog=document.getElementById('add-modal');dialog.addEventListener('cancel',e=>{e.preventDefault();closeAddModal();});const carousel=document.querySelector('.summary-carousel');carousel.addEventListener('scroll',()=>{const left=carousel.getBoundingClientRect();let nearest=0,distance=Infinity;[...carousel.children].forEach((el,i)=>{const d=Math.abs(el.getBoundingClientRect().right-left.right);if(d<distance){distance=d;nearest=i;}});document.querySelectorAll('.pager-dots button').forEach((b,i)=>b.classList.toggle('active',i===nearest));},{passive:true});carousel.addEventListener('keydown',e=>{if(!['ArrowLeft','ArrowRight'].includes(e.key))return;e.preventDefault();const current=[...document.querySelectorAll('.pager-dots button')].findIndex(b=>b.classList.contains('active'));summaryPage(Math.max(0,Math.min(carousel.children.length-1,current+(e.key==='ArrowLeft'?1:-1))));});let start=null;const pages=document.getElementById('app-pages');pages.addEventListener('pointerdown',e=>{if(e.target.closest('input,textarea,select,button,summary,a,.summary-carousel,.category-chips,dialog'))return;start={x:e.clientX,y:e.clientY};});pages.addEventListener('pointerup',e=>{if(!start)return;const dx=e.clientX-start.x,dy=e.clientY-start.y;start=null;if(Math.abs(dx)>65&&Math.abs(dx)>Math.abs(dy)*1.5)showPage(dx>0?1:0);});pages.addEventListener('pointercancel',()=>{start=null;});}

    let storageHealthy=true;
    // Read without rewriting financial data. Corrupt snapshots block writes instead of resetting.
    function initializeApp(){
    try {const saved=WorkRuntime.loadGuest(localStorage);shifts=saved.entries;categories=saved.categories;workers=saved.workers;webPreferences=WorkCategories.preferences(saved.webPreferences);WorkRuntime.checkpoint(localStorage);}
    catch(e){storageHealthy=false;document.getElementById('report-error').hidden=false;document.getElementById('report-error').textContent='לא ניתן לקרוא את הנתונים המקומיים. יש לשחזר גיבוי לפני הוספת משמרות.';document.getElementById('report-content').hidden=false;}
    ensureCategoryCatalog();initParity();initAppViews();renderShifts();document.getElementById('modal-category').value=WorkCategories.defaultName(categories,webPreferences);applyCategoryRate();

    }
