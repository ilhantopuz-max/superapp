package com.cardmaster

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.provider.AlarmClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date

const val DAY = 86_400_000L

fun Customer.needsFollowUp(now: Long): Boolean =
    status != "Kazanıldı" && status != "Kayıp" &&
        ((nextContactAt in 1..now) || (lastContactAt in 1..(now - 14 * DAY)))

class PendingAction(val label: String, val action: JSONObject) {
    var state by mutableStateOf(0) // 0 bekliyor, 1 yapıldı, 2 vazgeçildi
}

class ChatMsg(val fromUser: Boolean, val text: String, val pending: PendingAction? = null)

/** Yapay zekâya verilen güncel veri özeti + çevrimdışı özet. */
object Facts {
    private fun d(ms: Long) = SimpleDateFormat("d MMM HH:mm", TR).format(Date(ms))

    suspend fun context(app: Application): String {
        val dao = CeoDb.get(app).dao()
        val now = System.currentTimeMillis()
        val sod = startOfDay(now)
        val sb = StringBuilder()
        val events = dao.eventsSnap().filter { it.startAt >= sod && it.startAt < sod + 8 * DAY }
        sb.append("TAKVİM (bugün + 7 gün):\n")
        events.forEach { sb.append("- ${d(it.startAt)} ${it.title} [${it.kind}]\n") }
        val tasks = dao.openTasksSnap()
        sb.append("AÇIK GÖREVLER:\n")
        tasks.forEach {
            sb.append("- ${it.title}" + (if (it.dueAt > 0) " (vade ${d(it.dueAt)}" + (if (it.dueAt < now) ", GECİKMİŞ" else "") + ")" else "") + "\n")
        }
        val custs = dao.customersSnap()
        sb.append("MÜŞTERİLER:\n")
        custs.forEach {
            sb.append("- ${it.name} (${it.company}) durum=${it.status} son=${if (it.lastContactAt > 0) d(it.lastContactAt) else "-"} " +
                "sonraki=${if (it.nextContactAt > 0) d(it.nextContactAt) else "-"}${if (it.needsFollowUp(now)) " TAKİP GEREKLİ" else ""}\n")
        }
        val quotes = dao.quotesSnap().filter { it.status == "Bekliyor" }
        sb.append("BEKLEYEN TEKLİFLER:\n")
        quotes.forEach { q -> sb.append("- ${q.title} ${q.amount} (${custs.firstOrNull { it.id == q.customerId }?.name ?: "?"})\n") }
        sb.append("SON NOTLAR:\n")
        dao.notesSnap().sortedByDescending { it.updatedAt }.take(8).forEach { sb.append("- ${it.title}: ${it.body.take(100)}\n") }
        val cards = AppDb.get(app).dao().snapshot()
        sb.append("KARTVİZİTLER:\n")
        cards.take(60).forEach { sb.append("- ${it.company} | ${it.name} | ${it.jobTitle} | ${it.city}\n") }
        return sb.toString().take(9000)
    }

    suspend fun localSummary(app: Application): String {
        val dao = CeoDb.get(app).dao()
        val now = System.currentTimeMillis()
        val sod = startOfDay(now)
        val ev = dao.eventsSnap().filter { it.startAt in sod until sod + DAY }.sortedBy { it.startAt }
        val tasks = dao.openTasksSnap()
        val late = tasks.filter { it.dueAt in 1 until now }
        val today = tasks.filter { it.dueAt in sod until sod + DAY }
        val follow = dao.customersSnap().filter { it.needsFollowUp(now) }
        val sb = StringBuilder()
        sb.append("Bugün ${ev.size} etkinlik, ${today.size} görev vadesi var. Geciken görev: ${late.size}. Takip bekleyen müşteri: ${follow.size}.\n")
        ev.take(5).forEach { sb.append("• ${fmtT(it.startAt)} ${it.title}\n") }
        late.take(5).forEach { sb.append("• GECİKMİŞ: ${it.title}\n") }
        follow.take(5).forEach { sb.append("• Takip: ${it.name}\n") }
        return sb.toString().trim()
    }
}

private val NEEDS_CONFIRM = setOf("event_cancel", "whatsapp", "email_draft")

