package com.refsix.wear.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.material.*
import com.refsix.wear.ui.theme.*
import com.refsix.wear.viewmodel.MatchViewModel

@Composable
fun AddEventScreen(
    viewModel: MatchViewModel,
    navController: NavController
) {
    val state by viewModel.state.collectAsState()

    fun backToMatch() {
        navController.popBackStack()
    }

    ScalingLazyColumn(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 28.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        item {
            Text(
                text = "ADD EVENT",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = RefGreen
            )
        }

        // Home team row
        item {
            Text(
                text = state.homeTeam.take(8).uppercase(),
                style = MaterialTheme.typography.caption2,
                color = Color.LightGray
            )
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                CompactChip(
                    label = { Text("GOAL", fontWeight = FontWeight.Bold) },
                    onClick = {
                        if (state.isSpl) {
                            navController.navigate("goalScorer/home")
                        } else {
                            viewModel.recordGoal(state.homeTeam)
                            backToMatch()
                        }
                    },
                    colors = ChipDefaults.chipColors(backgroundColor = Color(0xFF2E7D32))
                )
                CompactChip(
                    label = { Text("YC", fontWeight = FontWeight.Bold) },
                    onClick = { navController.navigate("card/home/YELLOW") },
                    colors = ChipDefaults.chipColors(backgroundColor = RefYellow)
                )
                CompactChip(
                    label = { Text("RC", fontWeight = FontWeight.Bold) },
                    onClick = { navController.navigate("card/home/RED") },
                    colors = ChipDefaults.chipColors(backgroundColor = RefRed)
                )
            }
        }

        // Away team row
        item {
            Text(
                text = state.awayTeam.take(8).uppercase(),
                style = MaterialTheme.typography.caption2,
                color = Color.LightGray
            )
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                CompactChip(
                    label = { Text("GOAL", fontWeight = FontWeight.Bold) },
                    onClick = {
                        if (state.isSpl) {
                            navController.navigate("goalScorer/away")
                        } else {
                            viewModel.recordGoal(state.awayTeam)
                            backToMatch()
                        }
                    },
                    colors = ChipDefaults.chipColors(backgroundColor = Color(0xFF2E7D32))
                )
                CompactChip(
                    label = { Text("YC", fontWeight = FontWeight.Bold) },
                    onClick = { navController.navigate("card/away/YELLOW") },
                    colors = ChipDefaults.chipColors(backgroundColor = RefYellow)
                )
                CompactChip(
                    label = { Text("RC", fontWeight = FontWeight.Bold) },
                    onClick = { navController.navigate("card/away/RED") },
                    colors = ChipDefaults.chipColors(backgroundColor = RefRed)
                )
            }
        }

        item { Spacer(modifier = Modifier.height(4.dp)) }

        item {
            CompactChip(
                label = { Text("Cancel", fontWeight = FontWeight.Bold) },
                onClick = { backToMatch() },
                colors = ChipDefaults.chipColors(backgroundColor = Color(0xFF333333))
            )
        }
    }
}
