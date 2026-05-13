
$content = @'
package com.olh.usbrelay

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.net.wifi.WifiManager
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.olh.usbrelay.ui.theme.USBRelayTheme
import kotlinx.coroutines.launch
import java.net.InetAddress

class MainActivity : ComponentActivity() {

    private val usbManager: UsbManager by lazy {
        getSystemService(USB_SERVICE) as UsbManager
    }

    private val permissionManager: UsbPermissionManager by lazy {
        UsbPermissionManager(this, usbManager)
    }

    private var usbService: UsbService? = null
    private var serviceBound by mutableStateOf(false)

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as UsbService.UsbBinder
            usbService = binder.getService()
            serviceBound = true
            Log.d("MainActivity", "Service connected")
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            usbService = null
            serviceBound = false
            Log.d("MainActivity", "Service disconnected")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        permissionManager.registerReceiver()

        // Start and bind the service
        val serviceIntent = Intent(this, UsbService::class.java)
        startForegroundService(serviceIntent)
        bindService(serviceIntent, serviceConnection, Context.BIND_AUTO_CREATE)

        setContent {
            USBRelayTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    MainScreen(
                        modifier = Modifier.padding(innerPadding),
                        usbManager = usbManager,
                        permissionManager = permissionManager,
                        usbService = usbService,
                        serviceBound = serviceBound
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        handleUsbIntent(intent)
    }

    private fun handleUsbIntent(intent: Intent?) {
        if (intent?.action == UsbManager.ACTION_USB_DEVICE_ATTACHED) {
            Log.d("MainActivity", "USB device attached via intent")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        permissionManager.unregisterReceiver()
        if (serviceBound) {
            unbindService(serviceConnection)
            serviceBound = false
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    modifier: Modifier = Modifier,
    usbManager: UsbManager,
    permissionManager: UsbPermissionManager,
    usbService: UsbService?,
    serviceBound: Boolean
) {
    var devices by remember { mutableStateOf(emptyMap<String, UsbDevice>()) }
    var serverRunning by remember { mutableStateOf(false) }
    var logs by remember { mutableStateOf(listOf<String>()) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var refreshTrigger by remember { mutableIntStateOf(0) }

    // Get IP address
    val ipAddress by remember {
        mutableStateOf(getLocalIpAddress(context))
    }

    LaunchedEffect(refreshTrigger, serviceBound) {
        devices = permissionManager.getDeviceList()
        serverRunning = usbService?.serverRunning ?: false
    }

    // Set up log callback
    DisposableEffect(usbService) {
        val logCallback = object : LogCallback {
            override fun onLog(level: Int, message: String) {
                val timestamp = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())
                logs = logs.takeLast(50) + "[$timestamp] $message"
            }
        }
        usbService?.setLogCallback(logCallback)
        onDispose {
            usbService?.setLogCallback(null)
        }
    }

    DisposableEffect(Unit) {
        permissionManager.setOnDeviceAttachedListener {
            refreshTrigger++
        }
        permissionManager.setOnDeviceDetachedListener { device ->
            scope.launch {
                val wasBound = usbService?.handleDeviceDetached(device.deviceName) ?: false
                if (wasBound) {
                    serverRunning = usbService?.serverRunning ?: false
                    val deviceName = device.productName ?: "Unknown device"
                    android.widget.Toast.makeText(context, "$deviceName detached", android.widget.Toast.LENGTH_SHORT).show()
                }
            }
            refreshTrigger++
        }
        onDispose { }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        TopAppBar(
            title = { Text("USB Relay") }
        )

        // Server control card
        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text("Server Control", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = if (serverRunning) "Status: Running" else "Status: Stopped",
                            color = if (serverRunning) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                            fontSize = 14.sp
                        )
                        ipAddress?.let {
                            Text(
                                text = "IP: $it",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            text = "Port: ${usbService?.port ?: 3240}",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (!serviceBound) {
                            Text(
                                text = "Service not bound",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }

                    Button(
                        onClick = {
                            if (serverRunning) {
                                usbService?.stopServer()
                                serverRunning = false
                            } else {
                                if (usbService?.startServer(3240) == true) {
                                    serverRunning = true
                                }
                            }
                            refreshTrigger++
                        },
                        enabled = serviceBound
                    ) {
                        Text(if (serverRunning) "Stop" else "Start")
                    }
                }
            }
        }

        // USB devices card
        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("USB Devices (${devices.size})", style = MaterialTheme.typography.titleMedium)
                    Button(
                        onClick = { refreshTrigger++ },
                        modifier = Modifier.height(32.dp)
                    ) {
                        Text("Refresh", fontSize = 12.sp)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                if (devices.isEmpty()) {
                    Text(
                        "No USB devices connected",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.heightIn(max = 200.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(devices.values.toList()) { device ->
                            DeviceItem(
                                device = device,
                                permissionManager = permissionManager,
                                isBound = usbService?.boundDeviceNames?.contains(device.deviceName) == true,
                                isServerRunning = serverRunning,
                                onBind = {
                                    scope.launch {
                                        val result = usbService?.bindDevice(usbManager, device)
                                        if (result is DeviceBindResult.Success) {
                                            android.widget.Toast.makeText(context, "Device bound", android.widget.Toast.LENGTH_SHORT).show()
                                        } else {
                                            android.widget.Toast.makeText(context, "Failed to bind device", android.widget.Toast.LENGTH_SHORT).show()
                                        }
                                        refreshTrigger++
                                    }
                                },
                                onUnbind = {
                                    scope.launch {
                                        usbService?.unbindDevice(device.deviceName)
                                        refreshTrigger++
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }

        // Logs card
        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text("Logs", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(8.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 100.dp, max = 150.dp)
                        .padding(8.dp)
                ) {
                    if (logs.isEmpty()) {
                        Text(
                            text = "No logs yet",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        LazyColumn {
                            items(logs.takeLast(20)) { log ->
                                Text(
                                    text = log,
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DeviceItem(
    device: UsbDevice,
    permissionManager: UsbPermissionManager,
    isBound: Boolean,
    isServerRunning: Boolean,
    onBind: () -> Unit,
    onUnbind: () -> Unit
) {
    val hasPermission = permissionManager.hasPermission(device)
    val context = LocalContext.current

    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = device.productName ?: "Unknown Device",
                    style = MaterialTheme.typography.titleSmall
                )
                Text(
                    text = "VID: 0x${device.vendorId.toString(16).uppercase()} | PID: 0x${device.productId.toString(16).uppercase()}",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = if (hasPermission) "Permission: ✓" else "Permission: ✗",
                        fontSize = 12.sp,
                        color = if (hasPermission) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                    )
                    if (isBound) {
                        Text(
                            text = "Bound: ✓",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (!hasPermission) {
                    Button(
                        onClick = {
                            permissionManager.requestPermission(device) { _, _ ->
                            }
                        },
                        modifier = Modifier.height(36.dp)
                    ) {
                        Text("Grant", fontSize = 12.sp)
                    }
                } else if (isBound) {
                    Button(
                        onClick = onUnbind,
                        modifier = Modifier.height(36.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Text("Unbind", fontSize = 12.sp)
                    }
                } else if (isServerRunning) {
                    Button(
                        onClick = onBind,
                        modifier = Modifier.height(36.dp)
                    ) {
                        Text("Bind", fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

fun getLocalIpAddress(context: Context): String? {
    return try {
        val wifiManager = context.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val wifiInfo = wifiManager.connectionInfo
        val ipAddress = wifiInfo.ipAddress
        InetAddress.getByAddress(
            byteArrayOf(
                (ipAddress and 0xFF).toByte(),
                (ipAddress shr 8 and 0xFF).toByte(),
                (ipAddress shr 16 and 0xFF).toByte(),
                (ipAddress shr 24 and 0xFF).toByte()
            )
        ).hostAddress
    } catch (e: Exception) {
        Log.e("MainActivity", "Failed to get IP address", e)
        null
    }
}
'@

Set-Content -Path "E:\WORK\usb-relay-olh\relay-host\USBRelay\app\src\main\java\com\olh\usbrelay\MainActivity.kt" -Value $content -Encoding UTF8
Write-Host "MainActivity.kt updated successfully!"
