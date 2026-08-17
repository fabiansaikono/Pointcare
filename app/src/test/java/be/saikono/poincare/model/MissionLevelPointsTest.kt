package be.saikono.poincare.model

import be.saikono.poincare.ui.home.localizedSubcategoryName
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MissionLevelPointsTest {
    @Test
    fun `wrong answers never award level points`() {
        assertEquals(0, missionPointsFor(exercise(InputMode.SUDOKU_GRID, grade = 6, stars = 3), correct = false))
    }

    @Test
    fun `correct points increase with difficulty grade and input complexity but stay bounded`() {
        val easy = missionPointsFor(exercise(InputMode.TAP_CHOICE, grade = 1, stars = 1), correct = true)
        val medium = missionPointsFor(exercise(InputMode.CUSTOM_NUMBER_PAD, grade = 3, stars = 2), correct = true)
        val hard = missionPointsFor(exercise(InputMode.SUDOKU_GRID, grade = 6, stars = 3), correct = true)

        assertEquals(1, easy)
        assertTrue(medium > easy)
        assertTrue(hard > medium)
        assertEquals(MAX_LEVEL_POINTS_PER_ANSWER, hard)
    }

    @Test
    fun `historical oversized rewards are capped for level display and wrong events count zero`() {
        val correct = progress(answerCorrect = true, reward = 50)
        val wrong = progress(answerCorrect = false, reward = 50)

        assertEquals(MAX_LEVEL_POINTS_PER_ANSWER, correct.levelPointsEarned())
        assertEquals(0, wrong.levelPointsEarned())
    }

    @Test
    fun `puzzle grade sources are math for number puzzles and world knowledge otherwise`() {
        assertEquals(LearningDomain.MATH, puzzleGradeSourceDomain("sudoku"))
        assertEquals(LearningDomain.MATH, puzzleGradeSourceDomain("math-crosswords"))
        assertEquals(LearningDomain.MATH, puzzleGradeSourceDomain("calculator-puzzle"))
        assertEquals(LearningDomain.WORLD_KNOWLEDGE, puzzleGradeSourceDomain("word-seeker"))
        assertEquals(LearningDomain.WORLD_KNOWLEDGE, puzzleGradeSourceDomain("constellations"))
        assertEquals(LearningDomain.WORLD_KNOWLEDGE, puzzleGradeSourceDomain("galaxycon"))
    }

    @Test
    fun `legacy subcategory title remains natural Dutch when several curriculum groups match`() {
        assertEquals("Lezen", localizedSubcategoryName("reading", "nl-BE"))
    }

    private fun exercise(inputMode: InputMode, grade: Int, stars: Int) = ExerciseContent(
        id = "exercise-$inputMode-$grade-$stars",
        domain = LearningDomain.MATH,
        skill = "test",
        section = ExerciseSection.EXERCISES,
        level = skillLevelForGrade(grade),
        inputMode = inputMode,
        prompt = LocalizedText("Vraag", "Question", "Question"),
        data = mapOf("grade" to grade.toString(), "difficultyStars" to stars.toString()),
        answer = "1"
    )

    private fun progress(answerCorrect: Boolean, reward: Int) = ProgressEvent(
        id = "progress-$answerCorrect-$reward",
        childProfileId = "child",
        exerciseId = "exercise",
        domain = LearningDomain.MATH,
        skill = "test",
        timestamp = 1L,
        answerCorrect = answerCorrect,
        responseTimeMs = 100L,
        rewardStampsEarned = reward
    )
}
