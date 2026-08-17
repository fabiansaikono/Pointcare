package be.saikono.poincare.data.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.k2fsa.sherpa.onnx.GenerationConfig
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsVitsModelConfig
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Synthesizes complete Belgian-Dutch utterances locally. The model is bundled
 * with the APK; no network request and no per-word recording is used.
 */
internal class OfflineDutchTtsEngine(context: Context) {
    private val appContext = context.applicationContext
    private val serialPlayback = Mutex()
    private val cancellationGeneration = AtomicLong(0L)
    private val activePlayback = AtomicReference<PlaybackSession?>(null)
    @Volatile private var tts: OfflineTts? = null

    suspend fun speak(
        text: String,
        speed: Float,
        volume: Float,
        isStillCurrent: () -> Boolean
    ): Boolean = withContext(Dispatchers.Default) {
        serialPlayback.withLock {
            val requestGeneration = cancellationGeneration.get()
            if (text.isBlank() || !isStillCurrent()) return@withLock false

            val engine = ensureEngine()
            if (requestGeneration != cancellationGeneration.get() || !isStillCurrent()) {
                return@withLock false
            }

            val audio = engine.generateWithConfig(
                text = text,
                config = GenerationConfig(
                    sid = 0,
                    speed = speed.coerceIn(0.70f, 1.35f),
                    silenceScale = 0.24f
                )
            )
            if (audio.samples.isEmpty() ||
                requestGeneration != cancellationGeneration.get() ||
                !isStillCurrent()
            ) {
                return@withLock false
            }

            playCompleteUtterance(
                samples = audio.samples,
                sampleRate = audio.sampleRate,
                volume = volume,
                requestGeneration = requestGeneration,
                isStillCurrent = isStillCurrent
            )
        }
    }

    fun stop() {
        cancellationGeneration.incrementAndGet()
        activePlayback.getAndSet(null)?.finish(false)
    }

    fun shutdown() {
        stop()
        runCatching { tts?.release() }
        tts = null
    }

    private fun ensureEngine(): OfflineTts {
        tts?.let { return it }
        val dataDir = installEspeakData()
        val config = OfflineTtsConfig(
            model = OfflineTtsModelConfig(
                vits = OfflineTtsVitsModelConfig(
                    model = "$MODEL_ASSET_DIR/$MODEL_FILE",
                    tokens = "$MODEL_ASSET_DIR/tokens.txt",
                    dataDir = dataDir.absolutePath,
                    noiseScale = 0.667f,
                    noiseScaleW = 0.8f,
                    lengthScale = 1.0f
                ),
                numThreads = Runtime.getRuntime().availableProcessors().coerceIn(1, 2),
                debug = false,
                provider = "cpu"
            ),
            maxNumSentences = 1,
            silenceScale = 0.24f
        )
        return OfflineTts(assetManager = appContext.assets, config = config).also { tts = it }
    }

    private fun installEspeakData(): File {
        val installRoot = File(appContext.noBackupFilesDir, "tts/$MODEL_INSTALL_ID")
        val marker = File(installRoot, READY_MARKER)
        val dataDir = File(installRoot, "espeak-ng-data")
        if (marker.readTextOrNull() == MODEL_ARCHIVE_SHA256 && dataDir.isDirectory) return dataDir

        val parent = installRoot.parentFile ?: error("Missing TTS install parent")
        parent.mkdirs()
        val staging = File(parent, "$MODEL_INSTALL_ID.staging-${System.nanoTime()}")
        staging.deleteRecursively()
        staging.mkdirs()

        return try {
            val stagedData = File(staging, "espeak-ng-data")
            copyAssetTree("$MODEL_ASSET_DIR/espeak-ng-data", stagedData)
            check(File(stagedData, "nl_dict").isFile) { "Incomplete Dutch phonemizer data" }
            File(staging, READY_MARKER).writeText(MODEL_ARCHIVE_SHA256)

            installRoot.deleteRecursively()
            check(staging.renameTo(installRoot)) { "Unable to activate local TTS data" }
            File(installRoot, "espeak-ng-data")
        } catch (error: Throwable) {
            staging.deleteRecursively()
            throw error
        }
    }

