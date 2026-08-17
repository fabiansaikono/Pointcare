package be.saikono.poincare.domain.curriculum.generation

import be.saikono.poincare.model.LearningDomain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class Grade2AtomicCurriculumGeneratorTest {
    @Test
    fun `atomic generator is deterministic lesson bound and content valid`() {
        val parameters = parameters(
            atomKey = "optellen-met-brug",
            mode = "math_add_bridge",
            title = "Optellen tot 100 met brug",
            knowledge = "Bij een brug maak je eerst het volgende volle tiental.",
            skill = "optellen via het volgende volle tiental"
        )
        val seed = LessonGenerationSeed(42L, "000000000000002a")
        val first = Grade2AtomicCurriculumGenerator.generate(seed, 10, parameters)
        val repeated = Grade2AtomicCurriculumGenerator.generate(seed, 10, parameters)

        assertEquals(first, repeated)
        assertEquals(10, first.size)
        assertEquals(10, first.map { it.id }.distinct().size)
        assertTrue(first.all { it.domain == LearningDomain.MATH })
        assertTrue(first.all { it.data["atomicLessonKey"] == "optellen-met-brug" })
        assertTrue(first.all { GeneratedExerciseContentValidator.validate(it).isEmpty() })
        assertTrue(first.map { it.prompt.nl }.distinct().size > 5)
    }

    @Test
    fun `different atomic lesson parameters produce different content`() {
        val seed = LessonGenerationSeed(81L, "0000000000000051")
        val reading = Grade2AtomicCurriculumGenerator.generate(
            seed,
            5,
            parameters(
                atomKey = "personages-en-sfeer",
                mode = "literature",
                title = "Personages en sfeer",
                knowledge = "Woorden en beelden geven aanwijzingen over een personage en de sfeer.",
                skill = "personages en sfeer uit tekst en beeld afleiden"
            )
        )
        val science = Grade2AtomicCurriculumGenerator.generate(
            seed,
            5,
            parameters(
                atomKey = "licht-en-schaduw",
                mode = "science_light",
                title = "Licht en schaduw",
                knowledge = "Een schaduw ontstaat wanneer licht wordt tegengehouden.",
                skill = "voorspellen hoe een schaduw ontstaat"
            )
        )

        assertTrue(reading.map { it.prompt.nl } != science.map { it.prompt.nl })
        assertTrue(reading.all { it.domain == LearningDomain.READING })
        assertTrue(science.all { it.domain == LearningDomain.WORLD_KNOWLEDGE })
    }

    @Test
    fun `invalid or generic parameter bundles are rejected`() {
        val issues = Grade2AtomicCurriculumGenerator.validate(
            parameters("", "concept", "", "", "") - "vocabulary_nl"
        )
        assertTrue(issues.isNotEmpty())
        assertTrue(issues.any { it.contains("atom_key") })
        assertTrue(issues.any { it.contains("vocabulary_nl") })
    }

    @Test
    fun `reviewed Dutch pool replaces generic concept questions`() {
        val pool = listOf(
            "Welke zin klopt?", "Een lettergreep is een stukje dat je in één klap zegt.",
            "Ik sla een stukje over.", "Ik verander letters.", "Ik kijk alleen naar de prent.",
            "Je leest elk woordstuk en plakt ze daarna samen."
        ).joinToString("\u001f")
        val generated = Grade2AtomicCurriculumGenerator.generate(
            LessonGenerationSeed(92L, "000000000000005c"),
            1,
            parameters("lettergrepen", "language_reading", "Lettergrepen", "Lees alle delen.", "woorden samenvoegen") +
                ("question_pool_nl" to pool)
        ).single()

        assertEquals("Welke zin klopt?", generated.prompt.nl)
        assertEquals("Een lettergreep is een stukje dat je in één klap zegt.", generated.answer)
        assertTrue("Welke uitspraak past bij" !in generated.prompt.nl)
    }

    private fun parameters(
        atomKey: String,
        mode: String,
        title: String,
        knowledge: String,
        skill: String
    ): Map<String, String> = mapOf(
        "grade" to "2",
        "roadmap_entry" to "roadmap.g2.test.$atomKey",
        "phase" to "checkpoint",
        "editorial_status" to "approved",
        "atom_key" to atomKey,
        "mode" to mode,
        "title_nl" to title,
        "knowledge_nl" to knowledge,
        "skill_nl" to skill,
        "vocabulary_nl" to "begrip|voorbeeld|controle",
        "models_nl" to "stappenplan|schema",
        "distractors_nl" to "Onjuiste uitspraak één.|Onjuiste uitspraak twee.|Onjuiste uitspraak drie."
    )
}
