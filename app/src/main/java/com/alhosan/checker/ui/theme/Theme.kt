package com.alhosan.checker.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// ─── Color Palette (Gold on Black - same as original Flutter app) ────────

val Gold = Color(0xFFD4AF37)
val GoldLight = Color(0xFFE8C547)
val GoldDark = Color(0xFFB8960F)
val Black = Color(0xFF000000)
val SurfaceBlack = Color(0xFF0A0A0A)
val BorderGold = Color(0xFF1F1A0F)
val GoldDim = Color(0xFF8B7520)

private val DarkColorScheme = darkColorScheme(
    primary = Gold,
    onPrimary = Black,
    secondary = Gold,
    onSecondary = Black,
    tertiary = GoldLight,
    background = Black,
    onBackground = Color.White,
    surface = SurfaceBlack,
    onSurface = Color.White,
    surfaceVariant = SurfaceBlack,
    onSurfaceVariant = Color.Gray,
    outline = BorderGold,
    outlineVariant = GoldDark,
    error = Color(0xFFFF4444),
    onError = Color.White,
)

@Composable
fun AlHosanTheme(
    content: @Composable () -> Unit
) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = Black.toArgb()
            window.navigationBarColor = Black.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
        }
    }

    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = AlHosanTypography,
        content = content
    )
}
