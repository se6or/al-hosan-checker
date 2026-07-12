package com.alhosan.checker.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alhosan.checker.R
import com.alhosan.checker.ui.theme.BorderGold
import com.alhosan.checker.ui.theme.CardBg
import com.alhosan.checker.ui.theme.Gold
import com.alhosan.checker.ui.theme.GoldGradientBrush
import com.alhosan.checker.ui.theme.GoldLight
import com.alhosan.checker.ui.theme.GreenActive
import com.alhosan.checker.ui.theme.RedInactive
import com.alhosan.checker.ui.theme.SurfaceBlack
import com.alhosan.checker.ui.theme.TextDim
import com.alhosan.checker.ui.theme.ToastBg
import com.alhosan.checker.ui.theme.YellowUnknown

/**
 * Input field with paste button - matching HTML reference's input-row + paste-btn design
 */
@Composable
fun AlHosanInputRow(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    isPassword: Boolean = false,
    obscurePassword: Boolean = true,
    onTogglePassword: (() -> Unit)? = null,
    onPaste: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Input field wrapper
        Box(
            modifier = Modifier.weight(1f),
            contentAlignment = Alignment.CenterStart
        ) {
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                placeholder = { Text(placeholder, color = TextDim, fontSize = 14.sp) },
                trailingIcon = if (isPassword) {
                    {
                        IconButton(onClick = { onTogglePassword?.invoke() }) {
                            Icon(
                                imageVector = if (obscurePassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = null,
                                tint = TextDim,
                                modifier = Modifier.size(20.dp)
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
                    focusedPlaceholderColor = TextDim,
                    unfocusedPlaceholderColor = TextDim,
                    cursorColor = Gold,
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                ),
                shape = RoundedCornerShape(16.dp),
                singleLine = true,
                textStyle = androidx.compose.ui.text.TextStyle(
                    textAlign = TextAlign.Center,
                    fontSize = 14.sp
                ),
                modifier = Modifier.fillMaxWidth()
            )
        }

        // Paste button
        if (onPaste != null) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .border(1.dp, BorderGold, RoundedCornerShape(16.dp))
                    .clip(RoundedCornerShape(16.dp))
                    .clickable(onClick = onPaste),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.ContentPaste,
                    contentDescription = "Paste",
                    tint = Gold,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}

/**
 * Capsule card for result display - matching HTML reference's .capsule .capsule-stacked design
 * Horizontal layout: label on left, value on right
 */
@Composable
fun CapsuleRow(
    labelIcon: ImageVector,
    labelText: String,
    valueText: String,
    onCopy: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    CapsuleContainer(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Label with icon
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = labelIcon,
                    contentDescription = null,
                    tint = Gold,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = labelText,
                    color = TextDim,
                    fontSize = 13.sp
                )
            }

            // Value + optional copy button
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = valueText,
                    color = Color.White,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 14.sp,
                    textAlign = TextAlign.End
                )
                if (onCopy != null) {
                    CopyButton(onClick = onCopy)
                }
            }
        }
    }
}

/**
 * Stacked capsule - matching HTML reference's capsule-stacked design
 * Label on top, value below, centered
 */
@Composable
fun CapsuleStacked(
    items: List<CapsuleItem>,
    modifier: Modifier = Modifier
) {
    CapsuleContainer(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items.forEachIndexed { index, item ->
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Label
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = item.icon,
                            contentDescription = null,
                            tint = Gold,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = item.label,
                            color = TextDim,
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    // Value row with copy button
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = item.value,
                            color = Color.White,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 14.sp,
                            textAlign = TextAlign.Center
                        )
                        if (item.onCopy != null) {
                            Spacer(modifier = Modifier.width(6.dp))
                            CopyButton(onClick = item.onCopy)
                        }
                    }
                }

                // Divider between items (not after last)
                if (index < items.size - 1) {
                    DividerRow()
                }
            }
        }
    }
}

/**
 * Result primary-info capsule.
 * RTL (Arabic): copy on physical left, label/icon/value on right.
 * LTR (English): label/icon/value on left, copy on physical right.
 */
