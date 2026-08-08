# Deekseep LSPosed

一个面向官方 DeepSeek Android App 的独立 LSPosed/Xposed 模块，提供账号、聊天、图片、界面和本地 API 增强工具。

[English](README.md) | 简体中文

[![最新版本](https://img.shields.io/github/v/release/lllucccian/Deekseep?display_name=tag&sort=semver)](https://github.com/lllucccian/Deekseep/releases/latest)
[![GitHub 下载量](https://img.shields.io/github/downloads/lllucccian/Deekseep/total?label=Downloads)](https://github.com/lllucccian/Deekseep/releases)
[![Android 7.0+](https://img.shields.io/badge/Android-7.0%2B-3ddc84)](#环境要求)
[![Xposed API 82–102](https://img.shields.io/badge/Xposed_API-82%20%7C%20100%20%7C%20101%20%7C%20102-2f6feb)](#环境要求)
[![License: GPL v3](https://img.shields.io/badge/License-GPLv3-blue.svg)](LICENSE)

> [!NOTE]
> Deekseep 是独立增强模块。请先确认安装包与 DeepSeek 版本匹配；使用聊天、账号或实验性工具前，建议备份重要数据。

## 兼容情况速览

> [!TIP]
> Deekseep LSPosed 1.7.3 只有两个 APK：国内版和 Google Play 版各一个，二者均为多 API 通用版。

- 中国大陆官方版：支持 DeepSeek 2.2.2（`versionCode 233`）和 2.3.0（`versionCode 237`）。
- Google Play 版：只支持 DeepSeek 2.2.2（`versionCode 236`），暂不兼容最新版 Google Play DeepSeek。
- Android：7.0 及以上（API 24+）。
- Xposed 接口：同一个通用 APK 兼容 API 82 / 100 / 101 / 102。
- 模块作用域：只勾选 `com.deepseek.chat`。

## 推荐下载

### [中国大陆版——下载 Deekseep 1.7.3 多 API 通用版](https://github.com/lllucccian/Deekseep/releases/download/v1.7.3/deekseep-mainland-universal-api82-100-101-102-v1.7.3.apk)

适用于中国大陆 DeepSeek 2.2.2（`233`）和 2.3.0（`237`）。

### [Google Play 版——下载 Deekseep 1.7.3 多 API 通用版](https://github.com/lllucccian/Deekseep/releases/download/v1.7.3/deekseep-google-play-universal-api82-100-101-102-v1.7.3.apk)

只适用于 Google Play DeepSeek 2.2.2（`236`）。两个渠道的 APK 不能互换；专用 API 102、Legacy、测试版和诊断版均不再是当前发布选项。

## 项目截图

<p align="center">
  <img src="docs/images/Screenshot_2026-07-22-22-49-55-25_7614e48627b7380b17b386d382d1b2ef.jpg" alt="Deekseep LSPosed 项目截图" width="360">
</p>

截图展示了英文版模块设置中的提示词注入、响应替换保护、聊天多选和原生登录入口恢复开关。

<details>
<summary>查看更多项目截图</summary>

| 数据工具、语言与模块信息 | 实验性功能及使用提示 |
|---|---|
| <img src="docs/images/data-tools-preview.jpg" alt="Deekseep LSPosed 数据工具与模块信息" width="320"> | <img src="docs/images/experimental-features-preview.jpg" alt="Deekseep LSPosed 实验性功能页面" width="320"> |

</details>

## 项目介绍

Deekseep LSPosed 通过兼容的 LSPosed/Xposed 环境运行在官方 DeepSeek Android App 进程中，为本地会话、账号、提示词、界面、图片流程和开发者 API 增加可选工具。

本项目是独立第三方项目，不属于 DeepSeek 官方，也未获得 DeepSeek 的隶属、认可或支持。

## 功能介绍

### 聊天工具

- 导入系统提示词，并在不改动可见输入框的情况下写入发送请求。
- 编辑本地会话标题、用户消息、模型回复、思考内容、思考时间和消息图片；支持新建本地会话，并搜索问题、回复和思考文本。
- 支持导入图片作为聊天背景或贴纸；背景可连续缩放或输入精确倍率，并支持横向/纵向取景、旋转、不透明度、显示范围、裁剪/完整显示/拉伸，以及聊天、侧栏和设置页绑定。贴纸可拖动并调整大小、旋转、层级和不透明度；一键离线抠图可以保存透明贴纸，效果不理想时还可手动擦除。
- 将聊天导出为 Markdown，查看本地统计，手动或按保留数量自动备份数据库，并可选启用聊天批量选择与删除。
- 在已知的客户端 `CONTENT_FILTER` 替换事件发生时保留设备已经收到的文本；无法恢复服务器从未下发的内容。

### 账号工具

- 保存多个账号槽位，明确执行添加、切换、删除、选择性导入或导出，并在保存导入凭证前进行校验。
- 可选恢复中国大陆登录页中的 DeepSeek 原生 Google 登录入口，或恢复海外登录页中的原生微信和短信入口；账号、地区和风控结果仍由服务器决定。

### 图片工具

- 在编辑本地消息时复用或替换图片，并保存用于后续显示的私有持久副本。
- 实验性地通过临时视觉会话处理中继专家模式图片请求，并在本地历史中保存图片元数据；是否可用仍取决于 DeepSeek 服务端。

### 开发者与 API 工具

- 可选启动带独立 Gateway Key 的本机/可信局域网服务，通过 DeepSeek 原生传输提供 OpenAI Chat Completions/Responses 或 Anthropic Messages 兼容接口。
- 支持流式输出、工具结果续写、Codex 和 Claude Code 工具循环、深度思考参数、原生联网搜索与实时请求诊断；高级设置可申请 Pinggy 临时公网网址、固定监听端口，或用自有 Cloudflare Tunnel 令牌连接已配置域名。本地 API 位于可选的“实验性功能”页中，默认关闭。

### 界面与兼容增强

- 在 DeepSeek 设置中显示 Deekseep LSPosed 入口，支持中英文自动检测和手动选择。
- 为匹配的国内版或 Google Play 宿主使用同一个 API 82 / 100 / 101 / 102 通用接口包。
- 改进目标进程激活校验，解决模块已启用却长期显示“待验证”的问题。
- 更新了一些彩蛋。

详细行为与限制见[功能说明](docs/FEATURES.md)和[实验性功能说明](docs/EXPERIMENTAL_FEATURES.md)。

## 环境要求

- Android 7.0 / API 24 或更高版本。
- 安装上方兼容列表中精确匹配渠道和版本的官方 DeepSeek Android App。
- 能加载模块的 LSPosed/Xposed 环境，以及该环境本身所要求的 Root 或框架配置。
- 使用 API 82、100、101 或 102 的兼容 Xposed 环境。
- LSPosed/Xposed 作用域设置为 `com.deepseek.chat`。
- 使用数据库、账号、删除或实验性工具前，先备份重要聊天记录。

本仓库不提供官方 DeepSeek APK、Root 方案或 LSPosed/Xposed 安装器。

## 安装步骤

1. 在 Android 应用信息中确认 DeepSeek 渠道和 `versionCode`：国内版 `233`/`237`，或 Google Play `236`。
2. 备份重要的 DeepSeek 聊天记录和本地文件。
3. 只下载一个匹配渠道的 1.7.3 多 API 通用 APK：国内版用于 `233`/`237`，Google Play 版用于 `236`。
4. 安装模块 APK，并在 LSPosed/Xposed 管理器中启用。
5. 作用域只勾选 `com.deepseek.chat`，不要勾选模块自身应用。
6. 强制停止 DeepSeek 后重新打开。通常不需要重启整台设备；只有框架在目标 App 重启后仍未重新加载模块时再重启设备。
7. 阅读简短的首次使用说明并点击“我知道了”，然后进入 DeepSeek 设置，打开 Deekseep LSPosed 注入的 Deekseep 入口。

如果 Android 因旧版或不同签名而拒绝覆盖安装，请先停用并只卸载旧模块 APK，再安装 1.7.3；这不会卸载 DeepSeek。更多细节见[安装指南](docs/INSTALLATION.md)。

## 发布内容

上方两个按钮就是仅有的两个可安装 APK。[1.7.3 Release](https://github.com/lllucccian/Deekseep/releases/tag/v1.7.3)
中的另外两个文件是源码快照，`SHA256SUMS.txt` 是校验文件，它们都不是额外的 APK
版本。当前版本已经舍弃专用 API 102 和旧式单接口 APK；不要为同一个 DeepSeek
进程同时启用多个 Deekseep 版本。

## 兼容性表格

| App 渠道 | App 版本 | versionCode | 状态 | 说明 |
|---|---:|---:|---|---|
| 中国大陆官方版 | 2.2.2 | 233 | ✅ 支持 | 使用国内版多 API 通用 APK。 |
| 中国大陆官方版 | 2.3.0 | 237 | ✅ 支持 | 使用国内版多 API 通用 APK。 |
| Google Play 版 | 2.2.2 | 236 | ✅ 支持 | 使用 Google Play 多 API 通用 APK。 |
| 更新的 Google Play 版 | 最新版 | 其他 | ❌ 不支持 | 等待明确完成映射的 GP 版本。 |
| 更旧或其他 DeepSeek 构建 | 待确认 | Unknown | 🧪 未测试 | Hook 依赖具体构建的混淆符号，不能假定兼容。 |

## 常见问题

- Deekseep LSPosed 入口不显示：核对 App 渠道与版本，安装对应 APK，只启用一个模块版本，作用域勾选 `com.deepseek.chat`，强制停止 DeepSeek 后重新进入设置首页。
- 模块已启用但 Hook 不生效：确认渠道 APK 和 DeepSeek 版本匹配，再检查启动页激活状态与作用域；不要把模块自身加入作用域，同时停用可能修改同一界面或请求路径的其他模块。
- DeepSeek 版本不兼容：先停用 Deekseep LSPosed，确认原 App 能正常运行。只使用文档明确支持的 versionCode；App 更新后可能需要重新映射。
- Xposed API 不兼容：安装 1.7.3 多 API 通用包，不要继续使用已经舍弃的专用 API 102 或 Legacy 包。
- Google Play 版无法使用：确认 DeepSeek 恰好是 2.2.2（`236`），并使用文件名包含 `google-play-universal` 的 APK；不能用国内版 APK 替代。
- DeepSeek 更新后功能失效：停用模块并重启 DeepSeek，然后报告新的渠道、`versionName` 和 `versionCode`。本项目不自动保证未来版本兼容。
- 多账号功能异常：先备份当前账号数据，每次只测试一次添加或导入，并在验证成功前保留原活动账号。不要公开上传导出的账号 JSON。
- 图片功能异常：确认系统图片选择器能读取文件，并先测试单张图片。专家图片中继属于实验功能，可能受服务器权限、模型路由、PoW 或宿主内部变化影响。
- 收集日志：只复现一次，截取模块诊断中首次错误附近的少量行。必须删除 Token、Cookie、Authorization、账号信息、邮箱、手机号、设备标识、私有服务器地址、提示词、回复、文件链接和其他隐私信息。
- 提交 Issue：先搜索已有问题，再通过 [Bug 报告](https://github.com/lllucccian/Deekseep/issues/new?template=bug_report.yml)或[兼容性报告](https://github.com/lllucccian/Deekseep/issues/new?template=compatibility_report.yml)填写精确版本与最小脱敏日志。

更多排查方法见[故障排查文档](docs/TROUBLESHOOTING.md)。

## 使用前提示

- 按 DeepSeek 渠道和 `versionCode` 选择匹配的 APK。
- 编辑、删除会话或切换账号前，建议先备份重要聊天。
- 账号导出、API Key 和诊断日志请勿公开分享。

更多信息见简明的[项目说明](DISCLAIMER.md)。实验性功能只显示一次使用提示，并且在你主动开启前保持关闭。

## 开发计划

仓库中的本地 API 实现计划目前记录了以下状态：

- 已完成：OpenAI 与 Anthropic 双格式、多 API 通用发布、国内版 2.2.2/2.3.0 兼容、Google Play 2.2.2 精确映射，以及带门槛的实验性功能页。
- 计划中：socket 到宿主 Flow 的明确取消确认、API 图片输入、Responses 状态持久化和幂等键、脱敏诊断包，以及更广的 Anthropic/Claude Code 回归测试。
- 未排期：其他 DeepSeek 版本适配。每次 App 更新都需要重新确认兼容性，并可能需要新的符号映射。

详情见[本地 API 实现状态与计划](docs/LOCAL_DEEPSEEK_API_GATEWAY_PLAN.md)。计划项在真正实现并发布前不属于当前功能。

## 贡献说明

欢迎贡献新版本兼容测试、Google Play 映射、聚焦的 Hook 修复、文档改进、Bug 报告、翻译、界面截图和安装测试。

参与前请阅读 [CONTRIBUTING.md](CONTRIBUTING.md)，搜索现有 [Issues](https://github.com/lllucccian/Deekseep/issues)，并写明 DeepSeek 渠道、App 版本、versionCode、Android 版本及 LSPosed/Xposed 环境。聚焦的修改可以通过 [Pull Requests](https://github.com/lllucccian/Deekseep/pulls)提交。

## 项目说明

Deekseep LSPosed 是独立第三方项目，不属于 DeepSeek 官方。产品名称及相关商标归其合法权利人所有。兼容性、数据和隐私说明见简明的[项目说明](DISCLAIMER.md)。

## 许可证

项目自有源码和文档采用 [GNU GPL-3.0-only](LICENSE)。第三方组件及声明见 [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)。

如果 Deekseep LSPosed 对你有帮助，可以给仓库点一个 ⭐，让更多 DeepSeek 和 LSPosed 用户发现它。
