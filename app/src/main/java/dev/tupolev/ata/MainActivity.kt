package dev.tupolev.ata

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.tupolev.ata.api.Device
import dev.tupolev.ata.auth.KeySetupScreen
import dev.tupolev.ata.auth.LoginScreen
import dev.tupolev.ata.monitor.MonitorStatus
import dev.tupolev.ata.monitor.MonitoringService
import dev.tupolev.ata.monitor.TrackerConfig
import java.text.DateFormat
import java.util.Date

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MaterialTheme(colorScheme = lightColorScheme()) {
                val vm: MainViewModel = viewModel()
                val state by vm.state.collectAsState()

                AtaRoot(
                    state = state,
                    vm = vm,
                    readText = { uri ->
                        contentResolver.openInputStream(uri)
                            ?.bufferedReader()
                            ?.use { it.readText() }
                            ?: ""
                    },
                    startMonitoring = {
                        ContextCompat.startForegroundService(
                            this,
                            Intent(this, MonitoringService::class.java)
                        )
                    },
                    stopMonitoring = {
                        stopService(Intent(this, MonitoringService::class.java))
                    },
                )
            }
        }
    }
}

private enum class SetupScreen { WELCOME, LOGIN, IMPORT }

@Composable
private fun AtaRoot(
    state: AtaUiState,
    vm: MainViewModel,
    readText: (android.net.Uri) -> String,
    startMonitoring: () -> Unit,
    stopMonitoring: () -> Unit,
) {
    var setupScreen by remember { mutableStateOf(SetupScreen.WELCOME) }

    if (!state.authenticated) {
        when (setupScreen) {
            SetupScreen.WELCOME -> WelcomeScreen(
                onLogin = { setupScreen = SetupScreen.LOGIN },
                onImport = { setupScreen = SetupScreen.IMPORT },
            )
            SetupScreen.LOGIN -> LoginScreen(
                onTokenReceived = { email, token ->
                    vm.onTokenReceived(email, token)
                }
            )
            SetupScreen.IMPORT -> ImportScreen(
                onImported = { uri ->
                    vm.importSecrets(readText(uri))
                    setupScreen = SetupScreen.WELCOME
                },
                onBack = { setupScreen = SetupScreen.WELCOME },
            )
        }

        state.error?.let {
            ErrorSnackbar(it, onDismiss = vm::clearError)
        }
        return
    }

    if (state.needsKeySetup) {
        KeySetupScreen(onSharedKeyReceived = vm::onSharedKeyReceived)
        return
    }

    MainScreen(
        state = state,
        vm = vm,
        startMonitoring = startMonitoring,
        stopMonitoring = stopMonitoring,
    )
}

