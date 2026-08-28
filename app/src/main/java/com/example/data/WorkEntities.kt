package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "work_entries")
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
    val currency: String = "₪"
)

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
