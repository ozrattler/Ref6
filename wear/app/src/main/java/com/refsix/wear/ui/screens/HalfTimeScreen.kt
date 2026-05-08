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
import com.refsix.wear.data.MatchEvent
import com.refsix.wear.ui.theme.*
import com.refsix.wear.viewmodel.MatchViewModel

@Composable
fun HalfTimeScreen(viewModel: MatchViewModel, onStartSecondHalf: () -> Unit) {
    val state by viewModel.state.collectAsState()
    val countdown by viewModel.halfTimeCountdown.collectAsState()

    LaunchedEffect(Unit) { viewModel.ensureHalfTimeCountdown() }

    val firstHalfEvents = state.events
        .filter { it.half == 1 }
        .sortedBy { it.matchMinute }

    ScalingLazyColumn(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 28.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
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
            Row(
                horizontalArrangement = Arrangement.SpaceEvenly,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(state.homeTeam, style = MaterialTheme.typography.caption1, color = Color.Gray)
                Text(state.awayTeam, style = MaterialTheme.typography.caption1, color = Color.Gray)
            }
        }

        item {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(vertical = 2.dp)
            ) {
                Text(
                    text = if (countdown == 0) "BREAK OVER" else "HT BREAK",
                    style = MaterialTheme.typography.caption2,
                    color = Color.Gray
                )
                Text(
                    text = "%02d:%02d".format(countdown / 60, countdown % 60),
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Bold,
                    color = when {
                        countdown == 0 -> Color.Gray
                        countdown <= 60 -> RefRed
                        countdown <= 120 -> RefYellow
                        else -> Color.White
                    }
                )
            }
        }

        if (firstHalfEvents.isEmpty()) {
            item {
                Text(
                    text = "No events",
                    style = MaterialTheme.typography.caption2,
                    color = Color.Gray
                )
            }
        } else {
            items(firstHalfEvents.size) { i ->
                EventItem(firstHalfEvents[i])
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

// Shared by HalfTimeScreen, FullTimeScreen, and MatchReportScreen.
// mainFontSp / detailFontSp let callers scale up for readability.
@Composable
internal fun EventItem(
    event: MatchEvent,
    mainFontSp: Float = 13f,
    detailFontSp: Float = 11f
) {
    val (abbrev, color) = when (event.type) {
        EventType.GOAL -> "G" to RefGreen
        EventType.YELLOW_CARD -> "YC" to RefYellow
        EventType.RED_CARD -> "RC" to RefRed
        EventType.SIN_BIN -> "SB" to RefOrange
    }
    Column(modifier = Modifier.fillMaxWidth()) {
        val mainLine = if (event.type == EventType.GOAL) {
            val scorer = if (event.scorerNumber.isNotEmpty()) " #${event.scorerNumber}" else ""
            "${event.matchMinute}'  G$scorer  ${event.team}"
        } else {
            "${event.matchMinute}'  $abbrev  #${event.playerNumber}  ${event.team.take(5)}"
        }
        Text(
            text = mainLine,
            fontSize = mainFontSp.sp,
            fontWeight = FontWeight.Bold,
            color = color,
            modifier = Modifier.fillMaxWidth()
        )
        if (event.scorerName.isNotEmpty()) {
            Text(
                text = event.scorerName,
                fontSize = detailFontSp.sp,
                color = RefGreen.copy(alpha = 0.8f),
                modifier = Modifier.fillMaxWidth()
            )
        }
        if (event.detail.isNotEmpty()) {
            Text(
                text = event.detail,
                fontSize = detailFontSp.sp,
                color = Color.Gray,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
