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
fun FullTimeScreen(viewModel: MatchViewModel, onNewMatch: () -> Unit) {
    val state by viewModel.state.collectAsState()

    val allEvents = remember(state.events) { state.events.sortedBy { it.matchMinute } }
    val goalCount = allEvents.count { it.type == EventType.GOAL }
    val yellowCount = allEvents.count { it.type == EventType.YELLOW_CARD }
    val redCount = allEvents.count { it.type == EventType.RED_CARD }
    val sinCount = allEvents.count { it.type == EventType.SIN_BIN }

    ScalingLazyColumn(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 28.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        item {
            Text(
                text = "FULL TIME",
                style = MaterialTheme.typography.title1,
                color = RefGreen,
                fontWeight = FontWeight.Bold
            )
        }

        item {
            Text(
                text = "${state.homeScore}  –  ${state.awayScore}",
                fontSize = 36.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }

        item {
            Row(
                horizontalArrangement = Arrangement.SpaceEvenly,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(state.homeTeam, style = MaterialTheme.typography.caption1, color = Color.Gray)
                Text(state.awayTeam, style = MaterialTheme.typography.caption1, color = Color.Gray)
            }
        }

        if (state.kickoffDate.isNotEmpty() || state.kickoffTime.isNotEmpty()) {
            item {
                Text(
                    text = listOf(state.kickoffDate, state.kickoffTime)
                        .filter { it.isNotEmpty() }.joinToString("  "),
                    style = MaterialTheme.typography.caption2,
                    color = Color.Gray
                )
            }
        }

        item {
            Row(
                horizontalArrangement = Arrangement.SpaceEvenly,
                modifier = Modifier.fillMaxWidth()
            ) {
                StatBadge("$goalCount", "G", RefGreen)
                StatBadge("$yellowCount", "YC", RefYellow)
                StatBadge("$redCount", "RC", RefRed)
                StatBadge("$sinCount", "SB", RefOrange)
            }
        }

        if (allEvents.isEmpty()) {
            item {
                Text(
                    text = "No events recorded",
                    style = MaterialTheme.typography.caption1,
                    color = Color.Gray
                )
            }
        } else {
            items(allEvents.size) { i ->
                // Larger fonts for readability on the report screen
                EventItem(event = allEvents[i], mainFontSp = 15f, detailFontSp = 13f)
            }
        }

        item { Spacer(modifier = Modifier.height(4.dp)) }

        item {
            Chip(
                label = { Text("Done", fontWeight = FontWeight.Bold) },
                onClick = {
                    viewModel.resetMatch()
                    onNewMatch()
                },
                colors = ChipDefaults.chipColors(backgroundColor = RefBlue),
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun StatBadge(count: String, label: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = count, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = color)
        Text(text = label, style = MaterialTheme.typography.caption1, color = Color.Gray)
    }
}
