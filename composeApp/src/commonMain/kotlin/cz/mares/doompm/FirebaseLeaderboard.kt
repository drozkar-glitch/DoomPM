package cz.mares.doompm

import kotlin.random.Random

data class LeaderboardEntry(
    val nick: String,
    val value: Int,
    val deviceId: String = ""
)

object FirebaseLeaderboard {
    private const val DB_URL = "https://mares-games-default-rtdb.europe-west1.firebasedatabase.app"
    private const val PREF_NICK = "doom_player_nick"
    private const val PREF_DEVICE_ID = "doom_device_id"

    fun getDeviceId(): String {
        var existing = PlatformServices.loadString(PREF_DEVICE_ID, "")
        if (existing.length < 10) {
            val chars = "abcdefghijklmnopqrstuvwxyz0123456789"
            val sb = StringBuilder("dev_")
            repeat(20) {
                sb.append(chars[Random.nextInt(chars.length)])
            }
            existing = sb.toString()
            PlatformServices.saveString(PREF_DEVICE_ID, existing)
        }
        return existing
    }

    fun getSavedNick(): String = PlatformServices.loadString(PREF_NICK, "").trim()

    fun saveNick(nick: String) {
        val clean = sanitizeKey(nick)
        if (clean.isNotEmpty()) {
            PlatformServices.saveString(PREF_NICK, clean)
        }
    }

    private fun sanitizeKey(raw: String): String {
        return raw.trim()
            .replace(".", "")
            .replace("#", "")
            .replace("$", "")
            .replace("[", "")
            .replace("]", "")
            .replace("/", "")
            .take(18)
    }

    fun submitScoreAndTime(
        difficultyId: String,
        nick: String,
        score: Int,
        timeSeconds: Int,
        onComplete: (Boolean, String) -> Unit
    ) {
        val cleanNick = sanitizeKey(nick)
        if (cleanNick.isEmpty()) {
            onComplete(false, "Zadej platnou přezdívku.")
            return
        }
        val devId = getDeviceId()
        val gameScoreKey = "doompm_$difficultyId"
        val scoreUrl = "$DB_URL/high_scores/$gameScoreKey/$cleanNick.json"

        PlatformServices.httpRequest(scoreUrl, "GET", null) { ok, body ->
            if (ok && body.isNotBlank() && body != "null") {
                val existingDev = extractJsonString(body, "deviceId")
                val existingScore = extractJsonInt(body, "score") ?: extractJsonInt(body, "value") ?: 0
                if (existingDev.isNotEmpty() && existingDev != devId) {
                    onComplete(false, "Přezdívka '$cleanNick' už patří jinému hráči. Zvol jinou.")
                    return@httpRequest
                }
                saveNick(cleanNick)
                if (score > existingScore) {
                    val payload = """{"score":$score,"value":$score,"deviceId":"$devId","timestamp":${PlatformServices.currentTimeMillis()}}"""
                    PlatformServices.httpRequest(scoreUrl, "PUT", payload) { _, _ -> }
                }
            } else {
                saveNick(cleanNick)
                if (score > 0) {
                    val payload = """{"score":$score,"value":$score,"deviceId":"$devId","timestamp":${PlatformServices.currentTimeMillis()}}"""
                    PlatformServices.httpRequest(scoreUrl, "PUT", payload) { _, _ -> }
                }
            }

            if (timeSeconds > 0) {
                val timeUrl = "$DB_URL/best_times/$gameScoreKey/$cleanNick.json"
                PlatformServices.httpRequest(timeUrl, "GET", null) { tOk, tBody ->
                    val prevTime = if (tOk && tBody.isNotBlank() && tBody != "null") {
                        extractJsonInt(tBody, "time") ?: extractJsonInt(tBody, "value") ?: Int.MAX_VALUE
                    } else Int.MAX_VALUE
                    if (timeSeconds < prevTime) {
                        val tPayload = """{"time":$timeSeconds,"value":$timeSeconds,"deviceId":"$devId","timestamp":${PlatformServices.currentTimeMillis()}}"""
                        PlatformServices.httpRequest(timeUrl, "PUT", tPayload) { _, _ ->
                            onComplete(true, "Rekord byl uložen!")
                        }
                    } else {
                        onComplete(true, "Skóre bylo aktualizováno!")
                    }
                }
            } else {
                onComplete(true, "Skóre bylo uloženo!")
            }
        }
    }

