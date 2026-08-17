package be.saikono.poincare.domain.curriculum.manifest

import be.saikono.poincare.domain.curriculum.CurriculumPrerequisite
import be.saikono.poincare.domain.curriculum.CurriculumRequirement
import be.saikono.poincare.domain.curriculum.CurriculumUnit
import be.saikono.poincare.domain.curriculum.LearningGoalDefinition
import be.saikono.poincare.domain.curriculum.LessonActivitySource
import be.saikono.poincare.domain.curriculum.LessonAvailabilityPolicy
import be.saikono.poincare.domain.curriculum.LessonDefinition
import be.saikono.poincare.domain.curriculum.LessonMasteryCriteria
import be.saikono.poincare.domain.curriculum.LessonSection
import be.saikono.poincare.domain.curriculum.LessonSectionType
import be.saikono.poincare.domain.curriculum.LessonText
import be.saikono.poincare.domain.curriculum.ParentSupportLevel
import be.saikono.poincare.domain.curriculum.PrerequisiteTargetType
import be.saikono.poincare.model.LearningDomain
import be.saikono.poincare.model.LocalizedText

/**
 * Makes letter formation a mandatory Grade 1 learning path.
 *
 * The existing free letter-tracing route remains available for extra practice.
 * These lessons deliberately require adult verification because handwriting
 * quality cannot be established reliably from a multiple-choice answer.
 */
object Grade1MandatoryWritingPolicy {
    const val UNIT_ID = "unit.g1.writing.letter_formation"
    const val SUBCATEGORY_ID = "letter_tracing"
    const val LESSON_COUNT = 9
    const val PRACTICAL_SOURCE_COUNT = 36
    const val ESTIMATED_MINUTES = 177

    fun apply(manifest: CurriculumManifest): CurriculumManifest {
        if (manifest.lessonCatalog.units.any { it.id == UNIT_ID }) return manifest

        val writingCategory = manifest.categories.single { it.domain == LearningDomain.WRITING }
        val subcategory = CurriculumSubcategoryManifest(
            id = SUBCATEGORY_ID,
            title = localized("Letters schrijven", "Écrire les lettres", "Letter writing"),
            summary = localized(
                "Schrijfhouding, schrijfbewegingen, kleine en hoofdletters, woorden en korte zinnen.",
                "Posture, gestes graphiques, minuscules, majuscules, mots et phrases courtes.",
                "Writing posture, letter movements, lowercase and capital letters, words and short sentences."
            ),
            grades = listOf(1),
            requirement = CurriculumRequirement.MANDATORY,
            deliveryMode = CurriculumDeliveryMode.HYBRID_APP_PARENT,
            parentSupport = ParentSupportLevel.REQUIRED,
            legacySubcategoryIds = listOf(SUBCATEGORY_ID),
            tags = listOf("handwriting", "letter-formation", "mandatory-grade-1"),
            definitionVersion = 1
        )
        val unit = CurriculumUnit(
            id = UNIT_ID,
            domain = LearningDomain.WRITING,
            subcategoryId = SUBCATEGORY_ID,
            grades = listOf(1),
            title = text("Lettervorming en handschrift", "Formation des lettres et écriture", "Letter formation and handwriting"),
            description = text(
                "Leer alle kleine letters en hoofdletters leesbaar en in de juiste schrijfrichting vormen.",
                "Apprends à former lisiblement toutes les minuscules et majuscules dans le bon sens.",
                "Learn to form every lowercase and capital letter legibly and in the correct direction."
            ),
            requirement = CurriculumRequirement.MANDATORY,
            learningGoals = listOf(
                goal("goal.g1.writing.posture_strokes", "Schrijfhouding en basisbewegingen", "Posture et gestes de base", "Writing posture and basic strokes"),
                goal("goal.g1.writing.lowercase_letters", "Kleine letters correct vormen", "Former les minuscules", "Form lowercase letters"),
                goal("goal.g1.writing.capitals_words_sentences", "Hoofdletters, woorden en zinnen schrijven", "Écrire des majuscules, des mots et des phrases", "Write capitals, words and sentences")
            ),
            lessons = lessonSpecs().mapIndexed { index, spec ->
                lesson(index + 1, spec, lessonSpecs().getOrNull(index - 1)?.slug)
            },
            tags = listOf("mandatory-grade-1", "handwriting", "adult-verified"),
            definitionVersion = 1
        )
        val roadmap = CurriculumRoadmapEntry(
            id = "roadmap.g1.writing.letter_formation",
            grade = 1,
            categoryId = writingCategory.id,
            subcategoryIds = listOf(SUBCATEGORY_ID),
            unitId = UNIT_ID,
            title = localized("Lettervorming en handschrift", "Formation des lettres et écriture", "Letter formation and handwriting"),
            learningGoal = localized(
                "Alle kleine letters en hoofdletters leesbaar vormen en toepassen in woorden en korte zinnen.",
                "Former lisiblement toutes les minuscules et majuscules et les utiliser dans des mots et phrases courtes.",
                "Form every lowercase and capital letter legibly and use them in words and short sentences."
            ),
            sourceLearningGoalNl = "Alle kleine letters en hoofdletters leesbaar vormen en toepassen in woorden en korte zinnen.",
            deliveryMode = CurriculumDeliveryMode.HYBRID_APP_PARENT,
            requirement = CurriculumRequirement.MANDATORY,
            parentSupport = ParentSupportLevel.REQUIRED,
            priority = CurriculumPriority.HIGH,
            coverage = "NEW_MANDATORY_PATH",
            decision = "Promoted from optional extra practice to a mandatory, tiered Grade 1 unit.",
            appAction = localized(
                "Toon schrijfmodellen en open gerichte letteroefening per lettergroep.",
                "Montrer les modèles et ouvrir un exercice ciblé par groupe de lettres.",
                "Show writing models and open targeted practice for each letter group."
            ),
            adultAction = localized(
                "Controleer houding, schrijfrichting, leesbaarheid en verbindingen.",
                "Vérifier la posture, le sens du tracé, la lisibilité et les liaisons.",
                "Check posture, stroke direction, legibility and joins."
            ),
            sourceReference = "Vlaamse onderwijsdoelen Nederlands; OVSG Leer Lokaal Nederlands; GO! leerplan Nederlands basisonderwijs",
            definitionVersion = 1
        )

        return manifest.copy(
            definitionVersion = manifest.definitionVersion,
            categories = manifest.categories.map { category ->
                if (category.id == writingCategory.id) {
                    category.copy(subcategories = category.subcategories + subcategory)
                } else {
                    category
                }
            },
            roadmapEntries = manifest.roadmapEntries + roadmap,
            lessonCatalog = manifest.lessonCatalog.copy(
                definitionVersion = manifest.lessonCatalog.definitionVersion,
                units = manifest.lessonCatalog.units + unit
            )
        )
    }

