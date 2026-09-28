package com.example.data

/** Preserve imported financial values unless the user actually changes hours or rate. */
object WorkEntryEdits {
    fun rangeHours(start: String, end: String): Double {
        fun minutes(value: String): Int? {
            val parts = value.split(":")
            if (parts.size != 2) return null
            val hour = parts[0].toIntOrNull() ?: return null
            val minute = parts[1].toIntOrNull() ?: return null
            if (hour !in 0..23 || minute !in 0..59) return null
            return hour * 60 + minute
        }
        val startMinutes = minutes(start) ?: return 0.0
        val endMinutes = minutes(end) ?: return 0.0
        val duration = endMinutes - startMinutes
        return (if (duration < 0) duration + 24 * 60 else duration) / 60.0
    }

    fun breakMinutes(entry: WorkEntry): Double =
        if (entry.isTimeRange && entry.startTime != null && entry.endTime != null)
            maxOf(0.0, (rangeHours(entry.startTime, entry.endTime) - entry.hours) * 60.0)
        else 0.0

    fun netHours(original: WorkEntry, start: String, end: String, breakDurationMinutes: Double): Double {
        if (original.isTimeRange && start == original.startTime && end == original.endTime &&
            breakDurationMinutes == breakMinutes(original)) return original.hours
        return maxOf(0.0, rangeHours(start, end) - breakDurationMinutes / 60.0)
    }

    fun apply(original: WorkEntry, proposed: WorkEntry): WorkEntry = proposed.copy(
        id = original.id,
        createdAt = original.createdAt,
        totalEarnings = if (original.hours == proposed.hours && original.hourlyRate == proposed.hourlyRate)
            original.totalEarnings
        else Math.round(proposed.hours * proposed.hourlyRate * 100.0) / 100.0
    )
}
