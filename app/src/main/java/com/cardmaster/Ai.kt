package com.cardmaster

import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

class AiError(msg: String, val offline: Boolean = false) : Exception(msg)

/** Gemini genel istemcisi. Anahtar ve model AppSettings'ten gelir (Card sekmesiyle ortak). */
object Ai {
    fun textPart(t: String): JSONObject = JSONObject().put("text", t)

    fun dataPart(mime: String, bytes: ByteArray): JSONObject = JSONObject().put(
        "inline_data",
        JSONObject().put("mime_type", mime).put("data", Base64.encodeToString(bytes, Base64.NO_WRAP))
    )

    fun userTurn(vararg parts: JSONObject): JSONObject {
        val arr = JSONArray()
        parts.forEach { arr.put(it) }
        return JSONObject().put("role", "user").put("parts", arr)
    }

    private fun post(body: JSONObject): JSONObject {
        val key = AppSettings.apiKey
        val model = AppSettings.model.ifBlank { AppSettings.DEFAULT_MODEL }
        if (key.isBlank()) throw AiError("Gemini API anahtarı girilmemiş. Ayarlar sekmesinden ekleyin.", true)
        val c = URL("https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent")
            .openConnection() as HttpURLConnection
        try {
            c.requestMethod = "POST"
            c.connectTimeout = 20000
            c.readTimeout = 120000
            c.doOutput = true
            c.setRequestProperty("Content-Type", "application/json")
            c.setRequestProperty("x-goog-api-key", key)
            c.outputStream.use { it.write(body.toString().toByteArray()) }
            val code = c.responseCode
            val text = (if (code in 200..299) c.inputStream else c.errorStream)
                ?.bufferedReader()?.readText() ?: ""
            if (code !in 200..299) {
                val detail = try {
                    val e = JSONObject(text).getJSONObject("error")
                    e.optString("status") + ": " + e.optString("message")
                } catch (ex: Exception) {
                    text.take(400)
                }
                throw AiError("Gemini hatası (HTTP $code, model: $model)\n$detail")
            }
            return JSONObject(text)
        } catch (e: IOException) {
            throw AiError("İnternet yok ya da Gemini'ye ulaşılamadı.", true)
        } finally {
            c.disconnect()
        }
    }

    private fun textOf(root: JSONObject): String {
        val cands = root.optJSONArray("candidates")
            ?: throw AiError("Gemini boş yanıt döndürdü.")
        val parts = cands.getJSONObject(0).optJSONObject("content")?.optJSONArray("parts")
        val sb = StringBuilder()
        if (parts != null) for (i in 0 until parts.length()) {
            val p = parts.getJSONObject(i)
            if (!p.optBoolean("thought", false) && p.has("text")) sb.append(p.getString("text"))
        }
        return sb.toString()
    }

    fun generate(contents: JSONArray, system: String? = null, json: Boolean = false): String {
        val body = JSONObject().put("contents", contents)
        if (system != null) body.put(
            "system_instruction", JSONObject().put("parts", JSONArray().put(textPart(system)))
        )
        if (json) body.put("generationConfig", JSONObject().put("responseMimeType", "application/json"))
        return textOf(post(body))
    }

    fun ask(prompt: String, system: String? = null, json: Boolean = false): String =
        generate(JSONArray().put(userTurn(textPart(prompt))), system, json)

    /** İnternet aramalı (Google Search grounding) yanıt: metin + kaynaklar (başlık, adres). */
    fun grounded(prompt: String): Pair<String, List<Pair<String, String>>> {
        val body = JSONObject()
            .put("contents", JSONArray().put(userTurn(textPart(prompt))))
            .put("tools", JSONArray().put(JSONObject().put("google_search", JSONObject())))
        val root = post(body)
        val src = ArrayList<Pair<String, String>>()
        val chunks = root.optJSONArray("candidates")?.optJSONObject(0)
            ?.optJSONObject("groundingMetadata")?.optJSONArray("groundingChunks")
        if (chunks != null) for (i in 0 until chunks.length()) {
            val w = chunks.getJSONObject(i).optJSONObject("web") ?: continue
            src.add(Pair(w.optString("title"), w.optString("uri")))
        }
        return Pair(textOf(root), src)
    }

    fun cleanJson(s: String): String {
        val a = s.indexOf('{')
        val b = s.lastIndexOf('}')
        return if (a >= 0 && b > a) s.substring(a, b + 1) else s.trim()
    }
}
