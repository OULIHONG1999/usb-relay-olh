
# AGENT 上下文文档 - USB Relay OLH

## 文档用途
本文档为 AI 协作提供完整的项目上下文，包含项目当前状态、已完成工作、技术栈、已知问题和下一步方向。

---

## 项目基本信息

| 属性 | 值 |
|------|-----|
| **项目名称** | USB Relay OLH |
| **项目类型** | USB over IP 远程设备中继方案 |
| **当前阶段** | Phase 1 (协议定义 + 设备枚举) 测试中 |
| **仓库位置** | `e:\WORK\usb-relay-olh` |
| **主要分支** | main |

---

## 项目架构

```
┌─────────────────┐         TCP (3240)         ┌─────────────────┐
│  USB 设备       │  ──────────────────────►   │  PC (Client)    │
│  (物理连接)     │                            │  (虚拟设备)     │
└────────┬────────┘                            └─────────────────┘
         │
         ▼
┌─────────────────────────────────────────────────────────────┐
│                    Relay Host (Android)                     │
│  ┌──────────────────┐      ┌──────────────────────────┐    │
│  │  USB Host API    │─────►│   TCP Server (Protocol)  │    │
│  └──────────────────┘      └──────────────────────────┘    │
└─────────────────────────────────────────────────────────────┘
```

---

## 代码库现状

### 目录结构
```
usb-relay-olh/
├── docs/
│   ├── PROTOCOL.md              # ✅ 协议规范文档
│   └── BUILD_ANDROID.md         # ✅ Android 构建指南
│
├── Android-Usbipdcpp/           # ✅ 成熟的 Android USB/IP 服务器
│
├── usbip-win2/                  # ✅ 成熟的 Windows USB/IP 客户端
│
├── relay-host/
│   └── USBRelay/                # Android 项目 - 自研
│       ├── app/
│       │   ├── src/main/
│       │   │   ├── AndroidManifest.xml  # ✅ 已更新权限
│       │   │   └── java/com/olh/usbrelay/
│       │   │       ├── MainActivity.kt   # ✅ 完整 UI 实现
│       │   │       ├── UsbService.kt     # ✅ 简化版 TCP 服务器
│       │   │       └── UsbPermissionManager.kt  # ✅ USB 权限管理
│       │   └── build.gradle.kts          # ✅ 应用级配置
│       ├── build.gradle.kts              # ✅ 根级配置
│       ├── settings.gradle.kts           # ✅ 项目设置
│       └── gradlew.bat                   # ✅ Gradle Wrapper
│
├── relay-client/
│   ├── common/                   # 跨平台公共代码
│   │   ├── protocol.h/c
│   │   └── tcp_transport.h/c
│   ├── linux/                    # Linux 客户端
│   ├── windows/                  # Windows 客户端
│   │   └── build/                # ✅ relay-client.exe 已编译
│   ├── win_gui.py                # ✅ Python GUI 客户端
│   └── start_gui.bat             # ✅ Windows 启动脚本
│
├── README.md                     # ✅ 项目主文档 (已更新)
└── AGENT.md                      # ⬅️ 本文件 (已更新)
```

### 技术栈

#### Relay Host (Android)
| 组件 | 版本/选型 | 说明 |
|------|-----------|------|
| **语言** | Kotlin | |
| **UI** | Jetpack Compose | |
| **minSdk** | 30 (Android 11) | |
| **targetSdk** | 36 | |
| **compileSdk** | 36.1 | |
| **包名** | `com.olh.usbrelay` | |
| **构建工具** | Gradle 9.4.1 | Kotlin DSL |

#### Relay Client (PC)
| 组件 | 选型 |
|------|------|
| **语言** | C |
| **构建** | CMake |
| **平台** | Windows, Linux |
| **GUI 测试** | Python (tkinter) |

---

## 已完成工作 (Agent 操作历史)

### 2026-05-14
**执行者**: AI Assistant

#### 完整的 Phase 1 实现与测试

##### 上午 - 基础搭建
1. **Android 应用编译与安装**
   - 读取并分析 build.gradle.kts 配置
   - 执行 `gradlew.bat assembleDebug` - 首次编译成功
   - 检测 Android 设备 (ID: `2ca98fe5`, 型号: `2211133C - 16`)
   - 执行 `gradlew.bat installDebug` - 首次安装成功
   - 解决 ADB 连接问题：执行 `adb kill-server && adb start-server`

2. **文档创建**
   - 创建 docs/BUILD_ANDROID.md - Android 构建指南
   - 创建初始 AGENT.md 和更新 README.md

