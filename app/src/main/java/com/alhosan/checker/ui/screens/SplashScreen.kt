package com.alhosan.checker.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import com.alhosan.checker.ui.theme.Black

/**
 * In-app splash screen.
 *
 * No logo, no text — just a black screen that navigates to login immediately.
 * The system splash (core-splashscreen) already shows the launcher icon
 * instantly on tap, so this composable doesn't need to display anything.
 *
 * Previous version showed a logo image for 1 second — user requested to
 * remove it entirely.
 */
@Composable
fun SplashScreen(
    onSplashComplete: () -> Unit
) {
    // Navigate to login immediately — no image, no delay.
    LaunchedEffect(Unit) {
        onSplashComplete()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Black)
    )
}
