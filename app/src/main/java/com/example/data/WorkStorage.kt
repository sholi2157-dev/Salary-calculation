package com.example.data

import android.content.Context
import android.content.SharedPreferences
import android.database.sqlite.SQLiteException
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** One failure boundary for local writes. Never log the payload or recover by deleting data. */
object WorkStorage {
    const val SAVE_FAILED = "השמירה נכשלה. מומלץ להוריד גיבוי."
    const val BACKUP_FAILED = "הגיבוי האוטומטי נכשל. מומלץ להוריד גיבוי."

    private val pendingFailure = MutableStateFlow<String?>(null)
    val failure = pendingFailure.asStateFlow()
    fun failed(message: String = SAVE_FAILED) { pendingFailure.value = message }
    fun dismissFailure() { pendingFailure.value = null }

    fun isStorageFailure(error: Throwable): Boolean =
        generateSequence(error) { it.cause }.any { it is IOException || it is SQLiteException }

    fun preferences(context: Context, name: String): SharedPreferences =
        checked(context.applicationContext.getSharedPreferences(name, Context.MODE_PRIVATE))

    /** apply() cannot report disk failures. These small device preferences require an acknowledged commit. */
    internal fun checked(source: SharedPreferences): SharedPreferences = object : SharedPreferences by source {
        override fun edit(): SharedPreferences.Editor {
            val before = source.all
            val changed = mutableSetOf<String>()
            val editor = source.edit()
            return object : SharedPreferences.Editor by editor {
                override fun putString(key: String, value: String?) = apply { changed += key; editor.putString(key, value) }
                override fun putStringSet(key: String, values: Set<String>?) = apply { changed += key; editor.putStringSet(key, values) }
                override fun putInt(key: String, value: Int) = apply { changed += key; editor.putInt(key, value) }
                override fun putLong(key: String, value: Long) = apply { changed += key; editor.putLong(key, value) }
                override fun putFloat(key: String, value: Float) = apply { changed += key; editor.putFloat(key, value) }
                override fun putBoolean(key: String, value: Boolean) = apply { changed += key; editor.putBoolean(key, value) }
                override fun remove(key: String) = apply { changed += key; editor.remove(key) }
                override fun clear() = apply { changed += before.keys; editor.clear() }
                override fun apply() { commit() }
                override fun commit(): Boolean {
                    val saved = try { editor.commit() }
                    catch (cancelled: CancellationException) { throw cancelled }
                    catch (_: Exception) { false }
                    if (!saved) {
                        // Android changes its in-memory map even when commit returns false. Restore only this
                        // editor's keys, so a retry/getter cannot mistake an unsuccessful write for saved data.
                        val rollback = source.edit()
                        changed.forEach { key ->
                            when (val value = before[key]) {
                                null -> rollback.remove(key)
                                is String -> rollback.putString(key, value)
                                is Int -> rollback.putInt(key, value)
                                is Long -> rollback.putLong(key, value)
                                is Float -> rollback.putFloat(key, value)
                                is Boolean -> rollback.putBoolean(key, value)
                                is Set<*> -> {
                                    @Suppress("UNCHECKED_CAST")
                                    rollback.putStringSet(key, value as Set<String>)
                                }
                            }
                        }
                        // A full disk can reject rollback too; commit still restores Android's memory map.
                        try { rollback.commit() } catch (_: Exception) { }
                        failed()
                    }
                    return saved
                }
            }
        }
    }
}