    private fun lesson(
        sequence: Int,
        spec: WritingLessonSpec,
        previousSlug: String?
    ): LessonDefinition {
        val lessonId = "lesson.g1.writing.letter_formation.${spec.slug}"
        val sections = listOf(
            section(lessonId, spec, "model", "Kijken en verwoorden", "Observer et expliquer", "Observe and explain", 3),
            section(lessonId, spec, "trace", "Begeleid overtrekken", "Tracer avec guidage", "Guided tracing", 5),
            section(lessonId, spec, "copy", "Zelfstandig naschrijven", "Copier seul", "Independent copying", 6),
            section(lessonId, spec, "transfer", "Toepassen zonder voorbeeld", "Appliquer sans modèle", "Apply without a model", 6)
        )
        return LessonDefinition(
            id = lessonId,
            grade = 1,
            sequence = sequence,
            title = text(spec.nl, spec.fr, spec.en),
            summary = text(spec.instructionsNl, spec.instructionsFr, spec.instructionsEn),
            requirement = CurriculumRequirement.MANDATORY,
            availability = if (previousSlug == null) {
                LessonAvailabilityPolicy.ALWAYS
            } else {
                LessonAvailabilityPolicy.AFTER_PREREQUISITES
            },
            prerequisites = previousSlug?.let {
                listOf(
                    CurriculumPrerequisite(
                        targetType = PrerequisiteTargetType.LESSON,
                        targetId = "lesson.g1.writing.letter_formation.$it"
                    )
                )
            }.orEmpty(),
            learningGoalIds = spec.goalIds,
            parentSupport = ParentSupportLevel.REQUIRED,
            estimatedMinutes = spec.minutes,
            mastery = LessonMasteryCriteria(
                minimumCorrectRatio = 1.0,
                requiredSectionIds = sections.map { it.id }
            ),
            sections = sections,
            tags = listOf("handwriting", "four-tier-practice", "mandatory"),
            definitionVersion = 1
        )
    }

    private fun section(
        lessonId: String,
        spec: WritingLessonSpec,
        tier: String,
        nl: String,
        fr: String,
        en: String,
        minutes: Int
    ): LessonSection {
        val id = "$lessonId.$tier"
        return LessonSection(
            id = id,
            type = if (tier == "model") LessonSectionType.EXPLANATION else LessonSectionType.PRACTICAL_ACTIVITY,
            title = text(nl, fr, en),
            content = if (tier == "model") {
                text(spec.instructionsNl, spec.instructionsFr, spec.instructionsEn)
            } else {
                null
            },
            activitySource = LessonActivitySource.Practical(
                activityId = "writing.letter_tracing.${spec.slug}.$tier",
                instructions = text(
                    "$nl: ${spec.instructionsNl}",
                    "$fr : ${spec.instructionsFr}",
                    "$en: ${spec.instructionsEn}"
                ),
                requiresAdultVerification = true
            ),
            required = true,
            parentSupport = ParentSupportLevel.REQUIRED,
            estimatedMinutes = minutes
        )
    }

