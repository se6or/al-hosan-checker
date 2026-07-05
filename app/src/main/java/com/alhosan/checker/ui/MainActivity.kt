package com.alhosan.checker.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Language
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
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

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
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
 * Main app composable. Splash → Login → Result / History.
 * Fade-only transitions to avoid overlap glitches during system back gesture.
 *
 * LAYOUT FIX (this iteration):
 *   The previous "floating overlay" buttons kept ending up *behind* the Card
 *   because Compose Card has an elevation shadow that paints above siblings.
 *   Instead of overlay hacks, each screen now lays out a simple header Row
 *   ABOVE the card in a normal Column flow:
 *
 *       [statusBar padding]
 *       [header Row: back btn (left) ... lang btn (right)]   ← real layout slot
 *       [12dp breathing space]
 *       [Card with content]
 *
 *   This guarantees the back button is always tappable (never behind the card)
 *   and gives the requested breathing space between header and card.
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
            // Staggered-style screen transitions (inspired by reactbits.dev
            // staggered-menu). Forward navigation: new screen slides in from
            // top + slight horizontal offset, with a quick fade.
            // Backward navigation: current screen slides out to top + fade.
            // Both use the same direction so the effect is consistent.
            enterTransition = {
                slideInVertically(
                    initialOffsetY = { -it / 6 },
                    animationSpec = tween(380)
                ) + fadeIn(tween(280))
            },
            exitTransition = {
                slideOutVertically(
                    targetOffsetY = { it / 12 },
                    animationSpec = tween(280)
                ) + fadeOut(tween(220))
            },
            popEnterTransition = {
                slideInVertically(
                    initialOffsetY = { it / 12 },
                    animationSpec = tween(380)
                ) + fadeIn(tween(280))
            },
            popExitTransition = {
                slideOutVertically(
                    targetOffsetY = { -it / 6 },
                    animationSpec = tween(280)
                ) + fadeOut(tween(220))
            }
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
                    viewModel = viewModel
                )
            }

            composable("result") {
                ResultScreen(
                    onBack = {
                        // Pop back to the previous screen (history or login),
                        // THEN reset the state. Order matters: pop first so the
                        // back stack is correct, then clear the subscription so
                        // the next visit to result shows a fresh state.
                        navController.popBackStack()
                        viewModel.resetState()
                    },
                    viewModel = viewModel
                )
            }

            composable("history") {
                HistoryScreen(
                    onBack = { navController.popBackStack() },
                    onRestore = {
                        // Sequential navigation: history -> result.
                        // Don't popUpTo login — that would skip history on back.
                        // Just navigate to result, so back from result returns to history,
                        // then back from history returns to login.
                        navController.navigate("result")
                    },
                    viewModel = viewModel
                )
            }
        }
    }
}

/* ────────────────────────────────────────────────────────────────────────────
 * Header components — used inline by each screen, NOT as overlays.
 * They live in a normal Column/Row flow above the card, so they're always
 * tappable and never hidden behind the Card's elevation shadow.
 * ────────────────────────────────────────────────────────────────────────── */

/**
 * The single header Row shown above the card on every screen.
 *
 * Layout (RTL aware):
 *   - Start edge: back button (circular, 40dp). On login screen this slot is empty
 *     and a leading spacer keeps things balanced.
 *   - End edge: language toggle button (circular, 40dp).
 *
 * The whole Row sits inside `statusBarsPadding()` so it's below the system
 * status bar, and the card follows after a 12dp breathing space.
 */
@Composable
fun ScreenHeader(
    showBack: Boolean,
    onBack: () -> Unit,
    onLangToggle: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 14.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Leading slot: back button or spacer (to keep lang button on the end edge)
        if (showBack) {
            CircleHeaderButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = Gold,
                    modifier = Modifier.size(18.dp)
                )
            }
        } else {
            Spacer(modifier = Modifier.width(40.dp))
        }

        // Trailing slot: language toggle (always present)
        CircleHeaderButton(onClick = onLangToggle) {
            Icon(
                imageVector = Icons.Default.Language,
                contentDescription = "Language",
                tint = Gold,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

/**
 * One circular header button (40dp) — dark surface + thin gold border + gold icon.
 * Same visual style on every screen.
 */
@Composable
private fun CircleHeaderButton(
    onClick: () -> Unit,
    content: @Composable () -> Unit
) {
    Box(
        modifier = Modifier
            .size(40.dp)
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
 * 12dp breathing space — placed between the header Row and the content card
 * on every screen so the card isn't glued to the top.
 */
@Composable
fun HeaderSpacer() {
    Spacer(modifier = Modifier.height(12.dp))
}
