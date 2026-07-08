package com.alhosan.checker.ui.screens

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.alhosan.checker.data.model.CheckerState
import com.alhosan.checker.data.model.Subscription
import com.alhosan.checker.ui.components.AlHosanMainButton
import com.alhosan.checker.ui.components.AlHosanToast
import com.alhosan.checker.ui.components.CapsuleItem
import com.alhosan.checker.ui.components.ContentCountDisplay
import com.alhosan.checker.ui.components.ResultPrimaryInfoStacked
import com.alhosan.checker.ui.components.ResultSideBySideStacked
import com.alhosan.checker.ui.components.StaggeredColumn
import com.alhosan.checker.ui.components.StatusBadge
import com.alhosan.checker.ui.components.alHosanStaggeredEnter
import com.alhosan.checker.ui.components.alHosanStaggeredExit
import com.alhosan.checker.ui.theme.Black
import com.alhosan.checker.ui.theme.BorderGold
import com.alhosan.checker.ui.theme.Gold
import com.alhosan.checker.util.ImageExporter
import com.alhosan.checker.viewmodel.CheckerViewModel
import com.alhosan.checker.ui.i18n.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Result screen - matching HTML reference's #scr-result
 * Features: capsule-style cards, copy buttons, status badge, M3U generation, save, export-as-image
 *
 * The export-as-image button renders the subscription data to a Bitmap via
 * [com.alhosan.checker.util.ResultImageRenderer] (using Android's native Canvas)
 * and saves the PNG to Pictures/AlHosan via MediaStore.
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
    val isFromHistory by viewModel.isFromHistory.collectAsState()
    val subscription = (state as? CheckerState.Success)?.subscription
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Handle system back button / gesture — pop back to the previous screen
    // (history if we came from history, login if we came from login).
    BackHandler(enabled = true) { onBack() }

    // If there's no subscription to display, just render nothing.
    // We DON'T call onBack() from LaunchedEffect here because that created a
    // loop: resetState() -> subscription=null -> onBack() -> popBackStack -> ...
    // which is why restoring from history and pressing back jumped to login.
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

    val copyResultValue: (String) -> Unit = { value ->
        if (copyToClipboard(context, value)) {
            viewModel.showToast(lang.tCopied)
        }
    }

    fun saveResultImage(sub: Subscription) {
        scope.launch {
            val success = ImageExporter.saveSubscriptionToGallery(
                context = context,
                subscription = sub,
                lang = lang,
                // Saved PNG name: username.png, username (1).png, ...
                fileName = sub.username
            )
            viewModel.showToast(
                if (success) lang.tExportOk else lang.tExportFail
            )
        }
    }

    var pendingImageExport by remember { mutableStateOf<Subscription?>(null) }
    val exportPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        val pendingSub = pendingImageExport
        pendingImageExport = null
        if (granted && pendingSub != null) {
            saveResultImage(pendingSub)
        } else {
            viewModel.showToast(lang.tExportFail)
        }
    }

    val exportResultImage: (Subscription) -> Unit = { sub ->
        if (
            Build.VERSION.SDK_INT <= Build.VERSION_CODES.P &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            pendingImageExport = sub
            exportPermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        } else {
            saveResultImage(sub)
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

            // ── Card content ──
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
            // ─── Capture zone (matches HTML #capture-zone) ───
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Black, RoundedCornerShape(28.dp))
                    .padding(16.dp)
            ) {
                // ReactBits-like staggered reveal for every result frame.
                StaggeredColumn(perItemDelayMs = 45) {
                    // Frame 1: Main server information. Labels are fixed bilingual
                    // text, aligned right; copy buttons are left-only and show toast.
                    Item {
                        ResultPrimaryInfoStacked(
                            items = listOf(
                                CapsuleItem(
                                    icon = Icons.Default.SignalCellularAlt,
                                    label = "السيرفر / Server",
                                    value = subscription.host,
                                    onCopy = { copyResultValue(subscription.host) }
                                ),
                                CapsuleItem(
                                    icon = Icons.Default.Groups,
                                    label = "اسم المستخدم / Username",
                                    value = subscription.username,
                                    onCopy = { copyResultValue(subscription.username) }
                                ),
                                CapsuleItem(
                                    icon = Icons.Default.Key,
                                    label = "كلمة المرور / Password",
                                    value = subscription.password,
                                    onCopy = { copyResultValue(subscription.password) }
                                )
                            )
                        )
                    }

                    // Frame 2: Created / Expiry dates. Titles right, results left.
                    Item {
                        ResultSideBySideStacked(
                            items = listOf(
                                CapsuleItem(
                                    icon = Icons.Default.CalendarToday,
                                    label = lang.lCreated,
                                    value = subscription.created
                                ),
                                CapsuleItem(
                                    icon = Icons.Default.CalendarMonth,
                                    label = lang.lExpiry,
                                    value = subscription.expiry
                                )
                            )
                        )
                    }

                    // Frame 3: Status + Trial kept in its previous horizontal layout.
                    Item {
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
                                Text(
                                    text = if (subscription.isTrial) lang.yes else lang.no,
                                    color = Color.White,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 14.sp
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                    }

                    // Frame 4: Active connections / Max connections. Titles right,
                    // results left, with no copy icons.
                    Item {
                        ResultSideBySideStacked(
                            items = listOf(
                                CapsuleItem(
                                    icon = Icons.Default.Devices,
                                    label = lang.lDevices,
                                    value = subscription.activeCons
                                ),
                                CapsuleItem(
                                    icon = Icons.Default.Groups,
                                    label = lang.lMaxCons,
                                    value = subscription.maxCons
                                )
                            )
                        )
                    }

                    // Frame 5: Content counts (Channels | Movies | Series)
                    Item {
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
                }
            }

            // ─── Action row: Save (hidden for restored items), M3U, Export Image ───
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Save button — hidden when viewing a restored history item (matches HTML)
                if (!isFromHistory) {
                    Box(modifier = Modifier.weight(1.5f)) {
                        AlHosanMainButton(
                            text = lang.btnS,
                            icon = Icons.Default.Save,
                            onClick = { viewModel.saveToHistory() }
                        )
                    }
                }

                // M3U button — copy M3U link
                Box(modifier = Modifier.weight(1f)) {
                    AlHosanMainButton(
                        text = lang.btnM3U,
                        icon = Icons.Default.Link,
                        onClick = {
                            val m3uLink = viewModel.generateM3uLink()
                            if (copyToClipboard(context, m3uLink)) {
                                viewModel.showToast(lang.tCopiedM3U)
                            }
                        },
                        isSubButton = true
                    )
                }

                // Export as image button — renders the subscription data to a PNG via
                // ResultImageRenderer (Android Canvas) and saves it to the gallery.
                Box(modifier = Modifier.weight(if (isFromHistory) 1.5f else 1f)) {
                    AlHosanMainButton(
                        text = lang.btnE,
                        icon = Icons.Default.PhotoCamera,
                        onClick = {
                            val sub = subscription ?: return@AlHosanMainButton
                            exportResultImage(sub)
                        },
                        isSubButton = true
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
        }
        } // end outer scrollable Column

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
}

/**
 * Copy value to clipboard - matching HTML reference's copyVal() function
 */
private fun copyToClipboard(context: Context, text: String): Boolean {
    if (text == "--" || text == "N/A" || text.isBlank()) return false
    return try {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("AlHosan", text))
        true
    } catch (_: Exception) {
        false
    }
}
