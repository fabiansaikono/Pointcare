package be.saikono.poincare.domain.curriculum.generation

import be.saikono.poincare.model.ExerciseContent
import be.saikono.poincare.model.ExerciseSection
import be.saikono.poincare.model.InputMode
import be.saikono.poincare.model.LearningDomain
import be.saikono.poincare.model.LocalizedText
import be.saikono.poincare.model.SkillLevel

/**
 * Deterministic runtime for the complete Grade 1 curriculum catalogue.
 *
 * The catalogue supplies an editorially reviewed knowledge card per unit. The
 * generator only varies question form and answer position; it never invents
 * facts, vocabulary, examples or models at runtime.
 */
object Grade1CurriculumCatalogGenerator : LessonExerciseGenerator {
    override val generatorId: String = "generator.g1.curriculum.catalog"
    override val generatorVersion: Int = 1

    private val allowedParameterKeys = setOf(
        "grade", "unit_id", "domain", "skill", "phase", "editorial_status",
        "title_nl", "title_fr", "title_en",
        "focus_nl", "focus_fr", "focus_en",
        "target_nl", "target_fr", "target_en",
        "how_nl", "how_fr", "how_en",
        "check_nl", "check_fr", "check_en",
        "example_nl", "example_fr", "example_en",
        "non_example_nl", "non_example_fr", "non_example_en",
        "terms_nl", "terms_fr", "terms_en",
        "models_nl", "models_fr", "models_en",
        "distractor_focus_nl", "distractor_focus_fr", "distractor_focus_en",
        "distractor_target_nl", "distractor_target_fr", "distractor_target_en",
        "distractor_terms_nl", "distractor_terms_fr", "distractor_terms_en",
        "distractor_models_nl", "distractor_models_fr", "distractor_models_en",
        "question_pool_nl"
    )

    override fun validate(parameters: Map<String, String>): List<String> = buildList {
        val unknown = parameters.keys - allowedParameterKeys
        if (unknown.isNotEmpty()) add("unsupported parameters: ${unknown.sorted().joinToString()}")
        if (parameters["grade"] != "1") add("grade must be 1")
        if (parameters["phase"] !in setOf("guided", "independent", "test")) {
            add("phase must be guided, independent or test")
        }
        if (parameters["editorial_status"] != LessonGeneratorRegistry.APPROVED_STATUS) {
            add("editorial_status must be approved")
        }
        listOf("unit_id", "domain", "skill", "title_nl", "focus_nl", "target_nl").forEach { key ->
            if (parameters[key].isNullOrBlank()) add("$key is required")
        }
        if (runCatching { LearningDomain.valueOf(parameters["domain"].orEmpty()) }.isFailure) {
            add("domain must be a supported LearningDomain")
        }
    }

    override fun generate(
        seed: LessonGenerationSeed,
        exerciseCount: Int,
        parameters: Map<String, String>
    ): List<ExerciseContent> {
        val issues = validate(parameters)
        require(issues.isEmpty()) { issues.joinToString("; ") }
        require(exerciseCount > 0) { "exerciseCount must be positive" }

        val card = KnowledgeCard.from(parameters)
        val candidates = questionPool(parameters["question_pool_nl"]).ifEmpty { card.candidates() }
        require(candidates.isNotEmpty()) { "The knowledge card has no usable candidates." }
        val random = StableLessonRandom(seed.value)
        val offset = random.nextInt(candidates.size)
        return List(exerciseCount) { index ->
            val candidate = candidates[(index + offset) % candidates.size]
            exercise(seed, index, card, candidate, random)
        }
    }

