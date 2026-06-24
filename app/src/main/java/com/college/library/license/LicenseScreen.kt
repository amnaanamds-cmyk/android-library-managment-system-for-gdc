package com.college.library.license

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val DarkBg = Color(0xFF060C18)
private val DarkBg2 = Color(0xFF071428)
private val DarkBg3 = Color(0xFF0D1F38)
private val BorderColor = Color(0xFF1E3050)
private val BorderAccent = Color(0xFF1E4080)
private val GoldPrimary = Color(0xFFC8A84B)
private val GoldLight = Color(0xFFE6C96E)
private val BluePrimary = Color(0xFF1E5FD4)
private val BlueLight = Color(0xFF2872F0)
private val GreenAccent = Color(0xFF2EC98A)
private val RedError = Color(0xFFE05252)
private val TextPrimary = Color(0xFFE8EEF8)
private val TextSecondary = Color(0xFFA0B4CC)
private val TextMuted = Color(0xFF4D6A90)

@Composable
fun LicenseScreen(
    onLicenseActivated: () -> Unit,
    viewModel: LicenseViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val activationState by viewModel.activationState.collectAsState()
    val deviceId = remember { viewModel.getDeviceId() }
    val licenseStatus = remember { viewModel.getLicenseStatus() }

    var keyInput by remember { mutableStateOf("GDCLIB") }
    var showCard by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        delay(100)
        showCard = true
    }

    LaunchedEffect(activationState) {
        if (activationState is ActivationState.Success) {
            delay(1800)
            onLicenseActivated()
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "bg")
    val orbOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 30f,
        animationSpec = infiniteRepeatable(
            animation = tween(8000, easing = EaseInOut),
            repeatMode = RepeatMode.Reverse
        ),
        label = "orb"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBg)
            .drawBehind { drawGrid(this) },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .drawBehind {
                    drawCircle(
                        color = BluePrimary.copy(alpha = 0.12f),
                        radius = 250.dp.toPx(),
                        center = Offset(-75.dp.toPx(), -75.dp.toPx())
                    )
                    drawCircle(
                        color = GoldPrimary.copy(alpha = 0.10f),
                        radius = 200.dp.toPx(),
                        center = Offset(
                            size.width + 50.dp.toPx() - orbOffset.dp.toPx(),
                            size.height + 50.dp.toPx() - orbOffset.dp.toPx()
                        )
                    )
                }
        )

        AnimatedVisibility(
            visible = showCard,
            enter = fadeIn(tween(500)) + slideInVertically(
                initialOffsetY = { it / 4 },
                animationSpec = tween(500, easing = EaseOutCubic)
            )
        ) {
            Card(
                modifier = Modifier
                    .widthIn(max = 420.dp)
                    .padding(24.dp),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = DarkBg2),
                elevation = CardDefaults.cardElevation(defaultElevation = 24.dp)
            ) {
                Column {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(3.dp)
                            .background(
                                Brush.horizontalGradient(
                                    listOf(BluePrimary, GoldPrimary, BluePrimary)
                                )
                            )
                    )

                    Column(
                        modifier = Modifier.padding(horizontal = 32.dp, vertical = 36.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .size(72.dp)
                                .clip(RoundedCornerShape(18.dp))
                                .background(
                                    Brush.linearGradient(
                                        listOf(
                                            GoldPrimary.copy(alpha = 0.2f),
                                            GoldPrimary.copy(alpha = 0.08f)
                                        )
                                    )
                                )
                                .border(
                                    1.dp,
                                    GoldPrimary.copy(alpha = 0.35f),
                                    RoundedCornerShape(18.dp)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("📚", fontSize = 32.sp)
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        Text(
                            "GDC Library",
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Black,
                            color = GoldLight,
                            letterSpacing = 0.3.sp
                        )
                        Text(
                            "LICENSE ACTIVATION",
                            fontSize = 11.sp,
                            color = TextMuted,
                            letterSpacing = 2.sp,
                            fontWeight = FontWeight.SemiBold
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Box(
                            modifier = Modifier
                                .width(50.dp)
                                .height(2.dp)
                                .background(
                                    Brush.horizontalGradient(
                                        listOf(Color.Transparent, GoldPrimary, Color.Transparent)
                                    )
                                )
                        )

                        Spacer(modifier = Modifier.height(24.dp))

                        if (licenseStatus is LicenseStatus.Expired ||
                            licenseStatus is LicenseStatus.WrongDevice ||
                            licenseStatus is LicenseStatus.Tampered
                        ) {
                            StatusBanner(licenseStatus)
                            Spacer(modifier = Modifier.height(16.dp))
                        }

                        MachineIdBox(deviceId, context)

                        Spacer(modifier = Modifier.height(22.dp))

                        Text(
                            "🔑  Enter License Key",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextSecondary,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        val borderColor = when (activationState) {
                            is ActivationState.Error -> RedError
                            is ActivationState.Success -> GreenAccent
                            else -> BorderColor
                        }

                        OutlinedTextField(
                            value = keyInput,
                            onValueChange = { raw ->
                                keyInput = formatLicenseKey(raw)
                                if (activationState is ActivationState.Error) {
                                    viewModel.resetState()
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            textStyle = TextStyle(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = TextPrimary,
                                letterSpacing = 1.5.sp
                            ),
                            placeholder = {
                                Text(
                                    "GDCLIB-XXXX-XXXX-XXXX",
                                    color = TextMuted,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 13.sp
                                )
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                unfocusedContainerColor = DarkBg3,
                                focusedContainerColor = DarkBg3,
                                unfocusedBorderColor = borderColor,
                                focusedBorderColor = GoldPrimary,
                                cursorColor = GoldPrimary
                            ),
                            keyboardOptions = KeyboardOptions(
                                capitalization = KeyboardCapitalization.Characters,
                                imeAction = ImeAction.Done
                            ),
                            keyboardActions = KeyboardActions(
                                onDone = { viewModel.activate(keyInput) }
                            )
                        )

                        when (val state = activationState) {
                            is ActivationState.Error -> {
                                Spacer(modifier = Modifier.height(10.dp))
                                MessageBox(
                                    text = "❌ ${state.message}",
                                    bgColor = RedError.copy(alpha = 0.12f),
                                    borderColor = RedError.copy(alpha = 0.35f),
                                    textColor = Color(0xFFF08080)
                                )
                            }
                            is ActivationState.Success -> {
                                Spacer(modifier = Modifier.height(10.dp))
                                val expiryStr = SimpleDateFormat(
                                    "dd MMM yyyy", Locale.getDefault()
                                ).format(Date(state.expiry))
                                MessageBox(
                                    text = "✅ License activated! Expires: $expiryStr",
                                    bgColor = GreenAccent.copy(alpha = 0.12f),
                                    borderColor = GreenAccent.copy(alpha = 0.35f),
                                    textColor = Color(0xFF5CE8A8)
                                )
                            }
                            else -> {}
                        }

                        Spacer(modifier = Modifier.height(18.dp))

                        val isSuccess = activationState is ActivationState.Success
                        val isLoading = activationState is ActivationState.Loading

                        Button(
                            onClick = { viewModel.activate(keyInput) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp),
                            enabled = !isLoading && !isSuccess,
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isSuccess) GreenAccent else BluePrimary,
                                disabledContainerColor = if (isSuccess)
                                    GreenAccent else BluePrimary.copy(alpha = 0.6f)
                            )
                        ) {
                            if (isLoading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    color = Color.White,
                                    strokeWidth = 2.5.dp
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Verifying…", fontWeight = FontWeight.Bold)
                            } else if (isSuccess) {
                                Icon(
                                    Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    modifier = Modifier.size(22.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    "Activated — Loading App…",
                                    fontWeight = FontWeight.Bold
                                )
                            } else {
                                Icon(
                                    Icons.Default.Shield,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    if (activationState is ActivationState.Error)
                                        "Try Again" else "Activate License",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0xFF1E3050).copy(alpha = 0.4f))
                                .border(
                                    1.dp,
                                    Color(0xFF1E3050).copy(alpha = 0.8f),
                                    RoundedCornerShape(10.dp)
                                )
                                .padding(12.dp)
                        ) {
                            Text(
                                text = "Offline Activation: This software works fully offline. " +
                                        "Your license key is permanently bound to this device " +
                                        "and stored securely. Contact your software provider " +
                                        "if you need to transfer the license.",
                                fontSize = 11.sp,
                                color = TextMuted,
                                lineHeight = 18.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MachineIdBox(deviceId: String, context: Context) {
    var copied by remember { mutableStateOf(false) }

    LaunchedEffect(copied) {
        if (copied) {
            delay(2000)
            copied = false
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(DarkBg3)
            .border(1.dp, BorderAccent, RoundedCornerShape(12.dp))
            .padding(14.dp)
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Smartphone,
                    contentDescription = null,
                    tint = TextMuted,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    "THIS DEVICE'S MACHINE ID",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.5.sp,
                    color = TextMuted
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = deviceId,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = GoldLight,
                    letterSpacing = 1.sp,
                    modifier = Modifier.weight(1f)
                )

                Spacer(modifier = Modifier.width(10.dp))

                OutlinedButton(
                    onClick = {
                        val rawId = deviceId.replace("-", "")
                        val clipboard = context.getSystemService(
                            Context.CLIPBOARD_SERVICE
                        ) as ClipboardManager
                        clipboard.setPrimaryClip(
                            ClipData.newPlainText("Device ID", rawId)
                        )
                        copied = true
                        Toast.makeText(context, "Device ID copied!", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.height(32.dp),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = if (copied) GreenAccent else GoldPrimary
                    ),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        if (copied) GreenAccent.copy(alpha = 0.4f)
                        else GoldPrimary.copy(alpha = 0.3f)
                    ),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
                ) {
                    if (copied) {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Copied!", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    } else {
                        Icon(
                            Icons.Default.ContentCopy,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Copy", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            Spacer(modifier = Modifier.height(7.dp))

            Text(
                "Share this ID with your software provider to receive a license key tied to this device.",
                fontSize = 10.sp,
                color = TextMuted,
                lineHeight = 15.sp
            )
        }
    }
}

@Composable
private fun StatusBanner(status: LicenseStatus) {
    data class BannerStyle(val text: String, val bg: Color, val border: Color, val fg: Color)

    val style = when (status) {
        is LicenseStatus.Expired -> {
            val dateStr = SimpleDateFormat(
                "dd MMMM yyyy", Locale.getDefault()
            ).format(Date(status.expiry))
            BannerStyle(
                "Your license expired on $dateStr. Contact support for renewal.",
                RedError.copy(alpha = 0.12f),
                RedError.copy(alpha = 0.4f),
                Color(0xFFF08080)
            )
        }
        is LicenseStatus.WrongDevice -> BannerStyle(
            "This license is bound to a different device. Contact support.",
            GoldPrimary.copy(alpha = 0.10f),
            GoldPrimary.copy(alpha = 0.4f),
            GoldLight
        )
        is LicenseStatus.Tampered -> BannerStyle(
            "⚠️ License file has been tampered with. Please re-activate.",
            RedError.copy(alpha = 0.15f),
            RedError.copy(alpha = 0.5f),
            Color(0xFFFF7070)
        )
        else -> return
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(style.bg)
            .border(1.dp, style.border, RoundedCornerShape(10.dp))
            .padding(12.dp, 10.dp)
    ) {
        Text(
            text = style.text,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = style.fg,
            lineHeight = 20.sp
        )
    }
}

@Composable
private fun MessageBox(
    text: String,
    bgColor: Color,
    borderColor: Color,
    textColor: Color
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(9.dp))
            .background(bgColor)
            .border(1.dp, borderColor, RoundedCornerShape(9.dp))
            .padding(10.dp, 10.dp)
    ) {
        Text(
            text = text,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = textColor
        )
    }
}

private fun formatLicenseKey(raw: String): String {
    val clean = raw.uppercase().replace(Regex("[^A-Z0-9]"), "")
    val body = if (clean.startsWith("GDCLIB")) clean.drop(6) else clean
    val parts = body.chunked(4).take(3)
    return "GDCLIB" + if (parts.isNotEmpty()) "-${parts.joinToString("-")}" else ""
}

private fun drawGrid(scope: DrawScope) {
    val gridSize = 40f
    val gridColor = Color(0xFF1E5FD4).copy(alpha = 0.04f)
    val w = scope.size.width
    val h = scope.size.height

    var x = 0f
    while (x < w) {
        scope.drawLine(gridColor, Offset(x, 0f), Offset(x, h), strokeWidth = 1f)
        x += gridSize
    }
    var y = 0f
    while (y < h) {
        scope.drawLine(gridColor, Offset(0f, y), Offset(w, y), strokeWidth = 1f)
        y += gridSize
    }
}

