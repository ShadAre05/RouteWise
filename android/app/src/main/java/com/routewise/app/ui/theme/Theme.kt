package com.routewise.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Color tokens of the UX specification, chapter 6.1. Components use the Material roles below, never raw colors.
val Ink = Color(0xFF0F1E3C)
val Background = Color(0xFFF4F5F1)
val Card = Color(0xFFFFFFFF)
val Surface2 = Color(0xFFE3E7DF)
val Ink2 = Color(0xFF4F5968)
val Primary = Color(0xFF2F4B7C)
val PrimaryContainer = Color(0xFFDCE5F5)
val Accent = Color(0xFFB45F06)
val AccentText = Color(0xFF8F4A00)
val Error = Color(0xFFB3261E)
val ErrorContainer = Color(0xFFFDECEA)
val OnErrorContainer = Color(0xFF7A1812)

private val Scheme = lightColorScheme(
    primary = Primary,
    onPrimary = Color.White,
    primaryContainer = PrimaryContainer,
    onPrimaryContainer = Primary,
    secondary = Accent,
    onSecondary = Color.White,
    tertiary = AccentText,
    background = Background,
    onBackground = Ink,
    surface = Card,
    onSurface = Ink,
    surfaceVariant = Surface2,
    onSurfaceVariant = Ink2,
    outline = Ink2,
    outlineVariant = Surface2,
    error = Error,
    onError = Color.White,
    errorContainer = ErrorContainer,
    onErrorContainer = OnErrorContainer,
)

@Composable
fun RouteWiseTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Scheme, content = content)
}