    fun fetchLeaderboard(
        difficultyId: String,
        isBestTime: Boolean,
        includeZeroScores: Boolean = false,
        onResult: (List<LeaderboardEntry>) -> Unit
    ) {
        val branch = if (isBestTime) "best_times" else "high_scores"
        val gameKey = "doompm_$difficultyId"
        val url = "$DB_URL/$branch/$gameKey.json"

        PlatformServices.httpRequest(url, "GET", null) { ok, body ->
            if (!ok || body.isBlank() || body == "null") {
                onResult(emptyList())
                return@httpRequest
            }
            val parsed = parseFirebaseMap(body, isBestTime, includeZeroScores)
            onResult(parsed)
        }
    }

    private fun parseFirebaseMap(
        json: String,
        ascending: Boolean,
        includeZero: Boolean
    ): List<LeaderboardEntry> {
        val result = mutableListOf<LeaderboardEntry>()
        val trimmed = json.trim()
        if (!trimmed.startsWith("{") || !trimmed.endsWith("}")) return emptyList()
        val inner = trimmed.substring(1, trimmed.length - 1)
        var i = 0
        while (i < inner.length) {
            val keyStart = inner.indexOf('"', i)
            if (keyStart < 0) break
            val keyEnd = inner.indexOf('"', keyStart + 1)
            if (keyEnd < 0) break
            val key = inner.substring(keyStart + 1, keyEnd)
            val colon = inner.indexOf(':', keyEnd + 1)
            if (colon < 0) break
            var valStart = colon + 1
            while (valStart < inner.length && inner[valStart].isWhitespace()) valStart++
            if (valStart >= inner.length) break

            if (inner[valStart] == '{') {
                var depth = 0
                var valEnd = valStart
                while (valEnd < inner.length) {
                    if (inner[valEnd] == '{') depth++
                    else if (inner[valEnd] == '}') {
                        depth--
                        if (depth == 0) break
                    }
                    valEnd++
                }
                val objStr = inner.substring(valStart, (valEnd + 1).coerceAtMost(inner.length))
                val v = extractJsonInt(objStr, "score")
                    ?: extractJsonInt(objStr, "time")
                    ?: extractJsonInt(objStr, "value")
                    ?: 0
                val dev = extractJsonString(objStr, "deviceId")
                if (v > 0 || (includeZero && v == 0)) {
                    result.add(LeaderboardEntry(key, v, dev))
                }
                i = valEnd + 1
            } else {
                var valEnd = valStart
                while (valEnd < inner.length && inner[valEnd] != ',' && inner[valEnd] != '}') valEnd++
                val numStr = inner.substring(valStart, valEnd).trim()
                val v = numStr.toIntOrNull() ?: 0
                if (v > 0 || (includeZero && v == 0)) {
                    result.add(LeaderboardEntry(key, v, ""))
                }
                i = valEnd + 1
            }
        }
        return if (ascending) {
            result.filter { it.value > 0 }.sortedBy { it.value }
        } else {
            result.sortedByDescending { it.value }
        }
    }

    private fun extractJsonInt(json: String, field: String): Int? {
        val pattern = "\"$field\""
        val idx = json.indexOf(pattern)
        if (idx < 0) return null
        val colon = json.indexOf(':', idx + pattern.length)
        if (colon < 0) return null
        var start = colon + 1
        while (start < json.length && (json[start].isWhitespace() || json[start] == '"')) start++
        var end = start
        while (end < json.length && (json[end].isDigit() || json[end] == '-')) end++
        return json.substring(start, end).toIntOrNull()
    }

    private fun extractJsonString(json: String, field: String): String {
        val pattern = "\"$field\""
        val idx = json.indexOf(pattern)
        if (idx < 0) return ""
        val colon = json.indexOf(':', idx + pattern.length)
        if (colon < 0) return ""
        val q1 = json.indexOf('"', colon + 1)
        if (q1 < 0) return ""
        val q2 = json.indexOf('"', q1 + 1)
        if (q2 < 0) return ""
        return json.substring(q1 + 1, q2)
    }

    fun formatTimeMmSs(totalSeconds: Int): String {
        val m = totalSeconds / 60
        val s = totalSeconds % 60
        val mm = if (m < 10) "0$m" else "$m"
        val ss = if (s < 10) "0$s" else "$s"
        return "$mm:$ss"
    }
}
