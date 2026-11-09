package com.example

import com.example.data.WorkShareText
import org.junit.Assert.assertEquals
import org.junit.Test

/** Payment details (Zelle, Bit, bank) added to a shared report only when the user chooses; mirrors web-tests/payment-details.test.cjs. */
class WorkPaymentTextTest {
    @Test fun onlyTheFilledDetailsInAFixedOrder() {
        assertEquals(listOf("Zelle: a@b.co"), WorkShareText.paymentLines(" a@b.co ", "", "  "))
        assertEquals(listOf("Zelle: a@b.co", "ביט: 050-1234567", "חשבון בנק: לאומי 800 12345"), WorkShareText.paymentLines("a@b.co", "050-1234567", "לאומי 800 12345"))
    }

    @Test fun theDetailsGoBeforeTheClosingThanksOrAtTheEnd() {
        assertEquals("היי\nסכום\nלתשלום:\nZelle: x\nתודה!", WorkShareText.withPayment("היי\nסכום\nתודה!", listOf("Zelle: x")))
        assertEquals("עבודה: א\nלתשלום:\nביט: 1", WorkShareText.withPayment("עבודה: א", listOf("ביט: 1")))
        assertEquals("היי\nתודה!", WorkShareText.withPayment("היי\nתודה!", emptyList()))
    }
}
