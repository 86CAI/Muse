package com.caipan.music.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.painterResource
import com.caipan.music.R
import com.caipan.music.player.PlaybackSettings
import com.caipan.music.player.ReverbPreset
import com.caipan.music.plugin.BlurLocation
import com.kyant.backdrop.Backdrop
import kotlin.math.roundToInt

@Composable
fun PlaybackSettingsSheet(
    settings: PlaybackSettings,
    sleepTimerRemainingMs: Long,
    backdrop: Backdrop?,
    liquidGlass: Boolean,
    onUpdate: ((PlaybackSettings) -> PlaybackSettings) -> Unit,
    onDismiss: () -> Unit
) {
    val primary = MaterialTheme.colorScheme.onSurface
    val secondary = MaterialTheme.colorScheme.onSurfaceVariant
    val cardColor = MaterialTheme.colorScheme.surfaceContainerHigh
    // 跟随全局模糊半径，与播放器/卡片同步
    val glassConfig by (androidx.compose.ui.platform.LocalContext.current.applicationContext
        as? com.caipan.music.MuseApplication)
        ?.glassConfigStore?.state?.collectAsState() ?: remember { mutableStateOf(MuseGlassConfig()) }

    CompositionLocalProvider(LocalMuseControlBlurLocation provides BlurLocation.SHEETS) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.35f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss
            )
    ) {
        val shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .heightIn(max = 600.dp)
                .shadow(20.dp, shape)
                .museGlass(
                    backdrop,
                    shape,
                    // 浅色玻璃质感：壁纸偏暗时仍能看到明显的模糊样式
                    if (liquidGlass) MaterialTheme.colorScheme.surface.copy(alpha = .36f)
                    else Color.White.copy(alpha = .10f),
                    blurRadius = glassConfig.blurRadius.dp,
                    liquidGlass = liquidGlass,
                    location = BlurLocation.SHEETS
                )
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            DialogBlurEffect(location = BlurLocation.SHEETS)
            // 顶部高光：强化玻璃质感可见性
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(
                        Brush.horizontalGradient(
                            listOf(
                                Color.Transparent,
                                Color.White.copy(alpha = if (liquidGlass) 0.18f else 0.10f),
                                Color.Transparent
                            )
                        )
                    )
                    .padding(horizontal = 28.dp)
            )

            Box(
                Modifier
                    .align(Alignment.CenterHorizontally)
                    .width(40.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    // 面板底色很淡（磨砂 10%），手柄必须不透明才可见
                    .background(MaterialTheme.colorScheme.onSurface)
            )
            Spacer(Modifier.height(14.dp))

            Box(Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
                Text(
                    "播放设置",
                    color = primary,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.align(Alignment.Center)
                )
                MuseIconButton(
                    onClick = onDismiss,
                    modifier = Modifier.align(Alignment.CenterEnd).size(48.dp)
                ) {
                    Icon(painterResource(R.drawable.ic_apple_x), "关闭", tint = secondary, modifier = Modifier.size(22.dp))
                }
            }

            // ── 卡片 1：播放速度 ──
            SettingsCard(cardColor, "播放速度", backdrop) {
                val speed = settings.playbackSpeed
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "当前速度",
                        color = secondary,
                        fontSize = 13.sp,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        String.format("%.2f", speed).trimEnd('0').trimEnd('.') + "x",
                        color = primary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                }
                Spacer(Modifier.height(4.dp))
                MuseGlassSlider(
                    value = speed,
                    onValueChange = { v -> onUpdate { it.copy(playbackSpeed = v) } },
                    valueRange = 0.5f..2.0f,
                    accentColor = MaterialTheme.colorScheme.primary,
                    backdrop = backdrop
                )
                Spacer(Modifier.height(8.dp))
                ToggleRow("保持音调", settings.preservePitch, primary, backdrop) {
                    onUpdate { it.copy(preservePitch = !settings.preservePitch) }
                }
            }

            Spacer(Modifier.height(14.dp))

            // ── 卡片 2：跨歌曲淡入淡出 ──
            SettingsCard(cardColor, "跨歌曲淡入淡出", backdrop) {
                ToggleRow("启用淡入淡出", settings.crossfadeEnabled, primary, backdrop) {
                    onUpdate { it.copy(crossfadeEnabled = !settings.crossfadeEnabled) }
                }
                AnimatedVisibility(
                    visible = settings.crossfadeEnabled,
                    enter = fadeIn(tween(220)) + slideInVertically(initialOffsetY = { -it / 2 }, animationSpec = tween(220, easing = FastOutSlowInEasing)),
                    exit = fadeOut(tween(160)) + slideOutVertically(targetOffsetY = { -it / 2 }, animationSpec = tween(160))
                ) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "时长",
                            color = secondary,
                            fontSize = 13.sp,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            "${settings.crossfadeMs / 1000} 秒",
                            color = primary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    MuseGlassSlider(
                        value = settings.crossfadeMs.toFloat(),
                        onValueChange = { v -> onUpdate { it.copy(crossfadeMs = v.toInt()) } },
                        valueRange = 1000f..12000f,
                        accentColor = MaterialTheme.colorScheme.primary,
                        backdrop = backdrop
                    )
                }
            }

            Spacer(Modifier.height(14.dp))

            // ── 卡片：歌词 ──
            SettingsCard(cardColor, "歌词", backdrop) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "字号",
                        color = secondary,
                        fontSize = 13.sp,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        "${settings.lyricsFontSize} sp",
                        color = primary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
                MuseGlassSlider(
                    value = settings.lyricsFontSize.toFloat(),
                    onValueChange = { v -> onUpdate { it.copy(lyricsFontSize = v.roundToInt()) } },
                    valueRange = 14f..36f,
                    accentColor = MaterialTheme.colorScheme.primary,
                    backdrop = backdrop
                )
            }

            Spacer(Modifier.height(14.dp))

            // ── 卡片：界面（模糊强度，与播放器/卡片全局同步）──
            val glassApp = androidx.compose.ui.platform.LocalContext.current.applicationContext
                as? com.caipan.music.MuseApplication
            // 拖动时只更新内存预览（所有玻璃实时跟随），松手才落盘，避免每帧 JSON+SharedPreferences 写盘风暴
            var blurRadiusPreview by remember(glassConfig.blurRadius) {
                androidx.compose.runtime.mutableFloatStateOf(glassConfig.blurRadius)
            }
            SettingsCard(cardColor, "界面", backdrop) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "模糊强度",
                        color = secondary,
                        fontSize = 13.sp,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        "${blurRadiusPreview.toInt()}",
                        color = primary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
                MuseGlassSlider(
                    value = blurRadiusPreview,
                    onValueChange = { v ->
                        blurRadiusPreview = v
                        glassApp?.glassConfigStore?.setPreview(glassConfig.copy(blurRadius = v))
                    },
                    onValueChangeFinished = {
                        glassApp?.glassConfigStore?.set(glassConfig.copy(blurRadius = blurRadiusPreview))
                    },
                    valueRange = 0f..64f,
                    accentColor = MaterialTheme.colorScheme.primary,
                    backdrop = backdrop
                )
            }

            Spacer(Modifier.height(14.dp))

            // ── 卡片 3：音效 ──
            SettingsCard(cardColor, "音效", backdrop) {
                ToggleRow("低音增强", settings.bassBoostEnabled, primary, backdrop) {
                    onUpdate { it.copy(bassBoostEnabled = !settings.bassBoostEnabled) }
                }
                AnimatedVisibility(
                    visible = settings.bassBoostEnabled,
                    enter = fadeIn(tween(220)) + slideInVertically(initialOffsetY = { -it / 2 }, animationSpec = tween(220, easing = FastOutSlowInEasing)),
                    exit = fadeOut(tween(160)) + slideOutVertically(targetOffsetY = { -it / 2 }, animationSpec = tween(160))
                ) {
                    MuseGlassSlider(
                        value = settings.bassBoostStrength.toFloat(),
                        onValueChange = { v -> onUpdate { it.copy(bassBoostStrength = v.toInt()) } },
                        valueRange = 0f..1000f,
                        accentColor = MaterialTheme.colorScheme.primary,
                        backdrop = backdrop
                    )
                }
                ToggleRow("空间音频", settings.virtualizerEnabled, primary, backdrop) {
                    onUpdate { it.copy(virtualizerEnabled = !settings.virtualizerEnabled) }
                }
                AnimatedVisibility(
                    visible = settings.virtualizerEnabled,
                    enter = fadeIn(tween(220)) + slideInVertically(initialOffsetY = { -it / 2 }, animationSpec = tween(220, easing = FastOutSlowInEasing)),
                    exit = fadeOut(tween(160)) + slideOutVertically(targetOffsetY = { -it / 2 }, animationSpec = tween(160))
                ) {
                    MuseGlassSlider(
                        value = settings.virtualizerStrength.toFloat(),
                        onValueChange = { v -> onUpdate { it.copy(virtualizerStrength = v.toInt()) } },
                        valueRange = 0f..1000f,
                        accentColor = MaterialTheme.colorScheme.primary,
                        backdrop = backdrop
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text("混响", color = secondary, fontSize = 13.sp)
                Spacer(Modifier.height(6.dp))
                OptionGrid(
                    columns = 4,
                    options = ReverbPreset.entries.map { preset ->
                        GridOption(preset.label, settings.reverbPreset == preset) {
                            onUpdate { it.copy(reverbPreset = preset) }
                        }
                    }
                )
            }

            Spacer(Modifier.height(14.dp))

            // ── 卡片 4：睡眠定时器 ──
            SettingsCard(cardColor, "睡眠定时器", backdrop) {
                if (sleepTimerRemainingMs > 0) {
                    val mins = sleepTimerRemainingMs / 60000
                    val secs = (sleepTimerRemainingMs % 60000) / 1000
                    Text(
                        "剩余 ${"%02d".format(mins)}:${"%02d".format(secs)}",
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(8.dp))
                }
                OptionGrid(
                    columns = 3,
                    options = buildList {
                        add(GridOption("关闭", !settings.sleepTimerActive) {
                            onUpdate { it.copy(sleepTimerMinutes = 0, sleepTimerEndOfSong = false) }
                        })
                        listOf(15 to "15分钟", 30 to "30分钟", 45 to "45分钟", 60 to "60分钟").forEach { (mins, label) ->
                            add(GridOption(label, settings.sleepTimerMinutes == mins && !settings.sleepTimerEndOfSong) {
                                onUpdate { it.copy(sleepTimerMinutes = mins, sleepTimerEndOfSong = false) }
                            })
                        }
                        add(GridOption("播完当前", settings.sleepTimerEndOfSong) {
                            onUpdate { it.copy(sleepTimerMinutes = 0, sleepTimerEndOfSong = true) }
                        })
                    }
                )
            }

            Spacer(Modifier.height(24.dp))
        }
    }
    }
}

