package be.saikono.poincare.domain.curriculum.generation

import be.saikono.poincare.model.ExerciseContent
import be.saikono.poincare.model.ExerciseSection
import be.saikono.poincare.model.InputMode
import be.saikono.poincare.model.LearningDomain
import be.saikono.poincare.model.LocalizedText
import be.saikono.poincare.model.SkillLevel

/**
 * Parameter-driven Grade 2 runtime generator for the atomic V2 catalogue.
 *
 * The content-bearing parameters are stored on every exercise set. This keeps
 * one reviewed implementation while preventing a fixed cross-lesson question
 * bank: prompts, choices and mastery identity always come from the owning
 * atomic lesson.
 */
object Grade2AtomicCurriculumGenerator : LessonExerciseGenerator {
    override val generatorId: String = "generator.g2.atomic-curriculum"
    override val generatorVersion: Int = 1

    private val allowedKeys = setOf(
        "grade", "roadmap_entry", "phase", "editorial_status", "atom_key", "mode",
        "title_nl", "knowledge_nl", "skill_nl", "vocabulary_nl", "models_nl",
        "distractors_nl", "exclude_recent_exact_items", "question_pool_nl"
    )

    override fun validate(parameters: Map<String, String>): List<String> = buildList {
        val unknown = parameters.keys - allowedKeys
        if (unknown.isNotEmpty()) add("unsupported parameters: ${unknown.sorted().joinToString()}")
        if (parameters["grade"] != "2") add("grade must be 2")
        if (parameters["roadmap_entry"].isNullOrBlank()) add("roadmap_entry is required")
        if (parameters["phase"] !in setOf("guided", "checkpoint")) add("phase must be guided or checkpoint")
        if (parameters["editorial_status"] != LessonGeneratorRegistry.APPROVED_STATUS) {
            add("editorial_status must be approved")
        }
        listOf("atom_key", "mode", "title_nl", "knowledge_nl", "skill_nl").forEach { key ->
            if (parameters[key].isNullOrBlank()) add("$key is required")
        }
        if (split(parameters["vocabulary_nl"]).isEmpty()) add("vocabulary_nl needs at least one lesson-bound term")
        if (split(parameters["models_nl"]).isEmpty()) add("models_nl needs at least one lesson-bound model")
        if (split(parameters["distractors_nl"]).size < 2) add("distractors_nl needs at least two reviewed distractors")
    }

    override fun generate(
        seed: LessonGenerationSeed,
        exerciseCount: Int,
        parameters: Map<String, String>
    ): List<ExerciseContent> {
        val issues = validate(parameters)
        require(issues.isEmpty()) { issues.joinToString("; ") }
        require(exerciseCount > 0) { "exerciseCount must be positive" }
        val random = StableLessonRandom(seed.value)
        return List(exerciseCount) { index ->
            val candidate = candidate(parameters, index, random)
            exercise(seed, parameters, index, candidate, random)
        }
    }

    private fun candidate(
        parameters: Map<String, String>,
        index: Int,
        random: StableLessonRandom
    ): Candidate {
        val mode = parameters.getValue("mode")
        return questionPool(parameters["question_pool_nl"])
            .let { pool -> pool.getOrNull(index % pool.size.coerceAtLeast(1)) }
            ?: mathCandidate(mode, index, random)
            ?: conceptCandidate(parameters, index)
    }

