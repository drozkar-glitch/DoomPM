package cz.mares.doompm

import androidx.compose.ui.graphics.ImageBitmap
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin
import kotlin.random.Random

enum class DoomSoundEffect {
    PISTOL,
    SHOTGUN,
    CHAINGUN,
    ROCKET_LAUNCH,
    EXPLOSION,
    PLASMA,
    BFG_BLAST,
    CHAINSAW,
    PUNCH,
    DOOR_OPEN,
    SWITCH_CLICK,
    SECRET_FOUND,
    ITEM_PICKUP,
    WEAPON_PICKUP,
    MONSTER_SIGHT,
    MONSTER_PAIN,
    MONSTER_DEATH,
    PLAYER_PAIN,
    PLAYER_DEATH,
    TELEPORT
}

expect object PlatformServices {
    fun pixelsToImageBitmap(pixels: IntArray, width: Int, height: Int): ImageBitmap
    fun playWavBytes(wavData: ByteArray)
    fun triggerHaptic(heavy: Boolean = false)
    fun saveString(key: String, value: String)
    fun loadString(key: String, defaultValue: String = ""): String
    fun httpRequest(url: String, method: String = "GET", body: String? = null, onResult: (Boolean, String) -> Unit)
    fun currentTimeMillis(): Long
}

object DoomAudioManager {
    var soundEnabled: Boolean = true
    var musicEnabled: Boolean = true
    private var musicTick: Int = 0
    private var lastMusicStepMs: Long = 0L

    private val cachedWavs = mutableMapOf<DoomSoundEffect, ByteArray>()
    private val cachedMusicNotes = mutableMapOf<Int, ByteArray>()

    // Classic E1M1 (At Doom's Gate) inspired fast thrash bass/guitar riff frequencies (Hz)
    private val e1m1Riff = intArrayOf(
        82, 82, 165, 82, 82, 147, 82, 82, 131, 82, 82, 117, 82, 82, 123, 131,
        82, 82, 165, 82, 82, 147, 82, 82, 131, 82, 82, 117, 117, 117, 0, 0,
        110, 110, 220, 110, 110, 196, 110, 110, 175, 110, 110, 156, 110, 110, 165, 175,
        82, 82, 165, 82, 82, 147, 82, 82, 131, 123, 117, 110, 98, 92, 87, 82
    )

    fun playSound(effect: DoomSoundEffect) {
        if (!soundEnabled) return
        val wav = cachedWavs.getOrPut(effect) { synthesizeEffectWav(effect) }
        PlatformServices.playWavBytes(wav)
    }

    fun updateMusic(nowMs: Long, isPlayingGame: Boolean) {
        if (!musicEnabled || !isPlayingGame) return
        if (nowMs - lastMusicStepMs >= 145L) {
            lastMusicStepMs = nowMs
            val freq = e1m1Riff[musicTick % e1m1Riff.size]
            musicTick++
            if (freq > 0) {
                val wav = cachedMusicNotes.getOrPut(freq) { synthesizeGuitarNoteWav(freq) }
                PlatformServices.playWavBytes(wav)
            }
        }
    }

    fun stopMusic() {
        lastMusicStepMs = 0L
    }

    private fun synthesizeGuitarNoteWav(freqHz: Int): ByteArray {
        val sampleRate = 11025
        val durationSec = 0.13
        val numSamples = (sampleRate * durationSec).toInt()
        val samples = ShortArray(numSamples)
        for (i in 0 until numSamples) {
            val t = i.toDouble() / sampleRate
            val env = exp(-t * 14.0)
            val s1 = if (sin(2.0 * PI * freqHz * t) >= 0.0) 0.6 else -0.6
            val s2 = sin(2.0 * PI * (freqHz * 1.5) * t) * 0.4
            val raw = (s1 + s2) * env * 0.24
            samples[i] = (raw.coerceIn(-1.0, 1.0) * 32767.0).toInt().toShort()
        }
        return encodePcmToWav(samples, sampleRate)
    }

