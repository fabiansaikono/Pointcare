package be.saikono.poincare.domain.curriculum.manifest

import be.saikono.poincare.domain.curriculum.CurriculumCatalog
import be.saikono.poincare.domain.curriculum.CurriculumRequirement
import be.saikono.poincare.domain.curriculum.ParentSupportLevel
import be.saikono.poincare.model.LearningDomain
import be.saikono.poincare.model.LocalizedText
import kotlinx.serialization.Serializable

const val CURRENT_MANIFEST_SCHEMA_VERSION: Int = 1

@Serializable
data class CurriculumManifest(
    val schemaVersion: Int = CURRENT_MANIFEST_SCHEMA_VERSION,
    val definitionVersion: Int = 1,
    val scope: CurriculumManifestScope,
    val source: CurriculumManifestSource,
    val categories: List<CurriculumCategoryManifest>,
    val structureRevisions: List<CurriculumStructureRevisionManifest> = emptyList(),
    val pilotUnitIds: List<String> = emptyList(),
    val roadmapEntries: List<CurriculumRoadmapEntry> = emptyList(),
    val lessonCatalog: CurriculumCatalog
)

@Serializable
data class CurriculumManifestSource(
    val id: String,
    val version: Int,
    val workbookSha256: String,
    val sheet: String,
    val title: String,
    val roadmapSheet: String = "",
    val roadmapTitle: String = ""
)

@Serializable
data class CurriculumCategoryManifest(
    val id: String,
    val domain: LearningDomain,
    val title: LocalizedText,
    val subcategories: List<CurriculumSubcategoryManifest>,
    val iconUrl: String? = null,
    val iconAssetPath: String? = null,
    val imageGenerationTheme: String? = null,
    val definitionVersion: Int = 1
)

@Serializable
data class CurriculumSubcategoryManifest(
    val id: String,
    val title: LocalizedText,
    val summary: LocalizedText,
    val grades: List<Int>,
    val requirement: CurriculumRequirement,
    val deliveryMode: CurriculumDeliveryMode,
    val parentSupport: ParentSupportLevel,
    val legacySubcategoryIds: List<String> = emptyList(),
    val tags: List<String> = emptyList(),
    val gradeSequences: List<CurriculumGradeSequenceManifest> = emptyList(),
    val iconUrl: String? = null,
    val iconAssetPath: String? = null,
    val imageGenerationTheme: String? = null,
    val definitionVersion: Int = 1
)

@Serializable
data class CurriculumGradeSequenceManifest(
    val grade: Int,
    val sequence: Int
)

@Serializable
data class CurriculumStructureRevisionManifest(
    val id: String,
    val grade: Int,
    val sourceFile: String,
    val sourceSheet: String,
    val sourceWorkbookSha256: String,
    val duplicateSourceRows: List<List<Int>> = emptyList(),
    val summaryOverrideLessonIds: List<String> = emptyList()
)

@Serializable
data class CurriculumRoadmapEntry(
    val id: String,
    val grade: Int,
    val categoryId: String? = null,
    val subcategoryIds: List<String> = emptyList(),
    val unitId: String? = null,
    val title: LocalizedText,
    val learningGoal: LocalizedText,
    val sourceLearningGoalNl: String,
    val deliveryMode: CurriculumDeliveryMode,
    val requirement: CurriculumRequirement,
    val parentSupport: ParentSupportLevel,
    val priority: CurriculumPriority,
    val coverage: String,
    val decision: String,
    val appAction: LocalizedText,
    val adultAction: LocalizedText,
    val sourceReference: String,
    val definitionVersion: Int = 1
)

@Serializable
enum class CurriculumManifestScope {
    PILOT,
    FULL
}

@Serializable
enum class CurriculumDeliveryMode {
    IN_APP_CORE,
    HYBRID_APP_PARENT,
    OPTIONAL_IN_APP,
    PARENT_TEACHER_RESPONSIBILITY
}

@Serializable
enum class CurriculumPriority {
    HIGH,
    MEDIUM,
    LOW
}

class CurriculumManifestIndex private constructor(
    val manifest: CurriculumManifest,
    private val categoriesById: Map<String, CurriculumCategoryManifest>,
    private val subcategoriesById: Map<String, CurriculumSubcategoryManifest>,
    private val categoryBySubcategoryId: Map<String, CurriculumCategoryManifest>,
    private val roadmapEntriesById: Map<String, CurriculumRoadmapEntry>,
    private val roadmapEntryByUnitId: Map<String, CurriculumRoadmapEntry>
) {
    fun category(categoryId: String): CurriculumCategoryManifest? = categoriesById[categoryId]

    fun subcategory(subcategoryId: String): CurriculumSubcategoryManifest? = subcategoriesById[subcategoryId]

    fun categoryForSubcategory(subcategoryId: String): CurriculumCategoryManifest? =
        categoryBySubcategoryId[subcategoryId]

    fun roadmapEntry(roadmapEntryId: String): CurriculumRoadmapEntry? =
        roadmapEntriesById[roadmapEntryId]

    fun roadmapEntryForUnit(unitId: String): CurriculumRoadmapEntry? =
        roadmapEntryByUnitId[unitId]

    fun roadmapEntriesForGrade(grade: Int): List<CurriculumRoadmapEntry> =
        manifest.roadmapEntries.filter { it.grade == grade }

    fun subcategoriesForGrade(grade: Int): List<CurriculumSubcategoryManifest> = manifest.categories
        .flatMap { it.subcategories }
        .filter { grade in it.grades }

    fun resolveLegacySubcategory(
        domain: LearningDomain,
        legacySubcategoryId: String
    ): CurriculumSubcategoryManifest? = manifest.categories
        .asSequence()
        .filter { it.domain == domain }
        .flatMap { it.subcategories.asSequence() }
        .firstOrNull { legacySubcategoryId == it.id || legacySubcategoryId in it.legacySubcategoryIds }

    companion object {
        fun create(manifest: CurriculumManifest): CurriculumManifestIndex {
            val categoriesById = manifest.categories.associateBy { it.id }
            val subcategoriesById = linkedMapOf<String, CurriculumSubcategoryManifest>()
            val categoryBySubcategoryId = linkedMapOf<String, CurriculumCategoryManifest>()
            manifest.categories.forEach { category ->
                category.subcategories.forEach { subcategory ->
                    subcategoriesById[subcategory.id] = subcategory
                    categoryBySubcategoryId[subcategory.id] = category
                }
            }
            val roadmapEntriesById = manifest.roadmapEntries.associateBy { it.id }
            val roadmapEntryByUnitId = manifest.roadmapEntries
                .mapNotNull { entry -> entry.unitId?.let { it to entry } }
                .toMap()
            return CurriculumManifestIndex(
                manifest = manifest,
                categoriesById = categoriesById,
                subcategoriesById = subcategoriesById,
                categoryBySubcategoryId = categoryBySubcategoryId,
                roadmapEntriesById = roadmapEntriesById,
                roadmapEntryByUnitId = roadmapEntryByUnitId
            )
        }
    }
}
