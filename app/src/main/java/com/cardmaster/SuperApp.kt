@file:OptIn(ExperimentalMaterial3Api::class)

package com.cardmaster

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.speech.tts.TextToSpeech
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar
import java.util.Locale

object Tts {
    private var tts: TextToSpeech? = null
    private var ready = false

    fun speak(ctx: Context, text: String) {
        if (!AppSettings.speak || text.isBlank()) return
        val t = text.take(900)
        if (tts == null) {
            tts = TextToSpeech(ctx.applicationContext) { st ->
                ready = st == TextToSpeech.SUCCESS
                if (ready) {
                    tts?.setLanguage(Locale("tr", "TR"))
                    tts?.speak(t, TextToSpeech.QUEUE_FLUSH, null, "r")
                }
            }
        } else if (ready) tts?.speak(t, TextToSpeech.QUEUE_FLUSH, null, "r")
    }

    fun stop() { tts?.stop() }
}

private val LightScheme = lightColorScheme(
    primary = Color(0xFF0F4C5C), onPrimary = Color.White,
    primaryContainer = Color(0xFFD6ECF0), onPrimaryContainer = Color(0xFF062A33),
    secondary = Color(0xFFE36414), secondaryContainer = Color(0xFFFFE0CC),
    background = Color(0xFFF7F9FA), surface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFE9EFF1), onSurfaceVariant = Color(0xFF3F4A4D)
)
private val DarkScheme = darkColorScheme(
    primary = Color(0xFF7FCBDA), onPrimary = Color(0xFF00323C),
    primaryContainer = Color(0xFF174955), onPrimaryContainer = Color(0xFFD6ECF0),
    secondary = Color(0xFFFFB68A), secondaryContainer = Color(0xFF5A2D10),
    background = Color(0xFF101415), surface = Color(0xFF161B1D),
    surfaceVariant = Color(0xFF263033), onSurfaceVariant = Color(0xFFBFC8CB),
    onSurface = Color(0xFFE1E6E8), onBackground = Color(0xFFE1E6E8)
)

@Composable
fun SuperRoot(ceo: CeoVM, card: VM, unlocked: Boolean, onUnlock: () -> Unit) {
    val dark = when (AppSettings.theme) {
        "Koyu" -> true
        "Açık" -> false
        else -> isSystemInDarkTheme()
    }
    MaterialTheme(colorScheme = if (dark) DarkScheme else LightScheme) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            if (!unlocked) LockScreen(onUnlock) else SuperApp(ceo, card)
        }
    }
}

@Composable
fun LockScreen(onUnlock: () -> Unit) {
    var pin by remember { mutableStateOf("") }
    var err by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().padding(32.dp), Arrangement.Center, Alignment.CenterHorizontally) {
        Text("🔒", fontSize = 48.sp)
        Title("SüperApp")
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(
            value = pin, onValueChange = { pin = it.filter { c -> c.isDigit() }.take(8); err = false },
            label = { Text("PIN") }, singleLine = true, isError = err,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword)
        )
        if (err) Text("PIN hatalı", color = MaterialTheme.colorScheme.error)
        Spacer(Modifier.height(12.dp))
        Button(onClick = { if (AppSettings.checkPin(pin)) onUnlock() else { err = true; pin = "" } }) { Text("Aç") }
    }
}

