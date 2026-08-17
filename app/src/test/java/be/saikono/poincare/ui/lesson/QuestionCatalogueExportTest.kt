package be.saikono.poincare.ui.lesson

import be.saikono.poincare.data.content.PoincareContentPack
import be.saikono.poincare.data.content.PoincareContentParser
import be.saikono.poincare.data.content.PoincareExercise
import be.saikono.poincare.domain.curriculum.CurriculumUnit
import be.saikono.poincare.domain.curriculum.LessonActivitySource
import be.saikono.poincare.domain.curriculum.LessonDefinition
import be.saikono.poincare.domain.curriculum.LessonSection
import be.saikono.poincare.domain.curriculum.generation.LessonGenerationContext
import be.saikono.poincare.domain.curriculum.generation.LessonGenerationResult
import be.saikono.poincare.domain.curriculum.generation.LessonGeneratorRegistry
import be.saikono.poincare.domain.curriculum.manifest.CurriculumManifest
import be.saikono.poincare.domain.curriculum.manifest.CurriculumManifestLoader
import be.saikono.poincare.model.ExerciseContent
import be.saikono.poincare.model.LearningDomain
import be.saikono.poincare.model.SubcategoryCatalog
import be.saikono.poincare.model.localizedAnswer
import be.saikono.poincare.model.localizedOptions
import be.saikono.poincare.ui.home.getSubCategoriesForDomain
import be.saikono.poincare.ui.home.localizedSubcategoryName
import java.io.File
import java.time.Instant
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Writes a complete, reproducible audit snapshot when POINCARE_EXPORT_DIR is set.
 *
 * Stored XML questions are exported once. Generated lesson sections are materialized
 * with one stable audit profile and attempt. The generator contract is exported with
 * every sample row because the possible generated variants are intentionally unbounded.
 */
class QuestionCatalogueExportTest {
    private val json = Json {
        encodeDefaults = true
        explicitNulls = false
        prettyPrint = false
    }
    private val parser = PoincareContentParser()
    private val registry = LessonGeneratorRegistry.default()

