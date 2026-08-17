package be.saikono.poincare.domain.curriculum

import be.saikono.poincare.model.LocalizedText
import kotlinx.serialization.Serializable

@Serializable
enum class CurriculumValidationMode {
    DRAFT,
    RELEASE
}

@Serializable
enum class CurriculumIssueSeverity {
    WARNING,
    ERROR
}

@Serializable
data class CurriculumValidationIssue(
    val severity: CurriculumIssueSeverity,
    val code: String,
    val path: String,
    val message: String
)

@Serializable
data class CurriculumValidationReport(
    val issues: List<CurriculumValidationIssue>
) {
    val isValid: Boolean
        get() = issues.none { it.severity == CurriculumIssueSeverity.ERROR }

    val errors: List<CurriculumValidationIssue>
        get() = issues.filter { it.severity == CurriculumIssueSeverity.ERROR }

    val warnings: List<CurriculumValidationIssue>
        get() = issues.filter { it.severity == CurriculumIssueSeverity.WARNING }
}

object CurriculumValidator {
    private val stableIdRegex = Regex("^[a-z0-9]+(?:[._-][a-z0-9]+)*$")
    private val supportedGrades = 1..6

    fun validate(
        catalog: CurriculumCatalog,
        mode: CurriculumValidationMode = CurriculumValidationMode.RELEASE
    ): CurriculumValidationReport {
        val issues = mutableListOf<CurriculumValidationIssue>()
        val unitIds = linkedSetOf<String>()
        val lessonIds = linkedSetOf<String>()
        val goalIds = linkedSetOf<String>()
        val unitById = linkedMapOf<String, CurriculumUnit>()
        val lessonById = linkedMapOf<String, LessonDefinition>()
        val goalById = linkedMapOf<String, LearningGoalDefinition>()
        val lessonOwner = linkedMapOf<String, String>()

        if (catalog.schemaVersion != CURRENT_CURRICULUM_SCHEMA_VERSION) {
            issues.error(
                code = "unsupported_schema_version",
                path = "catalog.schemaVersion",
                message = "Expected curriculum schema version $CURRENT_CURRICULUM_SCHEMA_VERSION but found ${catalog.schemaVersion}."
            )
        }
        if (catalog.definitionVersion < 1) {
            issues.error("invalid_definition_version", "catalog.definitionVersion", "Definition version must be at least 1.")
        }
        if (catalog.units.isEmpty()) {
            issues.error("empty_catalog", "catalog.units", "A curriculum catalogue must contain at least one unit.")
        }

        catalog.units.forEachIndexed { unitIndex, unit ->
            val unitPath = "catalog.units[$unitIndex]"
            validateStableId(unit.id, "$unitPath.id", "unit", issues)
            if (!unitIds.add(unit.id)) {
                issues.error("duplicate_unit_id", "$unitPath.id", "Unit ID '${unit.id}' is not unique.")
            } else {
                unitById[unit.id] = unit
            }
            validateStableId(unit.subcategoryId, "$unitPath.subcategoryId", "subcategory", issues)
            validateDefinitionVersion(unit.definitionVersion, "$unitPath.definitionVersion", issues)
            validateGrades(unit.grades, "$unitPath.grades", issues)
            validateLessonText(unit.title, "$unitPath.title", mode, issues)
            unit.description?.let { validateLessonText(it, "$unitPath.description", mode, issues) }
            validateTags(unit.tags, "$unitPath.tags", issues)

            if (unit.learningGoals.isEmpty()) {
                issues.releaseSensitive(
                    mode,
                    code = "unit_without_learning_goals",
                    path = "$unitPath.learningGoals",
                    message = "A release curriculum unit must define at least one learning goal."
                )
            }
            if (unit.lessons.isEmpty()) {
                issues.releaseSensitive(
                    mode,
                    code = "unit_without_lessons",
                    path = "$unitPath.lessons",
                    message = "A release curriculum unit must define at least one lesson."
                )
            }

            unit.learningGoals.forEachIndexed { goalIndex, goal ->
                val goalPath = "$unitPath.learningGoals[$goalIndex]"
                validateStableId(goal.id, "$goalPath.id", "learning goal", issues)
                if (!goalIds.add(goal.id)) {
                    issues.error("duplicate_learning_goal_id", "$goalPath.id", "Learning-goal ID '${goal.id}' is not globally unique.")
                } else {
                    goalById[goal.id] = goal
                }
                validateDefinitionVersion(goal.definitionVersion, "$goalPath.definitionVersion", issues)
                validateLessonText(goal.title, "$goalPath.title", mode, issues)
                goal.description?.let { validateLessonText(it, "$goalPath.description", mode, issues) }
            }

            val lessonSequences = mutableSetOf<Pair<Int, Int>>()
            unit.lessons.forEachIndexed { lessonIndex, lesson ->
                val lessonPath = "$unitPath.lessons[$lessonIndex]"
                validateStableId(lesson.id, "$lessonPath.id", "lesson", issues)
                if (!lessonIds.add(lesson.id)) {
                    issues.error("duplicate_lesson_id", "$lessonPath.id", "Lesson ID '${lesson.id}' is not globally unique.")
                } else {
                    lessonById[lesson.id] = lesson
                    lessonOwner[lesson.id] = unit.id
                }
                validateDefinitionVersion(lesson.definitionVersion, "$lessonPath.definitionVersion", issues)
                if (lesson.grade !in supportedGrades) {
                    issues.error("invalid_lesson_grade", "$lessonPath.grade", "Lesson grade must be between 1 and 6.")
                } else if (lesson.grade !in unit.grades) {
                    issues.error(
                        "lesson_grade_outside_unit",
                        "$lessonPath.grade",
                        "Lesson grade ${lesson.grade} is not included in unit grades ${unit.grades}."
                    )
                }
                if (lesson.sequence < 1) {
                    issues.error("invalid_lesson_sequence", "$lessonPath.sequence", "Lesson sequence must be at least 1.")
                } else if (!lessonSequences.add(lesson.grade to lesson.sequence)) {
                    issues.error(
                        "duplicate_lesson_sequence",
                        "$lessonPath.sequence",
                        "Unit '${unit.id}' contains more than one grade-${lesson.grade} lesson at sequence ${lesson.sequence}."
                    )
                }
                validateLessonText(lesson.title, "$lessonPath.title", mode, issues)
                lesson.summary?.let { validateLessonText(it, "$lessonPath.summary", mode, issues) }
                validateTags(lesson.tags, "$lessonPath.tags", issues)
                if (lesson.estimatedMinutes != null && lesson.estimatedMinutes < 1) {
                    issues.error("invalid_estimated_minutes", "$lessonPath.estimatedMinutes", "Estimated minutes must be positive when provided.")
                }
                validateAvailability(lesson, lessonPath, issues)
                validateMastery(lesson.mastery, lessonPath, issues)

                if (lesson.learningGoalIds.isEmpty()) {
                    issues.releaseSensitive(
                        mode,
                        code = "lesson_without_learning_goals",
                        path = "$lessonPath.learningGoalIds",
                        message = "A release lesson must reference at least one learning goal."
                    )
                }
                val localGoalIds = unit.learningGoals.mapTo(linkedSetOf()) { it.id }
                lesson.learningGoalIds.forEachIndexed { goalRefIndex, goalId ->
                    validateStableId(goalId, "$lessonPath.learningGoalIds[$goalRefIndex]", "learning-goal reference", issues)
                    if (goalId !in localGoalIds) {
                        issues.error(
                            "unknown_lesson_learning_goal",
                            "$lessonPath.learningGoalIds[$goalRefIndex]",
                            "Lesson '${lesson.id}' references learning goal '$goalId' outside unit '${unit.id}'."
                        )
                    }
                }
                if (lesson.learningGoalIds.size != lesson.learningGoalIds.distinct().size) {
                    issues.error("duplicate_lesson_learning_goal", "$lessonPath.learningGoalIds", "Lesson learning-goal references must be unique.")
                }

                validateSections(lesson, lessonPath, mode, issues)
            }
        }

        validatePrerequisites(catalog, unitById, lessonById, goalById, lessonOwner, issues)
        validatePrerequisiteCycles(catalog, unitIds, lessonIds, issues)

        return CurriculumValidationReport(issues.toList())
    }

