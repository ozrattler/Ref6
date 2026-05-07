package com.refsix.wear.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.material.*
import com.refsix.wear.data.CardType
import com.refsix.wear.data.Offences
import com.refsix.wear.ui.theme.*
import com.refsix.wear.viewmodel.MatchViewModel

@Composable
fun CardScreen(viewModel: MatchViewModel, onCardRecorded: () -> Unit) {
    val state by viewModel.state.collectAsState()

    var selectedTeam by remember { mutableStateOf<String?>(null) }
    var selectedCard by remember { mutableStateOf<CardType?>(null) }
    var playerNumber by remember { mutableIntStateOf(1) }
    var selectedOffence by remember { mutableStateOf<String?>(null) }

    ScalingLazyColumn(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 28.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // Title
        item {
            Text(
                text = "CARD",
                style = MaterialTheme.typography.title1,
                fontWeight = FontWeight.Bold,
                color = RefYellow
            )
        }

        // Step 1: Team
        item {
            SectionLabel("1. Team")
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                SelectChip(
                    label = state.homeTeam.take(6),
                    selected = selectedTeam == state.homeTeam,
                    onClick = { selectedTeam = state.homeTeam }
                )
                SelectChip(
                    label = state.awayTeam.take(6),
                    selected = selectedTeam == state.awayTeam,
                    onClick = { selectedTeam = state.awayTeam }
                )
            }
        }

        // Step 2: Card type
        item { SectionLabel("2. Card Type") }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                CardTypeChip("YEL", CardType.YELLOW, RefYellow, selectedCard) {
                    selectedCard = it
                    selectedOffence = null
                }
                CardTypeChip("RED", CardType.RED, RefRed, selectedCard) {
                    selectedCard = it
                    selectedOffence = null
                }
                CardTypeChip("SIN", CardType.SIN_BIN, RefOrange, selectedCard) {
                    selectedCard = it
                    selectedOffence = null
                }
            }
        }

        // Step 3: Player number
        item { SectionLabel("3. Player #") }
        item {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CompactChip(
                    label = { Text("−") },
                    onClick = { if (playerNumber > 1) playerNumber-- }
                )
                Text(
                    text = "$playerNumber",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.width(36.dp),
                    textAlign = TextAlign.Center
                )
                CompactChip(
                    label = { Text("+") },
                    onClick = { if (playerNumber < 99) playerNumber++ }
                )
            }
        }

        // Second-yellow warning
        if (selectedCard == CardType.YELLOW && selectedTeam != null) {
            val yellows = state.playerYellowCount(selectedTeam!!, "$playerNumber")
            if (yellows >= 1) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0xFF3A1A1A))
                            .border(1.dp, RefRed, RoundedCornerShape(6.dp))
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = "2nd yellow — auto RED CARD",
                            style = MaterialTheme.typography.caption2,
                            color = RefRed,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // Step 4: Offence (show only when card type is selected)
        if (selectedCard != null) {
            item { SectionLabel("4. Offence") }

            val offences = Offences.forCardType(selectedCard!!)
            items(offences.size) { index ->
                val offence = offences[index]
                val isSelected = selectedOffence == offence
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isSelected) Color(0xFF2A3A2A) else Color(0xFF1E1E1E))
                        .border(
                            width = if (isSelected) 1.dp else 0.dp,
                            color = if (isSelected) RefGreen else Color.Transparent,
                            shape = RoundedCornerShape(8.dp)
                        )
                        .clickable { selectedOffence = offence }
                        .padding(horizontal = 10.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = offence,
                        style = MaterialTheme.typography.caption1,
                        color = if (isSelected) RefGreen else Color.White
                    )
                }
            }
        }

        // Confirm button
        if (selectedTeam != null && selectedCard != null && selectedOffence != null) {
            item { Spacer(modifier = Modifier.height(4.dp)) }
            item {
                val cardColor = when (selectedCard) {
                    CardType.YELLOW -> RefYellow
                    CardType.RED -> RefRed
                    CardType.SIN_BIN -> RefOrange
                    null -> Color.Gray
                }
                Chip(
                    label = { Text("CONFIRM", fontWeight = FontWeight.Bold) },
                    onClick = {
                        viewModel.recordCard(
                            team = selectedTeam!!,
                            playerNumber = "$playerNumber",
                            cardType = selectedCard!!,
                            offence = selectedOffence!!
                        )
                        onCardRecorded()
                    },
                    colors = ChipDefaults.chipColors(backgroundColor = cardColor),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.caption2,
        color = Color.Gray,
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun SelectChip(label: String, selected: Boolean, onClick: () -> Unit) {
    CompactChip(
        label = { Text(label, fontWeight = FontWeight.Bold) },
        onClick = onClick,
        colors = if (selected)
            ChipDefaults.chipColors(backgroundColor = RefGreen)
        else
            ChipDefaults.chipColors(backgroundColor = Color(0xFF333333))
    )
}

@Composable
private fun CardTypeChip(
    label: String,
    cardType: CardType,
    activeColor: Color,
    selectedCard: CardType?,
    onSelect: (CardType) -> Unit
) {
    CompactChip(
        label = { Text(label, fontWeight = FontWeight.Bold) },
        onClick = { onSelect(cardType) },
        colors = if (selectedCard == cardType)
            ChipDefaults.chipColors(backgroundColor = activeColor)
        else
            ChipDefaults.chipColors(backgroundColor = Color(0xFF333333))
    )
}
