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

/** Soft blush pink — premium light theme. */
private val BlushBg = Color(0xFFFFF5F8)
private val BlushSurface = Color(0xFFFFFBFC)
private val RosePrimary = Color(0xFFD4849A)
private val RoseDeep = Color(0xFFB65C78)
private val RoseOnPrimary = Color(0xFFFFFFFF)
private val RoseContainer = Color(0xFFFFE4EC)
private val Ink = Color(0xFF3A2F35)
private val Muted = Color(0xFF8A7A82)
private val Expense = Color(0xFFC45C6A)
private val Income = Color(0xFF7A9E8E)

val AccountExpenseColor = Expense
val AccountIncomeColor = Income
val AccountMutedColor = Muted

private val LightColors = lightColorScheme(
    primary = RosePrimary,
    onPrimary = RoseOnPrimary,
    primaryContainer = RoseContainer,
    onPrimaryContainer = Color(0xFF5C3344),
    secondary = RoseDeep,
    onSecondary = RoseOnPrimary,
    secondaryContainer = Color(0xFFFFEEF3),
    onSecondaryContainer = Ink,
    background = BlushBg,
    onBackground = Ink,
    surface = BlushSurface,
    onSurface = Ink,
    surfaceVariant = Color(0xFFF7E8EE),
    onSurfaceVariant = Muted,
    outline = Color(0xFFE5CDD6),
    error = Expense,
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
