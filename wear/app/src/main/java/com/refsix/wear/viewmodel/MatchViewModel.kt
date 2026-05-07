package com.refsix.wear.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.refsix.wear.data.AgeGroup
import com.refsix.wear.data.CardAlert
import com.refsix.wear.data.CardAlertType
import com.refsix.wear.data.CardType
import com.refsix.wear.data.EventType
import com.refsix.wear.data.MatchEvent
import com.refsix.wear.data.MatchPhase
import com.refsix.wear.data.MatchState
import com.refsix.wear.data.Offences
import com.refsix.wear.data.SinBinEntry
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

sealed class MatchUiEvent {
    data class SinBinExpired(val team: String, val playerNumber: String) : MatchUiEvent()
}

class MatchViewModel : ViewModel() {

    private val _state = MutableStateFlow(MatchState())
    val state: StateFlow<MatchState> = _state.asStateFlow()

    private val _uiEvents = MutableSharedFlow<MatchUiEvent>(extraBufferCapacity = 16)
    val uiEvents: SharedFlow<MatchUiEvent> = _uiEvents.asSharedFlow()

    private var timerJob: Job? = null

    init {
        launchTimer()
    }

    private fun launchTimer() {
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            while (isActive) {
                delay(1000L)
                var justExpired = emptyList<SinBinEntry>()
                _state.update { s ->
                    if (!s.isRunning) return@update s
                    val newHalf = s.halfElapsedSeconds + 1L
                    val newTotal = s.totalElapsedSeconds + 1L
                    justExpired = s.sinBins.filter {
                        !it.isExpired(s.totalElapsedSeconds) && it.isExpired(newTotal)
                    }
                    s.copy(
                        halfElapsedSeconds = newHalf,
                        totalElapsedSeconds = newTotal,
                        sinBins = s.sinBins.filterNot { it.isExpired(newTotal) }
                    )
                }
                justExpired.forEach { bin ->
                    _uiEvents.tryEmit(MatchUiEvent.SinBinExpired(bin.team, bin.playerNumber))
                }
            }
        }
    }

    fun updateSetup(
        homeTeam: String,
        awayTeam: String,
        halfLengthMinutes: Int,
        ageGroup: AgeGroup,
        sinBinMinutes: Int,
        dissentAutoSinBin: Boolean
    ) {
        _state.update {
            it.copy(
                homeTeam = homeTeam.ifBlank { "Home" },
                awayTeam = awayTeam.ifBlank { "Away" },
                halfLengthMinutes = halfLengthMinutes.coerceIn(10, 60),
                ageGroup = ageGroup,
                sinBinMinutes = sinBinMinutes.coerceIn(1, 30),
                dissentAutoSinBin = dissentAutoSinBin
            )
        }
    }

    fun startMatch() {
        _state.update {
            it.copy(
                phase = MatchPhase.FIRST_HALF,
                currentHalf = 1,
                halfElapsedSeconds = 0L,
                totalElapsedSeconds = 0L,
                isRunning = true,
                homeScore = 0,
                awayScore = 0,
                events = emptyList(),
                sinBins = emptyList(),
                cardAlert = null
            )
        }
    }

    fun toggleTimer() {
        _state.update { it.copy(isRunning = !it.isRunning) }
    }

    fun callHalfTime() {
        _state.update { it.copy(phase = MatchPhase.HALF_TIME, isRunning = false) }
    }

    fun startSecondHalf() {
        _state.update {
            it.copy(
                phase = MatchPhase.SECOND_HALF,
                currentHalf = 2,
                halfElapsedSeconds = 0L,
                isRunning = true
            )
        }
    }

    fun callFullTime() {
        _state.update { it.copy(phase = MatchPhase.FULL_TIME, isRunning = false) }
    }

    fun recordGoal(team: String) {
        _state.update { s ->
            val isHome = team == s.homeTeam
            val event = MatchEvent(
                type = EventType.GOAL,
                team = team,
                matchMinute = s.currentMatchMinute,
                half = s.currentHalf
            )
            s.copy(
                homeScore = if (isHome) s.homeScore + 1 else s.homeScore,
                awayScore = if (!isHome) s.awayScore + 1 else s.awayScore,
                events = s.events + event
            )
        }
    }

    fun recordCard(team: String, playerNumber: String, cardType: CardType, offence: String) {
        _state.update { s ->
            val minute = s.currentMatchMinute
            val half = s.currentHalf

            // Yellow card count is checked FIRST — any yellow offence including Dissent
            // triggers 2nd yellow logic when this player already has a caution.
            val isSecondYellow = cardType == CardType.YELLOW &&
                s.playerYellowCount(team, playerNumber) >= 1

            val cardEvent = MatchEvent(
                type = when (cardType) {
                    CardType.YELLOW -> EventType.YELLOW_CARD
                    CardType.RED -> EventType.RED_CARD
                    CardType.SIN_BIN -> EventType.SIN_BIN
                },
                team = team,
                playerNumber = playerNumber,
                detail = offence,
                matchMinute = minute,
                half = half
            )

            val newEvents: List<MatchEvent>
            val updatedSinBins: List<SinBinEntry>
            val alert: CardAlert?

            when {
                // Red card — clear any existing sin bin for this player
                cardType == CardType.RED -> {
                    newEvents = s.events + cardEvent
                    updatedSinBins = s.sinBins.filterNot {
                        it.team == team && it.playerNumber == playerNumber
                    }
                    alert = s.cardAlert
                }

                // 2nd yellow always means red card and dismissal — no exceptions
                isSecondYellow -> {
                    val autoRed = MatchEvent(
                        type = EventType.RED_CARD,
                        team = team,
                        playerNumber = playerNumber,
                        detail = "Second caution",
                        matchMinute = minute,
                        half = half
                    )
                    newEvents = s.events + cardEvent + autoRed
                    updatedSinBins = s.sinBins.filterNot {
                        it.team == team && it.playerNumber == playerNumber
                    }
                    alert = CardAlert(team, playerNumber, CardAlertType.SECOND_YELLOW_RED, s.sinBinMinutes)
                }

                // Dissent yellow (first caution only — 2nd yellow already handled above)
                cardType == CardType.YELLOW &&
                    offence == Offences.DISSENT &&
                    s.dissentAutoSinBin -> {
                    newEvents = s.events + cardEvent
                    updatedSinBins = s.sinBins + SinBinEntry(
                        team = team,
                        playerNumber = playerNumber,
                        offence = "Dissent",
                        startElapsedSeconds = s.totalElapsedSeconds,
                        durationSeconds = s.sinBinDurationSeconds
                    )
                    alert = CardAlert(team, playerNumber, CardAlertType.DISSENT_SIN_BIN, s.sinBinMinutes)
                }

                // Standard sin bin card
                cardType == CardType.SIN_BIN -> {
                    newEvents = s.events + cardEvent
                    updatedSinBins = s.sinBins + SinBinEntry(
                        team = team,
                        playerNumber = playerNumber,
                        offence = offence,
                        startElapsedSeconds = s.totalElapsedSeconds,
                        durationSeconds = s.sinBinDurationSeconds
                    )
                    alert = s.cardAlert
                }

                // Standard yellow — no special handling
                else -> {
                    newEvents = s.events + cardEvent
                    updatedSinBins = s.sinBins
                    alert = s.cardAlert
                }
            }

            s.copy(events = newEvents, sinBins = updatedSinBins, cardAlert = alert)
        }
    }

    fun dismissCardAlert() {
        _state.update { it.copy(cardAlert = null) }
    }

    fun returnFromSinBin(sinBinId: Long) {
        _state.update { s -> s.copy(sinBins = s.sinBins.filter { it.id != sinBinId }) }
    }

    fun resetMatch() {
        _state.value = MatchState()
    }
}
