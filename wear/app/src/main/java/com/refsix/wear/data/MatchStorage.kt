package com.refsix.wear.data

import android.content.Context

class MatchStorage(context: Context) {
    private val prefs = context.getSharedPreferences("ref6_history", Context.MODE_PRIVATE)

    fun saveMatch(state: MatchState) {
        val match = SavedMatch(
            dateMillis = System.currentTimeMillis(),
            homeTeam = state.homeTeam,
            awayTeam = state.awayTeam,
            homeScore = state.homeScore,
            awayScore = state.awayScore,
            halfLengthMinutes = state.halfLengthMinutes,
            events = state.events
        )
        val existing = loadMatches().toMutableList()
        existing.add(0, match)
        val toSave = existing.take(MAX_MATCHES)
        prefs.edit().apply {
            putInt("count", toSave.size)
            toSave.forEachIndexed { i, m -> putString("match_$i", m.toJson()) }
            apply()
        }
    }

    fun loadMatches(): List<SavedMatch> {
        val count = prefs.getInt("count", 0)
        return (0 until count).mapNotNull { i ->
            runCatching {
                SavedMatch.fromJson(prefs.getString("match_$i", null) ?: return@mapNotNull null)
            }.getOrNull()
        }
    }

    companion object {
        private const val MAX_MATCHES = 3
    }
}
