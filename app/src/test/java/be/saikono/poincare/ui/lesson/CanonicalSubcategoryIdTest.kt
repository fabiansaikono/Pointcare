package be.saikono.poincare.ui.lesson

import be.saikono.poincare.domain.curriculum.LessonActivitySource
import be.saikono.poincare.domain.curriculum.generation.LessonGenerationContext
import be.saikono.poincare.domain.curriculum.generation.LessonGenerationResult
import be.saikono.poincare.domain.curriculum.generation.LessonGeneratorRegistry
import be.saikono.poincare.domain.curriculum.manifest.CurriculumManifestLoader
import be.saikono.poincare.model.LearningDomain
import be.saikono.poincare.ui.home.getSubCategoriesForDomain
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CanonicalSubcategoryIdTest {
    @Test
    fun `every lesson and generated exercise has exactly one canonical tile ID`() {
        val manifest = assetFile("src/main/assets/${CurriculumManifestLoader.ASSET_PATH}")
            .inputStream()
            .use(CurriculumManifestLoader::fromStream)
        val registry = LessonGeneratorRegistry.default()

        manifest.lessonCatalog.units.forEach { unit ->
            unit.lessons.forEach { lesson ->
                val routes = LearningDomain.entries.flatMap { domain ->
                    getSubCategoriesForDomain(domain.name).mapNotNull { tile ->
                        (domain to tile.id).takeIf {
                            manifest.lessonMatchesLegacyTile(unit, lesson, domain, tile.id)
                        }
                    }
                }
                assertEquals("${lesson.id} must resolve to one tile", 1, routes.size)
                val (domain, tileId) = routes.single()

                lesson.sections.forEach { section ->
                    val generated = section.activitySource as? LessonActivitySource.Generated
                    if (generated != null) {
                        val attemptId = "canonical-${lesson.id}-${section.id}"
                        val result = registry.materialize(
                            source = generated,
                            context = LessonGenerationContext(
                                profileId = "canonical-audit",
                                unitId = unit.id,
                                lessonId = lesson.id,
                                sectionId = section.id,
                                canonicalDomain = domain,
                                canonicalSubcategoryId = tileId,
                                attemptId = attemptId
                            )
                        )
                        assertTrue("$lesson/${section.id}: $result", result is LessonGenerationResult.Success)
                        (result as LessonGenerationResult.Success).exercises.forEach { exercise ->
                            assertEquals(domain, exercise.domain)
                            assertEquals(tileId, exercise.data["subcategoryId"])
                        }
                    }
                }
            }
        }
    }

    private fun assetFile(relativePath: String): File {
        val candidates = listOf(File(relativePath), File("app/$relativePath"))
        return candidates.firstOrNull(File::exists) ?: error("Missing test asset: $relativePath")
    }
}
