# Deekseep LSPosed

一个面向官方 DeepSeek Android App 的独立 LSPosed/Xposed 模块，提供账号、聊天、图片、界面和本地 API 增强工具。

[English](README.md) | 简体中文

[![最新版本](https://img.shields.io/github/v/release/lllucccian/Deekseep?display_name=tag&sort=semver)](https://github.com/lllucccian/Deekseep/releases/latest)
[![GitHub 下载量](https://img.shields.io/github/downloads/lllucccian/Deekseep/total?label=Downloads)](https://github.com/lllucccian/Deekseep/releases)
[![Android 7.0+](https://img.shields.io/badge/Android-7.0%2B-3ddc84)](#环境要求)
[![Universal Xposed](https://img.shields.io/badge/Xposed-universal-2f6feb)](#环境要求)
[![License: GPL v3](https://img.shields.io/badge/License-GPLv3-blue.svg)](LICENSE)

> [!NOTE]
> Deekseep 是独立增强模块。请先确认安装包与 DeepSeek 版本匹配；使用聊天、账号或实验性工具前，建议备份重要数据。

## 1.7.4 发布说明

1.7.4 重构模块与设置界面，支持搜索，并将功能分为“聊天”“账号与隐私”“界面美化”“调试”和“工程”；同时优化开关 UI、对齐执行按钮，并优化设置入口（功能名右侧的小齿轮按钮）。

主要功能：

• 基础 Agent 工具（具体工具请在应用内查看/下载）。
• 服务器暂停长思考时自动继续生成。
• “回复完成通知”开关，位于“自动继续生成”下方。
• 本地禁言和自定义主页欢迎语。
• 自定义 DeepSeek 头像（需先在“灰度功能管理器”开启“显示助手头像”）。
• 鲸鱼旋转和深海文字波纹。
• 屏幕 Hook 日志、崩溃记录与测试。
• 自定义请求、禁用热更新、自动清理缓存和进程管理器。
• 本地 API 前台心跳保活及长上下文自动转文件上传。
• 仍建议在电池优化中不要限制 DeepSeek。

播放音乐的 Agent 工具要求安装最新 **20.7 或更高版本 QQ 音乐**；授予 Root 后可在后台自动播放音乐。原生设置注入属于实验性功能，可能导致宿主闪退。“禁用数据用于优化体验”会主动关闭并阻止宿主再次开启该选项。

本版将国内版与 Google Play 版融合为一个运行时通用 APK，强烈建议搭配 DeepSeek **2.3.4**（versionCode 245/246）。2.2.0 和 2.3.0 仍可使用但部分功能可能缺失；**不支持 2.3.1～2.3.3**。旧版本或个别环境可能仍有功能异常，欢迎提交可复现问题以便后续统一修复。

支持开发：[爱发电](https://www.afdian.com/a/lllucccian)。

## 兼容情况速览

> [!TIP]
> Deekseep LSPosed 1.7.4 只发布一个通用安装包，运行时会在支持的宿主符号表之间自动选择。

- 国内版或 Google Play：DeepSeek 2.2.0、2.3.0（`versionCode 237`）或 2.3.4（`versionCode 245/246`），支持融合通用安装包。
- Android：7.0 及以上（API 24+）。
- 框架要求：能加载传统 Xposed 入口的 LSPosed/Xposed 环境；API 82～102 已纳入逐版本回归。
- 模块作用域：只勾选 `com.deepseek.chat`。

## 推荐下载

### [下载 Deekseep LSPosed 1.7.4](https://github.com/lllucccian/Deekseep/releases/download/v1.7.4/Deekseep.apk)

这是唯一维护中的模块安装包，运行时会自动识别国内版或 Google Play 宿主并选择对应映射。

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
- 当前受支持的源码构建均可导入图片作为聊天背景或贴纸；背景支持裁剪取景、旋转、不透明度、可选景深，以及统一或分界面位移。动态效果采用先快后慢的缓出曲线：打开侧栏时随主界面向右、进入设置时向左，并在关闭或返回时平滑复位；高级选项可绑定聊天、侧栏或设置界面。贴纸会保留在聊天与设置页，并可拖动及调整大小、旋转、层级和不透明度。
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
- 支持流式输出、工具结果续写、Codex 和 Claude Code 工具循环、深度思考参数、原生联网搜索与实时请求诊断；高级设置可固定监听端口，并用自有 Cloudflare Tunnel 令牌连接一个或多个已配置域名。本地 API 位于可选的“实验性功能”页中，默认关闭。

### 界面与兼容增强

- 在 DeepSeek 设置中显示 Deekseep LSPosed 入口，支持中英文自动检测和手动选择。
- 使用通用 Xposed 兼容版，运行时自动选择已支持的宿主版本映射。

详细行为与限制见[功能说明](docs/FEATURES.md)和[实验性功能说明](docs/EXPERIMENTAL_FEATURES.md)。

## 环境要求

- Android 7.0 / API 24 或更高版本。
- 安装上方兼容列表中精确匹配渠道和版本的官方 DeepSeek Android App。
- 能加载模块的 LSPosed/Xposed 环境，以及该环境本身所要求的 Root 或框架配置。
- 需要能够加载传统 Xposed 入口的兼容版 LSPosed/Xposed 环境；已验证 API 82～102。
- LSPosed/Xposed 作用域设置为 `com.deepseek.chat`。
- 使用数据库、账号、删除或实验性工具前，先备份重要聊天记录。

本仓库不提供官方 DeepSeek APK、Root 方案或 LSPosed/Xposed 安装器。

## 安装步骤

1. 在 Android 应用信息中确认 DeepSeek 渠道和版本号（2.2.0、2.3.0 的 `versionCode 237`，或 2.3.4 的 `versionCode 245/246`）。
2. 备份重要的 DeepSeek 聊天记录和本地文件。
3. 下载通用版 Deekseep APK，并在兼容的 Xposed 框架中启用。
4. 安装模块 APK，并在 LSPosed/Xposed 管理器中启用。
5. 作用域只勾选 `com.deepseek.chat`；现代版不需要勾选模块自身应用。
6. 强制停止 DeepSeek 后重新打开。通常不需要重启整台设备；只有框架在目标 App 重启后仍未重新加载模块时再重启设备。
7. 阅读简短的首次使用说明并点击“我知道了”，然后进入 DeepSeek 设置，打开 Deekseep LSPosed 注入的 Deekseep 入口。

通用版包名为 `com.dsmod.probe`。更多细节见[安装指南](docs/INSTALLATION.md)。

## 兼容性表格

| App 渠道 | App 版本 | versionCode | 状态 | 说明 |
|---|---:|---:|---|---|
| 国内版或 Google Play | 2.2.0 | 不固定 | ✅ 支持但可能缺少功能 | 使用融合运行时的 2.2.x 映射。 |
| 国内版或 Google Play | 2.3.0 | 237 | ✅ 支持但可能缺少功能 | 运行时选择 2.3.0 映射。 |
| 国内版或 Google Play | 2.3.4 | 245/246 | ✅ 强烈推荐 | 一个 APK 在运行时选择渠道映射。 |
| 任意渠道 | 2.3.1～2.3.3 | 不固定 | ❌ 不支持 | 请升级到 2.3.4。 |

## 常见问题

- Deekseep LSPosed 入口不显示：核对 App 渠道与版本，安装对应 APK，只启用一个模块版本，作用域勾选 `com.deepseek.chat`，强制停止 DeepSeek 后重新进入设置首页。
- 模块已启用但 Hook 不生效：检查启动状态，只启用一个模块版本，不要把模块自身加入作用域；同时停用可能修改同一界面或请求路径的其他模块。
- DeepSeek 版本不兼容：先停用 Deekseep LSPosed，确认原 App 能正常运行。只使用文档明确支持的 versionCode；App 更新后可能需要重新映射。
- 框架入口不兼容：使用能够加载传统 Xposed 通用入口的兼容版 LSPosed/Xposed 环境；支持矩阵为 API 82～102。
- 宿主版本不支持：请升级到 DeepSeek 2.3.4；2.3.1～2.3.3 明确不支持。
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

- 已完成：OpenAI 与 Anthropic 双格式、国内版两个稳定接口、Google Play 2.2.2 精确映射，以及带门槛的实验性功能页。
- 计划中：socket 到宿主 Flow 的明确取消确认、API 图片输入、Responses 状态持久化和幂等键、脱敏诊断包，以及更广的 Anthropic/Claude Code 回归测试。
- 未排期：其他 DeepSeek 版本适配。每次 App 更新都需要重新确认兼容性，并可能需要新的符号映射。

详情见[本地 API 实现状态与计划](docs/LOCAL_DEEPSEEK_API_GATEWAY_PLAN.md)。计划项在真正实现并发布前不属于当前功能。

## 贡献说明

欢迎贡献新版本兼容测试、Google Play 映射、聚焦的 Hook 修复、文档改进、Bug 报告、翻译、界面截图和安装测试。

参与前请阅读 [CONTRIBUTING.md](CONTRIBUTING.md)，搜索现有 [Issues](https://github.com/lllucccian/Deekseep/issues)，并写明 DeepSeek 渠道、App 版本、versionCode、Android 版本及 LSPosed/Xposed 环境。聚焦的修改可以通过 [Pull Requests](https://github.com/lllucccian/Deekseep/pulls)提交。

## 项目说明

Deekseep LSPosed 是独立第三方项目，不属于 DeepSeek 官方。产品名称及相关商标归其合法权利人所有。兼容性、数据和隐私说明见简明的[项目说明](DISCLAIMER.md)。

## 致谢

设置页的信息层级和交互思路参考了 WeKit，仅作为 UI 参考；没有复制
WeKit 的源码或资源。第三方库及许可证见
[THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)。

## 许可证

项目自有源码和文档采用 [GNU GPL-3.0-only](LICENSE)。第三方组件及声明见 [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)。

如果 Deekseep LSPosed 对你有帮助，可以给仓库点一个 ⭐，或在[爱发电赞助开发](https://www.afdian.com/a/lllucccian)，让项目更快迭代。