    private fun validateDefinitionVersion(version: Int, path: String, issues: MutableList<CurriculumValidationIssue>) {
        if (version < 1) {
            issues.error("invalid_definition_version", path, "Definition version must be at least 1.")
        }
    }

    private fun validateStableId(
        id: String,
        path: String,
        kind: String,
        issues: MutableList<CurriculumValidationIssue>
    ) {
        if (!stableIdRegex.matches(id)) {
            issues.error(
                "invalid_stable_id",
                path,
                "$kind ID '$id' must be language-neutral lowercase text using letters, digits, '.', '_' or '-'."
            )
        }
    }

    private fun validateGrades(grades: List<Int>, path: String, issues: MutableList<CurriculumValidationIssue>) {
        if (grades.isEmpty()) {
            issues.error("empty_grade_scope", path, "A curriculum unit must apply to at least one grade.")
            return
        }
        if (grades.any { it !in supportedGrades }) {
            issues.error("invalid_grade_scope", path, "Grades must be between 1 and 6.")
        }
        if (grades != grades.distinct().sorted()) {
            issues.error("noncanonical_grade_scope", path, "Grades must be unique and sorted in ascending order.")
        }
    }

    private fun validateLessonText(
        text: LessonText,
        path: String,
        mode: CurriculumValidationMode,
        issues: MutableList<CurriculumValidationIssue>
    ) {
        validateLocalizedText(text.visible, "$path.visible", mode, issues)
        text.spoken?.let { validateLocalizedText(it, "$path.spoken", mode, issues) }
    }

