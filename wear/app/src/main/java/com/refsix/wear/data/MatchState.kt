package com.refsix.wear.data

enum class MatchPhase { SETUP, FIRST_HALF, HALF_TIME, SECOND_HALF, FULL_TIME }

enum class CardType { YELLOW, RED, SIN_BIN }

enum class EventType { GOAL, YELLOW_CARD, RED_CARD, SIN_BIN }

enum class AgeGroup(val label: String, val sinBinMinutes: Int, val defaultHalfMinutes: Int) {
    OPEN_SENIOR("Open/Senior", 10, 45),
    U16("U16", 5, 35),
    U15("U15", 5, 35),
    U14("U14", 5, 30),
    U12("U12", 5, 25)
}

enum class CompetitionType(val label: String) {
    STANDARD("Standard"),
    PLM("PLM"),
    PLR("PLR")
}

enum class CardAlertType {
    SECOND_YELLOW_RED,  // 2nd yellow → auto red card (dismissed)
    DISSENT_SIN_BIN     // dissent yellow → auto sin bin
}

data class CardAlert(
    val team: String,
    val playerNumber: String,
    val type: CardAlertType,
    val sinBinMinutes: Int = 10
)

data class MatchEvent(
    val id: Long = System.currentTimeMillis(),
    val type: EventType,
    val team: String,
    val playerNumber: String = "",
    val detail: String = "",
    val matchMinute: Int,
    val half: Int,
    val scorerNumber: String = "",
    val scorerName: String = "",
    val lat: Double? = null,
    val lng: Double? = null
)

data class SinBinEntry(
    val id: Long = System.currentTimeMillis(),
    val team: String,
    val playerNumber: String,
    val offence: String,
    val startElapsedSeconds: Long,
    val durationSeconds: Long = 600L
) {
    fun remainingSeconds(currentElapsed: Long): Long =
        maxOf(0L, startElapsedSeconds + durationSeconds - currentElapsed)
    fun isExpired(currentElapsed: Long): Boolean =
        currentElapsed >= startElapsedSeconds + durationSeconds
}

data class MatchState(
    val homeTeam: String = "Home",
    val awayTeam: String = "Away",
    val kickOffTeam: String = "",
    val halfLengthMinutes: Int = 45,
    val ageGroup: AgeGroup = AgeGroup.OPEN_SENIOR,
    val competitionType: CompetitionType = CompetitionType.STANDARD,
    val sinBinMinutes: Int = 10,
    val homeScore: Int = 0,
    val awayScore: Int = 0,
    val currentHalf: Int = 1,
    val isRunning: Boolean = false,
    val halfElapsedSeconds: Long = 0L,
    val totalElapsedSeconds: Long = 0L,
    val phase: MatchPhase = MatchPhase.SETUP,
    val events: List<MatchEvent> = emptyList(),
    val sinBins: List<SinBinEntry> = emptyList(),
    val cardAlert: CardAlert? = null,
    val matchSetupId: String? = null,
    val kickoffDate: String = "",
    val kickoffTime: String = "",
    // GPS tracking
    val gpsPoints: List<GpsPoint> = emptyList(),
    val totalDistanceMeters: Float = 0f,
    val maxSpeedMs: Float = 0f,
    val hasGpsFix: Boolean = false,
    val validSpeedCount: Int = 0,
    val totalValidSpeedSum: Float = 0f,
    // Heart rate tracking
    val currentHeartRate: Int = 0,
    val avgHeartRate: Int = 0,
    val maxHeartRate: Int = 0,
    val heartRateReadings: List<Int> = emptyList(),
) {
    val isSpl: Boolean get() = competitionType == CompetitionType.PLM || competitionType == CompetitionType.PLR

    val kickOffTeam2ndHalf: String get() = when (kickOffTeam) {
        homeTeam -> awayTeam
        awayTeam -> homeTeam
        else -> ""
    }

    val totalDistanceKm: Float get() = totalDistanceMeters / 1000f
    val avgSpeedMs: Float get() =
        if (validSpeedCount > 0) totalValidSpeedSum / validSpeedCount else 0f
    val avgSpeedKmh: Float get() = avgSpeedMs * 3.6f
    val maxSpeedKmh: Float get() = maxSpeedMs * 3.6f
    val halfLengthSeconds: Long get() = halfLengthMinutes * 60L
    val halfRemainingSeconds: Long get() = maxOf(0L, halfLengthSeconds - halfElapsedSeconds)
    val isInAdditionalTime: Boolean get() = halfElapsedSeconds > halfLengthSeconds
    val additionalSeconds: Long get() = maxOf(0L, halfElapsedSeconds - halfLengthSeconds)
    val sinBinDurationSeconds: Long get() = sinBinMinutes * 60L

    val displayMinutes: Int get() = when (phase) {
        MatchPhase.SECOND_HALF, MatchPhase.FULL_TIME ->
            halfLengthMinutes + (halfElapsedSeconds / 60).toInt()
        else -> (halfElapsedSeconds / 60).toInt()
    }
    val displaySeconds: Int get() = (halfElapsedSeconds % 60).toInt()

    val currentMatchMinute: Int get() = when (phase) {
        MatchPhase.SECOND_HALF, MatchPhase.FULL_TIME ->
            halfLengthMinutes + (halfElapsedSeconds / 60).toInt() + 1
        else -> (halfElapsedSeconds / 60).toInt() + 1
    }

    val activeSinBins: List<SinBinEntry>
        get() = sinBins.filter { !it.isExpired(totalElapsedSeconds) }

    val goals: List<MatchEvent> get() = events.filter { it.type == EventType.GOAL }
    val yellowCards: List<MatchEvent> get() = events.filter { it.type == EventType.YELLOW_CARD }
    val redCards: List<MatchEvent> get() = events.filter { it.type == EventType.RED_CARD }
    val sinBinEvents: List<MatchEvent> get() = events.filter { it.type == EventType.SIN_BIN }

    fun playerYellowCount(team: String, playerNumber: String): Int =
        events.count { it.type == EventType.YELLOW_CARD && it.team == team && it.playerNumber == playerNumber }

    fun playerInSinBin(team: String, playerNumber: String): Boolean =
        activeSinBins.any { it.team == team && it.playerNumber == playerNumber }
}

object Offences {
    const val DISSENT = "Dissent"

    val yellow = listOf(
        "Unsporting behaviour",
        DISSENT,
        "Persistent infringement",
        "Delaying restart",
        "Failure to respect distance",
        "Entering/leaving without permission",
        "Encroachment at penalty"
    )

    val red = listOf(
        "Serious foul play",
        "Violent conduct",
        "Biting or spitting",
        "DOGSO – foul",
        "DOGSO – handball",
        "Offensive language/gestures",
        "Second caution"
    )

    val sinBin = listOf(
        "Unsporting behaviour",
        DISSENT,
        "Delaying restart",
        "Failure to respect distance",
        "Encroachment at penalty"
    )

    fun forCardType(type: CardType): List<String> = when (type) {
        CardType.YELLOW -> yellow
        CardType.RED -> red
        CardType.SIN_BIN -> sinBin
    }
}
