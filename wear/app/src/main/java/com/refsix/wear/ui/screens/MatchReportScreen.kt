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
import com.refsix.wear.data.EventType
import com.refsix.wear.data.SavedMatch
import com.refsix.wear.ui.theme.*
import com.refsix.wear.viewmodel.MatchViewModel
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun MatchReportScreen(viewModel: MatchViewModel, index: Int, onDone: () -> Unit) {
    val matches by viewModel.savedMatches.collectAsState()
    val match = matches.getOrNull(index) ?: run {
        // Match no longer exists, exit immediately
        LaunchedEffect(Unit) { onDone() }
        return
    }

    MatchReportContent(match = match, onDone = onDone)
}

@Composable
fun MatchReportContent(match: SavedMatch, onDone: () -> Unit) {
    val dateFormat = remember { SimpleDateFormat("d MMM yyyy", Locale.getDefault()) }
    val allEvents = remember(match) { match.events.sortedBy { it.matchMinute } }

    val goalCount = allEvents.count { it.type == EventType.GOAL }
    val yellowCount = allEvents.count { it.type == EventType.YELLOW_CARD }
    val redCount = allEvents.count { it.type == EventType.RED_CARD }
    val sinCount = allEvents.count { it.type == EventType.SIN_BIN }

    ScalingLazyColumn(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 28.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        item {
            Text(
                text = "MATCH REPORT",
                style = MaterialTheme.typography.title2,
                color = RefGreen,
                fontWeight = FontWeight.Bold
            )
        }

        item {
            Text(
                text = dateFormat.format(Date(match.dateMillis)),
                style = MaterialTheme.typography.caption1,
                color = Color.Gray
            )
        }

        item {
            Text(
                text = "${match.homeScore}  –  ${match.awayScore}",
                fontSize = 36.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }

        item {
            Row(
                horizontalArrangement = Arrangement.SpaceEvenly,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(match.homeTeam, style = MaterialTheme.typography.caption1, color = Color.Gray)
                Text(match.awayTeam, style = MaterialTheme.typography.caption1, color = Color.Gray)
            }
        }

        item {
            Row(
                horizontalArrangement = Arrangement.SpaceEvenly,
                modifier = Modifier.fillMaxWidth()
            ) {
                StatBadge2("$goalCount", "G", RefGreen)
                StatBadge2("$yellowCount", "YC", RefYellow)
                StatBadge2("$redCount", "RC", RefRed)
                StatBadge2("$sinCount", "SB", RefOrange)
            }
        }

        if (allEvents.isEmpty()) {
            item {
                Text(
                    text = "No events recorded",
                    style = MaterialTheme.typography.caption1,
                    color = Color.Gray
                )
            }
        } else {
            items(allEvents.size) { i ->
                EventItem(event = allEvents[i], mainFontSp = 15f, detailFontSp = 13f)
            }
        }

        item { Spacer(modifier = Modifier.height(4.dp)) }

        item {
            Chip(
                label = { Text("Done", fontWeight = FontWeight.Bold) },
                onClick = onDone,
                colors = ChipDefaults.chipColors(backgroundColor = RefBlue),
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun StatBadge2(count: String, label: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = count, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = color)
        Text(text = label, style = MaterialTheme.typography.caption2, color = Color.Gray)
    }
}
