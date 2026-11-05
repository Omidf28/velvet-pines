package com.subtitleplayer.prototype

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

internal class EnglishPronunciation(context: Context) {
    private val context = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private var engine: TextToSpeech? = null
    private var initialized = false
    private var closed = false
    private var generation = 0
    private var sequence = 0L
    private var utterance: String? = null
    var initializing by mutableStateOf(true)
        private set
    var ready by mutableStateOf(false)
        private set
    var speaking by mutableStateOf(false)
        private set

    init { initialize() }

    private fun initialize() {
        val request = ++generation
        initializing = true
        initialized = false
        ready = false
        engine?.shutdown()
        engine = TextToSpeech(context) { result ->
            // Dispatch after the constructor assigns engine, including early init failures.
            main.post {
                if (closed || request != generation) return@post
                initializing = false
                initialized = result == TextToSpeech.SUCCESS
                if (initialized) {
                    engine?.setAudioAttributes(
                        AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build()
                    )
                    engine?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                        override fun onStart(utteranceId: String?) = Unit
                        override fun onDone(utteranceId: String?) = finish(utteranceId, false)
                        @Deprecated("Required by TextToSpeech")
                        override fun onError(utteranceId: String?) = finish(utteranceId, true)
                        override fun onError(utteranceId: String?, errorCode: Int) = finish(utteranceId, true)
                        override fun onStop(utteranceId: String?, interrupted: Boolean) = finish(utteranceId, false)
                    })
                    ready = localVoice() != null
                }
            }
        }
    }

    private fun localVoice(): Voice? {
        val voices = engine?.voices.orEmpty()
        val chosen = chooseEnglishSpeechVoice(voices.map {
            EnglishSpeechVoice(
                it.name, it.locale.language, it.locale.country, it.isNetworkConnectionRequired,
                TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED in it.features.orEmpty(),
                it.quality, it.latency
            )
        }) ?: return null
        return voices.firstOrNull { it.name == chosen.name }
    }

    fun refresh() {
        if (closed || initializing) return
        if (!initialized) initialize() else ready = localVoice() != null
    }

    fun speak(text: String, slow: Boolean, beforeSpeak: () -> Unit) {
        if (closed || !initialized || initializing) return
        val value = text.trim()
        if (value.isEmpty()) return
        if (value.length > TextToSpeech.getMaxSpeechInputLength()) {
            message("این جمله برای تلفظ بیش از حد طولانی است.")
            return
        }
        val voice = localVoice()
        if (voice == null) {
            ready = false
            message("ابتدا صدای آفلاین انگلیسی را در تنظیمات گفتار گوشی نصب کنید.")
            return
        }
        val tts = engine ?: return
        if (tts.setVoice(voice) != TextToSpeech.SUCCESS ||
            tts.setSpeechRate(if (slow) 0.75f else 1f) != TextToSpeech.SUCCESS) {
            message("آماده‌کردن تلفظ ممکن نبود.")
            return
        }
        beforeSpeak()
        val id = (++sequence).toString()
        utterance = id
        speaking = true
        if (tts.speak(value, TextToSpeech.QUEUE_FLUSH, null, id) != TextToSpeech.SUCCESS) {
            finish(id, true)
        }
    }

    private fun finish(id: String?, error: Boolean) {
        main.post {
            if (!closed && id != null && utterance == id) {
                utterance = null
                speaking = false
                if (error) message("پخش تلفظ ممکن نبود. صدای انگلیسی گوشی را بررسی کنید.")
            }
        }
    }

    fun stop() {
        utterance = null
        speaking = false
        engine?.stop()
    }

    fun close() {
        closed = true
        stop()
        engine?.shutdown()
        engine = null
    }

    fun openSettings() {
        try {
            context.startActivity(Intent("com.android.settings.TTS_SETTINGS").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: ActivityNotFoundException) {
            try {
                context.startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            } catch (_: ActivityNotFoundException) {
                message("تنظیمات گفتار را از تنظیمات گوشی باز کنید.")
            } catch (_: SecurityException) {
                message("تنظیمات گفتار را از تنظیمات گوشی باز کنید.")
            }
        } catch (_: SecurityException) {
            message("تنظیمات گفتار را از تنظیمات گوشی باز کنید.")
        }
    }

    private fun message(text: String) = Toast.makeText(context, text, Toast.LENGTH_LONG).show()
}