    private fun exercise(
        seed: LessonGenerationSeed,
        index: Int,
        card: KnowledgeCard,
        candidate: Candidate,
        random: StableLessonRandom
    ): ExerciseContent {
        val options = candidate.options.toMutableList()
        for (position in options.lastIndex downTo 1) {
            val other = random.nextInt(position + 1)
            val value = options[position]
            options[position] = options[other]
            options[other] = value
        }
        val answer = options.single { it.correct }
        return ExerciseContent(
            id = "generated.g1.catalog.${card.unitKey}.${seed.fingerprint}.${index + 1}",
            domain = card.domain,
            skill = card.skill,
            section = if (card.phase == "test") ExerciseSection.TESTS else ExerciseSection.PRACTICE,
            level = SkillLevel.FIRST_GRADE,
            inputMode = InputMode.TAP_CHOICE,
            prompt = candidate.prompt,
            speechText = candidate.prompt,
            data = linkedMapOf(
                "generatorId" to generatorId,
                "generatorVersion" to generatorVersion.toString(),
                "seedFingerprint" to seed.fingerprint,
                "unitId" to card.unitId,
                "phase" to card.phase,
                "promptKind" to candidate.kind,
                "contentKey" to "${candidate.kind}.${candidate.contentIndex}",
                "options_nl-BE" to options.joinToString("\n") { it.text.nl },
                "options_fr-BE" to options.joinToString("\n") { it.text.fr.orEmpty() },
                "options_en-US" to options.joinToString("\n") { it.text.en.orEmpty() },
                "optionSpeech_nl-BE" to options.joinToString("\n") { it.text.nl },
                "optionSpeech_fr-BE" to options.joinToString("\n") { it.text.fr.orEmpty() },
                "optionSpeech_en-US" to options.joinToString("\n") { it.text.en.orEmpty() },
                "answer_nl-BE" to answer.text.nl,
                "answer_fr-BE" to answer.text.fr.orEmpty(),
                "answer_en-US" to answer.text.en.orEmpty(),
                "hint_nl-BE" to card.check.nl,
                "hint_fr-BE" to card.check.fr.orEmpty(),
                "hint_en-US" to card.check.en.orEmpty(),
                "automatedScope" to "knowledge_strategy_recognition"
            ),
            answer = answer.text.nl
        )
    }

