package com.yuwhisper.account.ui.theme

import androidx.compose.animation.core.spring
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.Font
import com.yuwhisper.account.R
import android.app.Activity
import androidx.core.view.WindowCompat
import androidx.compose.ui.platform.LocalView
import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val LightColors = lightColorScheme(
    primary = Color(0xFF273F35), onPrimary = Color(0xFFF4F5EE),
    primaryContainer = Color(0xFFE9EDE2), onPrimaryContainer = Color(0xFF273F35),
    secondary = Color(0xFF617064), onSecondary = Color(0xFFF9FAF5),
    secondaryContainer = Color(0xFFE9EDE2), onSecondaryContainer = Color(0xFF273F35),
    tertiary = Color(0xFF426952), onTertiary = Color.White,
    tertiaryContainer = Color(0xFFE1EBDF), onTertiaryContainer = Color(0xFF304A37),
    background = Color(0xFFF4F5EE), onBackground = Color(0xFF273F35),
    surface = Color(0xFFF9FAF5), onSurface = Color(0xFF273F35),
    surfaceVariant = Color(0xFFE9EDE2), onSurfaceVariant = Color(0xFF617064),
    outline = Color(0xFF7A877B), outlineVariant = Color(0xFFC5CDC1),
    error = Color(0xFFA54B3B), onError = Color.White,
    errorContainer = Color(0xFFF3E5DD), onErrorContainer = Color(0xFF773D30),
    surfaceDim = Color(0xFFE7EAE0), surfaceBright = Color(0xFFF9FAF5),
    surfaceContainerLowest = Color(0xFFFDFEF9), surfaceContainerLow = Color(0xFFF2F4EC),
    surfaceContainer = Color(0xFFEDF0E6), surfaceContainerHigh = Color(0xFFE7ECDD),
    surfaceContainerHighest = Color(0xFFE1E7D8),
    inverseSurface = Color(0xFF273F35), inverseOnSurface = Color(0xFFF4F5EE), inversePrimary = Color(0xFFB5CDB6),
    scrim = Color(0xFF101513),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF9DB7A3), onPrimary = Color(0xFF101513),
    primaryContainer = Color(0xFF26352B), onPrimaryContainer = Color(0xFFDCE5D8),
    secondary = Color(0xFFA5B49F), onSecondary = Color(0xFF19231D),
    secondaryContainer = Color(0xFF26352B), onSecondaryContainer = Color(0xFFDCE5D8),
    tertiary = Color(0xFFB5CDB6), onTertiary = Color(0xFF19231D),
    tertiaryContainer = Color(0xFF2E4133), onTertiaryContainer = Color(0xFFDCE5D8),
    background = Color(0xFF101513), onBackground = Color(0xFFDCE5D8),
    surface = Color(0xFF19231D), onSurface = Color(0xFFDCE5D8),
    surfaceVariant = Color(0xFF26352B), onSurfaceVariant = Color(0xFFA5B49F),
    outline = Color(0xFF738775), outlineVariant = Color(0xFF303D35),
    error = Color(0xFFD9A28F), onError = Color(0xFF361C14),
    errorContainer = Color(0xFF432C24), onErrorContainer = Color(0xFFF4DFD4),
    surfaceDim = Color(0xFF101513), surfaceBright = Color(0xFF29372F),
    surfaceContainerLowest = Color(0xFF0C110E), surfaceContainerLow = Color(0xFF161F19),
    surfaceContainer = Color(0xFF1C2820), surfaceContainerHigh = Color(0xFF233128),
    surfaceContainerHighest = Color(0xFF2A3A30),
    inverseSurface = Color(0xFFDCE5D8), inverseOnSurface = Color(0xFF273F35), inversePrimary = Color(0xFF273F35),
    scrim = Color.Black,
)

val AccountExpenseColor: Color @Composable get() = MaterialTheme.colorScheme.error
val AccountIncomeColor: Color @Composable get() = MaterialTheme.colorScheme.tertiary
val AccountMutedColor: Color @Composable get() = MaterialTheme.colorScheme.onSurfaceVariant
val AccountFieldShape = RoundedCornerShape(8.dp)

val AccountSerif = FontFamily(Font(R.font.orchid_serif))

private val AppTypography = Typography(
    displaySmall = TextStyle(fontFamily = AccountSerif, fontWeight = FontWeight.Normal, fontSize = 40.sp, lineHeight = 50.sp),
    headlineLarge = TextStyle(fontFamily = AccountSerif, fontWeight = FontWeight.Normal, fontSize = 32.sp, lineHeight = 40.sp, letterSpacing = 1.sp),
    headlineMedium = TextStyle(fontFamily = AccountSerif, fontWeight = FontWeight.Normal, fontSize = 28.sp, lineHeight = 36.sp, letterSpacing = 1.sp),
    titleLarge = TextStyle(fontFamily = AccountSerif, fontWeight = FontWeight.Normal, fontSize = 24.sp, lineHeight = 32.sp, letterSpacing = 1.sp),
    titleMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 17.sp, lineHeight = 24.sp),
    titleSmall = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 15.sp, lineHeight = 22.sp),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 25.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 22.sp),
    bodySmall = TextStyle(fontSize = 13.sp, lineHeight = 20.sp),
    labelLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp),
    labelMedium = TextStyle(fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 18.sp),
)

val AccountMotionShort = spring<Float>(dampingRatio = 1f, stiffness = 700f)
val AccountMotionMedium = spring<Float>(dampingRatio = 1f, stiffness = 450f)

@Composable
fun AccountTheme(content: @Composable () -> Unit) {
    val preference by rememberAccountAppearance()
    val dark = preference == "dark" || (preference == "system" && isSystemInDarkTheme())
    val view = LocalView.current
    val context = LocalContext.current
    DisposableEffect(dark, view) {
        (context as? Activity)?.window?.let { window ->
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }
        }
        onDispose { }
    }
    CompositionLocalProvider(LocalAccountDarkTheme provides dark) {
    MaterialTheme(
        colorScheme = if (dark) DarkColors else LightColors,
        typography = AppTypography,
        shapes = Shapes(
            extraSmall = RoundedCornerShape(4.dp), small = RoundedCornerShape(8.dp),
            medium = AccountFieldShape, large = RoundedCornerShape(12.dp), extraLarge = RoundedCornerShape(24.dp),
        ),
        content = content,
    )
    }
}

val LocalAccountDarkTheme = staticCompositionLocalOf { false }

private fun appearancePreferences(context: Context) =
    context.applicationContext.getSharedPreferences("account_appearance", Context.MODE_PRIVATE)

@Composable
fun rememberAccountAppearance(): State<String> {
    val context = LocalContext.current
    val preferences = remember(context) { appearancePreferences(context) }
    val state = remember(preferences) { mutableStateOf(preferences.getString("mode", "system") ?: "system") }
    DisposableEffect(preferences) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { prefs, key ->
            if (key == "mode") state.value = prefs.getString("mode", "system") ?: "system"
        }
        preferences.registerOnSharedPreferenceChangeListener(listener)
        onDispose { preferences.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    return state
}

fun setAccountAppearance(context: Context, mode: String) {
    require(mode in listOf("system", "light", "dark"))
    appearancePreferences(context).edit().putString("mode", mode).apply()
}
