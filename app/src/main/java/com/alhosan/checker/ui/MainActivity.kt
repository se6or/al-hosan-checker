package com.alhosan.checker.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
import com.alhosan.checker.ui.theme.GoldGradientBrush
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
 * Main app composable with full 4-screen navigation matching HTML reference.
 * Routes: splash → login → result, login → history → result
 *
 * HEADER DESIGN (matches the user's reference app — last 3 screenshots):
 *  - Minimal header: centered title + ONE circular back-arrow button on the trailing edge.
 *  - On the login screen the trailing button is the language toggle (circular globe icon).
 *  - On splash there is no header at all (just the animated logo).
 *  - No more triple-element header (lang + logo + back) — that caused visual clutter.
 *
 * SWIPE-BACK FIX:
 *  - The default `slideIntoContainer`/`slideOutOfContainer` transitions caused overlap
 *    glitches when the system back gesture was used (one screen sliding over another).
 *  - Replaced with `fadeIn`/`fadeOut` (no horizontal movement) so there's no overlap
 *    at all — the new screen just fades in over the old one.
 */
@Composable
fun AlHosanApp() {
    val navController = rememberNavController()
    val viewModel: CheckerViewModel = viewModel()
    val lang by viewModel.lang.collectAsState()
    val isChecking by viewModel.isChecking.collectAsState()
    val state by viewModel.state.collectAsState()

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            NavHost(
                navController = navController,
                startDestination = "splash",
                // Use fade-only transitions to avoid swipe-back overlap glitches.
                // (Slide transitions stack two screens side-by-side during the gesture,
                //  which caused the visible overlap reported by the user.)
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
                    AppHeader(
                        lang = lang,
                        title = lang.splash,
                        trailingAction = HeaderAction.Language,
                        isRunning = isChecking,
                        onLangToggle = viewModel::toggleLang,
                        onBack = {}
                    )
                    LoginScreen(
                        onResultReady = {
                            navController.navigate("result")
                        },
                        onHistoryClick = {
                            navController.navigate("history")
                        },
                        viewModel = viewModel
                    )
                }

                composable("result") {
                    AppHeader(
                        lang = lang,
                        title = lang.resTitle,
                        trailingAction = HeaderAction.Back,
                        isRunning = isChecking,
                        onLangToggle = viewModel::toggleLang,
                        onBack = {
                            viewModel.resetState()
                            navController.popBackStack()
                        }
                    )
                    ResultScreen(
                        onBack = {
                            viewModel.resetState()
                            navController.popBackStack()
                        },
                        viewModel = viewModel
                    )
                }

                composable("history") {
                    AppHeader(
                        lang = lang,
                        title = lang.hTitle,
                        trailingAction = HeaderAction.Back,
                        isRunning = false,
                        onLangToggle = viewModel::toggleLang,
                        onBack = { navController.popBackStack() }
                    )
                    HistoryScreen(
                        onBack = { navController.popBackStack() },
                        onRestore = {
                            navController.navigate("result")
                        },
                        viewModel = viewModel
                    )
                }
            }
        }
    }
}

/** What the trailing circular button in the header should do. */
enum class HeaderAction { None, Back, Language }

/**
 * Minimal app header — matches the user's reference design.
 *
 * Layout:
 *   - Centered gold title (the screen name)
 *   - ONE circular button on the trailing edge:
 *       * Language toggle (globe icon) on the login screen
 *       * Back arrow on result / history screens
 *   - No leading element, no logo, no double-button clutter.
 *
 * This is the same minimal pattern used in the reference app screenshots:
 * centered title + single circular action button on the right.
 */
@Composable
fun AppHeader(
    lang: AppLang,
    title: String,
    trailingAction: HeaderAction,
    isRunning: Boolean,
    onLangToggle: () -> Unit,
    onBack: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Black)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Leading spacer — keeps the title visually centered.
        // Same width as the trailing circular button (44dp) for symmetry.
        Spacer(modifier = Modifier.width(44.dp))

        // Center: title (or running horse animation while checking)
        if (isRunning) {
            RunningHorseHeader(lang)
        } else {
            Text(
                text = title,
                color = Gold,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 16.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f)
            )
        }

        // Trailing: single circular action button
        when (trailingAction) {
            HeaderAction.None -> Spacer(modifier = Modifier.width(44.dp))
            HeaderAction.Language -> CircleHeaderButton(
                onClick = onLangToggle,
                content = {
                    Icon(
                        imageVector = Icons.Default.Language,
                        contentDescription = null,
                        tint = Gold,
                        modifier = Modifier.size(20.dp)
                    )
                }
            )
            HeaderAction.Back -> CircleHeaderButton(
                onClick = onBack,
                content = {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = null,
                        tint = Gold,
                        modifier = Modifier.size(20.dp)
                    )
                }
            )
        }
    }
}

/**
 * Single circular header button — same shape as the reference app's
 * circular action button (yellow icon on dark circular background with
 * a thin gold border).
 */
@Composable
private fun CircleHeaderButton(
    onClick: () -> Unit,
    content: @Composable () -> Unit
) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(SurfaceBlack, CircleShape)
            .border(1.dp, BorderGold, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}

/**
 * Running horse animation for header — bouncing horse logo during check.
 * Shown in place of the title while a subscription check is in progress.
 */
@Composable
private fun RunningHorseHeader(lang: AppLang) {
    val infiniteTransition = rememberInfiniteTransition(label = "headerHorse")
    val offsetY by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = -6f,
        animationSpec = infiniteRepeatable(
            animation = tween(400),
            repeatMode = RepeatMode.Reverse
        ),
        label = "headerHorseY"
    )

    Row(
        modifier = Modifier.weight(1f),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Image(
            painter = painterResource(id = R.drawable.ic_alhosan_logo),
            contentDescription = null,
            modifier = Modifier
                .size(24.dp)
                .offset(y = offsetY.dp),
            contentScale = ContentScale.Fit
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = if (lang == AppLang.AR) "الفحص جارٍ..." else "Checking...",
            color = Gold,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 14.sp
        )
    }
}
