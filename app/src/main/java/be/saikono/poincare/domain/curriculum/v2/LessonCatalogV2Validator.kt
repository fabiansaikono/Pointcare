package be.saikono.poincare.domain.curriculum.v2

data class LessonCatalogValidationIssueV2(
    val ruleId: String,
    val path: String,
    val message: String
)

object LessonCatalogV2Validator {
    private val idPattern = Regex("^[a-z0-9._-]+$")

    fun validate(catalog: LessonCatalogV2): List<LessonCatalogValidationIssueV2> = buildList {
        if (catalog.schemaVersion != CURRENT_LESSON_CATALOG_SCHEMA_VERSION) {
            issue("catalog.schema-version", "schemaVersion", "Expected schemaVersion 2.")
        }
        if (catalog.definitionVersion < 1) {
            issue("catalog.definition-version", "definitionVersion", "Definition version must be positive.")
        }
        if (catalog.lessons.isEmpty()) issue("catalog.lessons", "lessons", "At least one lesson is required.")
        duplicates(catalog.lessons.map { it.id }).forEach {
            issue("catalog.unique-lesson-id", "lessons", "Duplicate lesson ID: $it")
        }
        duplicates(catalog.lessons.flatMap(::stableIds)).forEach {
            issue("catalog.unique-stable-id", "lessons", "Duplicate stable library ID: $it")
        }
        catalog.lessons.forEachIndexed { index, lesson -> validateLesson(lesson, "lessons[$index]") }
    }

