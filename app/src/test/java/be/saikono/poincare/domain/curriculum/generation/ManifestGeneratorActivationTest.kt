package be.saikono.poincare.domain.curriculum.generation

import be.saikono.poincare.domain.curriculum.LessonActivitySource
import be.saikono.poincare.domain.curriculum.manifest.CurriculumManifest
import be.saikono.poincare.domain.curriculum.manifest.CurriculumManifestLoader
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ManifestGeneratorActivationTest {
    @Test
    fun `all reviewed Grade 1 to Grade 6 sources are approved and executable`() {
        val manifest = manifest()
        val registry = LessonGeneratorRegistry.default()
        val generated = buildList {
            manifest.lessonCatalog.units.forEach { unit ->
                unit.lessons.forEach { lesson ->
                    lesson.sections.forEach { section ->
                        val source = section.activitySource as? LessonActivitySource.Generated
                        if (source != null) {
                            add(LocatedSource(unit.id, lesson.id, section.id, source))
                        }
                    }
                }
            }
        }

        assertEquals(
            be.saikono.poincare.domain.curriculum.manifest.Grade1HundredHourPolicy.EXPECTED_TOTAL_GENERATED_SOURCE_COUNT,
            generated.size
        )
        assertEquals(0, generated.count { it.source.parameters["editorial_status"] == "planned" })

        val approved = generated.filter { it.source.parameters["editorial_status"] == "approved" }
        assertEquals(
            be.saikono.poincare.domain.curriculum.manifest.Grade1HundredHourPolicy.EXPECTED_TOTAL_GENERATED_SOURCE_COUNT,
            approved.size
        )
        val originalSectionIds = setOf(
                "lesson.math.subtraction.g1.within20.guided",
                "lesson.math.subtraction.g1.within20.checkpoint",
                "lesson.g1.math.number_sense.foundations.practice.guided",
                "lesson.g1.math.number_sense.foundations.checkpoint.checkpoint",
                "lesson.g1.geometry.measurement_shapes.foundations.practice.guided",
                "lesson.g1.geometry.measurement_shapes.foundations.checkpoint.checkpoint",
                "lesson.g1.language.early_literacy.story_awareness.practice.guided",
                "lesson.g1.language.early_literacy.story_awareness.checkpoint.checkpoint",
                "lesson.g1.spelling.phonology.sound_letter.practice.guided",
                "lesson.g1.spelling.phonology.sound_letter.checkpoint.checkpoint",
                "lesson.g1.language.technical_reading.decoding.practice.guided",
                "lesson.g1.language.technical_reading.decoding.checkpoint.checkpoint",
                "lesson.g1.language.listening.instructions_stories.practice.guided",
                "lesson.g1.language.listening.instructions_stories.checkpoint.checkpoint",
                "lesson.g1.language.speaking.basic_interaction.practice.guided",
                "lesson.g1.language.speaking.basic_interaction.checkpoint.checkpoint",
                "lesson.g1.language.writing.letters_sentences.practice.guided",
                "lesson.g1.language.writing.letters_sentences.checkpoint.checkpoint",
                "lesson.g1.world.environment_science.practice.guided",
                "lesson.g1.world.environment_science.checkpoint.checkpoint",
                "lesson.g1.world.citizenship.cooperation.practice.guided",
                "lesson.g1.world.citizenship.cooperation.checkpoint.checkpoint",
                "lesson.g1.world.traffic.pedestrian_safety.practice.guided",
                "lesson.g1.world.traffic.pedestrian_safety.checkpoint.checkpoint",
                "lesson.g2.math.operations.expansion.practice.guided",
                "lesson.g2.math.operations.expansion.checkpoint.checkpoint",
                "lesson.g2.geometry.measurement_context.practice.guided",
                "lesson.g2.geometry.measurement_context.checkpoint.checkpoint",
                "lesson.g2.math.statistics.introduction.practice.guided",
                "lesson.g2.math.statistics.introduction.checkpoint.checkpoint",
                "lesson.g2.world.integrated_foundations.practice.guided",
                "lesson.g2.world.integrated_foundations.checkpoint.checkpoint",
                "lesson.g2.language.technical_reading.fluency.practice.guided",
                "lesson.g2.language.technical_reading.fluency.checkpoint.checkpoint",
                "lesson.g2.spelling.rules.punctuation_syllables_plural.practice.guided",
                "lesson.g2.spelling.rules.punctuation_syllables_plural.checkpoint.checkpoint",
                "lesson.g2.language.writing.short_texts.practice.guided",
                "lesson.g2.language.writing.short_texts.checkpoint.checkpoint",
                "lesson.g2.language.listening.extended_instructions.practice.guided",
                "lesson.g2.language.listening.extended_instructions.checkpoint.checkpoint",
                "lesson.g2.world.digital.media_safety.practice.guided",
                "lesson.g2.world.digital.media_safety.checkpoint.checkpoint",
                "lesson.g2.world.citizenship.emotions_conflicts.practice.guided",
                "lesson.g2.world.citizenship.emotions_conflicts.checkpoint.checkpoint",
                "lesson.g3.language.reading_comprehension.information.practice.guided",
                "lesson.g3.language.reading_comprehension.information.checkpoint.checkpoint",
                "lesson.g3.language.information_skills.lookup.practice.guided",
                "lesson.g3.language.information_skills.lookup.checkpoint.checkpoint",
                "lesson.g3.language.writing.functional_texts.practice.guided",
                "lesson.g3.language.writing.functional_texts.checkpoint.checkpoint",
                "lesson.g3.math.statistics.compare_data.practice.guided",
                "lesson.g3.math.statistics.compare_data.checkpoint.checkpoint",
                "lesson.g3.math.multiplication_division_fractions.practice.guided",
                "lesson.g3.math.multiplication_division_fractions.checkpoint.checkpoint",
                "lesson.g3.geometry.money_time.practice.guided",
                "lesson.g3.geometry.money_time.checkpoint.checkpoint",
                "lesson.g3.language.reading_strategies.summarise_context.practice.guided",
                "lesson.g3.language.reading_strategies.summarise_context.checkpoint.checkpoint",
                "lesson.g3.world.time_space_nature_society.practice.guided",
                "lesson.g3.world.time_space_nature_society.checkpoint.checkpoint",
                "lesson.g3.world.citizenship.responsibility.practice.guided",
                "lesson.g3.world.citizenship.responsibility.checkpoint.checkpoint",
                "lesson.g3.language.modern_languages.initiation.practice.guided",
                "lesson.g3.language.modern_languages.initiation.checkpoint.checkpoint",
                "lesson.g4.language.integrated_comprehension_interaction.practice.guided",
                "lesson.g4.language.integrated_comprehension_interaction.checkpoint.checkpoint",
                "lesson.g4.math.place_value.operations.practice.guided",
                "lesson.g4.math.place_value.operations.checkpoint.checkpoint",
                "lesson.g4.geometry.metric_measurement.practice.guided",
                "lesson.g4.geometry.metric_measurement.checkpoint.checkpoint",
                "lesson.g4.geometry.temperature_scale.practice.guided",
                "lesson.g4.geometry.temperature_scale.checkpoint.checkpoint",
                "lesson.g4.math.statistics.problem_solving.practice.guided",
                "lesson.g4.math.statistics.problem_solving.checkpoint.checkpoint",
                "lesson.g4.world.science.inquiry_design.practice.guided",
                "lesson.g4.world.science.inquiry_design.checkpoint.checkpoint",
                "lesson.g4.world.geography_history.practice.guided",
                "lesson.g4.world.geography_history.checkpoint.checkpoint",
                "lesson.g4.world.digital_social_self_regulation.practice.guided",
                "lesson.g4.world.digital_social_self_regulation.checkpoint.checkpoint",
                "lesson.g4.language.modern_languages.initiation.practice.guided",
                "lesson.g4.language.modern_languages.initiation.checkpoint.checkpoint",
                "lesson.g5.language.reading_listening_strategies.practice.guided",
                "lesson.g5.language.reading_listening_strategies.checkpoint.checkpoint",
                "lesson.g5.language.listening.evaluate_information.practice.guided",
                "lesson.g5.language.listening.evaluate_information.checkpoint.checkpoint",
                "lesson.g5.language.writing.structure_revision.practice.guided",
                "lesson.g5.language.writing.structure_revision.checkpoint.checkpoint",
                "lesson.g5.math.fractions_decimals_ratios.practice.guided",
                "lesson.g5.math.fractions_decimals_ratios.checkpoint.checkpoint",
                "lesson.g5.math.problem_solving.model_strategies.practice.guided",
                "lesson.g5.math.problem_solving.model_strategies.checkpoint.checkpoint",
                "lesson.g5.geometry.scale_speed_distance_time.practice.guided",
                "lesson.g5.geometry.scale_speed_distance_time.checkpoint.checkpoint",
                "lesson.g5.language.french.integrated_skills.practice.guided",
                "lesson.g5.language.french.integrated_skills.checkpoint.checkpoint",
                "lesson.g5.spelling.french.grammar_spelling.practice.guided",
                "lesson.g5.spelling.french.grammar_spelling.checkpoint.checkpoint",
                "lesson.g5.world.integrated_geography_history_science.practice.guided",
                "lesson.g5.world.integrated_geography_history_science.checkpoint.checkpoint",
                "lesson.g5.world.digital.information_production.practice.guided",
                "lesson.g5.world.digital.information_production.checkpoint.checkpoint",
                "lesson.g5.world.health_social_learning.practice.guided",
                "lesson.g5.world.health_social_learning.checkpoint.checkpoint",
                "lesson.g6.language.critical_reading.arguments.practice.guided",
                "lesson.g6.language.critical_reading.arguments.checkpoint.checkpoint",
                "lesson.g6.language.writing.structured_genres.practice.guided",
                "lesson.g6.language.writing.structured_genres.checkpoint.checkpoint",
                "lesson.g6.language.speaking.presentation_arguments.practice.guided",
                "lesson.g6.language.speaking.presentation_arguments.checkpoint.checkpoint",
                "lesson.g6.math.large_numbers_ratios_percentages.practice.guided",
                "lesson.g6.math.large_numbers_ratios_percentages.checkpoint.checkpoint",
                "lesson.g6.geometry.scale.compound_measurement.practice.guided",
                "lesson.g6.geometry.scale.compound_measurement.checkpoint.checkpoint",
                "lesson.g6.math.statistics.mean_median.practice.guided",
                "lesson.g6.math.statistics.mean_median.checkpoint.checkpoint",
                "lesson.g6.math.problem_solving.multi_step.practice.guided",
                "lesson.g6.math.problem_solving.multi_step.checkpoint.checkpoint",
                "lesson.g6.language.french.functional_skills.practice.guided",
                "lesson.g6.language.french.functional_skills.checkpoint.checkpoint",
                "lesson.g6.spelling.french.grammar_vocabulary.practice.guided",
                "lesson.g6.spelling.french.grammar_vocabulary.checkpoint.checkpoint",
                "lesson.g6.world.integrated.sources_models.practice.guided",
                "lesson.g6.world.integrated.sources_models.checkpoint.checkpoint",
                "lesson.g6.world.digital.critical_safe_use.practice.guided",
                "lesson.g6.world.digital.critical_safe_use.checkpoint.checkpoint",
                "lesson.g6.world.social_self_regulation.practice.guided",
                "lesson.g6.world.social_self_regulation.checkpoint.checkpoint"
        )
        assertTrue(approved.mapTo(linkedSetOf()) { it.sectionId }.containsAll(originalSectionIds))

        approved.forEach { located ->
            assertTrue(registry.supports(
                located.source.generatorId,
                located.source.generatorVersion
            ))
            val result = registry.materialize(
                source = located.source,
                context = LessonGenerationContext(
                    profileId = "manifest-test-profile",
                    unitId = located.unitId,
                    lessonId = located.lessonId,
                    sectionId = located.sectionId,
                    attemptId = "manifest-test-${located.sectionId}"
                )
            )
            assertTrue(
                "Expected approved source ${located.sectionId} to generate, received $result",
                result is LessonGenerationResult.Success
            )
            assertEquals(
                located.source.exerciseCount,
                (result as LessonGenerationResult.Success).exercises.size
            )
        }
    }

    @Test
    fun `non approved contracts remain fail closed`() {
        val approved = manifest().lessonCatalog.units
            .flatMap { unit ->
                unit.lessons.flatMap { lesson ->
                    lesson.sections.mapNotNull { section ->
                        (section.activitySource as? LessonActivitySource.Generated)
                            ?.takeIf { it.parameters["editorial_status"] == "approved" }
                    }
                }
            }
            .first()
        val planned = approved.copy(
            parameters = approved.parameters + ("editorial_status" to "planned")
        )

        val result = LessonGeneratorRegistry.default().materialize(
            source = planned,
            context = LessonGenerationContext(
                profileId = "profile",
                unitId = "unit",
                lessonId = "lesson",
                sectionId = "section",
                attemptId = "attempt"
            )
        )

        assertTrue(result is LessonGenerationResult.Rejected)
        assertEquals(
            LessonGenerationRejectionCode.NOT_APPROVED,
            (result as LessonGenerationResult.Rejected).code
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

    private data class LocatedSource(
        val unitId: String,
        val lessonId: String,
        val sectionId: String,
        val source: LessonActivitySource.Generated
    )
}