    private data class KnowledgeCard(
        val unitId: String,
        val unitKey: String,
        val domain: LearningDomain,
        val skill: String,
        val phase: String,
        val title: LocalizedText,
        val focus: LocalizedText,
        val target: LocalizedText,
        val how: LocalizedText,
        val check: LocalizedText,
        val example: LocalizedText,
        val nonExample: LocalizedText,
        val terms: List<LocalizedText>,
        val models: List<LocalizedText>,
        val distractorFocus: List<LocalizedText>,
        val distractorTarget: List<LocalizedText>,
        val distractorTerms: List<LocalizedText>,
        val distractorModels: List<LocalizedText>
    ) {
        fun candidates(): List<Candidate> = buildList {
            add(choiceCandidate("core_idea", 0, prompt(
                "Welke uitleg hoort bij ${title.nl}?",
                "Quelle explication correspond à ${title.fr}?",
                "Which explanation belongs with ${title.en}?"
            ), focus, distractorFocus))
            add(choiceCandidate("target_skill", 0, prompt(
                "Wat oefen je bij ${title.nl}?",
                "Que vas-tu exercer dans ${title.fr}?",
                "What do you practise in ${title.en}?"
            ), target, distractorTarget))
            add(choiceCandidate("method", 0, prompt(
                "Welke aanpak helpt bij ${title.nl}?",
                "Quelle méthode aide pour ${title.fr}?",
                "Which method helps with ${title.en}?"
            ), how, distractorTarget))
            add(choiceCandidate("check", 0, prompt(
                "Hoe controleer je je werk bij ${title.nl}?",
                "Comment vérifies-tu ton travail pour ${title.fr}?",
                "How do you check your work for ${title.en}?"
            ), check, distractorFocus))
            add(choiceCandidate("example", 0, prompt(
                "Welk voorbeeld past bij ${title.nl}?",
                "Quel exemple correspond à ${title.fr}?",
                "Which example fits ${title.en}?"
            ), example, listOf(nonExample) + distractorFocus))
            terms.take(4).forEachIndexed { index, term ->
                add(choiceCandidate("vocabulary", index, prompt(
                    "Welk kernwoord hoort bij ${title.nl}?",
                    "Quel mot-clé appartient à ${title.fr}?",
                    "Which key word belongs with ${title.en}?"
                ), term, distractorTerms))
            }
            models.take(3).forEachIndexed { index, model ->
                add(choiceCandidate("model", index, prompt(
                    "Welk model of hulpmiddel past bij ${title.nl}?",
                    "Quel modèle ou outil convient à ${title.fr}?",
                    "Which model or tool fits ${title.en}?"
                ), model, distractorModels))
            }
        }

        private fun choiceCandidate(
            kind: String,
            index: Int,
            prompt: LocalizedText,
            answer: LocalizedText,
            distractors: List<LocalizedText>
        ): Candidate {
            val alternatives = distractors.filterNot { it.nl == answer.nl }.distinctBy { it.nl }.take(2)
            val padded = alternatives + listOf(
                localized("Alleen gokken zonder te controleren.", "Deviner sans vérifier.", "Only guess without checking."),
                localized("Een willekeurige stap die niet bij de les hoort.", "Une étape au hasard sans rapport avec la leçon.", "A random step unrelated to the lesson.")
            )
            return Candidate(kind, index, prompt, listOf(Option(answer, true)) + padded.distinctBy { it.nl }.take(2).map { Option(it, false) })
        }

        companion object {
            fun from(parameters: Map<String, String>): KnowledgeCard {
                fun text(prefix: String) = localized(
                    parameters["${prefix}_nl"].orEmpty(),
                    parameters["${prefix}_fr"].orEmpty().ifBlank { parameters["${prefix}_nl"].orEmpty() },
                    parameters["${prefix}_en"].orEmpty().ifBlank { parameters["${prefix}_nl"].orEmpty() }
                )
                fun list(prefix: String): List<LocalizedText> {
                    val nl = split(parameters["${prefix}_nl"])
                    val fr = split(parameters["${prefix}_fr"])
                    val en = split(parameters["${prefix}_en"])
                    return nl.mapIndexed { index, value ->
                        localized(value, fr.getOrNull(index) ?: value, en.getOrNull(index) ?: value)
                    }
                }
                val unitId = parameters.getValue("unit_id")
                return KnowledgeCard(
                    unitId = unitId,
                    unitKey = unitId.substringAfter("unit.g1.").replace('.', '_').replace('-', '_'),
                    domain = LearningDomain.valueOf(parameters.getValue("domain")),
                    skill = parameters.getValue("skill"),
                    phase = parameters.getValue("phase"),
                    title = text("title"),
                    focus = text("focus"),
                    target = text("target"),
                    how = text("how"),
                    check = text("check"),
                    example = text("example"),
                    nonExample = text("non_example"),
                    terms = list("terms"),
                    models = list("models"),
                    distractorFocus = list("distractor_focus"),
                    distractorTarget = list("distractor_target"),
                    distractorTerms = list("distractor_terms"),
                    distractorModels = list("distractor_models")
                )
            }

            private fun split(value: String?): List<String> = value.orEmpty().split('|').map(String::trim).filter(String::isNotBlank)
        }
    }

    private data class Candidate(
        val kind: String,
        val contentIndex: Int,
        val prompt: LocalizedText,
        val options: List<Option>
    )

    private data class Option(val text: LocalizedText, val correct: Boolean)

    private fun questionPool(value: String?): List<Candidate> = value.orEmpty()
        .split('\u001e')
        .mapIndexedNotNull { index, record ->
            val fields = record.split('\u001f')
            if (fields.size < 6 || fields.take(6).any(String::isBlank)) return@mapIndexedNotNull null
            Candidate(
                kind = "reviewed_pool",
                contentIndex = index,
                prompt = LocalizedText(fields[0]),
                options = listOf(Option(LocalizedText(fields[1]), true)) +
                    fields.subList(2, 5).map { Option(LocalizedText(it), false) }
            )
        }

    private fun prompt(nl: String, fr: String, en: String) = localized(nl, fr, en)

    private fun localized(nl: String, fr: String, en: String) = LocalizedText(nl = nl, fr = fr, en = en)
}
