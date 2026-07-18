package sahin.tethershare

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import sahin.tethershare.ui.theme.TetherShareTheme

class MainActivity : ComponentActivity() {

    // Keep active states to bind to UI
    private var activeConnections = mutableIntStateOf(0)
    private var dataUsedMB = mutableDoubleStateOf(0.0)
    private var currentSpeedKbps = mutableDoubleStateOf(0.0)
    private val speedHistory = mutableStateListOf<Float>() // Keeps speed data points for the graph
    private var themeMode = mutableIntStateOf(0)

    private val statsReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            intent?.let {
                activeConnections.intValue = it.getIntExtra(ProxyService.EXTRA_CONNECTIONS, 0)
                dataUsedMB.doubleValue = it.getDoubleExtra(ProxyService.EXTRA_TOTAL_MB, 0.0)

                val speed = it.getDoubleExtra(ProxyService.EXTRA_SPEED_KBPS, 0.0).toFloat()
                currentSpeedKbps.doubleValue = speed.toDouble()

                // Keep history size constrained to the width of the graph (e.g., 30 data points)
                if (speedHistory.size > 30) {
                    speedHistory.removeAt(0)
                }
                speedHistory.add(speed)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val themePrefs = ThemePreferences(this)
        themeMode.intValue = themePrefs.selectedTheme

        setContent {
            TetherShareTheme(themeMode = themeMode.intValue) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    ProxyAppUI(
                        activeConnections = activeConnections.intValue,
                        dataUsedMB = dataUsedMB.doubleValue,
                        currentSpeed = currentSpeedKbps.doubleValue,
                        speedHistory = speedHistory,
                        onStartProxy = { port ->
                            val intent = Intent(this, ProxyService::class.java).apply {
                                action = "START"
                                putExtra("PORT", port)
                            }
                            startForegroundService(intent)
                        },
                        onStopProxy = {
                            val intent = Intent(this, ProxyService::class.java).apply {
                                action = "STOP"
                            }
                            startService(intent)

                            // Reset stats
                            activeConnections.intValue = 0
                            dataUsedMB.doubleValue = 0.0
                            currentSpeedKbps.doubleValue = 0.0
                            speedHistory.clear()
                        },
                        onOpenSettings = {
                            startActivity(Intent(this, SettingsActivity::class.java))
                        }
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        themeMode.intValue = ThemePreferences(this).selectedTheme
        val filter = IntentFilter(ProxyService.STATS_ACTION)
        ContextCompat.registerReceiver(
            this,
            statsReceiver,
            filter,
            ContextCompat.RECEIVER_EXPORTED
        )
    }

    override fun onPause() {
        super.onPause()
        unregisterReceiver(statsReceiver)
    }
}

@Composable
fun ProxyAppUI(
    activeConnections: Int,
    dataUsedMB: Double,
    currentSpeed: Double,
    speedHistory: List<Float>,
    onStartProxy: (Int) -> Unit,
    onStopProxy: () -> Unit,
    onOpenSettings: () -> Unit
) {
    var isProxyRunning by remember { mutableStateOf(false) }
    var portString by remember { mutableStateOf("8080") }
    val context = LocalContext.current
    val unknownText = stringResource(R.string.unknown_interface)
    var networkAddresses by remember { mutableStateOf(emptyMap<String, String>()) }
    val clipboardManager = LocalClipboardManager.current
    val showBatteryDialog = remember { mutableStateOf(false) }

    LaunchedEffect(unknownText) {
        while (true) {
            networkAddresses = NetworkUtils.getLocalIpAddresses(unknownText)
            delay(5000)
        }
    }

    LaunchedEffect(Unit) {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        if (!powerManager.isIgnoringBatteryOptimizations(context.packageName)) {
            showBatteryDialog.value = true
        }
    }

    if (showBatteryDialog.value) {
        AlertDialog(
            onDismissRequest = { showBatteryDialog.value = false },
            title = { Text(stringResource(R.string.battery_optimization_title)) },
            text = { Text(stringResource(R.string.battery_optimization_message)) },
            confirmButton = {
                Button(onClick = {
                    showBatteryDialog.value = false
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = "package:${context.packageName}".toUri()
                    }
                    context.startActivity(intent)
                }) {
                    Text(stringResource(R.string.go_to_settings))
                }
            },
            dismissButton = {
                TextButton(onClick = { showBatteryDialog.value = false }) {
                    Text(stringResource(R.string.dismiss))
                }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Spacer(modifier = Modifier.width(48.dp)) // To center the title
            Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineMedium)
            IconButton(onClick = onOpenSettings) {
                Icon(Icons.Default.Settings, contentDescription = "Settings")
            }
        }
        Spacer(modifier = Modifier.height(16.dp))

        // Local Interfaces List
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(stringResource(R.string.local_ip_addresses), style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(8.dp))
                networkAddresses.forEach { (interfaceName, ip) ->
                    val copiedText = stringResource(R.string.copied_toast, ip)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = {
                                clipboardManager.setText(AnnotatedString(ip))
                                Toast.makeText(context, copiedText, Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = stringResource(R.string.copy_ip),
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("$interfaceName: $ip", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Connection Stats Card (MB/GB used & connected clients)
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(stringResource(R.string.total_data_used), style = MaterialTheme.typography.labelLarge)
                    // Auto-scale to GB if usage goes higher than 1024 MB
                    val dataString = if (dataUsedMB >= 1024.0) {
                        stringResource(R.string.data_gb, dataUsedMB / 1024.0)
                    } else {
                        stringResource(R.string.data_mb, dataUsedMB)
                    }
                    Text(dataString, style = MaterialTheme.typography.headlineSmall)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(stringResource(R.string.connected_devices), style = MaterialTheme.typography.labelLarge)
                    Text("$activeConnections", style = MaterialTheme.typography.headlineSmall)
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Bandwidth Graph Area
        val speedString = if (currentSpeed >= 1024.0) {
            stringResource(R.string.speed_mbps, currentSpeed / 1024.0)
        } else {
            stringResource(R.string.speed_kbps, currentSpeed)
        }
        Text(
            text = stringResource(R.string.live_network_usage, speedString),
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.align(Alignment.Start)
        )
        Spacer(modifier = Modifier.height(8.dp))

        NetworkUsageGraph(
            speedHistory = speedHistory,
            modifier = Modifier
                .fillMaxWidth()
                .height(150.dp)
                .background(Color.DarkGray, shape = RoundedCornerShape(8.dp))
                .padding(8.dp)
        )

        Spacer(modifier = Modifier.height(24.dp))

        OutlinedTextField(
            value = portString,
            onValueChange = { if (it.all { char -> char.isDigit() }) portString = it },
            label = { Text(stringResource(R.string.proxy_port)) },
            modifier = Modifier.fillMaxWidth(0.6f),
            enabled = !isProxyRunning
        )

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = {
                if (isProxyRunning) {
                    onStopProxy()
                } else {
                    val port = portString.toIntOrNull() ?: 8080
                    onStartProxy(port)
                }
                isProxyRunning = !isProxyRunning
            },
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isProxyRunning) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
            ),
            modifier = Modifier.height(50.dp)
        ) {
            Text(if (isProxyRunning) stringResource(R.string.stop_proxy) else stringResource(R.string.start_proxy))
        }
    }
}

@Composable
fun NetworkUsageGraph(speedHistory: List<Float>, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        if (speedHistory.size < 2) return@Canvas

        val maxSpeed = (speedHistory.maxOrNull() ?: 10f).coerceAtLeast(10f) // minimum scale ceiling
        val width = size.width
        val height = size.height
        val pointCount = speedHistory.size

        val path = Path()
        val stepX = width / (pointCount - 1)

        for (i in speedHistory.indices) {
            val x = i * stepX
            // Inverse Y because (0,0) starts at the top-left of the canvas screen
            val y = height - (speedHistory[i] / maxSpeed) * height

            if (i == 0) {
                path.moveTo(x, y)
            } else {
                path.lineTo(x, y)
            }
        }

        // Draw the throughput path line
        drawPath(
            path = path,
            color = Color.Green,
            style = Stroke(width = 4f)
        )
    }
}