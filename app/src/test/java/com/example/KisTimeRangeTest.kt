package com.example

import com.example.ui.kis.KisFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KisTimeRangeTest {
    @Test fun planningTimeRangesKeepStartBeforeEndInsideLtrIsolation() {
        for ((start, end) in listOf("08:00" to "16:00", "22:00" to "06:00")) {
            val range = KisFormat.timeRange(start, end)
            assertEquals("\u2066$start–$end\u2069", range)
            assertTrue(range.indexOf(start) < range.indexOf(end))
            assertEquals('\u2066', range.first()) // LTR isolate
            assertEquals('\u2069', range.last()) // Pop directional isolate
        }
    }
}
