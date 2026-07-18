package com.alhosan.checker.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
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
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Language
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.alhosan.checker.data.model.AppLang
import com.alhosan.checker.data.model.CheckerState
import com.alhosan.checker.ui.i18n.*
import com.alhosan.checker.ui.screens.HistoryScreen
import com.alhosan.checker.ui.screens.LoginScreen
import com.alhosan.checker.ui.screens.ResultScreen
import com.alhosan.checker.ui.screens.SplashScreen
import com.alhosan.checker.ui.components.alHosanStaggeredEnter
import com.alhosan.checker.ui.components.alHosanStaggeredExit
import com.alhosan.checker.ui.theme.Black
import com.alhosan.checker.ui.theme.BorderGold
import com.alhosan.checker.ui.theme.Gold
import com.alhosan.checker.ui.theme.SurfaceBlack
import com.alhosan.checker.ui.theme.AlHosanTheme
import com.alhosan.checker.viewmodel.CheckerViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

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
 * Shared staggered fade/slide transitions for open/close/back navigation.
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
    val appDirection = if (lang == AppLang.AR) LayoutDirection.Rtl else LayoutDirection.Ltr
    val transitionScope = rememberCoroutineScope()
    val checkerState by viewModel.state.collectAsState()
    val isChecking = checkerState is CheckerState.Loading
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    fun popResultAfterTransition() {
        // Snapshot the state BEFORE popping so we only reset if nothing new
        // has been loaded while the transition was running (e.g. user restored
        // a history item during the 330 ms exit animation).
        val stateAtExit = viewModel.state.value
        navController.popBackStack()
        transitionScope.launch {
            delay(330)
            if (viewModel.state.value === stateAtExit) {
                viewModel.resetState()
            }
        }
    }

    // Language toggle now uses the SAME slide+fade motion language as the
    // screen-to-screen transitions (alHosanStaggeredEnter/Exit) instead of a
    // generic alpha-only fade — applied ONLY to the content Box below, never
    // to the header, so the header's own button animations (lang/back/
    // history alpha+scale) are never doubled up with this effect. Pure
    // graphicsLayer transform (alpha + translationX) — does not recompose
    // or recreate any screen, so it can never re-trigger a LaunchedEffect(Unit)
    // block (that was the bug with the old Crossfade-based approach).
    val langAlpha = remember { Animatable(1f) }
    val langOffsetXDp = remember { Animatable(0f) }

    // Proactive wrapper: fades/slides OUT first, swaps the ACTUAL language
    // (viewModel.toggleLang()) only once fully invisible (alpha=0), then
    // fades/slides back IN with the new text already in place. The old
    // reactive LaunchedEffect(lang) approach animated AFTER Compose had
    // already recomposed every screen with the new language — so the fade
    // was just fading in the new text, not hiding the actual swap. Calling
    // toggleLang() ourselves, at the exact moment we choose mid-animation,
    // fixes that.
    fun animatedToggleLang() {
        transitionScope.launch {
            launch { langAlpha.animateTo(0f, tween(180)) }
            langOffsetXDp.animateTo(40f, tween(260))
            viewModel.toggleLang()
            langOffsetXDp.snapTo(-40f)
            launch { langAlpha.animateTo(1f, tween(280)) }
            langOffsetXDp.animateTo(0f, tween(360))
        }
    }

    CompositionLocalProvider(LocalLayoutDirection provides appDirection) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Black)
        ) {
            // Header is outside NavHost so the back button stays fixed and does not
        // participate in the screen open/close staggered transition.
        if (currentRoute != null && currentRoute != "splash") {
            ScreenHeader(
                showBack = currentRoute != "login",
                showLang = currentRoute == "login",
                showHistory = currentRoute == "login",
                langEnabled = !isChecking,
                blurred = isChecking,
                title = when (currentRoute) {
                    "history" -> lang.hTitle
                    "result" -> lang.resTitle
                    else -> null
                },
                onBack = {
                    when (currentRoute) {
                        "result" -> popResultAfterTransition()
                        "history" -> navController.popBackStack()
                    }
                },
                onLangToggle = ::animatedToggleLang,
                onHistoryClick = { navController.navigate("history") }
            )
            HeaderSpacer()
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .graphicsLayer {
                    alpha = langAlpha.value
                    translationX = langOffsetXDp.value.dp.toPx()
                }
        ) {
            NavHost(
                navController = navController,
                startDestination = "splash",
                enterTransition = { alHosanStaggeredEnter(durationMs = 420) },
                exitTransition = { alHosanStaggeredExit(durationMs = 300) },
                popEnterTransition = { alHosanStaggeredEnter(durationMs = 420) },
                popExitTransition = { alHosanStaggeredExit(durationMs = 300) }
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
                        viewModel = viewModel
                    )
                }

                composable("result") {
                    ResultScreen(
                        onBack = { popResultAfterTransition() },
                        viewModel = viewModel
                    )
                }

                composable("history") {
                    HistoryScreen(
                        onBack = { navController.popBackStack() },
                        onRestore = {
                            navController.navigate("result")
                        },
                        viewModel = viewModel
                    )
                }
                } // end NavHost
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
    showLang: Boolean = true,
    showHistory: Boolean = false,
    langEnabled: Boolean = true,
    title: String? = null,
    blurred: Boolean = false,
    onBack: () -> Unit,
    onLangToggle: () -> Unit,
    onHistoryClick: () -> Unit = {}
) {
    // Language globe is always on the PHYSICAL RIGHT regardless of language.
    // Back arrow follows platform convention: left in English, right in Arabic.
    // History button (login screen only) mirrors the globe on the PHYSICAL
    // LEFT — same circular style, opposite corner.
    // Forcing LTR on the Row lets us place children by absolute physical
    // positions using isRtl.
    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .then(if (blurred) Modifier.blur(12.dp) else Modifier)
        ) {
            // Centered page title — a separate layer so it's positioned
            // relative to the FULL header width, independent of whichever
            // combination of back/lang buttons happens to be visible.
            AnimatedContent(
                targetState = title,
                transitionSpec = {
                    fadeIn(tween(280, delayMillis = 80)) togetherWith fadeOut(tween(160))
                },
                modifier = Modifier.align(Alignment.Center),
                label = "header-title"
            ) { t ->
                if (t != null) {
                    Text(
                        text = t,
                        color = Gold,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        modifier = Modifier.padding(horizontal = 64.dp)
                    )
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // ── Left corner (physical), fixed 40dp — ALWAYS composed. ──
                // EN: back button. AR: history button (login only). Neither
                // combination overlaps (history only shows on login, where
                // showBack is always false), so a single fixed slot is safe.
                // Visibility is alpha/scale ONLY via graphicsLayer, which is a
                // render-phase transform — it can NEVER trigger a relayout,
                // so this button's on-screen position is physically
                // impossible to shift. This is what finally kills the
                // back-button "jump" bug for good.
                Box(
                    modifier = Modifier.width(40.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    // Only ONE of these is ever composed at a time — overlaying
                    // both (even with the invisible one "disabled") silently
                    // blocked clicks meant for the one underneath, because
                    // Compose's clickable(enabled=false) still consumes touch
                    // input instead of passing it through. A single conditional
                    // child fixes that, and stays 100% jump-free since the box
                    // itself is always a fixed 40dp regardless of which button
                    // (or neither) is inside it.
                    val enBackShown = !isRtl && showBack
                    if (enBackShown) {
                        CircleHeaderButton(onClick = onBack, enabled = true) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = Gold,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    } else if (showHistory) {
                        CircleHeaderButton(onClick = onHistoryClick, enabled = true) {
                            Icon(
                                imageVector = Icons.Default.History,
                                contentDescription = "History",
                                tint = Gold,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }

                Spacer(Modifier.weight(1f))

                // ── Right corner: back button (AR) OR globe — never both at
                // once (showLang is only ever true on login, showBack only
                // ever true elsewhere). Single conditional child, same
                // click-blocking fix as the left slot above — pins whichever
                // one is active exactly to the physical right edge.
                Box(
                    modifier = Modifier.width(40.dp),
                    contentAlignment = Alignment.CenterEnd
                ) {
                    val arBackShown = isRtl && showBack
                    if (arBackShown) {
                        CircleHeaderButton(onClick = onBack, enabled = true) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = Gold,
                                modifier = Modifier
                                    .size(18.dp)
                                    .graphicsLayer { scaleX = -1f }
                            )
                        }
                    } else if (showLang) {
                        CircleHeaderButton(onClick = onLangToggle, enabled = langEnabled) {
                            Icon(
                                imageVector = Icons.Default.Language,
                                contentDescription = "Language",
                                tint = if (langEnabled) Gold else Gold.copy(alpha = 0.35f),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
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
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(SurfaceBlack, CircleShape)
            .border(1.dp, BorderGold, CircleShape)
            .clickable(enabled = enabled, onClick = onClick),
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
    Spacer(modifier = Modifier.height(4.dp))
}

