package com.refsix.wear.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

class MatchStorage(context: Context) {
    private val prefs = context.getSharedPreferences("ref6_history", Context.MODE_PRIVATE)
    private val progressPrefs = context.getSharedPreferences("ref6_in_progress", Context.MODE_PRIVATE)

    fun hasInProgressState(): Boolean = progressPrefs.contains("state")

    fun saveInProgressState(state: MatchState, htBreakStartMillis: Long = 0L) {
        // commit() is synchronous — the write completes before we return, so a
        // forced app kill immediately after cannot discard the saved state.
        progressPrefs.edit().putString("state", serializeMatchState(state, htBreakStartMillis)).commit()
    }

    fun loadInProgressState(): Pair<MatchState, Long>? {
        val json = progressPrefs.getString("state", null) ?: return null
        return runCatching { deserializeMatchState(json) }.getOrElse {
            clearInProgressState()
            null
        }
    }

    fun clearInProgressState() {
        progressPrefs.edit().clear().commit()
    }

    private fun serializeMatchState(state: MatchState, htBreakStartMillis: Long): String {
        return JSONObject().apply {
            put("schema_version", SCHEMA_VERSION)
            put("htBreakStartMillis", htBreakStartMillis)
            put("role", state.role.name)
            put("homeTeam", state.homeTeam)
            put("awayTeam", state.awayTeam)
            put("kickOffTeam", state.kickOffTeam)
            put("halfLengthMinutes", state.halfLengthMinutes)
            put("ageGroup", state.ageGroup.name)
            put("competitionType", state.competitionType.name)
            put("gradeCode", state.gradeCode)
            put("competitionName", state.competitionName)
            put("homeColour", state.homeColour)
            put("awayColour", state.awayColour)
            put("sinBinMinutes", state.sinBinMinutes)
            put("homeScore", state.homeScore)
            put("awayScore", state.awayScore)
            put("currentHalf", state.currentHalf)
            put("halfElapsedSeconds", state.halfElapsedSeconds)
            put("totalElapsedSeconds", state.totalElapsedSeconds)
            put("phase", state.phase.name)
            put("extraTime", state.extraTime)
            put("matchSetupId", state.matchSetupId ?: "")
            put("kickoffDate", state.kickoffDate)
            put("kickoffTime", state.kickoffTime)
            if (state.referee.isNotEmpty())        put("referee",         state.referee)
            if (state.ar1.isNotEmpty())            put("ar1",             state.ar1)
            if (state.ar2.isNotEmpty())            put("ar2",             state.ar2)
            if (state.fourthOfficial.isNotEmpty()) put("fourthOfficial",  state.fourthOfficial)
            put("totalDistanceMeters", state.totalDistanceMeters.toDouble())
            put("maxSpeedMs", state.maxSpeedMs.toDouble())
            put("validSpeedCount", state.validSpeedCount)
            put("totalValidSpeedSum", state.totalValidSpeedSum.toDouble())
            put("avgHeartRate", state.avgHeartRate)
            put("maxHeartRate", state.maxHeartRate)
            put("heartRateReadings", JSONArray().also { arr ->
                state.heartRateReadings.forEach { arr.put(it) }
            })
            put("events", JSONArray().also { arr ->
                state.events.forEach { ev ->
                    arr.put(JSONObject().apply {
                        put("id", ev.id)
                        put("type", ev.type.name)
                        put("team", ev.team)
                        put("playerNumber", ev.playerNumber)
                        put("detail", ev.detail)
                        put("matchMinute", ev.matchMinute)
                        put("half", ev.half)
                        put("scorerNumber", ev.scorerNumber)
                        put("scorerName", ev.scorerName)
                        if (ev.lat != null) put("lat", ev.lat)
                        if (ev.lng != null) put("lng", ev.lng)
                    })
                }
            })
            put("sinBins", JSONArray().also { arr ->
                state.sinBins.forEach { bin ->
                    arr.put(JSONObject().apply {
                        put("id", bin.id)
                        put("team", bin.team)
                        put("playerNumber", bin.playerNumber)
                        put("offence", bin.offence)
                        put("startElapsedSeconds", bin.startElapsedSeconds)
                        put("durationSeconds", bin.durationSeconds)
                    })
                }
            })
            // GPS points are excluded to keep payload small for fast synchronous commit().
            // Aggregate distance/speed stats above are preserved so the final match record is correct.
        }.toString()
    }

