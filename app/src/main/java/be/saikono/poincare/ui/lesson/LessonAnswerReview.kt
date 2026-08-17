package be.saikono.poincare.ui.lesson

import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class LessonAnswerReview(
    val questionNumber: Int,
    val exerciseId: String,
    val question: String,
    val givenAnswer: String,
    val correctAnswer: String,
    val correct: Boolean
)

internal object LessonAnswerReviewCodec {
    const val METADATA_KEY: String = "answerReviewV1"

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun encode(answers: List<LessonAnswerReview>): String = json.encodeToString(answers)

    fun decode(value: String?): List<LessonAnswerReview> {
        if (value.isNullOrBlank()) return emptyList()
        return runCatching { json.decodeFromString<List<LessonAnswerReview>>(value) }
            .getOrDefault(emptyList())
    }
}
