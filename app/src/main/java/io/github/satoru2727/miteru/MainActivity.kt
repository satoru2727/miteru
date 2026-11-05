package io.github.satoru2727.miteru

import android.app.PictureInPictureParams
import android.os.Bundle
import android.util.Rational
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import io.github.satoru2727.miteru.ui.AppRoot

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AppRoot((application as MiteruApplication).container)
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        val container = (application as MiteruApplication).container
        if (container.playback.playerVisible && container.playback.nowPlaying.value != null && !isInPictureInPictureMode) {
            runCatching {
                enterPictureInPictureMode(
                    PictureInPictureParams.Builder()
                        .setAspectRatio(Rational(16, 9))
                        .build(),
                )
            }
        }
    }
}
