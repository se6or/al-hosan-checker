package com.alhosan.checker.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Language
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.alhosan.checker.data.model.AppLang
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

    CompositionLocalProvider(LocalLayoutDirection provides appDirection) {
        Column(modifier = Modifier.fillMaxSize().background(Black)) {
            // Header is outside NavHost so the back button stays fixed and does not
        // participate in the screen open/close staggered transition.
        if (currentRoute != null && currentRoute != "splash") {
            ScreenHeader(
                showBack = currentRoute != "login",
                showLang = currentRoute == "login",
                onBack = {
                    when (currentRoute) {
                        "result" -> popResultAfterTransition()
                        "history" -> navController.popBackStack()
                    }
                },
                onLangToggle = viewModel::toggleLang
            )
            HeaderSpacer()
        }

        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            // Crossfade content whenever the language changes so text and icons
            // don't snap — they fade out the old language and fade in the new
            // one smoothly.
            val currentLang by viewModel.lang.collectAsState()
            androidx.compose.animation.Crossfade(
                targetState = currentLang,
                animationSpec = tween(durationMillis = 320),
                label = "lang-crossfade"
            ) {
                NavHost(
                    navController = navController,
                    startDestination = "splash",
                    // Content enters from right to left, exits left to right.
                    // The header/back button above remains fixed.
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
                        onHistoryClick = { navController.navigate("history") },
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
            } // end Crossfade
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
    onBack: () -> Unit,
    onLangToggle: () -> Unit
) {
    // Language globe is always on the PHYSICAL RIGHT (LTR end) regardless of
    // language. Back arrow follows the platform convention for the current
    // layout direction: start edge = right in Arabic (RTL), left in English (LTR).
    val isRtl = androidx.compose.ui.platform.LocalLayoutDirection.current == LayoutDirection.Rtl
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 14.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Slot 1 (physical LEFT in LTR / physical RIGHT in RTL): Back arrow,
        // OR globe when on login (so in Arabic the globe/back swap sides
        // according to language, but the globe NEVER moves from its own
        // side — wait no: the user wants globe fixed on the RIGHT, back
        // follows language).
        // Implement:
        //   - English (LTR): Back on LEFT,  Globe on RIGHT
        //   - Arabic  (RTL): Back on RIGHT, Globe on RIGHT? No — user said
        //     "ارجعه الى مكانه الى اليمين في العربية واليسار في الإنجليزية"
        //     meaning the BACK button should be on the right in Arabic and
        //     left in English, AND the globe should be "ثابتة بنفس مكانها
        //     على اليمين". That means in Arabic both back and globe want
        //     the right side → put Back on the LEFT in Arabic? No, re-read:
        //     "ثبتها على اليمين" → globe on right (fixed).
        //     "زر الرجوع ... الى اليمين في العربية واليسار في الإنجليزية"
        //     Wait that would put BOTH on right in Arabic. That overlaps.
        //     Let's do: globe always rightmost. Back is on the START edge
        //     (left in EN, right in AR). If we put back on right in AR while
        //     globe is also on right, they clash. So put them side by side
        //     on the right in Arabic, single on left in English. Cleanest
        //     interpretation: back follows platform direction (top-left in
        //     English, top-right in Arabic), globe is on the OPPOSITE corner
        //     from back — wait user explicitly said globe ثابت على اليمين.
        //     Final layout:
        //       EN: [Back←] ........... [🌐]     (back left, globe right)
        //       AR: [🌐] ........... [→Back]    (globe left? No, user said
        //                                          globe fixed on right)
        //     Re-reading: "اجعل الايقونة ثابتة بنفس مكانها على اليمين" +
        //     "زر الرجوع الى اليمين في العربية واليسار في الإنجليزية"
        //     That means: globe always right, back is on right when AR and
        //     on left when EN → in AR they share the right side. So in AR
        //     put globe on far right, back next to it (slightly left of it).
        //     In EN back on far left, globe far right.
        // To achieve this without RTL flipping the Row, force LTR layout
        // for the header and place children based on isRtl boolean.
    }
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 14.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left corner (physical):
            //   EN: Back button here
            //   AR: empty
            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.CenterStart
            ) {
                if (!isRtl) {
                    // EN: back on left
                    HeaderButton(visible = showBack, onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Gold,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            // Center spacer
            Spacer(Modifier.width(8.dp))

            // Right corner:
            //   AR: Back button (flipped to point right) + gap + Globe
            //   EN: just Globe
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (isRtl) {
                    HeaderButton(visible = showBack, onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Gold,
                            modifier = Modifier
                                .size(18.dp)
                                .graphicsLayer { scaleX = -1f }
                        )
                    }
                }
                HeaderButton(visible = showLang, onClick = onLangToggle) {
                    Icon(
                        imageVector = Icons.Default.Language,
                        contentDescription = "Language",
                        tint = Gold,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun HeaderButton(
    visible: Boolean,
    onClick: () -> Unit,
    content: @Composable () -> Unit
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(180)) + scaleIn(initialScale = 0.7f, animationSpec = spring()),
        exit = fadeOut(tween(140)) + scaleOut(targetScale = 0.7f, animationSpec = spring())
    ) {
        CircleHeaderButton(onClick = onClick, content = content)
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
    Spacer(modifier = Modifier.height(4.dp))
}

