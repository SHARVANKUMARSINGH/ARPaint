package com.yashk2000.arpaint.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.yashk2000.arpaint.Utils.Settings

@Composable
fun LineThicknessDialog(
    initialThickness: Float,
    currentColor: Color,
    onThicknessSelected: (Float) -> Unit,
    onDismiss: () -> Unit
) {
    var thickness by remember { mutableFloatStateOf(initialThickness.coerceIn(0.08f, 3.0f)) }
    var shadowsEnabled by remember { mutableStateOf(Settings.isShadowsEnabled()) }

    val closeAndSave = {
        onThicknessSelected(thickness)
        Settings.setShadowsEnabled(shadowsEnabled)
        onDismiss()
    }

    Dialog(onDismissRequest = closeAndSave) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header with Title and Close Button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Brush & 3D Shading",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Volumetric AR stroke settings",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                    IconButton(
                        onClick = closeAndSave,
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close & Save",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Live 3D Stroke & Shadow Preview Box
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(84.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.Black.copy(alpha = 0.5f)),
                    contentAlignment = Alignment.Center
                ) {
                    val previewStrokeWidth = (thickness * 20f).coerceIn(4f, 52f)
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val centerY = size.height / 2f
                        val startX = 36.dp.toPx()
                        val endX = size.width - 36.dp.toPx()

                        // 1. Drop shadow pass (if enabled)
                        if (shadowsEnabled) {
                            val shadowOffsetY = (previewStrokeWidth * 0.45f).coerceIn(4f, 16f)
                            drawLine(
                                color = Color.Black.copy(alpha = 0.45f),
                                start = Offset(startX + 4f, centerY + shadowOffsetY),
                                end = Offset(endX + 4f, centerY + shadowOffsetY),
                                strokeWidth = previewStrokeWidth * 1.35f,
                                cap = StrokeCap.Round
                            )
                        }

                        // 2. Base stroke body
                        drawLine(
                            color = currentColor,
                            start = Offset(startX, centerY),
                            end = Offset(endX, centerY),
                            strokeWidth = previewStrokeWidth,
                            cap = StrokeCap.Round
                        )

                        // 3. Specular glossy highlight down the center spine
                        drawLine(
                            color = Color.White.copy(alpha = 0.45f),
                            start = Offset(startX + previewStrokeWidth * 0.2f, centerY - previewStrokeWidth * 0.15f),
                            end = Offset(endX - previewStrokeWidth * 0.2f, centerY - previewStrokeWidth * 0.15f),
                            strokeWidth = (previewStrokeWidth * 0.30f).coerceIn(1.5f, 12f),
                            cap = StrokeCap.Round
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Quick Preset Chips
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    listOf(
                        "Fine" to 0.15f,
                        "Medium" to 0.40f,
                        "Thick" to 0.90f,
                        "Marker" to 2.00f
                    ).forEach { (label, value) ->
                        val isSelected = kotlin.math.abs(thickness - value) < 0.12f
                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                thickness = value
                                onThicknessSelected(value)
                            },
                            label = { Text(label, style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Continuous Slider
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Width",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Slider(
                        value = thickness,
                        onValueChange = {
                            thickness = it
                            onThicknessSelected(it)
                        },
                        valueRange = 0.08f..3.0f,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = String.format("%.2fx", thickness),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // 3D Drop Shadows Toggle Switch Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f))
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "3D Ground Shadows",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Cast real-time depth shadows",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                    Switch(
                        checked = shadowsEnabled,
                        onCheckedChange = {
                            shadowsEnabled = it
                            Settings.setShadowsEnabled(it)
                        }
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = {
                            thickness = 0.33f
                            onThicknessSelected(0.33f)
                        }
                    ) {
                        Text("Reset Default", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
                    }
                    Button(
                        onClick = closeAndSave,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Text("Done", color = MaterialTheme.colorScheme.onPrimary)
                    }
                }
            }
        }
    }
}
