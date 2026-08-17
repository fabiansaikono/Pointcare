package be.saikono.poincare.ui.galaxy

import be.saikono.poincare.domain.curriculum.manifest.CurriculumManifestLoader
import be.saikono.poincare.domain.curriculum.v2.LessonGateStateV2
import be.saikono.poincare.domain.curriculum.v2.LessonCatalogV2Loader
import be.saikono.poincare.domain.curriculum.v2.LessonStageTypeV2
import be.saikono.poincare.data.story.StoryBook
import be.saikono.poincare.data.story.StorySeriesTitle
import be.saikono.poincare.data.story.StoryTranslation
import be.saikono.poincare.model.ChildProfile
import be.saikono.poincare.model.LearningDomain
import be.saikono.poincare.model.SyncStatus
import be.saikono.poincare.model.lesson.LessonMasteryLevel
import be.saikono.poincare.model.lesson.LessonProgress
import be.saikono.poincare.model.lesson.LessonProgressState
import be.saikono.poincare.repository.LessonStageProgressSnapshotV2
import java.io.File
import kotlin.math.hypot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LearningGalaxyModelsTest {

    @Test
    fun completedPrerequisiteUnlocksLesson() {
        assertTrue(
            galaxyPrerequisitesSatisfied(
                prerequisiteLessonIds = listOf("lesson-a"),
                passedLessonIds = setOf("lesson-a")
            )
        )
    }

    @Test
    fun incompleteSecondPrerequisiteKeepsLessonLocked() {
        assertFalse(
            galaxyPrerequisitesSatisfied(
                prerequisiteLessonIds = listOf("lesson-a", "lesson-b"),
                passedLessonIds = setOf("lesson-a")
            )
        )
    }

    @Test
    fun oneCompletedLessonCanUnlockTwoBranches() {
        val passed = setOf("lesson-a")
        val branches = mapOf(
            "lesson-b" to listOf("lesson-a"),
            "lesson-c" to listOf("lesson-a")
        )

        assertEquals(
            mapOf("lesson-b" to true, "lesson-c" to true),
            branches.mapValues { (_, prerequisites) ->
                galaxyPrerequisitesSatisfied(prerequisites, passed)
            }
        )
    }

    @Test
    fun progressMapsToGalaxyStatesWithoutReplacingCategoryIdentity() {
        assertEquals(
            GalaxyNodeState.LOCKED,
            galaxyNodeState(null, null, null, prerequisitesSatisfied = false)
        )
        assertEquals(
            GalaxyNodeState.AVAILABLE,
            galaxyNodeState(null, null, null, prerequisitesSatisfied = true)
        )
        assertEquals(
            GalaxyNodeState.IN_PROGRESS,
            galaxyNodeState(
                LessonProgressState.IN_PROGRESS,
                LessonMasteryLevel.DEVELOPING,
                null,
                prerequisitesSatisfied = true
            )
        )
        assertEquals(
            GalaxyNodeState.LOCKED,
            galaxyNodeState(
                LessonProgressState.IN_PROGRESS,
                LessonMasteryLevel.DEVELOPING,
                null,
                prerequisitesSatisfied = false
            )
        )
        assertEquals(
            GalaxyNodeState.LOCKED,
            galaxyNodeState(
                LessonProgressState.IN_PROGRESS,
                LessonMasteryLevel.MASTERED,
                null,
                prerequisitesSatisfied = false
            )
        )
        assertEquals(
            GalaxyNodeState.COMPLETED,
            galaxyNodeState(
                LessonProgressState.COMPLETED,
                LessonMasteryLevel.PROFICIENT,
                null,
                prerequisitesSatisfied = true
            )
        )
        assertEquals(
            GalaxyNodeState.MASTERED,
            galaxyNodeState(
                LessonProgressState.COMPLETED,
                LessonMasteryLevel.MASTERED,
                null,
                prerequisitesSatisfied = true
            )
        )
    }

    @Test
    fun categoryLayoutIsDeterministicAndAddsPhaseAndRealPrerequisiteEdges() {
        val track = GalaxyTrackUi(
            id = "numbers",
            title = "Numbers",
            iconResId = 1,
            lessons = listOf(
                lesson("a"),
                lesson("b", listOf("a")),
                lesson("c")
            )
        )

        val first = GalaxyLayoutEngine.layoutCategory(listOf(track))
        val second = GalaxyLayoutEngine.layoutCategory(listOf(track))

        assertEquals(first, second)
        assertTrue(
            first.connections.contains(
                GalaxyConnection(
                    galaxyOrbNodeId("a", GalaxyOrbKind.TEST),
                    galaxyOrbNodeId("b", GalaxyOrbKind.THEORY)
                )
            )
        )
        assertTrue(
            first.connections.contains(
                GalaxyConnection(
                    galaxyOrbNodeId("a", GalaxyOrbKind.THEORY),
                    galaxyOrbNodeId("a", GalaxyOrbKind.PRACTICE)
                )
            )
        )
        assertFalse(
            first.connections.contains(
                GalaxyConnection(
                    galaxyOrbNodeId("a", GalaxyOrbKind.TEST),
                    galaxyOrbNodeId("c", GalaxyOrbKind.THEORY)
                )
            )
        )
        assertEquals(
            setOf(GalaxyOrbKind.THEORY, GalaxyOrbKind.PRACTICE, GalaxyOrbKind.TEST),
            first.nodes.filter { it.lessonId == "a" }.map { it.kind }.toSet()
        )
    }

    @Test
    fun sphereOrderUsesDynamicPrerequisitesBeforeDuplicateSequenceTieBreakers() {
        val root = lesson("z-root")
        val dependent = lesson("a-dependent", prerequisites = listOf(root.id))
        val layout = GalaxyLayoutEngine.layoutCategory(
            listOf(GalaxyTrackUi("dynamic", "Dynamic", 1, listOf(dependent, root)))
        )

        assertEquals(
            listOf(root.id, dependent.id),
            layout.nodes.map { it.lessonId }.distinct()
        )
        assertTrue(
            GalaxyConnection(
                GALAXY_START_NODE_ID,
                galaxyOrbNodeId(root.id, GalaxyOrbKind.THEORY)
            ) in layout.startConnections
        )
        assertTrue(
            GalaxyConnection(
                galaxyOrbNodeId(root.id, GalaxyOrbKind.TEST),
                galaxyOrbNodeId(dependent.id, GalaxyOrbKind.THEORY)
            ) in layout.connections
        )
    }

    @Test
    fun centralStartUsesOnlyTheFirstDynamicRootOfEachSphere() {
        val firstRoot = lesson("a-first-root")
        val secondRoot = lesson("z-second-root")
        val layout = GalaxyLayoutEngine.layoutCategory(
            listOf(GalaxyTrackUi("dynamic", "Dynamic", 1, listOf(secondRoot, firstRoot)))
        )

        assertEquals(
            listOf(
                GalaxyConnection(
                    GALAXY_START_NODE_ID,
                    galaxyOrbNodeId(firstRoot.id, GalaxyOrbKind.THEORY)
                )
            ),
            layout.startConnections
        )
    }

    @Test
    fun dynamicLessonInsertionCreatesItsImmediateConnectionWithoutAncestorShortcut() {
        val root = lesson("root")
        val middle = lesson("middle", prerequisites = listOf(root.id))
        val inserted = lesson("new-lesson", prerequisites = listOf(root.id, middle.id))
        val layout = GalaxyLayoutEngine.layoutCategory(
            listOf(GalaxyTrackUi("dynamic", "Dynamic", 1, listOf(inserted, root, middle)))
        )

        assertTrue(layout.nodes.any { it.lessonId == inserted.id })
        assertTrue(
            GalaxyConnection(
                galaxyOrbNodeId(middle.id, GalaxyOrbKind.TEST),
                galaxyOrbNodeId(inserted.id, GalaxyOrbKind.THEORY)
            ) in layout.connections
        )
        assertFalse(
            GalaxyConnection(
                galaxyOrbNodeId(root.id, GalaxyOrbKind.TEST),
                galaxyOrbNodeId(inserted.id, GalaxyOrbKind.THEORY)
            ) in layout.connections
        )
    }

    @Test
    fun eachGradeGetsItsOwnOrganicSphereGroup() {
        val track = GalaxyTrackUi(
            id = "numbers",
            title = "Numbers",
            iconResId = 1,
            lessons = listOf(
                lesson("g1-a", grade = 1),
                lesson("g1-b", prerequisites = listOf("g1-a"), grade = 1),
                lesson("g2-a", grade = 2),
                lesson("g2-b", prerequisites = listOf("g2-a"), grade = 2)
            )
        )

        val layout = GalaxyLayoutEngine.layoutCategory(listOf(track))
        val gradeOne = layout.gradeSections.single { it.grade == 1 }
        val gradeTwo = layout.gradeSections.single { it.grade == 2 }

        assertEquals(listOf(1, 2), layout.gradeSections.map { it.grade })
        assertTrue(gradeOne.point != gradeTwo.point)
        assertEquals(2, layout.trackSpheres.map { it.centre }.distinct().size)
        assertTrue(layout.nodes.filter { it.grade == 1 }.all { it.lessonId.startsWith("g1-") })
        assertTrue(layout.nodes.filter { it.grade == 2 }.all { it.lessonId.startsWith("g2-") })
    }

    @Test
    fun minimumGalaxyScaleAllowsABroaderOverview() {
        assertTrue(MIN_GALAXY_SCALE <= 0.45f)
        assertTrue(MIN_GALAXY_SCALE >= 0.35f)
    }

    @Test
    fun actualFitScaleCanBecomeTheZoomMinimumForLargeGalaxies() {
        assertEquals(0.24f, galaxyMinimumScale(0.24f), 0.0001f)
        assertEquals(MIN_GALAXY_SCALE, galaxyMinimumScale(0.70f), 0.0001f)
    }

    @Test
    fun phaseOrbsExposeIndependentTheoryPracticeAndTestStates() {
        val definition = loadCatalog().lessons.first { lesson ->
            lesson.stages.any { it.type == LessonStageTypeV2.THEORY } &&
                lesson.stages.any { it.type == LessonStageTypeV2.EXERCISES } &&
                lesson.stages.any { it.type == LessonStageTypeV2.MASTERY_TEST }
        }
        val completedTheory = definition.stages
            .filter { it.type == LessonStageTypeV2.THEORY }
            .map { stage ->
                LessonStageProgressSnapshotV2(
                    lessonId = definition.id,
                    stageId = stage.id,
                    stageType = stage.type,
                    completed = true,
                    diagnosticScorePercent = null
                )
            }
        val progress = inProgress(
            unitId = definition.unitId,
            lessonId = definition.id,
            currentStageType = LessonStageTypeV2.EXERCISES
        )

        val orbs = buildGalaxyPhaseOrbs(
            lesson = definition,
            lessonState = GalaxyNodeState.IN_PROGRESS,
            lessonProgress = progress,
            stageProgress = completedTheory,
            prerequisitesSatisfied = true
        )
        val theoryOrbs = orbs.filter { it.kind == GalaxyOrbKind.THEORY }
        val practiceOrbs = orbs.filter { it.kind == GalaxyOrbKind.PRACTICE }
        val testOrbs = orbs.filter { it.kind == GalaxyOrbKind.TEST }

        assertEquals(
            listOf(GalaxyOrbKind.THEORY, GalaxyOrbKind.PRACTICE, GalaxyOrbKind.TEST),
            orbs.map { it.kind }
        )
        assertEquals(GalaxyNodeState.COMPLETED, theoryOrbs.single().state)
        assertEquals(GalaxyNodeState.IN_PROGRESS, practiceOrbs.single().state)
        assertEquals(GalaxyNodeState.LOCKED, testOrbs.single().state)
        assertTrue(practiceOrbs.first().stageId?.isNotBlank() == true)
    }

    @Test
    fun lockedLessonKeepsEveryGroupedPhaseOrbLockedEvenWithSavedStageProgress() {
        val definition = loadCatalog().lessons.first { lesson ->
            lesson.stages.any { it.type == LessonStageTypeV2.THEORY } &&
                lesson.stages.any { it.type == LessonStageTypeV2.EXERCISES }
        }
        val progress = inProgress(
            unitId = definition.unitId,
            lessonId = definition.id,
            currentStageType = LessonStageTypeV2.THEORY
        )

        val orbs = buildGalaxyPhaseOrbs(
            lesson = definition,
            lessonState = GalaxyNodeState.LOCKED,
            lessonProgress = progress,
            stageProgress = emptyList(),
            prerequisitesSatisfied = false
        )

        assertTrue(orbs.isNotEmpty())
        assertTrue(orbs.all { it.state == GalaxyNodeState.LOCKED })
    }

    @Test
    fun universeCategoryCentresStayInsidePortraitAndLandscapeBounds() {
        val ids = (1..6).map { "category-$it" }

        listOf(320f to 520f, 700f to 250f).forEach { (width, height) ->
            val layout = GalaxyLayoutEngine.layoutUniverse(ids, width, height)
            assertTrue(layout.categoryCentres.values.all { point ->
                point.x in 54f..(width - 54f) && point.y in 48f..(height - 62f)
            })
            val points = layout.categoryCentres.values.toList()
            val closestPair = points.indices.flatMap { first ->
                ((first + 1) until points.size).map { second ->
                    hypot(
                        points[first].x - points[second].x,
                        points[first].y - points[second].y
                    )
                }
            }.minOrNull() ?: Float.MAX_VALUE
            assertTrue(closestPair >= 108f)
        }
    }

    @Test
    fun galaxyUsesTheCompleteCanonicalLessonOverviewAndSequence() {
        val manifest = loadManifest()
        val catalog = loadCatalog()
        val profile = ChildProfile(
            id = "galaxy-child",
            name = "Galaxy",
            avatarId = "1",
            rocketColorId = "1",
            birthYear = 2018,
            selectedGrades = LearningDomain.entries.associate { it.name to listOf(1) }
        )

        val galaxy = buildLearningGalaxyUiState(
            profile = profile,
            manifest = manifest,
            catalog = catalog,
            progress = emptyList(),
            categoryProgressPercent = emptyMap()
        )
        val actualLessonIds = galaxy.categories.flatMap { category ->
            category.tracks.flatMap { track ->
                track.lessons.filter { it.portalDestination == null }.map { it.id }
            }
        }.toSet()
        val expectedLessonIds = catalog.lessons
            .filter { it.grade == 1 }
            .mapTo(linkedSetOf()) { it.id }

        assertEquals(expectedLessonIds, actualLessonIds)
        val numberTrack = galaxy.categories
            .first { it.domain == LearningDomain.MATH }
            .tracks
            .first { it.id == "math.number_place_value" }
        assertEquals("lesson.g1.math.numbers.tellen-tot-20", numberTrack.lessons.first().id)
        assertTrue(numberTrack.lessons.first().fallbackIconResId != 0)
    }

    @Test
    fun galaxyIncludesEveryCatalogLessonAcrossSelectedGrades() {
        val manifest = loadManifest()
        val catalog = loadCatalog()
        val profile = galaxyProfile(
            selectedGrades = LearningDomain.entries.associate { it.name to (1..6).toList() }
        )

        val galaxy = buildLearningGalaxyUiState(profile, manifest, catalog, emptyList(), emptyMap())
        val actualIds = galaxy.categories.flatMap { category ->
            category.tracks.flatMap { track ->
                track.lessons.filter { it.portalDestination == null }.map { it.id }
            }
        }.toSet()

        assertEquals(catalog.lessons.mapTo(linkedSetOf()) { it.id }, actualIds)
    }

    @Test
    fun readingGalaxyDoesNotCreateAGenericStoriesPortal() {
        val profile = galaxyProfile(
            selectedGrades = LearningDomain.entries.associate { it.name to listOf(1) }
        )
        val galaxy = buildLearningGalaxyUiState(
            profile,
            loadManifest(),
            loadCatalog(),
            emptyList(),
            emptyMap()
        )
        val storiesTrack = galaxy.categories
            .single { it.domain == LearningDomain.READING }
            .tracks
            .single { it.id == "language.stories" }
        assertTrue(storiesTrack.lessons.isEmpty())
        assertTrue(GalaxyLayoutEngine.layoutCategory(listOf(storiesTrack)).startConnections.isEmpty())
    }

    @Test
    fun readingGalaxyShowsEachAvailableStoryThemeAsItsOwnPortalOrb() {
        val base = buildLearningGalaxyUiState(
            galaxyProfile(
                selectedGrades = LearningDomain.entries.associate { it.name to listOf(1) }
            ),
            loadManifest(),
            loadCatalog(),
            emptyList(),
            emptyMap()
        )
        val book = StoryBook(
            id = "book-one",
            grade = 1,
            order = 1,
            coverAsset = "stories/book-one.png",
            translations = mapOf(
                "nl-BE" to StoryTranslation("Boek één", "Beschrijving")
            ),
            storyIds = listOf("story-one")
        )

        val galaxy = base.withStoryBooks(listOf(book), "nl-BE")
        val stories = galaxy.categories.single { it.domain == LearningDomain.READING }
            .tracks.single { isGalaxyStoryTrack(it.id) }

        val themePortal = stories.lessons.single()
        assertEquals(galaxyStoryThemePortalId("standalone"), themePortal.id)
        assertEquals(GalaxyPortalDestination.STORY_LIBRARY, themePortal.portalDestination)
        assertEquals(null, themePortal.portalTargetId)
        assertEquals(stories.title, themePortal.title)
        assertEquals(null, themePortal.iconAssetPath)
        assertEquals(GalaxyOrbKind.PORTAL, galaxyOrbNodes(themePortal).single().orb.kind)
        assertTrue(GalaxyLayoutEngine.layoutCategory(listOf(stories)).startConnections.isEmpty())
        assertTrue(galaxy.categories.single { it.domain == LearningDomain.READING }
            .nextLessonCandidates().none { it.id == themePortal.id })
    }

    @Test
    fun booksFromTheSameSeriesCollapseIntoOneThemeOrb() {
        val base = buildLearningGalaxyUiState(
            galaxyProfile(
                selectedGrades = LearningDomain.entries.associate { it.name to listOf(1) }
            ),
            loadManifest(),
            loadCatalog(),
            emptyList(),
            emptyMap()
        )
        fun book(id: String, bookNumber: Int) = StoryBook(
            id = id,
            grade = 1,
            order = bookNumber,
            coverAsset = "stories/$id.png",
            translations = mapOf("nl-BE" to StoryTranslation(id, id)),
            storyIds = listOf("story-$id"),
            seriesId = "series",
            seriesOrder = 3,
            bookNumber = bookNumber
        )
        val firstBook = book("z-first-book", bookNumber = 1)
        val secondBook = book("a-second-book", bookNumber = 2)
        val storiesTrack = base.withStoryBooks(
            books = listOf(secondBook, firstBook),
            locale = "nl-BE"
        ).categories.single { it.domain == LearningDomain.READING }
            .tracks.single { isGalaxyStoryTrack(it.id) }

        assertEquals(1, storiesTrack.lessons.size)
        assertEquals(galaxyStoryThemePortalId("series"), storiesTrack.lessons.single().id)
        assertEquals(1, GalaxyLayoutEngine.layoutCategory(listOf(storiesTrack)).nodes.size)
    }

    @Test
    fun storyBooksShareOneSphereAndAreGroupedBySeriesThemeAcrossGrades() {
        val base = buildLearningGalaxyUiState(
            galaxyProfile(
                selectedGrades = LearningDomain.entries.associate { it.name to listOf(1) }
            ),
            loadManifest(),
            loadCatalog(),
            emptyList(),
            emptyMap()
        )
        fun themedBook(id: String, grade: Int, seriesId: String, seriesTitle: String) = StoryBook(
            id = id,
            grade = grade,
            order = grade,
            coverAsset = "stories/$id.png",
            translations = mapOf("nl-BE" to StoryTranslation(id, id)),
            storyIds = listOf("story-$id"),
            seriesId = seriesId,
            seriesOrder = if (seriesId == "first") 1 else 2,
            seriesTitle = StorySeriesTitle(mapOf("nl-BE" to seriesTitle)),
            bookNumber = grade
        )
        val reading = base.withStoryBooks(
            books = listOf(
                themedBook("first-g2", 2, "first", "Eerste thema"),
                themedBook("second-g1", 1, "second", "Tweede thema"),
                themedBook("first-g1", 1, "first", "Eerste thema")
            ),
            locale = "nl-BE"
        ).categories.single { it.domain == LearningDomain.READING }
        val storyTracks = reading.tracks.filter { isGalaxyStoryTrack(it.id) }

        assertEquals(1, storyTracks.size)
        assertEquals("language.stories", storyTracks.single().id)
        assertEquals(
            listOf(galaxyStoryThemePortalId("first"), galaxyStoryThemePortalId("second")),
            storyTracks.single().lessons.map { it.id }
        )
        assertEquals(
            listOf("Eerste thema", "Tweede thema"),
            storyTracks.single().lessons.map { it.title }
        )
        assertTrue(storyTracks.single().lessons.all {
            it.portalDestination == GalaxyPortalDestination.STORY_LIBRARY && it.portalTargetId == null
        })
        val storyLayout = GalaxyLayoutEngine.layoutCategory(storyTracks)
        assertEquals(1, storyLayout.trackSpheres.size)
        assertEquals("language.stories", storyLayout.trackSpheres.single().trackId)
    }

    @Test
    fun parentDashboardCanHideTheStoriesPortal() {
        val profile = galaxyProfile(
            selectedGrades = LearningDomain.entries.associate { it.name to listOf(1) },
            hiddenSubcategories = listOf("language.stories")
        )

        val galaxy = buildLearningGalaxyUiState(
            profile,
            loadManifest(),
            loadCatalog(),
            emptyList(),
            emptyMap(),
            selectedGrade = 1
        )

        assertTrue(galaxy.categories.flatMap { it.tracks }.none { it.id == "language.stories" })
    }

    @Test
    fun selectedMathGradesProduceSeparateCanonicalSectionsInAscendingOrder() {
        val selectedGrades = LearningDomain.entries.associate { domain ->
            domain.name to if (domain == LearningDomain.MATH) listOf(1, 2) else listOf(1)
        }
        val galaxy = buildLearningGalaxyUiState(
            galaxyProfile(selectedGrades = selectedGrades),
            loadManifest(),
            loadCatalog(),
            emptyList(),
            emptyMap()
        )
        val math = galaxy.categories.single { it.domain == LearningDomain.MATH }
        val layout = GalaxyLayoutEngine.layoutCategory(math.tracks)

        assertEquals(listOf(1, 2), layout.gradeSections.map { it.grade })
        assertTrue(layout.nodes.any { it.grade == 1 })
        assertTrue(layout.nodes.any { it.grade == 2 })
        assertTrue(layout.trackSpheres.any { it.grade == 1 })
        assertTrue(layout.trackSpheres.any { it.grade == 2 })
    }

    @Test
    fun selectedGradeFilterKeepsStoriesAsTheOnlyReadingException() {
        val selectedGrades = LearningDomain.entries.associate { domain ->
            domain.name to if (domain == LearningDomain.MATH) listOf(1, 2) else listOf(1)
        }
        val galaxy = buildLearningGalaxyUiState(
            profile = galaxyProfile(selectedGrades = selectedGrades),
            manifest = loadManifest(),
            catalog = loadCatalog(),
            progress = emptyList(),
            categoryProgressPercent = emptyMap(),
            selectedGrade = 2
        )

        assertEquals(
            setOf(LearningDomain.READING, LearningDomain.MATH),
            galaxy.categories.mapTo(linkedSetOf()) { it.domain }
        )
        val reading = galaxy.categories.single { it.domain == LearningDomain.READING }
        assertEquals(listOf("language.stories"), reading.tracks.map { it.id })
        assertTrue(
            galaxy.categories
                .filterNot { it.domain == LearningDomain.READING }
                .flatMap { it.tracks }
                .flatMap { it.lessons }
                .all { it.grade == 2 }
        )
    }

    @Test
    fun lessonConnectionsFollowTheThreeGroupedPartsInOrder() {
        val stagedLesson = lesson("staged").copy(
            phaseOrbs = listOf(
                phase(GalaxyOrbKind.THEORY, "theory"),
                phase(GalaxyOrbKind.PRACTICE, "exercises"),
                phase(GalaxyOrbKind.TEST, "test")
            )
        )
        val layout = GalaxyLayoutEngine.layoutCategory(
            listOf(GalaxyTrackUi("track", "Track", 1, listOf(stagedLesson)))
        )
        val nodeIds = galaxyOrbNodes(stagedLesson).map { it.nodeId }

        assertEquals(
            nodeIds.zipWithNext { from, to -> GalaxyConnection(from, to) },
            layout.connections
        )
    }

    @Test
    fun crossSphereConnectionUsesLastTestAndFirstLessonOnly() {
        val sourceFirst = lesson("source-first")
        val sourceLast = lesson("source-last", prerequisites = listOf("source-first")).copy(
            phaseOrbs = listOf(
                phase(GalaxyOrbKind.THEORY, "source-theory"),
                phase(GalaxyOrbKind.TEST, "source-test-1"),
                phase(GalaxyOrbKind.TEST, "source-test-2")
            )
        )
        val targetFirst = lesson("target-first", prerequisites = listOf("source-last"))
        val invalidTarget = lesson("target-second", prerequisites = listOf("source-first"))
        val layout = GalaxyLayoutEngine.layoutCategory(
            listOf(
                GalaxyTrackUi("source", "Source", 1, listOf(sourceFirst, sourceLast)),
                GalaxyTrackUi("target", "Target", 1, listOf(targetFirst, invalidTarget))
            )
        )
        val lastSourceTest = galaxyOrbNodes(sourceLast).last { it.orb.kind == GalaxyOrbKind.TEST }.nodeId
        val firstTargetNode = galaxyOrbNodes(targetFirst).first().nodeId

        assertTrue(layout.connections.contains(GalaxyConnection(lastSourceTest, firstTargetNode)))
        assertFalse(
            layout.connections.contains(
                GalaxyConnection(
                    galaxyOrbNodeId("source-first", GalaxyOrbKind.TEST),
                    galaxyOrbNodeId("target-second", GalaxyOrbKind.THEORY)
                )
            )
        )
    }

    @Test
    fun crossSphereDependencyIsDynamicallyRoutedThroughGroupBoundaries() {
        val sourceRoot = lesson("source-root")
        val sourceLast = lesson("source-last", prerequisites = listOf(sourceRoot.id))
        val targetFirst = lesson("target-first", prerequisites = listOf(sourceRoot.id))
        val layout = GalaxyLayoutEngine.layoutCategory(
            listOf(
                GalaxyTrackUi("source", "Source", 1, listOf(sourceLast, sourceRoot)),
                GalaxyTrackUi("target", "Target", 1, listOf(targetFirst))
            )
        )

        assertTrue(
            GalaxyConnection(
                galaxyOrbNodeId(sourceLast.id, GalaxyOrbKind.TEST),
                galaxyOrbNodeId(targetFirst.id, GalaxyOrbKind.THEORY)
            ) in layout.connections
        )
        assertFalse(
            layout.connections.any {
                it.fromLessonId == galaxyOrbNodeId(sourceRoot.id, GalaxyOrbKind.TEST) &&
                    it.toLessonId == galaxyOrbNodeId(targetFirst.id, GalaxyOrbKind.THEORY)
            }
        )
    }

    @Test
    fun productionGalaxyRootsHaveCorrectEntryConnectionsAndInternalEdgesNeverRunBackward() {
        val selectedGrades = LearningDomain.entries.associate { it.name to listOf(2) }
        val galaxy = buildLearningGalaxyUiState(
            galaxyProfile(selectedGrades = selectedGrades),
            loadManifest(),
            loadCatalog(),
            emptyList(),
            emptyMap()
        )
        val math = galaxy.categories.single { it.domain == LearningDomain.MATH }
        val mathLayout = GalaxyLayoutEngine.layoutCategory(math.tracks, math.checkpoints)
        val independentRootId =
            "lesson.g2.math.operations.optellen-en-aftrekken-tot-100-zonder-en-met-brug.optellen-zonder-brug"
        val externallyGatedRootId =
            "lesson.g2.math.operations.vermenigvuldigen-als-gelijke-groepen.vermenigvuldigen-als-gelijke-groepen"

        assertTrue(
            GalaxyConnection(
                GALAXY_START_NODE_ID,
                galaxyOrbNodeId(independentRootId, GalaxyOrbKind.THEORY)
            ) in mathLayout.startConnections
        )
        val mathNodeById = mathLayout.nodes.associateBy { it.nodeId }
        val gatedRootNodeId = galaxyOrbNodeId(externallyGatedRootId, GalaxyOrbKind.THEORY)
        assertTrue(
            mathLayout.connections.any { connection ->
                val source = mathNodeById[connection.fromLessonId]
                connection.toLessonId == gatedRootNodeId &&
                    source?.kind == GalaxyOrbKind.TEST &&
                    source.trackId != mathNodeById.getValue(gatedRootNodeId).trackId
            }
        )

        galaxy.categories.forEach { category ->
            val layout = GalaxyLayoutEngine.layoutCategory(category.tracks, category.checkpoints)
            val nodeById = layout.nodes.associateBy { it.nodeId }
            val lessonOrderBySphere = layout.nodes
                .groupBy { it.trackId to it.grade }
                .mapValues { (_, positions) ->
                    positions.map { it.lessonId }.distinct().withIndex().associate { it.value to it.index }
                }
            layout.connections.forEach { connection ->
                val source = nodeById[connection.fromLessonId] ?: return@forEach
                val target = nodeById[connection.toLessonId] ?: return@forEach
                if (source.lessonId == target.lessonId ||
                    source.trackId != target.trackId ||
                    source.grade != target.grade
                ) return@forEach
                val order = lessonOrderBySphere.getValue(source.trackId to source.grade)
                assertTrue(
                    "Backward connection ${source.lessonId} -> ${target.lessonId}",
                    order.getValue(source.lessonId) < order.getValue(target.lessonId)
                )
            }
        }
    }

    @Test
    fun dependencyHighlightIncludesOnlyTheSelectedLockedOrbsIncomingPath() {
        val rootToTheory = GalaxyConnection("root", "theory")
        val theoryToPractice = GalaxyConnection("theory", "practice")
        val practiceToLocked = GalaxyConnection("practice", "locked")
        val lockedToLater = GalaxyConnection("locked", "later")
        val unrelated = GalaxyConnection("other-root", "other")

        assertEquals(
            linkedSetOf(rootToTheory, theoryToPractice, practiceToLocked),
            galaxyDependencyConnections(
                selectedNodeId = "locked",
                connections = listOf(
                    rootToTheory,
                    theoryToPractice,
                    practiceToLocked,
                    lockedToLater,
                    unrelated
                )
            )
        )
    }

    @Test
    fun lessonGroupOutlineFollowsTheOrbPositionsWithoutChangingTheirOrder() {
        val outline = galaxyLessonGroupOutline(
            nodePoints = listOf(
                GalaxyPoint(-82f, 0f),
                GalaxyPoint(0f, -34f),
                GalaxyPoint(82f, 0f)
            ),
            orbPadding = 42f
        )
        val width = outline.maxOf { it.x } - outline.minOf { it.x }
        val height = outline.maxOf { it.y } - outline.minOf { it.y }

        assertTrue(outline.size >= 16)
        assertEquals(-124f, outline.minOf { it.x }, 0.01f)
        assertEquals(124f, outline.maxOf { it.x }, 0.01f)
        assertTrue(width > height * 1.8f)
    }

    @Test
    fun lessonOrbSpacingStaysContinuousWhileInternalGapsBecomeSubtlyTighter() {
        val basePoints = (0..5).map { index -> GalaxyPoint(index * 100f, 0f) }
        val refined = coherentGalaxyLessonOrbPoints(
            basePoints = basePoints,
            lessonIds = listOf("lesson-a", "lesson-a", "lesson-a", "lesson-b", "lesson-b", "lesson-b")
        )
        val gaps = refined.zipWithNext { from, to -> to.x - from.x }
        val internalAverage = listOf(gaps[0], gaps[1], gaps[3], gaps[4]).average()
        val lessonTransition = gaps[2]

        assertEquals(basePoints.first(), refined.first())
        assertEquals(basePoints.last(), refined.last())
        assertTrue(refined.zipWithNext().all { (from, to) -> from.x < to.x })
        assertTrue(internalAverage < lessonTransition)
        assertTrue(internalAverage > lessonTransition * 0.80)
    }

    @Test
    fun eachSubcategoryGetsItsOwnSphereWithLessonsOnItsOrbit() {
        val tracks = listOf(
            GalaxyTrackUi("numbers", "Numbers", 1, listOf(lesson("n-1"), lesson("n-2"))),
            GalaxyTrackUi("money", "Money", 1, listOf(lesson("m-1"), lesson("m-2"), lesson("m-3")))
        )
        val layout = GalaxyLayoutEngine.layoutCategory(tracks)

        assertEquals(setOf("numbers", "money"), layout.trackSpheres.map { it.trackId }.toSet())
        layout.trackSpheres.forEach { sphere ->
            val trackNodes = layout.nodes.filter { it.trackId == sphere.trackId }
            assertTrue(trackNodes.isNotEmpty())
            assertTrue(trackNodes.all { node ->
                hypot(node.point.x - sphere.centre.x, node.point.y - sphere.centre.y) < sphere.radius
            })
        }
        assertEquals(layout.trackSpheres.size, layout.trackSpheres.map { it.centre }.distinct().size)
    }

    @Test
    fun firstAndLastOrbOfALargeSphereStayOnItsOuterRing() {
        val lessons = (1..5).map { index ->
            lesson(
                id = "lesson-$index",
                prerequisites = if (index == 1) emptyList() else listOf("lesson-${index - 1}")
            )
        }
        val layout = GalaxyLayoutEngine.layoutCategory(
            listOf(GalaxyTrackUi("track", "Track", 1, lessons))
        )
        val sphere = layout.trackSpheres.single()
        val distances = layout.nodes.map { node ->
            hypot(node.point.x - sphere.centre.x, node.point.y - sphere.centre.y)
        }
        val outerDistance = distances.maxOrNull() ?: 0f

        assertTrue(kotlin.math.abs(distances.first() - outerDistance) < 0.01f)
        assertTrue(kotlin.math.abs(distances.last() - outerDistance) < 0.01f)
    }

    @Test
    fun largeSphereUsesAChangingInteriorZigZagInsteadOfConcentricRings() {
        val lessons = (1..8).map { index ->
            lesson(
                id = "zigzag-$index",
                prerequisites = if (index == 1) emptyList() else listOf("zigzag-${index - 1}")
            )
        }
        val layout = GalaxyLayoutEngine.layoutCategory(
            listOf(GalaxyTrackUi("zigzag", "Zigzag", 1, lessons))
        )
        val sphere = layout.trackSpheres.single()
        val interiorDistances = layout.nodes.drop(1).dropLast(1).map { node ->
            hypot(node.point.x - sphere.centre.x, node.point.y - sphere.centre.y).toInt()
        }

        assertTrue(interiorDistances.distinct().size >= 5)
        assertTrue(interiorDistances.any { it < sphere.radius * 0.45f })
    }

    @Test
    fun connectedSubcategorySpheresAreOrderedFromPrerequisiteToDependant() {
        val source = GalaxyTrackUi("source", "Source", 1, listOf(lesson("source-lesson")))
        val target = GalaxyTrackUi(
            "target",
            "Target",
            1,
            listOf(lesson("target-lesson", prerequisites = listOf("source-lesson")))
        )

        val layout = GalaxyLayoutEngine.layoutCategory(listOf(target, source))

        assertEquals(listOf("source", "target"), layout.trackSpheres.map { it.trackId })
    }

    @Test
    fun storyThemeSphereIsPlacedFurtherFromStartThanRegularSphere() {
        val regular = GalaxyTrackUi("regular", "Regular", 1, listOf(lesson("regular-lesson")))
        val storyPortal = lesson("story-theme").copy(
            portalDestination = GalaxyPortalDestination.STORY_LIBRARY,
            portalTargetId = null,
            phaseOrbs = listOf(phase(GalaxyOrbKind.PORTAL, "theme"))
        )
        val stories = GalaxyTrackUi(
            "language.stories",
            "Stories",
            1,
            listOf(storyPortal)
        )

        val layout = GalaxyLayoutEngine.layoutCategory(listOf(regular, stories))
        val regularSphere = layout.trackSpheres.single { it.trackId == regular.id }
        val storySphere = layout.trackSpheres.single { it.trackId == stories.id }
        fun distanceFromStart(sphere: GalaxyTrackSpherePosition) = hypot(
            sphere.centre.x - layout.startPoint.x,
            sphere.centre.y - layout.startPoint.y
        )

        assertTrue(distanceFromStart(storySphere) > distanceFromStart(regularSphere) + 200f)
    }

    @Test
    fun checkpointCreatesASeparateGateBetweenPrerequisitesAndTargetLesson() {
        val track = GalaxyTrackUi(
            id = "time",
            title = "Time",
            iconResId = 1,
            lessons = listOf(
                lesson("clock"),
                lesson("measure"),
                lesson("duration", prerequisites = listOf("clock", "measure")).copy(checkpoint = true)
            )
        )
        val checkpoint = GalaxyCheckpointNodeUi(
            id = "checkpoint:duration",
            targetLessonId = "duration",
            grade = 2,
            prerequisiteLessonIds = listOf("clock", "measure"),
            requiredLessonIds = listOf("clock", "measure"),
            unmetPrerequisiteTitles = listOf("clock", "measure"),
            state = GalaxyNodeState.LOCKED,
            progressPercent = 0
        )

        val layout = GalaxyLayoutEngine.layoutCategory(listOf(track), listOf(checkpoint))

        assertEquals(1, layout.checkpointNodes.size)
        assertEquals(
            setOf(
                GalaxyConnection(galaxyOrbNodeId("clock", GalaxyOrbKind.TEST), checkpoint.id),
                GalaxyConnection(galaxyOrbNodeId("measure", GalaxyOrbKind.TEST), checkpoint.id),
                GalaxyConnection(checkpoint.id, galaxyOrbNodeId("duration", GalaxyOrbKind.THEORY))
            ),
            layout.checkpointConnections.toSet()
        )
        assertTrue(
            layout.connections.none {
                it.toLessonId == galaxyOrbNodeId("duration", GalaxyOrbKind.THEORY)
            }
        )
    }

    @Test
    fun checkpointRequirementsIncludeAllEarlierPrerequisiteBranches() {
        assertEquals(
            listOf("left", "root", "right"),
            galaxyCheckpointRequirementIds(
                lessonId = "target",
                prerequisiteIdsByLesson = mapOf(
                    "target" to listOf("left", "right"),
                    "left" to listOf("root"),
                    "right" to listOf("root")
                )
            )
        )
    }

    @Test
    fun hiddenSubcategoryIsAbsentFromGalaxyTracks() {
        val manifest = loadManifest()
        val catalog = loadCatalog()
        val profile = galaxyProfile(
            selectedGrades = LearningDomain.entries.associate { it.name to listOf(1) },
            hiddenSubcategories = listOf("language.dictation")
        )

        val galaxy = buildLearningGalaxyUiState(profile, manifest, catalog, emptyList(), emptyMap())

        assertTrue(galaxy.categories.flatMap { it.tracks }.none { it.id == "language.dictation" })
        assertTrue(
            galaxy.categories.flatMap { it.tracks }.flatMap { it.lessons }
                .none { it.subcategoryId == "language.dictation" }
        )
    }

    @Test
    fun hiddenMainCategoryIsAbsentFromGalaxyUniverse() {
        val profile = galaxyProfile(
            selectedGrades = LearningDomain.entries.associate { it.name to listOf(1) },
            hiddenMainCategories = listOf(LearningDomain.WRITING.name)
        )

        val galaxy = buildLearningGalaxyUiState(
            profile,
            loadManifest(),
            loadCatalog(),
            emptyList(),
            emptyMap()
        )

        assertTrue(galaxy.categories.none { it.domain == LearningDomain.WRITING })
    }

    @Test
    fun filteredCrossCategoryPrerequisiteRemainsLockedLikeThePlayer() {
        val manifest = loadManifest()
        val catalog = loadCatalog()
        val profile = galaxyProfile(
            selectedGrades = LearningDomain.entries.associate { domain ->
                domain.name to if (domain == LearningDomain.MATH) listOf(2) else listOf(1)
            }
        )

        val galaxy = buildLearningGalaxyUiState(profile, manifest, catalog, emptyList(), emptyMap())
        val moneyLesson = galaxy.categories.flatMap { it.tracks }.flatMap { it.lessons }
            .first { it.id == "lesson.g2.math.measurement.geld-rekenen-tot-100-euro.bedragen-samenstellen" }

        assertEquals(GalaxyNodeState.LOCKED, moneyLesson.state)
        assertTrue(moneyLesson.unmetPrerequisiteTitles.isNotEmpty())
    }

    private fun lesson(
        id: String,
        prerequisites: List<String> = emptyList(),
        grade: Int = 1
    ) = GalaxyLessonNodeUi(
        id = id,
        title = id,
        categoryTitle = "Math",
        subcategoryId = "numbers",
        subcategoryTitle = "Numbers",
        grade = grade,
        sequence = 1,
        fallbackIconResId = 1,
        iconAssetPath = null,
        prerequisiteLessonIds = prerequisites,
        unmetPrerequisiteTitles = emptyList(),
        state = GalaxyNodeState.AVAILABLE,
        progressPercent = 0
    )

    private fun phase(kind: GalaxyOrbKind, stageId: String) = GalaxyPhaseOrbUi(
        kind = kind,
        state = GalaxyNodeState.AVAILABLE,
        progressPercent = 0,
        stageId = stageId
    )

    private fun inProgress(
        unitId: String,
        lessonId: String,
        currentStageType: LessonStageTypeV2
    ) = LessonProgress(
        progressId = "progress-$lessonId",
        childProfileId = "galaxy-child",
        unitId = unitId,
        lessonId = lessonId,
        state = LessonProgressState.IN_PROGRESS,
        masteryLevel = LessonMasteryLevel.DEVELOPING,
        masteryPercent = 0,
        bestScorePercent = 0,
        latestScorePercent = 0,
        attemptCount = 0,
        startedAtUtc = 1L,
        completedAtUtc = null,
        lastActivityAtUtc = 1L,
        updatedAtUtc = 1L,
        syncId = "sync-$lessonId",
        syncStatus = SyncStatus.PENDING,
        revision = 1L,
        gateStateV2 = LessonGateStateV2.IN_PROGRESS,
        currentStageTypeV2 = currentStageType
    )

    private fun loadManifest() = sharedManifest

    private fun loadCatalog() = sharedCatalog

    private fun galaxyProfile(
        selectedGrades: Map<String, List<Int>>,
        hiddenSubcategories: List<String> = emptyList(),
        hiddenMainCategories: List<String> = emptyList()
    ) = ChildProfile(
        id = "galaxy-child",
        name = "Galaxy",
        avatarId = "1",
        rocketColorId = "1",
        birthYear = 2018,
        selectedGrades = selectedGrades,
        hiddenSubcategories = hiddenSubcategories,
        hiddenMainCategories = hiddenMainCategories
    )

    companion object {
        private val sharedManifest by lazy {
            assetFile("src/main/assets/${CurriculumManifestLoader.ASSET_PATH}")
                .inputStream().use(CurriculumManifestLoader::fromStream)
        }
        private val sharedCatalog by lazy {
            LessonCatalogV2Loader.fromFile(
                assetFile("src/main/assets/${LessonCatalogV2Loader.ASSET_PATH}")
            )
        }

        private fun assetFile(relativePath: String): File {
            val candidates = listOf(File(relativePath), File("app/$relativePath"))
            return candidates.firstOrNull(File::exists) ?: error("Missing test asset: $relativePath")
        }
    }
}