    @Test
    fun exportCompleteQuestionAndLessonMappingWhenRequested() {
        val requestedDirectory = System.getenv(EXPORT_DIRECTORY_ENV)?.takeIf { it.isNotBlank() }
            ?: return
        val outputDirectory = File(requestedDirectory).apply { mkdirs() }
        val manifest = manifest()
        val xmlRecords = XML_FILES.flatMap { fileName ->
            val file = assetFile("src/main/assets/poincare/content/$fileName")
            val pack = file.inputStream().use(parser::parse)
            pack.exercises.map { XmlRecord(fileName, pack, it) }
        }
        val xmlById = xmlRecords.associateBy { it.exercise.id }
        val questions = mutableListOf<QuestionExportRow>()
        val sections = mutableListOf<SectionExportRow>()
        val lessons = mutableListOf<LessonExportRow>()
        val practical = mutableListOf<PracticalExportRow>()
        val issues = mutableListOf<MappingIssueRow>()
        val curatedLinks = linkedMapOf<String, MutableList<String>>()

        manifest.lessonCatalog.units
            .sortedWith(compareBy<CurriculumUnit>({ it.grades.minOrNull() ?: 0 }, { it.domain.name }, { it.id }))
            .forEach { unit ->
                val roadmap = manifest.roadmapEntries.firstOrNull { it.unitId == unit.id }
                unit.lessons.sortedBy { it.sequence }.forEach { lesson ->
                    val tileRefs = tileReferences(manifest, unit, lesson)
                    if (tileRefs.size != 1) {
                        issues += MappingIssueRow(
                            severity = "ERROR",
                            issueType = "LESSON_TILE_CARDINALITY",
                            itemId = lesson.id,
                            details = "Expected one tile, found ${tileRefs.joinToString("|") { it.compositeId }}."
                        )
                    }
                    val canonicalTile = tileRefs.singleOrNull()
                    val questionStart = questions.size
                    lesson.sections.forEach { section ->
                        when (val source = section.activitySource) {
                            null -> sections += sectionRow(unit, lesson, section, tileRefs, "NONE", 0)
                            is LessonActivitySource.Generated -> {
                                val generatedCount = appendGeneratedQuestions(
                                    manifest = manifest,
                                    unit = unit,
                                    lesson = lesson,
                                    section = section,
                                    source = source,
                                    tileRefs = tileRefs,
                                    sourceType = "GENERATED_SAMPLE",
                                    questions = questions,
                                    issues = issues
                                )
                                sections += sectionRow(
                                    unit, lesson, section, tileRefs, "GENERATED",
                                    generatedCount, source.generatorId, source.generatorVersion,
                                    source.seedPolicy.name, source.parameters
                                )
                            }
                            is LessonActivitySource.Curated -> {
                                val count = appendCuratedQuestions(
                                    unit, lesson, section, tileRefs, source.exerciseIds,
                                    xmlById, questions, curatedLinks, issues, "CURATED_XML"
                                )
                                sections += sectionRow(
                                    unit, lesson, section, tileRefs, "CURATED",
                                    count, sourceReference = source.exerciseIds.joinToString("|")
                                )
                            }
                            is LessonActivitySource.Hybrid -> {
                                val generatedSource = LessonActivitySource.Generated(
                                    generatorId = source.generatorId,
                                    generatorVersion = source.generatorVersion,
                                    exerciseCount = source.generatedExerciseCount,
                                    seedPolicy = source.seedPolicy,
                                    parameters = source.parameters
                                )
                                val generatedCount = appendGeneratedQuestions(
                                    manifest, unit, lesson, section, generatedSource, tileRefs,
                                    "HYBRID_GENERATED_SAMPLE", questions, issues
                                )
                                val curatedCount = appendCuratedQuestions(
                                    unit, lesson, section, tileRefs, source.curatedExerciseIds,
                                    xmlById, questions, curatedLinks, issues, "HYBRID_CURATED_XML"
                                )
                                sections += sectionRow(
                                    unit, lesson, section, tileRefs, "HYBRID",
                                    generatedCount + curatedCount, source.generatorId,
                                    source.generatorVersion, source.seedPolicy.name, source.parameters,
                                    source.curatedExerciseIds.joinToString("|")
                                )
                            }
                            is LessonActivitySource.Practical -> {
                                practical += PracticalExportRow(
                                    activityId = source.activityId,
                                    unitId = unit.id,
                                    lessonId = lesson.id,
                                    lessonTitleNl = lesson.title.visibleFor("nl-BE"),
                                    grade = lesson.grade,
                                    domain = canonicalTile?.domain?.name ?: unit.domain.name,
                                    canonicalSubcategoryId = canonicalTile?.tileId ?: unit.subcategoryId,
                                    tiles = tileRefs.joinToString("|") { it.compositeId },
                                    instructionsNl = source.instructions.visibleFor("nl-BE"),
                                    instructionsFr = source.instructions.visibleFor("fr-BE"),
                                    instructionsEn = source.instructions.visibleFor("en-US"),
                                    requiresAdultVerification = source.requiresAdultVerification
                                )
                                sections += sectionRow(
                                    unit, lesson, section, tileRefs, "PRACTICAL", 0,
                                    sourceReference = source.activityId
                                )
                            }
                        }
                    }
                    val lessonQuestionCount = questions.size - questionStart
                    lessons += LessonExportRow(
                        unitId = unit.id,
                        lessonId = lesson.id,
                        lessonSequence = lesson.sequence,
                        grade = lesson.grade,
                        domain = canonicalTile?.domain?.name ?: unit.domain.name,
                        canonicalSubcategoryId = canonicalTile?.tileId ?: unit.subcategoryId,
                        tiles = tileRefs.joinToString("|") { it.compositeId },
                        tileTitlesNl = tileRefs.joinToString("|") { it.titleNl },
                        lessonTitleNl = lesson.title.visibleFor("nl-BE"),
                        lessonTitleFr = lesson.title.visibleFor("fr-BE"),
                        lessonTitleEn = lesson.title.visibleFor("en-US"),
                        summaryNl = lesson.summary?.visibleFor("nl-BE").orEmpty(),
                        estimatedMinutes = lesson.estimatedMinutes,
                        requirement = lesson.requirement.name,
                        availability = lesson.availability.name,
                        parentSupport = lesson.parentSupport.name,
                        questionCountInAuditAttempt = lessonQuestionCount,
                        sectionCount = lesson.sections.size,
                        practicalActivityCount = lesson.sections.count {
                            it.activitySource is LessonActivitySource.Practical
                        },
                        learningGoalIds = lesson.learningGoalIds.joinToString("|"),
                        prerequisiteIds = lesson.prerequisites.joinToString("|") { it.targetId },
                        minimumCorrectRatio = lesson.mastery.minimumCorrectRatio,
                        minimumAttempts = lesson.mastery.minimumAttempts,
                        sourceReference = roadmap?.sourceReference.orEmpty(),
                        tags = lesson.tags.joinToString("|"),
                        definitionVersion = lesson.definitionVersion
                    )
                }
            }

        val xmlQuestions = xmlRecords.map { record ->
            val exercise = record.exercise
            val domain = domainFor(record)
            val tileRefs = extraExerciseTileReferences(domain, exercise.subcategoryId)
            if (tileRefs.isEmpty()) {
                issues += MappingIssueRow(
                    severity = "ERROR",
                    issueType = "XML_QUESTION_WITHOUT_TILE",
                    itemId = exercise.id,
                    details = "${record.fileName}; subcategory=${exercise.subcategoryId.orEmpty()}"
                )
            }
            val lessonRefs = curatedLinks[exercise.id].orEmpty()
            XmlQuestionExportRow(
                exerciseId = exercise.id,
                sourceFile = record.fileName,
                packId = record.pack.id.orEmpty(),
                domain = domain.name,
                subcategoryId = exercise.subcategoryId.orEmpty(),
                grade = exercise.grade,
                difficultyStars = exercise.difficultyStars,
                topic = exercise.topic.orEmpty(),
                skillRef = exercise.skillRef.orEmpty(),
                inputType = exercise.type.orEmpty(),
                contentType = exercise.contentType.orEmpty(),
                tiles = tileRefs.joinToString("|") { it.compositeId },
                tileTitlesNl = tileRefs.joinToString("|") { it.titleNl },
                lessonReferences = lessonRefs.joinToString("|"),
                mappingStatus = when {
                    tileRefs.isEmpty() -> "UNREACHABLE"
                    lessonRefs.isEmpty() -> "EXTRA_ONLY"
                    else -> "LESSON_AND_EXTRA"
                },
                promptNl = promptFor(exercise, "nl-BE"),
                promptFr = promptFor(exercise, "fr-BE"),
                promptEn = promptFor(exercise, "en-US"),
                correctAnswerNl = correctAnswerFor(exercise, "nl-BE"),
                correctAnswerFr = correctAnswerFor(exercise, "fr-BE"),
                correctAnswerEn = correctAnswerFor(exercise, "en-US"),
                optionsNl = optionsFor(exercise, "nl-BE"),
                optionsFr = optionsFor(exercise, "fr-BE"),
                optionsEn = optionsFor(exercise, "en-US"),
                tags = exercise.tags.joinToString("|")
            )
        }

        val bundle = QuestionCatalogueExportBundle(
            metadata = ExportMetadata(
                generatedAtUtc = Instant.now().toString(),
                manifestDefinitionVersion = manifest.definitionVersion,
                catalogueDefinitionVersion = manifest.lessonCatalog.definitionVersion,
                auditProfileId = AUDIT_PROFILE_ID,
                auditAttemptPattern = AUDIT_ATTEMPT_PATTERN,
                xmlQuestionCount = xmlQuestions.size,
                lessonCount = lessons.size,
                lessonQuestionSampleCount = questions.size,
                practicalActivityCount = practical.size,
                mappingIssueCount = issues.size,
                generatorCaveat = "Generated rows are one deterministic audit attempt; generator variants are intentionally unbounded."
            ),
            lessons = lessons,
            sections = sections,
            lessonQuestions = questions,
            xmlQuestions = xmlQuestions,
            practicalActivities = practical,
            mappingIssues = issues
        )
        val output = File(outputDirectory, OUTPUT_FILE_NAME)
        output.writeText(json.encodeToString(bundle), Charsets.UTF_8)
        assertTrue("Expected export file ${output.absolutePath}", output.isFile && output.length() > 0)
        assertTrue("Export contains mapping errors: ${issues.take(10)}", issues.none { it.severity == "ERROR" })
    }

