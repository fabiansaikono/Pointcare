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
    /** Explicit multi-grade selections per domain. Empty entries fall back to the legacy single level. */
    val selectedGrades: Map<String, List<Int>> = emptyMap(),
    val hiddenMainCategories: List<String> = emptyList(),
    val hiddenSubcategories: List<String> = emptyList(),
    val showUnassignedExercises: Boolean = false,
    val giftedModeEnabled: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

fun ChildProfile.isMainCategoryEnabled(domain: LearningDomain): Boolean =
    domain == LearningDomain.PUZZLE || domain.name !in hiddenMainCategories
fun ChildProfile.isSubcategoryEnabled(subcategoryId: String): Boolean = subcategoryId !in hiddenSubcategories

fun SkillLevel.gradeNumber(): Int? = if (this == SkillLevel.NOT_APPLICABLE) null else ordinal + 1

fun skillLevelForGrade(grade: Int): SkillLevel = SkillLevel.entries
    .getOrNull(grade.coerceIn(1, 6) - 1)
    ?: SkillLevel.FIRST_GRADE

private fun ChildProfile.legacyLevelFor(domain: LearningDomain): SkillLevel = when (domain) {
    LearningDomain.READING -> readingLevel
    LearningDomain.WRITING -> writingLevel
    LearningDomain.MATH -> mathLevel
    LearningDomain.PUZZLE -> puzzleLevel
    LearningDomain.WORLD_KNOWLEDGE -> worldKnowledgeLevel
    LearningDomain.GEOMETRY -> geometryLevel
}

fun ChildProfile.selectedGradesFor(domain: LearningDomain): List<Int> {
    val explicit = selectedGrades[domain.name]
        .orEmpty()
        .filter { it in 1..6 }
        .distinct()
        .sorted()
    if (explicit.isNotEmpty()) return explicit
    return listOfNotNull(legacyLevelFor(domain).gradeNumber())
}

fun puzzleGradeSourceDomain(subcategoryId: String?): LearningDomain = when (subcategoryId) {
    "sudoku", "math-crosswords", "calculator-puzzle" -> LearningDomain.MATH
    else -> LearningDomain.WORLD_KNOWLEDGE
}

fun ChildProfile.selectedGradesForPuzzle(subcategoryId: String?): List<Int> =
    selectedGradesFor(puzzleGradeSourceDomain(subcategoryId))

fun ChildProfile.withSelectedGrades(domain: LearningDomain, grades: Collection<Int>): ChildProfile {
    val normalized = grades.filter { it in 1..6 }.distinct().sorted().ifEmpty {
        listOf(legacyLevelFor(domain).gradeNumber() ?: 1)
    }
    val updatedSelections = selectedGrades + (domain.name to normalized)
    val primaryLevel = skillLevelForGrade(normalized.first())
    return when (domain) {
        LearningDomain.READING -> copy(selectedGrades = updatedSelections, readingLevel = primaryLevel)
        LearningDomain.WRITING -> copy(selectedGrades = updatedSelections, writingLevel = primaryLevel)
        LearningDomain.MATH -> copy(selectedGrades = updatedSelections, mathLevel = primaryLevel)
        LearningDomain.PUZZLE -> copy(selectedGrades = updatedSelections, puzzleLevel = primaryLevel)
        LearningDomain.WORLD_KNOWLEDGE -> copy(selectedGrades = updatedSelections, worldKnowledgeLevel = primaryLevel)
        LearningDomain.GEOMETRY -> copy(selectedGrades = updatedSelections, geometryLevel = primaryLevel)
    }
}

/**
 * Hard Mode deliberately keeps the serialized [giftedModeEnabled] field for
 * backwards-compatible profile storage while changing its user-facing meaning.
 */
val ChildProfile.hardModeEnabled: Boolean
    get() = giftedModeEnabled

/**
 * Returns the generation difficulty while keeping the visible tier unchanged.
 * Normal mode uses levels 1..3. Hard Mode maps visible 1/2/3 stars to
 * generation levels 2/3/4 respectively.
 */
fun effectiveGeneratedDifficulty(displayedStars: Int, hardModeEnabled: Boolean): Int {
    val visible = displayedStars.coerceIn(1, 3)
    return if (hardModeEnabled) visible + 1 else visible
}

