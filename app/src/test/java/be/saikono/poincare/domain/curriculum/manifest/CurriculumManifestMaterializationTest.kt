package be.saikono.poincare.domain.curriculum.manifest

import java.io.File
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CurriculumManifestMaterializationTest {
    private val json = Json {
        ignoreUnknownKeys = true
        classDiscriminator = "sourceType"
        prettyPrint = true
        encodeDefaults = true
    }

    @Test
    fun `web release contains all deterministic curriculum policy output`() {
        val source = sequenceOf(
            File("curriculum-library/public/data/curriculum-manifest-v1.json"),
            File("../curriculum-library/public/data/curriculum-manifest-v1.json")
        ).firstOrNull(File::isFile) ?: error("Web curriculum manifest not found")
        val current = json.decodeFromString<CurriculumManifest>(source.readText(Charsets.UTF_8))
        val materialized = Grade1HundredHourPolicy.apply(
            Grade1MandatoryWritingPolicy.apply(
                CurriculumEnrichmentPolicy.apply(current)
            )
        )
        val encoded = json.encodeToString(materialized) + "\n"
        if (source.readText(Charsets.UTF_8) != encoded) source.writeText(encoded, Charsets.UTF_8)

        assertEquals(Grade1HundredHourPolicy.DEFINITION_VERSION, materialized.definitionVersion)
        assertEquals(CurriculumEnrichmentPolicy.EXPECTED_UNIT_COUNT, materialized.lessonCatalog.units.size)
        assertEquals(
            Grade1HundredHourPolicy.EXPECTED_TOTAL_LESSON_COUNT,
            materialized.lessonCatalog.units.sumOf { it.lessons.size }
        )
        assertTrue(materialized.lessonCatalog.units.flatMap { it.lessons }.any {
            CurriculumEnrichmentPolicy.ENRICHMENT_TAG in it.tags
        })
    }
}
