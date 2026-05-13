# USB/IP-OLH 协议规范

版本: 1.0
端口: 3240 (TCP)

## 消息格式

所有消息遵循统一头部格式:

```
Offset  Size  Field         Description
------  ----  -----         -----------
0       2     command       命令类型 (网络字节序 Big-Endian)
2       4     seq_num       序列号 (请求-响应配对)
6       2     dev_id        设备ID (0xFFFF = 全局)
8       4     length        Payload 长度 (不含头部)
12      4     reserved      保留字段 (对齐填充)
------  ----                -----------
Total:  16 bytes (头部固定)
```

## 命令定义

| Command       | Code   | 方向            | 说明                 |
|---------------|--------|-----------------|---------------------|
| DEVLIST_REQ   | 0x0001 | Client → Server | 请求设备列表          |
| DEVLIST_RES   | 0x0002 | Server → Client | 设备列表响应          |
| IMPORT_REQ    | 0x0003 | Client → Server | 请求连接/导入设备     |
| IMPORT_RES    | 0x0004 | Server → Client | 导入结果响应          |
| URB_SUBMIT    | 0x0005 | Client → Server | 提交 USB 传输请求     |
| URB_COMPLETE  | 0x0006 | Server → Client | USB 传输完成响应      |
| URB_UNLINK    | 0x0007 | Client → Server | 取消 USB 传输请求     |
| URB_UNLINK_RET| 0x0008 | Server → Client | 取消结果响应          |
| DISCONNECT    | 0x0009 | 双向            | 断开设备连接          |
| KEEPALIVE     | 0x000A | 双向            | 心跳保活              |

## DEVLIST — 设备列表

### DEVLIST_REQ (0x0001)

```
无 Payload，仅发送头部。
dev_id = 0xFFFF
```

### DEVLIST_RES (0x0002)

```
Offset  Size  Field         Description
------  ----  -----         -----------
0       2     num_devices   设备数量
2       N*    devices[]     设备信息数组

每个设备信息:
Offset  Size  Field              Description
------  ----  -----              -----------
0       2     dev_id             设备ID
2       2     busnum             总线号
4       2     devnum             设备号
6       2     speed              速度 (1=Low, 2=Full, 3=High)
8       2     vendor_id          厂商ID (VID)
10      2     product_id         产品ID (PID)
12      2     bcdDevice          设备版本
14      1     device_class       设备类
15      1     device_subclass    设备子类
16      1     device_protocol    设备协议
17      1     config_count       配置数量
18      1     interface_count    接口数量(当前配置)
19      2     path_len           设备路径字符串长度
21      N     path               设备路径 (如 "1-1")
?       2     desc_len           设备描述符长度
?       N     device_descriptor  原始设备描述符
```

## IMPORT — 导入设备

### IMPORT_REQ (0x0003)

```
Offset  Size  Field    Description
------  ----  -----    -----------
0       2     dev_id   要导入的设备ID
```

### IMPORT_RES (0x0004)

```
Offset  Size  Field           Description
------  ----  -----           -----------
0       4     status          0=成功, 非0=错误码
4       2     dev_id          设备ID
6       2     num_interfaces  接口数量
8       N     interfaces[]    接口信息

每个接口信息:
Offset  Size  Field         Description
------  ----  -----         -----------
0       1     bInterfaceNumber
1       1     bAlternateSetting
2       1     bInterfaceClass
3       1     bInterfaceSubClass
4       1     bInterfaceProtocol
5       1     num_endpoints
6       N*    endpoints[]   端点信息

每个端点信息:
Offset  Size  Field              Description
------  ----  -----              -----------
0       1     bEndpointAddress
1       1     bmAttributes       (传输类型: 控制/批量/中断/等时)
2       2     wMaxPacketSize
4       1     bInterval
```

## URB — USB 传输

### URB_SUBMIT (0x0005)

