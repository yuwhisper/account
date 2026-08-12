package com.yuwhisper.account.ui.theme

import androidx.compose.animation.core.tween
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** Warm paper + deep teal — confirm card / ledger shared look. */
private val Paper = Color(0xFFF3F0EA)
private val PaperSurface = Color(0xFFFFFDF9)
private val TealPrimary = Color(0xFF0F766E)
private val TealOnPrimary = Color(0xFFFFFFFF)
private val TealContainer = Color(0xFFD5F0EA)
private val Ink = Color(0xFF1A2422)
private val Muted = Color(0xFF6B7773)
private val Expense = Color(0xFFC0453A)
private val Income = Color(0xFF0F766E)

val AccountExpenseColor = Expense
val AccountIncomeColor = Income
val AccountMutedColor = Muted

private val LightColors = lightColorScheme(
    primary = TealPrimary,
    onPrimary = TealOnPrimary,
    primaryContainer = TealContainer,
    onPrimaryContainer = Ink,
    secondary = Color(0xFF3D5A56),
    onSecondary = TealOnPrimary,
    background = Paper,
    onBackground = Ink,
    surface = PaperSurface,
    onSurface = Ink,
    surfaceVariant = Color(0xFFE8E3DA),
    onSurfaceVariant = Muted,
    outline = Color(0xFFD0C9BE),
)

private val AppTypography = Typography(
    titleLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 22.sp, lineHeight = 28.sp),
    titleMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 17.sp, lineHeight = 24.sp),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    labelLarge = TextStyle(fontWeight = FontWeight.Medium, fontSize = 13.sp, lineHeight = 18.sp),
)

val AccountMotionShort = tween<Float>(durationMillis = 220)
val AccountMotionMedium = tween<Float>(durationMillis = 320)

@Composable
fun AccountTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LightColors,
        typography = AppTypography,
        content = content,
    )
}
