package com.example

import com.example.data.WorkEntry
import com.example.data.WorkPay
import com.example.ui.kis.HistoryFilter
import com.example.ui.kis.KisHistoryLogic
import com.example.ui.kis.PayFilter
import com.example.ui.kis.Period
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.TimeZone

/**
 * FB-09 (feedback 2026-11-03): "how much am I owed for one place (e.g. a convention) across October and November".
 * History already answers it with "כל התאריכים" + a place filter (+ "ממתין"); its total card is WorkPay.totals(rows).
 * This pins that the per-place total over all months is exactly the sum of the amounts already saved on each shift:
 * never recomputed from hours × rate, other places and pending amounts left out, ₪ and $ never added together.
 * Synthetic data only.
 */
class WorkFeedbackCategoryTotalTest {
    private val zone = ZoneId.of("Asia/Jerusalem")
    private lateinit var previousZone: TimeZone

    @Before fun setZone() { previousZone = TimeZone.getDefault(); TimeZone.setDefault(TimeZone.getTimeZone(zone)) }
    @After fun restoreZone() { TimeZone.setDefault(previousZone) }

    private val place = "כינוס"
    private fun at(day: String) = LocalDate.parse(day).atTime(LocalTime.NOON).atZone(zone).toInstant().toEpochMilli()
    private fun shift(id: Int, day: String, hours: Double, rate: Double, saved: Double, paid: Boolean = false,
                      category: String = place, currency: String = "₪", known: Boolean = true) =
        WorkEntry(id = id, category = category, date = at(day), isTimeRange = false, hours = hours, hourlyRate = rate,
            totalEarnings = saved, isPaid = paid, currency = currency, payKnown = known, createdAt = id.toLong())

    // Saved amounts are taken as they are; #3 and #5 deliberately differ from hours × rate (older-version rounding).
    private val entries = listOf(
        shift(1, "2026-10-12", 8.0, 40.0, 320.00),
        shift(2, "2026-10-20", 6.5, 40.0, 260.00, paid = true),
        shift(3, "2026-10-31", 3.33, 45.0, 149.99),
        shift(4, "2026-11-01", 7.0, 45.0, 315.00),
        shift(5, "2026-11-02", 2.0, 33.33, 66.67),
        shift(6, "2026-11-03", 4.0, 0.0, 0.0, known = false),                  // rate still missing
        shift(7, "2026-11-02", 5.0, 20.0, 100.00, currency = "$"),             // another currency, same place
        shift(8, "2026-10-15", 9.0, 50.0, 450.00, category = "מסעדה"),         // another place
        shift(9, "2026-11-02", 1.0, 40.0, 40.00, category = "כינוס ב"),        // similar name, another place
    )

    private fun sum(rows: List<WorkEntry>) = rows.fold(BigDecimal.ZERO) { a, e -> a + BigDecimal.valueOf(e.totalEarnings) }
    private fun shown(f: HistoryFilter) = KisHistoryLogic.apply(entries, f, "", zone)
    private fun cents(v: BigDecimal) = v.setScale(2, RoundingMode.HALF_UP)
    private fun assertMoney(expected: BigDecimal, actual: Double?) = assertEquals(cents(expected), cents(BigDecimal.valueOf(actual!!)))

    @Test fun allDatesPlaceTotalIsTheSumOfSavedAmounts() {
        val rows = shown(HistoryFilter(period = Period.ALL, category = place))
        assertEquals(setOf(1, 2, 3, 4, 5, 6, 7), rows.map { it.id }.toSet())
        val totals = WorkPay.totals(rows)
        val savedIls = entries.filter { it.category == place && it.currency == "₪" && it.payKnown }
        assertMoney(sum(savedIls), totals["₪"])                       // 1111.66, not hours × rate
        assertMoney(BigDecimal("1111.66"), totals["₪"])
        assertMoney(BigDecimal("100.00"), totals["$"])
        assertEquals(setOf("₪", "$"), totals.keys)
        assertEquals(1, WorkPay.pending(rows).size)
        assertEquals(35.83, rows.sumOf { it.hours }, 1e-9)
    }

    @Test fun owedForPlaceIsTheSumOfUnpaidSavedAmounts() {
        val rows = shown(HistoryFilter(period = Period.ALL, category = place, pay = PayFilter.UNPAID))
        assertMoney(BigDecimal("851.66"), WorkPay.totals(rows)["₪"])
        assertMoney(BigDecimal("100.00"), WorkPay.totals(rows)["$"])
    }

    @Test fun monthsAddUpToTheAllDatesTotal() {
        val oct = shown(HistoryFilter(period = Period.MONTH, month = HistoryFilter.monthKey(at("2026-10-12")), category = place))
        val nov = shown(HistoryFilter(period = Period.MONTH, month = HistoryFilter.monthKey(at("2026-11-01")), category = place))
        assertEquals(setOf(1, 2, 3), oct.map { it.id }.toSet())
        assertEquals(setOf(4, 5, 6, 7), nov.map { it.id }.toSet())
        val all = WorkPay.totals(shown(HistoryFilter(period = Period.ALL, category = place)))["₪"]
        assertMoney(BigDecimal.valueOf(WorkPay.totals(oct)["₪"]!!).add(BigDecimal.valueOf(WorkPay.totals(nov)["₪"]!!)), all)
    }
}
