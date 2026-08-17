package be.saikono.poincare.domain.curriculum.v2

import be.saikono.poincare.domain.curriculum.LessonActivitySource
import be.saikono.poincare.domain.curriculum.LessonSeedPolicy
import be.saikono.poincare.domain.curriculum.generation.LessonGenerationContext
import be.saikono.poincare.domain.curriculum.generation.LessonGenerationResult
import be.saikono.poincare.domain.curriculum.generation.LessonGeneratorRegistry
import be.saikono.poincare.model.ExerciseContent
import be.saikono.poincare.model.ExerciseSection
import be.saikono.poincare.model.InputMode
import be.saikono.poincare.model.LearningDomain
import be.saikono.poincare.model.LocalizedText
import be.saikono.poincare.repository.ContentRepository
import java.text.Normalizer

data class LessonResolveContextV2(
    val profileId: String,
    val lessonId: String,
    val unitId: String,
    val stageId: String,
    val attemptId: String,
    val canonicalDomain: LearningDomain? = null,
    val canonicalSubcategoryId: String? = null,
    val excludedExerciseSignatures: Set<String> = emptySet()
)

data class ResolvedLessonExerciseSetV2(
    val setId: String,
    val sourceMode: LessonSourceModeV2,
    val exercises: List<ExerciseContent>,
    val generatorSeed: Long? = null,
    val seedFingerprint: String? = null
)

sealed interface LessonExerciseResolutionV2 {
    data class Success(val value: ResolvedLessonExerciseSetV2) : LessonExerciseResolutionV2
    data class Rejected(val message: String, val details: List<String> = emptyList()) : LessonExerciseResolutionV2
}

fun interface CuratedLessonExerciseProviderV2 {
    suspend fun getByStableId(exerciseId: String): ExerciseContent?
}

/**
 * XML remains an import/storage detail behind this adapter. Callers only see stable curated IDs.
 */
class LegacyXmlExerciseAdapterV2(
    private val contentRepository: ContentRepository
) : CuratedLessonExerciseProviderV2 {
    override suspend fun getByStableId(exerciseId: String): ExerciseContent? =
        contentRepository.getExerciseById(exerciseId)
}

fun interface GeneratedLessonExerciseProviderV2 {
    fun generate(
        set: LessonExerciseSetV2,
        context: LessonResolveContextV2
    ): LessonExerciseResolutionV2
}

