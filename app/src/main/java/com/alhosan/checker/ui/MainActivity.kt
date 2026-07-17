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
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.runtime.mutableStateOf
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

    // Smooth cross-fade whenever the language toggles: fades the whole app
    // out (~120ms) then back in (~220ms) around the instant the text/direction
    // actually flips, instead of an abrupt snap. This is a pure render-phase
    // alpha transform (graphicsLayer) applied below — it does NOT recompose
    // or recreate any screen, so it can never re-trigger a LaunchedEffect(Unit)
    // block (that was the bug with the old Crossfade-based approach).
    val langFadeAlpha = remember { Animatable(1f) }
    var isFirstLangEmission by remember { mutableStateOf(true) }
    LaunchedEffect(lang) {
        if (isFirstLangEmission) {
            isFirstLangEmission = false
            return@LaunchedEffect
        }
        langFadeAlpha.animateTo(0f, tween(120))
        langFadeAlpha.animateTo(1f, tween(220))
    }

    CompositionLocalProvider(LocalLayoutDirection provides appDirection) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Black)
                .graphicsLayer { alpha = langFadeAlpha.value }
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
                onLangToggle = viewModel::toggleLang,
                onHistoryClick = { navController.navigate("history") }
            )
            HeaderSpacer()
        }

        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
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
                    val enBackShown = !isRtl && showBack
                    val enBackAlpha by animateFloatAsState(
                        targetValue = if (enBackShown) 1f else 0f,
                        animationSpec = tween(160), label = "enBackAlpha"
                    )
                    val enBackScale by animateFloatAsState(
                        targetValue = if (enBackShown) 1f else 0.85f,
                        animationSpec = tween(160), label = "enBackScale"
                    )
                    CircleHeaderButton(
                        onClick = onBack,
                        enabled = enBackShown,
                        modifier = Modifier.graphicsLayer {
                            alpha = enBackAlpha
                            scaleX = enBackScale
                            scaleY = enBackScale
                        }
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Gold,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    val historyAlpha by animateFloatAsState(
                        targetValue = if (showHistory) 1f else 0f,
                        animationSpec = tween(160), label = "historyAlpha"
                    )
                    val historyScale by animateFloatAsState(
                        targetValue = if (showHistory) 1f else 0.85f,
                        animationSpec = tween(160), label = "historyScale"
                    )
                    CircleHeaderButton(
                        onClick = onHistoryClick,
                        enabled = showHistory,
                        modifier = Modifier.graphicsLayer {
                            alpha = historyAlpha
                            scaleX = historyScale
                            scaleY = historyScale
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.History,
                            contentDescription = "History",
                            tint = Gold,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Spacer(Modifier.weight(1f))

                // ── Right corner: AR back (flipped) + gap + Globe. ──
                // Same always-composed / alpha-only pattern — this fixed
                // 86dp box was already jump-free positionally, but now the
                // buttons inside it are ALSO alpha-only for full consistency
                // and to remove the very last possibility of a settle-time
                // mismatch between the fade and any layout pass.
                Box(
                    modifier = Modifier.width(40.dp + 6.dp + 40.dp),
                    contentAlignment = Alignment.CenterEnd
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        val arBackShown = isRtl && showBack
                        val arBackAlpha by animateFloatAsState(
                            targetValue = if (arBackShown) 1f else 0f,
                            animationSpec = tween(160), label = "arBackAlpha"
                        )
                        val arBackScale by animateFloatAsState(
                            targetValue = if (arBackShown) 1f else 0.85f,
                            animationSpec = tween(160), label = "arBackScale"
                        )
                        CircleHeaderButton(
                            onClick = onBack,
                            enabled = arBackShown,
                            modifier = Modifier.graphicsLayer {
                                alpha = arBackAlpha
                                scaleX = arBackScale
                                scaleY = arBackScale
                            }
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = Gold,
                                modifier = Modifier
                                    .size(18.dp)
                                    .graphicsLayer { scaleX = -1f }
                            )
                        }

                        val langAlpha by animateFloatAsState(
                            targetValue = if (showLang) 1f else 0f,
                            animationSpec = tween(160), label = "langAlpha"
                        )
                        val langScale by animateFloatAsState(
                            targetValue = if (showLang) 1f else 0.85f,
                            animationSpec = tween(160), label = "langScale"
                        )
                        CircleHeaderButton(
                            onClick = onLangToggle,
                            enabled = showLang && langEnabled,
                            modifier = Modifier.graphicsLayer {
                                alpha = langAlpha
                                scaleX = langScale
                                scaleY = langScale
                            }
                        ) {
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

