package com.example.data

import androidx.room.withTransaction

/** Review a fixed guest snapshot; confirmation never deletes or claims the guest database. */
object WorkLegacyAdoption {
    suspend fun snapshot(source: WorkDatabase): WorkBackup.Contents = source.withTransaction {
        val dao = source.workDao()
        WorkBackup.Contents(dao.getCategoriesList(), dao.getEntriesList(), dao.getWorkersList())
    }

    suspend fun confirm(target: WorkDatabase, reviewed: WorkBackup.Contents): Int =
        target.workDao().adoptLegacy(reviewed)
}
