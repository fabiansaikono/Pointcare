package be.saikono.poincare.domain.curriculum.generation

import be.saikono.poincare.model.ExerciseContent
import be.saikono.poincare.model.ExerciseSection
import be.saikono.poincare.model.InputMode
import be.saikono.poincare.model.LearningDomain
import be.saikono.poincare.model.LocalizedText
import be.saikono.poincare.model.SkillLevel

/** Deterministic tracing material for the canonical Grade 1 handwriting lessons. */
object Grade1LetterTracingGenerator : LessonExerciseGenerator {
    override val generatorId: String = "generator.g1.writing.letter_tracing.core"
    override val generatorVersion: Int = 1

    private val allowedParameterKeys = setOf("grade", "phase", "symbols", "editorial_status")

    override fun validate(parameters: Map<String, String>): List<String> = buildList {
        val unknown = parameters.keys - allowedParameterKeys
        if (unknown.isNotEmpty()) add("unsupported parameters: ${unknown.sorted().joinToString()}")
        if (parameters["grade"] != "1") add("grade must be 1")
        if (parameters["phase"] !in setOf("theory", "practice", "test")) {
            add("phase must be theory, practice or test")
        }
        if (symbols(parameters).isEmpty()) add("symbols must contain at least one trace target")
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
        val targets = symbols(parameters)
        val phase = parameters.getValue("phase")
        return List(exerciseCount) { index ->
            val symbol = targets[index % targets.size]
            val labelNl = if (symbol.length == 1 && symbol[0].isLetter()) "letter $symbol" else "vorm $symbol"
            ExerciseContent(
                id = "generated.g1.writing.letter_tracing.v1.${seed.fingerprint}.${index + 1}",
                domain = LearningDomain.WRITING,
                skill = "letter_tracing",
                section = if (phase == "test") ExerciseSection.TESTS else ExerciseSection.PRACTICE,
                level = SkillLevel.FIRST_GRADE,
                inputMode = InputMode.TRACE_LETTER,
                prompt = LocalizedText(
                    nl = "Trek de $labelNl na.",
                    fr = "Repasse ${if (symbol[0].isLetter()) "la lettre" else "la forme"} $symbol.",
                    en = "Trace the ${if (symbol[0].isLetter()) "letter" else "shape"} $symbol."
                ),
                speechText = LocalizedText(
                    nl = "Trek ${if (symbol[0].isLetter()) "de letter $symbol" else "de vorm"} na.",
                    fr = "Repasse ${if (symbol[0].isLetter()) "la lettre $symbol" else "la forme"}.",
                    en = "Trace ${if (symbol[0].isLetter()) "the letter $symbol" else "the shape"}."
                ),
                data = linkedMapOf(
                    "generatorId" to generatorId,
                    "generatorVersion" to generatorVersion.toString(),
                    "seedFingerprint" to seed.fingerprint,
                    "phase" to phase,
                    "letter" to symbol,
                    "traceTargetIndex" to index.toString(),
                    "subcategoryId" to "letter_tracing",
                    "masteryTag" to "writing.letter_tracing.$symbol"
                ),
                answer = "completed"
            )
        }
    }

    private fun symbols(parameters: Map<String, String>): List<String> = parameters["symbols"]
        .orEmpty()
        .split('|')
        .map(String::trim)
        .filter(String::isNotEmpty)
}
