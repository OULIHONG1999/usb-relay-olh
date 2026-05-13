# Windows 客户端 - usbip-win2 介绍

## 项目信息
- **项目**: usbip-win2
- **位置**: usbip-win2/
- **来源**: https://github.com/vadimgrn/usbip-win2
- **语言**: C++ (驱动) + C++ (用户空间)
- **许可证**: BSD-2-Clause

## 特性

| 特性 | 说明 |
|------|------|
| WHLK 认证 | 驱动通过 Windows Hardware Lab Kit 认证 |
| 完整协议 | USB/IP v1.1.1 协议完全兼容 |
| 零拷贝传输 | 零拷贝机制优化性能 |
| 安全 | 仅由受信任用户运行驱动 |

## 安装包下载

最新版本: **v.0.9.7.7**
- 下载地址: https://github.com/vadimgrn/usbip-win2/releases/tag/v.0.9.7.7
- 文件名: USBip-Setup-v0.9.7.7.exe

## 安装前准备

### 1. 创建系统还原点 (重要！)
搜索 "创建还原点" 并创建一个还原点

### 2. 检查驱动签名要求
如果驱动没有 WHQL 签名，需要启用测试签名模式:

```powershell
# 管理员权限运行
bcdedit.exe /set testsigning on
```

**重启电脑**后生效

### 3. 运行安装程序
运行 `USBip-Setup-v0.9.7.7.exe`

⚠️ **注意**: 安装过程中所有 USB 设备会暂时重启

## 使用方法

### 基本命令

```powershell
# 列出远程 USB 设备
usbip.exe list -r <远程IP>

# 附加远程 USB 设备
usbip.exe attach -r <远程IP> -b <总线ID>

# 列出本地附加的设备
usbip.exe port

# 分离设备
usbip.exe detach -p <端口号>
```

## 项目结构

```
usbip-win2/
├── include/
│   └── usbip/          # 协议头文件
│       ├── consts.h    # 常量定义
│       ├── proto.h     # 协议结构
│       └── types.h     # 类型定义
├── userspace/          # 用户空间库和工具
│   ├── usbip/
│   └── src/
├── drivers/            # 驱动代码
└── README.md
```

## 与现有 relay-client 的对比

| 特性 | relay-client | usbip-win2 |
|------|-------------|------------|
| 协议 | 自定义协议 | 标准 USB/IP |
| 驱动 | 无 | 有 (WHLK 认证) |
| 兼容性 | 仅与自建服务器 | 所有 USB/IP 服务器 |
| 完整度 | 原型 | 生产级 |
