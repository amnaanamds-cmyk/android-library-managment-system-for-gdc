package com.college.library.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.college.library.ui.theme.CardGreen
import com.college.library.ui.theme.Gold
import com.college.library.ui.theme.NavyBlue
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BarcodeScannerDialog(
    title: String,
    instruction: String,
    candidateItems: List<Pair<String, String>>, // Pair of (Name/Title, Barcode/ISBN)
    onScanSuccess: (String) -> Unit,
    onDismiss: () -> Unit,
    onGalleryScan: (() -> Unit)? = null
) {
    var manualCode by remember { mutableStateOf("") }
    var scannedValue by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    // Viewfinder laser line animation
    val infiniteTransition = rememberInfiniteTransition(label = "laser")
    val laserYFraction by infiniteTransition.animateFloat(
        initialValue = 0.1f,
        targetValue = 0.9f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "laserY"
    )

    fun handleScan(code: String) {
        if (scannedValue != null) return // Already scanned
        scannedValue = code
    }

    // Trigger success callback after feedback delay
    LaunchedEffect(scannedValue) {
        if (scannedValue != null) {
            delay(600)
            onScanSuccess(scannedValue!!)
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = title,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = NavyBlue
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Simulated Viewfinder Box
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.Black)
                        .border(1.dp, Color.DarkGray, RoundedCornerShape(16.dp))
                ) {
                    if (scannedValue != null) {
                        // Green Success Overlay
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(CardGreen.copy(alpha = 0.4f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = "Scan Successful!",
                                    color = Color.White,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = scannedValue!!,
                                    color = Gold,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    } else {
                        // Viewfinder Overlay Brackets
                        Box(
                            modifier = Modifier
                                .align(Alignment.Center)
                                .size(width = 240.dp, height = 100.dp)
                                .border(BorderStroke(2.dp, Gold), RoundedCornerShape(8.dp))
                        )

                        // Animating Red Laser Line
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.9f)
                                .align(Alignment.TopCenter)
                                .offset(y = (180 * laserYFraction).dp)
                                .height(3.dp)
                                .background(Color.Red)
                        )

                        // Bottom Helper Text
                        Text(
                            text = instruction,
                            color = Color.White.copy(alpha = 0.7f),
                            fontSize = 11.sp,
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = 8.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Gallery Option
                if (onGalleryScan != null) {
                    OutlinedButton(
                        onClick = onGalleryScan,
                        border = BorderStroke(1.dp, NavyBlue),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = NavyBlue),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.PhotoLibrary, contentDescription = "Gallery")
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Choose Barcode Image from Gallery")
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }

                // Manual Input Text Field
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = manualCode,
                        onValueChange = { manualCode = it },
                        placeholder = { Text("Or enter code manually", fontSize = 13.sp) },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = NavyBlue,
                            unfocusedBorderColor = Color.LightGray
                        )
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            if (manualCode.isNotBlank()) {
                                handleScan(manualCode.trim())
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = NavyBlue),
                        enabled = manualCode.isNotBlank()
                    ) {
                        Text("Verify", color = Gold)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Candidate Quick-Scan selection list for emulator testing
                if (candidateItems.isNotEmpty()) {
                    Text(
                        text = "Emulator Quick Scan targets:",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Gray,
                        modifier = Modifier.align(Alignment.Start)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 120.dp)
                            .border(1.dp, Color.LightGray.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                            .padding(4.dp)
                    ) {
                        items(candidateItems) { item ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { handleScan(item.second) }
                                    .padding(8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = item.first,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    modifier = Modifier.weight(0.6f)
                                )
                                Text(
                                    text = item.second,
                                    fontSize = 11.sp,
                                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                    color = NavyBlue,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.End,
                                    modifier = Modifier.weight(0.4f)
                                )
                            }
                            HorizontalDivider(color = Color.LightGray.copy(alpha = 0.3f))
                        }
                    }
                }
            }
        }
    }
}
