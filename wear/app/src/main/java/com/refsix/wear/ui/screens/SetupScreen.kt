package com.refsix.wear.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.material.*
import com.refsix.wear.data.AgeGroup
import com.refsix.wear.data.CompetitionType
import com.refsix.wear.ui.theme.*
import com.refsix.wear.viewmodel.MatchViewModel

@Composable
fun SetupScreen(viewModel: MatchViewModel, onStartMatch: () -> Unit, onShowHistory: () -> Unit = {}) {
    val state by viewModel.state.collectAsState()

    var homeTeam by remember { mutableStateOf(state.homeTeam) }
    var awayTeam by remember { mutableStateOf(state.awayTeam) }
    var ageGroup by remember { mutableStateOf(state.ageGroup) }
    var sinBinMinutes by remember { mutableIntStateOf(state.sinBinMinutes) }
    var competitionType by remember { mutableStateOf(state.competitionType) }

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

        item { FieldLabel("Home Team") }
        item { TeamNameField(value = homeTeam, onValueChange = { homeTeam = it }) }

        item { FieldLabel("Away Team") }
        item { TeamNameField(value = awayTeam, onValueChange = { awayTeam = it }) }

        item { FieldLabel("Age Group") }
        item {
            AgeGroupPicker(
                selected = ageGroup,
                onSelect = { group ->
                    ageGroup = group
                    sinBinMinutes = group.sinBinMinutes
                }
            )
        }

        item { FieldLabel("Competition") }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                CompetitionChip(
                    label = "Standard",
                    selected = competitionType == CompetitionType.STANDARD,
                    onClick = { competitionType = CompetitionType.STANDARD }
                )
                CompetitionChip(
                    label = "SPL",
                    selected = competitionType == CompetitionType.SPL,
                    activeColor = Color(0xFF1565C0),
                    onClick = { competitionType = CompetitionType.SPL }
                )
            }
        }

        item { FieldLabel("Sin Bin Duration") }
        item {
            StepperRow(
                value = sinBinMinutes,
                label = "$sinBinMinutes min",
                onDecrement = { if (sinBinMinutes > 1) sinBinMinutes-- },
                onIncrement = { if (sinBinMinutes < 30) sinBinMinutes++ }
            )
        }

        item { Spacer(modifier = Modifier.height(4.dp)) }

        item {
            Chip(
                label = { Text("START MATCH", fontWeight = FontWeight.Bold) },
                onClick = {
                    viewModel.updateSetup(homeTeam, awayTeam, ageGroup.defaultHalfMinutes, ageGroup, sinBinMinutes, competitionType)
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
private fun FieldLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.caption1,
        color = Color.Gray
    )
}

@Composable
private fun StepperRow(value: Int, label: String, onDecrement: () -> Unit, onIncrement: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        CompactChip(label = { Text("−") }, onClick = onDecrement)
        Text(
            text = label,
            style = MaterialTheme.typography.body1,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.width(56.dp),
            textAlign = TextAlign.Center
        )
        CompactChip(label = { Text("+") }, onClick = onIncrement)
    }
}

@Composable
private fun AgeGroupPicker(selected: AgeGroup, onSelect: (AgeGroup) -> Unit) {
    val groups = AgeGroup.entries
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            groups.take(3).forEach { group ->
                AgeGroupChip(
                    label = if (group == AgeGroup.OPEN_SENIOR) "Open" else group.label,
                    selected = selected == group,
                    onClick = { onSelect(group) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            groups.drop(3).forEach { group ->
                AgeGroupChip(
                    label = group.label,
                    selected = selected == group,
                    onClick = { onSelect(group) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun AgeGroupChip(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(if (selected) RefGreen else Color(0xFF2A2A2A))
            .border(1.dp, if (selected) RefGreen else Color(0xFF444444), RoundedCornerShape(6.dp))
            .clickable { onClick() }
            .padding(vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = if (selected) Color.Black else Color.White,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun CompetitionChip(
    label: String,
    selected: Boolean,
    activeColor: Color = RefGreen,
    onClick: () -> Unit
) {
    CompactChip(
        label = { Text(label, fontWeight = FontWeight.Bold) },
        onClick = onClick,
        colors = if (selected)
            ChipDefaults.chipColors(backgroundColor = activeColor)
        else
            ChipDefaults.chipColors(backgroundColor = Color(0xFF333333))
    )
}

@Composable
private fun TeamNameField(value: String, onValueChange: (String) -> Unit) {
    BasicTextField(
        value = value,
        onValueChange = { if (it.length <= 12) onValueChange(it) },
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
