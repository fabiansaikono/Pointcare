package be.saikono.poincare.data.audio

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

class SystemTtsVoiceService(context: Context) : TextToSpeech.OnInitListener {
    interface RangeListener {
        fun onStart()
        fun onRangeStart(start: Int, endExclusive: Int)
        fun onDone()
        fun onError()
    }

    private var tts: TextToSpeech? = null
    private var isInitialized = false
    private var onReady: (() -> Unit)? = null
    private var pendingSpeak: Triple<String, String, Float>? = null
    private var pendingVoiceId: String? = null
    private var explicitVoiceSelected = false
    private var currentPitch: Float = 1.0f
    private var currentSpeechRate: Float = 1.0f
    private val rangeListeners = ConcurrentHashMap<String, RangeListener>()

    init {
        try {
            tts = TextToSpeech(context.applicationContext, this)
            tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    utteranceId?.let { rangeListeners[it]?.onStart() }
                }

                override fun onDone(utteranceId: String?) {
                    utteranceId?.let { id -> rangeListeners.remove(id)?.onDone() }
                }

                @Deprecated("Deprecated by Android")
                override fun onError(utteranceId: String?) {
                    utteranceId?.let { id -> rangeListeners.remove(id)?.onError() }
                }

                override fun onError(utteranceId: String?, errorCode: Int) {
                    utteranceId?.let { id -> rangeListeners.remove(id)?.onError() }
                }

                override fun onRangeStart(
                    utteranceId: String?,
                    start: Int,
                    end: Int,
                    frame: Int
                ) {
                    utteranceId?.let { rangeListeners[it]?.onRangeStart(start, end) }
                }
            })
        } catch (e: Exception) {
            // Log or handle initialization failure
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            isInitialized = true
            pendingVoiceId?.let(::setVoice)
            tts?.setPitch(currentPitch)
            tts?.setSpeechRate(currentSpeechRate)
            pendingSpeak?.let { (text, locale, volume) -> speak(text, locale, volume) }
            pendingSpeak = null
            onReady?.invoke()
        } else {
            isInitialized = false
        }
    }

    fun setOnReadyListener(listener: () -> Unit) {
        onReady = listener
        if (isInitialized) listener()
    }

    fun getAvailableVoices(localeCodes: List<String>): List<Voice> {
        if (!isInitialized) return emptyList()
        val locales = localeCodes.map { it.toLocale() }
        return try {
            tts?.voices?.filter { voice ->
                locales.any { locale -> voice.locale.language == locale.language }
            } ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun setVoice(voiceId: String) {
        pendingVoiceId = voiceId
        explicitVoiceSelected = true
        if (!isInitialized) return
        try {
            val voice = tts?.voices?.find { it.name == voiceId }
            if (voice != null) {
                tts?.voice = voice
            }
        } catch (e: Exception) {
            // Handle setVoice failure
        }
    }

    fun clearVoiceSelection() {
        pendingVoiceId = null
        explicitVoiceSelected = false
    }

    fun setPitch(pitch: Float) {
        currentPitch = pitch.coerceIn(0.5f, 2.0f)
        if (!isInitialized) return
        tts?.setPitch(currentPitch)
    }

    fun setSpeechRate(rate: Float) {
        currentSpeechRate = rate.coerceIn(0.5f, 2.0f)
        if (!isInitialized) return
        tts?.setSpeechRate(currentSpeechRate)
    }

    fun speak(text: String, locale: String, volume: Float = 1.0f) {
        if (!isInitialized) {
            pendingSpeak = Triple(text, locale, volume.coerceIn(0f, 1f))
            return
        }
        try {
            val targetLocale = locale.toLocale()
            tts?.language = targetLocale
            if (!explicitVoiceSelected) selectBestVoice(targetLocale)
            tts?.setPitch(currentPitch)
            tts?.setSpeechRate(currentSpeechRate)
            val params = Bundle().apply { putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, volume.coerceIn(0f, 1f)) }
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, params, null)
        } catch (e: Exception) {
            // Handle speak failure
        }
    }

    fun speakWithRanges(
        text: String,
        locale: String,
        volume: Float,
        utteranceId: String,
        listener: RangeListener
    ): Boolean {
        if (!isInitialized) return false
        return try {
            val targetLocale = locale.toLocale()
            tts?.language = targetLocale
            if (!explicitVoiceSelected) selectBestVoice(targetLocale)
            tts?.setPitch(currentPitch)
            tts?.setSpeechRate(currentSpeechRate)
            val params = Bundle().apply {
                putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, volume.coerceIn(0f, 1f))
            }
            rangeListeners[utteranceId] = listener
            val result = tts?.speak(text, TextToSpeech.QUEUE_FLUSH, params, utteranceId)
            if (result == TextToSpeech.SUCCESS) {
                true
            } else {
                rangeListeners.remove(utteranceId)
                false
            }
        } catch (_: Exception) {
            rangeListeners.remove(utteranceId)
            false
        }
    }

    fun stop() {
        pendingSpeak = null
        val listeners = rangeListeners.values.toList()
        rangeListeners.clear()
        runCatching { tts?.stop() }
        listeners.forEach { it.onError() }
    }

    fun shutdown() {
        try {
            tts?.stop()
            tts?.shutdown()
        } catch (e: Exception) {
            // Ignore
        } finally {
            tts = null
            isInitialized = false
        }
    }

    private fun String.toLocale(): Locale = when (this) {
        "nl-BE", "nl_BE" -> Locale("nl", "BE")
        "nl" -> Locale("nl", "BE")
        "fr-BE", "fr_BE" -> Locale("fr", "BE")
        "fr" -> Locale("fr", "BE")
        "fr-FR", "fr_FR" -> Locale.FRANCE
        "en-US", "en_US" -> Locale.US
        "en" -> Locale.ENGLISH
        else -> Locale(this)
    }

    private fun selectBestVoice(locale: Locale) {
        val bestVoice = tts?.voices
            ?.filter { voice -> voice.locale.language == locale.language }
            ?.maxByOrNull { voice -> voice.scoreFor(locale) }
        if (bestVoice != null) {
            tts?.voice = bestVoice
        }
    }

    private fun Voice.scoreFor(locale: Locale): Int {
        val nameScore = when {
            name.contains("google", ignoreCase = true) -> 30
            name.contains("x-", ignoreCase = true) -> 20
            name.contains("default", ignoreCase = true) -> -20
            else -> 0
        }
        val localeScore = if (this.locale.country == locale.country) 100 else 0
        return nameScore + localeScore + quality * 10 - latency
    }
}
