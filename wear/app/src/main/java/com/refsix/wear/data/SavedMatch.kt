package com.refsix.wear.data

import org.json.JSONArray
import org.json.JSONObject

data class SavedMatch(
    val id: Long = System.currentTimeMillis(),
    val dateMillis: Long = System.currentTimeMillis(),
    val homeTeam: String,
    val awayTeam: String,
    val homeScore: Int,
    val awayScore: Int,
    val halfLengthMinutes: Int,
    val ageGroup: String = "",
    val competition: String = "",
    val status: String = "completed",
    val gpsTrack: String = "",
    val totalDistanceKm: Float = 0f,
    val avgSpeedKmh: Float = 0f,
    val maxSpeedKmh: Float = 0f,
    val avgHeartRate: Int = 0,
    val maxHeartRate: Int = 0,
    val events: List<MatchEvent>,
    val pocketBaseId: String? = null,
    val matchSetupId: String? = null,
    val kickoffDate: String = "",
    val kickoffTime: String = ""
) {
    fun toJson(): String = JSONObject().apply {
        put("id", id)
        put("dateMillis", dateMillis)
        put("homeTeam", homeTeam)
        put("awayTeam", awayTeam)
        put("homeScore", homeScore)
        put("awayScore", awayScore)
        put("halfLengthMinutes", halfLengthMinutes)
        put("ageGroup", ageGroup)
        put("competition", competition)
        put("status", status)
        put("gpsTrack", gpsTrack)
        put("totalDistanceKm", totalDistanceKm.toDouble())
        put("avgSpeedKmh", avgSpeedKmh.toDouble())
        put("maxSpeedKmh", maxSpeedKmh.toDouble())
        put("avgHeartRate", avgHeartRate)
        put("maxHeartRate", maxHeartRate)
        pocketBaseId?.let { put("pocketBaseId", it) }
        matchSetupId?.let { put("matchSetupId", it) }
        if (kickoffDate.isNotEmpty()) put("kickoffDate", kickoffDate)
        if (kickoffTime.isNotEmpty()) put("kickoffTime", kickoffTime)
        put("events", JSONArray().apply {
            events.forEach { e ->
                put(JSONObject().apply {
                    put("type", e.type.name)
                    put("team", e.team)
                    put("playerNumber", e.playerNumber)
                    put("detail", e.detail)
                    put("matchMinute", e.matchMinute)
                    put("half", e.half)
                    put("scorerNumber", e.scorerNumber)
                    put("scorerName", e.scorerName)
                    e.lat?.let { put("lat", it) }
                    e.lng?.let { put("lng", it) }
                })
            }
        })
    }.toString()

    companion object {
        fun fromJson(json: String): SavedMatch {
            val o = JSONObject(json)
            val ea = o.getJSONArray("events")
            val events = (0 until ea.length()).map { i ->
                val e = ea.getJSONObject(i)
                MatchEvent(
                    type = EventType.valueOf(e.getString("type")),
                    team = e.getString("team"),
                    playerNumber = e.optString("playerNumber", ""),
                    detail = e.optString("detail", ""),
                    matchMinute = e.getInt("matchMinute"),
                    half = e.getInt("half"),
                    scorerNumber = e.optString("scorerNumber", ""),
                    scorerName = e.optString("scorerName", ""),
                    lat = if (e.has("lat")) e.getDouble("lat") else null,
                    lng = if (e.has("lng")) e.getDouble("lng") else null
                )
            }
            return SavedMatch(
                id = o.getLong("id"),
                dateMillis = o.getLong("dateMillis"),
                homeTeam = o.getString("homeTeam"),
                awayTeam = o.getString("awayTeam"),
                homeScore = o.getInt("homeScore"),
                awayScore = o.getInt("awayScore"),
                halfLengthMinutes = o.getInt("halfLengthMinutes"),
                ageGroup = o.optString("ageGroup", ""),
                competition = o.optString("competition", ""),
                status = o.optString("status", "completed"),
                gpsTrack = o.optString("gpsTrack", ""),
                totalDistanceKm = o.optDouble("totalDistanceKm", 0.0).toFloat(),
                avgSpeedKmh = o.optDouble("avgSpeedKmh", 0.0).toFloat(),
                maxSpeedKmh = o.optDouble("maxSpeedKmh", 0.0).toFloat(),
                avgHeartRate = o.optInt("avgHeartRate", 0),
                maxHeartRate = o.optInt("maxHeartRate", 0),
                events = events,
                pocketBaseId = o.optString("pocketBaseId", "").takeIf { it.isNotEmpty() },
                matchSetupId = o.optString("matchSetupId", "").takeIf { it.isNotEmpty() },
                kickoffDate = o.optString("kickoffDate", ""),
                kickoffTime = o.optString("kickoffTime", "")
            )
        }
    }
}
