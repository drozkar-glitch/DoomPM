@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package cz.mares.doompm

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asComposeImageBitmap
import kotlinx.cinterop.allocArrayOf
import kotlinx.cinterop.memScoped
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.ImageInfo
import platform.AVFAudio.AVAudioPlayer
import platform.Foundation.NSData
import platform.Foundation.NSDate
import platform.Foundation.NSMutableURLRequest
import platform.Foundation.NSString
import platform.Foundation.NSURL
import platform.Foundation.NSURLSession
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.NSUserDefaults
import platform.Foundation.create
import platform.Foundation.dataUsingEncoding
import platform.Foundation.setHTTPBody
import platform.Foundation.setHTTPMethod
import platform.Foundation.setValue
import platform.Foundation.timeIntervalSince1970
import platform.UIKit.UIImpactFeedbackGenerator
import platform.UIKit.UIImpactFeedbackStyle
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue

actual object PlatformServices {
    private val activePlayers = ArrayList<AVAudioPlayer>(8)
    private var playerWriteIdx = 0
    private val byteBufferCache = HashMap<Int, ByteArray>()

    actual fun pixelsToImageBitmap(pixels: IntArray, width: Int, height: Int): ImageBitmap {
        val count = width * height
        val byteLen = count * 4
        var bytes = byteBufferCache[byteLen]
        if (bytes == null || bytes.size != byteLen) {
            bytes = ByteArray(byteLen)
            byteBufferCache[byteLen] = bytes
        }

        var bi = 0
        for (i in 0 until count) {
            val c = pixels[i]
            bytes[bi] = (c and 0xFF).toByte()
            bytes[bi + 1] = ((c ushr 8) and 0xFF).toByte()
            bytes[bi + 2] = ((c ushr 16) and 0xFF).toByte()
            bytes[bi + 3] = ((c ushr 24) and 0xFF).toByte()
            bi += 4
        }

        val info = ImageInfo(width, height, ColorType.BGRA_8888, ColorAlphaType.PREMUL)
        val bmp = Bitmap()
        bmp.installPixels(info, bytes, width * 4)
        return bmp.asComposeImageBitmap()
    }

    actual fun playWavBytes(wavData: ByteArray) {
        if (wavData.isEmpty()) return
        try {
            val nsData = memScoped {
                NSData.create(bytes = allocArrayOf(wavData), length = wavData.size.toULong())
            }
            val player = AVAudioPlayer(data = nsData, error = null)
            player.prepareToPlay()
            player.play()
            if (activePlayers.size < 8) {
                activePlayers.add(player)
            } else {
                activePlayers[playerWriteIdx % 8] = player
                playerWriteIdx++
            }
        } catch (_: Throwable) {
        }
    }

    actual fun triggerHaptic(heavy: Boolean) {
        try {
            val style = if (heavy) {
                UIImpactFeedbackStyle.UIImpactFeedbackStyleHeavy
            } else {
                UIImpactFeedbackStyle.UIImpactFeedbackStyleLight
            }
            val gen = UIImpactFeedbackGenerator(style = style)
            gen.prepare()
            gen.impactOccurred()
        } catch (_: Throwable) {
        }
    }

    actual fun saveString(key: String, value: String) {
        NSUserDefaults.standardUserDefaults.setObject(value, forKey = key)
    }

    actual fun loadString(key: String, defaultValue: String): String {
        return NSUserDefaults.standardUserDefaults.stringForKey(key) ?: defaultValue
    }

    actual fun httpRequest(
        url: String,
        method: String,
        body: String?,
        onResult: (Boolean, String) -> Unit
    ) {
        val nsUrl = NSURL.URLWithString(url)
        if (nsUrl == null) {
            onResult(false, "Invalid URL")
            return
        }
        val request = NSMutableURLRequest.requestWithURL(nsUrl)
        request.setHTTPMethod(method)
        request.setValue("application/json; charset=UTF-8", forHTTPHeaderField = "Content-Type")
        if (body != null) {
            @Suppress("CAST_NEVER_SUCCEEDS")
            val nsBody = (body as NSString).dataUsingEncoding(NSUTF8StringEncoding)
            request.setHTTPBody(nsBody)
        }
        val task = NSURLSession.sharedSession.dataTaskWithRequest(request) { data, _, error ->
            val ok = error == null && data != null
            val text = if (data != null) {
                NSString.create(data = data, encoding = NSUTF8StringEncoding)?.toString() ?: ""
            } else {
                error?.localizedDescription ?: ""
            }
            dispatch_async(dispatch_get_main_queue()) {
                onResult(ok, text)
            }
        }
        task.resume()
    }

    actual fun currentTimeMillis(): Long {
        return (NSDate().timeIntervalSince1970 * 1000.0).toLong()
    }
}
