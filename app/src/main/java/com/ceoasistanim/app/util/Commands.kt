package com.ceoasistanim.app.util

import java.util.Calendar
import java.util.Locale

sealed class Cmd {
    data class Remind(val title: String, val at: Long?) : Cmd()
    data class NoteCmd(val text: String) : Cmd()
    object Today : Cmd()
    data class Ask(val text: String) : Cmd()
}

/** İnternetsiz çalışan basit Türkçe komut ayrıştırıcı. Anlaşılmayan komutlar yapay zekâya gider. */
object CommandParser {
    private val tr = Locale("tr", "TR")
    private val hourRe = Regex("saat\\s*(\\d{1,2})(?:[:.](\\d{2}))?(?:['’]\\w+)?")

    fun parse(raw: String): Cmd {
        val s = raw.trim().lowercase(tr)
        return when {
            s.contains("hatırlat") -> parseRemind(s)
            s.contains("not al") || s.startsWith("not ") -> {
                val t = s.replace("not al", "").removePrefix("not").trim(' ', ',', '.', ':')
                Cmd.NoteCmd(if (t.isBlank()) raw.trim() else t.replaceFirstChar { it.uppercase(tr) })
            }
            (s.contains("bugün") && (s.contains("iş") || s.contains("görev") || s.contains("program"))) ||
                s.contains("işlerimi söyle") -> Cmd.Today
            else -> Cmd.Ask(raw.trim())
        }
    }

    private fun parseRemind(s: String): Cmd {
        val cal = Calendar.getInstance()
        val m = hourRe.find(s)
        var hasDay = false
        if (s.contains("yarın")) { cal.add(Calendar.DAY_OF_YEAR, 1); hasDay = true }
        else if (s.contains("haftaya")) { cal.add(Calendar.DAY_OF_YEAR, 7); hasDay = true }
        else if (s.contains("bugün")) hasDay = true

        var at: Long? = null
        if (m != null) {
            val h = m.groupValues[1].toInt().coerceIn(0, 23)
            val min = m.groupValues[2].ifEmpty { "0" }.toInt().coerceIn(0, 59)
            cal.set(Calendar.HOUR_OF_DAY, h); cal.set(Calendar.MINUTE, min)
            cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
            if (!hasDay && cal.timeInMillis <= System.currentTimeMillis()) cal.add(Calendar.DAY_OF_YEAR, 1)
            at = cal.timeInMillis
        } else if (hasDay) {
            cal.set(Calendar.HOUR_OF_DAY, 9); cal.set(Calendar.MINUTE, 0)
            cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
            at = cal.timeInMillis
        }

        var title = s.replace(hourRe, " ")
        listOf("hatırlatır mısın", "hatırlat", "yarın", "bugün", "haftaya", "lütfen").forEach {
            title = title.replace(it, " ")
        }
        title = title.replace(Regex("\\s+"), " ").trim(' ', ',', '.', ':')
        if (title.isBlank()) title = "Hatırlatma"
        return Cmd.Remind(title.replaceFirstChar { it.uppercase(tr) }, at)
    }
}
