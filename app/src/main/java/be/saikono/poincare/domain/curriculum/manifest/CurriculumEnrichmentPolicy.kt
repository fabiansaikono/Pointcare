package be.saikono.poincare.domain.curriculum.manifest

import be.saikono.poincare.domain.curriculum.CurriculumCatalog
import be.saikono.poincare.domain.curriculum.CurriculumPrerequisite
import be.saikono.poincare.domain.curriculum.CurriculumRequirement
import be.saikono.poincare.domain.curriculum.CurriculumUnit
import be.saikono.poincare.domain.curriculum.LessonActivitySource
import be.saikono.poincare.domain.curriculum.LessonAvailabilityPolicy
import be.saikono.poincare.domain.curriculum.LessonDefinition
import be.saikono.poincare.domain.curriculum.LessonMasteryCriteria
import be.saikono.poincare.domain.curriculum.LessonSection
import be.saikono.poincare.domain.curriculum.LessonSectionType
import be.saikono.poincare.domain.curriculum.LessonSeedPolicy
import be.saikono.poincare.domain.curriculum.LessonText
import be.saikono.poincare.domain.curriculum.ParentSupportLevel
import be.saikono.poincare.domain.curriculum.PrerequisiteState
import be.saikono.poincare.domain.curriculum.PrerequisiteTargetType
import be.saikono.poincare.model.LearningDomain
import be.saikono.poincare.model.LocalizedText

/**
 * Versioned, deterministic expansion of the reviewed Grade 1-6 catalogue.
 *
 * The JSON manifest remains the reviewed curriculum source. This policy adds
 * reusable learning-cycle lessons without changing or replacing any existing
 * unit, lesson, exercise or progress identifier.
 */
object CurriculumEnrichmentPolicy {
    const val VERSION: Int = 1
    const val EXPANDED_DEFINITION_VERSION: Int = 38
    private const val REVIEWED_UNIT_COUNT: Int = 64
    const val EXPECTED_UNIT_COUNT: Int = 65
    const val EXPECTED_LESSON_COUNT: Int = 487
    const val EXPECTED_GENERATED_SOURCE_COUNT: Int = 384
    const val EXPECTED_PRACTICAL_SOURCE_COUNT: Int = 96
    const val EXPECTED_EXERCISE_COUNT: Int = 3_876
    const val EXPECTED_ESTIMATED_MINUTES: Int = 6_002

    const val ENRICHMENT_TAG: String = "enrichment-v1"
    const val REMEDIATION_TAG: String = "adaptive-remediation"
    const val EXTENSION_TAG: String = "adaptive-extension"
    const val TARGET_TAG_PREFIX: String = "target."

    fun apply(manifest: CurriculumManifest): CurriculumManifest {
        val reviewedUnits = manifest.lessonCatalog.units.filterNot {
            it.id == Grade1MandatoryWritingPolicy.UNIT_ID
        }
        if (reviewedUnits.count(::isExpanded) == REVIEWED_UNIT_COUNT) return manifest
        require(reviewedUnits.size == REVIEWED_UNIT_COUNT) {
            "Curriculum enrichment requires exactly $REVIEWED_UNIT_COUNT reviewed units."
        }

        val expandedCatalog = manifest.lessonCatalog.copy(
            definitionVersion = maxOf(
                EXPANDED_DEFINITION_VERSION,
                manifest.lessonCatalog.definitionVersion + 1
            ),
            units = manifest.lessonCatalog.units.map { unit ->
                if (unit.id == Grade1MandatoryWritingPolicy.UNIT_ID) unit else expandUnit(unit)
            }
        )
        return manifest.copy(
            definitionVersion = maxOf(
                EXPANDED_DEFINITION_VERSION,
                manifest.definitionVersion + 1
            ),
            lessonCatalog = expandedCatalog
        )
    }

    fun adaptiveTargetLessonId(lesson: LessonDefinition): String? = lesson.tags
        .firstOrNull { it.startsWith(TARGET_TAG_PREFIX) }
        ?.removePrefix(TARGET_TAG_PREFIX)

    private fun isExpanded(unit: CurriculumUnit): Boolean =
        unit.lessons.any { ENRICHMENT_TAG in it.tags }

