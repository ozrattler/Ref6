package com.refsix.wear.viewmodel

import android.Manifest
import android.app.Application
import android.app.NotificationManager
import android.content.pm.PackageManager
import android.location.Location
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.refsix.wear.data.AgeGroup
import com.refsix.wear.data.CardAlert
import com.refsix.wear.data.CardAlertType
import com.refsix.wear.data.CardType
import com.refsix.wear.data.CompetitionType
import com.refsix.wear.data.EventType
import com.refsix.wear.data.GpsPoint
import com.refsix.wear.data.GpsTracker
import com.refsix.wear.data.HeartRateTracker
import com.refsix.wear.data.MatchEvent
import com.refsix.wear.data.MatchPhase
import com.refsix.wear.data.MatchState
import com.refsix.wear.data.MatchStorage
import com.refsix.wear.data.Offences
import com.refsix.wear.data.MatchSetupData
import com.refsix.wear.data.PocketBaseSync
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
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

sealed class MatchUiEvent {
    data class SinBinExpired(val team: String, val playerNumber: String) : MatchUiEvent()
    object HalfTimeAlert : MatchUiEvent()
    object FullTimeAlert : MatchUiEvent()
    object HalfTimeCountdownExpired : MatchUiEvent()
}

class MatchViewModel(application: Application) : AndroidViewModel(application) {

    private val matchStorage = MatchStorage(application)
    private val pocketBaseSync = PocketBaseSync(application)
    private val gpsTracker = GpsTracker(application)
    private val heartRateTracker = HeartRateTracker(application)
    private val notificationManager: NotificationManager? =
        application.getSystemService(NotificationManager::class.java)
    private var gpsActive = false
    private var hrActive = false
    private var lastLocation: Location? = null
    private var lastHrSampleMs = 0L
    private var savedInterruptionFilter = NotificationManager.INTERRUPTION_FILTER_UNKNOWN

    private val _state = MutableStateFlow(MatchState())
    val state: StateFlow<MatchState> = _state.asStateFlow()

    // Counter-based signal: LaunchedEffect key changes on each increment,
    // restarting the effect without any mid-animation state mutation.
    private val _returnToCenterCount = MutableStateFlow(0)
    val returnToCenterCount: StateFlow<Int> = _returnToCenterCount.asStateFlow()

    private val _halfTimeCountdown = MutableStateFlow(0)
    val halfTimeCountdown: StateFlow<Int> = _halfTimeCountdown.asStateFlow()
    private var halfTimeCountdownJob: Job? = null

    private val _savedMatches = MutableStateFlow(matchStorage.loadMatches())
    val savedMatches: StateFlow<List<SavedMatch>> = _savedMatches.asStateFlow()

    private val _pendingSetups = MutableStateFlow<List<MatchSetupData>>(emptyList())
    val pendingSetups: StateFlow<List<MatchSetupData>> = _pendingSetups.asStateFlow()

    private val _isFetchingSetups = MutableStateFlow(false)
    val isFetchingSetups: StateFlow<Boolean> = _isFetchingSetups.asStateFlow()

    // One-shot signal: SetupScreen watches this to apply local form fields after
    // returning from the list screen. Cleared by consumeAppliedSetup().
    private val _appliedSetup = MutableStateFlow<MatchSetupData?>(null)
    val appliedSetup: StateFlow<MatchSetupData?> = _appliedSetup.asStateFlow()

    private val _uiEvents = MutableSharedFlow<MatchUiEvent>(extraBufferCapacity = 16)
    val uiEvents: SharedFlow<MatchUiEvent> = _uiEvents.asSharedFlow()

    private var timerJob: Job? = null

    init {
        launchTimer()
        viewModelScope.launch { syncUnsyncedMatches() }
        refreshPendingSetup()
        observeRunningForGps()
        observeRunningForDnd()
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

    // ── DND (notification suppression) ───────────────────────────────────────

    private fun observeRunningForDnd() {
        viewModelScope.launch {
            _state.map { it.isRunning }.distinctUntilChanged().collect { running ->
                val nm = notificationManager ?: return@collect
                if (!nm.isNotificationPolicyAccessGranted) return@collect
                if (running) {
                    savedInterruptionFilter = nm.currentInterruptionFilter
                    nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_NONE)
                } else {
                    val restore = if (savedInterruptionFilter != NotificationManager.INTERRUPTION_FILTER_UNKNOWN)
                        savedInterruptionFilter else NotificationManager.INTERRUPTION_FILTER_ALL
                    nm.setInterruptionFilter(restore)
                    savedInterruptionFilter = NotificationManager.INTERRUPTION_FILTER_UNKNOWN
                }
            }
        }
    }

    // ── GPS tracking ─────────────────────────────────────────────────────────

