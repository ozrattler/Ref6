package com.refsix.wear.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.refsix.wear.data.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class MatchViewModel : ViewModel() {

    private val _state = MutableStateFlow(MatchState())
    val state: StateFlow<MatchState> = _state.asStateFlow()

    private var timerJob: Job? = null

    init {
        launchTimer()
    }

    private fun launchTimer() {
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            while (isActive) {
                delay(1000L)
                _state.update { s ->
                    if (!s.isRunning) return@update s
                    val newHalf = s.halfElapsedSeconds + 1L
                    val newTotal = s.totalElapsedSeconds + 1L
                    s.copy(
                        halfElapsedSeconds = newHalf,
                        totalElapsedSeconds = newTotal,
                        sinBins = s.sinBins.filterNot { it.isExpired(newTotal) }
                    )
                }
            }
        }
    }

    fun updateSetup(homeTeam: String, awayTeam: String, halfLengthMinutes: Int) {
        _state.update {
            it.copy(
                homeTeam = homeTeam.ifBlank { "Home" },
                awayTeam = awayTeam.ifBlank { "Away" },
                halfLengthMinutes = halfLengthMinutes.coerceIn(10, 60)
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
                sinBins = emptyList()
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
            val eventType = when (cardType) {
                CardType.YELLOW -> EventType.YELLOW_CARD
                CardType.RED -> EventType.RED_CARD
                CardType.SIN_BIN -> EventType.SIN_BIN
            }
            val event = MatchEvent(
                type = eventType,
                team = team,
                playerNumber = playerNumber,
                detail = offence,
                matchMinute = s.currentMatchMinute,
                half = s.currentHalf
            )
            val updatedSinBins = if (cardType == CardType.SIN_BIN) {
                s.sinBins + SinBinEntry(
                    team = team,
                    playerNumber = playerNumber,
                    offence = offence,
                    startElapsedSeconds = s.totalElapsedSeconds
                )
            } else s.sinBins
            s.copy(events = s.events + event, sinBins = updatedSinBins)
        }
    }

    fun returnFromSinBin(sinBinId: Long) {
        _state.update { s -> s.copy(sinBins = s.sinBins.filter { it.id != sinBinId }) }
    }

    fun resetMatch() {
        _state.value = MatchState()
    }
}
