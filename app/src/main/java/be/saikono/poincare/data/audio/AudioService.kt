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
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class AudioService(
    private val context: Context,
    private val settingsDataStore: SettingsDataStore,
    private val profileRepository: ProfileRepository? = null
) {
    private val ttsVoiceService = SystemTtsVoiceService(context)
    private val neuralSpeechAssets = NeuralSpeechAssetStore(context)
    private var mediaPlayer: MediaPlayer? = null
    private var bundledPlayers: List<MediaPlayer> = emptyList()
    private var playbackGeneration = 0L
    private var playbackJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

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
        } catch (error: Exception) {
            if (requestGeneration == playbackGeneration) speak(textFallback, locale)
        }
    }

    /**
     * Belgian Dutch always uses bundled Nathalie speech. Static utterances are
     * played as one clip; generated prompts are composed from compact Nathalie
     * fragments. Android system TTS remains a fallback only for other locales.
     */
    fun speak(text: String, locale: String) {
        val requestGeneration = beginPlaybackRequest()
        playbackJob = scope.launch {
            val normalizedText = normalizeSpeechText(text)
            try {
                val profileId = settingsDataStore.selectedProfileId.first()
                val profile = profileId?.let { profileRepository?.getProfileById(it)?.first() }
                val selectedLocale = locale.ifBlank {
                    profile?.speechLanguage ?: profile?.exerciseLanguage ?: profile?.appLanguage ?: "nl-BE"
                }
                if (profileId != null) {
                    val settings = settingsDataStore.getVoiceSettings(profileId, selectedLocale).first()
                    applyVoiceSettings(settings, selectedLocale)
                } else {
                    ttsVoiceService.clearVoiceSelection()
                    ttsVoiceService.setPitch(1.0f)
                    ttsVoiceService.setSpeechRate(1.0f)
                }
                val volume = settingsDataStore.animationSettings.first().voiceVolume.coerceIn(0f, 1f)
                if (requestGeneration != playbackGeneration) return@launch
                val playedByNathalie = playNeuralSpeechIfAvailable(normalizedText, selectedLocale, volume)
                if (!playedByNathalie && !NeuralSpeechAssetStore.isBelgianDutch(selectedLocale)) {
                    releaseCurrentMediaOnly()
                    ttsVoiceService.speak(normalizedText, selectedLocale, volume)
                }
            } catch (error: Exception) {
                val safeLocale = locale.ifBlank { "nl-BE" }
                ttsVoiceService.clearVoiceSelection()
                ttsVoiceService.setPitch(1.0f)
                ttsVoiceService.setSpeechRate(1.0f)
                if (requestGeneration != playbackGeneration) return@launch
                val playedByNathalie = playNeuralSpeechIfAvailable(normalizedText, safeLocale, 1.0f)
                if (!playedByNathalie && !NeuralSpeechAssetStore.isBelgianDutch(safeLocale)) {
                    releaseCurrentMediaOnly()
                    ttsVoiceService.speak(normalizedText, safeLocale)
                }
            }
        }
    }

    private fun playNeuralSpeechIfAvailable(text: String, locale: String, volume: Float): Boolean {
        val assetPaths = neuralSpeechAssets.findAssets(locale, text) ?: return false
        return playBundledAssets(assetPaths, volume)
    }

    private fun playBundledAssets(assetPaths: List<String>, volume: Float): Boolean {
        if (assetPaths.isEmpty()) return false
        ttsVoiceService.stop()
        releaseCurrentMediaOnly()
        return runCatching {
            val players = assetPaths.map { path -> createBundledPlayer(path, volume) }
            players.zipWithNext().forEach { (current, next) -> current.setNextMediaPlayer(next) }
            val last = players.last()
            last.setOnCompletionListener {
                if (bundledPlayers === players) {
                    players.forEach { player -> player.release() }
                    bundledPlayers = emptyList()
                    mediaPlayer = null
                }
            }
            bundledPlayers = players
            mediaPlayer = players.first()
            players.first().start()
            true
        }.getOrElse {
            bundledPlayers.forEach { player -> player.release() }
            bundledPlayers = emptyList()
            mediaPlayer = null
            false
        }
    }

    private fun createBundledPlayer(assetPath: String, volume: Float): MediaPlayer {
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
        val safeVolume = volume.coerceIn(0f, 1f)
        player.setVolume(safeVolume, safeVolume)
        player.prepare()
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M && assetPath !in PAUSE_ASSETS) {
            player.playbackParams = player.playbackParams.setSpeed(NATHALIE_PLAYBACK_SPEED)
        }
        return player
    }

    private fun normalizeSpeechText(text: String): String {
        var normalized = text.replace('_', ' ')
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
        normalized = EURO_PREFIX.replace(normalized) { match -> "${match.groupValues[1]} euro" }
        normalized = EURO_SUFFIX.replace(normalized) { match -> "${match.groupValues[1]} euro" }
        normalized = ARITHMETIC_DASH.replace(normalized, " − ")
        normalized = NON_NUMERIC_DASH.replace(normalized, " ")
        return normalized.replace(Regex("[ \\t]+"), " ").trim()
    }

    private fun applyVoiceSettings(settings: VoiceSettings, locale: String) {
        if (NeuralSpeechAssetStore.isBelgianDutch(locale)) {
            // Nathalie is fixed for nl-BE; a parent-selected Android voice must not override it.
            ttsVoiceService.clearVoiceSelection()
        } else if (settings.voiceId != null) {
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
            val tone = ToneGenerator(
                AudioManager.STREAM_MUSIC,
                (settings.soundEffectsVolume.coerceIn(0f, 1f) * 100).toInt()
            )
            tone.startTone(toneType, durationMs)
            delay(durationMs.toLong() + 40L)
            tone.release()
        }
    }

    fun getTtsVoiceService() = ttsVoiceService

    private fun beginPlaybackRequest(): Long {
        cancelCurrentSpeech()
        return playbackGeneration
    }

    private fun releaseCurrentMediaOnly() {
        mediaPlayer?.runCatching { stop() }
        bundledPlayers.forEach { player -> player.runCatching { release() } }
        bundledPlayers = emptyList()
        mediaPlayer?.runCatching { release() }
        mediaPlayer = null
    }

    private fun cancelCurrentSpeech() {
        playbackGeneration += 1
        playbackJob?.cancel()
        playbackJob = null
        releaseCurrentMediaOnly()
        ttsVoiceService.stop()
    }

    fun stopPlayback() {
        cancelCurrentSpeech()
    }

    fun shutdown() {
        cancelCurrentSpeech()
        ttsVoiceService.shutdown()
    }
    private companion object {
        const val NATHALIE_PLAYBACK_SPEED = 1.12f
        const val SENTENCE_PAUSE_ASSET = "tts/common/pause_sentence.wav"
        const val WORD_PAUSE_ASSET = "tts/common/pause_word.wav"
        val PAUSE_ASSETS = setOf(SENTENCE_PAUSE_ASSET, WORD_PAUSE_ASSET)
        val EURO_PREFIX = Regex("€\\s*([0-9][0-9 .]*(?:[,.][0-9]+)?)")
        val EURO_SUFFIX = Regex("([0-9][0-9 .]*(?:[,.][0-9]+)?)\\s*€")
        val ARITHMETIC_DASH = Regex("(?<=\\d)\\s*[-–—]\\s*(?=\\d)")
        val NON_NUMERIC_DASH = Regex("[-–—]")
    }

}
