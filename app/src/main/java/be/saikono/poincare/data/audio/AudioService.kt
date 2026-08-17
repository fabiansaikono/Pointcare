package be.saikono.poincare.data.audio

import android.content.Context
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.ToneGenerator
import be.saikono.poincare.data.settings.SettingsDataStore
import be.saikono.poincare.data.settings.VoiceSettings
import be.saikono.poincare.repository.ProfileRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class AudioService(
    private val context: Context,
    private val settingsDataStore: SettingsDataStore,
    private val profileRepository: ProfileRepository? = null
) {
    private val systemTts = SystemTtsVoiceService(context)
    private val offlineDutchTts = OfflineDutchTtsEngine(context)
    private var mediaPlayer: MediaPlayer? = null
    private var playbackGeneration = 0L
    private var playbackJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Main.immediate + SupervisorJob())

    fun playExerciseAudio(exerciseId: String, textFallback: String, locale: String) {
        val requestGeneration = beginPlaybackRequest()
        playbackJob = scope.launch {
            playExerciseAudioRequest(requestGeneration, exerciseId, textFallback, locale)
        }
    }

    fun playExerciseAudioDelayed(
        exerciseId: String,
        textFallback: String,
        locale: String,
        delayMillis: Long = 500L
    ) {
        val requestGeneration = beginPlaybackRequest()
        playbackJob = scope.launch {
            delay(delayMillis)
            if (requestGeneration != playbackGeneration) return@launch
            playExerciseAudioRequest(requestGeneration, exerciseId, textFallback, locale)
        }
    }

    private suspend fun playExerciseAudioRequest(
        requestGeneration: Long,
        exerciseId: String,
        textFallback: String,
        locale: String
    ) {
        val fileName = "audio/$exerciseId.mp3"
        try {
            val descriptor = context.assets.openFd(fileName)
            val volume = settingsDataStore.animationSettings.first().voiceVolume.coerceIn(0f, 1f)
            if (requestGeneration != playbackGeneration) {
                descriptor.close()
                return
            }
            val player = MediaPlayer().apply {
                setDataSource(descriptor.fileDescriptor, descriptor.startOffset, descriptor.length)
                setVolume(volume, volume)
                prepare()
            }
            descriptor.close()
            if (requestGeneration != playbackGeneration) {
                player.release()
                return
            }
            mediaPlayer = player
            player.setOnCompletionListener { completed ->
                completed.release()
                if (mediaPlayer === completed) mediaPlayer = null
            }
            player.start()
        } catch (_: Exception) {
            if (requestGeneration == playbackGeneration) speak(textFallback, locale)
        }
    }

    /**
     * Speaks a complete utterance. Belgian Dutch is synthesized locally with
     * the bundled Nathalie model. Other locales use the installed Android TTS
     * voice until equivalent bundled models have passed their acceptance tests.
     */
    fun speak(text: String, locale: String) {
        val requestGeneration = beginPlaybackRequest()
        playbackJob = scope.launch {
            val runtime = resolveRuntimeSettings(locale)
            val normalizedText = SpeechTextNormalizer.normalize(text, runtime.locale)
            val vowelLabelDelayMillis = SpeechTextNormalizer.leadingSilenceMillisFor(
                normalizedText,
                runtime.locale
            )
            if (vowelLabelDelayMillis > 0L) {
                delay(vowelLabelDelayMillis)
                if (requestGeneration != playbackGeneration) return@launch
            }
            if (normalizedText.isBlank() || requestGeneration != playbackGeneration) return@launch

            if (SpeechTextNormalizer.isBelgianDutch(runtime.locale)) {
                val speed = SpeechTextNormalizer.speedFor(
                    text = normalizedText,
                    locale = runtime.locale,
                    requestedRate = runtime.rate
                )
                val playedLocally = runCatching {
                    offlineDutchTts.speak(
                        text = normalizedText,
                        speed = speed,
                        volume = runtime.volume
                    ) { requestGeneration == playbackGeneration }
                }.getOrDefault(false)

                if (!playedLocally && requestGeneration == playbackGeneration) {
                    systemTts.speak(normalizedText, runtime.locale, runtime.volume)
                }
            } else if (requestGeneration == playbackGeneration) {
                systemTts.speak(normalizedText, runtime.locale, runtime.volume)
            }
        }
    }

    fun speakStory(
        text: String,
        locale: String,
        rateMultiplier: Float = 1f,
        endingPauseMillis: Long = 0L,
        onRangeStart: (Int, Int) -> Unit,
        onRangesUnavailable: () -> Unit,
        onDone: () -> Unit
    ) {
        val requestGeneration = beginPlaybackRequest()
        playbackJob = scope.launch {
            val runtime = resolveRuntimeSettings(locale)
            val normalizedText = SpeechTextNormalizer.normalize(text, runtime.locale)
            if (normalizedText.isBlank() || requestGeneration != playbackGeneration) return@launch

            if (SpeechTextNormalizer.isBelgianDutch(runtime.locale)) {
                onRangesUnavailable()
                val speed = SpeechTextNormalizer.speedFor(
                    text = normalizedText,
                    locale = runtime.locale,
                    requestedRate = runtime.rate * rateMultiplier.coerceIn(0.6f, 1.2f)
                )
                val played = runCatching {
                    offlineDutchTts.speak(
                        text = normalizedText,
                        speed = speed,
                        volume = runtime.volume
                    ) { requestGeneration == playbackGeneration }
                }.getOrDefault(false)
                if (played && requestGeneration == playbackGeneration) {
                    delay(endingPauseMillis.coerceAtLeast(0L))
                    if (requestGeneration == playbackGeneration) onDone()
                }
            } else {
                val utteranceId = "story-$requestGeneration-${System.nanoTime()}"
                val started = systemTts.speakWithRanges(
                    text = normalizedText,
                    locale = runtime.locale,
                    volume = runtime.volume,
                    utteranceId = utteranceId,
                    listener = object : SystemTtsVoiceService.RangeListener {
                        override fun onStart() = Unit
                        override fun onRangeStart(start: Int, endExclusive: Int) {
                            if (requestGeneration == playbackGeneration) {
                                onRangeStart(start, endExclusive)
                            }
                        }
                        override fun onDone() {
                            if (requestGeneration == playbackGeneration) {
                                scope.launch {
                                    delay(endingPauseMillis.coerceAtLeast(0L))
                                    if (requestGeneration == playbackGeneration) onDone()
                                }
                            }
                        }
                        override fun onError() = Unit
                    }
                )
                if (!started && requestGeneration == playbackGeneration) {
                    onRangesUnavailable()
                    systemTts.speak(normalizedText, runtime.locale, runtime.volume)
                }
            }
        }
    }

    private suspend fun resolveRuntimeSettings(requestedLocale: String): RuntimeVoiceSettings {
        val profileId = settingsDataStore.selectedProfileId.first()
        val profile = profileId?.let { profileRepository?.getProfileById(it)?.first() }
        val selectedLocale = SpeechTextNormalizer.normalizeLocale(
            requestedLocale.ifBlank {
                profile?.speechLanguage ?: profile?.exerciseLanguage ?: profile?.appLanguage ?: "nl-BE"
            }
        )
        val voiceSettings = profileId?.let {
            runCatching { settingsDataStore.getVoiceSettings(it, selectedLocale).first() }.getOrNull()
        }
        applySystemVoiceSettings(voiceSettings, selectedLocale)
        val volume = settingsDataStore.animationSettings.first().voiceVolume.coerceIn(0f, 1f)
        return RuntimeVoiceSettings(
            locale = selectedLocale,
            volume = volume,
            rate = voiceSettings?.rate?.takeIf { it > 0f } ?: 1.0f
        )
    }

    private fun applySystemVoiceSettings(settings: VoiceSettings?, locale: String) {
        if (settings?.voiceId != null && !SpeechTextNormalizer.isBelgianDutch(locale)) {
            systemTts.setVoice(settings.voiceId)
        } else {
            systemTts.clearVoiceSelection()
        }
        systemTts.setPitch(settings?.pitch?.takeIf { it > 0f } ?: 1.0f)
        systemTts.setSpeechRate(settings?.rate?.takeIf { it > 0f } ?: 1.0f)
    }

    fun playSuccess() {
        playTone(ToneGenerator.TONE_PROP_ACK, 180)
    }

    fun playFailure() {
        playTone(ToneGenerator.TONE_CDMA_LOW_L, 320)
    }

    private fun playTone(toneType: Int, durationMs: Int) {
        scope.launch {
            val settings = settingsDataStore.animationSettings.first()
            if (!settings.soundEffectsEnabled || settings.soundEffectsVolume <= 0f) return@launch
            val tone = ToneGenerator(
                AudioManager.STREAM_MUSIC,
                (settings.soundEffectsVolume.coerceIn(0f, 1f) * 100).toInt()
            )
            tone.startTone(toneType, durationMs)
            delay(durationMs.toLong() + 40L)
            tone.release()
        }
    }

    fun getTtsVoiceService() = systemTts

    private fun beginPlaybackRequest(): Long {
        cancelCurrentSpeech()
        return playbackGeneration
    }

    private fun releaseCurrentMediaOnly() {
        mediaPlayer?.runCatching { stop() }
        mediaPlayer?.runCatching { release() }
        mediaPlayer = null
    }

    private fun cancelCurrentSpeech() {
        playbackGeneration += 1
        playbackJob?.cancel()
        playbackJob = null
        releaseCurrentMediaOnly()
        offlineDutchTts.stop()
        systemTts.stop()
    }

    fun stopPlayback() {
        cancelCurrentSpeech()
    }

    fun shutdown() {
        cancelCurrentSpeech()
        offlineDutchTts.shutdown()
        systemTts.shutdown()
        scope.cancel()
    }

    private data class RuntimeVoiceSettings(
        val locale: String,
        val volume: Float,
        val rate: Float
    )
}
