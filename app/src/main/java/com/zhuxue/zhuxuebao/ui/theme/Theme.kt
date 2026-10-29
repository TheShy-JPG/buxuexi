package com.zhuxue.zhuxuebao.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.zhuxue.zhuxuebao.data.AppSettings

private val LightColors = lightColorScheme(
    primary = Color(0xFF2E6F57),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD8EDE4),
    onPrimaryContainer = Color(0xFF0C2A20),
    secondary = Color(0xFFB9772F),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFFBEBD6),
    onSecondaryContainer = Color(0xFF3A2405),
    tertiary = Color(0xFF3D5F88),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFD9E6F7),
    onTertiaryContainer = Color(0xFF102238),
    background = Color(0xFFFAF8F3),
    onBackground = Color(0xFF1D211E),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1D211E),
    surfaceVariant = Color(0xFFF0EEE7),
    onSurfaceVariant = Color(0xFF5B5F59),
    outline = Color(0xFFD7D3C7),
    outlineVariant = Color(0xFFE8E4DA),
    error = Color(0xFFB3261E),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFF9DEDC),
    onErrorContainer = Color(0xFF410E0B)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF8FD3B4),
    onPrimary = Color(0xFF003825),
    primaryContainer = Color(0xFF17503B),
    onPrimaryContainer = Color(0xFFB5EFD7),
    secondary = Color(0xFFE7B97D),
    onSecondary = Color(0xFF452B08),
    secondaryContainer = Color(0xFF5F411C),
    onSecondaryContainer = Color(0xFFFFDDB6),
    tertiary = Color(0xFFA8C7EB),
    onTertiary = Color(0xFF0D2A45),
    tertiaryContainer = Color(0xFF294A6B),
    onTertiaryContainer = Color(0xFFD5E4F7),
    background = Color(0xFF111512),
    onBackground = Color(0xFFE2E6E1),
    surface = Color(0xFF191F1B),
    onSurface = Color(0xFFE2E6E1),
    surfaceVariant = Color(0xFF3F4640),
    onSurfaceVariant = Color(0xFFC3C9C2),
    outline = Color(0xFF8D948D),
    outlineVariant = Color(0xFF3F4640),
    error = Color(0xFFF2B8B5),
    onError = Color(0xFF601410),
    errorContainer = Color(0xFF8C1D18),
    onErrorContainer = Color(0xFFF9DEDC)
)

private val AppTypography = Typography(
    headlineMedium = TextStyle(fontWeight = FontWeight.Bold, fontSize = 26.sp, lineHeight = 32.sp),
    headlineSmall = TextStyle(fontWeight = FontWeight.Bold, fontSize = 22.sp, lineHeight = 28.sp),
    titleLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 19.sp, lineHeight = 25.sp),
    titleMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 22.sp),
    titleSmall = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 21.sp),
    bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 18.sp),
    labelLarge = TextStyle(fontWeight = FontWeight.Medium, fontSize = 14.sp),
    labelMedium = TextStyle(fontWeight = FontWeight.Medium, fontSize = 12.sp),
    labelSmall = TextStyle(fontWeight = FontWeight.Medium, fontSize = 11.sp)
)

fun parseHexColor(value: String): Color? {
    val hex = value.trim().removePrefix("#")
    if (hex.length != 6 && hex.length != 8) return null
    val parsed = hex.toLongOrNull(16) ?: return null
    return if (hex.length == 6) {
        Color(0xFF000000L or parsed)
    } else {
        Color(parsed)
    }
}

private fun customColorScheme(backgroundHex: String, fontHex: String): ColorScheme {
    val background = parseHexColor(backgroundHex) ?: LightColors.background
    val onBackground = parseHexColor(fontHex) ?: LightColors.onBackground
    val isDark = background.luminance() < 0.5f
    val base = if (isDark) DarkColors else LightColors
    return base.copy(
        background = background,
        onBackground = onBackground,
        surface = lerp(background, onBackground, 0.06f),
        onSurface = onBackground,
        surfaceVariant = lerp(background, onBackground, 0.12f),
        onSurfaceVariant = lerp(onBackground, background, 0.35f),
        outline = lerp(background, onBackground, 0.3f),
        outlineVariant = lerp(background, onBackground, 0.16f),
        primaryContainer = lerp(background, base.primary, 0.18f),
        onPrimaryContainer = onBackground
    )
}

@Composable
fun ZhuxueTheme(
    themeMode: String = AppSettings.THEME_LIGHT,
    customBackground: String = "#FAF8F3",
    customFont: String = "#1D211E",
    content: @Composable () -> Unit
) {
    val colorScheme = when (themeMode.uppercase()) {
        AppSettings.THEME_DARK -> DarkColors
        AppSettings.THEME_CUSTOM -> customColorScheme(customBackground, customFont)
        else -> LightColors
    }
    MaterialTheme(
        colorScheme = colorScheme,
        typography = AppTypography,
        content = content
    )
}