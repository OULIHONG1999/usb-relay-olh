# Windows 客户端 - relay-client 验证报告

## 项目信息
- **项目**: relay-client (Windows)
- **位置**: relay-client/windows/
- **语言**: C
- **构建工具**: CMake + MinGW

## 验证结果 ✅

### 1. 编译状态
- **状态**: 通过
- **构建时间**: 2026-05-14
- **编译器**: MinGW GCC 6.3.0
- **CMake 版本**: 4.2.3

### 2. 功能测试
- **测试**: 运行验证
- **结果**: ✅ 程序可正常启动
- **输出**:
  ```
  Relay Client v0.1.0
  Usage: E:\WORK\usb-relay-olh\relay-client\windows\build\relay-client.exe <host> [port]
  ```

## 源代码分析

### main.c (Windows)
**功能**:
- Windows 套接字 (Winsock) 初始化与清理
- TCP 连接建立
- 交互式命令行:
  - `list` - 请求设备列表
  - `import <id>` - 导入指定设备
  - `quit` - 退出程序

### common/protocol.h
**功能**:
- 定义协议消息结构
- 命令码:
  - CMD_PING = 0x01
  - CMD_LIST_DEVICES = 0x02
  - CMD_IMPORT_DEVICE = 0x03

### common/tcp_transport.c/h
**功能**:
- 封装 TCP 发送和接收函数
- 处理网络字节序转换

## 构建方式

```powershell
# 进入 Windows 客户端目录
cd relay-client/windows

# 创建构建目录
mkdir build ; cd build

# CMake 配置
cmake .. -G "MinGW Makefiles"

# 编译
mingw32-make
```

## 可执行文件位置
- `relay-client/windows/build/relay-client.exe`

## 备注
- 当前使用**自定义协议**，非 USB/IP 标准协议
- 如需与标准 USB/IP 客户端兼容，建议使用 usbip-win2
