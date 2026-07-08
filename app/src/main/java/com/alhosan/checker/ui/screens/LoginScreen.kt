package com.alhosan.checker.ui.screens

import android.app.Activity
import android.content.ClipboardManager
import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.alhosan.checker.R
import com.alhosan.checker.data.model.AppLang
import com.alhosan.checker.data.model.CheckMode
import com.alhosan.checker.data.model.CheckerState
import com.alhosan.checker.ui.components.AlHosanInputRow
import com.alhosan.checker.ui.components.AlHosanMainButton
import com.alhosan.checker.ui.components.AlHosanToast
import com.alhosan.checker.ui.components.ShinyText
import com.alhosan.checker.ui.components.StaggeredColumn
import com.alhosan.checker.ui.components.alHosanStaggeredEnter
import com.alhosan.checker.ui.components.alHosanStaggeredExit
import com.alhosan.checker.ui.theme.Black
import com.alhosan.checker.ui.theme.BorderGold
import com.alhosan.checker.ui.theme.CardBg
import com.alhosan.checker.ui.theme.Gold
import com.alhosan.checker.ui.theme.GoldGradientBrush
import com.alhosan.checker.viewmodel.CheckerViewModel
import com.alhosan.checker.ui.i18n.*
import kotlin.math.abs

/**
 * Login/Check screen.
 *
 * LAYOUT (top → bottom):
 *   1. ScreenHeader Row (back-slot empty + language toggle)
 *   2. HeaderSpacer (12dp breathing space)
 *   3. Card with tabs + inputs + START CHECK button
 *   4. 16dp breathing space (outside the card)
 *   5. HISTORY button — OUTSIDE the card, separate, full-width below
 *
 * While checking, a fullscreen blurred-black overlay is shown with the
 * horse logo + shiny-text label (instead of an inline progress bar).
 *
 * Staggered fade-in (reactbits.dev staggered-menu effect) cascades the
 * card content on first appearance using the app's gold theme colors.
 */
