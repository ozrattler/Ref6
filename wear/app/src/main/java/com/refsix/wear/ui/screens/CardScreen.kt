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
fun CardScreen(
    viewModel: MatchViewModel,
    teamKey: String? = null,
    cardTypeKey: String? = null,
    onCardRecorded: () -> Unit
) {
    val state by viewModel.state.collectAsState()

    val prefilledTeam = when (teamKey) {
        "home" -> state.homeTeam
        "away" -> state.awayTeam
        else -> null
    }
    val prefilledCardType = when (cardTypeKey) {
        "YELLOW" -> CardType.YELLOW
        "RED" -> CardType.RED
        "SIN_BIN" -> CardType.SIN_BIN
        else -> null
    }

    var selectedTeam by remember { mutableStateOf(prefilledTeam) }
    var selectedCard by remember { mutableStateOf(prefilledCardType) }
    var playerNumber by remember { mutableIntStateOf(1) }
    var selectedOffence by remember { mutableStateOf<String?>(null) }

    val isSecondYellow = selectedCard == CardType.YELLOW &&
        selectedTeam != null &&
        state.playerYellowCount(selectedTeam!!, "$playerNumber") >= 1

    val isDissentSelected = selectedCard == CardType.YELLOW &&
        selectedOffence == Offences.DISSENT &&
        !isSecondYellow

    ScalingLazyColumn(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 28.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        item {
            Text(
                text = "CARD",
                style = MaterialTheme.typography.title1,
                fontWeight = FontWeight.Bold,
                color = RefYellow
            )
        }

        // Step 1: Team (skip if pre-filled)
        if (prefilledTeam == null) {
            item { SectionLabel("1. Team") }
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
        }

        // Step 2: Card type (skip if pre-filled)
        if (prefilledCardType == null) {
            val stepNum = if (prefilledTeam == null) "2" else "1"
            item { SectionLabel("$stepNum. Card Type") }
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
        }

        // Step: Player number
        val playerStepNum = when {
            prefilledTeam != null && prefilledCardType != null -> "1"
            prefilledTeam != null || prefilledCardType != null -> "2"
            else -> "3"
        }
        item { SectionLabel("$playerStepNum. Player #") }
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

        // 2nd yellow warning — always results in red card, no exceptions
        if (isSecondYellow) {
            item {
                NoticeBanner(text = "2nd yellow — AUTO RED CARD", color = RefRed)
            }
        }

        // Step: Offence
        if (selectedCard != null) {
            val offenceStepNum = when {
                prefilledTeam != null && prefilledCardType != null -> "2"
                prefilledTeam != null || prefilledCardType != null -> "3"
                else -> "4"
            }
            item { SectionLabel("$offenceStepNum. Offence") }

            val offences = Offences.forCardType(selectedCard!!)
            items(offences.size) { index ->
                val offence = offences[index]
                val isSelected = selectedOffence == offence
                val isDissent = offence == Offences.DISSENT &&
                    selectedCard == CardType.YELLOW &&
                    !isSecondYellow

                val borderColor = when {
                    isSelected -> RefGreen
                    isDissent -> RefOrange.copy(alpha = 0.6f)
                    else -> Color.Transparent
                }
                val bgColor = when {
                    isSelected -> Color(0xFF1A2A1A)
                    isDissent -> Color(0xFF2A1E00)
                    else -> Color(0xFF1E1E1E)
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(bgColor)
                        .border(1.dp, borderColor, RoundedCornerShape(8.dp))
                        .clickable { selectedOffence = offence }
                        .padding(horizontal = 10.dp, vertical = 8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = offence,
                            style = MaterialTheme.typography.caption1,
                            color = when {
                                isSelected -> RefGreen
                                isDissent -> RefOrange
                                else -> Color.White
                            }
                        )
                        if (isDissent) {
                            Text(
                                text = "→ sin bin",
                                fontSize = 10.sp,
                                color = RefOrange,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        if (isDissentSelected) {
            item {
                NoticeBanner(
                    text = "Dissent — auto SIN BIN (${state.sinBinMinutes} min)",
                    color = RefOrange
                )
            }
        }

        if (selectedTeam != null && selectedCard != null && selectedOffence != null) {
            item { Spacer(modifier = Modifier.height(4.dp)) }
            item {
                val chipColor = when {
                    isSecondYellow -> RefRed
                    isDissentSelected -> RefOrange
                    selectedCard == CardType.RED -> RefRed
                    selectedCard == CardType.SIN_BIN -> RefOrange
                    else -> RefYellow
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
                    colors = ChipDefaults.chipColors(backgroundColor = chipColor),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun NoticeBanner(text: String, color: Color) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(color.copy(alpha = 0.12f))
            .border(1.dp, color, RoundedCornerShape(6.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.caption2,
            color = color,
            fontWeight = FontWeight.Bold
        )
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