fun assistantPrompt(data: String): String {
    val now = SimpleDateFormat("yyyy-MM-dd HH:mm EEEE", TR).format(Date())
    return """Sen "SüperApp" içindeki kişisel CEO asistanısın. Türkçe, kısa ve net konuş. Kullanıcı: ${AppSettings.userName.ifBlank { "CEO" }}.
Şu an: $now (Europe/Istanbul). "yarın", "haftaya cuma" gibi ifadeleri bu tarihe göre çöz.
SADECE şu JSON'u döndür: {"reply":"kullanıcıya kısa cevap","actions":[...]}
Eylem tipleri (gerekirse birden fazla):
{"type":"note_add","title":"","body":"","category":"Genel"}
{"type":"task_add","title":"","due":"yyyy-MM-dd HH:mm veya boş","priority":0,"customer":"müşteri adı veya boş"}  (priority: 0 düşük,1 normal,2 yüksek)
{"type":"event_add","title":"","start":"yyyy-MM-dd HH:mm","durationMin":60,"location":"","remindMin":15,"kind":"Toplantı|Randevu|Hatırlatma|Etkinlik"}
{"type":"alarm_set","hour":7,"minute":30,"label":""}
{"type":"event_cancel","title":"iptal edilecek etkinlik adı"}
{"type":"customer_add","name":"","company":"","phone":"","email":"","notes":""}
{"type":"customer_note","customer":"müşteri adı","text":""}
{"type":"call","name":"kişi/müşteri/firma adı"}
{"type":"whatsapp","name":"","phone":"","text":"mesaj taslağı"}
{"type":"email_draft","to":"","subject":"","body":""}
{"type":"open","tab":"panel|asistan|card|musteri|takvim|notlar|ayarlar"}
Kurallar: Bilmediğin bilgiyi uydurma; eksikse reply ile sor ve actions boş bırak. Bugünkü işler, müşteri durumu, kartvizit soruları için aşağıdaki VERİ'yi kullan. E-posta, WhatsApp ve iptal işlemleri sadece taslaktır, kullanıcı onaylar.
VERİ:
$data"""
}

class CeoVM(app: Application) : AndroidViewModel(app) {
    val app: Application = app
    val dao = CeoDb.get(app).dao()
    private val cardDao = AppDb.get(app).dao()

    var route by mutableStateOf("panel")
    var message by mutableStateOf<String?>(null)
    var autoListen by mutableStateOf(false)
    var busy by mutableStateOf(false)
    var selCustomer by mutableStateOf<Customer?>(null)

    val msgs = mutableStateListOf<ChatMsg>()
    var assistantBusy by mutableStateOf(false)

    private fun <T> flow(f: kotlinx.coroutines.flow.Flow<List<T>>) =
        f.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val notes = flow(dao.notes())
    val tasks = flow(dao.tasks())
    val events = flow(dao.events())
    val customers = flow(dao.customers())
    val quotes = flow(dao.quotes())

    fun run(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }

    // ---------- kaydetme işlemleri (alarm planlamasıyla birlikte) ----------
    fun saveNote(n: Note) = run { dao.putNote(n.copy(updatedAt = System.currentTimeMillis())) }
    fun deleteNote(n: Note) = run { dao.delNote(n) }

    fun saveTask(t: TaskItem) = run {
        val id = dao.putTask(t)
        Notify.scheduleTask(app, t.copy(id = if (t.id == 0L) id else t.id))
    }

    fun toggleTask(t: TaskItem) = run {
        if (!t.done) {
            val nx = Repeat.next(t.dueAt, t.repeatRule)
            val nt = if (nx > 0) t.copy(dueAt = nx) else t.copy(done = true)
            dao.putTask(nt); Notify.scheduleTask(app, nt)
        } else {
            val nt = t.copy(done = false); dao.putTask(nt); Notify.scheduleTask(app, nt)
        }
    }

    fun deleteTask(t: TaskItem) = run { Notify.cancelTask(app, t.id); dao.delTask(t) }

    suspend fun conflictsOf(e: EventItem): List<EventItem> {
        val end = if (e.endAt > e.startAt) e.endAt else e.startAt + 30 * 60_000L
        return dao.eventsSnap().filter { o ->
            val oEnd = if (o.endAt > o.startAt) o.endAt else o.startAt + 30 * 60_000L
            val overlap = end > o.startAt && oEnd > e.startAt
            o.id != e.id && o.kind != "Hatırlatma" && e.kind != "Hatırlatma" && overlap
        }
    }