@Composable
private fun SettingsCard(
    cardColor: Color,
    title: String,
    backdrop: Backdrop?,
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = RoundedCornerShape(20.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .museGlass(
                backdrop = backdrop,
                shape = shape,
                tint = cardColor.copy(alpha = .58f),
                location = BlurLocation.CARDS,
                readabilityBoost = true
            )
            .clip(shape)
            .animateContentSize()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Text(
            title,
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.5.sp
        )
        Spacer(Modifier.height(6.dp))
        content()
    }
}

@Composable
private fun ToggleRow(
    label: String,
    checked: Boolean,
    textColor: Color,
    backdrop: Backdrop?,
    onToggle: () -> Unit
) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = textColor, fontSize = 16.sp, modifier = Modifier.weight(1f))
        MuseGlassSwitch(
            checked = checked,
            onCheckedChange = { onToggle() },
            accentColor = MaterialTheme.colorScheme.primary,
            backdrop = backdrop
        )
    }
}

private data class GridOption(val label: String, val selected: Boolean, val onClick: () -> Unit)

@Composable
private fun OptionGrid(
    columns: Int,
    modifier: Modifier = Modifier,
    options: List<GridOption>
) {
    val rows = options.chunked(columns)
    Column(
        modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        rows.forEach { rowOptions ->
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                rowOptions.forEach { option ->
                    OptionCell(
                        label = option.label,
                        selected = option.selected,
                        onClick = option.onClick,
                        modifier = Modifier.weight(1f)
                    )
                }
                repeat(columns - rowOptions.size) {
                    Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun OptionCell(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bg = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest
    val fg = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
    Box(
        modifier
            .clip(RoundedCornerShape(14.dp))
            .background(bg)
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            color = fg,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
