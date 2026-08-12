package com.yuwhisper.account.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** Paper + teal look aligned with confirm-card direction. */
private val Paper = Color(0xFFF7F4EF)
private val PaperSurface = Color(0xFFFFFCF8)
private val TealPrimary = Color(0xFF1B7F6E)
private val TealOnPrimary = Color(0xFFFFFFFF)
private val TealContainer = Color(0xFFD8EFE9)
private val Ink = Color(0xFF1F2A28)
private val Muted = Color(0xFF5C6B67)
private val Expense = Color(0xFFB54A3C)
private val Income = Color(0xFF1B7F6E)

val AccountExpenseColor = Expense
val AccountIncomeColor = Income
val AccountMutedColor = Muted

private val LightColors = lightColorScheme(
    primary = TealPrimary,
    onPrimary = TealOnPrimary,
    primaryContainer = TealContainer,
    onPrimaryContainer = Ink,
    secondary = TealPrimary,
    onSecondary = TealOnPrimary,
    background = Paper,
    onBackground = Ink,
    surface = PaperSurface,
    onSurface = Ink,
    surfaceVariant = Color(0xFFECE7DF),
    onSurfaceVariant = Muted,
    outline = Color(0xFFC9C2B8),
)

@Composable
fun AccountTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LightColors,
        content = content,
    )
}