    private fun appendGeneratedQuestions(
        manifest: CurriculumManifest,
        unit: CurriculumUnit,
        lesson: LessonDefinition,
        section: LessonSection,
        source: LessonActivitySource.Generated,
        tileRefs: List<TileReference>,
        sourceType: String,
        questions: MutableList<QuestionExportRow>,
        issues: MutableList<MappingIssueRow>
    ): Int {
        val attemptId = AUDIT_ATTEMPT_PATTERN
            .replace("{lessonId}", lesson.id)
            .replace("{sectionId}", section.id)
        return when (val result = registry.materialize(
            source,
            LessonGenerationContext(
                profileId = AUDIT_PROFILE_ID,
                unitId = unit.id,
                lessonId = lesson.id,
                sectionId = section.id,
                canonicalDomain = tileRefs.singleOrNull()?.domain,
                canonicalSubcategoryId = tileRefs.singleOrNull()?.tileId,
                attemptId = attemptId
            )
        )) {
            is LessonGenerationResult.Success -> {
                result.exercises.forEachIndexed { index, exercise ->
                    questions += generatedQuestionRow(
                        manifest, unit, lesson, section, tileRefs, sourceType,
                        source, result.seed.fingerprint, index + 1, exercise
                    )
                }
                result.exercises.size
            }
            is LessonGenerationResult.Rejected -> {
                issues += MappingIssueRow(
                    severity = "ERROR",
                    issueType = "GENERATOR_REJECTED",
                    itemId = "${lesson.id}/${section.id}",
                    details = "${result.code}: ${result.message}; ${result.details.joinToString()}"
                )
                0
            }
        }
    }