@Composable
fun LoginScreen(
    onResultReady: () -> Unit,
    onHistoryClick: () -> Unit,
    viewModel: CheckerViewModel = viewModel()
) {
    val host by viewModel.host.collectAsState()
    val username by viewModel.username.collectAsState()
    val password by viewModel.password.collectAsState()
    val m3uLink by viewModel.m3uLink.collectAsState()
    val obscurePassword by viewModel.obscurePassword.collectAsState()
    val checkMode by viewModel.checkMode.collectAsState()
    val lang by viewModel.lang.collectAsState()
    val state by viewModel.state.collectAsState()
    val isChecking by viewModel.isChecking.collectAsState()
    val toastMessage by viewModel.toastMessage.collectAsState()

    val context = LocalContext.current

    BackHandler(enabled = true) {
        (context as? Activity)?.finish()
    }

    // Track whether this login screen instance initiated the check.
    // Without this, returning from history -> login would see the still-set
    // Success state and immediately jump back to result.
    var loginInitiatedCheck by remember { mutableStateOf(false) }

    LaunchedEffect(state) {
        when (state) {
            is CheckerState.Success -> {
                // Only navigate to result if THIS login screen started the check.
                if (loginInitiatedCheck) {
                    loginInitiatedCheck = false
                    onResultReady()
                }
            }
            is CheckerState.Error -> {
                if (loginInitiatedCheck) {
                    viewModel.showToast((state as CheckerState.Error).message)
                    viewModel.resetState()
                    loginInitiatedCheck = false
                }
            }
            else -> {}
        }
    }

    LaunchedEffect(toastMessage) {
        if (toastMessage != null) {
            kotlinx.coroutines.delay(2500)
            viewModel.clearToast()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Black)
            .pointerInput(checkMode, isChecking) {
                if (!isChecking) {
                    var dragTotal = 0f
                    detectHorizontalDragGestures(
                        onDragStart = { dragTotal = 0f },
                        onHorizontalDrag = { _, dragAmount -> dragTotal += dragAmount },
                        onDragEnd = {
                            if (abs(dragTotal) > 80f) {
                                if (dragTotal < 0f) viewModel.setCheckMode(CheckMode.M3U)
                                else viewModel.setCheckMode(CheckMode.XTREAM)
                            }
                            dragTotal = 0f
                        },
                        onDragCancel = { dragTotal = 0f }
                    )
                }
            }
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // ── Card with tabs + inputs + start-check button ──
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp),
                colors = CardDefaults.cardColors(containerColor = CardBg),
                border = androidx.compose.foundation.BorderStroke(1.dp, BorderGold),
                shape = RoundedCornerShape(28.dp)
            ) {
                Column(modifier = Modifier.padding(24.dp, 20.dp)) {
                    // Staggered reveal of the card content (staggered-menu effect)
                    StaggeredColumn(perItemDelayMs = 50) {
                        Item { TabsRow(checkMode, viewModel::setCheckMode, lang) }
                        Item {
                            Spacer(modifier = Modifier.height(22.dp))
                            SwipeableModeInputs(
                                checkMode = checkMode,
                                onModeChange = viewModel::setCheckMode,
                                host = host,
                                username = username,
                                password = password,
                                m3uLink = m3uLink,
                                obscurePassword = obscurePassword,
                                lang = lang,
                                context = context,
                                onHostChange = viewModel::updateHost,
                                onUsernameChange = viewModel::updateUsername,
                                onPasswordChange = viewModel::updatePassword,
                                onM3uLinkChange = viewModel::updateM3uLink,
                                onTogglePassword = viewModel::togglePasswordVisibility
                            )
                        }
                        // Start-check button — with extra breathing space above so it
                        // doesn't overlap the password field's border.
                        Item {
                            Spacer(modifier = Modifier.height(28.dp))
                            AlHosanMainButton(
                                text = lang.check,
                                icon = Icons.Default.Search,
                                onClick = {
                                    // Mark that THIS login screen initiated the check,
                                    // so the LaunchedEffect above knows to navigate to result.
                                    loginInitiatedCheck = true
                                    viewModel.checkSubscription()
                                },
                                isLoading = isChecking,
                                enabled = !isChecking
                            )
                        }
                    }
                }
            }

            // ── HISTORY button — OUTSIDE the card, below it with breathing space ──
            Spacer(modifier = Modifier.height(16.dp))
            AlHosanMainButton(
                text = lang.hist,
                icon = Icons.Default.History,
                onClick = onHistoryClick,
                isSubButton = true,
                modifier = Modifier.padding(horizontal = 14.dp)
            )
        }

        // ── Toast ──
        AnimatedVisibility(
            visible = toastMessage != null,
            enter = alHosanStaggeredEnter(),
            exit = alHosanStaggeredExit(),
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 35.dp)
        ) {
            if (toastMessage != null) AlHosanToast(message = toastMessage!!)
        }

        // ── Checking overlay: blurred black bg + horse logo + shiny text ──
        // Replaces the inline progress bar. Cover the entire screen so the user
        // sees a clean focused "checking" state.
        AnimatedVisibility(
            visible = isChecking,
            enter = alHosanStaggeredEnter(durationMs = 420),
            exit = alHosanStaggeredExit(durationMs = 300),
            modifier = Modifier.fillMaxSize()
        ) {
            CheckingOverlay(lang = lang)
        }
    }
}

/**
 * Fullscreen overlay shown while a subscription check is running.
 * - Semi-transparent black background with a blur-like darkening
 * - Small horse logo centered
 * - Shiny-text "الفحص جارٍ..." with animated gold gradient sweep
 */
@Composable
private fun CheckingOverlay(lang: AppLang) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xCC000000)),  // 80% black, simulates blurred backdrop
        contentAlignment = Alignment.Center
    ) {
        StaggeredColumn(perItemDelayMs = 70) {
            Item {
                Image(
                    painter = painterResource(id = R.drawable.ic_alhosan_logo),
                    contentDescription = null,
                    modifier = Modifier.size(96.dp),
                    contentScale = ContentScale.Fit
                )
            }
            Item {
                Spacer(modifier = Modifier.height(20.dp))
                ShinyText(
                    text = if (lang == AppLang.AR) "الفحص جارٍ..." else "Checking...",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            }
        }
    }
}

