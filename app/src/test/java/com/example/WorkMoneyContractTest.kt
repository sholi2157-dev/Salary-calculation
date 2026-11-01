package com.example

import com.example.data.WorkPay
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** 2.3.0 money contract (audit CAL-02, CAL-05): the same fixture as web web-tests/fixtures/money-contract.json. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class WorkMoneyContractTest {
    private val doc = JSONObject(javaClass.classLoader!!.getResourceAsStream("money-contract.json").bufferedReader().readText())

    @Test fun everyNewAmountFollowsTheSharedContract() {
        val cases = doc.getJSONArray("cases")
        for (i in 0 until cases.length()) {
            val c = cases.getJSONObject(i)
            assertEquals(c.toString(), c.getDouble("expected"), WorkPay.money(c.getDouble("hours"), c.getDouble("rate"), c.getDouble("bonus")), 0.0)
        }
    }

    @Test fun derivedGroupAmountsKeepTheir20Rounding() {
        assertEquals(149.98, WorkPay.amount(1.5, 99.99), 0.0)
    }

    @Test fun timerSecondsAreTheDifferenceTruncatedOnce() {
        assertEquals(0L, WorkPay.elapsedSeconds(1_000_999L, 1_001_000L))
        assertEquals(1L, WorkPay.elapsedSeconds(1_000_000L, 1_001_999L))
        assertEquals(0L, WorkPay.elapsedSeconds(5_000L, 1_000L))
    }
}
