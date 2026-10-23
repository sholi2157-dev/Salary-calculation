package com.example

import com.example.domain.V2Summary
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** When recaps open and which words they use: the shared expected results (v2-recap.json), asserted the same way by the website. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class V2RecapTest {
    private val doc by lazy { JSONObject(javaClass.classLoader!!.getResourceAsStream("v2-recap.json").bufferedReader().readText()) }
    private fun JSONArray.objects() = (0 until length()).map { getJSONObject(it) }
    private fun JSONArray.strings() = (0 until length()).map { getString(it) }
    private fun JSONObject.str(k: String): String? = if (isNull(k)) null else getString(k)

    @Test fun recapsOpenOnThe30thAreNewFor4MoreDaysAndNeverBeforeTheFirst() {
        assertEquals(doc.getString("first"), V2Summary.FIRST_RECAP)
        assertEquals(doc.getInt("newDays"), V2Summary.RECAP_NEW_DAYS)
        for (c in doc.getJSONArray("windows").objects()) {
            val today = c.getString("today")
            assertEquals(today, V2Summary.RecapNew(c.str("month"), c.str("year")), V2Summary.recapNew(today))
            assertEquals("$today archive", c.getJSONArray("archive").strings(), V2Summary.recapArchive(today))
            assertEquals("$today open", c.getJSONArray("open").strings(), listOf("2026-09", "2026-10", "2026-11", "2026", "2027-02").filter { V2Summary.recapOpen(it, today) })
        }
    }

    @Test fun wordsArePickedTheSameWayAsOnTheWebsite() {
        for (c in doc.getJSONArray("voices").objects()) {
            val seed = c.getString("seed"); val scope = c.getString("scope")
            assertEquals("$seed $scope hash", c.getLong("hash"), V2Summary.hash("$seed|$scope"))
            val want = c.getJSONObject("voice").let { o -> o.keys().asSequence().associateWith { o.getString(it) } }
            assertEquals("$seed $scope", want, V2Summary.voice(scope, seed))
        }
        assertTrue(V2Summary.VOICE.getValue("introLineYear").contains(V2Summary.voice("2026", "x").getValue("introLine")))
        assertEquals("40% מהזמן הלכו לאוהל. שיהיה ברור מי האהוב.", V2Summary.fill("{p}% מהזמן הלכו ל{name}. שיהיה ברור מי האהוב.", mapOf("p" to "40", "name" to "אוהל")))
    }
}
