package be.saikono.poincare.domain.curriculum.manifest

import be.saikono.poincare.data.content.PoincareContentParser
import be.saikono.poincare.domain.curriculum.LessonActivitySource
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PilotCurriculumSliceTest {
    @Test
    fun `the original pilot addition exercise references remain stable in the full catalogue`() {
        val pilot = manifest().lessonCatalog.units.single {
            it.id == "math.addition.g1.foundations"
        }
        val originalLessons = pilot.lessons.take(3)
        val referencedIds = originalLessons.flatMap { lesson ->
            lesson.sections.flatMap { section ->
                when (val source = section.activitySource) {
                    is LessonActivitySource.Curated -> source.exerciseIds
                    is LessonActivitySource.Hybrid -> source.curatedExerciseIds
                    else -> emptyList()
                }
            }
        }

        val pack = assetFile("src/main/assets/poincare/content/poincare-exercises-math.xml")
            .inputStream()
            .use { PoincareContentParser().parse(it) }
        val exercisesById = pack.exercises.associateBy { it.id }

        assertEquals(36, referencedIds.size)
        assertEquals(referencedIds.size, referencedIds.distinct().size)
        assertTrue(referencedIds.all { it in exercisesById })
        assertTrue(referencedIds.all { id ->
            val exercise = exercisesById.getValue(id)
            exercise.grade == 1 && exercise.subcategoryId == "addition"
        })
    }

    @Test
    fun `pilot lesson identities and prerequisites remain unchanged while subtraction is added`() {
        val lessons = manifest().lessonCatalog.units.single {
            it.id == "math.addition.g1.foundations"
        }.lessons

        assertEquals(
            listOf(
                "lesson.math.addition.g1.combine.to10",
                "lesson.math.addition.g1.sums.to20",
                "lesson.math.addition.g1.strategy.review"
            ),
            lessons.take(3).map { it.id }
        )
        assertTrue(lessons.first().prerequisites.isEmpty())
        assertEquals("lesson.math.addition.g1.combine.to10", lessons[1].prerequisites.single().targetId)
        assertEquals("lesson.math.addition.g1.sums.to20", lessons[2].prerequisites.single().targetId)

        val subtraction = lessons.single { it.id == "lesson.math.subtraction.g1.within20" }
        assertEquals(4, subtraction.sequence)
        assertTrue(subtraction.sections.any {
            it.activitySource is LessonActivitySource.Generated
        })
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