@Composable
fun SuperApp(ceo: CeoVM, card: VM) {
    val ctx = LocalContext.current
    LaunchedEffect(ceo.message) {
        ceo.message?.let { toast(ctx, it); ceo.message = null }
    }
    val notifPerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) notifPerm.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    val bottom = when (ceo.route) {
        "panel", "asistan", "card", "musteri" -> ceo.route
        else -> "daha"
    }
    BackHandler(enabled = ceo.route != "panel") {
        ceo.route = if (ceo.route in listOf("takvim", "notlar", "ayarlar")) "daha" else "panel"
    }
    Scaffold(
        bottomBar = {
            NavigationBar {
                listOf("panel" to ("🏠" to "Panel"), "asistan" to ("🎙" to "Asistan"), "card" to ("💳" to "Card"),
                    "musteri" to ("👔" to "Müşteri"), "daha" to ("☰" to "Daha")).forEach { (r, p) ->
                    NavigationBarItem(
                        selected = bottom == r, onClick = { ceo.route = r },
                        icon = { Text(p.first, fontSize = 20.sp) }, label = { Text(p.second, fontSize = 11.sp) }
                    )
                }
            }
        }
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize().imePadding()) {
            when (ceo.route) {
                "panel" -> PanelScreen(ceo)
                "asistan" -> AssistantScreen(ceo)
                "card" -> CardTab(card)
                "musteri" -> CrmScreen(ceo)
                "takvim" -> CalendarScreen(ceo)
                "notlar" -> NotesScreen(ceo)
                "ayarlar" -> SettingsTab(ceo)
                else -> MoreScreen(ceo)
            }
        }
    }
}

@Composable
fun MoreScreen(ceo: CeoVM) {
    val tiles = listOf(
        Triple("takvim", "📅", "Takvim & Görevler"), Triple("notlar", "📝", "Notlar"),
        Triple("ayarlar", "⚙", "Ayarlar")
    )
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp)) {
        Title("Daha")
        Spacer(Modifier.height(8.dp))
        tiles.chunked(2).forEach { row ->
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { (r, ic, name) ->
                    Card(Modifier.weight(1f).clickable { ceo.route = r }) {
                        Column(Modifier.padding(16.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(ic, fontSize = 30.sp); Text(name, fontWeight = FontWeight.Medium)
                        }
                    }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
        Spacer(Modifier.height(12.dp))
        Hint("Yakında: Toplantı asistanı, E-posta, Rehber & WhatsApp, Dosyalar, Her yerde arama.")
    }
}

// ------------------------------------------------------------------ PANEL
@Composable
fun StatTile(modifier: Modifier, value: Int, label: String, warn: Boolean = false, onClick: () -> Unit) {
    Card(modifier.clickable { onClick() }) {
        Column(Modifier.padding(12.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("$value", fontSize = 26.sp, fontWeight = FontWeight.Bold,
                color = if (warn && value > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
            Text(label, fontSize = 11.sp)
        }
    }
}

@Composable
fun PanelScreen(ceo: CeoVM) {
    val events by ceo.events.collectAsState()
    val tasks by ceo.tasks.collectAsState()
    val customers by ceo.customers.collectAsState()
    val quotes by ceo.quotes.collectAsState()
    var dialog by remember { mutableStateOf<Pair<String, String>?>(null) }
    val now = System.currentTimeMillis()
    val sod = startOfDay(now)
    val todayEv = events.filter { it.startAt in sod until sod + DAY }
    val open = tasks.filter { !it.done }
    val late = open.filter { it.dueAt in 1 until now }
    val follow = customers.filter { it.needsFollowUp(now) }
    val pendingQ = quotes.filter { it.status == "Bekliyor" }
    val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    val greet = when { hour < 6 -> "İyi geceler"; hour < 12 -> "Günaydın"; hour < 18 -> "İyi günler"; else -> "İyi akşamlar" }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp)) {
        Title("$greet${if (AppSettings.userName.isNotBlank()) ", ${AppSettings.userName}" else ""}")
        Hint(fmtDay(now))
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { ceo.autoListen = true; ceo.route = "asistan" }, modifier = Modifier.weight(1f)) { Text("🎙 Asistanla konuş") }
            OutlinedButton(onClick = { ceo.route = "card" }, modifier = Modifier.weight(1f)) { Text("💳 Kartvizit tara") }
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatTile(Modifier.weight(1f), todayEv.size, "Bugün etkinlik") { ceo.route = "takvim" }
            StatTile(Modifier.weight(1f), open.size, "Açık görev") { ceo.route = "takvim" }
            StatTile(Modifier.weight(1f), late.size, "Geciken", warn = true) { ceo.route = "takvim" }
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatTile(Modifier.weight(1f), follow.size, "Takip edilecek", warn = true) { ceo.route = "musteri" }
            StatTile(Modifier.weight(1f), pendingQ.size, "Bekleyen teklif") { ceo.route = "musteri" }
            StatTile(Modifier.weight(1f), customers.size, "Müşteri") { ceo.route = "musteri" }
        }

        SubTitle("Günlük yönetici özeti")
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp)) {
                Text(AppSettings.brief.ifBlank { "Henüz özet yok. “Özeti hazırla” ile Gemini bugünkü verilerinden özet çıkarır." }, fontSize = 14.sp)
                if (AppSettings.briefAt > 0) Hint("Hazırlanma: ${fmtDT(AppSettings.briefAt)}")
                Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(enabled = !ceo.busy, onClick = { ceo.brief { } }) { Text(if (ceo.busy) "Hazırlanıyor…" else "✨ Özeti hazırla") }
                    OutlinedButton(enabled = !ceo.busy, onClick = { ceo.brief(true) { dialog = "CEO danışmanı" to it } }) { Text("🧠 Danışman") }
                }
            }
        }

        SubTitle("Bugün")
        if (todayEv.isEmpty() && open.none { it.dueAt in sod until sod + DAY }) Hint("Bugün için kayıt yok.")
        todayEv.forEach { Text("• ${fmtT(it.startAt)}  ${it.title}") }
        open.filter { it.dueAt in sod until sod + DAY }.forEach { Text("• ✅ ${it.title}  (${fmtT(it.dueAt)})") }
        if (late.isNotEmpty()) {
            SubTitle("Geciken görevler")
            late.take(6).forEach { Text("• ${it.title} — ${fmtDT(it.dueAt)}", color = MaterialTheme.colorScheme.error) }
        }
        if (follow.isNotEmpty()) {
            SubTitle("Takip edilmesi gereken müşteriler")
            follow.take(6).forEach { c ->
                Text("• ${c.name} ${if (c.company.isNotBlank()) "(${c.company})" else ""}",
                    modifier = Modifier.clickable { ceo.selCustomer = c; ceo.route = "musteri" }.padding(vertical = 3.dp))
            }
        }
        if (pendingQ.isNotEmpty()) {
            SubTitle("Bekleyen teklifler")
            pendingQ.take(6).forEach { q ->
                Text("• ${q.title} — ${"%,.0f".format(q.amount)} ₺ (${customers.firstOrNull { it.id == q.customerId }?.name ?: "?"})")
            }
        }
    }
    dialog?.let { ResultDialog(it.first, it.second, { dialog = null }) }
}