    private fun expandUnit(unit: CurriculumUnit): CurriculumUnit {
        require(!isExpanded(unit)) { "Unit ${unit.id} is already enriched." }
        val locatedSources = unit.lessons.flatMap { lesson ->
            lesson.sections.mapNotNull { section ->
                (section.activitySource as? LessonActivitySource.Generated)
                    ?.let { LocatedGeneratedSource(lesson, it) }
            }
        }
        val guided = locatedSources.firstOrNull { it.source.parameters["phase"] == "guided" }
            ?: error("Unit ${unit.id} has no guided generator source.")
        val checkpoint = locatedSources.firstOrNull { it.source.parameters["phase"] == "checkpoint" }
            ?: error("Unit ${unit.id} has no checkpoint generator source.")
        val lastCoreLesson = unit.lessons.maxByOrNull { it.sequence }
            ?: error("Unit ${unit.id} has no lessons.")
        val maxSequence = lastCoreLesson.sequence

        val remediation = remediationLesson(
            unit = unit,
            sequence = maxSequence + 1,
            targetLessonId = checkpoint.lesson.id,
            source = sourceFor(unit, guided.source, phase = "guided", exerciseCount = 8)
        )
        val reinforcement = reinforcementLesson(
            unit = unit,
            sequence = maxSequence + 2,
            prerequisiteLessonId = lastCoreLesson.id,
            source = sourceFor(unit, guided.source, phase = "guided", exerciseCount = 10)
        )
        val mastery = masteryLesson(
            unit = unit,
            sequence = maxSequence + 3,
            prerequisiteLessonId = reinforcement.id,
            source = sourceFor(unit, checkpoint.source, phase = "checkpoint", exerciseCount = 12)
        )
        val practical = if (unit.hasPracticalActivity()) {
            emptyList()
        } else {
            listOf(practicalLesson(unit, maxSequence + 4, mastery.id))
        }
        val extension = extensionLesson(
            unit = unit,
            sequence = maxSequence + 5,
            prerequisiteLessonId = mastery.id,
            source = sourceFor(unit, checkpoint.source, phase = "checkpoint", exerciseCount = 12)
        )

        return unit.copy(
            lessons = unit.lessons + remediation + reinforcement + mastery + practical + extension
        )
    }

    private fun sourceFor(
        unit: CurriculumUnit,
        template: LessonActivitySource.Generated,
        phase: String,
        exerciseCount: Int
    ): LessonActivitySource.Generated {
        val parameters = template.parameters.toMutableMap().apply {
            this["phase"] = phase
            if (unit.id == "math.addition.g1.foundations") {
                this["operation"] = when (phase) {
                    "guided" -> "addition"
                    else -> "subtraction"
                }
            }
        }
        return template.copy(
            exerciseCount = exerciseCount,
            seedPolicy = LessonSeedPolicy.STABLE_PER_PROFILE_ATTEMPT,
            parameters = parameters.toSortedMap().toMap()
        )
    }

    private fun remediationLesson(
        unit: CurriculumUnit,
        sequence: Int,
        targetLessonId: String,
        source: LessonActivitySource.Generated
    ): LessonDefinition {
        val lessonId = "lesson.${unit.id}.adaptive-support"
        val practiceId = "$lessonId.guided"
        return LessonDefinition(
            id = lessonId,
            grade = unit.grades.single(),
            sequence = sequence,
            title = text(
                "Extra ondersteuning",
                "Soutien supplémentaire",
                "Extra support"
            ),
            summary = text(
                "Rustiger opnieuw oefenen met extra houvast.",
                "Reprendre plus calmement avec davantage de soutien.",
                "Practise again at a gentler pace with more support."
            ),
            requirement = CurriculumRequirement.RECOMMENDED,
            availability = LessonAvailabilityPolicy.ALWAYS,
            learningGoalIds = unit.learningGoals.map { it.id },
            parentSupport = recommendedSupport(unit),
            estimatedMinutes = 10,
            mastery = LessonMasteryCriteria(
                minimumCorrectRatio = 0.7,
                maximumHints = 4,
                requiredSectionIds = listOf(practiceId)
            ),
            sections = listOf(
                LessonSection(
                    id = "$lessonId.strategy",
                    type = LessonSectionType.EXPLANATION,
                    title = text("Denkstap", "Étape de réflexion", "Thinking step"),
                    content = text(
                        "Neem de tijd. Benoem wat je al weet, werk stap voor stap en controleer je antwoord.",
                        "Prends ton temps. Dis ce que tu sais déjà, avance étape par étape et vérifie ta réponse.",
                        "Take your time. Say what you already know, work step by step and check your answer."
                    ),
                    parentSupport = recommendedSupport(unit),
                    estimatedMinutes = 2
                ),
                LessonSection(
                    id = practiceId,
                    type = LessonSectionType.GUIDED_PRACTICE,
                    title = text("Begeleid oefenen", "Exercice guidé", "Guided practice"),
                    activitySource = source,
                    parentSupport = recommendedSupport(unit),
                    estimatedMinutes = 8
                )
            ),
            tags = listOf(
                ENRICHMENT_TAG,
                REMEDIATION_TAG,
                "$TARGET_TAG_PREFIX$targetLessonId"
            )
        )
    }

