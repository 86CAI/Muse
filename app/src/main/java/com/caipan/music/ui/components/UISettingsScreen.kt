package com.caipan.music.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.caipan.music.viewmodel.UiStyle
import com.kyant.backdrop.Backdrop

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UISettingsScreen(
    initialBgOpacity: Float,
    isLightTheme: Boolean,
    isChinese: Boolean = true,
    accentColor: Color = Color(0xFF1DB954),
    playerBgMode: com.caipan.music.player.PlayerBgMode = com.caipan.music.player.PlayerBgMode.ALBUM_EXTEND,
    onPlayerBgModeChanged: (com.caipan.music.player.PlayerBgMode) -> Unit = {},
    onBgOpacityChanged: (Float) -> Unit,
    uiStyle: UiStyle = UiStyle.APPLE,
    onUiStyleChanged: (UiStyle) -> Unit = {},
    backdrop: Backdrop? = null,
    onBackup: () -> Unit = {},
    onRestore: () -> Unit = {},
    onDismiss: () -> Unit
) {
    val textPrimary = MaterialTheme.colorScheme.onBackground
    val textSecondary = MaterialTheme.colorScheme.onSurfaceVariant
    val bgColor = MaterialTheme.colorScheme.background.copy(alpha = 0.42f)
    val cardBg = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.34f)
    var bgOpacity by remember(initialBgOpacity) { mutableStateOf(initialBgOpacity) }
    val zh = isChinese

    Box(Modifier.fillMaxSize().museGlass(backdrop, RoundedCornerShape(0.dp), bgColor, 24.dp)) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            // Header
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.ArrowBack, if (zh) "返回" else "Back",
                        tint = textPrimary, modifier = Modifier.size(24.dp))
                }
                Text(if (zh) "界面设置" else "UI Settings",
                    color = textPrimary, style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f).padding(start = 8.dp))
            }

            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
                // ── Background section ──
                Text(if (zh) "背景" else "Background",
                    color = accentColor, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 8.dp, bottom = 8.dp))

                Card(Modifier.fillMaxWidth().museGlass(backdrop, RoundedCornerShape(18.dp), cardBg),
                    colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                    shape = RoundedCornerShape(14.dp)) {
                    Column(Modifier.padding(20.dp)) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(if (zh) "背景遮罩透明度" else "Background Overlay",
                                    color = textPrimary, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                                Spacer(Modifier.height(2.dp))
                                Text(if (zh) "越低越能看到壁纸/视频，越高越护眼" else "Lower shows more wallpaper, higher protects eyes",
                                    color = textSecondary, fontSize = 12.sp)
                            }
                            Text((bgOpacity * 100).toInt().toString() + "%",
                                color = accentColor, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.height(8.dp))
                        MuseGlassSlider(
                            value = bgOpacity,
                            onValueChange = {
                                bgOpacity = it
                                onBgOpacityChanged(it)
                            },
                            valueRange = 0.1f..0.9f,
                            accentColor = accentColor,
                            backdrop = backdrop,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("10%", color = textSecondary, style = MaterialTheme.typography.labelSmall)
                            Text(if (zh) "推荐" else "Rec.", color = accentColor, style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(start = 12.dp))
                            Text("90%", color = textSecondary, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }

                Spacer(Modifier.height(24.dp))

                // ── Preview ──
                Text(if (zh) "预览" else "Preview", color = accentColor, fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 8.dp))

                Box(Modifier.fillMaxWidth().height(120.dp).clip(RoundedCornerShape(14.dp)).background(cardBg)) {
                    // Simulated wallpaper stripes
                    Row(Modifier.fillMaxSize().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Surface(Modifier.weight(1f).fillMaxHeight(), shape = RoundedCornerShape(8.dp), color = Color(0xFF1DB954)) {}
                        Surface(Modifier.weight(1f).fillMaxHeight(), shape = RoundedCornerShape(8.dp), color = Color(0xFF0A84FF)) {}
                        Surface(Modifier.weight(1f).fillMaxHeight(), shape = RoundedCornerShape(8.dp), color = Color(0xFFAF52DE)) {}
                    }
                    // Overlay
                    Box(Modifier.fillMaxSize().background(
                        if (isLightTheme) Color.White.copy(alpha = bgOpacity)
                        else Color.Black.copy(alpha = bgOpacity)
                    ))
                    // UI mockup
                    Column(Modifier.fillMaxSize().padding(12.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically) {
                            Text(if (zh) "歌曲标题" else "Song Title",
                                color = if (bgOpacity < 0.5f) Color.White else textPrimary,
                                fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            Surface(Modifier.size(28.dp), shape = RoundedCornerShape(6.dp),
                                color = accentColor.copy(alpha = 0.2f)) {}
                        }
                        Spacer(Modifier.weight(1f))
                        Surface(Modifier.fillMaxWidth().height(28.dp), shape = RoundedCornerShape(8.dp),
                            color = accentColor.copy(alpha = 0.15f)) {}
                    }
                }

                Spacer(Modifier.height(24.dp))

                // ── Player background mode ──
                Text(if (zh) "播放器背景" else "Player Background",
                    color = accentColor, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(bottom = 8.dp))

                val modes = listOf(
                    com.caipan.music.player.PlayerBgMode.ALBUM_EXTEND to (if (zh) "专辑封面延伸" else "Album Extend"),
                    com.caipan.music.player.PlayerBgMode.DYNAMIC_COLOR to (if (zh) "纯色动态律动" else "Dynamic Color"),
                    com.caipan.music.player.PlayerBgMode.CUSTOM to (if (zh) "自定义背景" else "Custom Wallpaper")
                )
                val modeDesc = mapOf(
                    com.caipan.music.player.PlayerBgMode.ALBUM_EXTEND to (if (zh) "模糊放大专辑封面作背景" else "Blurred album art as background"),
                    com.caipan.music.player.PlayerBgMode.DYNAMIC_COLOR to (if (zh) "提取封面主色，呆呼吸般渐变流动" else "Breathing gradient from album colors"),
                    com.caipan.music.player.PlayerBgMode.CUSTOM to (if (zh) "使用设置中的自定义壁纸" else "Use your custom wallpaper")
                )
                var selectedMode by remember { mutableStateOf(playerBgMode) }
                Card(Modifier.fillMaxWidth().museGlass(backdrop, RoundedCornerShape(18.dp), cardBg),
                    colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                    shape = RoundedCornerShape(14.dp)) {
                    Column {
                        modes.forEachIndexed { idx, (mode, label) ->
                            Row(Modifier.fillMaxWidth()
                                .clickable { selectedMode = mode; onPlayerBgModeChanged(mode) }
                                .padding(horizontal = 18.dp, vertical = 14.dp),
                                verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(label, color = textPrimary, fontSize = 15.sp,
                                        fontWeight = FontWeight.Medium)
                                    Spacer(Modifier.height(2.dp))
                                    Text(modeDesc[mode] ?: "", color = textSecondary, fontSize = 12.sp)
                                }
                                RadioButton(
                                    selected = selectedMode == mode,
                                    onClick = { selectedMode = mode; onPlayerBgModeChanged(mode) },
                                    colors = RadioButtonDefaults.colors(selectedColor = accentColor)
                                )
                            }
                            if (idx < modes.size - 1) {
                                HorizontalDivider(color = textSecondary.copy(alpha = 0.1f),
                                    modifier = Modifier.padding(start = 18.dp))
                            }
                        }
                    }
                }

                Spacer(Modifier.height(24.dp))

                // ── UI Style ──
                Text(if (zh) "界面风格" else "UI Style",
                    color = accentColor, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(bottom = 8.dp))

                val styles = listOf(
                    UiStyle.APPLE to "原版",
                    UiStyle.MONET to "Liquid Glass"
                )
                val styleDesc = mapOf(
                    UiStyle.APPLE to (if (zh) "Apple Music 风格，控件使用传统实时磨砂模糊" else "Apple Music style with classic real-time frosted blur"),
                    UiStyle.MONET to (if (zh) "动态折射、RGB 色散、高光、内阴影与弹性玻璃" else "Refraction, RGB dispersion, highlights, inner shadows and elastic glass")
                )
                var selectedStyle by remember(uiStyle) { mutableStateOf(uiStyle) }
                Card(Modifier.fillMaxWidth().museGlass(backdrop, RoundedCornerShape(18.dp), cardBg),
                    colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                    shape = RoundedCornerShape(14.dp)) {
                    Column {
                        styles.forEachIndexed { idx, (style, label) ->
                            Row(Modifier.fillMaxWidth()
                                .clickable {
                                    selectedStyle = style
                                    onUiStyleChanged(style)
                                }
                                .padding(horizontal = 18.dp, vertical = 14.dp),
                                verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(label, color = textPrimary, fontSize = 15.sp,
                                        fontWeight = FontWeight.Medium)
                                    Spacer(Modifier.height(2.dp))
                                    Text(styleDesc[style] ?: "", color = textSecondary, fontSize = 12.sp)
                                }
                                RadioButton(
                                    selected = selectedStyle == style,
                                    onClick = { selectedStyle = style; onUiStyleChanged(style) },
                                    colors = RadioButtonDefaults.colors(selectedColor = accentColor)
                                )
                            }
                            if (idx < styles.size - 1) {
                                HorizontalDivider(color = textSecondary.copy(alpha = 0.1f),
                                    modifier = Modifier.padding(start = 18.dp))
                            }
                        }
                    }
                }

                Spacer(Modifier.height(24.dp))

                // ── Backup ──
                Text(if (zh) "完整备份" else "Full Backup", color = accentColor, fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 8.dp))
                Card(Modifier.fillMaxWidth().museGlass(backdrop, RoundedCornerShape(18.dp), cardBg),
                    colors = CardDefaults.cardColors(containerColor = Color.Transparent), shape = RoundedCornerShape(14.dp)) {
                    Column(Modifier.padding(18.dp)) {
                        Text(if (zh) "备份所有设置与模块" else "Back up all settings and modules", color = textPrimary,
                            fontSize = 15.sp, fontWeight = FontWeight.Medium)
                        Spacer(Modifier.height(4.dp))
                        Text(if (zh) "包含壁纸、视频、头像、歌单封面、插件配置和外部模块文件" else "Includes media, playlists, plugin configuration and external modules",
                            color = textSecondary, fontSize = 12.sp, lineHeight = 18.sp)
                        Spacer(Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedButton(onClick = onBackup, modifier = Modifier.weight(1f)) {
                                Text(if (zh) "导出备份" else "Export")
                            }
                            Button(onClick = onRestore, modifier = Modifier.weight(1f)) {
                                Text(if (zh) "还原备份" else "Restore")
                            }
                        }
                    }
                }

                Spacer(Modifier.height(24.dp))

                // ── Tips ──
                Card(Modifier.fillMaxWidth().museGlass(backdrop, RoundedCornerShape(18.dp), cardBg),
                    colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                    shape = RoundedCornerShape(14.dp)) {
                    Column(Modifier.padding(20.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Info, contentDescription = null,
                                tint = accentColor, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(if (zh) "提示" else "Tip",
                                color = textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            if (zh) "• 在「设置」中可更换壁纸和视频背景\n• 视频背景已自动静音\n• 调整遮罩透明度让背景更明显\n• 浅色模式建议遮罩高一些"
                            else "• Change wallpaper/video in Settings\n• Video is auto-muted\n• Adjust overlay to show background\n• Light mode: higher overlay recommended",
                            color = textSecondary, fontSize = 12.sp, lineHeight = 20.sp
                        )
                    }
                }

                Spacer(Modifier.height(100.dp))
            }
        }
    }
}
