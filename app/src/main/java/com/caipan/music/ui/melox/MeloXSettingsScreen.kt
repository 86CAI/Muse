/*
 * MeloX 设置页
 *
 * Ported from NEORUAA/Mei_MeloX_Android
 * (ui/screen/setting/SettingScreen.kt + AppearanceSettings.kt + GeneralSettings.kt)：
 * IosPinnedListPage 大标题壳 + 分区标题(subheadline) + IosGroupedList 分组 +
 * GlassCard 行（padding 14dp / 图标 24dp / 文本区 13dp / 右侧 GlassToggle 或 IosPopupButton）。
 * 承载 Muse 原设置面板中的全部条目。
 *
 * Upstream: https://github.com/NEORUAA/Mei_MeloX_Android
 * License: GNU General Public License v3.0 (GPL-3.0) - see licenses/GPL-3.0.txt
 * Full attribution list: THIRD_PARTY_NOTICES.md
 */
package com.caipan.music.ui.melox

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.caipan.music.player.AudioQuality
import com.kyant.capsule.ContinuousRoundedRectangle

/** MeloX 设置页数据与行为。 */
data class MeloXSettingsState(
    val isLight: Boolean,
    val onlineMode: Boolean,
    val onlineSearchEnabled: Boolean,
    val neteaseNickname: String?,
    val gitHubName: String?,
    val mchatNickname: String?,
    val mchatAvatar: String?,
    val preferredQuality: AudioQuality,
    val wallpaperUri: Uri?,
    val videoUri: Uri?,
    val accentColor: Color?,
    val accentPresets: List<Pair<String, Color?>>,
    val bgOpacity: Float,
    val bgBlur: Float,
)

data class MeloXSettingsActions(
    val onToggleTheme: () -> Unit,
    val onToggleOnlineMode: () -> Unit,
    val onOnlineSearchEnabledChange: (Boolean) -> Unit,
    val onNeteaseAccount: () -> Unit,
    val onGitHubAccount: () -> Unit,
    val onMchatAccount: () -> Unit,
    val onOpenEqualizer: () -> Unit,
    val onOpenPlugins: () -> Unit,
    val onOpenUiSettings: () -> Unit,
    val onOpenSkins: () -> Unit,
    val onOpenAbout: () -> Unit,
    val onOpenProfile: () -> Unit,
    val onPreferredQualityChange: (AudioQuality) -> Unit,
    val onPickWallpaper: () -> Unit,
    val onPickVideo: () -> Unit,
    val onClearBackground: () -> Unit,
    val onAccentColorChange: (Color?) -> Unit,
    val onBgOpacityChange: (Float) -> Unit,
    val onBgBlurChange: (Float) -> Unit,
    val onBackup: () -> Unit,
    val onRestore: () -> Unit,
)

