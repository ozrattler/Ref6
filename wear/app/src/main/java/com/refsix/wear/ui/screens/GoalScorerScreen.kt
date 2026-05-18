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
import com.refsix.wear.ui.theme.*
import com.refsix.wear.viewmodel.MatchViewModel

private val GOAL_TYPES = listOf("Ordinary", "Penalty", "DFK", "Own Goal")

@Composable
fun GoalScorerScreen(
    viewModel: MatchViewModel,
    teamKey: String,
    onDone: () -> Unit
) {
    val state by viewModel.state.collectAsState()
    val team = if (teamKey == "home") state.homeTeam else state.awayTeam

    var selectedGoalType by remember { mutableStateOf("Ordinary") }
    var playerNumber by remember { mutableIntStateOf(1) }

    ScalingLazyColumn(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 28.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        item {
            Text(
                text = "GOAL",
                style = MaterialTheme.typography.title1,
                color = RefGreen,
                fontWeight = FontWeight.Bold
            )
        }

        item {
            Text(
                text = team.uppercase(),
                style = MaterialTheme.typography.body1,
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
        }

        // Goal type — 2×2 grid of compact chips
        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                GOAL_TYPES.chunked(2).forEach { row ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        row.forEach { gt ->
                            val isSelected = gt == selectedGoalType
                            CompactChip(
                                label = {
                                    Text(
                                        text = gt,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        fontSize = 11.sp,
                                        maxLines = 1
                                    )
                                },
                                onClick = { selectedGoalType = gt },
                                colors = ChipDefaults.chipColors(
                                    backgroundColor = if (isSelected) RefGreen else Color(0xFF2A2A2A)
                                ),
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }

        item {
            Text(
                text = "Player #",
                style = MaterialTheme.typography.caption2,
                color = Color.Gray,
                modifier = Modifier.fillMaxWidth()
            )
        }

        item {
            PlayerNumberPicker(
                value = playerNumber,
                onValueChange = { playerNumber = it },
                fontSize = 24.sp
            )
        }

        item { Spacer(modifier = Modifier.height(2.dp)) }

        item {
            Chip(
                label = { Text("CONFIRM GOAL", fontWeight = FontWeight.Bold) },
                onClick = {
                    viewModel.recordGoal(team, "$playerNumber", goalType = selectedGoalType)
                    onDone()
                },
                colors = ChipDefaults.chipColors(backgroundColor = RefGreen),
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
