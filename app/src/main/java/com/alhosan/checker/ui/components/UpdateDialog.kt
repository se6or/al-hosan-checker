package com.alhosan.checker.ui.components

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.alhosan.checker.data.model.AppLang
import com.alhosan.checker.ui.i18n.*
import com.alhosan.checker.ui.theme.BorderGold
import com.alhosan.checker.ui.theme.Gold
import com.alhosan.checker.ui.theme.GoldLight
import com.alhosan.checker.ui.theme.GreenActive
import com.alhosan.checker.ui.theme.RedInactive
import com.alhosan.checker.ui.theme.TextDim
import com.alhosan.checker.util.AppUpdater
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * In-app update gate shown on LoginScreen startup when a new release exists on
 * GitHub. Handles prompting, permission, download progress, failure/retry,
 * and handing-off to the system installer.
 */
@Composable
fun InAppUpdateGate(lang: AppLang) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    val installedVersion = remember(ctx) {
        try {
            ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName?.trimStart('v', 'V').orEmpty()
        } catch (_: Exception) { "" }
    }

    var update by remember { mutableStateOf<AppUpdater.UpdateInfo?>(null) }
    var phase by remember { mutableStateOf(Phase.CHECKING) }
    var progress by remember { mutableFloatStateOf(0f) }
    var downloadId by remember { mutableStateOf(-1L) }
    var errorMessage by remember { mutableStateOf("") }

    // Register download-complete receiver once.
    DisposableEffect(Unit) {
        val receiver = AppUpdater.registerDownloadReceiver(ctx)
        onDispose { runCatching { ctx.unregisterReceiver(receiver) } }
    }

    // "Allow install unknown apps" permission result.
    val installPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            // If we already downloaded the APK and were waiting for perm, trigger install now.
            val id = downloadId
            if (id != -1L) {
                val st = AppUpdater.downloadStatus(ctx, id)
                if (st.isSuccessful) {
                    AppUpdater.installDownloadedApk(ctx, id)
                    phase = Phase.DONE
                    return@rememberLauncherForActivityResult
                }
            }
            // Otherwise permission was granted before download started -> nothing to do.
        } else {
            // Don't fail — the BroadcastReceiver will also fire the install
            // intent which will prompt the user; we just note that the direct
            // launch was denied.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                runCatching {
                    val intent = Intent(
                        Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        Uri.parse("package:${ctx.packageName}")
                    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    ctx.startActivity(intent)
                }
            }
        }
    }

    // Check for update on launch.
    LaunchedEffect(Unit) {
        val info = AppUpdater.checkForUpdate(ctx) ?: return@LaunchedEffect
        update = info
        phase = Phase.PROMPT
    }

    // Poll progress while downloading.
    LaunchedEffect(downloadId) {
        if (downloadId == -1L) return@LaunchedEffect
        var completedHandled = false
        while (true) {
            val st = AppUpdater.downloadStatus(ctx, downloadId)
            if (st.isSuccessful) {
                if (!completedHandled) {
                    completedHandled = true
                    // Install permission check on API 26+; if already granted,
                    // install immediately; otherwise request, receiver fires
                    // install intent anyway after user grants.
                    val canInstall = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        @Suppress("DEPRECATION")
                        ctx.packageManager.canRequestPackageInstalls()
                    } else true
                    if (canInstall) {
                        AppUpdater.installDownloadedApk(ctx, downloadId)
                        phase = Phase.DONE
                    } else {
                        installPermLauncher.launch(Manifest.permission.REQUEST_INSTALL_PACKAGES)
                        phase = Phase.DENIED
                    }
                }
                break
            }
            if (st.isFailed) {
                errorMessage = when (st.reason) {
                    1006 -> if (lang == AppLang.AR) "لا يوجد اتصال بالإنترنت" else "No network connection"
                    else -> if (lang == AppLang.AR) "فشل التحميل، حاول مرة أخرى" else "Download failed, try again"
                }
                phase = Phase.FAILED
                break
            }
            if (st.totalBytes > 0) {
                progress = (st.bytesDownloaded.toFloat() / st.totalBytes.toFloat()).coerceIn(0f, 1f)
            }
            delay(250)
        }
    }

    if (phase == Phase.CHECKING || phase == Phase.IDLE || phase == Phase.DONE) return

    val animateEnter = fadeIn(animationSpec = tween(280)) +
        scaleIn(initialScale = 0.88f, animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow))
    val animateExit = fadeOut(animationSpec = tween(180)) + scaleOut(targetScale = 0.95f, animationSpec = tween(180))

    val progressAnim by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(220),
        label = "dl-prog"
    )

    Dialog(
        onDismissRequest = {
            if (phase == Phase.PROMPT || phase == Phase.FAILED) phase = Phase.IDLE
        },
        properties = DialogProperties(
            dismissOnBackPress = phase == Phase.PROMPT || phase == Phase.FAILED,
            dismissOnClickOutside = phase == Phase.PROMPT || phase == Phase.FAILED,
            usePlatformDefaultWidth = false
        )
    ) {
        AnimatedVisibility(
            visible = true,
            enter = animateEnter,
            exit = animateExit
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.92f)
                    .padding(horizontal = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                // Outer glow/border
                Card(
                    shape = RoundedCornerShape(26.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF141414)),
                    border = androidx.compose.foundation.BorderStroke(1.2f.dp, BorderGold.copy(alpha = 0.7f)),
                    elevation = CardDefaults.cardElevation(0.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        Color(0xFF1C1810),
                                        Color(0xFF0E0E0E)
                                    )
                                )
                            )
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 22.dp, vertical = 22.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            // Title
                            Text(
                                text = when (phase) {
                                    Phase.PROMPT, Phase.DENIED -> lang.updateAvailableTitle
                                    Phase.PROGRESS -> lang.updateDownloading
                                    Phase.FAILED -> lang.updateFailed
                                    else -> ""
                                },
                                color = Gold,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 20.sp,
                                letterSpacing = 0.3.sp
                            )
                            Spacer(Modifier.height(6.dp))

                            // Thin gold divider
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(0.35f)
                                    .height(2.dp)
                                    .clip(CircleShape)
                                    .background(
                                        Brush.horizontalGradient(
                                            colors = listOf(Color.Transparent, Gold, Color.Transparent)
                                        )
                                    )
                            )
                            Spacer(Modifier.height(18.dp))

                            val info = update
                            if (info != null && (phase == Phase.PROMPT || phase == Phase.DENIED)) {
                                // Version compare cards
                                val currentLayoutDir = LocalLayoutDirection.current
                                val arrowRotation = if (currentLayoutDir == androidx.compose.ui.unit.LayoutDirection.Rtl) 180f else 0f
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceEvenly
                                ) {
                                    VersionChip(
                                        label = lang.updateCurrentVersion,
                                        version = installedVersion.ifBlank { "1.0.1" },
                                        accent = TextDim,
                                        valueColor = Color.White
                                    )
                                    Box(
                                        modifier = Modifier
                                            .padding(horizontal = 8.dp)
                                            .graphicsLayer { rotationZ = arrowRotation }
                                    ) {
                                        Text(
                                            "➜",
                                            color = Gold,
                                            fontSize = 22.sp,
                                            fontWeight = FontWeight.Black
                                        )
                                    }
                                    VersionChip(
                                        label = lang.updateNewVersion,
                                        version = info.versionName,
                                        accent = GreenActive.copy(alpha = 0.8f),
                                        valueColor = GreenActive,
                                        highlight = true
                                    )
                                }
                                Spacer(Modifier.height(20.dp))
                            }

                            if (phase == Phase.PROGRESS) {
                                Text(
                                    text = if (lang == AppLang.AR) "جاري تحميل التحديث، لا تغلق التطبيق" else "Downloading update, please wait",
                                    color = TextDim,
                                    fontSize = 13.sp
                                )
                                Spacer(Modifier.height(14.dp))
                                // Fancy progress track
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(12.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFF222222))
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth(progressAnim)
                                            .height(12.dp)
                                            .clip(CircleShape)
                                            .background(
                                                Brush.horizontalGradient(
                                                    colors = listOf(Gold, GoldLight)
                                                )
                                            )
                                    )
                                }
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    "${(progressAnim * 100).toInt()}%",
                                    color = Gold,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 15.sp
                                )
                                Spacer(Modifier.height(10.dp))
                            }

                            if (phase == Phase.DENIED) {
                                Text(
                                    text = if (lang == AppLang.AR)
                                        "لازم تسمح للتطبيق بتثبيت التحديثات من الإعدادات حتى يكتمل التثبيت."
                                    else
                                        "Allow this app to install updates in Settings to finish installing.",
                                    color = TextDim,
                                    fontSize = 13.sp,
                                    textAlign = TextAlign.Center,
                                    lineHeight = 19.sp
                                )
                                Spacer(Modifier.height(12.dp))
                            }

                            if (phase == Phase.FAILED) {
                                Text(
                                    text = errorMessage.ifBlank {
                                        if (lang == AppLang.AR) "فشل تحميل التحديث" else "Download failed"
                                    },
                                    color = RedInactive,
                                    fontSize = 13.sp,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(Modifier.height(12.dp))
                            }

                            when (phase) {
                                Phase.PROMPT -> {
                                    if (info != null) {
                                        // Three buttons: Skip / Later / Update
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            ActionButton(
                                                text = lang.updateSkip,
                                                modifier = Modifier.weight(1f),
                                                outlined = true,
                                                textColor = TextDim,
                                            ) {
                                                AppUpdater.skipVersion(ctx, info.versionCode)
                                                phase = Phase.IDLE
                                            }
                                            ActionButton(
                                                text = lang.updateLater,
                                                modifier = Modifier.weight(1f),
                                                outlined = true,
                                                textColor = Color.White,
                                            ) {
                                                phase = Phase.IDLE
                                            }
                                            ActionButton(
                                                text = lang.updateNow,
                                                modifier = Modifier.weight(1.35f),
                                                filled = true,
                                                textColor = Color.Black,
                                            ) {
                                                // Start download immediately — permission
                                                // is only needed at install time, not at
                                                // download time, so no need to risk an
                                                // exit/crash before the download starts.
                                                scope.launch {
                                                    val id = withContext(Dispatchers.IO) {
                                                        runCatching { AppUpdater.startDownload(ctx, info) }
                                                            .getOrElse { -1L }
                                                    }
                                                    if (id == -1L) {
                                                        errorMessage = if (lang == AppLang.AR)
                                                            "تعذر بدء التحميل" else "Could not start download"
                                                        phase = Phase.FAILED
                                                    } else {
                                                        downloadId = id
                                                        phase = Phase.PROGRESS
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }

                                Phase.DENIED, Phase.FAILED -> {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        ActionButton(
                                            text = lang.updateLater,
                                            modifier = Modifier.weight(1f),
                                            outlined = true,
                                            textColor = Color.White,
                                        ) { phase = Phase.IDLE }
                                        val label = when (phase) {
                                            Phase.DENIED -> if (lang == AppLang.AR) "فتح الإعدادات" else "Open Settings"
                                            else -> if (lang == AppLang.AR) "إعادة المحاولة" else "Retry"
                                        }
                                        ActionButton(
                                            text = label,
                                            modifier = Modifier.weight(1.3f),
                                            filled = true,
                                            fillColor = if (phase == Phase.DENIED) Gold else RedInactive,
                                            textColor = if (phase == Phase.DENIED) Color.Black else Color.White,
                                        ) {
                                            val info2 = update ?: return@ActionButton
                                            if (phase == Phase.DENIED) {
                                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                                    runCatching {
                                                        val intent = Intent(
                                                            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                                                            Uri.parse("package:${ctx.packageName}")
                                                        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                                        ctx.startActivity(intent)
                                                    }
                                                    installPermLauncher.launch(Manifest.permission.REQUEST_INSTALL_PACKAGES)
                                                }
                                            } else {
                                                scope.launch {
                                                    val id = withContext(Dispatchers.IO) {
                                                        runCatching { AppUpdater.startDownload(ctx, info2) }
                                                            .getOrElse { -1L }
                                                    }
                                                    if (id == -1L) {
                                                        errorMessage = if (lang == AppLang.AR)
                                                            "تعذر بدء التحميل" else "Could not start download"
                                                    } else {
                                                        downloadId = id
                                                        phase = Phase.PROGRESS
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }

                                Phase.PROGRESS -> {
                                    // Cancel button (just dismisses dialog, download keeps going in background)
                                    OutlinedButton(
                                        onClick = { phase = Phase.IDLE },
                                        modifier = Modifier.fillMaxWidth(0.6f),
                                        shape = RoundedCornerShape(14.dp),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, BorderGold.copy(alpha = 0.5f)),
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = TextDim)
                                    ) {
                                        Text(
                                            if (lang == AppLang.AR) "تصغير" else "Hide",
                                            fontSize = 13.sp
                                        )
                                    }
                                }

                                else -> Unit
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun VersionChip(
    label: String,
    version: String,
    accent: Color,
    valueColor: Color,
    highlight: Boolean = false,
) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .border(
                1.dp,
                if (highlight) Gold.copy(alpha = 0.7f) else BorderGold.copy(alpha = 0.35f),
                RoundedCornerShape(16.dp)
            )
            .background(if (highlight) Gold.copy(alpha = 0.07f) else Color(0xFF1A1A1A))
            .padding(horizontal = 18.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(label, color = accent, fontSize = 11.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(4.dp))
        Text(
            "v${version.trimStart('v', 'V')}",
            color = valueColor,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 16.sp
        )
    }
}

@Composable
private fun ActionButton(
    text: String,
    modifier: Modifier = Modifier,
    outlined: Boolean = false,
    filled: Boolean = false,
    textColor: Color = Color.White,
    fillColor: Color = Gold,
    onClick: () -> Unit,
) {
    if (filled) {
        Button(
            onClick = onClick,
            modifier = modifier.height(46.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = fillColor,
                contentColor = textColor
            ),
            elevation = ButtonDefaults.buttonElevation(0.dp)
        ) {
            Text(text, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp)
        }
    } else {
        OutlinedButton(
            onClick = onClick,
            modifier = modifier.height(46.dp),
            shape = RoundedCornerShape(14.dp),
            border = androidx.compose.foundation.BorderStroke(
                1.dp,
                if (outlined) BorderGold.copy(alpha = 0.55f) else Color.Transparent
            ),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = textColor)
        ) {
            Text(text, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
        }
    }
}

private enum class Phase { CHECKING, IDLE, PROMPT, DENIED, PROGRESS, FAILED, DONE }