    private fun generatedQuestionRow(
        manifest: CurriculumManifest,
        unit: CurriculumUnit,
        lesson: LessonDefinition,
        section: LessonSection,
        tileRefs: List<TileReference>,
        sourceType: String,
        source: LessonActivitySource.Generated,
        seedFingerprint: String,
        position: Int,
        exercise: ExerciseContent
    ): QuestionExportRow {
        val roadmap = manifest.roadmapEntries.firstOrNull { it.unitId == unit.id }
        return QuestionExportRow(
            questionId = exercise.id,
            questionPosition = position,
            sourceType = sourceType,
            sourceReference = "${source.generatorId}@${source.generatorVersion}",
            sourceFile = "",
            generatorId = source.generatorId,
            generatorVersion = source.generatorVersion,
            seedPolicy = source.seedPolicy.name,
            seedFingerprint = seedFingerprint,
            generatorParameters = source.parameters.toSortedMap().entries.joinToString("|") { "${it.key}=${it.value}" },
            variantScope = "Fixed reproducible audit attempt; further deterministic variants are possible.",
            unitId = unit.id,
            lessonId = lesson.id,
            sectionId = section.id,
            sectionType = section.type.name,
            lessonTitleNl = lesson.title.visibleFor("nl-BE"),
            grade = lesson.grade,
            domain = tileRefs.singleOrNull()?.domain?.name ?: unit.domain.name,
            canonicalSubcategoryId = tileRefs.singleOrNull()?.tileId ?: unit.subcategoryId,
            tiles = tileRefs.joinToString("|") { it.compositeId },
            tileTitlesNl = tileRefs.joinToString("|") { it.titleNl },
            curriculumSourceReference = roadmap?.sourceReference.orEmpty(),
            inputMode = exercise.inputMode.name,
            promptNl = exercise.prompt.getRaw("nl-BE"),
            promptFr = exercise.prompt.getRaw("fr-BE"),
            promptEn = exercise.prompt.getRaw("en-US"),
            speechNl = exercise.speechText?.getRaw("nl-BE").orEmpty(),
            correctAnswerNl = exercise.localizedAnswer("nl-BE"),
            correctAnswerFr = exercise.localizedAnswer("fr-BE"),
            correctAnswerEn = exercise.localizedAnswer("en-US"),
            optionsNl = exercise.localizedOptions("nl-BE").joinToString(" | "),
            optionsFr = exercise.localizedOptions("fr-BE").joinToString(" | "),
            optionsEn = exercise.localizedOptions("en-US").joinToString(" | ")
        )
    }

