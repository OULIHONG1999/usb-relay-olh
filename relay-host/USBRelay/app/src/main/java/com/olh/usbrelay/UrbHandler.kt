package com.olh.usbrelay

import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbInterface
import android.hardware.usb.UsbManager
import android.util.Log
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap

/**
 * URB Handler - Manages USB device connection and URB processing
 */
class UrbHandler(
    private val usbManager: UsbManager,
    private val device: UsbDevice
) {
    companion object {
        private const val TAG = "UrbHandler"
        private const val USB_TIMEOUT = 5000  // 5 seconds timeout
    }
    
    private var connection: UsbDeviceConnection? = null
    private var usbInterface: UsbInterface? = null
    private val endpoints = mutableMapOf<Int, UsbEndpoint>()
    private val pendingUrbs = ConcurrentHashMap<Long, UrbContext>()
    
    data class UrbContext(
        val urbSubmit: UrbSubmit,
        val timestamp: Long = System.currentTimeMillis()
    )
    
    /**
     * Open USB device connection
     */
    fun open(): Boolean {
        return try {
            connection = usbManager.openDevice(device)
            if (connection == null) {
                Log.e(TAG, "Failed to open device: ${device.deviceName}")
                return false
            }
            
            // Claim first interface
            if (device.interfaceCount > 0) {
                usbInterface = device.getInterface(0)
                if (!connection!!.claimInterface(usbInterface, true)) {
                    Log.e(TAG, "Failed to claim interface")
                    close()
                    return false
                }
                
                // Cache endpoints
                for (i in 0 until usbInterface!!.endpointCount) {
                    val endpoint = usbInterface!!.getEndpoint(i)
                    endpoints[endpoint.address] = endpoint
                }
                
                Log.d(TAG, "Device opened successfully: ${device.deviceName}")
                Log.d(TAG, "Endpoints: ${endpoints.keys.joinToString()}")
            }
            
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error opening device", e)
            close()
            false
        }
    }
    
    /**
     * Close USB device connection
     */
    fun close() {
        try {
            if (usbInterface != null && connection != null) {
                connection!!.releaseInterface(usbInterface)
            }
            connection?.close()
            connection = null
            usbInterface = null
            endpoints.clear()
            pendingUrbs.clear()
            Log.d(TAG, "Device closed")
        } catch (e: Exception) {
            Log.e(TAG, "Error closing device", e)
        }
    }
    
    /**
     * Handle URB submit request
     */
    fun handleUrbSubmit(urbSubmit: UrbSubmit, dataPayload: ByteArray = byteArrayOf()): UrbComplete {
        return try {
            Log.d(TAG, "Processing URB: type=${urbSubmit.transferType}, ep=${urbSubmit.endpoint}, dir=${urbSubmit.direction}, dataLen=${dataPayload.size}")
            
            val result = when (urbSubmit.transferType) {
                URB_TRANSFER_CTRL -> executeControlTransfer(urbSubmit)
                URB_TRANSFER_BULK -> executeBulkTransfer(urbSubmit, dataPayload)
                URB_TRANSFER_INT -> executeInterruptTransfer(urbSubmit, dataPayload)
                URB_TRANSFER_ISO -> executeIsochronousTransfer(urbSubmit)
                else -> {
                    Log.e(TAG, "Unknown transfer type: ${urbSubmit.transferType}")
                    UrbComplete(urbSubmit.seqNum, URB_STATUS_ERROR, byteArrayOf())
                }
            }
            
            Log.d(TAG, "URB completed: status=${result.status}, dataLen=${result.data.size}")
            result
            
        } catch (e: Exception) {
            Log.e(TAG, "URB execution error", e)
            UrbComplete(urbSubmit.seqNum, URB_STATUS_ERROR, byteArrayOf())
        }
    }
    
    /**
     * Execute control transfer
     */
    private fun executeControlTransfer(urbSubmit: UrbSubmit): UrbComplete {
        val conn = connection ?: return UrbComplete(urbSubmit.seqNum, URB_STATUS_NO_DEVICE, byteArrayOf())
        
        try {
            val setupPacket = urbSubmit.setupPacket
            val requestType = setupPacket[0].toInt() and 0xFF
            val request = setupPacket[1].toInt() and 0xFF
            val value = ((setupPacket[3].toInt() and 0xFF) shl 8) or (setupPacket[2].toInt() and 0xFF)
            val index = ((setupPacket[5].toInt() and 0xFF) shl 8) or (setupPacket[4].toInt() and 0xFF)
            val length = urbSubmit.dataLen.coerceAtMost(4096)  // Limit size
            
            val buffer = ByteArray(length)
            
            val result = conn.controlTransfer(
                requestType, request, value, index,
                buffer, length, USB_TIMEOUT
            )
            
            if (result >= 0) {
                return UrbComplete(urbSubmit.seqNum, URB_STATUS_OK, buffer.copyOf(result))
            } else {
                Log.e(TAG, "Control transfer failed: $result")
                return UrbComplete(urbSubmit.seqNum, URB_STATUS_ERROR, byteArrayOf())
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Control transfer error", e)
            return UrbComplete(urbSubmit.seqNum, URB_STATUS_ERROR, byteArrayOf())
        }
    }
    
    /**
     * Execute bulk transfer
     */
    private fun executeBulkTransfer(urbSubmit: UrbSubmit, dataPayload: ByteArray): UrbComplete {
        val conn = connection ?: return UrbComplete(urbSubmit.seqNum, URB_STATUS_NO_DEVICE, byteArrayOf())
        
        try {
            val endpoint = endpoints[urbSubmit.endpoint]
            if (endpoint == null) {
                Log.e(TAG, "Endpoint not found: ${urbSubmit.endpoint}")
                return UrbComplete(urbSubmit.seqNum, URB_STATUS_ERROR, byteArrayOf())
            }
            
            if (urbSubmit.direction == URB_DIR_IN) {
                // IN transfer - read from device
                val buffer = ByteArray(urbSubmit.dataLen.coerceAtMost(65536))
                val result = conn.bulkTransfer(endpoint, buffer, buffer.size, USB_TIMEOUT)
                
                if (result >= 0) {
                    return UrbComplete(urbSubmit.seqNum, URB_STATUS_OK, buffer.copyOf(result))
                } else {
                    Log.e(TAG, "Bulk IN transfer failed: $result")
                    return UrbComplete(urbSubmit.seqNum, URB_STATUS_ERROR, byteArrayOf())
                }
            } else {
                // OUT transfer - write to device
                val data = if (dataPayload.isNotEmpty()) dataPayload else ByteArray(urbSubmit.dataLen)
                val result = conn.bulkTransfer(endpoint, data, data.size, USB_TIMEOUT)
                
                if (result >= 0) {
                    return UrbComplete(urbSubmit.seqNum, URB_STATUS_OK, byteArrayOf())
                } else {
                    Log.e(TAG, "Bulk OUT transfer failed: $result")
                    return UrbComplete(urbSubmit.seqNum, URB_STATUS_ERROR, byteArrayOf())
                }
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Bulk transfer error", e)
            return UrbComplete(urbSubmit.seqNum, URB_STATUS_ERROR, byteArrayOf())
        }
    }
    
    /**
     * Execute interrupt transfer
     */
    private fun executeInterruptTransfer(urbSubmit: UrbSubmit, dataPayload: ByteArray): UrbComplete {
        val conn = connection ?: return UrbComplete(urbSubmit.seqNum, URB_STATUS_NO_DEVICE, byteArrayOf())
        
        try {
            val endpoint = endpoints[urbSubmit.endpoint]
            if (endpoint == null) {
                Log.e(TAG, "Endpoint not found: ${urbSubmit.endpoint}")
                return UrbComplete(urbSubmit.seqNum, URB_STATUS_ERROR, byteArrayOf())
            }
            
            if (urbSubmit.direction == URB_DIR_IN) {
                val buffer = ByteArray(urbSubmit.dataLen.coerceAtMost(1024))
                val result = conn.bulkTransfer(endpoint, buffer, buffer.size, USB_TIMEOUT)
                
                if (result >= 0) {
                    return UrbComplete(urbSubmit.seqNum, URB_STATUS_OK, buffer.copyOf(result))
                } else {
                    return UrbComplete(urbSubmit.seqNum, URB_STATUS_ERROR, byteArrayOf())
                }
            } else {
                val data = if (dataPayload.isNotEmpty()) dataPayload else ByteArray(urbSubmit.dataLen)
                val result = conn.bulkTransfer(endpoint, data, data.size, USB_TIMEOUT)
                
                if (result >= 0) {
                    return UrbComplete(urbSubmit.seqNum, URB_STATUS_OK, byteArrayOf())
                } else {
                    return UrbComplete(urbSubmit.seqNum, URB_STATUS_ERROR, byteArrayOf())
                }
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Interrupt transfer error", e)
            return UrbComplete(urbSubmit.seqNum, URB_STATUS_ERROR, byteArrayOf())
        }
    }
    
    /**
     * Execute isochronous transfer (not fully supported on Android)
     */
    private fun executeIsochronousTransfer(urbSubmit: UrbSubmit): UrbComplete {
        // TODO: Android USB Host API不支持ISO传输，可能需要通过JNI调用libusb
        Log.w(TAG, "Isochronous transfer not supported on Android - requires libusb JNI")
        return UrbComplete(urbSubmit.seqNum, URB_STATUS_ERROR, byteArrayOf())
    }
    
    /**
     * Check if device is still connected
     */
    fun isConnected(): Boolean {
        return connection != null
    }
}