    private fun reinforcementLesson(
        unit: CurriculumUnit,
        sequence: Int,
        prerequisiteLessonId: String,
        source: LessonActivitySource.Generated
    ): LessonDefinition {
        val lessonId = "lesson.${unit.id}.reinforcement"
        val practiceId = "$lessonId.practice"
        return LessonDefinition(
            id = lessonId,
            grade = unit.grades.single(),
            sequence = sequence,
            title = text("Herhalen en verbinden", "Réviser et relier", "Review and connect"),
            summary = text(
                "Haal de leerstof opnieuw op en verbind ze met wat je al kent.",
                "Rappelle les notions et relie-les à ce que tu connais déjà.",
                "Retrieve the learning and connect it to what you already know."
            ),
            requirement = unit.requirement,
            availability = LessonAvailabilityPolicy.AFTER_PREREQUISITES,
            prerequisites = completedLesson(prerequisiteLessonId),
            learningGoalIds = unit.learningGoals.map { it.id },
            parentSupport = recommendedSupport(unit),
            estimatedMinutes = 12,
            mastery = LessonMasteryCriteria(
                minimumCorrectRatio = 0.75,
                maximumHints = 3,
                requiredSectionIds = listOf(practiceId)
            ),
            sections = listOf(
                LessonSection(
                    id = "$lessonId.recall",
                    type = LessonSectionType.REVIEW,
                    title = text("Eerst uit het hoofd", "D'abord de mémoire", "First from memory"),
                    content = text(
                        "Vertel eerst zonder hulp wat je nog weet. Controleer daarna en verbeter wat nog niet klopt.",
                        "Explique d'abord sans aide ce dont tu te souviens. Vérifie ensuite et corrige ce qui manque.",
                        "First explain what you remember without help. Then check and correct anything that is missing."
                    ),
                    parentSupport = recommendedSupport(unit),
                    estimatedMinutes = 2
                ),
                LessonSection(
                    id = practiceId,
                    type = LessonSectionType.INDEPENDENT_PRACTICE,
                    title = text("Nieuwe variaties", "Nouvelles variantes", "New variations"),
                    activitySource = source,
                    estimatedMinutes = 10
                )
            ),
            tags = listOf(ENRICHMENT_TAG, "retrieval-practice", "review-cycle-1")
        )
    }

    private fun masteryLesson(
        unit: CurriculumUnit,
        sequence: Int,
        prerequisiteLessonId: String,
        source: LessonActivitySource.Generated
    ): LessonDefinition {
        val lessonId = "lesson.${unit.id}.mastery-review"
        val checkpointId = "$lessonId.checkpoint"
        return LessonDefinition(
            id = lessonId,
            grade = unit.grades.single(),
            sequence = sequence,
            title = text("Herhaling en beheersing", "Révision et maîtrise", "Review and mastery"),
            summary = text(
                "Een tweede herhaling met nieuwe vragen om duurzame beheersing te controleren.",
                "Une deuxième révision avec de nouvelles questions pour vérifier une maîtrise durable.",
                "A second review with new questions to check lasting mastery."
            ),
            requirement = unit.requirement,
            availability = LessonAvailabilityPolicy.AFTER_PREREQUISITES,
            prerequisites = completedLesson(prerequisiteLessonId),
            learningGoalIds = unit.learningGoals.map { it.id },
            parentSupport = ParentSupportLevel.NONE,
            estimatedMinutes = 15,
            mastery = LessonMasteryCriteria(
                minimumCorrectRatio = 0.85,
                maximumHints = 2,
                requiredSectionIds = listOf(checkpointId)
            ),
            sections = listOf(
                LessonSection(
                    id = checkpointId,
                    type = LessonSectionType.CHECKPOINT,
                    title = text("Beheersingscheck", "Vérification de maîtrise", "Mastery check"),
                    activitySource = source,
                    estimatedMinutes = 15
                )
            ),
            tags = listOf(ENRICHMENT_TAG, "spaced-review", "review-cycle-2")
        )
    }

