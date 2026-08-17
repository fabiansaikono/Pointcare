package be.saikono.poincare.domain.curriculum.v2

import java.io.File
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class Grade1CoverageCrosswalkTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `all 65 reference units have explicit valid coverage evidence`() {
        val catalog = LessonCatalogV2Loader.fromFile(assetFile(LessonCatalogV2Loader.ASSET_PATH))
        val catalogLessons = catalog.lessons.filter { it.grade == 1 }.associateBy { it.id }
        val root = json.parseToJsonElement(assetFile(CROSSWALK_PATH).readText(Charsets.UTF_8)).jsonObject
        val units = root.getValue("units").jsonArray
        val allowed = root.getValue("allowedCoverageStatuses").jsonArray
            .map { it.jsonPrimitive.content }
            .toSet()

        assertEquals(65, units.size)
        assertEquals(65, units.map { it.jsonObject.getValue("referenceUnitId").jsonPrimitive.content }.distinct().size)
        units.forEach { element ->
            val row = element.jsonObject
            val status = row.getValue("coverageStatus").jsonPrimitive.content
            assertTrue(status in allowed)
            assertTrue("No Grade 1 reference unit may remain missing.", status != "MISSING")
            val lessonId = row.getValue("lessonId").jsonPrimitive.content
            assertTrue("Unknown mapped lesson: $lessonId", lessonId in catalogLessons)
            val evidence = row.getValue("evidence").jsonObject
            assertTrue(evidence.getValue("theoryStageId").jsonPrimitive.content.isNotBlank())
            assertEquals(4, evidence.getValue("exerciseStageIds").jsonArray.size)
            assertTrue(evidence.getValue("masteryTestStageId").jsonPrimitive.content.isNotBlank())
            assertTrue(evidence.getValue("exerciseSetIds").jsonArray.isNotEmpty())
            assertEquals(
                setOf("nl-BE", "fr-BE", "en-US"),
                evidence.getValue("requiredLocales").jsonArray.map { it.jsonPrimitive.content }.toSet()
            )
            if (status == "ADULT_MEDIATED") {
                assertTrue(row.getValue("adultEvidenceRequirement").jsonPrimitive.content.isNotBlank())
            }
        }
    }

    @Test
    fun `oral language lesson combines listening and speaking generators`() {
        val catalog = LessonCatalogV2Loader.fromFile(assetFile(LessonCatalogV2Loader.ASSET_PATH))
        val lesson = catalog.lessons.single {
            it.unitId == "unit.g1.nl.oral.luisteren-vertellen-en-instructies-volgen"
        }
        val generators = lesson.stages.flatMap { it.exerciseSets }
            .flatMap { set -> listOfNotNull(set.generatorId, set.parameters["original_generator_id"]) }
            .toSet()

        assertTrue("generator.g1.language.listening.instructions_stories" in generators)
        assertTrue("generator.g1.language.speaking.basic_interaction" in generators)
    }

    private fun assetFile(path: String): File = listOf(
        File("src/main/assets/$path"),
        File("app/src/main/assets/$path")
    ).firstOrNull(File::isFile) ?: error("Missing asset: $path")

    private companion object {
        const val CROSSWALK_PATH = "poincare/curriculum/grade1-coverage-crosswalk-v1.json"
    }
}
