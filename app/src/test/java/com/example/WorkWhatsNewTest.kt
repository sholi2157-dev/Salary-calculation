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
}
