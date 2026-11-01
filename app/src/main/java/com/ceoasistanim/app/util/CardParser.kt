package com.ceoasistanim.app.util

import java.util.Locale

data class ScannedCard(
    val name: String = "", val title: String = "", val company: String = "",
    val phone: String = "", val email: String = "", val web: String = ""
)

/** OCR metninden kartvizit alanlarını tahmin eder. Sonuç her zaman kullanıcı tarafından düzeltilebilir. */
object CardParser {
    private val tr = Locale("tr", "TR")
    private val emailRe = Regex("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}")
    private val phoneRe = Regex("(\\+?\\d[\\d\\s().-]{8,}\\d)")
    private val webRe = Regex("(?i)\\b(?:https?://)?(?:www\\.)?[a-z0-9-]+(?:\\.[a-z0-9-]+)*\\.(?:com\\.tr|com|net|org|biz|info|tr)\\b(?:/\\S*)?")
    private val companyKw = listOf("a.ş", "a.s.", "ltd", "şti", "san.", "tic.", "holding", "inc", "llc", "gmbh", "grup", "group", "company", "otomotiv", "yazılım", "inşaat", "teknoloji")
    private val titleKw = listOf("müdür", "direktör", "genel", "manager", "director", "ceo", "cto", "cfo", "uzman", "mühendis", "başkan", "kurucu", "founder", "sorumlu", "temsilci", "satış", "pazarlama", "avukat", "dr.", "prof", "danışman")

    private fun hasKw(s: String, kws: List<String>): Boolean {
        val l = s.lowercase(tr)
        return kws.any { l.contains(it) }
    }

    fun parse(raw: String): ScannedCard {
        val lines = raw.lines().map { it.trim() }.filter { it.isNotEmpty() }
        val email = emailRe.find(raw)?.value ?: ""
        val web = webRe.find(raw.replace(email, " "))?.value ?: ""
        val phone = lines
            .filterNot { val l = it.lowercase(tr); l.contains("fax") || l.contains("faks") }
            .firstNotNullOfOrNull { phoneRe.find(it)?.value?.trim() } ?: ""

        val rest = lines.filter {
            !it.contains("@") && phoneRe.find(it) == null && webRe.find(it) == null
        }
        val company = rest.firstOrNull { hasKw(it, companyKw) } ?: ""
        val title = rest.firstOrNull { it != company && hasKw(it, titleKw) } ?: ""
        val name = rest.firstOrNull { l ->
            l != company && l != title && l.none { it.isDigit() } && l.length < 40 &&
                l.split(" ").size in 2..4 &&
                l.split(" ").all { w -> w.isNotEmpty() && w[0].isLetter() && w[0].isUpperCase() }
        } ?: ""
        val finalCompany = company.ifEmpty { rest.firstOrNull { it != name && it != title } ?: "" }
        return ScannedCard(name, title, finalCompany, phone, email, web)
    }
}
