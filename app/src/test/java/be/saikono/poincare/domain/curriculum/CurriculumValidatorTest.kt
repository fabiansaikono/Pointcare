package be.saikono.poincare.domain.curriculum

import be.saikono.poincare.model.LearningDomain
import be.saikono.poincare.model.LocalizedText
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CurriculumValidatorTest {
    @Test
    fun `release validation accepts a complete deterministic catalogue`() {
        val report = CurriculumValidator.validate(validCatalog(), CurriculumValidationMode.RELEASE)

        assertTrue(report.issues.joinToString("\n") { "${it.code}: ${it.message}" }, report.isValid)
        assertTrue(report.errors.isEmpty())
    }

    @Test
    fun `draft localization gaps are warnings but release localization gaps are errors`() {
        val catalog = validCatalog().let { current ->
            val unit = current.units.single()
            current.copy(
                units = listOf(
                    unit.copy(
                        title = LessonText(LocalizedText(nl = "Getalbegrip"))
                    )
                )
            )
        }

        val draft = CurriculumValidator.validate(catalog, CurriculumValidationMode.DRAFT)
        val release = CurriculumValidator.validate(catalog, CurriculumValidationMode.RELEASE)

        assertTrue(draft.isValid)
        assertTrue(draft.warnings.any { it.code == "missing_fr_text" })
        assertFalse(release.isValid)
        assertTrue(release.errors.any { it.code == "missing_fr_text" })
        assertTrue(release.errors.any { it.code == "missing_en_text" })
    }

    @Test
    fun `validator rejects duplicate IDs invalid grades and empty curated sources`() {
        val current = validCatalog()
        val unit = current.units.single()
        val first = unit.lessons.single()
        val duplicate = first.copy(
            grade = 7,
            sequence = first.sequence,
            sections = listOf(
                LessonSection(
                    id = "checkpoint",
                    type = LessonSectionType.CHECKPOINT,
                    activitySource = LessonActivitySource.Curated(emptyList())
                )
            )
        )
        val invalid = current.copy(units = listOf(unit.copy(lessons = listOf(first, duplicate))))

        val report = CurriculumValidator.validate(invalid)
        val codes = report.errors.map { it.code }.toSet()

        assertFalse(report.isValid)
        assertTrue("duplicate_lesson_id" in codes)
        assertTrue("invalid_lesson_grade" in codes)
        assertTrue("empty_curated_source" in codes)
    }

    @Test
    fun `validator rejects prerequisite cycles`() {
        val current = validCatalog()
        val unit = current.units.single()
        val original = unit.lessons.single()
        val first = original.copy(
            id = "lesson.math.number-sense.g1.001",
            sequence = 1,
            availability = LessonAvailabilityPolicy.AFTER_PREREQUISITES,
            prerequisites = listOf(
                CurriculumPrerequisite(
                    targetType = PrerequisiteTargetType.LESSON,
                    targetId = "lesson.math.number-sense.g1.002"
                )
            )
        )
        val second = original.copy(
            id = "lesson.math.number-sense.g1.002",
            sequence = 2,
            availability = LessonAvailabilityPolicy.AFTER_PREREQUISITES,
            prerequisites = listOf(
                CurriculumPrerequisite(
                    targetType = PrerequisiteTargetType.LESSON,
                    targetId = first.id
                )
            )
        )
        val cyclic = current.copy(units = listOf(unit.copy(lessons = listOf(first, second))))

        val report = CurriculumValidator.validate(cyclic)

        assertFalse(report.isValid)
        assertTrue(report.errors.any { it.code == "prerequisite_cycle" })
    }

    @Test
    fun `mastery criteria only reference sections in the same lesson`() {
        val current = validCatalog()
        val unit = current.units.single()
        val lesson = unit.lessons.single().copy(
            mastery = LessonMasteryCriteria(requiredSectionIds = listOf("missing-section"))
        )
        val invalid = current.copy(units = listOf(unit.copy(lessons = listOf(lesson))))

        val report = CurriculumValidator.validate(invalid)

        assertFalse(report.isValid)
        assertTrue(report.errors.any { it.code == "unknown_required_section" })
    }

    private fun validCatalog(): CurriculumCatalog {
        val goalId = "goal.math.number-sense.g1.count"
        val lesson = LessonDefinition(
            id = "lesson.math.number-sense.g1.001",
            grade = 1,
            sequence = 1,
            title = text("Tellen tot 20", "Compter jusqu'à 20", "Count to 20"),
            summary = text(
                "Getallen herkennen en ordenen.",
                "Reconnaître et ordonner les nombres.",
                "Recognize and order numbers."
            ),
            learningGoalIds = listOf(goalId),
            estimatedMinutes = 12,
            mastery = LessonMasteryCriteria(
                minimumCorrectRatio = 0.8,
                minimumAttempts = 1,
                requiredSectionIds = listOf("checkpoint")
            ),
            sections = listOf(
                LessonSection(
                    id = "explanation",
                    type = LessonSectionType.EXPLANATION,
                    content = text(
                        "We tellen stap voor stap.",
                        "Nous comptons pas à pas.",
                        "We count step by step."
                    ),
                    estimatedMinutes = 3
                ),
                LessonSection(
                    id = "guided-practice",
                    type = LessonSectionType.GUIDED_PRACTICE,
                    activitySource = LessonActivitySource.Generated(
                        generatorId = "generator.math.counting",
                        generatorVersion = 1,
                        exerciseCount = 5,
                        parameters = mapOf("max-value" to "20")
                    ),
                    estimatedMinutes = 4
                ),
                LessonSection(
                    id = "checkpoint",
                    type = LessonSectionType.CHECKPOINT,
                    activitySource = LessonActivitySource.Curated(
                        exerciseIds = listOf("addition_d1_g1_seq0001")
                    ),
                    estimatedMinutes = 5
                )
            ),
            tags = listOf("number-sense")
        )
        return CurriculumCatalog(
            units = listOf(
                CurriculumUnit(
                    id = "math.number-sense.g1",
                    domain = LearningDomain.MATH,
                    subcategoryId = "numbers-20",
                    grades = listOf(1),
                    title = text("Getalbegrip", "Sens des nombres", "Number sense"),
                    description = text(
                        "Inzicht in getallen en hoeveelheden.",
                        "Comprendre les nombres et les quantités.",
                        "Understand numbers and quantities."
                    ),
                    learningGoals = listOf(
                        LearningGoalDefinition(
                            id = goalId,
                            title = text("Tellen tot 20", "Compter jusqu'à 20", "Count to 20"),
                            description = text(
                                "De leerling telt vooruit en achteruit.",
                                "L'élève compte en avant et en arrière.",
                                "The learner counts forward and backward."
                            )
                        )
                    ),
                    lessons = listOf(lesson),
                    tags = listOf("number-sense")
                )
            )
        )
    }

    private fun text(nl: String, fr: String, en: String): LessonText = LessonText(
        visible = LocalizedText(nl = nl, fr = fr, en = en)
    )
}