    private fun extensionLesson(
        unit: CurriculumUnit,
        sequence: Int,
        prerequisiteLessonId: String,
        source: LessonActivitySource.Generated
    ): LessonDefinition {
        val lessonId = "lesson.${unit.id}.extension"
        val practiceId = "$lessonId.practice"
        return LessonDefinition(
            id = lessonId,
            grade = unit.grades.single(),
            sequence = sequence,
            title = text("Extra variatie", "Variantes supplémentaires", "Extra variation"),
            summary = text(
                "Pas dezelfde leerstof toe in een nieuwe, gemengde reeks.",
                "Applique les mêmes notions dans une nouvelle série variée.",
                "Apply the same learning in a new mixed set."
            ),
            requirement = CurriculumRequirement.OPTIONAL,
            availability = LessonAvailabilityPolicy.AFTER_PREREQUISITES,
            prerequisites = listOf(
                CurriculumPrerequisite(
                    targetType = PrerequisiteTargetType.LESSON,
                    targetId = prerequisiteLessonId,
                    requiredState = PrerequisiteState.MASTERED
                )
            ),
            learningGoalIds = unit.learningGoals.map { it.id },
            parentSupport = ParentSupportLevel.NONE,
            estimatedMinutes = 15,
            mastery = LessonMasteryCriteria(
                minimumCorrectRatio = 0.9,
                maximumHints = 1,
                requiredSectionIds = listOf(practiceId)
            ),
            sections = listOf(
                LessonSection(
                    id = practiceId,
                    type = LessonSectionType.INDEPENDENT_PRACTICE,
                    title = text("Zelfstandig verdiepen", "Approfondir seul", "Independent extension"),
                    activitySource = source,
                    estimatedMinutes = 15
                )
            ),
            tags = listOf(ENRICHMENT_TAG, EXTENSION_TAG)
        )
    }

    private fun practicalLesson(
        unit: CurriculumUnit,
        sequence: Int,
        prerequisiteLessonId: String
    ): LessonDefinition {
        val lessonId = "lesson.${unit.id}.practical-transfer"
        val sectionId = "$lessonId.activity"
        return LessonDefinition(
            id = lessonId,
            grade = unit.grades.single(),
            sequence = sequence,
            title = text("Praktijkopdracht", "Activité pratique", "Practical activity"),
            summary = text(
                "Gebruik de leerstof buiten een gewone app-oefening.",
                "Utilise les notions en dehors d'un exercice ordinaire dans l'application.",
                "Use the learning beyond a regular app exercise."
            ),
            requirement = if (unit.requirement == CurriculumRequirement.OPTIONAL) {
                CurriculumRequirement.OPTIONAL
            } else {
                CurriculumRequirement.RECOMMENDED
            },
            availability = LessonAvailabilityPolicy.AFTER_PREREQUISITES,
            prerequisites = completedLesson(prerequisiteLessonId),
            learningGoalIds = unit.learningGoals.map { it.id },
            parentSupport = ParentSupportLevel.REQUIRED,
            estimatedMinutes = 12,
            mastery = LessonMasteryCriteria(
                minimumCorrectRatio = 1.0,
                requiredSectionIds = listOf(sectionId)
            ),
            sections = listOf(
                LessonSection(
                    id = sectionId,
                    type = LessonSectionType.PRACTICAL_ACTIVITY,
                    title = text("Doe het in het echt", "Fais-le en vrai", "Do it for real"),
                    activitySource = LessonActivitySource.Practical(
                        activityId = "activity.enrichment.${unit.id}.real-world",
                        instructions = practicalInstructions(unit),
                        requiresAdultVerification = true
                    ),
                    parentSupport = ParentSupportLevel.REQUIRED,
                    estimatedMinutes = 12
                )
            ),
            tags = listOf(ENRICHMENT_TAG, "practical-transfer", "adult-verified")
        )
    }