    private fun MutableList<LessonCatalogValidationIssueV2>.validateLesson(
        lesson: LessonDefinitionV2,
        path: String
    ) {
        if (!lesson.id.startsWith("lesson.") || !idPattern.matches(lesson.id)) {
            issue("lesson.id", "$path.id", "Lesson ID must be a stable lesson.* identifier.")
        }
        if (!lesson.unitId.startsWith("unit.") || !idPattern.matches(lesson.unitId)) {
            issue("lesson.unit-id", "$path.unitId", "Unit ID must be a stable unit.* identifier.")
        }
        if (lesson.grade !in 1..6) issue("lesson.grade", "$path.grade", "Grade must be between 1 and 6.")
        if (lesson.grade == 1 && lesson.checkpoint) {
            issue("lesson.checkpoint.grade-one", "$path.checkpoint", "Grade 1 lessons cannot start behind a checkpoint.")
        }
        if (lesson.curriculumCategoryId.isBlank()) issue("lesson.curriculum-category", "$path.curriculumCategoryId", "Curriculum category is required.")
        if (lesson.curriculumSubcategoryId.isBlank()) issue("lesson.curriculum-subcategory", "$path.curriculumSubcategoryId", "Curriculum subcategory is required.")
        localized(lesson.title, "$path.title")
        localized(lesson.summary, "$path.summary")
        localized(lesson.description, "$path.description")
        localized(lesson.pedagogicalLogic, "$path.pedagogicalLogic")
        if (lesson.defaultLocale != "nl-BE") issue("localization.default", "$path.defaultLocale", "nl-BE must remain the source and default locale.")
        validateTranslationMetadata(lesson, path)
        if (lesson.learningGoalIds.isEmpty()) issue("lesson.atomic-goal", "$path.learningGoalIds", "At least one observable goal is required.")
        if (lesson.masteryTags.isEmpty()) issue("lesson.mastery-tags", "$path.masteryTags", "At least one mastery tag is required.")

        validateStageSequence(lesson, path)
        duplicates(lesson.stages.map { it.id }).forEach {
            issue("lesson.unique-stage-id", "$path.stages", "Duplicate stage ID: $it")
        }

        val visualIds = lesson.visuals.map { it.id }.toSet()
        val representationIds = lesson.visuals.mapNotNull { it.representationId }.toSet()
        lesson.visuals.forEachIndexed { index, visual ->
            val visualPath = "$path.visuals[$index]"
            localizedMap(visual.altText, "$visualPath.altText")
            if (visual.licenseId.isNullOrBlank()) issue("visual.license", "$visualPath.licenseId", "A visual license ID is required.")
            if (visual.kind == LessonVisualKindV2.WORLD_BICOLOR_ILLUSTRATION && (
                    visual.assetPathSvg.isNullOrBlank() || visual.assetPathPng.isNullOrBlank()
                )
            ) issue("world.bicolor-required", visualPath, "World visuals require SVG and PNG paths.")
            if (visual.kind == LessonVisualKindV2.WORLD_BICOLOR_ILLUSTRATION &&
                (visual.palettePolicy !in setOf(
                    LessonPalettePolicyV2.PLANET_GOLD_WHITE_ONLY,
                    LessonPalettePolicyV2.POINCARE_TRICOLOUR
                ) || !visual.transparentBackground)
            ) issue("world.palette", visualPath, "World visuals must use the approved two- or three-colour palette on transparency.")
            if (visual.kind == LessonVisualKindV2.THEORY_ICON && visual.assetPathPng.isNullOrBlank()) {
                issue("theory-icon.png", "$visualPath.assetPathPng", "A lesson-specific theory icon requires a PNG asset.")
            }
            if (visual.kind == LessonVisualKindV2.THEORY_ICON &&
                (visual.palettePolicy != LessonPalettePolicyV2.POINCARE_TRICOLOUR || !visual.transparentBackground)
            ) issue("theory-icon.palette", visualPath, "Theory icons must use the Poincare tricolour palette on transparency.")
        }
        if (lesson.legacyRouting.domain == LessonDomainV2.WORLD_KNOWLEDGE &&
            lesson.visuals.none { it.kind == LessonVisualKindV2.WORLD_BICOLOR_ILLUSTRATION }
        ) issue("world.bicolor-required", "$path.visuals", "A world lesson needs a bicolor illustration.")
        if (lesson.legacyRouting.domain == LessonDomainV2.GEOMETRY &&
            lesson.visuals.none { it.kind in setOf(LessonVisualKindV2.GEOMETRY_DIAGRAM, LessonVisualKindV2.MEASUREMENT_DIAGRAM) }
        ) issue("measurement.visual-required", "$path.visuals", "A geometry or measurement lesson needs a matching diagram.")

        lesson.stages.forEachIndexed { index, stage ->
            val stagePath = "$path.stages[$index]"
            if (stage.type.isTheory() && (stage.sectionId != null || stage.seriesIndex != null)) {
                issue("lesson.theory-unsectioned", stagePath, "Theory cannot belong to an exercise series.")
            }
            if (stage.sectionId != null && !idPattern.matches(stage.sectionId)) {
                issue("lesson.section-id", "$stagePath.sectionId", "Section ID must be stable and use lowercase identifier characters.")
            }
            stage.sectionTitle?.let { localized(it, "$stagePath.sectionTitle") }
            stage.title?.let { localized(it, "$stagePath.title") }
            stage.contentBlocks.forEachIndexed { blockIndex, block ->
                val blockPath = "$stagePath.contentBlocks[$blockIndex]"
                if (block.pageId.isBlank() || block.pageSequence < 1) {
                    issue("lesson.page-id", blockPath, "Every content block must belong to a stable lesson page.")
                }
                block.title?.let { localized(it, "$blockPath.title") }
                block.body?.let { localized(it, "$blockPath.body") }
                block.terms.forEachIndexed { termIndex, term ->
                    localized(term.term, "$blockPath.terms[$termIndex].term")
                    localized(term.definition, "$blockPath.terms[$termIndex].definition")
                    term.example?.let { localized(it, "$blockPath.terms[$termIndex].example") }
                    term.nonExample?.let { localized(it, "$blockPath.terms[$termIndex].nonExample") }
                }
                (block.visualIds - visualIds).forEach {
                    issue("visual.reference", "$blockPath.visualIds", "Unknown visual ID: $it")
                }
            }
            stage.exerciseSets.forEachIndexed { setIndex, set ->
                val setPath = "$stagePath.exerciseSets[$setIndex]"
                validateExerciseSet(set, lesson, stage, setPath)
                (set.masteryTags - lesson.masteryTags.toSet()).forEach {
                    issue("test.coverage", "$setPath.masteryTags", "Mastery tag is not declared by the lesson: $it")
                }
                (set.representationIds - representationIds).forEach {
                    issue("math.representation-match", "$setPath.representationIds", "Representation is not declared by a lesson visual: $it")
                }
            }
        }

        val theoryBlocks = lesson.theoryStage()?.contentBlocks.orEmpty()
        val theorySets = lesson.theoryStage()?.exerciseSets.orEmpty()
        val interactiveTracingTheory = theorySets.any {
            it.generatorId == "generator.g1.writing.letter_tracing.core"
        }
        val theoryTypes = theoryBlocks.map { it.type }.toSet()
        val requiredTheoryCore = setOf(
            LessonContentBlockTypeV2.HOW_IT_WORKS,
            LessonContentBlockTypeV2.CORRECT_RESULT_RULE,
            LessonContentBlockTypeV2.WORKED_EXAMPLE,
            LessonContentBlockTypeV2.NON_EXAMPLE
        )
        val hasClassicTheoryStructure =
            LessonContentBlockTypeV2.LEARNING_INTENT in theoryTypes &&
                theoryTypes.containsAll(requiredTheoryCore)
        val hasDirectConceptStructure =
            lesson.grade == 1 &&
                lesson.legacyRouting.domain == LessonDomainV2.WORLD_KNOWLEDGE &&
                LessonContentBlockTypeV2.CHILD_FRIENDLY_DEFINITION in theoryTypes &&
                theoryTypes.containsAll(requiredTheoryCore)
        if (theoryBlocks.isNotEmpty() && !hasClassicTheoryStructure && !hasDirectConceptStructure) {
            issue("theory.child-language", "$path.stages", "Theory needs intent, explanation, correctness rule, example and non-example; terms are required only when the lesson introduces them.")
        }
        if (theoryBlocks.isNotEmpty() && lesson.legacyRouting.domain in setOf(
                LessonDomainV2.MATH,
                LessonDomainV2.GEOMETRY,
                LessonDomainV2.WRITING
            ) && theoryBlocks.count { it.type == LessonContentBlockTypeV2.WORKED_EXAMPLE } < 2
        ) {
            issue("theory.multiple-examples", "$path.stages", "Number, spelling/grammar and geometry lessons need several theory examples.")
        }
        val theoryFollowUpCount = theorySets.sumOf { it.itemCount }
        if (lesson.grade in setOf(1, 2) && !interactiveTracingTheory && theoryFollowUpCount != 0 && theoryFollowUpCount !in 2..5) {
            issue("lesson.theory-follow-up", "$path.stages", "Theory needs a few (2 to 5) follow-up questions.")
        }
        if (theorySets.any { it.parameters[WRONG_ANSWERS_ALLOWED_METADATA_KEY] != "1" }) {
            issue("exercise-set.wrong-answers-allowed", "$path.stages", "Theory follow-up questions must reveal the answer after one wrong response.")
        }

        val exerciseStages = lesson.exerciseSeries()
        val practicePurposes = exerciseStages.flatMap { it.exerciseSets }.map { it.purpose }.toSet()
        if (!practicePurposes.contains(LessonExercisePurposeV2.GUIDED) ||
            !practicePurposes.contains(LessonExercisePurposeV2.INDEPENDENT_PRACTICE)
        ) issue("exercises.variation", "$path.stages", "Exercise series need guided and independent practice across the lesson.")
        val practiceSets = exerciseStages.flatMap { it.exerciseSets }
        if (practiceSets.any { it.parameters[WRONG_ANSWERS_ALLOWED_METADATA_KEY] !in setOf("1", "2") }) {
            issue("exercise-set.wrong-answers-allowed", "$path.stages", "Every exercise set must declare a 'wrong answers allowed' value of 1 or 2.")
        }

        val testStages = lesson.stages.filter { it.type == LessonStageTypeV2.MASTERY_TEST }
        val allTests = testStages.flatMap { it.exerciseSets }
        val gateTests = lesson.finalTestStage()?.exerciseSets.orEmpty()
        if (allTests.isEmpty() || allTests.any {
                it.purpose != LessonExercisePurposeV2.MASTERY_TEST ||
                    it.hintPolicy != LessonHintPolicyV2.NONE ||
                    it.feedbackPolicy != LessonFeedbackPolicyV2.AFTER_TEST
            }
        ) issue("gate.no-hints", "$path.stages", "The final test requires no hints and feedback only after the test.")
        if (gateTests.sumOf { it.itemCount } < lesson.gatePolicy.minimumIndependentItems) {
            issue("gate.minimum-items", "$path.stages", "The final mastery test does not meet minimumIndependentItems.")
        }
        val testedTags = gateTests.flatMap { it.masteryTags }.toSet()
        if (!testedTags.containsAll(lesson.gatePolicy.requiredMasteryTags)) {
            issue("test.coverage", "$path.stages", "Every required mastery tag must occur in the final test blueprint.")
        }
        if (allTests.filter {
                it.sourceMode == LessonSourceModeV2.GENERATED &&
                    it.generatorId != "generator.reviewed.question-pool"
            }.any {
                it.parameters["exclude_recent_exact_items"] != "true"
            }
        ) issue("test.unseen-equivalent", "$path.stages", "Procedurally generated tests must exclude recent exact items.")
        if (lesson.gatePolicy.evidenceSource != LessonGateEvidenceSourceV2.MASTERY_TEST_ONLY) {
            issue("gate.test-only", "$path.gatePolicy", "Only mastery-test evidence may pass a lesson.")
        }
        if (!lesson.errorRepetitionPolicy.repeatConceptNotExactItem) {
            issue("errors.concept-not-item", "$path.errorRepetitionPolicy", "Error review must vary the exact item.")
        }
    }

