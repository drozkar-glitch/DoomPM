package cz.mares.doompm

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AndroidPlatformContext.init(applicationContext)
        enableEdgeToEdge()
        setContent {
            App()
        }
    }

    override fun onPause() {
        super.onPause()
        DoomAudioManager.stopMusic()
    }
}
