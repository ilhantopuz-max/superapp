@file:OptIn(ExperimentalMaterial3Api::class)

package com.cardmaster

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.Normalizer
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

val TR: Locale = Locale("tr", "TR")

fun fmtDT(ms: Long): String =
    if (ms <= 0) "—" else SimpleDateFormat("d MMM yyyy HH:mm", TR).format(Date(ms))

fun fmtT(ms: Long): String = SimpleDateFormat("HH:mm", TR).format(Date(ms))

fun fmtDay(ms: Long): String = SimpleDateFormat("d MMMM EEEE", TR).format(Date(ms))

fun startOfDay(ms: Long): Long {
    val c = Calendar.getInstance().apply { timeInMillis = ms }
    c.set(Calendar.HOUR_OF_DAY, 0); c.set(Calendar.MINUTE, 0); c.set(Calendar.SECOND, 0); c.set(Calendar.MILLISECOND, 0)
    return c.timeInMillis
}

/** "yyyy-MM-dd HH:mm" veya "yyyy-MM-dd" → ms. Hatalıysa 0. */
fun parseDT(s: String): Long {
    val t = s.trim()
    if (t.isEmpty()) return 0
    for (f in listOf("yyyy-MM-dd HH:mm", "yyyy-MM-dd'T'HH:mm", "yyyy-MM-dd")) {
        try {
            val d = SimpleDateFormat(f, Locale.US).apply { isLenient = false }.parse(t)
            if (d != null) return d.time
        } catch (e: Exception) { }
    }
    return 0
}

fun toast(ctx: Context, s: String) = Toast.makeText(ctx, s, Toast.LENGTH_LONG).show()

fun pickDateTime(ctx: Context, initial: Long, onPick: (Long) -> Unit) {
    val c = Calendar.getInstance().apply { timeInMillis = if (initial > 0) initial else System.currentTimeMillis() }
    DatePickerDialog(ctx, { _, y, m, d ->
        TimePickerDialog(ctx, { _, h, min ->
            val r = Calendar.getInstance()
            r.set(y, m, d, h, min, 0)
            r.set(Calendar.MILLISECOND, 0)
            onPick(r.timeInMillis)
        }, c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE), true).show()
    }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)).show()
}

@Composable
fun Title(text: String, modifier: Modifier = Modifier) =
    Text(text, fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = modifier)

@Composable
fun SubTitle(text: String) =
    Text(text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 12.dp, bottom = 4.dp))

@Composable
fun Hint(text: String) =
    Text(text, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

@Composable
fun Header(title: String, onBack: (() -> Unit)? = null, actions: @Composable RowScope.() -> Unit = {}) {
    Row(Modifier.fillMaxWidth().padding(bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        if (onBack != null) TextButton(onClick = onBack) { Text("‹ Geri") }
        Title(title, Modifier.weight(1f))
        actions()
    }
}

@Composable
fun Chips(options: List<String>, selected: String, onSelect: (String) -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        options.forEach { o ->
            FilterChip(selected = o == selected, onClick = { onSelect(o) }, label = { Text(o) })
        }
    }
}

@Composable
fun DateTimeField(label: String, value: Long, onChange: (Long) -> Unit) {
    val ctx = LocalContext.current
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        OutlinedButton(onClick = { pickDateTime(ctx, value, onChange) }, modifier = Modifier.weight(1f)) {
            Text("$label: ${fmtDT(value)}")
        }
        if (value > 0) TextButton(onClick = { onChange(0) }) { Text("Temizle") }
    }
}

@Composable
fun rememberSpeech(prompt: String = "Konuşun", onText: (String) -> Unit): () -> Unit {
    val ctx = LocalContext.current
    val cb by rememberUpdatedState(onText)
    val l = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        r.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.let { cb(it) }
    }
    return {
        try {
            l.launch(
                Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                    .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "tr-TR")
                    .putExtra(RecognizerIntent.EXTRA_PROMPT, prompt)
            )
        } catch (e: Exception) {
            toast(ctx, "Sesli giriş bu telefonda kullanılamıyor")
        }
    }
}

/** Yazı alanı + sesle yazdırma (🎤) */
@Composable
fun VoiceField(label: String, value: String, onChange: (String) -> Unit, lines: Int = 1) {
    val start = rememberSpeech { t -> onChange(if (value.isBlank()) t else value.trimEnd() + " " + t) }
    OutlinedTextField(
        value = value, onValueChange = onChange, label = { Text(label) },
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
        singleLine = lines == 1, minLines = lines,
        trailingIcon = { Text("🎤", modifier = Modifier.clickable { start() }.padding(10.dp)) }
    )
}

@Composable
fun ResultDialog(title: String, text: String, onDismiss: () -> Unit, extra: (@Composable () -> Unit)? = null) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { SelectionContainer { Text(text, fontSize = 14.sp, modifier = Modifier.verticalScroll(rememberScrollState())) } },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Tamam") } },
        dismissButton = extra
    )
}

@Composable
fun ConfirmDialog(title: String, text: String, ok: String, onOk: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) }, text = { Text(text) },
        confirmButton = { TextButton(onClick = { onDismiss(); onOk() }) { Text(ok) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Vazgeç") } }
    )
}

fun normTr(s: String): String = norm(s)
