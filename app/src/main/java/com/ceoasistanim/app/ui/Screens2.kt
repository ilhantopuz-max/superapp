package com.ceoasistanim.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.unit.dp
import com.ceoasistanim.app.data.Customer
import com.ceoasistanim.app.data.Meeting

// ------------------------------ NOTLAR ------------------------------
@Composable
fun NotesScreen(vm: MainViewModel, mic: ((String) -> Unit) -> Unit) {
    val notes by vm.notes.collectAsStateWithLifecycle()
    var q by remember { mutableStateOf("") }
    var cat by remember { mutableStateOf("Tümü") }
    var showAdd by remember { mutableStateOf(false) }
    val cats = listOf("Tümü") + notes.map { it.category }.distinct()
    val shown = notes.filter {
        (cat == "Tümü" || it.category == cat) &&
            (q.isBlank() || it.title.contains(q, true) || it.content.contains(q, true))
    }
    Box(Modifier.fillMaxSize()) {
        LazyColumn(contentPadding = PaddingValues(16.dp, 16.dp, 16.dp, 90.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item { OutlinedTextField(q, { q = it }, label = { Text("Notlarda ara") }, modifier = Modifier.fillMaxWidth(), singleLine = true) }
            item { Chips(cats, cat) { cat = it } }
            items(shown, key = { it.id }) { n ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Text(n.title, fontWeight = FontWeight.Bold)
                        Text("${n.category} • ${fmt(n.createdAt)}", style = MaterialTheme.typography.labelSmall)
                        Spacer(Modifier.height(4.dp))
                        Text(n.content)
                        Row {
                            TextButton(onClick = { vm.speakText(n.content) }) { Text("Oku") }
                            TextButton(onClick = { vm.summarizeNote(n) }) { Text("Özetle") }
                            TextButton(onClick = { vm.deleteNote(n) }) { Text("Sil") }
                        }
                    }
                }
            }
        }
        FloatingActionButton(onClick = { showAdd = true }, modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)) {
            Icon(Icons.Filled.Add, "Yeni not")
        }
    }
    if (showAdd) {
        var title by remember { mutableStateOf("") }
        var content by remember { mutableStateOf("") }
        var category by remember { mutableStateOf("Genel") }
        val close = { showAdd = false }
        FormDialog("Yeni not", close, { vm.addNote(title, content.trim(), category); close() }, okEnabled = content.isNotBlank()) {
            OutlinedTextField(title, { title = it }, label = { Text("Başlık (isteğe bağlı)") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(category, { category = it }, label = { Text("Kategori") }, modifier = Modifier.fillMaxWidth())
            DictField(content, { content = it }, "Not (yazın veya mikrofona dokunun)", mic, minLines = 4)
        }
    }
}

// ------------------------------ MÜŞTERİLER ------------------------------
@Composable
fun CustomersScreen(vm: MainViewModel, mic: ((String) -> Unit) -> Unit) {
    val customers by vm.customers.collectAsStateWithLifecycle()
    var selected by remember { mutableStateOf<Long?>(null) }
    var showAdd by remember { mutableStateOf(false) }
    var q by remember { mutableStateOf("") }
    val cur = customers.firstOrNull { it.id == selected }
    if (cur != null) { CustomerDetail(vm, cur, mic) { selected = null }; return }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(contentPadding = PaddingValues(16.dp, 16.dp, 16.dp, 90.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item { OutlinedTextField(q, { q = it }, label = { Text("Müşteri ara") }, modifier = Modifier.fillMaxWidth(), singleLine = true) }
            items(customers.filter { q.isBlank() || it.name.contains(q, true) || it.company.contains(q, true) }, key = { it.id }) { c ->
                Card(Modifier.fillMaxWidth(), onClick = { selected = c.id }) {
                    Column(Modifier.padding(12.dp)) {
                        Text(c.name, fontWeight = FontWeight.Bold)
                        Text("${c.company.ifBlank { "—" }} • ${c.status}", style = MaterialTheme.typography.bodySmall)
                        Text("Son: ${fmt(c.lastContact)}  Sonraki: ${fmt(c.nextContact)}", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
        FloatingActionButton(onClick = { showAdd = true }, modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)) {
            Icon(Icons.Filled.Add, "Yeni müşteri")
        }
    }
    if (showAdd) {
        var name by remember { mutableStateOf("") }
        var phone by remember { mutableStateOf("") }
        var email by remember { mutableStateOf("") }
        var company by remember { mutableStateOf("") }
        val close = { showAdd = false }
        FormDialog("Yeni müşteri", close, {
            vm.saveCustomer(Customer(name = name.trim(), phone = phone.trim(), email = email.trim(), company = company.trim())); close()
        }, okEnabled = name.isNotBlank()) {
            OutlinedTextField(name, { name = it }, label = { Text("Ad Soyad") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(company, { company = it }, label = { Text("Firma") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(phone, { phone = it }, label = { Text("Telefon") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(email, { email = it }, label = { Text("E-posta") }, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun CustomerDetail(vm: MainViewModel, c: Customer, mic: ((String) -> Unit) -> Unit, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val all by vm.interactions.collectAsStateWithLifecycle()
    val list = all.filter { it.customerId == c.id }
    var newText by remember(c.id) { mutableStateOf("") }
    var notes by remember(c.id) { mutableStateOf(c.notes) }
    var confirmDelete by remember { mutableStateOf(false) }
    var waText by remember { mutableStateOf<String?>(null) }

    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, "Geri") }
                Text(c.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }
        }
        item {
            InfoCard("Bilgiler") {
                Text("Firma: ${c.company.ifBlank { "—" }}")
                Text("Telefon: ${c.phone.ifBlank { "—" }}")
                Text("E-posta: ${c.email.ifBlank { "—" }}")
                Text("Son görüşme: ${fmt(c.lastContact)}")
                Text("Sonraki görüşme: ${fmt(c.nextContact)}")
                Chips(MainViewModel.STATUSES, c.status) { vm.saveCustomer(c.copy(status = it)) }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = {
                        pickDateTime(ctx, c.nextContact) { t -> vm.saveCustomer(c.copy(nextContact = t)) }
                    }) { Text("Sonraki görüşme") }
                    if (c.nextContact != null) OutlinedButton(onClick = {
                        addToCalendar(ctx, "Görüşme: ${c.name}", c.nextContact)
                    }) { Text("Takvime ekle") }
                }
            }
        }
        item {
            InfoCard("İletişim") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { dial(ctx, c.phone) }, enabled = c.phone.isNotBlank()) { Text("Ara") }
                    Button(onClick = { waText = "" }, enabled = c.phone.isNotBlank()) { Text("WhatsApp") }
                }
                OutlinedButton(onClick = {
                    vm.draftEmail(c, "Müşteri ile takip / görüşme sonrası e-posta") { body ->
                        composeEmail(ctx, c.email, "${c.company.ifBlank { c.name }} – Takip", body)
                    }
                }, enabled = c.email.isNotBlank()) { Text("Yapay zekâ ile e-posta taslağı") }
                Text("E-posta ve WhatsApp mesajları uygulamalarında açılır; göndermek için sizin onayınız gerekir.", style = MaterialTheme.typography.labelSmall)
            }
        }
        item {
            InfoCard("Özel notlar") {
                OutlinedTextField(notes, { notes = it }, modifier = Modifier.fillMaxWidth(), minLines = 3)
                Button(onClick = { vm.saveCustomer(c.copy(notes = notes)) }) { Text("Notu kaydet") }
            }
        }
        item {
            InfoCard("Yeni görüşme kaydı") {
                DictField(newText, { newText = it }, "Görüşme notu (sesle veya yazıyla)", mic, minLines = 3)
                Button(onClick = { vm.addInteraction(c, newText.trim()); newText = "" }, enabled = newText.isNotBlank()) { Text("Görüşmeyi kaydet") }
            }
        }
        items(list, key = { it.id }) { i ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Text(fmt(i.createdAt), style = MaterialTheme.typography.labelSmall)
                    Text(i.text)
                    if (i.summary.isNotBlank()) {
                        Spacer(Modifier.height(6.dp))
                        Text("Yapay zekâ özeti", fontWeight = FontWeight.Bold)
                        Text(i.summary)
                    }
                    Row {
                        TextButton(onClick = { vm.summarizeInteraction(i) }) { Text("Özetle") }
                        TextButton(onClick = { vm.deleteInteraction(i) }) { Text("Sil") }
                    }
                }
            }
        }
        item { TextButton(onClick = { confirmDelete = true }) { Text("Müşteriyi sil", color = MaterialTheme.colorScheme.error) } }
    }
    if (confirmDelete) AlertDialog(
        onDismissRequest = { confirmDelete = false },
        title = { Text("Müşteri silinsin mi?") }, text = { Text("Tüm görüşme kayıtları da silinir.") },
        confirmButton = { TextButton(onClick = { vm.deleteCustomer(c); confirmDelete = false; onBack() }) { Text("Sil") } },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("İptal") } }
    )
    waText?.let { txt ->
        var msg by remember { mutableStateOf(txt) }
        FormDialog("WhatsApp mesajı", { waText = null }, { openWhatsApp(ctx, c.phone, msg); waText = null }, okEnabled = msg.isNotBlank()) {
            DictField(msg, { msg = it }, "Mesaj", mic, minLines = 3)
            OutlinedButton(onClick = {
                vm.ai(MainViewModel.SYSTEM + " Kısa, nazik bir WhatsApp mesajı yaz. Yalnızca mesaj metnini ver.", "Alıcı: ${c.name}. Konu/istek: $msg") { msg = it }
            }) { Text("Yapay zekâ ile yaz") }
            Text("“Kaydet” WhatsApp'ı mesaj hazır şekilde açar; göndermeyi siz onaylarsınız.", style = MaterialTheme.typography.labelSmall)
        }
    }
}

// ------------------------------ TOPLANTILAR ------------------------------
@Composable
fun MeetingsScreen(vm: MainViewModel, mic: ((String) -> Unit) -> Unit) {
    val meetings by vm.meetings.collectAsStateWithLifecycle()
    var selected by remember { mutableStateOf<Long?>(null) }
    var showAdd by remember { mutableStateOf(false) }
    val cur = meetings.firstOrNull { it.id == selected }
    if (cur != null) { MeetingDetail(vm, cur, mic) { selected = null }; return }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(contentPadding = PaddingValues(16.dp, 16.dp, 16.dp, 90.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (meetings.isEmpty()) item { Text("Henüz toplantı yok.") }
            items(meetings, key = { it.id }) { m ->
                Card(Modifier.fillMaxWidth(), onClick = { selected = m.id }) {
                    Column(Modifier.padding(12.dp)) {
                        Text(m.title, fontWeight = FontWeight.Bold)
                        Text(fmt(m.startAt), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
        FloatingActionButton(onClick = { showAdd = true }, modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)) {
            Icon(Icons.Filled.Add, "Yeni toplantı")
        }
    }
    if (showAdd) {
        val ctx = LocalContext.current
        var title by remember { mutableStateOf("") }
        var start by remember { mutableStateOf<Long?>(null) }
        var agenda by remember { mutableStateOf("") }
        val close = { showAdd = false }
        FormDialog("Yeni toplantı", close, { vm.addMeeting(title.trim(), start!!, agenda); close() }, okEnabled = title.isNotBlank() && start != null) {
            OutlinedTextField(title, { title = it }, label = { Text("Başlık") }, modifier = Modifier.fillMaxWidth())
            OutlinedButton(onClick = { pickDateTime(ctx, start) { start = it } }) { Text(if (start == null) "Tarih / saat seç" else fmt(start)) }
            DictField(agenda, { agenda = it }, "Gündem / not", mic, minLines = 2)
            Text("Toplantıdan 15 dk önce bildirim kurulur.", style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun MeetingDetail(vm: MainViewModel, m: Meeting, mic: ((String) -> Unit) -> Unit, onBack: () -> Unit) {
    val ctx = LocalContext.current
    var transcript by remember(m.id) { mutableStateOf(m.transcript) }
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, "Geri") }
                Column {
                    Text(m.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(fmt(m.startAt), style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        item {
            InfoCard("Gündem / hazırlık") {
                Text(m.agenda.ifBlank { "—" })
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { vm.meetingAgenda(m) }) { Text("Yapay zekâ gündemi") }
                    OutlinedButton(onClick = { addToCalendar(ctx, m.title, m.startAt, m.agenda) }) { Text("Takvime ekle") }
                }
            }
        }
        item {
            InfoCard("Toplantı metni (dikte)") {
                DictField(transcript, { transcript = it }, "Konuşulanlar (sesle ekleyin veya yapıştırın)", mic, minLines = 5)
                Text("Not: Uygulama arka planda sürekli ses kaydetmez; her dikte parçasını mikrofona dokunarak eklersiniz.", style = MaterialTheme.typography.labelSmall)
                Button(onClick = { vm.updateMeeting(m.copy(transcript = transcript)) }) { Text("Metni kaydet") }
                Button(onClick = { vm.updateMeeting(m.copy(transcript = transcript)); vm.meetingSummary(m.copy(transcript = transcript)) }, enabled = transcript.isNotBlank()) {
                    Text("Özet, karar ve görevleri çıkar")
                }
            }
        }
        if (m.summary.isNotBlank()) item {
            InfoCard("Toplantı özeti") {
                Text(m.summary)
                Row {
                    TextButton(onClick = { vm.speakText(m.summary) }) { Text("Oku") }
                    TextButton(onClick = { composeEmail(ctx, "", "Toplantı özeti: ${m.title}", m.summary) }) { Text("Takip e-postası") }
                }
            }
        }
        item { TextButton(onClick = { vm.deleteMeeting(m); onBack() }) { Text("Toplantıyı sil", color = MaterialTheme.colorScheme.error) } }
    }
}
