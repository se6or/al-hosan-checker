package com.alhosan.checker.ui.screens

import android.Manifest
import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Build
import android.view.View
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
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
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.SignalCellularAlt
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.alhosan.checker.data.model.AppLang
import com.alhosan.checker.data.model.CheckerState
import com.alhosan.checker.data.model.Subscription
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.material.icons.filled.Check
import com.alhosan.checker.ui.components.AlHosanMainButton
import com.alhosan.checker.ui.components.AlHosanToast
import com.alhosan.checker.ui.components.CapsuleItem
import com.alhosan.checker.ui.components.ContentCountDisplay
import com.alhosan.checker.ui.components.ResultPrimaryInfoStacked
import com.alhosan.checker.ui.components.ResultSideBySideStacked
import com.alhosan.checker.ui.components.ShinyText
import com.alhosan.checker.ui.components.StaggeredColumn
import com.alhosan.checker.ui.components.StatusBadge
import com.alhosan.checker.ui.components.alHosanStaggeredEnter
import com.alhosan.checker.ui.components.alHosanStaggeredExit
import com.alhosan.checker.ui.theme.AlHosanTheme
import com.alhosan.checker.ui.theme.Black
import com.alhosan.checker.ui.theme.BorderGold
import com.alhosan.checker.ui.theme.Gold
import com.alhosan.checker.util.ImageExporter
import com.alhosan.checker.viewmodel.CheckerViewModel
import com.alhosan.checker.ui.i18n.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Result screen - matching HTML reference's #scr-result
 * Features: capsule-style cards, copy buttons, status badge, M3U generation, save, export-as-image
 *
 * The export-as-image button renders the exact result capture zone with Compose
 * into a Bitmap (screenshot-style, excluding header/action buttons) and saves
 * the PNG to Pictures/AlHosan via MediaStore.
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
    val isChecking by viewModel.isChecking.collectAsState()
    // Keep the last successful subscription alive so the screen doesn't go
    // black during Refresh (the check flow replaces state with Loading, but
    // we want to stay on the result screen and just show the spinner on the
    // Refresh button).
    val liveSub = (state as? CheckerState.Success)?.subscription
    var displayedSub by remember { mutableStateOf<Subscription?>(null) }
    LaunchedEffect(liveSub) { if (liveSub != null) displayedSub = liveSub }
    val subscription = displayedSub
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Handle system back button / gesture — pop back to the previous screen
    // (history if we came from history, login if we came from login).
    BackHandler(enabled = true) { onBack() }

    if (subscription == null) {
        return
    }

    val iconAtRight = lang == AppLang.AR

    val copyResultValue: (String) -> Unit = { value ->
        if (copyToClipboard(context, value)) {
            viewModel.showToast(lang.tCopied)
        }
    }

    var isCapturingImage by remember { mutableStateOf(false) }

    fun saveResultImage(sub: Subscription) {
        if (isCapturingImage) return
        scope.launch {
            isCapturingImage = true
            playSaveChime(context)
            delay(120)

            val success = try {
                val bitmap = captureResultContentBitmap(
                    context = context,
                    subscription = sub,
                    lang = lang,
                    isCounting = false
                )
                try {
                    withContext(Dispatchers.IO) {
                        ImageExporter.saveBitmapToGallery(context, bitmap, sub.username)
                    }
                } finally {
                    bitmap.recycle()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                false
            } finally {
                delay(180)
                isCapturingImage = false
            }
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
                .then(if (isChecking) Modifier.blur(12.dp) else Modifier)
        ) {
            // ── Card content ──
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, top = 0.dp, bottom = 16.dp)
            ) {
            // ─── Capture zone (matches HTML #capture-zone) ───
            ResultCaptureContent(
                subscription = subscription,
                lang = lang,
                isCounting = isCounting,
                iconAtRight = iconAtRight,
                animate = true,
                showCopyButtons = true,
                centerPrimaryInfo = false,
                onCopyValue = copyResultValue
            )

            // ─── Action row: Save / Refresh + M3U + Export ───
            // Uses Arrangement.spacedBy with small gap and equal-ish weights so
            // the row fits within the card's horizontal padding (no overflow).
            // Wrapped in its own AnimatedVisibility with the SAME slide+fade
            // ─── Action row: Save / Refresh + M3U + Export ───
            // Uses Arrangement.spacedBy with small gap and equal-ish weights so
            // the row fits within the card's horizontal padding (no overflow).
            // Always directly composed (no AnimatedVisibility indirection) —
            // guarantees the buttons are never accidentally left unclickable
            // by a stuck animation state.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Primary button: Save for fresh checks, Refresh for restored items
                Box(modifier = Modifier.weight(1f)) {
                    if (!isFromHistory) {
                        AlHosanMainButton(
                            text = lang.btnS,
                            icon = Icons.Default.Save,
                            onClick = { viewModel.saveToHistory() }
                        )
                    } else {
                        AlHosanMainButton(
                            text = if (lang == AppLang.AR) "تحديث" else "Refresh",
                            icon = Icons.Default.Refresh,
                            onClick = { viewModel.refreshFromHistory() },
                            isLoading = isChecking,
                            modifier = if (isChecking) Modifier.graphicsLayer { alpha = 0.4f } else Modifier
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

                // Export as image button
                Box(modifier = Modifier.weight(1f)) {
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

        // ─── Refresh overlay: transparent logo + shiny text, sits ABOVE the
        // blurred content (sibling, not a child) so it stays perfectly sharp
        // while everything behind it (card, buttons, header) blurs out. ───
        var wasChecking by remember { mutableStateOf(false) }
        var showUpdateSuccess by remember { mutableStateOf(false) }
        LaunchedEffect(isChecking) {
            if (isChecking) {
                wasChecking = true
            } else if (wasChecking) {
                wasChecking = false
                // CRITICAL FIX: only show the success checkmark if the
                // refresh actually succeeded. Previously this fired
                // unconditionally the instant isChecking flipped false —
                // meaning a FAILED refresh (bad response, network error,
                // server down) still showed "تم التحديث" as if it worked,
                // silently hiding real failures from the user.
                if (state is CheckerState.Success) {
                    showUpdateSuccess = true
                    delay(900)
                    showUpdateSuccess = false
                }
                // On failure: no checkmark, no success claim — the overlay
                // just fades away (via the AnimatedVisibility exit below)
                // and the screen falls back to showing the error state
                // underneath, same as any other failed check.
            }
        }
        AnimatedVisibility(
            visible = isChecking || showUpdateSuccess,
            enter = alHosanStaggeredEnter(durationMs = 300),
            exit = alHosanStaggeredExit(durationMs = 260),
            modifier = Modifier.fillMaxSize()
        ) {
            RefreshOverlay(lang = lang, showSuccess = showUpdateSuccess)
        }

        // ─── Modern capture effect while saving image: soft flash + an
        // expanding ring pulse from the center, instead of a flat white
        // flash — synced with the new save chime. ───
        AnimatedVisibility(
            visible = isCapturingImage,
            enter = fadeIn(animationSpec = tween(70)),
            exit = fadeOut(animationSpec = tween(320, easing = FastOutSlowInEasing)),
            modifier = Modifier.fillMaxSize()
        ) {
            val ringProgress = remember { Animatable(0f) }
            LaunchedEffect(Unit) {
                ringProgress.animateTo(1f, tween(520, easing = FastOutSlowInEasing))
            }
            Box(modifier = Modifier.fillMaxSize()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.White.copy(alpha = 0.22f))
                )
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val maxRadius = size.maxDimension * 0.55f
                    val radius = maxRadius * ringProgress.value
                    drawCircle(
                        color = Color.White.copy(alpha = (1f - ringProgress.value) * 0.9f),
                        radius = radius,
                        center = center,
                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3.dp.toPx())
                    )
                }
            }
        }

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
 * Fullscreen overlay shown while refreshing a saved subscription from the
 * result screen. Same visual language as the login screen's checking
 * overlay: transparent logo + shiny gold text, no backdrop of its own —
 * the blur comes from the sibling content behind it.
 *
 * Shows "جارِ التحديث" while the refresh is running, then briefly swaps to
 * a green checkmark + "تم التحديث" for ~900ms once it completes.
 */
@Composable
private fun RefreshOverlay(lang: AppLang, showSuccess: Boolean) {
    val ctx = LocalContext.current
    Box(
        modifier = Modifier
            .fillMaxSize()
            .then(
                if (!showSuccess) Modifier.pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent()
                            event.changes.forEach { it.consume() }
                        }
                    }
                } else Modifier
            ),
        contentAlignment = Alignment.Center
    ) {
        // Crossfade + pop between the spinner content and the success
        // checkmark — previously this was a plain `if/else` swap with no
        // transition at all, so "تم التحديث" just snapped into existence.
        androidx.compose.animation.AnimatedContent(
            targetState = showSuccess,
            transitionSpec = {
                (fadeIn(tween(260)) + scaleIn(initialScale = 0.8f, animationSpec = tween(260)))
                    .togetherWith(fadeOut(tween(180)) + scaleOut(targetScale = 0.85f, animationSpec = tween(180)))
            },
            label = "refresh-overlay-state"
        ) { success ->
            if (success) {
                // Play the confirmation chime the instant this content enters —
                // synced with the checkmark's draw-in animation below.
                LaunchedEffect(Unit) { playSuccessChime(ctx) }

                val checkProgress = remember { Animatable(0f) }
                val popScale = remember { Animatable(0.6f) }
                LaunchedEffect(Unit) {
                    launch { popScale.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy)) }
                    checkProgress.animateTo(1f, tween(420, easing = FastOutSlowInEasing))
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .graphicsLayer {
                                scaleX = popScale.value
                                scaleY = popScale.value
                            }
                            .background(Color(0xFF2ECC71), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        // Checkmark "fills in" as a stroke draw-in, not a static
                        // icon — synced with the chime above.
                        Canvas(modifier = Modifier.size(34.dp)) {
                            val w = size.width
                            val h = size.height
                            val p1 = Offset(w * 0.20f, h * 0.55f)
                            val p2 = Offset(w * 0.42f, h * 0.76f)
                            val p3 = Offset(w * 0.82f, h * 0.28f)
                            val firstLegLen = (p2 - p1).getDistance()
                            val secondLegLen = (p3 - p2).getDistance()
                            val totalLen = firstLegLen + secondLegLen
                            val drawn = checkProgress.value * totalLen

                            val path = Path()
                            path.moveTo(p1.x, p1.y)
                            if (drawn <= firstLegLen) {
                                val t = if (firstLegLen == 0f) 0f else drawn / firstLegLen
                                path.lineTo(
                                    p1.x + (p2.x - p1.x) * t,
                                    p1.y + (p2.y - p1.y) * t
                                )
                            } else {
                                path.lineTo(p2.x, p2.y)
                                val remaining = drawn - firstLegLen
                                val t = if (secondLegLen == 0f) 0f else (remaining / secondLegLen).coerceIn(0f, 1f)
                                path.lineTo(
                                    p2.x + (p3.x - p2.x) * t,
                                    p2.y + (p3.y - p2.y) * t
                                )
                            }
                            drawPath(
                                path = path,
                                color = Color.White,
                                style = androidx.compose.ui.graphics.drawscope.Stroke(
                                    width = 4.5.dp.toPx(),
                                    cap = StrokeCap.Round,
                                    join = StrokeJoin.Round
                                )
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = if (lang == AppLang.AR) "تم التحديث" else "Updated",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            } else {
                com.alhosan.checker.ui.components.StaggeredColumn(perItemDelayMs = 70) {
                    Item {
                        ShinyLogo(
                            modifier = Modifier.size(88.dp)
                        )
                    }
                    Item {
                        Spacer(modifier = Modifier.height(18.dp))
                        ShinyText(
                            text = if (lang == AppLang.AR) "جارِ التحديث" else "Updating",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.ExtraBold,
                            rtl = lang == AppLang.AR,
                            durationMs = 1800
                        )
                    }
                }
            }
        }
    }
}

/**
 * A short, pleasant ascending two-tone confirmation chime — synthesized with
 * ToneGenerator, not a bundled audio file. Apple's App-Store download-complete
 * sound is a proprietary asset we can't legally bundle or reproduce; this is
 * an original two-note "success" chime in the same spirit (bright, quick,
 * ascending) rather than a copy of it.
 */
private fun playSuccessChime(context: android.content.Context) {
    try {
        val tg = android.media.ToneGenerator(android.media.AudioManager.STREAM_NOTIFICATION, 85)
        tg.startTone(android.media.ToneGenerator.TONE_PROP_BEEP2, 90)
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            tg.startTone(android.media.ToneGenerator.TONE_PROP_ACK, 140)
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({ tg.release() }, 220)
        }, 90)
    } catch (_: Exception) {
        // Silent fallback — never crash the update flow over a missing chime.
    }
}

