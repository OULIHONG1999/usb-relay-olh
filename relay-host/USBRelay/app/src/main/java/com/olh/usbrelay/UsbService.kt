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
import kotlinx.coroutines.launch
import java.io.IOException
import java.io.OutputStream
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.atomic.AtomicBoolean

class UsbService : Service() {

    companion object {
        private const val TAG = "UsbService"
        private const val CHANNEL_ID = "usb_relay_service_channel"
        private const val NOTIFICATION_ID = 1001
        
        // Protocol command codes
        private const val CMD_DEVLIST_REQ = 0x0001
        private const val CMD_DEVLIST_RES = 0x0002
        private const val CMD_IMPORT_REQ = 0x0003
        private const val CMD_IMPORT_RES = 0x0004
        private const val CMD_URB_SUBMIT = 0x0005
        private const val CMD_URB_COMPLETE = 0x0006
        private const val CMD_URB_UNLINK = 0x0007
        private const val CMD_URB_UNLINK_RET = 0x0008
        private const val CMD_DISCONNECT = 0x0009
        private const val CMD_KEEPALIVE = 0x000A
        private const val CMD_PONG = 0x000B
        private const val CMD_LOG = 0x1001
        private const val CMD_DEVICE_UPDATE = 0x1002
        private const val CMD_IMPORT_DEVICE = 0x1003
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
    
    // Track available USB devices
    private var availableDevices = mutableMapOf<String, UsbDevice>()
    
    // Track imported devices (device_id -> UsbDevice)
    private val importedDevices = mutableMapOf<Int, UsbDevice>()
    
    // Device ID mapping (id -> deviceName)
    private val deviceIdMap = mutableMapOf<Int, String>()

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
                client.sendDeviceListUpdate()
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
        
        // Add to available devices
        availableDevices[device.deviceName] = device
        
        // Broadcast device update to all clients
        broadcastDeviceUpdate()
        
        return DeviceBindResult.Success("device-${availableDevices.size}")
    }

    fun unbindDevice(deviceName: String): DeviceUnbindResult {
        log("Unbind device: $deviceName")
        
        // Remove from available devices
        availableDevices.remove(deviceName)
        
        // Broadcast device update to all clients
        broadcastDeviceUpdate()
        
        return DeviceUnbindResult.Success
    }

    fun handleDeviceDetached(deviceName: String): Boolean {
        // Remove from available devices when detached
        availableDevices.remove(deviceName)
        
        broadcastDeviceUpdate()
        return false
    }

