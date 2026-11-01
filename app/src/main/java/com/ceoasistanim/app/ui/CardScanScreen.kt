package com.ceoasistanim.app.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.ceoasistanim.app.data.Customer
import com.ceoasistanim.app.util.CardParser
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.io.File

@Composable
fun CardScanScreen(vm: MainViewModel) {
    val ctx = LocalContext.current
    var busy by remember { mutableStateOf(false) }
    var raw by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var title by remember { mutableStateOf("") }
    var company by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var web by remember { mutableStateOf("") }
    var scanned by remember { mutableStateOf(false) }
    var photoUri by remember { mutableStateOf<Uri?>(null) }

    fun process(uri: Uri) {
        busy = true
        try {
            val img = InputImage.fromFilePath(ctx, uri)
            TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS).process(img)
                .addOnSuccessListener { t ->
                    val c = CardParser.parse(t.text)
                    raw = t.text; name = c.name; title = c.title; company = c.company
                    phone = c.phone; email = c.email; web = c.web
                    scanned = true; busy = false
                    if (t.text.isBlank()) vm.say("Kartta yazı bulunamadı. Daha aydınlık ve düz bir fotoğraf deneyin.")
                }
                .addOnFailureListener { busy = false; vm.say("Okuma hatası: ${it.message}") }
        } catch (e: Exception) {
            busy = false
            vm.say("Görüntü açılamadı.")
        }
    }

    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        if (ok) photoUri?.let { process(it) }
    }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) process(uri)
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        InfoCard("Kartvizit tara") {
            Text("Kartı düz bir zemine koyup yeterli ışıkta çekin. Okuma cihazda yapılır, internet gerekmez.")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    val dir = File(ctx.cacheDir, "cards").apply { mkdirs() }
                    val f = File(dir, "card_${System.currentTimeMillis()}.jpg")
                    val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".files", f)
                    photoUri = uri
                    camera.launch(uri)
                }) { Text("Fotoğraf çek") }
                OutlinedButton(onClick = { gallery.launch("image/*") }) { Text("Galeriden seç") }
            }
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        }

        if (scanned) {
            InfoCard("Okunan bilgiler (düzeltebilirsiniz)") {
                OutlinedTextField(name, { name = it }, label = { Text("Ad Soyad") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(title, { title = it }, label = { Text("Ünvan") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(company, { company = it }, label = { Text("Firma") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(phone, { phone = it }, label = { Text("Telefon") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(email, { email = it }, label = { Text("E-posta") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(web, { web = it }, label = { Text("Web") }, modifier = Modifier.fillMaxWidth())
                Button(onClick = {
                    val dup = vm.customers.value.any {
                        (email.isNotBlank() && it.email.equals(email, true)) || (phone.isNotBlank() && it.phone == phone)
                    }
                    if (dup) {
                        vm.say("Bu kişi müşterilerde zaten kayıtlı görünüyor.")
                    } else {
                        val note = buildString {
                            append("Kartvizitten eklendi.")
                            if (title.isNotBlank()) append("\nÜnvan: $title")
                            if (web.isNotBlank()) append("\nWeb: $web")
                        }
                        vm.saveCustomer(Customer(name = name.trim(), phone = phone.trim(), email = email.trim(), company = company.trim(), notes = note))
                        vm.say("${name.ifBlank { "Kişi" }} müşterilere eklendi.")
                        scanned = false; raw = ""; name = ""; title = ""; company = ""; phone = ""; email = ""; web = ""
                    }
                }, enabled = name.isNotBlank()) { Text("Müşterilere kaydet") }
            }
            InfoCard("Ham okunan metin") { Text(raw.ifBlank { "—" }, style = MaterialTheme.typography.bodySmall) }
        }
    }
}
