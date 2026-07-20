package com.caipan.music.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat

private val MuseRed = Color(0xFFFA2D55)

private val DarkColorScheme = darkColorScheme(
    primary = MuseRed,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF5E1020),
    onPrimaryContainer = Color(0xFFFFD9DF),
    secondary = Color(0xFF64D2FF),
    onSecondary = Color(0xFF003546),
    background = Color(0xFF09090B),
    onBackground = Color(0xFFF4F4F6),
    surface = Color(0xFF18181B),
    onSurface = Color(0xFFF4F4F6),
    surfaceVariant = Color(0xFF29292E),
    onSurfaceVariant = Color(0xFFB7B7BF),
    surfaceContainerLowest = Color(0xFF09090B),
    surfaceContainerLow = Color(0xFF121215),
    surfaceContainer = Color(0xFF18181B),
    surfaceContainerHigh = Color(0xFF202024),
    surfaceContainerHighest = Color(0xFF29292E),
    outline = Color(0xFF626269),
    outlineVariant = Color(0xFF35353A),
    error = Color(0xFFFF6B72),
    onError = Color(0xFF4A0008)
)

private val LightColorScheme = lightColorScheme(
    primary = MuseRed,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFD9DF),
    onPrimaryContainer = Color(0xFF3F0010),
    secondary = Color(0xFF006781),
    onSecondary = Color.White,
    background = Color(0xFFF6F6F8),
    onBackground = Color(0xFF1C1C1E),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1C1C1E),
    surfaceVariant = Color(0xFFE7E7EC),
    onSurfaceVariant = Color(0xFF5F5F66),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFFAFAFC),
    surfaceContainer = Color(0xFFF1F1F4),
    surfaceContainerHigh = Color(0xFFE9E9ED),
    surfaceContainerHighest = Color(0xFFDFDFE4),
    outline = Color(0xFF77777F),
    outlineVariant = Color(0xFFD0D0D6),
    error = Color(0xFFBA1A1A),
    onError = Color.White
)

private val MuseTypography = Typography(
    displaySmall = TextStyle(fontSize = 34.sp, lineHeight = 40.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
    headlineLarge = TextStyle(fontSize = 30.sp, lineHeight = 36.sp, fontWeight = FontWeight.Bold),
    headlineMedium = TextStyle(fontSize = 24.sp, lineHeight = 30.sp, fontWeight = FontWeight.Bold),
    titleLarge = TextStyle(fontSize = 20.sp, lineHeight = 26.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 17.sp, lineHeight = 23.sp, fontWeight = FontWeight.SemiBold),
    titleSmall = TextStyle(fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 23.sp, fontWeight = FontWeight.Normal),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Normal),
    bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 17.sp, fontWeight = FontWeight.Normal),
    labelLarge = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold),
    labelMedium = TextStyle(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium),
    labelSmall = TextStyle(fontSize = 11.sp, lineHeight = 15.sp, fontWeight = FontWeight.Medium)
)

private val MuseShapes = Shapes(
    extraSmall = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
    small = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
    medium = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
    large = androidx.compose.foundation.shape.RoundedCornerShape(22.dp),
    extraLarge = androidx.compose.foundation.shape.RoundedCornerShape(30.dp)
)

@Composable
fun MusicPlayerTheme(
    darkTheme: Boolean = true,
    dynamicColor: Boolean = false,
    primaryColor: Color? = null,
    content: @Composable () -> Unit
) {
    val base = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }
    val accent = primaryColor ?: base.primary
    val onAccent = if (accent.luminance() > 0.52f) Color(0xFF111113) else Color.White
    val colorScheme = base.copy(primary = accent, onPrimary = onAccent)

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = Color.Transparent.toArgb()
            window.navigationBarColor = Color.Transparent.toArgb()
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = MuseTypography,
        shapes = MuseShapes,
        content = content
    )
}