    private fun MutableList<LessonCatalogValidationIssueV2>.validateStageSequence(
        lesson: LessonDefinitionV2,
        path: String
    ) {
        val ordered = lesson.stages.sortedBy { it.sequence }
        if (ordered.map { it.sequence } != (1..ordered.size).toList()) {
            issue("lesson.stage-sequence", "$path.stages", "Stage sequences must be contiguous and start at 1.")
        }
        val theoryStages = ordered.filter { it.type.isTheory() }
        if (theoryStages.size != 1 || theoryStages.singleOrNull()?.sequence != 1) {
            issue("lesson.theory-first", "$path.stages", "A lesson needs exactly one theory part, and it must come first.")
        }
        val exercises = ordered.filter { it.type == LessonStageTypeV2.EXERCISES }
        if (exercises.isEmpty()) {
            issue("lesson.three-explicit-parts", "$path.stages", "A lesson needs an Exercises part between Theory and Test.")
        } else {
            val series = exercises.mapIndexed { index, stage -> stage.seriesIndex ?: index + 1 }
            if (series != (1..exercises.size).toList()) {
                issue("lesson.exercise-series", "$path.stages", "Exercise series indices must be contiguous and start at 1.")
            }
            if (exercises.any { it.sectionTitle != null }) {
                issue("lesson.no-intermediate-titles", "$path.stages", "Exercise series cannot add intermediate section titles.")
            }
        }
        val tests = ordered.filter { it.type == LessonStageTypeV2.MASTERY_TEST }
        if (tests.size != 1 || tests.singleOrNull()?.id != ordered.lastOrNull()?.id) {
            issue("lesson.single-final-test", "$path.stages", "A lesson needs exactly one final test after all exercise series.")
        }
        if (lesson.grade in setOf(1, 2) && (
                ordered.firstOrNull()?.type != LessonStageTypeV2.THEORY ||
                    ordered.lastOrNull()?.type != LessonStageTypeV2.MASTERY_TEST ||
                    ordered.drop(1).dropLast(1).any { it.type != LessonStageTypeV2.EXERCISES }
                )
        ) {
            issue("lesson.three-explicit-parts", "$path.stages", "Grade 1 and 2 lessons must expose only Theory, Exercises series and a final Test.")
        }
    }

