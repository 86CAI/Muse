package com.caipan.music.ui.components

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.caipan.music.plugin.BlurLocation
import com.caipan.music.plugin.BlurPolicy
import com.caipan.music.plugin.GlobalBlurControlPlugin
import com.caipan.music.plugin.PluginInfo
import com.kyant.backdrop.Backdrop

@Composable
fun PluginListScreen(
    plugins: List<PluginInfo>,
    onEnabledChange: (String, Boolean) -> Unit,
    onPermissionChange: (String, String, Boolean) -> Unit,
    onImport: (Uri) -> Unit,
    onOpenWebUi: (String) -> Unit,
    onOpenExternalPlayerAccess: () -> Unit = {},
    onDeleteExternal: (String) -> Unit = {},
    onDismiss: () -> Unit,
    accentColor: Color,
    isLightTheme: Boolean,
    isInstalling: Boolean = false,
    message: String? = null,
    backdrop: Backdrop? = null,
    blurPolicy: BlurPolicy = LocalMuseBlurPolicy.current,
    onBlurMasterChange: (Boolean) -> Unit = {},
    onBlurLocationChange: (BlurLocation, Boolean) -> Unit = { _, _ -> },
    onReadabilityBlurChange: (Float) -> Unit = {}
) {
    val cardColor = Color.Transparent
    val primary = androidx.compose.material3.MaterialTheme.colorScheme.onBackground
    val secondary = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(onImport)
    }
    var expandedPermissionsFor by remember { mutableStateOf<String?>(null) }
    var deleteTarget by remember { mutableStateOf<PluginInfo?>(null) }

    FullScreenGlassRoute(backdrop = backdrop, isLightTheme = isLightTheme) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.ArrowBack, "返回", tint = primary, modifier = Modifier.size(24.dp))
                }
                Text("播放插件", color = primary, style = androidx.compose.material3.MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f).padding(start = 8.dp))
                IconButton(
                    onClick = { importLauncher.launch(arrayOf("application/zip", "application/octet-stream", "application/x-zip-compressed", "*/*")) },
                    enabled = !isInstalling
                ) {
                    Icon(Icons.Default.FileOpen, "导入 .museplugin", tint = accentColor)
                }
                Text("${plugins.count { it.enabled }}/${plugins.size} 已启用", color = secondary,
                    fontSize = 12.sp, modifier = Modifier.padding(end = 12.dp))
            }

            if (isInstalling) {
                LinearProgressIndicator(Modifier.fillMaxWidth(), color = accentColor)
            }

            Text(
                "插件仅在对应播放事件发生时运行。关闭所有插件后，播放器保持原有行为。",
                color = secondary,
                fontSize = 13.sp,
                lineHeight = 19.sp,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
            )

            message?.let {
                Text(
                    it,
                    color = if (it.startsWith("导入失败")) Color(0xFFE53935) else accentColor,
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
                )
            }

            LazyColumn(
                Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(plugins, key = { it.id }) { plugin ->
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                            .museGlass(
                                backdrop, RoundedCornerShape(16.dp),
                                androidx.compose.material3.MaterialTheme.colorScheme.surface.copy(alpha = .34f),
                                location = BlurLocation.CARDS,
                                readabilityBoost = true
                            ),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = cardColor)
                    ) {
                        Column(Modifier.fillMaxWidth().padding(16.dp)) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier.size(44.dp).background(accentColor.copy(alpha = 0.14f), RoundedCornerShape(12.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Extension, null, tint = accentColor)
                            }
                            Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
                                Text(plugin.name, color = primary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                Text("v${plugin.version} · ${plugin.author}", color = secondary, fontSize = 12.sp)
                                Spacer(Modifier.height(7.dp))
                                Text(plugin.description, color = secondary, fontSize = 13.sp, lineHeight = 18.sp)
                                Text("Hook：${plugin.hooks.joinToString()}", color = accentColor,
                                    fontSize = 12.sp, modifier = Modifier.padding(top = 7.dp))
                                if (plugin.networkAllowHosts.isNotEmpty()) {
                                    Text("网络：${plugin.networkAllowHosts.joinToString()}", color = secondary,
                                        fontSize = 12.sp, modifier = Modifier.padding(top = 3.dp))
                                }
                            }
                            if (plugin.hasWebUi) {
                                IconButton(onClick = { onOpenWebUi(plugin.id) }) {
                                    Icon(Icons.Default.OpenInBrowser, "打开 WebUI", tint = accentColor)
                                }
                            }
                            if (plugin.id == "com.caipan.muse.external-player-monitor") {
                                IconButton(onClick = onOpenExternalPlayerAccess) {
                                    Icon(Icons.Default.Security, "开启系统监听权限", tint = accentColor)
                                }
                            }
                            if (plugin.external) {
                                IconButton(onClick = { deleteTarget = plugin }) {
                                    Icon(Icons.Default.Delete, "删除外部模块", tint = Color(0xFFE53935))
                                }
                            }
                            MuseGlassSwitch(
                                checked = plugin.enabled,
                                onCheckedChange = { onEnabledChange(plugin.id, it) },
                                accentColor = accentColor,
                                backdrop = backdrop
                            )
                        }
                        if (plugin.id == GlobalBlurControlPlugin.ID) {
                            BlurControlSettings(
                                policy = blurPolicy,
                                accentColor = accentColor,
                                backdrop = backdrop,
                                onMasterChange = onBlurMasterChange,
                                onLocationChange = onBlurLocationChange,
                                onReadabilityChange = onReadabilityBlurChange
                            )
                        }
                        if (plugin.permissions.isNotEmpty()) {
                            val expanded = expandedPermissionsFor == plugin.id
                            Row(
                                Modifier.fillMaxWidth().padding(top = 10.dp)
                                    .clickable { expandedPermissionsFor = if (expanded) null else plugin.id }
                                    .padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Security, null, tint = accentColor, modifier = Modifier.size(18.dp))
                                Text(
                                    "权限 ${plugin.grantedPermissions.size}/${plugin.permissions.size}",
                                    color = primary, fontSize = 13.sp, fontWeight = FontWeight.Medium,
                                    modifier = Modifier.weight(1f).padding(start = 8.dp)
                                )
                                Icon(if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null, tint = secondary)
                            }
                            if (expanded) {
                                Text(
                                    "仅授予必要权限。文件与局域网权限可能发送或修改你的数据。",
                                    color = secondary, fontSize = 12.sp, lineHeight = 17.sp,
                                    modifier = Modifier.padding(vertical = 6.dp)
                                )
                                plugin.permissions.forEach { permission ->
                                    val granted = permission in plugin.grantedPermissions
                                    Row(
                                        Modifier.fillMaxWidth().clickable {
                                            onPermissionChange(plugin.id, permission, !granted)
                                        }.padding(vertical = 5.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(Modifier.weight(1f)) {
                                            Text(permissionLabel(permission), color = primary, fontSize = 13.sp)
                                            Text(permission, color = secondary, fontSize = 11.sp)
                                        }
                                        MuseGlassSwitch(
                                            checked = granted,
                                            onCheckedChange = { onPermissionChange(plugin.id, permission, it) },
                                            accentColor = accentColor,
                                            backdrop = backdrop
                                        )
                                    }
                                }
                            }
                        }
                        }
                    }
                }
            }
        }
    }
    deleteTarget?.let { plugin ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("删除外部模块？") },
            text = { Text("将删除 ${plugin.name} 及其配置，内置模块不会受影响。") },
            confirmButton = { TextButton(onClick = { deleteTarget = null; onDeleteExternal(plugin.id) }) { Text("删除") } },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("取消") } }
        )
    }
}