// ------------------------------------------------------------------ ASİSTAN
@Composable
fun AssistantScreen(ceo: CeoVM) {
    val ctx = LocalContext.current
    var input by remember { mutableStateOf("") }
    val listen = rememberSpeech("Komutunuzu söyleyin") { ceo.ask(it) }
    val state = rememberLazyListState()
    LaunchedEffect(ceo.autoListen) {
        if (ceo.autoListen) { ceo.autoListen = false; listen() }
    }
    LaunchedEffect(ceo.msgs.size) { if (ceo.msgs.isNotEmpty()) state.animateScrollToItem(ceo.msgs.size - 1) }
    val inf = rememberInfiniteTransition(label = "mic")
    val pulse by inf.animateFloat(1f, 1.1f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "p")

    Column(Modifier.fillMaxSize().padding(12.dp)) {
        Header("Asistan", actions = {
            if (ceo.msgs.isNotEmpty()) TextButton(onClick = { ceo.msgs.clear(); Tts.stop() }) { Text("Temizle") }
        })
        if (AppSettings.apiKey.isBlank()) Hint("Gelişmiş anlama için Ayarlar'dan Gemini API anahtarı girin. Anahtarsız yalnızca basit çevrimdışı komutlar çalışır.")
        LazyColumn(Modifier.weight(1f), state = state) {
            if (ceo.msgs.isEmpty()) item {
                Column(Modifier.padding(vertical = 12.dp)) {
                    Text("Örnek komutlar (dokun):", fontWeight = FontWeight.Medium)
                    listOf("Bugünkü işlerimi söyle", "Yarın saat 10'da toplantım var, hatırlat",
                        "Ahmet Bey'i yarın aramam gerekiyor, görev ekle", "Sabah 7:30'a alarm kur",
                        "Bu hafta takip etmem gereken müşteriler kimler?").forEach {
                        AssistChip(onClick = { ceo.ask(it) }, label = { Text(it) }, modifier = Modifier.padding(vertical = 2.dp))
                    }
                }
            }
            items(ceo.msgs) { m ->
                val p = m.pending
                if (p != null) {
                    Card(Modifier.fillMaxWidth().padding(vertical = 4.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                        Column(Modifier.padding(10.dp)) {
                            Text("Onayınız gerekiyor", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            Text(p.label)
                            when (p.state) {
                                0 -> Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(onClick = { ceo.confirm(p, true) }) { Text("Onayla") }
                                    OutlinedButton(onClick = { ceo.confirm(p, false) }) { Text("Vazgeç") }
                                }
                                1 -> Hint("Onaylandı")
                                else -> Hint("Vazgeçildi")
                            }
                        }
                    }
                } else {
                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        horizontalArrangement = if (m.fromUser) Arrangement.End else Arrangement.Start) {
                        Surface(
                            shape = MaterialTheme.shapes.large,
                            color = if (m.fromUser) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.widthIn(max = 320.dp)
                        ) { Text(m.text, Modifier.padding(10.dp)) }
                    }
                }
            }
            if (ceo.assistantBusy) item { Row(Modifier.padding(8.dp)) { CircularProgressIndicator(Modifier.size(20.dp)); Spacer(Modifier.width(8.dp)); Text("Düşünüyor…") } }
        }
        Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = input, onValueChange = { input = it }, modifier = Modifier.weight(1f),
                placeholder = { Text("Yazın veya mikrofona dokunun") }, maxLines = 3
            )
            Spacer(Modifier.width(8.dp))
            Button(onClick = { ceo.ask(input); input = "" }, enabled = input.isNotBlank()) { Text("Gönder") }
        }
        Box(Modifier.fillMaxWidth().padding(top = 8.dp), contentAlignment = Alignment.Center) {
            Surface(
                shape = CircleShape, color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(68.dp).scale(pulse).clickable { listen() }
            ) { Box(contentAlignment = Alignment.Center) { Text("🎙", fontSize = 30.sp) } }
        }
    }
}

