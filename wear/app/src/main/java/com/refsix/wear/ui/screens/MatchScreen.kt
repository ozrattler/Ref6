@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.refsix.wear.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import androidx.wear.compose.material.*
import com.refsix.wear.data.CardAlertType
import com.refsix.wear.data.MatchPhase
import com.refsix.wear.data.MatchState
import com.refsix.wear.ui.theme.*
import com.refsix.wear.viewmodel.MatchViewModel
import kotlinx.coroutines.delay

@Composable
fun MatchScreen(navController: NavController, viewModel: MatchViewModel) {
    val state by viewModel.state.collectAsState()
    val pagerState = rememberPagerState(initialPage = 1) { 3 }
    val returnToCenter by viewModel.returnToCenter.collectAsState()

    LaunchedEffect(returnToCenter) {
        if (returnToCenter) {
            viewModel.consumeReturnToCenter()
            pagerState.animateScrollToPage(1)
        }
    }

    val indicatorState = remember(pagerState) {
        object : PageIndicatorState {
            override val pageCount: Int get() = 3
            override val pageOffset: Float get() = pagerState.currentPageOffsetFraction
            override val selectedPage: Int get() = pagerState.currentPage
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize()
        ) { page ->
            when (page) {
                0 -> TeamActionPage(
                    team = state.homeTeam,
                    teamKey = "home",
                    viewModel = viewModel,
                    navController = navController
                )
                1 -> MainMatchPage(state = state, viewModel = viewModel, navController = navController)
                2 -> TeamActionPage(
                    team = state.awayTeam,
                    teamKey = "away",
                    viewModel = viewModel,
                    navController = navController
                )
            }
        }

        HorizontalPageIndicator(
            pageIndicatorState = indicatorState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 6.dp)
        )

        // Card alert overlay
        state.cardAlert?.let { alert ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xEE000000)),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF1A0000))
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    when (alert.type) {
                        CardAlertType.SECOND_YELLOW_RED -> {
                            Text("2ND YELLOW", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = RefYellow, textAlign = TextAlign.Center)
                            Text("AUTO RED CARD", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = RefRed, textAlign = TextAlign.Center)
                            Text("Player dismissed", style = MaterialTheme.typography.caption2, color = Color.Gray, textAlign = TextAlign.Center)
                        }
                        CardAlertType.DISSENT_SIN_BIN -> {
                            Text("DISSENT", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = RefYellow, textAlign = TextAlign.Center)
                            Text("AUTO SIN BIN", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = RefOrange, textAlign = TextAlign.Center)
                            Text("${alert.sinBinMinutes} min", style = MaterialTheme.typography.caption2, color = Color.Gray, textAlign = TextAlign.Center)
                        }
                    }
                    Text(
                        text = "#${alert.playerNumber}  ${alert.team}",
                        style = MaterialTheme.typography.caption1,
                        color = Color.White,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Chip(
                        label = { Text("OK", fontWeight = FontWeight.Bold) },
                        onClick = { viewModel.dismissCardAlert() },
                        colors = ChipDefaults.chipColors(
                            backgroundColor = when (alert.type) {
                                CardAlertType.SECOND_YELLOW_RED -> RefRed
                                else -> RefOrange
                            }
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

@Composable
private fun MainMatchPage(
    state: MatchState,
    viewModel: MatchViewModel,
    navController: NavController
) {
    val homeBins = state.activeSinBins
        .filter { it.team == state.homeTeam }
        .sortedBy { it.remainingSeconds(state.totalElapsedSeconds) }
    val awayBins = state.activeSinBins
        .filter { it.team == state.awayTeam }
        .sortedBy { it.remainingSeconds(state.totalElapsedSeconds) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp),
            modifier = Modifier.padding(horizontal = 8.dp)
        ) {
            Text(
                text = when (state.phase) {
                    MatchPhase.FIRST_HALF -> "1ST HALF"
                    MatchPhase.SECOND_HALF -> "2ND HALF"
                    else -> "MATCH"
                },
                style = MaterialTheme.typography.caption2,
                color = Color.Gray
            )

            Text(
                text = "%02d:%02d".format(
                    state.halfRemainingSeconds / 60,
                    state.halfRemainingSeconds % 60
                ),
                fontSize = 30.sp,
                fontWeight = FontWeight.Bold,
                color = if (state.isRunning) Color.White else Color.Gray
            )

            if (state.isInAdditionalTime) {
                Text(
                    text = "+%02d:%02d".format(
                        state.additionalSeconds / 60,
                        state.additionalSeconds % 60
                    ),
                    fontSize = 12.sp,
                    color = RefYellow,
                    fontWeight = FontWeight.Bold
                )
            }

            Text(
                text = "%02d:%02d".format(state.displayMinutes, state.displaySeconds),
                fontSize = 12.sp,
                color = Color.Gray
            )

            Text(
                text = "${state.homeScore}  –  ${state.awayScore}",
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Text(
                    text = state.homeTeam.take(6),
                    style = MaterialTheme.typography.caption2,
                    color = Color.Gray,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = state.awayTeam.take(6),
                    style = MaterialTheme.typography.caption2,
                    color = Color.Gray,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Sin bins: home on left, away on right — tap to manage
            if (homeBins.isNotEmpty() || awayBins.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { navController.navigate("sinBin") }
                        .padding(vertical = 2.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.Start
                    ) {
                        homeBins.take(3).forEach { bin ->
                            val remaining = bin.remainingSeconds(state.totalElapsedSeconds)
                            Text(
                                text = "#${bin.playerNumber} %02d:%02d".format(
                                    remaining / 60, remaining % 60
                                ),
                                fontSize = 11.sp,
                                fontWeight = if (remaining < 60) FontWeight.Bold else FontWeight.Normal,
                                color = if (remaining < 60) RefRed else RefOrange
                            )
                        }
                    }
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.End
                    ) {
                        awayBins.take(3).forEach { bin ->
                            val remaining = bin.remainingSeconds(state.totalElapsedSeconds)
                            Text(
                                text = "#${bin.playerNumber} %02d:%02d".format(
                                    remaining / 60, remaining % 60
                                ),
                                fontSize = 11.sp,
                                fontWeight = if (remaining < 60) FontWeight.Bold else FontWeight.Normal,
                                color = if (remaining < 60) RefRed else RefOrange,
                                textAlign = TextAlign.End
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(2.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                CompactChip(
                    label = {
                        Text(
                            text = if (state.isRunning) "PAUSE" else "START",
                            fontWeight = FontWeight.Bold
                        )
                    },
                    onClick = { viewModel.toggleTimer() },
                    colors = ChipDefaults.chipColors(backgroundColor = RefBlue)
                )
                CompactChip(
                    label = {
                        Text(
                            text = if (state.currentHalf == 1) "H/T" else "F/T",
                            fontWeight = FontWeight.Bold
                        )
                    },
                    onClick = {
                        if (state.currentHalf == 1) {
                            viewModel.callHalfTime()
                            navController.navigate("halfTime")
                        } else {
                            viewModel.callFullTime()
                            navController.navigate("fullTime")
                        }
                    },
                    colors = ChipDefaults.chipColors(backgroundColor = Color(0xFF9C27B0))
                )
            }
        }
    }
}

@Composable
private fun TeamActionPage(
    team: String,
    teamKey: String,
    viewModel: MatchViewModel,
    navController: NavController
) {
    var goalFlash by remember { mutableStateOf(false) }
    LaunchedEffect(goalFlash) {
        if (goalFlash) {
            delay(900)
            goalFlash = false
            viewModel.signalReturnToCenter()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(horizontal = 16.dp)
        ) {
            Text(
                text = team.uppercase(),
                style = MaterialTheme.typography.title2,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            // Goal — large distinct button
            Chip(
                label = {
                    Text(
                        text = if (goalFlash) "GOAL  ✓" else "GOAL",
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                        letterSpacing = 1.sp
                    )
                },
                onClick = {
                    viewModel.recordGoal(team)
                    goalFlash = true
                },
                colors = ChipDefaults.chipColors(
                    backgroundColor = if (goalFlash) Color(0xFF1B5E20) else Color(0xFF2E7D32)
                ),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(4.dp))

            // Cards — compact, visually grouped below the goal button
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CompactChip(
                    label = { Text("YC", fontWeight = FontWeight.Bold) },
                    onClick = { navController.navigate("card/$teamKey/YELLOW") },
                    colors = ChipDefaults.chipColors(backgroundColor = RefYellow)
                )
                CompactChip(
                    label = { Text("RC", fontWeight = FontWeight.Bold) },
                    onClick = { navController.navigate("card/$teamKey/RED") },
                    colors = ChipDefaults.chipColors(backgroundColor = RefRed)
                )
            }
        }
    }
}