    private fun mathCandidate(mode: String, index: Int, random: StableLessonRandom): Candidate? {
        fun numeric(prompt: String, answer: Int): Candidate = Candidate(
            prompt,
            answer.toString(),
            listOf(answer, answer + 1, (answer - 1).coerceAtLeast(0), answer + 10).map(Int::toString),
            "Bereken stap voor stap en controleer of de uitkomst redelijk is."
        )
        val n = index + 1
        return when (mode) {
            "math_place_value" -> {
                val tens = random.nextInt(2, 10)
                val ones = random.nextInt(0, 10)
                numeric("Welk getal bestaat uit $tens tientallen en $ones eenheden?", tens * 10 + ones)
            }
            "math_digit_value" -> {
                val tens = random.nextInt(2, 10)
                val ones = random.nextInt(0, 10)
                Candidate(
                    "Wat is de waarde van het cijfer $tens in $tens$ones?",
                    (tens * 10).toString(),
                    listOf(tens * 10, tens, ones, tens * 10 + ones).distinct().map(Int::toString),
                    "Een cijfer op de tientallenplaats is tien keer zoveel waard."
                )
            }
            "math_skip_count", "math_number_pattern" -> {
                val step = if (mode == "math_skip_count") listOf(2, 5, 10)[n % 3] else random.nextInt(2, 7)
                val start = random.nextInt(2, 20)
                numeric("Vul de rij aan: $start, ${start + step}, ${start + 2 * step}, …", start + 3 * step)
            }
            "math_add_no_bridge" -> {
                val left = random.nextInt(2, 9) * 10 + random.nextInt(0, 4)
                val right = random.nextInt(2, 7)
                numeric("Bereken $left + $right.", left + right)
            }
            "math_sub_no_bridge" -> {
                val left = random.nextInt(3, 10) * 10 + random.nextInt(6, 10)
                val right = random.nextInt(2, 7)
                numeric("Bereken $left - $right.", left - right)
            }
            "math_add_bridge" -> {
                val digit = random.nextInt(2, 9)
                val left = random.nextInt(2, 8) * 10 + digit
                val gap = 10 - digit
                val right = gap + random.nextInt(1, digit.coerceAtLeast(2))
                numeric("Bereken $left + $right via het volgende tiental.", left + right)
            }
            "math_sub_bridge" -> {
                val digit = random.nextInt(1, 9)
                val left = random.nextInt(3, 10) * 10 + digit
                val right = digit + random.nextInt(1, (10 - digit).coerceAtLeast(2))
                numeric("Bereken $left - $right via het vorige tiental.", left - right)
            }
            "math_facts20" -> {
                val left = random.nextInt(2, 11)
                val right = random.nextInt(2, 21 - left)
                numeric("Bereken $left + $right.", left + right)
            }
            "math_multiply" -> {
                val groups = random.nextInt(2, 8)
                val per = random.nextInt(2, 6)
                numeric("Er zijn $groups gelijke groepjes van $per. Hoeveel zijn er samen?", groups * per)
            }
            "math_divide_share", "math_divide_group" -> {
                val groups = random.nextInt(2, 7)
                val per = random.nextInt(2, 6)
                val total = groups * per
                if (mode == "math_divide_share") numeric("Verdeel $total fiches eerlijk over $groups kinderen. Hoeveel krijgt elk kind?", per)
                else numeric("Hoeveel groepjes van $per passen precies in $total?", groups)
            }
            "math_clock" -> {
                val hour = random.nextInt(1, 11)
                val minutes = listOf(0, 15, 30, 45)[n % 4]
                val answer = "$hour:${minutes.toString().padStart(2, '0')}"
                Candidate(
                    "Welke digitale tijd hoort bij ${timeWords(hour, minutes)}?",
                    answer,
                    listOf(answer, "$hour:10", "$hour:30", "${hour + 1}:${minutes.toString().padStart(2, '0')}").distinct(),
                    "Een kwartier is 15 minuten; een halfuur is 30 minuten."
                )
            }
            "math_duration" -> {
                val start = random.nextInt(8, 17)
                val duration = listOf(15, 30, 45, 60)[n % 4]
                val endMinutes = start * 60 + duration
                val answer = "${endMinutes / 60}:${(endMinutes % 60).toString().padStart(2, '0')}"
                Candidate(
                    "Een activiteit start om $start:00 en duurt $duration minuten. Wanneer eindigt ze?",
                    answer,
                    listOf(answer, "$start:15", "$start:30", "${start + 2}:00").distinct(),
                    "Tel de duur op bij de begintijd."
                )
            }
            "math_money_total" -> {
                val first = random.nextInt(2, 25)
                val second = random.nextInt(1, 16)
                Candidate(
                    "Je koopt iets van € $first en iets van € $second. Hoeveel betaal je samen?",
                    "€ ${first + second}",
                    listOf("€ ${first + second}", "€ ${first + second + 1}", "€ ${kotlin.math.abs(first - second)}", "€ ${first * second}").distinct(),
                    "Tel beide prijzen op."
                )
            }
            "math_money_change" -> {
                val price = random.nextInt(5, 20)
                val paid = if (price <= 10) 20 else 50
                Candidate(
                    "Je betaalt € $paid voor een prijs van € $price. Hoeveel wisselgeld krijg je?",
                    "€ ${paid - price}",
                    listOf("€ ${paid - price}", "€ $price", "€ ${paid + price}", "€ ${paid - price - 1}").distinct(),
                    "Trek de prijs af van het betaalde bedrag."
                )
            }
            "math_word_one" -> {
                val total = random.nextInt(12, 35)
                val away = random.nextInt(2, 9)
                numeric("Er zijn $total knikkers. $away worden weggegeven. Hoeveel blijven er?", total - away)
            }
            "math_word_two" -> {
                val total = random.nextInt(20, 45)
                val away = random.nextInt(2, 9)
                val added = random.nextInt(1, 7)
                numeric("Er zijn $total kinderen. $away gaan weg en $added komen erbij. Hoeveel zijn er nu?", total - away + added)
            }
            else -> null
        }
    }

