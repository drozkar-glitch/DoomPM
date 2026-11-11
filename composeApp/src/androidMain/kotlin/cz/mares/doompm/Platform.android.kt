package cz.mares.doompm

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

object AndroidPlatformContext {
    var appContext: Context? = null
    fun init(context: Context) {
        appContext = context.applicationContext
    }
}

actual object PlatformServices {
    private val audioExecutor = Executors.newFixedThreadPool(4)
    private val netExecutor = Executors.newCachedThreadPool()
    private val mainHandler = Handler(Looper.getMainLooper())

    actual fun playWavBytes(wavData: ByteArray) {
        if (wavData.size <= 44) return
        audioExecutor.execute {
            try {
                val pcmSize = wavData.size - 44
                val sampleRate = 11025
                val minBuf = AudioTrack.getMinBufferSize(
                    sampleRate,
                    AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT
                ).coerceAtLeast(pcmSize)

                val track = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_GAME)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(sampleRate)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build()
                    )
                    .setBufferSizeInBytes(minBuf)
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .build()

                track.write(wavData, 44, pcmSize)
                track.play()
                val durationMs = (pcmSize.toLong() * 1000L) / (sampleRate * 2L) + 60L
                Thread.sleep(durationMs)
                track.stop()
                track.release()
            } catch (_: Throwable) {
            }
        }
    }

    actual fun triggerHaptic(heavy: Boolean) {
        val ctx = AndroidPlatformContext.appContext ?: return
        try {
            val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = ctx.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vm?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                ctx.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
            val duration = if (heavy) 45L else 18L
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val amp = if (heavy) 220 else 110
                vibrator?.vibrate(VibrationEffect.createOneShot(duration, amp))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(duration)
            }
        } catch (_: Throwable) {
        }
    }

    actual fun saveString(key: String, value: String) {
        val ctx = AndroidPlatformContext.appContext ?: return
        ctx.getSharedPreferences("doom_pm_prefs", Context.MODE_PRIVATE)
            .edit()
            .putString(key, value)
            .apply()
    }

    actual fun loadString(key: String, defaultValue: String): String {
        val ctx = AndroidPlatformContext.appContext ?: return defaultValue
        return ctx.getSharedPreferences("doom_pm_prefs", Context.MODE_PRIVATE)
            .getString(key, defaultValue) ?: defaultValue
    }

    actual fun httpRequest(
        url: String,
        method: String,
        body: String?,
        onResult: (Boolean, String) -> Unit
    ) {
        netExecutor.execute {
            var conn: HttpURLConnection? = null
            try {
                val u = URL(url)
                conn = (u.openConnection() as HttpURLConnection).apply {
                    requestMethod = method
                    connectTimeout = 10000
                    readTimeout = 10000
                    setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                    if (body != null) {
                        doOutput = true
                        OutputStreamWriter(outputStream, Charsets.UTF_8).use { it.write(body) }
                    }
                }
                val code = conn.responseCode
                val stream = if (code in 200..299) conn.inputStream else conn.errorStream
                val responseText = if (stream != null) {
                    BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).use { it.readText() }
                } else ""
                val ok = code in 200..299
                mainHandler.post { onResult(ok, responseText) }
            } catch (e: Throwable) {
                mainHandler.post { onResult(false, e.message ?: "Network error") }
            } finally {
                conn?.disconnect()
            }
        }
    }

    actual fun currentTimeMillis(): Long = System.currentTimeMillis()
}