@OptIn(ExperimentalAnimationApi::class)
@Composable
private fun SwipeableModeInputs(
    checkMode: CheckMode,
    onModeChange: (CheckMode) -> Unit,
    host: String,
    username: String,
    password: String,
    m3uLink: String,
    obscurePassword: Boolean,
    lang: AppLang,
    context: Context,
    onHostChange: (String) -> Unit,
    onUsernameChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onM3uLinkChange: (String) -> Unit,
    onTogglePassword: () -> Unit
) {
    var dragTotal by remember { mutableStateOf(0f) }

    AnimatedContent(
        targetState = checkMode,
        transitionSpec = {
            val toM3u = targetState == CheckMode.M3U
            val enter = slideInHorizontally(
                animationSpec = tween(320),
                // Reversed direction: M3U enters from left, Xtream enters from right.
                initialOffsetX = { fullWidth -> if (toM3u) -fullWidth else fullWidth }
            ) + fadeIn(tween(220))
            val exit = slideOutHorizontally(
                animationSpec = tween(260),
                // Reversed direction: old page exits opposite the entering page.
                targetOffsetX = { fullWidth -> if (toM3u) fullWidth else -fullWidth }
            ) + fadeOut(tween(180))
            enter togetherWith exit
        },
        label = "mode-inputs",
        modifier = Modifier
            .fillMaxWidth()
            .pointerInput(checkMode) {
                detectHorizontalDragGestures(
                    onDragStart = { dragTotal = 0f },
                    onHorizontalDrag = { _, dragAmount -> dragTotal += dragAmount },
                    onDragEnd = {
                        if (abs(dragTotal) > 80f) {
                            if (dragTotal < 0f) onModeChange(CheckMode.M3U)
                            else onModeChange(CheckMode.XTREAM)
                        }
                        dragTotal = 0f
                    },
                    onDragCancel = { dragTotal = 0f }
                )
            }
    ) { mode ->
        Column(modifier = Modifier.fillMaxWidth()) {
            if (mode == CheckMode.XTREAM) {
                AlHosanInputRow(
                    value = host,
                    onValueChange = onHostChange,
                    placeholder = lang.hPl,
                    onPaste = { pasteFromClipboard(context, onHostChange) }
                )
                Spacer(modifier = Modifier.height(14.dp))
                AlHosanInputRow(
                    value = username,
                    onValueChange = onUsernameChange,
                    placeholder = lang.uPl,
                    onPaste = { pasteFromClipboard(context, onUsernameChange) }
                )
                Spacer(modifier = Modifier.height(14.dp))
                AlHosanInputRow(
                    value = password,
                    onValueChange = onPasswordChange,
                    placeholder = lang.pPl,
                    isPassword = true,
                    obscurePassword = obscurePassword,
                    onTogglePassword = onTogglePassword,
                    onPaste = { pasteFromClipboard(context, onPasswordChange) }
                )
            } else {
                AlHosanInputRow(
                    value = m3uLink,
                    onValueChange = onM3uLinkChange,
                    placeholder = lang.mPl,
                    onPaste = { pasteFromClipboard(context, onM3uLinkChange) }
                )
            }
        }
    }
}


@Composable
private fun TabsRow(
    selectedMode: CheckMode,
    onModeChange: (CheckMode) -> Unit,
    lang: AppLang,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(Color.Black, RoundedCornerShape(18.dp))
            .border(1.dp, BorderGold, RoundedCornerShape(18.dp))
            .padding(6.dp),
        horizontalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        Box(
            modifier = Modifier
                .weight(1f).height(44.dp)
                .then(
                    if (selectedMode == CheckMode.XTREAM)
                        Modifier.clip(RoundedCornerShape(14.dp)).background(GoldGradientBrush)
                    else
                        Modifier.clip(RoundedCornerShape(14.dp)).background(Color.Transparent)
                )
                .clickable { onModeChange(CheckMode.XTREAM) },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Xtream",
                color = if (selectedMode == CheckMode.XTREAM) Color.Black else Color(0xFFA0A0A0),
                fontWeight = FontWeight.Bold, fontSize = 15.sp
            )
        }
        Box(
            modifier = Modifier
                .weight(1f).height(44.dp)
                .then(
                    if (selectedMode == CheckMode.M3U)
                        Modifier.clip(RoundedCornerShape(14.dp)).background(GoldGradientBrush)
                    else
                        Modifier.clip(RoundedCornerShape(14.dp)).background(Color.Transparent)
                )
                .clickable { onModeChange(CheckMode.M3U) },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "M3U Link",
                color = if (selectedMode == CheckMode.M3U) Color.Black else Color(0xFFA0A0A0),
                fontWeight = FontWeight.Bold, fontSize = 15.sp
            )
        }
    }
}

private fun pasteFromClipboard(context: Context, onPaste: (String) -> Unit) {
    try {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = clipboard.primaryClip
        if (clip != null && clip.itemCount > 0) {
            val text = clip.getItemAt(0)?.text?.toString()?.trim() ?: ""
            if (text.isNotEmpty()) onPaste(text)
        }
    } catch (_: Exception) { }
}
