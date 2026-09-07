package com.example.api

import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object AiService {
    data class Model(val id: String, val name: String, val available: Boolean = true)
    val defaults = listOf(
        Model("gemini:gemini-3.8-flash", "Gemini 3.8 Flash"),
        Model("openai:gpt-6-astra", "GPT-6 Astra", configured),
        Model("Gemini 3.5 Flash", "Gemini 3.5 Flash"),
        Model("Gemini 3.1 Flash-Lite", "Gemini 3.1 Flash-Lite")
    )
    val configured: Boolean get() = BuildConfig.AI_SERVICE_URL.startsWith("https://")
    private val client = OkHttpClient.Builder().readTimeout(120, TimeUnit.SECONDS).build()
    private suspend fun token(): String = suspendCancellableCoroutine { continuation ->
        val user = AuthManager.getFirebaseAuthSafely()?.currentUser
        if (user == null) { continuation.resumeWithException(IllegalStateException("נדרשת התחברות לחשבון לשימוש במודלים שבשרת")); return@suspendCancellableCoroutine }
        user.getIdToken(false).addOnSuccessListener { result ->
            if (continuation.isActive) {
                val token = result.token
                if (token == null) continuation.resumeWithException(IllegalStateException("לא התקבל אישור התחברות")) else continuation.resume(token)
            }
        }.addOnFailureListener { if (continuation.isActive) continuation.resumeWithException(it) }
    }
    private suspend fun call(body: JSONObject? = null): JSONObject {
        check(configured) { "חיבור המודלים לשרת עדיין לא הוגדר" }
        val token = token()
        return withContext(Dispatchers.IO) {
            val request = Request.Builder().url(BuildConfig.AI_SERVICE_URL).header("Authorization", "Bearer $token")
            if (body != null) request.post(body.toString().toRequestBody("application/json".toMediaType()))
            client.newCall(request.build()).execute().use { response ->
                val result = JSONObject(response.body?.string() ?: "{}")
                check(response.isSuccessful) { result.optString("error", "שירות המודלים אינו זמין") }
                result
            }
        }
    }
    suspend fun models(): List<Model> {
        val array = call().getJSONArray("models")
        return (0 until array.length()).map { i -> array.getJSONObject(i).let { Model(it.getString("id"), it.getString("name"), it.getBoolean("available")) } }
    }
    suspend fun generate(model: String, prompt: String): String = call(JSONObject().put("model", model).put("prompt", prompt)).getString("text")
}
