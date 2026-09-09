package com.example.api

/** The former shared-owner-key service is intentionally disabled. */
object AiService {
    val configured: Boolean get() = false
    suspend fun generate(prompt: String): String = error("יש להגדיר מפתח ג׳מיני אישי")
}
