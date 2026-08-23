/*
 * SF Symbols glyph rendering
 *
 * Ported from NEORUAA/Mei_MeloX_Android
 * (app/src/main/java/com/ljyh/mei/ui/glass/SfSymbol.kt). Code points are
 * resolved by the SF Symbols CLI using exact-name matching and rendered with
 * the bundled sf_pro font.
 *
 * Upstream: https://github.com/NEORUAA/Mei_MeloX_Android
 * License: GNU General Public License v3.0 (GPL-3.0) - see licenses/GPL-3.0.txt
 * Full attribution list: THIRD_PARTY_NOTICES.md
 *
 * WARNING - not open source: the glyphs referenced here are Apple SF Symbols,
 * rendered from Apple's SF Pro font (res/font/sf_pro.ttf). Both are covered by
 * Apple's proprietary font/SF Symbols license, which forbids embedding them in
 * shipped software on non-Apple platforms. Replace with Lucide icons (ISC) and
 * a freely redistributable font before publishing public builds.
 * See THIRD_PARTY_NOTICES.md section 2.
 */
package com.caipan.music.ui.melox

import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.res.ResourcesCompat
import com.caipan.music.R

@Immutable
enum class SfSymbol(
    val systemName: String,
    val codePoint: Int,
    val autoMirrored: Boolean = false,
) {
    House("house", 0x10039E),
    Sparkles("sparkles", 0x1001BF),
    MusicNote("music.note", 0x10046A),
    RadioWaves("dot.radiowaves.left.and.right", 0x100319),
    Safari("safari", 0x1003AC),
    MusicNoteList("music.note.list", 0x10046C),
    Microphone("mic", 0x1002B0),
    Heart("heart", 0x1002B4),
    Star("star", 0x1002C2),
    StarFilled("star.fill", 0x1002C3),
    Download("arrow.down.circle", 0x100078),
    Cloud("icloud", 0x10030B),
    Clock("clock", 0x10042B),
    ArrowClockwise("arrow.clockwise", 0x100148),
    Warning("exclamationmark.triangle", 0x1001FE),
    Search("magnifyingglass", 0x1002AB),
    Settings("gear", 0x10035F),
    PersonFilled("person.fill", 0x10026A),
    Waveform("waveform", 0x10066B),
    PlayFilled("play.fill", 0x100284),
    PauseFilled("pause.fill", 0x100286),
    ForwardFilled("forward.fill", 0x10028C),
    BackwardFilled("backward.fill", 0x10028A),
    Ellipsis("ellipsis", 0x100360),
    Close("xmark", 0x100184),
    ChevronBack("chevron.left", 0x100189, autoMirrored = true),
    ChevronForward("chevron.forward", 0x10018C, autoMirrored = true),
    ChevronUpChevronDown("chevron.up.chevron.down", 0x100194),
    Checkmark("checkmark", 0x100185),
    Plus("plus", 0x10017C),
    Minus("minus", 0x10017E),
    Gearshape("gearshape", 0x100362),
    Paintbrush("paintbrush", 0x100487),
    Waveform2("waveform.path", 0x10066D),
    QuoteBubble("quote.bubble", 0x1002F5),
    InfoCircle("info.circle", 0x1001E5),
    Puzzle("puzzlepiece.extension", 0x1006D2),
    Trash("trash", 0x100033),
    PersonCropCircle("person.crop.circle", 0x100274),
    RectangleGrid("rectangle.grid.1x2", 0x1004F2),
    InternalDrive("internaldrive.fill", 0x10064E),
    SliderVertical3("slider.vertical.3", 0x1004AE),
    Photo("photo", 0x1002B8),
    Bold("bold", 0x100150),
    TextFormatSize("textformat.size", 0x100154),
    PlayCircle("play.circle", 0x10027E),
    PauseCircle("pause.circle", 0x100288),
    Ladybug("ladybug", 0x1006B2),

    ;

    companion object {
        fun fromSystemName(name: String): SfSymbol? = entries.firstOrNull { it.systemName == name }
    }
}

@Composable
fun SfIcon(
    symbol: SfSymbol,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color = LocalGroupedListIconColor.current
        ?: LocalGlassContentColor.current
        ?: LocalGlassColors.current.content,
    size: Dp = 24.dp,
    fontSize: TextUnit = size.value.sp,
    weight: androidx.compose.ui.text.font.FontWeight = androidx.compose.ui.text.font.FontWeight.Normal,
    mirrored: Boolean = false,
) {
    val layoutDirection = LocalLayoutDirection.current
    SfIconGlyph(
        codePoint = symbol.codePoint,
        contentDescription = contentDescription,
        modifier = modifier,
        tint = tint,
        size = size,
        fontSize = fontSize,
        weight = weight,
        mirrored = mirrored && symbol.autoMirrored && layoutDirection == LayoutDirection.Rtl,
    )
}

@Composable
private fun SfIconGlyph(
    codePoint: Int,
    contentDescription: String?,
    modifier: Modifier,
    tint: Color,
    size: Dp,
    fontSize: TextUnit,
    weight: androidx.compose.ui.text.font.FontWeight,
    mirrored: Boolean,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val baseTypeface = remember(context) {
        requireNotNull(ResourcesCompat.getFont(context, R.font.sf_pro)) {
            "SF Pro font could not be loaded"
        }
    }
    val typeface = remember(baseTypeface, weight) {
        Typeface.create(baseTypeface, weight.weight, false)
    }
    val glyph = remember(codePoint) { String(Character.toChars(codePoint)) }
    val semanticsModifier = if (contentDescription == null) {
        Modifier.clearAndSetSemantics { }
    } else {
        Modifier.clearAndSetSemantics { this.contentDescription = contentDescription }
    }
    Canvas(
        modifier = modifier
            .size(size)
            .then(semanticsModifier)
            .then(if (mirrored) Modifier.scale(scaleX = -1f, scaleY = 1f) else Modifier),
    ) {
        drawIntoCanvas { canvas ->
            val requestedSize = with(density) { fontSize.toPx() }
            val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
                color = tint.toArgb()
                this.typeface = typeface
                textSize = requestedSize
                textAlign = Paint.Align.LEFT
                fontFeatureSettings = "'ss16' 1"
            }
            val bounds = Rect()
            paint.getTextBounds(glyph, 0, glyph.length, bounds)
            val safeWidth = this.size.width * 0.88f
            val safeHeight = this.size.height * 0.88f
            val scale = minOf(
                1f,
                safeWidth / bounds.width().coerceAtLeast(1),
                safeHeight / bounds.height().coerceAtLeast(1),
            )
            if (scale < 1f) {
                paint.textSize *= scale
                paint.getTextBounds(glyph, 0, glyph.length, bounds)
            }
            // Center from the actual ink bounds, not the font advance or line metrics.
            val x = this.size.width / 2f - (bounds.left + bounds.right) / 2f
            val baseline = this.size.height / 2f - (bounds.top + bounds.bottom) / 2f
            canvas.nativeCanvas.drawText(glyph, x, baseline, paint)
        }
    }
}