// ------------------------------------------------------------------ AYARLAR
@Composable
fun SettingsTab(ceo: CeoVM) {
    val ctx = LocalContext.current
    var key by remember { mutableStateOf(AppSettings.apiKey) }
    var model by remember { mutableStateOf(AppSettings.model) }
    var show by remember { mutableStateOf(false) }
    var theme by remember { mutableStateOf(AppSettings.theme) }
    var speak by remember { mutableStateOf(AppSettings.speak) }
    var name by remember { mutableStateOf(AppSettings.userName) }
    var pin by remember { mutableStateOf("") }
    var testing by remember { mutableStateOf(false) }
    var wipe by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp)) {
        Header("Ayarlar", onBack = { ceo.route = "daha" })

        SubTitle("Yapay zekâ (Gemini)")
        Hint("Bu anahtar Card dahil tüm sekmelerde ortak kullanılır. Telefonda şifreli saklanır, kodun içinde yer almaz. Anahtar: aistudio.google.com/apikey")
        OutlinedTextField(
            value = key, onValueChange = { key = it }, label = { Text("Gemini API anahtarı") }, singleLine = true,
            visualTransformation = if (show) androidx.compose.ui.text.input.VisualTransformation.None else PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            trailingIcon = { Text(if (show) "🙈" else "👁", Modifier.clickable { show = !show }.padding(10.dp)) }
        )
        OutlinedTextField(value = model, onValueChange = { model = it }, label = { Text("Model") }, singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { AppSettings.setAi(key, model); toast(ctx, "Kaydedildi") }) { Text("Kaydet") }
            OutlinedButton(enabled = !testing, onClick = {
                AppSettings.setAi(key, model); testing = true
                scope.launch2 {
                    val r = withContext(Dispatchers.IO) {
                        try { Ai.ask("Sadece 'tamam' yaz."); "✅ Bağlantı başarılı" } catch (e: Exception) { "⚠️ " + e.message }
                    }
                    testing = false; toast(ctx, r)
                }
            }) { Text(if (testing) "Deneniyor…" else "Bağlantıyı test et") }
        }

        SubTitle("Görünüm ve ses")
        OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Size nasıl hitap edeyim?") },
            singleLine = true, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp))
        Chips(listOf("Sistem", "Açık", "Koyu"), theme) { theme = it; AppSettings.setUi(theme, speak, name) }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Switch(checked = speak, onCheckedChange = { speak = it; AppSettings.setUi(theme, speak, name) })
            Spacer(Modifier.width(8.dp)); Text("Asistan yanıtlarını sesli oku")
        }
        Button(onClick = { AppSettings.setUi(theme, speak, name); toast(ctx, "Kaydedildi") }) { Text("Adı kaydet") }

        SubTitle("Güvenlik")
        Hint(if (AppSettings.pinHash.isEmpty()) "PIN kilidi kapalı." else "PIN kilidi açık. Uygulama her açılışta ve arka plandan dönüşte PIN ister.")
        OutlinedTextField(value = pin, onValueChange = { pin = it.filter { c -> c.isDigit() }.take(8) }, label = { Text("Yeni PIN (4-8 rakam)") },
            singleLine = true, visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(enabled = pin.length >= 4, onClick = { AppSettings.setPin(pin); pin = ""; toast(ctx, "PIN ayarlandı") }) { Text("PIN ayarla") }
            if (AppSettings.pinHash.isNotEmpty()) OutlinedButton(onClick = { AppSettings.setPin(""); toast(ctx, "PIN kaldırıldı") }) { Text("PIN'i kaldır") }
        }

        SubTitle("Bildirim ve alarm izinleri")
        Hint("Hatırlatmaların tam zamanında çalması için “alarm ve hatırlatıcı” izni gerekir.")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (Build.VERSION.SDK_INT >= 31) OutlinedButton(onClick = {
                try { ctx.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:" + ctx.packageName))) } catch (e: Exception) { }
            }) { Text("Tam zamanlı alarm izni") }
            OutlinedButton(onClick = {
                try { ctx.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName)) } catch (e: Exception) { }
            }) { Text("Bildirim ayarları") }
        }

        SubTitle("Veri")
        Hint("Card (kartvizit) yedeği Card sekmesi → ⚙ Ayarlar'dadır. Buradaki işlem Müşteri, Not, Görev ve Takvim verilerini siler.")
        OutlinedButton(onClick = { wipe = true }) { Text("🗑 Tüm CEO verilerini sil", color = MaterialTheme.colorScheme.error) }

        SubTitle("Özellik durumu")
        Text("✅ Card, Gemini asistan (sesli/yazılı), Not, Görev, Takvim+alarm, Müşteri/CRM, Panel, PIN\n" +
            "⏳ Sonraki aşama: Toplantı kaydı, E-posta, Rehber/WhatsApp, Dosya okuma, Ara, Biyometrik, Gmail/Google Takvim API", fontSize = 13.sp)
        Spacer(Modifier.height(24.dp))
    }
    if (wipe) ConfirmDialog("Tüm veriler silinsin mi?", "Müşteri, not, görev, etkinlik ve teklifler kalıcı olarak silinir. Kartvizitler etkilenmez.", "Sil",
        { scope.launch2 { withContext(Dispatchers.IO) { CeoDb.get(ctx).clearAllTables() }; toast(ctx, "Silindi") } }, { wipe = false })
}

fun kotlinx.coroutines.CoroutineScope.launch2(block: suspend () -> Unit) {
    this.launch { block() }
}
