package com.olh.usbrelay

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * URB (USB Request Block) Protocol Data Classes
 */

// Transfer types
const val URB_TRANSFER_ISO = 0
const val URB_TRANSFER_INT = 1
const val URB_TRANSFER_CTRL = 2
const val URB_TRANSFER_BULK = 3

// Directions
const val URB_DIR_OUT = 0
const val URB_DIR_IN = 1

// URB Status codes
const val URB_STATUS_OK = 0
const val URB_STATUS_NO_DEVICE = -1
const val URB_STATUS_STALL = -2
const val URB_STATUS_ERROR = -3
const val URB_STATUS_TIMEOUT = -4

/**
 * URB Submit request from client
 */
data class UrbSubmit(
    val seqNum: Long,
    val devId: Int,
    val transferType: Int,
    val endpoint: Int,
    val direction: Int,
    val dataLen: Int,
    val interval: Int,
    val setupPacket: ByteArray
) {
    companion object {
        fun parse(payload: ByteArray): UrbSubmit {
            val buffer = ByteBuffer.wrap(payload).order(ByteOrder.BIG_ENDIAN)
            
            val seqNum = buffer.int.toLong() and 0xFFFFFFFF
            val devId = buffer.short.toInt() and 0xFFFF
            val transferType = buffer.get().toInt() and 0xFF
            val endpoint = buffer.get().toInt() and 0xFF
            val direction = buffer.get().toInt() and 0xFF
            val reserved1 = buffer.get()
            val dataLen = buffer.int
            val interval = buffer.int
            val setupPacket = ByteArray(8)
            buffer.get(setupPacket)
            
            return UrbSubmit(
                seqNum = seqNum,
                devId = devId,
                transferType = transferType,
                endpoint = endpoint,
                direction = direction,
                dataLen = dataLen,
                interval = interval,
                setupPacket = setupPacket
            )
        }
    }
    
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        
        other as UrbSubmit
        
        if (seqNum != other.seqNum) return false
        if (devId != other.devId) return false
        if (transferType != other.transferType) return false
        if (endpoint != other.endpoint) return false
        if (direction != other.direction) return false
        if (dataLen != other.dataLen) return false
        if (interval != other.interval) return false
        if (!setupPacket.contentEquals(other.setupPacket)) return false
        
        return true
    }
    
    override fun hashCode(): Int {
        var result = seqNum.hashCode()
        result = 31 * result + devId
        result = 31 * result + transferType
        result = 31 * result + endpoint
        result = 31 * result + direction
        result = 31 * result + dataLen
        result = 31 * result + interval
        result = 31 * result + setupPacket.contentHashCode()
        return result
    }
}

/**
 * URB Complete response to client
 */
data class UrbComplete(
    val seqNum: Long,
    val status: Int,
    val data: ByteArray
) {
    fun toByteArray(): ByteArray {
        val headerSize = 4 + 4 + 4  // seq_num + status + data_len
        val payload = ByteArray(headerSize + data.size)
        val buffer = ByteBuffer.wrap(payload).order(ByteOrder.BIG_ENDIAN)
        
        buffer.putInt((seqNum and 0xFFFFFFFF).toInt())
        buffer.putInt(status)
        buffer.putInt(data.size)
        buffer.put(data)
        
        return payload
    }
    
    companion object {
        const val HEADER_SIZE = 12
    }
    
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        
        other as UrbComplete
        
        if (seqNum != other.seqNum) return false
        if (status != other.status) return false
        if (!data.contentEquals(other.data)) return false
        
        return true
    }
    
    override fun hashCode(): Int {
        var result = seqNum.hashCode()
        result = 31 * result + status
        result = 31 * result + data.contentHashCode()
        return result
    }
}

/**
 * URB Unlink request
 */
data class UrbUnlink(
    val seqNum: Long,
    val devId: Int
) {
    companion object {
        fun parse(payload: ByteArray): UrbUnlink {
            val buffer = ByteBuffer.wrap(payload).order(ByteOrder.BIG_ENDIAN)
            
            val seqNum = buffer.int.toLong() and 0xFFFFFFFF
            val devId = buffer.short.toInt() and 0xFFFF
            val reserved = buffer.short
            
            return UrbUnlink(seqNum = seqNum, devId = devId)
        }
    }
}

/**
 * URB Unlink response
 */
data class UrbUnlinkRet(
    val seqNum: Long,
    val status: Int
) {
    fun toByteArray(): ByteArray {
        val payload = ByteArray(8)
        val buffer = ByteBuffer.wrap(payload).order(ByteOrder.BIG_ENDIAN)
        
        buffer.putInt((seqNum and 0xFFFFFFFF).toInt())
        buffer.putInt(status)
        
        return payload
    }
}
