# Android 服务器 - Android-Usbipdcpp 介绍

## 项目信息
- **项目**: Android-Usbipdcpp
- **位置**: Android-Usbipdcpp/
- **来源**: https://github.com/yunsmall/Android-Usbipdcpp
- **语言**: Kotlin + C++ (NDK)
- **许可证**: GPL-3.0

## 特性

| 特性 | 说明 |
|------|------|
| USB/IP 协议 | 完整 v1.1.1 协议实现 |
| 多设备支持 | 可同时导出多个 USB 设备 |
| 可配置端口 | 默认 3240，可自定义 |
| 前台服务 | 保持后台稳定运行 |
| Jetpack Compose | 现代化 UI 界面 |

## 项目结构

```
Android-Usbipdcpp/
├── app/
│   ├── src/main/
│   │   ├── cpp/
│   │   │   ├── CMakeLists.txt          # CMake 构建配置
│   │   │   ├── usbipd_jni.cpp          # JNI 桥接层
│   │   │   └── vcpkg.json              # 依赖配置
│   │   ├── java/com/yunsmall/usbipdcpp/
│   │   │   ├── ui/                     # Compose UI
│   │   │   ├── MainActivity.kt         # 主活动
│   │   │   ├── UsbIpNative.kt          # 原生库封装
│   │   │   ├── UsbService.kt           # USB 前台服务
│   │   │   └── UsbPermissionManager.kt # 权限管理
│   │   ├── res/                        # 资源文件
│   │   └── AndroidManifest.xml         # 清单文件
│   └── build.gradle.kts                # Gradle 配置
└── README.md
```

## 核心代码分析

### UsbIpNative.kt
**功能**: 封装 JNI 原生调用

**主要方法**:
| 方法 | 说明 |
|------|------|
| nativeInit() | 初始化原生库 |
| bindUsbDeviceNative() | 绑定 USB 设备 |
| unbindUsbDeviceNative() | 解绑 USB 设备 |
| startServer(port) | 启动 USB/IP 服务器 |
| stopServer() | 停止服务器 |

**工作原理**:
1. 通过 `UsbManager.openDevice()` 打开 USB 设备
2. 反射获取文件描述符 (fd)
3. 将 fd 传递给原生层进行 USB 通信
4. 原生层实现完整 USB/IP 协议

### UsbService.kt
**功能**: 前台服务，保持服务器运行

**职责**:
- 启动/停止 USB/IP 服务器
- 管理 USB 设备绑定
- 前台通知栏显示状态

## 配置要求

### build.gradle.kts
```kotlin
minSdk = 28          # Android 9.0+
targetSdk = 36
ndkVersion = "29.0.14033849"
```

### ABI 支持
- arm64-v8a (推荐)
- x86_64 (模拟器)

### CMake 配置
```kotlin
externalNativeBuild {
    cmake {
        path = file("src/main/cpp/CMakeLists.txt")
        version = "3.22.1"
    }
}
```

## 使用流程

1. **授予 USB 权限**
2. **选择 USB 设备**
3. **绑定设备**
4. **启动服务器**
5. **在 Windows 端用 usbip-win2 连接**

## 与现有 relay-host 的对比

| 特性 | 原有 relay-host | Android-Usbipdcpp |
|------|----------------|------------------|
| 协议 | 自定义协议 | 标准 USB/IP |
| 完整性 | 原型框架 | 完整实现 |
| 语言 | Kotlin | Kotlin + C++ |
| 兼容性 | 仅与自建客户端 | 所有 USB/IP 客户端 |
| 功能 | 无实际实现 | 完整功能 |
