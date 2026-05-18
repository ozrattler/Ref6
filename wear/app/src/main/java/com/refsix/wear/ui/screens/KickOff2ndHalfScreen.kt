package com.refsix.wear.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.*
import com.refsix.wear.ui.theme.RefGreen
import com.refsix.wear.viewmodel.MatchViewModel

@Composable
fun KickOff2ndHalfScreen(viewModel: MatchViewModel, onStartSecondHalf: () -> Unit) {
    val state by viewModel.state.collectAsState()
    val kickOffTeam = state.kickOffTeam2ndHalf

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically)
    ) {
        Text(
            text = "2ND HALF",
            style = MaterialTheme.typography.title1,
            color = RefGreen,
            fontWeight = FontWeight.Bold
        )

        if (kickOffTeam.isNotEmpty()) {
            Text(
                text = "Kick-off:",
                style = MaterialTheme.typography.caption1,
                color = Color.Gray
            )
            Text(
                text = kickOffTeam,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

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
