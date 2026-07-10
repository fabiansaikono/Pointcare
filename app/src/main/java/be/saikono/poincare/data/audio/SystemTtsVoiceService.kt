package be.saikono.poincare.data.audio

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import java.util.Locale

// POINCARE_REPAIR_V31: neutral system fallback settings
class SystemTtsVoiceService(context: Context) : TextToSpeech.OnInitListener {
    private var tts: TextToSpeech? = null
    private var isInitialized = false
    private var onReady: (() -> Unit)? = null
    private var pendingSpeak: Triple<String, String, Float>? = null
    private var pendingVoiceId: String? = null
    private var explicitVoiceSelected = false
    private var currentPitch: Float = 1.0f
    private var currentSpeechRate: Float = 1.0f

    init {
        try {
            tts = TextToSpeech(context.applicationContext, this)
        } catch (e: Exception) {
            // Log or handle initialization failure
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            isInitialized = true
            pendingVoiceId?.let(::setVoice)
            tts?.setPitch(currentPitch)
            tts?.setSpeechRate(0.90f)
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
        currentPitch = pitch
        if (!isInitialized) return
        tts?.setPitch(pitch)
    }

    fun setSpeechRate(rate: Float) {
        currentSpeechRate = rate
        if (!isInitialized) return
        tts?.setSpeechRate(0.90f)
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
            tts?.setSpeechRate(0.90f)
            val params = Bundle().apply { putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, volume.coerceIn(0f, 1f)) }
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, params, null)
        } catch (e: Exception) {
            // Handle speak failure
        }
    }

    fun stop() {
        pendingSpeak = null
        runCatching { tts?.stop() }
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
