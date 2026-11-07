package com.classflow.timetable.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Material3 配色从「玻璃调色板」派生，保证两套体系不会打架。
 * 这样即使某个页面还在用 Material 组件，颜色也自动跟主题一致。
 */
private fun lightScheme(p: GlassPalette) = lightColorScheme(
    primary = p.accent,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDDE3FF),
    onPrimaryContainer = Color(0xFF06124F),
    secondary = Color(0xFF6B5CA8),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFEDE6FF),
    onSecondaryContainer = Color(0xFF231547),
    background = Color(0xFFE3EAFB),
    onBackground = p.textPrimary,
    surface = Color(0xCCFFFFFF),
    onSurface = p.textPrimary,
    surfaceVariant = Color(0x33FFFFFF),
    onSurfaceVariant = p.textSecondary,
    outline = Color(0x33707585),
    error = p.danger,
    onError = Color.White,
)

private fun darkScheme(p: GlassPalette) = darkColorScheme(
    primary = p.accent,
    onPrimary = Color(0xFF0A1030),
    primaryContainer = Color(0xFF25306B),
    onPrimaryContainer = Color(0xFFDCE2FF),
    secondary = Color(0xFFC4B8FF),
    onSecondary = Color(0xFF231547),
    secondaryContainer = Color(0xFF2C2450),
    onSecondaryContainer = Color(0xFFE8E0FF),
    background = Color(0xFF080B14),
    onBackground = p.textPrimary,
    surface = Color(0x1A101726),
    onSurface = p.textPrimary,
    surfaceVariant = Color(0x1FFFFFFF),
    onSurfaceVariant = p.textSecondary,
    outline = Color(0x33FFFFFF),
    error = p.danger,
    onError = Color(0xFF2A0505),
)

/** 标题字重比默认更重一点，玻璃背景上细字会发虚。 */
private val ClassFlowTypography = Typography().let { base ->
    Typography(
        displaySmall = base.displaySmall.copy(fontWeight = FontWeight.SemiBold),
        headlineMedium = base.headlineMedium.copy(fontWeight = FontWeight.SemiBold),
        headlineSmall = base.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
        titleLarge = base.titleLarge.copy(fontWeight = FontWeight.SemiBold),
        titleMedium = base.titleMedium.copy(fontWeight = FontWeight.Medium),
        bodyLarge = base.bodyLarge.copy(lineHeight = 24.sp),
        labelLarge = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium),
    )
}

@Composable
fun ClassFlowTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val palette = rememberGlassPalette(darkTheme)
    CompositionLocalProvider(LocalGlassPalette provides palette) {
        MaterialTheme(
            colorScheme = if (darkTheme) darkScheme(palette) else lightScheme(palette),
            typography = ClassFlowTypography,
            content = content,
        )
    }
}