/**
 * A short, modern single-tone "saved" chime — lighter and quicker than the
 * update-success chime (playSuccessChime), since this fires on every image
 * export rather than a rarer completion event. Replaces the old dated
 * MediaActionSound.SHUTTER_CLICK camera-shutter sound.
 */
private fun playSaveChime(context: android.content.Context) {
    try {
        val tg = android.media.ToneGenerator(android.media.AudioManager.STREAM_NOTIFICATION, 80)
        tg.startTone(android.media.ToneGenerator.TONE_PROP_BEEP2, 110)
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({ tg.release() }, 200)
    } catch (_: Exception) {
        // Silent fallback — never crash the export flow over a missing chime.
    }
}


private suspend fun captureResultContentBitmap(
    context: Context,
    subscription: Subscription,
    lang: AppLang,
    isCounting: Boolean
): Bitmap = withContext(Dispatchers.Main) {
    val activity = context.findActivity()
        ?: throw IllegalStateException("Activity context is required for capture")
    val decor = activity.window.decorView as FrameLayout
    val density = context.resources.displayMetrics.density
    val screenWidth = decor.width.takeIf { it > 0 } ?: context.resources.displayMetrics.widthPixels
    val captureWidth = (screenWidth - (32f * density).toInt()).coerceAtLeast(1)
    val iconAtRight = lang == AppLang.AR

    val offscreenLeft = -captureWidth * 4
    val composeView = ComposeView(activity).apply {
        setBackgroundColor(android.graphics.Color.BLACK)
        // Keep this temporary capture view completely outside the visible
        // window. The previous code manually laid it out at (0, 0), so users
        // could see the generated image flash over the app for a split second.
        translationX = offscreenLeft.toFloat()
        setContent {
            AlHosanTheme {
                ResultCaptureContent(
                    subscription = subscription,
                    lang = lang,
                    isCounting = isCounting,
                    iconAtRight = iconAtRight,
                    animate = false,
                    showCopyButtons = false,
                    centerPrimaryInfo = true,
                    onCopyValue = {}
                )
            }
        }
    }

    val params = FrameLayout.LayoutParams(captureWidth, FrameLayout.LayoutParams.WRAP_CONTENT).apply {
        leftMargin = offscreenLeft
        topMargin = 0
    }

    try {
        decor.addView(composeView, params)
        // Give Compose a short moment to complete first composition/layout.
        delay(120)

        composeView.measure(
            View.MeasureSpec.makeMeasureSpec(captureWidth, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        )
        val measuredHeight = composeView.measuredHeight.coerceAtLeast(1)
        composeView.layout(offscreenLeft, 0, offscreenLeft + captureWidth, measuredHeight)

        Bitmap.createBitmap(captureWidth, measuredHeight, Bitmap.Config.ARGB_8888).also { bitmap ->
            val canvas = Canvas(bitmap)
            canvas.drawColor(android.graphics.Color.BLACK)
            composeView.draw(canvas)
        }
    } finally {
        try { decor.removeView(composeView) } catch (_: Exception) {}
        composeView.disposeComposition()
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}


@Composable
private fun ResultCaptureContent(
    subscription: Subscription,
    lang: AppLang,
    isCounting: Boolean,
    iconAtRight: Boolean,
    animate: Boolean,
    showCopyButtons: Boolean,
    centerPrimaryInfo: Boolean,
    onCopyValue: (String) -> Unit
) {
    val primary: @Composable () -> Unit = {
        ResultPrimaryInfoStacked(
            items = listOf(
                CapsuleItem(
                    icon = Icons.Default.SignalCellularAlt,
                    label = lang.lHost,
                    value = subscription.host,
                    onCopy = if (showCopyButtons) ({ onCopyValue(subscription.host) }) else null
                ),
                CapsuleItem(
                    icon = Icons.Default.Groups,
                    label = lang.lUser,
                    value = subscription.username,
                    onCopy = if (showCopyButtons) ({ onCopyValue(subscription.username) }) else null
                ),
                CapsuleItem(
                    icon = Icons.Default.Key,
                    label = lang.lPass,
                    value = subscription.password,
                    onCopy = if (showCopyButtons) ({ onCopyValue(subscription.password) }) else null
                )
            ),
            iconAtRight = iconAtRight,
            centerContent = centerPrimaryInfo
        )
    }

    val dates: @Composable () -> Unit = {
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
            ),
            iconAtRight = iconAtRight
        )
    }

    val statusTrial: @Composable () -> Unit = {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
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
                if (iconAtRight) {
                    // Arabic: left group is arranged so the visual start from the
                    // right is icon → title → result.
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = if (subscription.isTrial) lang.yes else lang.no,
                            color = Color.White,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 14.sp
                        )
                        Text(
                            text = lang.lTrial,
                            color = Color(0xFFA0A0A0),
                            fontSize = 13.sp
                        )
                        Icon(
                            imageVector = Icons.Default.Science,
                            contentDescription = null,
                            tint = Gold,
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        StatusBadge(
                            isActive = subscription.isActive,
                            text = if (subscription.isActive) lang.on else lang.off
                        )
                        Text(
                            text = lang.lStatus,
                            color = Color(0xFFA0A0A0),
                            fontSize = 13.sp
                        )
                        Icon(
                            imageVector = Icons.Default.SignalCellularAlt,
                            contentDescription = null,
                            tint = Gold,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                } else {
                    // English: normal LTR direction — icon → title → result.
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
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
    }

    val devices: @Composable () -> Unit = {
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
            ),
            iconAtRight = iconAtRight
        )
    }

    val contentCounts: @Composable () -> Unit = {
        CompositionLocalProvider(
            LocalLayoutDirection provides if (iconAtRight) LayoutDirection.Rtl else LayoutDirection.Ltr
        ) {
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

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Black, RoundedCornerShape(28.dp))
            .padding(16.dp)
    ) {
        if (animate) {
            StaggeredColumn(perItemDelayMs = 45) {
                Item { primary() }
                Item { dates() }
                Item { statusTrial() }
                Item { devices() }
                Item { contentCounts() }
            }
        } else {
            Column(modifier = Modifier.fillMaxWidth()) {
                primary()
                dates()
                statusTrial()
                devices()
                contentCounts()
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
