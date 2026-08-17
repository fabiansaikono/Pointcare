package be.saikono.poincare.ui.lesson

import be.saikono.poincare.domain.curriculum.generation.GeneratedExerciseContentValidator
import be.saikono.poincare.domain.curriculum.v2.CuratedLessonExerciseProviderV2
import be.saikono.poincare.domain.curriculum.v2.ExerciseContentSignatureV2
import be.saikono.poincare.domain.curriculum.v2.LessonCatalogV2Loader
import be.saikono.poincare.domain.curriculum.v2.LessonDefinitionV2
import be.saikono.poincare.domain.curriculum.v2.LessonExerciseResolutionV2
import be.saikono.poincare.domain.curriculum.v2.LessonExerciseResolverV2
import be.saikono.poincare.domain.curriculum.v2.LessonResolveContextV2
import be.saikono.poincare.domain.curriculum.v2.LessonStageTypeV2
import be.saikono.poincare.domain.curriculum.v2.ResolvedExerciseContractValidatorV2
import be.saikono.poincare.model.ExerciseContent
import be.saikono.poincare.model.InputMode
import be.saikono.poincare.model.LearningDomain
import be.saikono.poincare.model.LocalizedText
import be.saikono.poincare.model.SkillLevel
import java.io.File
import java.security.MessageDigest
import java.time.Instant
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.encodeToStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Exports the requested V2 grades as the runtime resolves them.
 *
 * Unlike the earlier snapshot, this walks every lesson sequentially and passes
 * practice signatures into the assessment resolver. Theory, visuals, all three
 * locales, speech, mastery/error metadata, hints, feedback and signatures stay
 * visible to the external audit.
 */
@OptIn(ExperimentalSerializationApi::class)
class LessonV2ValidationExportTest {
    private val json = Json {
        encodeDefaults = true
        explicitNulls = false
        prettyPrint = false
    }

