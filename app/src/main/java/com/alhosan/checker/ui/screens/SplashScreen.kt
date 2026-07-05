package com.alhosan.checker.ui.screens

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text
import com.alhosan.checker.R
import com.alhosan.checker.ui.theme.Gold

/**
 * In-app splash screen.
 * The system splash (core-splashscreen) already shows the logo instantly on tap.
 * This composable provides a brief branded pause before the login screen.
 */
@Composable
fun SplashScreen(
    onSplashComplete: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "splash")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(2500),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseLogo"
    )

    // Navigate immediately — system splash already showed the logo.
    // Keep a minimal delay so the fade-in animation is visible.
    LaunchedEffect(Unit) {
        onSplashComplete()
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Image(
                painter = painterResource(id = R.drawable.ic_alhosan_logo),
                contentDescription = "الحصان",
                modifier = Modifier
                    .size(200.dp)
                    .scale(scale),
                contentScale = ContentScale.Fit
            )

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "محرك الحصان الفاحص",
                color = Gold,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 18.sp,
                letterSpacing = 2.sp
            )
        }
    }
}
