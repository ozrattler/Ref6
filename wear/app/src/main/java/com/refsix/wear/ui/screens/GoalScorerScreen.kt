package com.refsix.wear.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
    var scorerName by remember { mutableStateOf("") }

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
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CompactChip(
                    label = { Text("−") },
                    onClick = { if (playerNumber > 1) playerNumber-- }
                )
                Text(
                    text = "$playerNumber",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier.width(40.dp),
                    textAlign = TextAlign.Center
                )
                CompactChip(
                    label = { Text("+") },
                    onClick = { if (playerNumber < 99) playerNumber++ }
                )
            }
        }

        item {
            Text(
                text = "Name (optional)",
                style = MaterialTheme.typography.caption2,
                color = Color.Gray,
                modifier = Modifier.fillMaxWidth()
            )
        }

        item {
            BasicTextField(
                value = scorerName,
                onValueChange = { if (it.length <= 20) scorerName = it },
                textStyle = TextStyle(
                    color = Color.White,
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center
                ),
                cursorBrush = SolidColor(RefGreen),
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF2A2A2A), shape = RoundedCornerShape(8.dp))
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            )
        }

        item { Spacer(modifier = Modifier.height(2.dp)) }

        item {
            Chip(
                label = { Text("CONFIRM GOAL", fontWeight = FontWeight.Bold) },
                onClick = {
                    viewModel.recordGoal(team, "$playerNumber", scorerName.trim(), selectedGoalType)
                    onDone()
                },
                colors = ChipDefaults.chipColors(backgroundColor = RefGreen),
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
