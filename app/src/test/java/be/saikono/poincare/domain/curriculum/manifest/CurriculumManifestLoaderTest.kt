package be.saikono.poincare.domain.curriculum.manifest

import be.saikono.poincare.domain.curriculum.LessonActivitySource
import be.saikono.poincare.model.LearningDomain
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CurriculumManifestLoaderTest {
    @Test
    fun `canonical manifest loads the approved taxonomy and full roadmap catalogue`() {
        val manifest = manifest()
        val index = CurriculumManifestIndex.create(manifest)

        assertEquals(CURRENT_MANIFEST_SCHEMA_VERSION, manifest.schemaVersion)
        assertEquals(Grade1HundredHourPolicy.DEFINITION_VERSION, manifest.definitionVersion)
        assertEquals(CurriculumManifestScope.FULL, manifest.scope)
        assertEquals(6, manifest.categories.size)
        assertEquals(CurriculumManifestValidator.FULL_SUBCATEGORY_COUNT, manifest.categories.sumOf { it.subcategories.size })
        assertTrue(manifest.categories.single { it.domain == LearningDomain.PUZZLE }.subcategories.isEmpty())
        assertEquals(CurriculumManifestValidator.approvedCategoryIds, manifest.categories.map { it.id })
        assertEquals(CurriculumManifestValidator.FULL_ROADMAP_ENTRY_COUNT, manifest.roadmapEntries.size)
        assertEquals(CurriculumEnrichmentPolicy.EXPECTED_UNIT_COUNT, manifest.lessonCatalog.units.size)
        assertEquals(
            Grade1HundredHourPolicy.EXPECTED_TOTAL_LESSON_COUNT,
            manifest.lessonCatalog.units.sumOf { it.lessons.size }
        )
        assertEquals(
            Grade1HundredHourPolicy.DEFINITION_VERSION,
            manifest.lessonCatalog.definitionVersion
        )
        assertTrue(manifest.pilotUnitIds.isEmpty())

        val pilot = manifest.lessonCatalog.units.single { it.id == "math.addition.g1.foundations" }
        assertEquals(22, pilot.lessons.size)
        val numberSense = manifest.lessonCatalog.units.single {
            it.id == "unit.g1.math.number_sense.foundations"
        }
        assertEquals(2, numberSense.definitionVersion)
        assertEquals(2, numberSense.lessons.single {
            it.id == "lesson.g1.math.number_sense.foundations.practice"
        }.definitionVersion)
        assertEquals(2, numberSense.lessons.single {
            it.id == "lesson.g1.math.number_sense.foundations.checkpoint"
        }.definitionVersion)
        val measurementShapes = manifest.lessonCatalog.units.single {
            it.id == "unit.g1.geometry.measurement_shapes.foundations"
        }
        assertEquals(2, measurementShapes.definitionVersion)
        assertEquals(2, measurementShapes.lessons.single {
            it.id == "lesson.g1.geometry.measurement_shapes.foundations.practice"
        }.definitionVersion)
        assertEquals(2, measurementShapes.lessons.single {
            it.id == "lesson.g1.geometry.measurement_shapes.foundations.checkpoint"
        }.definitionVersion)
        val earlyLiteracy = manifest.lessonCatalog.units.single {
            it.id == "unit.g1.language.early_literacy.story_awareness"
        }
        assertEquals(2, earlyLiteracy.definitionVersion)
        assertEquals(2, earlyLiteracy.lessons.single {
            it.id == "lesson.g1.language.early_literacy.story_awareness.practice"
        }.definitionVersion)
        assertEquals(2, earlyLiteracy.lessons.single {
            it.id == "lesson.g1.language.early_literacy.story_awareness.checkpoint"
        }.definitionVersion)
        val phonology = manifest.lessonCatalog.units.single {
            it.id == "unit.g1.spelling.phonology.sound_letter"
        }
        assertEquals(2, phonology.definitionVersion)
        assertEquals(2, phonology.lessons.single {
            it.id == "lesson.g1.spelling.phonology.sound_letter.practice"
        }.definitionVersion)
        assertEquals(2, phonology.lessons.single {
            it.id == "lesson.g1.spelling.phonology.sound_letter.checkpoint"
        }.definitionVersion)
        val technicalReading = manifest.lessonCatalog.units.single {
            it.id == "unit.g1.language.technical_reading.decoding"
        }
        assertEquals(2, technicalReading.definitionVersion)
        assertEquals(2, technicalReading.lessons.single {
            it.id == "lesson.g1.language.technical_reading.decoding.practice"
        }.definitionVersion)
        assertEquals(2, technicalReading.lessons.single {
            it.id == "lesson.g1.language.technical_reading.decoding.checkpoint"
        }.definitionVersion)
        val listening = manifest.lessonCatalog.units.single {
            it.id == "unit.g1.language.listening.instructions_stories"
        }
        assertEquals(2, listening.definitionVersion)
        assertEquals(2, listening.lessons.single {
            it.id == "lesson.g1.language.listening.instructions_stories.practice"
        }.definitionVersion)
        assertEquals(2, listening.lessons.single {
            it.id == "lesson.g1.language.listening.instructions_stories.checkpoint"
        }.definitionVersion)
        val speaking = manifest.lessonCatalog.units.single {
            it.id == "unit.g1.language.speaking.basic_interaction"
        }
        assertEquals(2, speaking.definitionVersion)
        assertEquals(2, speaking.lessons.single {
            it.id == "lesson.g1.language.speaking.basic_interaction.practice"
        }.definitionVersion)
        assertEquals(2, speaking.lessons.single {
            it.id == "lesson.g1.language.speaking.basic_interaction.checkpoint"
        }.definitionVersion)
        val writing = manifest.lessonCatalog.units.single {
            it.id == "unit.g1.language.writing.letters_sentences"
        }
        assertEquals(2, writing.definitionVersion)
        assertEquals(2, writing.lessons.single {
            it.id == "lesson.g1.language.writing.letters_sentences.practice"
        }.definitionVersion)
        assertEquals(2, writing.lessons.single {
            it.id == "lesson.g1.language.writing.letters_sentences.checkpoint"
        }.definitionVersion)
        val environmentScience = manifest.lessonCatalog.units.single {
            it.id == "unit.g1.world.environment_science"
        }
        assertEquals(2, environmentScience.definitionVersion)
        assertEquals(2, environmentScience.lessons.single {
            it.id == "lesson.g1.world.environment_science.practice"
        }.definitionVersion)
        assertEquals(2, environmentScience.lessons.single {
            it.id == "lesson.g1.world.environment_science.checkpoint"
        }.definitionVersion)
        val citizenship = manifest.lessonCatalog.units.single {
            it.id == "unit.g1.world.citizenship.cooperation"
        }
        assertEquals(2, citizenship.definitionVersion)
        assertEquals(2, citizenship.lessons.single {
            it.id == "lesson.g1.world.citizenship.cooperation.practice"
        }.definitionVersion)
        assertEquals(2, citizenship.lessons.single {
            it.id == "lesson.g1.world.citizenship.cooperation.checkpoint"
        }.definitionVersion)
        val pedestrianSafety = manifest.lessonCatalog.units.single {
            it.id == "unit.g1.world.traffic.pedestrian_safety"
        }
        assertEquals(2, pedestrianSafety.definitionVersion)
        assertEquals(2, pedestrianSafety.lessons.single {
            it.id == "lesson.g1.world.traffic.pedestrian_safety.practice"
        }.definitionVersion)
        assertEquals(2, pedestrianSafety.lessons.single {
            it.id == "lesson.g1.world.traffic.pedestrian_safety.checkpoint"
        }.definitionVersion)
        val grade2Measurement = manifest.lessonCatalog.units.single {
            it.id == "unit.g2.geometry.measurement_context"
        }
        assertEquals(2, grade2Measurement.definitionVersion)
        assertEquals(2, grade2Measurement.lessons.single {
            it.id == "lesson.g2.geometry.measurement_context.practice"
        }.definitionVersion)
        assertEquals(2, grade2Measurement.lessons.single {
            it.id == "lesson.g2.geometry.measurement_context.checkpoint"
        }.definitionVersion)
        val grade2Statistics = manifest.lessonCatalog.units.single {
            it.id == "unit.g2.math.statistics.introduction"
        }
        assertEquals(2, grade2Statistics.definitionVersion)
        assertEquals(2, grade2Statistics.lessons.single {
            it.id == "lesson.g2.math.statistics.introduction.practice"
        }.definitionVersion)
        assertEquals(2, grade2Statistics.lessons.single {
            it.id == "lesson.g2.math.statistics.introduction.checkpoint"
        }.definitionVersion)
        val grade2IntegratedWorld = manifest.lessonCatalog.units.single {
            it.id == "unit.g2.world.integrated_foundations"
        }
        assertEquals(2, grade2IntegratedWorld.definitionVersion)
        assertEquals(2, grade2IntegratedWorld.lessons.single {
            it.id == "lesson.g2.world.integrated_foundations.practice"
        }.definitionVersion)
        assertEquals(2, grade2IntegratedWorld.lessons.single {
            it.id == "lesson.g2.world.integrated_foundations.checkpoint"
        }.definitionVersion)
        val grade2TechnicalReading = manifest.lessonCatalog.units.single {
            it.id == "unit.g2.language.technical_reading.fluency"
        }
        assertEquals(2, grade2TechnicalReading.definitionVersion)
        assertEquals(2, grade2TechnicalReading.lessons.single {
            it.id == "lesson.g2.language.technical_reading.fluency.practice"
        }.definitionVersion)
        assertEquals(2, grade2TechnicalReading.lessons.single {
            it.id == "lesson.g2.language.technical_reading.fluency.checkpoint"
        }.definitionVersion)
        val grade2Spelling = manifest.lessonCatalog.units.single {
            it.id == "unit.g2.spelling.rules.punctuation_syllables_plural"
        }
        assertEquals(2, grade2Spelling.definitionVersion)
        assertEquals(2, grade2Spelling.lessons.single {
            it.id == "lesson.g2.spelling.rules.punctuation_syllables_plural.practice"
        }.definitionVersion)
        assertEquals(2, grade2Spelling.lessons.single {
            it.id == "lesson.g2.spelling.rules.punctuation_syllables_plural.checkpoint"
        }.definitionVersion)
        val remainingGrade2Units = listOf(
            "unit.g2.language.writing.short_texts" to "lesson.g2.language.writing.short_texts",
            "unit.g2.language.listening.extended_instructions" to "lesson.g2.language.listening.extended_instructions",
            "unit.g2.world.digital.media_safety" to "lesson.g2.world.digital.media_safety",
            "unit.g2.world.citizenship.emotions_conflicts" to "lesson.g2.world.citizenship.emotions_conflicts"
        )
        remainingGrade2Units.forEach { (unitId, lessonBase) ->
            val unit = manifest.lessonCatalog.units.single { it.id == unitId }
            assertEquals(2, unit.definitionVersion)
            assertEquals(2, unit.lessons.single { it.id == "$lessonBase.practice" }.definitionVersion)
            assertEquals(2, unit.lessons.single { it.id == "$lessonBase.checkpoint" }.definitionVersion)
        }
        val grade3UnitSuffixes = listOf(
            "language.reading_comprehension.information",
            "language.information_skills.lookup",
            "language.writing.functional_texts",
            "math.statistics.compare_data",
            "math.multiplication_division_fractions",
            "geometry.money_time",
            "language.reading_strategies.summarise_context",
            "world.time_space_nature_society",
            "world.citizenship.responsibility",
            "language.modern_languages.initiation"
        )
        grade3UnitSuffixes.forEach { suffix ->
            val unit = manifest.lessonCatalog.units.single { it.id == "unit.g3.$suffix" }
            assertEquals(2, unit.definitionVersion)
            assertEquals(1, unit.lessons.single { it.id == "lesson.g3.$suffix.explore" }.definitionVersion)
            assertEquals(2, unit.lessons.single { it.id == "lesson.g3.$suffix.practice" }.definitionVersion)
            assertEquals(2, unit.lessons.single { it.id == "lesson.g3.$suffix.checkpoint" }.definitionVersion)
        }
        val grade4UnitSuffixes = listOf(
            "language.integrated_comprehension_interaction",
            "math.place_value.operations",
            "geometry.metric_measurement",
            "geometry.temperature_scale",
            "math.statistics.problem_solving",
            "world.science.inquiry_design",
            "world.geography_history",
            "world.digital_social_self_regulation",
            "language.modern_languages.initiation"
        )
        grade4UnitSuffixes.forEach { suffix ->
            val unit = manifest.lessonCatalog.units.single { it.id == "unit.g4.$suffix" }
            assertEquals(2, unit.definitionVersion)
            assertEquals(1, unit.lessons.single { it.id == "lesson.g4.$suffix.explore" }.definitionVersion)
            assertEquals(2, unit.lessons.single { it.id == "lesson.g4.$suffix.practice" }.definitionVersion)
            assertEquals(2, unit.lessons.single { it.id == "lesson.g4.$suffix.checkpoint" }.definitionVersion)
        }
        val grade4PracticalSources = manifest.lessonCatalog.units
            .filter { it.id.startsWith("unit.g4.") }
            .flatMap { it.lessons }
            .flatMap { it.sections }
            .mapNotNull { it.activitySource as? LessonActivitySource.Practical }
        assertEquals(9, grade4PracticalSources.size)
        grade4PracticalSources.forEach { source ->
            assertTrue(source.requiresAdultVerification)
            assertTrue(source.instructions.visible.nl.length > 100)
            assertTrue(source.instructions.visible.fr.orEmpty().length > 100)
            assertTrue(source.instructions.visible.en.orEmpty().length > 100)
        }
        val grade5UnitSuffixes = listOf(
            "language.reading_listening_strategies",
            "language.listening.evaluate_information",
            "language.writing.structure_revision",
            "math.fractions_decimals_ratios",
            "math.problem_solving.model_strategies",
            "geometry.scale_speed_distance_time",
            "language.french.integrated_skills",
            "spelling.french.grammar_spelling",
            "world.integrated_geography_history_science",
            "world.digital.information_production",
            "world.health_social_learning"
        )
        grade5UnitSuffixes.forEach { suffix ->
            val unit = manifest.lessonCatalog.units.single { it.id == "unit.g5.$suffix" }
            assertEquals(2, unit.definitionVersion)
            assertEquals(1, unit.lessons.single { it.id == "lesson.g5.$suffix.explore" }.definitionVersion)
            assertEquals(2, unit.lessons.single { it.id == "lesson.g5.$suffix.practice" }.definitionVersion)
            assertEquals(2, unit.lessons.single { it.id == "lesson.g5.$suffix.checkpoint" }.definitionVersion)
        }
        val grade5PracticalSources = manifest.lessonCatalog.units
            .filter { it.id.startsWith("unit.g5.") }
            .flatMap { it.lessons }
            .flatMap { it.sections }
            .mapNotNull { it.activitySource as? LessonActivitySource.Practical }
        assertEquals(11, grade5PracticalSources.size)
        grade5PracticalSources.forEach { source ->
            assertTrue(source.requiresAdultVerification)
            assertTrue(source.instructions.visible.nl.length > 100)
            assertTrue(source.instructions.visible.fr.orEmpty().length > 100)
            assertTrue(source.instructions.visible.en.orEmpty().length > 100)
        }
        val grade6UnitSuffixes = listOf(
            "language.critical_reading.arguments",
            "language.writing.structured_genres",
            "language.speaking.presentation_arguments",
            "math.large_numbers_ratios_percentages",
            "geometry.scale.compound_measurement",
            "math.statistics.mean_median",
            "math.problem_solving.multi_step",
            "language.french.functional_skills",
            "spelling.french.grammar_vocabulary",
            "world.integrated.sources_models",
            "world.digital.critical_safe_use",
            "world.social_self_regulation"
        )
        grade6UnitSuffixes.forEach { suffix ->
            val unit = manifest.lessonCatalog.units.single { it.id == "unit.g6.$suffix" }
            assertEquals(2, unit.definitionVersion)
            assertEquals(1, unit.lessons.single { it.id == "lesson.g6.$suffix.explore" }.definitionVersion)
            assertEquals(2, unit.lessons.single { it.id == "lesson.g6.$suffix.practice" }.definitionVersion)
            assertEquals(2, unit.lessons.single { it.id == "lesson.g6.$suffix.checkpoint" }.definitionVersion)
            assertTrue(unit.description?.visible?.nl.orEmpty().length > 80)
            assertTrue(unit.description?.visible?.fr.orEmpty().length > 80)
            assertTrue(unit.description?.visible?.en.orEmpty().length > 80)
        }
        val grade6PracticalSources = manifest.lessonCatalog.units
            .filter { it.id.startsWith("unit.g6.") }
            .flatMap { it.lessons }
            .flatMap { it.sections }
            .mapNotNull { it.activitySource as? LessonActivitySource.Practical }
        assertEquals(12, grade6PracticalSources.size)
        grade6PracticalSources.forEach { source ->
            assertTrue(source.requiresAdultVerification)
            assertTrue(source.instructions.visible.nl.length > 100)
            assertTrue(source.instructions.visible.fr.orEmpty().length > 100)
            assertTrue(source.instructions.visible.en.orEmpty().length > 100)
        }
        assertEquals(
            "language.speaking_interaction",
            index.resolveLegacySubcategory(LearningDomain.READING, "speaking")?.id
        )
        assertEquals(
            "math.tables_graphs_statistics",
            index.resolveLegacySubcategory(LearningDomain.MATH, "statistics")?.id
        )
        assertEquals(
            "math.addition_subtraction",
            index.resolveLegacySubcategory(LearningDomain.MATH, "addition")?.id
        )
        assertNotNull(index.category("world_orientation"))
        assertNotNull(index.roadmapEntry("roadmap.g1.math.addition_subtraction.foundations"))
        assertEquals(
            "roadmap.g1.math.addition_subtraction.foundations",
            index.roadmapEntryForUnit("math.addition.g1.foundations")?.id
        )
        assertEquals(CurriculumManifestValidator.fullRoadmapCountsByGrade.getValue(1), index.roadmapEntriesForGrade(1).size)
        assertNull(index.roadmapEntry("roadmap.unknown"))
    }

    @Test
    fun `manifest source records the exact reviewed workbook and both worksheets`() {
        val manifest = manifest()
        assertEquals("poincare.targets.reworked", manifest.source.id)
        assertEquals("Taxonomy", manifest.source.sheet)
        assertEquals("Grade roadmap", manifest.source.roadmapSheet)
        assertEquals(
            "5d48ed0caf5d0b2b164b1302fa8b32c8b5fd436cd62afaf2a4fb52938fe31568",
            manifest.source.workbookSha256
        )
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
