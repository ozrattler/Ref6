package com.refsix.wear.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

private const val TAG = "PocketBaseSync"

data class MatchSetupData(
    val id: String,
    val homeTeam: String,
    val awayTeam: String,
    val halfLengthMinutes: Int,
    val ageGroup: AgeGroup,
    val competitionType: CompetitionType,
    val sinBinMinutes: Int,
    val competition: String,
    val kickoffDate: String = "",
    val kickoffTime: String = ""
)

class PocketBaseSync(private val context: Context) {

    private val baseUrl = "https://refappb.duckdns.org/api/collections"
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS'Z'", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    // True if any usable network is available (WiFi or Bluetooth bridge via phone).
    // Wear OS often routes traffic through the paired phone over Bluetooth, so checking
    // for TRANSPORT_WIFI alone would always fail when tethered.
    fun isNetworkAvailable(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork ?: run {
            Log.d(TAG, "isNetworkAvailable: no active network")
            return false
        }
        val caps = cm.getNetworkCapabilities(network) ?: run {
            Log.d(TAG, "isNetworkAvailable: no capabilities")
            return false
        }
        val wifi = caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
        val bt   = caps.hasTransport(NetworkCapabilities.TRANSPORT_BLUETOOTH)
        val cell = caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
        Log.d(TAG, "isNetworkAvailable: wifi=$wifi bt=$bt cell=$cell")
        return wifi || bt || cell
    }

    // Kept for sync gating (matches sync best over direct WiFi).
    fun isWifiConnected(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val caps = cm.getNetworkCapabilities(cm.activeNetwork ?: return false) ?: return false
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
    }