@Composable
fun ResultPrimaryInfoStacked(
    items: List<CapsuleItem>,
    modifier: Modifier = Modifier,
    iconAtRight: Boolean = false,
    centerContent: Boolean = false
) {
    CapsuleContainer(modifier = modifier) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items.forEachIndexed { index, item ->
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Box(modifier = Modifier.fillMaxWidth()) {
                            if (item.onCopy != null) {
                                CopyButton(
                                    onClick = item.onCopy,
                                    modifier = Modifier.align(
                                        if (iconAtRight) Alignment.CenterStart else Alignment.CenterEnd
                                    )
                                )
                            }

                            Row(
                                modifier = Modifier
                                    .align(
                                        when {
                                            centerContent -> Alignment.Center
                                            iconAtRight -> Alignment.CenterEnd
                                            else -> Alignment.CenterStart
                                        }
                                    )
                                    .then(
                                        when {
                                            centerContent -> Modifier
                                            iconAtRight -> Modifier.padding(start = 44.dp)
                                            else -> Modifier.padding(end = 44.dp)
                                        }
                                    ),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                if (iconAtRight) {
                                    Text(
                                        text = item.label,
                                        color = TextDim,
                                        fontSize = 13.sp,
                                        textAlign = if (centerContent) TextAlign.Center else TextAlign.Right
                                    )
                                    Icon(
                                        imageVector = item.icon,
                                        contentDescription = null,
                                        tint = Gold,
                                        modifier = Modifier.size(16.dp)
                                    )
                                } else {
                                    Icon(
                                        imageVector = item.icon,
                                        contentDescription = null,
                                        tint = Gold,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = item.label,
                                        color = TextDim,
                                        fontSize = 13.sp,
                                        textAlign = if (centerContent) TextAlign.Center else TextAlign.Left
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(5.dp))

                        Text(
                            text = item.value,
                            modifier = Modifier
                                .fillMaxWidth()
                                .then(
                                    when {
                                        centerContent -> Modifier
                                        iconAtRight -> Modifier.padding(start = 44.dp)
                                        else -> Modifier.padding(end = 44.dp)
                                    }
                                ),
                            color = Color.White,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 14.sp,
                            textAlign = when {
                                centerContent -> TextAlign.Center
                                iconAtRight -> TextAlign.Right
                                else -> TextAlign.Left
                            },
                            lineHeight = 19.sp
                        )
                    }

                    if (index < items.size - 1) {
                        DividerRow()
                    }
                }
            }
        }
    }
}

/**
 * Result side-by-side capsule.
 * RTL (Arabic): value left, label/icon right.
 * LTR (English): label/icon left, value right.
 */
@Composable
fun ResultSideBySideStacked(
    items: List<CapsuleItem>,
    modifier: Modifier = Modifier,
    iconAtRight: Boolean = false
) {
    CapsuleContainer(modifier = modifier) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items.forEachIndexed { index, item ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        if (iconAtRight) {
                            Text(
                                text = item.value,
                                modifier = Modifier.weight(1f),
                                color = Color.White,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 14.sp,
                                textAlign = TextAlign.Left,
                                maxLines = 2,
                                lineHeight = 19.sp
                            )
                            ResultLabelWithIcon(item = item, iconAtRight = true)
                        } else {
                            ResultLabelWithIcon(item = item, iconAtRight = false)
                            Text(
                                text = item.value,
                                modifier = Modifier.weight(1f),
                                color = Color.White,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 14.sp,
                                textAlign = TextAlign.Right,
                                maxLines = 2,
                                lineHeight = 19.sp
                            )
                        }
                    }

                    if (index < items.size - 1) {
                        DividerRow()
                    }
                }
            }
        }
    }
}