    @Test
    fun exportValidationExercisesWhenRequested() = runBlocking {
        val requestedDirectory = (
            System.getenv(EXPORT_DIRECTORY_ENV)
                ?: System.getProperty(EXPORT_DIRECTORY_ENV)
            )?.takeIf { it.isNotBlank() }
            ?: return@runBlocking
        val outputDirectory = File(requestedDirectory).apply { mkdirs() }
        val requestedGrades = (System.getenv(EXPORT_GRADES_ENV) ?: System.getProperty(EXPORT_GRADES_ENV))
            ?.split(',')
            ?.mapNotNull { it.trim().toIntOrNull() }
            ?.toSet()
            ?.takeIf { it.isNotEmpty() }
            ?: setOf(1)
        val catalog = LessonCatalogV2Loader.fromFile(catalogFile())
        val lessons = catalog.lessons
            .filter { it.grade in requestedGrades }
            .sortedWith(compareBy(
                { it.legacyRouting.domain.name },
                { it.legacyRouting.subcategoryId },
                { it.sequence },
                { it.id }
            ))
        val resolver = LessonExerciseResolverV2(
            curatedProvider = curatedExerciseProvider()
        )
        val snapshots = mutableListOf<ValidationExerciseSetSnapshot>()
        val contentIssues = mutableListOf<String>()
        var practiceAssessmentOverlapCount = 0
        var duplicateAssessmentSignatureCount = 0
        var missingPromptLocaleCount = 0
        var missingRequiredSpeechLocaleCount = 0
        var missingMasteryTagCount = 0
        var missingErrorTagCount = 0
        var missingFeedbackLocaleCount = 0

        lessons.forEach { lesson ->
            val seenSignatures = linkedSetOf<String>()
            lesson.stages.sortedBy { it.sequence }.forEach { stage ->
                stage.exerciseSets.forEach { set ->
                    val resolution = resolver.resolve(
                        set,
                        LessonResolveContextV2(
                            profileId = AUDIT_PROFILE_ID,
                            unitId = lesson.unitId,
                            lessonId = lesson.id,
                            stageId = stage.id,
                            attemptId = "validation::${lesson.id}::v2",
                            canonicalDomain = LearningDomain.valueOf(lesson.legacyRouting.domain.name),
                            canonicalSubcategoryId = lesson.legacyRouting.subcategoryId,
                            excludedExerciseSignatures = seenSignatures
                        )
                    )
                    val success = (resolution as? LessonExerciseResolutionV2.Success)?.value
                        ?: error("Generation rejected for ${lesson.id}/${stage.id}/${set.id}: $resolution")
                    val signatures = success.exercises.map(ExerciseContentSignatureV2::of)

                    if (stage.type == LessonStageTypeV2.MASTERY_TEST) {
                        practiceAssessmentOverlapCount += signatures.count { it in seenSignatures }
                        duplicateAssessmentSignatureCount += signatures.size - signatures.distinct().size
                    }
                    if (stage.type != LessonStageTypeV2.THEORY) seenSignatures += signatures

                    success.exercises.forEachIndexed { index, exercise ->
                        GeneratedExerciseContentValidator.validate(exercise).forEach { issue ->
                            contentIssues += "${lesson.id}/${set.id}/$index: $issue"
                        }
                        ResolvedExerciseContractValidatorV2.validate(exercise).forEach { issue ->
                            contentIssues += "${lesson.id}/${set.id}/$index: $issue"
                        }
                        missingPromptLocaleCount += listOf(
                            exercise.prompt.nl,
                            exercise.prompt.fr.orEmpty(),
                            exercise.prompt.en.orEmpty()
                        ).count(String::isBlank)
                        if (
                            exercise.inputMode == InputMode.LISTEN_AND_SELECT ||
                            exercise.data["audioRequired"] == "true"
                        ) {
                            missingRequiredSpeechLocaleCount += listOf(
                                exercise.speechText?.nl.orEmpty(),
                                exercise.speechText?.fr.orEmpty(),
                                exercise.speechText?.en.orEmpty()
                            ).count(String::isBlank)
                        }
                        if (
                            exercise.data["masteryTag"].isNullOrBlank() &&
                            set.masteryTags.isEmpty()
                        ) {
                            missingMasteryTagCount++
                        }
                        if (exercise.data["errorTags"].isNullOrBlank()) missingErrorTagCount++
                        missingFeedbackLocaleCount += REQUIRED_LOCALES.count { locale ->
                            exercise.data["feedback_$locale"].isNullOrBlank()
                        }
                    }

                    snapshots += ValidationExerciseSetSnapshot(
                        lessonId = lesson.id,
                        stageId = stage.id,
                        stageType = stage.type.name,
                        seriesIndex = stage.seriesIndex,
                        setId = set.id,
                        purpose = set.purpose.name,
                        feedbackPolicy = set.feedbackPolicy.name,
                        wrongAnswersAllowed = set.parameters["wrong answers allowed"]?.toIntOrNull(),
                        generatorId = set.generatorId ?: "curated.v1",
                        generatorVersion = set.generatorImplementationVersion ?: 1,
                        generatorSeed = success.generatorSeed,
                        seedFingerprint = success.seedFingerprint,
                        setMasteryTags = set.masteryTags,
                        representationIds = set.representationIds,
                        visualAssetIds = lesson.visuals.map { it.id },
                        exerciseSignatures = signatures,
                        exercises = success.exercises
                    )
                }
            }
        }

        val missingVisualAssetFiles = lessons.flatMap { lesson ->
            lesson.visuals.flatMap { visual ->
                listOfNotNull(visual.assetPathPng, visual.assetPathSvg)
            }
        }.filterNot { File(assetRoot(), it).isFile }
        val visualAssets = lessons.flatMap { lesson ->
            lesson.visuals.flatMap { visual ->
                val stageIds = lesson.stages.filter { stage ->
                    stage.contentBlocks.any { visual.id in it.visualIds }
                }.map { it.id }
                val setIds = snapshots.filter { snapshot ->
                    snapshot.lessonId == lesson.id && visual.representationId in snapshot.representationIds
                }.map { it.setId }
                listOfNotNull(visual.assetPathPng, visual.assetPathSvg).map { relativePath ->
                    visualSnapshot(lesson, visual, relativePath, stageIds, setIds)
                }
            }
        }

        fun issueCount(fragment: String): Int = contentIssues.count { fragment in it }

        val bundle = ValidationExerciseExportBundle(
            generatedAtUtc = Instant.now().toString(),
            catalogDefinitionVersion = catalog.definitionVersion,
            lessonCount = lessons.size,
            exerciseSetCount = snapshots.size,
            exerciseCount = snapshots.sumOf { it.exercises.size },
            deterministicProfileId = AUDIT_PROFILE_ID,
            variantCaveat = "One deterministic sequential lesson attempt is exported. Assessment sets exclude signatures seen earlier in that same attempt.",
            auditSummary = ValidationAuditSummary(
                practiceAssessmentOverlapCount = practiceAssessmentOverlapCount,
                duplicateAssessmentSignatureCount = duplicateAssessmentSignatureCount,
                contentContractIssueCount = contentIssues.size,
                missingPromptLocaleCount = missingPromptLocaleCount,
                missingRequiredSpeechLocaleCount = missingRequiredSpeechLocaleCount,
                missingVisualAssetFileCount = missingVisualAssetFiles.size,
                missingMasteryTagCount = missingMasteryTagCount,
                missingErrorTagCount = missingErrorTagCount,
                missingFeedbackLocaleCount = missingFeedbackLocaleCount,
                substantiveUntranslatedCount = issueCount("substantively untranslated"),
                genericPlaceholderCount = issueCount("generic placeholder"),
                forbiddenSpeechNonwordCount = issueCount("unsafe TTS nonword"),
                masteryTestHintCount = issueCount("mastery-test item contains hint"),
                missingRepresentationCount = issueCount("has no representationId"),
                missingHybridEvidenceCount = issueCount("adult-mediated item is missing"),
                wrongSectionTypeCount = issueCount("labelled as TESTS"),
                invalidVisualMetadataCount = visualAssets.count { !it.paletteValid || it.sha256.isBlank() || it.width == null || it.height == null }
            ),
            missingVisualAssetFiles = missingVisualAssetFiles,
            contentContractIssues = contentIssues,
            visualAssets = visualAssets,
            lessons = lessons,
            sets = snapshots
        )
        val outputFileName = if (requestedGrades == setOf(1)) OUTPUT_FILE_NAME else {
            "grades-${requestedGrades.sorted().joinToString("-")}-v2-validation-exercises.json"
        }
        val output = File(outputDirectory, outputFileName)
        output.outputStream().buffered().use { stream -> json.encodeToStream(bundle, stream) }

        assertTrue(output.isFile && output.length() > 0)
        if (requestedGrades == setOf(1)) {
            assertEquals(67, bundle.lessonCount)
            assertEquals(402, bundle.exerciseSetCount)
            assertEquals(2_217, bundle.exerciseCount)
        }
        if (requestedGrades == setOf(1)) {
            assertEquals(0, bundle.auditSummary.practiceAssessmentOverlapCount)
            assertEquals(0, bundle.auditSummary.duplicateAssessmentSignatureCount)
        }
        if (requestedGrades == setOf(1)) {
            assertEquals(0, bundle.auditSummary.contentContractIssueCount)
            assertEquals(0, bundle.auditSummary.missingPromptLocaleCount)
            assertEquals(0, bundle.auditSummary.missingRequiredSpeechLocaleCount)
            assertEquals(0, bundle.auditSummary.missingVisualAssetFileCount)
            assertEquals(0, bundle.auditSummary.substantiveUntranslatedCount)
            assertEquals(0, bundle.auditSummary.genericPlaceholderCount)
            assertEquals(0, bundle.auditSummary.forbiddenSpeechNonwordCount)
            assertEquals(0, bundle.auditSummary.masteryTestHintCount)
            assertEquals(0, bundle.auditSummary.missingRepresentationCount)
            assertEquals(0, bundle.auditSummary.missingHybridEvidenceCount)
            assertEquals(0, bundle.auditSummary.wrongSectionTypeCount)
            assertEquals(0, bundle.auditSummary.invalidVisualMetadataCount)
        }
    }

