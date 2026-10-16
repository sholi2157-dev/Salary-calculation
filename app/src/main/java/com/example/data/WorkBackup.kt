package com.example.data

import org.json.JSONArray
import org.json.JSONObject

/** Versioned transfer format shared with the website; legacy exports remain readable. */
object WorkBackup {
    data class Contents(val categories: List<WorkCategory>, val entries: List<WorkEntry>, val workers: List<WorkerDirectory>, val localPreferences: Map<String, String> = emptyMap())

    /**
     * Version 3 only when a 2.0 feature is actually used (unknown pay, bonus, fixed-price job,
     * series, worker bonus). Otherwise the file is byte-for-byte the version 2 format, so older
     * app versions and the current website keep reading it.
     */
    fun needsVersion3(entries: List<WorkEntry>): Boolean = entries.any { e ->
        e.kind != WorkEntry.KIND_SHIFT || !e.payKnown || e.bonus != 0.0 || e.seriesKey != null || e.autoConfirmed ||
            WorkPay.workers(e).any { it.bonus > 0 }
    }

    fun encode(categories: List<WorkCategory>, entries: List<WorkEntry>, workers: List<WorkerDirectory>, localPreferences: Map<String, String> = emptyMap()): String {
        val v3 = needsVersion3(entries)
        return JSONObject().apply {
            put("formatVersion", if (v3) 3 else 2)
            if (localPreferences.isNotEmpty()) put("androidLocalPreferences", JSONObject(localPreferences))
            put("categories", JSONArray().apply {
                categories.forEach { put(JSONObject().put("name", it.name).put("defaultRate", it.defaultRate)) }
            })
            put("workers", JSONArray().apply { workers.forEach { put(JSONObject().put("name", it.name)) } })
            put("entries", JSONArray().apply {
                entries.forEach { e -> put(JSONObject().apply {
                    put("id", e.id)
                    put("category", e.category)
                    put("date", e.date)
                    put("createdAt", e.createdAt)
                    put("isTimeRange", e.isTimeRange)
                    put("startTime", e.startTime ?: JSONObject.NULL)
                    put("endTime", e.endTime ?: JSONObject.NULL)
                    put("hours", e.hours)
                    put("hourlyRate", e.hourlyRate)
                    put("totalEarnings", e.totalEarnings)
                    put("isPaid", e.isPaid)
                    put("notes", e.notes)
                    put("currency", e.currency)
                    put("isGroupShift", e.isGroupShift)
                    put("employerRate", e.employerRate ?: JSONObject.NULL)
                    put("workerRate", e.workerRate ?: JSONObject.NULL)
                    put("groupWorkersJson", e.groupWorkersJson)
                    if (v3) {
                        put("kind", e.kind)
                        put("payKnown", e.payKnown)
                        put("bonus", e.bonus)
                        put("seriesKey", e.seriesKey ?: JSONObject.NULL)
                        put("autoConfirmed", e.autoConfirmed)
                    }
                }) }
            })
        }.toString(2)
    }

    fun decode(text: String): Contents {
        val root = JSONObject(text)
        require(root.optInt("formatVersion", 1) in 1..3) { "Unsupported backup version" }
        val records = root.getJSONArray("entries")
        val entries = (0 until records.length()).map { i ->
            val e = records.getJSONObject(i)
            val currency = e.optString("currency", "₪")
            require(currency == "₪" || currency == "$") { "Unknown currency" }
            val date = e.getLong("date")
            require(date >= 0) { "Invalid date" }
            val hours = e.getDouble("hours")
            val rate = e.getDouble("hourlyRate")
            val earnings = e.getDouble("totalEarnings")
            require(listOf(hours, rate, earnings).all { it.isFinite() && it >= 0 }) { "Invalid amounts" }
            val groupJson = e.optString("groupWorkersJson", "")
            if (groupJson.isNotBlank()) {
                val group = JSONArray(groupJson)
                for (i in 0 until group.length()) {
                    val worker = group.getJSONObject(i)
                    require(worker.getString("name").isNotBlank()) { "Missing worker name" }
                    require(worker.getDouble("hours").let { it.isFinite() && it >= 0 }) { "Invalid worker hours" }
                    worker.nullableRate("workerRate"); worker.nullableRate("employerRate"); worker.nullableRate("bonus")
                }
            }
            val kind = e.optString("kind", WorkEntry.KIND_SHIFT)
            require(kind == WorkEntry.KIND_SHIFT || kind == WorkEntry.KIND_JOB) { "Unknown entry kind" }
            val bonus = e.optDouble("bonus", 0.0)
            require(bonus.isFinite() && bonus >= 0) { "Invalid bonus" }
            require(e.getString("category").isNotBlank()) { "Missing category" }
            WorkEntry(
                // Source IDs belong to the exporting installation; never overwrite by ID.
                category = e.getString("category"), date = date,
                isTimeRange = e.optBoolean("isTimeRange", false),
                startTime = if (e.isNull("startTime")) null else e.getString("startTime"),
                endTime = if (e.isNull("endTime")) null else e.getString("endTime"),
                hours = hours, hourlyRate = rate, totalEarnings = earnings,
                isPaid = e.getBoolean("isPaid"), notes = e.optString("notes", ""),
                createdAt = e.optLong("createdAt", date), currency = currency,
                isGroupShift = e.optBoolean("isGroupShift", false),
                employerRate = e.nullableRate("employerRate"), workerRate = e.nullableRate("workerRate"),
                groupWorkersJson = groupJson,
                kind = kind, payKnown = e.optBoolean("payKnown", true), bonus = bonus,
                seriesKey = if (e.isNull("seriesKey")) null else e.optString("seriesKey").takeIf { it.isNotBlank() },
                autoConfirmed = e.optBoolean("autoConfirmed", false)
            )
        }
        val cats = root.optJSONArray("categories") ?: JSONArray()
        val categories = (0 until cats.length()).map { i ->
            val c = cats.getJSONObject(i)
            val rate = c.optDouble("defaultRate", 40.0)
            require(rate.isFinite() && rate >= 0)
            WorkCategory(name = c.getString("name"), defaultRate = rate)
        }
        val workers = root.optJSONArray("workers") ?: JSONArray()
        val local = root.optJSONObject("androidLocalPreferences") ?: JSONObject()
        val preferences = local.keys().asSequence().associateWith { local.getString(it) }
        for ((key, value) in preferences) {
            require(key == "defaultCategory" || key == "default_currency" || key.startsWith("categoryCurrency:")) { "Unknown local preference" }
            if (key == "defaultCategory") require(value.isNotBlank())
            else require(value in listOf("₪", "$")) { "Invalid preference currency" }
        }
        return Contents(categories, entries, (0 until workers.length()).map {
            WorkerDirectory(name = workers.getJSONObject(it).getString("name"))
        }, preferences)
    }

    private fun JSONObject.nullableRate(key: String): Double? {
        if (!has(key) || isNull(key)) return null
        val value = getDouble(key)
        require(value.isFinite() && value >= 0)
        return value
    }

    /** Multiset merge: repeated import is a no-op, but genuine identical rows are retained. */
    fun missingEntries(existing: List<WorkEntry>, incoming: List<WorkEntry>): List<WorkEntry> {
        fun key(e: WorkEntry) = e.copy(id = 0, createdAt = 0)
        val available = existing.groupingBy { key(it) }.eachCount().toMutableMap()
        return incoming.filter { entry ->
            val signature = key(entry)
            val count = available[signature] ?: 0
            if (count > 0) { available[signature] = count - 1; false } else true
        }
    }
}
