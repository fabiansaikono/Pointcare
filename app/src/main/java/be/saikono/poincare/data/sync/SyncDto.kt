package be.saikono.poincare.data.sync

import be.saikono.poincare.model.*
import kotlinx.serialization.Serializable

@Serializable
data class ProgressSyncDto(
    val eventId: String,
    val childProfileId: String,
    val exerciseId: String,
    val domain: String,
    val skill: String,
    val timestampUtc: Long,
    val isCorrect: Boolean,
    val durationMs: Long,
    val hintsUsed: Int,
    val rewardStamps: Int,
    val metadata: Map<String, String>
)

@Serializable
data class ProfileSyncDto(
    val id: String,
    val name: String,
    val levels: Map<String, String>,
    val hiddenMainCategories: List<String>,
    val hiddenSubcategories: List<String>,
    val giftedMode: Boolean,
    val updatedAtUtc: Long
)

// Mapper functions
fun ProgressEvent.toDto() = ProgressSyncDto(
    eventId = id,
    childProfileId = childProfileId,
    exerciseId = exerciseId,
    domain = domain.name,
    skill = skill,
    timestampUtc = timestamp,
    isCorrect = answerCorrect,
    durationMs = responseTimeMs,
    hintsUsed = hintsUsed,
    rewardStamps = rewardStampsEarned,
    metadata = metadata
)

fun ChildProfile.toDto() = ProfileSyncDto(
    id = id,
    name = name,
    levels = mapOf(
        "reading" to readingLevel.name,
        "writing" to writingLevel.name,
        "math" to mathLevel.name,
        "puzzle" to puzzleLevel.name
    ),
    hiddenMainCategories = hiddenMainCategories,
    hiddenSubcategories = hiddenSubcategories,
    giftedMode = giftedModeEnabled,
    updatedAtUtc = updatedAt
)
