package be.saikono.poincare.ui.lesson

import be.saikono.poincare.domain.curriculum.manifest.CurriculumManifestLoader
import be.saikono.poincare.model.LearningDomain
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class Grade5LessonRoutingTest {
    @Test
    fun `every grade 5 unit is reachable through a visible legacy tile`() {
        val manifest = File("src/main/assets/${CurriculumManifestLoader.ASSET_PATH}")
            .inputStream()
            .use(CurriculumManifestLoader::fromStream)
        val expectedRoutes = mapOf(
            Route(LearningDomain.READING, "reading") to setOf(
                "unit.g5.language.reading_listening_strategies",
            ),
            Route(LearningDomain.READING, "listening") to setOf(
                "unit.g5.language.listening.evaluate_information",
                "unit.g5.language.french.integrated_skills",
            ),
            Route(LearningDomain.WRITING, "writing") to setOf(
                "unit.g5.language.writing.structure_revision",
            ),
            Route(LearningDomain.MATH, "fractions") to setOf(
                "unit.g5.math.fractions_decimals_ratios",
            ),
            Route(LearningDomain.MATH, "mixed_arithmetic") to setOf(
                "unit.g5.math.problem_solving.model_strategies",
            ),
            Route(LearningDomain.GEOMETRY, "measurement") to setOf(
                "unit.g5.geometry.scale_speed_distance_time",
            ),
            Route(LearningDomain.WRITING, "grammar") to setOf(
                "unit.g5.spelling.french.grammar_spelling",
            ),
            Route(LearningDomain.WORLD_KNOWLEDGE, "geography") to setOf(
                "unit.g5.world.integrated_geography_history_science",
            ),
            Route(LearningDomain.WORLD_KNOWLEDGE, "technology") to setOf(
                "unit.g5.world.digital.information_production",
            ),
            Route(LearningDomain.WORLD_KNOWLEDGE, "human_body") to setOf(
                "unit.g5.world.health_social_learning",
            ),
        )

        val reachedUnitIds = buildSet {
            expectedRoutes.forEach { (route, expectedUnitIds) ->
                val actualUnitIds = manifest.lessonCatalog.units
                    .filter { unit ->
                        5 in unit.grades &&
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
        val allGrade5UnitIds = manifest.lessonCatalog.units
            .filter { 5 in it.grades }
            .mapTo(linkedSetOf()) { it.id }

        assertEquals(11, allGrade5UnitIds.size)
        assertEquals(allGrade5UnitIds, reachedUnitIds)
    }

    private data class Route(
        val domain: LearningDomain,
        val tileId: String,
    )
}
