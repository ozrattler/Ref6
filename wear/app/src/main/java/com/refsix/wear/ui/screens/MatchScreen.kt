package com.refsix.wear.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.padding(horizontal = 8.dp)
        ) {
            // Half indicator
            Text(
                text = when (state.phase) {
                    MatchPhase.FIRST_HALF -> "1ST HALF"
                    MatchPhase.SECOND_HALF -> "2ND HALF"
                    else -> "MATCH"
                },
                style = MaterialTheme.typography.caption2,
                color = Color.Gray
            )

            // Timer
            Text(
                text = "%02d:%02d".format(state.displayMinutes, state.displaySeconds),
                fontSize = 36.sp,
                fontWeight = FontWeight.Bold,
                color = if (state.isRunning) Color.White else Color.Gray
            )

            // Additional time
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

            // Score
            Text(
                text = "${state.homeScore}  –  ${state.awayScore}",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )

            // Team names
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

            // Sin bin badge
            if (state.activeSinBins.isNotEmpty()) {
                Text(
                    text = "[ ${state.activeSinBins.size} IN SIN BIN ]",
                    style = MaterialTheme.typography.caption2,
                    color = RefOrange
                )
            }

            Spacer(modifier = Modifier.height(2.dp))

            // Action row 1
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

            // Action row 2
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
    }
}
