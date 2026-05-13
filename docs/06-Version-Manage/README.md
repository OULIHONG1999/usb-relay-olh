# 版本号管理 - 快速参考

## 🚀 常用命令

### 更新版本并构建
```batch
# 项目根目录运行（自动更新patch版本）
build_with_version.bat
```

### 手动更新版本
```powershell
cd relay-host\USBRelay

# 更新patch (1.0.2 → 1.0.3)
.\update_version.ps1 patch

# 更新minor (1.0.2 → 1.1.0)
.\update_version.ps1 minor

# 更新major (1.0.2 → 2.0.0)
.\update_version.ps1 major
```

### 构建应用
```powershell
cd relay-host\USBRelay
.\gradlew.bat assembleDebug      # Debug版本
.\gradlew.bat assembleRelease    # Release版本
```

### 安装到设备
```bash
adb install -r app\build\outputs\apk\debug\app-debug.apk
```

## 📍 版本号显示位置

App主界面 → Server Control卡片 → Version字段

```
Status: Running
IP: 192.168.31.97
Port: 3240
Version: 1.0.2  ← 这里
```

## 📄 配置文件

**位置**: `relay-host/USBRelay/version.properties`

```properties
VERSION_MAJOR=1
VERSION_MINOR=0
VERSION_PATCH=2
```

## 🔢 版本号规则

- **MAJOR**: 重大更新（不兼容变更）
- **MINOR**: 新功能（向后兼容）
- **PATCH**: Bug修复

## 💡 提示

- 每次构建前建议更新版本号
- Patch版本用于日常开发
- Minor版本用于功能发布
- Major版本用于架构重构
