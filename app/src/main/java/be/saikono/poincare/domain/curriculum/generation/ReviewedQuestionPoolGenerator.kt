package be.saikono.poincare.domain.curriculum.generation

import be.saikono.poincare.model.ExerciseContent
import be.saikono.poincare.model.ExerciseSection
import be.saikono.poincare.model.InputMode
import be.saikono.poincare.model.LearningDomain
import be.saikono.poincare.model.LocalizedText
import be.saikono.poincare.model.SkillLevel

/**
 * Materializes curriculum-reviewed Dutch question pools for legacy generators
 * that do not natively accept question_pool_nl. The catalog remains the source
 * of truth; no new educational content is invented at runtime.
 */
object ReviewedQuestionPoolGenerator : LessonExerciseGenerator {
    override val generatorId: String = "generator.reviewed.question-pool"
    override val generatorVersion: Int = 1

    override fun validate(parameters: Map<String, String>): List<String> = buildList {
        if (parameters["grade"] !in setOf("1", "2")) add("grade must be 1 or 2")
        if (parameters["editorial_status"] != LessonGeneratorRegistry.APPROVED_STATUS) {
            add("editorial_status must be approved")
        }
        if (runCatching { LearningDomain.valueOf(parameters["domain"].orEmpty()) }.isFailure) {
            add("domain must be a supported LearningDomain")
        }
        if (parameters["skill"].isNullOrBlank()) add("skill is required")
        if (parameters["question_pool_nl"].isNullOrBlank()) add("question_pool_nl is required")
        if (parameters["question_pool_nl"].isNullOrBlank().not() && questionPool(parameters["question_pool_nl"]).isEmpty()) {
            add("question_pool_nl must contain at least one complete record")
        }
        if (parameters["pool_input_mode"] !in setOf(null, "TAP_CHOICE", "LISTEN_AND_SELECT")) {
            add("pool_input_mode must be TAP_CHOICE or LISTEN_AND_SELECT")
        }
    }

    override fun generate(
        seed: LessonGenerationSeed,
        exerciseCount: Int,
        parameters: Map<String, String>
    ): List<ExerciseContent> {
        val issues = validate(parameters)
        require(issues.isEmpty()) { issues.joinToString("; ") }
        val candidates = questionPool(parameters.getValue("question_pool_nl"))
        require(exerciseCount <= candidates.size) {
            "question_pool_nl contains ${candidates.size} records for $exerciseCount requested exercises"
        }
        val random = StableLessonRandom(seed.value)
        val offset = random.nextInt(candidates.size)
        val grade = parameters.getValue("grade").toInt()
        val domain = LearningDomain.valueOf(parameters.getValue("domain"))
        val skill = parameters.getValue("skill")
        val inputMode = parameters["pool_input_mode"]
            ?.let { InputMode.valueOf(it) }
            ?: InputMode.TAP_CHOICE
        val isTest = parameters["phase"] == "test"

        return List(exerciseCount) { index ->
            val candidateIndex = (index + offset) % candidates.size
            val candidate = candidates[candidateIndex]
            val options = candidate.options.toMutableList()
            for (position in options.lastIndex downTo 1) {
                val other = random.nextInt(position + 1)
                val value = options[position]
                options[position] = options[other]
                options[other] = value
            }
            ExerciseContent(
                id = "generated.reviewed.pool.g$grade.${seed.fingerprint}.${index + 1}",
                domain = domain,
                skill = skill,
                section = if (isTest) ExerciseSection.TESTS else ExerciseSection.PRACTICE,
                level = if (grade == 1) SkillLevel.FIRST_GRADE else SkillLevel.SECOND_GRADE,
                inputMode = inputMode,
                prompt = LocalizedText(candidate.prompt),
                speechText = LocalizedText(candidate.prompt),
                data = linkedMapOf(
                    "generatorId" to generatorId,
                    "generatorVersion" to generatorVersion.toString(),
                    "seedFingerprint" to seed.fingerprint,
                    "originalGeneratorId" to parameters["original_generator_id"].orEmpty(),
                    "promptKind" to "reviewed_pool",
                    "contentKey" to "reviewed_pool.${candidateIndex + 1}",
                    "options_nl-BE" to options.joinToString("\n"),
                    "optionSpeech_nl-BE" to options.joinToString("\n"),
                    "answer_nl-BE" to candidate.answer,
                    "hint_nl-BE" to candidate.explanation
                ),
                answer = candidate.answer,
                hints = listOf(candidate.explanation).filter(String::isNotBlank)
            )
        }
    }

