# Deekseep LSPosed

Deekseep 是面向官方 DeepSeek Android App 的独立 LSPosed/Xposed 模块。它运行在
DeepSeek 进程中，提供可选的模块设置入口和兼容层。本项目不是 DeepSeek 官方项目。

[English](README.md) | [更新日志 (Changelog)](CHANGELOG.md)

> **QQ 交流群：1106465300** — 欢迎进群反馈问题、交流使用心得！
>
> **Telegram 交流群：** [@Deekseepapp](https://t.me/Deekseepapp)

---

### 关于无限期停更与致开源社区的一封信

我不知道从什么时候开始，开源圈子已经变成了这副令人心寒的模样。

开发这个项目的初衷，本是为了让广大使用 DeepSeek 的 Android 用户能有更好的体验与更多的自由度。但近期的种种乱象，真的让我感到极度的失望，甚至是失望透顶：
- **无视开源协议，肆意抄袭搬运**：多个衍生项目直接照抄、大段搬运本项目的源码，却完全拒不遵守 GPL 开源协议，不开源、不标明出处，甚至将代码打包后当成自己的私有成果发布；
- **招摇撞骗，冒充原作者**：网络社区和群聊中冒出了各种假冒原作者身份的人，利用信息差行骗；
- **毫无底线的滥用与恶意倒卖**：原本免费公开的心血，被某些人拿去大肆倒卖、牟取暴利，破坏了正常的用户环境，引发了严重的滥用与风控问题。

开发者的热情与付出，不该成为这些投机分子谋取私利的工具。面对这样的恶劣环境，我已经耗尽了所有的心力与期待。

**因此，我做出以下决定：**
1. **本项目开源版即日起无限期停更**。后续我也**不打算再更新开源版**。
2. **闭源版不再在 GitHub 仓库提供或更新**。因近期出现了大量滥用情况，如需获取闭源版本，请前往官方 **Telegram 交流群**（[@Deekseepapp](https://t.me/Deekseepapp)），切勿在 GitHub 提出索取要求或在第三方倒卖渠道购买。

感谢所有曾经给予鼓励、陪伴和真心支持本项目的每一位朋友。江湖路远，各位珍重。

---

## 当前版本 (v1.8)

- [源码构建说明](docs/BUILDING.md)
- [完整英文更新日志 (Changelog)](CHANGELOG.md)

> **关于安装包下载**：本次发布在 GitHub Releases 中提供 Open 开源版的预编译正式包（`Open.apk`）、Debug 包（`Open-debug.apk`）、哈希校验文件（`SHA256.txt`）以及源码包；闭源版本不再在公共平台分发，仅在官方 Telegram 交流群中同步。

本版本已适配国内版 DeepSeek **2.4.1**（versionCode 257）以及 2.3.6、2.3.4 等版本。

## 环境要求

- Android 7.0 或更高版本（API 24+）。
- 官方 DeepSeek 包名 `com.deepseek.chat`。
- 国内版 DeepSeek 2.4.1（versionCode 257）、DeepSeek 2.3.6（249）、DeepSeek 2.3.4（245/246）、DeepSeek 2.3.0（237）或 DeepSeek 2.2.x。
- 能加载传统 Xposed 入口的 LSPosed/Xposed，已覆盖 API 82～102。
- Root，或你的 LSPosed/Xposed 环境所要求的权限。

## 安装前准备

1. 在 Android 应用信息中确认 DeepSeek 包名、渠道和 versionCode。
2. 备份重要聊天记录及本地文件。
3. 先安装并配置 LSPosed/Xposed，再准备启用模块作用域。
4. 如需后台请求或通知，建议取消 DeepSeek 的电池优化限制。

## 源码与构建

- [源码构建说明](docs/BUILDING.md)
- [提交可复现问题](https://github.com/lllucccian/Deekseep/issues)

本仓库包含 1.8 Open 版的完整开源源码。除本地 API 及闭源保护组件外，各项主要功能均保持同步。

许可证：[GPL-3.0-only](LICENSE)。

---

**赞助作者：** [爱发电](https://www.afdian.com/a/lllucccian)

**用户交流：** QQ 群 1106465300

**Telegram：** [@Deekseepapp](https://t.me/Deekseepapp)
