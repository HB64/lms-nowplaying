package com.example.lmsnowplaying.helpers

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import com.example.lmsnowplaying.network.logitechmediaserver.LMS
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

// True once a held Play/Pause key has stopped playback: its release must not toggle as well.
private var mediaPlayPauseLongDone = false

// Called for every key event, on the main thread; the commands go out on the IO dispatcher.
fun HandleButton(keyEvent: KeyEvent){
    if (keyEvent.key == Key.MediaPlayPause) {
        // Short press: play or pause. Held (the key starts repeating): stop, once.
        if (keyEvent.type == KeyEventType.KeyDown) {
            if (keyEvent.nativeKeyEvent.repeatCount == 0) mediaPlayPauseLongDone = false
            else if (!mediaPlayPauseLongDone) {
                mediaPlayPauseLongDone = true
                CoroutineScope(Dispatchers.IO).launch { LMS.stop() }
            }
        } else if (keyEvent.type == KeyEventType.KeyUp) {
            if (mediaPlayPauseLongDone) mediaPlayPauseLongDone = false
            else CoroutineScope(Dispatchers.IO).launch { LMS.playPause() }
        }
        return
    }
    if (keyEvent.type == KeyEventType.KeyUp){
        CoroutineScope(Dispatchers.IO).launch {
            when(keyEvent.key){
                Key.MediaFastForward -> LMS.next()
                Key.MediaRewind -> LMS.prev()
                Key.MediaPause -> LMS.playPause(false)
                Key.MediaPlay -> LMS.playPause(true)
            }
        }
    }
}