    private fun appendCuratedQuestions(
        unit: CurriculumUnit,
        lesson: LessonDefinition,
        section: LessonSection,
        tileRefs: List<TileReference>,
        exerciseIds: List<String>,
        xmlById: Map<String, XmlRecord>,
        questions: MutableList<QuestionExportRow>,
        curatedLinks: MutableMap<String, MutableList<String>>,
        issues: MutableList<MappingIssueRow>,
        sourceType: String
    ): Int {
        var count = 0
        exerciseIds.forEachIndexed { index, exerciseId ->
            val record = xmlById[exerciseId]
            if (record == null) {
                issues += MappingIssueRow(
                    severity = "ERROR",
                    issueType = "MISSING_CURATED_XML_QUESTION",
                    itemId = exerciseId,
                    details = "${lesson.id}/${section.id}"
                )
                return@forEachIndexed
            }
            curatedLinks.getOrPut(exerciseId) { mutableListOf() } += "${lesson.id}/${section.id}"
            val exercise = record.exercise
            questions += QuestionExportRow(
                questionId = exercise.id,
                questionPosition = index + 1,
                sourceType = sourceType,
                sourceReference = record.fileName,
                sourceFile = record.fileName,
                generatorId = "",
                generatorVersion = null,
                seedPolicy = "",
                seedFingerprint = "",
                generatorParameters = "",
                variantScope = "Stored canonical XML question.",
                unitId = unit.id,
                lessonId = lesson.id,
                sectionId = section.id,
                sectionType = section.type.name,
                lessonTitleNl = lesson.title.visibleFor("nl-BE"),
                grade = lesson.grade,
                domain = tileRefs.singleOrNull()?.domain?.name ?: unit.domain.name,
                canonicalSubcategoryId = tileRefs.singleOrNull()?.tileId ?: unit.subcategoryId,
                tiles = tileRefs.joinToString("|") { it.compositeId },
                tileTitlesNl = tileRefs.joinToString("|") { it.titleNl },
                curriculumSourceReference = "",
                inputMode = exercise.type.orEmpty(),
                promptNl = promptFor(exercise, "nl-BE"),
                promptFr = promptFor(exercise, "fr-BE"),
                promptEn = promptFor(exercise, "en-US"),
                speechNl = exercise.promptTtsTexts["nl-BE"].orEmpty(),
                correctAnswerNl = correctAnswerFor(exercise, "nl-BE"),
                correctAnswerFr = correctAnswerFor(exercise, "fr-BE"),
                correctAnswerEn = correctAnswerFor(exercise, "en-US"),
                optionsNl = optionsFor(exercise, "nl-BE"),
                optionsFr = optionsFor(exercise, "fr-BE"),
                optionsEn = optionsFor(exercise, "en-US")
            )
            count += 1
        }
        return count
    }

    private fun sectionRow(
        unit: CurriculumUnit,
        lesson: LessonDefinition,
        section: LessonSection,
        tileRefs: List<TileReference>,
        sourceType: String,
        questionCount: Int,
        generatorId: String = "",
        generatorVersion: Int? = null,
        seedPolicy: String = "",
        parameters: Map<String, String> = emptyMap(),
        sourceReference: String = ""
    ) = SectionExportRow(
        unitId = unit.id,
        lessonId = lesson.id,
        sectionId = section.id,
        sectionType = section.type.name,
        sectionTitleNl = section.title?.visibleFor("nl-BE").orEmpty(),
        grade = lesson.grade,
        domain = tileRefs.singleOrNull()?.domain?.name ?: unit.domain.name,
        canonicalSubcategoryId = tileRefs.singleOrNull()?.tileId ?: unit.subcategoryId,
        tiles = tileRefs.joinToString("|") { it.compositeId },
        required = section.required,
        parentSupport = section.parentSupport.name,
        estimatedMinutes = section.estimatedMinutes,
        sourceType = sourceType,
        questionCountInAuditAttempt = questionCount,
        generatorId = generatorId,
        generatorVersion = generatorVersion,
        seedPolicy = seedPolicy,
        parameters = parameters.toSortedMap().entries.joinToString("|") { "${it.key}=${it.value}" },
        sourceReference = sourceReference
    )

    private fun tileReferences(
        manifest: CurriculumManifest,
        unit: CurriculumUnit,
        lesson: LessonDefinition
    ): List<TileReference> =
        LearningDomain.entries.flatMap { presentationDomain ->
            getSubCategoriesForDomain(presentationDomain.name).mapNotNull { tile ->
                tile.takeIf {
                    manifest.lessonMatchesLegacyTile(unit, lesson, presentationDomain, tile.id)
                }?.let {
                    TileReference(
                        domain = presentationDomain,
                        tileId = tile.id,
                        titleNl = localizedSubcategoryName(tile.id, "nl-BE") ?: tile.nameNl
                    )
                }
            }
        }.distinctBy { it.compositeId }