class ExistingGeneratorAdapterV2(
    private val registry: LessonGeneratorRegistry = LessonGeneratorRegistry.default()
) : GeneratedLessonExerciseProviderV2 {
    override fun generate(
        set: LessonExerciseSetV2,
        context: LessonResolveContextV2
    ): LessonExerciseResolutionV2 {
        val excludeRecent = set.parameters["exclude_recent_exact_items"] == "true"
        val source = LessonActivitySource.Generated(
            generatorId = requireNotNull(set.generatorId),
            generatorVersion = requireNotNull(set.generatorImplementationVersion),
            exerciseCount = set.itemCount,
            seedPolicy = when (requireNotNull(set.seedPolicy)) {
                LessonSeedPolicyV2.STABLE_PER_PROFILE_LESSON -> LessonSeedPolicy.STABLE_PER_PROFILE_LESSON
                LessonSeedPolicyV2.STABLE_PER_PROFILE_ATTEMPT -> LessonSeedPolicy.STABLE_PER_PROFILE_ATTEMPT
            },
            parameters = set.parameters - CATALOG_ONLY_PARAMETERS
        )
        if (!excludeRecent || context.excludedExerciseSignatures.isEmpty()) {
            return materialize(source, set, context, context.attemptId)
        }

        val selected = mutableListOf<ExerciseContent>()
        val selectedSignatures = context.excludedExerciseSignatures.toMutableSet()
        var firstSeed: Long? = null
        var firstFingerprint: String? = null

        repeat(MAX_UNSEEN_VARIANTS) { variantIndex ->
            val variantAttemptId = "${context.attemptId}.unseen.${variantIndex + 1}"
            when (val result = materialize(source, set, context, variantAttemptId)) {
                is LessonExerciseResolutionV2.Rejected -> return result
                is LessonExerciseResolutionV2.Success -> {
                    if (firstSeed == null) {
                        firstSeed = result.value.generatorSeed
                        firstFingerprint = result.value.seedFingerprint
                    }
                    result.value.exercises
                        .forEach { exercise ->
                            val signature = ExerciseContentSignatureV2.of(exercise)
                            if (selected.size < set.itemCount && selectedSignatures.add(signature)) {
                                selected += exercise
                            }
                        }
                    if (selected.size == set.itemCount) {
                        return LessonExerciseResolutionV2.Success(
                            ResolvedLessonExerciseSetV2(
                                setId = set.id,
                                sourceMode = set.sourceMode,
                                exercises = selected,
                                generatorSeed = firstSeed,
                                seedFingerprint = firstFingerprint
                            )
                        )
                    }
                }
            }
        }

        return LessonExerciseResolutionV2.Rejected(
            message = "The assessment bank could not provide enough unseen items.",
            details = listOf(
                "setId=${set.id}",
                "requested=${set.itemCount}",
                "unseenFound=${selected.size}",
                "excludedRecent=${context.excludedExerciseSignatures.size}",
                "testForm=${set.parameters["test_form"].orEmpty().ifBlank { "none" }}",
                "resolvedIntents=${selected.mapNotNull { it.data["questionIntent"] }.distinct().joinToString("|")}"
            )
        )
    }

    private fun materialize(
        source: LessonActivitySource.Generated,
        set: LessonExerciseSetV2,
        context: LessonResolveContextV2,
        attemptId: String
    ): LessonExerciseResolutionV2 = when (val result = registry.materialize(
        source = source,
        context = LessonGenerationContext(
            profileId = context.profileId,
            unitId = context.unitId,
            lessonId = context.lessonId,
            sectionId = "${context.stageId}.${set.id}",
            canonicalDomain = context.canonicalDomain,
            canonicalSubcategoryId = context.canonicalSubcategoryId,
            attemptId = attemptId
        )
    )) {
        is LessonGenerationResult.Success -> {
            val exercises = result.exercises.mapIndexed { index, exercise ->
                exercise
                    .withSemanticAssessmentChallenge(set, attemptId, index)
                    .enforceSetContract(set, context)
            }
            val issues = exercises.flatMapIndexed { index, exercise ->
                ResolvedExerciseContractValidatorV2.validate(exercise).map { issue ->
                    "exercise[$index] $issue"
                }
            }
            if (issues.isNotEmpty()) {
                LessonExerciseResolutionV2.Rejected(
                    message = "Resolved exercises violated the lesson-set contract.",
                    details = issues
                )
            } else {
                LessonExerciseResolutionV2.Success(
                    ResolvedLessonExerciseSetV2(
                        setId = set.id,
                        sourceMode = set.sourceMode,
                        exercises = exercises,
                        generatorSeed = result.seed.value,
                        seedFingerprint = result.seed.fingerprint
                    )
                )
            }
        }
        is LessonGenerationResult.Rejected -> LessonExerciseResolutionV2.Rejected(
            result.message,
            result.details
        )
    }

    private fun ExerciseContent.withSemanticAssessmentChallenge(
        set: LessonExerciseSetV2,
        attemptId: String,
        exerciseIndex: Int
    ): ExerciseContent {
        if (set.purpose != LessonExercisePurposeV2.MASTERY_TEST || data["dictationMode"] != null) {
            return this
        }
        val retryOrdinal = attemptId.substringAfterLast(".unseen.", "0").toIntOrNull() ?: 0
        val misconception: Map<String, String>
        val wrongKey: String
        val divisor: Int
        if (inputMode in CHOICE_INPUT_MODES) {
            fun options(locale: String): List<String> = data["options_$locale"].orEmpty()
                .split('\n')
                .map(String::trim)
                .filter(String::isNotBlank)
            val nlOptions = options("nl-BE")
            val optionsByLocale = LOCALES.associateWith { locale ->
                options(locale).takeIf { it.size == nlOptions.size } ?: nlOptions
            }
            val nlAnswer = data["answer_nl-BE"].orEmpty().trim().ifBlank { answer }
            val answersByLocale = LOCALES.associateWith { locale ->
                data["answer_$locale"].orEmpty().trim().ifBlank { nlAnswer }
            }
            val answerIndex = nlOptions.indexOf(answersByLocale.getValue("nl-BE"))
            if (answerIndex < 0 || nlOptions.size < 2) {
                return this
            }
            val wrongIndices = nlOptions.indices.filter { it != answerIndex }
            val wrongIndex = wrongIndices[(retryOrdinal + exerciseIndex) % wrongIndices.size]
            misconception = LOCALES.associateWith { locale -> optionsByLocale.getValue(locale)[wrongIndex] }
            wrongKey = if (wrongIndices.indexOf(wrongIndex) == 0) "wrong_a" else "wrong_b"
            divisor = wrongIndices.size
        } else {
            misconception = syntheticMisconception(inputMode, data, answer)
            wrongKey = "wrong_${(retryOrdinal + exerciseIndex) % 4}"
            divisor = 1
        }
        val mode = AssessmentChallengeMode.values()[
            (retryOrdinal / divisor + exerciseIndex) % AssessmentChallengeMode.values().size
        ]
        val assessmentForm = set.parameters["test_form"].orEmpty().uppercase()
        val prompts = mapOf(
            "nl-BE" to assessmentPromptNl(mode, assessmentForm, prompt.nl, misconception.getValue("nl-BE")),
            "fr-BE" to assessmentPromptFr(mode, assessmentForm, prompt.fr.orEmpty(), misconception.getValue("fr-BE")),
            "en-US" to assessmentPromptEn(mode, assessmentForm, prompt.en.orEmpty(), misconception.getValue("en-US"))
        )
        val challengeData = data.toMutableMap().apply {
            put("questionIntent", "ASSESSMENT_${mode.name}_FORM_${assessmentForm.ifBlank { "A" }}")
            put("contentKey", "${data["contentKey"].orEmpty()}.assessment.${mode.key}.$wrongKey.form_${assessmentForm.ifBlank { "a" }.lowercase()}")
            put("assessmentMisconception_nl-BE", misconception.getValue("nl-BE"))
            put("assessmentMisconception_fr-BE", misconception.getValue("fr-BE"))
            put("assessmentMisconception_en-US", misconception.getValue("en-US"))
        }
        return copy(
            prompt = LocalizedText(
                nl = prompts.getValue("nl-BE"),
                fr = prompts.getValue("fr-BE"),
                en = prompts.getValue("en-US")
            ),
            speechText = LocalizedText(
                nl = prompts.getValue("nl-BE"),
                fr = prompts.getValue("fr-BE"),
                en = prompts.getValue("en-US")
            ),
            data = challengeData
        )
    }

    private fun syntheticMisconception(
        inputMode: InputMode,
        data: Map<String, String>,
        fallbackAnswer: String
    ): Map<String, String> {
        val localizedAnswers = LOCALES.associateWith { locale ->
            data["answer_$locale"].orEmpty().ifBlank { fallbackAnswer }
        }
        val numeric = localizedAnswers.getValue("nl-BE").toIntOrNull()
        return when {
            numeric != null -> LOCALES.associateWith { (numeric + 1).toString() }
            inputMode.name.startsWith("TRACE_") -> mapOf(
                "nl-BE" to "beginnen aan het eindpunt en de vorm onderbreken",
                "fr-BE" to "commencer au point final et interrompre la forme",
                "en-US" to "starting at the end point and breaking the shape"
            )
            inputMode == InputMode.CUSTOM_TEXT_KEYBOARD -> mapOf(
                "nl-BE" to "een onvolledig antwoord zonder de gevraagde informatie",
                "fr-BE" to "une réponse incomplète sans l'information demandée",
                "en-US" to "an incomplete answer without the requested information"
            )
            else -> mapOf(
                "nl-BE" to "een antwoord dat de belangrijkste stap overslaat",
                "fr-BE" to "une réponse qui omet l'étape principale",
                "en-US" to "an answer that skips the main step"
            )
        }
    }

    private fun assessmentPromptNl(mode: AssessmentChallengeMode, form: String, original: String, wrong: String): String =
        if (form == "B") when (mode) {
            AssessmentChallengeMode.ERROR_REPAIR ->
                "Welke juiste keuze vervangt ‘$wrong’ bij deze opdracht: ${original.trimEnd(' ', '?')}?"
            AssessmentChallengeMode.CONTRAST ->
                "Wat hoort in plaats van ‘$wrong’ bij deze opdracht: ${original.trimEnd(' ', '?')}?"
            AssessmentChallengeMode.VERIFY ->
                "Met welke juiste keuze toon je aan dat ‘$wrong’ niet klopt bij: ${original.trimEnd(' ', '?')}?"
        } else
        when (mode) {
            AssessmentChallengeMode.ERROR_REPAIR ->
                "Een leerling kiest ‘$wrong’. Welke keuze herstelt die fout bij deze opdracht: ${original.trimEnd(' ', '?')}?"
            AssessmentChallengeMode.CONTRAST ->
                "De keuze ‘$wrong’ past niet. Welke keuze past wel bij deze opdracht: ${original.trimEnd(' ', '?')}?"
            AssessmentChallengeMode.VERIFY ->
                "Je controleert waarom ‘$wrong’ niet klopt. Met welke juiste keuze vergelijk je dit bij: ${original.trimEnd(' ', '?')}?"
        }

    private fun assessmentPromptFr(mode: AssessmentChallengeMode, form: String, original: String, wrong: String): String =
        if (form == "B") when (mode) {
            AssessmentChallengeMode.ERROR_REPAIR ->
                "Quel bon choix remplace « $wrong » pour l'exercice suivant : ${original.trimEnd(' ', '?')} ?"
            AssessmentChallengeMode.CONTRAST ->
                "Que faut-il choisir à la place de « $wrong » pour : ${original.trimEnd(' ', '?')} ?"
            AssessmentChallengeMode.VERIFY ->
                "Avec quel bon choix montres-tu que « $wrong » est incorrect pour : ${original.trimEnd(' ', '?')} ?"
        } else
        when (mode) {
            AssessmentChallengeMode.ERROR_REPAIR ->
                "Un élève choisit « $wrong ». Quel choix corrige cette erreur pour l'exercice suivant : ${original.trimEnd(' ', '?')} ?"
            AssessmentChallengeMode.CONTRAST ->
                "Le choix « $wrong » ne convient pas. Quel choix convient pour l'exercice suivant : ${original.trimEnd(' ', '?')} ?"
            AssessmentChallengeMode.VERIFY ->
                "Tu vérifies pourquoi « $wrong » est incorrect. À quel bon choix le compares-tu pour : ${original.trimEnd(' ', '?')} ?"
        }

    private fun assessmentPromptEn(mode: AssessmentChallengeMode, form: String, original: String, wrong: String): String =
        if (form == "B") when (mode) {
            AssessmentChallengeMode.ERROR_REPAIR ->
                "Which correct choice replaces ‘$wrong’ for this task: ${original.trimEnd(' ', '?')}?"
            AssessmentChallengeMode.CONTRAST ->
                "What belongs in place of ‘$wrong’ for this task: ${original.trimEnd(' ', '?')}?"
            AssessmentChallengeMode.VERIFY ->
                "Which correct choice shows that ‘$wrong’ is wrong for: ${original.trimEnd(' ', '?')}?"
        } else
        when (mode) {
            AssessmentChallengeMode.ERROR_REPAIR ->
                "A learner chooses ‘$wrong’. Which choice repairs that error for this task: ${original.trimEnd(' ', '?')}?"
            AssessmentChallengeMode.CONTRAST ->
                "The choice ‘$wrong’ does not fit. Which choice does fit this task: ${original.trimEnd(' ', '?')}?"
            AssessmentChallengeMode.VERIFY ->
                "You are checking why ‘$wrong’ is incorrect. Which correct choice should you compare it with for: ${original.trimEnd(' ', '?')}?"
        }

    private fun ExerciseContent.enforceSetContract(
        set: LessonExerciseSetV2,
        context: LessonResolveContextV2
    ): ExerciseContent {
        val isTest = set.purpose == LessonExercisePurposeV2.MASTERY_TEST
        val learningPhase = when (set.purpose) {
            LessonExercisePurposeV2.GUIDED,
            LessonExercisePurposeV2.FADING_SUPPORT,
            LessonExercisePurposeV2.INDEPENDENT_PRACTICE,
            LessonExercisePurposeV2.ERROR_REVIEW -> "PRACTISE"
            LessonExercisePurposeV2.NEAR_TRANSFER,
            LessonExercisePurposeV2.FAR_TRANSFER -> "APPLY_DEMONSTRATE"
            LessonExercisePurposeV2.MASTERY_TEST -> "MASTERY_TEST"
        }
        val contractData = data
            .filterKeys { key -> !isTest || !key.startsWith("hint", ignoreCase = true) }
            .toMutableMap()
            .apply {
                put("catalogUnitId", context.unitId)
                put("setPurpose", set.purpose.name)
                put("learningPhase", learningPhase)
                if (set.representationIds.isNotEmpty()) {
                    put("representationIds", set.representationIds.joinToString("|"))
                }
                if (
                    context.unitId.startsWith("unit.g1.") &&
                    any { (key, value) -> key.startsWith("requiresAdult") && value.equals("true", true) }
                ) {
                    val profile = Grade1HybridEvidenceProfiles.requireFor(context.unitId)
                    put("deliveryMode", profile.deliveryMode)
                    put("evidenceMode", profile.evidenceMode)
                    put("practicalTask_nl-BE", profile.taskNl)
                    put("practicalTask_fr-BE", profile.taskFr)
                    put("practicalTask_en-US", profile.taskEn)
                    put("observableCriteria_nl-BE", profile.criteriaNl)
                    put("observableCriteria_fr-BE", profile.criteriaFr)
                    put("observableCriteria_en-US", profile.criteriaEn)
                    put("adultObservationRubric", profile.rubric)
                    put("completionEvidence", "ADULT_RUBRIC_CONFIRMATION")
                }
            }
        return copy(
            section = if (isTest) ExerciseSection.TESTS else ExerciseSection.PRACTICE,
            hints = if (isTest) emptyList() else hints,
            data = contractData
        )
    }

    private companion object {
        const val MAX_UNSEEN_VARIANTS = 72
        val CATALOG_ONLY_PARAMETERS = setOf(
            "exclude_recent_exact_items",
            WRONG_ANSWERS_ALLOWED_METADATA_KEY,
            "theory_follow_up",
            "series_variant",
            "exercise_type",
            "target_cluster",
            "first_item_signature",
            "french_theme_tag",
            "v1_pool_id",
            "v1_pool_status",
            "test_form"
        )
        val LOCALES = listOf("nl-BE", "fr-BE", "en-US")
        val CHOICE_INPUT_MODES = setOf(InputMode.TAP_CHOICE, InputMode.LISTEN_AND_SELECT)
    }

    private enum class AssessmentChallengeMode(val key: String) {
        ERROR_REPAIR("error_repair"),
        CONTRAST("contrast"),
        VERIFY("verify")
    }
}

