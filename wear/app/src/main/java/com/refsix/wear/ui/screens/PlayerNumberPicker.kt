package com.refsix.wear.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.CompactChip
import androidx.wear.compose.material.Text
import com.refsix.wear.ui.theme.RefGreen
import kotlinx.coroutines.delay

@Composable
internal fun PlayerNumberPicker(
    value: Int,
    onValueChange: (Int) -> Unit,
    fontSize: TextUnit = 22.sp,
    modifier: Modifier = Modifier
) {
    var isEditing by remember { mutableStateOf(false) }

    if (isEditing) {
        NumberKeypad(
            initialValue = value,
            onValueChange = onValueChange,
            onDismiss = { isEditing = false },
            modifier = modifier
        )
    } else {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = modifier
        ) {
            HoldableStepButton("−") { if (value > 1) onValueChange(value - 1) }

            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(width = 56.dp, height = 36.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0xFF1E1E1E))
                    .clickable { isEditing = true }
            ) {
                Text(
                    text = if (value == 0) "" else "$value",
                    fontSize = fontSize,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    textAlign = TextAlign.Center
                )
            }

            HoldableStepButton("+") { if (value < 999) onValueChange(value + 1) }
        }
    }
}

// Custom inline numeric keypad — avoids all Wear OS IME / composition issues.
// Displayed as a Column in the parent ScalingLazyColumn item; no Dialog/window
// overlay, so navigation works correctly underneath.
@Composable
private fun NumberKeypad(
    initialValue: Int,
    onValueChange: (Int) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Local digit string is the single source of truth for what's displayed.
    // Each button tap appends/removes a character; no IME state to lose track of.
    var digits by remember { mutableStateOf(if (initialValue == 0) "" else "$initialValue") }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = modifier
    ) {
        // Number display
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxWidth()
                .height(34.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF1A1A1A))
                .border(
                    1.dp,
                    if (digits.isEmpty()) Color(0xFF555555) else RefGreen,
                    RoundedCornerShape(8.dp)
                )
        ) {
            Text(
                text = if (digits.isEmpty()) "#" else digits,
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                color = if (digits.isEmpty()) Color(0xFF666666) else Color.White,
                textAlign = TextAlign.Center
            )
        }

        // Key grid: rows 1–3 then ⌫ / 0 / ✓
        listOf(
            listOf("1", "2", "3"),
            listOf("4", "5", "6"),
            listOf("7", "8", "9"),
            listOf("⌫", "0", "✓")
        ).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                row.forEach { key ->
                    KeypadButton(label = key) {
                        when (key) {
                            "⌫" -> {
                                digits = digits.dropLast(1)
                                onValueChange(
                                    digits.toIntOrNull()?.takeIf { it in 1..999 } ?: 0
                                )
                            }
                            "✓" -> onDismiss()
                            else -> {
                                if (digits.length < 3) {
                                    digits += key
                                    digits.toIntOrNull()
                                        ?.takeIf { it in 1..999 }
                                        ?.let { onValueChange(it) }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun KeypadButton(label: String, onClick: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(width = 50.dp, height = 30.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(
                when (label) {
                    "✓" -> Color(0xFF1B3A1B)
                    "⌫" -> Color(0xFF3A2000)
                    else -> Color(0xFF2A2A2A)
                }
            )
            .clickable(onClick = onClick)
    ) {
        Text(
            text = label,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = when (label) {
                "✓" -> RefGreen
                "⌫" -> Color(0xFFFF9800)
                else -> Color.White
            },
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun HoldableStepButton(label: String, onStep: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val currentOnStep by rememberUpdatedState(onStep)

    LaunchedEffect(isPressed) {
        if (isPressed) {
            delay(500L)
            var intervalMs = 150L
            while (isPressed) {
                currentOnStep()
                delay(intervalMs)
                intervalMs = maxOf(40L, intervalMs - 20L)
            }
        }
    }

    CompactChip(
        label = { Text(label) },
        onClick = onStep,
        interactionSource = interactionSource
    )
}
