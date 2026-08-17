package be.saikono.poincare.domain.curriculum.manifest

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
 * Additive Grade 1 depth layer for the 90-125 active-hour curriculum target.
 *
 * Existing units, lessons and progress identifiers remain untouched. Each core
 * unit receives twelve short learning-cycle lessons and one later readiness
 * check. The lessons deliberately expose explicit instruction, guided work,
 * independent work, retrieval, real-world transfer and an exit check as
 * separate runtime sections so future Compose scenes and animations can replace
 * the textual scene descriptions without another progress migration.
 */
object Grade1HundredHourPolicy {
    const val VERSION: Int = 1
    const val DEFINITION_VERSION: Int = 39
    const val TAG: String = "grade1-depth-v1"
    const val CYCLE_LESSONS_PER_UNIT: Int = 12
    const val READINESS_LESSONS_PER_UNIT: Int = 1
    const val ADDED_LESSONS_PER_UNIT: Int = CYCLE_LESSONS_PER_UNIT + READINESS_LESSONS_PER_UNIT
    const val GRADE1_UNIT_COUNT: Int = 13
    const val ADDED_LESSON_COUNT: Int = GRADE1_UNIT_COUNT * ADDED_LESSONS_PER_UNIT
    const val ADDED_ESTIMATED_MINUTES: Int = 4_940
    const val EXPECTED_TOTAL_LESSON_COUNT: Int = 657
    const val EXPECTED_TOTAL_GENERATED_SOURCE_COUNT: Int = 783
    const val EXPECTED_TOTAL_PRACTICAL_SOURCE_COUNT: Int = 257
    const val EXPECTED_TOTAL_EXERCISE_COUNT: Int = 5_859
    const val EXPECTED_TOTAL_ESTIMATED_MINUTES: Int = 10_962
    const val EXPECTED_GRADE1_MINUTES: Int = 6_236
    const val EXPECTED_GRADE1_SECTION_COUNT: Int = 1_151

    private const val ADDITION_UNIT_ID = "math.addition.g1.foundations"
    private const val MEASUREMENT_UNIT_ID = "unit.g1.geometry.measurement_shapes.foundations"
    private const val NUMBER_SENSE_UNIT_ID = "unit.g1.math.number_sense.foundations"
    private const val LISTENING_UNIT_ID = "unit.g1.language.listening.instructions_stories"
    private const val TECHNICAL_READING_UNIT_ID = "unit.g1.language.technical_reading.decoding"
    private const val SPELLING_UNIT_ID = "unit.g1.spelling.phonology.sound_letter"
    private const val WRITING_UNIT_ID = "unit.g1.language.writing.letters_sentences"
    private const val NATURE_UNIT_ID = "unit.g1.world.environment_science"

    private val approvedAdditionExerciseIds = listOf(
        "addition_d1_g1_seq0013",
        "addition_d1_g1_seq0016",
        "addition_d1_g1_seq0018",
        "addition_d1_g1_seq0019",
        "addition_d1_g1_seq0020",
        "addition_d1_g1_seq0021",
        "addition_d1_g1_seq0022",
        "addition_d1_g1_seq0023",
        "addition_d1_g1_seq0024",
        "addition_d1_g1_seq0025",
        "addition_d1_g1_seq0026",
        "addition_d1_g1_seq0028",
        "addition_d1_g1_seq0034",
        "addition_d2_g1_seq0049",
        "addition_d2_g1_seq0050",
        "addition_d2_g1_seq0051",
        "addition_d2_g1_seq0052",
        "addition_d2_g1_seq0053",
        "addition_d2_g1_seq0054",
        "addition_d2_g1_seq0055",
        "addition_d2_g1_seq0056",
        "addition_d2_g1_seq0057",
        "addition_d2_g1_seq0058",
        "addition_d2_g1_seq0059",
        "addition_d2_g1_seq0060",
        "addition_d2_g1_seq0061",
        "addition_d2_g1_seq0062",
        "addition_d2_g1_seq0063",
        "addition_d2_g1_seq0064",
        "addition_d2_g1_seq0065",
        "addition_d2_g1_seq0066",
        "addition_d2_g1_seq0067",
        "addition_d3_g1_seq0080",
        "addition_d3_g1_seq0081",
        "addition_d3_g1_seq0082",
        "addition_d3_g1_seq0083",
        "addition_d3_g1_seq0085",
        "addition_d3_g1_seq0087",
        "addition_d3_g1_seq0088",
        "addition_d3_g1_seq0090",
        "addition_d3_g1_seq0091",
        "addition_d3_g1_seq0093",
        "addition_d3_g1_seq0094",
        "addition_d3_g1_seq0096",
        "addition_d3_g1_seq0097",
        "addition_d3_g1_seq0098",
        "addition_d3_g1_seq0100"
    )

