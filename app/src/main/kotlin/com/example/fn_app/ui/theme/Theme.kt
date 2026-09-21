package com.example.fn_app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColors = darkColorScheme(
    primary = FnPrimary,
    onPrimary = FnOnPrimary,
    secondary = FnSecondary,
    background = FnBackground,
    onBackground = FnTextHigh,
    surface = FnSurface,
    onSurface = FnTextHigh,
    surfaceVariant = FnSurfaceVariant,
    onSurfaceVariant = FnTextMuted,
    error = FnError,
    onError = FnOnPrimary,
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF00838F),
    onPrimary = Color(0xFFFFFFFF),
    secondary = Color(0xFFB26A00),
    background = Color(0xFFF6F8FA),
    onBackground = Color(0xFF1A2027),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1A2027),
    surfaceVariant = Color(0xFFE7ECF1),
    onSurfaceVariant = Color(0xFF5A6772),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
)

@Composable
fun FnDogTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content,
    )
}