/**
 * Quality gate for metadata that only exists after a generated item has been
 * bound to a concrete catalog set. Source generators are validated separately.
 */
object ResolvedExerciseContractValidatorV2 {
    fun validate(exercise: ExerciseContent): List<String> = buildList {
        val purpose = exercise.data["setPurpose"]
        if (purpose == LessonExercisePurposeV2.MASTERY_TEST.name) {
            if (exercise.section != ExerciseSection.TESTS) {
                add("mastery-test item is not labelled as TESTS")
            }
            if (exercise.hints.isNotEmpty() || exercise.data.keys.any { key ->
                    key.startsWith("hint", ignoreCase = true)
                }) {
                add("mastery-test item contains hint metadata")
            }
        } else if (purpose != null && exercise.section == ExerciseSection.TESTS) {
            add("non-assessment item is incorrectly labelled as TESTS")
        }

        val isGradeOne = exercise.data["catalogUnitId"]?.startsWith("unit.g1.") == true
        if (
            isGradeOne &&
            exercise.domain in setOf(LearningDomain.MATH, LearningDomain.GEOMETRY) &&
            exercise.data["representationIds"].isNullOrBlank()
        ) {
            add("math or geometry item has no representationId")
        }

        val adultMediated = exercise.data.any { (key, value) ->
            key.startsWith("requiresAdult") && value.equals("true", ignoreCase = true)
        }
        if (isGradeOne && adultMediated) {
            listOf(
                "deliveryMode",
                "evidenceMode",
                "practicalTask_nl-BE",
                "practicalTask_fr-BE",
                "practicalTask_en-US",
                "observableCriteria_nl-BE",
                "observableCriteria_fr-BE",
                "observableCriteria_en-US",
                "adultObservationRubric",
                "completionEvidence"
            ).forEach { key ->
                if (exercise.data[key].isNullOrBlank()) {
                    add("adult-mediated item is missing $key")
                }
            }
        }
    }
}

