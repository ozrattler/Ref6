package com.refsix.wear.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.material.*
import com.refsix.wear.ui.theme.RefBlue
import com.refsix.wear.ui.theme.RefGreen
import com.refsix.wear.viewmodel.MatchViewModel
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun MatchHistoryScreen(viewModel: MatchViewModel, navController: NavController) {
    val matches by viewModel.savedMatches.collectAsState()
    val dateFormat = remember { SimpleDateFormat("d MMM yyyy", Locale.getDefault()) }

    ScalingLazyColumn(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 28.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        item {
            Text(
                text = "HISTORY",
                style = MaterialTheme.typography.title1,
                color = RefBlue,
                fontWeight = FontWeight.Bold
            )
        }

        if (matches.isEmpty()) {
            item {
                Text(
                    text = "No saved matches",
                    style = MaterialTheme.typography.body2,
                    color = Color.Gray
                )
            }
        } else {
            items(matches.size) { i ->
                val m = matches[i]
                Chip(
                    label = {
                        Text(
                            text = "${m.homeTeam}  ${m.homeScore}–${m.awayScore}  ${m.awayTeam}",
                            fontWeight = FontWeight.Bold
                        )
                    },
                    secondaryLabel = {
                        Text(
                            text = dateFormat.format(Date(m.dateMillis)),
                            color = Color.Gray
                        )
                    },
                    onClick = { navController.navigate("report/$i") },
                    colors = ChipDefaults.chipColors(backgroundColor = Color(0xFF1E2A1E)),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}
