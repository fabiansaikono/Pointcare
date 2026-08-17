package be.saikono.poincare.ui.lesson

import be.saikono.poincare.domain.curriculum.ParentSupportLevel
import be.saikono.poincare.domain.curriculum.manifest.CurriculumManifestLoader
import be.saikono.poincare.model.LearningDomain
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LessonInteractionPolicyTest {
    @Test
    fun `adult-supported choice reads once and confirms only on the repeated tap`() {
        assertEquals(
            AssistedChoiceTapAction.READ,
            assistedChoiceTapAction(2, ParentSupportLevel.RECOMMENDED, null, "antwoord")
        )
        assertEquals(
            AssistedChoiceTapAction.READ,
            assistedChoiceTapAction(2, ParentSupportLevel.REQUIRED, "ander", "antwoord")
        )
        assertEquals(
            AssistedChoiceTapAction.CONFIRM,
            assistedChoiceTapAction(2, ParentSupportLevel.REQUIRED, "antwoord", "antwoord")
        )
    }

    @Test
    fun `grade one choices are read first and confirmed on the repeated tap`() {
        assertEquals(
            AssistedChoiceTapAction.READ,
            assistedChoiceTapAction(1, ParentSupportLevel.NONE, null, "antwoord")
        )
        assertEquals(
            AssistedChoiceTapAction.CONFIRM,
            assistedChoiceTapAction(1, ParentSupportLevel.NONE, "antwoord", "antwoord")
        )
    }

    @Test
    fun `grade two ordinary choices keep one-tap confirmation`() {
        assertEquals(
            AssistedChoiceTapAction.CONFIRM,
            assistedChoiceTapAction(2, ParentSupportLevel.NONE, null, "antwoord")
        )
    }

    @Test
    fun `answer reveal follows the configured wrong answer allowance`() {
        assertEquals(
            LessonAnswerAttemptOutcome(1, revealCorrectAnswer = false, finalized = false),
            lessonAnswerAttemptOutcome(isCorrect = false, currentWrongAnswerCount = 0, wrongAnswersAllowed = 2)
        )
        assertEquals(
            LessonAnswerAttemptOutcome(2, revealCorrectAnswer = true, finalized = true),
            lessonAnswerAttemptOutcome(isCorrect = false, currentWrongAnswerCount = 1, wrongAnswersAllowed = 2)
        )
        assertEquals(
            LessonAnswerAttemptOutcome(1, revealCorrectAnswer = true, finalized = true),
            lessonAnswerAttemptOutcome(isCorrect = false, currentWrongAnswerCount = 0, wrongAnswersAllowed = 1)
        )
        assertEquals(
            LessonAnswerAttemptOutcome(1, revealCorrectAnswer = true, finalized = true),
            lessonAnswerAttemptOutcome(isCorrect = true, currentWrongAnswerCount = 1, wrongAnswersAllowed = 2)
        )
    }

    @Test
    fun `legacy reading tile resolves every matching curriculum subcategory`() {
        val file = listOf(
            File("src/main/assets/${CurriculumManifestLoader.ASSET_PATH}"),
            File("app/src/main/assets/${CurriculumManifestLoader.ASSET_PATH}")
        ).first { it.exists() }
        val manifest = file.inputStream().use(CurriculumManifestLoader::fromStream)

        val matches = manifest.subcategoryIdsForLegacyTile(LearningDomain.READING, "reading")

        assertEquals(5, matches.size)
        assertTrue("language.early_literacy" in matches)
        assertTrue("language.technical_reading" in matches)
        assertTrue("language.reading_comprehension" in matches)
        assertTrue("language.reading_strategies_information" in matches)
        assertTrue("language.literature_experience" in matches)
    }

    @Test
    fun `listening tile excludes reading-only early literacy and keeps listening lessons`() {
        val file = listOf(
            File("src/main/assets/${CurriculumManifestLoader.ASSET_PATH}"),
            File("app/src/main/assets/${CurriculumManifestLoader.ASSET_PATH}")
        ).first { it.exists() }
        val manifest = file.inputStream().use(CurriculumManifestLoader::fromStream)

        val matches = manifest.subcategoryIdsForLegacyTile(LearningDomain.READING, "listening")

        assertEquals(setOf("language.listening_comprehension", "language.listening_speaking"), matches)
        assertFalse("language.early_literacy" in matches)
        assertTrue(
            manifest.lessonCatalog.units.any {
                it.id == "unit.g1.language.listening.instructions_stories" &&
                    manifest.unitMatchesLegacyTile(it, LearningDomain.READING, "listening")
            }
        )
        assertFalse(
            manifest.lessonCatalog.units.any {
                it.id == "unit.g1.language.early_literacy.story_awareness" &&
                    manifest.unitMatchesLegacyTile(it, LearningDomain.READING, "listening")
            }
        )
    }

    @Test
    fun `grade one addition and subtraction lessons have separate routes`() {
        val file = listOf(
            File("src/main/assets/${CurriculumManifestLoader.ASSET_PATH}"),
            File("app/src/main/assets/${CurriculumManifestLoader.ASSET_PATH}")
        ).first { it.exists() }
        val manifest = file.inputStream().use(CurriculumManifestLoader::fromStream)
        val gradeOneOperations = manifest.lessonCatalog.units.single {
            it.id == "math.addition.g1.foundations"
        }

        assertTrue(
            manifest.unitMatchesLegacyTile(
                gradeOneOperations,
                LearningDomain.MATH,
                "addition"
            )
        )
        assertFalse(
            manifest.unitMatchesLegacyTile(
                gradeOneOperations,
                LearningDomain.MATH,
                "subtraction"
            )
        )
        val subtractionLesson = gradeOneOperations.lessons.single {
            it.id == "lesson.math.subtraction.g1.within20"
        }
        assertTrue(
            manifest.lessonMatchesLegacyTile(
                gradeOneOperations,
                subtractionLesson,
                LearningDomain.MATH,
                "subtraction"
            )
        )
        assertFalse(
            manifest.lessonMatchesLegacyTile(
                gradeOneOperations,
                subtractionLesson,
                LearningDomain.MATH,
                "addition"
            )
        )
    }

    @Test
    fun `every measurement unit has exactly one primary tile`() {
        val manifest = manifest()
        val gradeOne = manifest.lessonCatalog.units.single {
            it.id == "unit.g1.geometry.measurement_shapes.foundations"
        }
        val gradeTwo = manifest.lessonCatalog.units.single {
            it.id == "unit.g2.geometry.measurement_context"
        }
        val gradeFourMetric = manifest.lessonCatalog.units.single {
            it.id == "unit.g4.geometry.metric_measurement"
        }
        val gradeFourTemperature = manifest.lessonCatalog.units.single {
            it.id == "unit.g4.geometry.temperature_scale"
        }

        assertTrue(manifest.unitMatchesLegacyTile(gradeOne, LearningDomain.GEOMETRY, "geometry_shapes"))
        assertFalse(manifest.unitMatchesLegacyTile(gradeOne, LearningDomain.GEOMETRY, "time"))
        assertFalse(manifest.unitMatchesLegacyTile(gradeOne, LearningDomain.GEOMETRY, "compare_size"))
        assertTrue(manifest.unitMatchesLegacyTile(gradeTwo, LearningDomain.GEOMETRY, "compare_size"))
        assertFalse(manifest.unitMatchesLegacyTile(gradeTwo, LearningDomain.GEOMETRY, "time"))
        assertFalse(manifest.unitMatchesLegacyTile(gradeTwo, LearningDomain.GEOMETRY, "measurement"))
        assertTrue(manifest.unitMatchesLegacyTile(gradeFourMetric, LearningDomain.GEOMETRY, "measurement"))
        assertTrue(manifest.unitMatchesLegacyTile(gradeFourTemperature, LearningDomain.GEOMETRY, "temperature"))
    }

    @Test
    fun `dictation spelling and writing routes are never bridged`() {
        val manifest = manifest()
        val phonology = manifest.lessonCatalog.units.single {
            it.id == "unit.g1.spelling.phonology.sound_letter"
        }
        val writing = manifest.lessonCatalog.units.single {
            it.id == "unit.g1.language.writing.letters_sentences"
        }

        assertTrue(manifest.unitMatchesLegacyTile(phonology, LearningDomain.READING, "dictation"))
        assertFalse(manifest.unitMatchesLegacyTile(phonology, LearningDomain.WRITING, "spelling"))
        assertFalse(manifest.unitMatchesLegacyTile(writing, LearningDomain.WRITING, "letter_tracing"))
        assertTrue(manifest.unitMatchesLegacyTile(writing, LearningDomain.WRITING, "writing"))
        assertFalse(manifest.unitMatchesLegacyTile(writing, LearningDomain.READING, "writing"))
    }

    @Test
    fun `all reviewed units resolve to one visible tile only`() {
        val manifest = manifest()
        val visibleTiles = LearningDomain.entries.flatMap { domain ->
            be.saikono.poincare.ui.home.getSubCategoriesForDomain(domain.name)
                .map { domain to it.id }
        }

        manifest.lessonCatalog.units.forEach { unit ->
            val routes = visibleTiles.filter { (domain, tileId) ->
                manifest.unitMatchesLegacyTile(unit, domain, tileId)
            }
            assertEquals("${unit.id} must have exactly one primary tile", 1, routes.size)
        }
    }

    @Test
    fun `roadmap expansion does not recombine world knowledge tiles`() {
        val manifest = manifest()
        val integratedWorld = manifest.lessonCatalog.units.single {
            it.id == "unit.g1.world.environment_science"
        }

        assertTrue(
            manifest.unitMatchesLegacyTile(
                integratedWorld,
                LearningDomain.WORLD_KNOWLEDGE,
                "nature"
            )
        )
        assertFalse(
            manifest.unitMatchesLegacyTile(
                integratedWorld,
                LearningDomain.WORLD_KNOWLEDGE,
                "science"
            )
        )
        assertFalse(
            manifest.unitMatchesLegacyTile(
                integratedWorld,
                LearningDomain.WORLD_KNOWLEDGE,
                "technology"
            )
        )
        assertFalse(
            manifest.unitMatchesLegacyTile(
                integratedWorld,
                LearningDomain.WORLD_KNOWLEDGE,
                "everyday_life"
            )
        )
    }

    private fun manifest() = listOf(
        File("src/main/assets/${CurriculumManifestLoader.ASSET_PATH}"),
        File("app/src/main/assets/${CurriculumManifestLoader.ASSET_PATH}")
    ).first { it.exists() }.inputStream().use(CurriculumManifestLoader::fromStream)
}
