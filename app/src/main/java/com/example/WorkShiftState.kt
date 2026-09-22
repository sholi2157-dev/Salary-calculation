package com.example

import android.content.Context
import com.example.data.WorkAccountScope
import kotlinx.coroutines.flow.MutableStateFlow

/** A timer never obtains its owner from whichever Firebase user happens to be current. */
class WorkShiftState internal constructor(private val context: Context, val owner: WorkAccountScope) {
    private val prefs = context.getSharedPreferences(owner.timerName, Context.MODE_PRIVATE)
    val activeShiftStartTime = MutableStateFlow(prefs.getLong("start_time", -1L).takeIf { it != -1L })
    val activeShiftCategory = MutableStateFlow(prefs.getString("category", "עצמאי") ?: "עצמאי")
    val activeShiftRate = MutableStateFlow(prefs.getString("rate_double", null)?.toDoubleOrNull()
        ?: prefs.getFloat("rate", 40f).toDouble())
    val activeShiftCurrency = MutableStateFlow(prefs.getString("currency", "₪") ?: "₪")

    fun start(category: String, rate: Double, startTime: Long, currency: String = "₪"): Boolean = synchronized(ShiftStateManager) {
        require(rate.isFinite() && rate >= 0 && startTime > 0)
        if (ShiftStateManager.hasActiveShift(context)) return false
        // Persist the owner first; no timer is lost if process death occurs between commits.
        if (!ShiftStateManager.setOwner(context, owner)) return false
        if (!prefs.edit().putLong("start_time", startTime).putString("category", category)
                .putString("rate_double", rate.toString()).putString("currency", currency).commit()) return false
        activeShiftCategory.value = category
        activeShiftRate.value = rate
        activeShiftCurrency.value = currency
        activeShiftStartTime.value = startTime
        true
    }

    /** An old notification/completion cannot clear a newer timer. */
    fun clear(expectedStart: Long? = activeShiftStartTime.value): Boolean = synchronized(ShiftStateManager) {
        if (expectedStart == null || activeShiftStartTime.value != expectedStart) return false
        if (!prefs.edit().remove("start_time").commit()) return false
        activeShiftStartTime.value = null
        true
    }
}

object ShiftStateManager {
    private val states = mutableMapOf<WorkAccountScope, WorkShiftState>()
    @Synchronized fun forAccount(context: Context, owner: WorkAccountScope): WorkShiftState =
        states.getOrPut(owner) { WorkShiftState(context.applicationContext, owner) }

    private fun routing(context: Context) = context.getSharedPreferences("shift_service_owner", Context.MODE_PRIVATE)
    internal fun setOwner(context: Context, owner: WorkAccountScope): Boolean =
        routing(context).edit().putString("uid", owner.uid).commit()
    fun activeOwner(context: Context): WorkAccountScope = WorkAccountScope(routing(context).getString("uid", null))
    @Synchronized fun hasActiveShift(context: Context): Boolean =
        forAccount(context, activeOwner(context)).activeShiftStartTime.value != null ||
            forAccount(context, WorkAccountScope(null)).activeShiftStartTime.value != null
}
