package be.saikono.poincare.data.local

import androidx.room.TypeConverter
import be.saikono.poincare.model.LearningDomain
import be.saikono.poincare.model.SkillLevel
import be.saikono.poincare.model.SyncStatus
import be.saikono.poincare.model.lesson.LessonMasteryLevel
import be.saikono.poincare.model.lesson.LessonProgressState
import be.saikono.poincare.domain.curriculum.v2.LessonGateStateV2
import be.saikono.poincare.domain.curriculum.v2.LessonStageTypeV2

class Converters {
    @TypeConverter
    fun fromSkillLevel(value: SkillLevel): String = value.name

    @TypeConverter
    fun toSkillLevel(value: String): SkillLevel = runCatching { SkillLevel.valueOf(value) }.getOrDefault(SkillLevel.NOT_APPLICABLE)

    @TypeConverter
    fun fromLearningDomain(value: LearningDomain): String = value.name

    @TypeConverter
    fun toLearningDomain(value: String): LearningDomain = LearningDomain.valueOf(value)

    @TypeConverter
    fun fromSyncStatus(value: SyncStatus): String = value.name

    @TypeConverter
    fun toSyncStatus(value: String): SyncStatus = SyncStatus.valueOf(value)

    @TypeConverter
    fun fromLessonProgressState(value: LessonProgressState): String = value.name

    @TypeConverter
    fun toLessonProgressState(value: String): LessonProgressState = LessonProgressState.valueOf(value)

    @TypeConverter
    fun fromLessonMasteryLevel(value: LessonMasteryLevel): String = value.name

    @TypeConverter
    fun toLessonMasteryLevel(value: String): LessonMasteryLevel = LessonMasteryLevel.valueOf(value)

    @TypeConverter
    fun fromLessonGateStateV2(value: LessonGateStateV2?): String? = value?.name

    @TypeConverter
    fun toLessonGateStateV2(value: String?): LessonGateStateV2? = value?.let(LessonGateStateV2::valueOf)

    @TypeConverter
    fun fromLessonStageTypeV2(value: LessonStageTypeV2?): String? = value?.name

    @TypeConverter
    fun toLessonStageTypeV2(value: String?): LessonStageTypeV2? = value?.let(LessonStageTypeV2::valueOf)

    @TypeConverter
    fun fromStringList(value: List<String>): String = value.joinToString("\n")

    @TypeConverter
    fun toStringList(value: String): List<String> = value
        .split('\n')
        .map { it.trim() }
        .filter { it.isNotBlank() }

    @TypeConverter
    fun fromSelectedGrades(value: Map<String, List<Int>>): String = value
        .toSortedMap()
        .entries
        .joinToString(";") { (domain, grades) ->
            val normalized = grades.filter { it in 1..6 }.distinct().sorted().joinToString(",")
            "$domain=$normalized"
        }

    @TypeConverter
    fun toSelectedGrades(value: String): Map<String, List<Int>> = value
        .split(';')
        .mapNotNull { entry ->
            val domain = entry.substringBefore('=', missingDelimiterValue = "").trim()
            if (domain.isBlank()) return@mapNotNull null
            val grades = entry.substringAfter('=', missingDelimiterValue = "")
                .split(',')
                .mapNotNull { it.trim().toIntOrNull() }
                .filter { it in 1..6 }
                .distinct()
                .sorted()
            domain to grades
        }
        .toMap()
}
