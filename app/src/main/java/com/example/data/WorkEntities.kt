package com.example.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "work_entries", indices = [Index(value = ["seriesKey"], unique = true)])
data class WorkEntry(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val category: String,
    val date: Long, // timestamp in millis for the selected day
    val isTimeRange: Boolean,
    val startTime: String? = null, // formatted as "HH:MM"
    val endTime: String? = null,   // formatted as "HH:MM"
    val hours: Double,
    val hourlyRate: Double,
    val totalEarnings: Double,
    val isPaid: Boolean = false,
    val notes: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    
    // Group Shift Fields
    val isGroupShift: Boolean = false,
    val employerRate: Double? = null,
    val workerRate: Double? = null,
    val groupWorkersJson: String = "", // JSON string of group workers
    val currency: String = "₪",

    // 2.0 (schema 9). Added columns only; every existing row keeps these defaults.
    /** "shift" (clock / hours / group) or "job" (fixed-price work). */
    @ColumnInfo(defaultValue = "shift") val kind: String = KIND_SHIFT,
    /** False when the rate (shift) or amount (job) is not known yet. Then totalEarnings is a 0 placeholder, never real pay. */
    @ColumnInfo(defaultValue = "1") val payKnown: Boolean = true,
    /** The user's own bonus, already included in totalEarnings. */
    @ColumnInfo(defaultValue = "0") val bonus: Double = 0.0,
    /** "seriesId@YYYY-MM-DD" when confirmed from a recurring series; unique so a planned shift is never recorded twice. */
    val seriesKey: String? = null,
    /** Recorded automatically when its planned time ended. */
    @ColumnInfo(defaultValue = "0") val autoConfirmed: Boolean = false,
) {
    companion object {
        const val KIND_SHIFT = "shift"
        const val KIND_JOB = "job"
    }
}

@Entity(tableName = "worker_directory")
data class WorkerDirectory(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String
)

@Entity(tableName = "work_categories")
data class WorkCategory(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val defaultRate: Double = 40.0
)

/** Local preferences deliberately excluded from records_v1 until sync supports them. */
@Entity(tableName = "work_local_preferences")
data class WorkLocalPreference(@PrimaryKey val name: String, val value: String)

/** A weekly recurring plan (2.0). Planned occurrences are derived from it, never stored as shifts. */
@Entity(tableName = "shift_series")
data class ShiftSeries(
    @PrimaryKey val id: String,
    val category: String,
    /** Bit n set = weekday n (0 = Sunday). */
    val daysMask: Int,
    val startTime: String,
    val endTime: String,
    /** Null when the rate is not known yet. */
    val hourlyRate: Double?,
    val currency: String,
    /** Inclusive, "YYYY-MM-DD". */
    val fromDate: String,
    val untilDate: String,
    val createdAt: Long = System.currentTimeMillis(),
)

/** A change to a single occurrence of a series: cancelled, held for manual confirmation, or different hours/rate. */
@Entity(tableName = "series_exceptions", primaryKeys = ["seriesId", "date"])
data class SeriesException(
    val seriesId: String,
    val date: String,
    @ColumnInfo(defaultValue = "0") val cancelled: Boolean = false,
    @ColumnInfo(defaultValue = "0") val hold: Boolean = false,
    val startTime: String? = null,
    val endTime: String? = null,
    @ColumnInfo(defaultValue = "0") val rateOverridden: Boolean = false,
    val hourlyRate: Double? = null,
)
