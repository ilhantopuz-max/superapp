@file:OptIn(ExperimentalMaterial3Api::class)

package com.cardmaster

import android.content.Intent
import android.net.Uri
import android.provider.AlarmClock
import android.provider.CalendarContract
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.activity.compose.BackHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun TextFieldRow(label: String, value: String, onChange: (String) -> Unit, kb: KeyboardType = KeyboardType.Text, lines: Int = 1) {
    OutlinedTextField(
        value = value, onValueChange = onChange, label = { Text(label) },
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
        keyboardOptions = KeyboardOptions(keyboardType = kb), singleLine = lines == 1, minLines = lines
    )
}

// ------------------------------------------------------------------ NOTLAR
@Composable
fun NotesScreen(ceo: CeoVM) {
    val notes by ceo.notes.collectAsState()
    var query by remember { mutableStateOf("") }
    var cat by remember { mutableStateOf("Hepsi") }
    var editing by remember { mutableStateOf<Note?>(null) }
    var ai by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val cats = listOf("Genel", "İş", "Müşteri", "Fikir", "Kişisel")

    val e = editing
    if (e != null) {
        BackHandler { editing = null }
        var n by remember(e) { mutableStateOf(e) }
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp)) {
            Header(if (e.id == 0L) "Yeni not" else "Notu düzenle", onBack = { editing = null })
            VoiceField("Başlık", n.title, { n = n.copy(title = it) })
            VoiceField("Not (🎤 ile sesle yazdırın)", n.body, { n = n.copy(body = it) }, lines = 8)
            Text("Kategori", fontSize = 12.sp)
            Chips(cats, n.category) { n = n.copy(category = it) }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { ceo.saveNote(n.copy(title = n.title.ifBlank { n.body.take(40) })); editing = null }) { Text("Kaydet") }
                OutlinedButton(enabled = n.body.isNotBlank() && !ceo.busy, onClick = {
                    ceo.busy = true
                    scope.launch {
                        ai = try {
                            withContext(Dispatchers.IO) { Ai.ask("Bu notu Türkçe, 3-5 maddede özetle ve varsa yapılacak işleri çıkar:\n\n${n.body}") }
                        } catch (ex: Exception) { ex.message ?: "Hata" }
                        ceo.busy = false
                    }
                }) { Text(if (ceo.busy) "…" else "✨ Özetle") }
                if (e.id != 0L) TextButton(onClick = { ceo.deleteNote(e); editing = null }) { Text("Sil", color = MaterialTheme.colorScheme.error) }
            }
        }
        ai?.let { ResultDialog("Özet", it, { ai = null }) }
        return
    }

    val list = notes.filter { (cat == "Hepsi" || it.category == cat) && matchesText(query, it.title, it.body, it.category) }
    Column(Modifier.fillMaxSize().padding(12.dp)) {
        Header("Notlar", onBack = { ceo.route = "daha" }, actions = { Button(onClick = { editing = Note() }) { Text("＋ Not") } })
        OutlinedTextField(value = query, onValueChange = { query = it }, label = { Text("Notlarda ara") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Chips(listOf("Hepsi") + cats, cat) { cat = it }
        Text("${list.size} not", fontSize = 12.sp, modifier = Modifier.padding(vertical = 4.dp))
        LazyColumn(Modifier.weight(1f)) {
            items(list, key = { it.id }) { n ->
                Card(Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable { editing = n }) {
                    Column(Modifier.padding(10.dp)) {
                        Text(n.title.ifBlank { "(başlıksız)" }, fontWeight = FontWeight.Bold)
                        Text(n.body, maxLines = 2, fontSize = 13.sp)
                        Hint("${n.category} • ${fmtDT(n.updatedAt)}")
                    }
                }
            }
        }
    }
}

fun matchesText(query: String, vararg fields: String): Boolean {
    val tokens = norm(query).split(" ").filter { it.isNotBlank() }
    if (tokens.isEmpty()) return true
    val hay = norm(fields.joinToString(" "))
    return tokens.all { hay.contains(it) }
}

// ------------------------------------------------------------------ TAKVİM & GÖREVLER
@Composable
fun CalendarScreen(ceo: CeoVM) {
    var tab by remember { mutableStateOf(0) }
    Column(Modifier.fillMaxSize().padding(12.dp)) {
        Header("Takvim & Görevler", onBack = { ceo.route = "daha" })
        TabRow(selectedTabIndex = tab) {
            Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("📅 Takvim") })
            Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("✅ Görevler") })
        }
        Spacer(Modifier.height(8.dp))
        if (tab == 0) EventsTab(ceo) else TasksTab(ceo)
    }
}