@Composable
private fun BlurControlSettings(
    policy: BlurPolicy,
    accentColor: Color,
    backdrop: Backdrop?,
    onMasterChange: (Boolean) -> Unit,
    onLocationChange: (BlurLocation, Boolean) -> Unit,
    onReadabilityChange: (Float) -> Unit
) {
    HorizontalDivider(Modifier.padding(vertical = 12.dp), color = Color.White.copy(alpha = .12f))
    Text("玻璃效果位置", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("全部实时模糊与折射", modifier = Modifier.weight(1f), fontSize = 13.sp)
        MuseGlassSwitch(policy.masterEnabled, onMasterChange, accentColor, backdrop)
    }
    BlurLocation.entries.forEach { location ->
        Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(location.label, modifier = Modifier.weight(1f), fontSize = 13.sp,
                color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant)
            MuseGlassSwitch(
                checked = policy.enabledAt(location),
                onCheckedChange = { onLocationChange(location, it) },
                accentColor = accentColor,
                backdrop = backdrop,
                enabled = policy.masterEnabled
            )
        }
    }
    Text("Liquid 文字背景分离模糊", fontSize = 12.sp,
        color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 9.dp))
    MuseGlassSlider(
        value = policy.liquidReadabilityBlur,
        onValueChange = onReadabilityChange,
        valueRange = 0f..1f,
        accentColor = accentColor,
        backdrop = backdrop,
        modifier = Modifier.fillMaxWidth()
    )
}

private fun permissionLabel(permission: String): String = when (permission) {
    "config" -> "读写插件配置"
    "player.read" -> "读取播放状态"
    "player.control" -> "控制播放器"
    "queue.read" -> "读取播放队列"
    "queue.control" -> "修改播放队列"
    "library.read" -> "读取音乐资料库"
    "library.refresh" -> "刷新音乐资料库"
    "playlists.read" -> "读取播放列表"
    "playlists.write" -> "修改播放列表"
    "playlists.delete" -> "删除播放列表"
    "lyrics.read" -> "读取歌词"
    "stats.read" -> "读取收听统计"
    "theme.read" -> "读取外观设置"
    "theme.write" -> "修改外观设置"
    "equalizer.read" -> "读取均衡器"
    "equalizer.control" -> "控制均衡器"
    "profile.read" -> "读取用户资料"
    "profile.write" -> "修改用户资料"
    "lan.discovery" -> "发现局域网设备"
    "lan.pairing" -> "配对局域网设备"
    "lan.state" -> "读取局域网设备状态"
    "lan.control" -> "控制局域网设备"
    "lan.hosting" -> "允许其他设备控制本机"
    "lan.transfer" -> "向已配对设备发送当前音乐"
    "network.request" -> "访问清单指定的网络服务"
    "externalPlayer.read" -> "读取外部播放器状态"
    "externalPlayer.control" -> "控制外部播放器"
    else -> permission
}