@Composable
fun MeloXSettingsScreen(
    state: MeloXSettingsState,
    actions: MeloXSettingsActions,
    bottomPadding: Dp,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalGlassColors.current
    IosPinnedListPage(
        title = "设置",
        bottomPadding = bottomPadding,
        horizontalContentPadding = 0.dp,
        onNavigateBack = onNavigateBack,
        modifier = modifier,
    ) {
        item(key = "settings-account") {
            MeloXSettingsGroup("账号") {
                MeloXSettingsEntry(
                    title = state.neteaseNickname?.takeIf { it.isNotBlank() } ?: "登录网易云音乐",
                    subtitle = if (state.neteaseNickname != null) "已登录 · 点击退出" else "登录后同步推荐、歌单与收藏",
                    symbol = SfSymbol.PersonCropCircle,
                    showTopSeparator = false,
                    onClick = actions.onNeteaseAccount,
                )
                MeloXSettingsEntry(
                    title = state.mchatNickname?.takeIf { it.isNotBlank() } ?: "登录 MChat",
                    subtitle = if (state.mchatNickname != null) "MChat 已登录 · 点击退出" else "使用 MChat 账号登录",
                    symbol = SfSymbol.PersonFilled,
                    onClick = actions.onMchatAccount,
                )
                MeloXSettingsEntry(
                    title = state.gitHubName ?: "登录 GitHub",
                    subtitle = if (state.gitHubName != null) "已登录 · 管理同步" else "登录后同步歌单与设置",
                    symbol = SfSymbol.Cloud,
                    onClick = actions.onGitHubAccount,
                )
                MeloXSettingsEntry(
                    title = "个人资料",
                    symbol = SfSymbol.PersonFilled,
                    onClick = actions.onOpenProfile,
                )
            }
        }

        item(key = "settings-general") {
            MeloXSettingsGroup("通用") {
                MeloXSettingsToggleRow(
                    title = "浅色模式",
                    description = "切换浅色 / 深色主题",
                    symbol = SfSymbol.Sparkles,
                    checked = state.isLight,
                    onCheckedChange = { actions.onToggleTheme() },
                )
                MeloXSettingsToggleRow(
                    title = "在线音乐模式",
                    description = if (state.onlineMode) "正在使用网易云内容" else "正在使用设备音乐",
                    symbol = SfSymbol.RadioWaves,
                    checked = state.onlineMode,
                    onCheckedChange = { actions.onToggleOnlineMode() },
                )
                MeloXSettingsToggleRow(
                    title = "在线搜索",
                    description = "允许搜索在线音源",
                    symbol = SfSymbol.Search,
                    checked = state.onlineSearchEnabled,
                    onCheckedChange = actions.onOnlineSearchEnabledChange,
                )
            }
        }

        item(key = "settings-playback") {
            MeloXSettingsGroup("播放") {
                MeloXSettingsChoiceRow(
                    title = "在线音质",
                    symbol = SfSymbol.Waveform,
                    selected = state.preferredQuality,
                    values = AudioQuality.entries.toList(),
                    valueLabel = { it.label },
                    onSelected = actions.onPreferredQualityChange,
                )
                MeloXSettingsEntry(
                    title = "均衡器",
                    subtitle = "导入预设、调整频段",
                    symbol = SfSymbol.SliderVertical3,
                    onClick = actions.onOpenEqualizer,
                )
            }
        }

        item(key = "settings-appearance") {
            MeloXSettingsGroup("外观") {
                MeloXSettingsEntry(
                    title = "更多设置",
                    subtitle = "背景透明度、模糊等",
                    symbol = SfSymbol.Gearshape,
                    showTopSeparator = false,
                    onClick = actions.onOpenUiSettings,
                )
                MeloXSettingsEntry(
                    title = "皮肤",
                    subtitle = "导入声明式皮肤包",
                    symbol = SfSymbol.Paintbrush,
                    onClick = actions.onOpenSkins,
                )
                MeloXSettingsSliderRow(
                    title = "背景遮罩透明度",
                    value = state.bgOpacity,
                    onValueChange = actions.onBgOpacityChange,
                    valueRange = 0.1f..0.9f,
                    readout = "${(state.bgOpacity * 100).toInt()}%",
                )
                MeloXSettingsSliderRow(
                    title = "背景模糊",
                    value = state.bgBlur,
                    onValueChange = actions.onBgBlurChange,
                    valueRange = 0f..40f,
                    readout = "${state.bgBlur.toInt()}dp",
                )
                MeloXSettingsValueRow(
                    title = "背景",
                    symbol = SfSymbol.Photo,
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (state.wallpaperUri != null) {
                            AsyncImage(
                                model = state.wallpaperUri,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(ContinuousRoundedRectangle(8.dp)),
                            )
                        } else if (state.videoUri != null) {
                            Box(
                                Modifier
                                    .size(40.dp)
                                    .clip(ContinuousRoundedRectangle(8.dp))
                                    .background(colors.tertiaryContent),
                                contentAlignment = Alignment.Center,
                            ) {
                                SfIcon(SfSymbol.PlayFilled, null, size = 16.dp, tint = colors.content)
                            }
                        }
                        Text(
                            "图片",
                            style = IosTypography.body,
                            color = colors.accent,
                            modifier = Modifier.clickableText(actions.onPickWallpaper),
                        )
                        Text(
                            "视频",
                            style = IosTypography.body,
                            color = colors.accent,
                            modifier = Modifier.clickableText(actions.onPickVideo),
                        )
                        if (state.wallpaperUri != null || state.videoUri != null) {
                            Text(
                                "移除",
                                style = IosTypography.body,
                                color = colors.destructive,
                                modifier = Modifier.clickableText(actions.onClearBackground),
                            )
                        }
                    }
                }
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    androidx.compose.foundation.layout.Column(
                        Modifier.fillMaxWidth().padding(14.dp),
                    ) {
                        Text("强调色", style = IosTypography.body, color = colors.content)
                        Spacer(Modifier.padding(top = 10.dp))
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            state.accentPresets.forEach { (name, color) ->
                                Box(
                                    Modifier
                                        .size(34.dp)
                                        .clip(CircleShape)
                                        .background(color ?: colors.tertiaryContent)
                                        .clickableText { actions.onAccentColorChange(color) },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    val isSelected = color == state.accentColor
                                    if (isSelected) {
                                        SfIcon(
                                            SfSymbol.Checkmark,
                                            name,
                                            size = 16.dp,
                                            tint = Color.White,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        item(key = "settings-extensions") {
            MeloXSettingsGroup("扩展") {
                MeloXSettingsEntry(
                    title = "插件",
                    subtitle = "安装、授权与管理播放插件",
                    symbol = SfSymbol.Puzzle,
                    showTopSeparator = false,
                    onClick = actions.onOpenPlugins,
                )
            }
        }

        item(key = "settings-backup") {
            MeloXSettingsGroup("备份") {
                MeloXSettingsEntry(
                    title = "导出完整备份",
                    symbol = SfSymbol.Download,
                    showTopSeparator = false,
                    onClick = actions.onBackup,
                )
                MeloXSettingsEntry(
                    title = "从备份还原",
                    symbol = SfSymbol.ArrowClockwise,
                    onClick = actions.onRestore,
                )
            }
        }

        item(key = "settings-info") {
            MeloXSettingsGroup("信息") {
                MeloXSettingsEntry(
                    title = "关于",
                    subtitle = "版本信息、语言与许可",
                    symbol = SfSymbol.InfoCircle,
                    showTopSeparator = false,
                    onClick = actions.onOpenAbout,
                )
            }
        }
    }
}

private fun Modifier.clickableText(onClick: () -> Unit): Modifier =
    this.clickable(
        interactionSource = null,
        indication = null,
        role = androidx.compose.ui.semantics.Role.Button,
        onClick = onClick,
    )
