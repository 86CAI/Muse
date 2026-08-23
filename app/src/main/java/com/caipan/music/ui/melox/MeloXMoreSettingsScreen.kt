/*
 * MeloX 更多设置页
 *
 * Ported from NEORUAA/Mei_MeloX_Android
 * (ui/screen/setting/AppearanceSettings.kt / GeneralSettings.kt)：
 * IosPinnedListPage + 分区标题 + IosGroupedList 分组 +
 * GlassCard 行（滑块行 / 选择行 / 开关行 / 说明行）。承载 Muse 的更多设置项。
 *
 * Upstream: https://github.com/NEORUAA/Mei_MeloX_Android
 * License: GNU General Public License v3.0 (GPL-3.0) - see licenses/GPL-3.0.txt
 * Full attribution list: THIRD_PARTY_NOTICES.md
 */
package com.caipan.music.ui.melox

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.caipan.music.viewmodel.UiStyle

@Composable
fun MeloXMoreSettingsScreen(
    bgOpacity: Float,
    bgBlur: Float,
    uiStyle: UiStyle,
    onlineSearchEnabled: Boolean,
    isChinese: Boolean,
    bottomPadding: Dp,
    onBgOpacityChange: (Float) -> Unit,
    onBgBlurChange: (Float) -> Unit,
    onUiStyleChange: (UiStyle) -> Unit,
    onOnlineSearchEnabledChange: (Boolean) -> Unit,
    onBackup: () -> Unit,
    onRestore: () -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val zh = isChinese
    IosPinnedListPage(
        title = if (zh) "更多设置" else "More Settings",
        bottomPadding = bottomPadding,
        horizontalContentPadding = 0.dp,
        onNavigateBack = onNavigateBack,
        modifier = modifier,
    ) {
        item(key = "more-background") {
            MeloXSettingsGroup(if (zh) "背景" else "Background") {
                MeloXSettingsSliderRow(
                    title = if (zh) "背景遮罩透明度" else "Background overlay",
                    value = bgOpacity,
                    onValueChange = onBgOpacityChange,
                    valueRange = 0.1f..0.9f,
                    readout = "${(bgOpacity * 100).toInt()}%",
                )
                MeloXSettingsSliderRow(
                    title = if (zh) "背景模糊" else "Background blur",
                    value = bgBlur,
                    onValueChange = onBgBlurChange,
                    valueRange = 0f..40f,
                    readout = "${bgBlur.toInt()}dp",
                )
                MeloXSettingsHintRow(
                    text = if (zh) {
                        "透明度越低越能看到壁纸或视频，模糊可提升文字可读性。"
                    } else {
                        "Lower opacity shows more wallpaper; blur improves readability."
                    },
                    symbol = SfSymbol.InfoCircle,
                )
            }
        }

        item(key = "more-style") {
            MeloXSettingsGroup(if (zh) "界面风格" else "UI Style") {
                MeloXSettingsChoiceRow(
                    title = if (zh) "版式" else "Layout",
                    symbol = SfSymbol.Paintbrush,
                    selected = uiStyle,
                    values = listOf(UiStyle.MELOX, UiStyle.CLOUD, UiStyle.LIQUID),
                    valueLabel = { style ->
                        when (style) {
                            UiStyle.MELOX -> "MeloX"
                            UiStyle.CLOUD -> if (zh) "云版" else "Cloud"
                            else -> "Liquid Glass"
                        }
                    },
                    onSelected = onUiStyleChange,
                )
                MeloXSettingsHintRow(
                    text = when (uiStyle) {
                        UiStyle.MELOX -> if (zh) {
                            "悬浮胶囊底栏与大标题首页，移植自开源项目 Mei_MeloX_Android。"
                        } else {
                            "Floating capsule tab bar, ported from Mei_MeloX_Android."
                        }
                        UiStyle.CLOUD -> if (zh) {
                            "Muse 现有云版首页与资料库布局，不使用玻璃。"
                        } else {
                            "Muse's Cloud home and library layout — no glass."
                        }
                        else -> if (zh) {
                            "动态折射、RGB 色散、高光、内阴影与弹性玻璃。"
                        } else {
                            "Refraction, RGB dispersion, highlights and elastic glass."
                        }
                    },
                )
            }
        }

        item(key = "more-online") {
            MeloXSettingsGroup(if (zh) "在线音源" else "Online sources") {
                MeloXSettingsToggleRow(
                    title = if (zh) "在线搜索" else "Online search",
                    description = if (zh) "允许搜索并播放在线音源" else "Search and play online sources",
                    symbol = SfSymbol.Search,
                    checked = onlineSearchEnabled,
                    onCheckedChange = onOnlineSearchEnabledChange,
                )
            }
        }

        item(key = "more-backup") {
            MeloXSettingsGroup(if (zh) "备份" else "Backup") {
                MeloXSettingsEntry(
                    title = if (zh) "导出完整备份" else "Export backup",
                    subtitle = if (zh) "设置、歌单与插件配置" else "Settings, playlists and plugins",
                    symbol = SfSymbol.Download,
                    showTopSeparator = false,
                    onClick = onBackup,
                )
                MeloXSettingsEntry(
                    title = if (zh) "从备份还原" else "Restore backup",
                    subtitle = if (zh) "还原后需要重启应用" else "Requires restarting the app",
                    symbol = SfSymbol.ArrowClockwise,
                    onClick = onRestore,
                )
            }
        }
    }
}