object ExerciseContentSignatureV2 {
    fun of(exercise: ExerciseContent): String = buildString {
        append(canonicalPrompt(exercise.prompt.nl))
        append('|')
        append(canonicalPrompt(exercise.prompt.fr.orEmpty()))
        append('|')
        append(canonicalPrompt(exercise.prompt.en.orEmpty()))
        append('|')
        append(normalize(exercise.data["questionIntent"] ?: exercise.data["promptKind"].orEmpty()))
        append('|')
        append(normalizeContentKey(exercise.data["contentKey"].orEmpty()))
        append('|')
        append(STIMULUS_KEYS.joinToString("¦") { key -> normalize(exercise.data[key].orEmpty()) })
        LOCALES.forEach { locale ->
            append('|')
            append(normalize(exercise.data["answer_$locale"] ?: exercise.answer))
            append('|')
            append(
                exercise.data["options_$locale"].orEmpty()
                    .split('\n')
                    .map(::normalize)
                    .filter(String::isNotBlank)
                    .sorted()
                    .joinToString("¦")
            )
        }
    }

    private fun normalize(value: String): String = Normalizer
        .normalize(value.trim().lowercase(), Normalizer.Form.NFKC)
        .replace(Regex("""\s+"""), " ")

    private fun canonicalPrompt(value: String): String {
        var normalized = normalize(value)
        var previous: String
        do {
            previous = normalized
            ASSESSMENT_LEADS.forEach { lead ->
                normalized = normalized.removePrefix(lead)
            }
            normalized = normalized.trim()
        } while (normalized != previous)
        return normalized
    }