fun ChildProfile.effectiveGeneratedDifficulty(displayedStars: Int): Int =
    effectiveGeneratedDifficulty(displayedStars, hardModeEnabled)

fun formatVisibleNumbers(value: String): String = value
    .replace(VISIBLE_NUMBER_TOKEN_REGEX) { match -> formatVisibleNumberToken(match.value) }
    .replace(EURO_SPACING_REGEX, "€ ")

private fun formatVisibleNumberToken(raw: String): String {
    val sign = when {
        raw.startsWith("−") -> "−"
        raw.startsWith("-") -> "-"
        else -> ""
    }
    val unsigned = raw.removePrefix("−").removePrefix("-")
    val compact = unsigned.replace(" ", "")

    // Preserve time/date components such as 08:05 instead of turning them into 8:5.
    if (!compact.contains('.') && !compact.contains(',') && compact.length > 1 && compact.startsWith('0')) {
        return sign + compact
    }

    val integerPart: String
    val decimalPart: String?
    when {
        compact.contains(',') -> {
            integerPart = compact.substringBefore(',').replace(".", "")
            decimalPart = compact.substringAfter(',', "").takeIf { it.isNotEmpty() }
        }
        compact.count { it == '.' } > 1 -> {
            integerPart = compact.replace(".", "")
            decimalPart = null
        }
        compact.contains('.') -> {
            val before = compact.substringBefore('.')
            val after = compact.substringAfter('.')
            val dotIsThousandsSeparator = after.length == 3 && before.isNotEmpty() && before != "0"
            if (dotIsThousandsSeparator) {
                integerPart = before + after
                decimalPart = null
            } else {
                integerPart = before
                decimalPart = after.takeIf { it.isNotEmpty() }
            }
        }
        else -> {
            integerPart = compact
            decimalPart = null
        }
    }

    val normalizedInteger = integerPart.ifBlank { "0" }
    val groupedInteger = normalizedInteger
        .reversed()
        .chunked(3)
        .joinToString(" ")
        .reversed()
    return buildString {
        append(sign)
        append(groupedInteger)
        decimalPart?.let {
            append(',')
            append(it)
        }
    }
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

private val EURO_SPACING_REGEX = Regex("€\\s*")
private val VISIBLE_NUMBER_TOKEN_REGEX = Regex(
    "(?<![\\p{L}\\d])([−-]?(?:\\d{1,3}(?:[ .]\\d{3})+|\\d+)(?:[.,]\\d+)?)(?![\\p{L}\\d])"
)

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
    if (!correct) return 0
    val difficulty = exercise.data["difficultyStars"]?.toIntOrNull()?.coerceIn(1, 3) ?: 1
    val grade = exercise.data["grade"]?.toIntOrNull()
        ?: exercise.level.gradeNumber()
        ?: 1
    val complexity = exercise.data["complexityWeight"]?.toIntOrNull()?.coerceIn(0, 3) ?: when (exercise.inputMode) {
        InputMode.TAP_CHOICE,
        InputMode.LISTEN_AND_SELECT -> 0
        InputMode.CUSTOM_NUMBER_PAD -> 1
        InputMode.DRAG_DROP_DIGITS,
        InputMode.DRAG_DROP_LETTERS,
        InputMode.TRACE_LETTER,
        InputMode.CUSTOM_TEXT_KEYBOARD -> 2
        InputMode.MATH_CROSSWORD,
        InputMode.SUDOKU_GRID,
        InputMode.CALCULATOR_PUZZLE,
        InputMode.WORD_SEEKER -> 3
        else -> 1
    }
    val gradeBand = ((grade.coerceIn(1, 6) - 1) / 2).coerceIn(0, 2)
    return (1 + (difficulty - 1) + gradeBand + complexity)
        .coerceIn(1, MAX_LEVEL_POINTS_PER_ANSWER)
}

const val MAX_LEVEL_POINTS_PER_ANSWER: Int = 8

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

fun ProgressEvent.levelPointsEarned(): Int =
    if (answerCorrect) rewardStampsEarned.coerceIn(0, MAX_LEVEL_POINTS_PER_ANSWER) else 0

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
