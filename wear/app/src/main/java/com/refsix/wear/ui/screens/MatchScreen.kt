package com.refsix.wear.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import androidx.wear.compose.material.*
import com.refsix.wear.data.MatchPhase
import com.refsix.wear.ui.theme.*
import com.refsix.wear.viewmodel.MatchViewModel

@Composable
fun MatchScreen(navController: NavController, viewModel: MatchViewModel) {
    val state by viewModel.state.collectAsState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        // Main match content
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.padding(horizontal = 8.dp)
        ) {
            Text(
                text = when (state.phase) {
                    MatchPhase.FIRST_HALF -> "1ST HALF"
                    MatchPhase.SECOND_HALF -> "2ND HALF"
                    else -> "MATCH"
                },
                style = MaterialTheme.typography.caption2,
                color = Color.Gray
            )

            Text(
                text = "%02d:%02d".format(state.displayMinutes, state.displaySeconds),
                fontSize = 36.sp,
                fontWeight = FontWeight.Bold,
                color = if (state.isRunning) Color.White else Color.Gray
            )

            if (state.isInAdditionalTime) {
                Text(
                    text = "+%02d:%02d".format(
                        state.additionalSeconds / 60,
                        state.additionalSeconds % 60
                    ),
                    fontSize = 14.sp,
                    color = RefYellow,
                    fontWeight = FontWeight.Bold
                )
            }

            Text(
                text = "${state.homeScore}  –  ${state.awayScore}",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Text(
                    text = state.homeTeam.take(6),
                    style = MaterialTheme.typography.caption2,
                    color = Color.Gray,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = state.awayTeam.take(6),
                    style = MaterialTheme.typography.caption2,
                    color = Color.Gray,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (state.activeSinBins.isNotEmpty()) {
                Text(
                    text = "[ ${state.activeSinBins.size} IN SIN BIN ]",
                    style = MaterialTheme.typography.caption2,
                    color = RefOrange
                )
            }

            Spacer(modifier = Modifier.height(2.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                CompactChip(
                    label = { Text("GOAL", fontWeight = FontWeight.Bold) },
                    onClick = { navController.navigate("goal") },
                    colors = ChipDefaults.chipColors(backgroundColor = RefGreen)
                )
                CompactChip(
                    label = { Text("CARD", fontWeight = FontWeight.Bold) },
                    onClick = { navController.navigate("card") },
                    colors = ChipDefaults.chipColors(backgroundColor = RefYellow)
                )
                CompactChip(
                    label = { Text("SIN", fontWeight = FontWeight.Bold) },
                    onClick = { navController.navigate("sinBin") },
                    colors = ChipDefaults.chipColors(backgroundColor = RefOrange)
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                CompactChip(
                    label = {
                        Text(
                            text = if (state.isRunning) "PAUSE" else "START",
                            fontWeight = FontWeight.Bold
                        )
                    },
                    onClick = { viewModel.toggleTimer() },
                    colors = ChipDefaults.chipColors(backgroundColor = RefBlue)
                )
                CompactChip(
                    label = {
                        Text(
                            text = if (state.currentHalf == 1) "H/T" else "F/T",
                            fontWeight = FontWeight.Bold
                        )
                    },
                    onClick = {
                        if (state.currentHalf == 1) {
                            viewModel.callHalfTime()
                            navController.navigate("halfTime")
                        } else {
                            viewModel.callFullTime()
                            navController.navigate("fullTime")
                        }
                    },
                    colors = ChipDefaults.chipColors(backgroundColor = Color(0xFF9C27B0))
                )
            }
        }

        // Second yellow → red card alert overlay
        state.secondYellowAlert?.let { alert ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xEE000000)),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF1A0000))
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Text(
                        text = "2ND YELLOW",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = RefYellow,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = "AUTO RED CARD",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = RefRed,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = "#${alert.playerNumber}",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = alert.team,
                        style = MaterialTheme.typography.caption1,
                        color = Color.Gray,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = "Player dismissed",
                        style = MaterialTheme.typography.caption2,
                        color = Color.Gray,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Chip(
                        label = { Text("OK", fontWeight = FontWeight.Bold) },
                        onClick = { viewModel.dismissSecondYellowAlert() },
                        colors = ChipDefaults.chipColors(backgroundColor = RefRed),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}