    private fun normalizeContentKey(value: String): String = normalize(value)
        .replace(Regex("""(?:[._-](?:round|variant|attempt))?[._-]?\d+$"""), "")

    private val LOCALES = listOf("nl-BE", "fr-BE", "en-US")
    private val STIMULUS_KEYS = listOf(
        "stimulus", "values", "targetWord_nl-BE", "targetWord_fr-BE", "targetWord_en-US",
        "word", "sentence", "label", "focus", "unit"
    )
    private val ASSESSMENT_LEADS = listOf(
        "luister zonder hulp en kies daarna. ",
        "luister naar de hele opdracht voordat je antwoordt. ",
        "luister, denk na en kies dan zelfstandig. ",
        "los zelfstandig op en controleer je uitkomst. ",
        "reken zonder voorbeeld en controleer elke stap. ",
        "denk eerst aan de aanpak en reken daarna. ",
        "pas de geleerde regel toe op deze situatie. ",
        "kies zelfstandig de meest juiste handeling of uitleg. ",
        "denk aan het gevolg voordat je kiest. ",
        "gebruik zelfstandig wat je hebt geleerd. ",
        "controleer elke mogelijkheid voordat je kiest. ",
        "leg de regel eerst in gedachten uit en kies daarna. ",
        "écoute sans aide et choisis ensuite. ",
        "écoute toute la consigne avant de répondre. ",
        "écoute, réfléchis, puis choisis de façon autonome. ",
        "résous seul et vérifie ton résultat. ",
        "calcule sans exemple et contrôle chaque étape. ",
        "réfléchis d'abord à la stratégie, puis calcule. ",
        "applique la règle apprise à cette situation. ",
        "choisis seul l'action ou l'explication la plus juste. ",
        "réfléchis à la conséquence avant de choisir. ",
        "utilise seul ce que tu as appris. ",
        "vérifie chaque possibilité avant de choisir. ",
        "explique-toi d'abord la règle, puis choisis. ",
        "listen without help, then choose. ",
        "listen to the full instruction before answering. ",
        "listen, think, and then choose independently. ",
        "solve independently and check your result. ",
        "calculate without an example and check each step. ",
        "think about the strategy first, then calculate. ",
        "apply the rule you learned to this situation. ",
        "choose the most accurate action or explanation independently. ",
        "think about the consequence before choosing. ",
        "use what you learned independently. ",
        "check every possibility before choosing. ",
        "explain the rule to yourself first, then choose. "
    ).map(::normalize)
}

/** The only runtime dispatch point for v2 CURATED and GENERATED exercise sets. */
class LessonExerciseResolverV2(
    private val curatedProvider: CuratedLessonExerciseProviderV2,
    private val generatedProvider: GeneratedLessonExerciseProviderV2 = ExistingGeneratorAdapterV2()
) {
    suspend fun resolve(
        set: LessonExerciseSetV2,
        context: LessonResolveContextV2
    ): LessonExerciseResolutionV2 = when (set.sourceMode) {
        LessonSourceModeV2.GENERATED -> generatedProvider.generate(set, context)
        LessonSourceModeV2.CURATED -> {
            val resolved = set.curatedExerciseIds.map { it to curatedProvider.getByStableId(it) }
            val missing = resolved.filter { it.second == null }.map { it.first }
            if (missing.isNotEmpty()) {
                LessonExerciseResolutionV2.Rejected("Curated lesson content is incomplete.", missing)
            } else {
                LessonExerciseResolutionV2.Success(
                    ResolvedLessonExerciseSetV2(
                        setId = set.id,
                        sourceMode = set.sourceMode,
                        exercises = resolved.map { requireNotNull(it.second) }
                    )
                )
            }
        }
    }
}
