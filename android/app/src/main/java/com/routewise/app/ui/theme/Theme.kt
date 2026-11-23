package com.routewise.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Teal = Color(0xFF0F766E)
val TealDark = Color(0xFF115E59)
val Amber = Color(0xFFB45309)
val Ink = Color(0xFF1F2933)
val Surface = Color(0xFFF7F9F9)
val Danger = Color(0xFFB3261E)

private val Scheme = lightColorScheme(
    primary = Teal,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFCCEDE9),
    onPrimaryContainer = TealDark,
    secondary = Amber,
    background = Surface,
    surface = Color.White,
    onSurface = Ink,
    onBackground = Ink,
    error = Danger,
)

@Composable
fun RouteWiseTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Scheme, content = content)
}