    fun saveEvent(e: EventItem) = run {
        val id = dao.putEvent(e)
        Notify.scheduleEvent(app, e.copy(id = if (e.id == 0L) id else e.id))
    }

    fun deleteEvent(e: EventItem) = run { Notify.cancelEvent(app, e.id); dao.delEvent(e) }

    fun saveCustomer(c: Customer, then: (Long) -> Unit = {}) = run { then(dao.putCustomer(c)) }

    fun deleteCustomer(c: Customer) = run {
        dao.delInteractionsOf(c.id); dao.delQuotesOf(c.id); dao.delCustomer(c)
    }

    fun addInteraction(c: Customer, type: String, text: String, next: Long = 0) = run {
        dao.putInteraction(Interaction(customerId = c.id, type = type, summary = text))
        val u = c.copy(lastContactAt = System.currentTimeMillis(), nextContactAt = if (next > 0) next else c.nextContactAt)
        dao.putCustomer(u)
        selCustomer = u
    }

    fun interactions(cid: Long) = dao.interactionsOf(cid)
    fun saveQuote(q: Quote) = run { dao.putQuote(q) }
    fun deleteQuote(q: Quote) = run { dao.delQuote(q) }

    fun fromCard(c: BizCard) = run {
        val exists = dao.customersSnap().any { it.cardId == c.id && c.id != 0L }
        if (exists) { message = "Bu kartvizit zaten müşteri olarak ekli"; return@run }
        dao.putCustomer(
            Customer(name = c.name.ifBlank { c.company }, company = c.company,
                phone = c.mobile.ifBlank { c.phone }, email = c.email, details = c.note, cardId = c.id)
        )
        message = "Müşteri olarak eklendi (Müşteri sekmesi)"
    }

    // ---------- yapay zekâ: özet / danışman ----------
    fun brief(advisor: Boolean = false, onResult: (String) -> Unit) {
        if (busy) return
        busy = true
        viewModelScope.launch {
            try {
                val text = withContext(Dispatchers.IO) {
                    val data = Facts.context(app)
                    val p = if (advisor)
                        "Aşağıdaki verilere göre bir CEO danışmanı gibi: iş yoğunluğunu analiz et, unutulmuş takipleri, gecikmeleri ve riskleri belirt, günün önceliklerini sırala, zaman yönetimi için 3 öneri ver. En fazla 220 kelime, Türkçe, madde için '•' kullan. Veri uydurma.\n\n$data"
                    else
                        "Aşağıdaki verilere göre ${AppSettings.userName.ifBlank { "CEO" }} için en fazla 120 kelimelik Türkçe günlük yönetici özeti yaz: önce bugünün en önemli 3 önceliği, sonra gecikme/risk, sonra takip edilecek müşteriler. Madde için '•' kullan. Veri uydurma.\n\n$data"
                    Ai.ask(p)
                }
                if (!advisor) AppSettings.saveBrief(text)
                onResult(text)
            } catch (e: Exception) {
                val local = Facts.localSummary(app)
                if (!advisor) AppSettings.saveBrief(local)
                onResult((e.message ?: "Hata") + "\n\n(Çevrimdışı özet)\n" + local)
            } finally {
                busy = false
            }
        }
    }

    // ---------- asistan ----------
    fun ask(text: String) {
        val t = text.trim()
        if (t.isEmpty() || assistantBusy) return
        msgs.add(ChatMsg(true, t))
        assistantBusy = true
        viewModelScope.launch {
            try {
                val data = withContext(Dispatchers.IO) { Facts.context(app) }
                val contents = JSONArray()
                msgs.filter { it.pending == null && it.text.isNotBlank() }.takeLast(10).forEach {
                    contents.put(
                        JSONObject().put("role", if (it.fromUser) "user" else "model")
                            .put("parts", JSONArray().put(Ai.textPart(it.text)))
                    )
                }
                val raw = withContext(Dispatchers.IO) { Ai.generate(contents, assistantPrompt(data), json = true) }
                val j = try { JSONObject(Ai.cleanJson(raw)) } catch (e: Exception) { null }
                val reply = j?.optString("reply") ?: raw
                val acts = j?.optJSONArray("actions") ?: JSONArray()
                val results = ArrayList<String>()
                val pend = ArrayList<PendingAction>()
                for (i in 0 until acts.length()) {
                    val a = acts.getJSONObject(i)
                    if (a.optString("type") in NEEDS_CONFIRM) pend.add(PendingAction(describe(a), a))
                    else exec(a).takeIf { it.isNotBlank() }?.let { results.add(it) }
                }
                msgs.add(ChatMsg(false, reply + results.joinToString("") { "\n$it" }))
                pend.forEach { msgs.add(ChatMsg(false, "", it)) }
                Tts.speak(app, reply)
            } catch (e: AiError) {
                if (e.offline) msgs.add(ChatMsg(false, "📴 ${e.message}\n" + offline(t)))
                else msgs.add(ChatMsg(false, "⚠️ ${e.message}"))
            } catch (e: Exception) {
                msgs.add(ChatMsg(false, "⚠️ Hata: ${e.message}"))
            } finally {
                assistantBusy = false
            }
        }
    }

