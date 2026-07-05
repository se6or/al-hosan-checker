package com.alhosan.checker.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// ─── Color Palette (Gold on Black - matching HTML reference) ───

val Gold = Color(0xFFD4AF37)
val GoldLight = Color(0xFFFFDF00)
val GoldDark = Color(0xFFB8960F)
val Black = Color(0xFF000000)
val SurfaceBlack = Color(0xFF0A0A0A)
val SurfaceGradientStart = Color(0xFF080808)
val SurfaceGradientEnd = Color(0xFF121212)
val BorderGold = Color(0xFF1F1A0F)
val GoldDim = Color(0xFF8B7520)
val TextDim = Color(0xFFA0A0A0)
val GreenActive = Color(0xFF00E676)
val RedInactive = Color(0xFFFF1744)
val YellowUnknown = Color(0xFFFFD600)
val ToastBg = Color(0xCF0F0F0F)
val CardBg = Color(0xFF0A0A0A)
val ModalBg = Color(0xBF000000)

// Gold gradient brush (matches --accent-grad)
val GoldGradientBrush = Brush.linearGradient(
    colors = listOf(GoldLight, Gold)
)

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
    onSurfaceVariant = TextDim,
    outline = BorderGold,
    outlineVariant = GoldDark,
    error = RedInactive,
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