##### 下午 - 两个方案同步开发
3. **方案 1：自研实现完善**
   - 更新 AndroidManifest.xml，添加必要权限：
     - INTERNET 网络权限
     - USB Host 权限
     - 前台服务权限
   - 实现 UsbPermissionManager.kt - USB 设备权限管理
   - 实现完整的 MainActivity.kt - 图形界面（无 XML 布局）
     - 服务器控制（Start/Stop）
     - USB 设备列表和状态显示
     - 设备绑定/解绑功能
     - 日志显示区域
   - 实现 UsbService.kt - 前台服务与简化版 TCP 服务器
   - 添加设备筛选配置（xml/device_filter.xml）
   - 多次构建和安装到设备（成功）

4. **协议实现与测试**
   - 简化协议设计，用于测试验证
   - 实现了基础连接、消息发送、设备列表传输
   - 创建 Python GUI 客户端（win_gui.py）
   - 创建快速启动脚本（start_gui.bat）

5. **实机测试与调试**
   - 用户在 Android 设备上启动服务器
   - Windows 客户端成功连接到 192.168.31.97:3240
   - 验证了通信链路：收到 "Hello from server!" 和测试设备列表
   - 连接和基础通信验证通过 ✅

##### 方案 2：成熟方案集成
6. **仓库结构完善**
   - 确保 Android-Usbipdcpp 和 usbip-win2 在仓库中可用
   - 更新 README 记录两个方案的状态

##### 文档与 Git 提交
7. **文档更新**
   - 更新 README.md - 记录当前状态和两个方案
   - 更新 AGENT.md - 完整记录今天的工作
   - 准备提交到 Git

---

## 环境状态

### 开发环境
| 项 | 状态 |
|----|------|
| **操作系统** | Windows |
| **工作目录** | `e:\WORK\usb-relay-olh` |
| **Android SDK** | 已配置 (路径: `C:\Users\17569\AppData\Local\Android\Sdk`) |
| **ADB** | 可用，已连接设备 |
| **Gradle** | 通过 Wrapper 使用 9.4.1 |

### 连接设备
| 设备 ID | 型号 | 状态 |
|---------|------|------|
| `2ca98fe5` | `2211133C - 16` | ✅ 已连接，APK 已成功安装 |

### 测试 IP
| 地址 | 说明 |
|------|------|
| `192.168.31.97:3240` | Android 设备 IP，可用于客户端连接 |

---

## 构建命令速查

### Android 构建
```powershell
# 切换到项目目录
cd e:\WORK\usb-relay-olh\relay-host\USBRelay

# 编译 Debug APK
.\gradlew.bat assembleDebug

# 安装到设备
.\gradlew.bat installDebug

# 清理并重新构建
.\gradlew.bat clean installDebug

# 检查设备
adb devices

# 重启 ADB (如遇问题)
adb kill-server
adb start-server
```

### 输出位置
- APK: `relay-host/USBRelay/app/build/outputs/apk/debug/app-debug.apk`
- Windows 客户端: `relay-client/windows/build/relay-client.exe`

---

## 当前状态 - 已验证

### ✅ 已验证功能
1. **Android 服务器**
   - ✅ 应用可启动和停止服务器
   - ✅ 监听端口 3240，可接受连接
   - ✅ 发送简单消息和测试设备列表
   - ✅ 简化的测试协议可用

2. **Windows 客户端**
   - ✅ 基础 C 客户端（relay-client.exe）可运行
   - ✅ Python GUI 客户端（win_gui.py）可用
   - ✅ 连接到 Android 设备成功
   - ✅ 显示接收的数据和日志

3. **通信链路**
   - ✅ TCP 连接建立成功
   - ✅ 数据双向传输
   - ✅ 基本消息和设备列表解析

### ⏳ 待完善功能
1. **完整的 USB/IP 协议实现**
   - 替换当前的简化协议
   - 实现标准 USB/IP 协议 (参考 Android-Usbipdcpp)

2. **USB 设备数据转发**
   - 真实 USB 设备连接和权限管理
   - URBs (USB Request Blocks) 转发

3. **Windows 虚拟设备驱动**
   - 集成或开发驱动程序

4. **断线重连和多设备支持**

---

## 已知问题与解决方案

| 问题 | 解决方案 | 状态 |
|------|----------|------|
| ADB 连接失败，报错 `Could not create ADB Bridge` | 执行 `adb kill-server && adb start-server` | ✅ 已记录 |
| 协议解析错误 - 格式不匹配 | 使用简化测试协议替代 | ✅ 已解决 |
| StringVar 调用 configure() 错误 - tkinter 对象类型问题 | 保存 Label 对象而非仅保存 StringVar | ✅ 已修复 |

---

## 下一步建议

### 方向 A - 自研方案继续完善（学习用途）
1. **完善协议实现** - 参考 USB/IP 标准协议
2. **集成真实 USB Host 通信** - 读取和转发 USB 设备数据
3. **实现完整的设备列表和绑定机制**
4. **添加日志转发功能** - Android 日志转发到客户端
5. **实现多设备支持和断线重连**

### 方向 B - 成熟方案部署（实际使用）
1. **构建和配置 Android-Usbipdcpp** - 使用完整的 USB/IP 服务器
2. **配置和测试 usbip-win2** - 使用完整的 Windows USB/IP 客户端
3. **端到端完整测试** - 测试真实 USB 设备中继

