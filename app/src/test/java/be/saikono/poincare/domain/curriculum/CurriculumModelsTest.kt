package be.saikono.poincare.domain.curriculum

import be.saikono.poincare.model.LearningDomain
import be.saikono.poincare.model.LocalizedText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class CurriculumModelsTest {
    @Test
    fun `visible and spoken lesson text remain separate`() {
        val text = LessonText(
            visible = LocalizedText(
                nl = "Hoeveel is 1/3 van 12?",
                fr = "Combien vaut 1/3 de 12 ?",
                en = "What is 1/3 of 12?"
            ),
            spoken = LocalizedText(
                nl = "Hoeveel is één derde van twaalf?",
                fr = "Combien vaut un tiers de douze ?",
                en = "What is one third of twelve?"
            )
        )

        assertEquals("Hoeveel is 1/3 van 12?", text.visibleFor("nl-BE"))
        assertEquals("Hoeveel is één derde van twaalf?", text.spokenFor("nl-BE"))
    }

    @Test
    fun `spoken text falls back to unmodified visible source text`() {
        val text = LessonText(
            visible = LocalizedText(
                nl = "5 + 4",
                fr = "5 + 4",
                en = "5 + 4"
            )
        )

        assertEquals("5 + 4", text.spokenFor("nl-BE"))
        assertNull(text.spoken)
    }

    @Test
    fun `catalog lookup helpers keep stable definitions connected`() {
        val catalog = validCatalog()
        val unit = catalog.units.single()
        val lesson = unit.lessons.single()
        val goal = unit.learningGoals.single()

        assertSame(unit, catalog.findUnit(unit.id))
        assertSame(lesson, catalog.findLesson(lesson.id))
        assertSame(goal, catalog.findLearningGoal(goal.id))
        assertEquals(listOf(lesson), catalog.allLessons())
    }

    private fun validCatalog(): CurriculumCatalog {
        val goalId = "goal.math.number-sense.g1.count"
        val lesson = LessonDefinition(
            id = "lesson.math.number-sense.g1.001",
            grade = 1,
            sequence = 1,
            title = text("Tellen tot 20", "Compter jusqu'à 20", "Count to 20"),
            learningGoalIds = listOf(goalId),
            mastery = LessonMasteryCriteria(requiredSectionIds = listOf("checkpoint")),
            sections = listOf(
                LessonSection(
                    id = "explanation",
                    type = LessonSectionType.EXPLANATION,
                    content = text("We tellen stap voor stap.", "Nous comptons pas à pas.", "We count step by step.")
                ),
                LessonSection(
                    id = "checkpoint",
                    type = LessonSectionType.CHECKPOINT,
                    activitySource = LessonActivitySource.Generated(
                        generatorId = "generator.math.counting",
                        exerciseCount = 5
                    )
                )
            )
        )
        return CurriculumCatalog(
            units = listOf(
                CurriculumUnit(
                    id = "math.number-sense.g1",
                    domain = LearningDomain.MATH,
                    subcategoryId = "numbers-20",
                    grades = listOf(1),
                    title = text("Getalbegrip", "Sens des nombres", "Number sense"),
                    learningGoals = listOf(
                        LearningGoalDefinition(
                            id = goalId,
                            title = text("Tellen tot 20", "Compter jusqu'à 20", "Count to 20")
                        )
                    ),
                    lessons = listOf(lesson)
                )
            )
        )
    }

    private fun text(nl: String, fr: String, en: String): LessonText = LessonText(
        visible = LocalizedText(nl = nl, fr = fr, en = en)
    )
}
