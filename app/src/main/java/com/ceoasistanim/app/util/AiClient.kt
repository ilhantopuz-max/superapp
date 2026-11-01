package com.ceoasistanim.app.util

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** Değiştirilebilir yapay zekâ altyapısı: Google Gemini (ücretsiz katman) veya Anthropic. */
object AiClient {
    suspend fun complete(p: SecurePrefs, system: String, user: String): Result<String> =
        withContext(Dispatchers.IO) {
            runCatching {
                if (p.provider == "anthropic") anthropic(p, system, user) else gemini(p, system, user)
            }
        }

    private fun gemini(p: SecurePrefs, system: String, user: String): String {
        val body = JSONObject()
            .put("system_instruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", system))))
            .put(
                "contents", JSONArray().put(
                    JSONObject().put("role", "user")
                        .put("parts", JSONArray().put(JSONObject().put("text", user)))
                )
            )
        val base = p.baseUrl.trimEnd('/').ifBlank { "https://generativelanguage.googleapis.com/v1beta" }
        val res = post(
            "$base/models/${p.model}:generateContent",
            mapOf("x-goog-api-key" to p.apiKey),
            body.toString()
        )
        val parts = JSONObject(res).getJSONArray("candidates").getJSONObject(0)
            .getJSONObject("content").getJSONArray("parts")
        val sb = StringBuilder()
        for (k in 0 until parts.length()) sb.append(parts.getJSONObject(k).optString("text"))
        return sb.toString().trim()
    }

    private fun anthropic(p: SecurePrefs, system: String, user: String): String {
        val body = JSONObject()
            .put("model", p.model)
            .put("max_tokens", 1500)
            .put("system", system)
            .put("messages", JSONArray().put(JSONObject().put("role", "user").put("content", user)))
        val res = post(
            p.baseUrl.trimEnd('/') + "/messages",
            mapOf("x-api-key" to p.apiKey, "anthropic-version" to "2023-06-01"),
            body.toString()
        )
        return JSONObject(res).getJSONArray("content").getJSONObject(0).getString("text").trim()
    }

    private fun post(url: String, headers: Map<String, String>, body: String): String {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "POST"
            conn.connectTimeout = 20000
            conn.readTimeout = 90000
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json")
            headers.forEach { (k, v) -> conn.setRequestProperty(k, v) }
            conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""
            if (code !in 200..299) throw IOException("HTTP $code: ${text.take(300)}")
            return text
        } finally {
            conn.disconnect()
        }
    }
}
