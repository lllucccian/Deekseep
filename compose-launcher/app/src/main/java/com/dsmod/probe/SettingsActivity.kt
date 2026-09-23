package com.dsmod.probe

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.ResultReceiver
import android.system.Os
import android.widget.Toast
import android.widget.EditText
import android.text.InputType
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Devices
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.Gavel
import androidx.compose.material.icons.outlined.Home as HomeOutlined
import androidx.compose.material.icons.outlined.Info as InfoOutlined
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Settings as SettingsOutlined
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dsmod.probe.ui.DeekseepTheme
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import java.lang.reflect.Proxy

/** Standalone module application UI, independently implemented with public Material 3 APIs. */
class SettingsActivity : ComponentActivity() {
    companion object {
        @JvmField val VERSION: String = BuildInfo.MODULE_VERSION
        private const val TARGET_PACKAGE = "com.deepseek.chat"
        private const val REPOSITORY = "https://github.com/lllucccian/Deekseep"
        private const val QQ_GROUP_URL = "https://qun.qq.com/universal-share/share?ac=1&authKey=FbzWCRfmPumvnJwdOWMefz5c5SQbWWzKD2Pe90Vz6K923ic8eQA9GX3EWc453AV1&busi_data=eyJncm91cENvZGUiOiIxMTA2NDY1MzAwIiwidG9rZW4iOiJTU1BPNGgwTXN4VFBra0ozZkpCMVo5RGtEWWtIS2hOVktjcis2dTI4NU9EZ2ZidkN2cTI5QUJSTWxiNktYK01OIiwidWluIjoiMzY5MTE4NjM1MyJ9&data=Cc4E1sQAT4lvdZkfiRyQjjj8f4YzEq2YqvcfeSbXP_-V9G-G8pQyBNh9D5GUeI7CEH9I6op7AAvZn78Ca-j6gw&svctype=4&tempid=h5_group_info"
    }

    private data class ActivationState(
        val active: Boolean,
        val title: String,
        val detail: String,
        val color: Color,
    )

    private data class DeviceSnapshot(
        val product: String,
        val android: String,
        val abi: String,
        val kernel: String,
        val display: String,
        val securityPatch: String,
    )

