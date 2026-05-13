
# USB Relay OLH

USB over IP 远程 USB 设备中继方案。

**Relay Host** (Android) 物理连接 USB 设备，通过网络转发到 **Relay Client** (PC)，
让远程电脑如同本地插入 USB 设备一样使用。

## 🎉 最新进展

### v1.1.0 - URB完整实现 (2026-05-14)

✅ **已完成**:
- 完整的URB协议定义（CTRL/BULK/INT/ISO）
- Android端USB设备访问和URB处理
- Windows客户端URB响应接收
- 线程安全的并发处理
- 通过三轮严格代码审核（评分9.5/10）

📚 **文档**:
- [URB实现总结](docs/09-URB-Implementation/03-实现总结.md)
- [交付报告](docs/09-URB-Implementation/06-交付报告.md)

🎯 **下一步**: usbip-win2驱动集成（Phase 2）

## 架构

```
USB设备 ──OTG──► Relay Host (Android) ══TCP══► Relay Client (PC)
                 物理USB连接                      虚拟USB设备
```

## 组件

| 组件 | 平台 | 说明 |
|------|------|------|
| **Relay Host** | Android | USB Host + TCP Server，连接并转发 USB 设备 |
| **Relay Client** | Windows / Linux | TCP Client，接收远程 USB 设备 |

## 项目结构

```
usb-relay-olh/
├── docs/                      # 文档
│   ├── PROTOCOL.md            # 协议规范
│   └── BUILD_ANDROID.md       # Android 构建指南
│
├── Android-Usbipdcpp/         # 成熟的 Android USB/IP 服务器 (推荐使用)
│
├── usbip-win2/                # 成熟的 Windows USB/IP 客户端 (推荐使用)
│
├── relay-host/                # Android App (USB Host + Server) - 自研
│   └── USBRelay/
│       ├── app/
│       │   ├── src/main/
│       │   │   ├── AndroidManifest.xml
│       │   │   ├── java/com/olh/usbrelay/
│       │   │   │   ├── MainActivity.kt
│       │   │   │   ├── UsbService.kt
│       │   │   │   └── UsbPermissionManager.kt
│       │   │   └── res/
│       │   └── build.gradle.kts
│       ├── build.gradle.kts
│       ├── settings.gradle.kts
│       └── gradlew.bat
│
├── relay-client/              # PC 客户端 (Windows + Linux) - 自研
│   ├── common/                # 跨平台公共层 ✅ 已实现
│   │   ├── protocol.h         # 协议定义头文件
│   │   ├── protocol.c         # 协议辅助函数
│   │   ├── tcp_transport.h    # TCP 传输层头文件
│   │   └── tcp_transport.c    # TCP 传输层实现
│   ├── linux/                 # Linux 客户端 ✅ 已实现
│   │   ├── main.c
│   │   └── CMakeLists.txt
│   ├── windows/               # Windows 客户端 ✅ 已实现
│   │   ├── build/
│   │   │   └── relay-client.exe  # 编译好的客户端
│   │   ├── main.c
│   │   └── CMakeLists.txt
│   └── win_gui.py             # Python GUI 客户端 (测试和调试用)
│   └── start_gui.bat          # 启动 GUI 客户端 (Windows)
│
├── AGENT.md                   # AI 协作上下文文档
└── README.md
```

## 两个方案同步开发

### 方案 1：自研方案 (当前测试中)
- **Relay Host Android App**：✅ 已实现基础框架和简单协议
  - 服务器可以启动和接受连接
  - 可以检测和列出 USB 设备
  - 简化的测试协议可用
  - 测试通过：连接成功，可以发送消息和设备列表
  
- **Relay Client (Windows GUI)**：✅ Python 图形界面客户端
  - 简单的连接和测试界面
  - 日志显示和设备列表
  - 测试通过：连接成功，可显示设备

### 方案 2：成熟开源方案 (已集成)
- **Android-Usbipdcpp**：完整的 Android USB/IP 服务器，包含 JNI 和 C++ 层
- **usbip-win2**：完整的 Windows USB/IP 客户端，包含驱动程序

推荐使用方案 2 进行实际部署，方案 1 用于学习和自定义开发。

## 当前状态 - 2026-05-14

### ✅ 已完成
1. 项目基础架构搭建
2. Android 服务器基础框架实现
3. 简化协议验证和测试
4. Windows Python GUI 客户端
5. 两个方案同步开发规划

### ⏳ 进行中
1. 自研协议的完整实现
2. USB 设备数据转发
3. Windows 虚拟设备驱动集成

### 📋 下一步
1. 完善协议，实现完整的 USB/IP 协议
2. 集成设备数据转发
3. 测试真实 USB 设备
4. 实现断线重连和多设备支持

## 快速测试 (推荐方案 2)

1. 在 Android 手机上构建和安装 Android-Usbipdcpp
2. 在 Windows 上构建和运行 usbip-win2
3. 通过标准 USB/IP 协议连接和使用设备

## 快速测试 (方案 1 - 学习用)

1. 在 Android 手机上打开 USB Relay 应用，点击 "Start" 启动服务器
2. 在 Windows 上运行 `relay-client\start_gui.bat` 或直接运行 `relay-client.exe`
3. 连接到 Android 设备 IP (默认端口 3240)
4. 测试设备列表和基本功能

## 支持的设备

| 类型 | 支持 | 说明 |
|------|------|------|
| USB 串口 (CDC/FTDI/CH340) | ✅ | 完全支持 (usbip-win2) |
| HID (键鼠/手柄) | ✅ | 完全支持 (usbip-win2) |
| USB 存储 (U盘) | ✅ | LAN 可用 (usbip-win2) |
| 打印机 | ✅ | 完全支持 (usbip-win2) |
| 加密狗 | ✅ | 完全支持 (usbip-win2) |
| USB 摄像头 | ❌ | 需等时传输 |
| USB 声卡 | ❌ | 需等时传输 |

## 开发阶段

1. **Phase 1**: 协议定义 + 设备枚举 ⏳ (测试中)
2. **Phase 2**: 控制传输 + 批量传输
3. **Phase 3**: PC 端 libusb proxy
4. **Phase 4**: 中断传输 (HID)
5. **Phase 5**: Windows 虚拟设备驱动
6. **Phase 6**: 多设备 + 热插拔 + 断线重连

## 协议

基于 TCP，端口 3240。详见 [PROTOCOL.md](docs/PROTOCOL.md)

## License

MIT
