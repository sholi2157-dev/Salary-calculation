package com.example.data

import org.json.JSONArray
import org.json.JSONObject

/** Versioned transfer format shared with the website; legacy exports remain readable. */
object WorkBackup {
    data class Contents(val categories: List<WorkCategory>, val entries: List<WorkEntry>, val workers: List<WorkerDirectory>)

    fun encode(categories: List<WorkCategory>, entries: List<WorkEntry>, workers: List<WorkerDirectory>): String {
        return JSONObject().apply {
            put("formatVersion", 2)
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
                }) }
            })
        }.toString(2)
    }

    fun decode(text: String): Contents {
        val root = JSONObject(text)
        require(root.optInt("formatVersion", 1) in 1..2) { "Unsupported backup version" }
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
            if (groupJson.isNotBlank()) JSONArray(groupJson)
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
                groupWorkersJson = groupJson
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
        return Contents(categories, entries, (0 until workers.length()).map {
            WorkerDirectory(name = workers.getJSONObject(it).getString("name"))
        })
    }

    private fun JSONObject.nullableRate(key: String): Double? {
        if (isNull(key)) return null
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