@Composable
private fun WelcomeScreen(
    onLogin: () -> Unit,
    onImport: () -> Unit,
) {
    Box(
        modifier = Modifier.fillMaxSize().padding(28.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Icon(Icons.Default.Shield, contentDescription = null, modifier = Modifier.size(72.dp))
            Text("ATA", style = MaterialTheme.typography.displaySmall)
            Text("Android Tracker Alarm", style = MaterialTheme.typography.titleMedium)
            Text(
                "Monitor Google Find Hub trackers and alert when they move outside a safe zone.",
                style = MaterialTheme.typography.bodyLarge,
            )
            Button(onClick = onLogin, modifier = Modifier.fillMaxWidth()) {
                Text("Sign in with Google")
            }
            OutlinedButton(onClick = onImport, modifier = Modifier.fillMaxWidth()) {
                Text("Import GoogleFindMyTools secrets.json")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ImportScreen(
    onImported: (android.net.Uri) -> Unit,
    onBack: () -> Unit,
) {
    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) onImported(uri)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Import credentials") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        }
    ) { padding ->
        Column(
            Modifier.padding(padding).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Text(
                "Choose Auth/secrets.json generated by GoogleFindMyTools. " +
                    "ATA copies the required values into encrypted Android storage and does not retain the original file."
            )
            Button(
                onClick = {
                    picker.launch(arrayOf("application/json", "text/plain", "*/*"))
                }
            ) {
                Text("Choose secrets.json")
            }
            Text(
                "Treat secrets.json like a password. Never commit or share it.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainScreen(
    state: AtaUiState,
    vm: MainViewModel,
    startMonitoring: () -> Unit,
    stopMonitoring: () -> Unit,
) {
    var tab by remember { mutableIntStateOf(0) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("ATA") },
                actions = {
                    IconButton(onClick = { vm.refreshDevices() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                },
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = tab == 0,
                    onClick = { tab = 0 },
                    icon = { Icon(Icons.Default.LocationOn, contentDescription = null) },
                    label = { Text("Trackers") },
                )
                NavigationBarItem(
                    selected = tab == 1,
                    onClick = { tab = 1 },
                    icon = { Icon(Icons.Default.Security, contentDescription = null) },
                    label = { Text("Monitoring") },
                )
                NavigationBarItem(
                    selected = tab == 2,
                    onClick = { tab = 2 },
                    icon = { Icon(Icons.Default.Settings, contentDescription = null) },
                    label = { Text("Settings") },
                )
            }
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when (tab) {
                0 -> TrackerList(state, vm)
                1 -> MonitoringScreen(
                    state,
                    vm,
                    startMonitoring,
                    stopMonitoring,
                )
                2 -> SettingsScreen(vm)
            }

            if (state.loading) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
            }

            state.error?.let {
                ErrorSnackbar(
                    message = it,
                    onDismiss = vm::clearError,
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
            }
        }
    }
}

@Composable
private fun TrackerList(
    state: AtaUiState,
    vm: MainViewModel,
) {
    if (state.devices.isEmpty() && !state.loading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No trackers found")
        }
        return
    }

    LazyColumn(
        Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(state.devices, key = { it.id }) { device ->
            val config = state.monitors[device.id]
            TrackerCard(device, config, vm)
        }
    }
}

@Composable
private fun TrackerCard(
    device: Device,
    config: TrackerConfig?,
    vm: MainViewModel,
) {
    ElevatedCard(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
    ) {
        Column(
            Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.BluetoothSearching, contentDescription = null)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        device.name.ifBlank { "Unnamed tracker" },
                        style = MaterialTheme.typography.titleLarge,
                    )
                    Text(
                        device.id.take(8) + "…",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                StatusBadge(config)
            }

            if (config != null && config.lastTimestamp > 0) {
                Text("Last report: " + formatTime(config.lastTimestamp))
                config.lastDistanceM?.let {
                    Text("Distance from armed position: " + it.toInt() + " m")
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { vm.refreshLocation(device) }) {
                    Text("Refresh")
                }

                if (config?.armed == true) {
                    OutlinedButton(onClick = { vm.disarm(device) }) {
                        Text("Disarm")
                    }
                } else {
                    OutlinedButton(
                        enabled = config?.lastLat != null && config.lastLon != null,
                        onClick = { vm.arm(device) },
                    ) {
                        Text("Arm here")
                    }
                }
            }

            if (config?.lastLat == null) {
                Text(
                    "Refresh once before arming so ATA has a reference position.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun StatusBadge(config: TrackerConfig?) {
    val text = when (config?.status) {
        MonitorStatus.SAFE -> "SAFE"
        MonitorStatus.SUSPICIOUS -> "CHECK"
        MonitorStatus.ALARM -> "ALARM"
        MonitorStatus.STALE -> "STALE"
        else -> if (config?.armed == true) "ARMED" else "OFF"
    }
    AssistChip(onClick = {}, label = { Text(text) })
}

@Composable
private fun MonitoringScreen(
    state: AtaUiState,
    vm: MainViewModel,
    startMonitoring: () -> Unit,
    stopMonitoring: () -> Unit,
) {
    val armed = state.devices.filter { state.monitors[it.id]?.armed == true }

    val notificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted || Build.VERSION.SDK_INT < 33) startMonitoring()
    }

    fun start() {
        if (Build.VERSION.SDK_INT >= 33) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            startMonitoring()
        }
    }

    LazyColumn(
        Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            ElevatedCard(Modifier.fillMaxWidth()) {
                Column(
                    Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        armed.size.toString() + " tracker(s) armed",
                        style = MaterialTheme.typography.titleLarge,
                    )
                    Text(
                        "ATA keeps a foreground service active while monitoring so a 5-minute interval is possible."
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            enabled = armed.isNotEmpty(),
                            onClick = { start() },
                        ) {
                            Text("Start monitoring")
                        }
                        OutlinedButton(onClick = stopMonitoring) {
                            Text("Stop service")
                        }
                    }
                }
            }
        }

        items(armed, key = { it.id }) { device ->
            state.monitors[device.id]?.let { config ->
                MonitorConfigCard(device, config, vm)
            }
        }
    }
}

