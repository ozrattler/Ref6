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

    val allGoals = state.events.filter { it.type == EventType.GOAL }
    val allYellows = state.events.filter { it.type == EventType.YELLOW_CARD }
    val allReds = state.events.filter { it.type == EventType.RED_CARD }
    val allSinBins = state.events.filter { it.type == EventType.SIN_BIN }

    ScalingLazyColumn(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 28.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
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

        // Stats summary row
        item {
            Row(
                horizontalArrangement = Arrangement.SpaceEvenly,
                modifier = Modifier.fillMaxWidth()
            ) {
                StatBadge("${allGoals.size}", "Goals", RefGreen)
                StatBadge("${allYellows.size}", "Yellow", RefYellow)
                StatBadge("${allReds.size}", "Red", RefRed)
                StatBadge("${allSinBins.size}", "Sin", RefOrange)
            }
        }

        if (allGoals.isNotEmpty()) {
            item { ReportDivider("Goals") }
            items(allGoals.size) { i ->
                val g = allGoals[i]
                ReportRow("${g.team.take(8)}  ${g.matchMinute}'  H${g.half}", RefGreen)
            }
        }

        if (allYellows.isNotEmpty()) {
            item { ReportDivider("Yellow Cards") }
            items(allYellows.size) { i ->
                val c = allYellows[i]
                Column(modifier = Modifier.fillMaxWidth()) {
                    ReportRow("#${c.playerNumber} ${c.team.take(6)}  ${c.matchMinute}'", RefYellow)
                    Text(
                        text = c.detail,
                        style = MaterialTheme.typography.caption2,
                        color = Color.Gray
                    )
                }
            }
        }

        if (allReds.isNotEmpty()) {
            item { ReportDivider("Red Cards") }
            items(allReds.size) { i ->
                val c = allReds[i]
                Column(modifier = Modifier.fillMaxWidth()) {
                    ReportRow("#${c.playerNumber} ${c.team.take(6)}  ${c.matchMinute}'", RefRed)
                    Text(
                        text = c.detail,
                        style = MaterialTheme.typography.caption2,
                        color = Color.Gray
                    )
                }
            }
        }

        if (allSinBins.isNotEmpty()) {
            item { ReportDivider("Sin Bins") }
            items(allSinBins.size) { i ->
                val c = allSinBins[i]
                Column(modifier = Modifier.fillMaxWidth()) {
                    ReportRow("#${c.playerNumber} ${c.team.take(6)}  ${c.matchMinute}'", RefOrange)
                    Text(
                        text = c.detail,
                        style = MaterialTheme.typography.caption2,
                        color = Color.Gray
                    )
                }
            }
        }

        item { Spacer(modifier = Modifier.height(4.dp)) }

        item {
            Chip(
                label = { Text("NEW MATCH", fontWeight = FontWeight.Bold) },
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
        Text(text = count, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = color)
        Text(text = label, style = MaterialTheme.typography.caption2, color = Color.Gray)
    }
}

@Composable
private fun ReportDivider(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.caption2,
        color = Color.Gray,
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun ReportRow(text: String, color: Color) {
    Text(
        text = text,
        style = MaterialTheme.typography.caption1,
        color = color,
        modifier = Modifier.fillMaxWidth()
    )
}
