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

@Composable
fun SetupScreen(
    viewModel: MatchViewModel,
    onStartMatch: () -> Unit,
    onShowHistory: () -> Unit = {},
    onShowSetupList: () -> Unit = {}
) {
    val state by viewModel.state.collectAsState()
    val pendingSetups by viewModel.pendingSetups.collectAsState()
    val appliedSetup by viewModel.appliedSetup.collectAsState()

    var homeTeam by remember { mutableStateOf(state.homeTeam) }
    var awayTeam by remember { mutableStateOf(state.awayTeam) }

    // Refresh pending setups every time this screen is shown.
    LaunchedEffect(Unit) { viewModel.refreshPendingSetup() }

    // Pre-fill team names when returning from the setup list.
    LaunchedEffect(appliedSetup) {
        appliedSetup?.let { setup ->
            homeTeam = setup.homeTeam.ifBlank { "Home" }
            awayTeam = setup.awayTeam.ifBlank { "Away" }
            viewModel.consumeAppliedSetup()
        }
    }

    ScalingLazyColumn(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 28.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        item {
            Text(
                text = "REF6",
                style = MaterialTheme.typography.title1,
                color = RefGreen,
                fontWeight = FontWeight.Bold
            )
        }

        if (pendingSetups.isNotEmpty()) {
            item {
                val count = pendingSetups.size
                Chip(
                    label = {
                        Text(
                            text = if (count == 1) "LOAD SETUP" else "LOAD SETUP ($count)",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    },
                    secondaryLabel = {
                        val s = pendingSetups.first()
                        Text(
                            text = "${s.homeTeam.ifBlank { "?" }} vs ${s.awayTeam.ifBlank { "?" }}" +
                                if (count > 1) " …" else "",
                            fontSize = 10.sp
                        )
                    },
                    onClick = onShowSetupList,
                    colors = ChipDefaults.chipColors(backgroundColor = Color(0xFF1B4D1B)),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        item {
            Text(
                text = "Home Team",
                style = MaterialTheme.typography.caption1,
                color = Color.Gray
            )
        }
        item { TeamNameField(value = homeTeam, onValueChange = { homeTeam = it }) }

        item {
            Text(
                text = "Away Team",
                style = MaterialTheme.typography.caption1,
                color = Color.Gray
            )
        }
        item { TeamNameField(value = awayTeam, onValueChange = { awayTeam = it }) }

        item { Spacer(modifier = Modifier.height(4.dp)) }

        item {
            Chip(
                label = { Text("START MATCH", fontWeight = FontWeight.Bold) },
                onClick = {
                    viewModel.updateSetup(homeTeam, awayTeam)
                    viewModel.startMatch()
                    onStartMatch()
                },
                colors = ChipDefaults.chipColors(backgroundColor = RefGreen),
                modifier = Modifier.fillMaxWidth()
            )
        }

        item {
            CompactChip(
                label = { Text("Match History", fontWeight = FontWeight.Bold) },
                onClick = onShowHistory,
                colors = ChipDefaults.chipColors(backgroundColor = Color(0xFF1A2A3A))
            )
        }
    }
}

@Composable
private fun TeamNameField(value: String, onValueChange: (String) -> Unit) {
    BasicTextField(
        value = value,
        onValueChange = { if (it.length <= 20) onValueChange(it) },
        textStyle = TextStyle(
            color = Color.White,
            fontSize = 16.sp,
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
