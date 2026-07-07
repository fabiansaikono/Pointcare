package be.saikono.poincare.model

import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.serialization.Serializable

val LocalAppLanguage = staticCompositionLocalOf { "nl" }

@Serializable
enum class SkillLevel {
    FIRST_GRADE,
    SECOND_GRADE,
    THIRD_GRADE,
    FOURTH_GRADE,
    FIFTH_GRADE,
    SIXTH_GRADE,
    NOT_APPLICABLE
}

@Serializable
enum class LearningDomain {
    MATH,
    READING,
    WRITING,
    PUZZLE,
    WORLD_KNOWLEDGE,
    GEOMETRY
}

@Serializable
enum class SyncStatus {
    PENDING,
    SYNCED,
    FAILED
}

@Serializable
data class ChildProfile(
    val id: String,
    val name: String,
    val avatarId: String,
    val rocketColorId: String,
    val birthYear: Int,
    val appLanguage: String = "nl",
    val exerciseLanguage: String = "nl",
    val supportLanguage: String = "nl",
    val speechLanguage: String = "nl",
    val readingLevel: SkillLevel = SkillLevel.FIRST_GRADE,
    val writingLevel: SkillLevel = SkillLevel.FIRST_GRADE,
    val mathLevel: SkillLevel = SkillLevel.FIRST_GRADE,
    val geometryLevel: SkillLevel = SkillLevel.FIRST_GRADE,
    val worldKnowledgeLevel: SkillLevel = SkillLevel.FIRST_GRADE,
    val puzzleLevel: SkillLevel = SkillLevel.FIRST_GRADE,
    val hiddenMainCategories: List<String> = emptyList(),
    val hiddenSubcategories: List<String> = emptyList(),
    val showUnassignedExercises: Boolean = false,
    val giftedModeEnabled: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

fun ChildProfile.isMainCategoryEnabled(domain: LearningDomain): Boolean = domain.name !in hiddenMainCategories
fun ChildProfile.isSubcategoryEnabled(subcategoryId: String): Boolean = subcategoryId !in hiddenSubcategories

fun formatVisibleNumbers(value: String): String = value
    .replace(GENERATED_EURO_WHOLE_NUMBER_REGEX) { match ->
        "€ ${match.groupValues[1].toIntOrNull()?.toString() ?: match.groupValues[1]}"
    }
    .replace(EURO_SPACING_REGEX, "€ ")
    .replace(GENERATED_WHOLE_NUMBER_REGEX) { match ->
        match.groupValues[1].toIntOrNull()?.toString() ?: match.value
    }
    .replace(LARGE_WHOLE_NUMBER_REGEX) { match ->
        match.value.reversed().chunked(3).joinToString(" ").reversed()
    }

fun isDisplayNumber(value: String): Boolean {
    val trimmed = value.trim()
    return trimmed.isNotEmpty() && trimmed.all { it.isDigit() || it == '.' || it == ',' || it == '-' || it == ' ' }
}

fun normalizeAnswer(value: String): String {
    val normalized = formatVisibleNumbers(value).trim().lowercase().replace("\\s+".toRegex(), " ")
    return if (normalized.any { it.isDigit() }) {
        normalized.replace(".", "").replace(" ", "").replace(',', '.')
    } else {
        normalized
    }
}

private val GENERATED_EURO_WHOLE_NUMBER_REGEX = Regex("€\\s*(\\d+)\\.000(?:,0)?")
private val EURO_SPACING_REGEX = Regex("€\\s*")
private val GENERATED_WHOLE_NUMBER_REGEX = Regex("(?<![\\d.,])(\\d+)\\.000(?:,0)?(?![\\d.,])")
private val LARGE_WHOLE_NUMBER_REGEX = Regex("(?<![\\d.,])\\d{4,}(?![\\d.,])")

@Serializable
data class LevelProgress(
    val level: Int,
    val totalPoints: Int,
    val currentLevelPoints: Int,
    val pointsNeededForNextLevel: Int,
    val progressFraction: Float
)

fun pointsNeededForLevel(level: Int): Int = when {
    level <= 0 -> 100
    level == 1 -> 100
    else -> 100 + level * 5
}

fun levelProgressFor(totalPoints: Int): LevelProgress {
    var level = 0
    var remaining = totalPoints.coerceAtLeast(0)
    while (remaining >= pointsNeededForLevel(level + 1)) {
        level += 1
        remaining -= pointsNeededForLevel(level)
    }
    val needed = pointsNeededForLevel(level + 1)
    return LevelProgress(
        level = level,
        totalPoints = totalPoints.coerceAtLeast(0),
        currentLevelPoints = remaining,
        pointsNeededForNextLevel = needed,
        progressFraction = if (needed == 0) 0f else remaining.toFloat() / needed.toFloat()
    )
}

fun missionPointsFor(exercise: ExerciseContent, correct: Boolean): Int {
    if (!correct) return 1
    val difficulty = exercise.data["difficultyStars"]?.toIntOrNull()?.coerceIn(1, 3) ?: 1
    val typeBonus = when (exercise.inputMode) {
        InputMode.MATH_CROSSWORD,
        InputMode.SUDOKU_GRID,
        InputMode.CALCULATOR_PUZZLE -> 18
        InputMode.DRAG_DROP_DIGITS,
        InputMode.DRAG_DROP_LETTERS,
        InputMode.TRACE_LETTER -> 10
        InputMode.CUSTOM_NUMBER_PAD -> 8
        InputMode.TAP_CHOICE,
        InputMode.LISTEN_AND_SELECT -> 5
        else -> 6
    }
    return (difficulty * 10 + typeBonus).coerceIn(1, 50)
}

@Serializable
data class ProgressEvent(
    val id: String,
    val childProfileId: String,
    val exerciseId: String,
    val domain: LearningDomain,
    val skill: String,
    val timestamp: Long,
    val answerCorrect: Boolean,
    val responseTimeMs: Long,
    val hintsUsed: Int = 0,
    val retryCount: Int = 0,
    val languageSwitches: Int = 0,
    val translationsUsed: Int = 0,
    val rewardStampsEarned: Int = 0,
    val syncStatus: SyncStatus = SyncStatus.PENDING,
    val metadata: Map<String, String> = emptyMap()
)

@Serializable
data class ParentReward(
    val id: String,
    val childProfileId: String,
    val tier: Int,
    val label: Map<String, String>,
    val stampsRequired: Int,
    val currentStamps: Int = 0,
    val isClaimed: Boolean = false,
    val requiresParentApproval: Boolean = true
)

@Serializable
data class StampCard(
    val id: String,
    val childProfileId: String,
    val totalStamps: Int,
    val lastUpdated: Long = System.currentTimeMillis()
)

@Serializable
data class AnimationSettings(
    val animationsEnabled: Boolean = true,
    val reducedMotionEnabled: Boolean = false,
    val soundEffectsEnabled: Boolean = true,
    val soundEffectsVolume: Float = 0.8f,
    val voiceVolume: Float = 1.0f,
    val hapticFeedbackEnabled: Boolean = true,
    val unlockAnimationsEnabled: Boolean = true,
    val maxAnimationIntensity: Float = 1.0f
)
