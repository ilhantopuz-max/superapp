package com.ceoasistanim.app.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat

private val Navy = Color(0xFF0F1B33)
private val Gold = Color(0xFFD4A93C)

@Composable
fun AppTheme(dark: Boolean, content: @Composable () -> Unit) {
    val scheme = if (dark) darkColorScheme(
        primary = Gold, onPrimary = Navy, background = Color(0xFF0B1220), surface = Color(0xFF131E33),
        surfaceVariant = Color(0xFF1B2A47), secondaryContainer = Color(0xFF28406B)
    ) else lightColorScheme(
        primary = Navy, onPrimary = Color.White, secondary = Gold, background = Color(0xFFF5F6FA),
        surface = Color.White, surfaceVariant = Color(0xFFE9ECF4), secondaryContainer = Color(0xFFF3E4B5)
    )
    MaterialTheme(colorScheme = scheme, content = content)
}

@Composable
fun SectionTitle(t: String) =
    Text(t, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

@Composable
fun InfoCard(title: String, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Card(modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            SectionTitle(title)
            content()
        }
    }
}

@Composable
fun FormDialog(
    title: String, onDismiss: () -> Unit, onOk: () -> Unit,
    okEnabled: Boolean = true, content: @Composable ColumnScope.() -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                content = content
            )
        },
        confirmButton = { TextButton(onClick = onOk, enabled = okEnabled) { Text("Kaydet") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("İptal") } }
    )
}

@Composable
fun Chips(options: List<String>, selected: String, onSelect: (String) -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { o ->
            FilterChip(selected = o == selected, onClick = { onSelect(o) }, label = { Text(o) })
        }
    }
}

@Composable
fun DictField(
    value: String, onChange: (String) -> Unit, label: String,
    mic: ((String) -> Unit) -> Unit, minLines: Int = 1
) {
    OutlinedTextField(
        value = value, onValueChange = onChange, label = { Text(label) }, minLines = minLines,
        modifier = Modifier.fillMaxWidth(),
        trailingIcon = {
            IconButton(onClick = { mic { onChange((value + " " + it).trim()) } }) {
                Icon(Icons.Filled.Mic, contentDescription = "Sesle yaz")
            }
        }
    )
}

/** Mikrofon izni kontrolünü yapıp sesle dikteyi başlatan yardımcı. */
@Composable
fun rememberMic(vm: MainViewModel): ((String) -> Unit) -> Unit {
    val ctx = LocalContext.current
    var pending by remember { mutableStateOf<((String) -> Unit)?>(null) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        val cb = pending
        pending = null
        if (ok && cb != null) vm.dictate(cb) else vm.say("Sesli komut için mikrofon izni gerekli.")
    }
    return { cb ->
        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            vm.dictate(cb)
        } else {
            pending = cb
            launcher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }
}
