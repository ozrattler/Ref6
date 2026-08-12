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

@Composable
fun ExtraTimeOfferScreen(
    viewModel: MatchViewModel,
    onStartExtraTime: () -> Unit,
    onEndMatch: () -> Unit
) {
    val state by viewModel.state.collectAsState()
    val etMinutes = state.extraTimeHalfMinutes

    ScalingLazyColumn(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 28.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        item {
            Text(
                text = "EXTRA TIME?",
                style = MaterialTheme.typography.title3,
                color = RefYellow,
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
                Text(state.homeTeam.take(4), style = MaterialTheme.typography.caption1, color = Color.Gray)
                Text(state.awayTeam.take(4), style = MaterialTheme.typography.caption1, color = Color.Gray)
            }
        }
        item {
            Text(
                text = "2 × $etMinutes min",
                style = MaterialTheme.typography.caption1,
                color = Color.LightGray
            )
        }
        item { Spacer(modifier = Modifier.height(4.dp)) }
        item {
            Chip(
                label = { Text("Start ET", fontWeight = FontWeight.Bold) },
                onClick = {
                    viewModel.startExtraTime()
                    onStartExtraTime()
                },
                colors = ChipDefaults.chipColors(backgroundColor = RefGreen),
                modifier = Modifier.fillMaxWidth()
            )
        }
        item {
            Chip(
                label = { Text("End Match", fontWeight = FontWeight.Bold) },
                onClick = {
                    viewModel.callFullTime()
                    onEndMatch()
                },
                colors = ChipDefaults.chipColors(backgroundColor = Color(0xFF4A1A1A)),
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