    private fun validateLocalizedText(
        text: LocalizedText,
        path: String,
        mode: CurriculumValidationMode,
        issues: MutableList<CurriculumValidationIssue>
    ) {
        if (text.nl.isBlank()) {
            issues.error("missing_nl_text", "$path.nl", "Belgian Dutch text is required.")
        }
        if (mode == CurriculumValidationMode.RELEASE) {
            if (text.fr.isNullOrBlank()) {
                issues.error("missing_fr_text", "$path.fr", "Belgian French text is required for release content.")
            }
            if (text.en.isNullOrBlank()) {
                issues.error("missing_en_text", "$path.en", "US English text is required for release content.")
            }
        } else {
            if (text.fr.isNullOrBlank()) {
                issues.warning("missing_fr_text", "$path.fr", "Belgian French text is still missing from this draft.")
            }
            if (text.en.isNullOrBlank()) {
                issues.warning("missing_en_text", "$path.en", "US English text is still missing from this draft.")
            }
        }
    }

    private fun validateTags(tags: List<String>, path: String, issues: MutableList<CurriculumValidationIssue>) {
        if (tags.size != tags.distinct().size) {
            issues.error("duplicate_tags", path, "Tags must be unique.")
        }
        tags.forEachIndexed { index, tag ->
            validateStableId(tag, "$path[$index]", "tag", issues)
        }
    }

    private fun validateAvailability(
        lesson: LessonDefinition,
        lessonPath: String,
        issues: MutableList<CurriculumValidationIssue>
    ) {
        when (lesson.availability) {
            LessonAvailabilityPolicy.ALWAYS -> if (lesson.prerequisites.isNotEmpty()) {
                issues.error(
                    "always_available_with_prerequisites",
                    "$lessonPath.prerequisites",
                    "An ALWAYS lesson cannot also declare prerequisites."
                )
            }
            LessonAvailabilityPolicy.AFTER_PREREQUISITES -> if (lesson.prerequisites.isEmpty()) {
                issues.error(
                    "prerequisite_policy_without_prerequisites",
                    "$lessonPath.prerequisites",
                    "AFTER_PREREQUISITES requires at least one prerequisite."
                )
            }
            LessonAvailabilityPolicy.PARENT_ASSIGNED -> Unit
        }
    }

