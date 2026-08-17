package be.saikono.poincare.domain.curriculum.manifest

import be.saikono.poincare.domain.curriculum.CurriculumValidationMode
import be.saikono.poincare.domain.curriculum.LessonActivitySource
import be.saikono.poincare.domain.curriculum.LessonSectionType
import be.saikono.poincare.domain.curriculum.allLessons
import be.saikono.poincare.model.LearningDomain
import be.saikono.poincare.ui.lesson.lessonMatchesLegacyTile
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Grade1HundredHourPolicyTest {
    private val manifest by lazy(::manifest)

    @Test
    fun `Grade 1 reaches the active-hour target with complete short learning cycles`() {
        val units = manifest.lessonCatalog.units.filter { 1 in it.grades }
        val lessons = units.flatMap { it.lessons }
        val depth = lessons.filter { Grade1HundredHourPolicy.TAG in it.tags }

        assertEquals(Grade1HundredHourPolicy.GRADE1_UNIT_COUNT, units.size)
        assertEquals(Grade1HundredHourPolicy.ADDED_LESSON_COUNT, depth.size)
        assertEquals(
            Grade1HundredHourPolicy.EXPECTED_GRADE1_MINUTES,
            lessons.sumOf { it.estimatedMinutes ?: 0 }
        )
        assertEquals(
            Grade1HundredHourPolicy.EXPECTED_GRADE1_SECTION_COUNT,
            lessons.sumOf { it.sections.size }
        )
        assertTrue(lessons.sumOf { it.estimatedMinutes ?: 0 } in 5_400..7_500)
        units.forEach { unit ->
            assertEquals(
                Grade1HundredHourPolicy.CYCLE_LESSONS_PER_UNIT,
                unit.lessons.count { "learning-cycle" in it.tags }
            )
            assertEquals(1, unit.lessons.count { "grade1-readiness" in it.tags })
        }

        depth.filter { "learning-cycle" in it.tags }.forEach { lesson ->
            val types = lesson.sections.mapTo(linkedSetOf()) { it.type }
            assertTrue(LessonSectionType.EXPLANATION in types)
            assertTrue(LessonSectionType.GUIDED_PRACTICE in types)
            assertTrue(LessonSectionType.INDEPENDENT_PRACTICE in types)
            assertTrue(LessonSectionType.REVIEW in types)
            assertTrue(LessonSectionType.PRACTICAL_ACTIVITY in types)
            assertTrue(LessonSectionType.CHECKPOINT in types)
            assertEquals(lesson.estimatedMinutes, lesson.sections.sumOf { it.estimatedMinutes ?: 0 })
        }
    }

    @Test
    fun `approved Grade 1 XML questions are linked once and rejected duplicates stay out`() {
        val approved = Grade1HundredHourPolicy.approvedCuratedIds()
        val linked = manifest.lessonCatalog.allLessons()
            .flatMap { it.sections }
            .flatMap { section ->
                (section.activitySource as? LessonActivitySource.Curated)?.exerciseIds.orEmpty()
            }

        assertEquals(339, approved.size)
        assertEquals(approved.size, approved.distinct().size)
        assertTrue(approved.all { linked.count { linkedId -> linkedId == it } == 1 })
        assertFalse("addition_d1_g1_seq0014" in linked)
        assertFalse("addition_d3_g1_seq0099" in linked)
        assertFalse("spelling_d2_g1_seq0067" in linked)
    }

    @Test
    fun `comparison measurement time money and shapes depth lessons resolve to separate tiles`() {
        val unit = manifest.lessonCatalog.units.single {
            it.id == "unit.g1.geometry.measurement_shapes.foundations"
        }
        val depth = unit.lessons.filter { "learning-cycle" in it.tags }

        assertEquals(2, depth.count {
            manifest.lessonMatchesLegacyTile(unit, it, LearningDomain.GEOMETRY, "compare_size")
        })
        assertEquals(3, depth.count {
            manifest.lessonMatchesLegacyTile(unit, it, LearningDomain.GEOMETRY, "measurement")
        })
        assertEquals(3, depth.count {
            manifest.lessonMatchesLegacyTile(unit, it, LearningDomain.GEOMETRY, "time")
        })
        assertEquals(2, depth.count {
            manifest.lessonMatchesLegacyTile(unit, it, LearningDomain.MATH, "money")
        })
        assertEquals(2, depth.count {
            manifest.lessonMatchesLegacyTile(unit, it, LearningDomain.GEOMETRY, "geometry_shapes")
        })
    }

    @Test
    fun `depth policy is idempotent and release-valid`() {
        assertEquals(manifest, Grade1HundredHourPolicy.apply(manifest))
        assertEquals(Grade1HundredHourPolicy.DEFINITION_VERSION, manifest.definitionVersion)
        assertEquals(
            Grade1HundredHourPolicy.EXPECTED_TOTAL_LESSON_COUNT,
            manifest.lessonCatalog.allLessons().size
        )
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
        return candidates.firstOrNull(File::exists) ?: error("Missing test asset: $relativePath")
    }
}