    private fun MutableList<LessonCatalogValidationIssueV2>.validateExerciseSet(
        set: LessonExerciseSetV2,
        lesson: LessonDefinitionV2,
        stage: LessonStageV2,
        path: String
    ) {
        if (set.itemCount < 1) issue("exercise-set.item-count", "$path.itemCount", "Item count must be positive.")
        if (set.masteryTags.isEmpty()) issue("exercise-set.mastery-tags", "$path.masteryTags", "Mastery tags are required.")
        if (set.exerciseSetId != set.id || set.lessonId != lesson.id || set.stageId != stage.id) {
            issue("exercise-set.relations", path, "Exercise-set links must reference their lesson, stage and own stable ID.")
        }
        if (set.generatorVersion != "v2") issue("exercise-set.v2", "$path.generatorVersion", "V2 exercise sets must declare generatorVersion v2.")
        if (set.questionBlueprints.size != set.itemCount) {
            issue("exercise-set.questions", "$path.questionBlueprints", "Every runtime question needs a stable question blueprint.")
        }
        set.questionBlueprints.forEachIndexed { index, question ->
            val questionPath = "$path.questionBlueprints[$index]"
            if (question.lessonId != lesson.id || question.stageId != stage.id || question.exerciseSetId != set.id) {
                issue("question.relations", questionPath, "Question links must reference the owning lesson, stage and exercise set.")
            }
            if (question.generatorVersion != "v2" || question.origin != set.origin) {
                issue("question.provenance", questionPath, "Question provenance must match its V2 exercise set.")
            }
            if (question.answerOptionIds.isEmpty() || duplicates(question.answerOptionIds).isNotEmpty()) {
                issue("answer.stable-id", "$questionPath.answerOptionIds", "Every runtime answer option needs a unique stable ID.")
            }
        }
        if (lesson.publicationStatus == LessonPublicationStatusV2.ACTIVE && (
                set.publicationStatus != LessonPublicationStatusV2.ACTIVE ||
                    set.validationStatus != LessonValidationStatusV2.VALIDATED
                )
        ) issue("publication.explicit", path, "Published lessons may only contain validated, published exercise sets.")
        if (lesson.publicationStatus != LessonPublicationStatusV2.ACTIVE && set.publicationStatus == LessonPublicationStatusV2.ACTIVE) {
            issue("publication.parent", path, "An exercise set cannot be published while its lesson is not published.")
        }
        when (set.sourceMode) {
            LessonSourceModeV2.CURATED -> {
                if (set.curatedExerciseIds.size != set.itemCount) {
                    issue("curated.stable-id", "$path.curatedExerciseIds", "Curated IDs must be stable and match itemCount.")
                }
                if (set.generatorId != null || set.generatorImplementationVersion != null) {
                    issue("source.two-modes", path, "A curated set cannot also declare a generator.")
                }
            }
            LessonSourceModeV2.GENERATED -> {
                if (set.generatorId.isNullOrBlank() || set.generatorImplementationVersion == null || set.seedPolicy == null) {
                    issue("generated.deterministic", path, "A generated set needs generator ID, version and seed policy.")
                }
                if (set.curatedExerciseIds.isNotEmpty()) {
                    issue("source.two-modes", path, "A generated set cannot also contain curated IDs.")
                }
            }
        }
    }