    private fun conceptCandidate(parameters: Map<String, String>, index: Int): Candidate {
        val title = parameters.getValue("title_nl")
        val knowledge = parameters.getValue("knowledge_nl")
        val skill = parameters.getValue("skill_nl")
        val terms = split(parameters["vocabulary_nl"])
        val models = split(parameters["models_nl"])
        val distractors = split(parameters["distractors_nl"])
        return when (index % 5) {
            0 -> Candidate(
                "Welke uitspraak past bij ${title.lowercase()}?",
                knowledge,
                listOf(knowledge) + distractors.take(3),
                "$knowledge Daarom leer je $skill."
            )
            1 -> Candidate(
                "Wat leer je bij ${title.lowercase()}?",
                skill,
                listOf(skill, "zonder plan een antwoord raden", "alle informatie negeren", "stoppen vóór de controle"),
                "Het leerdoel is: $skill."
            )
            2 -> {
                val term = terms[(index / 5) % terms.size]
                Candidate(
                    "Welk kernwoord hoort bij de les ‘$title’?",
                    term,
                    (listOf(term) + terms.filterNot { it == term } + listOf("toeval", "gok", "versiering")).take(4),
                    "$term is een kernwoord in deze les."
                )
            }
            3 -> {
                val model = models[(index / 5) % models.size]
                Candidate(
                    "Welk model helpt bij ${title.lowercase()}?",
                    model,
                    (listOf(model) + models.filterNot { it == model } + listOf("willekeurige gok", "geen model")).take(4),
                    "Gebruik $model om je aanpak zichtbaar te maken."
                )
            }
            else -> Candidate(
                "Je krijgt een nieuw voorbeeld over ${title.lowercase()}. Welke aanpak past?",
                "Ik gebruik ${models.first()}, voer de stappen uit en controleer mijn resultaat.",
                listOf(
                    "Ik gebruik ${models.first()}, voer de stappen uit en controleer mijn resultaat.",
                    "Ik neem het eerste antwoord zonder bewijs.",
                    "Ik verander het doel van de opdracht.",
                    "Ik sla uitleg en controle over."
                ),
                "Een goede toepassing gebruikt het lesmodel én een controle."
            )
        }
    }

