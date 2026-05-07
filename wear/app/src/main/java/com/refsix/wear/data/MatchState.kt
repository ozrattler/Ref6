package com.refsix.wear.data

enum class MatchPhase { SETUP, FIRST_HALF, HALF_TIME, SECOND_HALF, FULL_TIME }

enum class CardType { YELLOW, RED, SIN_BIN }

enum class EventType { GOAL, YELLOW_CARD, RED_CARD, SIN_BIN }

data class MatchEvent(
    val id: Long = System.currentTimeMillis(),
    val type: EventType,
    val team: String,
    val playerNumber: String = "",
    val detail: String = "",
    val matchMinute: Int,
    val half: Int
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
    val halfLengthMinutes: Int = 45,
    val homeScore: Int = 0,
    val awayScore: Int = 0,
    val currentHalf: Int = 1,
    val isRunning: Boolean = false,
    val halfElapsedSeconds: Long = 0L,
    val totalElapsedSeconds: Long = 0L,
    val phase: MatchPhase = MatchPhase.SETUP,
    val events: List<MatchEvent> = emptyList(),
    val sinBins: List<SinBinEntry> = emptyList()
) {
    val halfLengthSeconds: Long get() = halfLengthMinutes * 60L
    val isInAdditionalTime: Boolean get() = halfElapsedSeconds > halfLengthSeconds
    val additionalSeconds: Long get() = maxOf(0L, halfElapsedSeconds - halfLengthSeconds)

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
}

object Offences {
    val yellow = listOf(
        "Unsporting behaviour",
        "Dissent",
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
        "Dissent",
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
