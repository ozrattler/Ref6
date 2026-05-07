package com.refsix.wear.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.refsix.wear.data.AgeGroup
import com.refsix.wear.data.CardAlert
import com.refsix.wear.data.CardAlertType
import com.refsix.wear.data.CardType
import com.refsix.wear.data.CompetitionType
import com.refsix.wear.data.EventType
import com.refsix.wear.data.MatchEvent
import com.refsix.wear.data.MatchPhase
import com.refsix.wear.data.MatchState
import com.refsix.wear.data.MatchStorage
import com.refsix.wear.data.Offences
import com.refsix.wear.data.SavedMatch
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
    object HalfTimeAlert : MatchUiEvent()
    object FullTimeAlert : MatchUiEvent()
}

class MatchViewModel(application: Application) : AndroidViewModel(application) {

    private val matchStorage = MatchStorage(application)

    private val _state = MutableStateFlow(MatchState())
    val state: StateFlow<MatchState> = _state.asStateFlow()

    // Counter-based signal: LaunchedEffect key changes on each increment,
    // restarting the effect without any mid-animation state mutation.
    private val _returnToCenterCount = MutableStateFlow(0)
    val returnToCenterCount: StateFlow<Int> = _returnToCenterCount.asStateFlow()

    private val _savedMatches = MutableStateFlow(matchStorage.loadMatches())
    val savedMatches: StateFlow<List<SavedMatch>> = _savedMatches.asStateFlow()

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
                var justReachedHalfEnd = false
                _state.update { s ->
                    if (!s.isRunning) return@update s
                    val newHalf = s.halfElapsedSeconds + 1L
                    val newTotal = s.totalElapsedSeconds + 1L
                    justExpired = s.sinBins.filter {
                        !it.isExpired(s.totalElapsedSeconds) && it.isExpired(newTotal)
                    }
                    justReachedHalfEnd =
                        (s.phase == MatchPhase.FIRST_HALF || s.phase == MatchPhase.SECOND_HALF) &&
                        s.halfElapsedSeconds < s.halfLengthSeconds &&
                        newHalf >= s.halfLengthSeconds
                    s.copy(
                        halfElapsedSeconds = newHalf,
                        totalElapsedSeconds = newTotal,
                        sinBins = s.sinBins.filterNot { it.isExpired(newTotal) }
                    )
                }
                justExpired.forEach { bin ->
                    _uiEvents.tryEmit(MatchUiEvent.SinBinExpired(bin.team, bin.playerNumber))
                }
                if (justReachedHalfEnd) {
                    _uiEvents.tryEmit(MatchUiEvent.HalfTimeAlert)
                }
            }
        }
    }

    fun signalReturnToCenter() { _returnToCenterCount.update { it + 1 } }

    fun updateSetup(
        homeTeam: String,
        awayTeam: String,
        halfLengthMinutes: Int,
        ageGroup: AgeGroup,
        sinBinMinutes: Int,
        competitionType: CompetitionType
    ) {
        _state.update {
            it.copy(
                homeTeam = homeTeam.ifBlank { "Home" },
                awayTeam = awayTeam.ifBlank { "Away" },
                halfLengthMinutes = halfLengthMinutes.coerceIn(10, 60),
                ageGroup = ageGroup,
                competitionType = competitionType,
                sinBinMinutes = sinBinMinutes.coerceIn(1, 30)
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
        matchStorage.saveMatch(_state.value)
        _savedMatches.value = matchStorage.loadMatches()
        viewModelScope.launch { _uiEvents.emit(MatchUiEvent.FullTimeAlert) }
    }

    fun recordGoal(team: String, scorerNumber: String = "", scorerName: String = "") {
        _state.update { s ->
            val isHome = team == s.homeTeam
            val event = MatchEvent(
                type = EventType.GOAL,
                team = team,
                matchMinute = s.currentMatchMinute,
                half = s.currentHalf,
                scorerNumber = scorerNumber,
                scorerName = scorerName
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
                cardType == CardType.RED -> {
                    newEvents = s.events + cardEvent
                    updatedSinBins = s.sinBins.filterNot {
                        it.team == team && it.playerNumber == playerNumber
                    }
                    alert = s.cardAlert
                }

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

                cardType == CardType.YELLOW &&
                    offence == Offences.DISSENT -> {
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

    fun clearHistory() {
        matchStorage.clearHistory()
        _savedMatches.value = emptyList()
    }

    fun resetMatch() {
        _state.value = MatchState()
    }
}
