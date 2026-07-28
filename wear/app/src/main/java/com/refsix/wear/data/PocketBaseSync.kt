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
    val gradeCode: String = "",  // raw age_group string from PocketBase (e.g. "O45D", "PLM")
    val kickoffDate: String = "",
    val kickoffTime: String = "",
    val homeColour: String = "",  // hex e.g. "#FF0000"
    val awayColour: String = "",
    val field: String = "",       // specific pitch name, e.g. "Ridge 5"
    val referee: String = "",
    val ar1: String = "",
    val ar2: String = "",
    val fourthOfficial: String = ""
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

    // Returns the list of pending setups on success, or null if the request failed.
    // An empty list means PocketBase responded successfully with zero pending setups.
    suspend fun fetchPendingMatchSetups(): List<MatchSetupData>? = withContext(Dispatchers.IO) {
        try {
            val filter = URLEncoder.encode("(status='pending')", "UTF-8")
            val url = "$baseUrl/match_setups/records?filter=$filter&sort=-created&perPage=50"
            Log.i(TAG, "fetchPendingMatchSetups: GET $url")
            val conn = URL(url).openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.connectTimeout = 5_000
            conn.readTimeout = 5_000
            val code = conn.responseCode
            Log.i(TAG, "fetchPendingMatchSetups: HTTP $code")
            if (code !in 200..299) {
                val errBody = conn.errorStream?.bufferedReader()?.readText()
                Log.e(TAG, "fetchPendingMatchSetups: error HTTP $code body=$errBody")
                return@withContext null
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
                    gradeCode = ageGroupStr,
                    kickoffDate = item.optString("kickoff_date", ""),
                    kickoffTime = item.optString("kickoff_time", ""),
                    homeColour = item.optString("home_colour", ""),
                    awayColour = item.optString("away_colour", ""),
                    field = item.optString("field", ""),
                    referee = item.optString("referee", ""),
                    ar1 = item.optString("ar1", ""),
                    ar2 = item.optString("ar2", ""),
                    fourthOfficial = item.optString("fourth_official", "")
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "fetchPendingMatchSetups: exception", e)
            null
        }
    }

    // Returns the PocketBase record ID on success, null on any failure.
    // A non-null return guarantees HTTP 2xx was received for the match record.
    suspend fun syncMatch(match: SavedMatch): String? = withContext(Dispatchers.IO) {
        if (match.pocketBaseId != null) {
            Log.i(TAG, "syncMatch: already synced id=${match.pocketBaseId} — skipping duplicate POST")
            return@withContext match.pocketBaseId
        }
        Log.i(TAG, "syncMatch: START ${match.homeTeam} vs ${match.awayTeam} status=${match.status} setupId=${match.matchSetupId}")
        Log.i(TAG, "syncMatch: baseUrl=$baseUrl networkAvailable=${isNetworkAvailable()}")
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
                if (match.referee.isNotEmpty())       put("referee",         match.referee)
                if (match.ar1.isNotEmpty())           put("ar1",             match.ar1)
                if (match.ar2.isNotEmpty())           put("ar2",             match.ar2)
                if (match.fourthOfficial.isNotEmpty()) put("fourth_official", match.fourthOfficial)
            }
            Log.i(TAG, "syncMatch: POST $baseUrl/matches/records")
            val (matchHttpCode, pbMatchId) = postJson("$baseUrl/matches/records", matchBody)
            if (matchHttpCode !in 200..299 || pbMatchId == null) {
                Log.e(TAG, "syncMatch: match POST failed HTTP $matchHttpCode id=$pbMatchId — local data preserved")
                return@withContext null
            }
            Log.i(TAG, "syncMatch: match created HTTP $matchHttpCode id=$pbMatchId, posting ${match.events.size} events")

            match.events.forEach { event ->
                Log.d(TAG, "syncMatch: incident type=${event.type} half=${event.half} minute=${event.matchMinute}")
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
                val (incCode, _) = postJson("$baseUrl/incidents/records", incidentBody)
                if (incCode !in 200..299) {
                    Log.w(TAG, "syncMatch: incident POST failed HTTP $incCode (match $pbMatchId already saved)")
                }
            }

            // Only now that sync succeeded, mark the originating setup as done.
            match.matchSetupId?.let { setupId ->
                patchSetupStatus(setupId, match.status)
            }

            Log.i(TAG, "syncMatch: DONE id=$pbMatchId")
            pbMatchId
        } catch (e: Exception) {
            Log.e(TAG, "syncMatch: EXCEPTION ${e.javaClass.simpleName}: ${e.message}", e)
            null
        }
    }

    private fun patchSetupStatus(id: String, status: String) {
        val url = "$baseUrl/match_setups/records/$id"
        Log.i(TAG, "patchSetupStatus: PATCH $url → status=$status")
        try {
            val conn = URL(url).openConnection() as HttpURLConnection
            conn.requestMethod = "PATCH"
            conn.setRequestProperty("Content-Type", "application/json")
            conn.doOutput = true
            conn.connectTimeout = 5_000
            conn.readTimeout = 5_000
            OutputStreamWriter(conn.outputStream).use {
                it.write(JSONObject().apply { put("status", status) }.toString())
            }
            val code = conn.responseCode
            if (code !in 200..299) {
                val errBody = conn.errorStream?.bufferedReader()?.readText()
                Log.e(TAG, "patchSetupStatus: HTTP $code body=$errBody")
            } else {
                Log.i(TAG, "patchSetupStatus: HTTP $code OK")
            }
            conn.disconnect()
        } catch (e: Exception) {
            Log.e(TAG, "patchSetupStatus: EXCEPTION ${e.javaClass.simpleName}: ${e.message}", e)
        }
    }

    private fun parseAgeGroup(str: String): AgeGroup = when {
        str.contains("U16", ignoreCase = true) -> AgeGroup.U16
        str.contains("U15", ignoreCase = true) -> AgeGroup.U15
        str.contains("U14", ignoreCase = true) -> AgeGroup.U14
        str.contains("U12", ignoreCase = true) -> AgeGroup.U12
        else -> AgeGroup.OPEN_SENIOR
    }

    // Returns (httpCode, recordId). httpCode=0 means a network/IO exception occurred.
    // recordId is non-null only when httpCode is 2xx and the response body contained "id".
    private fun postJson(url: String, body: JSONObject): Pair<Int, String?> {
        Log.i(TAG, "postJson: POST $url")
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json")
            conn.doOutput = true
            conn.connectTimeout = 5_000
            conn.readTimeout = 5_000
            OutputStreamWriter(conn.outputStream).use { it.write(body.toString()) }
            val code = conn.responseCode
            if (code !in 200..299) {
                val errBody = runCatching { conn.errorStream?.bufferedReader()?.readText() }.getOrNull()
                Log.e(TAG, "postJson: HTTP $code url=$url errBody=$errBody")
                return Pair(code, null)
            }
            val responseText = conn.inputStream.bufferedReader().readText()
            val id = runCatching { JSONObject(responseText).getString("id") }.getOrElse {
                Log.e(TAG, "postJson: HTTP $code but failed to parse id from response: $responseText", it)
                return Pair(code, null)
            }
            Log.i(TAG, "postJson: HTTP $code id=$id")
            return Pair(code, id)
        } catch (e: Exception) {
            Log.e(TAG, "postJson: EXCEPTION ${e.javaClass.simpleName}: ${e.message}", e)
            return Pair(0, null)
        } finally {
            runCatching { conn.disconnect() }
        }
    }
}
