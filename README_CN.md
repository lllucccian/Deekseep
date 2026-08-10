# Deekseep LSPosed

Deekseep 是面向官方 DeepSeek Android App 的独立 LSPosed/Xposed 模块。它运行在
DeepSeek 进程中，提供可选的模块设置入口和兼容层。本项目不是 DeepSeek 官方项目。

[English](README.md)

## 当前稳定版

[下载 Deekseep 1.7.4](https://github.com/lllucccian/Deekseep/releases/download/v1.7.4/Deekseep.apk)

当前发布的是一个通用 APK，同时适配支持的国内版和 Google Play 版。强烈建议使用
DeepSeek 2.3.4；2.2.0 和 2.3.0 仍可使用但部分功能可能受限；不支持 2.3.1～2.3.3。

## 环境要求

- Android 7.0 或更高版本（API 24+）。
- 官方 DeepSeek 包名 `com.deepseek.chat`。
- DeepSeek 2.3.4（versionCode 245/246）、2.3.0（237）或 2.2.0。
- 能加载传统 Xposed 入口的 LSPosed/Xposed，已覆盖 API 82～102。
- Root，或你的 LSPosed/Xposed 环境所要求的权限。

## 安装前准备

1. 在 Android 应用信息中确认 DeepSeek 包名、渠道和 versionCode。
2. 备份重要聊天记录及本地文件。
3. 先安装并配置 LSPosed/Xposed，再准备启用模块作用域。
4. 如需后台请求或通知，建议取消 DeepSeek 的电池优化限制。

## 安装步骤

1. 下载上方 APK 并安装。
2. 在 LSPosed/Xposed 中启用 **Deekseep**。
3. 作用域只勾选 `com.deepseek.chat`，不要添加无关应用。
4. 强制停止后重新打开 DeepSeek；只有框架没有重新加载目标进程时才需要重启设备。

本模块不包含官方 DeepSeek APK、Root 方案或 LSPosed/Xposed 安装器。实验性设置可能
影响宿主稳定性；如遇异常，请先关闭模块并使用备份恢复。

## 源码与发布

- [源码构建说明](docs/BUILDING.md)
- [版本发布说明](https://github.com/lllucccian/Deekseep/releases)
- [提交可复现问题](https://github.com/lllucccian/Deekseep/issues)
- [赞助开发](https://www.afdian.com/a/lllucccian)

许可证：[GPL-3.0-only](LICENSE)。

---

**赞助作者以加速开发：** [爱发电](https://www.afdian.com/a/lllucccian)