    private fun deserializeMatchState(json: String): Pair<MatchState, Long> {
        val obj = JSONObject(json)
        val version = obj.optInt("schema_version", 0)
        check(version == SCHEMA_VERSION) { "stale in-progress schema v$version (expected $SCHEMA_VERSION)" }
        val htBreakStartMillis = obj.optLong("htBreakStartMillis", 0L)

        val eventsArr = obj.getJSONArray("events")
        val events = (0 until eventsArr.length()).map { i ->
            val e = eventsArr.getJSONObject(i)
            MatchEvent(
                id = e.getLong("id"),
                type = EventType.valueOf(e.getString("type")),
                team = e.getString("team"),
                playerNumber = e.getString("playerNumber"),
                detail = e.getString("detail"),
                matchMinute = e.getInt("matchMinute"),
                half = e.getInt("half"),
                scorerNumber = e.getString("scorerNumber"),
                scorerName = e.getString("scorerName"),
                lat = if (e.has("lat")) e.getDouble("lat") else null,
                lng = if (e.has("lng")) e.getDouble("lng") else null
            )
        }

        val sinBinsArr = obj.getJSONArray("sinBins")
        val sinBins = (0 until sinBinsArr.length()).map { i ->
            val b = sinBinsArr.getJSONObject(i)
            SinBinEntry(
                id = b.getLong("id"),
                team = b.getString("team"),
                playerNumber = b.getString("playerNumber"),
                offence = b.getString("offence"),
                startElapsedSeconds = b.getLong("startElapsedSeconds"),
                durationSeconds = b.getLong("durationSeconds")
            )
        }

        val hrArr = obj.getJSONArray("heartRateReadings")
        val heartRateReadings = (0 until hrArr.length()).map { i -> hrArr.getInt(i) }

        val state = MatchState(
            role = runCatching { MatchRole.valueOf(obj.optString("role", "REFEREE")) }.getOrDefault(MatchRole.REFEREE),
            homeTeam = obj.getString("homeTeam"),
            awayTeam = obj.getString("awayTeam"),
            kickOffTeam = obj.getString("kickOffTeam"),
            halfLengthMinutes = obj.getInt("halfLengthMinutes"),
            ageGroup = AgeGroup.valueOf(obj.getString("ageGroup")),
            competitionType = CompetitionType.valueOf(obj.getString("competitionType")),
            gradeCode = obj.getString("gradeCode"),
            competitionName = obj.getString("competitionName"),
            homeColour = obj.optString("homeColour", ""),
            awayColour = obj.optString("awayColour", ""),
            sinBinMinutes = obj.getInt("sinBinMinutes"),
            homeScore = obj.getInt("homeScore"),
            awayScore = obj.getInt("awayScore"),
            currentHalf = obj.getInt("currentHalf"),
            isRunning = false,
            halfElapsedSeconds = obj.getLong("halfElapsedSeconds"),
            totalElapsedSeconds = obj.getLong("totalElapsedSeconds"),
            phase = MatchPhase.valueOf(obj.getString("phase")),
            extraTime = obj.optBoolean("extraTime", false),
            matchSetupId = obj.getString("matchSetupId").ifEmpty { null },
            kickoffDate = obj.getString("kickoffDate"),
            kickoffTime = obj.getString("kickoffTime"),
            referee = obj.optString("referee", ""),
            ar1 = obj.optString("ar1", ""),
            ar2 = obj.optString("ar2", ""),
            fourthOfficial = obj.optString("fourthOfficial", ""),
            totalDistanceMeters = obj.getDouble("totalDistanceMeters").toFloat(),
            maxSpeedMs = obj.getDouble("maxSpeedMs").toFloat(),
            validSpeedCount = obj.getInt("validSpeedCount"),
            totalValidSpeedSum = obj.getDouble("totalValidSpeedSum").toFloat(),
            avgHeartRate = obj.getInt("avgHeartRate"),
            maxHeartRate = obj.getInt("maxHeartRate"),
            heartRateReadings = heartRateReadings,
            events = events,
            sinBins = sinBins
        )
        return Pair(state, htBreakStartMillis)
    }

    fun saveMatch(state: MatchState, status: String = "completed") {
        val match = SavedMatch(
            dateMillis = System.currentTimeMillis(),
            homeTeam = state.homeTeam,
            awayTeam = state.awayTeam,
            homeScore = state.homeScore,
            awayScore = state.awayScore,
            halfLengthMinutes = state.halfLengthMinutes,
            ageGroup = state.gradeCode.ifEmpty { state.ageGroup.label },
            competition = state.competitionName.ifEmpty { state.competitionType.label },
            status = status,
            homeColour = state.homeColour,
            awayColour = state.awayColour,
            extraTime = state.extraTime,
            gpsTrack = buildGpsTrackJson(state),
            totalDistanceKm = state.totalDistanceKm,
            avgSpeedKmh = state.avgSpeedKmh,
            maxSpeedKmh = state.maxSpeedKmh,
            avgHeartRate = state.avgHeartRate,
            maxHeartRate = state.maxHeartRate,
            events = state.events,
            matchSetupId = state.matchSetupId,
            kickoffDate = state.kickoffDate,
            kickoffTime = state.kickoffTime,
            referee = state.referee,
            ar1 = state.ar1,
            ar2 = state.ar2,
            fourthOfficial = state.fourthOfficial
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
        private const val MAX_MATCHES = 50
        const val SCHEMA_VERSION = 3  // bump whenever serialised fields change

        fun buildGpsTrackJson(state: MatchState): String {
            if (state.gpsPoints.isEmpty()) return ""
            val arr = JSONArray()
            state.gpsPoints.forEach { p ->
                arr.put(JSONObject().apply {
                    put("latitude",   p.lat)
                    put("longitude",  p.lng)
                    put("timestamp",  p.timestamp)
                    put("match_time", p.matchMinute)
                })
            }
            return arr.toString()
        }
    }
}
