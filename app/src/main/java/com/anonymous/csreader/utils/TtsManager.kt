package com.anonymous.csreader.utils

import android.content.Context
import android.speech.tts.TextToSpeech
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.Locale

class TtsManager(context: Context) : TextToSpeech.OnInitListener {
    private var tts: TextToSpeech? = TextToSpeech(context.applicationContext, this)
    var isInitialized by mutableStateOf(false)
        private set
    var isSpeaking by mutableStateOf(false)
        private set

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.language = Locale.getDefault()
            isInitialized = true
        }
    }

    fun speak(text: String, onComplete: (() -> Unit)? = null) {
        if (!isInitialized || text.isBlank()) return
        tts?.stop()
        isSpeaking = true

        tts?.setOnUtteranceProgressListener(object : android.speech.tts.UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                isSpeaking = true
            }

            override fun onDone(utteranceId: String?) {
                isSpeaking = false
                onComplete?.invoke()
            }

            override fun onError(utteranceId: String?) {
                isSpeaking = false
            }
        })

        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "CsReaderUtterance")
    }

    fun stop() {
        tts?.stop()
        isSpeaking = false
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        isSpeaking = false
        isInitialized = false
    }
}
