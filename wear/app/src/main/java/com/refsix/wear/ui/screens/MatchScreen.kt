@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.refsix.wear.ui.screens

import android.graphics.Color as AndroidColor
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.activity.compose.BackHandler
import androidx.navigation.NavController
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.material.*
import com.refsix.wear.data.CardAlertType
import com.refsix.wear.data.EventType
import com.refsix.wear.data.MatchEvent
import com.refsix.wear.data.MatchPhase
import com.refsix.wear.data.MatchState
import com.refsix.wear.ui.theme.*
import com.refsix.wear.viewmodel.MatchViewModel
import kotlinx.coroutines.delay

// Overlay state machine for the long-press options menu and event management flows.
private sealed class MenuState {
    object None : MenuState()
    object Options : MenuState()
    class EventListPick(val forDelete: Boolean) : MenuState()
    class EditEventForm(val event: MatchEvent) : MenuState()
    class DeleteConfirm(val event: MatchEvent) : MenuState()
}

@Composable
fun MatchScreen(navController: NavController, viewModel: MatchViewModel) {
    // Hardware back button is a dead key during a live match — back navigation
    // must only happen through the explicit End Match control.
    BackHandler(enabled = true) {}

    val state by viewModel.state.collectAsState()
    val sinBinAlert by viewModel.sinBinAlert.collectAsState()
    val pagerState = rememberPagerState(initialPage = 1) { 3 }
    val centerCount by viewModel.returnToCenterCount.collectAsState()
    LaunchedEffect(centerCount) {
        if (centerCount > 0) pagerState.animateScrollToPage(1)
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
                1 -> MainMatchPage(
                    state = state,
                    viewModel = viewModel,
                    navController = navController,
                    onSwipeUp = { navController.navigate("cardsSummary") }
                )
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

        // Sin bin expiry alert — shown below card alert so card alert renders on top
        sinBinAlert?.let { (team, playerNum) ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xDD000000)),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF1A1200))
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Text(
                        text = "SIN BIN ENDED",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = RefOrange,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = "${if (playerNum == "Coach") "Coach" else "#$playerNum"}  $team",
                        style = MaterialTheme.typography.caption1,
                        color = Color.White,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Chip(
                        label = { Text("OK", fontWeight = FontWeight.Bold) },
                        onClick = { viewModel.dismissSinBinAlert() },
                        colors = ChipDefaults.chipColors(backgroundColor = RefOrange),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        // Card alert overlay — on top of sin bin alert
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
                        text = "${if (alert.playerNumber == "Coach") "Coach" else "#${alert.playerNumber}"}  ${alert.team}",
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
    navController: NavController,
    onSwipeUp: () -> Unit = {}
) {
    val homeBins = state.activeSinBins
        .filter { it.team == state.homeTeam }
        .sortedBy { it.remainingSeconds(state.totalElapsedSeconds) }
    val awayBins = state.activeSinBins
        .filter { it.team == state.awayTeam }
        .sortedBy { it.remainingSeconds(state.totalElapsedSeconds) }

    var totalVerticalDrag by remember { mutableFloatStateOf(0f) }
    var swipeTriggered by remember { mutableStateOf(false) }
    var menuState by remember { mutableStateOf<MenuState>(MenuState.None) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragStart = { totalVerticalDrag = 0f; swipeTriggered = false },
                    onVerticalDrag = { change, dragAmount ->
                        totalVerticalDrag += dragAmount
                        if (!swipeTriggered && totalVerticalDrag < -80f) {
                            swipeTriggered = true
                            change.consume()
                            onSwipeUp()
                        }
                    }
                )
            }
            // Long press opens match options without pausing the timer.
            .pointerInput(Unit) {
                detectTapGestures(onLongPress = { menuState = MenuState.Options })
            },
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

            // Elapsed clock
            Text(
                text = "%02d:%02d".format(state.displayMinutes, state.displaySeconds),
                fontSize = 30.sp,
                fontWeight = FontWeight.Bold,
                color = if (state.isRunning) Color.White else Color.Gray
            )

            // Countdown on its own line — enlarged so it's easy to read at a glance
            if (!state.isInAdditionalTime) {
                val remaining = state.halfRemainingSeconds
                Text(
                    text = "-%02d:%02d".format(remaining / 60, remaining % 60),
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                    color = when {
                        remaining <= 60 -> RefRed
                        remaining <= 120 -> RefYellow
                        else -> Color.White
                    }
                )
            }

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
                text = "${state.homeScore}  –  ${state.awayScore}",
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                KitTeamLabel(state.homeTeam.take(4), state.homeColour)
                KitTeamLabel(state.awayTeam.take(4), state.awayColour)
            }

            // Sin bins: home on left, away on right — tap to manage
            if (homeBins.isNotEmpty() || awayBins.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { navController.navigate("sinBin") }
                        .padding(horizontal = 12.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.Start
                    ) {
                        homeBins.take(3).forEach { bin ->
                            val remaining = bin.remainingSeconds(state.totalElapsedSeconds)
                            Text(
                                text = "${if (bin.playerNumber == "Coach") "Coach" else "#${bin.playerNumber}"} %02d:%02d".format(
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
                                text = "${if (bin.playerNumber == "Coach") "Coach" else "#${bin.playerNumber}"} %02d:%02d".format(
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
        }

        // ── Overlay state machine ─────────────────────────────────────────────
        when (val ms = menuState) {
            MenuState.None -> {}

            MenuState.Options -> OptionsMenuOverlay(
                isRunning = state.isRunning,
                onPauseResume = { viewModel.toggleTimer(); menuState = MenuState.None },
                onEndMatch = { menuState = MenuState.None; navController.navigate("confirmEnd/fullTime") },
                onEditEvent = { menuState = MenuState.EventListPick(forDelete = false) },
                onAddEvent  = { menuState = MenuState.None; navController.navigate("addEvent") },
                onDeleteEvent = { menuState = MenuState.EventListPick(forDelete = true) },
                onDismiss = { menuState = MenuState.None }
            )

            is MenuState.EventListPick -> EventListOverlay(
                events = state.events,
                homeTeam = state.homeTeam,
                awayTeam = state.awayTeam,
                forDelete = ms.forDelete,
                onEventSelected = { event ->
                    menuState = if (ms.forDelete) MenuState.DeleteConfirm(event)
                                 else MenuState.EditEventForm(event)
                },
                onBack = { menuState = MenuState.Options }
            )

            is MenuState.EditEventForm -> EditEventOverlay(
                event = ms.event,
                homeTeam = state.homeTeam,
                awayTeam = state.awayTeam,
                onSave = { newTeam, newPlayer ->
                    viewModel.editEvent(ms.event.id, newTeam, newPlayer)
                    menuState = MenuState.None
                },
                onBack = { menuState = MenuState.EventListPick(forDelete = false) }
            )

            is MenuState.DeleteConfirm -> DeleteConfirmOverlay(
                event = ms.event,
                onConfirm = { viewModel.deleteEvent(ms.event.id); menuState = MenuState.None },
                onBack = { menuState = MenuState.EventListPick(forDelete = true) }
            )
        }
    }
}

// ── Options overlay ───────────────────────────────────────────────────────────

@Composable
private fun OptionsMenuOverlay(
    isRunning: Boolean,
    onPauseResume: () -> Unit,
    onEndMatch: () -> Unit,
    onEditEvent: () -> Unit,
    onAddEvent: () -> Unit,
    onDeleteEvent: () -> Unit,
    onDismiss: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xEE000000)),
        contentAlignment = Alignment.Center
    ) {
        ScalingLazyColumn(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 28.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            item {
                Text("OPTIONS", fontSize = 11.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
            }
            item {
                Chip(
                    label = {
                        Text(
                            text = if (isRunning) "Pause Match" else "Resume Match",
                            fontWeight = FontWeight.Bold
                        )
                    },
                    onClick = onPauseResume,
                    colors = ChipDefaults.chipColors(backgroundColor = RefBlue),
                    modifier = Modifier.fillMaxWidth()
                )
            }
            item {
                Chip(
                    label = { Text("End Match", fontWeight = FontWeight.Bold) },
                    onClick = onEndMatch,
                    colors = ChipDefaults.chipColors(backgroundColor = Color(0xFF4A1A1A)),
                    modifier = Modifier.fillMaxWidth()
                )
            }
            item {
                Spacer(modifier = Modifier.height(2.dp))
                Text("EVENT", fontSize = 10.sp, color = Color.Gray)
            }
            item {
                Chip(
                    label = { Text("Edit", fontWeight = FontWeight.Bold) },
                    onClick = onEditEvent,
                    colors = ChipDefaults.chipColors(backgroundColor = Color(0xFF1B3A1B)),
                    modifier = Modifier.fillMaxWidth()
                )
            }
            item {
                Chip(
                    label = { Text("Add", fontWeight = FontWeight.Bold) },
                    onClick = onAddEvent,
                    colors = ChipDefaults.chipColors(backgroundColor = Color(0xFF1A2A4A)),
                    modifier = Modifier.fillMaxWidth()
                )
            }
            item {
                Chip(
                    label = { Text("Delete", fontWeight = FontWeight.Bold) },
                    onClick = onDeleteEvent,
                    colors = ChipDefaults.chipColors(backgroundColor = Color(0xFF5A1A1A)),
                    modifier = Modifier.fillMaxWidth()
                )
            }
            item {
                CompactChip(
                    label = { Text("Cancel", fontWeight = FontWeight.Bold) },
                    onClick = onDismiss,
                    colors = ChipDefaults.chipColors(backgroundColor = Color(0xFF333333))
                )
            }
        }
    }
}

