package com.refsix.wear.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.material.*
import com.refsix.wear.ui.theme.RefGreen
import com.refsix.wear.ui.theme.RefOrange
import com.refsix.wear.viewmodel.MatchViewModel

@Composable
fun SinBinScreen(viewModel: MatchViewModel, onDismiss: () -> Unit) {
    val state by viewModel.state.collectAsState()
    val active = state.activeSinBins

    ScalingLazyColumn(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 28.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        item {
            Text(
                text = "SIN BIN",
                style = MaterialTheme.typography.title1,
                color = RefOrange,
                fontWeight = FontWeight.Bold
            )
        }

        if (active.isEmpty()) {
            item {
                Text(
                    text = "No players in\nsin bin",
                    style = MaterialTheme.typography.body2,
                    color = Color.Gray
                )
            }
        } else {
            items(active.size) { index ->
                val entry = active[index]
                val remaining = entry.remainingSeconds(state.totalElapsedSeconds)
                val mins = remaining / 60
                val secs = remaining % 60

                Card(
                    onClick = {},
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(8.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "#${entry.playerNumber} ${entry.team.take(5)}",
                                style = MaterialTheme.typography.caption1,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "%02d:%02d".format(mins, secs),
                                style = MaterialTheme.typography.caption1,
                                color = if (remaining < 60) RefOrange else RefGreen,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Text(
                            text = entry.offence,
                            style = MaterialTheme.typography.caption2,
                            color = Color.Gray
                        )
                        CompactChip(
                            label = { Text("Returned") },
                            onClick = { viewModel.returnFromSinBin(entry.id) },
                            colors = ChipDefaults.chipColors(backgroundColor = RefGreen),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }

        item {
            CompactChip(
                label = { Text("Back to Match") },
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
