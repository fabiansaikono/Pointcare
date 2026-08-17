package be.saikono.poincare.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import be.saikono.poincare.data.story.StoryProgress
import be.saikono.poincare.data.story.normalizedStoryLocale
import be.saikono.poincare.model.AnimationSettings
import be.saikono.poincare.model.LearningDomain
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

const val DEFAULT_LESSON_MASTERY_THRESHOLD_PERCENT = 75
private const val MAX_TEACHER_ID_LENGTH = 64

internal fun normalizedTeacherId(value: String): String =
    value.trim().take(MAX_TEACHER_ID_LENGTH)

internal fun normalizedLessonMasteryThresholdPercent(value: Int): Int =
    (((value.coerceIn(50, 100) + 2) / 5) * 5).coerceIn(50, 100)

data class LessonMasteryThresholdSettings(
    val defaultPercent: Int = DEFAULT_LESSON_MASTERY_THRESHOLD_PERCENT,
    val overrides: Map<LearningDomain, Int> = emptyMap()
) {
    fun effectivePercent(domain: LearningDomain): Int = overrides[domain] ?: defaultPercent
}

class SettingsDataStore(private val context: Context) {
    private val json = Json { ignoreUnknownKeys = false }

    private object Keys {
        val SELECTED_PROFILE_ID = stringPreferencesKey("selected_profile_id")
        val PARENT_PIN = stringPreferencesKey("parent_pin")
        val ANIMATIONS_ENABLED = booleanPreferencesKey("animations_enabled")
        val REDUCED_MOTION = booleanPreferencesKey("reduced_motion")
        val SOUND_EFFECTS_ENABLED = booleanPreferencesKey("sound_effects_enabled")
        val SOUND_EFFECTS_VOLUME = floatPreferencesKey("sound_effects_volume")
        val VOICE_VOLUME = floatPreferencesKey("voice_volume")
        val HAPTIC_FEEDBACK_ENABLED = booleanPreferencesKey("haptic_feedback_enabled")
        val UNLOCK_ANIMATIONS_ENABLED = booleanPreferencesKey("unlock_animations_enabled")
        val ANIMATION_INTENSITY = floatPreferencesKey("animation_intensity")
        val APP_LANGUAGE = stringPreferencesKey("app_language")
        
        fun voiceIdKey(profileId: String, locale: String) = stringPreferencesKey("voice_id_${profileId}_$locale")
        fun pitchKey(profileId: String, locale: String) = floatPreferencesKey("pitch_${profileId}_$locale")
        fun rateKey(profileId: String, locale: String) = floatPreferencesKey("rate_${profileId}_$locale")
        fun storyProgressKey(profileId: String, storyId: String, locale: String) =
            stringPreferencesKey("story_progress_${profileId}_${storyId}_${locale.normalizedStoryLocale()}")
        fun lessonMasteryThresholdKey(profileId: String) =
            intPreferencesKey("lesson_mastery_threshold_percent_$profileId")
        fun lessonMasteryThresholdOverrideKey(profileId: String, domain: LearningDomain) =
            intPreferencesKey("lesson_mastery_threshold_percent_${profileId}_${domain.name.lowercase()}")
        fun teacherIdKey(profileId: String) = stringPreferencesKey("teacher_id_$profileId")
    }

