package com.example.api

import android.util.Log
import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

object GeminiParser {
    private const val TAG = "GeminiParser"
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    data class ParsedShift(
        val category: String,
        val date: Long,
        val hours: Double,
        val hourlyRate: Double,
        val notes: String,
        val isGroup: Boolean = false,
        val groupMembers: List<String> = emptyList(),
        val currency: String = "₪"
    )

    suspend fun parseNaturalLanguageToShifts(
        input: String,
        existingCategories: List<String>,
        modelName: String = "Gemini 3.5 Flash",
        categoryRates: Map<String, Double> = emptyMap()
    ): List<ParsedShift> = withContext(Dispatchers.IO) {
        val apiKey = if (BuildConfig.GEMINI_API_KEY.isNotEmpty()) BuildConfig.GEMINI_API_KEY else ""
        val useServer = AiService.configured || modelName.startsWith("openai:")
        if (!useServer && (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY")) {
            Log.e(TAG, "API Key is missing or default placeholder value.")
            error("מפתח השירות של ג׳מיני לא הוגדר")
        }

        val modelIdentifier = when (modelName) {
            "Gemini 3.1 Flash-Lite", "gemini-3.1-flash-lite", "gemini-1.5-flash-8b" -> "gemini-3.1-flash-lite"
            "Gemini 3.5 Flash", "gemini-3.5-flash", "gemini-1.5-flash" -> "gemini-3.5-flash"
            else -> modelName.removePrefix("gemini:")
        }

        Log.d("ModelVerification", "Active Model API ID: $modelIdentifier")

        val url = "https://generativelanguage.googleapis.com/v1beta/models/$modelIdentifier:generateContent?key=$apiKey"

        // Calculate helper dates to provide to the model
        val now = Calendar.getInstance()
        val sdfFull = SimpleDateFormat("EEEE, yyyy-MM-dd HH:mm", Locale.US)
        val currentDateTimeStr = sdfFull.format(now.time)
        val dayOfWeek = now.getDisplayName(Calendar.DAY_OF_WEEK, Calendar.LONG, Locale.US) ?: ""

        val todayDateStr = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(now.time)
        
        val yesterday = Calendar.getInstance().apply { add(Calendar.DATE, -1) }
        val yesterdayDateStr = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(yesterday.time)

        val categoriesStr = existingCategories.joinToString(", ")

        val prompt = """
            You are a strict data-extraction engine for work shift logs.
            Valid Known Categories: [$categoriesStr].
            Category hourly defaults: ${JSONObject(categoryRates).toString()}.
            If the user input matches or sounds like an existing category (e.g., 'צח' vs 'צאח'), map it to the exact existing category name. DO NOT invent new categories if an existing match is found.
            CRITICAL OUTPUT RULE: Return ONLY a raw, valid JSON array matching the schema. Do not include any markdown code fences (no ```json), conversational text, prefixes, or suffixes.

            The current date and time is: $currentDateTimeStr (Day: $dayOfWeek).

            Extract ALL shifts found in the input (whether 1 shift, multiple shifts, bullet points, table lines, or multi-day descriptions) into a JSON Array of shift objects:
            [
              { 
                "category": "String", 
                "date": Long, 
                "hours": Double, 
                "hourlyRate": Double, 
                "currency": "String",
                "notes": "String",
                "isGroup": Boolean,
                "groupMembers": ["String"]
              }
            ]

            Field Instructions:
            - "category":
              If the input contains a category/workplace name, extract it cleanly.
              Remove Hebrew prepositional prefixes like 'ב-' (e.g. 'בלב לדעת' -> 'לב לדעת').
              If the workplace is not specified or generic, default to "עצמאי".
            - "date": Epoch timestamp in milliseconds for that day (UTC / local start of day).
              - "היום" (today) or "עכשיו" (now) -> epoch for $todayDateStr.
              - "אתמול" (yesterday) -> epoch for $yesterdayDateStr.
              - "שלשום" -> epoch for 2 days ago.
              - Specific dates like "25/08" or day names like "יום ראשון" -> appropriate epoch in the current or closest relevant month.
            - "hours": Number of hours worked as a positive Double (e.g., 8.0, 4.5).
            - "hourlyRate": Rate per hour as a Double. If not specified, use the matching category hourly default above; only if absent use 40.0.
            - "currency": If dollar / $ / דולר is mentioned for this shift, return "$". Otherwise return "₪".
            - "notes": Any extra notes, descriptions, or tasks mentioned.
            - "isGroup": True if multiple people or a group is described (e.g. contains names, "צוות", "חברים", "בחורים"). Otherwise false.
            - "groupMembers": List of member names if isGroup is true, else [].

            Input: $input
            Return ONLY the valid JSON array.
        """.trimIndent()

        // Build OkHttp Request JSON payload
        val requestJson = JSONObject()
        val contentsArray = org.json.JSONArray()
        val contentObj = JSONObject()
        val partsArray = org.json.JSONArray()
        val partObj = JSONObject()
        partObj.put("text", prompt)
        partsArray.put(partObj)
        contentObj.put("parts", partsArray)
        contentsArray.put(contentObj)
        requestJson.put("contents", contentsArray)

        // Request JSON Response format
        val generationConfig = JSONObject()
        generationConfig.put("responseMimeType", "application/json")
        generationConfig.put("temperature", 0.1)
        requestJson.put("generationConfig", generationConfig)

        val mediaType = "application/json; charset=utf-8".toMediaType()
        val body = requestJson.toString().toRequestBody(mediaType)

        val request = Request.Builder()
            .url(url)
            .post(body)
            .build()

        suspend fun responseText(): String {
            if (useServer) {
                val serverModel = if (modelName.contains(":")) modelName else "gemini:$modelIdentifier"
                return AiService.generate(serverModel, prompt)
            }
            return client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.e(TAG, "Request failed with status code ${response.code}: ${response.message}")
                    error("השירות לא החזיר נתוני משמרות תקינים")
                }

                val responseBodyStr = response.body?.string() ?: error("השירות לא החזיר נתוני משמרות תקינים")
                val rootJson = JSONObject(responseBodyStr)
                val candidates = rootJson.optJSONArray("candidates") ?: error("השירות לא החזיר נתוני משמרות תקינים")
                val candidateObj = candidates.optJSONObject(0) ?: error("השירות לא החזיר נתוני משמרות תקינים")
                val contentObjRes = candidateObj.optJSONObject("content") ?: error("השירות לא החזיר נתוני משמרות תקינים")
                val partsArrayRes = contentObjRes.optJSONArray("parts") ?: error("השירות לא החזיר נתוני משמרות תקינים")
                val partObjRes = partsArrayRes.optJSONObject(0) ?: error("השירות לא החזיר נתוני משמרות תקינים")
                val responseText = partObjRes.optString("text") ?: error("השירות לא החזיר נתוני משמרות תקינים")

                responseText
            }
        }
        val rawResponseTemp = responseText().trim()
                    .removePrefix("```json")
                    .removePrefix("```")
                    .removeSuffix("```")
                    .trim()