    private fun synthesizeEffectWav(effect: DoomSoundEffect): ByteArray {
        val sampleRate = 11025
        val rng = Random(effect.ordinal * 997 + 42)
        val durationSec = when (effect) {
            DoomSoundEffect.PISTOL -> 0.18
            DoomSoundEffect.SHOTGUN -> 0.42
            DoomSoundEffect.CHAINGUN -> 0.14
            DoomSoundEffect.ROCKET_LAUNCH -> 0.35
            DoomSoundEffect.EXPLOSION -> 0.55
            DoomSoundEffect.PLASMA -> 0.16
            DoomSoundEffect.BFG_BLAST -> 0.70
            DoomSoundEffect.CHAINSAW -> 0.16
            DoomSoundEffect.PUNCH -> 0.12
            DoomSoundEffect.DOOR_OPEN -> 0.45
            DoomSoundEffect.SWITCH_CLICK -> 0.12
            DoomSoundEffect.SECRET_FOUND -> 0.50
            DoomSoundEffect.ITEM_PICKUP -> 0.15
            DoomSoundEffect.WEAPON_PICKUP -> 0.32
            DoomSoundEffect.MONSTER_SIGHT -> 0.34
            DoomSoundEffect.MONSTER_PAIN -> 0.22
            DoomSoundEffect.MONSTER_DEATH -> 0.44
            DoomSoundEffect.PLAYER_PAIN -> 0.25
            DoomSoundEffect.PLAYER_DEATH -> 0.55
            DoomSoundEffect.TELEPORT -> 0.40
        }

        val numSamples = (sampleRate * durationSec).toInt()
        val samples = ShortArray(numSamples)

        for (i in 0 until numSamples) {
            val t = i.toDouble() / sampleRate
            val progress = i.toDouble() / numSamples
            val noise = rng.nextDouble(-1.0, 1.0)

            val value: Double = when (effect) {
                DoomSoundEffect.PISTOL -> {
                    val env = exp(-t * 22.0)
                    val bang = sin(2.0 * PI * (180.0 - 110.0 * progress) * t) * 0.6 + noise * 0.7
                    bang * env * 0.65
                }
                DoomSoundEffect.SHOTGUN -> {
                    if (t < 0.24) {
                        val env = exp(-t * 14.0)
                        val boom = sin(2.0 * PI * (115.0 - 65.0 * progress) * t) * 0.75 + noise * 0.85
                        boom * env * 0.75
                    } else if (t > 0.28 && t < 0.35) {
                        noise * 0.45 * exp(-(t - 0.28) * 35.0)
                    } else if (t >= 0.35) {
                        noise * 0.50 * exp(-(t - 0.35) * 35.0)
                    } else 0.0
                }
                DoomSoundEffect.CHAINGUN -> {
                    val env = exp(-t * 26.0)
                    (sin(2.0 * PI * 150.0 * t) * 0.6 + noise * 0.75) * env * 0.65
                }
                DoomSoundEffect.ROCKET_LAUNCH -> {
                    val env = (1.0 - progress)
                    val whoosh = sin(2.0 * PI * (95.0 + 40.0 * progress) * t) * 0.4 + noise * 0.7
                    whoosh * env * 0.65
                }
                DoomSoundEffect.EXPLOSION -> {
                    val env = exp(-t * 6.5)
                    val rumble = sin(2.0 * PI * (68.0 - 38.0 * progress) * t) * 0.7 + noise * 0.8
                    rumble * env * 0.8
                }
                DoomSoundEffect.PLASMA -> {
                    val env = exp(-t * 18.0)
                    val freq = 920.0 - 680.0 * progress
                    (sin(2.0 * PI * freq * t) * 0.8 + noise * 0.2) * env * 0.55
                }
                DoomSoundEffect.BFG_BLAST -> {
                    if (t < 0.22) {
                        val chargeFreq = 220.0 + (t / 0.22) * 880.0
                        sin(2.0 * PI * chargeFreq * t) * 0.55
                    } else {
                        val bt = t - 0.22
                        val env = exp(-bt * 4.8)
                        (sin(2.0 * PI * 75.0 * bt) * 0.7 + sin(2.0 * PI * 420.0 * bt) * 0.4 + noise * 0.75) * env * 0.85
                    }
                }
                DoomSoundEffect.CHAINSAW -> {
                    val saw = ((t * 135.0) - (t * 135.0).toInt()) * 2.0 - 1.0
                    (saw * 0.7 + noise * 0.3) * 0.48
                }
                DoomSoundEffect.PUNCH -> {
                    val env = exp(-t * 28.0)
                    (sin(2.0 * PI * (120.0 - 70.0 * progress) * t) * 0.8 + noise * 0.4) * env * 0.65
                }
                DoomSoundEffect.DOOR_OPEN -> {
                    val freq = 75.0 + 35.0 * sin(progress * PI)
                    (sin(2.0 * PI * freq * t) * 0.65 + noise * 0.25) * (1.0 - progress * 0.4) * 0.5
                }
                DoomSoundEffect.SWITCH_CLICK -> {
                    val freq = if (t < 0.05) 520.0 else 340.0
                    (sin(2.0 * PI * freq * t) * 0.75 + noise * 0.25) * exp(-t * 22.0) * 0.6
                }
                DoomSoundEffect.SECRET_FOUND -> {
                    val freq = when {
                        t < 0.15 -> 440.0
                        t < 0.30 -> 554.37
                        else -> 659.25
                    }
                    sin(2.0 * PI * freq * t) * exp(-(t % 0.15) * 8.0) * 0.55
                }
                DoomSoundEffect.ITEM_PICKUP -> {
                    val freq = if (t < 0.07) 587.33 else 880.0
                    sin(2.0 * PI * freq * t) * (1.0 - progress) * 0.45
                }
                DoomSoundEffect.WEAPON_PICKUP -> {
                    val sub = if (t < 0.14) t else (t - 0.16).coerceAtLeast(0.0)
                    noise * exp(-sub * 26.0) * 0.6
                }
                DoomSoundEffect.MONSTER_SIGHT -> {
                    val freq = 140.0 + 55.0 * sin(t * 28.0)
                    (sin(2.0 * PI * freq * t) * 0.65 + noise * 0.35) * (1.0 - progress * 0.5) * 0.55
                }
                DoomSoundEffect.MONSTER_PAIN -> {
                    val freq = 230.0 - 90.0 * progress
                    (sin(2.0 * PI * freq * t) * 0.7 + noise * 0.3) * (1.0 - progress) * 0.55
                }
                DoomSoundEffect.MONSTER_DEATH -> {
                    val freq = 185.0 - 135.0 * progress
                    (sin(2.0 * PI * freq * t) * 0.65 + noise * 0.45) * (1.0 - progress) * 0.65
                }
                DoomSoundEffect.PLAYER_PAIN -> {
                    val freq = 165.0 - 45.0 * progress + 12.0 * sin(t * 45.0)
                    (sin(2.0 * PI * freq * t) * 0.75 + noise * 0.25) * (1.0 - progress) * 0.65
                }
                DoomSoundEffect.PLAYER_DEATH -> {
                    val freq = 210.0 - 155.0 * progress
                    (sin(2.0 * PI * freq * t) * 0.7 + noise * 0.35) * (1.0 - progress) * 0.75
                }
                DoomSoundEffect.TELEPORT -> {
                    val freq = 300.0 + 600.0 * sin(t * 35.0)
                    (sin(2.0 * PI * freq * t) * 0.6 + noise * 0.4) * (1.0 - progress) * 0.6
                }
            }

            samples[i] = (value.coerceIn(-1.0, 1.0) * 32767.0).toInt().toShort()
        }

        return encodePcmToWav(samples, sampleRate)
    }

