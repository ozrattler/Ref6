package com.refsix.wear.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.material.*
import com.refsix.wear.ui.theme.RefGreen
import com.refsix.wear.viewmodel.MatchViewModel

@Composable
fun GoalScreen(viewModel: MatchViewModel, onGoalRecorded: () -> Unit) {
    val state by viewModel.state.collectAsState()

    ScalingLazyColumn(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 28.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
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
                text = "Which team?",
                style = MaterialTheme.typography.caption1
            )
        }

        item {
            Chip(
                label = {
                    Text(
                        text = state.homeTeam,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                },
                secondaryLabel = { Text("Home") },
                onClick = {
                    viewModel.recordGoal(state.homeTeam)
                    onGoalRecorded()
                },
                colors = ChipDefaults.chipColors(backgroundColor = RefGreen),
                modifier = Modifier.fillMaxWidth()
            )
        }

        item {
            Chip(
                label = {
                    Text(
                        text = state.awayTeam,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                },
                secondaryLabel = { Text("Away") },
                onClick = {
                    viewModel.recordGoal(state.awayTeam)
                    onGoalRecorded()
                },
                colors = ChipDefaults.chipColors(backgroundColor = RefGreen),
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