    private fun copyAssetTree(assetPath: String, destination: File) {
        val children = appContext.assets.list(assetPath).orEmpty()
        if (children.isEmpty()) {
            destination.parentFile?.mkdirs()
            appContext.assets.open(assetPath).use { input ->
                destination.outputStream().use { output -> input.copyTo(output) }
            }
            return
        }

        destination.mkdirs()
        children.forEach { child ->
            copyAssetTree("$assetPath/$child", File(destination, child))
        }
    }

    private suspend fun playCompleteUtterance(
        samples: FloatArray,
        sampleRate: Int,
        volume: Float,
        requestGeneration: Long,
        isStillCurrent: () -> Boolean
    ): Boolean = suspendCancellableCoroutine { continuation ->
        val minimumBuffer = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_FLOAT
        ).coerceAtLeast(0)
        val bufferBytes = maxOf(samples.size * Float.SIZE_BYTES, minimumBuffer)
        val attributes = AudioAttributes.Builder()
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .build()
        val format = AudioFormat.Builder()
            .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
            .setSampleRate(sampleRate)
            .build()
        val track = AudioTrack(
            attributes,
            format,
            bufferBytes,
            AudioTrack.MODE_STATIC,
            AudioManager.AUDIO_SESSION_ID_GENERATE
        )
        val completed = AtomicBoolean(false)
        lateinit var session: PlaybackSession

        fun finish(success: Boolean) {
            if (!completed.compareAndSet(false, true)) return
            activePlayback.compareAndSet(session, null)
            runCatching { track.pause() }
            runCatching { track.flush() }
            runCatching { track.release() }
            if (continuation.isActive) continuation.resume(success)
        }

        session = PlaybackSession(finish = ::finish)
        continuation.invokeOnCancellation { finish(false) }

        try {
            track.setVolume(volume.coerceIn(0f, 1f))
            val written = track.write(samples, 0, samples.size, AudioTrack.WRITE_BLOCKING)
            check(written == samples.size) { "AudioTrack accepted $written of ${samples.size} samples" }
            check(requestGeneration == cancellationGeneration.get() && isStillCurrent()) {
                "TTS playback superseded"
            }
            track.setPlaybackPositionUpdateListener(
                object : AudioTrack.OnPlaybackPositionUpdateListener {
                    override fun onMarkerReached(ignored: AudioTrack) = finish(true)
                    override fun onPeriodicNotification(ignored: AudioTrack) = Unit
                },
                Handler(Looper.getMainLooper())
            )
            track.setNotificationMarkerPosition(samples.size)
            activePlayback.getAndSet(session)?.finish(false)
            check(requestGeneration == cancellationGeneration.get() && isStillCurrent()) {
                "TTS playback superseded before start"
            }
            track.play()
        } catch (error: Throwable) {
            Log.e(TAG, "Unable to play synthesized utterance", error)
            finish(false)
        }
    }

    private fun File.readTextOrNull(): String? = runCatching { readText().trim() }.getOrNull()

    private data class PlaybackSession(
        val finish: (Boolean) -> Unit
    )

    private companion object {
        const val TAG = "OfflineDutchTts"
        const val MODEL_INSTALL_ID = "nathalie-medium-v1"
        const val MODEL_ASSET_DIR = "tts/vits-piper-nl_BE-nathalie-medium"
        const val MODEL_FILE = "nl_BE-nathalie-medium.onnx"
        const val READY_MARKER = ".ready"
        const val MODEL_ARCHIVE_SHA256 =
            "c7484313abea395243e872196bc75181c07aad8120b8a61e0f6f4b3863b6a7c8"
    }
}
