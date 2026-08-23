/*
 * MeloX 设置行与分组
 *
 * Ported from NEORUAA/Mei_MeloX_Android
 * (ui/screen/setting/SettingScreen.kt / AppearanceSettings.kt /
 *  GeneralSettings.kt / PlaySetting.kt / LyricsSettings.kt)：
 * 分区标题 subheadline + IosGroupedList；行为 GlassCard + Row(padding 14dp)，
 * 图标 24dp、文本区 padding horizontal 13dp、右侧 GlassToggle / IosPopupButton。
 *
 * Upstream: https://github.com/NEORUAA/Mei_MeloX_Android
 * License: GNU General Public License v3.0 (GPL-3.0) - see licenses/GPL-3.0.txt
 * Full attribution list: THIRD_PARTY_NOTICES.md
 */
package com.caipan.music.ui.melox

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun MeloXSettingsSectionTitle(title: String) {
    Text(
        title,
        style = IosTypography.subheadline,
        color = LocalGlassColors.current.secondaryContent,
        modifier = Modifier.padding(top = 12.dp, start = 16.dp),
    )
}

@Composable
fun MeloXSettingsGroup(
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        MeloXSettingsSectionTitle(title)
        IosGroupedList(content = content)
    }
}

/** 导航行：图标 23dp + 标题 + chevron.forward。 */
@Composable
fun MeloXSettingsEntry(
    title: String,
    symbol: SfSymbol,
    showTopSeparator: Boolean = true,
    subtitle: String? = null,
    detail: String? = null,
    onClick: () -> Unit,
) {
    val colors = LocalGlassColors.current
    IosListRow(
        title = title,
        subtitle = subtitle,
        detail = detail,
        leading = { SfIcon(symbol, null, size = 23.dp, tint = colors.accent) },
        showTopSeparator = showTopSeparator,
        onClick = onClick,
    )
}

/** 开关行：整行可点，右侧 GlassToggle。 */
@Composable
fun MeloXSettingsToggleRow(
    title: String,
    symbol: SfSymbol?,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    description: String? = null,
    enabled: Boolean = true,
) {
    val colors = LocalGlassColors.current
    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = { if (enabled) onCheckedChange(!checked) },
    ) {
        Row(
            Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            symbol?.let { SfIcon(it, null, size = 24.dp, tint = colors.accent) }
            Column(
                Modifier
                    .weight(1f)
                    .padding(horizontal = if (symbol != null) 13.dp else 0.dp),
            ) {
                Text(title, style = IosTypography.body, color = colors.content)
                description?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.secondaryContent,
                    )
                }
            }
            GlassToggle(
                checked = checked,
                onCheckedChange = onCheckedChange,
                enabled = enabled,
            )
        }
    }
}

/** 下拉选择行：右侧 IosPopupButton。 */
@Composable
fun <T> MeloXSettingsChoiceRow(
    title: String,
    symbol: SfSymbol?,
    selected: T,
    values: List<T>,
    valueLabel: @Composable (T) -> String,
    onSelected: (T) -> Unit,
    enabled: Boolean = true,
) {
    val colors = LocalGlassColors.current
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            symbol?.let { SfIcon(it, null, size = 24.dp, tint = colors.accent) }
            Text(
                title,
                style = IosTypography.body,
                color = colors.content,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = if (symbol != null) 13.dp else 0.dp),
            )
            IosPopupButton(
                selected = selected,
                items = values,
                onSelected = onSelected,
                label = valueLabel,
                enabled = enabled,
            )
        }
    }
}

/** 滑块行：标题 + 右对齐读数 + 全宽 GlassSlider。 */
@Composable
fun MeloXSettingsSliderRow(
    title: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    readout: String? = null,
    enabled: Boolean = true,
) {
    val colors = LocalGlassColors.current
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    title,
                    style = IosTypography.body,
                    color = colors.content,
                    modifier = Modifier.weight(1f),
                )
                readout?.let {
                    Text(it, style = IosTypography.subheadline, color = colors.secondaryContent)
                }
            }
            GlassSlider(
                value = value,
                onValueChange = onValueChange,
                valueRange = valueRange,
                enabled = enabled,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** 值展示行：右侧自定义槽（例如音质选择）。 */
@Composable
fun MeloXSettingsValueRow(
    title: String,
    symbol: SfSymbol?,
    value: @Composable () -> Unit,
) {
    val colors = LocalGlassColors.current
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            symbol?.let { SfIcon(it, null, size = 24.dp, tint = colors.accent) }
            Text(
                title,
                style = IosTypography.body,
                color = colors.content,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = if (symbol != null) 14.dp else 0.dp),
            )
            value()
        }
    }
}

/** 说明行：图标 + 灰色说明文本。 */
@Composable
fun MeloXSettingsHintRow(
    text: String,
    symbol: SfSymbol? = null,
) {
    val colors = LocalGlassColors.current
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            symbol?.let { SfIcon(it, null, size = 24.dp, tint = colors.accent) }
            Text(
                text,
                style = MaterialTheme.typography.bodySmall,
                color = colors.secondaryContent,
                modifier = Modifier.padding(start = if (symbol != null) 12.dp else 0.dp),
            )
        }
    }
}
