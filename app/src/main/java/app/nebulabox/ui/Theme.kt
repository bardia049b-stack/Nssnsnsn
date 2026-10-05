package app.nebulabox.ui

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import app.nebulabox.ui.theme.JavidDarkColors
import app.nebulabox.ui.theme.JavidDanger
import app.nebulabox.ui.theme.JavidDangerDark
import app.nebulabox.ui.theme.JavidLightColors
import app.nebulabox.ui.theme.JavidOutlineDark
import app.nebulabox.ui.theme.JavidOutlineLight
import app.nebulabox.ui.theme.JavidProtocolTag
import app.nebulabox.ui.theme.JavidSuccess
import app.nebulabox.ui.theme.JavidSuccessDark
import app.nebulabox.ui.theme.JavidWarning

val colorPing: Color
    @Composable get() = if (LocalDarkTheme.current) JavidSuccessDark else JavidSuccess
val colorPingRed: Color
    @Composable get() = if (LocalDarkTheme.current) JavidDangerDark else JavidDanger
val colorWarning: Color
    @Composable get() = JavidWarning
val colorConfigType = JavidProtocolTag
val colorFabActive = JavidSuccess
val colorFabInactiveLight = Color(0xFF0F172A)
val colorFabInactiveDark = Color(0xFF1E293B)
val dividerColorLight = JavidOutlineLight
val dividerColorDark = JavidOutlineDark

val LocalDarkTheme = compositionLocalOf { false }

@Composable
fun NebulaTheme(
    theme: String = "system",
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val dark = when (theme) {
        "light" -> false
        "dark" -> true
        else -> isSystemInDarkTheme()
    }

    val context = LocalContext.current
    val colors = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> JavidDarkColors
        else -> JavidLightColors
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val activity = view.context as? Activity ?: return@SideEffect
            val window = activity.window
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }
        }
    }

    CompositionLocalProvider(LocalDarkTheme provides dark) {
        MaterialTheme(
            colorScheme = colors,
            typography = NebulaTypography,
            content = content,
        )
    }
}

@Composable
fun AppDivider(modifier: Modifier = Modifier) {
    val color = if (LocalDarkTheme.current) dividerColorDark else dividerColorLight
    HorizontalDivider(modifier = modifier, thickness = 0.7.dp, color = color)
}