    private fun extraExerciseTileReferences(
        domain: LearningDomain,
        subcategoryId: String?
    ): List<TileReference> {
        val baseId = SubcategoryCatalog.baseId(subcategoryId)
        return getSubCategoriesForDomain(domain.name).mapNotNull { tile ->
            val directMatch = baseId == tile.id
            val optionMatch = tile.options.any { option ->
                baseId == option.targetId || baseId == option.id
            }
            tile.takeIf { directMatch || optionMatch }?.let {
                TileReference(domain, tile.id, localizedSubcategoryName(tile.id, "nl-BE") ?: tile.nameNl)
            }
        }
    }

    private fun domainFor(record: XmlRecord): LearningDomain {
        val exercise = record.exercise
        val category = exercise.mainCategory ?: record.pack.mainCategory
        return when (category?.lowercase()) {
            "wiskunde", "math", "mathematics" -> LearningDomain.MATH
            "language" -> if (
                SubcategoryCatalog.baseId(exercise.subcategoryId) in SubcategoryCatalog.writingBaseIds
            ) LearningDomain.WRITING else LearningDomain.READING
            "lezen", "reading" -> LearningDomain.READING
            "schrijven", "writing" -> LearningDomain.WRITING
            "puzzel", "puzzels", "puzzle", "logic-puzzles", "logic_puzzles" -> LearningDomain.PUZZLE
            "world-themes", "world_themes", "world-knowledge", "world_knowledge" -> LearningDomain.WORLD_KNOWLEDGE
            "geometry" -> LearningDomain.GEOMETRY
            else -> when (record.fileName) {
                "poincare-exercises-language.xml" -> LearningDomain.READING
                "poincare-exercises-logic-puzzles.xml" -> LearningDomain.PUZZLE
                "poincare-exercises-world-knowledge.xml" -> LearningDomain.WORLD_KNOWLEDGE
                "poincare-exercises-geometry.xml" -> LearningDomain.GEOMETRY
                else -> LearningDomain.MATH
            }
        }
    }

    private fun promptFor(exercise: PoincareExercise, locale: String): String =
        exercise.translations[locale]?.question
            ?: exercise.translations[locale]?.title
            ?: exercise.questionText.orEmpty()

    private fun correctAnswerFor(exercise: PoincareExercise, locale: String): String {
        val answers = exercise.translations[locale]?.answers.orEmpty().ifEmpty { exercise.answers }
        val correct = answers.filter { it.correct }
        return correct.joinToString(" | ") { answer ->
            answer.displays[locale] ?: answer.value ?: answer.text
        }.ifBlank { exercise.solutionValue.orEmpty() }
    }

    private fun optionsFor(exercise: PoincareExercise, locale: String): String {
        val answers = exercise.translations[locale]?.answers.orEmpty().ifEmpty { exercise.answers }
        return answers.joinToString(" | ") { answer ->
            answer.displays[locale] ?: answer.value ?: answer.text
        }
    }

    private fun manifest(): CurriculumManifest =
        assetFile("src/main/assets/${CurriculumManifestLoader.ASSET_PATH}")
            .inputStream().use(CurriculumManifestLoader::fromStream)

    private fun assetFile(relativePath: String): File {
        val candidates = listOf(File(relativePath), File("app/$relativePath"))
        return candidates.firstOrNull { it.exists() }
            ?: error("Missing test asset: $relativePath")
    }

    private data class XmlRecord(
        val fileName: String,
        val pack: PoincareContentPack,
        val exercise: PoincareExercise
    )

    private data class TileReference(
        val domain: LearningDomain,
        val tileId: String,
        val titleNl: String
    ) {
        val compositeId: String get() = "${domain.name}:$tileId"
    }

    private companion object {
        const val EXPORT_DIRECTORY_ENV = "POINCARE_EXPORT_DIR"
        const val OUTPUT_FILE_NAME = "poincare-question-audit.json"
        const val AUDIT_PROFILE_ID = "manual-curriculum-audit"
        const val AUDIT_ATTEMPT_PATTERN = "audit::{lessonId}::{sectionId}::v1"
        val XML_FILES = listOf(
            "poincare-exercises-math.xml",
            "poincare-exercises-language.xml",
            "poincare-exercises-logic-puzzles.xml",
            "poincare-exercises-world-knowledge.xml",
            "poincare-exercises-geometry.xml"
        )
    }
}

