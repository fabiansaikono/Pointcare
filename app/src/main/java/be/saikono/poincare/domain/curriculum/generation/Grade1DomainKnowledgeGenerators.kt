package be.saikono.poincare.domain.curriculum.generation

import be.saikono.poincare.model.ExerciseContent
import be.saikono.poincare.model.ExerciseSection
import be.saikono.poincare.model.InputMode
import be.saikono.poincare.model.LearningDomain
import be.saikono.poincare.model.LocalizedText
import be.saikono.poincare.model.SkillLevel

/**
 * Domain-aware generators for the remaining Grade 1 curriculum units.
 *
 * The lesson catalogue remains the editorial source of truth. These generators
 * turn each reviewed lesson card into deterministic, localized question forms
 * that fit the domain. They assess recognition on screen only; practical or
 * spoken performance is explicitly left to adult observation where applicable.
 */
abstract class Grade1DomainKnowledgeGenerator(
    final override val generatorId: String,
    private val supportedDomains: Set<LearningDomain>,
    private val inputMode: InputMode,
    private val automatedScope: String
) : LessonExerciseGenerator {
    final override val generatorVersion: Int = 1

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

    protected abstract fun supportsUnit(unitId: String): Boolean
    protected abstract fun prompt(kind: PromptKind, title: LocalizedText): LocalizedText
    protected abstract fun subjectArea(unitId: String): String
    protected open fun observationMetadata(unitId: String): Pair<String, String>? = null

    final override fun validate(parameters: Map<String, String>): List<String> = buildList {
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
        val domain = runCatching { LearningDomain.valueOf(parameters["domain"].orEmpty()) }.getOrNull()
        if (domain == null || domain !in supportedDomains) add("domain is not supported by $generatorId")
        parameters["unit_id"]?.takeIf(String::isNotBlank)?.let { unitId ->
            if (!supportsUnit(unitId)) add("unit_id is not supported by $generatorId")
        }
    }

    final override fun generate(
        seed: LessonGenerationSeed,
        exerciseCount: Int,
        parameters: Map<String, String>
    ): List<ExerciseContent> {
        val issues = validate(parameters)
        require(issues.isEmpty()) { issues.joinToString("; ") }
        require(exerciseCount > 0) { "exerciseCount must be positive" }

        val card = LessonCard.from(parameters)
        val profile = Grade1UnitKnowledgeProfiles.require(card.unitId)
        val candidates = questionPool(parameters["question_pool_nl"]).ifEmpty { candidates(profile) }
        val random = StableLessonRandom(seed.value)
        val offset = random.nextInt(candidates.size)
        return List(exerciseCount) { index ->
            val candidate = candidates[(index + offset) % candidates.size]
            exercise(seed, index, card, profile, candidate, random)
        }
    }

    private fun candidates(profile: Grade1UnitKnowledgeProfile): List<Candidate> =
        profile.facts.flatMapIndexed { factIndex, fact ->
            PromptKind.values().map { kind ->
                Candidate(
                    key = "${kind.key}.fact.${factIndex + 1}",
                    kind = kind,
                    prompt = prompt(kind, profile.title),
                    answer = fact,
                    distractors = Grade1UnitKnowledgeProfiles.distractors(profile.unitId, factIndex)
                )
            }
        }

    private fun exercise(
        seed: LessonGenerationSeed,
        index: Int,
        card: LessonCard,
        profile: Grade1UnitKnowledgeProfile,
        candidate: Candidate,
        random: StableLessonRandom
    ): ExerciseContent {
        val options = (listOf(Option(candidate.answer, true)) +
            candidate.distractors.map { Option(it, false) }).toMutableList()
        for (position in options.lastIndex downTo 1) {
            val other = random.nextInt(position + 1)
            val value = options[position]
            options[position] = options[other]
            options[other] = value
        }
        val answer = options.single { it.correct }.text
        val metadata = linkedMapOf(
            "generatorId" to generatorId,
            "generatorVersion" to generatorVersion.toString(),
            "seedFingerprint" to seed.fingerprint,
            "unitId" to card.unitId,
            "phase" to card.phase,
            "subjectArea" to subjectArea(card.unitId),
            "promptKind" to candidate.kind.key,
            "questionIntent" to "UNIT_KNOWLEDGE_${candidate.kind.key.uppercase()}",
            "contentKey" to candidate.key,
            "automatedScope" to automatedScope,
            "options_nl-BE" to options.joinToString("\n") { it.text.nl },
            "options_fr-BE" to options.joinToString("\n") { it.text.fr.orEmpty() },
            "options_en-US" to options.joinToString("\n") { it.text.en.orEmpty() },
            "optionSpeech_nl-BE" to options.joinToString("\n") { it.text.nl },
            "optionSpeech_fr-BE" to options.joinToString("\n") { it.text.fr.orEmpty() },
            "optionSpeech_en-US" to options.joinToString("\n") { it.text.en.orEmpty() },
            "answer_nl-BE" to answer.nl,
            "answer_fr-BE" to answer.fr.orEmpty(),
            "answer_en-US" to answer.en.orEmpty(),
            "hint_nl-BE" to profile.warning.nl,
            "hint_fr-BE" to profile.warning.fr.orEmpty(),
            "hint_en-US" to profile.warning.en.orEmpty()
        )
        observationMetadata(card.unitId)?.let { (key, value) -> metadata[key] = value }
        return ExerciseContent(
            id = "generated.g1.${subjectArea(card.unitId)}.${card.unitKey}.v1.${seed.fingerprint}.${index + 1}",
            domain = card.domain,
            skill = card.skill,
            section = if (card.phase == "test") ExerciseSection.TESTS else ExerciseSection.PRACTICE,
            level = SkillLevel.FIRST_GRADE,
            inputMode = inputMode,
            prompt = candidate.prompt,
            speechText = candidate.prompt,
            data = metadata,
            answer = answer.nl
        )
    }

    protected enum class PromptKind(val key: String) {
        CORE_IDEA("core_idea"),
        TARGET_SKILL("target_skill"),
        METHOD("method"),
        CHECK("check"),
        EXAMPLE("example"),
        NON_EXAMPLE("non_example"),
        WORD_ONE("vocabulary_1"),
        WORD_TWO("vocabulary_2"),
        MODEL_ONE("model_1"),
        MODEL_TWO("model_2")
    }

    private data class Candidate(
        val key: String,
        val kind: PromptKind,
        val prompt: LocalizedText,
        val answer: LocalizedText,
        val distractors: List<LocalizedText>
    )

    private data class Option(val text: LocalizedText, val correct: Boolean)

    private fun questionPool(value: String?): List<Candidate> = value.orEmpty()
        .split('\u001e')
        .mapIndexedNotNull { index, record ->
            val fields = record.split('\u001f')
            if (fields.size < 6 || fields.take(6).any(String::isBlank)) return@mapIndexedNotNull null
            Candidate(
                key = "reviewed_pool.${index + 1}",
                kind = PromptKind.EXAMPLE,
                prompt = LocalizedText(fields[0]),
                answer = LocalizedText(fields[1]),
                distractors = fields.subList(2, 5).map { LocalizedText(it) }
            )
        }

    private data class LessonCard(
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
        companion object {
            fun from(parameters: Map<String, String>): LessonCard {
                fun text(prefix: String): LocalizedText = localized(
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
                return LessonCard(
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

            private fun split(value: String?): List<String> = value.orEmpty()
                .split('|')
                .map(String::trim)
                .filter(String::isNotBlank)
        }
    }

    companion object {
        fun localized(nl: String, fr: String, en: String): LocalizedText = LocalizedText(nl, fr, en)
    }
}

object Grade1MathConceptsGenerator : Grade1DomainKnowledgeGenerator(
    generatorId = "generator.g1.math.concepts",
    supportedDomains = setOf(LearningDomain.MATH, LearningDomain.GEOMETRY),
    inputMode = InputMode.TAP_CHOICE,
    automatedScope = "mathematical_concept_strategy_choice"
) {
    override fun supportsUnit(unitId: String): Boolean =
        unitId.startsWith("unit.g1.math.fractions.") ||
            unitId.startsWith("unit.g1.math.geometry.orientatie-") ||
            unitId.startsWith("unit.g1.math.data.")

    override fun subjectArea(unitId: String): String = "math_concepts"

    override fun prompt(kind: PromptKind, title: LocalizedText): LocalizedText = when (kind) {
        PromptKind.CORE_IDEA -> q("Welke wiskundige uitleg hoort bij ${title.nl}?", "Quelle explication mathématique correspond à ${title.fr} ?", "Which mathematical explanation belongs with ${title.en}?")
        PromptKind.TARGET_SKILL -> q("Wat moet je kunnen bij ${title.nl}?", "Que dois-tu savoir faire pour ${title.fr} ?", "What should you be able to do in ${title.en}?")
        PromptKind.METHOD -> q("Welke aanpak helpt bij ${title.nl}?", "Quelle méthode aide pour ${title.fr} ?", "Which method helps with ${title.en}?")
        PromptKind.CHECK -> q("Hoe controleer je dit wiskundewerk?", "Comment vérifies-tu ce travail mathématique ?", "How do you check this mathematics work?")
        PromptKind.EXAMPLE -> q("Welk voorbeeld is wiskundig juist?", "Quel exemple est mathématiquement correct ?", "Which example is mathematically correct?")
        PromptKind.NON_EXAMPLE -> q("Welke keuze toont een veelgemaakte fout?", "Quel choix montre une erreur fréquente ?", "Which choice shows a common mistake?")
        PromptKind.WORD_ONE, PromptKind.WORD_TWO -> q("Welk kernwoord heb je hier nodig?", "De quel mot-clé as-tu besoin ici ?", "Which key word do you need here?")
        PromptKind.MODEL_ONE, PromptKind.MODEL_TWO -> q("Welk model of hulpmiddel past hierbij?", "Quel modèle ou outil convient ici ?", "Which model or tool fits here?")
    }

    private fun q(nl: String, fr: String, en: String) = localized(nl, fr, en)
}

object Grade1FrenchBasicsGenerator : Grade1DomainKnowledgeGenerator(
    generatorId = "generator.g1.language.french_basics",
    supportedDomains = setOf(LearningDomain.READING),
    inputMode = InputMode.LISTEN_AND_SELECT,
    automatedScope = "french_word_phrase_receptive_recognition"
) {
    override fun supportsUnit(unitId: String): Boolean = unitId.startsWith("unit.g1.fr.")
    override fun subjectArea(unitId: String): String = "french_basics"
    override fun observationMetadata(unitId: String): Pair<String, String> =
        "requiresAdultPronunciationParticipationObservation" to "true"

    override fun prompt(kind: PromptKind, title: LocalizedText): LocalizedText = when (kind) {
        PromptKind.CORE_IDEA -> q("Luister. Welke uitleg past bij ${title.nl}?", "Écoute. Quelle explication correspond à ${title.fr} ?", "Listen. Which explanation belongs with ${title.en}?")
        PromptKind.TARGET_SKILL -> q("Wat oefen je met deze Franse woorden?", "Que pratiques-tu avec ces mots français ?", "What do you practise with these French words?")
        PromptKind.METHOD -> q("Welke luister- en nazegaanpak helpt?", "Quelle méthode d'écoute et de répétition aide ?", "Which listen-and-repeat method helps?")
        PromptKind.CHECK -> q("Hoe controleer je of je het Franse woord herkent?", "Comment vérifies-tu que tu reconnais le mot français ?", "How do you check that you recognize the French word?")
        PromptKind.EXAMPLE -> q("Welk voorbeeld past bij deze Franse les?", "Quel exemple correspond à cette leçon de français ?", "Which example fits this French lesson?")
        PromptKind.NON_EXAMPLE -> q("Welke keuze helpt niet bij luisteren en nazeggen?", "Quel choix n'aide pas à écouter et répéter ?", "Which choice does not help with listening and repeating?")
        PromptKind.WORD_ONE, PromptKind.WORD_TWO -> q("Welk Frans kernwoord hoor je bij deze les?", "Quel mot-clé français entends-tu dans cette leçon ?", "Which French key word belongs in this lesson?")
        PromptKind.MODEL_ONE, PromptKind.MODEL_TWO -> q("Welke taalsteun helpt je luisteren en meedoen?", "Quel support linguistique t'aide à écouter et participer ?", "Which language support helps you listen and join in?")
    }

    private fun q(nl: String, fr: String, en: String) = localized(nl, fr, en)
}

object Grade1AppliedDomainsGenerator : Grade1DomainKnowledgeGenerator(
    generatorId = "generator.g1.applied_domains",
    supportedDomains = setOf(LearningDomain.WORLD_KNOWLEDGE),
    inputMode = InputMode.TAP_CHOICE,
    automatedScope = "concept_safety_sequence_recognition"
) {
    private val prefixes = listOf("geo", "history", "ict", "arts", "pe")

    override fun supportsUnit(unitId: String): Boolean = prefixes.any { unitId.startsWith("unit.g1.$it.") }

    override fun subjectArea(unitId: String): String = prefixes.first { unitId.startsWith("unit.g1.$it.") }

    override fun observationMetadata(unitId: String): Pair<String, String> = when (subjectArea(unitId)) {
        "arts", "pe" -> "requiresAdultPerformanceObservation" to "true"
        "ict" -> "requiresAdultDeviceUseObservation" to "true"
        else -> "requiresAdultObservationConversation" to "true"
    }

    override fun prompt(kind: PromptKind, title: LocalizedText): LocalizedText = when (kind) {
        PromptKind.CORE_IDEA -> q("Welke uitleg hoort bij ${title.nl}?", "Quelle explication correspond à ${title.fr} ?", "Which explanation belongs with ${title.en}?")
        PromptKind.TARGET_SKILL -> q("Wat leer je doen bij ${title.nl}?", "Qu'apprends-tu à faire dans ${title.fr} ?", "What do you learn to do in ${title.en}?")
        PromptKind.METHOD -> q("Welke veilige aanpak past bij deze les?", "Quelle méthode sûre convient à cette leçon ?", "Which safe approach fits this lesson?")
        PromptKind.CHECK -> q("Hoe controleer je of je aanpak klopt?", "Comment vérifies-tu que ta méthode est correcte ?", "How do you check that your approach is correct?")
        PromptKind.EXAMPLE -> q("Welk voorbeeld past het best bij ${title.nl}?", "Quel exemple correspond le mieux à ${title.fr} ?", "Which example best fits ${title.en}?")
        PromptKind.NON_EXAMPLE -> q("Welke keuze past niet bij een goede uitvoering?", "Quel choix ne correspond pas à une bonne réalisation ?", "Which choice does not fit a good performance?")
        PromptKind.WORD_ONE, PromptKind.WORD_TWO -> q("Welk kernwoord hoort bij deze situatie?", "Quel mot-clé correspond à cette situation ?", "Which key word belongs with this situation?")
        PromptKind.MODEL_ONE, PromptKind.MODEL_TWO -> q("Welk model, signaal of hulpmiddel helpt hierbij?", "Quel modèle, signal ou outil aide ici ?", "Which model, signal, or tool helps here?")
    }

    private fun q(nl: String, fr: String, en: String) = localized(nl, fr, en)
}