    private fun exercise(
        seed: LessonGenerationSeed,
        parameters: Map<String, String>,
        index: Int,
        candidate: Candidate,
        random: StableLessonRandom
    ): ExerciseContent {
        val options = candidate.options.distinct().toMutableList()
        while (options.size < 4) options += "Onjuiste keuze ${options.size + 1}"
        for (position in options.lastIndex downTo 1) {
            val other = random.nextInt(position + 1)
            val value = options[position]
            options[position] = options[other]
            options[other] = value
        }
        val mode = parameters.getValue("mode")
        val phase = parameters.getValue("phase")
        return ExerciseContent(
            id = "generated.g2.atomic.${parameters.getValue("atom_key")}.v1.${seed.fingerprint}.${index + 1}",
            domain = domain(mode),
            skill = parameters.getValue("atom_key"),
            section = if (phase == "guided") ExerciseSection.PRACTICE else ExerciseSection.TESTS,
            level = SkillLevel.SECOND_GRADE,
            inputMode = InputMode.TAP_CHOICE,
            prompt = LocalizedText(candidate.prompt, candidate.prompt, candidate.prompt),
            speechText = LocalizedText(candidate.prompt, candidate.prompt, candidate.prompt),
            data = linkedMapOf(
                "generatorId" to generatorId,
                "generatorVersion" to generatorVersion.toString(),
                "seedFingerprint" to seed.fingerprint,
                "roadmapEntry" to parameters.getValue("roadmap_entry"),
                "atomicLessonKey" to parameters.getValue("atom_key"),
                "phase" to phase,
                "promptKind" to if (mode.startsWith("math_")) "lesson_bound_calculation" else "lesson_bound_concept",
                "contentKey" to "${parameters.getValue("atom_key")}.${index + 1}",
                "answerRole" to "choice",
                "options_nl-BE" to options.joinToString("\n"),
                "options_fr-BE" to options.joinToString("\n"),
                "options_en-US" to options.joinToString("\n"),
                "optionSpeech_nl-BE" to options.joinToString("\n"),
                "optionSpeech_fr-BE" to options.joinToString("\n"),
                "optionSpeech_en-US" to options.joinToString("\n"),
                "answer_nl-BE" to candidate.answer,
                "answer_fr-BE" to candidate.answer,
                "answer_en-US" to candidate.answer,
                "hint_nl-BE" to candidate.explanation,
                "hint_fr-BE" to candidate.explanation,
                "hint_en-US" to candidate.explanation,
                "masteryTag" to "g2.atomic.${parameters.getValue("atom_key")}"
            ),
            answer = candidate.answer
        )
    }

    private fun domain(mode: String): LearningDomain = when {
        mode.startsWith("math_") && mode in setOf(
            "math_length", "math_mass", "math_capacity", "math_clock", "math_duration",
            "math_calendar", "math_shapes", "math_solids", "math_routes", "math_mirror"
        ) -> LearningDomain.GEOMETRY
        mode.startsWith("math_") -> LearningDomain.MATH
        mode in setOf("spelling", "handwriting", "language_composition") -> LearningDomain.WRITING
        mode.startsWith("language") || mode in setOf("literature", "french") -> LearningDomain.READING
        else -> LearningDomain.WORLD_KNOWLEDGE
    }

    private fun timeWords(hour: Int, minutes: Int): String = when (minutes) {
        0 -> "$hour uur"
        15 -> "kwart over $hour"
        30 -> "half ${hour + 1}"
        else -> "kwart voor ${hour + 1}"
    }

    private data class Candidate(
        val prompt: String,
        val answer: String,
        val options: List<String>,
        val explanation: String
    )

    private fun questionPool(value: String?): List<Candidate> = value.orEmpty()
        .split('\u001e')
        .mapNotNull { record ->
            val fields = record.split('\u001f')
            if (fields.size < 6 || fields.take(6).any(String::isBlank)) return@mapNotNull null
            Candidate(
                prompt = fields[0],
                answer = fields[1],
                options = fields.subList(1, 5),
                explanation = fields[5]
            )
        }

    private fun split(value: String?): List<String> = value.orEmpty()
        .split('|')
        .map(String::trim)
        .filter(String::isNotBlank)
        .distinct()
}
