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
        modelName: String = "Gemini 3.5 Flash"
    ): List<ParsedShift> = withContext(Dispatchers.IO) {
        val apiKey = if (BuildConfig.GEMINI_API_KEY.isNotEmpty()) BuildConfig.GEMINI_API_KEY else "YOUR_FALLBACK_KEY_IF_NEEDED"
        if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
            Log.e(TAG, "API Key is missing or default placeholder value.")
            return@withContext emptyList()
        }

        val modelIdentifier = when (modelName) {
            "Gemini 3.1 Flash-Lite", "gemini-3.1-flash-lite", "gemini-1.5-flash-8b" -> "gemini-3.1-flash-lite"
            "Gemini 3.5 Flash", "gemini-3.5-flash", "gemini-1.5-flash" -> "gemini-3.5-flash"
            else -> "gemini-3.5-flash"
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
            - "hourlyRate": Rate per hour as a Double. If not specified, default to 40.0.
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

        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.e(TAG, "Request failed with status code ${response.code}: ${response.message}")
                    return@withContext emptyList()
                }

                val responseBodyStr = response.body?.string() ?: return@withContext emptyList()
                val rootJson = JSONObject(responseBodyStr)
                val candidates = rootJson.optJSONArray("candidates") ?: return@withContext emptyList()
                val candidateObj = candidates.optJSONObject(0) ?: return@withContext emptyList()
                val contentObjRes = candidateObj.optJSONObject("content") ?: return@withContext emptyList()
                val partsArrayRes = contentObjRes.optJSONArray("parts") ?: return@withContext emptyList()
                val partObjRes = partsArrayRes.optJSONObject(0) ?: return@withContext emptyList()
                val responseText = partObjRes.optString("text") ?: return@withContext emptyList()

                val rawResponseTemp = responseText.trim()
                    .removePrefix("```json")
                    .removePrefix("```")
                    .removeSuffix("```")
                    .trim()

                val resultList = mutableListOf<ParsedShift>()

                fun parseShiftObject(obj: JSONObject): ParsedShift {
                    val category = obj.optString("category", "עצמאי").ifBlank { "עצמאי" }
                    val date = obj.optLong("date", System.currentTimeMillis())
                    val hours = obj.optDouble("hours", 8.0)
                    val hourlyRate = obj.optDouble("hourlyRate", 40.0)
                    val currency = if (obj.optString("currency", "₪") == "$") "$" else "₪"
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
                            val obj = jsonArr.optJSONObject(i)
                            if (obj != null) {
                                resultList.add(parseShiftObject(obj))
                            }
                        }
                    } else {
                        val jsonObj = JSONObject(rawResponseTemp)
                        val shiftsArr = jsonObj.optJSONArray("shifts") 
                            ?: jsonObj.optJSONArray("entries") 
                            ?: jsonObj.optJSONArray("items")
                        if (shiftsArr != null) {
                            for (i in 0 until shiftsArr.length()) {
                                val obj = shiftsArr.optJSONObject(i)
                                if (obj != null) {
                                    resultList.add(parseShiftObject(obj))
                                }
                            }
                        } else {
                            resultList.add(parseShiftObject(jsonObj))
                        }
                    }
                } catch (jsonEx: Exception) {
                    Log.e("AI_Parsing_Error", "Failed to parse. Raw response was: $rawResponseTemp", jsonEx)
                }

                resultList
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception during parsing", e)
            emptyList()
        }
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
