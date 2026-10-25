/* The owner's private dashboard. The server checks the Google account; this page only draws what it is given. */
(function () {
  'use strict';
  const $ = id => document.getElementById(id);
  const nf = new Intl.NumberFormat('he-IL');
  const regions = (() => { try { return new Intl.DisplayNames(['he'], { type: 'region' }); } catch { return null; } })();
  const DEVICES = { mobile: 'טלפון', desktop: 'מחשב', tablet: 'טאבלט' };
  const PAGES = { '/': 'דף הבית', '/admin': 'ניהול', '/repair': 'דף תיקון' };
  const esc = s => String(s).replace(/[&<>"']/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' })[c]);
  const shortDay = d => new Date(d + 'T12:00:00').toLocaleDateString('he-IL', { day: 'numeric', month: 'numeric' });
  const longDay = d => new Date(d + 'T12:00:00').toLocaleDateString('he-IL', { weekday: 'short', day: 'numeric', month: 'long' });
  let days = 30, user = null;

  function show(state, text) {
    $('gate').hidden = state !== 'gate'; $('board').hidden = state !== 'board'; $('refresh').hidden = state !== 'board';
    if (text) $('gate-text').textContent = text;
  }

  /** Columns over time. Sliding a finger across the chart shows each day's numbers in the readout above it. */
  function columns(rows, series, id) {
    const max = Math.max(1, ...rows.map(r => series.reduce((s, [k]) => s + r[k], 0)));
    const cols = rows.map((r, i) => {
      const sum = series.reduce((s, [k]) => s + r[k], 0);
      return '<div class="col' + (sum ? '' : ' zero') + '" data-i="' + i + '">' +
        series.map(([k, cls]) => r[k] ? '<span class="' + cls + '" style="height:calc(' + (r[k] / max * 100) + '% - 2px)"></span>' : '').join('') + '</div>';
    }).join('');
    readouts[id] = i => ({ when: longDay(rows[i].day), parts: series.map(([k, cls, label]) => [nf.format(rows[i][k]), label, cls]) });
    return readout(id) + '<div class="chart" id="' + id + '" data-last="' + (rows.length - 1) + '">' + cols + '</div><div class="axis"><span>' +
      shortDay(rows[0].day) + '</span><span>' + shortDay(rows[rows.length - 1].day) + '</span></div>';
  }

  function hoursChart(hours) {
    const max = Math.max(1, ...hours);
    const cols = hours.map((v, h) => '<div class="col' + (v ? '' : ' zero') + '" data-i="' + h + '">' +
      (v ? '<span style="height:calc(' + (v / max * 100) + '% - 2px)"></span>' : '') + '</div>').join('');
    const busiest = hours.indexOf(Math.max(...hours));
    readouts.hours = h => ({ when: String(h).padStart(2, '0') + ':00 עד ' + String(h).padStart(2, '0') + ':59', parts: [[nf.format(hours[h]), 'צפיות', '']] });
    return readout('hours') + '<div class="chart" id="hours" data-last="' + busiest + '">' + cols + '</div><div class="axis"><span>00</span><span>06</span><span>12</span><span>18</span><span>23</span></div>';
  }

  const readout = id => '<div class="readout" data-for="' + id + '"><span class="when"></span><span class="vals"></span></div>';
  const readouts = {};
  function pick(chart, i) {
    const r = readouts[chart.id](i), box = document.querySelector('.readout[data-for="' + chart.id + '"]');
    box.querySelector('.when').textContent = r.when;
    box.querySelector('.vals').innerHTML = r.parts.map(([n, label, cls]) => '<span class="v' + (cls ? ' ' + cls : '') + '"><b>' + n + '</b> ' + label + '</span>').join('');
    chart.classList.add('has-on');
    for (const c of chart.children) c.classList.toggle('on', +c.dataset.i === i);
  }
  /** Reads the column under the finger while it slides sideways; vertical swipes still scroll the page. */
  function wireCharts() {
    for (const chart of document.querySelectorAll('.chart')) {
      pick(chart, +chart.dataset.last);
      const at = e => {
        const box = chart.getBoundingClientRect(), n = chart.children.length;
        pick(chart, Math.max(0, Math.min(n - 1, Math.floor((e.clientX - box.left) / box.width * n))));
      };
      chart.addEventListener('pointerdown', at); chart.addEventListener('pointermove', at);
    }
  }

  function ranked(rows, name, field = 'visitors') {
    if (!rows || !rows.length) return '<p class="empty">אין נתונים בתקופה הזאת</p>';
    const max = Math.max(1, ...rows.map(r => r[field]));
    return '<div class="bars">' + rows.map(r => '<div class="bar-row"><span class="name">' + esc(name(r)) + '</span><span class="val">' + nf.format(r[field]) +
      '</span><span class="track"><i style="width:' + (r[field] / max * 100) + '%"></i></span></div>').join('') + '</div>';
  }

  const section = (title, body, caption) => '<div class="section-title"><h2>' + title + '</h2></div>' + (caption ? '<p class="caption">' + caption + '</p>' : '') + '<div class="card">' + body + '</div>';
  const kpi = (label, value, sub, wide) => '<div class="kpi' + (wide ? ' wide' : '') + '"><span class="label">' + label + '</span><span class="num">' + value + '</span>' + (sub ? '<span class="caption">' + sub + '</span>' : '') + '</div>';

  function siteHtml(site) {
    if (site.error) return '<p class="note">' + ({
      'missing-token': 'עדיין לא חובר מפתח הגישה של Vercel, ולכן נתוני הכניסות לאתר לא מוצגים.',
      'bad-token': 'מפתח הגישה של Vercel לא התקבל. כדאי ליצור מפתח חדש ולעדכן אותו.' }[site.error] || 'נתוני האתר לא זמינים כרגע. נסה לרענן בעוד רגע.') + '</p>';
    const country = r => r.country ? (regions?.of(r.country) || r.country) : 'לא ידוע';
    return '<div class="kpis">' +
      kpi('מבקרים', nf.format(site.total.visitors), 'אנשים שונים (בקירוב)') + kpi('צפיות', nf.format(site.total.pageviews), 'כל פתיחה של דף') + '</div>' +
      section('כניסות לפי יום', columns(site.daily, [['visitors', '', 'מבקרים']], 'site-days')) +
      section('שעות פעילות', hoursChart(site.hours), 'לפי שעון ניו יורק. מוצגת השעה העמוסה ביותר') +
      section('מדינות', ranked(site.country, country)) +
      section('מכשירים', ranked(site.deviceType, r => DEVICES[r.deviceType] || r.deviceType || 'לא ידוע') +
        '<div class="divider" style="margin:14px 0"></div>' + ranked(site.osName, r => r.osName || 'לא ידוע')) +
      section('דפדפנים', ranked(site.browserName, r => r.browserName || 'לא ידוע')) +
      section('מאיפה הגיעו', ranked(site.referrerHostname, r => r.referrerHostname || 'ישירות (קישור או הקלדה)')) +
      section('דפים', ranked(site.requestPath, r => PAGES[r.requestPath] || r.requestPath, 'pageviews'), 'לפי צפיות') +
      '<p class="caption foot">התוכנית החינמית של Vercel שומרת רק 31 יום אחורה. המדינה נקבעת לפי כתובת האינטרנט, כך ש-VPN או ממסר פרטיות של אייפון יכולים להראות מדינה אחרת. דפדפן עם חוסם פרסומות לא נספר.</p>';
  }

  function appHtml(app, release) {
    const head = '<div class="kpis">' +
      kpi('גרסה מופצת', esc(release.version), release.fallback ? 'לא הצלחתי לבדוק ב-GitHub, זו הגרסה השמורה' : 'Android · הורדה ישירה', true);
    if (app.error) return head + '</div><div style="margin-top:10px"><p class="note">' + (app.error === 'rules'
      ? 'Firebase עדיין לא מאפשר לחשבון שלך לקרוא את ספירת ההתקנות. צריך לפרסם את כללי Firestore המעודכנים.'
      : 'ספירת ההתקנות לא זמינה כרגע. נסה לרענן בעוד רגע.') + '</p></div>';
    const last = app.last ? new Date(app.last).toLocaleString('he-IL', { day: 'numeric', month: 'long', hour: '2-digit', minute: '2-digit' }) : 'עדיין אין';
    return head + kpi('מכשירים שנספרו', nf.format(app.total), 'מאז שהספירה התחילה') + kpi('בשבוע האחרון', nf.format(app.week), 'מכשירים חדשים בספירה') +
      kpi('התקנות חדשות', nf.format(app.new)) + kpi('עדכונים', nf.format(app.update), 'מכשיר שהיה בו כבר גרסה ישנה') +
      kpi('נספר לאחרונה', '<small>' + last + '</small>', '', true) + '</div>' +
      section('לפי יום', columns(app.daily, [['new', '', 'חדשות'], ['update', 'alt', 'עדכונים']], 'app-days') +
        '<div class="legend"><span><i style="background:var(--glow)"></i>התקנה חדשה</span><span><i style="background:var(--muted)"></i>עדכון</span></div>') +
      section('לפי גרסה', ranked(app.versions.map(v => ({ ...v, n: v.new + v.update })), v => 'גרסה ' + v.version + ' · ' + nf.format(v.new) + ' חדשות, ' + nf.format(v.update) + ' עדכונים', 'n'),
        'כל מכשיר נספר פעם אחת, בגרסה הראשונה שבה הספירה פעלה אצלו') +
      '<p class="caption foot">הספירה קיימת רק מגרסה 2.0.7 (23 באוקטובר). מכשיר נספר אחרי שהותקנה בו 2.0.7 ומעלה ונפתחה פעם אחת עם אינטרנט. מי שהוריד גרסה ישנה יותר ועוד לא עדכן לא מופיע כאן.</p>';
  }

  async function load() {
    $('stamp').textContent = 'טוען נתונים…';
    try {
      const token = await user.getIdToken();
      const r = await fetch('/api/admin?days=' + days, { headers: { Authorization: 'Bearer ' + token }, cache: 'no-store' });
      if (r.status === 403) { show('gate', 'החשבון ' + (user.email || '') + ' אינו החשבון של בעל האתר.'); $('signout').hidden = false; $('stamp').textContent = ''; return; }
      if (!r.ok) throw Error(r.status);
      const data = await r.json();
      show('board');
      $('pane-app').innerHTML = appHtml(data.app, data.release); $('pane-site').innerHTML = siteHtml(data.site);
      $('stamp').textContent = 'עודכן ' + new Date(data.generatedAt).toLocaleTimeString('he-IL', { hour: '2-digit', minute: '2-digit' });
      wireCharts();
    } catch { $('stamp').textContent = 'לא הצלחתי לטעון. בדוק חיבור ונסה לרענן.'; }
  }

  $('range').addEventListener('click', e => {
    const b = e.target.closest('button[data-days]'); if (!b) return;
    for (const x of $('range').children) x.setAttribute('aria-pressed', String(x === b));
    days = +b.dataset.days; load();
  });
  function tab(name) {
    for (const b of $('tabs').children) b.setAttribute('aria-pressed', String(b.dataset.tab === name));
    $('pane-app').hidden = name !== 'app'; $('pane-site').hidden = name !== 'site';
    try { localStorage.setItem('admin-tab', name); } catch {}
  }
  $('tabs').addEventListener('click', e => { const b = e.target.closest('button[data-tab]'); if (b) { tab(b.dataset.tab); scrollTo(0, 0); } });
  try { tab(localStorage.getItem('admin-tab') === 'site' ? 'site' : 'app'); } catch { tab('app'); }
  $('refresh').addEventListener('click', load);
  $('signout').addEventListener('click', async () => { await firebase.auth().signOut(); $('signout').hidden = true; });
  $('signin').addEventListener('click', async () => {
    const provider = new firebase.auth.GoogleAuthProvider(); provider.setCustomParameters({ prompt: 'select_account' });
    try { await firebase.auth().signInWithPopup(provider); }
    catch (e) {
      if (e.code === 'auth/popup-blocked' || e.code === 'auth/operation-not-supported-in-this-environment') return firebase.auth().signInWithRedirect(provider);
      if (e.code !== 'auth/popup-closed-by-user' && e.code !== 'auth/cancelled-popup-request') $('gate-text').textContent = 'ההתחברות נכשלה (' + (e.code || 'שגיאה') + ').';
    }
  });

  (async () => {
    try {
      const config = await (await fetch('/api/admin?config', { cache: 'no-store' })).json();
      firebase.initializeApp(config);
      firebase.auth().onAuthStateChanged(u => { user = u; if (u) load(); else { show('gate'); $('stamp').textContent = ''; } });
    } catch { $('stamp').textContent = 'שירות ההתחברות לא זמין כרגע.'; }
  })();
})();
