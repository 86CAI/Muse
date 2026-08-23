/*
 * MeloX 插件页
 *
 * Ported from NEORUAA/Mei_MeloX_Android 的设置页语言：
 * IosPinnedListPage 大标题 + 分区标题 + IosGroupedList 分组 +
 * GlassCard 行（开关行 / 导航行 / 说明行）。数据为 Muse 的插件系统。
 *
 * Upstream: https://github.com/NEORUAA/Mei_MeloX_Android
 * License: GNU General Public License v3.0 (GPL-3.0) - see licenses/GPL-3.0.txt
 * Full attribution list: THIRD_PARTY_NOTICES.md
 */
package com.caipan.music.ui.melox

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.caipan.music.plugin.PluginInfo

@Composable
fun MeloXPluginScreen(
    plugins: List<PluginInfo>,
    isInstalling: Boolean,
    message: String?,
    bottomPadding: Dp,
    onEnabledChange: (String, Boolean) -> Unit,
    onPermissionChange: (String, String, Boolean) -> Unit,
    onImport: () -> Unit,
    onOpenWebUi: (String) -> Unit,
    onDelete: (String) -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalGlassColors.current
    IosPinnedListPage(
        title = "插件",
        bottomPadding = bottomPadding,
        horizontalContentPadding = 0.dp,
        onNavigateBack = onNavigateBack,
        modifier = modifier,
    ) {
        item(key = "plugins-install") {
            MeloXSettingsGroup("安装") {
                MeloXSettingsEntry(
                    title = if (isInstalling) "正在安装…" else "导入插件包",
                    subtitle = "选择 .museplugin 文件",
                    symbol = SfSymbol.Download,
                    showTopSeparator = false,
                    onClick = { if (!isInstalling) onImport() },
                )
                if (isInstalling) {
                    GlassCard(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            Modifier.fillMaxWidth().padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.padding(2.dp),
                                strokeWidth = 2.dp,
                            )
                            Text("安装中", style = IosTypography.body, color = colors.content)
                        }
                    }
                }
                message?.takeIf { it.isNotBlank() }?.let { text ->
                    MeloXSettingsHintRow(text = text, symbol = SfSymbol.InfoCircle)
                }
            }
        }

        if (plugins.isEmpty()) {
            item(key = "plugins-empty") {
                MeloXSettingsGroup("已安装") {
                    MeloXSettingsHintRow(
                        text = "还没有安装任何插件",
                        symbol = SfSymbol.Puzzle,
                    )
                }
            }
        } else {
            plugins.forEach { plugin ->
                item(key = "plugin-${plugin.id}") {
                    MeloXSettingsGroup(plugin.name) {
                        MeloXSettingsToggleRow(
                            title = "启用",
                            description = "v${plugin.version} · ${plugin.author}",
                            symbol = SfSymbol.Puzzle,
                            checked = plugin.enabled,
                            onCheckedChange = { onEnabledChange(plugin.id, it) },
                        )
                        if (plugin.description.isNotBlank()) {
                            MeloXSettingsHintRow(text = plugin.description)
                        }
                        if (plugin.hasWebUi) {
                            MeloXSettingsEntry(
                                title = "打开插件界面",
                                symbol = SfSymbol.Safari,
                                onClick = { onOpenWebUi(plugin.id) },
                            )
                        }
                        plugin.permissions.forEach { permission ->
                            MeloXSettingsToggleRow(
                                title = meloXPermissionLabel(permission),
                                symbol = null,
                                checked = permission in plugin.grantedPermissions,
                                onCheckedChange = { granted ->
                                    onPermissionChange(plugin.id, permission, granted)
                                },
                            )
                        }
                        if (plugin.networkAllowHosts.isNotEmpty()) {
                            MeloXSettingsHintRow(
                                text = "允许访问：" + plugin.networkAllowHosts.joinToString("、"),
                                symbol = SfSymbol.Cloud,
                            )
                        }
                        if (plugin.external) {
                            GlassCard(
                                modifier = Modifier.fillMaxWidth(),
                                onClick = { onDelete(plugin.id) },
                            ) {
                                Row(
                                    Modifier.fillMaxWidth().padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                ) {
                                    SfIcon(SfSymbol.Trash, null, size = 24.dp, tint = colors.destructive)
                                    Text(
                                        "删除插件",
                                        style = IosTypography.body,
                                        color = colors.destructive,
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

private fun meloXPermissionLabel(permission: String): String = when (permission) {
    "network" -> "网络访问"
    "storage" -> "存储访问"
    "playback.read" -> "读取播放状态"
    "playback.control" -> "控制播放"
    "library.read" -> "读取音乐库"
    "playlist.write" -> "修改歌单"
    "theme.write" -> "修改主题"
    "externalPlayer.read" -> "读取外部播放器状态"
    "externalPlayer.control" -> "控制外部播放器"
    "webui" -> "插件网页界面"
    else -> permission
}
