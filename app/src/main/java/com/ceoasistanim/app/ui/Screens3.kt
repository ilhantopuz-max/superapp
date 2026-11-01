package com.ceoasistanim.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp

@Composable
fun SettingsScreen(vm: MainViewModel) {
    val ctx = LocalContext.current
    val p = vm.prefs
    var provider by remember { mutableStateOf(p.provider) }
    var baseUrl by remember { mutableStateOf(p.baseUrl) }
    var model by remember { mutableStateOf(p.model) }
    var key by remember { mutableStateOf(p.apiKey) }
    var speak by remember { mutableStateOf(p.speak) }
    var bio by remember { mutableStateOf(p.biometric) }
    var hasPin by remember { mutableStateOf(p.hasPin()) }
    var pinDialog by remember { mutableStateOf(false) }
    var wipe by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        InfoCard("Görünüm") {
            val mode by vm.theme.collectAsState()
            Chips(listOf("Sistem", "Açık", "Koyu"), listOf("Sistem", "Açık", "Koyu")[mode]) {
                vm.setTheme(listOf("Sistem", "Açık", "Koyu").indexOf(it))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Yanıtları sesli oku", Modifier.weight(1f))
                Switch(checked = speak, onCheckedChange = { speak = it; p.speak = it })
            }
        }
        InfoCard("Yapay zekâ bağlantısı") {
            Chips(listOf("gemini", "anthropic"), provider) {
                provider = it
                if (it == "anthropic") { baseUrl = "https://api.anthropic.com/v1"; model = "claude-sonnet-5-5" }
                else { baseUrl = "https://generativelanguage.googleapis.com/v1beta"; model = "gemini-3.5-flash" }
            }
            OutlinedTextField(baseUrl, { baseUrl = it }, label = { Text("Adres (Base URL)") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(model, { model = it }, label = { Text("Model") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(
                key, { key = it }, label = { Text("API anahtarı") }, modifier = Modifier.fillMaxWidth(),
                visualTransformation = PasswordVisualTransformation(), singleLine = true
            )
            Text("Anahtar yalnızca bu cihazda, Android Keystore ile şifreli saklanır. “gemini” için ücretsiz anahtarı aistudio.google.com/apikey adresinden alabilirsiniz (ücretsiz kota günlük/dakikalık sınırlıdır).", style = MaterialTheme.typography.labelSmall)
            Button(onClick = {
                p.provider = provider; p.baseUrl = baseUrl.trim(); p.model = model.trim(); p.apiKey = key.trim()
                vm.say("Yapay zekâ ayarları kaydedildi.")
            }) { Text("Kaydet") }
        }
        InfoCard("Güvenlik") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(if (hasPin) "PIN koruması açık" else "PIN koruması kapalı", Modifier.weight(1f))
                Button(onClick = { pinDialog = true }) { Text(if (hasPin) "PIN değiştir" else "PIN belirle") }
            }
            if (hasPin) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Parmak izi / yüz ile aç", Modifier.weight(1f))
                    Switch(checked = bio, onCheckedChange = { bio = it; p.biometric = it })
                }
                TextButton(onClick = { p.clearPin(); hasPin = false; bio = false }) { Text("PIN'i kaldır") }
            }
        }
        InfoCard("Veriler") {
            OutlinedButton(onClick = { shareText(ctx, "Verileri dışa aktar", vm.exportJson()) }) { Text("Dışa aktar (JSON)") }
            TextButton(onClick = { wipe = true }) { Text("Tüm verileri sil", color = MaterialTheme.colorScheme.error) }
        }
        Text("CEO ASİSTANIM v0.1.0 (MVP)", style = MaterialTheme.typography.labelSmall)
    }

    if (pinDialog) {
        var pin by remember { mutableStateOf("") }
        FormDialog("PIN belirle (4-8 hane)", { pinDialog = false }, {
            p.setPin(pin); hasPin = true; pinDialog = false
        }, okEnabled = pin.length in 4..8) {
            OutlinedTextField(
                pin, { pin = it.filter(Char::isDigit).take(8) }, label = { Text("PIN") }, singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.NumberPassword)
            )
        }
    }
    if (wipe) AlertDialog(
        onDismissRequest = { wipe = false },
        title = { Text("Tüm veriler silinsin mi?") }, text = { Text("Not, görev, müşteri ve toplantılar kalıcı olarak silinir.") },
        confirmButton = { TextButton(onClick = { vm.deleteAll(); wipe = false }) { Text("Sil") } },
        dismissButton = { TextButton(onClick = { wipe = false }) { Text("İptal") } }
    )
}
