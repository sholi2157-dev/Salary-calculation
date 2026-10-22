(function (root) {
'use strict';
/*
 * Summaries (2.0 stage D): "החודש שלך" and the story recap "החודש/השנה שלך בכיס". Port target: Android
 * domain/V2Summary.kt; expected results: web-tests/fixtures/v2-summary.json (byte-identical on both platforms).
 *
 * Row:   {date:'YYYY-MM-DD', start:'HH:mm'|null, end:'HH:mm'|null, hours, kind:'shift'|'job', payKnown, total, currency, paid, category}
 *        Only done shifts (never planned ones); the caller passes them, and rows after `today` are ignored anyway.
 * Scope: 'YYYY-MM' (a month) or 'YYYY' (a year).
 * Amounts are always per currency and never added across currencies. Jobs count toward income, not the hourly average.
 */
const r2 = v => Math.round(v * 100) / 100;
const toMin = t => { const [h, m] = t.split(':').map(Number); return h * 60 + m; };
const weekday = ymd => { const [y, m, d] = ymd.split('-').map(Number); return new Date(Date.UTC(y, m - 1, d)).getUTCDay(); };
const prevMonth = ym => { let [y, m] = ym.split('-').map(Number); m--; if (!m) { m = 12; y--; } return `${y}-${String(m).padStart(2, '0')}`; };
const timed = r => !!(r.start && r.end);
const overnight = r => timed(r) && toMin(r.end) <= toMin(r.start);

/** Minutes of a timed row in each clock hour (0–23), following the shift across midnight. */
function hourProfile(rows) {
  const out = Array(24).fill(0);
  for (const r of rows) if (timed(r)) {
    let a = toMin(r.start), b = toMin(r.end); if (b <= a) b += 1440;
    for (let t = a; t < b; t = Math.min(b, (Math.floor(t / 60) + 1) * 60)) out[Math.floor(t / 60) % 24] += Math.min(b, (Math.floor(t / 60) + 1) * 60) - t;
  }
  return out;
}

// Addressed in the plural, as Hebrew does for everyone (the same words as Android).
const PERSONAS = {
  owl: { name: 'ינשוף לילה', line: 'כשכולם הולכים לישון, אתם רק מתחממים. הקפה שלכם עובד משמרת כפולה.' },
  bird: { name: 'ציפור בוקר', line: 'השעון המעורר מפחד מכם. עד שכולם קמים, אתם כבר באמצע היום.' },
  weekend: { name: 'האנרגיה של סופ״ש', line: 'שישי-שבת? אצלכם זה פשוט עוד יומיים בלוח.' },
  marathon: { name: 'ריצת מרתון', line: 'משמרת ממוצעת של # שעות. המילה "הפסקה" כנראה לא במילון שלכם.' },
  loyal: { name: 'נאמנות מלאה', line: 'מקום אחד בלב, ועוד יותר בלוח המשמרות.' },
  juggler: { name: 'להטוטנות', line: '# מקומות עבודה, וכולם באוויר בבת אחת.' },
};
const FUN = { flights: 'טיסות לניו יורק וחזרה', episodes: 'פרקים של "חברים"', movies: 'סרטים באורך מלא', coffees: 'כוסות קפה שהספיקו להתקרר' };

function persona(list, hours, places) {
  if (!hours) return null;
  const share = pred => list.filter(pred).reduce((a, r) => a + r.hours, 0) / hours;
  const late = share(r => timed(r) && (overnight(r) || toMin(r.end) >= 22 * 60));
  const early = share(r => timed(r) && toMin(r.start) < 9 * 60);
  const weekend = share(r => [5, 6].includes(weekday(r.date)));
  const avg = hours / list.length;
  const top = (places[0]?.hours || 0) / hours;
  if (late >= 0.35) return 'owl';
  if (early >= 0.35) return 'bird';
  if (weekend >= 0.3) return 'weekend';
  if (avg >= 7) return 'marathon';
  if (top >= 0.7) return 'loyal';
  return 'juggler';
}

function summary(rows, scope, today) {
  const list = rows.filter(r => r.date.startsWith(scope) && r.date <= today);
  const hours = r2(list.reduce((a, r) => a + r.hours, 0));
  const byCur = {};
  for (const r of list) if (r.payKnown) {
    const c = byCur[r.currency] ||= { total: 0, paid: 0, unpaid: 0, rateTotal: 0, rateHours: 0 };
    c.total += r.total; if (r.paid) c.paid += r.total; else c.unpaid += r.total;
    if (r.kind !== 'job') { c.rateTotal += r.total; c.rateHours += r.hours; }
  }
  const currencies = Object.keys(byCur).sort((a, b) => byCur[b].total - byCur[a].total || (a < b ? -1 : 1)).map(cur => {
    const c = byCur[cur];
    return { currency: cur, total: r2(c.total), paid: r2(c.paid), unpaid: r2(c.unpaid), perHour: c.rateHours > 0 ? r2(c.rateTotal / c.rateHours) : null };
  });
  const pending = list.filter(r => !r.payKnown).length;
  const days = Array(7).fill(0); for (const r of list) days[weekday(r.date)] += r.hours;
  const placeMap = {}; for (const r of list) placeMap[r.category] = (placeMap[r.category] || 0) + r.hours;
  const places = Object.entries(placeMap).map(([name, h]) => ({ name, hours: r2(h) })).sort((a, b) => b.hours - a.hours || (a.name < b.name ? -1 : 1));
  const longest = list.reduce((a, r) => (!a || r.hours > a.hours || (r.hours === a.hours && r.date < a.date) ? r : a), null);
  const profile = hourProfile(list);
  const anyTimed = list.some(timed);
  const nights = list.filter(r => timed(r) && (overnight(r) || toMin(r.end) >= 23 * 60)).length;
  const bestDay = hours > 0 ? days.indexOf(Math.max(...days)) : null;
  const peakHour = anyTimed ? profile.indexOf(Math.max(...profile)) : null;
  let previous = null, trend = null;
  if (scope.length === 7) {
    const cutoff = scope === today.slice(0, 7) ? Number(today.slice(8)) : 31;
    const p = prevMonth(scope);
    previous = r2(rows.filter(r => r.date.startsWith(p) && Number(r.date.slice(8)) <= cutoff).reduce((a, r) => a + r.hours, 0));
    if (previous > 0 && hours > 0) trend = Math.round((hours - previous) / previous * 100);
  }
  const fun = [['flights', hours / 23], ['episodes', hours * 60 / 22], ['movies', hours / 2.2], ['coffees', hours * 60 / 4]].filter(([, n]) => n >= 2);
  const pick = fun.length ? fun[(list.length * 7) % fun.length] : null;
  const insights = [];
  const unpaid = currencies.filter(c => c.unpaid > 0);
  if (unpaid.length) insights.push({ kind: 'unpaid', amounts: unpaid.map(c => ({ currency: c.currency, amount: c.unpaid })) });
  if (pending) insights.push({ kind: 'pending', count: pending });
  if (trend != null && Math.abs(trend) >= 5) insights.push({ kind: 'trend', percent: trend, previousMonth: prevMonth(scope) });
  if (bestDay != null) insights.push({ kind: 'day', day: bestDay });
  return {
    scope, count: list.length, hours, currencies, pending,
    days: days.map(r2), hourMinutes: profile, places: places.slice(0, 5),
    longest: longest ? { date: longest.date, start: longest.start || null, end: longest.end || null, hours: longest.hours } : null,
    nights, bestDay, peakHour, previousHours: previous, trend,
    persona: persona(list, hours, places), fun: pick ? { key: pick[0], count: Math.round(pick[1]) } : null,
    insights: insights.slice(0, 3),
  };
}

/**
 * The story slides that have something real to say, in order (Android: V2Summary.slides, checked by the shared expected
 * results): a type needs 4 shifts, the weekday rhythm and the busiest hour 3, the longest shift 2, the places two places.
 */
function slides(s) {
  const out = ['intro', 'hours'];
  if (s.places.length >= 2) out.push('places');
  if (s.persona != null && s.count >= 4) out.push('persona');
  if (s.longest != null && s.count >= 2) out.push('longest');
  if (s.bestDay != null && s.count >= 3 && s.days.filter(d => d > 0).length >= 2) out.push('days');
  if (s.peakHour != null && s.count >= 3) out.push('hour');
  out.push('money', 'card');
  return out;
}

const api = { summary, hourProfile, PERSONAS, FUN, prevMonth, weekday, slides };
if (typeof module !== 'undefined') module.exports = api; else root.KisSummary = api;
})(typeof globalThis !== 'undefined' ? globalThis : this);