@Composable
private fun MonitorConfigCard(
    device: Device,
    config: TrackerConfig,
    vm: MainViewModel,
) {
    var radius by remember(config.safeRadiusM) {
        mutableStateOf(config.safeRadiusM.toInt().toString())
    }
    var immediate by remember(config.immediateAlarmM) {
        mutableStateOf(config.immediateAlarmM.toInt().toString())
    }
    var confirmations by remember(config.confirmations) {
        mutableStateOf(config.confirmations.toString())
    }
    var interval by remember(config.intervalMinutes) {
        mutableStateOf(config.intervalMinutes.toString())
    }
    var stale by remember(config.staleMinutes) {
        mutableStateOf(config.staleMinutes.toString())
    }

    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(
            Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    device.name.ifBlank { device.id.take(8) },
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f),
                )
                StatusBadge(config)
            }

            config.lastDistanceM?.let {
                Text("Current distance: " + it.toInt() + " m")
            }

            OutlinedTextField(
                value = radius,
                onValueChange = { radius = it },
                label = { Text("Safe radius (m)") },
                singleLine = true,
            )
            OutlinedTextField(
                value = immediate,
                onValueChange = { immediate = it },
                label = { Text("Immediate alarm (m)") },
                singleLine = true,
            )
            OutlinedTextField(
                value = confirmations,
                onValueChange = { confirmations = it },
                label = { Text("Outside reports before alarm") },
                singleLine = true,
            )
            OutlinedTextField(
                value = interval,
                onValueChange = { interval = it },
                label = { Text("Polling interval (min)") },
                singleLine = true,
            )
            OutlinedTextField(
                value = stale,
                onValueChange = { stale = it },
                label = { Text("Stale after (min)") },
                singleLine = true,
            )

            Button(
                onClick = {
                    vm.updateConfig(
                        device = device,
                        radius = radius.toDoubleOrNull() ?: 120.0,
                        immediate = immediate.toDoubleOrNull() ?: 300.0,
                        confirmations = confirmations.toIntOrNull() ?: 2,
                        intervalMinutes = interval.toIntOrNull() ?: 5,
                        staleMinutes = stale.toIntOrNull() ?: 30,
                    )
                }
            ) {
                Text("Save")
            }
        }
    }
}

@Composable
private fun SettingsScreen(vm: MainViewModel) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Security", style = MaterialTheme.typography.titleLarge)
        Text(
            "Credentials are stored with Android EncryptedSharedPreferences backed by the Android Keystore."
        )
        Text(
            "ATA is an unofficial Find Hub client. Google can change the internal protocol at any time.",
            style = MaterialTheme.typography.bodySmall,
        )
        OutlinedButton(onClick = vm::signOut) {
            Text("Delete credentials / sign out")
        }

        HorizontalDivider()

        Text("ATA 0.1.0")
        Text(
            "Defaults: 120 m safe radius, two confirmations, 300 m immediate alarm, 5-minute polling."
        )
    }
}

@Composable
private fun ErrorSnackbar(
    message: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Snackbar(
        modifier = modifier.padding(16.dp),
        action = {
            TextButton(onClick = onDismiss) {
                Text("Dismiss")
            }
        },
    ) {
        Text(message)
    }
}

private fun formatTime(timestamp: Long): String =
    DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
        .format(Date(timestamp))
