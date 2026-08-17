package be.saikono.poincare.domain.curriculum.v2

import be.saikono.poincare.model.ExerciseSection
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LessonCatalogV2Test {
    private val catalog by lazy {
        LessonCatalogV2Loader.fromFile(assetFile(LessonCatalogV2Loader.ASSET_PATH))
    }
    private val gradeOneLessons by lazy { catalog.lessons.filter { it.grade == 1 } }
    private val dictationLessons by lazy {
        gradeOneLessons.filter { it.id.startsWith("lesson.g1.nl.dictation.") }
    }
    private val dictationSubcategoryLessons by lazy {
        gradeOneLessons.filter { it.curriculumSubcategoryId == "language.dictation" }
    }
    private val coreGradeOneLessons by lazy {
        gradeOneLessons.filterNot { it in dictationLessons }
    }
    private val communicationLessons by lazy {
        coreGradeOneLessons.filter {
            it.id.startsWith("lesson.g1.nl.speaking.") || it.id.startsWith("lesson.g1.nl.writing.")
        }
    }
    private val tracingLessons by lazy {
        coreGradeOneLessons.filter { it.curriculumSubcategoryId == "letter_tracing" }
    }
    private val concreteArithmeticLessons by lazy {
        coreGradeOneLessons.filter { it.id.startsWith("lesson.g1.math.concrete.") }
    }
    private val baselineCoreGradeOneLessons by lazy {
        coreGradeOneLessons.filterNot {
            it in communicationLessons || it in tracingLessons || it in concreteArithmeticLessons
        }
    }
    @Test
    fun `complete grade one catalogue exposes three logical lesson parts`() {
        assertEquals(2, catalog.schemaVersion)
        assertEquals(93, gradeOneLessons.size)
        assertEquals(89, coreGradeOneLessons.size)
        assertEquals(10, communicationLessons.size)
        assertEquals(67, baselineCoreGradeOneLessons.size)
        assertEquals(8, tracingLessons.size)
        assertEquals(4, concreteArithmeticLessons.size)
        assertEquals(4, dictationLessons.size)
        assertEquals(5, dictationSubcategoryLessons.size)
        assertEquals(0, catalog.lessons.count { it.publicationStatus == LessonPublicationStatusV2.ACTIVE })
        assertEquals(93, gradeOneLessons.count { it.publicationStatus == LessonPublicationStatusV2.DRAFT_REVIEW_REQUIRED })
        val validationIssues = LessonCatalogV2Validator.validate(catalog)
        assertTrue(validationIssues.joinToString("\n"), validationIssues.isEmpty())

        baselineCoreGradeOneLessons.forEach { lesson ->
            assertTrue(lesson.curriculumCategoryId.isNotBlank())
            assertTrue(lesson.curriculumSubcategoryId.isNotBlank())
            assertEquals("nl-BE", lesson.defaultLocale)
            assertEquals(LessonTranslationStatusV2.DEFAULT, lesson.translationStatus.getValue("nl-BE").status)
            assertTrue(lesson.contentFields.isNotEmpty())
            assertEquals(LessonStageTypeV2.THEORY, lesson.stages.first().type)
            assertEquals(LessonStageTypeV2.MASTERY_TEST, lesson.stages.last().type)
            assertTrue(lesson.stages.drop(1).dropLast(1).all { it.type == LessonStageTypeV2.EXERCISES })
            assertTrue(lesson.exerciseSeries().isNotEmpty())
            assertEquals((1..lesson.exerciseSeries().size).toList(), lesson.exerciseSeries().map { it.seriesIndex })
            assertTrue(lesson.exerciseSeries().all { it.sectionTitle == null })
            assertEquals((1..lesson.stages.size).toList(), lesson.stages.map { it.sequence })
            assertTrue(lesson.exerciseSeries().sumOf { stage -> stage.exerciseSets.sumOf { it.itemCount } } > 0)
            val theoryCheckCount = requireNotNull(lesson.theoryStage()).exerciseSets.sumOf { it.itemCount }
            assertTrue("${lesson.id}: unexpected theory check count $theoryCheckCount", theoryCheckCount in 1..3)
            assertTrue(
                requireNotNull(lesson.finalTestStage()).exerciseSets.sumOf { it.itemCount } >=
                    lesson.gatePolicy.minimumIndependentItems
            )
            assertTrue(lesson.exerciseSeries().flatMap { it.exerciseSets }
                .all { it.parameters[WRONG_ANSWERS_ALLOWED_METADATA_KEY] in setOf("1", "2") })
            lesson.stages.forEach { stage ->
                stage.contentBlocks.forEach { block ->
                    assertTrue(block.pageId.isNotBlank())
                    assertTrue(block.pageSequence > 0)
                }
                stage.exerciseSets.forEach { set ->
                    assertEquals("v2", set.generatorVersion)
                    if (set.sourceMode == LessonSourceModeV2.GENERATED) {
                        assertEquals(1, set.generatorImplementationVersion)
                    } else {
                        assertEquals(null, set.generatorImplementationVersion)
                    }
                    assertEquals(lesson.id, set.lessonId)
                    assertEquals(stage.id, set.stageId)
                    assertEquals(set.id, set.exerciseSetId)
                    assertEquals(set.itemCount, set.questionBlueprints.size)
                    assertTrue(set.questionBlueprints.all { it.answerOptionIds.isNotEmpty() })
                }
            }
        }
        dictationLessons.forEach { lesson ->
            assertEquals(
                listOf(LessonStageTypeV2.THEORY) +
                    List(2) { LessonStageTypeV2.EXERCISES } +
                    LessonStageTypeV2.MASTERY_TEST,
                lesson.stages.map { it.type }
            )
            assertEquals((1..4).toList(), lesson.stages.map { it.sequence })
            assertEquals(4, lesson.stages.sumOf { it.exerciseSets.size })
        }
        communicationLessons.forEach { lesson ->
            assertEquals(
                listOf(LessonStageTypeV2.THEORY) +
                    List(8) { LessonStageTypeV2.EXERCISES } +
                    LessonStageTypeV2.MASTERY_TEST,
                lesson.stages.map { it.type }
            )
            assertEquals(8, lesson.exerciseSeries().size)
            assertEquals(32, lesson.exerciseSeries().flatMap { it.exerciseSets }.sumOf { it.itemCount })
            assertEquals(3, requireNotNull(lesson.theoryStage()).exerciseSets.sumOf { it.itemCount })
            assertTrue(
                requireNotNull(lesson.finalTestStage()).exerciseSets.sumOf { it.itemCount } >=
                    lesson.gatePolicy.minimumIndependentItems
            )
            assertTrue(lesson.stages.flatMap { it.exerciseSets }
                .filter { it.sourceMode == LessonSourceModeV2.GENERATED }
                .all { it.parameters.getValue("question_pool_nl").split('\u001e').size >= it.itemCount })
        }
        tracingLessons.forEach { lesson ->
            val symbols = lesson.theoryStage()!!.exerciseSets.single().parameters.getValue("symbols").split("|")
            assertEquals(symbols.size, lesson.theoryStage()!!.exerciseSets.sumOf { it.itemCount })
            assertEquals(symbols.size * 5, lesson.exerciseSeries().flatMap { it.exerciseSets }.sumOf { it.itemCount })
            assertEquals(symbols.size, lesson.finalTestStage()!!.exerciseSets.sumOf { it.itemCount })
        }
        concreteArithmeticLessons.forEach { lesson ->
            assertEquals(3, lesson.theoryStage()!!.exerciseSets.sumOf { it.itemCount })
            assertEquals(64, lesson.exerciseSeries().flatMap { it.exerciseSets }.sumOf { it.itemCount })
            assertEquals(12, lesson.finalTestStage()!!.exerciseSets.sumOf { it.itemCount })
        }
    }

    @Test
    fun `grade one split concept theory starts directly with child friendly concepts`() {
        val splitConceptLessons = gradeOneLessons.filter { lesson ->
            lesson.legacyRouting.domain == LessonDomainV2.WORLD_KNOWLEDGE &&
                lesson.theoryStage()?.contentBlocks.orEmpty().any {
                    it.type == LessonContentBlockTypeV2.CHILD_FRIENDLY_DEFINITION
                }
        }
        assertTrue(splitConceptLessons.isNotEmpty())
        splitConceptLessons.forEach { lesson ->
            val theoryTypes = requireNotNull(lesson.theoryStage()).contentBlocks.map { it.type }.toSet()
            assertTrue(LessonContentBlockTypeV2.LEARNING_INTENT in theoryTypes)
            assertFalse(LessonContentBlockTypeV2.VOCABULARY in theoryTypes)
            assertTrue(LessonContentBlockTypeV2.CHILD_FRIENDLY_DEFINITION in theoryTypes)
            assertFalse(
                LessonCatalogV2Validator.validate(catalog.copy(lessons = listOf(lesson)))
                    .any { it.ruleId == "theory.child-language" }
            )
        }
    }

    @Test
    fun `every grade one lesson has a lesson bound theory check`() {
        val withoutChecks = gradeOneLessons.filter {
            it.theoryStage()?.exerciseSets.orEmpty().sumOf { set -> set.itemCount } == 0
        }
        assertTrue(withoutChecks.joinToString { it.id }, withoutChecks.isEmpty())
    }

    @Test
    fun `checkpoint flags are explicit with one multi-prerequisite grade two example`() {
        assertTrue(gradeOneLessons.none { it.checkpoint })
        val checkpoints = catalog.lessons.filter { it.checkpoint }
        assertEquals(
            listOf("lesson.g2.math.measurement.klok-duur-en-kalender.tijdsduur"),
            checkpoints.map { it.id }
        )
        assertTrue(checkpoints.single().grade > 1)
        assertTrue(checkpoints.single().prerequisiteLessonIds.size > 1)

        val invalidGradeOne = gradeOneLessons.first().copy(checkpoint = true)
        assertTrue(
            LessonCatalogV2Validator.validate(catalog.copy(lessons = listOf(invalidGradeOne)))
                .any { it.ruleId == "lesson.checkpoint.grade-one" }
        )

        val index = assetFile(LessonCatalogV2Loader.ASSET_PATH)
        val shardRoot = index.parentFile.resolve("lesson-catalog-v2")
        val explicitFlagCount = shardRoot.listFiles { file -> file.extension == "json" }.orEmpty()
            .sumOf { shard -> Regex("\\\"checkpoint\\\"\\s*:").findAll(shard.readText(Charsets.UTF_8)).count() }
        assertEquals(catalog.lessons.size, explicitFlagCount)
    }

    @Test
    fun `all three grade one lesson parts remain required`() {
        val source = gradeOneLessons.first()
        val withoutTheory = source.copy(stages = source.stages.drop(1).mapIndexed { index, stage -> stage.copy(sequence = index + 1) })
        assertTrue(
            LessonCatalogV2Validator.validate(catalog.copy(lessons = listOf(withoutTheory)))
                .any { it.ruleId == "lesson.theory-first" || it.ruleId == "lesson.three-explicit-parts" }
        )

        val withoutTest = source.copy(stages = source.stages.dropLast(1))
        assertTrue(
            LessonCatalogV2Validator.validate(catalog.copy(lessons = listOf(withoutTest)))
                .any { it.ruleId == "lesson.single-final-test" }
        )
    }

    @Test
    fun `every grade one math and geometry exercise set declares a lesson representation`() {
        gradeOneLessons.filter {
            it.legacyRouting.domain in setOf(LessonDomainV2.MATH, LessonDomainV2.GEOMETRY)
        }.forEach { lesson ->
            val declared = lesson.visuals.mapNotNull { it.representationId }.toSet()
            assertTrue("Missing lesson representation for ${lesson.id}", declared.isNotEmpty())
            lesson.stages.flatMap { it.exerciseSets }.forEach { set ->
                assertTrue("Missing representation on ${set.id}", set.representationIds.isNotEmpty())
                assertTrue(set.representationIds.all { it in declared })
            }
        }
    }

    @Test
    fun `pilot routes Dutch hierarchy and corrected theory visuals remain intact`() {
        val routes = catalog.lessons.associate { it.id to it.legacyRouting.subcategoryId }
        assertEquals("addition", routes["lesson.g1.math.addition.within10.meaning-and-model"])
        assertEquals("reading", routes["lesson.g1.reading.initial-sound.identify-and-compare"])
        assertEquals("measurement", routes["lesson.g1.geometry.length.compare-and-order"])
        assertEquals("nature", routes["lesson.g2.world.nature.seasons.order-and-signs"])

        val addition = gradeOneLessons.single { it.id == "lesson.g1.math.addition.within10.meaning-and-model" }
        assertEquals(setOf("nl-BE"), addition.title.visible.keys)
        assertEquals(LessonTranslationStatusV2.MISSING, addition.translationStatus.getValue("fr-BE").status)
        assertEquals(LessonTranslationStatusV2.MISSING, addition.translationStatus.getValue("en-US").status)
        assertEquals(
            listOf(
                "poincare/visuals/theory/addition-within10-v1.png",
                "poincare/visuals/theory/addition-within10-example2-v1.png"
            ),
            addition.visuals.mapNotNull { it.assetPathPng }
        )
        assertEquals(
            listOf(
                "visual.g1.math.addition.within10.example1",
                "visual.g1.math.addition.within10.example2"
            ),
            requireNotNull(addition.theoryStage()).contentBlocks
                .filter { it.type == LessonContentBlockTypeV2.WORKED_EXAMPLE }
                .flatMap { it.visualIds }
        )
    }

    @Test
    fun `catalogue text is canonical utf8 and support translation statuses are explicit`() {
        val rawCatalog = assetFile(LessonCatalogV2Loader.ASSET_PATH).readText(Charsets.UTF_8)
        listOf("\u00C3", "\u00C2", "\u00E2\u20AC\u2122", "\u00E2\u20AC\u201C").forEach { marker ->
            assertFalse("Unexpected encoding marker '$marker' in v2 catalogue", rawCatalog.contains(marker))
        }
        catalog.lessons.forEach { lesson ->
            assertTrue("nl-BE" in lesson.title.visible.keys)
            assertTrue(lesson.title.visible.keys.all { it in REQUIRED_LESSON_LOCALES })
            assertTrue(lesson.title.visibleFor("nl-BE").isNotBlank())
            assertTrue(lesson.summary.visibleFor("nl-BE").isNotBlank())
            assertEquals(REQUIRED_LESSON_LOCALES, lesson.translationStatus.keys)
            lesson.visuals.forEach { visual ->
                assertTrue("nl-BE" in visual.altText.keys)
                assertTrue(visual.altText.keys.all { it in REQUIRED_LESSON_LOCALES })
            }
            lesson.contentFields.forEach { field ->
                assertEquals(REQUIRED_LESSON_LOCALES, field.translations.map { it.locale }.toSet())
            }
        }
    }

    @Test
    fun `all generated sets resolve deterministically with unique exercises`() = runBlocking {
        val resolver = LessonExerciseResolverV2(curatedProvider = CuratedLessonExerciseProviderV2 { null })
        catalog.lessons.forEach { lesson ->
            lesson.stages.flatMap { stage -> stage.exerciseSets.map { stage to it } }
                .filter { (_, set) -> set.sourceMode == LessonSourceModeV2.GENERATED }
                .forEach { (stage, set) ->
                val context = LessonResolveContextV2(
                    profileId = "profile-1",
                    lessonId = lesson.id,
                    unitId = lesson.unitId,
                    stageId = stage.id,
                    attemptId = "attempt-1"
                )
                val first = resolver.resolve(set, context).success()
                val replay = resolver.resolve(set, context).success()
                assertEquals(set.itemCount, first.exercises.size)
                assertEquals(first, replay)
                assertEquals(first.exercises.size, first.exercises.map { it.id }.distinct().size)
                if (stage.type == LessonStageTypeV2.MASTERY_TEST) {
                    assertTrue(first.exercises.all { it.section == ExerciseSection.TESTS })
                }
            }
        }
    }

    @Test
    fun `mastery tests exclude every exercise signature already seen in the lesson attempt`() = runBlocking {
        val resolver = LessonExerciseResolverV2(curatedProvider = CuratedLessonExerciseProviderV2 { null })
        val resolutionFailures = mutableListOf<String>()

        gradeOneLessons.forEach { lesson ->
            val seen = linkedSetOf<String>()
            lesson.stages.sortedBy { it.sequence }.forEach { stage ->
                stage.exerciseSets.forEach { set ->
                    if (set.sourceMode == LessonSourceModeV2.CURATED) {
                        if (!stage.type.isTheory()) {
                            seen += set.curatedExerciseIds.map { "curated:$it" }
                        }
                        return@forEach
                    }
                    val resolution = resolver.resolve(
                        set,
                        LessonResolveContextV2(
                            profileId = "profile-audit",
                            lessonId = lesson.id,
                            unitId = lesson.unitId,
                            stageId = stage.id,
                            attemptId = "attempt-audit",
                            excludedExerciseSignatures = seen
                        )
                    )
                    if (resolution is LessonExerciseResolutionV2.Rejected) {
                        resolutionFailures += "${lesson.id}/${set.id}: ${resolution.message} ${resolution.details}"
                        return@forEach
                    }
                    val result = (resolution as LessonExerciseResolutionV2.Success).value
                    val signatures = result.exercises.map(ExerciseContentSignatureV2::of)
                    if (stage.type == LessonStageTypeV2.MASTERY_TEST) {
                        assertEquals(
                            "A mastery test must not contain duplicate visible content: ${set.id}",
                            signatures.size,
                            signatures.distinct().size
                        )
                        assertTrue(
                            "Mastery test repeats recent practice in ${lesson.id}",
                            signatures.none { it in seen }
                        )
                    }
                    if (!stage.type.isTheory()) seen += signatures
                }
            }
        }
        assertTrue(
            "All Grade 1 sets must resolve with unseen assessment content:\n${resolutionFailures.joinToString("\n")}",
            resolutionFailures.isEmpty()
        )
    }

    @Test
    fun `addition and subtraction lesson routing is operation compatible`() {
        gradeOneLessons.forEach { lesson ->
            lesson.stages.flatMap { it.exerciseSets }.filter {
                it.generatorId == "generator.g1.math.addition_subtraction.foundations"
            }.forEach { set ->
                when (lesson.legacyRouting.subcategoryId) {
                    "addition" -> assertEquals(
                        "Addition lesson received a non-addition set: ${lesson.id}/${set.id}",
                        "addition",
                        set.parameters["operation"]
                    )
                    "subtraction" -> assertEquals(
                        "Subtraction lesson received a non-subtraction set: ${lesson.id}/${set.id}",
                        "subtraction",
                        set.parameters["operation"]
                    )
                }
            }
        }
    }

    @Test
    fun `every lesson visual exists uses transparency and obeys its declared svg palette`() {
        catalog.lessons.forEach { lesson ->
            lesson.visuals.forEach { visual ->
                visual.assetPathPng?.let { relative ->
                    val png = assetFile(relative)
                    val header = png.readBytes().take(26).map { it.toInt() and 0xFF }
                    assertEquals(listOf(137, 80, 78, 71, 13, 10, 26, 10), header.take(8))
                    assertEquals("PNG colour type must be RGBA for $relative", 6, header[25])
                }
                visual.assetPathSvg?.let { relative ->
                    val svgText = assetFile(relative).readText(Charsets.UTF_8)
                    val colours = Regex("#[0-9A-Fa-f]{6}").findAll(svgText)
                        .map { it.value.uppercase() }
                        .toSet()
                    val allowed = when (visual.palettePolicy) {
                        LessonPalettePolicyV2.PLANET_GOLD_WHITE_ONLY -> setOf("#FFD700", "#FFFFFF")
                        LessonPalettePolicyV2.POINCARE_TRICOLOUR -> setOf("#FFD700", "#FFFFFF", "#4FC3F7")
                        else -> colours
                    }
                    assertTrue("Unexpected SVG colours $colours in $relative", colours.all { it in allowed })
                }
                assertTrue(visual.transparentBackground)
            }
            val referenced = requireNotNull(lesson.theoryStage()).contentBlocks.flatMap { it.visualIds }.toSet()
            assertTrue("Theory does not reference a lesson visual for ${lesson.id}", referenced.isNotEmpty())
            assertTrue(referenced.all { id -> lesson.visuals.any { it.id == id } })
        }
    }

    private fun LessonExerciseResolutionV2.success(): ResolvedLessonExerciseSetV2 {
        assertTrue("Expected success but received $this", this is LessonExerciseResolutionV2.Success)
        return (this as LessonExerciseResolutionV2.Success).value
    }

    private fun assetFile(relative: String): File = listOf(
        File("src/main/assets/$relative"),
        File("app/src/main/assets/$relative")
    ).first { it.isFile }
}