    private fun visualSnapshot(
        lesson: LessonDefinitionV2,
        visual: be.saikono.poincare.domain.curriculum.v2.LessonVisualV2,
        relativePath: String,
        stageIds: List<String>,
        setIds: List<String>
    ): ValidationVisualAssetSnapshot {
        val file = File(assetRoot(), relativePath)
        val isPng = relativePath.endsWith(".png", ignoreCase = true)
        val pngBytes = if (isPng && file.isFile) file.readBytes() else byteArrayOf()
        val pngWidth = pngBytes.pngInt(16)
        val pngHeight = pngBytes.pngInt(20)
        val pngHasAlpha = pngBytes.size > 25 && (pngBytes[25].toInt() and 0xFF) in setOf(4, 6)
        val svgText = if (!isPng && file.isFile) file.readText(Charsets.UTF_8) else ""
        val svgWidth = Regex("""\bwidth=[\"'](\d+)""").find(svgText)?.groupValues?.get(1)?.toIntOrNull()
        val svgHeight = Regex("""\bheight=[\"'](\d+)""").find(svgText)?.groupValues?.get(1)?.toIntOrNull()
        val colours = Regex("#[0-9A-Fa-f]{6}").findAll(svgText).map { it.value.uppercase() }.toSet()
        val palettePolicy = visual.palettePolicy?.name ?: "UNSPECIFIED"
        val allowed = when (palettePolicy) {
            "PLANET_GOLD_WHITE_ONLY" -> setOf("#FFD700", "#FFFFFF")
            "POINCARE_TRICOLOUR" -> setOf("#FFD700", "#FFFFFF", "#4FC3F7")
            else -> colours
        }
        val paletteValid = if (isPng) pngHasAlpha else colours.all { it in allowed }
        return ValidationVisualAssetSnapshot(
            lessonId = lesson.id,
            visualId = visual.id,
            representationId = visual.representationId,
            assetPath = relativePath,
            mimeType = if (isPng) "image/png" else "image/svg+xml",
            width = pngWidth ?: svgWidth,
            height = pngHeight ?: svgHeight,
            sha256 = if (file.isFile) file.sha256() else "",
            altText = visual.altText,
            stageIds = stageIds,
            exerciseSetIds = setIds,
            palettePolicy = palettePolicy,
            observedSvgColours = colours.sorted(),
            transparentBackground = visual.transparentBackground,
            paletteValid = paletteValid
        )
    }

