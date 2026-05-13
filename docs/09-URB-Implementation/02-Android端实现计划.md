# Android端URB处理实现计划

## 目标
在Android端实现完整的USB设备访问和URB转发功能

## 架构设计

### 组件结构
```
UsbService
  └─ ClientHandler (per client)
      ├─ importedDevices: Map<Int, UsbDevice>
      └─ UrbHandler (NEW - per imported device)
          ├─ UsbDeviceConnection
          ├─ UsbInterface
          ├─ UsbEndpoint[]
          └─ URB Queue
```

### 数据流
```
Windows Client                    Android Server
     |                                  |
     |-- CMD_URB_SUBMIT --------------->|
     |     (URB请求)                     |-- UsbDeviceConnection
     |                                  |-- bulkTransfer()/controlTransfer()
     |                                  |-- 读取/写入USB设备
     |<-- CMD_URB_COMPLETE -------------|
           (URB响应+数据)
```

## 实现步骤

### Step 1: 创建UrbHandler类
**文件**: `relay-host/USBRelay/app/src/main/java/com/olh/usbrelay/UrbHandler.kt`

**职责**:
- 管理单个USB设备的连接
- 处理URB提交请求
- 执行实际的USB传输
- 返回URB完成响应

**关键方法**:
```kotlin
class UrbHandler(
    private val usbManager: UsbManager,
    private val device: UsbDevice
) {
    private var connection: UsbDeviceConnection? = null
    private val urbQueue = ConcurrentHashMap<Long, UrbContext>()
    
    fun open(): Boolean
    fun close()
    fun handleUrbSubmit(urbSubmit: UrbSubmit): UrbComplete
    private fun executeControlTransfer(...): ByteArray
    private fun executeBulkTransfer(...): ByteArray
    private fun executeInterruptTransfer(...): ByteArray
}
```

### Step 2: 修改UsbService.kt
**添加**:
1. 在ClientHandler中集成UrbHandler
2. 处理CMD_URB_SUBMIT命令
3. 发送CMD_URB_COMPLETE响应
4. 处理设备断开时的清理

**关键修改**:
```kotlin
private inner class ClientHandler(...) {
    private val urbHandlers = mutableMapOf<Int, UrbHandler>()
    
    // 在handleImportDevice成功后
    val urbHandler = UrbHandler(usbManager, device)
    if (urbHandler.open()) {
        urbHandlers[devId] = urbHandler
    }
    
    // 处理URB_SUBMIT
    CMD_URB_SUBMIT -> {
        handleUrbSubmit(devId, payload)
    }
}
```

### Step 3: 定义URB数据结构
**文件**: `relay-host/USBRelay/app/src/main/java/com/olh/usbrelay/UrbProtocol.kt`

```kotlin
data class UrbSubmit(
    val seqNum: Long,
    val devId: Int,
    val transferType: Int,
    val endpoint: Int,
    val direction: Int,
    val dataLen: Int,
    val interval: Int,
    val setupPacket: ByteArray
)

data class UrbComplete(
    val seqNum: Long,
    val status: Int,
    val data: ByteArray
)
```

### Step 4: 实现USB传输逻辑

#### 控制传输 (CTRL)
```kotlin
private fun executeControlTransfer(
    connection: UsbDeviceConnection,
    setupPacket: ByteArray,
    dataLen: Int,
    direction: Int
): ByteArray {
    val requestType = setupPacket[0].toInt()
    val request = setupPacket[1].toInt()
    val value = ((setupPacket[3].toInt() and 0xFF) shl 8) or (setupPacket[2].toInt() and 0xFF)
    val index = ((setupPacket[5].toInt() and 0xFF) shl 8) or (setupPacket[4].toInt() and 0xFF)
    val length = ((setupPacket[7].toInt() and 0xFF) shl 8) or (setupPacket[6].toInt() and 0xFF)
    
    val buffer = ByteArray(length)
    
    val result = if (direction == USBIP_OLH_DIR_IN) {
        connection.controlTransfer(requestType, request, value, index, buffer, length, TIMEOUT)
    } else {
        connection.controlTransfer(requestType, request, value, index, buffer, length, TIMEOUT)
    }
    
    return if (result >= 0) buffer.copyOf(result) else byteArrayOf()
}
```

