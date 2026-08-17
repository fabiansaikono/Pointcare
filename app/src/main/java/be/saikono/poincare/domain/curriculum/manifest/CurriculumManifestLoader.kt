package be.saikono.poincare.domain.curriculum.manifest

import java.io.InputStream
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

object CurriculumManifestLoader {
    const val ASSET_PATH: String = "poincare/curriculum/curriculum-manifest-v1.json"

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = false
        classDiscriminator = "sourceType"
    }

    fun fromStream(input: InputStream): CurriculumManifest = input.bufferedReader(Charsets.UTF_8).use {
        fromJson(it.readText())
    }

    /**
     * Loads the validated release exactly as exported by the Curriculum Room.
     * Deterministic policy output is materialized before publication; Android
     * must not mutate the canonical curriculum while parsing it.
     */
    fun fromJson(value: String): CurriculumManifest = json.decodeFromString(value)
}
