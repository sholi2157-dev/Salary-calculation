// 2.4 task 07 (idea C): "N משמרות לא שולמו מעל 30 יום · ₪X" per workplace and currency. Display only: each line's amount is the
// plain sum of the saved per-shift amounts, unknown amounts are counted as unknown (never ₪0), ₪ and $ stay apart, nothing is
// stored. Synthetic data only; the same fixture is checked on Android (WorkUnpaidAgingTest).
const { test } = require('node:test'), assert = require('node:assert/strict'), path = require('node:path'), fs = require('node:fs');
const P = require('../web/v2/pay.js');
const doc = JSON.parse(fs.readFileSync(path.join(__dirname, 'fixtures', 'unpaid-aging.json'), 'utf8'));
const at = (day, time) => { const [y, m, d] = day.split('-').map(Number), [h, mi] = time.split(':').map(Number); return new Date(y, m - 1, d, h, mi).getTime(); };
const dayOf = ms => { const d = new Date(ms); return [d.getFullYear(), String(d.getMonth() + 1).padStart(2, '0'), String(d.getDate()).padStart(2, '0')].join('-'); };
const entries = () => doc.entries.map(e => ({ ...e, date: at(e.day, e.time) }));
const money = (v, c) => c + (Math.round(v * 100) % 100 === 0 ? Math.round(v).toLocaleString('en-US') : v.toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 }));

test('the synthetic dataset is the realistic one: 22 shifts, 8 workplaces, both currencies, unknown amounts, team shifts and jobs', () => {
  const e = doc.entries;
  assert.equal(e.length, 22); assert.equal(new Set(e.map(x => x.category)).size, 8); assert.equal(doc.categories.length, 8);
  assert.deepEqual([...new Set(e.map(x => x.currency))].sort(), ['$', '₪']);
  assert.ok(e.some(x => !x.payKnown) && e.some(x => x.isGroupShift) && e.some(x => x.kind === 'job') && e.some(x => x.isPaid));
});

test('lines, counts, ranges and wording match the shared fixture', () => {
  const got = P.unpaidAging(entries(), at(doc.now.day, doc.now.time));
  assert.deepEqual(got.map(g => ({ category: g.category, currency: g.currency, count: g.count, amount: g.amount == null ? null : Math.round(g.amount * 100) / 100,
    unknown: g.unknown, from: dayOf(g.from), to: dayOf(g.to), line: P.agingLine(g, money) })), doc.expected);
});

test('each amount equals the sum of the saved amounts of exactly the old unpaid shifts (independent recount)', () => {
  const now = at(doc.now.day, doc.now.time), list = entries(), got = P.unpaidAging(list, now);
  const cutoff = new Date(now); cutoff.setHours(0, 0, 0, 0); cutoff.setDate(cutoff.getDate() - 30);
  const old = list.filter(e => !e.isPaid && e.date < cutoff.getTime());
  assert.deepEqual(got.flatMap(g => g.ids).sort(), old.map(e => e.id).sort(), 'exactly the shifts over 30 days, no paid, no recent, no future');
  for (const g of got) {
    const mine = old.filter(e => e.category === g.category && e.currency === g.currency), known = mine.filter(e => e.payKnown);
    const cents = known.reduce((s, e) => s + Math.round(e.totalEarnings * 100), 0);
    assert.equal(g.amount == null ? null : Math.round(g.amount * 100), known.length ? cents : null, g.category + g.currency);
    assert.equal(g.unknown, mine.length - known.length);
  }
  // ₪ and $ never in one line.
  assert.ok(got.every(g => !P.agingLine(g, money).includes(g.currency === '₪' ? '$' : '₪')));
});

test('the 30/31-day edge: a shift exactly 30 days old is not counted, 31 days is', () => {
  const now = at(doc.now.day, doc.now.time), ids = P.unpaidAging(entries(), now).flatMap(g => g.ids);
  assert.ok(ids.includes('a03')); assert.ok(!ids.includes('a04'));
});

test('nothing is shown when no unpaid shift is older than 30 days', () => {
  assert.deepEqual(P.unpaidAging(entries(), at(doc.nothingOld.now.day, doc.nothingOld.now.time)), doc.nothingOld.expected);
  assert.deepEqual(P.unpaidAging(entries().map(e => ({ ...e, isPaid: true })), at(doc.now.day, doc.now.time)), []);
  assert.deepEqual(P.unpaidAging([], Date.now()), []);
});

test('display only: the entries are not changed', () => {
  const list = entries(), before = JSON.stringify(list);
  P.unpaidAging(list, at(doc.now.day, doc.now.time));
  assert.equal(JSON.stringify(list), before);
});
