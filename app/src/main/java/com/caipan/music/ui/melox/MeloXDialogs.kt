/*
 * MeloX 弹窗 / 菜单 / 分组行
 *
 * Ported from NEORUAA/Mei_MeloX_Android (ui/glass/Ios27Components.kt)：
 * IosAlertSurface(300dp 宽, 34dp 圆角) + IosAlertButton(48dp 胶囊) +
 * IosMenuItem(44dp 行, 20dp 图标槽) + IosPopupButton(chevron.up.chevron.down) +
 * IosStepper(32dp 高, 46dp 触控) + IosSheetSurface / IosActionSheetContent。
 *
 * Upstream: https://github.com/NEORUAA/Mei_MeloX_Android
 * License: GNU General Public License v3.0 (GPL-3.0) - see licenses/GPL-3.0.txt
 * Full attribution list: THIRD_PARTY_NOTICES.md
 */
package com.caipan.music.ui.melox

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.colorControls
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow
import com.kyant.capsule.ContinuousRoundedRectangle
import com.kyant.shapes.Capsule

val IosModalSheetShape: Shape = ContinuousRoundedRectangle(
    topStart = 34.dp,
    topEnd = 34.dp,
    bottomStart = 58.dp,
    bottomEnd = 58.dp,
)

enum class IosAlertButtonLayout { SideBySide, Stacked }
enum class IosAlertButtonRole { Default, Cancel, Destructive }

data class IosAlertButtonSpec(
    val label: String,
    val onClick: () -> Unit,
    val role: IosAlertButtonRole = IosAlertButtonRole.Default,
    val enabled: Boolean = true,
)