@Serializable
data class QuestionCatalogueExportBundle(
    val metadata: ExportMetadata,
    val lessons: List<LessonExportRow>,
    val sections: List<SectionExportRow>,
    val lessonQuestions: List<QuestionExportRow>,
    val xmlQuestions: List<XmlQuestionExportRow>,
    val practicalActivities: List<PracticalExportRow>,
    val mappingIssues: List<MappingIssueRow>
)

@Serializable
data class ExportMetadata(
    val generatedAtUtc: String,
    val manifestDefinitionVersion: Int,
    val catalogueDefinitionVersion: Int,
    val auditProfileId: String,
    val auditAttemptPattern: String,
    val xmlQuestionCount: Int,
    val lessonCount: Int,
    val lessonQuestionSampleCount: Int,
    val practicalActivityCount: Int,
    val mappingIssueCount: Int,
    val generatorCaveat: String
)

@Serializable
data class LessonExportRow(
    val unitId: String,
    val lessonId: String,
    val lessonSequence: Int,
    val grade: Int,
    val domain: String,
    val canonicalSubcategoryId: String,
    val tiles: String,
    val tileTitlesNl: String,
    val lessonTitleNl: String,
    val lessonTitleFr: String,
    val lessonTitleEn: String,
    val summaryNl: String,
    val estimatedMinutes: Int?,
    val requirement: String,
    val availability: String,
    val parentSupport: String,
    val questionCountInAuditAttempt: Int,
    val sectionCount: Int,
    val practicalActivityCount: Int,
    val learningGoalIds: String,
    val prerequisiteIds: String,
    val minimumCorrectRatio: Double,
    val minimumAttempts: Int,
    val sourceReference: String,
    val tags: String,
    val definitionVersion: Int
)

@Serializable
data class SectionExportRow(
    val unitId: String,
    val lessonId: String,
    val sectionId: String,
    val sectionType: String,
    val sectionTitleNl: String,
    val grade: Int,
    val domain: String,
    val canonicalSubcategoryId: String,
    val tiles: String,
    val required: Boolean,
    val parentSupport: String,
    val estimatedMinutes: Int?,
    val sourceType: String,
    val questionCountInAuditAttempt: Int,
    val generatorId: String,
    val generatorVersion: Int?,
    val seedPolicy: String,
    val parameters: String,
    val sourceReference: String
)

@Serializable
data class QuestionExportRow(
    val questionId: String,
    val questionPosition: Int,
    val sourceType: String,
    val sourceReference: String,
    val sourceFile: String,
    val generatorId: String,
    val generatorVersion: Int?,
    val seedPolicy: String,
    val seedFingerprint: String,
    val generatorParameters: String,
    val variantScope: String,
    val unitId: String,
    val lessonId: String,
    val sectionId: String,
    val sectionType: String,
    val lessonTitleNl: String,
    val grade: Int,
    val domain: String,
    val canonicalSubcategoryId: String,
    val tiles: String,
    val tileTitlesNl: String,
    val curriculumSourceReference: String,
    val inputMode: String,
    val promptNl: String,
    val promptFr: String,
    val promptEn: String,
    val speechNl: String,
    val correctAnswerNl: String,
    val correctAnswerFr: String,
    val correctAnswerEn: String,
    val optionsNl: String,
    val optionsFr: String,
    val optionsEn: String
)

@Serializable
data class XmlQuestionExportRow(
    val exerciseId: String,
    val sourceFile: String,
    val packId: String,
    val domain: String,
    val subcategoryId: String,
    val grade: Int?,
    val difficultyStars: Int?,
    val topic: String,
    val skillRef: String,
    val inputType: String,
    val contentType: String,
    val tiles: String,
    val tileTitlesNl: String,
    val lessonReferences: String,
    val mappingStatus: String,
    val promptNl: String,
    val promptFr: String,
    val promptEn: String,
    val correctAnswerNl: String,
    val correctAnswerFr: String,
    val correctAnswerEn: String,
    val optionsNl: String,
    val optionsFr: String,
    val optionsEn: String,
    val tags: String
)

@Serializable
data class PracticalExportRow(
    val activityId: String,
    val unitId: String,
    val lessonId: String,
    val lessonTitleNl: String,
    val grade: Int,
    val domain: String,
    val canonicalSubcategoryId: String,
    val tiles: String,
    val instructionsNl: String,
    val instructionsFr: String,
    val instructionsEn: String,
    val requiresAdultVerification: Boolean
)

@Serializable
data class MappingIssueRow(
    val severity: String,
    val issueType: String,
    val itemId: String,
    val details: String
)
