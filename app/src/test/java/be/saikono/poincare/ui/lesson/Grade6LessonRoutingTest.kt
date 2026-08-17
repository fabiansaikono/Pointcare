package be.saikono.poincare.ui.lesson

import be.saikono.poincare.domain.curriculum.manifest.CurriculumManifestLoader
import be.saikono.poincare.model.LearningDomain
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class Grade6LessonRoutingTest {
    @Test
    fun `every grade 6 unit is reachable through a visible child tile`() {
        val manifest = File("src/main/assets/${CurriculumManifestLoader.ASSET_PATH}")
            .inputStream()
            .use(CurriculumManifestLoader::fromStream)
        val expectedRoutes = mapOf(
            Route(LearningDomain.READING, "reading") to setOf(
                "unit.g6.language.critical_reading.arguments",
            ),
            Route(LearningDomain.WRITING, "writing") to setOf(
                "unit.g6.language.writing.structured_genres",
            ),
            Route(LearningDomain.READING, "speaking") to setOf(
                "unit.g6.language.speaking.presentation_arguments",
            ),
            Route(LearningDomain.MATH, "number_sequences") to setOf(
                "unit.g6.math.large_numbers_ratios_percentages",
            ),
            Route(LearningDomain.GEOMETRY, "measurement") to setOf(
                "unit.g6.geometry.scale.compound_measurement",
            ),
            Route(LearningDomain.MATH, "statistics") to setOf(
                "unit.g6.math.statistics.mean_median",
            ),
            Route(LearningDomain.MATH, "mixed_arithmetic") to setOf(
                "unit.g6.math.problem_solving.multi_step",
            ),
            Route(LearningDomain.READING, "listening") to setOf(
                "unit.g6.language.french.functional_skills",
            ),
            Route(LearningDomain.WRITING, "grammar") to setOf(
                "unit.g6.spelling.french.grammar_vocabulary",
            ),
            Route(LearningDomain.WORLD_KNOWLEDGE, "science") to setOf(
                "unit.g6.world.integrated.sources_models",
            ),
            Route(LearningDomain.WORLD_KNOWLEDGE, "technology") to setOf(
                "unit.g6.world.digital.critical_safe_use",
            ),
            Route(LearningDomain.WORLD_KNOWLEDGE, "citizenship_society") to setOf(
                "unit.g6.world.social_self_regulation",
            ),
        )

        val reachedUnitIds = buildSet {
            expectedRoutes.forEach { (route, expectedUnitIds) ->
                val actualUnitIds = manifest.lessonCatalog.units
                    .filter { unit ->
                        6 in unit.grades &&
                            manifest.unitMatchesLegacyTile(unit, route.domain, route.tileId)
                    }
                    .mapTo(linkedSetOf()) { it.id }

                assertTrue(
                    "Route ${route.domain}/${route.tileId} does not contain $expectedUnitIds; actual=$actualUnitIds",
                    actualUnitIds.containsAll(expectedUnitIds),
                )
                addAll(expectedUnitIds)
            }
        }
        val allGrade6UnitIds = manifest.lessonCatalog.units
            .filter { 6 in it.grades }
            .mapTo(linkedSetOf()) { it.id }

        assertEquals(12, allGrade6UnitIds.size)
        assertEquals(allGrade6UnitIds, reachedUnitIds)
    }

    private data class Route(
        val domain: LearningDomain,
        val tileId: String,
    )
}