    suspend fun fetchPendingMatchSetups(): List<MatchSetupData> = withContext(Dispatchers.IO) {
        try {
            val filter = URLEncoder.encode("(status='pending')", "UTF-8")
            val url = "$baseUrl/match_setups/records?filter=$filter&sort=-created&perPage=50"
            Log.d(TAG, "fetchPendingMatchSetups: GET $url")
            val conn = URL(url).openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.connectTimeout = 5_000
            conn.readTimeout = 5_000
            val code = conn.responseCode
            Log.d(TAG, "fetchPendingMatchSetups: HTTP $code")
            if (code !in 200..299) {
                Log.w(TAG, "fetchPendingMatchSetups: error body=${conn.errorStream?.bufferedReader()?.readText()}")
                return@withContext emptyList()
            }
            val body = conn.inputStream.bufferedReader().readText()
            conn.disconnect()
            val json = JSONObject(body)
            val items = json.getJSONArray("items")
            Log.d(TAG, "fetchPendingMatchSetups: totalItems=${json.optInt("totalItems")} returned=${items.length()}")
            (0 until items.length()).map { i ->
                val item = items.getJSONObject(i)
                val ageGroupStr = item.optString("age_group", "")
                val ageGroup = parseAgeGroup(ageGroupStr)
                val competition = item.optString("competition", "")
                // age_group is the authoritative source for PLM/PLR; legacy SPL/SPLR also accepted
                val compType = when {
                    ageGroupStr.equals("PLR",  ignoreCase = true) -> CompetitionType.PLR
                    ageGroupStr.equals("PLM",  ignoreCase = true) -> CompetitionType.PLM
                    ageGroupStr.equals("SPLR", ignoreCase = true) -> CompetitionType.PLR
                    ageGroupStr.equals("SPL",  ignoreCase = true) -> CompetitionType.PLM
                    competition.contains("PLR",  ignoreCase = true) -> CompetitionType.PLR
                    competition.contains("PLM",  ignoreCase = true) -> CompetitionType.PLM
                    competition.contains("SPLR", ignoreCase = true) -> CompetitionType.PLR
                    competition.contains("SPL",  ignoreCase = true) -> CompetitionType.PLM
                    else -> CompetitionType.STANDARD
                }
                MatchSetupData(
                    id = item.getString("id"),
                    homeTeam = item.optString("home_team", ""),
                    awayTeam = item.optString("away_team", ""),
                    halfLengthMinutes = item.optInt("half_length", ageGroup.defaultHalfMinutes),
                    ageGroup = ageGroup,
                    competitionType = compType,
                    sinBinMinutes = ageGroup.sinBinMinutes,
                    competition = competition,
                    kickoffDate = item.optString("kickoff_date", ""),
                    kickoffTime = item.optString("kickoff_time", "")
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "fetchPendingMatchSetups: exception", e)
            emptyList()
        }
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
                put("status", match.status)
                match.matchSetupId?.let { put("match_setup_id", it) }
                if (match.gpsTrack.isNotEmpty()) {
                    put("gps_track",        match.gpsTrack)
                    put("total_distance_km",  match.totalDistanceKm.toDouble())
                    put("average_speed_kmh",  match.avgSpeedKmh.toDouble())
                    put("max_speed_kmh",      match.maxSpeedKmh.toDouble())
                }
                if (match.avgHeartRate > 0) {
                    put("avg_heart_rate", match.avgHeartRate)
                    put("max_heart_rate", match.maxHeartRate)
                }
            }
            Log.d(TAG, "syncMatch: posting match ${match.homeTeam} vs ${match.awayTeam}")
            val pbMatchId = postJson("$baseUrl/matches/records", matchBody)
                ?: return@withContext null
            Log.d(TAG, "syncMatch: match created id=$pbMatchId, posting ${match.events.size} events")

            match.events.forEach { event ->
                val incidentBody = JSONObject().apply {
                    put("match_id", pbMatchId)
                    put("half", event.half)
                    put("minute", event.matchMinute)
                    put("type", event.type.name)
                    put("team", event.team)
                    put("player_number", if (event.type == EventType.GOAL) event.scorerNumber else event.playerNumber)
                    put("player_name", event.scorerName)
                    put("offence_description", if (event.type == EventType.GOAL) "" else event.detail)
                    if (event.type == EventType.GOAL && event.detail.isNotEmpty()) {
                        put("goal_type", event.detail)
                    }
                    event.lat?.let { put("latitude", it) }
                    event.lng?.let { put("longitude", it) }
                }
                postJson("$baseUrl/incidents/records", incidentBody)
            }

            // Only now that sync succeeded, mark the originating setup as done.
            match.matchSetupId?.let { setupId ->
                patchSetupStatus(setupId, match.status)
            }

            Log.d(TAG, "syncMatch: done id=$pbMatchId")
            pbMatchId
        } catch (e: Exception) {
            Log.e(TAG, "syncMatch: exception", e)
            null
        }
    }

    private fun patchSetupStatus(id: String, status: String) {
        try {
            val conn = URL("$baseUrl/match_setups/records/$id").openConnection() as HttpURLConnection
            conn.requestMethod = "PATCH"
            conn.setRequestProperty("Content-Type", "application/json")
            conn.doOutput = true
            conn.connectTimeout = 5_000
            conn.readTimeout = 5_000
            OutputStreamWriter(conn.outputStream).use {
                it.write(JSONObject().apply { put("status", status) }.toString())
            }
            val code = conn.responseCode
            Log.d(TAG, "patchSetupStatus($id → $status): HTTP $code")
            conn.disconnect()
        } catch (e: Exception) {
            Log.e(TAG, "patchSetupStatus: exception", e)
        }
    }

    private fun parseAgeGroup(str: String): AgeGroup = when {
        str.contains("U16", ignoreCase = true) -> AgeGroup.U16
        str.contains("U15", ignoreCase = true) -> AgeGroup.U15
        str.contains("U14", ignoreCase = true) -> AgeGroup.U14
        str.contains("U12", ignoreCase = true) -> AgeGroup.U12
        else -> AgeGroup.OPEN_SENIOR
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