    private fun encodePcmToWav(samples: ShortArray, sampleRate: Int): ByteArray {
        val dataSize = samples.size * 2
        val totalSize = 44 + dataSize
        val out = ByteArray(totalSize)

        fun writeAscii(offset: Int, text: String) {
            for (i in text.indices) {
                out[offset + i] = text[i].code.toByte()
            }
        }

        fun writeIntLE(offset: Int, value: Int) {
            out[offset] = (value and 0xFF).toByte()
            out[offset + 1] = ((value ushr 8) and 0xFF).toByte()
            out[offset + 2] = ((value ushr 16) and 0xFF).toByte()
            out[offset + 3] = ((value ushr 24) and 0xFF).toByte()
        }

        fun writeShortLE(offset: Int, value: Int) {
            out[offset] = (value and 0xFF).toByte()
            out[offset + 1] = ((value ushr 8) and 0xFF).toByte()
        }

        writeAscii(0, "RIFF")
        writeIntLE(4, totalSize - 8)
        writeAscii(8, "WAVE")
        writeAscii(12, "fmt ")
        writeIntLE(16, 16)
        writeShortLE(20, 1)
        writeShortLE(22, 1)
        writeIntLE(24, sampleRate)
        writeIntLE(28, sampleRate * 2)
        writeShortLE(32, 2)
        writeShortLE(34, 16)
        writeAscii(36, "data")
        writeIntLE(40, dataSize)

        var idx = 44
        for (s in samples) {
            val v = s.toInt()
            out[idx++] = (v and 0xFF).toByte()
            out[idx++] = ((v ushr 8) and 0xFF).toByte()
        }
        return out
    }
}
