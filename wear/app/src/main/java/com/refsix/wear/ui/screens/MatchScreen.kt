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
import com.refsix.wear.data.CardAlertType
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
            verticalArrangement = Arrangement.spacedBy(3.dp),
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

            // Sin bin countdowns — sorted by most urgent first
            val activeBins = state.activeSinBins
                .sortedBy { it.remainingSeconds(state.totalElapsedSeconds) }
            if (activeBins.isNotEmpty()) {
                Spacer(modifier = Modifier.height(1.dp))
                activeBins.take(3).forEach { bin ->
                    val remaining = bin.remainingSeconds(state.totalElapsedSeconds)
                    val urgent = remaining < 60L
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "SIN  #${bin.playerNumber} ${bin.team.take(5)}  %02d:%02d".format(
                                remaining / 60, remaining % 60
                            ),
                            fontSize = 12.sp,
                            fontWeight = if (urgent) FontWeight.Bold else FontWeight.Normal,
                            color = if (urgent) RefRed else RefOrange
                        )
                    }
                }
                if (activeBins.size > 3) {
                    Text(
                        text = "+${activeBins.size - 3} more in sin bin",
                        style = MaterialTheme.typography.caption2,
                        color = Color.Gray
                    )
                }
            }

            Spacer(modifier = Modifier.height(1.dp))

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

        // Card alert overlay (2nd yellow or dissent auto sin bin)
        state.cardAlert?.let { alert ->
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
                    when (alert.type) {
                        CardAlertType.SECOND_YELLOW_RED -> {
                            Text("2ND YELLOW", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = RefYellow, textAlign = TextAlign.Center)
                            Text("AUTO RED CARD", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = RefRed, textAlign = TextAlign.Center)
                            Text("Player dismissed", style = MaterialTheme.typography.caption2, color = Color.Gray, textAlign = TextAlign.Center)
                        }
                        CardAlertType.SECOND_YELLOW_SIN_BIN -> {
                            Text("2ND YELLOW", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = RefYellow, textAlign = TextAlign.Center)
                            Text("SIN BIN", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = RefOrange, textAlign = TextAlign.Center)
                            Text("${alert.sinBinMinutes} min — escalate to red if needed", style = MaterialTheme.typography.caption2, color = Color.Gray, textAlign = TextAlign.Center)
                        }
                        CardAlertType.DISSENT_SIN_BIN -> {
                            Text("DISSENT", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = RefYellow, textAlign = TextAlign.Center)
                            Text("AUTO SIN BIN", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = RefOrange, textAlign = TextAlign.Center)
                            Text("${alert.sinBinMinutes} min", style = MaterialTheme.typography.caption2, color = Color.Gray, textAlign = TextAlign.Center)
                        }
                    }
                    Text(
                        text = "#${alert.playerNumber}  ${alert.team}",
                        style = MaterialTheme.typography.caption1,
                        color = Color.White,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Chip(
                        label = { Text("OK", fontWeight = FontWeight.Bold) },
                        onClick = { viewModel.dismissCardAlert() },
                        colors = ChipDefaults.chipColors(
                            backgroundColor = when (alert.type) {
                                CardAlertType.SECOND_YELLOW_RED -> RefRed
                                else -> RefOrange
                            }
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}
