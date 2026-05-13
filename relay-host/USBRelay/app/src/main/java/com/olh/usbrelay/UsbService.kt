
package com.olh.usbrelay

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.ServerSocket
import java.net.Socket
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicBoolean

class UsbService : Service() {

    companion object {
        private const val TAG = "UsbService"
        private const val CHANNEL_ID = "usb_relay_service_channel"
        private const val NOTIFICATION_ID = 1001
    }

    private val binder = UsbBinder()
    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)
    
    private var serverSocket: ServerSocket? = null
    private val isRunning = AtomicBoolean(false)
    private val clients = mutableListOf<ClientHandler>()
    
    private var logCallback: LogCallback? = null

    val boundDeviceNames: Set<String> = emptySet()
    val serverRunning: Boolean get() = isRunning.get()
    var port: Int = 3240
        private set
    
    private lateinit var usbManager: UsbManager
    private lateinit var permissionManager: UsbPermissionManager

    inner class UsbBinder : Binder() {
        fun getService(): UsbService = this@UsbService
    }

    override fun onCreate() {
        super.onCreate()
        usbManager = getSystemService(USB_SERVICE) as UsbManager
        permissionManager = UsbPermissionManager(this, usbManager)
        permissionManager.registerReceiver()
        createNotificationChannel()
        startForegroundService()
        log("Service created")
    }

    override fun onBind(intent: Intent?): IBinder {
        return binder
    }

    override fun onDestroy() {
        super.onDestroy()
        stopServer()
        permissionManager.unregisterReceiver()
        serviceJob.cancel()
        log("Service destroyed")
    }

    fun setLogCallback(callback: LogCallback?) {
        logCallback = callback
    }

    private fun log(message: String, level: Int = Log.INFO) {
        Log.d(TAG, message)
        logCallback?.onLog(level, message)
        broadcastLog(level, message)
    }

    private fun broadcastLog(level: Int, message: String) {
        clients.forEach { client ->
            try {
                client.sendLog(level, message)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to send log", e)
            }
        }
    }

    fun broadcastDeviceUpdate() {
        clients.forEach { client ->
            try {
                client.sendDeviceList()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to send device update", e)
            }
        }
    }

    fun startServer(port: Int = 3240): Boolean {
        if (isRunning.get()) {
            log("Server already running", Log.WARN)
            return false
        }
        
        this.port = port
        
        return try {
            serverSocket = ServerSocket(port)
            isRunning.set(true)
            updateNotification()
            log("Server started on port $port")

            serviceScope.launch {
                acceptClients()
            }

            true
        } catch (e: IOException) {
            log("Failed to start server: ${e.message}", Log.ERROR)
            e.printStackTrace()
            false
        }
    }

    fun stopServer() {
        if (!isRunning.get()) return

        log("Stopping server...")
        isRunning.set(false)
        updateNotification()

        clients.forEach { it.close() }
        clients.clear()

        try {
            serverSocket?.close()
        } catch (e: IOException) {
            e.printStackTrace()
        }
        serverSocket = null
        log("Server stopped")
    }

    private suspend fun acceptClients() {
        while (isRunning.get()) {
            try {
                val socket = serverSocket?.accept() ?: continue
                log("New client connected: ${socket.inetAddress}")

                val clientHandler = ClientHandler(socket)
                clients.add(clientHandler)

                serviceScope.launch {
                    clientHandler.run()
                    clients.remove(clientHandler)
                }
            } catch (e: IOException) {
                if (isRunning.get()) {
                    log("Accept error: ${e.message}", Log.ERROR)
                }
            }
        }
    }

    fun bindDevice(usbManager: UsbManager, device: UsbDevice): DeviceBindResult {
        log("Bind device: ${device.deviceName}")
        broadcastDeviceUpdate()
        return DeviceBindResult.Success("device-1")
    }

    fun unbindDevice(deviceName: String): DeviceUnbindResult {
        log("Unbind device: $deviceName")
        broadcastDeviceUpdate()
        return DeviceUnbindResult.Success
    }

    fun handleDeviceDetached(deviceName: String): Boolean {
        broadcastDeviceUpdate()
        return false
    }

    private fun writeU16BE(output: OutputStream, value: Int) {
        output.write((value shr 8) and 0xFF)
        output.write(value and 0xFF)
    }

    private fun writeU32BE(output: OutputStream, value: Long) {
        output.write(((value shr 24) and 0xFF).toInt())
        output.write(((value shr 16) and 0xFF).toInt())
        output.write(((value shr 8) and 0xFF).toInt())
        output.write((value and 0xFF).toInt())
    }

    private inner class ClientHandler(private val socket: Socket) {
        private var running = false
        private var clientJob: Job? = null
        private var output: OutputStream? = null

        suspend fun run() {
            running = true
            log("Client handler started")

            try {
                val input = socket.getInputStream()
                output = socket.getOutputStream()

                // 连接上就直接发送简单的JSON数据，不用复杂的协议
                sendSimpleMessage("Hello from server!")
                sendSimpleDeviceList()

                // 简单循环，只读取数据，不处理
                clientJob = serviceScope.launch {
                    val buffer = ByteArray(4096)
                    while (running) {
                        try {
                            val bytesRead = input.read(buffer)
                            if (bytesRead < 0) break
                            log("Received $bytesRead bytes from client")
                        } catch (e: Exception) {
                            if (running) {
                                log("Client read error: ${e.message}", Log.ERROR)
                            }
                            break
                        }
                    }
                }

                clientJob?.join()
            } catch (e: Exception) {
                if (running) {
                    log("Client error: ${e.message}", Log.ERROR)
                    e.printStackTrace()
                }
            } finally {
                close()
            }

            log("Client disconnected")
        }

        private fun sendSimpleMessage(text: String) {
            try {
                val output = output ?: return
                val msgBytes = text.toByteArray(Charsets.UTF_8)
                output.write(msgBytes)
                output.flush()
                log("Sent message: $text")
            } catch (e: Exception) {
                log("Failed to send simple message: ${e.message}", Log.ERROR)
            }
        }

        private fun sendSimpleDeviceList() {
            try {
                val output = output ?: return
                val json = """
                    [
                        {
                            "id": 1,
                            "vid": "0x1234",
                            "pid": "0x5678",
                            "class": "0x00",
                            "name": "Test USB Device"
                        }
                    ]
                """.trimIndent()
                
                val jsonBytes = json.toByteArray(Charsets.UTF_8)
                output.write(jsonBytes)
                output.flush()
                log("Sent simple device list")
            } catch (e: Exception) {
                log("Failed to send device list: ${e.message}", Log.ERROR)
            }
        }

        fun sendLog(level: Int, message: String) {
            val output = output ?: return
            try {
                val msgBytes = message.toByteArray(Charsets.UTF_8)
                output.write(msgBytes)
                output.flush()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to send log", e)
            }
        }

        fun sendDeviceList() {
            sendSimpleDeviceList()
        }

        fun close() {
            running = false
            clientJob?.cancel()
            try {
                socket.close()
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "USB Relay Service"
            val descriptionText = "USB Relay Service Notification"
            val importance = NotificationManager.IMPORTANCE_LOW
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            val notificationManager: NotificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun startForegroundService() {
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent: PendingIntent =
            PendingIntent.getActivity(this, 0, notificationIntent,
                PendingIntent.FLAG_IMMUTABLE)

        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("USB Relay")
            .setContentText("Service is running")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(pendingIntent)
            .build()

        startForeground(NOTIFICATION_ID, notification)
    }

    private fun updateNotification() {
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent: PendingIntent =
            PendingIntent.getActivity(this, 0, notificationIntent,
                PendingIntent.FLAG_IMMUTABLE)

        val statusText = if (isRunning.get()) "Server is running on port $port" else "Server is stopped"
        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("USB Relay")
            .setContentText(statusText)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(pendingIntent)
            .build()

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, notification)
    }
}