#### 批量传输 (BULK)
```kotlin
private fun executeBulkTransfer(
    connection: UsbDeviceConnection,
    endpoint: UsbEndpoint,
    data: ByteArray,
    direction: Int
): ByteArray {
    return if (direction == USBIP_OLH_DIR_IN) {
        val buffer = ByteArray(data.size)
        val result = connection.bulkTransfer(endpoint, buffer, buffer.size, TIMEOUT)
        if (result >= 0) buffer.copyOf(result) else byteArrayOf()
    } else {
        val result = connection.bulkTransfer(endpoint, data, data.size, TIMEOUT)
        if (result >= 0) byteArrayOf() else throw IOException("Bulk transfer failed")
    }
}
```

### Step 5: 协议编码/解码

#### 解析URB_SUBMIT
```kotlin
fun parseUrbSubmit(payload: ByteArray): UrbSubmit {
    val buffer = ByteBuffer.wrap(payload).order(ByteOrder.BIG_ENDIAN)
    
    return UrbSubmit(
        seqNum = buffer.int.toLong() and 0xFFFFFFFF,
        devId = buffer.short.toInt() and 0xFFFF,
        transferType = buffer.get().toInt() and 0xFF,
        endpoint = buffer.get().toInt() and 0xFF,
        direction = buffer.get().toInt() and 0xFF,
        reserved1 = buffer.get(),
        dataLen = buffer.int,
        interval = buffer.int,
        setupPacket = ByteArray(8).also { buffer.get(it) }
    )
}
```

#### 构建URB_COMPLETE
```kotlin
fun buildUrbComplete(urbComplete: UrbComplete): ByteArray {
    val headerSize = 4 + 4 + 4  // seq_num + status + data_len
    val payload = ByteArray(headerSize + urbComplete.data.size)
    val buffer = ByteBuffer.wrap(payload).order(ByteOrder.BIG_ENDIAN)
    
    buffer.putInt((urbComplete.seqNum and 0xFFFFFFFF).toInt())
    buffer.putInt(urbComplete.status)
    buffer.putInt(urbComplete.data.size)
    buffer.put(urbComplete.data)
    
    return payload
}
```

## 技术难点

### 1. 异步URB处理
**问题**: USB传输可能阻塞，需要在后台线程执行  
**方案**: 使用Coroutine或ThreadPool

### 2. URB超时处理
**问题**: USB设备可能不响应  
**方案**: 设置合理的timeout（默认5秒）

### 3. 内存管理
**问题**: 大量URB可能导致内存泄漏  
**方案**: 使用对象池和及时释放

### 4. 线程安全
**问题**: 多个客户端同时访问同一设备  
**方案**: 使用ConcurrentHashMap和synchronized

## 测试计划

### 单元测试
- [ ] URB协议编解码测试
- [ ] 控制传输测试
- [ ] 批量传输测试
- [ ] 中断传输测试

### 集成测试
- [ ] 简单USB设备（如U盘）读写
- [ ] USB串口设备通信
- [ ] HID设备测试
- [ ] 多设备并发测试

### 性能测试
- [ ] 吞吐量测试
- [ ] 延迟测试
- [ ] 长时间稳定性测试

## 预期成果

1. ✅ Android端可以接收和处理URB请求
2. ✅ 通过Android USB Host API执行实际USB传输
3. ✅ 返回URB完成响应给Windows客户端
4. ⚠️ Windows端仍为占位符（需要驱动集成才能真正使用）

## 下一步

完成Android端实现后：
1. 进行代码审核（第二轮）
2. 实现Windows端URB接收框架
3. 端到端测试
4. 性能优化

---

**文档版本**: v1.0  
**创建日期**: 2026-05-14  
**状态**: 准备开始实施