                val resultList = mutableListOf<ParsedShift>()

                fun parseShiftObject(obj: JSONObject): ParsedShift {
                    val category = obj.optString("category", "עצמאי").ifBlank { "עצמאי" }
                    val date = obj.getLong("date").also { require(it >= 0) { "תאריך לא תקין בתשובת המודל" } }
                    val hours = obj.getDouble("hours").also { require(it.isFinite() && it > 0) { "שעות לא תקינות בתשובת המודל" } }
                    val hourlyRate = obj.getDouble("hourlyRate").also { require(it.isFinite() && it >= 0) { "תעריף לא תקין בתשובת המודל" } }
                    val currency = obj.getString("currency").also { require(it in listOf("₪", "$")) { "מטבע לא מזוהה בתשובת המודל" } }
                    val notes = obj.optString("notes", "")

                    val isGroup = obj.optBoolean("isGroup", false)
                    val groupMembersArray = obj.optJSONArray("groupMembers")
                    val groupMembers = mutableListOf<String>()
                    if (groupMembersArray != null) {
                        for (i in 0 until groupMembersArray.length()) {
                            val member = groupMembersArray.optString(i)
                            if (member.isNotBlank()) {
                                groupMembers.add(member)
                            }
                        }
                    }
                    return ParsedShift(category, date, hours, hourlyRate, notes, isGroup, groupMembers, currency)
                }

                try {
                    if (rawResponseTemp.startsWith("[")) {
                        val jsonArr = org.json.JSONArray(rawResponseTemp)
                        for (i in 0 until jsonArr.length()) {
                            resultList.add(parseShiftObject(jsonArr.getJSONObject(i)))
                        }
                    } else {
                        val jsonObj = JSONObject(rawResponseTemp)
                        val shiftsArr = jsonObj.optJSONArray("shifts") 
                            ?: jsonObj.optJSONArray("entries") 
                            ?: jsonObj.optJSONArray("items")
                        if (shiftsArr != null) {
                            for (i in 0 until shiftsArr.length()) {
                                resultList.add(parseShiftObject(shiftsArr.getJSONObject(i)))
                            }
                        } else {
                            resultList.add(parseShiftObject(jsonObj))
                        }
                    }
                } catch (jsonEx: Exception) {
                    throw IllegalArgumentException("תשובת המודל לא תקינה; לא נשמרו משמרות", jsonEx)
                }

        resultList
    }

    suspend fun parseNaturalLanguageToShift(
        input: String,
        existingCategories: List<String>,
        modelName: String = "Gemini 3.5 Flash"
    ): ParsedShift? {
        val list = parseNaturalLanguageToShifts(input, existingCategories, modelName)
        return list.firstOrNull()
    }
}
