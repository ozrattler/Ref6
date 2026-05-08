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
import com.refsix.wear.ui.theme.RefGreen
import com.refsix.wear.viewmodel.MatchViewModel

@Composable
fun MatchSetupListScreen(
    viewModel: MatchViewModel,
    onSetupSelected: () -> Unit,
    onCancel: () -> Unit
) {
    val setups by viewModel.pendingSetups.collectAsState()

    ScalingLazyColumn(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 28.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        item {
            Text(
                text = "LOAD SETUP",
                style = MaterialTheme.typography.title3,
                color = RefGreen,
                fontWeight = FontWeight.Bold
            )
        }

        if (setups.isEmpty()) {
            item {
                Text(
                    text = "No pending setups",
                    style = MaterialTheme.typography.body2,
                    color = Color.Gray
                )
            }
        } else {
            items(setups.size) { i ->
                val setup = setups[i]
                val detail = listOf(setup.competition, setup.ageGroup.label)
                    .filter { it.isNotBlank() }
                    .joinToString(" · ")
                Chip(
                    label = {
                        Text(
                            text = "${setup.homeTeam.ifBlank { "?" }} vs ${setup.awayTeam.ifBlank { "?" }}",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    },
                    secondaryLabel = if (detail.isNotBlank()) {
                        { Text(text = detail, fontSize = 10.sp) }
                    } else null,
                    onClick = {
                        viewModel.applyMatchSetup(setup)
                        onSetupSelected()
                    },
                    colors = ChipDefaults.chipColors(backgroundColor = Color(0xFF1B4D1B)),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        item { Spacer(modifier = Modifier.height(4.dp)) }

        item {
            CompactChip(
                label = { Text("Cancel", fontWeight = FontWeight.Bold) },
                onClick = onCancel,
                colors = ChipDefaults.chipColors(backgroundColor = Color(0xFF2A2A2A))
            )
        }
    }
}
