package com.refsix.wear.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.material.*
import com.refsix.wear.data.MatchRole
import com.refsix.wear.ui.theme.RefGreen
import com.refsix.wear.viewmodel.MatchViewModel

@Composable
fun MatchSetupListScreen(
    viewModel: MatchViewModel,
    onSetupSelected: () -> Unit,
    onCancel: () -> Unit
) {
    val setups by viewModel.pendingSetups.collectAsState()
    val isFetching by viewModel.isFetchingSetups.collectAsState()

    var selectedSetup by remember { mutableStateOf<com.refsix.wear.data.MatchSetupData?>(null) }

    // Sort ascending by date then time so earliest kickoff appears first.
    val sortedSetups = remember(setups) {
        setups.sortedWith(compareBy(
            { it.kickoffDate.ifBlank { "9999-99-99" } },
            { parseKickoffMinutes(it.kickoffTime) }
        ))
    }

    LaunchedEffect(Unit) { viewModel.refreshPendingSetup() }

    // ── Role picker (shown after a fixture is tapped) ─────────────────────────
    if (selectedSetup != null) {
        val setup = selectedSetup!!
        ScalingLazyColumn(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 28.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                Text(
                    text = "LOAD AS",
                    style = MaterialTheme.typography.title3,
                    color = RefGreen,
                    fontWeight = FontWeight.Bold
                )
            }
            item {
                Text(
                    text = "${setup.homeTeam.ifBlank { "?" }} vs ${setup.awayTeam.ifBlank { "?" }}",
                    style = MaterialTheme.typography.body2,
                    color = Color.LightGray,
                    fontWeight = FontWeight.Bold
                )
            }
            item {
                Chip(
                    label = { Text("Referee", fontWeight = FontWeight.Bold) },
                    onClick = {
                        viewModel.applyMatchSetup(setup, MatchRole.REFEREE)
                        onSetupSelected()
                    },
                    colors = ChipDefaults.chipColors(backgroundColor = Color(0xFF1B4D1B)),
                    modifier = Modifier.fillMaxWidth()
                )
            }
            item {
                Chip(
                    label = { Text("4th Official", fontWeight = FontWeight.Bold) },
                    onClick = {
                        viewModel.applyMatchSetup(setup, MatchRole.FOURTH_OFFICIAL)
                        onSetupSelected()
                    },
                    colors = ChipDefaults.chipColors(backgroundColor = Color(0xFF1A3A5C)),
                    modifier = Modifier.fillMaxWidth()
                )
            }
            item {
                CompactChip(
                    label = { Text("Back", fontWeight = FontWeight.Bold) },
                    onClick = { selectedSetup = null },
                    colors = ChipDefaults.chipColors(backgroundColor = Color(0xFF2A2A2A))
                )
            }
        }
        return
    }

    // ── Fixture list ──────────────────────────────────────────────────────────
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

        when {
            isFetching -> item {
                Text(
                    text = "Checking…",
                    style = MaterialTheme.typography.body2,
                    color = Color.Gray
                )
            }

            sortedSetups.isEmpty() -> item {
                Text(
                    text = "No setups available",
                    style = MaterialTheme.typography.body2,
                    color = Color.Gray
                )
            }

            else -> items(sortedSetups.size) { i ->
                val setup = sortedSetups[i]

                val day   = kickoffDayAbbrev(setup.kickoffDate)
                val field = setup.field
                val grade = setup.gradeCode
                val time  = formatTime12h(setup.kickoffTime)

                // Build text segments around the grade badge:
                //   preGradeText  → [badge] → postGradeText
                // e.g. "SUN · Ridge 5 · "  [AM04]  " · 11:40AM"
                val preGradeParts = listOf(day, field).filter { it.isNotBlank() }
                val preGradeText = when {
                    preGradeParts.isEmpty() -> ""
                    grade.isNotBlank() || time.isNotBlank() ->
                        preGradeParts.joinToString(" · ") + " · "
                    else -> preGradeParts.joinToString(" · ")
                }
                val postGradeText = when {
                    time.isBlank()          -> ""
                    grade.isNotBlank()      -> " · $time"
                    else                    -> time
                }
                val hasSecondaryLabel =
                    preGradeText.isNotBlank() || grade.isNotBlank() || postGradeText.isNotBlank()

                Chip(
                    label = {
                        Text(
                            text = "${setup.homeTeam.ifBlank { "?" }} vs ${setup.awayTeam.ifBlank { "?" }}",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            modifier = Modifier.fillMaxWidth()
                        )
                    },
                    secondaryLabel = if (hasSecondaryLabel) {
                        {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (preGradeText.isNotBlank()) {
                                    Text(preGradeText, fontSize = 10.sp, color = Color.LightGray)
                                }
                                if (grade.isNotBlank()) {
                                    Box(
                                        modifier = Modifier
                                            .background(Color(0xFF2E7D32), RoundedCornerShape(4.dp))
                                            .padding(horizontal = 5.dp, vertical = 1.dp)
                                    ) {
                                        Text(
                                            text = grade,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 10.sp,
                                            color = Color.White
                                        )
                                    }
                                }
                                if (postGradeText.isNotBlank()) {
                                    Text(postGradeText, fontSize = 10.sp, color = Color.LightGray)
                                }
                            }
                        }
                    } else null,
                    onClick = { selectedSetup = setup },
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

// Returns minutes since midnight for sort ordering; unparseable times sort last.
private fun parseKickoffMinutes(timeStr: String): Int {
    if (timeStr.isBlank()) return Int.MAX_VALUE
    return try {
        val upper = timeStr.trim().uppercase()
        val isPm = upper.contains("PM")
        val isAm = upper.contains("AM")
        val digits = upper.replace("AM", "").replace("PM", "").trim()
        val parts = digits.split(":")
        var h = parts[0].trim().toInt()
        val m = parts.getOrNull(1)?.trim()?.toInt() ?: 0
        if (isPm && h != 12) h += 12
        if (isAm && h == 12) h = 0
        h * 60 + m
    } catch (_: Exception) { Int.MAX_VALUE }
}
