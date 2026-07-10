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
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class AudioService(
    private val context: Context,
    private val settingsDataStore: SettingsDataStore,
    private val profileRepository: ProfileRepository? = null
) {
    private val ttsVoiceService = SystemTtsVoiceService(context)
    // POINCARE_REPAIR_V31: manifest-backed Belgian-Dutch neural audio
    private val neuralSpeechAssets = NeuralSpeechAssetStore(context)
    private var mediaPlayer: MediaPlayer? = null
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    fun playExerciseAudio(exerciseId: String, textFallback: String, locale: String) {
        scope.launch {
            val fileName = "audio/$exerciseId.mp3"
            try {
                val descriptor = context.assets.openFd(fileName)
                mediaPlayer?.release()
                mediaPlayer = MediaPlayer().apply {
                    setDataSource(descriptor.fileDescriptor, descriptor.startOffset, descriptor.length)
                    val volume = settingsDataStore.animationSettings.first().voiceVolume.coerceIn(0f, 1f)
                    setVolume(volume, volume)
                    prepare()
                    start()
                    setOnCompletionListener {
                        it.release()
                        mediaPlayer = null
                    }
                }
                descriptor.close()
            } catch (e: Exception) {
                speak(textFallback, locale)
            }
        }
    }

    fun playExerciseAudioDelayed(exerciseId: String, textFallback: String, locale: String, delayMillis: Long = 500L) {
        scope.launch {
            delay(delayMillis)
            playExerciseAudio(exerciseId, textFallback, locale)
        }
    }

    // POINCARE_REPAIR_V31: neural NL-BE first, system TTS fallback
    fun speak(text: String, locale: String) {
        scope.launch {
            val normalizedText = normalizeSpeechText(text)
            try {
                val profileId = settingsDataStore.selectedProfileId.first()
                val profile = profileId?.let { profileRepository?.getProfileById(it)?.first() }
                val selectedLocale = locale.ifBlank {
                    profile?.speechLanguage ?: profile?.exerciseLanguage ?: profile?.appLanguage ?: "nl-BE"
                }
                if (profileId != null) {
                    val settings = settingsDataStore.getVoiceSettings(profileId, selectedLocale).first()
                    applyVoiceSettings(settings)
                } else {
                    ttsVoiceService.setPitch(1.0f)
                    ttsVoiceService.setSpeechRate(1.0f)
                }
                val volume = settingsDataStore.animationSettings.first().voiceVolume.coerceIn(0f, 1f)
                if (!playNeuralSpeechIfAvailable(normalizedText, selectedLocale, volume)) {
                    ttsVoiceService.speak(normalizedText, selectedLocale, volume)
                }
            } catch (e: Exception) {
                val safeLocale = locale.ifBlank { "nl-BE" }
                ttsVoiceService.setPitch(1.0f)
                ttsVoiceService.setSpeechRate(1.0f)
                if (!playNeuralSpeechIfAvailable(normalizedText, safeLocale, 1.0f)) {
                    ttsVoiceService.speak(normalizedText, safeLocale)
                }
            }
        }
    }

    private fun playNeuralSpeechIfAvailable(text: String, locale: String, volume: Float): Boolean {
        val assetPath = neuralSpeechAssets.findAsset(locale, text) ?: return false
        return playBundledAsset(assetPath, volume)
    }

    private fun playBundledAsset(assetPath: String, volume: Float): Boolean = runCatching {
        ttsVoiceService.stop()
        mediaPlayer?.release()
        val player = MediaPlayer()
        val descriptor = runCatching { context.assets.openFd(assetPath) }.getOrNull()
        if (descriptor != null) {
            descriptor.use {
                player.setDataSource(it.fileDescriptor, it.startOffset, it.length)
            }
        } else {
            val extension = assetPath.substringAfterLast('.', "bin")
            val cacheFile = java.io.File(
                context.cacheDir,
                "poincare_tts_${assetPath.hashCode()}.$extension"
            )
            if (!cacheFile.exists() || cacheFile.length() == 0L) {
                context.assets.open(assetPath).use { input ->
                    cacheFile.outputStream().use { output -> input.copyTo(output) }
                }
            }
            player.setDataSource(cacheFile.absolutePath)
        }
        player.setVolume(volume.coerceIn(0f, 1f), volume.coerceIn(0f, 1f))
        player.prepare()
        mediaPlayer = player
        player.setOnCompletionListener { completed ->
            completed.release()
            if (mediaPlayer === completed) mediaPlayer = null
        }
        player.start()
        true
    }.getOrElse {
        mediaPlayer?.release()
        mediaPlayer = null
        false
    }

    private fun normalizeSpeechText(text: String): String = text
        .replace('_', ' ')
        .replace(Regex("\\b(\\d{1,2}):(\\d{2})\\b")) { match ->
            val hour = match.groupValues[1].toIntOrNull()?.toString() ?: match.groupValues[1]
            val minutes = match.groupValues[2].toIntOrNull() ?: return@replace match.value
            if (minutes == 0) hour else "$hour ${minutes.toString().padStart(2, '0')}"
        }
        .replace(Regex("\\b(\\d{1,2})/(\\d{1,2})/(\\d{2,4})\\b")) { match ->
            "${match.groupValues[1]} ${match.groupValues[2]} ${match.groupValues[3]}"
        }
        .replace(Regex("\\b(\\d{1,2})-(\\d{1,2})-(\\d{2,4})\\b")) { match ->
            "${match.groupValues[1]} ${match.groupValues[2]} ${match.groupValues[3]}"
        }

    private fun applyVoiceSettings(settings: VoiceSettings) {
        if (settings.voiceId != null) {
            ttsVoiceService.setVoice(settings.voiceId)
        } else {
            ttsVoiceService.clearVoiceSelection()
        }
        ttsVoiceService.setPitch(settings.pitch.takeIf { it > 0f } ?: 1.0f)
        ttsVoiceService.setSpeechRate(settings.rate.takeIf { it > 0f } ?: 1.0f)
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
            val tone = ToneGenerator(AudioManager.STREAM_MUSIC, (settings.soundEffectsVolume.coerceIn(0f, 1f) * 100).toInt())
            tone.startTone(toneType, durationMs)
            delay(durationMs.toLong() + 40L)
            tone.release()
        }
    }

    fun getTtsVoiceService() = ttsVoiceService

    fun stopPlayback() {
        mediaPlayer?.stop()
        mediaPlayer?.release()
        mediaPlayer = null
        ttsVoiceService.stop()
    }

    fun shutdown() {
        ttsVoiceService.shutdown()
        mediaPlayer?.release()
        mediaPlayer = null
    }
}