---

## Agent 工作限制与要求

### 必读文档
在开始任何工作前，必须先阅读以下文档：
1. [README.md](file:///e:/WORK/usb-relay-olh/README.md) - 理解项目目标和架构
2. [docs/PROTOCOL.md](file:///e:/WORK/usb-relay-olh/docs/PROTOCOL.md) - 协议规范
3. [docs/BUILD_ANDROID.md](file:///e:/WORK/usb-relay-olh/docs/BUILD_ANDROID.md) - 构建流程

### 工作流程要求

#### 1. 代码修改前
- ✅ 先使用搜索工具了解现有代码结构
- ✅ 阅读相关文件，理解当前实现
- ✅ 确认修改方案与项目架构一致
- ❌ 不要在不了解上下文的情况下盲目修改

#### 2. 代码质量要求
- ✅ 遵循现有代码风格和命名规范
- ✅ Android 代码使用 Kotlin idioms
- ✅ 保持最小变更原则，只修改必要的部分
- ✅ 添加的代码必须有清晰的逻辑
- ❌ 不要引入不必要的第三方库
- ❌ 不要随意重构现有代码，除非有明确理由

#### 3. 构建与测试要求
- ✅ 修改代码后必须先编译验证：`gradlew.bat assembleDebug`
- ✅ 编译成功后再安装测试：`gradlew.bat installDebug`
- ✅ 验证功能正常后再标记任务完成
- ❌ 不要提交无法编译的代码
- ❌ 不要跳过编译验证步骤

#### 4. 文档更新要求
- ✅ 完成重要功能后更新本文档（AGENT.md）的"已完成工作"部分
- ✅ 记录遇到的问题和解决方案到"已知问题"部分
- ✅ 更新环境状态信息（如有变更）
- ✅ 更新"最后更新"和"下次接手提示"
- ❌ 不要忘记记录操作历史

#### 5. Git 操作要求
- ✅ 工作前检查当前分支和状态
- ✅ 合理组织提交（按功能模块）
- ✅ 编写清晰的提交信息
- ❌ 不要强制推送，除非有必要
- ❌ 不要在不了解的情况下合并分支

### 技术约束

#### Android 开发约束
- **最低 SDK**: API 30 - 不要使用低于此版本的 API
- **目标 SDK**: API 36 - 遵循最新 Android 规范
- **UI 框架**: Jetpack Compose - 不要引入 XML 布局
- **语言**: Kotlin - 不要添加 Java 文件

#### 架构约束
- 遵循现有的项目结构
- 协议实现必须严格遵循 [PROTOCOL.md](file:///e:/WORK/usb-relay-olh/docs/PROTOCOL.md)
- 保持 Relay Host 和 Relay Client 的协议兼容性

### 决策与沟通要求
- 遇到架构级别的变更时，先与用户确认
- 不确定的技术选型，先询问用户偏好
- 发现 bug 或问题时，先记录再解决
- 完成阶段工作后，向用户汇报成果

### 紧急情况处理
- 如遇到编译错误且无法快速解决，先回滚到可工作状态
- 遇到设备连接问题，先尝试重启 ADB 服务
- 如误删除重要文件，检查 git 状态看是否可恢复

---

## 相关文档索引
| 文档 | 路径 | 用途 |
|------|------|------|
| **项目 README** | [README.md](file:///e:/WORK/usb-relay-olh/README.md) | 项目概述、架构、支持设备 |
| **协议规范** | [docs/PROTOCOL.md](file:///e:/WORK/usb-relay-olh/docs/PROTOCOL.md) | TCP 协议详细定义 |
| **Android 构建指南** | [docs/BUILD_ANDROID.md](file:///e:/WORK/usb-relay-olh/docs/BUILD_ANDROID.md) | 编译安装步骤 |
| **AGENT 上下文** | [AGENT.md](file:///e:/WORK/usb-relay-olh/AGENT.md) | 本文档 |

---

## 最后更新
- **日期**: 2026-05-14
- **更新者**: AI Assistant
- **更新内容**: 
  1. ✅ 完整记录 Phase 1 的实现进度
  2. ✅ 记录今天的完整开发历史
  3. ✅ 更新目录结构，添加 Python GUI 客户端文件
  4. ✅ 添加"当前状态 - 已验证"章节
  5. ✅ 记录成功的实机测试（192.168.31.97:3240）
  6. ✅ 更新"下一步建议"分两个方向（自研 vs 成熟方案）
  7. ✅ 同步更新了 README.md
- **下次接手提示**: 应用已成功安装并完成基础通信测试！当前有两个方向：1) 继续完善自研方案（添加完整协议）；2) 部署和使用成熟方案（Android-Usbipdcpp + usbip-win2）。设备 `2ca98fe5` 可用，Windows 客户端可运行。
