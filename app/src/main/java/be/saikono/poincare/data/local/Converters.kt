package be.saikono.poincare.data.local

import androidx.room.TypeConverter
import be.saikono.poincare.model.LearningDomain
import be.saikono.poincare.model.SkillLevel
import be.saikono.poincare.model.SyncStatus

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
    fun fromStringList(value: List<String>): String = value.joinToString("\n")

    @TypeConverter
    fun toStringList(value: String): List<String> = value
        .split('\n')
        .map { it.trim() }
        .filter { it.isNotBlank() }
}
