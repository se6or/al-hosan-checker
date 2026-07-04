package com.alhosan.checker.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.alhosan.checker.data.model.CheckerState
import com.alhosan.checker.ui.components.AlHosanButton
import com.alhosan.checker.ui.components.AlHosanTextField
import com.alhosan.checker.ui.theme.Gold
import com.alhosan.checker.viewmodel.CheckerViewModel

/**
 * Login/Check screen - main entry point of the app.
 * Matches the original Flutter LoginScreen design with gold-on-black theme.
 */
@Composable
fun LoginScreen(
    onResultReady: () -> Unit,
    viewModel: CheckerViewModel = viewModel()
) {
    val host by viewModel.host.collectAsState()
    val username by viewModel.username.collectAsState()
    val password by viewModel.password.collectAsState()
    val obscurePassword by viewModel.obscurePassword.collectAsState()
    val state by viewModel.state.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }

    // Handle state changes
    LaunchedEffect(state) {
        when (state) {
            is CheckerState.Success -> onResultReady()
            is CheckerState.Error -> {
                val msg = (state as CheckerState.Error).message
                snackbarHostState.showSnackbar(msg)
                viewModel.resetState()
            }
            else -> {}
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp)
    ) {
        // Snackbar host at top
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.TopCenter)
        ) { data ->
            Snackbar(
                snackbarData = data,
                containerColor = Color(0xFF1A1A1A),
                contentColor = Color.White
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // App Icon - Horse head (using Dns as server icon since Hotel doesn't fit)
            Icon(
                imageVector = Icons.Default.Dns,
                contentDescription = null,
                tint = Gold,
                modifier = Modifier.size(80.dp)
            )

            Spacer(modifier = Modifier.height(10.dp))

            // App Title
            Text(
                text = "محرك الحصان الفاحص",
                style = MaterialTheme.typography.headlineMedium,
                color = Gold,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(40.dp))

            // Host input
            AlHosanTextField(
                value = host,
                onValueChange = viewModel::updateHost,
                label = "السيرفر (Host)",
                icon = Icons.Default.Dns
            )

            Spacer(modifier = Modifier.height(15.dp))

            // Username input
            AlHosanTextField(
                value = username,
                onValueChange = viewModel::updateUsername,
                label = "اسم المستخدم",
                icon = Icons.Default.Person
            )

            Spacer(modifier = Modifier.height(15.dp))

            // Password input
            AlHosanTextField(
                value = password,
                onValueChange = viewModel::updatePassword,
                label = "كلمة المرور",
                icon = Icons.Default.Lock,
                isPassword = true,
                obscurePassword = obscurePassword,
                onTogglePassword = viewModel::togglePasswordVisibility
            )

            Spacer(modifier = Modifier.weight(1f))

            // Check button
            AlHosanButton(
                text = "بدء فحص الحصان",
                onClick = viewModel::checkSubscription,
                isLoading = state is CheckerState.Loading
            )
        }
    }
}
