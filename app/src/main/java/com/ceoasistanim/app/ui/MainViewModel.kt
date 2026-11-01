package com.ceoasistanim.app.ui

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ceoasistanim.app.data.*
import com.ceoasistanim.app.util.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.abs

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val ctx: Context = app.applicationContext
    private val db = AppDatabase.get(ctx)
    val prefs = SecurePrefs(ctx)
    private val voice = VoiceManager(ctx)

    private fun <T> Flow<T>.state(init: T): StateFlow<T> = stateIn(viewModelScope, SharingStarted.Eagerly, init)

    val notes = db.notes().observeAll().state(emptyList())
    val tasks = db.tasks().observeAll().state(emptyList())
    val customers = db.customers().observeAll().state(emptyList())
    val interactions = db.interactions().observeAll().state(emptyList())
    val meetings = db.meetings().observeAll().state(emptyList())

    val heard = MutableStateFlow("")
    val reply = MutableStateFlow("")
    val listening = MutableStateFlow(false)
    val busy = MutableStateFlow(false)
    val theme = MutableStateFlow(prefs.theme)

    override fun onCleared() {
        voice.shutdown()
    }

    // ---------- Ses ----------
    fun say(msg: String) {
        reply.value = msg
        if (prefs.speak) voice.speak(msg)
    }

    fun clearReply() { reply.value = "" }

    fun dictate(cb: (String) -> Unit) {
        if (listening.value) { voice.stop(); listening.value = false; return }
        listening.value = true
        heard.value = ""
        voice.listen(
            onPartial = { heard.value = it },
            onResult = { listening.value = false; heard.value = it; cb(it) },
            onError = { listening.value = false; reply.value = it }
        )
    }

    fun speakText(t: String) = voice.speak(t)

    fun handleCommand(text: String) {
        viewModelScope.launch {
            when (val c = CommandParser.parse(text)) {
                is Cmd.Remind -> {
                    val t = Task(title = c.title, dueAt = c.at)
                    val id = db.tasks().insert(t)
                    Reminders.schedule(ctx, t.copy(id = id))
                    say(if (c.at != null) "Tamam, ${fmt(c.at)} için hatırlatma kurdum: ${c.title}" else "Görev eklendi: ${c.title}")
                }
                is Cmd.NoteCmd -> {
                    db.notes().insert(Note(title = c.text.take(40), content = c.text))
                    say("Notu kaydettim.")
                }
                Cmd.Today -> say(todaySummary())
                is Cmd.Ask -> askAi(c.text)
            }
        }
    }

    private fun askAi(q: String) {
        if (!prefs.hasAi()) {
            say("Bu komutu çevrimdışı anlayamadım. Hatırlat, not al ve bugünkü işler komutlarını kullanabilirsiniz. Serbest sorular için Ayarlar'dan yapay zekâ anahtarı girin.")
            return
        }
        ai(SYSTEM, "Güncel durum:\n${contextText()}\n\nSoru/komut: $q") { say(it) }
    }

    // ---------- Yapay zekâ ----------
    fun ai(system: String, user: String, onDone: (String) -> Unit) {
        if (!prefs.hasAi()) { reply.value = "Yapay zekâ anahtarı girilmemiş. Ayarlar'dan ekleyin."; return }
        viewModelScope.launch {
            busy.value = true
            val r = AiClient.complete(prefs, system, user)
            busy.value = false
            r.onSuccess { onDone(it) }.onFailure { reply.value = "Yapay zekâ hatası: ${it.message}" }
        }
    }

    private fun contextText(): String {
        val now = System.currentTimeMillis()
        return buildString {
            append("Şu an: ${fmt(now)}\n")
            append("Açık görevler:\n")
            tasks.value.filter { !it.done }.take(30).forEach { append("- ${it.title} (${fmt(it.dueAt)})\n") }
            append("Toplantılar:\n")
            meetings.value.filter { it.startAt >= now }.take(10).forEach { append("- ${it.title} (${fmt(it.startAt)})\n") }
            append("Takip edilecek müşteriler:\n")
            customers.value.filter { needsFollowUp(it, now) }.take(15).forEach { append("- ${it.name} / ${it.company} / ${it.status}\n") }
        }
    }

    fun dailyBrief() {
        if (!prefs.hasAi()) { say(todaySummary()); return }
        ai(SYSTEM + " Kısa bir günlük yönetici özeti hazırla: öncelikler, riskler, takip edilmesi gereken müşteriler, öneriler.", contextText()) { say(it) }
    }

    fun weeklyReport() {
        val now = System.currentTimeMillis()
        val weekAgo = now - 7L * 24 * 3600 * 1000
        val ctxText = contextText() +
            "\nBu hafta tamamlanan görev sayısı: ${tasks.value.count { it.done }}" +
            "\nBu hafta eklenen müşteri: ${customers.value.count { it.createdAt >= weekAgo }}" +
            "\nBu hafta yapılan görüşme: ${interactions.value.count { it.createdAt >= weekAgo }}"
        ai(SYSTEM + " Haftalık performans raporu hazırla: özet, başarılar, geciken işler, sorunlar, gelecek hafta önerileri.", ctxText) { say(it) }
    }

    fun todaySummary(): String {
        val now = System.currentTimeMillis()
        val end = endOfDay(now)
        val open = tasks.value.filter { !it.done }
        val overdue = open.filter { val d = it.dueAt; d != null && d < now }
        val today = open.filter { val d = it.dueAt; d != null && d in now..end }
        val meets = meetings.value.filter { it.startAt in now..end }
        val follow = customers.value.filter { needsFollowUp(it, now) }
        return buildString {
            if (today.isEmpty() && meets.isEmpty() && overdue.isEmpty()) append("Bugün için planlı işiniz yok. ")
            if (meets.isNotEmpty()) append("Bugün ${meets.size} toplantınız var: ${meets.joinToString { it.title }}. ")
            if (today.isNotEmpty()) append("Bugün ${today.size} göreviniz var: ${today.joinToString { it.title }}. ")
            if (overdue.isNotEmpty()) append("${overdue.size} geciken göreviniz var: ${overdue.take(3).joinToString { it.title }}. ")
            if (follow.isNotEmpty()) append("Takip edilmesi gereken ${follow.size} müşteri var.")
        }.trim()
    }

    // ---------- Görevler ----------
    fun addTask(title: String, due: Long?, repeat: String) {
        viewModelScope.launch {
            val t = Task(title = title, dueAt = due, repeat = repeat)
            val id = db.tasks().insert(t)
            Reminders.schedule(ctx, t.copy(id = id))
        }
    }

    fun toggleTask(t: Task) {
        viewModelScope.launch {
            val n = t.copy(done = !t.done)
            db.tasks().update(n)
            if (n.done) Reminders.cancel(ctx, n) else Reminders.schedule(ctx, n)
        }
    }

    fun deleteTask(t: Task) {
        viewModelScope.launch { Reminders.cancel(ctx, t); db.tasks().delete(t) }
    }

    // ---------- Notlar ----------
    fun addNote(title: String, content: String, category: String) {
        viewModelScope.launch {
            db.notes().insert(Note(title = title.ifBlank { content.take(40) }, content = content, category = category.ifBlank { "Genel" }))
        }
    }

    fun deleteNote(n: Note) { viewModelScope.launch { db.notes().delete(n) } }

    fun summarizeNote(n: Note) =
        ai(SYSTEM + " Notu kısaca özetle.", n.content) { say(it) }

    // ---------- Müşteriler ----------
    fun saveCustomer(c: Customer) { viewModelScope.launch { db.customers().upsert(c) } }

    fun deleteCustomer(c: Customer) {
        viewModelScope.launch { db.interactions().deleteForCustomer(c.id); db.customers().delete(c) }
    }

    fun addInteraction(c: Customer, text: String) {
        viewModelScope.launch {
            db.interactions().insert(Interaction(customerId = c.id, text = text))
            db.customers().upsert(c.copy(lastContact = System.currentTimeMillis()))
        }
    }

    fun deleteInteraction(i: Interaction) { viewModelScope.launch { db.interactions().delete(i) } }

    fun summarizeInteraction(i: Interaction) {
        ai(
            SYSTEM + " Müşteri görüşme notundan şunları çıkar: 1) Kısa özet 2) Müşterinin talepleri 3) Verilen sözler ve yapılacak işler 4) Satış fırsatları 5) Sonraki görüşme için öneriler. Maddeli ve kısa yaz.",
            i.text
        ) { out -> viewModelScope.launch { db.interactions().update(i.copy(summary = out)) } }
    }

    fun draftEmail(c: Customer, goal: String, onDone: (String) -> Unit) {
        val history = interactions.value.filter { it.customerId == c.id }.take(5).joinToString("\n") { it.summary.ifBlank { it.text } }
        ai(
            SYSTEM + " Profesyonel, kısa bir Türkçe e-posta metni yaz. Yalnızca e-posta gövdesini ver.",
            "Alıcı: ${c.name} (${c.company}). Amaç: $goal\nGeçmiş görüşmeler:\n$history"
        ) { onDone(it) }
    }

    // ---------- Toplantılar ----------
    fun addMeeting(title: String, start: Long, agenda: String) {
        viewModelScope.launch {
            val clash = meetings.value.any { abs(it.startAt - start) < 3600_000 }
            db.meetings().insert(Meeting(title = title, startAt = start, agenda = agenda))
            addTask("Toplantı: $title (15 dk sonra)", start - 15 * 60_000, "none")
            if (clash) say("Uyarı: Bu toplantı başka bir toplantıyla çakışıyor olabilir.")
        }
    }

    fun updateMeeting(m: Meeting) { viewModelScope.launch { db.meetings().update(m) } }
    fun deleteMeeting(m: Meeting) { viewModelScope.launch { db.meetings().delete(m) } }

    fun meetingAgenda(m: Meeting) =
        ai(SYSTEM + " Toplantı için gündem maddeleri ve hazırlık önerileri yaz.", "Toplantı: ${m.title}\nNot: ${m.agenda}\n\n${contextText()}") {
            updateMeeting(m.copy(agenda = it))
        }

    fun meetingSummary(m: Meeting) =
        ai(SYSTEM + " Toplantı metninden: özet, alınan kararlar, görevler ve sorumlular, sonraki adımlar çıkar.", m.transcript) {
            updateMeeting(m.copy(summary = it))
        }

    // ---------- Ayarlar / veri ----------
    fun setTheme(v: Int) { prefs.theme = v; theme.value = v }

    fun exportJson(): String {
        val o = JSONObject()
        o.put("notes", JSONArray(notes.value.map {
            JSONObject().put("title", it.title).put("content", it.content).put("category", it.category).put("createdAt", it.createdAt)
        }))
        o.put("tasks", JSONArray(tasks.value.map {
            JSONObject().put("title", it.title).put("dueAt", it.dueAt).put("done", it.done).put("repeat", it.repeat)
        }))
        o.put("customers", JSONArray(customers.value.map {
            JSONObject().put("id", it.id).put("name", it.name).put("phone", it.phone).put("email", it.email)
                .put("company", it.company).put("status", it.status).put("notes", it.notes)
                .put("lastContact", it.lastContact).put("nextContact", it.nextContact)
        }))
        o.put("interactions", JSONArray(interactions.value.map {
            JSONObject().put("customerId", it.customerId).put("text", it.text).put("summary", it.summary).put("createdAt", it.createdAt)
        }))
        o.put("meetings", JSONArray(meetings.value.map {
            JSONObject().put("title", it.title).put("startAt", it.startAt).put("agenda", it.agenda)
                .put("transcript", it.transcript).put("summary", it.summary)
        }))
        return o.toString(2)
    }

    fun deleteAll() {
        viewModelScope.launch {
            tasks.value.forEach { Reminders.cancel(ctx, it) }
            withContext(Dispatchers.IO) { db.clearAllTables() }
            say("Tüm veriler silindi.")
        }
    }

    companion object {
        const val SYSTEM = "Sen bir CEO'nun kişisel yönetici asistanısın. Türkçe, net, kısa ve profesyonel yanıt ver. Bilmediğin şeyi uydurma."
        val STATUSES = listOf("Aday", "Görüşmede", "Teklif Verildi", "Kazanıldı", "Kaybedildi")
    }
}
