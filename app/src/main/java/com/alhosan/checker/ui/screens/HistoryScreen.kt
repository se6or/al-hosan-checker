package com.alhosan.checker.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.alhosan.checker.data.model.AppLang
import com.alhosan.checker.data.model.HistoryItem
import com.alhosan.checker.ui.components.AlHosanToast
import com.alhosan.checker.ui.components.StaggeredColumn
import com.alhosan.checker.ui.components.alHosanStaggeredEnter
import com.alhosan.checker.ui.components.alHosanStaggeredExit
import com.alhosan.checker.ui.i18n.*
import com.alhosan.checker.ui.theme.Black
import com.alhosan.checker.ui.theme.BorderGold
import com.alhosan.checker.ui.theme.Gold
import com.alhosan.checker.ui.theme.CardBg
import com.alhosan.checker.ui.theme.GreenActive
import com.alhosan.checker.ui.theme.RedInactive
import com.alhosan.checker.ui.theme.TextDim
import com.alhosan.checker.viewmodel.CheckerViewModel

/**
 * History screen - matching HTML reference's #scr-history
 * Features: Saved list, delete individual, clear all, restore, empty state
 */
@Composable
fun HistoryScreen(
    onBack: () -> Unit,
    onRestore: () -> Unit,
    viewModel: CheckerViewModel = viewModel()
) {
    val history by viewModel.history.collectAsState()
    val lang by viewModel.lang.collectAsState()
    val toastMessage by viewModel.toastMessage.collectAsState()
    val modalMessage by viewModel.modalMessage.collectAsState()

    // Handle system back button / gesture — matches HTML's handleAndroidBack
    BackHandler(enabled = true) {
        onBack()
    }

    // Clear toast after delay
    LaunchedEffect(toastMessage) {
        if (toastMessage != null) {
            kotlinx.coroutines.delay(2500)
            viewModel.clearToast()
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Black)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            // ── Card container ──
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 14.dp, end = 14.dp, top = 4.dp, bottom = 14.dp),
                colors = CardDefaults.cardColors(containerColor = CardBg),
                border = androidx.compose.foundation.BorderStroke(1.dp, BorderGold),
                shape = RoundedCornerShape(28.dp)
            ) {
                StaggeredColumn(
                    modifier = Modifier.padding(24.dp, 20.dp),
                    perItemDelayMs = 45
                ) {
                    // Header row: title + clear all button
                    Item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = lang.hSub,
                                color = TextDim,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )

                            // Only show "Clear All" when there's something to clear
                            if (history.isNotEmpty()) {
                                Box(
                                    modifier = Modifier
                                        .background(Color.Transparent, RoundedCornerShape(16.dp))
                                        .border(1.dp, RedInactive, RoundedCornerShape(16.dp))
                                        .clip(RoundedCornerShape(16.dp))
                                        .clickable {
                                            viewModel.showModal("clearAllMsg") {
                                                viewModel.clearHistory()
                                            }
                                        }
                                        .padding(horizontal = 18.dp, vertical = 10.dp)
                                ) {
                                    Text(
                                        text = lang.clearAll,
                                        color = RedInactive,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp
                                    )
                                }
                            }
                        }
                    }

                    Item { Spacer(modifier = Modifier.height(24.dp)) }

                    // History list or empty state
                    if (history.isEmpty()) {
                        Item {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 60.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    imageVector = Icons.Default.History,
                                    contentDescription = null,
                                    tint = BorderGold,
                                    modifier = Modifier.size(56.dp)
                                )
                                Spacer(modifier = Modifier.height(18.dp))
                                Text(
                                    text = lang.noHistory,
                                    color = TextDim,
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    } else {
                        history.forEachIndexed { index, item ->
                            Item {
                                HistoryItemRow(
                                    item = item,
                                    lang = lang,
                                    onClick = {
                                        if (viewModel.restoreHistoryItem(index)) {
                                            onRestore()
                                        }
                                    },
                                    onDelete = {
                                        viewModel.showModal("delLogMsg") {
                                            viewModel.deleteHistoryItem(index)
                                        }
                                    }
                                )
                                Spacer(modifier = Modifier.height(14.dp))
                            }
                        }
                    }
                }
            }
            } // end outer Column

        // ─── Toast at bottom ───
        AnimatedVisibility(
            visible = toastMessage != null,
            enter = alHosanStaggeredEnter(),
            exit = alHosanStaggeredExit(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 35.dp)
        ) {
            if (toastMessage != null) {
                AlHosanToast(message = toastMessage!!)
            }
        }
    }

    // ─── Modal overlay ───
    AnimatedVisibility(
        visible = modalMessage != null,
        enter = alHosanStaggeredEnter(durationMs = 420),
        exit = alHosanStaggeredExit(durationMs = 300)
    ) {
        if (modalMessage != null) {
            com.alhosan.checker.ui.components.AlHosanModal(
                message = when (modalMessage) {
                    "delLogMsg" -> lang.delLogMsg
                    "clearAllMsg" -> lang.clearAllMsg
                    else -> modalMessage!!
                },
                cancelText = lang.modalCancel,
                confirmText = lang.modalConfirm,
                onCancel = viewModel::onModalCancel,
                onConfirm = viewModel::onModalConfirm
            )
        }
    }
}

/**
 * Single history item row - compact layout with theme icons
 * Order: username (title) → server (with Dns icon) → saved date (with CalendarToday icon)
 */
@Composable
private fun HistoryItemRow(
    item: HistoryItem,
    lang: AppLang,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF050505), RoundedCornerShape(18.dp))
            .border(1.dp, BorderGold, RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Left side: status dot + content column
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Status dot — top aligned
            Box(
                modifier = Modifier
                    .padding(top = 6.dp)
                    .size(9.dp)
                    .background(
                        if (item.isActive) GreenActive else RedInactive,
                        CircleShape
                    )
            )

            // Content column: Username → Server → Saved date
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.Start
            ) {
                // Row 1: Username (bold)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = null,
                        tint = Gold,
                        modifier = Modifier.size(13.dp)
                    )
                    Text(
                        text = item.user.ifBlank { if (lang == AppLang.AR) "(بلا اسم)" else "(no user)" },
                        color = Color.White,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 14.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.height(3.dp))

                // Row 2: Server/host with Dns icon
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Dns,
                        contentDescription = null,
                        tint = TextDim,
                        modifier = Modifier.size(12.dp)
                    )
                    Text(
                        text = if (item.isM3uMode && item.m3uLink.isNotBlank()) {
                            item.m3uLineForDisplay()
                        } else {
                            item.host
                        }.take(40) + if ((if (item.isM3uMode) item.m3uLink.length else item.host.length) > 40) "…" else "",
                        color = TextDim,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Row 3: Saved date with CalendarToday icon (only if present)
                if (item.time.isNotBlank()) {
                    Spacer(modifier = Modifier.height(3.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CalendarToday,
                            contentDescription = null,
                            tint = Gold.copy(alpha = 0.8f),
                            modifier = Modifier.size(12.dp)
                        )
                        Text(
                            text = item.time,
                            color = Gold.copy(alpha = 0.85f),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }

        // Right side: Delete button (compact)
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .clickable(onClick = onDelete),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Delete,
                contentDescription = "Delete",
                tint = RedInactive.copy(alpha = 0.85f),
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

/** Extract a short display string from an M3U URL. */
private fun String.m3uLineForDisplay(): String {
    return try {
        val uri = android.net.Uri.parse(this)
        val host = uri.host ?: this
        val port = if (uri.port != -1) ":${uri.port}" else ""
        host + port
    } catch (_: Exception) { this.take(40) }
}