// ── Event list picker ─────────────────────────────────────────────────────────

@Composable
private fun EventListOverlay(
    events: List<MatchEvent>,
    homeTeam: String,
    awayTeam: String,
    forDelete: Boolean,
    onEventSelected: (MatchEvent) -> Unit,
    onBack: () -> Unit
) {
    val recentEvents = remember(events) { events.takeLast(8).reversed() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xEE000000))
    ) {
        ScalingLazyColumn(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 28.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            item {
                Text(
                    text = if (forDelete) "DELETE EVENT" else "EDIT EVENT",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (forDelete) RefRed else RefGreen
                )
            }

            if (recentEvents.isEmpty()) {
                item { Text("No events yet", fontSize = 12.sp, color = Color.Gray) }
            } else {
                items(recentEvents.size) { i ->
                    val event = recentEvents[i]
                    val typeLabel = when (event.type) {
                        EventType.GOAL        -> "GOAL"
                        EventType.YELLOW_CARD -> "YC"
                        EventType.RED_CARD    -> "RC"
                        EventType.SIN_BIN     -> "SIN"
                    }
                    val typeColor = when (event.type) {
                        EventType.GOAL        -> Color(0xFF66BB6A)
                        EventType.YELLOW_CARD -> RefYellow
                        EventType.RED_CARD    -> RefRed
                        EventType.SIN_BIN     -> RefOrange
                    }
                    val teamAbbrev = event.team.take(4).uppercase()
                    val playerPart = if (event.playerNumber.isNotBlank()) " #${event.playerNumber}" else ""
                    Chip(
                        label = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(typeLabel, color = typeColor, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                Text(
                                    "  $teamAbbrev$playerPart  ${event.matchMinute}'",
                                    color = Color.White,
                                    fontSize = 11.sp
                                )
                            }
                        },
                        onClick = { onEventSelected(event) },
                        colors = ChipDefaults.chipColors(backgroundColor = Color(0xFF2A2A2A)),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            item {
                CompactChip(
                    label = { Text("Back", fontWeight = FontWeight.Bold) },
                    onClick = onBack,
                    colors = ChipDefaults.chipColors(backgroundColor = Color(0xFF333333))
                )
            }
        }
    }
}

// ── Edit event form ───────────────────────────────────────────────────────────

@Composable
private fun EditEventOverlay(
    event: MatchEvent,
    homeTeam: String,
    awayTeam: String,
    onSave: (team: String, playerNumber: String) -> Unit,
    onBack: () -> Unit
) {
    var selectedTeam by remember { mutableStateOf(event.team) }
    var playerNum by remember { mutableIntStateOf(event.playerNumber.toIntOrNull() ?: 0) }

    // Card events use playerNumber; goals use scorerNumber — only show picker for card events
    val showPlayerPicker = event.type == EventType.YELLOW_CARD ||
                           event.type == EventType.RED_CARD ||
                           event.type == EventType.SIN_BIN

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xEE000000))
    ) {
        ScalingLazyColumn(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 28.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            item {
                Text("EDIT EVENT", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = RefGreen)
            }
            item {
                val typeLabel = when (event.type) {
                    EventType.GOAL        -> "Goal"
                    EventType.YELLOW_CARD -> "Yellow Card"
                    EventType.RED_CARD    -> "Red Card"
                    EventType.SIN_BIN     -> "Sin Bin"
                }
                Text(
                    text = "$typeLabel  ${event.matchMinute}'",
                    style = MaterialTheme.typography.caption1,
                    color = Color.LightGray
                )
            }

            // Team toggle
            item {
                Text("Team", style = MaterialTheme.typography.caption2, color = Color.Gray)
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CompactChip(
                        label = { Text(homeTeam.take(6), fontWeight = FontWeight.Bold) },
                        onClick = { selectedTeam = homeTeam },
                        colors = ChipDefaults.chipColors(
                            backgroundColor = if (selectedTeam == homeTeam) RefGreen else Color(0xFF2A2A2A)
                        )
                    )
                    CompactChip(
                        label = { Text(awayTeam.take(6), fontWeight = FontWeight.Bold) },
                        onClick = { selectedTeam = awayTeam },
                        colors = ChipDefaults.chipColors(
                            backgroundColor = if (selectedTeam == awayTeam) RefGreen else Color(0xFF2A2A2A)
                        )
                    )
                }
            }

            // Player number picker — only for card/sin bin events
            if (showPlayerPicker) {
                item {
                    Text("Player #", style = MaterialTheme.typography.caption2, color = Color.Gray)
                }
                item {
                    PlayerNumberPicker(
                        value = playerNum,
                        onValueChange = { playerNum = it }
                    )
                }
            }

            item {
                Chip(
                    label = { Text("Save", fontWeight = FontWeight.Bold) },
                    onClick = {
                        val newPlayer = if (showPlayerPicker && playerNum > 0) "$playerNum"
                                        else event.playerNumber
                        onSave(selectedTeam, newPlayer)
                    },
                    colors = ChipDefaults.chipColors(backgroundColor = RefGreen),
                    modifier = Modifier.fillMaxWidth()
                )
            }
            item {
                CompactChip(
                    label = { Text("Back", fontWeight = FontWeight.Bold) },
                    onClick = onBack,
                    colors = ChipDefaults.chipColors(backgroundColor = Color(0xFF333333))
                )
            }
        }
    }
}

