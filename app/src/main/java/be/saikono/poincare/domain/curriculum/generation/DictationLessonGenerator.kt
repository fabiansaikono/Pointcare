package be.saikono.poincare.domain.curriculum.generation

import be.saikono.poincare.model.ExerciseContent
import be.saikono.poincare.model.ExerciseSection
import be.saikono.poincare.model.InputMode
import be.saikono.poincare.model.LearningDomain
import be.saikono.poincare.model.LocalizedText
import be.saikono.poincare.model.RewardMetadata
import be.saikono.poincare.model.SkillLevel

/** Deterministic Dutch dictation questions for the canonical curriculum lessons. */
object DictationLessonGenerator : LessonExerciseGenerator {
    override val generatorId: String = "generator.language.dictation"
    override val generatorVersion: Int = 1

    private val allowedParameterKeys = setOf(
        "editorial_status",
        "grade",
        "phase",
        "exercise_type",
        "dictation_mode",
        "keyboard_layout",
        "instruction_nl",
        "spoken_content_policy",
        "wrong answers allowed"
    )

    override fun validate(parameters: Map<String, String>): List<String> = buildList {
        val unknown = parameters.keys - allowedParameterKeys
        if (unknown.isNotEmpty()) add("unsupported parameters: ${unknown.sorted().joinToString()}")
        if (parameters["editorial_status"] != LessonGeneratorRegistry.APPROVED_STATUS) {
            add("editorial_status must be approved")
        }
        if (parameters["grade"]?.toIntOrNull() !in 1..4) add("grade must be between 1 and 4")
        if (parameters["phase"] !in setOf("guided", "checkpoint")) {
            add("phase must be guided or checkpoint")
        }
        if (parameters["dictation_mode"] !in DictationMode.entries.map { it.id }) {
            add("dictation_mode is not supported")
        }
        if (parameters["keyboard_layout"] != "AZERTY_BE_GENERATED") {
            add("keyboard_layout must be AZERTY_BE_GENERATED")
        }
        if (parameters["spoken_content_policy"] != "QUESTION_ONLY") {
            add("spoken_content_policy must be QUESTION_ONLY")
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

        val grade = parameters.getValue("grade").toInt()
        val mode = DictationMode.entries.single { it.id == parameters.getValue("dictation_mode") }
        val phase = parameters.getValue("phase")
        val candidates = if (mode == DictationMode.WRITE_SENTENCE) {
            SENTENCES.getValue(grade)
        } else {
            WORDS.getValue(grade)
        }.shuffledWith(StableLessonRandom(seed.value))

        return List(exerciseCount) { index ->
            val answer = candidates[index % candidates.size]
            exercise(seed, index, grade, phase, mode, answer)
        }
    }

    private fun exercise(
        seed: LessonGenerationSeed,
        index: Int,
        grade: Int,
        phase: String,
        mode: DictationMode,
        answer: String
    ): ExerciseContent {
        val id = "generated.dictation.g$grade.v1.${seed.fingerprint}.${index + 1}"
        val hint = when (mode) {
            DictationMode.MISSING_ONE -> "Luister opnieuw en zoek de ene ontbrekende letter."
            DictationMode.MISSING_MANY -> "Luister opnieuw en vul de ontbrekende letters één voor één in."
            DictationMode.WRITE_WORD -> "Luister opnieuw, zeg het woord langzaam en schrijf alle klanken."
            DictationMode.WRITE_SENTENCE -> "Luister opnieuw en controleer ook hoofdletter, spaties en leesteken."
        }
        return ExerciseContent(
            id = id,
            domain = LearningDomain.READING,
            skill = "dictation",
            section = if (phase == "checkpoint") ExerciseSection.TESTS else ExerciseSection.EXERCISES,
            level = grade.toSkillLevel(),
            inputMode = InputMode.CUSTOM_TEXT_KEYBOARD,
            prompt = LocalizedText("Schrijf wat je hoort."),
            // The read-aloud action speaks only the actual question: the target word or sentence.
            speechText = LocalizedText(answer),
            data = buildMap {
                put("generatorId", generatorId)
                put("generatorVersion", generatorVersion.toString())
                put("seedFingerprint", seed.fingerprint)
                put("contentKey", "g$grade.${mode.key}.${answer.contentKey()}")
                put("phase", phase)
                put("grade", grade.toString())
                put("readingGrade", grade.toString())
                put("dictationMode", mode.id)
                put("keyboardLayout", "AZERTY_BE_GENERATED")
                put("spokenContentPolicy", "QUESTION_ONLY")
                put("wrongAnswersAllowed", if (mode.missingLetterCount > 0) "1" else "2")
                put("promptKind", "dictation")
                put("masteryTag", "language.dictation.grade$grade.${mode.key}")
                put("errorTags", "incorrect.dictation.${mode.key}")
                put("audioRequired", "true")
                put("topic", "Dictee")
                put("answer_nl-BE", answer)
                put("targetWord_nl-BE", answer)
                put("dictationText_nl-BE", answer)
                put("audioText_nl-BE", answer)
                put("audioTtsLanguage_nl-BE", "nl-BE")
                put("audioId_nl-BE", id)
                put("hint_nl-BE", hint)
                put("feedback_nl-BE", hint)
                if (mode.missingLetterCount > 0) {
                    put("missingLetterCount", mode.missingLetterCount.toString())
                    put("maskedText", answer.maskLetters(mode.missingLetterCount))
                }
            },
            answer = answer,
            rewardMetadata = RewardMetadata(stamps = 1)
        )
    }

    private fun List<String>.shuffledWith(random: StableLessonRandom): List<String> {
        val result = toMutableList()
        for (index in result.lastIndex downTo 1) {
            val other = random.nextInt(index + 1)
            val value = result[index]
            result[index] = result[other]
            result[other] = value
        }
        return result
    }

    private fun String.maskLetters(count: Int): String {
        val letterIndices = indices.filter { this[it].isLetter() }
        val selected = letterIndices.takeLast(count.coerceAtMost(letterIndices.size)).toSet()
        return mapIndexed { index, character -> if (index in selected) '_' else character }.joinToString("")
    }

    private fun String.contentKey(): String = lowercase()
        .replace(Regex("[^a-z0-9]+"), "-")
        .trim('-')

    private fun Int.toSkillLevel(): SkillLevel = when (this) {
        1 -> SkillLevel.FIRST_GRADE
        2 -> SkillLevel.SECOND_GRADE
        3 -> SkillLevel.THIRD_GRADE
        else -> SkillLevel.FOURTH_GRADE
    }

    private enum class DictationMode(
        val id: String,
        val key: String,
        val missingLetterCount: Int = 0
    ) {
        MISSING_ONE("MISSING_LETTER_DRAG_DROP", "missing-one", 1),
        MISSING_MANY("MISSING_LETTERS_DRAG_DROP", "missing-many", 2),
        WRITE_WORD("WRITE_WORD", "whole-word"),
        WRITE_SENTENCE("WRITE_SENTENCE", "full-sentence")
    }

    private val WORDS = mapOf(
        1 to listOf("maan", "vis", "boek", "roos", "boom", "kat", "pen", "huis", "zon", "neus", "jas", "voet", "deur", "ring", "sok", "bus", "koek", "mus", "peer", "tuin"),
        2 to listOf("bakker", "winter", "appel", "school", "konijn", "vriend", "bloem", "straat", "vogel", "tafel", "letter", "jongen", "meisje", "zomer", "keuken", "zwemmen", "fietsen", "kamer", "regen", "sleutel"),
        3 to listOf("avontuur", "bibliotheek", "verrassing", "moeilijk", "schaduw", "eiland", "verhaal", "onderzoek", "vliegtuig", "eerlijk", "prachtig", "antwoord", "geheim", "natuur", "verdwalen", "beloven", "oefening", "gelukkig", "ontdekken", "oplossing"),
        4 to listOf("onmiddellijk", "waarschijnlijk", "nieuwsgierig", "technologie", "verantwoordelijk", "gebeurtenis", "verschillend", "bijzonder", "natuurlijk", "omgeving", "kampioen", "belangrijk", "onderzoeker", "maatschappij", "ontwikkeling", "uitzondering", "tegelijkertijd", "beslissing", "mogelijkheid", "samenwerking")
    )

    private val SENTENCES = mapOf(
        1 to listOf("De kat zit op de mat.", "Ik lees een boek.", "De zon is warm.", "Wij gaan naar huis.", "De vis zwemt snel.", "Mijn jas is rood.", "De maan staat hoog.", "Papa bakt brood.", "De bus is geel.", "Een mus zit op het dak.", "Ik eet een peer.", "De deur is dicht.", "De hond rent in het bos.", "Mijn pen ligt op de bank.", "Wij zien een grote boot.", "De muis eet kaas.", "Mama zet thee op tafel.", "Het kind draagt een pet.", "Een kip loopt bij het hek.", "Ik speel met de bal."),
        2 to listOf("De kinderen spelen op straat.", "Mijn vriend leest een spannend boek.", "De vogel zit hoog in de boom.", "Wij wandelen samen naar school.", "De bakker maakt vers brood.", "Het konijn loopt door de tuin.", "Mijn appel ligt op de tafel.", "In de winter draag ik een jas.", "Na de regen fietsen we naar huis.", "Het meisje zoekt haar sleutel.", "Wij zwemmen elke woensdag samen.", "De bloemen staan in de keuken.", "De juf schrijft een woord op het bord.", "Mijn broer ruimt zijn kamer op.", "De hond slaapt naast de stoel.", "Vanmiddag spelen wij in het park.", "Ik zet mijn schoenen bij de deur.", "De trein vertrekt om acht uur.", "In de zomer eten we buiten.", "Het regent zacht op het raam."),
        3 to listOf("Morgen bezoeken we de bibliotheek.", "Het vliegtuig vliegt boven de wolken.", "Wij onderzoeken waarom de schaduw beweegt.", "Het verhaal eindigt met een verrassing.", "Mijn zus geeft altijd een eerlijk antwoord.", "Op het eiland beleven we een avontuur.", "De prachtige bloem groeit bij het raam.", "Deze oefening lijkt moeilijk maar lukt wel.", "Samen ontdekken we een slimme oplossing.", "Het geheim bleef lang verborgen.", "In de natuur voelen wij ons gelukkig.", "Ik beloof dat ik morgen oefen.", "De klas bespreekt het nieuwe onderwerp.", "Tijdens de wandeling vonden we een veer.", "Mijn buurjongen bouwde een stevige hut.", "Na het ontbijt vertrokken we meteen.", "De gids vertelde een bijzonder verhaal.", "Iedere leerling controleert zijn antwoord.", "We maakten een tekening van het landschap.", "Mijn oma bewaart brieven in een doos."),
        4 to listOf("Nieuwsgierig onderzocht ze de vreemde gebeurtenis.", "Technologie verandert onze omgeving voortdurend.", "Waarschijnlijk begint de wedstrijd onmiddellijk.", "Iedereen is verantwoordelijk voor zijn eigen materiaal.", "De kampioen gaf een bijzonder eerlijk antwoord.", "Natuurlijk kunnen verschillende oplossingen juist zijn.", "Het is belangrijk om de opdracht aandachtig te lezen.", "Wij bespreken morgen het volledige onderzoeksverslag.", "Een goede samenwerking biedt nieuwe mogelijkheden.", "De onderzoeker verklaarde zijn beslissing duidelijk.", "De maatschappij verandert door technologische ontwikkeling.", "Tegelijkertijd controleerden we de uitzonderingen.", "De leerlingen vergeleken zorgvuldig hun waarnemingen.", "Onze klas organiseert binnenkort een tentoonstelling.", "De journalist beschreef de situatie nauwkeurig.", "Hoewel het regende, ging de uitstap gewoon door.", "De burgemeester kondigde een nieuw initiatief aan.", "Na de vergadering noteerden we alle afspraken.", "Het experiment leverde een verrassend resultaat op.", "Met voldoende voorbereiding lukt deze moeilijke opdracht.")
    )
}