    private fun practicalInstructions(unit: CurriculumUnit): LessonText {
        val focus = unit.description ?: unit.title
        fun localized(locale: String): String = focus.visible.getRaw(locale)
        return when (unit.domain) {
            LearningDomain.READING -> text(
                "Lees of beluister samen nieuw materiaal over dit leerdoel: ${localized("nl-BE")} Laat het kind de kern navertellen, twee aanwijzingen tonen en één nieuwe vraag beantwoorden. De volwassene bevestigt alleen wat echt werd uitgevoerd.",
                "Lisez ou écoutez ensemble un nouveau support lié à cet objectif : ${localized("fr-BE")} L'enfant reformule l'essentiel, montre deux indices et répond à une nouvelle question. L'adulte confirme uniquement ce qui a réellement été réalisé.",
                "Read or listen to new material linked to this goal: ${localized("en-US")} Ask the child to retell the key idea, point out two clues and answer one new question. The adult confirms only what was actually completed."
            )
            LearningDomain.WRITING -> text(
                "Laat het kind met de hand een korte eigen tekst maken die dit leerdoel toepast: ${localized("nl-BE")} Bespreek samen inhoud, opbouw en taal en laat het kind minstens één verbetering aanbrengen.",
                "Demandez à l'enfant d'écrire à la main un court texte personnel qui applique cet objectif : ${localized("fr-BE")} Discutez du contenu, de la structure et de la langue, puis faites apporter au moins une amélioration.",
                "Ask the child to handwrite a short original text that applies this goal: ${localized("en-US")} Discuss content, structure and language, then have the child make at least one improvement."
            )
            LearningDomain.MATH -> text(
                "Bedenk samen een concrete situatie met voorwerpen, geld, een tekening of meetgegevens rond: ${localized("nl-BE")} Laat het kind eerst voorspellen, daarna uitwerken en ten slotte uitleggen hoe het antwoord werd gecontroleerd.",
                "Créez une situation concrète avec des objets, de l'argent, un dessin ou des mesures autour de : ${localized("fr-BE")} L'enfant prédit, résout puis explique comment la réponse a été vérifiée.",
                "Create a real situation with objects, money, a drawing or measurements around: ${localized("en-US")} Have the child predict, solve and then explain how the answer was checked."
            )
            LearningDomain.GEOMETRY -> text(
                "Gebruik echte voorwerpen, een meetinstrument of een tekening om dit leerdoel toe te passen: ${localized("nl-BE")} Laat het kind meten, vergelijken of construeren en de gekozen werkwijze uitleggen.",
                "Utilisez de vrais objets, un instrument de mesure ou un dessin pour appliquer cet objectif : ${localized("fr-BE")} L'enfant mesure, compare ou construit, puis explique sa démarche.",
                "Use real objects, a measuring tool or a drawing to apply this goal: ${localized("en-US")} Have the child measure, compare or construct and explain the chosen method."
            )
            LearningDomain.WORLD_KNOWLEDGE -> text(
                "Voer samen een veilige observatie, opzoekopdracht of kleine proef uit rond: ${localized("nl-BE")} Laat het kind vooraf een vraag stellen, bevindingen noteren en achteraf besluiten wat de waarneming wel en niet aantoont.",
                "Réalisez ensemble une observation sûre, une recherche ou une petite expérience autour de : ${localized("fr-BE")} L'enfant formule une question, note les résultats puis explique ce que l'observation montre ou ne montre pas.",
                "Carry out a safe observation, lookup task or small investigation around: ${localized("en-US")} Have the child ask a question, record findings and explain what the evidence does and does not show."
            )
            LearningDomain.PUZZLE -> text(
                "Los samen een nieuw probleem op. Laat het kind eerst een strategie kiezen, daarna uitvoeren en tenslotte uitleggen wat bij een volgende poging anders kan.",
                "Résolvez ensemble un nouveau problème. L'enfant choisit une stratégie, l'applique puis explique ce qui pourrait changer lors d'un nouvel essai.",
                "Solve a new problem together. Have the child choose a strategy, carry it out and explain what could change on another attempt."
            )
        }
    }

    private fun CurriculumUnit.hasPracticalActivity(): Boolean = lessons
        .flatMap { it.sections }
        .any { it.activitySource is LessonActivitySource.Practical }

    private fun recommendedSupport(unit: CurriculumUnit): ParentSupportLevel = when {
        unit.learningGoals.any { it.parentSupport == ParentSupportLevel.REQUIRED } ->
            ParentSupportLevel.RECOMMENDED
        unit.learningGoals.any { it.parentSupport == ParentSupportLevel.RECOMMENDED } ->
            ParentSupportLevel.RECOMMENDED
        else -> ParentSupportLevel.NONE
    }

    private fun completedLesson(lessonId: String): List<CurriculumPrerequisite> = listOf(
        CurriculumPrerequisite(
            targetType = PrerequisiteTargetType.LESSON,
            targetId = lessonId,
            requiredState = PrerequisiteState.COMPLETED
        )
    )

    private fun text(nl: String, fr: String, en: String): LessonText = LessonText(
        visible = LocalizedText(nl = nl, fr = fr, en = en)
    )

    private data class LocatedGeneratedSource(
        val lesson: LessonDefinition,
        val source: LessonActivitySource.Generated
    )
}
