package app.nebulabox.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

val JavidPrimaryLight = Color(0xFF0F172A)
val JavidAccentLight = Color(0xFF2563EB)
val JavidAccentContainerLight = Color(0xFFDBEAFE)
val JavidOnAccentContainerLight = Color(0xFF1E3A8A)
val JavidBackgroundLight = Color(0xFFF8FAFC)
val JavidSurfaceLight = Color(0xFFFFFFFF)
val JavidSurfaceVariantLight = Color(0xFFF1F5F9)
val JavidOnSurfaceLight = Color(0xFF0F172A)
val JavidOnSurfaceVariantLight = Color(0xFF64748B)
val JavidOutlineLight = Color(0xFFE2E8F0)

val JavidPrimaryDark = Color(0xFFF8FAFC)
val JavidAccentDark = Color(0xFF38BDF8)
val JavidAccentContainerDark = Color(0xFF0C4A6E)
val JavidOnAccentContainerDark = Color(0xFFE0F2FE)
val JavidBackgroundDark = Color(0xFF0B0F17)
val JavidSurfaceDark = Color(0xFF111827)
val JavidSurfaceVariantDark = Color(0xFF1E293B)
val JavidOnSurfaceDark = Color(0xFFF8FAFC)
val JavidOnSurfaceVariantDark = Color(0xFF94A3B8)
val JavidOutlineDark = Color(0xFF1E293B)

val JavidSuccess = Color(0xFF10B981)
val JavidDanger = Color(0xFFF43F5E)
val JavidProtocolTag = Color(0xFF0EA5E9)

val JavidLightColors = lightColorScheme(
    primary = JavidAccentLight,
    onPrimary = Color.White,
    primaryContainer = JavidAccentContainerLight,
    onPrimaryContainer = JavidOnAccentContainerLight,
    secondary = JavidPrimaryLight,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE2E8F0),
    onSecondaryContainer = JavidPrimaryLight,
    tertiary = JavidSuccess,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFD1FAE5),
    onTertiaryContainer = Color(0xFF065F46),
    error = Color(0xFFE11D48),
    errorContainer = Color(0xFFFFE4E6),
    onError = Color.White,
    onErrorContainer = Color(0xFF881337),
    background = JavidBackgroundLight,
    onBackground = JavidOnSurfaceLight,
    surface = JavidSurfaceLight,
    onSurface = JavidOnSurfaceLight,
    surfaceVariant = JavidSurfaceVariantLight,
    onSurfaceVariant = JavidOnSurfaceVariantLight,
    outline = JavidOutlineLight,
    outlineVariant = Color(0xFFCBD5E1),
)

val JavidDarkColors = darkColorScheme(
    primary = JavidAccentDark,
    onPrimary = Color(0xFF082F49),
    primaryContainer = JavidAccentContainerDark,
    onPrimaryContainer = JavidOnAccentContainerDark,
    secondary = Color(0xFF818CF8),
    onSecondary = Color(0xFF1E1B4B),
    secondaryContainer = Color(0xFF1E293B),
    onSecondaryContainer = Color(0xFFE2E8F0),
    tertiary = Color(0xFF34D399),
    onTertiary = Color(0xFF064E3B),
    tertiaryContainer = Color(0xFF065F46),
    onTertiaryContainer = Color(0xFFD1FAE5),
    error = Color(0xFFFB7185),
    errorContainer = Color(0xFF881337),
    onError = Color(0xFF4C0519),
    onErrorContainer = Color(0xFFFFE4E6),
    background = JavidBackgroundDark,
    onBackground = JavidOnSurfaceDark,
    surface = JavidSurfaceDark,
    onSurface = JavidOnSurfaceDark,
    surfaceVariant = JavidSurfaceVariantDark,
    onSurfaceVariant = JavidOnSurfaceVariantDark,
    outline = JavidOutlineDark,
    outlineVariant = Color(0xFF334155),
)
