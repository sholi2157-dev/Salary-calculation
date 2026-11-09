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
    /** A report to a worker asks for what the user gets paid with (owner feedback 2.3.1). */
    @Test fun theAskFollowsTheProfile() {
        assertEquals(WorkShareText.ASK_PAYMENT, WorkShareText.askPayment(emptyList()))
        assertEquals(WorkShareText.ASK_PAYMENT, WorkShareText.askPayment(listOf("zelle", "bit", "bank")))
        assertEquals("אפשר לשלוח לי את ה-Zelle שלך, ואעביר את התשלום.", WorkShareText.askPayment(listOf("zelle")))
        assertEquals("אפשר לשלוח לי את הביט או פרטי החשבון שלך, ואעביר את התשלום.", WorkShareText.askPayment(listOf("bank", "bit")))
    }
}