@Composable
fun EventsTab(ceo: CeoVM) {
    val ctx = LocalContext.current
    val events by ceo.events.collectAsState()
    var editing by remember { mutableStateOf<EventItem?>(null) }
    var past by remember { mutableStateOf(false) }
    var conflict by remember { mutableStateOf<Pair<EventItem, String>?>(null) }
    val scope = rememberCoroutineScope()
    val now = System.currentTimeMillis()
    val sod = startOfDay(now)

    val e = editing
    if (e != null) {
        BackHandler { editing = null }
        var ev by remember(e) { mutableStateOf(e) }
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            Text(if (e.id == 0L) "Yeni etkinlik" else "Etkinliği düzenle", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            VoiceField("Başlık", ev.title, { ev = ev.copy(title = it) })
            Chips(listOf("Toplantı", "Randevu", "Hatırlatma", "Etkinlik"), ev.kind) { ev = ev.copy(kind = it) }
            DateTimeField("Başlangıç", ev.startAt) { ev = ev.copy(startAt = it, endAt = if (ev.endAt > 0) it + (ev.endAt - ev.startAt) else it + 3_600_000L) }
            Text("Süre (dk)", fontSize = 12.sp)
            val dur = if (ev.endAt > ev.startAt) ((ev.endAt - ev.startAt) / 60_000).toString() else "60"
            Chips(listOf("15", "30", "60", "90", "120"), dur) { ev = ev.copy(endAt = ev.startAt + it.toLong() * 60_000L) }
            TextFieldRow("Yer", ev.location, { ev = ev.copy(location = it) })
            TextFieldRow("Not", ev.details, { ev = ev.copy(details = it) }, lines = 2)
            Text("Önceden hatırlat (dk)", fontSize = 12.sp)
            Chips(listOf("0", "5", "15", "30", "60", "1440"), ev.remindMin.toString()) { ev = ev.copy(remindMin = it.toInt()) }
            Text("Tekrar", fontSize = 12.sp)
            Chips(listOf("Yok", "Günlük", "Haftalık", "Aylık"), ev.repeatRule.ifBlank { "Yok" }) { ev = ev.copy(repeatRule = if (it == "Yok") "" else it) }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    if (ev.title.isBlank() || ev.startAt == 0L) { toast(ctx, "Başlık ve başlangıç zamanı gerekli"); return@Button }
                    val fixed = if (ev.endAt <= ev.startAt) ev.copy(endAt = ev.startAt + 3_600_000L) else ev
                    scope.launch {
                        val c = ceo.conflictsOf(fixed)
                        if (c.isNotEmpty()) conflict = fixed to c.joinToString { it.title }
                        else { ceo.saveEvent(fixed); editing = null }
                    }
                }) { Text("Kaydet") }
                OutlinedButton(onClick = { editing = null }) { Text("İptal") }
                if (e.id != 0L) TextButton(onClick = { ceo.deleteEvent(e); editing = null }) { Text("Sil", color = MaterialTheme.colorScheme.error) }
            }
            if (ev.startAt > 0) Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = {
                    try {
                        ctx.startActivity(Intent(Intent.ACTION_INSERT, CalendarContract.Events.CONTENT_URI)
                            .putExtra(CalendarContract.Events.TITLE, ev.title)
                            .putExtra(CalendarContract.Events.EVENT_LOCATION, ev.location)
                            .putExtra(CalendarContract.Events.DESCRIPTION, ev.details)
                            .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, ev.startAt)
                            .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, if (ev.endAt > ev.startAt) ev.endAt else ev.startAt + 3_600_000L))
                    } catch (ex: Exception) { toast(ctx, "Takvim uygulaması bulunamadı") }
                }) { Text("Google Takvim'e ekle") }
            }
        }
        conflict?.let { (cev, names) ->
            ConfirmDialog("Çakışma var", "Bu saatte başka bir kayıt var: $names\nYine de kaydedilsin mi?", "Kaydet",
                { ceo.saveEvent(cev); editing = null }, { conflict = null })
        }
        return
    }

    var alarmMsg by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize()) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { editing = EventItem(startAt = (now / 3_600_000L + 1) * 3_600_000L) }) { Text("＋ Etkinlik") }
            OutlinedButton(onClick = { pickDateTime(ctx, now) { t ->
                val c = java.util.Calendar.getInstance().apply { timeInMillis = t }
                try {
                    ctx.startActivity(Intent(AlarmClock.ACTION_SET_ALARM)
                        .putExtra(AlarmClock.EXTRA_HOUR, c.get(java.util.Calendar.HOUR_OF_DAY))
                        .putExtra(AlarmClock.EXTRA_MINUTES, c.get(java.util.Calendar.MINUTE)))
                } catch (ex: Exception) { toast(ctx, "Alarm uygulaması bulunamadı") }
            } }) { Text("⏰ Alarm kur") }
            TextButton(onClick = { past = !past }) { Text(if (past) "Yaklaşanlar" else "Geçmiş") }
        }
        val list = events.filter { if (past) it.startAt < sod else it.startAt >= sod }.let { if (past) it.reversed() else it }
        val firstOfDay = list.groupBy { fmtDay(it.startAt) }.values.map { it.first().id }.toSet()
        if (list.isEmpty()) Hint(if (past) "Geçmiş kayıt yok." else "Yaklaşan etkinlik yok.")
        LazyColumn(Modifier.weight(1f)) {
            items(list, key = { it.id }) { ev ->
                Column {
                    if (ev.id in firstOfDay) SubTitle(fmtDay(ev.startAt))
                    Card(Modifier.fillMaxWidth().padding(vertical = 3.dp).clickable { editing = ev }) {
                        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(fmtT(ev.startAt), fontWeight = FontWeight.Bold, modifier = Modifier.width(52.dp))
                            Column {
                                Text(ev.title, fontWeight = FontWeight.Medium)
                                Hint(listOf(ev.kind, ev.location, ev.repeatRule).filter { it.isNotBlank() }.joinToString(" • "))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TasksTab(ceo: CeoVM) {
    val ctx = LocalContext.current
    val tasks by ceo.tasks.collectAsState()
    var editing by remember { mutableStateOf<TaskItem?>(null) }
    var showDone by remember { mutableStateOf(false) }
    var ai by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val now = System.currentTimeMillis()

    val e = editing
    if (e != null) {
        BackHandler { editing = null }
        var t by remember(e) { mutableStateOf(e) }
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            Text(if (e.id == 0L) "Yeni görev" else "Görevi düzenle", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            VoiceField("Görev", t.title, { t = t.copy(title = it) })
            TextFieldRow("Detay", t.details, { t = t.copy(details = it) }, lines = 2)
            DateTimeField("Vade", t.dueAt) { t = t.copy(dueAt = it) }
            Text("Öncelik", fontSize = 12.sp)
            Chips(listOf("Düşük", "Normal", "Yüksek"), listOf("Düşük", "Normal", "Yüksek")[t.priority.coerceIn(0, 2)]) {
                t = t.copy(priority = listOf("Düşük", "Normal", "Yüksek").indexOf(it))
            }
            Text("Tekrar", fontSize = 12.sp)
            Chips(listOf("Yok", "Günlük", "Haftalık", "Aylık"), t.repeatRule.ifBlank { "Yok" }) { t = t.copy(repeatRule = if (it == "Yok") "" else it) }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    if (t.title.isBlank()) { toast(ctx, "Görev başlığı gerekli"); return@Button }
                    ceo.saveTask(t); editing = null
                }) { Text("Kaydet") }
                OutlinedButton(onClick = { editing = null }) { Text("İptal") }
                if (e.id != 0L) TextButton(onClick = { ceo.deleteTask(e); editing = null }) { Text("Sil", color = MaterialTheme.colorScheme.error) }
            }
        }
        return
    }

    Column(Modifier.fillMaxSize()) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { editing = TaskItem() }) { Text("＋ Görev") }
            OutlinedButton(enabled = !ceo.busy, onClick = {
                ceo.busy = true
                val open = tasks.filter { !it.done }
                scope.launch {
                    ai = try {
                        withContext(Dispatchers.IO) {
                            Ai.ask("Aşağıdaki açık görevleri aciliyet ve önemine göre sırala; her biri için tek cümle gerekçe ver. Türkçe, numaralı liste. Şu an: ${fmtDT(now)}\n\n" +
                                open.joinToString("\n") { "- ${it.title} (vade ${fmtDT(it.dueAt)}, öncelik ${it.priority})" })
                        }
                    } catch (ex: Exception) { ex.message ?: "Hata" }
                    ceo.busy = false
                }
            }) { Text(if (ceo.busy) "…" else "🧠 Önceliklendir") }
            TextButton(onClick = { showDone = !showDone }) { Text(if (showDone) "Açıklar" else "Tamamlanan") }
        }
        val list = tasks.filter { it.done == showDone }
        if (list.isEmpty()) Hint(if (showDone) "Tamamlanan görev yok." else "Açık görev yok.")
        LazyColumn(Modifier.weight(1f)) {
            items(list, key = { it.id }) { t ->
                val late = !t.done && t.dueAt in 1 until now
                Card(Modifier.fillMaxWidth().padding(vertical = 3.dp).clickable { editing = t }) {
                    Row(Modifier.padding(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = t.done, onCheckedChange = { ceo.toggleTask(t) })
                        Column {
                            Text((if (t.priority == 2) "❗ " else "") + t.title, fontWeight = FontWeight.Medium)
                            if (t.dueAt > 0) Text("Vade: ${fmtDT(t.dueAt)}" + (if (t.repeatRule.isNotBlank()) " • ${t.repeatRule}" else ""),
                                fontSize = 12.sp, color = if (late) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
    ai?.let { ResultDialog("Önerilen sıra", it, { ai = null }) }
}

// ------------------------------------------------------------------ MÜŞTERİ / CRM
val STATUSES = listOf("Aday", "Görüşülüyor", "Teklif verildi", "Kazanıldı", "Kayıp")

@Composable
fun CrmScreen(ceo: CeoVM) {
    val customers by ceo.customers.collectAsState()
    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf("Hepsi") }
    val now = System.currentTimeMillis()
    val sel = ceo.selCustomer

    if (sel != null) {
        BackHandler { ceo.selCustomer = null }
        CustomerDetail(ceo, sel)
        return
    }
    val list = customers.filter {
        (filter == "Hepsi" || (filter == "Takip" && it.needsFollowUp(now)) || it.status == filter) &&
            matchesText(query, it.name, it.company, it.phone, it.email, it.details)
    }
    Column(Modifier.fillMaxSize().padding(12.dp)) {
        Header("Müşteriler", actions = { Button(onClick = { ceo.selCustomer = Customer() }) { Text("＋ Müşteri") } })
        OutlinedTextField(value = query, onValueChange = { query = it }, label = { Text("Ara: isim, firma, telefon…") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Chips(listOf("Hepsi", "Takip") + STATUSES, filter) { filter = it }
        Text("${list.size} müşteri", fontSize = 12.sp, modifier = Modifier.padding(vertical = 4.dp))
        if (customers.isEmpty()) Hint("Müşteri yok. “＋ Müşteri” ile ekleyin ya da Card sekmesinde bir kartviziti açıp “Müşteri yap” deyin.")
        LazyColumn(Modifier.weight(1f)) {
            items(list, key = { it.id }) { c ->
                Card(Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable { ceo.selCustomer = c }) {
                    Column(Modifier.padding(10.dp)) {
                        Text(c.name.ifBlank { c.company }, fontWeight = FontWeight.Bold)
                        if (c.company.isNotBlank() && c.name.isNotBlank()) Text(c.company, fontSize = 13.sp)
                        Text(c.status + (if (c.nextContactAt > 0) " • Sonraki: ${fmtDT(c.nextContactAt)}" else "") + (if (c.needsFollowUp(now)) " • ⚠ takip" else ""),
                            fontSize = 12.sp, color = if (c.needsFollowUp(now)) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
fun CustomerDetail(ceo: CeoVM, start: Customer) {
    val ctx = LocalContext.current
    var c by remember(start) { mutableStateOf(start) }
    val inter by remember(start.id) { ceo.interactions(start.id) }.collectAsState(initial = emptyList())
    val quotes by ceo.quotes.collectAsState()
    val myQuotes = quotes.filter { it.customerId == start.id && start.id != 0L }
    var note by remember { mutableStateOf("") }
    var qTitle by remember { mutableStateOf("") }
    var qAmount by remember { mutableStateOf("") }
    var confirmDel by remember { mutableStateOf(false) }
    var ai by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp)) {
        Header(if (start.id == 0L) "Yeni müşteri" else "Müşteri", onBack = { ceo.selCustomer = null })
        TextFieldRow("Ad Soyad", c.name, { c = c.copy(name = it) })
        TextFieldRow("Firma", c.company, { c = c.copy(company = it) })
        TextFieldRow("Telefon", c.phone, { c = c.copy(phone = it) }, KeyboardType.Phone)
        TextFieldRow("E-posta", c.email, { c = c.copy(email = it) }, KeyboardType.Email)
        Text("Satış durumu", fontSize = 12.sp)
        Chips(STATUSES, c.status) { c = c.copy(status = it) }
        DateTimeField("Son görüşme", c.lastContactAt) { c = c.copy(lastContactAt = it) }
        DateTimeField("Sonraki görüşme", c.nextContactAt) { c = c.copy(nextContactAt = it) }
        VoiceField("Özel notlar", c.details, { c = c.copy(details = it) }, lines = 3)
        Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {
                if (c.name.isBlank() && c.company.isBlank()) { toast(ctx, "Ad veya firma gerekli"); return@Button }
                ceo.saveCustomer(c) { id -> ceo.selCustomer = c.copy(id = if (c.id == 0L) id else c.id) }
                toast(ctx, "Kaydedildi")
            }) { Text("Kaydet") }
            if (c.phone.isNotBlank()) OutlinedButton(onClick = { ctx.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + c.phone))) }) { Text("📞") }
            if (c.email.isNotBlank()) OutlinedButton(onClick = { ctx.startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:" + c.email))) }) { Text("✉") }
        }

        if (start.id != 0L) {
            SubTitle("Görüşme geçmişi")
            VoiceField("Yeni görüşme notu", note, { note = it }, lines = 2)
            Button(enabled = note.isNotBlank(), onClick = { ceo.addInteraction(start.copy(nextContactAt = c.nextContactAt), "Görüşme", note); note = "" }) { Text("Notu ekle") }
            OutlinedButton(enabled = !ceo.busy && (inter.isNotEmpty() || c.details.isNotBlank()), modifier = Modifier.padding(top = 6.dp), onClick = {
                ceo.busy = true
                scope.launch {
                    ai = try {
                        withContext(Dispatchers.IO) {
                            Ai.ask("Bir CEO için bu müşteri hakkında: kısa durum özeti, müşterinin talepleri, verilen sözler/yapılacaklar, satış fırsatı ve bir sonraki görüşme için 3 öneri yaz. Türkçe, madde için '•'. Veri uydurma.\n\n" +
                                "Müşteri: ${c.name} (${c.company}), durum: ${c.status}\nNotlar: ${c.details}\nGörüşmeler:\n" +
                                inter.joinToString("\n") { "- ${fmtDT(it.whenAt)}: ${it.summary}" })
                        }
                    } catch (ex: Exception) { ex.message ?: "Hata" }
                    ceo.busy = false
                }
            }) { Text(if (ceo.busy) "…" else "🧠 Yapay zekâ önerisi") }
            inter.forEach { i ->
                Card(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                    Column(Modifier.padding(8.dp)) { Hint("${fmtDT(i.whenAt)} • ${i.type}"); Text(i.summary, fontSize = 14.sp) }
                }
            }

            SubTitle("Teklifler")
            myQuotes.forEach { q ->
                Card(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                    Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) { Text(q.title, fontWeight = FontWeight.Medium); Hint("${"%,.0f".format(q.amount)} ₺ • ${q.status}") }
                        TextButton(onClick = {
                            val next = when (q.status) { "Bekliyor" -> "Kabul"; "Kabul" -> "Red"; else -> "Bekliyor" }
                            ceo.saveQuote(q.copy(status = next))
                        }) { Text(q.status) }
                        TextButton(onClick = { ceo.deleteQuote(q) }) { Text("🗑") }
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(value = qTitle, onValueChange = { qTitle = it }, label = { Text("Teklif") }, singleLine = true, modifier = Modifier.weight(1f))
                OutlinedTextField(value = qAmount, onValueChange = { qAmount = it }, label = { Text("₺") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.width(100.dp))
            }
            Button(enabled = qTitle.isNotBlank(), modifier = Modifier.padding(top = 6.dp), onClick = {
                ceo.saveQuote(Quote(customerId = start.id, title = qTitle, amount = qAmount.replace(',', '.').toDoubleOrNull() ?: 0.0))
                qTitle = ""; qAmount = ""
            }) { Text("Teklif ekle") }

            Spacer(Modifier.height(12.dp))
            TextButton(onClick = { confirmDel = true }) { Text("🗑 Müşteriyi sil", color = MaterialTheme.colorScheme.error) }
        }
        Spacer(Modifier.height(24.dp))
    }
    ai?.let { ResultDialog("Öneriler", it, { ai = null }) }
    if (confirmDel) ConfirmDialog("Silinsin mi?", "${c.name.ifBlank { c.company }} ve tüm görüşme/teklif kayıtları silinecek.", "Sil",
        { ceo.deleteCustomer(start); ceo.selCustomer = null }, { confirmDel = false })
}
