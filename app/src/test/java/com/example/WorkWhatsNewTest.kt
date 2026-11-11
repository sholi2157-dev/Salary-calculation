package com.example

import com.example.ui.kis.WhatsNew
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** FB23-F11: highlights show once after an update, never after a fresh install, and only for a version that has them. */
class WorkWhatsNewTest {
    private val notes = mapOf("2.3.0" to listOf("הוספנו X"))

    @Test fun showsAfterAnUpdateFromABuildThatNeverRecordedAVersion() =
        assertEquals(listOf("הוספנו X"), WhatsNew.decide(null, 49, updated = true, versionName = "2.3.0", notes = notes))

    @Test fun freshInstallShowsNothing() = assertNull(WhatsNew.decide(null, 49, updated = false, versionName = "2.3.0", notes = notes))

    @Test fun sameVersionAgainShowsNothing() = assertNull(WhatsNew.decide(49, 49, updated = true, versionName = "2.3.0", notes = notes))

    @Test fun recordedOlderVersionShowsAndSuffixIsIgnored() =
        assertEquals(listOf("הוספנו X"), WhatsNew.decide(48, 49, updated = false, versionName = "2.3.0-rc2", notes = notes))

    @Test fun versionWithoutNotesStaysQuiet() = assertNull(WhatsNew.decide(49, 50, updated = true, versionName = "2.3.1", notes = notes))

    @Test fun shippedDraftIsShortAndFeaturesOnly() {
        val draft = WhatsNew.NOTES.getValue("2.3.0")
        assertTrue(draft.size in 3..5)
        assertTrue(draft.none { it.contains("תקלה") || it.contains("באג") || it.contains("תיקון") })
    }

    @Test fun release240HasTheThreeAgreedHighlightsAndOnlyThoseShowAfterTheUpdate() {
        val lines = WhatsNew.NOTES.getValue("2.4.0")
        assertEquals(
            listOf(
                "משמרות שלא שולמו יותר מ-30 יום מופיעות עכשיו במסך הבית, לכל מקום עבודה, ובלחיצה רואים בדיוק אילו.",
                "לחיצה ארוכה על האייקון במסך הבית פותחת ישר את \"הוספת משמרת\".",
                "בהגדרות, כרטיס ההתראות מראה בדיוק מה פועל: הכול, חלק או כלום.",
            ),
            lines,
        )
        assertTrue(lines.none { it.contains("תקלה") || it.contains("באג") || it.contains("תיקון") })
        // Updated from 2.3.1 (code 54) to 2.4.0 (code 55): shown once; a fresh install of 2.4.0 shows nothing.
        assertEquals(lines, WhatsNew.decide(54, 55, updated = true, versionName = "2.4.0"))
        assertEquals(lines, WhatsNew.decide(54, 55, updated = true, versionName = "2.4.0-rc1"))
        assertNull(WhatsNew.decide(null, 55, updated = false, versionName = "2.4.0"))
        assertNull(WhatsNew.decide(55, 55, updated = true, versionName = "2.4.0"))
    }
}
