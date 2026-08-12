package com.refsix.wear.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.material.*
import com.refsix.wear.data.MatchEvent
import com.refsix.wear.ui.theme.*
import com.refsix.wear.viewmodel.MatchViewModel

private sealed class ManageMenuState {
    object Menu : ManageMenuState()
    class EventListPick(val forDelete: Boolean) : ManageMenuState()
    class EditEventForm(val event: MatchEvent) : ManageMenuState()
    class DeleteConfirm(val event: MatchEvent) : ManageMenuState()
}

@Composable
fun ManageEventsScreen(
    viewModel: MatchViewModel,
    navController: NavController
) {
    val state by viewModel.state.collectAsState()
    var ms by remember { mutableStateOf<ManageMenuState>(ManageMenuState.Menu) }

    when (val cur = ms) {
        ManageMenuState.Menu -> ScalingLazyColumn(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 28.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            item {
                Text("EVENTS", fontSize = androidx.compose.ui.unit.TextUnit.Unspecified,
                    style = MaterialTheme.typography.caption2,
                    color = Color.Gray, fontWeight = FontWeight.Bold)
            }
            item {
                Chip(
                    label = { Text("Edit", fontWeight = FontWeight.Bold) },
                    onClick = { ms = ManageMenuState.EventListPick(forDelete = false) },
                    colors = ChipDefaults.chipColors(backgroundColor = Color(0xFF1B3A1B)),
                    modifier = Modifier.fillMaxWidth()
                )
            }
            item {
                Chip(
                    label = { Text("Add", fontWeight = FontWeight.Bold) },
                    onClick = { navController.navigate("addEvent") },
                    colors = ChipDefaults.chipColors(backgroundColor = Color(0xFF1A2A4A)),
                    modifier = Modifier.fillMaxWidth()
                )
            }
            item {
                Chip(
                    label = { Text("Delete", fontWeight = FontWeight.Bold) },
                    onClick = { ms = ManageMenuState.EventListPick(forDelete = true) },
                    colors = ChipDefaults.chipColors(backgroundColor = Color(0xFF5A1A1A)),
                    modifier = Modifier.fillMaxWidth()
                )
            }
            item {
                CompactChip(
                    label = { Text("Done", fontWeight = FontWeight.Bold) },
                    onClick = { navController.popBackStack() },
                    colors = ChipDefaults.chipColors(backgroundColor = Color(0xFF333333))
                )
            }
        }

        is ManageMenuState.EventListPick -> EventListOverlay(
            events = state.events,
            homeTeam = state.homeTeam,
            awayTeam = state.awayTeam,
            forDelete = cur.forDelete,
            onEventSelected = { event ->
                ms = if (cur.forDelete) ManageMenuState.DeleteConfirm(event)
                     else ManageMenuState.EditEventForm(event)
            },
            onBack = { ms = ManageMenuState.Menu }
        )

        is ManageMenuState.EditEventForm -> EditEventOverlay(
            event = cur.event,
            homeTeam = state.homeTeam,
            awayTeam = state.awayTeam,
            onSave = { newType, newTeam, newPlayer, newMinute ->
                viewModel.editEvent(cur.event.id, newType, newTeam, newPlayer, newMinute)
                ms = ManageMenuState.Menu
            },
            onBack = { ms = ManageMenuState.EventListPick(forDelete = false) }
        )

        is ManageMenuState.DeleteConfirm -> DeleteConfirmOverlay(
            event = cur.event,
            onConfirm = { viewModel.deleteEvent(cur.event.id); ms = ManageMenuState.Menu },
            onBack = { ms = ManageMenuState.EventListPick(forDelete = true) }
        )
    }
}
