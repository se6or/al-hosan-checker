package com.alhosan.checker.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.alhosan.checker.data.model.CheckerState
import com.alhosan.checker.data.model.Subscription
import com.alhosan.checker.ui.components.InfoCard
import com.alhosan.checker.ui.theme.Gold
import com.alhosan.checker.viewmodel.CheckerViewModel

/**
 * Result screen - shows subscription details after a successful check.
 * Matches the original Flutter ResultScreen with gold info cards.
 */
@Composable
fun ResultScreen(
    onBack: () -> Unit,
    viewModel: CheckerViewModel = viewModel()
) {
    val state by viewModel.state.collectAsState()
    val subscription = (state as? CheckerState.Success)?.subscription

    if (subscription == null) {
        onBack()
        return
    }

    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        // Top App Bar
        TopAppBar(
            title = {
                Text(
                    text = "تفاصيل الاشتراك",
                    color = Color.White
                )
            },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "رجوع",
                        tint = Gold
                    )
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = Color.Transparent
            )
        )

        // Content
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // Server info
            InfoCard(label = "السيرفر", value = subscription.host)
            InfoCard(label = "اسم المستخدم", value = subscription.username)
            InfoCard(label = "كلمة المرور", value = subscription.password)

            // Subscription details
            InfoCard(label = "الحالة", value = subscription.status, isStatus = true)
            InfoCard(label = "تاريخ الانتهاء", value = subscription.expiry)
            InfoCard(label = "تاريخ الإنشاء", value = subscription.created)
            InfoCard(label = "تجريبي", value = subscription.trialText)

            // Connection info
            InfoCard(label = "الأجهزة المتصلة", value = subscription.connectionInfo)

            // Content counts
            InfoCard(label = "قنوات مباشرة", value = subscription.liveCount)
            InfoCard(label = "أفلام", value = subscription.movieCount)
            InfoCard(label = "مسلسلات", value = subscription.seriesCount)

            // Server details
            if (subscription.serverUrl.isNotEmpty()) {
                InfoCard(label = "عنوان السيرفر", value = subscription.serverUrl)
            }
            if (subscription.serverProtocol.isNotEmpty()) {
                InfoCard(label = "بروتوكول", value = subscription.serverProtocol)
            }
            if (subscription.timezone.isNotEmpty()) {
                InfoCard(label = "المنطقة الزمنية", value = subscription.timezone)
            }

            // Bottom padding
            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}
