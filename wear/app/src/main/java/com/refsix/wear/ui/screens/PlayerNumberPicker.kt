package com.refsix.wear.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
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
    // digitsState is the single source of truth for the text field display.
    // Using a plain String and constructing TextFieldValue fresh each render
    // ensures the BasicTextField always renders what we intend, regardless of
    // IME state divergence (a known issue on Wear OS after backspace + retype).
    var digitsState by remember { mutableStateOf(if (value == 0) "" else "$value") }
    val focusRequester = remember { FocusRequester() }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier
    ) {
        HoldableStepButton("−") { if (value > 1) onValueChange(value - 1) }

        if (isEditing) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(width = 62.dp, height = 36.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0xFF2A2A2A))
                    .border(1.dp, RefGreen, RoundedCornerShape(6.dp))
            ) {
                BasicTextField(
                    value = TextFieldValue(
                        text = digitsState,
                        selection = TextRange(digitsState.length)
                    ),
                    onValueChange = { tfv ->
                        val newDigits = tfv.text.filter { it.isDigit() }.take(3)
                        digitsState = newDigits
                        val n = newDigits.toIntOrNull()
                        when {
                            n != null && n in 1..999 -> onValueChange(n)
                            else -> onValueChange(0)
                        }
                    },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(onDone = { isEditing = false }),
                    singleLine = true,
                    textStyle = TextStyle(
                        color = Color.White,
                        fontSize = fontSize,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    ),
                    cursorBrush = SolidColor(RefGreen),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester)
                )
            }
            LaunchedEffect(Unit) { focusRequester.requestFocus() }
        } else {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(width = 56.dp, height = 36.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0xFF1E1E1E))
                    .clickable {
                        digitsState = if (value == 0) "" else "$value"
                        isEditing = true
                    }
            ) {
                Text(
                    text = if (value == 0) "" else "$value",
                    fontSize = fontSize,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    textAlign = TextAlign.Center
                )
            }
        }

        HoldableStepButton("+") { if (value < 999) onValueChange(value + 1) }
    }
}

@Composable
private fun HoldableStepButton(label: String, onStep: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    // rememberUpdatedState ensures the coroutine always calls the latest lambda,
    // preventing the stale-closure bug where value is captured once at press time
    // and the fast-scroll loop keeps decrementing to the same number.
    val currentOnStep by rememberUpdatedState(onStep)

    // After 500 ms hold, fire repeatedly with acceleration (150 ms → 40 ms floor).
    // LaunchedEffect restarts when isPressed flips, cancelling the loop on release.
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
