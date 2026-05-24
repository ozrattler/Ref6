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
import androidx.navigation.NavController
import androidx.wear.compose.material.*
import com.refsix.wear.ui.theme.*
import com.refsix.wear.viewmodel.MatchViewModel
import kotlinx.coroutines.delay

@Composable
fun ConfirmEndMatchScreen(
    action: String,
    viewModel: MatchViewModel,
    navController: NavController
) {
    var countdown by remember { mutableStateOf(10) }

    LaunchedEffect(Unit) {
        while (countdown > 0) {
            delay(1000L)
            countdown--
        }
        // Countdown expired without confirmation — return to match, nothing changed
        navController.popBackStack()
    }

    val isFullTime = action == "fullTime"

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(horizontal = 16.dp)
        ) {
            Text(
                text = if (isFullTime) "END MATCH?" else "HALF TIME?",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = if (isFullTime) RefRed else Color(0xFF9C27B0),
                textAlign = TextAlign.Center
            )

            Text(
                text = countdown.toString(),
                fontSize = 52.sp,
                fontWeight = FontWeight.Bold,
                color = when {
                    countdown <= 3 -> RefRed
                    countdown <= 6 -> RefYellow
                    else -> Color.White
                },
                textAlign = TextAlign.Center
            )

            Chip(
                label = { Text("CONFIRM", fontWeight = FontWeight.Bold) },
                onClick = {
                    if (isFullTime) {
                        viewModel.callFullTime()
                        navController.navigate("fullTime") {
                            popUpTo("match") { inclusive = false }
                        }
                    } else {
                        viewModel.callHalfTime()
                        navController.navigate("halfTime") {
                            popUpTo("match") { inclusive = false }
                        }
                    }
                },
                colors = ChipDefaults.chipColors(
                    backgroundColor = if (isFullTime) RefRed else Color(0xFF9C27B0)
                ),
                modifier = Modifier.fillMaxWidth()
            )

            CompactChip(
                label = { Text("Cancel", fontWeight = FontWeight.Bold) },
                onClick = { navController.popBackStack() },
                colors = ChipDefaults.chipColors(backgroundColor = RefBlue)
            )
        }
    }
}
