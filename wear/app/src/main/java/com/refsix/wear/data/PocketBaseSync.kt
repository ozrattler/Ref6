package com.refsix.wear.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class PocketBaseSync(private val context: Context) {

    private val baseUrl = "http://192.168.1.106:8090/api/collections"
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS'Z'", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    fun isWifiConnected(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
    }

    // Returns the PocketBase record ID on success, null on failure.
    suspend fun syncMatch(match: SavedMatch): String? = withContext(Dispatchers.IO) {
        try {
            val matchBody = JSONObject().apply {
                put("date", dateFormat.format(Date(match.dateMillis)))
                put("competition", match.competition)
                put("home_team", match.homeTeam)
                put("away_team", match.awayTeam)
                put("final_score", "${match.homeScore}-${match.awayScore}")
                put("age_group", match.ageGroup)
                put("half_length", match.halfLengthMinutes)
                put("status", "completed")
            }
            val pbMatchId = postJson("$baseUrl/matches/records", matchBody)
                ?: return@withContext null

            match.events.forEach { event ->
                val incidentBody = JSONObject().apply {
                    put("match_id", pbMatchId)
                    put("half", event.half)
                    put("minute", event.matchMinute)
                    put("type", event.type.name)
                    put("team", event.team)
                    put("player_number", if (event.type == EventType.GOAL) event.scorerNumber else event.playerNumber)
                    put("player_name", event.scorerName)
                    put("offence_description", event.detail)
                }
                postJson("$baseUrl/incidents/records", incidentBody)
            }
            pbMatchId
        } catch (_: Exception) {
            null
        }
    }

    private fun postJson(url: String, body: JSONObject): String? {
        val conn = URL(url).openConnection() as HttpURLConnection
        return try {
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json")
            conn.doOutput = true
            conn.connectTimeout = 5_000
            conn.readTimeout = 5_000
            OutputStreamWriter(conn.outputStream).use { it.write(body.toString()) }
            if (conn.responseCode !in 200..299) return null
            JSONObject(conn.inputStream.bufferedReader().readText()).getString("id")
        } finally {
            conn.disconnect()
        }
    }
}
