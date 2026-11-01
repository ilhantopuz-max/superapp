package com.ceoasistanim.app.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ceoasistanim.app.data.Task
import java.util.Calendar

@Composable
fun DashboardScreen(vm: MainViewModel, mic: ((String) -> Unit) -> Unit) {
    val tasks by vm.tasks.collectAsStateWithLifecycle()
    val meetings by vm.meetings.collectAsStateWithLifecycle()
    val customers by vm.customers.collectAsStateWithLifecycle()
    val listening by vm.listening.collectAsStateWithLifecycle()
    val heard by vm.heard.collectAsStateWithLifecycle()

    val now = System.currentTimeMillis()
    val end = endOfDay(now)
    val open = tasks.filter { !it.done }
    val overdue = open.filter { val d = it.dueAt; d != null && d < now }
    val today = open.filter { val d = it.dueAt; d != null && d in now..end }
    val done = tasks.count { it.done }
    val todayMeetings = meetings.filter { it.startAt in now..end }
    val upcoming = meetings.filter { it.startAt > end }.sortedBy { it.startAt }.take(3)
    val follow = customers.filter { needsFollowUp(it, now) }
    val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    val greet = when { hour < 12 -> "Günaydın"; hour < 18 -> "İyi günler"; else -> "İyi akşamlar" }

    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text("$greet, CEO", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold) }
        item {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                val inf = rememberInfiniteTransition(label = "pulse")
                val pulse by inf.animateFloat(
                    initialValue = 1f, targetValue = 1.25f,
                    animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "scale"
                )
                Box(
                    Modifier.size(96.dp).scale(if (listening) pulse else 1f)
                        .background(if (listening) Color(0xFFC62828) else MaterialTheme.colorScheme.primary, CircleShape)
                        .clickable { mic { vm.handleCommand(it) } },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.Mic, "Sesli komut", Modifier.size(44.dp), tint = MaterialTheme.colorScheme.onPrimary)
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    if (listening) (heard.ifBlank { "Dinliyorum…" }) else if (heard.isNotBlank()) "“$heard”" else "Konuşmak için dokunun",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Stat("Bugün", (today.size + todayMeetings.size).toString(), Modifier.weight(1f))
                Stat("Geciken", overdue.size.toString(), Modifier.weight(1f))
                Stat("Takip", follow.size.toString(), Modifier.weight(1f))
                Stat("Biten", done.toString(), Modifier.weight(1f))
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { vm.dailyBrief() }) { Text("Günlük özet") }
                OutlinedButton(onClick = { vm.weeklyReport() }) { Text("Haftalık rapor") }
            }
        }
        item {
            InfoCard("Bugünkü toplantılar") {
                if (todayMeetings.isEmpty()) Text("Toplantı yok.")
                todayMeetings.forEach { Text("• ${fmt(it.startAt)} – ${it.title}") }
            }
        }
        item {
            InfoCard("Günlük görevler") {
                if (today.isEmpty()) Text("Bugün için görev yok.")
                today.forEach { Text("• ${it.title} (${fmt(it.dueAt)})") }
            }
        }
        if (overdue.isNotEmpty()) item {
            InfoCard("Geciken görevler") {
                overdue.take(8).forEach { Text("• ${it.title} (${fmt(it.dueAt)})", color = MaterialTheme.colorScheme.error) }
            }
        }
        item {
            InfoCard("Takip edilecek müşteriler") {
                if (follow.isEmpty()) Text("Takip bekleyen müşteri yok.")
                follow.take(6).forEach { Text("• ${it.name} – ${it.company.ifBlank { it.status }}") }
            }
        }
        item {
            InfoCard("Yaklaşan randevular") {
                if (upcoming.isEmpty()) Text("Yaklaşan kayıt yok.")
                upcoming.forEach { Text("• ${fmt(it.startAt)} – ${it.title}") }
            }
        }
    }
}

@Composable
private fun Stat(label: String, value: String, modifier: Modifier) {
    Card(modifier, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(Modifier.padding(10.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(label, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
fun TasksScreen(vm: MainViewModel) {
    val tasks by vm.tasks.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    var showAdd by remember { mutableStateOf(false) }
    val now = System.currentTimeMillis()

    Box(Modifier.fillMaxSize()) {
        LazyColumn(contentPadding = PaddingValues(16.dp, 16.dp, 16.dp, 90.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (tasks.isEmpty()) item { Text("Henüz görev yok. “Yarın saat 10'da toplantım var, hatırlat” diyebilirsiniz.") }
            items(tasks, key = { it.id }) { t -> TaskRow(vm, t, now) { 
                t.dueAt?.let { d -> addToCalendar(ctx, t.title, d) }
            } }
        }
        FloatingActionButton(onClick = { showAdd = true }, modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)) {
            Icon(Icons.Filled.Add, "Yeni görev")
        }
    }
    if (showAdd) AddTaskDialog(vm) { showAdd = false }
}

@Composable
private fun TaskRow(vm: MainViewModel, t: Task, now: Long, onCalendar: () -> Unit) {
    val overdue = !t.done && (t.dueAt ?: Long.MAX_VALUE) < now
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = t.done, onCheckedChange = { vm.toggleTask(t) })
            Column(Modifier.weight(1f)) {
                Text(t.title, textDecoration = if (t.done) TextDecoration.LineThrough else null)
                val rep = when (t.repeat) { "daily" -> " • Günlük"; "weekly" -> " • Haftalık"; "monthly" -> " • Aylık"; else -> "" }
                Text(
                    fmt(t.dueAt) + rep, style = MaterialTheme.typography.bodySmall,
                    color = if (overdue) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (t.dueAt != null) IconButton(onClick = onCalendar) { Icon(Icons.Filled.DateRange, "Takvime ekle") }
            IconButton(onClick = { vm.deleteTask(t) }) { Icon(Icons.Filled.Delete, "Sil") }
        }
    }
}

@Composable
private fun AddTaskDialog(vm: MainViewModel, onClose: () -> Unit) {
    val ctx = LocalContext.current
    var title by remember { mutableStateOf("") }
    var due by remember { mutableStateOf<Long?>(null) }
    var repeat by remember { mutableStateOf("Yok") }
    FormDialog("Yeni görev / hatırlatma", onClose, {
        val r = when (repeat) { "Günlük" -> "daily"; "Haftalık" -> "weekly"; "Aylık" -> "monthly"; else -> "none" }
        vm.addTask(title.trim(), due, r); onClose()
    }, okEnabled = title.isNotBlank()) {
        OutlinedTextField(title, { title = it }, label = { Text("Başlık") }, modifier = Modifier.fillMaxWidth())
        OutlinedButton(onClick = { pickDateTime(ctx, due) { due = it } }) { Text(if (due == null) "Tarih / saat seç" else fmt(due)) }
        Text("Tekrar", style = MaterialTheme.typography.labelMedium)
        Chips(listOf("Yok", "Günlük", "Haftalık", "Aylık"), repeat) { repeat = it }
    }
}