    private fun File.sha256(): String = MessageDigest.getInstance("SHA-256")
        .digest(readBytes())
        .joinToString("") { byte -> "%02x".format(byte) }

    private fun ByteArray.pngInt(offset: Int): Int? {
        if (size < offset + 4) return null
        return ((this[offset].toInt() and 0xFF) shl 24) or
            ((this[offset + 1].toInt() and 0xFF) shl 16) or
            ((this[offset + 2].toInt() and 0xFF) shl 8) or
            (this[offset + 3].toInt() and 0xFF)
    }

    private fun catalogFile(): File = File(assetRoot(), LessonCatalogV2Loader.ASSET_PATH)
        .takeIf(File::isFile)
        ?: error("Missing v2 catalog asset: ${LessonCatalogV2Loader.ASSET_PATH}")

    private fun curatedExerciseProvider(): CuratedLessonExerciseProviderV2 {
        val auditFile = listOf(
            File("deliverables/lesson-hierarchy-source/poincare-question-audit.json"),
            File("../deliverables/lesson-hierarchy-source/poincare-question-audit.json")
        ).firstOrNull(File::isFile)
            ?: return CuratedLessonExerciseProviderV2 { null }
        val questions = json.parseToJsonElement(auditFile.readText(Charsets.UTF_8))
            .jsonObject["xmlQuestions"]
            ?.jsonArray
            .orEmpty()
        val byId = questions.associateNotNull { element ->
            val item = element.jsonObject
            val id = item.text("exerciseId").takeIf(String::isNotBlank) ?: return@associateNotNull null
            val grade = item["grade"]?.jsonPrimitive?.intOrNull ?: 1
            val domain = runCatching { LearningDomain.valueOf(item.text("domain")) }
                .getOrDefault(LearningDomain.MATH)
            val optionsNl = item.text("optionsNl").options()
            val answerNl = item.text("correctAnswerNl")
            val inputType = item.text("inputType").lowercase()
            val audioRequired = inputType in setOf("audio_choice", "audio_translation_choice", "listen_and_select")
            val skill = sequenceOf(item.text("skillRef"), item.text("topic"), item.text("subcategoryId"))
                .firstOrNull(String::isNotBlank)
                .orEmpty()
            id to ExerciseContent(
                id = id,
                domain = domain,
                skill = skill,
                section = be.saikono.poincare.model.ExerciseSection.EXERCISES,
                level = when (grade) {
                    1 -> SkillLevel.FIRST_GRADE
                    2 -> SkillLevel.SECOND_GRADE
                    3 -> SkillLevel.THIRD_GRADE
                    4 -> SkillLevel.FOURTH_GRADE
                    5 -> SkillLevel.FIFTH_GRADE
                    6 -> SkillLevel.SIXTH_GRADE
                    else -> SkillLevel.NOT_APPLICABLE
                },
                inputMode = when (inputType) {
                    "open_numeric", "custom_number_pad" -> InputMode.CUSTOM_NUMBER_PAD
                    "audio_choice", "audio_translation_choice", "listen_and_select" -> InputMode.LISTEN_AND_SELECT
                    "trace_letter" -> InputMode.TRACE_LETTER
                    else -> InputMode.TAP_CHOICE
                },
                prompt = LocalizedText(
                    nl = item.text("promptNl")
                ),
                speechText = LocalizedText(
                    nl = item.text("promptNl")
                ),
                data = buildMap {
                    put("options_nl-BE", optionsNl.joinToString("\n"))
                    put("answer_nl-BE", answerNl)
                    if (audioRequired) put("optionSpeech_nl-BE", optionsNl.joinToString("\n"))
                    put("masteryTag", skill.ifBlank { item.text("subcategoryId") })
                    put("errorTags", "curated.v1")
                    put("feedback_nl-BE", "Juist antwoord: $answerNl")
                    put("exerciseLocales", "nl-BE")
                    put("sourceVersion", "V1")
                    put("exerciseType", item.text("inputType"))
                },
                answer = answerNl
            )
        }
        return CuratedLessonExerciseProviderV2 { exerciseId -> byId[exerciseId] }
    }

