package com.alhosan.checker.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Language
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.alhosan.checker.R
import com.alhosan.checker.data.model.AppLang
import com.alhosan.checker.ui.screens.HistoryScreen
import com.alhosan.checker.ui.screens.LoginScreen
import com.alhosan.checker.ui.screens.ResultScreen
import com.alhosan.checker.ui.screens.SplashScreen
import com.alhosan.checker.ui.theme.Black
import com.alhosan.checker.ui.theme.BorderGold
import com.alhosan.checker.ui.theme.Gold
import com.alhosan.checker.ui.theme.SurfaceBlack
import com.alhosan.checker.ui.theme.AlHosanTheme
import com.alhosan.checker.viewmodel.CheckerViewModel
import com.alhosan.checker.ui.i18n.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AlHosanTheme {
                AlHosanApp()
            }
        }
    }
}

/**
 * Main app composable — NO HEADER BAR.
 *
 * Matches the user's reference design (last 3 screenshots):
 *  - Content card starts directly at the top of the screen (below the status bar).
 *  - No title bar, no horse logo, no language button taking up a header row.
 *  - Back / language actions are small FLOATING circular buttons overlaid on top
 *    of the content, positioned at the top corners so they don't waste vertical space.
 *
 * SWIPE-BACK FIX:
 *  - Fade-only transitions (no slide) to avoid overlap glitches during the
 *    system back gesture.
 */
@Composable
fun AlHosanApp() {
    val navController = rememberNavController()
    val viewModel: CheckerViewModel = viewModel()
    val lang by viewModel.lang.collectAsState()
    val isChecking by viewModel.isChecking.collectAsState()

    Box(modifier = Modifier.fillMaxSize().background(Black)) {
        NavHost(
            navController = navController,
            startDestination = "splash",
            enterTransition = { fadeIn(tween(220)) },
            exitTransition = { fadeOut(tween(180)) },
            popEnterTransition = { fadeIn(tween(220)) },
            popExitTransition = { fadeOut(tween(180)) }
        ) {
            composable("splash") {
                SplashScreen(
                    onSplashComplete = {
                        navController.navigate("login") {
                            popUpTo("splash") { inclusive = true }
                        }
                    }
                )
            }

            composable("login") {
                LoginScreen(
                    onResultReady = { navController.navigate("result") },
                    onHistoryClick = { navController.navigate("history") },
                    viewModel = viewModel,
                    floatingHeader = {
                        FloatingLanguageButton(
                            onClick = viewModel::toggleLang,
                            modifier = Modifier.statusBarsPadding()
                        )
                    }
                )
            }

            composable("result") {
                ResultScreen(
                    onBack = {
                        viewModel.resetState()
                        navController.popBackStack()
                    },
                    viewModel = viewModel,
                    floatingHeader = {
                        FloatingBackButton(
                            onClick = {
                                viewModel.resetState()
                                navController.popBackStack()
                            },
                            modifier = Modifier.statusBarsPadding()
                        )
                    }
                )
            }

            composable("history") {
                HistoryScreen(
                    onBack = { navController.popBackStack() },
                    onRestore = { navController.navigate("result") },
                    viewModel = viewModel,
                    floatingHeader = {
                        FloatingBackButton(
                            onClick = { navController.popBackStack() },
                            modifier = Modifier.statusBarsPadding()
                        )
                    }
                )
            }
        }

        // Global running-horse indicator shown on top of everything while checking
        if (isChecking) {
            RunningHorseFloating(lang = lang, modifier = Modifier.statusBarsPadding())
        }
    }
}

/**
 * Small floating circular button overlaid at the top-start corner of the screen.
 * Uses statusBarsPadding so it sits below the system status bar (not under it).
 *
 * This replaces the old header bar entirely — no vertical space is wasted,
 * the content card starts from the very top just like the reference design.
 */
@Composable
fun FloatingBackButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .padding(start = 14.dp, top = 8.dp)
            .size(40.dp)
            .clip(CircleShape)
            .background(SurfaceBlack, CircleShape)
            .border(1.dp, BorderGold, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = "Back",
            tint = Gold,
            modifier = Modifier.size(18.dp)
        )
    }
}

/**
 * Small floating circular language-toggle button at the top-end corner.
 * Only shown on the login screen (where there's no back button).
 */
@Composable
fun FloatingLanguageButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .padding(end = 14.dp, top = 8.dp)
            .size(40.dp)
            .clip(CircleShape)
            .background(SurfaceBlack, CircleShape)
            .border(1.dp, BorderGold, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Default.Language,
            contentDescription = "Language",
            tint = Gold,
            modifier = Modifier.size(18.dp)
        )
    }
}

/**
 * Tiny floating "checking..." indicator (horse + text) shown at the top-center
 * while a subscription check is in progress. Overlaid on top of content so it
 * doesn't push the layout.
 */
@Composable
private fun RunningHorseFloating(lang: AppLang, modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "floatingHorse")
    val offsetY by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = -4f,
        animationSpec = infiniteRepeatable(
            animation = tween(400),
            repeatMode = RepeatMode.Reverse
        ),
        label = "floatingHorseY"
    )

    Row(
        modifier = modifier
            .padding(top = 8.dp)
            .clip(CircleShape)
            .background(SurfaceBlack, CircleShape)
            .border(1.dp, BorderGold, CircleShape)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Image(
            painter = painterResource(id = R.drawable.ic_alhosan_logo),
            contentDescription = null,
            modifier = Modifier
                .size(18.dp)
                .offset(y = offsetY.dp),
            contentScale = ContentScale.Fit
        )
        Text(
            text = if (lang == AppLang.AR) "الفحص جارٍ..." else "Checking...",
            color = Gold,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 12.sp
        )
    }
}
