package be.saikono.poincare.domain.curriculum.generation

import be.saikono.poincare.model.ExerciseContent
import be.saikono.poincare.model.ExerciseSection
import be.saikono.poincare.model.InputMode
import be.saikono.poincare.model.LearningDomain
import be.saikono.poincare.model.LocalizedText
import be.saikono.poincare.model.SkillLevel

/**
 * Deterministic Grade 2 generator for exact, screen-assessable measurement contexts.
 *
 * Version 1 uses whole-number answers with the existing number pad. Practical
 * estimating and measuring remain parent-supported activities rather than being
 * assigned a misleading automatic score.
 */
object Grade2MeasurementContextGenerator : LessonExerciseGenerator {
    override val generatorId: String = "generator.g2.geometry.measurement_context"
    override val generatorVersion: Int = 1

    private const val ROADMAP_ENTRY = "roadmap.g2.geometry.measurement_context"
    private val allowedParameterKeys = setOf(
        "grade",
        "roadmap_entry",
        "phase",
        "editorial_status"
    )

    override fun validate(parameters: Map<String, String>): List<String> = buildList {
        val unknown = parameters.keys - allowedParameterKeys
        if (unknown.isNotEmpty()) {
            add("unsupported parameters: ${unknown.sorted().joinToString()}")
        }
        if (parameters["grade"] != "2") add("grade must be 2")
        if (parameters["roadmap_entry"] != ROADMAP_ENTRY) {
            add("roadmap_entry must be $ROADMAP_ENTRY")
        }
        if (parameters["phase"] !in setOf("guided", "checkpoint")) {
            add("phase must be guided or checkpoint")
        }
        if (parameters["editorial_status"] != LessonGeneratorRegistry.APPROVED_STATUS) {
            add("editorial_status must be approved")
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

        val phase = Phase.valueOf(parameters.getValue("phase").uppercase())
        val random = StableLessonRandom(seed.value)
        val used = linkedSetOf<String>()
        val offset = if (phase == Phase.GUIDED) 0 else random.nextInt(PromptKind.entries.size)

        return List(exerciseCount) { index ->
            val kind = PromptKind.entries[(index + offset) % PromptKind.entries.size]
            val candidate = uniqueCandidate(kind, phase, random, used)
            exercise(seed, index, phase, candidate)
        }
    }

    private fun uniqueCandidate(
        kind: PromptKind,
        phase: Phase,
        random: StableLessonRandom,
        used: MutableSet<String>
    ): Candidate {
        repeat(128) {
            val candidate = createCandidate(kind, phase, random)
            if (used.add(candidate.signature())) return candidate
        }
        return fallbackCandidates(kind).firstOrNull { used.add(it.signature()) }
            ?: error("Unable to create a unique Grade 2 measurement exercise for ${kind.name}")
    }

    private fun createCandidate(
        kind: PromptKind,
        phase: Phase,
        random: StableLessonRandom
    ): Candidate {
        val guided = phase == Phase.GUIDED
        return when (kind) {
            PromptKind.HALF_HOUR_DURATION -> {
                val hour = random.nextInt(1, 12)
                Candidate(kind, 30, listOf(hour, 0, hour, 30), "min")
            }
            PromptKind.HOUR_DURATION -> {
                val start = random.nextInt(1, 12)
                val duration = if (guided) 1 else random.nextInt(1, 3)
                val end = start + duration
                Candidate(kind, duration * 60, listOf(start, end), "min")
            }
            PromptKind.MONEY_TOTAL -> {
                val maximumPrice = if (guided) 10 else 25
                val first = random.nextInt(1, maximumPrice + 1)
                val second = random.nextInt(1, maximumPrice + 1)
                Candidate(kind, first + second, listOf(first, second), "euro")
            }
            PromptKind.MONEY_CHANGE -> {
                val paid = if (guided) 20 else listOf(20, 50)[random.nextInt(2)]
                val price = random.nextInt(1, paid)
                Candidate(kind, paid - price, listOf(price, paid), "euro")
            }
            PromptKind.METRES_TO_CENTIMETRES -> {
                val metres = random.nextInt(1, if (guided) 3 else 4)
                Candidate(kind, metres * 100, listOf(metres), "cm")
            }
            PromptKind.LENGTH_DIFFERENCE -> {
                val maximum = if (guided) 60 else 100
                val shorter = random.nextInt(5, maximum)
                val longer = random.nextInt(shorter + 1, maximum + 1)
                Candidate(kind, longer - shorter, listOf(longer, shorter), "cm")
            }
            PromptKind.MASS_TOTAL -> {
                val maximum = if (guided) 5 else 10
                val first = random.nextInt(1, maximum + 1)
                val second = random.nextInt(1, maximum + 1)
                Candidate(kind, first + second, listOf(first, second), "kg")
            }
            PromptKind.MASS_DIFFERENCE -> {
                val maximum = if (guided) 10 else 20
                val lighter = random.nextInt(1, maximum)
                val heavier = random.nextInt(lighter + 1, maximum + 1)
                Candidate(kind, heavier - lighter, listOf(heavier, lighter), "kg")
            }
        }
    }

    private fun fallbackCandidates(kind: PromptKind): Sequence<Candidate> = sequence {
        when (kind) {
            PromptKind.HALF_HOUR_DURATION -> for (hour in 1..11) {
                yield(Candidate(kind, 30, listOf(hour, 0, hour, 30), "min"))
            }
            PromptKind.HOUR_DURATION -> for (start in 1..10) {
                for (duration in 1..2) {
                    yield(Candidate(kind, duration * 60, listOf(start, start + duration), "min"))
                }
            }
            PromptKind.MONEY_TOTAL -> for (first in 1..25) {
                for (second in 1..25) {
                    yield(Candidate(kind, first + second, listOf(first, second), "euro"))
                }
            }
            PromptKind.MONEY_CHANGE -> for (paid in listOf(20, 50)) {
                for (price in 1 until paid) {
                    yield(Candidate(kind, paid - price, listOf(price, paid), "euro"))
                }
            }
            PromptKind.METRES_TO_CENTIMETRES -> for (metres in 1..3) {
                yield(Candidate(kind, metres * 100, listOf(metres), "cm"))
            }
            PromptKind.LENGTH_DIFFERENCE -> for (shorter in 5..99) {
                for (longer in (shorter + 1)..100) {
                    yield(Candidate(kind, longer - shorter, listOf(longer, shorter), "cm"))
                }
            }
            PromptKind.MASS_TOTAL -> for (first in 1..10) {
                for (second in 1..10) {
                    yield(Candidate(kind, first + second, listOf(first, second), "kg"))
                }
            }
            PromptKind.MASS_DIFFERENCE -> for (lighter in 1..19) {
                for (heavier in (lighter + 1)..20) {
                    yield(Candidate(kind, heavier - lighter, listOf(heavier, lighter), "kg"))
                }
            }
        }
    }

    private fun exercise(
        seed: LessonGenerationSeed,
        index: Int,
        phase: Phase,
        candidate: Candidate
    ): ExerciseContent = ExerciseContent(
        id = "generated.g2.geometry.measurement_context.v1.${seed.fingerprint}.${index + 1}",
        domain = LearningDomain.GEOMETRY,
        skill = "measurement_context",
        section = if (phase == Phase.GUIDED) ExerciseSection.PRACTICE else ExerciseSection.TESTS,
        level = SkillLevel.SECOND_GRADE,
        inputMode = InputMode.CUSTOM_NUMBER_PAD,
        prompt = visiblePrompt(candidate),
        speechText = spokenPrompt(candidate),
        data = linkedMapOf(
            "generatorId" to generatorId,
            "generatorVersion" to generatorVersion.toString(),
            "seedFingerprint" to seed.fingerprint,
            "roadmapEntry" to ROADMAP_ENTRY,
            "phase" to phase.name.lowercase(),
            "promptKind" to candidate.kind.name.lowercase(),
            "answerRole" to answerRole(candidate.kind),
            "values" to candidate.values.joinToString(","),
            "unit" to candidate.unit,
            "hint_nl-BE" to hintNl(candidate.kind),
            "hint_fr-BE" to hintFr(candidate.kind),
            "hint_en-US" to hintEn(candidate.kind)
        ),
        answer = candidate.answer.toString()
    )

    private fun visiblePrompt(candidate: Candidate): LocalizedText {
        val v = candidate.values
        return when (candidate.kind) {
            PromptKind.HALF_HOUR_DURATION -> LocalizedText(
                nl = "Een activiteit begint om ${v[0]}:00 en eindigt om ${v[2]}:30. Hoeveel minuten duurt ze?",
                fr = "Une activité commence à ${v[0]} h 00 et finit à ${v[2]} h 30. Combien de minutes dure-t-elle ?",
                en = "An activity starts at ${v[0]}:00 and ends at ${v[2]}:30. How many minutes does it last?"
            )
            PromptKind.HOUR_DURATION -> LocalizedText(
                nl = "Een activiteit begint om ${v[0]}:00 en eindigt om ${v[1]}:00. Hoeveel minuten duurt ze?",
                fr = "Une activité commence à ${v[0]} h 00 et finit à ${v[1]} h 00. Combien de minutes dure-t-elle ?",
                en = "An activity starts at ${v[0]}:00 and ends at ${v[1]}:00. How many minutes does it last?"
            )
            PromptKind.MONEY_TOTAL -> LocalizedText(
                nl = "Een boek kost €${v[0]} en een spel kost €${v[1]}. Hoeveel euro kosten ze samen?",
                fr = "Un livre coûte ${v[0]} € et un jeu coûte ${v[1]} €. Combien coûtent-ils ensemble, en euros ?",
                en = "A book costs €${v[0]} and a game costs €${v[1]}. How many euros do they cost altogether?"
            )
            PromptKind.MONEY_CHANGE -> LocalizedText(
                nl = "Je koopt iets van €${v[0]} en betaalt met €${v[1]}. Hoeveel euro krijg je terug?",
                fr = "Tu achètes un objet à ${v[0]} € et tu paies avec ${v[1]} €. Combien d'euros reçois-tu en retour ?",
                en = "You buy something for €${v[0]} and pay with €${v[1]}. How many euros change do you get?"
            )
            PromptKind.METRES_TO_CENTIMETRES -> LocalizedText(
                nl = "Hoeveel centimeter is ${v[0]} m?",
                fr = "Combien de centimètres font ${v[0]} m ?",
                en = "How many centimeters are in ${v[0]} m?"
            )
            PromptKind.LENGTH_DIFFERENCE -> LocalizedText(
                nl = "Een lint is ${v[0]} cm lang en een ander lint ${v[1]} cm. Hoeveel centimeter langer is het eerste lint?",
                fr = "Un ruban mesure ${v[0]} cm et un autre ${v[1]} cm. De combien de centimètres le premier est-il plus long ?",
                en = "One ribbon is ${v[0]} cm long and another is ${v[1]} cm. How many centimeters longer is the first ribbon?"
            )
            PromptKind.MASS_TOTAL -> LocalizedText(
                nl = "Een zak weegt ${v[0]} kg en een doos ${v[1]} kg. Hoeveel kilogram wegen ze samen?",
                fr = "Un sac pèse ${v[0]} kg et une boîte ${v[1]} kg. Combien de kilogrammes pèsent-ils ensemble ?",
                en = "A bag weighs ${v[0]} kg and a box weighs ${v[1]} kg. How many kilograms do they weigh altogether?"
            )
            PromptKind.MASS_DIFFERENCE -> LocalizedText(
                nl = "Een kist weegt ${v[0]} kg en een doos ${v[1]} kg. Hoeveel kilogram zwaarder is de kist?",
                fr = "Une caisse pèse ${v[0]} kg et une boîte ${v[1]} kg. De combien de kilogrammes la caisse est-elle plus lourde ?",
                en = "A crate weighs ${v[0]} kg and a box weighs ${v[1]} kg. How many kilograms heavier is the crate?"
            )
        }
    }

    private fun spokenPrompt(candidate: Candidate): LocalizedText {
        val v = candidate.values
        return when (candidate.kind) {
            PromptKind.HALF_HOUR_DURATION -> LocalizedText(
                nl = "Een activiteit begint om ${v[0]} uur en eindigt om half ${v[0] + 1}. Hoeveel minuten duurt ze",
                fr = "Une activité commence à ${v[0]} heures et finit à ${v[0]} heures trente. Combien de minutes dure-t-elle",
                en = "An activity starts at ${v[0]} o'clock and ends at half past ${v[0]}. How many minutes does it last"
            )
            PromptKind.HOUR_DURATION -> LocalizedText(
                nl = "Een activiteit begint om ${v[0]} uur en eindigt om ${v[1]} uur. Hoeveel minuten duurt ze",
                fr = "Une activité commence à ${v[0]} heures et finit à ${v[1]} heures. Combien de minutes dure-t-elle",
                en = "An activity starts at ${v[0]} o'clock and ends at ${v[1]} o'clock. How many minutes does it last"
            )
            PromptKind.MONEY_TOTAL -> LocalizedText(
                nl = "Een boek kost ${v[0]} euro en een spel kost ${v[1]} euro. Hoeveel euro kosten ze samen",
                fr = "Un livre coûte ${v[0]} euros et un jeu coûte ${v[1]} euros. Combien coûtent-ils ensemble",
                en = "A book costs ${v[0]} euros and a game costs ${v[1]} euros. How many euros do they cost altogether"
            )
            PromptKind.MONEY_CHANGE -> LocalizedText(
                nl = "Je koopt iets van ${v[0]} euro en betaalt met ${v[1]} euro. Hoeveel euro krijg je terug",
                fr = "Tu achètes un objet à ${v[0]} euros et tu paies avec ${v[1]} euros. Combien d'euros reçois-tu en retour",
                en = "You buy something for ${v[0]} euros and pay with ${v[1]} euros. How many euros change do you get"
            )
            PromptKind.METRES_TO_CENTIMETRES -> LocalizedText(
                nl = "Hoeveel centimeter is ${v[0]} meter",
                fr = "Combien de centimètres font ${v[0]} mètres",
                en = "How many centimeters are in ${v[0]} meters"
            )
            PromptKind.LENGTH_DIFFERENCE -> LocalizedText(
                nl = "Een lint is ${v[0]} centimeter lang en een ander lint ${v[1]} centimeter. Hoeveel centimeter langer is het eerste lint",
                fr = "Un ruban mesure ${v[0]} centimètres et un autre ${v[1]} centimètres. De combien de centimètres le premier est-il plus long",
                en = "One ribbon is ${v[0]} centimeters long and another is ${v[1]} centimeters. How many centimeters longer is the first ribbon"
            )
            PromptKind.MASS_TOTAL -> LocalizedText(
                nl = "Een zak weegt ${v[0]} kilogram en een doos ${v[1]} kilogram. Hoeveel kilogram wegen ze samen",
                fr = "Un sac pèse ${v[0]} kilogrammes et une boîte ${v[1]} kilogrammes. Combien de kilogrammes pèsent-ils ensemble",
                en = "A bag weighs ${v[0]} kilograms and a box weighs ${v[1]} kilograms. How many kilograms do they weigh altogether"
            )
            PromptKind.MASS_DIFFERENCE -> LocalizedText(
                nl = "Een kist weegt ${v[0]} kilogram en een doos ${v[1]} kilogram. Hoeveel kilogram zwaarder is de kist",
                fr = "Une caisse pèse ${v[0]} kilogrammes et une boîte ${v[1]} kilogrammes. De combien de kilogrammes la caisse est-elle plus lourde",
                en = "A crate weighs ${v[0]} kilograms and a box weighs ${v[1]} kilograms. How many kilograms heavier is the crate"
            )
        }
    }

    private fun answerRole(kind: PromptKind): String = when (kind) {
        PromptKind.HALF_HOUR_DURATION, PromptKind.HOUR_DURATION -> "duration_minutes"
        PromptKind.MONEY_TOTAL -> "total_euros"
        PromptKind.MONEY_CHANGE -> "change_euros"
        PromptKind.METRES_TO_CENTIMETRES -> "length_centimetres"
        PromptKind.LENGTH_DIFFERENCE -> "length_difference_centimetres"
        PromptKind.MASS_TOTAL -> "total_kilograms"
        PromptKind.MASS_DIFFERENCE -> "mass_difference_kilograms"
    }

    private fun hintNl(kind: PromptKind): String = when (kind) {
        PromptKind.HALF_HOUR_DURATION -> "Een halfuur duurt 30 minuten."
        PromptKind.HOUR_DURATION -> "Elk volledig uur duurt 60 minuten."
        PromptKind.MONEY_TOTAL -> "Tel de twee bedragen op."
        PromptKind.MONEY_CHANGE -> "Trek de prijs af van het betaalde bedrag."
        PromptKind.METRES_TO_CENTIMETRES -> "Eén meter is 100 centimeter."
        PromptKind.LENGTH_DIFFERENCE -> "Trek de kortste lengte af van de langste."
        PromptKind.MASS_TOTAL -> "Tel de twee massa's op."
        PromptKind.MASS_DIFFERENCE -> "Trek de kleinste massa af van de grootste."
    }

    private fun hintFr(kind: PromptKind): String = when (kind) {
        PromptKind.HALF_HOUR_DURATION -> "Une demi-heure dure 30 minutes."
        PromptKind.HOUR_DURATION -> "Chaque heure complète dure 60 minutes."
        PromptKind.MONEY_TOTAL -> "Additionne les deux montants."
        PromptKind.MONEY_CHANGE -> "Soustrais le prix du montant payé."
        PromptKind.METRES_TO_CENTIMETRES -> "Un mètre vaut 100 centimètres."
        PromptKind.LENGTH_DIFFERENCE -> "Soustrais la longueur la plus courte de la plus longue."
        PromptKind.MASS_TOTAL -> "Additionne les deux masses."
        PromptKind.MASS_DIFFERENCE -> "Soustrais la plus petite masse de la plus grande."
    }

    private fun hintEn(kind: PromptKind): String = when (kind) {
        PromptKind.HALF_HOUR_DURATION -> "Half an hour is 30 minutes."
        PromptKind.HOUR_DURATION -> "Each whole hour is 60 minutes."
        PromptKind.MONEY_TOTAL -> "Add the two amounts."
        PromptKind.MONEY_CHANGE -> "Subtract the price from the amount paid."
        PromptKind.METRES_TO_CENTIMETRES -> "One meter is 100 centimeters."
        PromptKind.LENGTH_DIFFERENCE -> "Subtract the shorter length from the longer length."
        PromptKind.MASS_TOTAL -> "Add the two masses."
        PromptKind.MASS_DIFFERENCE -> "Subtract the smaller mass from the larger mass."
    }

    private data class Candidate(
        val kind: PromptKind,
        val answer: Int,
        val values: List<Int>,
        val unit: String
    ) {
        fun signature(): String = "${kind.name}:${answer}:${values.joinToString(":")}:$unit"
    }

    private enum class Phase { GUIDED, CHECKPOINT }

    private enum class PromptKind {
        HALF_HOUR_DURATION,
        HOUR_DURATION,
        MONEY_TOTAL,
        MONEY_CHANGE,
        METRES_TO_CENTIMETRES,
        LENGTH_DIFFERENCE,
        MASS_TOTAL,
        MASS_DIFFERENCE
    }
}
