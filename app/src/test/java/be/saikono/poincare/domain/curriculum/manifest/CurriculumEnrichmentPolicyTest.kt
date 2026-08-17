package be.saikono.poincare.domain.curriculum.manifest

import be.saikono.poincare.domain.curriculum.CurriculumValidationMode
import be.saikono.poincare.domain.curriculum.LessonActivitySource
import be.saikono.poincare.domain.curriculum.LessonSectionType
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CurriculumEnrichmentPolicyTest {
    private val manifest by lazy(::manifest)

    @Test
    fun `every reviewed unit receives a complete reusable learning cycle`() {
        val units = manifest.lessonCatalog.units
        val lessons = units.flatMap { it.lessons }
        val sections = lessons.flatMap { it.sections }

        assertEquals(CurriculumEnrichmentPolicy.EXPECTED_UNIT_COUNT, units.size)
        assertEquals(Grade1HundredHourPolicy.EXPECTED_TOTAL_LESSON_COUNT, lessons.size)
        assertEquals(
            Grade1HundredHourPolicy.EXPECTED_TOTAL_GENERATED_SOURCE_COUNT,
            sections.count { it.activitySource is LessonActivitySource.Generated }
        )
        assertEquals(
            Grade1HundredHourPolicy.EXPECTED_TOTAL_PRACTICAL_SOURCE_COUNT,
            sections.count { it.activitySource is LessonActivitySource.Practical }
        )
        assertEquals(
            Grade1HundredHourPolicy.EXPECTED_TOTAL_ESTIMATED_MINUTES,
            lessons.sumOf { it.estimatedMinutes ?: 0 }
        )

        units.filterNot { it.id == Grade1MandatoryWritingPolicy.UNIT_ID }.forEach { unit ->
            assertEquals(1, unit.lessons.count {
                CurriculumEnrichmentPolicy.REMEDIATION_TAG in it.tags
            })
            assertEquals(1, unit.lessons.count { "retrieval-practice" in it.tags })
            assertEquals(1, unit.lessons.count { "spaced-review" in it.tags })
            assertEquals(1, unit.lessons.count {
                CurriculumEnrichmentPolicy.EXTENSION_TAG in it.tags
            })
            assertTrue(unit.lessons.flatMap { it.sections }.any {
                it.type == LessonSectionType.PRACTICAL_ACTIVITY &&
                    it.activitySource is LessonActivitySource.Practical
            })
        }
        val handwriting = units.single { it.id == Grade1MandatoryWritingPolicy.UNIT_ID }
        assertEquals(
            Grade1MandatoryWritingPolicy.LESSON_COUNT + Grade1HundredHourPolicy.ADDED_LESSONS_PER_UNIT,
            handwriting.lessons.size
        )
        assertTrue(handwriting.lessons.filter { "four-tier-practice" in it.tags }.all { lesson ->
            lesson.sections.count {
                it.activitySource is LessonActivitySource.Practical
            } == 4
        })
    }

    @Test
    fun `expanded sources remain executable and add new deterministic exercise capacity`() {
        val sections = manifest.lessonCatalog.units
            .flatMap { it.lessons }
            .flatMap { it.sections }
        val exerciseCount = sections.sumOf { section ->
            when (val source = section.activitySource) {
                is LessonActivitySource.Generated -> source.exerciseCount
                is LessonActivitySource.Curated -> source.exerciseIds.size
                is LessonActivitySource.Hybrid ->
                    source.generatedExerciseCount + source.curatedExerciseIds.size
                else -> 0
            }
        }

        assertEquals(Grade1HundredHourPolicy.EXPECTED_TOTAL_EXERCISE_COUNT, exerciseCount)
        assertTrue(sections
            .mapNotNull { it.activitySource as? LessonActivitySource.Generated }
            .all {
                it.parameters["editorial_status"] == "approved" &&
                    it.seedPolicy.name == "STABLE_PER_PROFILE_ATTEMPT"
            })
    }

    @Test
    fun `enrichment is idempotent and keeps original identities`() {
        val expandedAgain = CurriculumEnrichmentPolicy.apply(manifest)

        assertEquals(manifest, expandedAgain)
        assertTrue(manifest.lessonCatalog.units.any { unit ->
            unit.lessons.any { it.id == "lesson.math.addition.g1.combine.to10" }
        })
        assertTrue(manifest.lessonCatalog.units.any { unit ->
            unit.lessons.any { it.id == "lesson.g6.math.statistics.mean_median.checkpoint" }
        })
        assertFalse(manifest.lessonCatalog.units.any { unit ->
            unit.lessons.map { it.id }.size != unit.lessons.map { it.id }.distinct().size
        })
    }

    @Test
    fun `expanded release catalogue passes the production validator`() {
        val report = CurriculumManifestValidator.validate(
            manifest,
            CurriculumValidationMode.RELEASE
        )
        assertTrue(report.issues.joinToString("\n"), report.isValid)
    }

    private fun manifest(): CurriculumManifest = assetFile(
        "src/main/assets/${CurriculumManifestLoader.ASSET_PATH}"
    ).inputStream().use(CurriculumManifestLoader::fromStream)

    private fun assetFile(relativePath: String): File {
        val candidates = listOf(File(relativePath), File("app/$relativePath"))
        return candidates.firstOrNull { it.exists() }
            ?: error("Missing test asset: $relativePath")
    }
}