@Composable
private fun ResultLabelWithIcon(
    item: CapsuleItem,
    iconAtRight: Boolean
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (iconAtRight) {
            Text(
                text = item.label,
                color = TextDim,
                fontSize = 13.sp,
                textAlign = TextAlign.Right
            )
            Icon(
                imageVector = item.icon,
                contentDescription = null,
                tint = Gold,
                modifier = Modifier.size(16.dp)
            )
        } else {
            Icon(
                imageVector = item.icon,
                contentDescription = null,
                tint = Gold,
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = item.label,
                color = TextDim,
                fontSize = 13.sp,
                textAlign = TextAlign.Left
            )
        }
    }
}

data class CapsuleItem(
    val icon: ImageVector,
    val label: String,
    val value: String,
    val onCopy: (() -> Unit)? = null
)

/**
 * Capsule container - the rounded border container matching HTML reference's .capsule
 */
@Composable
private fun CapsuleContainer(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.Transparent
        ),
        border = BorderStroke(1.dp, BorderGold),
        shape = RoundedCornerShape(20.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(
                        colors = listOf(Color(0xFF080808), Color(0xFF121212))
                    )
                )
        ) {
            content()
        }
    }
}

/**
 * Divider line - matching HTML reference's .divider-line
 */
@Composable
fun DividerRow(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth(0.9f)
            .height(1.dp)
            .background(BorderGold)
    )
}

/**
 * Copy button - matching HTML reference's .copy-btn-res
 */
@Composable
fun CopyButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(32.dp)
            .border(1.dp, BorderGold, RoundedCornerShape(10.dp))
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Default.ContentPaste,
            contentDescription = "Copy",
            tint = Gold,
            modifier = Modifier.size(16.dp)
        )
    }
}

/**
 * Status badge - matching HTML reference's .status-badge
 */
@Composable
fun StatusBadge(
    isActive: Boolean,
    text: String,
    modifier: Modifier = Modifier
) {
    val bgColor = if (isActive) GreenActive else RedInactive
    val textColor = if (isActive) Color.Black else Color.White

    Box(
        modifier = modifier
            .height(36.dp)
            .background(bgColor, RoundedCornerShape(25.dp))
            .padding(horizontal = 22.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = textColor,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 14.sp
        )
    }
}

/**
 * Progress bar - matching HTML reference's .progress-track / .progress-fill
 */
@Composable
fun AlHosanProgressBar(
    progress: Float,
    label: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Progress track
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .background(Color.Black, RoundedCornerShape(20.dp))
                .border(1.dp, BorderGold, RoundedCornerShape(20.dp))
        ) {
            // Progress fill
            Box(
                modifier = Modifier
                    .fillMaxWidth(progress)
                    .fillMaxHeight()
                    .background(GoldGradientBrush, RoundedCornerShape(20.dp))
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Progress label
        Text(
            text = label,
            color = Gold,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center
        )
    }
}

/**
 * Main action button - matching HTML reference's .btn-main
 * Gold gradient background with black text
 */
@Composable
fun AlHosanMainButton(
    text: String,
    icon: ImageVector? = null,
    onClick: () -> Unit,
    isLoading: Boolean = false,
    enabled: Boolean = true,
    isSubButton: Boolean = false,
    modifier: Modifier = Modifier
) {
    val bgColor = if (isSubButton) Color.Transparent else Color(0xFFD4AF37)
    val textColor = if (isSubButton) Gold else Color.Black
    val iconColor = if (isSubButton) Gold else Color.Black
    val border = if (isSubButton) BorderStroke(1.dp, BorderGold) else null

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .then(
                if (border != null) Modifier.border(border, RoundedCornerShape(22.dp))
                else Modifier
            )
            .clip(RoundedCornerShape(22.dp))
            .then(
                if (isSubButton) Modifier.background(Color.Transparent)
                else Modifier.background(GoldGradientBrush)
            )
            .clickable(enabled = enabled && !isLoading) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        if (isLoading) {
            androidx.compose.material3.CircularProgressIndicator(
                color = Color.Black,
                strokeWidth = 2.dp,
                modifier = Modifier.size(24.dp)
            )
        } else {
            Row(
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 16.dp)
            ) {
                if (icon != null) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = iconColor,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                }
                Text(
                    text = text,
                    color = textColor,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 15.sp
                )
            }
        }
    }
}

