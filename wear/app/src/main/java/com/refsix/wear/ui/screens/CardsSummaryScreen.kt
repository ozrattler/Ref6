package com.refsix.wear.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.material.*
import com.refsix.wear.data.EventType
import com.refsix.wear.ui.theme.*
import com.refsix.wear.viewmodel.MatchViewModel

@Composable
fun CardsSummaryScreen(
    viewModel: MatchViewModel,
    onDismiss: () -> Unit
) {
    val state by viewModel.state.collectAsState()
    val cardEvents = remember(state.events) {
        state.events
            .filter {
                it.type == EventType.YELLOW_CARD ||
                it.type == EventType.RED_CARD ||
                it.type == EventType.SIN_BIN
            }
            .sortedBy { it.matchMinute }
    }

    ScalingLazyColumn(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 28.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        item {
            Text(
                text = "CARDS",
                style = MaterialTheme.typography.title2,
                fontWeight = FontWeight.Bold,
                color = RefYellow
            )
        }

        if (cardEvents.isEmpty()) {
            item {
                Text(
                    text = "No cards issued",
                    style = MaterialTheme.typography.body2,
                    color = Color.Gray
                )
            }
        } else {
            items(cardEvents.size) { i ->
                val event = cardEvents[i]
                val cardTypeColor = when (event.type) {
                    EventType.YELLOW_CARD -> RefYellow
                    EventType.RED_CARD -> RefRed
                    EventType.SIN_BIN -> RefOrange
                    else -> Color.Gray
                }
                val label = when (event.type) {
                    EventType.YELLOW_CARD -> "YC"
                    EventType.RED_CARD -> "RC"
                    EventType.SIN_BIN -> "SB"
                    else -> ""
                }
                val kitHex = if (event.team == state.homeTeam) state.homeColour else state.awayColour
                val displayColor = kitHex.toKitColor() ?: cardTypeColor
                val who = if (event.playerNumber == "Coach") "Coach" else "#${event.playerNumber}"
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "$label  $who  ${event.team.take(5)}",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = displayColor
                    )
                    Text(
                        text = "${event.matchMinute}'",
                        fontSize = 12.sp,
                        color = Color.Gray
                    )
                }
            }
        }

        item { Spacer(modifier = Modifier.height(4.dp)) }

        item {
            CompactChip(
                label = { Text("Back", fontWeight = FontWeight.Bold) },
                onClick = onDismiss,
                colors = ChipDefaults.chipColors(backgroundColor = Color(0xFF333333))
            )
        }
    }
}
