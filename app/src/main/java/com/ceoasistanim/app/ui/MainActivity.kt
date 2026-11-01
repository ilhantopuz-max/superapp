package com.ceoasistanim.app.ui

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val vm: MainViewModel = viewModel()
            val mode by vm.theme.collectAsStateWithLifecycle()
            val dark = when (mode) { 1 -> false; 2 -> true; else -> androidx.compose.foundation.isSystemInDarkTheme() }
            AppTheme(dark) { Root(this, vm) }
        }
    }
}

private enum class Screen(val label: String) {
    PANEL("Panel"), TASKS("Görevler"), NOTES("Notlar"), CUSTOMERS("Müşteriler"), MEETINGS("Toplantı"), CARDS("Kartvizit"), SETTINGS("Ayarlar")
}

@Composable
private fun Root(activity: FragmentActivity, vm: MainViewModel) {
    var unlocked by rememberSaveable { mutableStateOf(!vm.prefs.hasPin()) }
    if (!unlocked) { LockScreen(activity, vm) { unlocked = true }; return }

    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(activity, Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    var screen by rememberSaveable { mutableStateOf(Screen.PANEL) }
    val reply by vm.reply.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val mic = rememberMic(vm)

    Scaffold(
        topBar = {
            Row(
                Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically
            ) {
                Text("CEO ASİSTANIM", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                IconButton(onClick = { screen = Screen.SETTINGS }) { Icon(Icons.Filled.Settings, "Ayarlar") }
            }
        },
        bottomBar = {
            NavigationBar {
                listOf(
                    Triple(Screen.PANEL, Icons.Filled.Dashboard, "Panel"),
                    Triple(Screen.TASKS, Icons.Filled.CheckCircle, "Görev"),
                    Triple(Screen.NOTES, Icons.Filled.Edit, "Notlar"),
                    Triple(Screen.CUSTOMERS, Icons.Filled.People, "Müşteri"),
                    Triple(Screen.MEETINGS, Icons.Filled.Event, "Toplantı"),
                    Triple(Screen.CARDS, Icons.Filled.CameraAlt, "Kartvizit")
                ).forEach { (s, icon, label) ->
                    NavigationBarItem(
                        selected = screen == s, onClick = { screen = s },
                        icon = { Icon(icon, label) }, label = { Text(label) }
                    )
                }
            }
        }
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (reply.isNotBlank()) {
                Card(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Column(Modifier.heightIn(max = 220.dp).verticalScroll(rememberScrollState())) { Text(reply) }
                        Row {
                            TextButton(onClick = { vm.speakText(reply) }) { Text("Oku") }
                            TextButton(onClick = { vm.clearReply() }) { Text("Kapat") }
                        }
                    }
                }
            }
            Box(Modifier.weight(1f)) {
                when (screen) {
                    Screen.PANEL -> DashboardScreen(vm, mic)
                    Screen.TASKS -> TasksScreen(vm)
                    Screen.NOTES -> NotesScreen(vm, mic)
                    Screen.CUSTOMERS -> CustomersScreen(vm, mic)
                    Screen.MEETINGS -> MeetingsScreen(vm, mic)
                    Screen.CARDS -> CardScanScreen(vm)
                    Screen.SETTINGS -> SettingsScreen(vm)
                }
            }
        }
    }
}

@Composable
private fun LockScreen(activity: FragmentActivity, vm: MainViewModel, onUnlock: () -> Unit) {
    var pin by remember { mutableStateOf("") }
    var err by remember { mutableStateOf(false) }
    val canBio = vm.prefs.biometric &&
        BiometricManager.from(activity).canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) == BiometricManager.BIOMETRIC_SUCCESS

    fun bio() {
        val prompt = BiometricPrompt(activity, ContextCompat.getMainExecutor(activity),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) { onUnlock() }
            })
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle("CEO ASİSTANIM").setSubtitle("Kilidi açmak için doğrulayın")
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
            .setNegativeButtonText("PIN kullan").build()
        prompt.authenticate(info)
    }
    LaunchedEffect(Unit) { if (canBio) bio() }

    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(Icons.Filled.Lock, null, Modifier.size(56.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(12.dp))
        Text("CEO ASİSTANIM", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(24.dp))
        OutlinedTextField(
            value = pin, onValueChange = { pin = it.filter(Char::isDigit).take(8); err = false },
            label = { Text("PIN") }, isError = err, singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            supportingText = { if (err) Text("Hatalı PIN") }
        )
        Spacer(Modifier.height(12.dp))
        Button(onClick = { if (vm.prefs.checkPin(pin)) onUnlock() else err = true }) { Text("Kilidi aç") }
        if (canBio) TextButton(onClick = { bio() }) { Text("Parmak izi / yüz ile aç") }
    }
}
