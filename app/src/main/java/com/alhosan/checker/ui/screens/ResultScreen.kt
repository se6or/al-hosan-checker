package com.alhosan.checker.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.SignalCellularAlt
import androidx.compose.material3.Icon
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
import com.alhosan.checker.data.model.CheckerState
import com.alhosan.checker.ui.components.AlHosanMainButton
import com.alhosan.checker.ui.components.AlHosanToast
import com.alhosan.checker.ui.components.CapsuleItem
import com.alhosan.checker.ui.components.CapsuleStacked
import com.alhosan.checker.ui.components.ContentCountDisplay
import com.alhosan.checker.ui.components.StatusBadge
import com.alhosan.checker.ui.theme.Black
import com.alhosan.checker.ui.theme.BorderGold
import com.alhosan.checker.ui.theme.Gold
import com.alhosan.checker.viewmodel.CheckerViewModel
import com.alhosan.checker.ui.i18n.*
import kotlinx.coroutines.delay

/**
 * Result screen - matching HTML reference's #scr-result
 * Features: capsule-style cards, copy buttons, status badge, M3U generation, save, export
 */
@Composable
fun ResultScreen(
    onBack: () -> Unit,
    viewModel: CheckerViewModel = viewModel()
) {
    val state by viewModel.state.collectAsState()
    val lang by viewModel.lang.collectAsState()
    val toastMessage by viewModel.toastMessage.collectAsState()
    val isCounting by viewModel.isCounting.collectAsState()
    val subscription = (state as? CheckerState.Success)?.subscription
    val context = LocalContext.current

    // Navigate back if no subscription data
    LaunchedEffect(subscription) {
        if (subscription == null) {
            onBack()
        }
    }

    if (subscription == null) {
        return
    }

    // Clear toast after delay
    LaunchedEffect(toastMessage) {
        if (toastMessage != null) {
            delay(2500)
            viewModel.clearToast()
        }
    }

    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            // ─── Capture zone (matches HTML #capture-zone) ───
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Black, RoundedCornerShape(28.dp))
                    .padding(16.dp)
            ) {
                // Capsule 1: Host, Username, Password (stacked with copy)
                CapsuleStacked(
                    items = listOf(
                        CapsuleItem(
                            icon = Icons.Default.SignalCellularAlt,
                            label = lang.lHost,
                            value = subscription.host,
                            onCopy = { copyToClipboard(context, subscription.host, lang) }
                        ),
                        CapsuleItem(
                            icon = Icons.Default.Groups,
                            label = lang.lUser,
                            value = subscription.username,
                            onCopy = { copyToClipboard(context, subscription.username, lang) }
                        ),
                        CapsuleItem(
                            icon = Icons.Default.Key,
                            label = lang.lPass,
                            value = subscription.password,
                            onCopy = { copyToClipboard(context, subscription.password, lang) }
                        )
                    )
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Capsule 2: Created / Expiry dates
                CapsuleStacked(
                    items = listOf(
                        CapsuleItem(
                            icon = Icons.Default.CalendarToday,
                            label = lang.lCreated,
                            value = subscription.created,
                            onCopy = { copyToClipboard(context, subscription.created, lang) }
                        ),
                        CapsuleItem(
                            icon = Icons.Default.CalendarMonth,
                            label = lang.lExpiry,
                            value = subscription.expiry,
                            onCopy = { copyToClipboard(context, subscription.expiry, lang) }
                        )
                    )
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Capsule 3: Status + Trial (horizontal, matching HTML reference)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.linearGradient(listOf(Color(0xFF080808), Color(0xFF121212))),
                            RoundedCornerShape(20.dp)
                        )
                        .border(1.dp, BorderGold, RoundedCornerShape(20.dp))
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Status
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.SignalCellularAlt,
                                contentDescription = null,
                                tint = Gold,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = lang.lStatus,
                                color = Color(0xFFA0A0A0),
                                fontSize = 13.sp
                            )
                        }
                        StatusBadge(
                            isActive = subscription.isActive,
                            text = if (subscription.isActive) lang.on else lang.off
                        )
                    }

                    // Trial
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Science,
                                contentDescription = null,
                                tint = Gold,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = lang.lTrial,
                                color = Color(0xFFA0A0A0),
                                fontSize = 13.sp
                            )
                        }
                        Text(
                            text = if (subscription.isTrial) lang.yes else lang.no,
                            color = Color.White,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 14.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Capsule 4: Active connections / Max connections
                CapsuleStacked(
                    items = listOf(
                        CapsuleItem(
                            icon = Icons.Default.Devices,
                            label = lang.lDevices,
                            value = subscription.activeCons,
                            onCopy = { copyToClipboard(context, subscription.activeCons, lang) }
                        ),
                        CapsuleItem(
                            icon = Icons.Default.Groups,
                            label = lang.lMaxCons,
                            value = subscription.maxCons,
                            onCopy = { copyToClipboard(context, subscription.maxCons, lang) }
                        )
                    )
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Capsule 5: Content counts (Channels | Movies | Series)
                ContentCountDisplay(
                    liveCount = subscription.liveCount,
                    movieCount = subscription.movieCount,
                    seriesCount = subscription.seriesCount,
                    channelsLabel = lang.lChannels,
                    moviesLabel = lang.lMovies,
                    seriesLabel = lang.lSeries,
                    isLoading = isCounting
                )
            }

            // ─── Action row: Save, M3U, Export Image ───
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Save button
                Box(modifier = Modifier.weight(1.5f)) {
                    AlHosanMainButton(
                        text = lang.btnS,
                        icon = Icons.Default.Save,
                        onClick = { viewModel.saveToHistory() }
                    )
                }

                // M3U button
                Box(modifier = Modifier.weight(1f)) {
                    AlHosanMainButton(
                        text = lang.btnM3U,
                        icon = Icons.Default.Link,
                        onClick = {
                            val m3uLink = viewModel.generateM3uLink()
                            copyToClipboard(context, m3uLink, lang)
                            viewModel.showToast(lang.tCopiedM3U)
                        },
                        isSubButton = true
                    )
                }

                // Export as image button
                Box(modifier = Modifier.weight(1f)) {
                    AlHosanMainButton(
                        text = lang.btnE,
                        icon = Icons.Default.PhotoCamera,
                        onClick = {
                            // Export as image - copy all text as workaround
                            val info = "${lang.lHost}: ${subscription.host}\n" +
                                    "${lang.lUser}: ${subscription.username}\n" +
                                    "${lang.lPass}: ${subscription.password}\n" +
                                    "${lang.lStatus}: ${if (subscription.isActive) lang.on else lang.off}\n" +
                                    "${lang.lCreated}: ${subscription.created}\n" +
                                    "${lang.lExpiry}: ${subscription.expiry}\n" +
                                    "${lang.lDevices}: ${subscription.activeCons}\n" +
                                    "${lang.lMaxCons}: ${subscription.maxCons}\n" +
                                    "${lang.lChannels}: ${subscription.liveCount} | ${lang.lMovies}: ${subscription.movieCount} | ${lang.lSeries}: ${subscription.seriesCount}"
                            copyToClipboard(context, info, lang)
                            viewModel.showToast(lang.tCopied)
                        },
                        isSubButton = true
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
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
 * Copy value to clipboard - matching HTML reference's copyVal() function
 */
private fun copyToClipboard(context: Context, text: String, lang: AppLang) {
    if (text == "--" || text == "N/A" || text.isBlank()) return
    try {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("AlHosan", text))
    } catch (_: Exception) { }
}
