package com.example

import com.example.data.WorkPay
import com.example.domain.V2Rules
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.math.BigDecimal
import java.math.BigInteger

/**
 * 2.3.0 money oracle (audit M-08, decision D-5), the same grid as web web-tests/money-oracle.test.cjs: a shift of whole
 * minutes (or timer seconds) pays exactly minutes × rate / 60 + bonus, rounded once, half up, to whole agorot. The oracle
 * uses integer arithmetic on the typed decimal rate, independent of WorkPay.money.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class WorkMoneyOracleTest {
    private val rates = listOf("0.01", "0.3", "2.05", "29.12", "30.61", "32.3", "33.33", "35.4", "40", "45.55", "50.01", "66.67", "99.99", "850.005")

    /** Exact cents of units × rateText / per + bonusText, half up. */
    private fun oracle(units: Long, per: Long, rateText: String, bonusText: String = "0"): Double {
        fun dec(t: String) = BigDecimal(t).movePointRight(10).toBigIntegerExact()
        val num = (BigInteger.valueOf(units) * dec(rateText) + BigInteger.valueOf(per) * dec(bonusText)) * BigInteger.valueOf(100)
        val den = BigInteger.valueOf(per) * BigInteger.TEN.pow(10)
        val cents = (BigInteger.valueOf(2) * num + den) / (BigInteger.valueOf(2) * den)
        return BigDecimal(cents, 2).toDouble()
    }

    @Test fun fiftyMinutesAt3333IsRoundedOnceTo2778() {
        assertEquals(27.78, WorkPay.money(50 / 60.0, 33.33), 0.0)
        val ev = V2Rules.evaluate(V2Rules.Draft(mode = V2Rules.Mode.CLOCK, start = "08:00", end = "08:50", rate = 33.33))
        assertEquals(27.78, ev.total!!, 0.0)
    }

    @Test fun everyWholeMinuteOfADayTimesFourteenRatesHasNoMismatch() {
        val bad = mutableListOf<String>()
        for (m in 1L..1440L) for (r in rates) {
            val want = oracle(m, 60, r); val got = WorkPay.money(m / 60.0, r.toDouble())
            if (got != want) bad += "${m}min×$r: $got≠$want"
        }
        assertEquals(bad.take(10).joinToString(), 0, bad.size)
    }

    @Test fun timerSecondsAreExactToo() {
        for ((s, r) in listOf(3601L to "36.01", 7L to "0.3", 5999L to "33.33", 30L to "0.3", 86399L to "99.99"))
            assertEquals("${s}s×$r", oracle(s, 3600, r), WorkPay.money(s / 3600.0, r.toDouble()), 0.0)
    }

    @Test fun typedDecimalHoursKeepTheDecimalContract() {
        assertEquals(2.68, WorkPay.money(2.675, 1.0), 0.0)
        assertEquals(123.46, WorkPay.money(1.234567, 100.0), 0.0)
        assertEquals(149.99, WorkPay.money(1.5, 99.99), 0.0)
        assertEquals(6.06, WorkPay.money(10 / 60.0, 33.33, 0.5), 0.0)
    }
}
