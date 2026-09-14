package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Transactional completion markers survive retries and process death. No credentials. */
@Entity(tableName = "work_local_receipts")
data class WorkLocalReceipt(@PrimaryKey val operation: String, val result: Long)