    private fun goal(id: String, nl: String, fr: String, en: String) = LearningGoalDefinition(
        id = id,
        title = text(nl, fr, en),
        requirement = CurriculumRequirement.MANDATORY,
        parentSupport = ParentSupportLevel.REQUIRED
    )

    private fun text(nl: String, fr: String, en: String) = LessonText(localized(nl, fr, en))
    private fun localized(nl: String, fr: String, en: String) = LocalizedText(nl = nl, fr = fr, en = en)

    private fun lessonSpecs() = listOf(
        WritingLessonSpec("houding_basislijnen", "Houding en basislijnen", "Posture et traits de base", "Posture and basic strokes", 17, listOf("goal.g1.writing.posture_strokes"), "Zit stabiel, houd het potlood ontspannen vast en oefen rechte, gebogen en schuine lijnen.", "Assieds-toi de manière stable, tiens le crayon sans serrer et exerce les traits droits, courbes et obliques.", "Sit steadily, hold the pencil lightly and practise straight, curved and slanted strokes."),
        WritingLessonSpec("kleine_letters_recht", "Kleine letters met rechte lijnen", "Minuscules à traits droits", "Lowercase letters with straight strokes", 20, listOf("goal.g1.writing.lowercase_letters"), "Oefen i, l, t en j met de juiste startplaats, richting en verhouding.", "Exerce i, l, t et j avec le bon point de départ, le bon sens et les bonnes proportions.", "Practise i, l, t and j with the correct starting point, direction and proportions."),
        WritingLessonSpec("kleine_letters_rond", "Ronde kleine letters", "Minuscules rondes", "Rounded lowercase letters", 20, listOf("goal.g1.writing.lowercase_letters"), "Oefen c, o, a, d, g en q vanuit dezelfde ronde basisbeweging.", "Exerce c, o, a, d, g et q à partir du même geste arrondi.", "Practise c, o, a, d, g and q from the same rounded movement."),
        WritingLessonSpec("kleine_letters_bogen", "Kleine letters met bogen", "Minuscules à ponts", "Lowercase letters with arches", 20, listOf("goal.g1.writing.lowercase_letters"), "Oefen n, m, r, u, h, b en p met regelmatige bogen en stoklengtes.", "Exerce n, m, r, u, h, b et p avec des ponts réguliers et des hampes correctes.", "Practise n, m, r, u, h, b and p with even arches and correct stem lengths."),
        WritingLessonSpec("kleine_letters_lussen", "Lussen en overige kleine letters", "Boucles et autres minuscules", "Loops and remaining lowercase letters", 20, listOf("goal.g1.writing.lowercase_letters"), "Oefen e, f, s, k, v, w, x, y en z en controleer de leesbaarheid.", "Exerce e, f, s, k, v, w, x, y et z et vérifie la lisibilité.", "Practise e, f, s, k, v, w, x, y and z and check legibility."),
        WritingLessonSpec("hoofdletters_a_m", "Hoofdletters A tot M", "Majuscules A à M", "Capital letters A to M", 20, listOf("goal.g1.writing.capitals_words_sentences"), "Oefen A tot en met M op de juiste hoogte en met duidelijke verschillen tegenover kleine letters.", "Exerce A à M à la bonne hauteur et distingue-les clairement des minuscules.", "Practise A through M at the correct height and distinguish them clearly from lowercase letters."),
        WritingLessonSpec("hoofdletters_n_z", "Hoofdletters N tot Z", "Majuscules N à Z", "Capital letters N to Z", 20, listOf("goal.g1.writing.capitals_words_sentences"), "Oefen N tot en met Z en gebruik elke hoofdletter bij een naam of aan het begin van een zin.", "Exerce N à Z et utilise chaque majuscule pour un nom ou au début d'une phrase.", "Practise N through Z and use each capital for a name or at the start of a sentence."),
        WritingLessonSpec("alle_letters", "Alle letters oefenen", "Exercer toutes les lettres", "Practise all letters", 20, listOf("goal.g1.writing.lowercase_letters", "goal.g1.writing.capitals_words_sentences"), "Oefen alle kleine letters en hoofdletters en let op startplaats, schrijfrichting en leesbaarheid.", "Exerce toutes les minuscules et majuscules en respectant le point de départ, le sens du tracé et la lisibilité.", "Practise every lowercase and capital letter, checking starting point, stroke direction and legibility."),
        WritingLessonSpec("woorden_zinnen", "Woorden en korte zinnen schrijven", "Écrire des mots et de courtes phrases", "Write words and short sentences", 20, listOf("goal.g1.writing.capitals_words_sentences"), "Schrijf klankzuivere woorden en korte zinnen met spaties, een hoofdletter en een punt.", "Écris des mots réguliers et de courtes phrases avec des espaces, une majuscule et un point.", "Write regular words and short sentences with spaces, a capital letter and a full stop.")
    )

    private data class WritingLessonSpec(
        val slug: String,
        val nl: String,
        val fr: String,
        val en: String,
        val minutes: Int,
        val goalIds: List<String>,
        val instructionsNl: String,
        val instructionsFr: String,
        val instructionsEn: String
    )
}