// ── Delete confirmation ───────────────────────────────────────────────────────

@Composable
private fun DeleteConfirmOverlay(
    event: MatchEvent,
    onConfirm: () -> Unit,
    onBack: () -> Unit
) {
    val typeLabel = when (event.type) {
        EventType.GOAL        -> "Goal"
        EventType.YELLOW_CARD -> "Yellow Card"
        EventType.RED_CARD    -> "Red Card"
        EventType.SIN_BIN     -> "Sin Bin"
    }
    val playerPart = if (event.playerNumber.isNotBlank()) " #${event.playerNumber}" else ""

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xEE000000)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF1A0000))
                .padding(horizontal = 16.dp, vertical = 14.dp)
        ) {
            Text(
                text = "DELETE?",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = RefRed
            )
            Text(
                text = "$typeLabel$playerPart\n${event.team.take(8)}  ${event.matchMinute}'",
                style = MaterialTheme.typography.caption1,
                color = Color.LightGray,
                textAlign = TextAlign.Center
            )
            Chip(
                label = { Text("Delete", fontWeight = FontWeight.Bold) },
                onClick = onConfirm,
                colors = ChipDefaults.chipColors(backgroundColor = RefRed),
                modifier = Modifier.fillMaxWidth()
            )
            CompactChip(
                label = { Text("Cancel", fontWeight = FontWeight.Bold) },
                onClick = onBack,
                colors = ChipDefaults.chipColors(backgroundColor = Color(0xFF333333))
            )
        }
    }
}

// ── Team action page (home / away swipe pages) ────────────────────────────────

@Composable
private fun TeamActionPage(
    team: String,
    teamKey: String,
    viewModel: MatchViewModel,
    navController: NavController
) {
    val state by viewModel.state.collectAsState()
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
                text = team.take(4).uppercase(),
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
                    if (state.isSpl) {
                        navController.navigate("goalScorer/$teamKey")
                    } else {
                        viewModel.recordGoal(team)
                        goalFlash = true
                    }
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

@Composable
private fun KitTeamLabel(name: String, hexColour: String) {
    val kitColor = hexColour.toKitColor()
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        if (kitColor != null) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(kitColor)
            )
        }
        Text(
            text = name,
            fontSize = 14.sp,
            color = Color.Gray,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

internal fun String.toKitColor(): Color? {
    val s = trim()
    if (s.isEmpty()) return null
    val hex = if (s.startsWith("#")) s else "#$s"
    return runCatching { Color(AndroidColor.parseColor(hex)) }.getOrNull()
}