@Composable
fun IosAlertDialog(
    onDismissRequest: () -> Unit,
    title: String,
    message: String? = null,
    modifier: Modifier = Modifier,
    backdrop: Backdrop = LocalGlassBackdrop.current,
    buttonLayout: IosAlertButtonLayout = IosAlertButtonLayout.SideBySide,
    buttons: List<IosAlertButtonSpec> = emptyList(),
    properties: DialogProperties = DialogProperties(
        dismissOnBackPress = true,
        dismissOnClickOutside = true,
        usePlatformDefaultWidth = false,
    ),
    content: @Composable ColumnScope.() -> Unit = {},
) {
    Dialog(onDismissRequest = onDismissRequest, properties = properties) {
        IosAlertSurface(
            modifier = modifier,
            backdrop = backdrop,
            title = title,
            message = message,
        ) {
            content()
            when (buttonLayout) {
                IosAlertButtonLayout.SideBySide -> Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    buttons.forEach { spec ->
                        IosAlertButton(
                            text = spec.label,
                            onClick = spec.onClick,
                            role = spec.role,
                            enabled = spec.enabled,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                IosAlertButtonLayout.Stacked -> Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    buttons.forEach { spec ->
                        IosAlertButton(
                            text = spec.label,
                            onClick = spec.onClick,
                            role = spec.role,
                            enabled = spec.enabled,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun IosAlertSurface(
    modifier: Modifier = Modifier,
    backdrop: Backdrop = LocalGlassBackdrop.current,
    title: String,
    message: String? = null,
    content: @Composable ColumnScope.() -> Unit = {},
) {
    val colors = LocalGlassColors.current
    val isLight = !colors.isDark
    val alertBackgroundAlpha = if (isLight) 0.72f else 0.64f
    val shape = ContinuousRoundedRectangle(34.dp)
    Box(
        modifier = modifier
            .width(300.dp)
            .drawBackdrop(
                backdrop = backdrop,
                shape = { shape },
                effects = {
                    colorControls(
                        brightness = if (isLight) 0.2f else 0f,
                        saturation = 1.5f,
                    )
                    blur((if (isLight) 16.dp else 8.dp).toPx())
                    lens(24.dp.toPx(), 48.dp.toPx(), depthEffect = true)
                },
                highlight = { Highlight.Plain },
                shadow = { Shadow(radius = 48.dp, alpha = 0.25f) },
                innerShadow = { InnerShadow(radius = 8.dp, alpha = 0.12f) },
                onDrawSurface = {
                    if (isLight) {
                        drawRect(
                            Color.White.copy(alpha = 0.70f * alertBackgroundAlpha),
                            blendMode = BlendMode.Lighten,
                        )
                        drawRect(
                            Color(0x1ABFBFBF).copy(alpha = 0.10f * alertBackgroundAlpha),
                            blendMode = BlendMode.Darken,
                        )
                    } else {
                        drawRect(
                            Color(0xB31A1A1A).copy(alpha = 0.70f * alertBackgroundAlpha),
                            blendMode = BlendMode.Luminosity,
                        )
                        drawRect(
                            Color(0xE61A1A1A).copy(alpha = 0.90f * alertBackgroundAlpha),
                            blendMode = BlendMode.Luminosity,
                        )
                        drawRect(
                            Color(0xFF1A1A1A).copy(alpha = alertBackgroundAlpha),
                            blendMode = BlendMode.Lighten,
                        )
                    }
                },
            )
            .padding(14.dp),
    ) {
        CompositionLocalProvider(
            LocalContentColor provides colors.content,
            LocalGlassContentColor provides colors.content,
        ) {
            Column {
                Column(
                    Modifier.padding(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(title, style = IosTypography.headline, color = colors.content)
                    message?.let {
                        Text(it, style = IosTypography.body, color = colors.content)
                    }
                }
                content()
            }
        }
    }
}

@Composable
fun IosAlertButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    role: IosAlertButtonRole = IosAlertButtonRole.Default,
    enabled: Boolean = true,
) {
    val colors = LocalGlassColors.current
    val isLight = !colors.isDark
    val fill = when (role) {
        IosAlertButtonRole.Default -> Color(0xFF0088FF)
        else -> if (isLight) Color(0x28787880) else Color(0x52787880)
    }
    val labelColor = when (role) {
        IosAlertButtonRole.Default -> Color.White
        IosAlertButtonRole.Cancel -> colors.content
        IosAlertButtonRole.Destructive -> colors.destructive
    }
    Box(
        modifier = modifier
            .height(48.dp)
            .clip(Capsule())
            .background(fill.copy(alpha = fill.alpha * if (enabled) 1f else 0.45f))
            .clickable(
                enabled = enabled,
                interactionSource = null,
                indication = null,
                role = Role.Button,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = IosTypography.headline, color = labelColor)
    }
}

@Composable
fun IosAlertFieldGroup(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = LocalGlassColors.current
    Column(
        modifier
            .clip(ContinuousRoundedRectangle(26.dp))
            .background(if (colors.isDark) Color.White.copy(alpha = 0.16f) else Color(0x28787880))
            .padding(bottom = 19.dp),
        content = content,
    )
}

@Composable
fun IosMenuItem(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    systemName: String? = null,
    destructive: Boolean = false,
) {
    val colors = LocalGlassColors.current
    Row(
        modifier
            .fillMaxWidth()
            .height(44.dp)
            .clip(Capsule())
            .clickable(
                interactionSource = null,
                indication = null,
                role = Role.Button,
                onClick = onClick,
            )
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(20.dp), contentAlignment = Alignment.Center) {
            systemName?.let { name ->
                SfSymbol.fromSystemName(name)?.let { symbol ->
                    SfIcon(
                        symbol,
                        null,
                        size = 20.dp,
                        tint = if (destructive) colors.destructive else colors.content,
                    )
                }
            }
        }
        Spacer(Modifier.width(12.dp))
        Text(
            title,
            style = IosTypography.body,
            color = if (destructive) colors.destructive else colors.content,
        )
    }
}

/** 上游设置页的下拉选择：accent 文字 + chevron.up.chevron.down + 勾选项菜单。 */
@Composable
fun <T> IosPopupButton(
    selected: T,
    items: List<T>,
    onSelected: (T) -> Unit,
    label: @Composable (T) -> String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val colors = LocalGlassColors.current
    var expanded by remember { mutableStateOf(false) }
    Box(modifier) {
        Row(
            Modifier
                .clickable(
                    enabled = enabled,
                    interactionSource = null,
                    indication = null,
                    role = Role.Button,
                ) { expanded = true },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                label(selected),
                style = IosTypography.body,
                color = if (enabled) colors.accent else colors.secondaryContent,
            )
            SfIcon(
                SfSymbol.ChevronUpChevronDown,
                null,
                size = 15.dp,
                tint = if (enabled) colors.accent else colors.secondaryContent,
                modifier = Modifier.padding(start = 7.dp),
            )
        }
        if (expanded) {
            IosAlertDialog(
                onDismissRequest = { expanded = false },
                title = label(selected),
                buttons = listOf(
                    IosAlertButtonSpec(
                        label = "取消",
                        onClick = { expanded = false },
                        role = IosAlertButtonRole.Cancel,
                    ),
                ),
            ) {
                Column(Modifier.padding(bottom = 14.dp)) {
                    items.forEach { item ->
                        IosMenuItem(
                            title = label(item),
                            systemName = if (item == selected) "checkmark" else null,
                            onClick = {
                                expanded = false
                                onSelected(item)
                            },
                        )
                    }
                }
            }
        }
    }
}

/** 32dp 高步进器，46dp 触控区，中间 1dp 分隔。 */
@Composable
fun IosStepper(
    value: Int,
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    range: IntRange = Int.MIN_VALUE..Int.MAX_VALUE,
) {
    val colors = LocalGlassColors.current
    val fill = if (colors.isDark) Color.White.copy(alpha = 0.12f) else Color(0x14747480)
    Row(
        modifier
            .height(32.dp)
            .clip(Capsule())
            .background(fill),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(width = 46.dp, height = 32.dp)
                .clickable(
                    enabled = value > range.first,
                    interactionSource = null,
                    indication = null,
                ) { onValueChange(value - 1) },
            contentAlignment = Alignment.Center,
        ) {
            SfIcon(SfSymbol.Minus, null, size = 17.dp, tint = colors.content)
        }
        Box(Modifier.width(1.dp).height(22.dp).background(colors.separator))
        Box(
            Modifier
                .size(width = 46.dp, height = 32.dp)
                .clickable(
                    enabled = value < range.last,
                    interactionSource = null,
                    indication = null,
                ) { onValueChange(value + 1) },
            contentAlignment = Alignment.Center,
        ) {
            SfIcon(SfSymbol.Plus, null, size = 17.dp, tint = colors.content)
        }
    }
}

@Composable
fun IosSheetSurface(
    modifier: Modifier = Modifier,
    backdrop: Backdrop = LocalGlassBackdrop.current,
    shape: Shape = ContinuousRoundedRectangle(38.dp),
    content: @Composable BoxScope.() -> Unit,
) {
    val colors = LocalGlassColors.current
    val isLight = !colors.isDark
    Box(
        modifier = modifier.drawBackdrop(
            backdrop = backdrop,
            shape = { shape },
            effects = {
                vibrancy()
                blur(16.dp.toPx())
                lens(20.dp.toPx(), 34.dp.toPx(), depthEffect = true)
            },
            highlight = {
                Highlight.Default.copy(alpha = if (isLight) 0.58f else 0.38f)
            },
            shadow = { Shadow(radius = 48.dp, alpha = 0.25f) },
            innerShadow = { InnerShadow(radius = 8.dp, alpha = 0.12f) },
            onDrawSurface = {
                drawRect(colors.elevatedBackground.copy(alpha = if (isLight) 0.72f else 0.54f))
                drawRect(Color.White.copy(alpha = if (isLight) 0.12f else 0.04f))
            },
        ),
        content = {
            CompositionLocalProvider(
                LocalContentColor provides colors.content,
                LocalGlassContentColor provides colors.content,
            ) {
                content()
            }
        },
    )
}

/** 上游 IosActionSheetContent：标题 + 说明 + 分组列表。 */
@Composable
fun IosActionSheetContent(
    title: String,
    modifier: Modifier = Modifier,
    message: String? = null,
    showHandle: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = LocalGlassColors.current
    Column(
        modifier
            .padding(horizontal = 16.dp)
            .padding(top = if (showHandle) 4.dp else 18.dp, bottom = 18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (showHandle) {
            Box(Modifier.fillMaxWidth().height(12.dp), contentAlignment = Alignment.TopCenter) {
                Box(
                    Modifier
                        .size(width = 58.dp, height = 4.dp)
                        .background(colors.tertiaryContent.copy(alpha = 0.55f), Capsule()),
                )
            }
        }
        Column(Modifier.padding(horizontal = 8.dp)) {
            Text(title, style = IosTypography.headline, color = colors.content)
            message?.let {
                Text(
                    it,
                    style = IosTypography.subheadline,
                    color = colors.secondaryContent,
                    modifier = Modifier.padding(top = 3.dp),
                )
            }
        }
        IosGroupedList(content = content)
    }
}

/** 底部弹层容器：38dp/34dp 圆角玻璃 + 58 × 4dp 抓手。 */
@Composable
fun MeloXBottomSheetContainer(
    modifier: Modifier = Modifier,
    backdrop: Backdrop = LocalGlassBackdrop.current,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = LocalGlassColors.current
    IosSheetSurface(
        modifier = modifier.fillMaxWidth(),
        backdrop = backdrop,
        shape = IosModalSheetShape,
    ) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding()) {
            Box(Modifier.fillMaxWidth().height(16.dp), contentAlignment = Alignment.TopCenter) {
                Box(
                    Modifier
                        .padding(top = 5.dp)
                        .size(width = 58.dp, height = 4.dp)
                        .background(colors.tertiaryContent.copy(alpha = 0.55f), Capsule()),
                )
            }
            content()
        }
    }
}