    fun confirm(p: PendingAction, yes: Boolean) {
        if (p.state != 0) return
        if (!yes) { p.state = 2; return }
        p.state = 1
        viewModelScope.launch { msgs.add(ChatMsg(false, exec(p.action))) }
    }

    private fun describe(a: JSONObject): String = when (a.optString("type")) {
        "event_cancel" -> "“${a.optString("title")}” etkinliğini iptal et"
        "whatsapp" -> "WhatsApp → ${a.optString("name").ifBlank { a.optString("phone") }}: ${a.optString("text")}"
        else -> "E-posta → ${a.optString("to")}\nKonu: ${a.optString("subject")}\n${a.optString("body")}"
    }

    private suspend fun offline(text: String): String {
        val n = norm(text)
        Regex("^\\s*not\\s*(al|ekle)?[:,]?\\s*(.+)$", RegexOption.IGNORE_CASE).find(text)?.let {
            val b = it.groupValues[2]
            dao.putNote(Note(title = b.take(40), body = b)); return "📝 Not kaydedildi."
        }
        Regex("^\\s*g[öo]rev\\s*(ekle|ver|al)?[:,]?\\s*(.+)$", RegexOption.IGNORE_CASE).find(text)?.let {
            dao.putTask(TaskItem(title = it.groupValues[2])); return "✅ Görev eklendi."
        }
        if (n.contains("bugun") && (n.contains("is") || n.contains("gorev") || n.contains("toplanti") || n.contains("program")))
            return Facts.localSummary(app)
        return "Çevrimdışı komutlar: “not al …”, “görev ekle …”, “bugünkü işlerimi söyle”."
    }