/**
 * Running horse animation - matching HTML reference's .running-horse @keyframes horseSprint
 */
@Composable
fun RunningHorseLogo(
    isRunning: Boolean,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "horse")
    val offsetY by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = -14f,
        animationSpec = infiniteRepeatable(
            animation = tween(550, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "horseY"
    )
    val rotation by infiniteTransition.animateFloat(
        initialValue = -12f,
        targetValue = 12f,
        animationSpec = infiniteRepeatable(
            animation = tween(550, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "horseR"
    )

    Image(
        painter = painterResource(id = R.drawable.ic_alhosan_logo),
        contentDescription = "الحصان",
        modifier = modifier
            .then(
                if (isRunning) Modifier.offset(y = offsetY.dp)
                else Modifier
            ),
        contentScale = ContentScale.Fit
    )
}

/**
 * Toast notification - matching HTML reference's .toast-item
 */
@Composable
fun AlHosanToast(
    message: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .wrapContentHeight()
            .background(ToastBg, RoundedCornerShape(50.dp))
            .border(1.dp, BorderGold, RoundedCornerShape(50.dp))
            .padding(horizontal = 28.dp, vertical = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = message,
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
            textAlign = TextAlign.Center
        )
    }
}

/**
 * Modal confirmation dialog - matching HTML reference's custom modal
 */
@Composable
fun AlHosanModal(
    message: String,
    cancelText: String,
    confirmText: String,
    onCancel: () -> Unit,
    onConfirm: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight()
            .background(Color(0xBF000000)),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            colors = CardDefaults.cardColors(containerColor = CardBg),
            border = BorderStroke(1.dp, BorderGold),
            shape = RoundedCornerShape(28.dp)
        ) {
            StaggeredColumn(
                modifier = Modifier.padding(24.dp),
                perItemDelayMs = 65,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Item {
                    Text(
                        text = message,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp,
                        textAlign = TextAlign.Center,
                        lineHeight = 24.sp
                    )
                }

                Item {
                    Spacer(modifier = Modifier.height(24.dp))
                }

                Item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                    // Cancel button
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .background(Color(0xFF1A1A1A), RoundedCornerShape(16.dp))
                            .border(1.dp, Color(0xFF333333), RoundedCornerShape(16.dp))
                            .clickable(onClick = onCancel),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = cancelText,
                            color = Color.White,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 14.sp
                        )
                    }

                    // Confirm button
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .background(Color.Transparent, RoundedCornerShape(16.dp))
                            .border(1.dp, RedInactive.copy(alpha = 0.4f), RoundedCornerShape(16.dp))
                            .clickable(onClick = onConfirm),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = confirmText,
                            color = RedInactive,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 14.sp
                        )
                    }
                }
            }
        }
    }
}
}

/**
 * Content count display - matching HTML reference's .v-content with spinner
 */