    private fun questionPool(value: String?): List<Candidate> = value.orEmpty()
        .split('\u001e')
        .mapNotNull { record ->
            val fields = record.split('\u001f')
            if (fields.size < 6 || fields.take(6).any(String::isBlank)) return@mapNotNull null
            sanitizeCandidate(
                Candidate(
                    prompt = fields[0],
                    answer = fields[1],
                    options = fields.subList(1, 5),
                    explanation = fields[5]
                )
            )
        }

    private fun sanitizeCandidate(candidate: Candidate): Candidate {
        val prompt = candidate.prompt
            .replace(NAMED_CHILD_CONTEXT_PREFIX, "")
            .replace(CARD_CONTEXT_PREFIX, "")
            .trim()
        if (FRENCH_GREETING_PROMPT.containsMatchIn(prompt)) {
            return candidate.copy(
                prompt = "Welke informele Franse begroeting gebruik je voor iemand die je goed kent?",
                answer = "salut",
                options = listOf("salut", "au revoir", "merci", "s’il vous plaît"),
                explanation = "Je zegt ‘salut’ tegen iemand die je goed kent."
            )
        }
        val usedChoices = linkedSetOf<String>()
        val options = candidate.options.mapIndexed { index, option ->
            var sanitized = if (EDITORIAL_DISTRACTOR.containsMatchIn(option)) {
                replacementDistractor(prompt, index)
            } else {
                option
            }
            var variant = index
            while (!usedChoices.add(sanitized.trim().lowercase())) {
                sanitized = replacementDistractor(prompt, variant++)
            }
            sanitized
        }
        return candidate.copy(
            prompt = prompt,
            options = options
        )
    }

    private fun replacementDistractor(prompt: String, variant: Int = 0): String {
        val normalized = prompt.lowercase()
        return when {
            "welke concrete stap" in normalized -> "Sla de stappen over en begin willekeurig."
            "bruikbare voorbeeld" in normalized -> "Gebruik geen voorbeeld en kies willekeurig."
            "welke uitleg" in normalized -> "Alle begrippen betekenen precies hetzelfde."
            "welke controle" in normalized -> "Sla de controle over en ga meteen verder."
            "tik het begrip" in normalized -> "geen van deze begrippen"
            else -> listOf(
                "Deze keuze past niet bij de vraag.",
                "Dit is niet het gevraagde antwoord.",
                "Deze oplossing hoort bij een andere vraag.",
                "Hiermee beantwoord je de vraag niet.",
                "Dit voorbeeld toont iets anders.",
                "Deze stap levert geen passend antwoord op."
            )[variant % 6]
        }
    }

    private data class Candidate(
        val prompt: String,
        val answer: String,
        val options: List<String>,
        val explanation: String
    )

    private val NAMED_CHILD_CONTEXT_PREFIX = Regex(
        "^(?!Je\\b)[\\p{L}'’.\\-]+\\s+gebruikt\\b[^.!?]*[.!?]\\s*",
        RegexOption.IGNORE_CASE
    )
    private val CARD_CONTEXT_PREFIX = Regex(
        "^[^.!?]*(?:voorbeeldkaart|stappenstrook|theoriekaart|opdrachtkaart|toetskaart|hertoetskaart|steunkaart|hulpkaart|herhaalkaart)[^.!?]*[.!?]\\s*",
        RegexOption.IGNORE_CASE
    )
    private val EDITORIAL_DISTRACTOR = Regex(
        "voor een andere opdracht dan de vraag",
        RegexOption.IGNORE_CASE
    )
    private val FRENCH_GREETING_PROMPT = Regex(
        "hoe zeg je [‘']hallo[’'] in het frans",
        RegexOption.IGNORE_CASE
    )
}
