package com.alhosan.checker.ui.screens

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.alhosan.checker.data.model.AppLang
import com.alhosan.checker.data.model.CheckMode
import com.alhosan.checker.data.model.CheckerState
import com.alhosan.checker.ui.components.AlHosanInputRow
import com.alhosan.checker.ui.components.AlHosanMainButton
import com.alhosan.checker.ui.components.AlHosanProgressBar
import com.alhosan.checker.ui.components.AlHosanToast
import com.alhosan.checker.ui.components.RunningHorseLogo
import com.alhosan.checker.ui.theme.BorderGold
import com.alhosan.checker.ui.theme.CardBg
import com.alhosan.checker.ui.theme.Gold
import com.alhosan.checker.ui.theme.GoldGradientBrush
import com.alhosan.checker.ui.theme.SurfaceBlack
import com.alhosan.checker.viewmodel.CheckerViewModel
import com.alhosan.checker.ui.i18n.Strings.*

/**
 * Login/Check screen - matching HTML reference's #scr-login
 * Features: Xtream/M3U tabs, paste buttons, progress bar, running horse animation
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
    val progressPhase by viewModel.progressPhase.collectAsState()
    val progressPercent by viewModel.progressPercent.collectAsState()
    val toastMessage by viewModel.toastMessage.collectAsState()
    val isCounting by viewModel.isCounting.collectAsState()

    val context = LocalContext.current

    // Handle state changes
    LaunchedEffect(state) {
        when (state) {
            is CheckerState.Success -> onResultReady()
            is CheckerState.Error -> {
                viewModel.showToast((state as CheckerState.Error).message)
                viewModel.resetState()
            }
            else -> {}
        }
    }

    // Clear toast after delay
    LaunchedEffect(toastMessage) {
        if (toastMessage != null) {
            kotlinx.coroutines.delay(2500)
            viewModel.clearToast()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(14.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // ─── Card container matching HTML reference's .card ───
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = CardBg),
                border = androidx.compose.foundation.BorderStroke(1.dp, BorderGold),
                shape = RoundedCornerShape(28.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp, 20.dp)
                ) {
                    // ─── Tabs: Xtream / M3U Link ───
                    TabsRow(
                        selectedMode = checkMode,
                        onModeChange = viewModel::setCheckMode,
                        lang = lang
                    )

                    Spacer(modifier = Modifier.height(22.dp))

                    // ─── Input fields ───
                    if (checkMode == CheckMode.XTREAM) {
                        // Xtream inputs
                        AlHosanInputRow(
                            value = host,
                            onValueChange = viewModel::updateHost,
                            placeholder = lang.hPl,
                            onPaste = { pasteFromClipboard(context, viewModel::updateHost) }
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        AlHosanInputRow(
                            value = username,
                            onValueChange = viewModel::updateUsername,
                            placeholder = lang.uPl,
                            onPaste = { pasteFromClipboard(context, viewModel::updateUsername) }
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        AlHosanInputRow(
                            value = password,
                            onValueChange = viewModel::updatePassword,
                            placeholder = lang.pPl,
                            isPassword = true,
                            obscurePassword = obscurePassword,
                            onTogglePassword = viewModel::togglePasswordVisibility,
                            onPaste = { pasteFromClipboard(context, viewModel::updatePassword) }
                        )
                    } else {
                        // M3U Link input
                        AlHosanInputRow(
                            value = m3uLink,
                            onValueChange = viewModel::updateM3uLink,
                            placeholder = lang.mPl,
                            onPaste = { pasteFromClipboard(context, viewModel::updateM3uLink) }
                        )
                    }

                    // ─── Progress bar (visible during check) ───
                    AnimatedVisibility(
                        visible = isChecking,
                        enter = fadeIn(),
                        exit = fadeOut()
                    ) {
                        Column {
                            Spacer(modifier = Modifier.height(20.dp))
                            AlHosanProgressBar(
                                progress = progressPercent / 100f,
                                label = when (progressPhase) {
                                    com.alhosan.checker.data.model.ProgressPhase.CONNECTING -> lang.prog1
                                    com.alhosan.checker.data.model.ProgressPhase.VERIFYING -> lang.prog2
                                    com.alhosan.checker.data.model.ProgressPhase.COUNTING -> lang.prog3
                                    com.alhosan.checker.data.model.ProgressPhase.FINALIZING -> lang.prog4
                                    else -> lang.prog1
                                }
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                        }
                    }

                    // ─── Check button ───
                    AlHosanMainButton(
                        text = lang.check,
                        icon = Icons.Default.Search,
                        onClick = viewModel::checkSubscription,
                        isLoading = isChecking,
                        enabled = !isChecking
                    )

                    // ─── History button ───
                    Spacer(modifier = Modifier.height(14.dp))

                    AlHosanMainButton(
                        text = lang.hist,
                        icon = Icons.Default.History,
                        onClick = onHistoryClick,
                        isSubButton = true
                    )
                }
            }
        }

        // ─── Toast at bottom ───
        AnimatedVisibility(
            visible = toastMessage != null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 35.dp)
        ) {
            if (toastMessage != null) {
                AlHosanToast(message = toastMessage!!)
            }
        }
    }
}

/**
 * Tabs row - matching HTML reference's .tabs design
 */
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
        // Xtream tab
        Box(
            modifier = Modifier
                .weight(1f)
                .height(44.dp)
                .then(
                    if (selectedMode == CheckMode.XTREAM) Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .background(GoldGradientBrush)
                    else Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color.Transparent)
                )
                .clickable { onModeChange(CheckMode.XTREAM) },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Xtream",
                color = if (selectedMode == CheckMode.XTREAM) Color.Black else Color(0xFFA0A0A0),
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp
            )
        }

        // M3U Link tab
        Box(
            modifier = Modifier
                .weight(1f)
                .height(44.dp)
                .then(
                    if (selectedMode == CheckMode.M3U) Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .background(GoldGradientBrush)
                    else Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color.Transparent)
                )
                .clickable { onModeChange(CheckMode.M3U) },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "M3U Link",
                color = if (selectedMode == CheckMode.M3U) Color.Black else Color(0xFFA0A0A0),
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp
            )
        }
    }
}

/**
 * Paste from clipboard - matching HTML reference's vPaste() function
 */
private fun pasteFromClipboard(context: Context, onPaste: (String) -> Unit) {
    try {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = clipboard.primaryClip
        if (clip != null && clip.itemCount > 0) {
            val text = clip.getItemAt(0)?.text?.toString()?.trim() ?: ""
            if (text.isNotEmpty()) {
                onPaste(text)
                return
            }
        }
    } catch (_: Exception) { }
}
