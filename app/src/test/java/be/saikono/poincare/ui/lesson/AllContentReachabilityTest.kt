package be.saikono.poincare.ui.lesson

import be.saikono.poincare.data.content.PoincareContentParser
import be.saikono.poincare.domain.curriculum.manifest.CurriculumManifestLoader
import be.saikono.poincare.model.LearningDomain
import be.saikono.poincare.ui.home.getSubCategoriesForDomain
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AllContentReachabilityTest {
    @Test
    fun `every curriculum unit is reachable through a child subcategory tile`() {
        val manifest = manifest()
        val unreachable = manifest.lessonCatalog.units.filter { unit ->
            LearningDomain.entries.all { domain ->
                getSubCategoriesForDomain(domain.name).none { tile ->
                    manifest.unitMatchesLegacyTile(unit, domain, tile.id)
                }
            }
        }

        assertEquals(
            "Curriculum units without a visible child route: ${unreachable.map { it.id }}",
            emptyList<String>(),
            unreachable.map { it.id }
        )
    }

    @Test
    fun `every canonical XML exercise belongs to a reachable tile or tile option`() {
        val parser = PoincareContentParser()
        val files = listOf(
            "poincare-exercises-math.xml",
            "poincare-exercises-language.xml",
            "poincare-exercises-logic-puzzles.xml",
            "poincare-exercises-world-knowledge.xml",
            "poincare-exercises-geometry.xml"
        )
        val exercises = files.flatMap { fileName ->
            assetFile("src/main/assets/poincare/content/$fileName")
                .inputStream()
                .use(parser::parse)
                .exercises
        }
        val unreachable = exercises.filterNot { exercise ->
            val subcategoryId = exercise.subcategoryId ?: return@filterNot false
            domainsFor(exercise.mainCategory).any { domain ->
                getSubCategoriesForDomain(domain.name).any { tile ->
                    tile.id == subcategoryId ||
                        tile.options.any { it.targetId == subcategoryId }
                }
            }
        }

        assertEquals(canonicalManifestTotal(), exercises.size)
        assertTrue(
            "XML exercises without a visible child route: ${
                unreachable.groupingBy { "${it.mainCategory}/${it.subcategoryId}" }.eachCount()
            }",
            unreachable.isEmpty()
        )
    }

    private fun domainsFor(mainCategory: String?): Set<LearningDomain> = when (mainCategory) {
        "math" -> setOf(LearningDomain.MATH)
        "geometry" -> setOf(LearningDomain.GEOMETRY)
        "language" -> setOf(LearningDomain.READING, LearningDomain.WRITING)
        "logic_puzzles" -> setOf(LearningDomain.PUZZLE)
        "world_knowledge", "world-knowledge" -> setOf(LearningDomain.WORLD_KNOWLEDGE)
        else -> emptySet()
    }

    private fun manifest() = assetFile("src/main/assets/${CurriculumManifestLoader.ASSET_PATH}")
        .inputStream()
        .use(CurriculumManifestLoader::fromStream)

    private fun canonicalManifestTotal(): Int = Regex(""""totalExerciseCount"\s*:\s*(\d+)""")
        .find(assetFile("src/main/assets/poincare/content/manifest.json").readText())
        ?.groupValues
        ?.get(1)
        ?.toIntOrNull()
        ?: error("manifest.json does not contain totalExerciseCount")

    private fun assetFile(relativePath: String): File {
        val candidates = listOf(File(relativePath), File("app/$relativePath"))
        return candidates.firstOrNull(File::exists) ?: error("Missing test asset: $relativePath")
    }
}
