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
import com.refsix.wear.ui.theme.RefGreen
import com.refsix.wear.viewmodel.MatchViewModel

@Composable
fun SetupScreen(viewModel: MatchViewModel, onStartMatch: () -> Unit) {
    val state by viewModel.state.collectAsState()

    var homeTeam by remember { mutableStateOf(state.homeTeam) }
    var awayTeam by remember { mutableStateOf(state.awayTeam) }
    var halfLength by remember { mutableIntStateOf(state.halfLengthMinutes) }
    var ageGroup by remember { mutableStateOf(state.ageGroup) }

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

        item {
            Text(
                text = "Home Team",
                style = MaterialTheme.typography.caption1,
                color = Color.Gray
            )
        }
        item {
            TeamNameField(value = homeTeam, onValueChange = { homeTeam = it })
        }

        item {
            Text(
                text = "Away Team",
                style = MaterialTheme.typography.caption1,
                color = Color.Gray
            )
        }
        item {
            TeamNameField(value = awayTeam, onValueChange = { awayTeam = it })
        }

        item {
            Text(
                text = "Age Group",
                style = MaterialTheme.typography.caption1,
                color = Color.Gray
            )
        }
        item {
            AgeGroupPicker(selected = ageGroup, onSelect = { ageGroup = it })
        }

        item {
            Text(
                text = "Half Length",
                style = MaterialTheme.typography.caption1,
                color = Color.Gray
            )
        }
        item {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CompactChip(
                    label = { Text("−") },
                    onClick = { if (halfLength > 10) halfLength-- }
                )
                Text(
                    text = "$halfLength min",
                    style = MaterialTheme.typography.body1,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.width(52.dp),
                    textAlign = TextAlign.Center
                )
                CompactChip(
                    label = { Text("+") },
                    onClick = { if (halfLength < 60) halfLength++ }
                )
            }
        }

        item {
            Text(
                text = "Sin bin: ${ageGroup.sinBinMinutes} min",
                style = MaterialTheme.typography.caption2,
                color = Color.Gray
            )
        }

        item { Spacer(modifier = Modifier.height(4.dp)) }

        item {
            Chip(
                label = { Text("START MATCH", fontWeight = FontWeight.Bold) },
                onClick = {
                    viewModel.updateSetup(homeTeam, awayTeam, halfLength, ageGroup)
                    viewModel.startMatch()
                    onStartMatch()
                },
                colors = ChipDefaults.chipColors(backgroundColor = RefGreen),
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun AgeGroupPicker(selected: AgeGroup, onSelect: (AgeGroup) -> Unit) {
    val groups = AgeGroup.entries
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        // Row 1: Open/Senior, U18, U16
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
        // Row 2: U14, U12, U10
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
            .border(
                width = if (selected) 0.dp else 1.dp,
                color = Color(0xFF444444),
                shape = RoundedCornerShape(6.dp)
            )
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
