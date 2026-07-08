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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.History
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
            // ── Header Row (above the card, real layout slot — not overlay) ──
            com.alhosan.checker.ui.ScreenHeader(
                showBack = true,
                onBack = onBack,
                onLangToggle = viewModel::toggleLang
            )

            // ── Breathing space between header and card ──
            com.alhosan.checker.ui.HeaderSpacer()

            // ── Card container ──
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
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
 * Single history item row - matching HTML reference's .history-item
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
            .background(Color(0xFF050505), RoundedCornerShape(24.dp))
            .border(1.dp, BorderGold, RoundedCornerShape(24.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 18.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Left side: status dot + text
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Status dot
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .background(
                        if (item.isActive) GreenActive else RedInactive,
                        CircleShape
                    )
            )

            // Host + username
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.Start
            ) {
                Text(
                    text = item.host.take(35) + if (item.host.length > 35) "…" else "",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = item.user,
                    color = TextDim,
                    fontSize = 13.sp
                )
            }
        }

        // Delete button
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .clickable(onClick = onDelete),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Delete,
                contentDescription = "Delete",
                tint = RedInactive,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
