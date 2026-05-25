package com.refsix.wear.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.*
import com.refsix.wear.data.CardType
import com.refsix.wear.ui.theme.*
import com.refsix.wear.viewmodel.MatchViewModel

@Composable
fun CardConfirmScreen(
    viewModel: MatchViewModel,
    onConfirm: () -> Unit,
    onGoBack: () -> Unit
) {
    val pendingCard by viewModel.pendingCard.collectAsState()
    val card = pendingCard ?: run {
        LaunchedEffect(Unit) { onGoBack() }
        return
    }

    val cardColor = when {
        card.isSecondYellow -> RefRed
        card.cardType == CardType.RED -> RefRed
        card.cardType == CardType.SIN_BIN -> RefOrange
        else -> RefYellow
    }

    val cardLabel = when {
        card.isSecondYellow -> "RED CARD (2nd yellow)"
        card.cardType == CardType.YELLOW -> "YELLOW CARD"
        card.cardType == CardType.RED -> "RED CARD"
        card.cardType == CardType.SIN_BIN -> "SIN BIN  ${card.sinBinMinutes} min"
        else -> "CARD"
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(3.dp),
            modifier = Modifier.padding(horizontal = 20.dp)
        ) {
            Text(
                text = cardLabel,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = cardColor,
                textAlign = TextAlign.Center
            )

            Text(
                text = card.team,
                style = MaterialTheme.typography.caption2,
                color = Color.Gray,
                textAlign = TextAlign.Center
            )

            Text(
                text = if (card.playerNumber == "Coach") "Coach" else "Player #${card.playerNumber}",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                textAlign = TextAlign.Center
            )

            Text(
                text = card.offence,
                fontSize = 11.sp,
                color = Color.LightGray,
                textAlign = TextAlign.Center
            )

            if (card.isDissentSinBin) {
                Text(
                    text = "→ auto sin bin (${card.sinBinMinutes}m)",
                    fontSize = 10.sp,
                    color = RefOrange,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            Chip(
                label = { Text("CONFIRM", fontWeight = FontWeight.Bold) },
                onClick = {
                    viewModel.recordCard(
                        team = card.team,
                        playerNumber = card.playerNumber,
                        cardType = card.cardType,
                        offence = card.offence
                    )
                    viewModel.clearPendingCard()
                    onConfirm()
                },
                colors = ChipDefaults.chipColors(backgroundColor = cardColor),
                modifier = Modifier.fillMaxWidth()
            )

            CompactChip(
                label = { Text("Go Back", fontWeight = FontWeight.Bold) },
                onClick = {
                    viewModel.clearPendingCard()
                    onGoBack()
                },
                colors = ChipDefaults.chipColors(backgroundColor = Color(0xFF333333))
            )
        }
    }
}
