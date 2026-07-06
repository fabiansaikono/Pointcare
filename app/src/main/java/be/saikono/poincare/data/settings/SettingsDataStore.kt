package be.saikono.poincare.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import be.saikono.poincare.model.AnimationSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsDataStore(private val context: Context) {
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
}

data class VoiceSettings(
    val voiceId: String?,
    val pitch: Float,
    val rate: Float
)
