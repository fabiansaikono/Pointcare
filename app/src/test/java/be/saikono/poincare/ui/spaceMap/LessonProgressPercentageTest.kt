package be.saikono.poincare.ui.spaceMap

import be.saikono.poincare.domain.curriculum.manifest.CurriculumManifestLoader
import be.saikono.poincare.domain.curriculum.v2.LessonCatalogV2Loader
import be.saikono.poincare.model.ChildProfile
import be.saikono.poincare.model.LearningDomain
import be.saikono.poincare.model.SyncStatus
import be.saikono.poincare.model.lesson.LessonMasteryLevel
import be.saikono.poincare.model.lesson.LessonProgress
import be.saikono.poincare.model.lesson.LessonProgressState
import java.io.File
import kotlin.math.roundToInt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LessonProgressPercentageTest {
    @Test
    fun `canonical v2 lessons expose manifest subcategories while awaiting review`() {
        val manifest = sharedManifest
        val catalogV2 = sharedCatalog
        val profile = ChildProfile(
            id = "child-a",
            name = "Child",
            avatarId = "1",
            rocketColorId = "1",
            birthYear = 2018,
            selectedGrades = LearningDomain.entries.associate { it.name to listOf(1) }
        )

        val result = calculateLessonProgressPercentages(
            profile = profile,
            manifest = manifest,
            progress = emptyList(),
            catalogV2 = catalogV2
        )

        assertTrue(1 in result.subcategoryGradeAvailability["math.addition"].orEmpty())
        assertTrue(1 in result.subcategoryGradeAvailability["math.subtraction"].orEmpty())
        assertTrue(1 in result.subcategoryGradeAvailability["math.mixed_calculations"].orEmpty())
        assertTrue(1 in result.subcategoryGradeAvailability["geometry.length_perimeter_scale"].orEmpty())
        assertTrue(1 in result.subcategoryGradeAvailability["language.technical_reading"].orEmpty())
        assertTrue("language.reading_comprehension" in result.subcategoryGradeAvailability)
        assertTrue("math.money_financial_literacy" in result.subcategoryGradeAvailability)
    }

    @Test
    fun `selected grades are averaged instead of weighted by lesson count`() {
        val manifest = sharedManifest
        val catalogV2 = sharedCatalog
        val profile = ChildProfile(
            id = "child-a",
            name = "Child",
            avatarId = "1",
            rocketColorId = "1",
            birthYear = 2018,
            selectedGrades = mapOf(LearningDomain.READING.name to listOf(1, 2)),
            hiddenMainCategories = LearningDomain.entries.filterNot { it == LearningDomain.READING }.map { it.name }
        )
        val readingCategoryIds = manifest.categories
            .filter { it.domain == LearningDomain.READING }
            .mapTo(linkedSetOf()) { it.id }
        val completedGradeOne = catalogV2.lessons
            .asSequence()
            .filter { it.curriculumCategoryId in readingCategoryIds && it.grade == 1 }
            .map { it.unitId to it.id }
            .map { (unitId, lessonId) -> completedProgress(unitId, lessonId) }
            .toList()

        val result = calculateLessonProgressPercentages(profile, manifest, completedGradeOne, catalogV2)

        assertEquals(50, result.byDomain[LearningDomain.READING])
        assertEquals(100, result.byDomainAndGrade[LearningDomain.READING]?.get(1))
        assertEquals(0, result.byDomainAndGrade[LearningDomain.READING]?.get(2))
        assertEquals(50, result.overallPercent)
        assertEquals(setOf(1, 2), result.subcategoryGradeAvailability["language.technical_reading"])
    }

    @Test
    fun `category progress uses canonical manifest domain instead of legacy routing`() {
        val manifest = sharedManifest
        val catalogV2 = sharedCatalog
        val profile = ChildProfile(
            id = "child-a",
            name = "Child",
            avatarId = "1",
            rocketColorId = "1",
            birthYear = 2018,
            selectedGrades = LearningDomain.entries.associate { it.name to listOf(1) }
        )
        val dictation = catalogV2.lessons.single {
            it.id == "lesson.g1.nl.spelling.klankzuivere-woorden-en-eerste-dictee"
        }
        val readingCategoryIds = manifest.categories
            .filter { it.domain == LearningDomain.READING }
            .mapTo(linkedSetOf()) { it.id }
        val readingLessonCount = catalogV2.lessons.count {
            it.grade == 1 && it.curriculumCategoryId in readingCategoryIds
        }

        val result = calculateLessonProgressPercentages(
            profile,
            manifest,
            listOf(completedProgress(dictation.unitId, dictation.id)),
            catalogV2
        )

        assertEquals((100f / readingLessonCount).roundToInt(), result.byDomain[LearningDomain.READING])
        assertEquals(0, result.byDomain[LearningDomain.WRITING])
        assertTrue((result.bySubcategory["language.dictation"] ?: 0) > 0)
    }

    @Test
    fun `hidden subcategory is excluded from visible progress denominators`() {
        val manifest = sharedManifest
        val catalogV2 = sharedCatalog
        val profile = ChildProfile(
            id = "child-a",
            name = "Child",
            avatarId = "1",
            rocketColorId = "1",
            birthYear = 2018,
            selectedGrades = LearningDomain.entries.associate { it.name to listOf(1) },
            hiddenSubcategories = listOf("language.dictation")
        )

        val result = calculateLessonProgressPercentages(profile, manifest, emptyList(), catalogV2)

        assertTrue("language.dictation" !in result.subcategoryGradeAvailability)
        assertTrue("language.dictation" !in result.bySubcategory)
    }

    @Test
    fun `lesson progress includes the canonical grade two catalogue`() {
        val manifest = sharedManifest
        val catalogV2 = sharedCatalog
        val gradeTwoLessons = catalogV2.lessons
            .asSequence()
            .filter { it.legacyRouting.domain.name == LearningDomain.MATH.name && it.grade == 2 }
            .map { it.unitId to it.id }
            .toList()
        val profile = ChildProfile(
            id = "child-a",
            name = "Child",
            avatarId = "1",
            rocketColorId = "1",
            birthYear = 2014,
            selectedGrades = mapOf(LearningDomain.MATH.name to listOf(2)),
            hiddenMainCategories = LearningDomain.entries.filterNot { it == LearningDomain.MATH }.map { it.name }
        )

        val result = calculateLessonProgressPercentages(
            profile,
            manifest,
            gradeTwoLessons.map { (unitId, lessonId) -> completedProgress(unitId, lessonId) },
            catalogV2
        )

        assertEquals(100, result.byDomain[LearningDomain.MATH])
        assertEquals(100, result.overallPercent)
    }

    private fun completedProgress(unitId: String, lessonId: String) = LessonProgress(
        progressId = "progress-$lessonId",
        childProfileId = "child-a",
        unitId = unitId,
        lessonId = lessonId,
        state = LessonProgressState.COMPLETED,
        masteryLevel = LessonMasteryLevel.PROFICIENT,
        masteryPercent = 80,
        bestScorePercent = 80,
        latestScorePercent = 80,
        attemptCount = 1,
        startedAtUtc = 1L,
        completedAtUtc = 2L,
        lastActivityAtUtc = 2L,
        updatedAtUtc = 2L,
        syncId = "sync-$lessonId",
        syncStatus = SyncStatus.PENDING,
        revision = 1L
    )

    companion object {
        private val sharedManifest by lazy {
            assetFile("src/main/assets/${CurriculumManifestLoader.ASSET_PATH}")
                .inputStream().use(CurriculumManifestLoader::fromStream)
        }
        private val sharedCatalog by lazy {
            LessonCatalogV2Loader.fromFile(
                assetFile("src/main/assets/${LessonCatalogV2Loader.ASSET_PATH}")
            )
        }

        private fun assetFile(relativePath: String): File {
            val candidates = listOf(File(relativePath), File("app/$relativePath"))
            return candidates.firstOrNull(File::exists) ?: error("Missing test asset: $relativePath")
        }
    }
}
