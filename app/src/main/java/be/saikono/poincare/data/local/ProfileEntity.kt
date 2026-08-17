package be.saikono.poincare.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import be.saikono.poincare.model.SkillLevel

@Entity(tableName = "profiles")
data class ProfileEntity(
    @PrimaryKey val id: String,
    val name: String,
    val avatarId: String,
    val rocketColorId: String,
    val birthYear: Int,
    val appLanguage: String,
    val exerciseLanguage: String,
    val supportLanguage: String,
    val speechLanguage: String,
    val readingLevel: SkillLevel,
    val writingLevel: SkillLevel,
    val mathLevel: SkillLevel,
    val geometryLevel: SkillLevel,
    val worldKnowledgeLevel: SkillLevel,
    val puzzleLevel: SkillLevel,
    val selectedGrades: Map<String, List<Int>>,
    val hiddenMainCategories: List<String>,
    val hiddenSubcategories: List<String>,
    val showUnassignedExercises: Boolean,
    val giftedModeEnabled: Boolean,
    val createdAt: Long,
    val updatedAt: Long
)