    private fun MutableList<LessonCatalogValidationIssueV2>.validateTranslationMetadata(
        lesson: LessonDefinitionV2,
        path: String
    ) {
        if (lesson.contentFields.isEmpty()) issue("content-field.index", "$path.contentFields", "Stable content-field metadata is required.")
        if (lesson.translationStatus.keys != REQUIRED_LESSON_LOCALES) {
            issue("localization.status", "$path.translationStatus", "Every supported language needs an explicit translation status.")
        }
        if (lesson.translationStatus["nl-BE"]?.status != LessonTranslationStatusV2.DEFAULT) {
            issue("localization.source-status", "$path.translationStatus.nl-BE", "nl-BE must be marked as the default language.")
        }
        lesson.contentFields.forEachIndexed { index, field ->
            val fieldPath = "$path.contentFields[$index]"
            if (field.id.isBlank() || field.ownerId.isBlank() || field.path.isBlank()) {
                issue("content-field.required", fieldPath, "Content-field ID, owner and path are required.")
            }
            if (field.translations.map { it.locale }.toSet() != REQUIRED_LESSON_LOCALES) {
                issue("localization.field-status", "$fieldPath.translations", "Every content field needs an explicit status for every language.")
            }
            field.translations.forEach { translation ->
                if (translation.locale == "nl-BE" && translation.status != LessonTranslationStatusV2.DEFAULT) {
                    issue("localization.source-status", fieldPath, "The nl-BE field translation must be marked DEFAULT.")
                }
                if (translation.fallbackLocale == "nl-BE" && translation.status == LessonTranslationStatusV2.COMPLETE) {
                    issue("localization.fallback-status", fieldPath, "A Dutch fallback can never be marked as a completed translation.")
                }
            }
        }
        // Support translations are optional aids. Publishing Dutch source content
        // must not present a nl-BE fallback as a completed FR/EN translation.
    }

    private fun MutableList<LessonCatalogValidationIssueV2>.localized(text: LessonTextV2, path: String) {
        localizedMap(text.visible, "$path.visible")
        text.spoken?.let { localizedMap(it, "$path.spoken") }
    }

    private fun MutableList<LessonCatalogValidationIssueV2>.localizedMap(values: Map<String, String>, path: String) {
        if (values["nl-BE"].isNullOrBlank()) {
            issue("localization.source-required", path, "The nl-BE source text is required.")
        }
    }

    private fun MutableList<LessonCatalogValidationIssueV2>.issue(rule: String, path: String, message: String) {
        add(LessonCatalogValidationIssueV2(rule, path, message))
    }

    private fun duplicates(values: List<String>): Set<String> = values.groupingBy { it }.eachCount()
        .filterValues { it > 1 }.keys

    private fun stableIds(lesson: LessonDefinitionV2): List<String> = buildList {
        add(lesson.id)
        addAll(lesson.visuals.map { it.id })
        addAll(lesson.contentFields.map { it.id })
        addAll(lesson.contentFields.flatMap { field -> field.translations.map { it.id } })
        addAll(lesson.translationStatus.values.map { it.id })
        lesson.stages.forEach { stage ->
            add(stage.id)
            addAll(stage.contentBlocks.map { it.pageId }.distinct())
            stage.contentBlocks.forEach { block ->
                add(block.id)
                addAll(block.terms.map { it.id })
            }
            stage.exerciseSets.forEach { set ->
                add(set.id)
                set.questionBlueprints.forEach { question ->
                    add(question.id)
                    addAll(question.answerOptionIds)
                }
            }
        }
    }
}
