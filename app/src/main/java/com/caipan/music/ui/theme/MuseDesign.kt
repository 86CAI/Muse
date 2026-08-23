package com.caipan.music.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Shared visual tokens. Colors should normally come from MaterialTheme.colorScheme. */
object MuseDesign {
    // ── 品牌色 ──
    val Red = Color(0xFFFA2D55)
    val RedLight = Color(0xFFFF6B81)
    val RedDark = Color(0xFFD91E42)

    // ── 语义色 ──
    val Success = Color(0xFF34C759)
    val Warning = Color(0xFFFF9500)
    val Error = Color(0xFFFF3B30)

    // ── 中性色阶（深色模式） ──
    val NeutralDark900 = Color(0xFF09090B)
    val NeutralDark800 = Color(0xFF18181B)
    val NeutralDark700 = Color(0xFF29292E)
    val NeutralDark600 = Color(0xFF3A3A40)
    val NeutralDark500 = Color(0xFF626269)
    val NeutralDark400 = Color(0xFF8E8E96)
    val NeutralDark300 = Color(0xFFB7B7BF)
    val NeutralDark200 = Color(0xFFD0D0D6)
    val NeutralDark100 = Color(0xFFE7E7EC)
    val NeutralDark50 = Color(0xFFF4F4F6)

    // ── 中性色阶（浅色模式） ──
    val NeutralLight900 = Color(0xFF1C1C1E)
    val NeutralLight800 = Color(0xFF2C2C2E)
    val NeutralLight700 = Color(0xFF3A3A3C)
    val NeutralLight600 = Color(0xFF48484A)
    val NeutralLight500 = Color(0xFF636366)
    val NeutralLight400 = Color(0xFF8E8E93)
    val NeutralLight300 = Color(0xFFAEAEB2)
    val NeutralLight200 = Color(0xFFC7C7CC)
    val NeutralLight100 = Color(0xFFE5E5EA)
    val NeutralLight50 = Color(0xFFF2F2F7)

    // ── 间距系统（4 的倍数） ──
    val Spacing4 = 4.dp
    val Spacing8 = 8.dp
    val Spacing12 = 12.dp
    val Spacing16 = 16.dp
    val Spacing20 = 20.dp
    val Spacing24 = 24.dp
    val Spacing32 = 32.dp
    val Spacing40 = 40.dp
    val Spacing48 = 48.dp

    // 兼容旧命名
    val PagePadding = Spacing20
    val CompactPadding = Spacing12
    val SectionGap = Spacing32
    val ItemGap = Spacing12
    val DenseGap = Spacing8

    // ── 圆角系统（4 级） ──
    val RadiusCompact = 12.dp   // 小按钮、标签
    val RadiusStandard = 16.dp  // 标准卡片、输入框
    val RadiusCard = 20.dp      // 大卡片、封面
    val RadiusFloating = 28.dp  // 浮层、底部表单

    // 兼容旧命名
    val CardRadius = RadiusCard
    val CompactRadius = RadiusCompact
    val FloatingRadius = RadiusFloating
    val ArtworkRadius = RadiusStandard
    val SheetRadius = RadiusFloating

    // ── 尺寸 ──
    val MinTouch = 48.dp
    val ListItemMinHeight = 64.dp
    val TopBarHeight = 64.dp
    val BottomBarHeight = 80.dp

    // ── 玻璃材质 ──
    const val GlassAlphaLight = 0.78f
    const val GlassAlphaDark = 0.70f
    const val GlassBorderAlphaLight = 0.34f
    const val GlassBorderAlphaDark = 0.12f

    // ── 动效时长 ──
    const val DurationFast = 150
    const val DurationNormal = 250
    const val DurationSlow = 400

    // ── 字体大小扩展 ──
    val FontDisplay = 34.sp
    val FontHeadline = 28.sp
    val FontTitle = 22.sp
    val FontBody = 16.sp
    val FontCaption = 13.sp
    val FontMicro = 11.sp
}
