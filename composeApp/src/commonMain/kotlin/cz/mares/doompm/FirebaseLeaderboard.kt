package cz.mares.doompm

import kotlin.random.Random

data class LeaderboardEntry(
    val nick: String,
    val value: Int,
    val deviceId: String = ""
)

object FirebaseLeaderboard {
    private const val DB_URL = "https://mares-games-default-rtdb.europe-west1.firebasedatabase.app"
    private const val PREF_NICK = "doom_player_nick_v2"
    private const val PREF_DEVICE_ID = "doom_device_id"
    private const val CANONICAL_NICK_GAME_ID = "doompm_medium"

    fun getDeviceId(): String {
        var existing = PlatformServices.loadString(PREF_DEVICE_ID, "")
        if (!existing.startsWith("dev_") || existing.length < 24) {
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

    private fun saveNickLocally(nick: String) {
        val clean = sanitizeKey(nick)
        if (clean.isNotEmpty()) {
            PlatformServices.saveString(PREF_NICK, clean)
        }
    }

    fun getLocalBestScore(difficultyId: String): Int {
        return PlatformServices.loadString("doom_best_score_$difficultyId", "0").toIntOrNull() ?: 0
    }

    fun getLocalBestTime(difficultyId: String): Int {
        return PlatformServices.loadString("doom_best_time_$difficultyId", "0").toIntOrNull() ?: 0
    }

    fun updateLocalBest(difficultyId: String, score: Int, timeSeconds: Int) {
        if (score > getLocalBestScore(difficultyId)) {
            PlatformServices.saveString("doom_best_score_$difficultyId", score.toString())
        }
        if (timeSeconds > 0) {
            val prev = getLocalBestTime(difficultyId)
            if (prev <= 0 || timeSeconds < prev) {
                PlatformServices.saveString("doom_best_time_$difficultyId", timeSeconds.toString())
            }
        }
    }

    fun sanitizeKey(raw: String): String {
        return raw.trim()
            .replace(".", "")
            .replace("#", "")
            .replace("$", "")
            .replace("[", "")
            .replace("]", "")
            .replace("/", "")
            .replace("\"", "")
            .replace("\\", "")
            .take(18)
            .trim()
    }

    private fun encodeUriComponent(input: String): String {
        val hex = "0123456789ABCDEF"
        val bytes = input.encodeToByteArray()
        val sb = StringBuilder(bytes.size * 2)
        for (b in bytes) {
            val c = b.toInt() and 0xFF
            val isUnreserved = (c in 'a'.code..'z'.code) ||
                (c in 'A'.code..'Z'.code) ||
                (c in '0'.code..'9'.code) ||
                c == '-'.code || c == '_'.code || c == '~'.code
            if (isUnreserved) {
                sb.append(c.toChar())
            } else {
                sb.append('%')
                sb.append(hex[(c ushr 4) and 0x0F])
                sb.append(hex[c and 0x0F])
            }
        }
        return sb.toString()
    }

    /**
     * Claims and locks a unique nickname across devices via deviceId (`dev_` + 20 chars).
     * Even if initialScore == 0, writes a 0-score reservation into Firebase (filtered out by includeZeroScores = false).
     */
    fun registerNickname(
        rawNick: String,
        difficultyId: String,
        initialScore: Int = 0,
        initialTimeSec: Int = 0,
        onComplete: (Boolean, String) -> Unit
    ) {
        val cleanNick = sanitizeKey(rawNick)
        if (cleanNick.length < 2) {
            onComplete(false, "Zadej přezdívku alespoň o 2 znacích.")
            return
        }
        val devId = getDeviceId()
        val encodedNick = encodeUriComponent(cleanNick)
        val checkUrl = "$DB_URL/high_scores/$CANONICAL_NICK_GAME_ID/$encodedNick.json"

        PlatformServices.httpRequest(checkUrl, "GET", null) { ok, body ->
            if (!ok) {
                onComplete(false, "Nelze ověřit přezdívku online. Zkontroluj internet.")
                return@httpRequest
            }

            if (body.isNotBlank() && body.trim() != "null") {
                val ownerDev = extractJsonString(body, "deviceId")
                if (ownerDev.isNotEmpty() && ownerDev != devId) {
                    onComplete(false, "Přezdívka '$cleanNick' už patří jinému hráči! Zvol jinou.")
                    return@httpRequest
                }
            }

            val existingCanonicalScore = if (body.isNotBlank() && body.trim() != "null") {
                extractJsonInt(body, "score") ?: extractJsonInt(body, "value") ?: 0
            } else -1

            val reserveScore = if (difficultyId == "medium") {
                maxOf(initialScore, existingCanonicalScore.coerceAtLeast(0))
            } else {
                existingCanonicalScore.coerceAtLeast(0)
            }

            val reservePayload = """{"score":$reserveScore,"deviceId":"$devId"}"""
            PlatformServices.httpRequest(checkUrl, "PUT", reservePayload) { putOk, _ ->
                if (!putOk) {
                    onComplete(false, "Zápis přezdívky do databáze se nezdařil.")
                    return@httpRequest
                }
                saveNickLocally(cleanNick)
                if (initialScore > 0 || initialTimeSec > 0) {
                    submitScoreAndTime(difficultyId, cleanNick, initialScore, initialTimeSec, onComplete)
                } else {
                    onComplete(true, "Přezdívka '$cleanNick' byla trvale uložena!")
                }
            }
        }
    }

    fun submitScoreAndTime(
        difficultyId: String,
        nick: String,
        score: Int,
        timeSeconds: Int,
        onComplete: (Boolean, String) -> Unit
    ) {
        val cleanNick = sanitizeKey(nick)
        if (cleanNick.length < 2) {
            onComplete(false, "Zadej platnou přezdívku.")
            return
        }

        updateLocalBest(difficultyId, score, timeSeconds)

        val devId = getDeviceId()
        val encodedNick = encodeUriComponent(cleanNick)
        val gameKey = "doompm_$difficultyId"
        val scoreUrl = "$DB_URL/high_scores/$gameKey/$encodedNick.json"

        PlatformServices.httpRequest(scoreUrl, "GET", null) { ok, body ->
            if (!ok) {
                onComplete(false, "Offline: Rekord uložen v telefonu, odešle se po připojení.")
                return@httpRequest
            }

            var existingScore = -1
            if (body.isNotBlank() && body.trim() != "null") {
                val existingDev = extractJsonString(body, "deviceId")
                if (existingDev.isNotEmpty() && existingDev != devId) {
                    onComplete(false, "Přezdívka '$cleanNick' už patří jinému hráči!")
                    return@httpRequest
                }
                existingScore = extractJsonInt(body, "score") ?: extractJsonInt(body, "value") ?: 0
            }

            saveNickLocally(cleanNick)

            fun finishWithTimeCheck(scoreSavedOk: Boolean) {
                if (timeSeconds > 0) {
                    val timeUrl = "$DB_URL/best_times/$gameKey/$encodedNick.json"
                    PlatformServices.httpRequest(timeUrl, "GET", null) { tOk, tBody ->
                        if (!tOk) {
                            onComplete(scoreSavedOk, "Skóre uloženo!")
                            return@httpRequest
                        }
                        if (tBody.isNotBlank() && tBody.trim() != "null") {
                            val tOwner = extractJsonString(tBody, "deviceId")
                            if (tOwner.isNotEmpty() && tOwner != devId) {
                                onComplete(scoreSavedOk, "Skóre uloženo!")
                                return@httpRequest
                            }
                        }
                        val prevTime = if (tBody.isNotBlank() && tBody.trim() != "null") {
                            extractJsonInt(tBody, "time") ?: extractJsonInt(tBody, "value") ?: Int.MAX_VALUE
                        } else Int.MAX_VALUE

                        if (timeSeconds < prevTime) {
                            val tPayload = """{"time":$timeSeconds,"deviceId":"$devId"}"""
                            PlatformServices.httpRequest(timeUrl, "PUT", tPayload) { tPutOk, _ ->
                                onComplete(
                                    scoreSavedOk || tPutOk,
                                    "Online rekord uložen (${formatTimeMmSs(timeSeconds)})!"
                                )
                            }
                        } else {
                            onComplete(true, "Online výsledky aktualizovány!")
                        }
                    }
                } else {
                    onComplete(scoreSavedOk, if (scoreSavedOk) "Online skóre uloženo!" else "Zápis selhal.")
                }
            }

            if (score > existingScore && (score > 0 || existingScore < 0)) {
                val sPayload = """{"score":$score,"deviceId":"$devId"}"""
                PlatformServices.httpRequest(scoreUrl, "PUT", sPayload) { putOk, _ ->
                    finishWithTimeCheck(putOk)
                }
            } else {
                finishWithTimeCheck(true)
            }
        }
    }

    fun syncSavedPlayerRecordIfAny(difficultyId: String, score: Int, timeSeconds: Int) {
        if (score > 0 || timeSeconds > 0) {
            updateLocalBest(difficultyId, score, timeSeconds)
        }
        val saved = getSavedNick()
        if (saved.length >= 2 && (score > 0 || timeSeconds > 0)) {
            submitScoreAndTime(difficultyId, saved, score, timeSeconds) { _, _ -> }
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
            if (!ok || body.isBlank() || body.trim() == "null") {
                onResult(emptyList())
                return@httpRequest
            }
            val parsed = parseFirebaseMap(body, isBestTime, includeZeroScores)
            onResult(parsed)
        }
    }

    private fun parseFirebaseMap(
        json: String,
        isBestTime: Boolean,
        includeZeroScores: Boolean
    ): List<LeaderboardEntry> {
        val result = mutableListOf<LeaderboardEntry>()
        val trimmed = json.trim()
        if (!trimmed.startsWith("{") || !trimmed.endsWith("}")) return emptyList()
        val inner = trimmed.substring(1, trimmed.length - 1)
        var i = 0
        while (i < inner.length) {
            val keyStart = findNextUnescapedQuote(inner, i)
            if (keyStart < 0) break
            val keyEnd = findNextUnescapedQuote(inner, keyStart + 1)
            if (keyEnd < 0) break
            val key = unescapeJsonString(inner.substring(keyStart + 1, keyEnd))

            val colon = inner.indexOf(':', keyEnd + 1)
            if (colon < 0) break
            var valStart = colon + 1
            while (valStart < inner.length && inner[valStart].isWhitespace()) valStart++
            if (valStart >= inner.length) break

            if (inner[valStart] == '{') {
                var depth = 0
                var inStr = false
                var valEnd = valStart
                while (valEnd < inner.length) {
                    val ch = inner[valEnd]
                    if (ch == '"' && (valEnd == 0 || inner[valEnd - 1] != '\\')) {
                        inStr = !inStr
                    } else if (!inStr) {
                        if (ch == '{') depth++
                        else if (ch == '}') {
                            depth--
                            if (depth == 0) break
                        }
                    }
                    valEnd++
                }
                val objStr = inner.substring(valStart, (valEnd + 1).coerceAtMost(inner.length))
                val v = if (isBestTime) {
                    extractJsonInt(objStr, "time") ?: extractJsonInt(objStr, "value") ?: 0
                } else {
                    extractJsonInt(objStr, "score") ?: extractJsonInt(objStr, "value") ?: 0
                }
                val dev = extractJsonString(objStr, "deviceId")
                if (isBestTime) {
                    if (v > 0) result.add(LeaderboardEntry(key, v, dev))
                } else {
                    if (v > 0 || (includeZeroScores && v == 0)) {
                        result.add(LeaderboardEntry(key, v, dev))
                    }
                }
                i = valEnd + 1
            } else {
                var valEnd = valStart
                while (valEnd < inner.length && inner[valEnd] != ',' && inner[valEnd] != '}') valEnd++
                val numStr = inner.substring(valStart, valEnd).trim()
                val v = numStr.toIntOrNull() ?: 0
                if (isBestTime) {
                    if (v > 0) result.add(LeaderboardEntry(key, v, ""))
                } else {
                    if (v > 0 || (includeZeroScores && v == 0)) {
                        result.add(LeaderboardEntry(key, v, ""))
                    }
                }
                i = valEnd + 1
            }
        }
        return if (isBestTime) {
            result.filter { it.value > 0 }.sortedBy { it.value }
        } else {
            result.sortedByDescending { it.value }
        }
    }

    private fun findNextUnescapedQuote(s: String, fromIndex: Int): Int {
        var idx = fromIndex
        while (idx < s.length) {
            if (s[idx] == '"' && (idx == 0 || s[idx - 1] != '\\')) return idx
            idx++
        }
        return -1
    }

    private fun unescapeJsonString(raw: String): String {
        if (!raw.contains('\\')) return raw
        val sb = StringBuilder(raw.length)
        var i = 0
        while (i < raw.length) {
            val ch = raw[i]
            if (ch == '\\' && i + 1 < raw.length) {
                val next = raw[i + 1]
                if (next == 'u' && i + 5 < raw.length) {
                    val code = raw.substring(i + 2, i + 6).toIntOrNull(16)
                    if (code != null) {
                        sb.append(code.toChar())
                        i += 6
                        continue
                    }
                }
                sb.append(next)
                i += 2
            } else {
                sb.append(ch)
                i++
            }
        }
        return sb.toString()
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
        if (start >= end) return null
        return json.substring(start, end).toIntOrNull()
    }

    private fun extractJsonString(json: String, field: String): String {
        val pattern = "\"$field\""
        val idx = json.indexOf(pattern)
        if (idx < 0) return ""
        val colon = json.indexOf(':', idx + pattern.length)
        if (colon < 0) return ""
        val q1 = findNextUnescapedQuote(json, colon + 1)
        if (q1 < 0) return ""
        val q2 = findNextUnescapedQuote(json, q1 + 1)
        if (q2 < 0) return ""
        return unescapeJsonString(json.substring(q1 + 1, q2))
    }

    fun formatTimeMmSs(totalSeconds: Int): String {
        val safe = totalSeconds.coerceAtLeast(0)
        val m = safe / 60
        val s = safe % 60
        val mm = if (m < 10) "0$m" else "$m"
        val ss = if (s < 10) "0$s" else "$s"
        return "$mm:$ss"
    }
}
