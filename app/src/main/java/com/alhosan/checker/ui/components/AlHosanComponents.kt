package com.alhosan.checker.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.alhosan.checker.ui.theme.BorderGold
import com.alhosan.checker.ui.theme.Gold
import com.alhosan.checker.ui.theme.SurfaceBlack

/**
 * Styled text field matching the original Flutter app's dark + gold design
 */
@Composable
fun AlHosanTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    icon: ImageVector,
    isPassword: Boolean = false,
    obscurePassword: Boolean = true,
    onTogglePassword: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, color = Color.Gray) },
        leadingIcon = {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Gold
            )
        },
        trailingIcon = if (isPassword) {
            {
                IconButton(onClick = { onTogglePassword?.invoke() }) {
                    Icon(
                        imageVector = if (obscurePassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        contentDescription = null,
                        tint = Gold
                    )
                }
            }
        } else null,
        visualTransformation = if (isPassword && obscurePassword) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = if (isPassword) KeyboardOptions(keyboardType = KeyboardType.Password) else KeyboardOptions.Default,
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = Color.White,
            unfocusedTextColor = Color.White,
            focusedBorderColor = Gold,
            unfocusedBorderColor = BorderGold,
            focusedLabelColor = Gold,
            unfocusedLabelColor = Color.Gray,
            cursorColor = Gold,
            focusedLeadingIconColor = Gold,
            unfocusedLeadingIconColor = Gold,
            focusedContainerColor = SurfaceBlack,
            unfocusedContainerColor = SurfaceBlack,
        ),
        shape = RoundedCornerShape(15.dp),
        singleLine = true,
        modifier = modifier.fillMaxWidth()
    )
}

/**
 * Gold info card for displaying subscription details
 * Matches the original Flutter app's card design
 */
@Composable
fun InfoCard(
    label: String,
    value: String,
    isStatus: Boolean = false,
    modifier: Modifier = Modifier
) {
    val valueColor by animateColorAsState(
        targetValue = when {
            isStatus && value.equals("Active", ignoreCase = true) -> Color(0xFF4CAF50)
            isStatus && value.equals("Disabled", ignoreCase = true) -> Color(0xFFFF4444)
            isStatus -> Color(0xFFFF9800)
            else -> Color.White
        },
        animationSpec = tween(300),
        label = "status_color"
    )

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
        colors = CardDefaults.cardColors(
            containerColor = SurfaceBlack
        ),
        border = BorderStroke(1.dp, BorderGold),
        shape = RoundedCornerShape(15.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 15.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                color = Color.Gray,
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = value,
                color = valueColor,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                style = MaterialTheme.typography.bodyLarge
            )
        }
    }
}

/**
 * Main action button with gold styling
 */
@Composable
fun AlHosanButton(
    text: String,
    onClick: () -> Unit,
    isLoading: Boolean = false,
    enabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = onClick,
        enabled = enabled && !isLoading,
        colors = ButtonDefaults.buttonColors(
            containerColor = Gold,
            contentColor = Color.Black,
            disabledContainerColor = Gold.copy(alpha = 0.5f),
            disabledContentColor = Color.Black.copy(alpha = 0.5f)
        ),
        shape = RoundedCornerShape(15.dp),
        modifier = modifier
            .fillMaxWidth()
            .height(60.dp)
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                color = Color.Black,
                strokeWidth = 2.dp,
                modifier = Modifier.size(24.dp)
            )
        } else {
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge
            )
        }
    }
}