@Composable
fun ContentCountDisplay(
    liveCount: String,
    movieCount: String,
    seriesCount: String,
    channelsLabel: String,
    moviesLabel: String,
    seriesLabel: String,
    isLoading: Boolean,
    modifier: Modifier = Modifier
) {
    CapsuleContainer(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .height(86.dp)
                .padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                ContentCountItem(channelsLabel, liveCount, isLoading, Modifier.weight(1f))
                Text("|", color = Color(0xFF333333), fontWeight = FontWeight.ExtraBold, fontSize = 22.sp)
                ContentCountItem(moviesLabel, movieCount, isLoading, Modifier.weight(1f))
                Text("|", color = Color(0xFF333333), fontWeight = FontWeight.ExtraBold, fontSize = 22.sp)
                ContentCountItem(seriesLabel, seriesCount, isLoading, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun RowScope.ContentCountItem(
    label: String,
    count: String,
    isLoading: Boolean,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.height(58.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            color = TextDim,
            fontSize = 12.sp,
            maxLines = 1
        )
        CountUpNumber(count = count, isLoading = isLoading)
    }
}

/**
 * Content count number — independent per-field count-up animation.
 *
 * Each field (channels / movies / series) is FULLY INDEPENDENT:
 * - Turns GOLD during counting, turns WHITE the instant THIS field reaches
 *   its own server number — without waiting for sibling fields.
 * - isLoading = false (image-export capture): renders final value directly,
 *   no coroutine, guaranteeing the exported PNG shows the correct number.
 * - LaunchedEffect keyed ONLY on [target] — never restarts when the global
 *   isCounting flag flips, no restart bug.
 * - target == null → fast random counter (pending)
 * - target == 0   → snap white immediately
 * - target  > 0   → animate 0 → target, then turn white independently
 */
@Composable
private fun CountUpNumber(count: String, isLoading: Boolean) {
    val trimmed = count.trim()
    val target: Int? = remember(trimmed) {
        if (trimmed.isNotEmpty() && trimmed.all { it.isDigit() })
            trimmed.toIntOrNull()
        else null
    }

    // ALL remember/LaunchedEffect calls must be unconditional — Compose
    // requires a consistent number and order of composable calls on every
    // recomposition. Moving them before any branching avoids slot-table
    // misalignment (which was the compile/runtime error from early return).
    var display by remember { mutableStateOf(0) }
    var done    by remember { mutableStateOf(false) }

    LaunchedEffect(target) {
        // ── Static capture (isLoading = false at launch time) ────────────────
        // Image export passes isCounting=false → isLoading=false. Snap to the
        // real value immediately so the captured bitmap is always correct.
        if (!isLoading) {
            display = target ?: 0
            done    = true
            return@LaunchedEffect
        }
        // ── Live animation ────────────────────────────────────────────────────
        when {
            target == null -> {
                // Pending: server hasn't replied — fast random counter.
                done = false
                while (true) {
                    display = (display + (8..55).random()).coerceAtMost(999_999)
                    delay(25)
                }
            }
            target == 0 -> {
                // Real zero — snap white immediately.
                display = 0
                done    = true
            }
            else -> {
                // Real positive count — animate 0 → target.
                // This field turns white on its OWN schedule, independently.
                done    = false
                display = 0
                val dur = 700L
                val t0  = System.currentTimeMillis()
                while (true) {
                    val p = ((System.currentTimeMillis() - t0).toFloat() / dur).coerceIn(0f, 1f)
                    display = (target * p).toInt()
                    if (p >= 1f) break
                    delay(16)
                }
                display = target
                done    = true   // ← THIS field turns white NOW, independently
            }
        }
    }

    // When isLoading=false (capture mode) render final value directly so the
    // exported PNG shows the correct number even before the LaunchedEffect
    // coroutine gets a chance to run its first frame.
    Text(
        text  = if (!isLoading) (target ?: 0).toString() else display.toString(),
        color = if (!isLoading || done) Color.White else Gold,
        fontWeight = FontWeight.Black,
        fontSize   = 18.sp
    )
}

/* ════════════════════════════════════════════════════════════════════
 * Shiny Text — animated gold gradient that sweeps across the text.
 * Inspired by https://reactbits.dev/text-animations/shiny-text
 *
 * Uses Brush.linearGradient with an animated start offset so a bright
 * gold highlight continuously moves left-to-right across the glyphs.
 * ═════════════════════════════════════════════════════════════════ */
@Composable
fun ShinyText(
    text: String,
    modifier: Modifier = Modifier,
    fontSize: androidx.compose.ui.unit.TextUnit = 16.sp,
    fontWeight: FontWeight = FontWeight.ExtraBold,
    rtl: Boolean = false,
    durationMs: Int = 2200
) {
    val transition = rememberInfiniteTransition(label = "shiny")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMs, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shinyProgress"
    )

    // The brush sweeps across the text. Arabic should shimmer right-to-left;
    // English should shimmer left-to-right.
    val sweep = if (rtl) 2f - progress * 3f else progress * 3f - 1f
    val colors = listOf(
        Gold,                       // dim gold
        GoldLight,                  // bright gold
        Color.White,                // peak highlight
        GoldLight,                  // bright gold
        Gold                        // dim gold
    )
    val brush = Brush.linearGradient(
        colors = colors,
        start = Offset(sweep * 600f, 0f),
        end = Offset(sweep * 600f + 200f, 40f)
    )

    Text(
        text = text,
        modifier = modifier,
        fontSize = fontSize,
        fontWeight = fontWeight,
        color = Color.White,
        style = androidx.compose.ui.text.TextStyle(brush = brush)
    )
}

/* ════════════════════════════════════════════════════════════════════
 * Shared open/close transitions for overlays, toasts and dialogs.
 * These keep every small interface consistent with the same staggered-menu
 * feel used by screens: short fade + cascading slide.
 * ═════════════════════════════════════════════════════════════════ */
fun alHosanStaggeredEnter(
    durationMs: Int = 360,
    delayMs: Int = 0
): EnterTransition = slideInHorizontally(
    // Enter from the physical right and settle leftward into place.
    initialOffsetX = { it / 4 },
    animationSpec = tween(durationMillis = durationMs, delayMillis = delayMs)
) + fadeIn(animationSpec = tween(durationMillis = (durationMs - 80).coerceAtLeast(120), delayMillis = delayMs))

fun alHosanStaggeredExit(
    durationMs: Int = 260
): ExitTransition = slideOutHorizontally(
    // Exit from left to right, matching the requested reverse direction.
    targetOffsetX = { it / 4 },
    animationSpec = tween(durationMillis = durationMs)
) + fadeOut(animationSpec = tween(durationMillis = (durationMs - 60).coerceAtLeast(100)))

/* ════════════════════════════════════════════════════════════════════
 * StaggeredAppear — child appears with a slight delay + slide-up + fade.
 * Inspired by https://reactbits.dev/components/staggered-menu
 *
 * Wrap a list of children with StaggeredColumn and each one will animate
 * in with a small delay based on its index, producing a cascading reveal
 * that matches the staggered-menu effect from reactbits.dev.
 * ═════════════════════════════════════════════════════════════════ */
@Composable
fun StaggeredColumn(
    modifier: Modifier = Modifier,
    perItemDelayMs: Int = 60,
    horizontalAlignment: Alignment.Horizontal = Alignment.Start,
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    content: @Composable StaggeredScope.() -> Unit
) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        // Trigger the staggered reveal on first composition
        visible = true
    }
    Column(
        modifier = modifier,
        horizontalAlignment = horizontalAlignment,
        verticalArrangement = verticalArrangement
    ) {
        val scope = StaggeredScopeInstance(visible, perItemDelayMs)
        scope.content()
    }
}

