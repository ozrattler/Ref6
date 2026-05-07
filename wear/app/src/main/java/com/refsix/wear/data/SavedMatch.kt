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
    val events: List<MatchEvent>
) {
    fun toJson(): String = JSONObject().apply {
        put("id", id)
        put("dateMillis", dateMillis)
        put("homeTeam", homeTeam)
        put("awayTeam", awayTeam)
        put("homeScore", homeScore)
        put("awayScore", awayScore)
        put("halfLengthMinutes", halfLengthMinutes)
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
                    scorerName = e.optString("scorerName", "")
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
                events = events
            )
        }
    }
}
