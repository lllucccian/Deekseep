package com.dsmod.probe;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** English copy catalog used by the manually drawn module UI. */
final class UiLanguageCatalog {
    private static final Map<String, String> EXACT = new HashMap<>();
    private static final List<Entry> FRAGMENTS = new ArrayList<>();

    static {
        // The complete catalog is grouped below by screen. Longer fragments are applied first so
        // formatted module status strings can safely reuse the same translations.
        add("Deekseep", "Deekseep");
        add("聊天", "Chat");
        add("账号与隐私", "Accounts & privacy");
        add("外观", "Appearance");
        add("界面美化", "Interface customization");
        add("外观设置", "Appearance settings");
        add("自定义 DeepSeek 头像", "Custom DeepSeek avatar");
        add("当前使用 DeepSeek 默认头像", "Currently using the default DeepSeek avatar");
        add("已使用自定义头像", "Custom avatar in use");
        add("需要在灰度功能管理里面开启“显示助手头像”，否则聊天页不会显示自定义头像。",
                "Enable “Show assistant avatar” in Grey feature management first, or the custom avatar will not appear in chats.");
        add("选择后返回聊天或重新进入会话即可生效。",
                "Return to the chat or reopen the conversation after selecting an image to apply it.");
        add("选择图片", "Choose image");
        add("DeepSeek 头像已更新", "DeepSeek avatar updated");
        add("已恢复 DeepSeek 默认头像", "Restored the default DeepSeek avatar");
        add("恢复默认头像失败", "Could not restore the default avatar");
        add("头像保存失败", "Could not save the avatar");
        add("头像设置保存失败", "Could not save the avatar setting");
        add("头像处理失败", "Could not process the avatar");
        add("无法解码所选图片", "Could not decode the selected image");
        add("助手头像 灰度 显示助手头像", "assistant avatar grey feature show assistant avatar");
        add("鲸鱼图标动效", "Whale icon motion");
        add("让首页鲸鱼图标缓慢旋转", "Slowly rotate the whale icon on the chat home screen");
        add("首页鲸鱼持续旋转", "Continuously rotate the whale icon on the home screen");
        add("鲸鱼旋转速度", "Whale rotation speed");
        add("0.1× 慢速                                      1.0× 快速",
                "0.1× Slow                                      1.0× Fast");
        add("深海文字波纹", "Deep-sea text wave");
        add("右上向左下斜落 · 宽波峰约占字宽 1/3",
                "Falls diagonally from upper right to lower left · broad crest spans about one third of a glyph");
        add("文字波纹速度", "Text-wave speed");
        add("0.2× 舒缓                                      1.5× 灵动",
                "0.2× Calm                                      1.5× Lively");
        add("文字波纹设置保存失败", "Could not save the text-wave setting");
        add("文字波纹速度保存失败", "Could not save the text-wave speed");
        add("主页欢迎语", "Home greeting");
        add("当前：跟随 DeepSeek 默认文案", "Current: follow DeepSeek's default copy");
        add("当前：", "Current: ");
        add("自定义主页欢迎语", "Custom home greeting");
        add("例如：今天想我了没？", "For example: Did you miss me today?");
        add("保存后返回主页即可看到新文案；留空会恢复 DeepSeek 默认文案。",
                "Return to the home screen after saving to see the new copy. Leave it blank to restore DeepSeek's default.");
        add("恢复默认", "Restore default");
        add("主页欢迎语保存失败", "Could not save the home greeting");
        add("主页欢迎语已保存，返回主页后生效",
                "Home greeting saved; return home to apply");
        add("原生设置入口 · 实验性", "Native settings entry · Experimental");
        add("原生设置入口", "Native settings entry");
        add("开启：置于 DeepSeek 设置顶部的独立“插件”分组；关闭：回退为悬浮入口。",
                "On: place it in a dedicated Plugins section at the top of DeepSeek settings. Off: fall back to the floating entry.");
        add("宿主版本变化时可能导致应用闪退，切换后重新进入设置生效。",
                "Host updates may cause crashes; reopen settings after switching.");
        add("开启：置于 DeepSeek 设置顶部的独立“插件”分组；关闭：回退为悬浮入口。宿主版本变化时可能导致应用闪退，切换后重新进入设置生效。",
                "On: place it in a dedicated Plugins section at the top of DeepSeek settings. Off: fall back to the floating entry. Host updates may cause crashes; reopen settings after switching.");
        add("入口模式已保存，重新进入 DeepSeek 设置后生效",
                "Entry mode saved; reopen DeepSeek settings to apply");
        add("入口模式保存失败", "Could not save the entry mode");
        add("插件", "Plugins");
        add("通过爱发电赞助", "Sponsor via Afdian");
        add("通过微信赞助", "Sponsor via WeChat");
        add("微信赞赏码", "WeChat donation code");
        add("调试", "Diagnostics");
        add("工程", "Engineering");
        add("Hook 日志", "Hook logs");
        add("透明显示 Hook 日志", "Show Hook logs transparently");
        add("Hook 日志显示在屏幕", "Show Hook logs on screen");
        add("在 DeepSeek 上方透明显示最近的模块 Hook 日志。",
                "Show recent module Hook logs in a transparent overlay over DeepSeek.");
        add("首页", "Home");
        add("设置", "Settings");
        add("运行信息", "Runtime");
        add("模块运行状态", "Module status");
        add("构建渠道", "Build channel");
        add("打开 DeepSeek", "Open DeepSeek");
        add("模块设置位于 DeepSeek 设置页", "Module settings are in DeepSeek settings");
        add("配置", "Configuration");
        add("导出配置", "Export configuration");
        add("导入配置", "Import configuration");
        add("保存当前功能开关，不包含提示词、密钥和聊天",
                "Save feature switches without prompts, keys, or chats");
        add("从配置文件恢复功能开关", "Restore feature switches from a configuration file");
        add("正在读取 DeepSeek 配置", "Reading DeepSeek configuration");
        add("配置已导出", "Configuration exported");
        add("配置已导入，重新打开 DeepSeek 后生效",
                "Configuration imported; reopen DeepSeek to apply");
        add("请先启动一次 DeepSeek，再重试", "Launch DeepSeek once, then try again");
        add("项目", "Project");
        add("赞助开发", "Sponsor development");
        add("支持更快地维护和适配", "Help speed up maintenance and compatibility work");
        add("感谢支持持续维护与适配。", "Thank you for supporting ongoing maintenance.");
        add("GitHub 仓库", "GitHub repository");
        add("赞助开发者", "Sponsor the developer");
        add("支持持续开发和新版本适配",
                "Support continued development and new-version compatibility");
        add("感谢支持持续开发与 DeepSeek 版本适配。",
                "Thank you for supporting continued development and DeepSeek compatibility.");
        add("兼容性诊断报告", "Compatibility diagnostic report");
        add("汇总宿主渠道、版本映射和功能适配结果，可一键复制。",
                "Summarize the host channel, version mapping, and feature compatibility results, with one-tap copy.");
        add("Hook 性能统计", "Hook performance statistics");
        add("性能统计", "Performance statistics");
        add("查看本次运行的事件量、平均耗时、最慢耗时和慢事件数。",
                "View event count, average duration, slowest duration, and slow-event count for this run.");
        add("脱敏事件追踪与导出", "Sanitized event trace and export");
        add("事件追踪", "Event trace");
        add("导出有限长度的关键 Hook 事件；自动隐藏令牌和私有路径。",
                "Export a bounded trace of key Hook events with tokens and private paths automatically redacted.");
        add("本地禁言", "Local mute");
        add("本地禁言 · 实验性", "Local mute · Experimental");
        // Legacy labels are kept only for category matching across older saved layouts.
        add("伪禁言", "Simulated mute");
        add("本地禁言截止时间", "Local-mute end time");
        add("仅在本地显示禁言状态", "Show mute status locally only");
        add("显示至 ", "Shown until ");
        add("仅替换本地禁言状态，让 DeepSeek 绘制原生禁言界面；不发送网络数据。",
                "Replace only the local mute state so DeepSeek draws its native mute UI; no network data is sent.");
        add("选择宿主原生禁言栏显示的本地截止日期与时间。",
                "Choose the local date and time shown by the host's native mute bar.");
        add("本地禁言已关闭", "Local mute is off");
        add("尚未设置有效截止时间", "No valid deadline set");
        add("已开启至 ", "Enabled until ");
        add("已保存，开关未开启 · ", "Saved; switch is off · ");
        add("请先设置一个未来的截止时间", "Set a future deadline first");
        add("截止时间已更新，本地禁言保持开启",
                "Deadline updated; local mute remains enabled");
        add("截止时间已保存，打开开关后启用",
                "Deadline saved; turn on the switch to enable it");
        add("禁用数据用于优化体验",
                "Disable data used for service improvement");
        add("开启后立即关闭 DeepSeek 的“数据用于优化体验”，并阻止再次开启。",
                "Turn off DeepSeek's data use for service improvement immediately and prevent it from being enabled again.");
        add("禁用设置保存失败", "Could not save the disable-data setting");
        add("已关闭数据用于优化体验",
                "Data use for service improvement is now off");
        add("禁用已启用，但服务器同步失败：",
                "Data-use blocking is enabled, but server sync failed: ");
        add("无法关闭数据用于优化体验",
                "Could not disable data use for service improvement");
        add("数据用于优化体验", "Data use for service improvement");
        add("选择截止日期", "Choose end date");
        add("选择截止时间", "Choose end time");
        add("下一步", "Next");
        add("上一步", "Back");
        add("年", "Year");
        add("月", "Month");
        add("日", "Day");
        add("时", "Hour");
        add("分", "Minute");
        add("已启用本地原生禁言显示，返回聊天界面后生效",
                "Native local mute display is enabled; return to the chat to apply it");
        add("截止时间必须晚于当前时间", "The end time must be in the future");
        add("本地原生禁言界面显示至 ", "Native local mute UI shown until ");
        add("提示词、会话工具、AI 心跳和模型能力",
                "Prompts, chat tools, AI heartbeat, and model features");
        add("账号管理、登录入口和内容保护",
                "Account management, sign-in options, and content protection");
        add("背景、贴纸、气泡与聊天界面定制",
                "Backgrounds, stickers, bubbles, and chat customization");
        add("诊断日志和兼容性排查", "Diagnostic logs and compatibility checks");
        add("Agent、语言、备份与帮助", "Agent, language, backups, and help");
        add("自定义背景、贴纸、气泡、透明度、取景和空间动效。",
                "Customize backgrounds, stickers, bubbles, opacity, framing, and spatial motion.");
        add("解锁专家模式与图片上传 · 实验性",
                "Unlock expert mode and image upload · Experimental");
        add("启用专家模型能力，并通过视觉描述中继图片。",
                "Enable expert-model features and relay images through visual descriptions.");
        add("AI 心跳 · 实验性", "AI heartbeat · Experimental");
        add("AI 心跳间隔 · 实验性", "AI heartbeat interval · Experimental");
        add("设置 AI 心跳的默认触发间隔。",
                "Set the default interval for AI heartbeats.");
        add("Agent · 实验性", "Agent · Experimental");
        add("管理本地工具、权限模式以及 Root / Shizuku 后端。",
                "Manage local tools, permission mode, and Root / Shizuku backends.");
        add("配置兼容接口、后台保活、密钥、监听地址和请求统计。",
                "Configure compatible endpoints, background keepalive, keys, listeners, and request statistics.");
        add("禁用热更新 · 实验性", "Disable hot updates · Experimental");
        add("阻止 DeepSeek 展示普通或强制更新弹窗；不影响商店手动更新。",
                "Prevent DeepSeek from showing normal or forced update dialogs; manual store updates are unaffected.");
        add("热更新设置保存失败", "Could not save the update setting");
        add("Google 登录", "Google sign-in");
        add("专家模式", "Expert mode");
        add("AI 心跳", "AI heartbeat");
        add("【功能】AI 主动消息", "[Feature] AI proactive messages");
        add("设置主动消息间隔", "Set proactive-message interval");
        add("主动消息间隔已保存", "Proactive-message interval saved");
        add("每 %d 分钟", "Every %d minutes");
        add("未绑定", "Not bound");
        add("已绑定当前对话", "Bound to current chat");
        add("已绑定其他对话", "Bound to another chat");
        add("主动消息", "Proactive messages");
        add("系统提示词", "System prompt");
        add("安全审查", "Safety filtering");
        add("微信与手机号", "WeChat and phone number");
        add("导出会话", "Export chats");
        add("导入聊天记录", "Import chat history");
        add("导出 Markdown，并在下载目录生成可恢复的聊天备份包。",
                "Export Markdown and create a restorable chat backup in Downloads.");
        add("导出期间请保持 DeepSeek 运行；备份包卸载后仍保留在 Download/Deekseep。",
                "Keep DeepSeek running during export. The backup remains in Download/Deekseep after uninstalling.");
        add("选择导出的聊天备份包，只覆盖服务器已重新下发的同 ID 会话。",
                "Select an exported chat backup and overwrite only same-ID chats already restored by the server.");
        add("只覆盖服务器已重新下发、且会话 ID 与备份命中的记录。导入后请完整重启 DeepSeek。",
                "Overwrite only records whose chat IDs match server-restored chats. Fully restart DeepSeek after importing.");
        add("聊天备份包：", "Chat backup: ");
        add("备份包跳过会话：", "Backup archive skipped chat: ");
        add("覆盖失败：", "Overwrite failed: ");
        add("已覆盖 ", "Overwrote ");
        add(" 个命中会话；未命中 ", " matching chats; ");
        add(" 个；失败 ", " unmatched; ");
        add(" 个。请完整重启 DeepSeek。", " failed. Fully restart DeepSeek.");
        add("全局搜索", "Global search");
        add("记录服务器返回", "Log server responses");
        add("诊断", "Diagnostics");
        add("导入提示词", "Import prompt");
        add("还原设置", "Reset settings");
        add("搜索功能", "Search features");
        add("位于：", "In: ");
        add(" · 点击进入", " · Tap to open");
        add("没有找到相关功能", "No matching features");
        add("去除安全审查", "Remove safety filtering");
        add("提示词 prompt 导入", "prompt import");
        add("内容替换 一键破甲", "content replacement one-tap armor break");
        add("批量 删除", "bulk delete");
        add("修改 新建对话", "edit create chat");
        add("导出 备份包", "export backup");
        add("恢复 覆盖 备份包", "restore overwrite backup");
        add("消息 搜索", "message search");
        add("消息 字数", "message character count");
        add("数据库 backup", "database backup");
        add("每日", "daily");
        add("主动消息 间隔", "proactive message interval");
        add("账号 切换 导入", "account switch import");
        add("谷歌 国内版", "Google mainland build");
        add("海外版 短信", "international build SMS");
        add("时间 截止日期", "time deadline");
        add("隐私 training", "privacy training");
        add("首页 文案", "home copy");
        add("插件 悬浮", "plugin floating entry");
        add("背景 气泡 液态玻璃 空间动效",
                "background bubble liquid glass spatial motion");
        add("字体 渐变", "font gradient");
        add("日志 overlay", "log overlay");
        add("版本 映射", "version mapping");
        add("耗时", "duration");
        add("网络 API", "network API");
        add("中文 English", "Chinese English");
        add("更新 强制更新", "update forced update");
        add("工具 Root Shizuku", "tools Root Shizuku");
        add("禁用热更新", "Disable hot updates");
        add("灰度功能管理器", "Feature Flag Manager");
        add("灰度功能管理器 · 实验性", "Feature Flag Manager · Experimental");
        add("DeepSeek 原生功能管理", "DeepSeek native features");
        add("进程管理", "Process manager");
        add("进程管理 · Root", "Process manager · Root");
        add("查看 DeepSeek 与模块进程，并精确冻结、解冻或杀死。",
                "Inspect DeepSeek and module processes, then precisely freeze, resume, or kill them.");
        add("无法打开进程管理，请确认模块 APK 已更新",
                "Could not open process manager; update the module APK");
        add("进程 冻结 解冻 杀死 Root", "process freeze resume kill Root");
        add("查看、冻结或终止 DeepSeek 与模块进程",
                "Inspect, freeze, or stop DeepSeek and module processes");
        add("远程配置 feature flags", "remote configuration feature flags");
        add("使用 DeepSeek 原生设置覆盖层；保留宿主自带的全部灰度项目。",
                "Uses DeepSeek's native settings override layer and retains every host flag.");
        add("原生覆盖已管理 ", "Native overrides manage ");
        add(" 项。", " items.");
        add("自动清理缓存", "Automatic cache cleanup");
        add("每天检查一次，仅清理 DeepSeek 已确认的图片、Markdown 图表缓存中超过 ",
                "Check once a day and delete only files older than ");
        add(" 天的文件。", " days from verified DeepSeek image and Markdown-chart caches.");
        add("缓存保留时间", "Cache retention");
        add("保留 3 天", "Keep 3 days");
        add("保留 7 天", "Keep 7 days");
        add("保留 30 天", "Keep 30 days");
        add("保存失败", "Save failed");
        add("消息时间与详情", "Message time and details");
        add("时间戳 message details", "timestamp message details");
        add("在聊天编辑器中显示每条本地消息的时间，点击时间可查看消息 ID 与详情。",
                "Show each local message's time in the chat editor; tap it for the message ID and details.");
        add("自动继续生成", "Automatically continue generating");
        add("继续生成 长思考 后台 续写", "continue generation long reasoning background resume");
        add("长思考被服务器暂停时自动继续，切到后台也会生效。",
                "Automatically resume when the server pauses a long response, even in the background.");
        add("回复完成通知", "Reply-ready notification");
        add("切到后台后，模型完成回答时发送系统通知。",
                "Send a system notification when a response finishes in the background.");
        add("消息详情", "Message details");
        add("消息 ID：", "Message ID: ");
        add("角色：", "Role: ");
        add("时间：", "Time: ");
        add("时间未知", "Unknown time");
        add("图片：", "Images: ");
        add("含思考内容：", "Has reasoning: ");
        add("详情", "Details");
        add("用户", "User");
        add("助手", "Assistant");
        add("是", "Yes");
        add("否", "No");
        add("系统提示词注入", "System prompt injection");
        add("去他妈的安全审查", "Prevent response replacement");
        add("聊天记录多选", "Multi-select chats");
        add("长按会话进入多选", "Long-press a chat to select multiple");
        add("每日自动备份", "Back up daily");
        add("保留被替换前的完整回答", "Keep the complete answer before replacement");
        add("解锁 Google 登录", "Unlock Google sign-in");
        add("解锁微信与手机号登录", "Unlock WeChat and phone sign-in");
        add("记录服务器返回（诊断）", "Log server responses (diagnostics)");
        add("编辑聊天记录", "Edit chat history");
        add("聊天外观", "Chat appearance");
        add("全局液态玻璃", "Global liquid glass");
        add("启用全局液态玻璃", "Enable global liquid glass");
        add("共享一张背板纹理，在气泡、输入区和侧栏边缘产生柔和折射。",
                "Share one backdrop texture for soft refraction around bubbles, the input area, and sidebar edges.");
        add("已开启其他功能，请先关闭后再使用", "Another feature is enabled; turn it off before importing");
        add("此功能尚未完善", "This feature is not finished yet");
        add("聊天气泡、输入区、侧栏和交互控件共用一张背板纹理；不会给每个按键重复截图和模糊。",
                "Chat bubbles, the input area, sidebar, and interactive controls share one backdrop texture; screenshots and blur are not repeated for every control.");
        add("自动模式按 Android 版本、分辨率、内存、节电与温度动态选择实时折射、共享模糊或静态磨砂，不绑定具体机型。",
                "Auto mode dynamically selects real-time refraction, shared blur, or static frosting from the Android version, resolution, memory, power-saving state, and temperature instead of a device model.");
        add("液态玻璃画质", "Liquid glass quality");
        add("渲染画质：", "Rendering quality: ");
        add("当前设备将使用：", "This device will use: ");
        add("自动（推荐）", "Auto (recommended)");
        add("高画质", "High quality");
        add("均衡", "Balanced");
        add("省电", "Battery saver");
        add("自动", "Auto");
        add("抠出透明贴纸", "Cut out transparent sticker");
        add("贴纸抠图", "Sticker cutout");
        add("一键抠图", "Automatic cutout");
        add("一键抠图失败，可继续手动擦除",
                "Automatic cutout failed; manual erase is still available");
        add("擦除", "Erase");
        add("恢复", "Restore");
        add("还原", "Restore");
        add("撤销", "Undo");
        add("最终结果", "Final result");
        add("仅涂抹区", "Painted only");
        add("仅未涂抹区", "Unpainted only");
        add("显示范围", "Display area");
        add("显示：", "View: ");
        add("画笔粗细：", "Brush size: ");
        add("画笔：", "Brush: ");
        add("画笔", "Brush");
        add("画笔 ▾", "Brush ▾");
        add("全屏", "Full screen");
        add("退出全屏", "Exit full screen");
        add("保存透明贴纸", "Save transparent sticker");
        add("正在识别背景…", "Detecting background…");
        add("正在保存…", "Saving…");
        add("没有可保存的结果", "There is no result to save");
        add("没有可保存的抠图结果", "There is no cutout result to save");
        add("找不到要编辑的贴纸", "The sticker to edit could not be found");
        add("请先导入气泡贴纸", "Import a bubble decoration first");
        add("尚未导入气泡贴纸", "No bubble decoration has been imported");
        add("无法读取贴纸图片", "Could not read the sticker image");
        add("抠图编辑器打开失败", "Could not open the cutout editor");
        add("抠图结果保存失败", "Could not save the cutout result");
        add("抠图设置保存失败", "Could not save the cutout settings");
        add("贴纸抠图已保存", "Sticker cutout saved");
        add("用户气泡贴纸抠图已保存",
                "User bubble decoration cutout saved");
        add("DeepSeek 气泡贴纸抠图已保存",
                "DeepSeek bubble decoration cutout saved");
        add("取景与旋转（横向 / 纵向 / 旋转） ▾",
                "Focus and rotation (horizontal / vertical / rotation) ▾");
        add("收起取景与旋转 ▴", "Hide focus and rotation ▴");
        add("展开取景与旋转", "Show focus and rotation");
        add("收起取景与旋转", "Hide focus and rotation");
        add("展开背景选项", "Show wallpaper options");
        add("收起背景选项", "Hide wallpaper options");
        add("更多背景详细参数 ▾", "More wallpaper details ▾");
        add("收起背景详细参数 ▴", "Hide wallpaper details ▴");
        add("大小、透明度与旋转 ▾", "Size, opacity, and rotation ▾");
        add("收起大小、透明度与旋转 ▴",
                "Hide size, opacity, and rotation ▴");
        add("气泡样式与贴纸参数 ▾",
                "Bubble and decoration details ▾");
        add("收起气泡样式与贴纸参数 ▴",
                "Hide bubble and decoration details ▴");
        add("导入图片作为背景或贴纸；支持取景、旋转、景深、界面绑定、曲线位移和完整贴纸布局。",
                "Import an image as wallpaper or a sticker, with crop focus, rotation, depth, screen binding, curved motion, and full sticker layout controls.");
        add("定制背景、页面贴纸和用户/DeepSeek 气泡；支持取景、旋转、景深、曲线位移及气泡顶部贴纸。",
                "Customize wallpaper, page stickers, and user/DeepSeek bubbles, with crop focus, rotation, depth, curved motion, and bubble-top decorations.");
        add("启用聊天背景与贴纸", "Enable chat wallpaper and stickers");
        add("只在 DeepSeek 聊天页显示；覆盖层不会拦截输入、滚动或消息操作。",
                "Shown only on DeepSeek chat pages. The overlay does not block typing, scrolling, or message actions.");
        add("背景可在高级选项中绑定界面；贴纸会保留在聊天与设置页。覆盖层不会拦截输入、滚动或操作。",
                "Advanced options bind the wallpaper to selected screens; stickers remain on chats and settings. The overlay does not block typing, scrolling, or actions.");
        add("聊天气泡", "Chat bubbles");
        add("启用气泡定制", "Enable bubble customization");
        add("分别修改用户与 DeepSeek 消息；样式和贴纸绑定到真实消息节点，会随聊天内容一起滚动。",
                "Customize user and DeepSeek messages separately. Styles and decorations attach to real message nodes and scroll with the conversation.");
        add("用户消息", "User messages");
        add("DeepSeek 消息", "DeepSeek messages");
        add("用户消息风格：", "User message style: ");
        add("DeepSeek 消息风格：", "DeepSeek message style: ");
        add("选择气泡风格", "Choose bubble style");
        add("跟随原版", "Original");
        add("柔和纯色", "Soft color");
        add("轻描边", "Outline");
        add("磨砂玻璃", "Frosted glass");
        add("液态玻璃", "Liquid glass");
        add("填充不透明度：", "Fill opacity: ");
        add("圆角：", "Corner radius: ");
        add("描边宽度：", "Border width: ");
        add("用户气泡顶部贴纸", "User bubble top decoration");
        add("DeepSeek 气泡顶部贴纸", "DeepSeek bubble top decoration");
        add("横向位置：", "Horizontal position: ");
        add("（已导入）", " (imported)");
        add("（未设置）", " (not set)");
        add("可以分别给两边的消息换风格。",
                "Each side can use its own style.");
        add("贴纸也会跟着每条气泡移动。",
                "Decorations follow every bubble.");
        add("导入顶部贴纸", "Import top decoration");
        add("移除", "Remove");
        add("移除气泡贴纸？", "Remove bubble decoration?");
        add("导入的贴纸副本会从 DeepSeek 私有目录删除。",
                "The imported decoration copy will be deleted from DeepSeek's private directory.");
        add("移除气泡贴纸失败", "Could not remove bubble decoration");
        add("布局预览", "Layout preview");
        add("点按选中贴纸，直接拖动调整位置；下面可以继续调整大小、旋转和不透明度。",
                "Tap a sticker to select it, then drag to position it. Use the controls below for size, rotation, and opacity.");
        add("导入图片", "Import image");
        add("从系统相册选择一次，然后决定设为背景图还是添加为贴纸。",
                "Choose an image from the system gallery, then use it as wallpaper or add it as a sticker.");
        add("模块会保存私有副本，删除相册原图后仍可显示。",
                "The module keeps a private copy, so it remains available if the gallery original is deleted.");
        add("选择图片", "Select image");
        add("背景图", "Wallpaper");
        add("背景图旋转：", "Wallpaper rotation: ");
        add("取景决定缩放或裁剪后保留哪一块；横图可重点保留左侧、中央或右侧。",
                "Focus chooses the area kept after zooming or cropping; landscape images can prioritize the left, center, or right.");
        add("显示范围：", "Display area: ");
        add("背景显示范围", "Wallpaper display area");
        add("先选择背景显示范围", "First choose the wallpaper display area");
        add("上半屏", "Top half");
        add("中间半屏", "Center half");
        add("下半屏", "Bottom half");
        add("边界处理：", "Edge handling: ");
        add("背景边界处理", "Wallpaper edge handling");
        add("截断：范围外透明", "Clip: transparent outside");
        add("镜像：反射延展边缘", "Mirror: reflect image edges");
        add("边缘延展：沿用最外圈像素",
                "Edge extend: repeat outermost pixels");
        add("截断", "Clip");
        add("镜像延展", "Mirrored extension");
        add("边缘像素延展", "Outermost-pixel extension");
        add("连续缩放：", "Continuous zoom: ");
        add("（滑动后回中，可反复缩放）",
                " (recenters after release; repeat as needed)");
        add("精确倍率：", "Exact multiplier: ");
        add("（点按输入，无固定上限）",
                " (tap to enter; no fixed maximum)");
        add("输入缩放倍率（无固定上限）",
                "Enter zoom multiplier (no fixed maximum)");
        add("1 为原始适配大小；小于 1 会缩小，大于 1 会放大。",
                "1 is the fitted size; values below 1 zoom out and values above 1 zoom in.");
        add("例如 0.5、1、3、20", "For example 0.5, 1, 3, or 20");
        add("重置为 1", "Reset to 1");
        add("请输入大于 0 的缩放倍率",
                "Enter a zoom multiplier greater than zero");
        add("横向取景：", "Horizontal focus: ");
        add("纵向取景：", "Vertical focus: ");
        add("选择背景取景区域", "Choose wallpaper focus");
        add("左上", "Top left");
        add("上方", "Top");
        add("右上", "Top right");
        add("左侧", "Left");
        add("中央", "Center");
        add("右侧", "Right");
        add("左下", "Bottom left");
        add("下方", "Bottom");
        add("右下", "Bottom right");
        add("稍后调整", "Adjust later");
        add("背景景深", "Wallpaper depth");
        add("只轻微虚化、降对比并放大背景，让原生聊天框看起来悬浮在图片上方；不会修改 DeepSeek UI。",
                "Slightly blur, soften, and enlarge only the wallpaper so native chat cards appear to float above it; the DeepSeek UI is unchanged.");
        add("动态背景位移", "Dynamic wallpaper motion");
        add("打开侧栏时背景随主界面向右，进入设置时向左；画布会预留边缘，避免露白。",
                "The wallpaper follows the main screen right when the sidebar opens and moves left in settings; overscan prevents blank edges.");
        add("位移强度：", "Motion amount: ");
        add("分别设置各界面位移", "Set offsets per screen");
        add("关闭时只显示统一强度；打开后分别设置聊天、侧栏和设置界面的水平位移。",
                "When off, only unified strength is shown. Turn it on to set horizontal offsets for chat, sidebar, and settings separately.");
        add("统一位移强度：", "Unified motion: ");
        add("聊天界面位移：", "Chat offset: ");
        add("侧栏界面位移：", "Sidebar offset: ");
        add("设置界面位移：", "Settings offset: ");
        add("动态效果预览（侧栏向右 / 设置向左）",
                "Motion preview (sidebar right / settings left)");
        add("侧栏", "Sidebar");
        add("聊天", "Chat");
        add("设置", "Settings");
        add("选择背景图在哪些界面显示；未选中的界面仍会保留贴纸。",
                "Choose which screens show the wallpaper; unselected screens still keep stickers.");
        add("高级选项：", "Advanced: ");
        add("背景图绑定界面", "Wallpaper screen binding");
        add("聊天界面", "Chat screen");
        add("会话侧栏", "Conversation sidebar");
        add("设置界面", "Settings screen");
        add("未绑定", "None");
        add("、", ", ");
        add("清除背景图", "Remove wallpaper");
        add("清除背景图？", "Remove wallpaper?");
        add("已导入的背景副本会从 DeepSeek 私有目录删除。",
                "The imported wallpaper copy will be deleted from DeepSeek's private directory.");
        add("清除背景图失败", "Could not remove wallpaper");
        add("贴纸", "Sticker");
        add("大小", "Size");
        add("大小：", "Size: ");
        add("旋转", "Rotation");
        add("旋转：", "Rotation: ");
        add("贴纸不透明度", "Sticker opacity");
        add("贴纸不透明度：", "Sticker opacity: ");
        add("移到最上层", "Bring to front");
        add("删除贴纸", "Delete sticker");
        add("删除这张贴纸？", "Delete this sticker?");
        add("已导入的贴纸副本会从 DeepSeek 私有目录删除。",
                "The imported sticker copy will be deleted from DeepSeek's private directory.");
        add("删除贴纸失败", "Could not delete sticker");
        add("重置全部聊天外观", "Reset all chat appearance");
        add("重置全部聊天外观？", "Reset all chat appearance?");
        add("背景图、全部贴纸和布局设置都会清除；聊天记录不会受到影响。",
                "The wallpaper, every sticker, and layout settings will be cleared. Chat history is not affected.");
        add("背景图、全部贴纸、气泡样式和布局设置都会清除；聊天记录不会受到影响。",
                "The wallpaper, every sticker, bubble styles, and layout settings will be cleared. Chat history is not affected.");
        add("重置聊天外观失败", "Could not reset chat appearance");
        add("设为背景图", "Use as wallpaper");
        add("添加为贴纸", "Add as sticker");
        add("添加为页面贴纸", "Add as page sticker");
        add("设为用户气泡顶部贴纸", "Use as user bubble decoration");
        add("设为 DeepSeek 气泡顶部贴纸", "Use as DeepSeek bubble decoration");
        add("这张图片怎么使用？", "How should this image be used?");
        add("正在导入图片…", "Importing image…");
        add("裁剪填充", "Crop to fill");
        add("完整显示", "Fit entire image");
        add("拉伸填满", "Stretch to fill");
        add("背景图显示方式", "Wallpaper display mode");
        add("尚未添加贴纸", "No stickers added");
        add("尚未设置背景图", "No wallpaper selected");
        add("背景图不透明度：", "Wallpaper opacity: ");
        add("显示方式：", "Display mode: ");
        add("已选贴纸 ", "Selected sticker ");
        add("聊天外观设置保存失败", "Could not save chat appearance");
        add("背景图预览", "Wallpaper preview");
        add("导入一张图片开始设置", "Import an image to begin");
        add("返回", "Back");
        add("没有选择图片", "No image was selected");
        add("最多只能添加 ", "You can add at most ");
        add(" 张贴纸", " stickers");
        add("无法创建外观图片目录", "Could not create the appearance image directory");
        add("外观设置保存失败", "Could not save appearance settings");
        add("背景图已导入", "Wallpaper imported");
        add("贴纸已添加", "Sticker added");
        add("用户气泡贴纸已导入", "User bubble decoration imported");
        add("DeepSeek 气泡贴纸已导入", "DeepSeek bubble decoration imported");
        add("无法读取所选图片", "Could not read the selected image");
        add("图片不能超过 32 MB", "Images cannot exceed 32 MB");
        add("所选图片为空", "The selected image is empty");
        add("复制图片失败", "Could not copy the image");
        add("所选文件不是可识别的图片", "The selected file is not a recognized image");
        add("图片尺寸过大，请选择较小的图片",
                "The image dimensions are too large. Choose a smaller image.");
        add("【功能】聊天背景与贴纸", "[Feature] Chat wallpaper and stickers");
        add("【功能】聊天背景、贴纸与气泡",
                "[Feature] Chat wallpaper, stickers, and bubbles");
        add("从“聊天外观”导入图片后，可选择设为背景图或添加为贴纸。背景支持九宫格/横纵取景、裁剪、完整显示、拉伸、旋转、不透明度和景深；",
                "After importing an image from Chat appearance, use it as wallpaper or add it as a sticker. Wallpaper supports 3x3 or horizontal/vertical crop focus, crop, fit, stretch, rotation, opacity, and depth; ");
        add("动态位移采用先快后慢的曲线，侧栏展开时背景随主界面向右且关闭后完整返回，设置路由向左并在返回时平滑复位。统一强度可切换为聊天、侧栏、设置三个独立位移。",
                "Motion uses a fast-starting ease-out curve: the wallpaper follows the main screen right as the sidebar opens and fully returns when it closes, while settings move it left and smoothly restore it on return. Unified strength can switch to independent chat, sidebar, and settings offsets. ");
        add("图片复制到 DeepSeek 私有目录；高级选项决定背景出现在哪些界面；景深只处理背景图片，不修改 DeepSeek UI。贴纸支持拖动、大小、旋转、不透明度和层级，并保留在聊天与设置路由，覆盖层不会拦截触摸。",
                "Images are copied to DeepSeek's private directory; advanced options choose wallpaper screens, and depth processes only the wallpaper without modifying the DeepSeek UI. Stickers support dragging, size, rotation, opacity, and layering, remain on chat and settings routes, and never intercept touch.");
        add("图片复制到 DeepSeek 私有目录；高级选项决定背景出现在哪些界面；景深只处理背景图片，不修改 DeepSeek UI。页面贴纸支持拖动、大小、旋转、不透明度和层级，并保留在聊天与设置路由，覆盖层不会拦截触摸。",
                "Images are copied to DeepSeek's private directory; advanced options choose wallpaper screens, and depth processes only the wallpaper without modifying DeepSeek UI. Page stickers support dragging, size, rotation, opacity, and layering, remain on chat and settings routes, and never intercept touch.");
        add("聊天气泡可分别设置用户与 DeepSeek 风格、填充不透明度、圆角和描边，也可为每条气泡绑定顶部贴纸并调整大小、横向位置、不透明度与旋转；气泡贴纸属于真实消息节点，会随消息滚动。",
                "User and DeepSeek bubbles can use separate styles, fill opacity, corner radius, and borders. Each bubble can also have a top decoration with adjustable size, horizontal position, opacity, and rotation; decorations belong to real message nodes and scroll with them.");
        add("【问题】为什么背景图或贴纸没有显示，或者看起来遮住界面？",
                "[Question] Why is the wallpaper or sticker missing, or why does it appear to cover the interface?");
        add("解决办法：确认总开关已打开，并在背景图“高级选项”中勾选当前界面；贴纸会在聊天与设置页保留，登录页等其他路由会自动隐藏。",
                "Solution: enable the master switch and select the current screen under the wallpaper's Advanced options. Stickers remain on chats and settings; sign-in and other routes hide them. ");
        add("背景层位于宿主 Compose 界面上方，建议把背景不透明度调低以保留文字可读性；贴纸即使覆盖按钮也不会拦截点击。",
                "The wallpaper layer sits above the host Compose UI, so use a lower opacity to keep text readable. Stickers never intercept taps, even when they visually cover a button.");
        add("多账号管理", "Multiple accounts");
        add("导出会话为 Markdown", "Export chats as Markdown");
        add("全局搜索聊天记录", "Search all chats");
        add("会话数据统计", "Chat statistics");
        add("立即备份聊天数据库", "Back up chat database now");
        add("自动备份聊天数据库", "Automatic chat database backup");
        add("将全部本地会话导出为 Markdown 文件。",
                "Export every local chat as a Markdown file.");
        add("导出期间请保持 DeepSeek 运行；无法读取的账号会在日志中单独标记。",
                "Keep DeepSeek running during export; unreadable accounts are identified in the log.");
        add("仅统计设备上的聊天数据库，不会上传聊天内容。",
                "Only on-device chat databases are counted; chat content is never uploaded.");
        add("复制全部聊天数据库到应用外部目录。",
                "Copy every chat database to the app's external directory.");
        add("备份不会修改原数据库；完成后日志会显示保存目录。",
                "The backup does not modify the source databases; the destination is shown when complete.");
        add("执行", "Run");
        add("发送自定义请求", "Send custom request");
        add("向 DeepSeek 官方 API 发送 GET 或 POST 调试请求。",
                "Send a GET or POST diagnostic request to the official DeepSeek API.");
        add("解锁国内用户的 Google 登录入口。",
                "Unlock Google sign-in for mainland users.");
        add("解锁海外用户的微信与手机号登录入口。",
                "Unlock WeChat and phone sign-in for overseas users.");
        add("每 %d 分钟向绑定对话发送主动消息。",
                "Send a proactive message to the bound chat every %d minutes.");
        add("正在扫描聊天数据库", "Scanning chat databases");
        add("找到 ", "Found ");
        add(" 个账号数据库", " account databases");
        add("读取：", "Reading: ");
        add("跳过：", "Skipped: ");
        add(" 个会话，", " chats, ");
        add(" 个会话", " chats");
        add(" 个数据库未能读取", " databases could not be read");
        add(" 个数据库", " databases");
        add("有 ", "There are ");
        add("输出目录：", "Output: ");
        add("备份目录：", "Backup directory: ");
        add("正在读取 ", "Reading ");
        add("汇总：", "Summary: ");
        add(" 条消息，", " messages, ");
        add(" 消息", " messages");
        add(" 字", " characters");
        add("实验性功能", "Experimental Features");
        add("功能可能随时失败、产生不完整结果或导致数据丢失。继续表示你已理解上述风险并自行承担后果。",
                "Features may fail, produce incomplete results, or cause data loss. Continuing means you understand and accept these risks.");
        add("帮助与问题", "Help & Questions");
        add("Agent、语言与备份", "Agent, language, and backups");
        add("记录崩溃", "Crash records");
        add("查看 Java、Native 与 ANR 记录，并保存为日志文件。",
                "View Java, native, and ANR records and save them as a log file.");
        add("崩溃测试", "Crash tests");
        add("选择应用进程内的 Java 崩溃方式。",
                "Choose a Java crash method within the app process.");
        add("记录 Java 未捕获异常，并读取系统保留的 Native 崩溃与 ANR 记录。",
                "Records uncaught Java exceptions and reads system-retained native crash and ANR reports.");
        add("暂无崩溃", "No crash records");
        add("保存成文件", "Save to file");
        add("无法打开保存位置", "Could not open a save location");
        add("仅终止 DeepSeek 应用进程。已移除可能牵连系统冻结器的 Native 信号测试。",
                "Only the DeepSeek app process is terminated. Native signal tests that could affect the system freezer have been removed.");
        add("主线程异常", "Main-thread exceptions");
        add("8 种 Java 异常与错误", "8 Java exceptions and errors");
        add("异步与回调异常", "Async and callback exceptions");
        add("后台线程、消息队列与绘制帧回调", "Worker threads, message queue, and frame callbacks");
        add("发送链异常", "Send-path exception");
        add("下一次真实消息发送时触发 Java 崩溃", "Trigger a Java crash on the next real message send");
        add("点击后立即终止当前 DeepSeek 进程。", "Tap to immediately terminate the current DeepSeek process.");
        add("主线程 RuntimeException", "Main-thread RuntimeException");
        add("后台线程 RuntimeException", "Worker-thread RuntimeException");
        add("主线程 NullPointerException", "Main-thread NullPointerException");
        add("主线程 IllegalStateException", "Main-thread IllegalStateException");
        add("主线程 ClassCastException", "Main-thread ClassCastException");
        add("主线程 IndexOutOfBoundsException", "Main-thread IndexOutOfBoundsException");
        add("主线程 ArithmeticException", "Main-thread ArithmeticException");
        add("主线程 AssertionError", "Main-thread AssertionError");
        add("主线程 StackOverflowError", "Main-thread StackOverflowError");
        add("覆盖不同的应用线程和主线程调度入口。", "Covers app threads and main-thread scheduling entry points.");
        add("线程池任务 RuntimeException", "Executor task RuntimeException");
        add("Handler 延迟回调异常", "Delayed Handler callback exception");
        add("下一绘制帧回调异常", "Next-frame callback exception");
        add("设置后返回聊天页，在下一次真实发送时触发。", "Return to the chat after arming; it triggers on the next real send.");
        add("下一次发送消息时 RuntimeException", "RuntimeException on the next message");
        add("等待下一次真实消息请求", "Wait for the next real message request");
        add("立即终止当前 DeepSeek 进程", "Immediately terminate the current DeepSeek process");
        add("崩溃日志已保存", "Crash log saved");
        add("语言", "Language");
        add("选择 Deekseep 语言", "Choose Deekseep language");
        add("跟随 DeepSeek（自动）", "Follow DeepSeek (Auto)");
        add("DeepSeek 为中文时使用中文；其他任何语言使用英文。",
                "Use Chinese when DeepSeek is Chinese; use English for every other language.");
        add("始终显示中文", "Always display Chinese");
        add("始终显示英文", "Always display English");
        add("语言设置保存失败", "Could not save language");
        add("DeepSeek 私有目录暂时不可写，请完整重启后重试。",
                "DeepSeek's private directory is temporarily unavailable. Fully restart the app and try again.");
        add("提示", "Notice");
        add("确定", "OK");
        add("取消", "Cancel");
        add("关闭", "Close");
        add("知道了", "Got it");

        // Standalone module status surface (system language and system color scheme).
        add("运行状态", "Module status");
        add("构建信息", "Build information");
        add("DeepSeek 增强模块", "DeepSeek enhancement module");
        add("状态正常", "Ready");
        add("目标已验证", "Target verified");
        add("等待验证", "Verification pending");
        add("尚未连接", "Not connected");
        add("已连接并生效", "Connected and active");
        add("已在 DeepSeek 生效", "Active in DeepSeek");
        add("等待 DeepSeek 启动", "Waiting for DeepSeek");
        add("等待模块激活", "Waiting for activation");
        add("DeepSeek 目标进程已确认模块注入，当前作用域可以正常使用。",
                "The DeepSeek target process confirmed module injection. The configured scope is working.");
        add("DeepSeek 最近已确认注入；框架服务暂未连接，恢复可用后会自动重连。",
                "DeepSeek recently confirmed injection. The framework service will reconnect when available.");
        add("框架已经连接。请启动一次 DeepSeek，以完成目标作用域验证。",
                "The framework is connected. Launch DeepSeek once to verify the target scope.");
        add("请在 Xposed 管理器中启用 Deekseep、勾选 DeepSeek，然后启动一次 DeepSeek。无需勾选模块应用自身。",
                "Enable Deekseep in your Xposed manager, select DeepSeek as its scope, then launch DeepSeek once. Do not select the module app itself.");
        add("Xposed 框架", "Xposed framework");
        add("DeepSeek 作用域", "DeepSeek scope");
        add("传统框架管理", "Managed by legacy framework");
        add("已连接", "Connected");
        add("未连接", "Not connected");
        add("已验证 · ", "Verified · ");
        add("已验证", "Verified");
        add("等待启动", "Waiting for launch");
        add("模块版本", "Module version");
        add("框架接口", "Framework API");
        add("模块编译时间", "Module build time");
        add("编译时间", "Built on");
        add("模块本体跟随系统语言与深浅色；DeepSeek 内的 Deekseep 页面单独跟随 DeepSeek 语言。",
                "This module app follows the system language and color scheme. The Deekseep page inside DeepSeek follows DeepSeek's language separately.");
        add("Deekseep 模块激活状态", "Deekseep module activation status");
        add("已激活", "Activated");
        add("等待 DeepSeek", "Waiting for DeepSeek");
        add("未激活", "Not activated");
        add("框架已连接，启动 DeepSeek 后验证",
                "Framework connected; launch DeepSeek to verify");
        add("请先在 Xposed 管理器中启用模块",
                "Enable the module in your Xposed manager");
        add("强制执行", "Enforcing");
        add("宽容模式", "Permissive");
        add("未安装", "Not installed");
        add("待验证", "Waiting for verification");
        add("启动 DeepSeek 后确认", "Launch DeepSeek to confirm");
        add("启动 DeepSeek 生效", "Ready — launch DeepSeek");
        add("已禁用", "Disabled");

        // Main settings page and Experimental Features.
        add("回答流完后，服务端会追加一帧把整段内容替换成模板回复",
                "After an answer finishes streaming, the server may append a frame that replaces the entire response with the template ");
        add("\u201C这个问题我暂时无法回答\u201D。开启后，模块直接丢弃这帧",
                "\u201cI can't answer that right now.\u201d When enabled, the module discards that frame ");
        add("（带 CONTENT_FILTER 标记的替换帧），已生成的内容原样留在屏幕上。",
                "(the replacement frame marked CONTENT_FILTER), leaving text already received on screen unchanged.");
        add("开启后，长按左侧聊天记录进入多选模式；关闭后使用 DeepSeek 原本的重命名/删除菜单。",
                "When enabled, long-press a chat in the left sidebar to enter multi-select. When disabled, DeepSeek's original rename/delete menu is used.");
        add("国内登录页默认隐藏 Google。开启后保留微信、手机号等入口，并把 DeepSeek 自带的",
                "The mainland sign-in page hides Google by default. Enabling this keeps WeChat, phone, and other options and restores DeepSeek's ");
        add("原生 Google 登录项恢复到列表；点击仍走宿主 Credential Manager 和官方登录接口。",
                "native Google item to the list. Tapping it still uses the host Credential Manager and official sign-in endpoint. ");
        add("请在进入登录页前开启，切换后建议完整重启 DeepSeek。",
                "Enable it before opening sign-in; a full DeepSeek restart is recommended after changing it.");
        add("海外登录页默认隐藏微信和短信手机号。此开关会同时恢复这两个 DeepSeek 原生入口，",
                "The overseas sign-in page hides WeChat and SMS phone sign-in by default. This switch restores both native DeepSeek entries ");
        add("不会联动 Google 开关；点击后仍走宿主自己的微信 SDK、验证码页和官方登录接口。",
                "without changing the Google switch. They still use the host WeChat SDK, verification page, and official sign-in endpoints. ");
        add("把服务器返回的每一条 SSE 原始事件写到日志，用于排查内容为何被替换。",
                "Write every raw server SSE event to a log to diagnose response replacement. ");
        add("日志：/data/data/com.deepseek.chat/files/deekseep_srv.log",
                "Log: /data/data/com.deepseek.chat/files/deekseep_srv.log");
        add("（也会尽量写一份到 /sdcard/deekseep_srv.log）。仅诊断时打开。",
                " (with a best-effort copy at /sdcard/deekseep_srv.log). Enable only for diagnostics.");
        add("长按可修改用户输入、模型回答和思考内容；没有思考链时可新增，",
                "Long-press to edit user input, model answers, and reasoning. You can add missing reasoning ");
        add("并可自定义思考用时（改后重启 DeepSeek 生效）。",
                "and set a custom reasoning duration (restart DeepSeek after changes).");
        add("添加、切换和移除账号；可严格验真导入 JSON，也可勾选账号导出明文凭证。",
                "Add, switch, and remove accounts; strictly validate imported JSON or select accounts to export plaintext credentials.");
        add("把全部本地会话导出成 .md 文件到应用外部目录，可用文件管理器查看/分享。",
                "Export all local chats as .md files to app external storage for viewing or sharing with a file manager.");
        add("检索用户输入、模型回答和深度思考内容，点击进入原生会话。",
                "Search user input, model answers, and deep reasoning, then tap a result to open the native chat.");
        add("统计本地会话数、消息数、总字数，并按账号分组。",
                "Count local chats, messages, and characters, grouped by account.");
        add("把全部 deepseek_chat 数据库复制到应用外部目录，重装前手动留底。",
                "Copy all deepseek_chat databases to app external storage as a manual backup before reinstalling.");
        add("开启后，每次启动 DeepSeek 若距上次备份超过 24 小时，自动把数据库复制到",
                "When enabled, each DeepSeek start copies databases when the last backup is over 24 hours old to the ");
        add("应用内部目录（仅保留最近 5 份）。",
                "app's internal directory (keeping only the latest five backups).");
        add("实验性功能使用提示", "About experimental features");
        add("这些功能默认关闭，按需开启即可。使用前请留意：\n\n",
                "These features are off by default and can be enabled only when needed. Before using them:\n\n");
        add("• 聊天外观只修改本机显示层；若宿主更新后出现错位，关闭对应外观开关即可。\n",
                "• Chat appearance changes only the local display layer. Turn off the related appearance option if a host update causes misalignment.\n");
        add("• 专家图片中继会先通过视觉模型生成图片描述，结果和可用性取决于 DeepSeek 服务。\n",
                "• Expert image relay first creates an image description with a vision model; results and availability depend on the DeepSeek service.\n");
        add("• 涉及聊天、文件或 Agent 工具时，建议先备份重要内容，并保留客户端的确认和权限设置。\n\n",
                "• For chat, file, or Agent tools, back up important content and keep client confirmation and permission controls enabled.\n\n");
        add("如果 DeepSeek 更新后出现异常，关闭对应开关即可。",
                "If a DeepSeek update causes a problem, simply turn off the related option.");
        add("退出", "Exit");
        add("了解并进入", "Continue");
        add("无法保存确认状态", "Could not save acknowledgement");
        add("DeepSeek 私有目录暂时不可写，因此没有进入实验性功能。请完整重启应用后重试。",
                "DeepSeek's private directory is temporarily unavailable, so Experimental Features was not opened. Fully restart the app and try again.");
        add("功能默认关闭。建议一次只开启需要的选项；操作重要数据前先备份，DeepSeek 更新后如有异常可随时关闭。",
                "Features are off by default. Enable only what you need, back up before important changes, and turn an option off if a DeepSeek update causes an issue.");
        add("解锁专家模式与图片上传", "Unlock expert mode and image upload");
        add("点亮专家模式的思考、搜索和文件能力；图片会先由视觉模型识别，再把描述中继给专家模型。切换后需重进应用或重选模型。",
                "Enable expert reasoning, search, and file capabilities. Images are recognized by the vision model first, then their descriptions are relayed to the expert model. Reopen the app or reselect the model after changing this.");
        add("解锁专家模式与文件中转", "Unlock expert mode and file relay");
        add("点亮思考、搜索和文件能力；普通文件保留原文由 DeepSeek 读取，只有图片经视觉模型中继。切换后需重选模型。",
                "Enable reasoning, search, and files. Documents stay as native, byte-preserving attachments for DeepSeek to read; only images use the vision relay. Reselect the model after changing this.");
        add("AI 心跳", "AI heartbeat");
        add("问答、当前时间、截图与基础界面操作；可逐项关闭工具，并选择应用内、Root 或 Shizuku 后端。",
                "Questions, local time, screenshots, and basic UI actions. Each tool can be disabled, with in-app, Root, or Shizuku execution.");
        add("陀螺仪背景", "Gyroscope wallpaper");
        add("晃动手机时背景轻微漂移并回弹；需先设置背景图",
                "The wallpaper drifts slightly and springs back as the phone moves; set a wallpaper first");
        add("陀螺仪背景设置保存失败", "Could not save gyroscope-wallpaper settings");
        add("已就绪", "Ready");
        add("未设置", "Not configured");
        add("可用", "Available");
        add("不可用", "Unavailable");
        add("暂停动态", "Motion paused");
        add("无限制", "Unrestricted");
        add("  ·  传感器：", "  ·  Sensor: ");
        add("请先在聊天外观中设置背景图",
                "Set a wallpaper in Chat appearance first");
        add("空间动效（实验）", "Spatial motion (experimental)");
        add("空间动效", "Spatial motion");
        add("已开启 · 点按进入专属设置",
                "Enabled · tap to open dedicated settings");
        add("已关闭 · 点按进入专属设置",
                "Disabled · tap to open dedicated settings");
        add("启用空间动效", "Enable spatial motion");
        add("姿态视差直接跟手；侧边栏仍沿用原有平滑延迟",
                "Pose parallax tracks directly; the sidebar keeps its original smooth lag");
        add("半球相机直接跟手：背景 5、中景人物 1.5，文字与界面反向 0.5",
                "Direct hemispherical-camera tracking: background 5, midground subject 1.5, text and UI 0.5 in the opposite direction");
        add("空间动效设置保存失败",
                "Could not save spatial-motion settings");
        add("外沿颜色无限延伸", "Unlimited outer-edge color extension");
        add("沿用图片最外圈颜色填满移动范围，防止尺寸不足时露白",
                "Repeat the image's outermost colors across all motion to prevent white gaps");
        add("外沿延伸设置保存失败",
                "Could not save outer-edge extension");
        add("关闭传感器视差，保留静态分层和背景边缘处理",
                "Disable sensor parallax while retaining static layers and edge handling");
        add("稳定约 650ms 后只修正极小的传感器零点误差",
                "After about 650ms of stability, correct only tiny sensor-zero errors");
        add("将当前持机姿态设为视觉中心",
                "Use the current device pose as the visual center");
        add("已请求重新校准", "Recenter requested");
        add("动效强度", "Motion strength");
        add("弱", "Weak");
        add("标准", "Standard");
        add("稍强", "Slightly stronger");
        add("弱（0.55×）", "Weak (0.55×)");
        add("标准（1.0×）", "Standard (1.0×)");
        add("稍强（1.25×）", "Slightly stronger (1.25×)");
        add("动效强度设置保存失败",
                "Could not save motion strength");
        add("减少动态效果", "Reduce motion");
        add("关闭传感器视差，保留背景防露边处理",
                "Disable sensor parallax while retaining wallpaper edge protection");
        add("减少动态效果设置保存失败",
                "Could not save reduced-motion settings");
        add("自动重新校准", "Automatic recentering");
        add("稳定约 650ms 后缓慢修正小范围零点误差",
                "Slowly correct small neutral-point errors after about 650ms of stability");
        add("自动重新校准设置保存失败",
                "Could not save automatic-recentering settings");
        add("反转动效方向", "Reverse motion direction");
        add("统一反转背景图的上下左右视差方向",
                "Reverse the wallpaper parallax direction on both axes");
        add("动效方向设置保存失败",
                "Could not save motion direction");
        add("立即重新校准", "Recenter now");
        add("将当前持机姿态设为视觉中心",
                "Use the current device pose as the visual center");
        add("已请求重新校准", "Recentering requested");
        add("修改 AI 心跳间隔", "Change AI heartbeat interval");
        add("当前间隔：%d 分钟。点按此处修改；模型主动消息会写入绑定对话，并在前后台都发送系统通知。",
                "Current interval: %d minutes. Tap here to change it. Proactive model messages are added to the bound chat and always produce a system notification.");
        add("聊天中可直接约定每次心跳要做什么，也可让 AI 安排或取消指定时间的一次性心跳。",
                "In chat, you can agree on what each heartbeat should do or ask the AI to schedule or cancel a one-time heartbeat for a specific time.");
        add("当前间隔：%d 分钟。点按此处修改；模型主动消息会写入绑定对话，",
                "Current interval: %d minutes. Tap here to change it. Proactive model messages are added to the bound chat, ");
        add("并在前后台都发送系统通知。聊天中可直接约定每次心跳要做什么，",
                "and always produce a system notification. In chat, you can agree on what each heartbeat should do, ");
        add("也可让 AI 安排或取消指定时间的一次性心跳。",
                "or ask the AI to schedule or cancel a one-time heartbeat for a specific time.");
        add("尚未绑定；请在目标对话中告诉 AI 心跳要做什么。",
                "Not bound yet; tell the AI what heartbeats should do in the target chat.");
        add("已绑定当前对话。", "Bound to the current chat.");
        add("已绑定一个对话；在目标对话中重新约定即可切换。",
                "Bound to a chat; make a new heartbeat agreement in the target chat to switch.");
        add("例如 30、180、1440", "For example 30, 180, or 1440");
        add("设置 AI 心跳间隔", "Set AI heartbeat interval");
        add("请输入 15 到 10080 分钟（最长 7 天）。从保存时重新计时；",
                "Enter 15 to 10080 minutes (up to 7 days). Timing restarts when you save;");
        add("系统省电策略可能让实际触发略有延迟。",
                "Android battery policies may delay delivery slightly.");
        add("AI 心跳间隔已保存", "AI heartbeat interval saved");
        add("请输入 15 到 10080 之间的整数分钟",
                "Enter a whole number from 15 to 10080 minutes");
        add("AI 心跳设置保存失败", "Could not save AI heartbeat settings");
        add("心跳已绑定当前对话", "Heartbeat bound to this chat");
        add("心跳绑定失败", "Could not bind heartbeat to this chat");

        add("模块版本：", "Module version: ");
        add("\n编译时间：", "\nBuild time: ");
        add("\nDeepSeek 版本：", "\nDeepSeek version: ");
        add("未知", "Unknown");
        add("读取失败", "Could not read");
        add("功能说明、常见提示与对应解决办法", "Feature notes, common prompts, and solutions");
        add("包含最新功能说明与常见问题。点一下条目展开；问题条目下方均给出解决办法。",
                "Includes current feature notes and common questions. Tap an item to expand it; every question includes a solution.");
        add("【功能】语言自动检测与手动选择", "[Feature] Automatic and manual language selection");
        add("默认在每次 DeepSeek 启动或回到前台时读取宿主当前语言：中文使用中文，任何其他语言使用英文。",
                "By default, the module reads the host language whenever DeepSeek starts or returns to the foreground: Chinese uses Chinese, and every other language uses English. ");
        add("也可在 Deekseep 首页的“语言”中固定为 Chinese 或 English；选择“跟随 DeepSeek（自动）”可恢复自动检测。",
                "You can also lock the module to Chinese or English from Language on the Deekseep page. Select Follow DeepSeek (Auto) to resume automatic detection.");

        // Experimental help.
        add("【功能】聊天背景、贴纸与气泡",
                "[Feature] Chat wallpaper, stickers, and bubbles");
        add("从“实验性功能 → 聊天外观”导入图片后，可设为背景、页面贴纸或两侧气泡顶部贴纸。背景可选全屏、上/中/下半屏，裁剪、完整显示或拉伸，并支持取景、旋转、不透明度、景深和界面绑定。横向取景、纵向取景与旋转位于预览图正下方的独立折叠区，可一边调节一边查看效果。连续缩放条每次松手会回到中点，可反复缩放且没有固定上限；也可点“精确倍率”直接输入。缩小后，截断模式让范围外保持透明，镜像模式会反射图片边缘补齐，边缘像素延展会把最外圈像素一直铺到画布边界。动态位移可统一设置，也可分别设置聊天、侧栏和设置界面。大陆版与 Google Play 版共用同一配置和渲染链路。",
                "Import an image from Experimental Features → Chat appearance and use it as wallpaper, a page sticker, or a top decoration for either bubble side. Wallpaper supports full screen or the top, center, or bottom half; crop, fit, or stretch; focus, rotation, opacity, depth, and screen binding. Horizontal focus, vertical focus, and rotation are in a separate collapsible section directly below the preview, so changes remain visible while adjusting. The continuous zoom slider recenters after every release, can be repeated without a fixed maximum, and Exact multiplier accepts a direct value. After zooming out, Clip leaves the outside transparent, Mirror reflects the image edges, and Outermost-pixel extension carries the border pixels to the canvas boundary. Motion can be unified or configured separately for chat, sidebar, and settings. Mainland and Google Play builds share this configuration and rendering path.");
        add("【问题】为什么半屏背景仍显示在别处，或者缩小后出现空白？",
                "[Question] Why does half-screen wallpaper appear elsewhere, or why is there empty space after zooming out?");
        add("解决办法：半屏有上方、中间和下方三个位置，先确认“显示范围”选项；容器会在半屏边界处裁切旋转和位移后的图片。选择“截断”时，缩小后留下透明区域是预期效果；需要反射补齐时改为“镜像延展”，需要沿用图片边缘颜色时改为“边缘像素延展”。横图还可在预览图下方展开横向/纵向取景，决定主体保留位置。",
                "Solution: half-screen wallpaper has top, center, and bottom positions, so first check Display area. The container clips the rotated and shifted image at the half-screen boundary. Transparent space after zooming out is expected in Clip mode; choose Mirrored extension for reflected fill, or Outermost-pixel extension to continue the image's border colors. For a landscape image, expand horizontal and vertical focus below the preview to choose where the subject remains.");
        add("【问题】为什么背景缩放条松手后回到中间？",
                "[Question] Why does the wallpaper zoom slider return to the center after release?");
        add("解决办法：这是无固定上限缩放的设计。中点代表本次缩放的起点，向左或向右连续调整，松手后自动把当前倍率作为新的起点；可以反复拖动继续放大或缩小。需要准确倍率时点“精确倍率”输入大于 0 的数值，1 表示原始适配大小。",
                "Solution: this is how zoom avoids a fixed maximum. The midpoint is the starting value for the current gesture. Drag left or right continuously; after release, the current multiplier becomes the next starting point, so you can repeat the gesture to keep zooming. For an exact value, tap Exact multiplier and enter a number above zero. A value of 1 is the fitted size.");
        add("解决办法：确认聊天外观总开关已打开，并在背景图“高级选项”中勾选当前界面；贴纸会在聊天与设置页保留，登录页等其他路由会自动隐藏。背景层不会拦截点击，但不透明度过高会降低文字可读性，可调低背景不透明度。",
                "Solution: enable the Chat appearance master switch and select the current screen under the wallpaper's Advanced options. Stickers remain on chat and settings screens while sign-in and other routes hide them. The wallpaper does not intercept taps, but high opacity can reduce text readability, so lower wallpaper opacity if needed.");
        add("【功能】专家模式图片上传", "[Feature] Expert-mode image upload");
        add("开启后，专家模式可选择相册图片。图片会先保存到 DeepSeek 私有目录，并由视觉模型生成客观描述，再把描述交给专家模型；同一会话后续轮会继续捕获图片上下文。视觉识别结果和服务器能力不作保证。",
                "When enabled, expert mode can select gallery images. An image is saved to DeepSeek's private directory, objectively described by the vision model, and then relayed to the expert model. Later turns in the same chat continue to capture image context. Vision results and server capabilities are not guaranteed.");
        add("【功能】AI 心跳", "[Feature] AI heartbeat");
        add("开启后，模块会按你设置的分钟间隔唤醒 DeepSeek，并使用绑定对话的近期上下文生成自然的主动消息。点按功能说明即可修改间隔，范围为 15 分钟到 7 天。请在想绑定的对话中直接说“以后心跳时来找我闲聊”；在另一个对话重新约定会切换周期心跳的绑定，不会建立全局约定。也可以说“5 天后晚上 6:37 来找我”安排只属于当前对话的一次性心跳，或让 AI 取消单次、周期或全部心跳。AI 会调用真实的本地调度能力，内部执行指令不会显示；模型回复会写入任务所属对话，无论 DeepSeek 是否在前台，模块应用都会同时发送系统通知，点按通知会回到该对话。关闭周期心跳不会删除已经确认的一次性任务，除非明确要求 AI 一并取消。该功能默认关闭，通知需要在模块应用中授权，系统省电策略可能让时间略有延迟。",
                "When enabled, the module wakes DeepSeek at the minute interval you choose and uses recent context from the bound chat to generate a natural proactive message. Tap the feature description to set an interval from 15 minutes to 7 days. In the chat you want to bind, say “come chat with me whenever the heartbeat runs.” Making a new agreement in another chat switches the recurring heartbeat binding instead of creating a global agreement. You can also say “come talk to me at 6:37 PM five days from now” to schedule a one-time heartbeat belonging only to the current chat, or ask the AI to cancel one-time, recurring, or all heartbeats. The AI uses real local scheduling and hides internal execution instructions. The model reply is added to the task's chat, and the module app also sends a system notification whether or not DeepSeek is in the foreground; tapping it returns to that chat. Turning off recurring heartbeats does not delete confirmed one-time tasks unless you explicitly ask the AI to cancel them too. The feature is off by default, notification permission is required in the module app, and battery policies may delay delivery slightly.");
        add("【问题】为什么专家模式第一轮能发图，后续轮却提示不支持？",
                "[Question] Why can expert mode send an image on the first turn but reject later turns?");
        add("解决办法：新版会按会话捕获每一轮完整图片 fragment，并在发送点识别专家模型。安装后先冷启动，再新建专家会话测试；服务器若调整模型能力仍可能拒绝，此时可关闭功能改用普通视觉模型。",
                "Solution: the current build captures complete image fragments for every turn and identifies the expert model at the send point. Cold-start after installation and test in a new expert chat. The server can still reject images after a capability change; disable the feature and use the normal vision model in that case.");
        add("解决办法：401 表示 Authorization: Bearer 后的密钥不匹配，可从控制页重新复制；503 表示原生传输或 PoW 尚未初始化，保持应用前台数秒后重试。连接被拒绝通常表示 DeepSeek 已被彻底退出、开关关闭或端口被占用；重新打开应用后查看控制页中的实际端口。",
                "Solution: 401 means the key after Authorization: Bearer does not match; copy it again from the control page. 503 means native transport or PoW is not initialized; keep the app foregrounded for a few seconds and retry. Connection refused usually means DeepSeek fully exited, the switch is off, or the port is occupied. Reopen the app and check the actual port on the control page.");
        add("【问题】为什么 Codex 能聊天但没有完整 apply_patch 工具？",
                "[Question] Why can Codex chat but not expose the complete apply_patch tool?");
        add("【问题】为什么 API 调用没有出现在聊天列表？",
                "[Question] Why don't API calls appear in the chat list?");
        add("解决办法：这是预期行为。API 复用独立隐藏会话以降低会话创建限流，但侧栏、编辑器和云目录会过滤它们；关闭服务时才集中走原生删除链清理，不会用每次创建和删除污染正常聊天。",
                "Solution: this is expected. The API reuses separate hidden sessions to reduce session-creation rate limits. The sidebar, editor, and cloud directory filter them. They are removed through the native deletion chain when the service is disabled, avoiding create/delete noise in normal chats.");
        add("【问题】Claude Code 的 /clear 或 /new 为什么看起来还在旧对话？",
                "[Question] Why do Claude Code /clear or /new still look like the old conversation?");
        add("【问题】为什么请求开始后不会立刻显示 thinking？",
                "[Question] Why doesn't thinking appear immediately after a request starts?");
        add("【建议】怎样更稳妥地使用这些功能？",
                "[Tip] How can I use these features more smoothly?");
        add("实验性功能 · 帮助与问题", "Experimental Features · Help & Questions");

        // General Help & Questions feature notes.
        add("【功能】系统提示词注入", "[Feature] System prompt injection");
        add("选择完整的 TXT/MD 文本后开启开关，模块会在发送请求时把它作为系统指令附加到用户原文前。",
                "Select a complete TXT/MD file and enable the switch. The module prepends it to the user's original input as a system instruction when sending a request. ");
        add("在线历史、数据库写入和旧数据迁移会清理这段包装，因此正常聊天页只应显示用户真正输入的内容。",
                "Online history, database writes, and legacy migration remove this wrapper, so the normal chat page should show only what the user actually entered.");
        add("【功能】回复保留（去安全审查替换）", "[Feature] Preserve replies (prevent safety-template replacement)");
        add("开启后会识别 CONTENT_FILTER 等替换事件，保留本机已经观察到的原始回复，并在冷启动同步时继续保护。",
                "When enabled, the module recognizes replacement events such as CONTENT_FILTER, preserves the original reply already observed on this device, and continues protecting it during cold-start sync. ");
        add("它不能改变服务器规则，也不能恢复开关启用前已经丢失的回答。",
                "It cannot change server rules or recover answers lost before the switch was enabled.");
        add("【功能】聊天记录多选删除", "[Feature] Multi-select chat deletion");
        add("打开开关后，在 DeepSeek 左侧会话列表长按进入多选，勾选后走宿主原生删除事件，同时清理本地会话表、",
                "After enabling, long-press a chat in the DeepSeek sidebar to enter multi-select. Confirmed items use the host's native delete event and also clean local session tables, ");
        add("消息表和 Deekseep 恢复副本。关闭开关会恢复宿主原来的长按菜单。",
                "message tables, and Deekseep recovery copies. Disabling restores the host's original long-press menu.");
        add("【功能】编辑聊天记录", "[Feature] Edit chat history");
        add("每次打开都会重新合并当前账号数据库、宿主已加载会话和最新内存历史。可修改标题、用户消息、AI 回复、",
                "Each opening remerges the current account database, host-loaded sessions, and latest in-memory history. You can edit titles, user messages, AI replies, ");
        add("思考内容和思考用时；图片选择会立即保存，不必再点顶部保存。",
                "reasoning content, and reasoning duration. Image selections save immediately without using the top Save button.");
        add("【功能】新建对话与追加消息", "[Feature] Create chats and append messages");
        add("手机端点编辑器左上角菜单，再点“新建对话”即可建立空白会话；进入任意会话后，底部可直接追加用户消息",
                "On a phone, open the editor's top-left menu and tap Create chat to make a blank chat. In any chat, use the bottom controls to append a user message ");
        add("或 AI 回复。追加动作属于当前会话，不是只能添加首条消息。",
                "or AI reply. Appends belong to the current chat and are not limited to the first message.");
        add("【功能】编辑器相册图片", "[Feature] Gallery images in the editor");
        add("在用户消息的图片管理中点“从相册选择并上传”。模块把长期副本保存到 DeepSeek files 目录，并建立",
                "In a user message's image manager, tap Select from gallery and upload. The module stores a durable copy in the DeepSeek files directory and creates a ");
        add("FileProvider 可读镜像；重启或缓存被清理时会尝试从长期副本重建。AI 消息不能附加用户图片。",
                "FileProvider-readable mirror. It attempts to rebuild the mirror from the durable copy after restart or cache clearing. AI messages cannot attach user images.");
        add("【功能】多账号管理与凭证导入导出", "[Feature] Multiple accounts and credential import/export");
        add("多账号页可添加、切换、移除账号，也可勾选单个或多个账号导出。导入会严格解析完整 JSON，逐个请求",
                "The account page can add, switch, and remove accounts and export one or more selected accounts. Import strictly parses complete JSON and requests ");
        add("DeepSeek 当前用户接口，只有外层和业务层都成功时才整包写入；响应若带账号 ID 还必须一致。导出 TXT 含明文 token，绝不能分享。",
                "DeepSeek's current-user endpoint for every candidate. Nothing is written until both transport and business layers succeed, and any returned account ID must match. Exported TXT contains plaintext tokens and must never be shared.");
        add("【功能】解锁 Google 登录", "[Feature] Unlock Google sign-in");
        add("开启后，模块只把国内登录页隐藏的 DeepSeek 原生 Google 项插回登录方式列表，微信、手机号等国内入口仍保留。",
                "When enabled, the module only inserts DeepSeek's hidden native Google item back into the mainland sign-in list. WeChat, phone, and other mainland options remain. ");
        add("点击后继续使用宿主 Credential Manager 获取 Google ID Token，并交给 DeepSeek 官方 Google 登录接口换票；模块不会读取或记录该 token。",
                "Tapping it still uses the host Credential Manager for a Google ID token and exchanges it through DeepSeek's official Google sign-in endpoint. The module does not read or log that token.");
        add("【功能】解锁微信与手机号登录", "[Feature] Unlock WeChat and phone sign-in");
        add("这是独立于 Google 的一个联合开关。开启后会在海外登录方式列表中同时补回 DeepSeek 原生微信项和短信手机号项，",
                "This combined switch is independent of Google. It restores both DeepSeek's native WeChat and SMS phone items to the overseas sign-in list, ");
        add("保留 Google、密码和注册等已有选项；模块不接管凭证，也不会伪造登录成功。",
                "while preserving Google, password, registration, and other existing options. The module neither handles credentials nor fakes successful sign-in.");
        add("【功能】Markdown、搜索与统计", "[Feature] Markdown, search, and statistics");
        add("Markdown 工具把本地会话导出到应用外部目录；全局搜索覆盖用户输入、AI 回复和思考内容；",
                "The Markdown tool exports local chats to app external storage. Global search covers user input, AI replies, and reasoning. ");
        add("会话统计按账号汇总会话数、消息数和字数。外部导出文件需自行保护。",
                "Chat statistics summarize chat, message, and character counts by account. Protect external export files yourself.");
        add("【功能】手动与自动数据库备份", "[Feature] Manual and automatic database backup");
        add("“立即备份”会复制聊天数据库到应用外部目录。自动备份开启后按启动时间间隔保存到应用内部备份目录，",
                "Back up now copies chat databases to app external storage. Automatic backup saves to the internal backup directory at startup-based intervals ");
        add("并限制保留数量。数据库仍可能在复制时变化，重要操作前建议退出聊天页后再手动备份。",
                "and limits retained copies. A database can still change while being copied; before important work, leave the chat page and make a manual backup.");
        add("【功能】记录服务器返回（诊断）", "[Feature] Log server responses (diagnostics)");
        add("只在排查时开启。它会记录 SSE 事件和部分网络诊断信息，日志可能包含聊天内容或服务器错误；",
                "Enable only while troubleshooting. It records SSE events and some network diagnostics; logs may include chat content or server errors. ");
        add("问题确认后应立即关闭，并在分享日志前自行脱敏。",
                "Disable it immediately after diagnosis and redact logs before sharing.");

        // General Help & Questions troubleshooting.
        add("【问题】为什么会提示“当前显示最新内存记录，等待 DeepSeek 落库后再编辑”？",
                "[Question] Why does it say ‘Showing the latest in-memory record; wait for DeepSeek to save it before editing’?");
        add("解决办法：这是为了避免把不完整的内存快照强行写进数据库。先回到原生会话页等待消息加载和落库，",
                "Solution: this prevents an incomplete in-memory snapshot from being forced into the database. Return to the native chat and wait for messages to load and persist, ");
        add("再重新点选该会话或关闭后重开编辑器。仍提示时，保持网络可用并冷启动 DeepSeek 后再试。",
                "then reselect the chat or reopen the editor. If it persists, keep networking available, cold-start DeepSeek, and retry.");
        add("【问题】为什么会提示“完整在线历史仍在加载”？",
                "[Question] Why does it say ‘Complete online history is still loading’?");
        add("解决办法：当前只拿到增量消息，还没有完整基线，因此禁止追加或物化。先在原生界面打开该对话并等待加载完成，",
                "Solution: only incremental messages are available without a complete baseline, so appending or materializing is blocked. Open the chat in the native UI and wait for loading to finish, ");
        add("然后回到编辑器重新选择；不要连续点击添加按钮。",
                "then return to the editor and reselect it. Do not repeatedly tap an Add button.");
        add("【问题】为什么编辑器提示“未找到聊天数据库”或“没有本地或已加载的对话”？",
                "[Question] Why does the editor say ‘Chat database not found’ or ‘No local or loaded chats’?");
        add("解决办法：确认 DeepSeek 已登录正确账号，并在原生会话列表打开一次目标对话；随后重新进入编辑器。",
                "Solution: confirm DeepSeek is signed into the correct account and open the target chat once from the native list, then reopen the editor. ");
        add("刚切号时需要等待宿主建立该账号数据库。不要清除 DeepSeek 应用数据。",
                "After switching accounts, wait for the host to create that account's database. Do not clear DeepSeek app data.");
        add("【问题】为什么保存或添加时提示“在线历史刚刚更新，请重新打开后再试”？",
                "[Question] Why does save/add say ‘Online history just changed; reopen and try again’?");
        add("解决办法：保存前服务器同步了更新版本，模块为防止覆盖新消息而回滚了整个事务。重新点选对话，核对最新内容后",
                "Solution: the server synchronized a newer version before saving, so the module rolled back the whole transaction to avoid overwriting new messages. Reselect the chat, verify the latest content, ");
        add("再编辑；本次失败不会只写一半。",
                "and edit again. This failure does not leave a partial write.");
        add("【问题】为什么新建对话短暂出现后消失，或点开提示“对话已删除”？",
                "[Question] Why does a new chat briefly appear and disappear, or open as ‘Chat deleted’?");
        add("解决办法：新版通过 sidecar 和原生列表并集保护编辑器本地会话，同时允许服务器新增会话进入。请确认安装的是",
                "Solution: the current build protects editor-local chats using the union of sidecar and native lists while still accepting new server chats. Confirm you installed ");
        add("同一最新版模块并完整冷启动；旧版本创建的异常条目可在编辑器打开并重新保存一次。",
                "the same latest module build and cold-started fully. Open and save an abnormal entry created by an old build once in the editor.");
        add("【问题】为什么点一次“新建对话”偶尔出现两个同名对话？",
                "[Question] Why can one tap on ‘Create chat’ occasionally produce two chats with the same name?");
        add("解决办法：新版有点击防抖和在途锁。出现时先不要重复点击，关闭编辑器再打开确认；若仍为两个真实条目，",
                "Solution: the current build has click debouncing and an in-flight lock. Do not tap again; close and reopen the editor to verify. If two real entries remain, ");
        add("勾选多余的一条删除。安装新版后必须完整重启 DeepSeek，不能只覆盖安装后继续旧进程。",
                "select and delete the extra one. Fully restart DeepSeek after installing the new build instead of continuing the old process after an overwrite install.");
        add("【问题】为什么相册图片提示保存失败、写入失败或“对话已经切换”？",
                "[Question] Why does a gallery image report save failure, write failure, or ‘Chat changed’?");
        add("解决办法：保持目标对话不变，确认系统文件选择器授予了读取权限，并重新选择图片。“对话已经切换”是防止异步",
                "Solution: remain in the target chat, confirm the system picker granted read access, and select the image again. ‘Chat changed’ prevents an asynchronous ");
        add("上传把图片写到错误会话；文件可能已保存，但聊天记录不会被误改。",
                "upload from writing the image into the wrong chat. The file may be saved, but chat history is not modified incorrectly.");
        add("【问题】为什么旧图片提示“图片凭证刷新失败”？",
                "[Question] Why does an old image say ‘Image credential refresh failed’?");
        add("解决办法：旧服务器图片可能只有短期访问凭证。先在 DeepSeek 原生聊天页打开该图片并保持网络可用，再回编辑器重试。",
                "Solution: an older server image may have only a short-lived access credential. Open it in the native DeepSeek chat with networking available, then retry in the editor. ");
        add("从新版相册入口添加的图片使用本地长期副本，不依赖服务器长期凭证。",
                "Images added through the current gallery entry use a durable local copy and do not depend on long-lived server credentials.");
        add("【问题】为什么追加 AI 回复后，用户消息的附带图片消失？",
                "[Question] Why does a user message's image disappear after appending an AI reply?");
        add("解决办法：新版在图片选择时立即保存 FILE fragment，追加 USER/AI 前还会再次保存未提交选择。若是旧版本产生的数据，",
                "Solution: the current build immediately saves the FILE fragment on image selection and saves any pending selection again before appending USER/AI. For data from an older build, ");
        add("重新选择图片并等待“已持久保存并附加”提示后，再追加回复。",
                "select the image again, wait for the durable-save-and-attach confirmation, and only then append the reply.");
        add("【问题】为什么重启后打开带图片的对话变成空白？",
                "[Question] Why does a chat with images become blank after restart?");
        add("解决办法：新版会在启动早期恢复 sidecar、消息头和图片镜像。确认未清除 DeepSeek 私有 files 目录，并安装后完整冷启动。",
                "Solution: the current build restores sidecars, message heads, and image mirrors early at startup. Confirm the private DeepSeek files directory was not cleared and perform a full cold start after installation. ");
        add("若旧版本已经把消息表覆盖为空，模块无法凭空恢复没有备份的数据，可检查数据库备份。",
                "If an older build already emptied the message table, the module cannot recreate unbacked data; check database backups.");
        add("【问题】为什么重启后系统提示词出现在用户消息里？",
                "[Question] Why does the system prompt appear inside a user message after restart?");
        add("解决办法：新版会在在线历史、仓库写入和启动迁移三层清理。保持系统提示词文件不变，完整重启一次让迁移执行；",
                "Solution: the current build cleans it at online history, repository write, and startup migration layers. Keep the system prompt file unchanged and fully restart once to run migration. ");
        add("若数据库正被占用，下一次启动会继续重试。",
                "If the database is busy, the next start retries.");
        add("【问题】为什么原回复重启后又变成“这个问题我暂时无法回答”？",
                "[Question] Why does the original answer become ‘I can't answer that right now’ again after restart?");
        add("解决办法：必须在回复第一次生成时已开启回复保留，模块才能记录原内容并保护冷启动同步。更新后完整重启；",
                "Solution: reply preservation must already be enabled when the answer is first generated so the module can record the original and protect cold-start sync. Fully restart after updating. ");
        add("已经只剩模板且没有本地原文副本的旧消息无法恢复。",
                "An old message that contains only the template and has no local original copy cannot be recovered.");
        add("【问题】为什么多选删除显示已提交，但本地删除为 0 或重启后又出现？",
                "[Question] Why does multi-delete say submitted but remove zero locally or reappear after restart?");
        add("解决办法：最新版先发送宿主真实 h61 删除事件，再按账号数据库清理会话、消息表和恢复副本。确认当前账号正确并重新打开",
                "Solution: the current build first sends the host's real h61 delete event, then cleans sessions, message tables, and recovery copies in the account database. Confirm the current account and reopen ");
        add("编辑器刷新；若服务器删除失败，云端副本仍可能重新同步，应在网络恢复后从原生列表再删一次。",
                "the editor to refresh. If server deletion failed, a cloud copy may sync again; delete it once more from the native list after networking recovers.");
        add("【问题】为什么账号 JSON 导入失败或提示凭证无效？",
                "[Question] Why does account JSON import fail or say the credential is invalid?");
        add("解决办法：只能导入完整 UTF-8 JSON；id、token、email、mobile_number、status、chat_status、id_profiles 和",
                "Solution: only complete UTF-8 JSON is accepted. The id, token, email, mobile_number, status, chat_status, id_profiles, and ");
        add("need_birthday 字段及类型必须齐全。过期 token、外层/业务 code 非 0、网络失败或服务器返回的 ID 不一致都会整包拒绝。",
                "need_birthday fields and types must all be present. Expired tokens, nonzero transport/business codes, network failure, or a mismatched server ID reject the entire package. ");
        add("请从仍正常登录的设备重新导出，不要手工拼 token。",
                "Export again from a device that is still signed in; do not assemble tokens manually.");
        add("【问题】为什么导出账号时反复提示明文凭证风险？",
                "[Question] Why does account export repeatedly warn about plaintext credentials?");
        add("解决办法：这是有意的安全提示，不应关闭。导出文件等同于登录钥匙；只保存到你控制的位置，不通过聊天软件或网盘分享，",
                "Solution: this intentional safety warning should not be disabled. An export file is equivalent to a sign-in key. Store it only somewhere you control and never share it through messaging or cloud drives. ");
        add("导入完成后删除文件。模块不会把 token 写进诊断日志。",
                "Delete the file after importing. The module does not write tokens to diagnostic logs.");
        add("【问题】为什么添加或切换账号后必须重启 DeepSeek？",
                "[Question] Why must DeepSeek restart after adding or switching accounts?");
        add("解决办法：宿主会在进程启动时缓存 key_user_info 和账号仓库，运行中只改文件不能保证所有页面一致。模块只重启",
                "Solution: the host caches key_user_info and the account repository at process startup, so changing files at runtime cannot keep every page consistent. The module restarts only ");
        add("DeepSeek 自己的进程，让宿主按新凭证冷启动；不会停止其他应用。",
                "the DeepSeek process so the host cold-starts with the new credential; other apps are not stopped.");
        add("【问题】为什么开启后仍看不到 Google 登录，或点击后提示不可用？",
                "[Question] Why is Google sign-in still missing or unavailable after enabling it?");
        add("解决办法：先在已登录状态开启“解锁 Google 登录”，再从多账号页添加账号并完整重启 DeepSeek。设备需要可用的",
                "Solution: while signed in, enable Unlock Google sign-in, then add an account from Multiple accounts and fully restart DeepSeek. The device needs working ");
        add("Google Play 服务和网络环境。模块只恢复客户端原生入口，不绕过 DeepSeek 服务器的地区、账号或风控判断；",
                "Google Play services and network access. The module only restores the native client entry and does not bypass DeepSeek server region, account, or risk decisions. ");
        add("若官方接口明确拒绝，请勿反复提交，关闭开关后改用手机号、微信等正常入口。",
                "If the official endpoint explicitly rejects it, do not submit repeatedly. Disable the switch and use a normal option such as phone or WeChat.");
        add("【问题】为什么海外环境仍看不到微信或手机号登录？",
                "[Question] Why are WeChat or phone sign-in still missing in an overseas environment?");
        add("解决办法：开启“解锁微信与手机号登录”后完整重启 DeepSeek，再进入登录页；它不会随 Google 开关自动开启。",
                "Solution: enable Unlock WeChat and phone sign-in, fully restart DeepSeek, and then open the sign-in page. It is not enabled automatically with the Google switch. ");
        add("微信入口还需要设备安装可用的微信客户端，短信入口需要官方服务支持当前号码与地区。服务器拒绝时模块不会绕过。",
                "WeChat also requires a working WeChat client on the device, and SMS requires official support for the number and region. The module does not bypass a server rejection.");
        add("【问题】为什么模块启动页显示“待验证”，LSPosed 明明已经启用？",
                "[Question] Why does the module launch page say ‘Pending verification’ when LSPosed is enabled?");
        add("解决办法：通用版不需要把模块自身加入作用域，只需勾选 DeepSeek。",
                "Solution: the universal build does not need the module app in scope; select DeepSeek only.");
        add("请确认模块总开关已开、作用域勾选 DeepSeek，然后启动一次 DeepSeek 再返回模块页；",
                "Confirm the module master switch is on and DeepSeek is selected in scope, then start DeepSeek once and return to the module page; ");
        add("不要用旧版的“自我 Hook”状态作为判据。",
                "do not rely on the old self-hook state.");
        add("【问题】为什么搜索、统计或编辑器显示的账号不对？",
                "[Question] Why do search, statistics, or the editor show the wrong account?");
        add("解决办法：先在多账号页确认“当前”标记，完成切号重启后再打开工具。编辑器默认只显示当前账号；若启用“显示所有账号”，",
                "Solution: first confirm the Current marker on the account page, finish the restart after switching, and only then open tools. The editor shows only the current account by default. If Show all accounts is enabled, ");
        add("保存前必须核对顶部账号和目标数据库。",
                "verify the account and target database at the top before saving.");

        // Chat editor.
        add("已思考", "Reasoned");
        add("无法打开聊天编辑器: ", "Could not open chat editor: ");
        add("已上传图片", "Uploaded image");
        add("新建用户对话", "Create user chat");
        add("新建 AI 对话", "Create AI chat");
        add("新对话", "New chat");
        add("未找到聊天数据库", "Chat database not found");
        add("对话历史", "Chat history");
        add("＋新建对话", "+ Create chat");
        add("选择", "Select");
        add("当前账号", "Current account");
        add("（显示所有账号）", " (showing all accounts)");
        add("只显示当前账号", "Current account only");
        add("显示所有账号", "Show all accounts");
        add("聊天记录范围", "Chat history scope");
        add("请先新建或选择一个对话", "Create or select a chat first");
        add("添加用户消息", "Add user message");
        add("添加 AI 回复", "Add AI reply");
        add("直接输入要追加到当前对话的内容（可留空）",
                "Enter content to append to the current chat (may be empty)");
        add("创建后可点消息下方的“图片”入口，直接从系统相册上传并附加。 ",
                "After creating it, tap Images under the message to upload and attach directly from the system gallery. ");
        add("添加", "Add");
        add("新建对话失败，请确认数据库可写", "Could not create chat; confirm the database is writable");
        add("已新建空白对话，可在底部添加用户消息或 AI 回复",
                "Created a blank chat; add a user message or AI reply at the bottom");
        add("完整在线历史仍在加载，请稍后重新点选后再添加",
                "Complete online history is still loading; reselect the chat later before adding");
        add("添加失败或在线历史刚刚更新，请重新点选对话后再试",
                "Add failed or online history just changed; reselect the chat and try again");
        add("已追加到当前对话", "Appended to the current chat");
        add("删除(", "Delete (");
        add("删除", "Delete");
        add("已选择 ", "Selected ");
        add("选择对话", "Select chats");
        add("先选择要删除的对话", "Select chats to delete first");
        add("全选", "Select all");
        add("没有匹配到可删除的对话", "No matching chats can be deleted");
        add("正在删除 ", "Deleting ");
        add("DeepSeek 已删除 ", "DeepSeek deleted ");
        add(" 个，本地已清理 ", "; cleaned locally: ");
        add("原生失败 ", "Native failures: ");
        add("本地失败 ", "Local failures: ");
        add("删除 ", "Delete ");
        add(" 个对话", " chats");
        add(" 个", " items");
        add("会先走 DeepSeek 原生删除链路提交服务器删除，再清理本机会话、",
                "DeepSeek's native deletion chain submits server deletion first, then cleans local sessions, ");
        add("消息表和 Deekseep 恢复副本。", "message tables, and Deekseep recovery copies.");
        add("已请求 DeepSeek 删除 ", "Asked DeepSeek to delete ");
        add(" 个，本地已移除 ", "; removed locally: ");
        add("，未取得原生链路 ", "; native path unavailable: ");
        add("，本地失败 ", "; local failures: ");
        add("聊天记录", "Chats");
        add("帮助与反馈", "Help & feedback");
        add("保存", "Save");
        add("＋ 用户消息", "+ User message");
        add("＋ AI 回复", "+ AI reply");
        add("没有本地或已加载的对话", "No local or loaded chats");
        add("未命名对话", "Untitled chat");
        add("这是一个空白对话\n请用底部按钮添加用户消息或 AI 回复",
                "This is a blank chat\nUse the bottom buttons to add a user message or AI reply");
        add("正在从 DeepSeek 加载该对话记录…", "Loading this chat from DeepSeek…");
        add("暂时无法请求该云端对话\n请先返回 DeepSeek 主界面刷新侧栏后重试",
                "This cloud chat is temporarily unavailable\nReturn to the DeepSeek home screen, refresh the sidebar, and try again");
        add("该对话没有消息记录", "This chat has no messages");
        add("未能取得该对话的在线记录\n请检查网络后重新点选此对话",
                "Could not obtain this chat's online history\nCheck networking and reselect the chat");
        add("该对话目前只有云端目录，尚未取得在线消息记录",
                "Only the cloud directory entry is available; online messages have not loaded yet");
        add("当前显示的是 DeepSeek 内存记录（只读）\n完整在线历史返回后即可编辑保存",
                "Showing DeepSeek's in-memory record (read-only)\nEditing is available after complete online history returns");
        add("已刷新到 DeepSeek 最新内存记录（暂时只读）\n",
                "Refreshed to DeepSeek's latest in-memory record (temporarily read-only)\n");
        add("等待宿主落库后重新打开即可编辑",
                "Reopen after the host persists it to enable editing");
        add("添加思考内容", "Add reasoning");
        add(" 秒", " sec");
        add("在此输入思考内容（长按进入编辑）", "Enter reasoning here (long-press to edit)");
        add("思考用时（秒）", "Reasoning time (seconds)");
        add("例如 12.5", "For example, 12.5");
        add("图片 0 张 · 从相册添加", "0 images · Add from gallery");
        add("图片 ", "Images: ");
        add(" 张 · 相册 / 管理", " · Gallery / Manage");
        add("当前显示最新内存记录，等待 DeepSeek 落库后再修改图片",
                "Showing the latest in-memory record; wait for DeepSeek to persist it before changing images");
        add("用户消息图片", "User message images");
        add("从相册选择并上传", "Select from gallery and upload");
        add("没有旧图片。可直接从相册选择一张新图片。",
                "There are no existing images. You can select a new image from the gallery.");
        add("也可以勾选本机聊天记录中已上传过的图片：",
                "You can also select images previously uploaded in local chats:");
        add("全部移除", "Remove all");
        add("应用", "Apply");
        add("当前记录还不能附加图片", "Images cannot be attached to the current record yet");
        add("正在保存图片", "Saving image");
        add("正在保存到 DeepSeek 私有目录，并同步登记图片信息…",
                "Saving to DeepSeek's private directory and registering image metadata…");
        add("图片保存失败", "Image save failed");
        add("无法从系统相册读取或复制这张图片；聊天记录没有改变。",
                "Could not read or copy this image from the system gallery; chat history was not changed.");
        add("对话已经切换", "Chat changed");
        add("图片已上传，但为了避免加到错误对话，本次没有写入聊天记录。请回到目标消息重新选择。 ",
                "The image was uploaded but was not written to chat history to avoid attaching it to the wrong chat. Return to the target message and select it again. ");
        add("图片写入失败", "Image write failed");
        add("图片文件已保存，但未能附加到这条用户消息；原聊天记录没有改变，请重新打开后再试。",
                "The image file was saved but could not be attached to this user message. The original chat was unchanged; reopen and try again.");
        add("图片已持久保存并附加到用户消息", "Image saved durably and attached to the user message");
        add("正在准备图片", "Preparing image");
        add("正在向 DeepSeek 获取新的图片访问凭证…", "Requesting a new image access credential from DeepSeek…");
        add("图片凭证刷新失败", "Image credential refresh failed");
        add("无法刷新“", "Could not refresh ‘");
        add("”。请确认网络可用，返回 DeepSeek 聊天页一次后再打开编辑器重试；原聊天记录没有改变。",
                "’. Confirm networking is available, visit the DeepSeek chat page once, then reopen the editor and retry. The original chat was unchanged.");
        add("图片凭证已准备完成，但未能写入这条用户消息；原聊天记录没有改变。",
                "The image credential is ready but could not be written to this user message. The original chat was unchanged.");
        add("图片已刷新并保存", "Image refreshed and saved");
        add("当前显示最新内存记录，等待 DeepSeek 落库后再编辑",
                "Showing the latest in-memory record; wait for DeepSeek to persist it before editing");
        add("最新记录尚未落库，请重新打开编辑器后再保存",
                "The latest record is not persisted yet; reopen the editor before saving");
        add("请先输入思考内容，再设置思考用时", "Enter reasoning before setting its duration");
        add("思考用时必须是大于或等于 0 的秒数", "Reasoning duration must be a number of seconds greater than or equal to zero");
        add("保存失败或在线历史已更新，请重新打开后再试",
                "Save failed or online history changed; reopen and try again");
        add("已保存 ", "Saved ");
        add(" 处，重启 DeepSeek 生效", " changes; restart DeepSeek to apply");
        add("无改动", "No changes");
        add("请先长按一条消息进入编辑，再插入格式",
                "Long-press a message to edit it before inserting formatting");
        add("插入 Markdown 格式", "Insert Markdown formatting");
        add("加粗文字", "Bold text");
        add("斜体文字", "Italic text");
        add("粗斜体文字", "Bold italic text");
        add("删除线文字", "Strikethrough text");
        add("行内代码", "Inline code");
        add("代码块", "Code block");
        add("一级标题", "Heading 1");
        add("二级标题", "Heading 2");
        add("三级标题", "Heading 3");
        add("四级标题", "Heading 4");
        add("五级标题", "Heading 5");
        add("六级标题", "Heading 6");
        add("\u2022 无序列表", "• Bulleted list");
        add("1. 有序列表", "1. Numbered list");
        add("引用文字", "Quoted text");
        add("分割线 \u2500\u2500\u2500", "Divider ───");
        add("链接", "Link");
        add("图片", "Image");
        add("加粗", "Bold");
        add("斜体", "Italic");
        add("粗斜体", "Bold italic");
        add("删除线", "Strikethrough");
        add("无序列表", "Bulleted list");
        add("有序列表", "Numbered list");
        add("引用", "Quote");
        add("内容", "Content");
        add("插入", "Insert");
        add("完成", "Done");
        add("语言（可留空，如 java）", "Language (optional, e.g. java)");
        add("代码", "Code");
        add("插入代码块", "Insert code block");
        add("图片描述（可留空）", "Image description (optional)");
        add("显示文字", "Display text");
        add("链接地址", "Link URL");
        add("插入图片", "Insert image");
        add("插入链接", "Insert link");

        // Multiple-account UI and credential validation.
        add("多账号", "Multiple accounts");
        add("未检测到已登录账号。请先在 DeepSeek 正常登录一个账号。",
                "No signed-in account was detected. Sign in normally to one account in DeepSeek first.");
        add("＋  添加账号", "+  Add account");
        add("导入账号", "Import accounts");
        add("导出账号", "Export accounts");
        add("点击账号切换（会重启 DeepSeek）。切换前当前账号自动备份，长按可移除已保存账号。",
                "Tap an account to switch (DeepSeek will restart). The current account is backed up automatically before switching; long-press to remove a saved account. ");
        add("添加账号会登出当前账号进入登录页。导入会先严格校验 JSON，再逐个请求 DeepSeek ",
                "Adding an account signs out the current account and opens sign-in. Import strictly validates JSON first, then requests DeepSeek ");
        add("确认凭证和账号 ID；请求身份与当前安装的宿主版本一致，批量校验会自动控制频率。",
                "to verify every credential and account ID. Requests match the installed host version and batch validation is automatically paced. ");
        add("全部有效后才一次性写入。导出文件含明文登录凭证，请勿分享。",
                "Nothing is written until every candidate is valid. Export files contain plaintext sign-in credentials and must not be shared.");
        add("当前", "Current");
        add("不能移除当前登录账号", "The currently signed-in account cannot be removed");
        add("切换账号", "Switch account");
        add("切换到「", "Switch to ‘");
        add("」？\n将重启 DeepSeek 以新账号启动，当前账号已自动备份。",
                "’?\nDeepSeek will restart with the new account. The current account has been backed up automatically.");
        add("切换并重启", "Switch and restart");
        add("切换失败", "Switch failed");
        add("无法写入 DeepSeek 登录态，未执行重启。",
                "Could not write DeepSeek sign-in state; restart was not performed.");
        add("正在切换…", "Switching…");
        add("添加账号", "Add account");
        add("将登出当前账号并进入原生登录页以登录新账号（支持微信、手机号等；若已开启“解锁 Google 登录”，",
                "This signs out the current account and opens the native page to sign in to a new account (WeChat, phone, and other methods are supported; if Unlock Google sign-in is enabled, ");
        add("登录页也会显示宿主原生 Google 入口）。\n",
                "the host's native Google entry also appears).\n");
        add("当前账号已自动备份，登录新号后可在多账号里切回。是否继续？",
                "The current account has been backed up automatically and can be restored from Multiple accounts after signing in to the new one. Continue?");
        add("登出并登录新号", "Sign out and add new account");
        add("操作失败", "Operation failed");
        add("无法清除当前登录态，未执行重启。",
                "Could not clear the current sign-in state; restart was not performed.");
        add("正在进入登录页…", "Opening sign-in…");
        add("移除已保存账号", "Remove saved account");
        add("从多账号列表移除「", "Remove ‘");
        add("仅删除本模块保存的凭证备份，不影响服务器数据和本地聊天记录。",
                "Only the credential backup saved by this module is removed. Server data and local chats are not affected.");
        add("移除", "Remove");
        add("已移除", "Removed");
        add("移除失败", "Removal failed");
        add("账号槽文件未能更新，请稍后重试。",
                "The account-slot file could not be updated. Try again later.");
        add("导入账号凭证", "Import account credentials");
        add("只接受完整 JSON。模块会先检查全部账号的字段和类型，再使用每个候选 token 请求 ",
                "Only complete JSON is accepted. The module checks fields and types for every account before using each candidate token to request ");
        add("DeepSeek 当前用户接口；请求会使用当前安装版本的宿主身份并自动限速，且必须同时通过外层和业务层校验。",
                "DeepSeek's current-user endpoint. Requests use the installed host identity, are automatically paced, and must pass both transport and business validation. ");
        add("服务器若返回账号 ID，还必须与文件一致，",
                "Any account ID returned by the server must also match the file, ");
        add("之后才会一次性加入多账号列表。\n\n",
                "and only then are all accounts added to the list in one transaction.\n\n");
        add("校验前不会写入 MMKV、数据库或账号槽。请只导入你本人合法持有的凭证。",
                "Nothing is written to MMKV, databases, or account slots before validation. Import only credentials you lawfully own.");
        add("选择 JSON/TXT", "Choose JSON/TXT");
        add("无法打开文件选择器", "Could not open file picker");
        add("请确认系统文件选择器可用后重试。",
                "Confirm the system file picker is available and try again.");
        add("没有可导出的账号", "No accounts to export");
        add("请先登录或添加至少一个账号。", "Sign in or add at least one account first.");
        add("选择要导出的账号", "Select accounts to export");
        add("导出的是可登录账号的明文凭证。拿到文件的人可能直接使用你的账号，请勿分享，使用后及时删除。",
                "The export contains plaintext credentials that can sign in to your accounts. Anyone with the file may be able to use them. Never share it and delete it promptly after use.");
        add("  · 当前", "  · Current");
        add("导出所选", "Export selected");
        add("请至少勾选一个账号", "Select at least one account");
        add("无法导出", "Could not export");
        add("已选择", "Selected");
        add("未选择", "Not selected");
        add("无法打开保存位置", "Could not open save location");
        add("导出内容已失效，请重新选择账号", "Export selection expired; select accounts again");
        add("目标文件不可写", "Destination file is not writable");
        add("目标文件写入失败", "Could not write destination file");
        add("导出完成", "Export complete");
        add("已保存：", "Saved: ");
        add("\n\n文件含明文登录凭证，请妥善保管且不要分享。",
                "\n\nThe file contains plaintext sign-in credentials. Protect it and do not share it.");
        add("导出失败", "Export failed");
        add("正在读取并校验账号文件…", "Reading and validating account file…");
        add("正在向 DeepSeek 验证 ", "Validating with DeepSeek: ");
        add("账号「", "Account ‘");
        add("」验证失败：", "’ validation failed: ");
        add("全部凭证有效，正在写入账号列表…", "All credentials are valid; writing account list…");
        add("账号槽文件写入失败，未完成导入", "Account-slot write failed; import was not completed");
        add("格式错误：", "Format error: ");
        add("文件不是完整有效的 UTF-8 文本", "The file is not complete valid UTF-8 text");
        add("导入失败", "Import failed");
        add("导入完成", "Import complete");
        add("已验证并加入 ", "Validated and added ");
        add(" 个账号。当前登录账号未改变，可在列表中随时切换。",
                " accounts. The currently signed-in account was not changed; switch from the list at any time.");
        add("\n\n没有写入任何候选登录凭证。",
                "\n\nNo candidate sign-in credentials were written.");
        add("无法读取所选文件", "Could not read selected file");
        add("文件超过 1 MiB 上限", "File exceeds the 1 MiB limit");
        add("凭证格式校验失败", "Credential format validation failed");
        add("账号校验已取消", "Account validation was cancelled");
        add("连接 DeepSeek 服务器超时", "Connection to the DeepSeek server timed out");
        add("无法连接或解析 DeepSeek 校验结果", "Could not connect to or parse the DeepSeek validation result");
        add("DeepSeek 暂时限流，请稍后重试", "DeepSeek is temporarily rate limiting; try again later");
        add("DeepSeek 暂时限流（HTTP 429），请稍后再导入",
                "DeepSeek is temporarily rate limiting (HTTP 429); import again later");
        add("服务器校验失败（HTTP ", "Server validation failed (HTTP ");
        add("凭证已失效或被服务器拒绝（code=", "Credential expired or was rejected by the server (code=");
        add("服务器未确认该凭证有效（code=", "The server did not confirm this credential as valid (code=");
        add("服务器校验响应缺少 biz_code", "Server validation response is missing biz_code");
        add("服务器未确认该凭证有效（biz_code=", "The server did not confirm this credential as valid (biz_code=");
        add("服务器返回的账号与文件中的账号不一致", "The account returned by the server does not match the file");
        add("无法解析 DeepSeek 校验结果", "Could not parse the DeepSeek validation result");

        // Search, export, statistics, activation, and keepalive surfaces.
        add("输入关键词", "Enter keywords");
        add("搜索全部账号（默认只搜当前登录账号）",
                "Search all accounts (default: current account only)");
        add("搜索聊天记录", "Search chats");
        add("搜索", "Search");
        add("搜索中…", "Searching…");
        add("用户输入", "User input");
        add("模型回答", "Model answer");
        add("深度思考", "Deep reasoning");
        add("该对话不在当前账号的原生侧栏列表中（已删除、未同步或属于其他账号）",
                "This chat is not in the current account's native sidebar (it may be deleted, unsynced, or belong to another account)");
        add("未找到「", "No results for ‘");
        add("」命中 ", "’: ");
        add(" 条", " matches");
        add("当前登录账号的原生会话列表中没有该对话",
                "This chat is not in the native chat list for the signed-in account");
        add("立即备份", "Back up now");
        add("失败: ", "Failed: ");
        add("**用户**", "**User**");
        add("**助手**", "**Assistant**");
        add("正在导出…", "Exporting…");
        add("没有可导出的本地会话", "No local chats to export");
        add("已导出 ", "Exported ");
        add(" 个会话到\n", " chats to\n");
        add("我 · ", "Me · ");
        add("正在备份…", "Backing up…");
        add("没有可备份的数据库", "No databases to back up");
        add("已备份 ", "Backed up ");
        add(" 个数据库到\n", " databases to\n");
        add("统计中…", "Calculating…");
        add(" 会话 / ", " chats / ");
        add(" 消息\n", " messages\n");
        add("本地账号数：", "Local accounts: ");
        add("会话总数：", "Total chats: ");
        add("消息总数：", "Total messages: ");
        add("正文+思考总字数：", "Answer + reasoning characters: ");
        add("按账号：\n", "By account:\n");
        add("DeepSeek 模块", "DeepSeek module");
        add("版本", "Version");
        add("　·　编译于 ", " · Built ");
        add("\u25CF  已激活", "●  Active");
        add("\u25CB  待验证", "○  Pending verification");
        add("DeepSeek 目标进程最近已验证传统 Xposed 注入。",
                "The DeepSeek target process recently verified traditional Xposed injection.");
        add("尚未收到 DeepSeek 目标回报。请在传统 Xposed/FPA 中启用模块、勾选 ",
                "No report has been received from the DeepSeek target. Enable the module and select ");
        add("DeepSeek，然后启动一次 DeepSeek。",
                "DeepSeek in traditional Xposed/FPA, then start DeepSeek once.");
        add("LSPosed 服务已连接，DeepSeek 目标进程也已验证注入。",
                "LSPosed service is connected and the DeepSeek target process verified injection.");
        add("DeepSeek 目标进程最近已验证注入；框架服务会在可用时自动重连。",
                "The DeepSeek target process recently verified injection; the framework service reconnects automatically when available.");
        add("\u25CF  已启用", "●  Enabled");
        add("LSPosed 已连接本模块。启动一次 DeepSeek 后，将进一步验证目标作用域。 ",
                "LSPosed is connected to this module. Start DeepSeek once to verify target scope. ");
        add("尚未收到现代 Xposed 服务或 DeepSeek 目标回报。请在 LSPosed 启用模块、",
                "No modern Xposed service or DeepSeek target report has been received. Enable the module in LSPosed, ");
        add("勾选 DeepSeek，然后启动一次 DeepSeek。无需勾选模块应用自身。",
                "select DeepSeek, and start DeepSeek once. The module app itself does not need to be selected.");
        add("请授予 Deekseep 储存权限", "Grant Deekseep storage permission");
        add("储存权限已授予", "Storage permission granted");
        add("未授予储存权限", "Storage permission not granted");
        add("DeepSeek 长时间未确认保活，服务已自动停止",
                "DeepSeek did not confirm keepalive for an extended period; the service stopped automatically");
        add("发送保活心跳失败：", "Keepalive heartbeat failed: ");
        add("模块上下文不可用", "Module context is unavailable");
        add("启动", "Start");
        add("停止", "Stop");
        add("前台保活失败：", "Foreground keepalive failed: ");
        add("前台保活初始化失败：", "Foreground keepalive initialization failed: ");
        add("拒绝了无效的保活启动请求", "Rejected an invalid keepalive start request");
        add("正在保持后台监听与流式响应稳定",
                "Keeping background listening and streaming responses stable");
        add("CPU 保活不可用：", "CPU keepalive unavailable: ");

        // Account JSON codec validation.
        add("文件为空", "File is empty");
        add("JSON 末尾含有多余内容", "JSON has trailing content");
        add("不是完整有效的 JSON", "Not complete valid JSON");
        add("第 ", "Account #");
        add(" 个账号不是 JSON 对象", " is not a JSON object");
        add("JSON 根节点必须是账号对象或账号数组",
                "The JSON root must be an account object or an account array");
        add("文件中没有账号", "The file contains no accounts");
        add("单次最多导入 ", "At most ");
        add(" 个账号", " accounts");
        add("个账号", "accounts");
        add("文件中包含重复账号：", "The file contains a duplicate account: ");
        add("不支持的账号文件版本", "Unsupported account-file version");
        add("缺少 accounts 数组", "Missing accounts array");
        add(" 个账号缺少 credential 对象", " is missing a credential object");
        add("账号", "Account");
        add("不是 JSON 对象", " is not a JSON object");
        add("的 id 长度不正确", " has an invalid id length");
        add("的 token 长度不正确", " has an invalid token length");
        add("的 chat_status 必须是对象", " chat_status must be an object");
        add("的 id_profiles 必须是数组", " id_profiles must be an array");
        add("的 id_profiles[", " id_profiles[");
        add("] 必须是对象", "] must be an object");
        add("的 need_birthday 必须是布尔值", " need_birthday must be boolean");
        add("没有选择账号", "No accounts selected");
        add("选择的账号过多", "Too many accounts selected");
        add("无法生成账号 JSON", "Could not generate account JSON");
        add("DeepSeek账号", "DeepSeekAccount");
        add("_等", "_and_");
        add("_DeepSeek账号.txt", "_DeepSeekAccounts.txt");
        add("缺少字符串字段 ", "Missing string field ");
        add("的 ", " ");
        add(" 不能为空", " must not be empty");
        add("缺少字段 ", "Missing field ");
        add(" 必须是字符串或 null", " must be a string or null");
        add("缺少数字字段 ", "Missing numeric field ");
        add(" 必须是整数", " must be an integer");
        add(" 必须是字符串", " must be a string");
        add("未知账号", "Unknown account");
        add("画笔粗细", "Brush size");
        add("画笔粗细：", "Brush size: ");
        add("背景、页面贴纸和气泡；支持全屏/半屏、无固定上限缩放、截断、镜像或边缘像素延展、取景、旋转、景深与曲线位移。",
                "Wallpaper, page stickers, and bubbles; supports full/half screen, uncapped zoom, clip, mirror or outermost-pixel extension, focus, rotation, depth, and curved motion.");
        add("被你发现彩蛋了喵～", "You found the easter egg~");
        add("隐藏彩蛋", "Hidden easter egg");
        add("一键破甲", "One-tap armor break");
        add("启用全局液态玻璃", "Enable global liquid glass");
        add("按 Android 版本、性能和节电状态自动降级",
                "Automatically fall back based on Android version, performance, and power-saving state.");
        add("懂你意思喵～", "Got it, meow~");
        add("内置提示词启用失败", "Could not enable the bundled prompt");
        add("此页面保留快捷开关。液态玻璃会按设备能力选择实时折射、共享模糊或静态磨砂。",
                "This page keeps quick toggles. Liquid glass selects real-time refraction, shared blur, or static frosting based on device capability.");
        add("液态玻璃设置保存失败", "Could not save liquid-glass settings");
        add("非 Google Play 版本提供；Google Play 版不含此开关。",
                "Available in non-Google-Play builds; the Google Play build does not include this switch.");

        // Compact help page entries.
        add("功能说明与常见问题", "Feature notes and common questions");
        add("【功能】系统提示词注入", "[Feature] System prompt injection");
        add("把选定的提示词附加到请求中。", "Attach the selected prompt to requests.");
        add("【功能】心跳与定时提醒", "[Feature] Heartbeat and scheduled reminders");
        add("按当前对话发送主动消息，并可设置或取消提醒。", "Send proactive messages in the current chat and set or cancel reminders.");
        add("【功能】Agent 工具", "[Feature] Agent tools");
        add("在实验性功能中管理工具和权限，支持基础操作。", "Manage tools and permissions in Experimental Features for basic actions.");
        add("【功能】工具调用日志", "[Feature] Tool-call log");
        add("显示工具名称、调用时间和执行结果。", "Show the tool name, call time, and result.");
        add("【功能】问答工具", "[Feature] Question tool");
        add("模型提出选项或问题，你选择或输入后继续对话。", "Choose or type an answer when the model asks a question.");
        add("【功能】面板与图形输出", "[Feature] Panels and graphics");
        add("模型可生成信息面板、进度条和简单图形。", "The model can generate panels, progress bars, and simple graphics.");
        add("【功能】聊天外观", "[Feature] Chat appearance");
        add("自定义气泡、背景图、贴纸和透明度。", "Customize bubbles, wallpapers, stickers, and opacity.");
        add("提供兼容接口和本地 Agent 工具调用。", "Provides compatible endpoints and local Agent tool calls.");
        add("【问题】工具只显示文字，没有真正执行？", "[Question] A tool is shown as text but did not run?");
        add("确认实验性功能中的 Agent 开关已开启，并检查工具权限。", "Enable Agent in Experimental Features and check tool permissions.");
        add("【问题】心跳提醒没有出现在对话？", "[Question] A heartbeat reminder is missing from the chat?");
        add("确认已绑定目标对话，并允许通知和后台运行。", "Bind the target chat and allow notifications and background activity.");
        add("【问题】背景图或聊天外观没有生效？", "[Question] Wallpaper or chat appearance did not apply?");
        add("重新打开对应页面；部分设置需要重启 DeepSeek。", "Reopen the relevant page; some settings require restarting DeepSeek.");
        add("【问题】编辑或历史记录显示异常？", "[Question] Editing or history looks wrong?");
        add("先在 DeepSeek 原生页面打开目标对话并等待加载，再重试。", "Open the target chat in DeepSeek and wait for it to load, then try again.");
        add("【问题】账号导入失败？", "[Question] Account import failed?");
        add("使用完整 UTF-8 JSON，并确认凭证仍有效。", "Use complete UTF-8 JSON and make sure the credential is still valid.");
        add("【功能】空间动效", "[Feature] Spatial motion");
        add("控制背景视差、层次和动态强度。", "Control background parallax, depth, and motion strength.");
        add("把会话图片交给视觉模型，再继续专家对话。", "Send chat images to the vision model, then continue the expert chat.");
        add("按当前对话设置周期或一次性心跳提醒。", "Set recurring or one-time heartbeat reminders for the current chat.");
        add("提供本地兼容接口、SSE 和 Agent 工具结果。", "Provide local compatible endpoints, SSE, and Agent tool results.");
        add("模型提出选项，你选择或输入后继续。", "The model offers options; choose or type one to continue.");
        add("【功能】Agent 工具与权限", "[Feature] Agent tools and permissions");
        add("管理工具开关、执行权限和 Shizuku/Root 模式。", "Manage tool switches, execution permissions, and Shizuku/Root mode.");
        add("【问题】背景图或贴纸没有显示？", "[Question] Wallpaper or stickers are missing?");
        add("检查聊天外观开关和当前界面绑定，必要时重启 DeepSeek。", "Check the chat-appearance switch and screen binding; restart DeepSeek if needed.");
        add("检查服务开关、密钥、端口和后台运行权限。", "Check the service switch, key, port, and background permission.");
        add("【问题】心跳没有写入对话？", "[Question] Heartbeat was not written to the chat?");
        add("确认目标对话已绑定，并允许通知和后台活动。", "Confirm the target chat is bound and notifications/background activity are allowed.");
        add("【问题】Agent 只输出调用文字？", "[Question] Agent only printed a tool call?");
        add("开启 Agent 和对应工具权限，再重新发送请求。", "Enable Agent and the relevant tool permission, then send the request again.");
        add("导入图片并调整取景、缩放、透明度和界面。", "Import an image and adjust framing, zoom, opacity, and screen.");
        add("功能说明与必要排查。点一下条目展开。", "Feature notes and essential troubleshooting. Tap an item to expand.");

        Collections.sort(FRAGMENTS, new Comparator<Entry>() {
            @Override public int compare(Entry left, Entry right) {
                return right.zh.length() - left.zh.length();
            }
        });
    }

    private UiLanguageCatalog() {}

    static boolean mightTranslate(String value) {
        if (value == null || value.length() == 0) return false;
        if (EXACT.containsKey(value)) return true;
        for (Entry entry : FRAGMENTS) {
            if (value.contains(entry.zh)) return true;
        }
        return false;
    }

    static String toEnglish(String value) {
        if (value == null || value.length() == 0) return value == null ? "" : value;
        String exact = EXACT.get(value);
        if (exact != null) return exact;
        String translated = value;
        for (Entry entry : FRAGMENTS) {
            if (translated.contains(entry.zh)) {
                translated = translated.replace(entry.zh, entry.en);
            }
        }
        return translated;
    }

    private static void add(String zh, String en) {
        if (zh == null || zh.length() == 0 || en == null) return;
        EXACT.put(zh, en);
        // Single-character words are exact labels, not safe global replacement fragments.
        // The ideographic comma is the sole punctuation fragment intentionally retained.
        if (zh.length() > 1 || "、".equals(zh)) {
            FRAGMENTS.add(new Entry(zh, en));
        }
    }

    private static final class Entry {
        final String zh;
        final String en;
        Entry(String zh, String en) { this.zh = zh; this.en = en; }
    }
}
