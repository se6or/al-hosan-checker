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
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.clip
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
 * Header bar with: language toggle (left), horse logo/title (center), back button (right)
 */
@Composable
fun AlHosanApp() {
    val navController = rememberNavController()
    val viewModel: CheckerViewModel = viewModel()
    val lang by viewModel.lang.collectAsState()
    val isChecking by viewModel.isChecking.collectAsState()
    val state by viewModel.state.collectAsState()

    // Track current route for header visibility
    // Splash has no header; other screens show the app header
    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            // NavHost
            NavHost(
                navController = navController,
                startDestination = "splash",
                enterTransition = {
                    slideIntoContainer(
                        AnimatedContentTransitionScope.SlideDirection.Start,
                        animationSpec = tween(300)
                    )
                },
                exitTransition = {
                    slideOutOfContainer(
                        AnimatedContentTransitionScope.SlideDirection.Start,
                        animationSpec = tween(300)
                    )
                },
                popEnterTransition = {
                    slideIntoContainer(
                        AnimatedContentTransitionScope.SlideDirection.End,
                        animationSpec = tween(300)
                    )
                },
                popExitTransition = {
                    slideOutOfContainer(
                        AnimatedContentTransitionScope.SlideDirection.End,
                        animationSpec = tween(300)
                    )
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
                    AppHeader(
                        lang = lang,
                        isRunning = isChecking,
                        showBack = false,
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
                        isRunning = isChecking,
                        showBack = true,
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
                        isRunning = false,
                        showBack = true,
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

/**
 * App header bar - matching HTML reference's .app-header design
 * Left: Language toggle button
 * Center: Running horse animation (when checking) or app title
 * Right: Back button (when applicable)
 */
@Composable
fun AppHeader(
    lang: AppLang,
    isRunning: Boolean,
    showBack: Boolean,
    onLangToggle: () -> Unit,
    onBack: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Black)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Left: Language toggle
        Box(
            modifier = Modifier
                .background(SurfaceBlack, RoundedCornerShape(14.dp))
                .border(1.dp, BorderGold, RoundedCornerShape(14.dp))
                .clip(RoundedCornerShape(14.dp))
                .clickable(onClick = onLangToggle)
                .padding(horizontal = 14.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Language,
                    contentDescription = null,
                    tint = Gold,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = lang.lBtn,
                    color = Gold,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
            }
        }

        // Center: Running horse animation or app title
        if (isRunning) {
            RunningHorseHeader(lang)
        } else {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Image(
                    painter = painterResource(id = R.drawable.ic_alhosan_logo),
                    contentDescription = null,
                    modifier = Modifier.size(28.dp),
                    contentScale = ContentScale.Fit
                )
                Text(
                    text = lang.splash,
                    color = Gold,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 14.sp
                )
            }
        }

        // Right: Back button or spacer
        if (showBack) {
            Box(
                modifier = Modifier
                    .background(SurfaceBlack, RoundedCornerShape(14.dp))
                    .border(1.dp, BorderGold, RoundedCornerShape(14.dp))
                    .clip(RoundedCornerShape(14.dp))
                    .clickable(onClick = onBack)
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = null,
                        tint = Gold,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = if (lang == AppLang.AR) "رجوع" else "Back",
                        color = Gold,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }
            }
        } else {
            // Invisible spacer to balance layout
            Spacer(modifier = Modifier.width(80.dp))
        }
    }
}

/**
 * Running horse animation for header - bouncing horse logo during check
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
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Image(
            painter = painterResource(id = R.drawable.ic_alhosan_logo),
            contentDescription = null,
            modifier = Modifier
                .size(28.dp)
                .offset(y = offsetY.dp),
            contentScale = ContentScale.Fit
        )
        Text(
            text = if (lang == AppLang.AR) "الفحص جارٍ..." else "Checking...",
            color = Gold,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 13.sp
        )
    }
}