```
Offset  Size  Field           Description
------  ----  -----           -----------
0       2     dev_id          设备ID
2       4     seq_num         URB序列号 (用于配对和取消)
6       1     transfer_type   0=ISO, 1=INT, 2=CTRL, 3=BULK
7       1     endpoint        端点地址
8       1     direction       0=OUT (host→dev), 1=IN (dev→host)
9       1     reserved
10      4     transfer_flags  传输标志位
14      4     buffer_length   数据缓冲区长度
18      8     setup_packet    控制传输的setup包 (非控制传输全0)
26      4     interval        轮询间隔 (中断/等时传输)
30      4     number_of_packets  等时传输的包数量 (非等时=0)
34      4     iso_frame_desc  等时帧描述 (非等时=0)
38      N     buffer          OUT方向: 要发送的数据
                              IN方向: 空 (长度=0)
```

**transfer_type 值:**
| 值 | 类型 | 说明 |
|----|------|------|
| 0  | ISOCHRONOUS | 等时传输 (Phase 5) |
| 1  | INTERRUPT   | 中断传输 (Phase 4) |
| 2  | CONTROL     | 控制传输 (Phase 1) |
| 3  | BULK        | 批量传输 (Phase 2) |

**setup_packet (仅控制传输):**
```
Offset  Size  Field           Description
------  ----  -----           -----------
0       1     bmRequestType   请求类型
1       1     bRequest        请求码
2       2     wValue          值
4       2     wIndex          索引
6       2     wLength         数据长度
```

### URB_COMPLETE (0x0006)

```
Offset  Size  Field           Description
------  ----  -----           -----------
0       2     dev_id          设备ID
2       4     seq_num         对应的URB序列号
6       4     status          传输状态 (0=成功, 负数=USB错误码)
10      4     actual_length   实际传输的数据长度
14      4     error_count     等时传输的错误计数 (非等时=0)
18      N     buffer          IN方向: 接收到的数据
                              OUT方向: 空
```

**USB 错误码:**
| 值 | 含义 |
|----|------|
| 0  | 成功 |
| -1 | 传输错误 |
| -2 | 设备断开 |
| -3 | 超时 |
| -4 | 管道停止 (STALL) |
| -5 | 溢出 |

### URB_UNLINK (0x0007)

```
Offset  Size  Field       Description
------  ----  -----       -----------
0       2     dev_id      设备ID
2       4     seq_num     要取消的URB序列号
```

### URB_UNLINK_RET (0x0008)

```
Offset  Size  Field       Description
------  ----  -----       -----------
0       2     dev_id      设备ID
2       4     seq_num     被取消的URB序列号
6       4     status      取消状态 (0=成功)
```

## 连接生命周期

```
Client                              Server
  │                                    │
  │──── DEVLIST_REQ ──────────────────►│
  │◄─── DEVLIST_RES (设备列表) ────────│
  │                                    │
  │──── IMPORT_REQ (dev_id=1) ────────►│
  │◄─── IMPORT_RES (status=0) ────────│
  │                                    │
  │  [设备已连接，开始传输]              │
  │                                    │
  │──── URB_SUBMIT (ctrl, GET_DESC) ──►│
  │◄─── URB_COMPLETE (描述符数据) ─────│
  │                                    │
  │──── URB_SUBMIT (bulk, WRITE) ─────►│
  │◄─── URB_COMPLETE (status=0) ──────│
  │                                    │
  │──── URB_SUBMIT (bulk, READ) ──────►│
  │◄─── URB_COMPLETE (数据) ──────────│
  │                                    │
  │  ... 持续传输 ...                   │
  │                                    │
  │──── DISCONNECT ───────────────────►│
  │                                    │
```

## 超时与重连

- TCP 连接超时: 10 秒
- URB 超时: 由 Client 端根据设备类型决定 (默认 5 秒)
- 心跳间隔: 30 秒 (KEEPALIVE)
- 断线重连: Client 检测到断连后自动重连，重连后需重新 IMPORT
