package com.refsix.wear.viewmodel

import android.Manifest
import android.app.Application
import android.app.NotificationManager
import android.content.pm.PackageManager
import android.location.Location
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
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
import com.refsix.wear.data.MatchRole
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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
    object FullTimeAutoReached : MatchUiEvent()
    object HalfTimeCountdownExpired : MatchUiEvent()
}

data class PendingCard(
    val team: String,
    val playerNumber: String,
    val cardType: CardType,
    val offence: String,
    val isSecondYellow: Boolean,
    val isDissentSinBin: Boolean,
    val sinBinMinutes: Int
)

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

    private val _hasResumableMatch = MutableStateFlow(matchStorage.hasInProgressState())
    val hasResumableMatch: StateFlow<Boolean> = _hasResumableMatch.asStateFlow()

    private val _pendingCard = MutableStateFlow<PendingCard?>(null)
    val pendingCard: StateFlow<PendingCard?> = _pendingCard.asStateFlow()

    private val _sinBinAlert = MutableStateFlow<Pair<String, String>?>(null)
    val sinBinAlert: StateFlow<Pair<String, String>?> = _sinBinAlert.asStateFlow()

    private val _pendingSetups = MutableStateFlow<List<MatchSetupData>>(emptyList())
    val pendingSetups: StateFlow<List<MatchSetupData>> = _pendingSetups.asStateFlow()

    private val _isFetchingSetups = MutableStateFlow(false)
    val isFetchingSetups: StateFlow<Boolean> = _isFetchingSetups.asStateFlow()

    private val connectivityManager: ConnectivityManager? =
        application.getSystemService(ConnectivityManager::class.java)
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private val syncMutex = Mutex()

    private val _syncResult = MutableStateFlow<Boolean?>(null)
    val syncResult: StateFlow<Boolean?> = _syncResult.asStateFlow()

    // One-shot signal: SetupScreen watches this to apply local form fields after
    // returning from the list screen. Cleared by consumeAppliedSetup().
    private val _appliedSetup = MutableStateFlow<MatchSetupData?>(null)
    val appliedSetup: StateFlow<MatchSetupData?> = _appliedSetup.asStateFlow()

    private val _uiEvents = MutableSharedFlow<MatchUiEvent>(extraBufferCapacity = 16)
    val uiEvents: SharedFlow<MatchUiEvent> = _uiEvents.asSharedFlow()

    private var timerJob: Job? = null

    fun clearSyncResult() { _syncResult.value = null }

    fun syncNow() {
        viewModelScope.launch { syncUnsyncedMatches() }
    }

    private fun updateNetworkCallbackRegistration() {
        if (matchStorage.getUnsyncedMatches().isNotEmpty()) registerNetworkCallback()
        else unregisterNetworkCallback()
    }

    private fun registerNetworkCallback() {
        if (networkCallback != null) return
        val cm = connectivityManager ?: return
        val cb = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                viewModelScope.launch { syncUnsyncedMatches() }
            }
        }
        networkCallback = cb
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
            .build()
        try {
            cm.registerNetworkCallback(request, cb)
        } catch (e: Exception) {
            Log.w("MatchViewModel", "registerNetworkCallback failed", e)
            networkCallback = null
        }
    }

    private fun unregisterNetworkCallback() {
        val cb = networkCallback ?: return
        networkCallback = null
        try { connectivityManager?.unregisterNetworkCallback(cb) } catch (_: Exception) {}
    }

    override fun onCleared() {
        super.onCleared()
        unregisterNetworkCallback()
    }

    init {
        launchTimer()
        viewModelScope.launch { syncUnsyncedMatches() }
        // Retry any unsynced matches periodically on startup (covers airplane-mode case)
        viewModelScope.launch {
            repeat(5) {
                delay(60_000L)
                if (matchStorage.getUnsyncedMatches().isNotEmpty()) syncUnsyncedMatches()
            }
        }
        refreshPendingSetup()
        observeRunningForGps()
        observeRunningForDnd()
        updateNetworkCallbackRegistration()
    }

    private fun saveInProgress(htBreakStartMillis: Long = 0L) {
        val s = _state.value
        if (s.phase == MatchPhase.SETUP || s.phase == MatchPhase.FULL_TIME) return
        matchStorage.saveInProgressState(s, htBreakStartMillis)
        _hasResumableMatch.value = true
    }

    private fun launchTimer() {
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            var saveTickCount = 0
            while (isActive) {
                delay(1000L)
                var justExpired = emptyList<SinBinEntry>()
                var halfTimeAutoTrigger = false
                var fullTimeAutoTrigger = false
                _state.update { s ->
                    if (!s.isRunning) return@update s
                    val newHalf = s.halfElapsedSeconds + 1L
                    val newTotal = s.totalElapsedSeconds + 1L
                    justExpired = s.sinBins.filter {
                        !it.isExpired(s.totalElapsedSeconds) && it.isExpired(newTotal)
                    }
                    val justReachedHalfEnd =
                        (s.phase == MatchPhase.FIRST_HALF || s.phase == MatchPhase.SECOND_HALF ||
                         s.phase == MatchPhase.EXTRA_TIME_1 || s.phase == MatchPhase.EXTRA_TIME_2) &&
                        s.halfElapsedSeconds < s.halfLengthSeconds &&
                        newHalf >= s.halfLengthSeconds
                    if (justReachedHalfEnd) {
                        when (s.phase) {
                            MatchPhase.FIRST_HALF, MatchPhase.EXTRA_TIME_1 -> halfTimeAutoTrigger = true
                            MatchPhase.SECOND_HALF, MatchPhase.EXTRA_TIME_2 -> fullTimeAutoTrigger = true
                            else -> {}
                        }
                    }
                    s.copy(
                        halfElapsedSeconds = newHalf,
                        totalElapsedSeconds = newTotal,
                        sinBins = s.sinBins.filterNot { it.isExpired(newTotal) }
                    )
                }
                if (_state.value.isRunning) {
                    saveTickCount++
                    if (saveTickCount >= 10) {
                        saveTickCount = 0
                        saveInProgress()
                    }
                }
                if (justExpired.isNotEmpty()) {
                    saveInProgress()
                }
                justExpired.forEach { bin ->
                    _uiEvents.tryEmit(MatchUiEvent.SinBinExpired(bin.team, bin.playerNumber))
                }
                if (justExpired.isNotEmpty()) {
                    _sinBinAlert.value = justExpired.last().let { it.team to it.playerNumber }
                }
                if (halfTimeAutoTrigger) {
                    _uiEvents.tryEmit(MatchUiEvent.HalfTimeAlert)
                }
                if (fullTimeAutoTrigger) {
                    _uiEvents.tryEmit(MatchUiEvent.FullTimeAutoReached)
                }
            }
        }
    }

    // ── DND (notification suppression) ───────────────────────────────────────

    private fun observeRunningForDnd() {
        viewModelScope.launch {
            _state.map {
                it.phase == MatchPhase.FIRST_HALF ||
                it.phase == MatchPhase.SECOND_HALF ||
                it.phase == MatchPhase.HALF_TIME ||
                it.phase == MatchPhase.EXTRA_TIME_1 ||
                it.phase == MatchPhase.EXTRA_TIME_2
            }.distinctUntilChanged().collect { matchActive ->
                val nm = notificationManager ?: return@collect
                if (!nm.isNotificationPolicyAccessGranted) return@collect
                if (matchActive) {
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
        // 4th Official mode: no GPS — distance/speed not recorded for this role.
        if (_state.value.role == MatchRole.FOURTH_OFFICIAL) return
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
        if (location.hasAccuracy() && location.accuracy > 100f) return
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

            // Count distance when implied speed is plausible. The speed cap is the
            // sole guard against GPS coordinate jumps — the upper time bound was
            // redundant and was dropping real movement during infrequent Wear OS
            // GPS updates (which routinely arrive 20–40 s apart in ambient mode).
            val distanceAdded = if (s.gpsPoints.isNotEmpty()) {
                val prev = s.gpsPoints.last()
                val timeDeltaSec = (now - prev.timestamp) / 1000L
                if (timeDeltaSec >= 1L) {
                    val results = FloatArray(1)
                    Location.distanceBetween(prev.lat, prev.lng, newPoint.lat, newPoint.lng, results)
                    val impliedSpeedMs = results[0] / timeDeltaSec
                    if (impliedSpeedMs <= MAX_PLAUSIBLE_SPEED_MS) results[0] else 0f
                } else 0f
            } else 0f

            // GPS-reported speed: accept only readings in the plausible range.
            // Require two consecutive valid readings before recording max speed —
            // a single Doppler spike followed by walking doesn't constitute a sprint.
            val validSpeed = rawSpeedMs.takeIf { it in 0.1f..MAX_PLAUSIBLE_SPEED_MS }
            val newMax = if (validSpeed != null && s.prevValidSpeedMs > 0f)
                maxOf(s.maxSpeedMs, validSpeed)
            else s.maxSpeedMs
            val newCount = if (validSpeed != null) s.validSpeedCount + 1 else s.validSpeedCount
            val newSum   = if (validSpeed != null) s.totalValidSpeedSum + validSpeed else s.totalValidSpeedSum

            s.copy(
                gpsPoints = s.gpsPoints + newPoint,
                totalDistanceMeters = s.totalDistanceMeters + distanceAdded,
                maxSpeedMs = newMax,
                prevValidSpeedMs = validSpeed ?: 0f,
                validSpeedCount = newCount,
                totalValidSpeedSum = newSum,
                hasGpsFix = true
            )
        }
    }

    companion object {
        private const val MAX_PLAUSIBLE_SPEED_MS = 6.944f  // 25 km/h — covers referee sprinting
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
        matchStorage.clearInProgressState()
        _hasResumableMatch.value = false
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
                prevValidSpeedMs = 0f,
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
        saveInProgress()
    }

    fun callHalfTime() {
        _state.update { it.copy(phase = MatchPhase.HALF_TIME, isRunning = false) }
        val htStartMillis = System.currentTimeMillis()
        startHalfTimeCountdown()
        saveInProgress(htBreakStartMillis = htStartMillis)
    }

    fun ensureHalfTimeCountdown() {
        if (halfTimeCountdownJob?.isActive == true) return  // already running
        if (halfTimeCountdownJob != null) return            // already completed
        if (_state.value.phase == MatchPhase.HALF_TIME) startHalfTimeCountdown()
    }

    private fun startHalfTimeCountdown(fromSeconds: Int = 300) {
        halfTimeCountdownJob?.cancel()
        halfTimeCountdownJob = viewModelScope.launch {
            var remaining = fromSeconds
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

    fun resumeMatch() {
        // Guard: never overwrite a match that's already in progress. This prevents
        // Wear OS activity recreation (screen-off/wrist-turn) from calling resumeMatch
        // via the startup LaunchedEffect and stopping the running timer.
        if (_state.value.phase != MatchPhase.SETUP) return
        val (savedState, htBreakStartMillis) = matchStorage.loadInProgressState() ?: return
        _state.value = savedState
        if (savedState.phase == MatchPhase.HALF_TIME) {
            val elapsedSec = if (htBreakStartMillis > 0)
                ((System.currentTimeMillis() - htBreakStartMillis) / 1000L).toInt()
            else Int.MAX_VALUE
            startHalfTimeCountdown(maxOf(0, 300 - elapsedSec))
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
        saveInProgress()
    }

    fun prepareEtHalfBreak() {
        _state.update { it.copy(isRunning = false) }
        saveInProgress()
    }

    fun startExtraTime() {
        _state.update {
            it.copy(
                phase = MatchPhase.EXTRA_TIME_1,
                currentHalf = 3,
                halfElapsedSeconds = 0L,
                isRunning = true
            )
        }
        saveInProgress()
    }

    fun startExtraTime2() {
        _state.update {
            it.copy(
                phase = MatchPhase.EXTRA_TIME_2,
                currentHalf = 4,
                halfElapsedSeconds = 0L,
                isRunning = true
            )
        }
        saveInProgress()
    }

    fun callFullTime() {
        if (_state.value.phase == MatchPhase.FULL_TIME) return
        _state.update { it.copy(phase = MatchPhase.FULL_TIME, isRunning = false) }
        matchStorage.clearInProgressState()
        _hasResumableMatch.value = false
        matchStorage.saveMatch(_state.value)
        _savedMatches.value = matchStorage.loadMatches()
        updateNetworkCallbackRegistration()
        viewModelScope.launch {
            _uiEvents.emit(MatchUiEvent.FullTimeAlert)
            syncUnsyncedMatches()
            // Refresh the fixture list so next open of setup list shows current pending setups.
            refreshPendingSetupInternal()
        }
        // Retry sync in background every 30 s for up to 5 min (handles airplane-mode case)
        viewModelScope.launch {
            repeat(10) {
                delay(30_000L)
                if (matchStorage.getUnsyncedMatches().isNotEmpty()) syncUnsyncedMatches()
            }
        }
    }

    private suspend fun syncUnsyncedMatches() {
        // Mutex ensures only one sync run executes at a time. Concurrent triggers
        // (full-time coroutine, retry loop, NetworkCallback, manual button) all
        // queue here; by the time the second caller acquires the lock the first
        // has already called markSynced, so getUnsyncedMatches() returns empty
        // and no duplicate POST is sent.
        syncMutex.withLock {
            try {
                val unsynced = matchStorage.getUnsyncedMatches()
                if (unsynced.isEmpty()) {
                    unregisterNetworkCallback()
                    return
                }
                if (!pocketBaseSync.isNetworkAvailable()) {
                    registerNetworkCallback()
                    return
                }
                var anySuccess = false
                var anyFailed = false
                unsynced.forEach { match ->
                    var pbId: String? = null
                    for (attempt in 1..3) {
                        pbId = pocketBaseSync.syncMatch(match)
                        if (pbId != null) break
                        if (attempt < 3) delay(3_000L)
                    }
                    // Only mark synced when syncMatch confirms HTTP 2xx and returns a valid ID.
                    // Any failure (403, timeout, exception) leaves the match unsynced for retry.
                    if (pbId != null) {
                        matchStorage.markSynced(match.id, pbId)
                        _savedMatches.value = matchStorage.loadMatches()
                        anySuccess = true
                    } else {
                        Log.w("MatchViewModel", "syncUnsyncedMatches: match ${match.id} not synced — will retry")
                        anyFailed = true
                    }
                }
                _syncResult.value = if (anyFailed) false else if (anySuccess) true else null
                updateNetworkCallbackRegistration()
            } catch (e: Exception) {
                Log.e("MatchViewModel", "syncUnsyncedMatches: unexpected exception — local data preserved", e)
            }
        }
    }

    fun refreshPendingSetup() {
        viewModelScope.launch { refreshPendingSetupInternal() }
    }

    private suspend fun refreshPendingSetupInternal() {
        _isFetchingSetups.value = true
        val hasNetwork = pocketBaseSync.isNetworkAvailable()
        Log.d("MatchViewModel", "refreshPendingSetup: hasNetwork=$hasNetwork")
        if (hasNetwork) {
            val setups = pocketBaseSync.fetchPendingMatchSetups()
            if (setups != null) {
                val playedSetupIds = _savedMatches.value.mapNotNull { it.matchSetupId }.toSet()
                val filtered = setups.filter { it.id !in playedSetupIds }
                Log.d("MatchViewModel", "refreshPendingSetup: ${setups.size} from server, ${filtered.size} after filtering played")
                _pendingSetups.value = filtered
            } else {
                Log.w("MatchViewModel", "refreshPendingSetup: fetch failed, keeping existing list")
            }
        }
        _isFetchingSetups.value = false
    }

    fun applyMatchSetup(setup: MatchSetupData, role: MatchRole = MatchRole.REFEREE) {
        _appliedSetup.value = setup
        _state.update {
            it.copy(
                role = role,
                homeTeam = setup.homeTeam.ifBlank { "Home" },
                awayTeam = setup.awayTeam.ifBlank { "Away" },
                ageGroup = setup.ageGroup,
                halfLengthMinutes = setup.halfLengthMinutes,
                competitionType = setup.competitionType,
                gradeCode = setup.gradeCode,
                competitionName = setup.competition,
                sinBinMinutes = setup.sinBinMinutes,
                extraTime = setup.extraTime,
                matchSetupId = setup.id,
                kickoffDate = setup.kickoffDate,
                kickoffTime = setup.kickoffTime,
                homeColour = setup.homeColour,
                awayColour = setup.awayColour,
                referee = setup.referee,
                ar1 = setup.ar1,
                ar2 = setup.ar2,
                fourthOfficial = setup.fourthOfficial
            )
        }
    }

    fun consumeAppliedSetup() {
        _appliedSetup.value = null
    }

    fun dismissPendingSetups() {
        _pendingSetups.value = emptyList()
    }

    fun setPendingCard(card: PendingCard) { _pendingCard.value = card }
    fun clearPendingCard() { _pendingCard.value = null }

    fun recordGoal(team: String, scorerNumber: String = "", scorerName: String = "", goalType: String = "") {
        val loc = lastLocation
        val minute = _state.value.currentMatchMinute
        val half = _state.value.currentHalf
        Log.d("MatchViewModel", "recordGoal: team=$team minute=$minute half=$half")
        _state.update { s ->
            val isHome = team == s.homeTeam
            val event = MatchEvent(
                type = EventType.GOAL,
                team = team,
                matchMinute = minute,
                half = half,
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
        saveInProgress()
    }

    fun recordCard(team: String, playerNumber: String, cardType: CardType, offence: String) {
        val loc = lastLocation
        val minute = _state.value.currentMatchMinute
        val half = _state.value.currentHalf
        Log.d("MatchViewModel", "recordCard: team=$team player=$playerNumber cardType=$cardType minute=$minute half=$half")
        _state.update { s ->

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
        saveInProgress()
    }

    fun dismissCardAlert() {
        _state.update { it.copy(cardAlert = null) }
    }

    fun dismissSinBinAlert() { _sinBinAlert.value = null }

    fun returnFromSinBin(sinBinId: Long) {
        _state.update { s -> s.copy(sinBins = s.sinBins.filter { it.id != sinBinId }) }
        saveInProgress()
    }

    fun deleteEvent(eventId: Long) {
        _state.update { s ->
            val event = s.events.find { it.id == eventId } ?: return@update s
            val newEvents = s.events.filter { it.id != eventId }
            val homeScore = if (event.type == EventType.GOAL && event.team == s.homeTeam)
                maxOf(0, s.homeScore - 1) else s.homeScore
            val awayScore = if (event.type == EventType.GOAL && event.team == s.awayTeam)
                maxOf(0, s.awayScore - 1) else s.awayScore
            s.copy(events = newEvents, homeScore = homeScore, awayScore = awayScore)
        }
        saveInProgress()
    }

    fun editEvent(
        eventId: Long,
        newType: EventType,
        newTeam: String,
        newPlayerNumber: String,
        newMinute: Int
    ) {
        _state.update { s ->
            val event = s.events.find { it.id == eventId } ?: return@update s
            val updatedEvent = event.copy(
                type = newType,
                team = newTeam,
                playerNumber = newPlayerNumber,
                matchMinute = newMinute
            )
            val updatedEvents = s.events.map { if (it.id == eventId) updatedEvent else it }
            var homeScore = s.homeScore
            var awayScore = s.awayScore
            val oldIsGoal = event.type == EventType.GOAL
            val newIsGoal = newType == EventType.GOAL
            when {
                // Goal removed: decrement old team's score
                oldIsGoal && !newIsGoal -> {
                    if (event.team == s.homeTeam) homeScore = maxOf(0, homeScore - 1)
                    else if (event.team == s.awayTeam) awayScore = maxOf(0, awayScore - 1)
                }
                // Goal added: increment new team's score
                !oldIsGoal && newIsGoal -> {
                    if (newTeam == s.homeTeam) homeScore += 1
                    else if (newTeam == s.awayTeam) awayScore += 1
                }
                // Goal kept but team swapped: move the point
                oldIsGoal && newIsGoal && event.team != newTeam -> {
                    if (event.team == s.homeTeam) {
                        homeScore = maxOf(0, homeScore - 1); awayScore += 1
                    } else {
                        awayScore = maxOf(0, awayScore - 1); homeScore += 1
                    }
                }
            }
            s.copy(events = updatedEvents, homeScore = homeScore, awayScore = awayScore)
        }
        saveInProgress()
    }

    fun clearHistory() {
        matchStorage.clearHistory()
        _savedMatches.value = emptyList()
    }

    fun resetMatch() {
        halfTimeCountdownJob?.cancel()
        _halfTimeCountdown.value = 0
        _state.value = MatchState()
        matchStorage.clearInProgressState()
        _hasResumableMatch.value = false
    }
}
