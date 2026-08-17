package be.saikono.poincare.domain.curriculum.manifest

import be.saikono.poincare.domain.curriculum.LessonActivitySource
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FullCurriculumCatalogueTest {
    @Test
    fun `full roadmap has all reviewed entries units and outside app responsibilities`() {
        val manifest = manifest()
        val entries = manifest.roadmapEntries
        val units = manifest.lessonCatalog.units

        assertEquals(CurriculumManifestScope.FULL, manifest.scope)
        assertEquals(CurriculumManifestValidator.FULL_ROADMAP_ENTRY_COUNT, entries.size)
        assertEquals(CurriculumEnrichmentPolicy.EXPECTED_UNIT_COUNT, units.size)
        assertEquals(Grade1HundredHourPolicy.EXPECTED_TOTAL_LESSON_COUNT, units.sumOf { it.lessons.size })
        assertEquals(2, entries.count {
            it.deliveryMode == CurriculumDeliveryMode.PARENT_TEACHER_RESPONSIBILITY
        })
        assertEquals(
            CurriculumManifestValidator.fullRoadmapCountsByGrade,
            (1..6).associateWith { grade -> entries.count { it.grade == grade } }
        )
        assertEquals(
            CurriculumManifestValidator.fullUnitCountsByGrade,
            (1..6).associateWith { grade -> units.count { it.grades == listOf(grade) } }
        )
    }

    @Test
    fun `each app roadmap entry maps one to one to a stable grade specific unit`() {
        val manifest = manifest()
        val unitsById = manifest.lessonCatalog.units.associateBy { it.id }
        val appEntries = manifest.roadmapEntries.filter { it.unitId != null }

        assertEquals(CurriculumEnrichmentPolicy.EXPECTED_UNIT_COUNT, appEntries.size)
        assertEquals(CurriculumEnrichmentPolicy.EXPECTED_UNIT_COUNT, appEntries.mapNotNull { it.unitId }.distinct().size)
        assertEquals(unitsById.keys, appEntries.mapNotNullTo(linkedSetOf()) { it.unitId })
        appEntries.forEach { entry ->
            val unit = unitsById.getValue(entry.unitId!!)
            assertEquals(listOf(entry.grade), unit.grades)
            assertEquals(entry.requirement, unit.requirement)
            assertTrue(entry.subcategoryIds.isNotEmpty())
        }
    }

    @Test
    fun `generated and practical sources are deterministic identified and activation gated`() {
        val sources = manifest().lessonCatalog.units
            .flatMap { it.lessons }
            .flatMap { it.sections }
            .mapNotNull { it.activitySource }

        val generated = sources.filterIsInstance<LessonActivitySource.Generated>()
        val practical = sources.filterIsInstance<LessonActivitySource.Practical>()

        assertEquals(Grade1HundredHourPolicy.EXPECTED_TOTAL_GENERATED_SOURCE_COUNT, generated.size)
        assertTrue(generated.all {
            it.generatorId.startsWith("generator.") &&
                it.generatorVersion == 1 &&
                it.exerciseCount > 0 &&
                it.parameters["grade"]?.toIntOrNull() in 1..6
        })
        val stableParameterKey = Regex("^[a-z0-9]+(?:[._-][a-z0-9]+)*$")
        assertTrue(generated.all { source -> source.parameters.keys.all(stableParameterKey::matches) })
        assertTrue(generated.all { it.parameters["phase"] in setOf("guided", "checkpoint") })

        val byEditorialStatus = generated.groupingBy { it.parameters["editorial_status"] }.eachCount()
        assertEquals(
            mapOf("approved" to Grade1HundredHourPolicy.EXPECTED_TOTAL_GENERATED_SOURCE_COUNT),
            byEditorialStatus
        )
        val approved = generated.filter { it.parameters["editorial_status"] == "approved" }
        assertEquals(
            mapOf(
                "generator.g1.math.addition_subtraction.foundations" to 31,
                "generator.g1.math.number_sense.foundations" to 38,
                "generator.g1.geometry.measurement_shapes.foundations" to 33,
                "generator.g1.language.early_literacy.story_awareness" to 43,
                "generator.g1.spelling.phonology.sound_letter" to 40,
                "generator.g1.language.technical_reading.decoding" to 35,
                "generator.g1.language.listening.instructions_stories" to 41,
                "generator.g1.language.speaking.basic_interaction" to 43,
                "generator.g1.language.writing.letters_sentences" to 41,
                "generator.g1.world.environment_science" to 40,
                "generator.g1.world.citizenship.cooperation" to 43,
                "generator.g1.world.traffic.pedestrian_safety" to 43,
                "generator.g2.math.operations.expansion" to 6,
                "generator.g2.geometry.measurement_context" to 6,
                "generator.g2.math.statistics.introduction" to 6,
                "generator.g2.world.integrated_foundations" to 6,
                "generator.g2.language.technical_reading.fluency" to 6,
                "generator.g2.spelling.rules.punctuation_syllables_plural" to 6,
                "generator.g2.language.writing.short_texts" to 6,
                "generator.g2.language.listening.extended_instructions" to 6,
                "generator.g2.world.digital.media_safety" to 6,
                "generator.g2.world.citizenship.emotions_conflicts" to 6,
                "generator.g3.language.reading_comprehension.information" to 6,
                "generator.g3.language.information_skills.lookup" to 6,
                "generator.g3.language.writing.functional_texts" to 6,
                "generator.g3.math.statistics.compare_data" to 6,
                "generator.g3.math.multiplication_division_fractions" to 6,
                "generator.g3.geometry.money_time" to 6,
                "generator.g3.language.reading_strategies.summarise_context" to 6,
                "generator.g3.world.time_space_nature_society" to 6,
                "generator.g3.world.citizenship.responsibility" to 6,
                "generator.g3.language.modern_languages.initiation" to 6,
                "generator.g4.language.integrated_comprehension_interaction" to 6,
                "generator.g4.math.place_value.operations" to 6,
                "generator.g4.geometry.metric_measurement" to 6,
                "generator.g4.geometry.temperature_scale" to 6,
                "generator.g4.math.statistics.problem_solving" to 6,
                "generator.g4.world.science.inquiry_design" to 6,
                "generator.g4.world.geography_history" to 6,
                "generator.g4.world.digital_social_self_regulation" to 6,
                "generator.g4.language.modern_languages.initiation" to 6,
                "generator.g5.language.reading_listening_strategies" to 6,
                "generator.g5.language.listening.evaluate_information" to 6,
                "generator.g5.language.writing.structure_revision" to 6,
                "generator.g5.math.fractions_decimals_ratios" to 6,
                "generator.g5.math.problem_solving.model_strategies" to 6,
                "generator.g5.geometry.scale_speed_distance_time" to 6,
                "generator.g5.language.french.integrated_skills" to 6,
                "generator.g5.spelling.french.grammar_spelling" to 6,
                "generator.g5.world.integrated_geography_history_science" to 6,
                "generator.g5.world.digital.information_production" to 6,
                "generator.g5.world.health_social_learning" to 6,
                "generator.g6.language.critical_reading.arguments" to 6,
                "generator.g6.language.writing.structured_genres" to 6,
                "generator.g6.language.speaking.presentation_arguments" to 6,
                "generator.g6.math.large_numbers_ratios_percentages" to 6,
                "generator.g6.geometry.scale.compound_measurement" to 6,
                "generator.g6.math.statistics.mean_median" to 6,
                "generator.g6.math.problem_solving.multi_step" to 6,
                "generator.g6.language.french.functional_skills" to 6,
                "generator.g6.spelling.french.grammar_vocabulary" to 6,
                "generator.g6.world.integrated.sources_models" to 6,
                "generator.g6.world.digital.critical_safe_use" to 6,
                "generator.g6.world.social_self_regulation" to 6
            ),
            approved.groupingBy { it.generatorId }.eachCount()
        )
        assertEquals(
            mapOf("1" to 471, "2" to 60, "3" to 60, "4" to 54, "5" to 66, "6" to 72),
            approved.groupingBy { it.parameters.getValue("grade") }.eachCount()
        )
        val gradeOneArithmetic = approved
            .filter { it.generatorId == "generator.g1.math.addition_subtraction.foundations" }
        assertEquals(
            mapOf("addition" to 14, "subtraction" to 17),
            gradeOneArithmetic.groupingBy { it.parameters.getValue("operation") }.eachCount()
        )
        assertTrue(approved
            .filter { it.generatorId == "generator.g1.math.number_sense.foundations" }
            .all { it.parameters["roadmap_entry"] == "roadmap.g1.math.number_sense.foundations" })
        assertTrue(approved
            .filter { it.generatorId == "generator.g1.geometry.measurement_shapes.foundations" }
            .all {
                it.parameters["roadmap_entry"] ==
                    "roadmap.g1.geometry.measurement_shapes.foundations"
            })
        assertTrue(approved
            .filter { it.generatorId == "generator.g1.language.early_literacy.story_awareness" }
            .all {
                it.parameters["roadmap_entry"] ==
                    "roadmap.g1.language.early_literacy.story_awareness"
            })
        assertTrue(approved
            .filter { it.generatorId == "generator.g1.spelling.phonology.sound_letter" }
            .all {
                it.parameters["roadmap_entry"] ==
                    "roadmap.g1.spelling.phonology.sound_letter"
            })
        assertTrue(approved
            .filter { it.generatorId == "generator.g1.language.technical_reading.decoding" }
            .all {
                it.parameters["roadmap_entry"] ==
                    "roadmap.g1.language.technical_reading.decoding"
            })
        assertTrue(approved
            .filter { it.generatorId == "generator.g1.language.listening.instructions_stories" }
            .all {
                it.parameters["roadmap_entry"] ==
                    "roadmap.g1.language.listening.instructions_stories"
            })
        assertTrue(approved
            .filter { it.generatorId == "generator.g1.language.speaking.basic_interaction" }
            .all {
                it.parameters["roadmap_entry"] ==
                    "roadmap.g1.language.speaking.basic_interaction"
            })
        assertTrue(approved
            .filter { it.generatorId == "generator.g1.language.writing.letters_sentences" }
            .all {
                it.parameters["roadmap_entry"] ==
                    "roadmap.g1.language.writing.letters_sentences"
            })
        assertTrue(approved
            .filter { it.generatorId == "generator.g1.world.environment_science" }
            .all {
                it.parameters["roadmap_entry"] ==
                    "roadmap.g1.world.environment_science"
            })
        assertTrue(approved
            .filter { it.generatorId == "generator.g1.world.citizenship.cooperation" }
            .all {
                it.parameters["roadmap_entry"] ==
                    "roadmap.g1.world.citizenship.cooperation"
            })
        assertTrue(approved
            .filter { it.generatorId == "generator.g1.world.traffic.pedestrian_safety" }
            .all {
                it.parameters["roadmap_entry"] ==
                    "roadmap.g1.world.traffic.pedestrian_safety"
            })
        assertTrue(approved
            .filter { it.generatorId == "generator.g2.math.operations.expansion" }
            .all {
                it.parameters["roadmap_entry"] ==
                    "roadmap.g2.math.operations.expansion"
            })
        assertTrue(approved
            .filter { it.generatorId == "generator.g2.geometry.measurement_context" }
            .all {
                it.parameters["roadmap_entry"] ==
                    "roadmap.g2.geometry.measurement_context"
            })
        assertTrue(approved
            .filter { it.generatorId == "generator.g2.math.statistics.introduction" }
            .all {
                it.parameters["roadmap_entry"] ==
                    "roadmap.g2.math.statistics.introduction"
            })
        assertTrue(approved
            .filter { it.generatorId == "generator.g2.world.integrated_foundations" }
            .all {
                it.parameters["roadmap_entry"] ==
                    "roadmap.g2.world.integrated_foundations"
            })
        assertTrue(approved
            .filter { it.generatorId == "generator.g2.language.technical_reading.fluency" }
            .all {
                it.parameters["roadmap_entry"] ==
                    "roadmap.g2.language.technical_reading.fluency"
            })
        assertTrue(approved
            .filter { it.generatorId == "generator.g2.spelling.rules.punctuation_syllables_plural" }
            .all {
                it.parameters["roadmap_entry"] ==
                    "roadmap.g2.spelling.rules.punctuation_syllables_plural"
            })
        assertTrue(approved.filter { it.generatorId == "generator.g2.language.writing.short_texts" }.all {
            it.parameters["roadmap_entry"] == "roadmap.g2.language.writing.short_texts"
        })
        assertTrue(approved.filter { it.generatorId == "generator.g2.language.listening.extended_instructions" }.all {
            it.parameters["roadmap_entry"] == "roadmap.g2.language.listening.extended_instructions"
        })
        assertTrue(approved.filter { it.generatorId == "generator.g2.world.digital.media_safety" }.all {
            it.parameters["roadmap_entry"] == "roadmap.g2.world.digital.media_safety"
        })
        assertTrue(approved.filter { it.generatorId == "generator.g2.world.citizenship.emotions_conflicts" }.all {
            it.parameters["roadmap_entry"] == "roadmap.g2.world.citizenship.emotions_conflicts"
        })
        assertTrue(approved.filter { it.parameters["grade"] == "3" }.all {
            it.parameters["roadmap_entry"] == it.generatorId.replace("generator.", "roadmap.")
        })
        assertTrue(approved.filter { it.parameters["grade"] == "5" }.all {
            it.parameters["roadmap_entry"] == it.generatorId.replace("generator.", "roadmap.")
        })
        assertTrue(approved.filter { it.parameters["grade"] == "6" }.all {
            it.parameters["roadmap_entry"] == it.generatorId.replace("generator.", "roadmap.")
        })
        assertEquals(setOf("guided", "checkpoint"), approved.mapTo(linkedSetOf()) {
            it.parameters.getValue("phase")
        })

        assertEquals(Grade1HundredHourPolicy.EXPECTED_TOTAL_PRACTICAL_SOURCE_COUNT, practical.size)
        assertTrue(practical.all { it.requiresAdultVerification })
        assertEquals(practical.size, practical.map { it.activityId }.distinct().size)
    }

    @Test
    fun `roadmap identifiers are semantic language neutral and unique`() {
        val ids = manifest().roadmapEntries.map { it.id }
        val stableId = Regex("^[a-z0-9]+(?:[._-][a-z0-9]+)*$")

        assertEquals(ids.size, ids.distinct().size)
        assertTrue(ids.all(stableId::matches))
        assertTrue(ids.none { Regex("(^|[._-])0*[0-9]{2,}([._-]|$)").containsMatchIn(it) })
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
