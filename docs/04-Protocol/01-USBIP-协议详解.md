# USB/IP 协议详解

## 协议版本
- **版本**: v1.1.1 (0x0111)
- **参考**: Linux 内核驱动

## 协议架构

### 连接建立流程
```
Windows (客户端)                  Android (服务器)
     |                               |
     |---- OP_REQ_DEVLIST --------->|   列出设备
     |<--- OP_REP_DEVLIST ----------|
     |                               |
     |---- OP_REQ_IMPORT ---------->|   导入设备
     |<--- OP_REP_IMPORT -----------|
     |                               |
     |<== USBIP_CMD_SUBMIT =========>|   URB 转发
     |== USBIP_RET_SUBMIT ==========>|
```

## 协议头定义 (consts.h)

### 版本号
```cpp
constexpr UINT16 USBIP_VERSION = 0x0111; // v1.1.1
```

### 端口
```cpp
constexpr UINT16 USBIP_PORT = 3240; // 标准 USB/IP 端口
```

## 操作阶段协议 (proto_op.h)

### 1. 公共头 (op_common)
```cpp
struct op_common {
    UINT16 version;  // 0x0111
    UINT16 code;     // 操作码
    UINT32 status;   // 状态码 (仅用于回复)
};
```

### 2. 操作码
| 操作码 | 说明 |
|-------|------|
| OP_REQ_DEVLIST | 请求设备列表 |
| OP_REP_DEVLIST | 设备列表回复 |
| OP_REQ_IMPORT | 请求导入设备 |
| OP_REP_IMPORT | 导入设备回复 |

### 3. 设备列表请求/回复

**请求**:
```cpp
struct op_devlist_request {
    UINT32 _reserved;  // 保留
};
```

**回复**:
```cpp
struct op_devlist_reply {
    UINT32 ndev;  // 设备数量
    // 后跟 op_devlist_reply_extra[] 数组
};

struct op_devlist_reply_extra {
    usbip_usb_device udev;
    // 后跟 usbip_usb_interface[] 数组
};
```

### 4. 导入设备请求/回复

**请求**:
```cpp
struct op_import_request {
    char busid[BUS_ID_SIZE];  // 总线ID
};
```

**回复**:
```cpp
struct op_import_reply {
    usbip_usb_device udev;
};
```

### 5. USB 设备描述
```cpp
struct usbip_usb_device {
    char path[DEV_PATH_MAX];
    char busid[BUS_ID_SIZE];
    UINT32 busnum;
    UINT32 devnum;
    UINT32 speed;
    
    UINT16 idVendor;
    UINT16 idProduct;
    UINT16 bcdDevice;
    
    UINT8 bDeviceClass;
    UINT8 bDeviceSubClass;
    UINT8 bDeviceProtocol;
    
    UINT8 bConfigurationValue;
    UINT8 bNumConfigurations;
    UINT8 bNumInterfaces;
};
```

## URB 转发阶段协议 (proto.h)

### 1. 基础头 (header_basic)
```cpp
struct header_basic {
    UINT32 command;    // CMD_SUBMIT, CMD_UNLINK, RET_SUBMIT, RET_UNLINK
    seqnum_t seqnum;   // 序列号
    UINT32 devid;      // 设备ID
    UINT32 direction;  // in/out
    UINT32 ep;         // 端点号
};
```

### 2. 命令类型
| 命令 | 值 | 说明 |
|-----|----|------|
| CMD_SUBMIT | 1 | 提交 URB |
| CMD_UNLINK | 2 | 取消 URB |
| RET_SUBMIT | 3 | URB 完成回复 |
| RET_UNLINK | 4 | 取消回复 |

### 3. CMD_SUBMIT 头
```cpp
struct header_cmd_submit {
    UINT32 transfer_flags;
    INT32 transfer_buffer_length;
    INT32 start_frame;
    INT32 number_of_packets;  // 非等时传输用 -1
    INT32 interval;
    UINT8 setup[8];  // 控制传输的 setup 包
};
```

### 4. RET_SUBMIT 头
```cpp
struct header_ret_submit {
    INT32 status;
    INT32 actual_length;
    INT32 start_frame;
    INT32 number_of_packets;
    INT32 error_count;
};
```

### 5. 完整头结构
```cpp
struct header : header_basic {
    union {
        header_cmd_submit cmd_submit;
        header_ret_submit ret_submit;
        header_cmd_unlink cmd_unlink;
        header_ret_unlink ret_unlink;
    };
};
// 总大小: 48 字节
```

## 字节序说明

⚠️ **重要**: 所有数字字段必须转换为**网络字节序 (大端序)**

需字节序转换的字段:
- `op_common.version`
- `op_common.code`
- `op_common.status`
- `header.command`
- `header.seqnum`
- `header.devid`
- 所有 `usbip_usb_device` 中的多字节字段

## 通信流程完整示例

### 1. 建立 TCP 连接
- 连接到服务器端口 3240

### 2. 列出设备
```
客户端发送: OP_REQ_DEVLIST
服务器回复: OP_REP_DEVLIST + ndev + 设备列表
```

### 3. 导入设备
```
客户端发送: OP_REQ_IMPORT (busid)
服务器回复: OP_REP_IMPORT (udev)
```

### 4. URB 转发循环
```
对于每个 USB 请求:
    客户端发送: USBIP_CMD_SUBMIT + (可选数据)
    服务器处理: 执行 USB 传输
    服务器回复: USBIP_RET_SUBMIT + (可选数据)
```

## 等时传输特殊处理

- `number_of_packets`: 包数量 (0 ~ 1024)
- 后跟 `iso_packet_descriptor[]` 数组:
```cpp
struct iso_packet_descriptor {
    UINT32 offset;
    UINT32 length;
    UINT32 actual_length;
    UINT32 status;
};
```
