package com.hskpath.app

import android.content.Context
import android.speech.tts.TextToSpeech
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.Locale

/** Reads Chinese aloud with the device's text-to-speech engine. */
class Speaker(context: Context) : TextToSpeech.OnInitListener {
    /** True when no Mandarin voice is installed, so the UI can say so. */
    var unavailable by mutableStateOf(false)
        private set
    private var ready = false
    private val tts = TextToSpeech(context.applicationContext, this)

    override fun onInit(status: Int) {
        if (status != TextToSpeech.SUCCESS) {
            unavailable = true
            return
        }
        val result = tts.setLanguage(Locale.SIMPLIFIED_CHINESE)
        if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
            unavailable = true
        } else {
            ready = true
        }
    }

    fun speak(text: String, slow: Boolean = false) {
        if (!ready || text.isBlank()) return
        tts.setSpeechRate(if (slow) 0.5f else 0.9f)
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "speak")
    }

    /** Reads several lines one after another, e.g. a whole dialogue. */
    fun speakAll(lines: List<String>) {
        if (!ready) return
        tts.setSpeechRate(0.9f)
        lines.forEachIndexed { i, line ->
            tts.speak(line, if (i == 0) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD, null, "line$i")
            tts.playSilentUtterance(350, TextToSpeech.QUEUE_ADD, "pause$i")
        }
    }

    fun stop() {
        if (ready) tts.stop()
    }

    fun shutdown() = tts.shutdown()
}