    private val main = Handler(Looper.getMainLooper())
    private val exportLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri -> if (uri != null) requestExport(uri) }
    private val importLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> if (uri != null) requestImport(uri) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        UiLanguage.refreshSystem(this)
        setContent {
            DeekseepTheme {
                ModuleApplication()
            }
        }
    }

    private fun tr(zh: String, en: String): String = UiLanguage.text(this, zh, en)

    // Faint diagonal author watermark. Kept at a very low alpha so it is imperceptible during
    // normal use but still captured in screenshots (暗水印). Increase the alpha argument to make
    // it more visible, decrease it to make it fainter.
    private fun Modifier.watermarkOverlay(): Modifier = drawWithContent {
        drawContent()
        val text = "@lllucccian"
        val paint = android.graphics.Paint().apply {
            color = android.graphics.Color.argb(18, 128, 128, 128)
            textSize = 14f * density
            isAntiAlias = true
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }
        val spacingX = 220f * density
        val spacingY = 140f * density
        val native = drawContext.canvas.nativeCanvas
        native.save()
        native.rotate(-30f, size.width / 2f, size.height / 2f)
        var y = -spacingY
        while (y < size.height + spacingY) {
            var x = -spacingX
            while (x < size.width + spacingX) {
                native.drawText(text, x, y, paint)
                x += spacingX
            }
            y += spacingY
        }
        native.restore()
    }

    private fun activationState(): ActivationState {
        val framework = XposedActivationProvider.isFrameworkConnected()
        val target = XposedActivationProvider.isTargetRecentlyActive(this)
        val active = framework || target
        return ActivationState(
            active = active,
            title = if (active) tr("模块已激活", "Module active")
                else tr("模块未激活", "Module inactive"),
            detail = if (active) {
                "Xposed API ${BuildInfo.API_VERSION} · DeepSeek ${deepSeekVersion()}"
            } else {
                tr(
                    "未检测到注入，请在 LSPosed 中启用模块并勾选 DeepSeek",
                    "No injection detected. Enable the module and select DeepSeek in LSPosed.",
                )
            },
            color = if (active) Color(0xFF3D8A55) else Color(0xFFC64B46),
        )
    }

    private fun deviceSnapshot(): DeviceSnapshot {
        val uname = runCatching { Os.uname() }.getOrNull()
        val metrics = resources.displayMetrics
        val product = listOf(Build.MANUFACTURER, Build.MODEL)
            .filter { it.isNotBlank() }
            .joinToString(" ")
        val kernelRelease = uname?.release?.takeIf { it.isNotBlank() }
            ?: System.getProperty("os.version", tr("未知", "Unknown"))
        val kernelMachine = uname?.machine?.takeIf { it.isNotBlank() }
        return DeviceSnapshot(
            product = product.ifBlank { Build.DEVICE },
            android = "Android ${Build.VERSION.RELEASE} · API ${Build.VERSION.SDK_INT}",
            abi = Build.SUPPORTED_ABIS.joinToString(", "),
            kernel = listOfNotNull(kernelRelease, kernelMachine).joinToString(" · "),
            display = "${metrics.widthPixels} × ${metrics.heightPixels} · ${metrics.densityDpi} dpi",
            securityPatch = Build.VERSION.SECURITY_PATCH.ifBlank { tr("未知", "Unknown") },
        )
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun ModuleApplication() {
        var selectedPage by remember { mutableIntStateOf(0) }
        var menuOpen by remember { mutableStateOf(false) }
        var sponsorOpen by remember { mutableStateOf(false) }
        var aboutOpen by remember { mutableStateOf(false) }
        var licenseOpen by remember { mutableStateOf(false) }
        val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

        Scaffold(
            modifier = Modifier.fillMaxSize().watermarkOverlay(),
            topBar = {
                CenterAlignedTopAppBar(
                    title = {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "Deekseep",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                text = "${BuildInfo.DISPLAY_VERSION} (33)",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                    actions = {
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(Icons.Filled.MoreVert, tr("更多", "More"))
                        }
                        DropdownMenu(
                            expanded = menuOpen,
                            onDismissRequest = { menuOpen = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text(tr("赞助开发", "Sponsor development")) },
                                leadingIcon = {
                                    Icon(Icons.Outlined.FavoriteBorder, contentDescription = null)
                                },
                                onClick = { menuOpen = false; sponsorOpen = true },
                            )
                            DropdownMenuItem(
                                text = { Text(tr("关于", "About")) },
                                leadingIcon = {
                                    Icon(Icons.Outlined.InfoOutlined, contentDescription = null)
                                },
                                onClick = { menuOpen = false; aboutOpen = true },
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        scrolledContainerColor = MaterialTheme.colorScheme
                            .surfaceColorAtElevation(3.dp),
                    ),
                    scrollBehavior = scrollBehavior,
                )
            },
            bottomBar = {
                NavigationBar {
                    NavigationBarItem(
                        selected = selectedPage == 0,
                        onClick = { selectedPage = 0 },
                        icon = {
                            Icon(if (selectedPage == 0) Icons.Filled.Home else Icons.Outlined.HomeOutlined,
                                contentDescription = null)
                        },
                        label = { Text(tr("首页", "Home")) },
                    )
                    NavigationBarItem(
                        selected = selectedPage == 1,
                        onClick = { selectedPage = 1 },
                        icon = {
                            Icon(if (selectedPage == 1) Icons.Filled.Settings else Icons.Outlined.SettingsOutlined,
                                contentDescription = null)
                        },
                        label = { Text(tr("设置", "Settings")) },
                    )
                }
            },
        ) { padding ->
            if (selectedPage == 0) {
                HomePage(padding)
            } else {
                SettingsPage(
                    padding = padding,
                    onSponsor = { sponsorOpen = true },
                    onLicense = { licenseOpen = true },
                )
            }
        }

        if (sponsorOpen) SponsorDialog { sponsorOpen = false }
        if (licenseOpen) TextDialog(
            title = "GNU GPL-3.0-only",
            body = licenseText(),
        ) { licenseOpen = false }
        if (aboutOpen) TextDialog(
            title = tr("关于", "About"),
            body = tr(
                "Deekseep 是适用于 DeepSeek 的 Xposed 模块。\n\n模块版本：${BuildInfo.DISPLAY_VERSION}\nDeepSeek：${deepSeekVersion()}",
                "Deekseep is an Xposed module for DeepSeek.\n\nModule: ${BuildInfo.DISPLAY_VERSION}\nDeepSeek: ${deepSeekVersion()}",
            ),
        ) { aboutOpen = false }
    }

    @Composable
    private fun HomePage(padding: PaddingValues) {
        val activation = activationState()
        val device = remember { deviceSnapshot() }
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { ActivationCard(activation) }
            item {
                InfoPanel(
                    icon = Icons.Filled.Info,
                    title = tr("构建信息", "Build information"),
                    rows = listOf(
                        tr("模块版本", "Module version") to BuildInfo.DISPLAY_VERSION,
                        tr("DeepSeek 版本", "DeepSeek version") to deepSeekVersion(),
                        tr("兼容接口", "Compatibility") to BuildInfo.API_VERSION,
                        tr("构建时间", "Build date") to BuildInfo.BUILD_DATE,
                    ),
                )
            }
            item {
                InfoPanel(
                    icon = Icons.Outlined.Devices,
                    title = tr("设备信息", "Device information"),
                    rows = listOf(
                        tr("设备", "Device") to device.product,
                        tr("系统", "System") to device.android,
                        tr("处理器架构", "ABI") to device.abi,
                        tr("内核版本", "Kernel") to device.kernel,
                        tr("屏幕", "Display") to device.display,
                        tr("安全补丁", "Security patch") to device.securityPatch,
                    ),
                )
            }
            item {
                ActionCard(
                    icon = Icons.Outlined.OpenInNew,
                    title = tr("打开 DeepSeek", "Open DeepSeek"),
                    subtitle = tr("打开 DeepSeek 并直接进入模块设置", "Open DeepSeek and go straight to the module settings"),
                ) {
                    val launchIntent = packageManager.getLaunchIntentForPackage(TARGET_PACKAGE)
                    if (launchIntent == null) {
                        toast(tr("未安装 DeepSeek", "DeepSeek is not installed"))
                        return@ActionCard
                    }
                    startActivity(launchIntent)
                    main.postDelayed({
                        runCatching {
                            sendBroadcast(ModuleConfigBridge.requestOpenSettings())
                        }
                    }, 900L)
                }
            }
        }
    }

    @Composable
    private fun SettingsPage(
        padding: PaddingValues,
        onSponsor: () -> Unit,
        onLicense: () -> Unit,
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                ActionCard(
                    Icons.Outlined.ArrowUpward,
                    tr("导出配置", "Export configuration"),
                    tr("保存当前功能开关", "Save current feature settings"),
                ) { exportLauncher.launch("deekseep-config-${BuildInfo.MODULE_VERSION}.json") }
            }
            item {
                ActionCard(
                    Icons.Outlined.ArrowDownward,
                    tr("导入配置", "Import configuration"),
                    tr("恢复已保存的功能开关", "Restore saved feature settings"),
                ) { importLauncher.launch(arrayOf("application/json", "text/json", "text/plain")) }
            }
            if (BuildInfo.PROTECTED_BUILD && isFullBackupHostSupported()) {
                item {
                    ActionCard(
                        Icons.Outlined.ArrowUpward,
                        tr("一键备份全部数据", "Back up all data"),
                        tr("加密保存模块配置、DeepSeek 数据、聊天记录与日志", "Encrypt module settings, DeepSeek data, chats, and logs"),
                    ) { ClosedFullDataBackupBridge.beginBackup(this@SettingsActivity) }
                }
                item {
                    ActionCard(
                        Icons.Outlined.ArrowDownward,
                        tr("一键恢复全部数据", "Restore all data"),
                        tr("校验备份后覆盖当前私有数据，需要 Root", "Verify and replace current private data; Root required"),
                    ) { ClosedFullDataBackupBridge.beginRestore(this@SettingsActivity) }
                }
            }
            item {
                ActionCard(
                    Icons.Outlined.FavoriteBorder,
                    tr("赞助开发", "Sponsor development"),
                    tr("支持持续维护和版本适配", "Support maintenance and compatibility work"),
                    onSponsor,
                )
            }
            item {
                ActionCard(
                    Icons.Outlined.Forum,
                    tr("交流群", "Community group"),
                    tr("加入 QQ 群获取更新与支持", "Join the QQ group for updates and support"),
                ) {
                    runCatching {
                        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(QQ_GROUP_URL)))
                    }.onFailure {
                        toast(tr("无法打开 QQ 群链接", "Could not open the QQ group link"))
                    }
                }
            }
            item {
                ActionCard(
                    Icons.Outlined.Code,
                    tr("GitHub 仓库", "GitHub repository"),
                    REPOSITORY,
                ) {
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(REPOSITORY)))
                }
            }
            if (!BuildInfo.PROTECTED_BUILD) {
                item {
                    ActionCard(
                        Icons.Outlined.Gavel,
                        tr("开源声明", "Open-source notice"),
                        "GNU GPL-3.0-only",
                        onLicense,
                    )
                }
            }
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == Main.FULL_DATA_BACKUP_REQUEST) {
            ClosedFullDataBackupBridge.handleExportResult(this, resultCode, data)
        } else if (requestCode == Main.FULL_DATA_RESTORE_REQUEST) {
            ClosedFullDataBackupBridge.handleRestoreResult(this, resultCode, data)
        }
    }

    @Composable
    private fun ActivationCard(state: ActivationState) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = state.color),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = if (state.active) Icons.Filled.CheckCircle else Icons.Filled.Warning,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(32.dp),
                )
                Spacer(Modifier.width(16.dp))
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(
                        state.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White,
                    )
                    Text(
                        state.detail,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.84f),
                    )
                }
            }
        }
    }

    @Composable
    private fun InfoPanel(icon: ImageVector, title: String, rows: List<Pair<String, String>>) {
        ElevatedCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp),
                    )
                    Spacer(Modifier.width(14.dp))
                    Text(title, style = MaterialTheme.typography.titleMedium)
                }
                Spacer(Modifier.height(12.dp))
                rows.forEachIndexed { index, row ->
                    if (index > 0) HorizontalDivider(
                        modifier = Modifier.padding(vertical = 10.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f),
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.Top,
                    ) {
                        Text(
                            row.first,
                            modifier = Modifier.weight(0.38f),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            row.second,
                            modifier = Modifier.weight(0.62f),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
        }
    }

    @Composable
    private fun ActionCard(
        icon: ImageVector,
        title: String,
        subtitle: String,
        onClick: () -> Unit,
    ) {
        ElevatedCard(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp),
                )
                Spacer(Modifier.width(16.dp))
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(title, style = MaterialTheme.typography.titleMedium)
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }

    @Composable
    private fun SponsorDialog(onClose: () -> Unit) {
        var showWechat by remember { mutableStateOf(false) }
        val imageId = resources.getIdentifier("sponsor_qr", "drawable", packageName)
        if (!showWechat) {
            AlertDialog(
                onDismissRequest = onClose,
                title = { Text(tr("赞助开发", "Sponsor development")) },
                text = {
                    Column {
                        TextButton(onClick = {
                            try {
                                startActivity(Intent(Intent.ACTION_VIEW,
                                    Uri.parse("https://afdian.com/a/lllucccian")))
                                onClose()
                            } catch (_: Throwable) {
                                Toast.makeText(this@SettingsActivity,
                                    "https://afdian.com/a/lllucccian", Toast.LENGTH_LONG).show()
                            }
                        }, modifier = Modifier.fillMaxWidth()) {
                            Text(tr("通过爱发电赞助", "Sponsor via Afdian"))
                        }
                        TextButton(onClick = { showWechat = true },
                            modifier = Modifier.fillMaxWidth()) {
                            Text(tr("通过微信赞助", "Sponsor via WeChat"))
                        }
                    }
                },
                confirmButton = {},
                dismissButton = { TextButton(onClick = onClose) { Text(tr("取消", "Cancel")) } },
            )
            return
        }
        AlertDialog(
            onDismissRequest = onClose,
            title = { Text(tr("微信赞赏码", "WeChat donation code")) },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(tr("感谢支持持续维护与适配。", "Thank you for supporting continued development."))
                    Spacer(Modifier.height(12.dp))
                    if (imageId != 0) {
                        Image(
                            painter = painterResource(imageId),
                            contentDescription = tr("微信赞赏码", "WeChat donation code"),
                            modifier = Modifier.fillMaxWidth(),
                            contentScale = ContentScale.Fit,
                        )
                    }
                }
            },
            confirmButton = { TextButton(onClick = onClose) { Text(tr("完成", "Done")) } },
        )
    }

    @Composable
    private fun TextDialog(title: String, body: String, onClose: () -> Unit) {
        AlertDialog(
            onDismissRequest = onClose,
            title = { Text(title) },
            text = { Text(body, modifier = Modifier.verticalScroll(rememberScrollState())) },
            confirmButton = { TextButton(onClick = onClose) { Text(tr("关闭", "Close")) } },
        )
    }

    private fun requestExport(uri: Uri) {
        val reply = object : ResultReceiver(main) {
            override fun onReceiveResult(code: Int, data: Bundle?) {
                if (code != ModuleConfigBridge.RESULT_OK) {
                    toast(tr("请先启动一次 DeepSeek，再重试", "Open DeepSeek once, then try again"))
                    return
                }
                runCatching {
                    contentResolver.openOutputStream(uri, "wt")!!.use { output ->
                        output.write(
                            data?.getString(ModuleConfigBridge.EXTRA_JSON, "")
                                .orEmpty().toByteArray(StandardCharsets.UTF_8),
                        )
                    }
                }.onSuccess { toast(tr("配置已导出", "Configuration exported")) }
                    .onFailure { toast(it.message.orEmpty()) }
            }
        }
        sendBroadcast(ModuleConfigBridge.request(ModuleConfigBridge.MODE_EXPORT, null, reply))
    }

    private fun requestImport(uri: Uri) {
        runCatching {
            val output = ByteArrayOutputStream()
            contentResolver.openInputStream(uri)!!.use { it.copyTo(output, 4096) }
            String(output.toByteArray(), StandardCharsets.UTF_8)
        }.onSuccess { json ->
            val reply = object : ResultReceiver(main) {
                override fun onReceiveResult(code: Int, data: Bundle?) {
                    toast(
                        if (code == ModuleConfigBridge.RESULT_OK) {
                            tr("配置已导入，重新打开 DeepSeek 后生效", "Imported. Reopen DeepSeek to apply.")
                        } else {
                            tr("请先启动一次 DeepSeek，再重试", "Open DeepSeek once, then try again")
                        },
                    )
                }
            }
            sendBroadcast(ModuleConfigBridge.request(ModuleConfigBridge.MODE_IMPORT, json, reply))
        }.onFailure { toast(it.message.orEmpty()) }
    }

    @Suppress("DEPRECATION")
    private fun deepSeekVersion(): String = runCatching {
        val info = packageManager.getPackageInfo(TARGET_PACKAGE, 0)
        val code = if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else info.versionCode.toLong()
        "${info.versionName} ($code)"
    }.getOrDefault(tr("未安装", "Not installed"))

    @Suppress("DEPRECATION")
    private fun isFullBackupHostSupported(): Boolean = runCatching {
        val info = packageManager.getPackageInfo(TARGET_PACKAGE, 0)
        val code = if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else info.versionCode.toLong()
        code == 249L || code == 257L || code == 268L
    }.getOrDefault(false)

    private fun toast(value: String) = Toast.makeText(this, value, Toast.LENGTH_LONG).show()

    private fun licenseText(): String = runCatching {
        val id = resources.getIdentifier("gpl_3_0", "raw", packageName)
        resources.openRawResource(id).bufferedReader().use { it.readText() }
    }.getOrDefault("GNU General Public License version 3")
}