    private fun startIntent(i: Intent): Boolean = try {
        app.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); true
    } catch (e: Exception) { false }

    private suspend fun findCustomer(name: String): Customer? {
        if (name.isBlank()) return null
        val n = norm(name)
        return dao.customersSnap().firstOrNull { norm(it.name).contains(n) || norm(it.company).contains(n) }
    }

    private suspend fun findPhone(name: String, given: String): String {
        if (given.isNotBlank()) return given
        val n = norm(name)
        if (n.isBlank()) return ""
        dao.customersSnap().firstOrNull { (norm(it.name).contains(n) || norm(it.company).contains(n)) && it.phone.isNotBlank() }
            ?.let { return it.phone }
        cardDao.snapshot().firstOrNull { matches(it, name) && (it.mobile.isNotBlank() || it.phone.isNotBlank()) }
            ?.let { return it.mobile.ifBlank { it.phone } }
        return ""
    }

    private fun waNumber(raw: String): String {
        val d = raw.filter { it.isDigit() }
        return when {
            d.startsWith("90") && d.length == 12 -> d
            d.startsWith("0") && d.length == 11 -> "9" + d
            d.length == 10 && d.startsWith("5") -> "90$d"
            else -> d
        }
    }

    suspend fun exec(a: JSONObject): String {
        when (a.optString("type")) {
            "note_add" -> {
                val body = a.optString("body")
                val title = a.optString("title").ifBlank { body.take(40) }
                dao.putNote(Note(title = title, body = body, category = a.optString("category").ifBlank { "Genel" }))
                return "📝 Not kaydedildi: $title"
            }
            "task_add" -> {
                val cust = findCustomer(a.optString("customer"))
                val t = TaskItem(title = a.optString("title"), dueAt = parseDT(a.optString("due")),
                    priority = a.optInt("priority", 1), customerId = cust?.id ?: 0)
                val id = dao.putTask(t)
                Notify.scheduleTask(app, t.copy(id = id))
                return "✅ Görev eklendi: ${t.title}" + (if (t.dueAt > 0) " (${fmtDT(t.dueAt)})" else "")
            }
            "event_add" -> {
                val st = parseDT(a.optString("start"))
                if (st == 0L) return "⚠️ Tarih/saat anlaşılamadı, etkinlik eklenmedi."
                val e = EventItem(title = a.optString("title"), startAt = st,
                    endAt = st + a.optInt("durationMin", 60) * 60_000L, location = a.optString("location"),
                    remindMin = a.optInt("remindMin", 15), kind = a.optString("kind").ifBlank { "Etkinlik" })
                val conflicts = conflictsOf(e)
                val id = dao.putEvent(e)
                Notify.scheduleEvent(app, e.copy(id = id))
                return "📅 Eklendi: ${e.title} — ${fmtDT(st)}" +
                    (if (conflicts.isNotEmpty()) "\n⚠️ Çakışma: " + conflicts.joinToString { it.title } else "")
            }
            "alarm_set" -> {
                val ok = startIntent(
                    Intent(AlarmClock.ACTION_SET_ALARM)
                        .putExtra(AlarmClock.EXTRA_HOUR, a.optInt("hour"))
                        .putExtra(AlarmClock.EXTRA_MINUTES, a.optInt("minute"))
                        .putExtra(AlarmClock.EXTRA_MESSAGE, a.optString("label"))
                        .putExtra(AlarmClock.EXTRA_SKIP_UI, true)
                )
                return if (ok) "⏰ Alarm kuruldu: %02d:%02d".format(a.optInt("hour"), a.optInt("minute")) else "⚠️ Alarm uygulaması bulunamadı."
            }
            "event_cancel" -> {
                val n = norm(a.optString("title"))
                val e = dao.eventsSnap().filter { it.startAt >= System.currentTimeMillis() - DAY }
                    .firstOrNull { norm(it.title).contains(n) } ?: return "⚠️ Eşleşen etkinlik bulunamadı."
                Notify.cancelEvent(app, e.id); dao.delEvent(e)
                return "🗑 İptal edildi: ${e.title}"
            }
            "customer_add" -> {
                dao.putCustomer(Customer(name = a.optString("name"), company = a.optString("company"),
                    phone = a.optString("phone"), email = a.optString("email"), details = a.optString("notes")))
                return "👔 Müşteri eklendi: ${a.optString("name")}"
            }
            "customer_note" -> {
                val c = findCustomer(a.optString("customer")) ?: return "⚠️ Müşteri bulunamadı: ${a.optString("customer")}"
                dao.putInteraction(Interaction(customerId = c.id, summary = a.optString("text")))
                dao.putCustomer(c.copy(lastContactAt = System.currentTimeMillis()))
                return "🗒 ${c.name} profiline not eklendi."
            }
            "call" -> {
                val ph = findPhone(a.optString("name"), "")
                if (ph.isBlank()) return "⚠️ ${a.optString("name")} için numara bulunamadı."
                startIntent(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$ph")))
                return "📞 Arama ekranı açıldı: $ph"
            }
            "whatsapp" -> {
                val ph = findPhone(a.optString("name"), a.optString("phone"))
                if (ph.isBlank()) return "⚠️ Numara bulunamadı."
                val ok = startIntent(Intent(Intent.ACTION_VIEW,
                    Uri.parse("https://wa.me/${waNumber(ph)}?text=" + Uri.encode(a.optString("text")))))
                return if (ok) "💬 WhatsApp açıldı; göndermek için gönder tuşuna siz basın." else "⚠️ WhatsApp açılamadı."
            }
            "email_draft" -> {
                val i = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:"))
                    .putExtra(Intent.EXTRA_EMAIL, arrayOf(a.optString("to")))
                    .putExtra(Intent.EXTRA_SUBJECT, a.optString("subject"))
                    .putExtra(Intent.EXTRA_TEXT, a.optString("body"))
                return if (startIntent(i)) "✉️ E-posta uygulaması açıldı; kontrol edip siz gönderin." else "⚠️ E-posta uygulaması bulunamadı."
            }
            "open" -> {
                val t = a.optString("tab")
                if (t in setOf("panel", "asistan", "card", "musteri", "takvim", "notlar", "ayarlar")) route = t
                return ""
            }
        }
        return ""
    }
}