    private fun validateMastery(
        mastery: LessonMasteryCriteria,
        lessonPath: String,
        issues: MutableList<CurriculumValidationIssue>
    ) {
        if (!mastery.minimumCorrectRatio.isFinite() || mastery.minimumCorrectRatio <= 0.0 || mastery.minimumCorrectRatio > 1.0) {
            issues.error(
                "invalid_mastery_ratio",
                "$lessonPath.mastery.minimumCorrectRatio",
                "Mastery ratio must be greater than 0 and at most 1."
            )
        }
        if (mastery.minimumAttempts < 1) {
            issues.error("invalid_minimum_attempts", "$lessonPath.mastery.minimumAttempts", "Minimum attempts must be at least 1.")
        }
        if (mastery.maximumHints != null && mastery.maximumHints < 0) {
            issues.error("invalid_maximum_hints", "$lessonPath.mastery.maximumHints", "Maximum hints cannot be negative.")
        }
        if (mastery.requiredSectionIds.size != mastery.requiredSectionIds.distinct().size) {
            issues.error(
                "duplicate_required_section",
                "$lessonPath.mastery.requiredSectionIds",
                "Required mastery section IDs must be unique."
            )
        }
    }

    private fun validateSections(
        lesson: LessonDefinition,
        lessonPath: String,
        mode: CurriculumValidationMode,
        issues: MutableList<CurriculumValidationIssue>
    ) {
        if (lesson.sections.isEmpty()) {
            issues.error("lesson_without_sections", "$lessonPath.sections", "A lesson must contain at least one section.")
            return
        }
        val sectionIds = linkedSetOf<String>()
        lesson.sections.forEachIndexed { sectionIndex, section ->
            val sectionPath = "$lessonPath.sections[$sectionIndex]"
            validateStableId(section.id, "$sectionPath.id", "section", issues)
            if (!sectionIds.add(section.id)) {
                issues.error("duplicate_section_id", "$sectionPath.id", "Section ID '${section.id}' is not unique within lesson '${lesson.id}'.")
            }
            section.title?.let { validateLessonText(it, "$sectionPath.title", mode, issues) }
            section.content?.let { validateLessonText(it, "$sectionPath.content", mode, issues) }
            if (section.content == null && section.activitySource == null) {
                issues.error("empty_lesson_section", sectionPath, "A lesson section must contain text, an activity source, or both.")
            }
            if (section.estimatedMinutes != null && section.estimatedMinutes < 1) {
                issues.error("invalid_estimated_minutes", "$sectionPath.estimatedMinutes", "Estimated minutes must be positive when provided.")
            }
            if (section.parentSupport.ordinal > lesson.parentSupport.ordinal) {
                issues.error(
                    "lesson_parent_support_too_low",
                    "$lessonPath.parentSupport",
                    "Lesson parent support must cover the strongest support requirement of its sections."
                )
            }
            section.activitySource?.let { validateActivitySource(it, "$sectionPath.activitySource", mode, issues) }
            if (section.type == LessonSectionType.PRACTICAL_ACTIVITY &&
                section.activitySource !is LessonActivitySource.Practical
            ) {
                issues.releaseSensitive(
                    mode,
                    code = "practical_section_without_practical_source",
                    path = "$sectionPath.activitySource",
                    message = "A practical-activity section should use a Practical activity source."
                )
            }
        }

        lesson.mastery.requiredSectionIds.forEachIndexed { index, sectionId ->
            validateStableId(sectionId, "$lessonPath.mastery.requiredSectionIds[$index]", "section reference", issues)
            if (sectionId !in sectionIds) {
                issues.error(
                    "unknown_required_section",
                    "$lessonPath.mastery.requiredSectionIds[$index]",
                    "Mastery references unknown section '$sectionId' in lesson '${lesson.id}'."
                )
            }
        }
    }

    private fun validateActivitySource(
        source: LessonActivitySource,
        path: String,
        mode: CurriculumValidationMode,
        issues: MutableList<CurriculumValidationIssue>
    ) {
        when (source) {
            is LessonActivitySource.Generated -> {
                validateStableId(source.generatorId, "$path.generatorId", "generator", issues)
                validateGenerator(source.generatorVersion, source.exerciseCount, source.parameters, path, issues)
            }
            is LessonActivitySource.Curated -> {
                validateExerciseIds(source.exerciseIds, "$path.exerciseIds", issues)
            }
            is LessonActivitySource.Hybrid -> {
                validateStableId(source.generatorId, "$path.generatorId", "generator", issues)
                validateGenerator(
                    source.generatorVersion,
                    source.generatedExerciseCount,
                    source.parameters,
                    path,
                    issues
                )
                validateExerciseIds(source.curatedExerciseIds, "$path.curatedExerciseIds", issues)
            }
            is LessonActivitySource.Practical -> {
                validateStableId(source.activityId, "$path.activityId", "practical activity", issues)
                validateLessonText(source.instructions, "$path.instructions", mode, issues)
            }
        }
    }

