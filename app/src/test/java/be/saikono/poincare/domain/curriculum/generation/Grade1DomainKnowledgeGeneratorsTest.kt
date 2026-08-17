package be.saikono.poincare.domain.curriculum.generation

import be.saikono.poincare.domain.curriculum.LessonActivitySource
import be.saikono.poincare.domain.curriculum.LessonSeedPolicy
import be.saikono.poincare.model.InputMode
import be.saikono.poincare.model.LearningDomain
import be.saikono.poincare.model.localizedAnswer
import be.saikono.poincare.model.localizedOptions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class Grade1DomainKnowledgeGeneratorsTest {
    private val registry = LessonGeneratorRegistry.default()

    @Test
    fun `math concept generator is deterministic localized and creates ten distinct question forms`() {
        val parameters = parameters(
            unitId = "unit.g1.math.fractions.helft-dubbel-en-kwart-in-concrete-situaties",
            domain = LearningDomain.MATH,
            phase = "test"
        )
        val first = success(Grade1MathConceptsGenerator, parameters, "math-attempt")
        val replay = success(Grade1MathConceptsGenerator, parameters, "math-attempt")
        val changed = success(Grade1MathConceptsGenerator, parameters, "math-attempt-2")

        assertEquals(first, replay)
        assertNotEquals(first.seed, changed.seed)
        assertEquals(InputMode.TAP_CHOICE, first.exercises.first().inputMode)
        assertEquals(10, first.exercises.map { it.data.getValue("promptKind") }.distinct().size)
        assertEquals(
            setOf("mathematical_concept_strategy_choice"),
            first.exercises.map { it.data.getValue("automatedScope") }.toSet()
        )
        assertLocalizedChoices(first)
    }

    @Test
    fun `French generator assesses receptive recognition and leaves pronunciation to observation`() {
        val parameters = parameters(
            unitId = "unit.g1.fr.listening.franse-klanken-en-begroetingen",
            domain = LearningDomain.READING,
            phase = "guided"
        )
        val result = success(Grade1FrenchBasicsGenerator, parameters, "french-attempt")

        result.exercises.forEach { exercise ->
            assertEquals(InputMode.LISTEN_AND_SELECT, exercise.inputMode)
            assertEquals("french_word_phrase_receptive_recognition", exercise.data["automatedScope"])
            assertEquals("true", exercise.data["requiresAdultPronunciationParticipationObservation"])
            assertTrue("pronunciationScore" !in exercise.data)
            assertTrue("speakingMastery" !in exercise.data)
        }
        assertLocalizedChoices(result)
    }

    @Test
    fun `applied domains use subject-specific adult observation boundaries`() {
        val units = mapOf(
            "unit.g1.geo.maps.mijn-plaats-in-klas-en-school" to "requiresAdultObservationConversation",
            "unit.g1.history.sources.wat-vertelt-een-bron" to "requiresAdultObservationConversation",
            "unit.g1.ict.devices_data.toestellen-en-basisbediening" to "requiresAdultDeviceUseObservation",
            "unit.g1.arts.music.stem-puls-en-klankkleur" to "requiresAdultPerformanceObservation",
            "unit.g1.pe.ball_games.gooien-vangen-rollen-en-mikken" to "requiresAdultPerformanceObservation"
        )

        units.forEach { (unitId, observationKey) ->
            val result = success(
                Grade1AppliedDomainsGenerator,
                parameters(unitId, LearningDomain.WORLD_KNOWLEDGE, "independent"),
                "applied-${unitId.hashCode()}"
            )
            result.exercises.forEach { exercise ->
                assertEquals("concept_safety_sequence_recognition", exercise.data["automatedScope"])
                assertEquals("true", exercise.data[observationKey])
                assertTrue("practicalMasteryScore" !in exercise.data)
                assertTrue("performanceCompleted" !in exercise.data)
            }
            assertLocalizedChoices(result)
        }
    }

    @Test
    fun `generators reject units and domains outside their reviewed scope`() {
        assertTrue(
            Grade1MathConceptsGenerator.validate(
                parameters("unit.g1.fr.culture.liedjes-tellen-en-kleuren", LearningDomain.READING, "guided")
            ).isNotEmpty()
        )
        assertTrue(
            Grade1FrenchBasicsGenerator.validate(
                parameters("unit.g1.math.data.gegevens-verzamelen-en-voorstellen", LearningDomain.MATH, "guided")
            ).isNotEmpty()
        )
        assertTrue(
            Grade1AppliedDomainsGenerator.validate(
                parameters("unit.g1.math.fractions.helft-dubbel-en-kwart-in-concrete-situaties", LearningDomain.MATH, "guided")
            ).isNotEmpty()
        )
    }

    private fun success(
        generator: LessonExerciseGenerator,
        parameters: Map<String, String>,
        attemptId: String
    ): LessonGenerationResult.Success {
        val unitId = parameters.getValue("unit_id")
        val result = registry.materialize(
            LessonActivitySource.Generated(
                generatorId = generator.generatorId,
                generatorVersion = generator.generatorVersion,
                exerciseCount = 10,
                seedPolicy = LessonSeedPolicy.STABLE_PER_PROFILE_ATTEMPT,
                parameters = parameters
            ),
            LessonGenerationContext(
                profileId = "profile-grade1-domain-generators",
                unitId = unitId,
                lessonId = unitId.replace("unit.", "lesson."),
                sectionId = "section-${parameters.getValue("phase")}",
                canonicalDomain = LearningDomain.valueOf(parameters.getValue("domain")),
                canonicalSubcategoryId = parameters.getValue("skill"),
                attemptId = attemptId
            )
        )
        assertTrue("Expected success but received $result", result is LessonGenerationResult.Success)
        return result as LessonGenerationResult.Success
    }

    private fun assertLocalizedChoices(result: LessonGenerationResult.Success) {
        assertEquals(10, result.exercises.size)
        assertEquals(10, result.exercises.map { it.id }.distinct().size)
        assertEquals(10, result.exercises.map { it.data.getValue("contentKey") }.distinct().size)
        result.exercises.forEach { exercise ->
            listOf("nl-BE", "fr-BE", "en-US").forEach { locale ->
                val options = exercise.localizedOptions(locale)
                assertEquals(3, options.size)
                assertEquals(3, options.distinct().size)
                assertEquals(1, options.count { it == exercise.localizedAnswer(locale) })
            }
            assertTrue(exercise.prompt.nl.isNotBlank())
            assertTrue(exercise.prompt.fr?.isNotBlank() == true)
            assertTrue(exercise.prompt.en?.isNotBlank() == true)
        }
    }

    private fun parameters(
        unitId: String,
        domain: LearningDomain,
        phase: String
    ): Map<String, String> = linkedMapOf(
        "grade" to "1",
        "unit_id" to unitId,
        "domain" to domain.name,
        "skill" to "grade1_reviewed_skill",
        "phase" to phase,
        "editorial_status" to "approved",
        "title_nl" to "Een voorbeeldles",
        "title_fr" to "Une leçon exemple",
        "title_en" to "An example lesson",
        "focus_nl" to "Je begrijpt het kernidee van de les.",
        "focus_fr" to "Tu comprends l'idée principale de la leçon.",
        "focus_en" to "You understand the lesson's main idea.",
        "target_nl" to "Je past het kernidee toe.",
        "target_fr" to "Tu appliques l'idée principale.",
        "target_en" to "You apply the main idea.",
        "how_nl" to "Werk stap voor stap met het model.",
        "how_fr" to "Travaille étape par étape avec le modèle.",
        "how_en" to "Work step by step with the model.",
        "check_nl" to "Controleer je antwoord met de lesregel.",
        "check_fr" to "Vérifie ta réponse avec la règle de la leçon.",
        "check_en" to "Check your answer with the lesson rule.",
        "example_nl" to "Dit is een juist uitgewerkt voorbeeld.",
        "example_fr" to "Ceci est un exemple correctement réalisé.",
        "example_en" to "This is a correctly worked example.",
        "non_example_nl" to "Dit voorbeeld slaat de belangrijke stap over.",
        "non_example_fr" to "Cet exemple saute l'étape importante.",
        "non_example_en" to "This example skips the important step.",
        "terms_nl" to "kernwoord één|kernwoord twee|kernwoord drie",
        "terms_fr" to "premier mot-clé|deuxième mot-clé|troisième mot-clé",
        "terms_en" to "key word one|key word two|key word three",
        "models_nl" to "model één|model twee|model drie",
        "models_fr" to "premier modèle|deuxième modèle|troisième modèle",
        "models_en" to "model one|model two|model three",
        "distractor_focus_nl" to "Een andere lesregel|Een willekeurige uitkomst",
        "distractor_focus_fr" to "Une autre règle|Un résultat au hasard",
        "distractor_focus_en" to "Another lesson rule|A random result",
        "distractor_target_nl" to "Alleen kijken|Een stap overslaan",
        "distractor_target_fr" to "Seulement regarder|Sauter une étape",
        "distractor_target_en" to "Only look|Skip a step",
        "distractor_terms_nl" to "vreemd woord één|vreemd woord twee",
        "distractor_terms_fr" to "mot étranger un|mot étranger deux",
        "distractor_terms_en" to "unrelated word one|unrelated word two",
        "distractor_models_nl" to "verkeerd model één|verkeerd model twee",
        "distractor_models_fr" to "mauvais modèle un|mauvais modèle deux",
        "distractor_models_en" to "wrong model one|wrong model two"
    )
}
