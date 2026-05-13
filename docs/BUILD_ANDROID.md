# Android Relay Host 构建与安装指南

本文档说明如何编译和安装 USB Relay OLH 的 Android 应用（Relay Host）。

## 前置要求

- Android SDK（API 30 或更高版本）
- Gradle（项目已包含 Wrapper）
- Android 设备（Android 11+，支持 USB OTG）
- 设备开启 USB 调试

## 项目配置

项目配置位于：
- [build.gradle.kts](../relay-host/USBRelay/build.gradle.kts) - 根级构建配置
- [app/build.gradle.kts](../relay-host/USBRelay/app/build.gradle.kts) - 应用级构建配置

主要配置：
- `minSdk`: 30 (Android 11)
- `targetSdk`: 36
- `compileSdk`: 36.1
- `applicationId`: `com.olh.usbrelay`

## 构建步骤

### 1. 切换到项目目录

```powershell
cd e:\WORK\usb-relay-olh\relay-host\USBRelay
```

### 2. 编译 Debug 版本

```powershell
.\gradlew.bat assembleDebug
```

### 3. 检查连接的设备

```powershell
adb devices
```

### 4. 安装到设备

```powershell
.\gradlew.bat installDebug
```

### 5. 完整的清理并重新构建（可选）

```powershell
.\gradlew.bat clean installDebug
```

## 常见问题

### ADB 连接问题

如果遇到 ADB 连接问题，重启 ADB 服务：

```powershell
adb kill-server
adb start-server
```

### 安装失败

如果安装失败，可以尝试先卸载应用：

```powershell
adb uninstall com.olh.usbrelay
```

然后再重新安装。

## APK 位置

编译生成的 APK 文件位于：
```
relay-host/USBRelay/app/build/outputs/apk/debug/app-debug.apk
```

## 运行应用

安装完成后，在设备上找到 "USBRelay" 应用并启动。

## 相关文档

- [协议规范](PROTOCOL.md)
- [项目 README](../README.md)