    private inner class ClientHandler(private val socket: Socket) {
        private var running = false
        private var clientJob: Job? = null
        private var output: OutputStream? = null
        private var seqNum: Int = 0
        
        // URB handlers for imported devices
        private val urbHandlers = mutableMapOf<Int, UrbHandler>()

        suspend fun run() {
            running = true
            log("Client handler started")

            try {
                val input = socket.getInputStream()
                output = socket.getOutputStream()

                // Send initial device list using protocol
                sendDeviceListUpdate()

                // Read and process commands from client
                clientJob = serviceScope.launch {
                    val headerBuffer = ByteArray(16)
                    while (running) {
                        try {
                            // Read header (16 bytes)
                            var bytesRead = 0
                            while (bytesRead < 16 && running) {
                                val n = input.read(headerBuffer, bytesRead, 16 - bytesRead)
                                if (n < 0) {
                                    running = false
                                    break
                                }
                                bytesRead += n
                            }
                            
                            if (!running || bytesRead < 16) break
                            
                            // Parse header (big-endian)
                            val command = ((headerBuffer[0].toInt() and 0xFF) shl 8) or 
                                         (headerBuffer[1].toInt() and 0xFF)
                            val seq = ((headerBuffer[2].toInt() and 0xFF) shl 24) or
                                     ((headerBuffer[3].toInt() and 0xFF) shl 16) or
                                     ((headerBuffer[4].toInt() and 0xFF) shl 8) or
                                     (headerBuffer[5].toInt() and 0xFF)
                            val devId = ((headerBuffer[6].toInt() and 0xFF) shl 8) or 
                                       (headerBuffer[7].toInt() and 0xFF)
                            val length = ((headerBuffer[8].toInt() and 0xFF) shl 24) or
                                        ((headerBuffer[9].toInt() and 0xFF) shl 16) or
                                        ((headerBuffer[10].toInt() and 0xFF) shl 8) or
                                        (headerBuffer[11].toInt() and 0xFF)
                            
                            log("Received cmd=0x${command.toString(16).uppercase()}, seq=$seq, len=$length")
                            
                            // Handle commands
                            when (command) {
                                CMD_DEVLIST_REQ -> {
                                    log("Client requested device list")
                                    sendDeviceListUpdate()
                                }
                                CMD_KEEPALIVE -> {
                                    log("Ping received")
                                    sendPong()
                                }
                                CMD_IMPORT_DEVICE -> {
                                    log("Import device request: dev_id=$devId")
                                    handleImportDevice(devId, input, length.toLong())
                                }
                                CMD_URB_SUBMIT -> {
                                    log("URB submit received: dev_id=$devId, len=$length")
                                    handleUrbSubmit(devId, input, length)
                                }
                                CMD_URB_UNLINK -> {
                                    log("URB unlink received: dev_id=$devId")
                                    handleUrbUnlink(devId, input, length)
                                }
                                else -> {
                                    log("Unknown command: 0x${command.toString(16).uppercase()}")
                                }
                            }
                            
                            // Skip payload if any
                            if (length > 0) {
                                val skipBuf = ByteArray(length.toInt())
                                var skipped = 0
                                while (skipped < length && running) {
                                    val n = input.read(skipBuf, skipped, (length - skipped).toInt())
                                    if (n < 0) break
                                    skipped += n
                                }
                            }
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
        
        private fun sendProtocolMessage(command: Int, devId: Int = 0xFFFF, payload: ByteArray = byteArrayOf()) {
            try {
                val out = output ?: return
                val currentSeq = seqNum++
                
                // Write header
                writeU16BE(out, command)
                writeU32BE(out, currentSeq.toLong())
                writeU16BE(out, devId)
                writeU32BE(out, payload.size.toLong())
                writeU32BE(out, 0) // reserved
                
                // Write payload
                if (payload.isNotEmpty()) {
                    out.write(payload)
                }
                out.flush()
            } catch (e: Exception) {
                log("Failed to send protocol message: ${e.message}", Log.ERROR)
            }
        }
        
        private fun sendPong() {
            sendProtocolMessage(CMD_PONG)
        }
        
        fun sendLog(level: Int, message: String) {
            try {
                val timestamp = System.currentTimeMillis()
                val msgBytes = message.toByteArray(Charsets.UTF_8)
                
                // Build payload: timestamp(8) + level(1) + msg_len(4) + message
                val payload = ByteArray(8 + 1 + 4 + msgBytes.size)
                var offset = 0
                
                // Timestamp (8 bytes, big-endian)
                for (i in 7 downTo 0) {
                    payload[offset++] = ((timestamp shr (i * 8)) and 0xFF).toByte()
                }
                
                // Level (1 byte)
                payload[offset++] = level.toByte()
                
                // Message length (4 bytes, big-endian)
                val msgLen = msgBytes.size
                payload[offset++] = ((msgLen shr 24) and 0xFF).toByte()
                payload[offset++] = ((msgLen shr 16) and 0xFF).toByte()
                payload[offset++] = ((msgLen shr 8) and 0xFF).toByte()
                payload[offset++] = (msgLen and 0xFF).toByte()
                
                // Message
                System.arraycopy(msgBytes, 0, payload, offset, msgBytes.size)
                
                sendProtocolMessage(CMD_LOG, 0xFFFF, payload)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to send log", e)
            }
        }
        
        fun sendDeviceListUpdate() {
            try {
                // Clear and rebuild device ID map
                deviceIdMap.clear()
                
                // Create JSON device list
                val devicesJson = StringBuilder("[")
                var first = true
                var id = 1
                for ((deviceName, device) in availableDevices) {
                    if (!first) devicesJson.append(",")
                    first = false
                    
                    // Build ID mapping
                    deviceIdMap[id] = deviceName
                    
                    devicesJson.append("{")
                    devicesJson.append("\"id\":$id,")
                    devicesJson.append("\"vid\":\"0x${String.format("%04x", device.vendorId)}\",")
                    devicesJson.append("\"pid\":\"0x${String.format("%04x", device.productId)}\",")
                    devicesJson.append("\"class\":\"0x${String.format("%02x", device.deviceClass)}\",")
                    devicesJson.append("\"name\":\"${device.productName ?: "Unknown Device"}\"")
                    devicesJson.append("}")
                    id++
                }
                devicesJson.append("]")
                
                val jsonBytes = devicesJson.toString().toByteArray(Charsets.UTF_8)
                sendProtocolMessage(CMD_DEVICE_UPDATE, 0xFFFF, jsonBytes)
                log("Sent device list update with ${availableDevices.size} devices")
            } catch (e: Exception) {
                log("Failed to send device list: ${e.message}", Log.ERROR)
            }
        }
        
        private fun handleImportDevice(devId: Int, input: java.io.InputStream, payloadLength: Long) {
            try {
                // Read payload if any
                if (payloadLength > 0) {
                    val skipBuf = ByteArray(payloadLength.toInt())
                    var skipped = 0
                    while (skipped < payloadLength) {
                        val n = input.read(skipBuf, skipped, (payloadLength - skipped).toInt())
                        if (n < 0) break
                        skipped += n
                    }
                }
                
                // Find device by ID using the mapping
                val deviceName = deviceIdMap[devId]
                
                if (deviceName == null) {
                    log("Import failed: Device ID $devId not found")
                    sendImportResponse(1, devId, 1, "Device not found")
                    return
                }
                
                val device = availableDevices[deviceName]
                if (device == null) {
                    log("Import failed: Device data not found for $deviceName")
                    sendImportResponse(1, devId, 2, "Device data error")
                    return
                }
                
                // Check if already imported
                if (importedDevices.containsValue(device)) {
                    log("Device already imported: $deviceName")
                    sendImportResponse(0, devId, 0, "Already imported")
                    return
                }
                
                // Import device (placeholder - real implementation needs usbip-win2 driver)
                importedDevices[devId] = device
                
                // Create URB handler for this device
                val urbHandler = UrbHandler(usbManager, device)
                if (urbHandler.open()) {
                    urbHandlers[devId] = urbHandler
                    log("URB handler created for device: $deviceName")
                } else {
                    log("Failed to create URB handler for: $deviceName", Log.WARN)
                }
                
                log("Device imported successfully: $deviceName (ID: $devId)")
                sendImportResponse(0, devId, 0, "Success - Note: Full USB virtualization requires driver integration")
                
            } catch (e: Exception) {
                log("Import device error: ${e.message}", Log.ERROR)
                sendImportResponse(1, devId, 2, e.message ?: "Unknown error")
            }
        }
        
        private fun sendImportResponse(status: Int, devId: Int, resultCode: Int, message: String) {
            try {
                val msgBytes = message.toByteArray(Charsets.UTF_8)
                
                // Build payload: status(2) + dev_id(2) + result_code(4) + message
                val payload = ByteArray(2 + 2 + 4 + msgBytes.size)
                var offset = 0
                
                // Status (2 bytes, big-endian)
                payload[offset++] = ((status shr 8) and 0xFF).toByte()
                payload[offset++] = (status and 0xFF).toByte()
                
                // Device ID (2 bytes, big-endian)
                payload[offset++] = ((devId shr 8) and 0xFF).toByte()
                payload[offset++] = (devId and 0xFF).toByte()
                
                // Result code (4 bytes, big-endian)
                payload[offset++] = ((resultCode shr 24) and 0xFF).toByte()
                payload[offset++] = ((resultCode shr 16) and 0xFF).toByte()
                payload[offset++] = ((resultCode shr 8) and 0xFF).toByte()
                payload[offset++] = (resultCode and 0xFF).toByte()
                
                // Message
                System.arraycopy(msgBytes, 0, payload, offset, msgBytes.size)
                
                sendProtocolMessage(CMD_IMPORT_RES, devId.toShort().toInt() and 0xFFFF, payload)
            } catch (e: Exception) {
                log("Failed to send import response: ${e.message}", Log.ERROR)
            }
        }
        
        private fun handleUrbSubmit(devId: Int, input: java.io.InputStream, payloadLength: Int) {
            try {
                // Read URB submit payload
                val urbPayload = ByteArray(payloadLength)
                var bytesRead = 0
                while (bytesRead < payloadLength) {
                    val n = input.read(urbPayload, bytesRead, payloadLength - bytesRead)
                    if (n < 0) break
                    bytesRead += n
                }
                
                // Parse URB submit
                val urbSubmit = UrbSubmit.parse(urbPayload)
                
                // Find URB handler
                val urbHandler = urbHandlers[devId]
                if (urbHandler == null) {
                    log("URB handler not found for dev_id=$devId", Log.WARN)
                    val urbComplete = UrbComplete(urbSubmit.seqNum, URB_STATUS_NO_DEVICE, byteArrayOf())
                    sendProtocolMessage(CMD_URB_COMPLETE, devId.toShort().toInt() and 0xFFFF, urbComplete.toByteArray())
                    return
                }
                
                // Execute URB
                val urbComplete = urbHandler.handleUrbSubmit(urbSubmit)
                
                // Send response
                sendProtocolMessage(CMD_URB_COMPLETE, devId.toShort().toInt() and 0xFFFF, urbComplete.toByteArray())
                
            } catch (e: Exception) {
                log("URB submit error: ${e.message}", Log.ERROR)
                e.printStackTrace()
            }
        }
        
        private fun handleUrbUnlink(devId: Int, input: java.io.InputStream, payloadLength: Int) {
            try {
                // Read unlink payload
                val unlinkPayload = ByteArray(payloadLength)
                var bytesRead = 0
                while (bytesRead < payloadLength) {
                    val n = input.read(unlinkPayload, bytesRead, payloadLength - bytesRead)
                    if (n < 0) break
                    bytesRead += n
                }
                
                // Parse unlink request
                val urbUnlink = UrbUnlink.parse(unlinkPayload)
                
                // For now, just acknowledge the unlink
                val unlinkRet = UrbUnlinkRet(urbUnlink.seqNum, 0)
                sendProtocolMessage(CMD_URB_UNLINK_RET, devId.toShort().toInt() and 0xFFFF, unlinkRet.toByteArray())
                
                log("URB unlinked: seq=${urbUnlink.seqNum}")
                
            } catch (e: Exception) {
                log("URB unlink error: ${e.message}", Log.ERROR)
            }
        }

        fun close() {
            running = false
            clientJob?.cancel()
            
            // Close all URB handlers
            urbHandlers.values.forEach { it.close() }
            urbHandlers.clear()
            
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