    private val approvedExerciseIdsByTarget: Map<CuratedTarget, List<String>> = mapOf(
        CuratedTarget(ADDITION_UNIT_ID, "addition") to approvedAdditionExerciseIds,
        CuratedTarget(ADDITION_UNIT_ID, "subtraction") to exerciseIds(
            "subtraction",
            1,
            (1..14) + listOf(16, 17, 18, 20, 21, 22, 23, 24, 25, 26, 27, 28, 29, 30, 31, 32, 33)
        ) + exerciseIds(
            "subtraction",
            2,
            listOf(35, 37, 38, 39, 40, 41, 42, 43, 44, 45, 46, 47, 48, 49, 50, 51, 52)
        ),
        CuratedTarget(MEASUREMENT_UNIT_ID, "comparison") to exerciseIds(
            "compare_size",
            1,
            1..16
        ),
        CuratedTarget(MEASUREMENT_UNIT_ID, "measurement") to exerciseIds(
            "measurement",
            1,
            listOf(1, 2, 3, 4, 6, 7)
        ),
        CuratedTarget(MEASUREMENT_UNIT_ID, "time") to exerciseIds(
            "time",
            1,
            listOf(1, 2, 4, 5, 9, 11, 12, 14, 15, 17, 19)
        ) + exerciseIds(
            "time",
            2,
            listOf(35, 37, 42, 46, 48, 50, 52, 57, 58, 64)
        ) + exerciseIds("time", 3, listOf(78)),
        CuratedTarget(MEASUREMENT_UNIT_ID, "money") to exerciseIds(
            "money",
            1,
            1..16
        ),
        CuratedTarget(MEASUREMENT_UNIT_ID, "shapes") to exerciseIds(
            "geometry_shapes",
            1,
            1..16
        ),
        CuratedTarget(NUMBER_SENSE_UNIT_ID, "core") to exerciseIds(
            "number_sequences",
            1,
            listOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 11, 13, 16, 18, 19, 20, 23, 25, 26, 28, 30, 34)
        ) + exerciseIds(
            "number_sequences",
            2,
            listOf(37, 40, 42, 43, 44, 46, 47, 51, 56, 58, 59, 61, 63, 67)
        ) + exerciseIds("number_sequences", 3, listOf(74, 80, 81, 94)),
        CuratedTarget(LISTENING_UNIT_ID, "core") to exerciseIds(
            "listening",
            1,
            1..5
        ) + exerciseIds(
            "listening",
            2,
            35..39
        ) + exerciseIds("listening", 3, 68..72),
        CuratedTarget(TECHNICAL_READING_UNIT_ID, "core") to exerciseIds(
            "reading",
            1,
            1..34
        ) + exerciseIds("reading", 2, 35..64),
        CuratedTarget(SPELLING_UNIT_ID, "core") to exerciseIds(
            "spelling",
            1,
            listOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 15, 24, 28, 32)
        ) + exerciseIds("spelling", 2, listOf(37, 63, 65)) +
            exerciseIds("spelling", 3, listOf(72)),
        CuratedTarget(WRITING_UNIT_ID, "core") to exerciseIds(
            "writing",
            1,
            listOf(1, 11, 21, 31)
        ) + exerciseIds(
            "writing",
            2,
            listOf(41, 51, 61)
        ) + exerciseIds("writing", 3, listOf(71, 81, 91)),
        CuratedTarget(NATURE_UNIT_ID, "core") to exerciseIds(
            "nature",
            1,
            1..20
        )
    )

    private val stages = listOf(
        Stage("concrete", "Eerst met echte voorbeelden", "D'abord avec des exemples concrets", "Start with concrete examples"),
        Stage("visual-model", "Van voorbeeld naar beeld", "De l'exemple au modèle visuel", "From example to visual model"),
        Stage("worked-example", "Een voorbeeld stap voor stap", "Un exemple pas à pas", "A worked example step by step"),
        Stage("guided-a", "Samen oefenen met veel steun", "S'exercer ensemble avec beaucoup d'aide", "Guided practice with strong support"),
        Stage("guided-b", "Samen oefenen met minder steun", "S'exercer ensemble avec moins d'aide", "Guided practice with less support"),
        Stage("strategy-choice", "Een passende aanpak kiezen", "Choisir une stratégie adaptée", "Choose a suitable strategy"),
        Stage("independent", "Zelfstandig toepassen", "Appliquer seul", "Apply independently"),
        Stage("error-repair", "Fouten onderzoeken en herstellen", "Comprendre et corriger les erreurs", "Investigate and repair errors"),
        Stage("retrieval", "Ophalen zonder voorbeeld", "Rappeler sans exemple", "Retrieve without an example"),
        Stage("interleave", "Mengen en verbinden", "Mélanger et relier", "Mix and connect"),
        Stage("transfer", "Gebruiken in een nieuwe situatie", "Utiliser dans une nouvelle situation", "Use in a new situation"),
        Stage("mastery", "Voorbereiden op beheersing", "Se préparer à la maîtrise", "Prepare for mastery")
    )

    fun apply(manifest: CurriculumManifest): CurriculumManifest {
        val grade1Units = manifest.lessonCatalog.units.filter { 1 in it.grades }
        val depthLessons = grade1Units.flatMap { it.lessons }.count { TAG in it.tags }
        if (depthLessons == ADDED_LESSON_COUNT) return manifest
        require(depthLessons == 0) {
            "Grade 1 depth policy found a partial installation: $depthLessons of $ADDED_LESSON_COUNT lessons."
        }
        require(grade1Units.size == GRADE1_UNIT_COUNT) {
            "Grade 1 depth policy requires exactly $GRADE1_UNIT_COUNT Grade 1 units."
        }

        val expandedUnits = manifest.lessonCatalog.units.map { unit ->
            if (1 !in unit.grades) unit else expandUnit(unit)
        }
        return manifest.copy(
            definitionVersion = DEFINITION_VERSION,
            lessonCatalog = manifest.lessonCatalog.copy(
                definitionVersion = DEFINITION_VERSION,
                units = expandedUnits
            )
        )
    }

    fun approvedAdditionIds(): List<String> = approvedAdditionExerciseIds

    fun approvedCuratedIds(): List<String> = approvedExerciseIdsByTarget.values.flatten()

    private fun expandUnit(unit: CurriculumUnit): CurriculumUnit {
        val generated = unit.lessons
            .flatMap { it.sections }
            .mapNotNull { it.activitySource as? LessonActivitySource.Generated }
        val guidedTemplate = generated.firstOrNull { it.parameters["phase"] == "guided" }
        val checkpointTemplate = generated.firstOrNull { it.parameters["phase"] == "checkpoint" }
        val baseSequence = unit.lessons.maxOf { it.sequence }
        val predecessor = unit.lessons
            .filter { it.requirement != CurriculumRequirement.OPTIONAL }
            .maxByOrNull { it.sequence }
            ?.id
            ?: unit.lessons.maxBy { it.sequence }.id

        val cycleLessons = stages.mapIndexed { index, stage ->
            val previousId = if (index == 0) predecessor else depthLessonId(unit, stages[index - 1])
            cycleLesson(
                unit = unit,
                stage = stage,
                stageIndex = index,
                sequence = baseSequence + index + 1,
                prerequisiteLessonId = previousId,
                guidedTemplate = guidedTemplate,
                checkpointTemplate = checkpointTemplate
            )
        }
        val readiness = readinessLesson(
            unit = unit,
            sequence = baseSequence + CYCLE_LESSONS_PER_UNIT + 1,
            prerequisiteLessonId = cycleLessons.last().id,
            checkpointTemplate = checkpointTemplate
        )
        return unit.copy(
            lessons = unit.lessons + cycleLessons + readiness
        )
    }

    private fun cycleLesson(
        unit: CurriculumUnit,
        stage: Stage,
        stageIndex: Int,
        sequence: Int,
        prerequisiteLessonId: String,
        guidedTemplate: LessonActivitySource.Generated?,
        checkpointTemplate: LessonActivitySource.Generated?
    ): LessonDefinition {
        val lessonId = depthLessonId(unit, stage)
        val guidedId = "$lessonId.guided"
        val independentId = "$lessonId.independent"
        val practicalId = "$lessonId.application"
        val checkpointId = "$lessonId.exit-check"
        val focus = focusFor(unit, stageIndex)
        val guidedSource = guidedTemplate?.let {
            generatedSource(unit, it, "guided", 4, focus, stageIndex)
        }
        val checkpointSource = checkpointTemplate?.let {
            generatedSource(unit, it, "checkpoint", 4, focus, stageIndex)
        }
        val independentSource = independentSource(unit, stageIndex, focus, guidedTemplate)
        val requiredSections = buildList {
            if (guidedSource != null) add(guidedId)
            if (independentSource != null) add(independentId)
            add(practicalId)
            if (checkpointSource != null) add(checkpointId)
        }

        return LessonDefinition(
            id = lessonId,
            grade = 1,
            sequence = sequence,
            title = text(stage.nl, stage.fr, stage.en),
            summary = text(
                "Een korte volledige leercyclus rond ${focusLabel(unit, focus, "nl-BE")}.",
                "Un cycle d'apprentissage court et complet autour de ${focusLabel(unit, focus, "fr-BE")}.",
                "A short complete learning cycle about ${focusLabel(unit, focus, "en-US")}."
            ),
            requirement = unit.requirement,
            availability = LessonAvailabilityPolicy.AFTER_PREREQUISITES,
            prerequisites = completedLesson(prerequisiteLessonId),
            learningGoalIds = unit.learningGoals.map { it.id },
            parentSupport = ParentSupportLevel.REQUIRED,
            estimatedMinutes = 30,
            mastery = LessonMasteryCriteria(
                minimumCorrectRatio = if (stage.slug == "mastery") 0.85 else 0.75,
                maximumHints = if (stage.slug.startsWith("guided")) 4 else 2,
                requiredSectionIds = requiredSections
            ),
            sections = listOf(
                LessonSection(
                    id = "$lessonId.instruction",
                    type = LessonSectionType.EXPLANATION,
                    title = text("Kijk en denk mee", "Observe et réfléchis", "Watch and think"),
                    content = instructionScene(unit, focus, stage),
                    parentSupport = supportFor(unit),
                    estimatedMinutes = 4
                ),
                LessonSection(
                    id = guidedId,
                    type = LessonSectionType.GUIDED_PRACTICE,
                    title = text("Samen proberen", "Essayer ensemble", "Try together"),
                    content = guidedScene(unit, focus),
                    activitySource = guidedSource,
                    parentSupport = supportFor(unit),
                    estimatedMinutes = 6
                ),
                LessonSection(
                    id = independentId,
                    type = LessonSectionType.INDEPENDENT_PRACTICE,
                    title = text("Nu zelf", "Maintenant seul", "Now independently"),
                    content = independentScene(unit, focus),
                    activitySource = independentSource,
                    estimatedMinutes = 6
                ),
                LessonSection(
                    id = "$lessonId.review",
                    type = LessonSectionType.REVIEW,
                    title = text("Ophalen en verbeteren", "Rappeler et améliorer", "Retrieve and improve"),
                    content = reviewScene(unit, focus),
                    parentSupport = supportFor(unit),
                    estimatedMinutes = 3
                ),
                LessonSection(
                    id = practicalId,
                    type = LessonSectionType.PRACTICAL_ACTIVITY,
                    title = text("Doe het in het echt", "Fais-le en vrai", "Do it for real"),
                    activitySource = LessonActivitySource.Practical(
                        activityId = "activity.$lessonId.transfer",
                        instructions = practicalInstructions(unit, focus),
                        requiresAdultVerification = true
                    ),
                    parentSupport = ParentSupportLevel.REQUIRED,
                    estimatedMinutes = 7
                ),
                LessonSection(
                    id = checkpointId,
                    type = LessonSectionType.CHECKPOINT,
                    title = text("Korte zelfstandige check", "Petit contrôle autonome", "Short independent check"),
                    content = exitScene(unit, focus),
                    activitySource = checkpointSource,
                    estimatedMinutes = 4
                )
            ),
            tags = listOf(TAG, "learning-cycle", "stage.${stage.slug}", "focus.$focus")
        )
    }

    private fun readinessLesson(
        unit: CurriculumUnit,
        sequence: Int,
        prerequisiteLessonId: String,
        checkpointTemplate: LessonActivitySource.Generated?
    ): LessonDefinition {
        val lessonId = "lesson.${unit.id}.grade1-readiness"
        val checkpointId = "$lessonId.checkpoint"
        val source = checkpointTemplate?.let {
            generatedSource(unit, it, "checkpoint", 8, "mixed", CYCLE_LESSONS_PER_UNIT)
        }
        val activitySource = source ?: LessonActivitySource.Practical(
            activityId = "activity.$lessonId.observed-check",
            instructions = practicalInstructions(unit, "mixed"),
            requiresAdultVerification = true
        )
        return LessonDefinition(
            id = lessonId,
            grade = 1,
            sequence = sequence,
            title = text(
                "Klaar voor de leerjaarcheck",
                "Prêt pour le bilan de l'année",
                "Ready for the grade check"
            ),
            summary = text(
                "Nieuwe, gemengde opdrachten controleren of dit verplichte leerdoel duurzaam beheerst wordt.",
                "De nouvelles tâches variées vérifient si cet objectif obligatoire est maîtrisé durablement.",
                "New mixed tasks check whether this required goal is mastered securely."
            ),
            requirement = unit.requirement,
            availability = LessonAvailabilityPolicy.AFTER_PREREQUISITES,
            prerequisites = listOf(
                CurriculumPrerequisite(
                    targetType = PrerequisiteTargetType.LESSON,
                    targetId = prerequisiteLessonId,
                    requiredState = PrerequisiteState.MASTERED
                )
            ),
            learningGoalIds = unit.learningGoals.map { it.id },
            parentSupport = if (source == null) ParentSupportLevel.REQUIRED else ParentSupportLevel.NONE,
            estimatedMinutes = 20,
            mastery = LessonMasteryCriteria(
                minimumCorrectRatio = 0.85,
                maximumHints = 1,
                requiredSectionIds = listOf(checkpointId)
            ),
            sections = listOf(
                LessonSection(
                    id = "$lessonId.recall",
                    type = LessonSectionType.REVIEW,
                    title = text("Eerst uit het hoofd", "D'abord de mémoire", "First from memory"),
                    content = reviewScene(unit, "mixed"),
                    estimatedMinutes = 4
                ),
                LessonSection(
                    id = checkpointId,
                    type = if (source == null) {
                        LessonSectionType.PRACTICAL_ACTIVITY
                    } else {
                        LessonSectionType.CHECKPOINT
                    },
                    title = text("Cumulatieve beheersingscheck", "Bilan cumulatif", "Cumulative mastery check"),
                    activitySource = activitySource,
                    parentSupport = if (source == null) ParentSupportLevel.REQUIRED else ParentSupportLevel.NONE,
                    estimatedMinutes = 12
                ),
                LessonSection(
                    id = "$lessonId.reflection",
                    type = LessonSectionType.REVIEW,
                    title = text("Bewijs en volgende stap", "Preuves et prochaine étape", "Evidence and next step"),
                    content = text(
                        "Bekijk welke doelen bewezen zijn en welke opnieuw onderwezen en met nieuwe vragen getoetst moeten worden.",
                        "Regarde quels objectifs sont démontrés et lesquels doivent être réenseignés puis vérifiés avec de nouvelles questions.",
                        "Review which goals are evidenced and which need reteaching followed by a new equivalent check."
                    ),
                    estimatedMinutes = 4
                )
            ),
            tags = listOf(TAG, "grade1-readiness", "unit-mastery-evidence")
        )
    }

    private fun independentSource(
        unit: CurriculumUnit,
        stageIndex: Int,
        focus: String,
        guidedTemplate: LessonActivitySource.Generated?
    ): LessonActivitySource? {
        val approvedIds = approvedExerciseIdsByTarget[CuratedTarget(unit.id, focus)]
        val chunkIndex = when (unit.id) {
            ADDITION_UNIT_ID -> if (focus == "subtraction") stageIndex - 6 else stageIndex
            MEASUREMENT_UNIT_ID -> (0..stageIndex).count {
                focusFor(unit, it) == focus
            } - 1
            else -> stageIndex
        }
        val curatedIds = approvedIds?.chunked(8)?.getOrNull(chunkIndex)
        if (!curatedIds.isNullOrEmpty()) {
            return LessonActivitySource.Curated(curatedIds)
        }
        return guidedTemplate?.let {
            generatedSource(unit, it, "guided", 4, focus, stageIndex)
        }
    }

    private fun generatedSource(
        unit: CurriculumUnit,
        template: LessonActivitySource.Generated,
        phase: String,
        count: Int,
        focus: String,
        stageIndex: Int
    ): LessonActivitySource.Generated {
        val parameters = template.parameters.toMutableMap().apply {
            this["phase"] = phase
            if (unit.id == ADDITION_UNIT_ID) {
                this["operation"] = if (stageIndex < 6) "addition" else "subtraction"
            }
            if (unit.id == MEASUREMENT_UNIT_ID) {
                this["focus"] = focus
            }
        }
        return template.copy(
            exerciseCount = count,
            seedPolicy = LessonSeedPolicy.STABLE_PER_PROFILE_ATTEMPT,
            parameters = parameters.toSortedMap()
        )
    }

    private fun focusFor(unit: CurriculumUnit, stageIndex: Int): String = when (unit.id) {
        ADDITION_UNIT_ID -> if (stageIndex < 6) "addition" else "subtraction"
        MEASUREMENT_UNIT_ID -> listOf(
            "comparison",
            "comparison",
            "measurement",
            "measurement",
            "measurement",
            "time",
            "time",
            "time",
            "money",
            "money",
            "shapes",
            "shapes"
        )[stageIndex]
        else -> "core"
    }

    private fun instructionScene(unit: CurriculumUnit, focus: String, stage: Stage): LessonText =
        when (unit.domain) {
            LearningDomain.MATH -> text(
                "Leg blokjes in twee rijen voor $focus. Schuif één stap tegelijk, benoem wat verandert en toon daarna dezelfde stap op een getallenlijn. Werk één voorbeeld volledig uit.",
                "Place des jetons en deux rangées pour $focus. Déplace-les étape par étape, dis ce qui change, puis montre la même étape sur une droite numérique. Résous un exemple complet.",
                "Arrange counters in two rows for $focus. Move one step at a time, name what changes, then show the same step on a number line. Work one full example."
            )
            LearningDomain.GEOMETRY -> text(
                "Gebruik echte voorwerpen en een eenvoudig beeldmodel voor ${focusLabel(unit, focus, "nl-BE")}. Voorspel eerst, wijs daarna de relevante kenmerken aan en controleer stap voor stap.",
                "Utilise de vrais objets et un modèle visuel simple pour ${focusLabel(unit, focus, "fr-BE")}. Prédis d'abord, montre ensuite les caractéristiques utiles et vérifie pas à pas.",
                "Use real objects and a simple visual model for ${focusLabel(unit, focus, "en-US")}. Predict first, point out the useful features, then check step by step."
            )
            LearningDomain.READING -> text(
                "Luister of kijk naar één kort voorbeeld. Markeer de klank, het woord of de informatie die helpt. Denk hardop voor: eerst kijken of luisteren, dan aanwijzen, daarna controleren.",
                "Écoute ou observe un court exemple. Repère le son, le mot ou l'information utile. Modélise: écouter ou regarder, montrer l'indice, puis vérifier.",
                "Listen to or inspect one short example. Mark the sound, word or detail that helps. Model: look or listen, point to the clue, then check."
            )
            LearningDomain.WRITING -> text(
                "Bekijk het voorbeeld zonder het meteen over te nemen. Benoem klank, lettervorm, woordvolgorde of leesteken, modelleer één juiste stap en vergelijk met een veelgemaakte fout.",
                "Observe l'exemple sans le copier tout de suite. Nomme le son, la forme de la lettre, l'ordre des mots ou le signe, puis compare une étape correcte à une erreur fréquente.",
                "Inspect the example before copying it. Name the sound, letter form, word order or punctuation, model one correct step and compare it with a common error."
            )
            LearningDomain.WORLD_KNOWLEDGE -> text(
                "Start met een veilige waarneembare situatie. Stel één vraag, voorspel, bekijk twee aanwijzingen en leg uit welk besluit wel en niet door de waarneming wordt ondersteund.",
                "Pars d'une situation sûre et observable. Pose une question, prédis, examine deux indices et explique quelle conclusion est ou n'est pas soutenue.",
                "Start with a safe observable situation. Ask one question, predict, inspect two clues and explain which conclusion is and is not supported."
            )
            LearningDomain.PUZZLE -> text(
                "Bekijk het probleem, benoem de regel, probeer één stap en controleer of die stap bij alle aanwijzingen past.",
                "Observe le problème, nomme la règle, essaie une étape et vérifie qu'elle respecte tous les indices.",
                "Inspect the problem, name the rule, try one step and check it against every clue."
            )
        }

    private fun guidedScene(unit: CurriculumUnit, focus: String): LessonText = text(
        "Los samen nieuwe voorbeelden op. Laat het kind telkens voorspellen, één houvast kiezen en uitleggen waarom die stap past bij ${focusLabel(unit, focus, "nl-BE")}.",
        "Résolvez ensemble de nouveaux exemples. L'enfant prédit, choisit un appui et explique pourquoi l'étape convient à ${focusLabel(unit, focus, "fr-BE")}.",
        "Solve new examples together. Ask the child to predict, choose one scaffold and explain why the step fits ${focusLabel(unit, focus, "en-US")}."
    )

    private fun independentScene(unit: CurriculumUnit, focus: String): LessonText = text(
        "Werk zonder voorgedaan antwoord. Kies zelf een aanpak, geef het antwoord en controleer met een tweede voorstelling of strategie.",
        "Travaille sans réponse modèle. Choisis une stratégie, réponds et vérifie avec une deuxième représentation ou stratégie.",
        "Work without a model answer. Choose a strategy, answer and check with a second representation or strategy."
    )

    private fun reviewScene(unit: CurriculumUnit, focus: String): LessonText = text(
        "Vertel uit het hoofd de belangrijkste stap voor ${focusLabel(unit, focus, "nl-BE")}. Vergelijk daarna met het model en herstel één onvolledige of foutieve stap.",
        "Explique de mémoire l'étape essentielle pour ${focusLabel(unit, focus, "fr-BE")}. Compare ensuite avec le modèle et corrige une étape incomplète ou fausse.",
        "Recall the key step for ${focusLabel(unit, focus, "en-US")}. Then compare with the model and repair one incomplete or incorrect step."
    )

    private fun exitScene(unit: CurriculumUnit, focus: String): LessonText = text(
        "Beantwoord de nieuwe vragen zelfstandig. Voorlezen, voorbeelden en begeleide antwoorden tellen niet mee voor deze korte beheersingscheck.",
        "Réponds seul aux nouvelles questions. La lecture, les exemples et les réponses guidées ne comptent pas dans ce petit bilan.",
        "Answer the new questions independently. Read-aloud support, examples and guided answers do not count in this short mastery check."
    )

    private fun practicalInstructions(unit: CurriculumUnit, focus: String): LessonText =
        when (unit.domain) {
            LearningDomain.READING -> text(
                "Gebruik een nieuw boek, kaartje of mondeling bericht rond ${focusLabel(unit, focus, "nl-BE")}. Het kind leest of luistert, wijst twee relevante aanwijzingen aan en vertelt de kern. De volwassene noteert alleen wat werkelijk gebeurde.",
                "Utilisez un nouveau livre, une carte ou un message oral autour de ${focusLabel(unit, focus, "fr-BE")}. L'enfant lit ou écoute, montre deux indices utiles et reformule l'essentiel. L'adulte confirme uniquement ce qui a été réalisé.",
                "Use a new book, card or spoken message about ${focusLabel(unit, focus, "en-US")}. The child reads or listens, points to two useful clues and retells the key idea. The adult confirms only what was completed."
            )
            LearningDomain.WRITING -> text(
                "Laat het kind op papier toepassen wat geoefend werd rond ${focusLabel(unit, focus, "nl-BE")}. Controleer houding, leesbaarheid, inhoud en taal; laat het kind minstens één verbetering aanbrengen. De volwassene beoordeelt geen vaardigheid die niet echt werd getoond.",
                "L'enfant applique sur papier ce qui a été travaillé autour de ${focusLabel(unit, focus, "fr-BE")}. Vérifiez posture, lisibilité, contenu et langue, puis faites apporter une amélioration. L'adulte n'évalue que ce qui a réellement été montré.",
                "Have the child apply the work on paper about ${focusLabel(unit, focus, "en-US")}. Check posture, legibility, content and language, then have the child make one improvement. The adult assesses only what was actually shown."
            )
            LearningDomain.MATH, LearningDomain.GEOMETRY -> text(
                "Maak met voorwerpen, een klok, munten, een meetinstrument of een tekening een nieuwe situatie rond ${focusLabel(unit, focus, "nl-BE")}. Het kind voorspelt, voert uit en controleert met een tweede aanpak. De volwassene bevestigt uitvoering, niet automatisch meesterschap.",
                "Créez avec des objets, une horloge, des pièces, un instrument de mesure ou un dessin une nouvelle situation autour de ${focusLabel(unit, focus, "fr-BE")}. L'enfant prédit, réalise et vérifie d'une autre manière. L'adulte confirme l'activité, pas une maîtrise automatique.",
                "Use objects, a clock, coins, a measuring tool or a drawing to create a new situation about ${focusLabel(unit, focus, "en-US")}. The child predicts, carries it out and checks another way. The adult confirms completion, not automatic mastery."
            )
            LearningDomain.WORLD_KNOWLEDGE -> text(
                "Voer samen een veilige observatie, simulatie of rollenspel uit rond ${focusLabel(unit, focus, "nl-BE")}. Het kind stelt een vraag, beschrijft twee waarnemingen en formuleert een voorzichtig besluit of veilige keuze. Vraag hulp van een vertrouwde volwassene bij onveilige situaties.",
                "Réalisez une observation sûre, une simulation ou un jeu de rôle autour de ${focusLabel(unit, focus, "fr-BE")}. L'enfant pose une question, décrit deux observations et formule une conclusion prudente ou un choix sûr. Demandez l'aide d'un adulte de confiance en cas de danger.",
                "Carry out a safe observation, simulation or role-play about ${focusLabel(unit, focus, "en-US")}. The child asks a question, describes two observations and gives a cautious conclusion or safe choice. Seek help from a trusted adult in unsafe situations."
            )
            LearningDomain.PUZZLE -> text(
                "Los samen een nieuw probleem op, vergelijk twee strategieën en laat het kind uitleggen welke aanwijzing de doorslag gaf.",
                "Résolvez un nouveau problème, comparez deux stratégies et demandez à l'enfant quel indice a été décisif.",
                "Solve a new problem together, compare two strategies and ask the child which clue made the difference."
            )
        }

    private fun focusLabel(unit: CurriculumUnit, focus: String, locale: String): String {
        if (unit.id == MEASUREMENT_UNIT_ID) {
            return when (focus) {
                "comparison" -> localizedRaw(locale, "vergelijken en ordenen", "comparer et ordonner", "comparing and ordering")
                "measurement" -> localizedRaw(locale, "meten en vergelijken", "mesurer et comparer", "measuring and comparing")
                "time" -> localizedRaw(locale, "tijd en hele uren", "le temps et les heures entières", "time and whole hours")
                "money" -> localizedRaw(locale, "munten en eenvoudige geldbedragen", "les pièces et les petites sommes", "coins and simple amounts")
                "shapes" -> localizedRaw(locale, "vormen en hun kenmerken", "les formes et leurs propriétés", "shapes and their features")
                else -> localizedRaw(locale, "meten, tijd, geld en vormen", "mesures, temps, argent et formes", "measurement, time, money and shapes")
            }
        }
        if (unit.id == ADDITION_UNIT_ID) {
            return when (focus) {
                "subtraction" -> localizedRaw(locale, "aftrekken tot 20", "soustraire jusqu'à 20", "subtracting within 20")
                else -> localizedRaw(locale, "optellen tot 20", "additionner jusqu'à 20", "adding within 20")
            }
        }
        return (unit.description ?: unit.title).visible.getRaw(locale)
    }

    private fun localizedRaw(locale: String, nl: String, fr: String, en: String): String = when (locale) {
        "fr-BE" -> fr
        "en-US" -> en
        else -> nl
    }

    private fun supportFor(unit: CurriculumUnit): ParentSupportLevel = when {
        unit.id == Grade1MandatoryWritingPolicy.UNIT_ID -> ParentSupportLevel.REQUIRED
        unit.learningGoals.any { it.parentSupport == ParentSupportLevel.REQUIRED } -> ParentSupportLevel.RECOMMENDED
        unit.learningGoals.any { it.parentSupport == ParentSupportLevel.RECOMMENDED } -> ParentSupportLevel.RECOMMENDED
        else -> ParentSupportLevel.NONE
    }

    private fun depthLessonId(unit: CurriculumUnit, stage: Stage): String =
        "lesson.${unit.id}.depth.${stage.slug}"

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

    private data class Stage(
        val slug: String,
        val nl: String,
        val fr: String,
        val en: String
    )

    private data class CuratedTarget(
        val unitId: String,
        val focus: String
    )

    private fun exerciseIds(
        prefix: String,
        difficulty: Int,
        sequences: Iterable<Int>
    ): List<String> = sequences.map { sequence ->
        "${prefix}_d${difficulty}_g1_seq${sequence.toString().padStart(4, '0')}"
    }
}
