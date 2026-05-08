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
            ageGroup = state.ageGroup.label,
            competition = state.competitionType.label,
            events = state.events,
            matchSetupId = state.matchSetupId
        )
        val existing = loadMatches().toMutableList()
        existing.add(0, match)
        persist(existing.take(MAX_MATCHES))
    }

    fun loadMatches(): List<SavedMatch> {
        val count = prefs.getInt("count", 0)
        return (0 until count).mapNotNull { i ->
            runCatching {
                SavedMatch.fromJson(prefs.getString("match_$i", null) ?: return@mapNotNull null)
            }.getOrNull()
        }
    }

    fun getUnsyncedMatches(): List<SavedMatch> = loadMatches().filter { it.pocketBaseId == null }

    fun markSynced(matchId: Long, pocketBaseId: String) {
        val updated = loadMatches().map { m ->
            if (m.id == matchId) m.copy(pocketBaseId = pocketBaseId) else m
        }
        persist(updated)
    }

    fun clearHistory() {
        prefs.edit().clear().apply()
    }

    private fun persist(matches: List<SavedMatch>) {
        prefs.edit().apply {
            putInt("count", matches.size)
            matches.forEachIndexed { i, m -> putString("match_$i", m.toJson()) }
            apply()
        }
    }

    companion object {
        private const val MAX_MATCHES = 5
    }
}
