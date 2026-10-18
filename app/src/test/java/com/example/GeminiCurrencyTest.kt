package com.example

import com.example.api.GeminiParser
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** A shift described without a currency takes its workplace's (else the user's default), never a fixed ₪. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class GeminiCurrencyTest {
    @Test fun anUnnamedCurrencyFollowsTheWorkplaceAndANamedOneWins() {
        // The user's default is $; the workplace "חנות" is set to ₪.
        val currencyOf = { name: String -> if (name == "חנות") "₪" else "$" }
        val raw = """[
            {"category": "חנות", "date": 1000, "hours": 2.0, "hourlyRate": 20.0, "currency": "", "notes": ""},
            {"category": "מקום חדש", "date": 1000, "hours": 2.0, "hourlyRate": 20.0, "notes": ""},
            {"category": "מקום חדש", "date": 1000, "hours": 2.0, "hourlyRate": 20.0, "currency": "₪", "notes": ""}
        ]"""
        assertEquals(listOf("₪", "$", "₪"), GeminiParser.parseResponse(raw, currencyOf).map { it.currency })
    }
}