interface StaggeredScope {
    @Composable
    fun Item(content: @Composable () -> Unit)
}

private class StaggeredScopeInstance(
    private val visible: Boolean,
    private val perItemDelayMs: Int
) : StaggeredScope {
    private var index = 0

    @Composable
    override fun Item(content: @Composable () -> Unit) {
        val i = index++
        val delayMs = i * perItemDelayMs
        val alpha by animateFloatAsState(
            targetValue = if (visible) 1f else 0f,
            animationSpec = tween(durationMillis = 380, delayMillis = delayMs),
            label = "staggerAlpha_$i"
        )
        val offsetX by animateFloatAsState(
            targetValue = if (visible) 0f else 32f,
            animationSpec = tween(durationMillis = 380, delayMillis = delayMs),
            label = "staggerOffset_$i"
        )
        // IMPORTANT: Column (not Box) so children stack VERTICALLY.
        // Box would stack them on top of each other — that's the bug
        // that caused host/username/password to overlap.
        //
        // CompositingStrategy.Adjust: avoids an offscreen compositing buffer
        // for simple alpha + translation operations. Without this, transparent
        // PNG logos (like the horse logo) show a black background during the
        // stagger-in animation, because the offscreen buffer's default
        // background is opaque black.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer {
                    this.alpha = alpha
                    this.translationX = offsetX
                    this.compositingStrategy =
                        androidx.compose.ui.graphics.CompositingStrategy.ModulateAlpha
                },
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            content()
        }
    }
}