    val appLanguage: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[Keys.APP_LANGUAGE]
    }

    suspend fun setAppLanguage(language: String?) {
        context.dataStore.edit { preferences ->
            if (language == null) preferences.remove(Keys.APP_LANGUAGE)
            else preferences[Keys.APP_LANGUAGE] = language
        }
    }

    val selectedProfileId: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[Keys.SELECTED_PROFILE_ID]
    }

    val parentPin: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[Keys.PARENT_PIN]
    }

    val animationSettings: Flow<AnimationSettings> = context.dataStore.data.map { preferences ->
        AnimationSettings(
            animationsEnabled = preferences[Keys.ANIMATIONS_ENABLED] ?: true,
            reducedMotionEnabled = preferences[Keys.REDUCED_MOTION] ?: false,
            soundEffectsEnabled = preferences[Keys.SOUND_EFFECTS_ENABLED] ?: true,
            soundEffectsVolume = preferences[Keys.SOUND_EFFECTS_VOLUME] ?: 0.8f,
            voiceVolume = preferences[Keys.VOICE_VOLUME] ?: 1.0f,
            hapticFeedbackEnabled = preferences[Keys.HAPTIC_FEEDBACK_ENABLED] ?: true,
            unlockAnimationsEnabled = preferences[Keys.UNLOCK_ANIMATIONS_ENABLED] ?: true,
            maxAnimationIntensity = preferences[Keys.ANIMATION_INTENSITY] ?: 1.0f
        )
    }

    suspend fun setSelectedProfileId(id: String?) {
        context.dataStore.edit { preferences ->
            if (id == null) preferences.remove(Keys.SELECTED_PROFILE_ID)
            else preferences[Keys.SELECTED_PROFILE_ID] = id
        }
    }

    suspend fun setParentPin(pin: String?) {
        context.dataStore.edit { preferences ->
            if (pin == null) preferences.remove(Keys.PARENT_PIN)
            else preferences[Keys.PARENT_PIN] = pin
        }
    }

    suspend fun updateAnimationSettings(settings: AnimationSettings) {
        context.dataStore.edit { preferences ->
            preferences[Keys.ANIMATIONS_ENABLED] = settings.animationsEnabled
            preferences[Keys.REDUCED_MOTION] = settings.reducedMotionEnabled
            preferences[Keys.SOUND_EFFECTS_ENABLED] = settings.soundEffectsEnabled
            preferences[Keys.SOUND_EFFECTS_VOLUME] = settings.soundEffectsVolume.coerceIn(0f, 1f)
            preferences[Keys.VOICE_VOLUME] = settings.voiceVolume.coerceIn(0f, 1f)
            preferences[Keys.HAPTIC_FEEDBACK_ENABLED] = settings.hapticFeedbackEnabled
            preferences[Keys.UNLOCK_ANIMATIONS_ENABLED] = settings.unlockAnimationsEnabled
            preferences[Keys.ANIMATION_INTENSITY] = settings.maxAnimationIntensity
        }
    }

    fun getVoiceSettings(profileId: String, locale: String): Flow<VoiceSettings> = context.dataStore.data.map { preferences ->
        VoiceSettings(
            voiceId = preferences[Keys.voiceIdKey(profileId, locale)],
            pitch = preferences[Keys.pitchKey(profileId, locale)] ?: 1.1f,
            rate = preferences[Keys.rateKey(profileId, locale)] ?: 1.1f
        )
    }

    suspend fun setVoiceSettings(profileId: String, locale: String, settings: VoiceSettings) {
        context.dataStore.edit { preferences ->
            if (settings.voiceId == null) preferences.remove(Keys.voiceIdKey(profileId, locale))
            else preferences[Keys.voiceIdKey(profileId, locale)] = settings.voiceId
            preferences[Keys.pitchKey(profileId, locale)] = settings.pitch
            preferences[Keys.rateKey(profileId, locale)] = settings.rate
        }
    }

    fun getTeacherId(profileId: String): Flow<String> = context.dataStore.data.map { preferences ->
        preferences[Keys.teacherIdKey(profileId)].orEmpty()
    }

    suspend fun setTeacherId(profileId: String, teacherId: String) {
        val normalized = normalizedTeacherId(teacherId)
        context.dataStore.edit { preferences ->
            val key = Keys.teacherIdKey(profileId)
            if (normalized.isBlank()) preferences.remove(key)
            else preferences[key] = normalized
        }
    }

    fun getStoryProgress(profileId: String, storyId: String, locale: String): Flow<StoryProgress> {
        val normalizedLocale = locale.normalizedStoryLocale()
        val key = Keys.storyProgressKey(profileId, storyId, normalizedLocale)
        return context.dataStore.data.map { preferences ->
            preferences[key]?.let { encoded ->
                runCatching { json.decodeFromString<StoryProgress>(encoded) }.getOrNull()
            } ?: StoryProgress(profileId = profileId, storyId = storyId, locale = normalizedLocale)
        }
    }

    suspend fun setStoryProgress(progress: StoryProgress) {
        val normalized = progress.copy(locale = progress.locale.normalizedStoryLocale())
        context.dataStore.edit { preferences ->
            preferences[Keys.storyProgressKey(normalized.profileId, normalized.storyId, normalized.locale)] =
                json.encodeToString(normalized)
        }
    }

    fun getLessonMasteryThresholdPercent(profileId: String): Flow<Int> =
        getLessonMasteryThresholdSettings(profileId).map { it.defaultPercent }

    fun getLessonMasteryThresholdSettings(profileId: String): Flow<LessonMasteryThresholdSettings> =
        context.dataStore.data.map { preferences ->
            val defaultPercent = normalizedLessonMasteryThresholdPercent(
                preferences[Keys.lessonMasteryThresholdKey(profileId)]
                    ?: DEFAULT_LESSON_MASTERY_THRESHOLD_PERCENT
            )
            val overrides = LearningDomain.entries.mapNotNull { domain ->
                preferences[Keys.lessonMasteryThresholdOverrideKey(profileId, domain)]?.let { value ->
                    domain to normalizedLessonMasteryThresholdPercent(value)
                }
            }.toMap()
            LessonMasteryThresholdSettings(
                defaultPercent = defaultPercent,
                overrides = overrides
            )
        }

    fun getEffectiveLessonMasteryThresholdPercent(
        profileId: String,
        domain: LearningDomain
    ): Flow<Int> = getLessonMasteryThresholdSettings(profileId).map { it.effectivePercent(domain) }

    suspend fun setLessonMasteryThresholdPercent(profileId: String, percent: Int) {
        context.dataStore.edit { preferences ->
            preferences[Keys.lessonMasteryThresholdKey(profileId)] =
                normalizedLessonMasteryThresholdPercent(percent)
        }
    }

    suspend fun setLessonMasteryThresholdOverride(
        profileId: String,
        domain: LearningDomain,
        percent: Int?
    ) {
        context.dataStore.edit { preferences ->
            val key = Keys.lessonMasteryThresholdOverrideKey(profileId, domain)
            if (percent == null) {
                preferences.remove(key)
            } else {
                preferences[key] = normalizedLessonMasteryThresholdPercent(percent)
            }
        }
    }
}

data class VoiceSettings(
    val voiceId: String?,
    val pitch: Float,
    val rate: Float
)