    private fun validateGenerator(
        generatorVersion: Int,
        exerciseCount: Int,
        parameters: Map<String, String>,
        path: String,
        issues: MutableList<CurriculumValidationIssue>
    ) {
        if (generatorVersion < 1) {
            issues.error("invalid_generator_version", "$path.generatorVersion", "Generator version must be at least 1.")
        }
        if (exerciseCount < 1) {
            issues.error("invalid_generated_exercise_count", "$path.exerciseCount", "Generated exercise count must be at least 1.")
        }
        parameters.keys.forEach { key ->
            validateStableId(key, "$path.parameters[$key]", "generator parameter", issues)
        }
    }

    private fun validateExerciseIds(
        exerciseIds: List<String>,
        path: String,
        issues: MutableList<CurriculumValidationIssue>
    ) {
        if (exerciseIds.isEmpty()) {
            issues.error("empty_curated_source", path, "A curated activity source must reference at least one exercise.")
        }
        if (exerciseIds.size != exerciseIds.distinct().size) {
            issues.error("duplicate_curated_exercise", path, "Curated exercise IDs must be unique.")
        }
        exerciseIds.forEachIndexed { index, exerciseId ->
            validateStableId(exerciseId, "$path[$index]", "exercise reference", issues)
        }
    }

    private fun validatePrerequisites(
        catalog: CurriculumCatalog,
        unitById: Map<String, CurriculumUnit>,
        lessonById: Map<String, LessonDefinition>,
        goalById: Map<String, LearningGoalDefinition>,
        lessonOwner: Map<String, String>,
        issues: MutableList<CurriculumValidationIssue>
    ) {
        catalog.units.forEachIndexed { unitIndex, unit ->
            val unitPath = "catalog.units[$unitIndex]"
            unit.prerequisites.forEachIndexed { prerequisiteIndex, prerequisite ->
                val path = "$unitPath.prerequisites[$prerequisiteIndex]"
                validatePrerequisiteTarget(prerequisite, path, unitById, lessonById, goalById, issues)
                if (prerequisite.targetType == PrerequisiteTargetType.UNIT && prerequisite.targetId == unit.id) {
                    issues.error("self_prerequisite", path, "Unit '${unit.id}' cannot depend on itself.")
                }
            }
            unit.lessons.forEachIndexed { lessonIndex, lesson ->
                val lessonPath = "$unitPath.lessons[$lessonIndex]"
                lesson.prerequisites.forEachIndexed { prerequisiteIndex, prerequisite ->
                    val path = "$lessonPath.prerequisites[$prerequisiteIndex]"
                    validatePrerequisiteTarget(prerequisite, path, unitById, lessonById, goalById, issues)
                    if (prerequisite.targetType == PrerequisiteTargetType.LESSON && prerequisite.targetId == lesson.id) {
                        issues.error("self_prerequisite", path, "Lesson '${lesson.id}' cannot depend on itself.")
                    }
                    if (prerequisite.targetType == PrerequisiteTargetType.LEARNING_GOAL) {
                        val goalOwner = catalog.units.firstOrNull { candidate ->
                            candidate.learningGoals.any { it.id == prerequisite.targetId }
                        }?.id
                        val owner = lessonOwner[lesson.id]
                        if (goalOwner != null && owner != null && goalOwner != owner) {
                            issues.warning(
                                "cross_unit_learning_goal_prerequisite",
                                path,
                                "Lesson '${lesson.id}' depends on a learning goal from another unit. Confirm this is intentional."
                            )
                        }
                    }
                }
            }
        }
    }

