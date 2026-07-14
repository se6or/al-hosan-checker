package com.alhosan.checker.ui.components

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.alhosan.checker.BuildConfig
import com.alhosan.checker.data.model.AppLang
import com.alhosan.checker.ui.i18n.*
import com.alhosan.checker.ui.theme.BorderGold
import com.alhosan.checker.ui.theme.CardBg
import com.alhosan.checker.ui.theme.Gold
import com.alhosan.checker.ui.theme.GreenActive
import com.alhosan.checker.ui.theme.RedInactive
import com.alhosan.checker.ui.theme.TextDim
import com.alhosan.checker.util.AppUpdater
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Shown on LoginScreen startup when a new release exists on GitHub.
 *
 * States:
 *   - checking: silent LaunchedEffect
 *   - idle:     no update, nothing shown
 *   - prompt:   update available, ask user
 *   - denied:   cannot install until REQUEST_INSTALL_PACKAGES is granted
 *   - progress: downloading, show LinearProgressIndicator
 *   - done:     download finished, system install UI will open via receiver
 */
@Composable
fun InAppUpdateGate(lang: AppLang) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    var update by remember { mutableStateOf<AppUpdater.UpdateInfo?>(null) }
    var phase by remember { mutableStateOf(Phase.CHECKING) }
    var progress by remember { mutableFloatStateOf(0f) }
    var downloadId by remember { mutableStateOf(-1L) }

    DisposableEffect(Unit) {
        val receiver = AppUpdater.registerDownloadReceiver(ctx)
        onDispose { runCatching { ctx.unregisterReceiver(receiver) } }
    }

    // Register for the "allow install unknown sources" permission result.
    // On Android 8+ (API 26+) we must hold REQUEST_INSTALL_PACKAGES before
    // launching the package installer. If the user permanently denied, open
    // the app's "install unknown apps" settings page.
    val installPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        val info = update ?: return@rememberLauncherForActivityResult
        if (granted) {
            val id = AppUpdater.startDownload(ctx, info)
            downloadId = id
            phase = Phase.PROGRESS
        } else {
            // User declined at least once — take them to system settings
            // so they can toggle it explicitly.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val intent = Intent(
                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:${ctx.packageName}")
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                runCatching { ctx.startActivity(intent) }
            }
            phase = Phase.DENIED
        }
    }

    LaunchedEffect(Unit) {
        val info = AppUpdater.checkForUpdate(ctx) ?: return@LaunchedEffect
        update = info
        phase = Phase.PROMPT
    }

    // Poll progress while downloading.
    LaunchedEffect(downloadId) {
        if (downloadId == -1L) return@LaunchedEffect
        while (true) {
            val st = AppUpdater.downloadStatus(ctx, downloadId)
            if (st.isSuccessful) {
                // Receiver will fire the install Intent; just close our dialog.
                phase = Phase.DONE
                break
            }
            if (st.isFailed) {
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

    Dialog(
        onDismissRequest = {
            if (phase == Phase.PROMPT) phase = Phase.IDLE
        },
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)
    ) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = CardBg),
            border = androidx.compose.foundation.BorderStroke(1.dp, BorderGold)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = when (phase) {
                        Phase.PROMPT -> lang.updateAvailableTitle
                        Phase.DENIED -> lang.updateInstallPrompt
                        Phase.PROGRESS -> lang.updateDownloading
                        Phase.FAILED -> lang.updateFailed
                        else -> ""
                    },
                    color = Gold,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 18.sp
                )
                Spacer(Modifier.height(14.dp))

                val info = update
                if (info != null && phase == Phase.PROMPT) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(lang.updateCurrentVersion, color = TextDim, fontSize = 12.sp)
                            Text(
                                "v${BuildConfig.VERSION_NAME}",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                        }
                        Text("→", color = Gold, fontSize = 20.sp, fontWeight = FontWeight.Black)
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(lang.updateNewVersion, color = TextDim, fontSize = 12.sp)
                            Text(
                                "v${info.versionName}",
                                color = GreenActive,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 14.sp
                            )
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                }

                if (phase == Phase.PROGRESS) {
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(10.dp)
                            .clip(RoundedCornerShape(6.dp)),
                        color = Gold,
                        trackColor = Color(0xFF222222),
                    )
                    Spacer(Modifier.height(6.dp))
                    Text("${(progress * 100).toInt()}%", color = Color.White, fontSize = 13.sp)
                    Spacer(Modifier.height(10.dp))
                }

                if (phase == Phase.DENIED) {
                    Text(
                        text = if (lang == AppLang.AR)
                            "لازم تسمح للتطبيق بتثبيت التحديثات من الإعدادات حتى يكتمل التحديث."
                        else
                            "You must allow this app to install updates in Settings to continue.",
                        color = TextDim, fontSize = 13.sp
                    )
                    Spacer(Modifier.height(10.dp))
                }

                if (phase == Phase.PROMPT && info != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        TextButton(
                            onClick = {
                                AppUpdater.skipVersion(ctx, info.versionCode)
                                phase = Phase.IDLE
                            },
                            modifier = Modifier
                                .weight(1f)
                                .border(1.dp, BorderGold, RoundedCornerShape(14.dp))
                                .padding(vertical = 2.dp)
                        ) {
                            Text(lang.updateSkip, color = TextDim, fontSize = 12.sp)
                        }
                        TextButton(
                            onClick = {
                                AppUpdater.skipVersion(ctx, info.versionCode) // won't nag again this session
                                phase = Phase.IDLE
                            },
                            modifier = Modifier
                                .weight(1f)
                                .border(1.dp, BorderGold, RoundedCornerShape(14.dp))
                                .padding(vertical = 2.dp)
                        ) {
                            Text(lang.updateLater, color = Color.White, fontSize = 13.sp)
                        }
                        Box(
                            modifier = Modifier
                                .weight(1.3f)
                                .clip(RoundedCornerShape(14.dp))
                                .background(Gold),
                            contentAlignment = Alignment.Center
                        ) {
                            TextButton(onClick = {
                                scope.launch {
                                    // On Android 8+ (API 26+) we must hold
                                    // REQUEST_INSTALL_PACKAGES before installing.
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                        val pm = ctx.packageManager
                                        @Suppress("DEPRECATION")
                                        if (!pm.canRequestPackageInstalls()) {
                                            installPermLauncher.launch(
                                                Manifest.permission.REQUEST_INSTALL_PACKAGES
                                            )
                                            return@launch
                                        }
                                    }
                                    val id = withContext(Dispatchers.IO) {
                                        AppUpdater.startDownload(ctx, info)
                                    }
                                    downloadId = id
                                    phase = Phase.PROGRESS
                                }
                            }) {
                                Text(
                                    lang.updateNow,
                                    color = Color.Black,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 13.sp
                                )
                            }
                        }
                    }
                }

                if (phase == Phase.FAILED || phase == Phase.DENIED) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        TextButton(
                            onClick = { phase = Phase.IDLE },
                            modifier = Modifier
                                .weight(1f)
                                .border(1.dp, BorderGold, RoundedCornerShape(14.dp))
                        ) {
                            Text(lang.updateLater, color = Color.White)
                        }
                        val retryLabel = when (phase) {
                            Phase.DENIED -> if (lang == AppLang.AR) "فتح الإعدادات" else "Open Settings"
                            else -> if (lang == AppLang.AR) "إعادة المحاولة" else "Retry"
                        }
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(14.dp))
                                .background(if (phase == Phase.DENIED) Gold else RedInactive),
                            contentAlignment = Alignment.Center
                        ) {
                            TextButton(onClick = {
                                val info2 = update ?: return@TextButton
                                if (phase == Phase.DENIED) {
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                        val intent = Intent(
                                            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                                            Uri.parse("package:${ctx.packageName}")
                                        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        runCatching { ctx.startActivity(intent) }
                                    }
                                    // Try install perm request directly too.
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                        installPermLauncher.launch(
                                            Manifest.permission.REQUEST_INSTALL_PACKAGES
                                        )
                                    }
                                } else {
                                    val id = AppUpdater.startDownload(ctx, info2)
                                    downloadId = id
                                    phase = Phase.PROGRESS
                                }
                            }) {
                                Text(
                                    retryLabel,
                                    color = if (phase == Phase.DENIED) Color.Black else Color.White,
                                    fontWeight = FontWeight.ExtraBold
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private enum class Phase { CHECKING, IDLE, PROMPT, DENIED, PROGRESS, FAILED, DONE }