    private fun observeRunningForGps() {
        viewModelScope.launch {
            _state.map { it.isRunning }.distinctUntilChanged().collect { running ->
                if (running) {
                    startGpsTracking()
                    startHeartRateTracking()
                } else {
                    stopGpsTracking()
                    stopHeartRateTracking()
                }
            }
        }
    }

    private fun startGpsTracking() {
        if (gpsActive) return
        val app = getApplication<Application>()
        if (ContextCompat.checkSelfPermission(app, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED) return
        gpsActive = true
        gpsTracker.startTracking(
            onLocation = { location -> processGpsLocation(location) },
            onFixChanged = { hasFix -> _state.update { it.copy(hasGpsFix = hasFix) } }
        )
    }

    private fun stopGpsTracking() {
        if (!gpsActive) return
        gpsActive = false
        gpsTracker.stopTracking()
        _state.update { it.copy(hasGpsFix = false) }
    }

    private fun startHeartRateTracking() {
        if (hrActive || !heartRateTracker.isAvailable) return
        val app = getApplication<Application>()
        if (ContextCompat.checkSelfPermission(app, Manifest.permission.BODY_SENSORS)
            != PackageManager.PERMISSION_GRANTED) return
        hrActive = true
        heartRateTracker.startTracking { bpm -> processHeartRate(bpm) }
    }

    private fun stopHeartRateTracking() {
        if (!hrActive) return
        hrActive = false
        heartRateTracker.stopTracking()
        _state.update { it.copy(currentHeartRate = 0) }
    }

    private fun processHeartRate(bpm: Int) {
        val now = System.currentTimeMillis()
        val doSample = now - lastHrSampleMs >= 10_000L
        if (doSample) lastHrSampleMs = now
        _state.update { s ->
            if (!s.isRunning) return@update s
            val readings = if (doSample) s.heartRateReadings + bpm else s.heartRateReadings
            s.copy(
                currentHeartRate = bpm,
                heartRateReadings = readings,
                avgHeartRate = if (readings.isNotEmpty()) readings.average().toInt() else 0,
                maxHeartRate = maxOf(s.maxHeartRate, bpm)
            )
        }
    }

    private fun processGpsLocation(location: Location) {
        if (location.hasAccuracy() && location.accuracy > 50f) return
        val rawSpeedMs = if (location.hasSpeed()) location.speed else 0f
        lastLocation = location

        _state.update { s ->
            if (!s.isRunning) return@update s

            val now = System.currentTimeMillis()
            val newPoint = GpsPoint(
                timestamp = now,
                matchMinute = s.currentMatchMinute,
                half = s.currentHalf,
                lat = location.latitude,
                lng = location.longitude,
                accuracyMeters = if (location.hasAccuracy()) location.accuracy else 0f,
                speedMs = rawSpeedMs
            )

            // Only count distance between consecutive points when the implied speed
            // is plausible (≤ 20 km/h). GPS drift between updates produces large
            // coordinate jumps that would otherwise inflate both distance and speed.
            val distanceAdded = if (s.gpsPoints.isNotEmpty()) {
                val prev = s.gpsPoints.last()
                val timeDeltaSec = (now - prev.timestamp) / 1000L
                if (timeDeltaSec in 1L..15L) {
                    val results = FloatArray(1)
                    Location.distanceBetween(prev.lat, prev.lng, newPoint.lat, newPoint.lng, results)
                    val impliedSpeedMs = results[0] / timeDeltaSec
                    if (impliedSpeedMs <= MAX_PLAUSIBLE_SPEED_MS) results[0] else 0f
                } else 0f
            } else 0f

            // GPS-reported speed: accept only readings ≤ 20 km/h.
            val validSpeed = rawSpeedMs.takeIf { it in 0.1f..MAX_PLAUSIBLE_SPEED_MS }
            val newMax = if (validSpeed != null) maxOf(s.maxSpeedMs, validSpeed) else s.maxSpeedMs
            val newCount = if (validSpeed != null) s.validSpeedCount + 1 else s.validSpeedCount
            val newSum   = if (validSpeed != null) s.totalValidSpeedSum + validSpeed else s.totalValidSpeedSum

            s.copy(
                gpsPoints = s.gpsPoints + newPoint,
                totalDistanceMeters = s.totalDistanceMeters + distanceAdded,
                maxSpeedMs = newMax,
                validSpeedCount = newCount,
                totalValidSpeedSum = newSum,
                hasGpsFix = true
            )
        }
    }

    companion object {
        private const val MAX_PLAUSIBLE_SPEED_MS = 3.333f  // 12 km/h
    }

    fun signalReturnToCenter() { _returnToCenterCount.update { it + 1 } }

    fun updateSetup(homeTeam: String, awayTeam: String, kickOffTeam: String = "") {
        _state.update {
            it.copy(
                homeTeam = homeTeam.ifBlank { "Home" },
                awayTeam = awayTeam.ifBlank { "Away" },
                kickOffTeam = kickOffTeam
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
                cardAlert = null,
                gpsPoints = emptyList(),
                totalDistanceMeters = 0f,
                maxSpeedMs = 0f,
                hasGpsFix = false,
                validSpeedCount = 0,
                totalValidSpeedSum = 0f,
                currentHeartRate = 0,
                avgHeartRate = 0,
                maxHeartRate = 0,
                heartRateReadings = emptyList()
            )
        }
    }

    fun toggleTimer() {
        _state.update { it.copy(isRunning = !it.isRunning) }
    }

    fun callHalfTime() {
        _state.update { it.copy(phase = MatchPhase.HALF_TIME, isRunning = false) }
        startHalfTimeCountdown()
    }

    fun ensureHalfTimeCountdown() {
        if (halfTimeCountdownJob?.isActive == true) return  // already running
        if (halfTimeCountdownJob != null) return            // already completed
        if (_state.value.phase == MatchPhase.HALF_TIME) startHalfTimeCountdown()
    }

    private fun startHalfTimeCountdown() {
        halfTimeCountdownJob?.cancel()
        halfTimeCountdownJob = viewModelScope.launch {
            var remaining = 300
            _halfTimeCountdown.value = remaining
            while (remaining > 0 && isActive) {
                delay(1000L)
                remaining--
                _halfTimeCountdown.value = remaining
            }
            if (isActive) {
                _uiEvents.tryEmit(MatchUiEvent.HalfTimeCountdownExpired)
            }
        }
    }

    fun startSecondHalf() {
        halfTimeCountdownJob?.cancel()
        _halfTimeCountdown.value = 0
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
        viewModelScope.launch {
            _uiEvents.emit(MatchUiEvent.FullTimeAlert)
            syncUnsyncedMatches()
        }
    }

    private suspend fun syncUnsyncedMatches() {
        if (!pocketBaseSync.isNetworkAvailable()) return
        matchStorage.getUnsyncedMatches().forEach { match ->
            val pbId = pocketBaseSync.syncMatch(match)
            if (pbId != null) {
                matchStorage.markSynced(match.id, pbId)
                _savedMatches.value = matchStorage.loadMatches()
            }
        }
    }

    fun refreshPendingSetup() {
        viewModelScope.launch {
            _isFetchingSetups.value = true
            val hasNetwork = pocketBaseSync.isNetworkAvailable()
            Log.d("MatchViewModel", "refreshPendingSetup: hasNetwork=$hasNetwork")
            if (hasNetwork) {
                val setups = pocketBaseSync.fetchPendingMatchSetups()
                Log.d("MatchViewModel", "refreshPendingSetup: ${setups.size} pending setups")
                _pendingSetups.value = setups
            }
            _isFetchingSetups.value = false
        }
    }

    fun applyMatchSetup(setup: MatchSetupData) {
        _appliedSetup.value = setup
        _state.update {
            it.copy(
                ageGroup = setup.ageGroup,
                halfLengthMinutes = setup.halfLengthMinutes,
                competitionType = setup.competitionType,
                sinBinMinutes = setup.sinBinMinutes,
                matchSetupId = setup.id,
                kickoffDate = setup.kickoffDate,
                kickoffTime = setup.kickoffTime
            )
        }
    }

    fun consumeAppliedSetup() {
        _appliedSetup.value = null
    }

    fun dismissPendingSetups() {
        _pendingSetups.value = emptyList()
    }

    fun recordGoal(team: String, scorerNumber: String = "", scorerName: String = "", goalType: String = "") {
        val loc = lastLocation
        _state.update { s ->
            val isHome = team == s.homeTeam
            val event = MatchEvent(
                type = EventType.GOAL,
                team = team,
                matchMinute = s.currentMatchMinute,
                half = s.currentHalf,
                scorerNumber = scorerNumber,
                scorerName = scorerName,
                detail = goalType,
                lat = loc?.latitude,
                lng = loc?.longitude
            )
            s.copy(
                homeScore = if (isHome) s.homeScore + 1 else s.homeScore,
                awayScore = if (!isHome) s.awayScore + 1 else s.awayScore,
                events = s.events + event
            )
        }
    }

    fun recordCard(team: String, playerNumber: String, cardType: CardType, offence: String) {
        val loc = lastLocation
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
                half = half,
                lat = loc?.latitude,
                lng = loc?.longitude
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
                        half = half,
                        lat = loc?.latitude,
                        lng = loc?.longitude
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
        halfTimeCountdownJob?.cancel()
        _halfTimeCountdown.value = 0
        _state.value = MatchState()
    }
}
