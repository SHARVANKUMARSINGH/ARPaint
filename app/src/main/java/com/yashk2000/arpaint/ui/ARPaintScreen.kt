package com.yashk2000.arpaint.ui

import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yashk2000.arpaint.ui.components.ClearConfirmationDialog
import com.yashk2000.arpaint.ui.components.ColorPickerDialog
import com.yashk2000.arpaint.ui.components.LineThicknessDialog
import com.yashk2000.arpaint.ui.components.VideoSavedDialog
import kotlinx.coroutines.delay
import java.io.File
import java.util.Locale

enum class ARTrackingState {
    TRACKING,
    SEARCHING,
    PAUSED
}

@Composable
fun ARPaintScreen(
    trackingState: ARTrackingState,
    strokeCount: Int,
    currentColor: Color,
    currentThickness: Float,
    isRecording: Boolean,
    isPhysicsActive: Boolean = false,
    onColorChanged: (Color) -> Unit,
    onThicknessChanged: (Float) -> Unit,
    onTogglePhysics: () -> Unit = {},
    onResetPhysics: (() -> Unit)? = null,
    onUndo: () -> Unit,
    onClear: () -> Unit,
    onToggleRecording: () -> Unit,
    savedVideoFile: File?,
    savedVideoUri: Uri?,
    onDismissVideoSaved: () -> Unit,
    errorMessage: String? = null,
    onDismissError: () -> Unit = {}
) {
    var showColorPicker by remember { mutableStateOf(false) }
    var showThicknessPicker by remember { mutableStateOf(false) }
    var showClearConfirm by remember { mutableStateOf(false) }

    // Live timer for recording
    var recordingSeconds by remember { mutableLongStateOf(0L) }
    LaunchedEffect(isRecording) {
        if (isRecording) {
            recordingSeconds = 0L
            while (true) {
                delay(1000L)
                recordingSeconds++
            }
        } else {
            recordingSeconds = 0L
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        // ================= TOP BAR =================
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // AR Tracking State Badge
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = Color.Black.copy(alpha = 0.55f),
                tonalElevation = 4.dp
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val statusDotColor = when (trackingState) {
                        ARTrackingState.TRACKING -> Color(0xFF00E676)
                        ARTrackingState.SEARCHING -> Color(0xFFFFD600)
                        ARTrackingState.PAUSED -> Color(0xFFFF5252)
                    }
                    val statusText = when (trackingState) {
                        ARTrackingState.TRACKING -> "AR Active"
                        ARTrackingState.SEARCHING -> "Detecting..."
                        ARTrackingState.PAUSED -> "Paused"
                    }

                    // Pulsing animation for tracking indicator
                    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
                    val pulseScale by infiniteTransition.animateFloat(
                        initialValue = 0.85f,
                        targetValue = 1.15f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(1000, easing = FastOutSlowInEasing),
                            repeatMode = RepeatMode.Reverse
                        ),
                        label = "scale"
                    )

                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .scale(if (trackingState == ARTrackingState.TRACKING) pulseScale else 1f)
                            .clip(CircleShape)
                            .background(statusDotColor)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            // Top-right actions: Reset to Air, Undo & Clear
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = Color.Black.copy(alpha = 0.55f),
                tonalElevation = 4.dp
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Reset to Air button (when physics is active)
                    if (isPhysicsActive && onResetPhysics != null) {
                        IconButton(
                            onClick = onResetPhysics,
                            enabled = strokeCount > 0,
                            modifier = Modifier.size(38.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Float Back to Air",
                                tint = if (strokeCount > 0) Color(0xFF00E5FF) else Color.White.copy(alpha = 0.3f),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(2.dp))
                    }

                    // Undo Button
                    IconButton(
                        onClick = onUndo,
                        enabled = strokeCount > 0,
                        modifier = Modifier.size(38.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Undo,
                            contentDescription = "Undo",
                            tint = if (strokeCount > 0) Color.White else Color.White.copy(alpha = 0.3f),
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(2.dp))

                    // Clear Canvas Button
                    IconButton(
                        onClick = { showClearConfirm = true },
                        enabled = strokeCount > 0,
                        modifier = Modifier.size(38.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteSweep,
                            contentDescription = "Clear Canvas",
                            tint = if (strokeCount > 0) MaterialTheme.colorScheme.error else Color.White.copy(alpha = 0.3f),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }

        // ================= RECORDING BANNER (Top Center) =================
        AnimatedVisibility(
            visible = isRecording,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 64.dp)
        ) {
            val infiniteTransition = rememberInfiniteTransition(label = "recordingPulse")
            val blinkAlpha by infiniteTransition.animateFloat(
                initialValue = 0.3f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(600, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "blink"
            )

            Surface(
                shape = RoundedCornerShape(24.dp),
                color = Color.Red.copy(alpha = 0.85f),
                shadowElevation = 8.dp
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = blinkAlpha))
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    val minutes = recordingSeconds / 60
                    val seconds = recordingSeconds % 60
                    val timerStr = String.format(Locale.US, "%02d:%02d", minutes, seconds)
                    Text(
                        text = "REC $timerStr",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }
        }

        // ================= BOTTOM CONTROLS DOCK =================
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(bottom = 24.dp, start = 16.dp, end = 16.dp),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                shape = RoundedCornerShape(36.dp),
                color = Color.Black.copy(alpha = 0.68f),
                tonalElevation = 12.dp,
                border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.18f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 1. Color Picker Button
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .clip(CircleShape)
                            .clickable { showColorPicker = true }
                            .padding(4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(currentColor)
                                .border(2.dp, Color.White, CircleShape)
                        )
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            text = "Color",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White,
                            fontSize = 11.sp
                        )
                    }

                    // 2. Line Thickness Button
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .clip(CircleShape)
                            .clickable { showThicknessPicker = true }
                            .padding(4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Brush,
                                contentDescription = "Thickness",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            text = String.format(Locale.US, "%.1fx", currentThickness),
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White,
                            fontSize = 11.sp
                        )
                    }

                    // 3. Main Center Record Button
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        val recordButtonColor = if (isRecording) MaterialTheme.colorScheme.error else Color(0xFFFF1744)

                        val infiniteTransition = rememberInfiniteTransition(label = "btnPulse")
                        val glowScale by infiniteTransition.animateFloat(
                            initialValue = 1.0f,
                            targetValue = 1.12f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(700, easing = FastOutSlowInEasing),
                                repeatMode = RepeatMode.Reverse
                            ),
                            label = "glow"
                        )

                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(58.dp)
                                .scale(if (isRecording) glowScale else 1.0f)
                                .clip(CircleShape)
                                .background(
                                    Brush.radialGradient(
                                        listOf(
                                            recordButtonColor,
                                            recordButtonColor.copy(alpha = 0.75f)
                                        )
                                    )
                                )
                                .border(3.dp, Color.White, CircleShape)
                                .clickable { onToggleRecording() }
                        ) {
                            if (isRecording) {
                                Box(
                                    modifier = Modifier
                                        .size(18.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(Color.White)
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Videocam,
                                    contentDescription = "Record Video",
                                    tint = Color.White,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            text = if (isRecording) "Stop" else "Record",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (isRecording) Color(0xFFFF5252) else Color.White,
                            fontSize = 11.sp
                        )
                    }

                    // 4. Physics Button (ON / OFF)
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .clip(CircleShape)
                            .clickable { onTogglePhysics() }
                            .padding(4.dp)
                    ) {
                        val infiniteTransition = rememberInfiniteTransition(label = "physicsPulse")
                        val physicsGlowScale by infiniteTransition.animateFloat(
                            initialValue = 0.95f,
                            targetValue = 1.08f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(800, easing = FastOutSlowInEasing),
                                repeatMode = RepeatMode.Reverse
                            ),
                            label = "physicsGlow"
                        )

                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .scale(if (isPhysicsActive) physicsGlowScale else 1.0f)
                                .clip(CircleShape)
                                .background(
                                    if (isPhysicsActive)
                                        Brush.linearGradient(listOf(Color(0xFF00E5FF), Color(0xFF00E676)))
                                    else
                                        Brush.linearGradient(listOf(Color.White.copy(alpha = 0.15f), Color.White.copy(alpha = 0.10f)))
                                )
                                .border(
                                    width = if (isPhysicsActive) 2.dp else 1.dp,
                                    color = if (isPhysicsActive) Color.White else Color.White.copy(alpha = 0.25f),
                                    shape = CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = "Physics Gravity",
                                tint = if (isPhysicsActive) Color.Black else Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            text = if (isPhysicsActive) "Phys ON" else "Phys OFF",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = if (isPhysicsActive) FontWeight.Bold else FontWeight.Normal,
                            color = if (isPhysicsActive) Color(0xFF00E5FF) else Color.White.copy(alpha = 0.8f),
                            fontSize = 11.sp
                        )
                    }
                }
            }
        }

        // ================= POPUP DIALOGS =================

        // 1. Color Picker Popup
        if (showColorPicker) {
            ColorPickerDialog(
                initialColor = currentColor,
                onColorSelected = { newColor ->
                    onColorChanged(newColor)
                    // Keep dialog open so user can adjust RGB sliders and presets freely!
                },
                onDismiss = { showColorPicker = false }
            )
        }

        // 2. Line Thickness Popup
        if (showThicknessPicker) {
            LineThicknessDialog(
                initialThickness = currentThickness,
                currentColor = currentColor,
                onThicknessSelected = { newThickness ->
                    onThicknessChanged(newThickness)
                    // Keep dialog open so user can slide smoothly and see preview!
                },
                onDismiss = { showThicknessPicker = false }
            )
        }

        // 3. Clear Canvas Confirmation
        if (showClearConfirm) {
            ClearConfirmationDialog(
                onConfirmClear = {
                    onClear()
                    showClearConfirm = false
                },
                onDismiss = { showClearConfirm = false }
            )
        }

        // 4. Video Saved Popup
        if (savedVideoFile != null) {
            VideoSavedDialog(
                videoFile = savedVideoFile,
                mediaUri = savedVideoUri,
                onDismiss = onDismissVideoSaved
            )
        }

        // 5. Error Dialog / Snackbar
        if (errorMessage != null) {
            AlertDialog(
                onDismissRequest = onDismissError,
                title = { Text("Notice", fontWeight = FontWeight.Bold) },
                text = { Text(errorMessage) },
                confirmButton = {
                    TextButton(onClick = onDismissError) {
                        Text("OK")
                    }
                }
            )
        }
    }
}
