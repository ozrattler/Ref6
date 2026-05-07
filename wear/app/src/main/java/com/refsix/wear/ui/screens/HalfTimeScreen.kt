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
fun HalfTimeScreen(viewModel: MatchViewModel, onStartSecondHalf: () -> Unit) {
    val state by viewModel.state.collectAsState()

    val firstHalfGoals = state.events.filter { it.type == EventType.GOAL && it.half == 1 }
    val firstHalfYellows = state.events.filter { it.type == EventType.YELLOW_CARD && it.half == 1 }
    val firstHalfReds = state.events.filter { it.type == EventType.RED_CARD && it.half == 1 }
    val firstHalfSinBins = state.events.filter { it.type == EventType.SIN_BIN && it.half == 1 }

    ScalingLazyColumn(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 28.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        item {
            Text(
                text = "HALF TIME",
                style = MaterialTheme.typography.title1,
                color = Color(0xFF9C27B0),
                fontWeight = FontWeight.Bold
            )
        }

        item {
            Text(
                text = "${state.homeScore}  –  ${state.awayScore}",
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }

        item {
            Row(horizontalArrangement = Arrangement.SpaceEvenly, modifier = Modifier.fillMaxWidth()) {
                Text(state.homeTeam, style = MaterialTheme.typography.caption1, color = Color.Gray)
                Text(state.awayTeam, style = MaterialTheme.typography.caption1, color = Color.Gray)
            }
        }

        if (firstHalfGoals.isNotEmpty()) {
            item { SummaryDivider("Goals") }
            items(firstHalfGoals.size) { i ->
                val g = firstHalfGoals[i]
                EventRow(text = "${g.team.take(8)} ${g.matchMinute}'", color = RefGreen)
            }
        }

        if (firstHalfYellows.isNotEmpty()) {
            item { SummaryDivider("Yellow Cards") }
            items(firstHalfYellows.size) { i ->
                val c = firstHalfYellows[i]
                EventRow(text = "#${c.playerNumber} ${c.team.take(5)} ${c.matchMinute}'", color = RefYellow)
            }
        }

        if (firstHalfReds.isNotEmpty()) {
            item { SummaryDivider("Red Cards") }
            items(firstHalfReds.size) { i ->
                val c = firstHalfReds[i]
                EventRow(text = "#${c.playerNumber} ${c.team.take(5)} ${c.matchMinute}'", color = RefRed)
            }
        }

        if (firstHalfSinBins.isNotEmpty()) {
            item { SummaryDivider("Sin Bins") }
            items(firstHalfSinBins.size) { i ->
                val c = firstHalfSinBins[i]
                EventRow(text = "#${c.playerNumber} ${c.team.take(5)} ${c.matchMinute}'", color = RefOrange)
            }
        }

        item { Spacer(modifier = Modifier.height(4.dp)) }

        item {
            Chip(
                label = { Text("START 2ND HALF", fontWeight = FontWeight.Bold) },
                onClick = {
                    viewModel.startSecondHalf()
                    onStartSecondHalf()
                },
                colors = ChipDefaults.chipColors(backgroundColor = RefGreen),
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun SummaryDivider(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.caption2,
        color = Color.Gray,
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun EventRow(text: String, color: Color) {
    Text(
        text = text,
        style = MaterialTheme.typography.caption1,
        color = color,
        modifier = Modifier.fillMaxWidth()
    )
}
