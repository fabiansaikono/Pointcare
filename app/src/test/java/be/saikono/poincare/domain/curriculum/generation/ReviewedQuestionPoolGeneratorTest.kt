package be.saikono.poincare.domain.curriculum.generation

import be.saikono.poincare.model.localizedOptions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReviewedQuestionPoolGeneratorTest {
    @Test
    fun `editorial example-card context never reaches the child-facing question or answers`() {
        val record = listOf(
            "Zoë gebruikt aan de oefentafel een voorbeeldkaart met zichtbare steun. Tik het begrip aan dat erbij hoort.",
            "springen",
            "landen",
            "lopen",
            "Zoë gebruikt een voorbeeldkaart met zichtbare steun voor een andere opdracht dan de vraag.",
            "Springen is jezelf afzetten."
        ).joinToString("\u001f")

        val exercise = generate(record)

        assertEquals("Tik het begrip aan dat erbij hoort.", exercise.prompt.nl)
        assertFalse(exercise.localizedOptions("nl-BE").any { "voorbeeldkaart" in it.lowercase() })
        assertTrue("geen van deze begrippen" in exercise.localizedOptions("nl-BE"))
    }

    @Test
    fun `French greeting question has one unambiguous correct answer`() {
        val record = listOf(
            "Mila gebruikt bij het bord een voorbeeldkaart met zichtbare steun. Hoe zeg je ‘hallo’ in het Frans?",
            "salut",
            "bonjour",
            "bonsoir",
            "Mila gebruikt een voorbeeldkaart met zichtbare steun voor een andere opdracht dan de vraag.",
            "Je zegt ‘salut’."
        ).joinToString("\u001f")

        val exercise = generate(record)

        assertEquals("Welke informele Franse begroeting gebruik je voor iemand die je goed kent?", exercise.prompt.nl)
        assertEquals("salut", exercise.answer)
        assertEquals(listOf("salut", "au revoir", "merci", "s’il vous plaît").toSet(), exercise.localizedOptions("nl-BE").toSet())
    }

    @Test
    fun `all named child scaffolding is removed and answer choices stay unique`() {
        val record = listOf(
            "Mila gebruikt aan de oefentafel een stappenstrook met twee ingevulde stappen. Welke keuze past?",
            "juist",
            "juist",
            "juist",
            "juist",
            "Kies het passende antwoord."
        ).joinToString("\u001f")

        val exercise = generate(record)

        assertEquals("Welke keuze past?", exercise.prompt.nl)
        assertEquals(4, exercise.localizedOptions("nl-BE").map(String::lowercase).toSet().size)
    }

    private fun generate(record: String) = ReviewedQuestionPoolGenerator.generate(
        seed = LessonGenerationSeed(12L, "reviewed-pool-test"),
        exerciseCount = 1,
        parameters = mapOf(
            "grade" to "1",
            "editorial_status" to "approved",
            "domain" to "READING",
            "skill" to "vocabulary",
            "phase" to "guided",
            "question_pool_nl" to List(10) { record }.joinToString("\u001e")
        )
    ).single()
}