    private fun validatePrerequisiteTarget(
        prerequisite: CurriculumPrerequisite,
        path: String,
        unitById: Map<String, CurriculumUnit>,
        lessonById: Map<String, LessonDefinition>,
        goalById: Map<String, LearningGoalDefinition>,
        issues: MutableList<CurriculumValidationIssue>
    ) {
        validateStableId(prerequisite.targetId, "$path.targetId", "prerequisite target", issues)
        val exists = when (prerequisite.targetType) {
            PrerequisiteTargetType.UNIT -> prerequisite.targetId in unitById
            PrerequisiteTargetType.LESSON -> prerequisite.targetId in lessonById
            PrerequisiteTargetType.LEARNING_GOAL -> prerequisite.targetId in goalById
        }
        if (!exists) {
            issues.error(
                "unknown_prerequisite",
                path,
                "Prerequisite target '${prerequisite.targetId}' (${prerequisite.targetType}) does not exist."
            )
        }
    }

    private fun validatePrerequisiteCycles(
        catalog: CurriculumCatalog,
        unitIds: Set<String>,
        lessonIds: Set<String>,
        issues: MutableList<CurriculumValidationIssue>
    ) {
        val graph = linkedMapOf<String, MutableSet<String>>()
        fun node(type: PrerequisiteTargetType, id: String): String = "${type.name}:$id"

        catalog.units.forEach { unit ->
            val source = node(PrerequisiteTargetType.UNIT, unit.id)
            graph.getOrPut(source) { linkedSetOf() }
            unit.prerequisites.forEach { prerequisite ->
                if (prerequisite.targetType != PrerequisiteTargetType.LEARNING_GOAL) {
                    graph.getValue(source).add(node(prerequisite.targetType, prerequisite.targetId))
                }
            }
            unit.lessons.forEach { lesson ->
                val lessonSource = node(PrerequisiteTargetType.LESSON, lesson.id)
                graph.getOrPut(lessonSource) { linkedSetOf() }
                lesson.prerequisites.forEach { prerequisite ->
                    if (prerequisite.targetType != PrerequisiteTargetType.LEARNING_GOAL) {
                        graph.getValue(lessonSource).add(node(prerequisite.targetType, prerequisite.targetId))
                    }
                }
            }
        }

        val knownNodes = buildSet {
            unitIds.forEach { add(node(PrerequisiteTargetType.UNIT, it)) }
            lessonIds.forEach { add(node(PrerequisiteTargetType.LESSON, it)) }
        }
        val state = mutableMapOf<String, Int>()
        val stack = mutableListOf<String>()
        val reported = mutableSetOf<String>()

        fun visit(current: String) {
            state[current] = 1
            stack += current
            for (next in graph[current].orEmpty()) {
                if (next !in knownNodes) continue
                when (state[next] ?: 0) {
                    0 -> visit(next)
                    1 -> {
                        val cycleStart = stack.indexOf(next).coerceAtLeast(0)
                        val cycleNodes = (stack.drop(cycleStart) + next)
                        val signature = cycleNodes.joinToString(" -> ")
                        if (reported.add(signature)) {
                            issues.error(
                                "prerequisite_cycle",
                                "catalog.prerequisites",
                                "Prerequisite cycle detected: $signature"
                            )
                        }
                    }
                }
            }
            stack.removeAt(stack.lastIndex)
            state[current] = 2
        }

        knownNodes.forEach { if ((state[it] ?: 0) == 0) visit(it) }
    }

    private fun MutableList<CurriculumValidationIssue>.releaseSensitive(
        mode: CurriculumValidationMode,
        code: String,
        path: String,
        message: String
    ) {
        add(
            CurriculumValidationIssue(
                severity = if (mode == CurriculumValidationMode.RELEASE) CurriculumIssueSeverity.ERROR else CurriculumIssueSeverity.WARNING,
                code = code,
                path = path,
                message = message
            )
        )
    }

    private fun MutableList<CurriculumValidationIssue>.error(code: String, path: String, message: String) {
        add(CurriculumValidationIssue(CurriculumIssueSeverity.ERROR, code, path, message))
    }

    private fun MutableList<CurriculumValidationIssue>.warning(code: String, path: String, message: String) {
        add(CurriculumValidationIssue(CurriculumIssueSeverity.WARNING, code, path, message))
    }
}