    private fun Map<String, kotlinx.serialization.json.JsonElement>.text(key: String): String =
        this[key]?.jsonPrimitive?.contentOrNull.orEmpty()

    private fun String.options(): List<String> = split('|')
        .map(String::trim)
        .filter(String::isNotBlank)

    private inline fun <T, K, V> Iterable<T>.associateNotNull(transform: (T) -> Pair<K, V>?): Map<K, V> =
        buildMap { for (element in this@associateNotNull) transform(element)?.let { put(it.first, it.second) } }

    private fun assetRoot(): File = listOf(
        File("src/main/assets"),
        File("app/src/main/assets")
    ).firstOrNull(File::isDirectory)
        ?: error("Missing Android asset root.")

    private companion object {
        const val EXPORT_DIRECTORY_ENV = "POINCARE_V2_VALIDATION_EXPORT_DIR"
        const val EXPORT_GRADES_ENV = "POINCARE_V2_VALIDATION_GRADES"
        const val OUTPUT_FILE_NAME = "grade1-v2-validation-exercises.json"
        const val AUDIT_PROFILE_ID = "manual-grade1-v2-validation"
        val REQUIRED_LOCALES = listOf("nl-BE", "fr-BE", "en-US")
    }
}

@Serializable
private data class ValidationExerciseExportBundle(
    val generatedAtUtc: String,
    val catalogDefinitionVersion: Int,
    val lessonCount: Int,
    val exerciseSetCount: Int,
    val exerciseCount: Int,
    val deterministicProfileId: String,
    val variantCaveat: String,
    val auditSummary: ValidationAuditSummary,
    val missingVisualAssetFiles: List<String>,
    val contentContractIssues: List<String>,
    val visualAssets: List<ValidationVisualAssetSnapshot>,
    val lessons: List<LessonDefinitionV2>,
    val sets: List<ValidationExerciseSetSnapshot>
)

@Serializable
private data class ValidationAuditSummary(
    val practiceAssessmentOverlapCount: Int,
    val duplicateAssessmentSignatureCount: Int,
    val contentContractIssueCount: Int,
    val missingPromptLocaleCount: Int,
    val missingRequiredSpeechLocaleCount: Int,
    val missingVisualAssetFileCount: Int,
    val missingMasteryTagCount: Int,
    val missingErrorTagCount: Int,
    val missingFeedbackLocaleCount: Int,
    val substantiveUntranslatedCount: Int,
    val genericPlaceholderCount: Int,
    val forbiddenSpeechNonwordCount: Int,
    val masteryTestHintCount: Int,
    val missingRepresentationCount: Int,
    val missingHybridEvidenceCount: Int,
    val wrongSectionTypeCount: Int,
    val invalidVisualMetadataCount: Int
)

@Serializable
private data class ValidationVisualAssetSnapshot(
    val lessonId: String,
    val visualId: String,
    val representationId: String? = null,
    val assetPath: String,
    val mimeType: String,
    val width: Int? = null,
    val height: Int? = null,
    val sha256: String,
    val altText: Map<String, String>,
    val stageIds: List<String>,
    val exerciseSetIds: List<String>,
    val palettePolicy: String,
    val observedSvgColours: List<String>,
    val transparentBackground: Boolean,
    val paletteValid: Boolean
)

@Serializable
private data class ValidationExerciseSetSnapshot(
    val lessonId: String,
    val stageId: String,
    val stageType: String,
    val seriesIndex: Int? = null,
    val setId: String,
    val purpose: String,
    val feedbackPolicy: String,
    val wrongAnswersAllowed: Int? = null,
    val generatorId: String,
    val generatorVersion: Int,
    val generatorSeed: Long? = null,
    val seedFingerprint: String? = null,
    val setMasteryTags: List<String>,
    val representationIds: List<String>,
    val visualAssetIds: List<String>,
    val exerciseSignatures: List<String>,
    val exercises: List<ExerciseContent>
)
