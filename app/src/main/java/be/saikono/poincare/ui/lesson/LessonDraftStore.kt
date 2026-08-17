package be.saikono.poincare.ui.lesson

import android.content.Context
import java.nio.charset.StandardCharsets
import java.util.UUID
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class LessonSessionDraft(
    val attemptId: String,
    val profileId: String,
    val unitId: String,
    val lessonId: String,
    val contentVersion: String,
    val currentStepIndex: Int,
    val correctCount: Int,
    val questionCount: Int,
    val startedAtUtc: Long,
    val stageIdV2: String? = null,
    val answerReviews: List<LessonAnswerReview> = emptyList()
)

interface LessonDraftStore {
    fun load(profileId: String, lessonId: String): LessonSessionDraft?
    fun loadStage(profileId: String, lessonId: String, stageId: String): LessonSessionDraft? =
        load(profileId, lessonId)?.takeIf { it.stageIdV2 == stageId }
    fun save(draft: LessonSessionDraft)
    fun clear(profileId: String, lessonId: String)
    fun clearStage(profileId: String, lessonId: String, stageId: String) = clear(profileId, lessonId)
}

internal object LessonDraftCodec {
    private val json = Json { ignoreUnknownKeys = false }

    fun encode(draft: LessonSessionDraft): String = json.encodeToString(draft)

    fun decode(value: String): LessonSessionDraft? = runCatching {
        json.decodeFromString<LessonSessionDraft>(value)
    }.getOrNull()
}

class SharedPreferencesLessonDraftStore(context: Context) : LessonDraftStore {
    private val preferences = context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    override fun load(profileId: String, lessonId: String): LessonSessionDraft? {
        val legacy = preferences.getString(key(profileId, lessonId), null)
            ?.let(LessonDraftCodec::decode)
            ?.takeIf { it.profileId == profileId && it.lessonId == lessonId }
        return legacy ?: preferences.all.values.asSequence()
            .filterIsInstance<String>()
            .mapNotNull(LessonDraftCodec::decode)
            .firstOrNull { it.profileId == profileId && it.lessonId == lessonId }
    }

    override fun loadStage(profileId: String, lessonId: String, stageId: String): LessonSessionDraft? =
        preferences.getString(stageKey(profileId, lessonId, stageId), null)
            ?.let(LessonDraftCodec::decode)
            ?.takeIf { it.profileId == profileId && it.lessonId == lessonId && it.stageIdV2 == stageId }

    override fun save(draft: LessonSessionDraft) {
        preferences.edit()
            .putString(
                draft.stageIdV2?.let { stageKey(draft.profileId, draft.lessonId, it) }
                    ?: key(draft.profileId, draft.lessonId),
                LessonDraftCodec.encode(draft)
            )
            .apply()
    }

    override fun clear(profileId: String, lessonId: String) {
        val matchingKeys = preferences.all.asSequence().mapNotNull { (storedKey, value) ->
            (value as? String)?.let(LessonDraftCodec::decode)
                ?.takeIf { it.profileId == profileId && it.lessonId == lessonId }
                ?.let { storedKey }
        }.toList()
        preferences.edit().apply {
            remove(key(profileId, lessonId))
            matchingKeys.forEach(::remove)
        }.apply()
    }

    override fun clearStage(profileId: String, lessonId: String, stageId: String) {
        preferences.edit().remove(stageKey(profileId, lessonId, stageId)).apply()
    }

    private fun key(profileId: String, lessonId: String): String {
        val source = "$profileId|$lessonId".toByteArray(StandardCharsets.UTF_8)
        return UUID.nameUUIDFromBytes(source).toString()
    }

    private fun stageKey(profileId: String, lessonId: String, stageId: String): String {
        val source = "$profileId|$lessonId|$stageId".toByteArray(StandardCharsets.UTF_8)
        return UUID.nameUUIDFromBytes(source).toString()
    }

    private companion object {
        const val FILE_NAME = "lesson_runtime_drafts_v1"
    }
}
