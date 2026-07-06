package be.saikono.poincare.data.content

import be.saikono.poincare.model.*
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class ContentParsingTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `test exercise content json parsing`() {
        val jsonString = """
            {
                "id": "math_001",
                "domain": "MATH",
                "skill": "addition",
                "section": "EXERCISES",
                "level": "FIRST_GRADE",
                "inputMode": "CUSTOM_NUMBER_PAD",
                "prompt": {
                    "nl": "Hoeveel is 1 + 1?",
                    "en": "What is 1 + 1?"
                },
                "data": {
                    "op1": "1",
                    "op2": "1"
                },
                "answer": "2",
                "rewardMetadata": {
                    "stamps": 1
                }
            }
        """.trimIndent()

        val exercise = json.decodeFromString<ExerciseContent>(jsonString)
        
        assertEquals("math_001", exercise.id)
        assertEquals(LearningDomain.MATH, exercise.domain)
        assertEquals(SkillLevel.FIRST_GRADE, exercise.level)
        assertEquals("Hoeveel is 1 + 1?", exercise.prompt.nl)
        assertEquals("2", exercise.answer)
        assertEquals(1, exercise.rewardMetadata.stamps)
    }

    @Test
    fun `test list of exercises parsing`() {
        val jsonString = """
            [
                {
                    "id": "ex1",
                    "domain": "READING",
                    "skill": "phonics",
                    "section": "PRACTICE",
                    "level": "FIRST_GRADE",
                    "inputMode": "LISTEN_AND_SELECT",
                    "prompt": { "nl": "Vraag 1" },
                    "data": {},
                    "answer": "a"
                },
                {
                    "id": "ex2",
                    "domain": "READING",
                    "skill": "phonics",
                    "section": "PRACTICE",
                    "level": "FIRST_GRADE",
                    "inputMode": "LISTEN_AND_SELECT",
                    "prompt": { "nl": "Vraag 2" },
                    "data": {},
                    "answer": "b"
                }
            ]
        """.trimIndent()

        val exercises = json.decodeFromString<List<ExerciseContent>>(jsonString)
        assertEquals(2, exercises.size)
        assertEquals("ex1", exercises[0].id)
        assertEquals("ex2", exercises[1].id)
    }
}
