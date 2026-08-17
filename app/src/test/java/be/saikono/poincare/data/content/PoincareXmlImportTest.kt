package be.saikono.poincare.data.content

import be.saikono.poincare.data.icons.PoincareIconCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class PoincareXmlImportTest {
    @Test
    fun `icon catalog parses known icon mappings`() {
        assetFile("src/main/assets/poincare/icons/poincare-icons-final-complete-pack-v1.xml").inputStream().use { stream ->
            val catalog = PoincareIconCatalog.fromStream(stream)

            assertNotNull(catalog.findById("topic-lezen"))
            assertNotNull(catalog.findByTopic("begrijpend lezen"))
            assertNotNull(catalog.findByTopic("math_crosswords"))
            assertNotNull(catalog.findBySkill("SPELL"))
            assertNotNull(catalog.findByUiKey("parent-zone"))
            assertNotNull(catalog.findByReward("snoepje"))
            assertNotNull(catalog.findByReward("poepje"))
        }
    }

    @Test
    fun `minimal content XML parses multiple choice answers`() {
        assetFile("src/main/assets/poincare/content/exercises/poincare-minimal-import-test.xml").inputStream().use { stream ->
            val pack = PoincareContentParser().parse(stream)

            assertEquals("poincare-minimal-test", pack.id)
            assertEquals(2, pack.exercises.size)
            val multipleChoice = pack.exercises.first { it.type == "multiple_choice" }
            assertEquals("test-sub-001", multipleChoice.id)
            assertEquals(3, multipleChoice.answers.size)
            assertTrue(multipleChoice.answers.any { it.correct })
        }
    }

    @Test
    fun `starter content XML parses unique exercises with icons`() {
        assetFile("src/main/assets/poincare/content/exercises/poincare-original-math-l4-starter-001-with-icons.xml").inputStream().use { stream ->
            val pack = PoincareContentParser().parse(stream)
            val exerciseIds = pack.exercises.map { it.id }

            assertTrue(pack.exercises.isNotEmpty())
            assertEquals(exerciseIds.size, exerciseIds.toSet().size)
            assertTrue(pack.exercises.any { it.topic == "getallen tot 10 000" })
            assertTrue(pack.exercises.any { it.skillRef == "ADD-SUB" })
            assertTrue(pack.exercises.any { it.iconRef != null && it.skillIconRef != null })
        }
    }

    @Test
    fun `v3 multilingual content pack parses every canonical exercise file`() {
        val fileNames = listOf(
            "poincare-exercises-math.xml",
            "poincare-exercises-language.xml",
            "poincare-exercises-logic-puzzles.xml",
            "poincare-exercises-world-knowledge.xml",
            "poincare-exercises-geometry.xml"
        )
        val expectedTotal = canonicalManifestTotal()
        val parser = PoincareContentParser()
        val packs = fileNames.map { fileName ->
            assetFile("src/main/assets/poincare/content/$fileName").inputStream().use { stream ->
                parser.parse(stream).also { pack ->
                    assertTrue("$fileName must expose a main category", !pack.mainCategory.isNullOrBlank())
                    if (fileName == "poincare-exercises-logic-puzzles.xml") {
                        assertTrue("legacy fixed puzzle exercises must stay removed", pack.exercises.isEmpty())
                        assertTrue("legacy fixed puzzle subcategories must stay removed", pack.subcategories.isEmpty())
                    } else {
                        assertTrue("$fileName must contain exercises", pack.exercises.isNotEmpty())
                        assertTrue(pack.subcategories.isNotEmpty())
                    }
                }
            }
        }

        val exercises = packs.flatMap { it.exercises }
        val exerciseIds = exercises.map { it.id }
        assertEquals(expectedTotal, exercises.size)
        assertEquals(exerciseIds.size, exerciseIds.toSet().size)
        assertTrue(exercises.all { it.translations.keys.containsAll(listOf("nl-BE", "en-US", "fr-BE")) })
        assertTrue(exercises.all { it.grade in 1..6 })
        assertTrue(exercises.all { it.difficultyStars in 1..3 })
        assertTrue(exercises.all { !it.mainCategory.isNullOrBlank() && !it.subcategoryId.isNullOrBlank() })
    }

    @Test
    fun `v3 parser ignores unknown tags and preserves translated answers`() {
        assetFile("src/main/assets/poincare/content/poincare-exercises-math.xml").inputStream().use { stream ->
            val pack = PoincareContentParser().parse(stream)
            val exercise = pack.exercises.first { it.id == "addition_d1_g1_seq0001" }

            assertEquals("math", pack.mainCategory)
            assertEquals("addition", exercise.subcategoryId)
            assertTrue(!exercise.translations["en-US"]?.question.isNullOrBlank())
            assertEquals(4, exercise.translations["fr-BE"]?.answers?.size)
            assertTrue(exercise.translations["nl-BE"]?.answers?.any { it.correct } == true)
        }
    }

    private fun canonicalManifestTotal(): Int {
        val manifest = assetFile("src/main/assets/poincare/content/manifest.json").readText()
        return Regex(""""totalExerciseCount"\s*:\s*(\d+)""")
            .find(manifest)
            ?.groupValues
            ?.get(1)
            ?.toIntOrNull()
            ?: error("manifest.json does not contain totalExerciseCount")
    }

    private fun assetFile(relativePath: String): File {
        val candidates = listOf(File(relativePath), File("app/$relativePath"))
        return candidates.firstOrNull { it.exists() }
            ?: error("Missing test asset: $relativePath")
    }
}
