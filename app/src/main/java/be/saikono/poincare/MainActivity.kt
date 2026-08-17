package be.saikono.poincare

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import be.saikono.poincare.data.audio.AudioService
import be.saikono.poincare.data.audit.AuditLog
import be.saikono.poincare.data.local.PoincareDatabase
import be.saikono.poincare.data.settings.SettingsDataStore
import be.saikono.poincare.domain.adaptive.AdaptiveDifficultyEngine
import be.saikono.poincare.model.LearningDomain
import be.saikono.poincare.model.LocalAppLanguage
import be.saikono.poincare.model.SkillLevel
import be.saikono.poincare.model.isMainCategoryEnabled
import be.saikono.poincare.model.levelProgressFor
import be.saikono.poincare.repository.AssetContentRepository
import be.saikono.poincare.repository.AssetHomeworkTaskRepository
import be.saikono.poincare.repository.AssetStoryRepository
import be.saikono.poincare.repository.LocalProfileRepository
import be.saikono.poincare.repository.LocalProgressRepository
import be.saikono.poincare.repository.LocalLessonProgressRepository
import be.saikono.poincare.ui.Screen
import be.saikono.poincare.ui.home.SubCategorySelectionScreen
import be.saikono.poincare.ui.galaxy.LearningGalaxyScreen
import be.saikono.poincare.ui.galaxy.nextLessonCandidates
import be.saikono.poincare.ui.math.MathScreen
import be.saikono.poincare.ui.math.MathViewModel
import be.saikono.poincare.ui.math.MathViewModelFactory
import be.saikono.poincare.ui.lesson.LessonHubScreen
import be.saikono.poincare.ui.lesson.LessonIntroScreen
import be.saikono.poincare.ui.lesson.LessonPlayerScreen
import be.saikono.poincare.ui.lesson.LessonViewModel
import be.saikono.poincare.ui.lesson.LessonViewModelFactory
import be.saikono.poincare.ui.parent.ParentDashboardScreen
import be.saikono.poincare.ui.parent.ParentPinGate
import be.saikono.poincare.ui.parent.ParentViewModel
import be.saikono.poincare.ui.parent.ParentViewModelFactory
import be.saikono.poincare.ui.profile.CreateProfileScreen
import be.saikono.poincare.ui.profile.ProfileSelectionScreen
import be.saikono.poincare.ui.profile.ProfileViewModel
import be.saikono.poincare.ui.profile.ProfileViewModelFactory
import be.saikono.poincare.ui.puzzle.PuzzleScreen
import be.saikono.poincare.ui.puzzle.PuzzleViewModel
import be.saikono.poincare.ui.puzzle.PuzzleViewModelFactory
import be.saikono.poincare.ui.reading.ReadingScreen
import be.saikono.poincare.ui.reading.ReadingViewModel
import be.saikono.poincare.ui.reading.ReadingViewModelFactory
import be.saikono.poincare.ui.spaceMap.SpaceMapScreen
import be.saikono.poincare.ui.spaceMap.SpaceMapViewModel
import be.saikono.poincare.ui.spaceMap.SpaceMapViewModelFactory
import be.saikono.poincare.ui.story.StoryLibraryScreen
import be.saikono.poincare.ui.story.StoryBookScreen
import be.saikono.poincare.ui.story.StoryReaderScreen
import be.saikono.poincare.ui.story.StoryViewModel
import be.saikono.poincare.ui.story.StoryViewModelFactory
import be.saikono.poincare.ui.tasks.TasksScreen
import be.saikono.poincare.ui.tasks.TasksViewModel
import be.saikono.poincare.ui.tasks.TasksViewModelFactory
import be.saikono.poincare.ui.theme.PoincareTheme
import be.saikono.poincare.ui.writing.WritingScreen
import be.saikono.poincare.ui.writing.WritingViewModel
import be.saikono.poincare.ui.writing.WritingViewModelFactory
import be.saikono.poincare.data.sync.SyncManager
import be.saikono.poincare.ui.puzzle.orbitbuilder.OrbitBuilderScreen
import be.saikono.poincare.ui.puzzle.orbitbuilder.OrbitBuilderViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        try {
            super.onCreate(savedInstanceState)
            enableEdgeToEdge()
            WindowCompat.setDecorFitsSystemWindows(window, false)
            val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)
            windowInsetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            windowInsetsController.hide(WindowInsetsCompat.Type.systemBars())

            AuditLog.initialize(applicationContext)

            val database = PoincareDatabase.build(applicationContext)

            val profileRepository = LocalProfileRepository(database.profileDao())
            val progressRepository = LocalProgressRepository(database.progressDao())
            val lessonProgressRepository = LocalLessonProgressRepository(database)
            val contentRepository = AssetContentRepository(applicationContext)
            val storyRepository = AssetStoryRepository(applicationContext)
            val homeworkTaskRepository = AssetHomeworkTaskRepository(applicationContext)
            val settingsDataStore = SettingsDataStore(applicationContext)
            val audioService = AudioService(applicationContext, settingsDataStore, profileRepository)
            val syncManager = SyncManager(applicationContext)
            val adaptiveEngine = AdaptiveDifficultyEngine()

            val profileViewModelFactory = ProfileViewModelFactory(profileRepository, settingsDataStore, contentRepository)
            val spaceMapViewModelFactory = SpaceMapViewModelFactory(
                applicationContext,
                profileRepository,
                progressRepository,
                lessonProgressRepository,
                settingsDataStore,
                contentRepository
            )
            val mathViewModelFactory = MathViewModelFactory(
                contentRepository,
                progressRepository,
                settingsDataStore,
                syncManager,
                adaptiveEngine,
                audioService,
                profileRepository
            )
            val writingViewModelFactory = WritingViewModelFactory(
                contentRepository,
                progressRepository,
                settingsDataStore,
                syncManager,
                audioService,
                profileRepository
            )
            val readingViewModelFactory = ReadingViewModelFactory(
                contentRepository,
                progressRepository,
                settingsDataStore,
                syncManager,
                audioService,
                profileRepository
            )
            val storyViewModelFactory = StoryViewModelFactory(
                storyRepository,
                settingsDataStore,
                profileRepository,
                audioService
            )
            val puzzleViewModelFactory = PuzzleViewModelFactory(
                contentRepository,
                progressRepository,
                settingsDataStore,
                syncManager,
                audioService,
                profileRepository
            )
            val parentViewModelFactory = ParentViewModelFactory(
                profileRepository,
                progressRepository,
                settingsDataStore,
                audioService
            )
            val tasksViewModelFactory = TasksViewModelFactory(homeworkTaskRepository)
            val lessonViewModelFactory = LessonViewModelFactory(
                applicationContext,
                profileRepository,
                settingsDataStore,
                lessonProgressRepository,
                contentRepository,
                audioService
            )

            setContent {
                val appLanguage by settingsDataStore.appLanguage.collectAsState(initial = "nl")
                PoincareTheme {
                    CompositionLocalProvider(LocalAppLanguage provides (appLanguage ?: "nl")) {
                        PoincareApp(
                            profileViewModelFactory,
                            spaceMapViewModelFactory,
                            mathViewModelFactory,
                            writingViewModelFactory,
                            readingViewModelFactory,
                            storyViewModelFactory,
                            puzzleViewModelFactory,
                            parentViewModelFactory,
                            lessonViewModelFactory,
                            tasksViewModelFactory
                        )
                    }
                }
            }
        } catch (error: Throwable) {
            Log.e("PoincareStartup", "MainActivity startup failed", error)
            throw error
        }
    }
}

@Composable
fun PoincareApp(
    profileViewModelFactory: ProfileViewModelFactory,
    spaceMapViewModelFactory: SpaceMapViewModelFactory,
    mathViewModelFactory: MathViewModelFactory,
    writingViewModelFactory: WritingViewModelFactory,
    readingViewModelFactory: ReadingViewModelFactory,
    storyViewModelFactory: StoryViewModelFactory,
    puzzleViewModelFactory: PuzzleViewModelFactory,
    parentViewModelFactory: ParentViewModelFactory,
    lessonViewModelFactory: LessonViewModelFactory,
    tasksViewModelFactory: TasksViewModelFactory
) {
    val navController = rememberNavController()
    val profileViewModel: ProfileViewModel = viewModel(factory = profileViewModelFactory)
    val spaceMapViewModel: SpaceMapViewModel = viewModel(factory = spaceMapViewModelFactory)
    val mathViewModel: MathViewModel = viewModel(factory = mathViewModelFactory)
    val writingViewModel: WritingViewModel = viewModel(factory = writingViewModelFactory)
    val readingViewModel: ReadingViewModel = viewModel(factory = readingViewModelFactory)
    val storyViewModel: StoryViewModel = viewModel(factory = storyViewModelFactory)
    val puzzleViewModel: PuzzleViewModel = viewModel(factory = puzzleViewModelFactory)
    val orbitBuilderViewModel: OrbitBuilderViewModel = viewModel()
    val parentViewModel: ParentViewModel = viewModel(factory = parentViewModelFactory)
    val lessonViewModel: LessonViewModel = viewModel(factory = lessonViewModelFactory)
    val tasksViewModel: TasksViewModel = viewModel(factory = tasksViewModelFactory)

    Box(modifier = Modifier.fillMaxSize()) {
        NavHost(navController = navController, startDestination = Screen.ProfileSelection.route) {
            composable(Screen.ProfileSelection.route) {
                ProfileSelectionScreen(
                    viewModel = profileViewModel,
                    onCreateProfile = { navController.navigate(Screen.CreateProfile.route) },
                    onProfileSelected = { AuditLog.record("profile_selected"); navController.navigate(Screen.SpaceMap.route) }
                )
            }

            composable(Screen.CreateProfile.route) {
                CreateProfileScreen(
                    viewModel = profileViewModel,
                    onProfileCreated = { navController.navigate(Screen.SpaceMap.route) },
                    onCancel = { navController.popBackStack() }
                )
            }

            composable(Screen.SpaceMap.route) {
                SpaceMapScreen(
                    viewModel = spaceMapViewModel,
                    onNavigateToMath = { AuditLog.record("category_opened", domain = LearningDomain.MATH.name); navController.navigate(Screen.SubCategorySelection.createRoute(LearningDomain.MATH.name)) },
                    onNavigateToReading = { AuditLog.record("category_opened", domain = LearningDomain.READING.name); navController.navigate(Screen.SubCategorySelection.createRoute(LearningDomain.READING.name)) },
                    onNavigateToWriting = { AuditLog.record("category_opened", domain = LearningDomain.WRITING.name); navController.navigate(Screen.SubCategorySelection.createRoute(LearningDomain.WRITING.name)) },
                    onNavigateToPuzzle = { AuditLog.record("category_opened", domain = LearningDomain.PUZZLE.name); navController.navigate(Screen.SubCategorySelection.createRoute(LearningDomain.PUZZLE.name)) },
                    onNavigateToWorldKnowledge = { AuditLog.record("category_opened", domain = LearningDomain.WORLD_KNOWLEDGE.name); navController.navigate(Screen.SubCategorySelection.createRoute(LearningDomain.WORLD_KNOWLEDGE.name)) },
                    onNavigateToGeometry = { AuditLog.record("category_opened", domain = LearningDomain.GEOMETRY.name); navController.navigate(Screen.SubCategorySelection.createRoute(LearningDomain.GEOMETRY.name)) },
                    onNavigateToParent = { AuditLog.record("parent_dashboard_opened"); navController.navigate(Screen.ParentDashboard.route) },
                    onNavigateToTasks = { AuditLog.record("tasks_opened"); navController.navigate(Screen.Tasks.route) },
                    onSwitchProfile = { navController.navigate(Screen.ProfileSelection.route) },
                    onSwitchInterface = {
                        AuditLog.record("learning_galaxy_opened")
                        navController.navigate(Screen.LearningGalaxy.route)
                    }
                )
            }

            composable(Screen.LearningGalaxy.route) {
                val storyUiState by storyViewModel.uiState.collectAsState()
                LaunchedEffect(Unit) { storyViewModel.refreshLibrary() }
                LearningGalaxyScreen(
                    spaceMapViewModel = spaceMapViewModel,
                    lessonViewModel = lessonViewModel,
                    onSwitchToTiles = { navController.popBackStack() },
                    onNavigateToParent = {
                        AuditLog.record("parent_dashboard_opened")
                        navController.navigate(Screen.ParentDashboard.route)
                    },
                    onNavigateToTasks = {
                        AuditLog.record("tasks_opened")
                        navController.navigate(Screen.Tasks.route)
                    },
                    onSwitchProfile = { navController.navigate(Screen.ProfileSelection.route) },
                    onOpenLesson = { lessonId, stageId ->
                        AuditLog.record(
                            action = "lesson_opened_from_galaxy",
                            profileId = spaceMapViewModel.uiState.value.currentProfile?.id,
                            details = buildMap {
                                put("lessonId", lessonId)
                                stageId?.let { put("stageId", it) }
                            }
                        )
                        navController.navigate(
                            stageId?.let { Screen.LessonPlayer.createRoute(lessonId, it) }
                                ?: Screen.LessonIntro.createRoute(lessonId)
                        )
                    },
                    onOpenStoryLibrary = {
                        AuditLog.record(
                            action = "story_library_opened_from_galaxy",
                            profileId = spaceMapViewModel.uiState.value.currentProfile?.id,
                            domain = LearningDomain.READING.name,
                            details = mapOf("subcategoryId" to "language.stories")
                        )
                        navController.navigate(Screen.StoryLibrary.route)
                    },
                    onOpenStoryBook = { bookId ->
                        AuditLog.record(
                            action = "story_book_opened_from_galaxy",
                            profileId = spaceMapViewModel.uiState.value.currentProfile?.id,
                            domain = LearningDomain.READING.name,
                            details = mapOf("bookId" to bookId, "subcategoryId" to "language.stories")
                        )
                        navController.navigate(Screen.StoryBook.createRoute(bookId))
                    },
                    storyBooks = storyUiState.books.map { it.book },
                    onOpenExistingCategory = { domain ->
                        AuditLog.record("galaxy_category_fallback_opened", domain = domain.name)
                        navController.navigate(Screen.SubCategorySelection.createRoute(domain.name))
                    }
                )
            }

            composable(Screen.SubCategorySelection.route) { backStackEntry ->
                val domain = backStackEntry.arguments?.getString("domain") ?: ""
                val spaceMapUiState by spaceMapViewModel.uiState.collectAsState()
                val currentLevel = when (domain) {
                    LearningDomain.MATH.name -> if (spaceMapUiState.currentProfile?.isMainCategoryEnabled(LearningDomain.MATH) != false) spaceMapUiState.currentProfile?.mathLevel ?: SkillLevel.NOT_APPLICABLE else SkillLevel.NOT_APPLICABLE
                    LearningDomain.READING.name -> if (spaceMapUiState.currentProfile?.isMainCategoryEnabled(LearningDomain.READING) != false) spaceMapUiState.currentProfile?.readingLevel ?: SkillLevel.NOT_APPLICABLE else SkillLevel.NOT_APPLICABLE
                    LearningDomain.WRITING.name -> if (spaceMapUiState.currentProfile?.isMainCategoryEnabled(LearningDomain.WRITING) != false) spaceMapUiState.currentProfile?.writingLevel ?: SkillLevel.NOT_APPLICABLE else SkillLevel.NOT_APPLICABLE
                    LearningDomain.PUZZLE.name -> if (spaceMapUiState.currentProfile?.isMainCategoryEnabled(LearningDomain.PUZZLE) != false) SkillLevel.FIRST_GRADE else SkillLevel.NOT_APPLICABLE
                    LearningDomain.WORLD_KNOWLEDGE.name -> if (spaceMapUiState.currentProfile?.isMainCategoryEnabled(LearningDomain.WORLD_KNOWLEDGE) != false) SkillLevel.FIRST_GRADE else SkillLevel.NOT_APPLICABLE
                    LearningDomain.GEOMETRY.name -> if (spaceMapUiState.currentProfile?.isMainCategoryEnabled(LearningDomain.GEOMETRY) != false) spaceMapUiState.currentProfile?.geometryLevel ?: SkillLevel.NOT_APPLICABLE else SkillLevel.NOT_APPLICABLE
                    else -> SkillLevel.NOT_APPLICABLE
                }
                val activeDomain = runCatching { LearningDomain.valueOf(domain) }.getOrNull()
                val nextLessons = spaceMapUiState.learningGalaxy.categories
                    .firstOrNull { it.domain == activeDomain }
                    ?.nextLessonCandidates()
                    .orEmpty()

                SubCategorySelectionScreen(
                    domain = domain,
                    curriculumManifest = spaceMapUiState.curriculumManifest,
                    currentLevel = currentLevel,
                    categoryProgress = when (domain) {
                        LearningDomain.MATH.name -> spaceMapUiState.categoryProgress[LearningDomain.MATH]
                        LearningDomain.READING.name -> spaceMapUiState.categoryProgress[LearningDomain.READING]
                        LearningDomain.WRITING.name -> spaceMapUiState.categoryProgress[LearningDomain.WRITING]
                        LearningDomain.PUZZLE.name -> spaceMapUiState.categoryProgress[LearningDomain.PUZZLE]
                        LearningDomain.WORLD_KNOWLEDGE.name -> spaceMapUiState.categoryProgress[LearningDomain.WORLD_KNOWLEDGE]
                        LearningDomain.GEOMETRY.name -> spaceMapUiState.categoryProgress[LearningDomain.GEOMETRY]
                        else -> null
                    } ?: levelProgressFor(0),
                    categoryProgressPercent = activeDomain
                        ?.let { spaceMapUiState.categoryLessonProgressPercent[it] }
                        ?: 0,
                    subcategoryCompletions = spaceMapUiState.subcategoryCompletions,
                    subcategoryEarnedXp = spaceMapUiState.subcategoryEarnedXp,
                    subcategoryGradeCompletions = spaceMapUiState.subcategoryGradeCompletions,
                    subcategoryGradeAvailability = spaceMapUiState.subcategoryGradeAvailability,
                    subcategoryLessonProgressPercent = spaceMapUiState.subcategoryLessonProgressPercent,
                    subcategoryLessonProgressPercentByGrade = spaceMapUiState.subcategoryLessonProgressPercentByGrade,
                    subcategoryLessonGradeAvailability = spaceMapUiState.subcategoryLessonGradeAvailability,
                    profile = spaceMapUiState.currentProfile,
                    onSubCategorySelected = { subId, grade, stars ->
                        val route = if (domain == LearningDomain.PUZZLE.name) {
                            puzzleViewModel.setSelectedGrade(grade)
                            puzzleViewModel.setDifficultyStars(stars?.takeIf { it in 1..3 })
                            puzzleViewModel.setSubcategoryId(subId)
                            Screen.Puzzle.route
                        } else {
                            Screen.LessonHub.createRoute(domain, subId)
                        }
                        AuditLog.record(
                            action = "subcategory_opened",
                            profileId = spaceMapUiState.currentProfile?.id,
                            domain = domain,
                            subcategoryId = subId,
                            details = mapOf("grade" to grade.toString(), "difficultyStars" to (stars?.toString() ?: ""))
                        )
                        navController.navigate(route)
                    },
                    onNavigateToLessons = { subcategoryId ->
                        AuditLog.record(
                            action = "lesson_hub_opened",
                            profileId = spaceMapUiState.currentProfile?.id,
                            domain = domain,
                            subcategoryId = subcategoryId
                        )
                        if (domain == LearningDomain.READING.name && subcategoryId == "language.stories") {
                            navController.navigate(Screen.StoryLibrary.route)
                        } else {
                            navController.navigate(Screen.LessonHub.createRoute(domain, subcategoryId))
                        }
                    },
                    nextLessons = nextLessons,
                    onNextLessonSelected = { lessonId ->
                        AuditLog.record(
                            action = "next_lesson_opened",
                            profileId = spaceMapUiState.currentProfile?.id,
                            domain = domain,
                            details = mapOf("lessonId" to lessonId)
                        )
                        navController.navigate(Screen.LessonIntro.createRoute(lessonId))
                    },
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            composable(Screen.LessonHub.route) { backStackEntry ->
                val domain = backStackEntry.arguments?.getString("domain") ?: ""
                val subcategoryId = backStackEntry.arguments?.getString("subcategoryId") ?: ""
                LessonHubScreen(
                    domain = domain,
                    legacySubcategoryId = subcategoryId,
                    viewModel = lessonViewModel,
                    onLessonSelected = { lessonId ->
                        AuditLog.record(
                            action = "lesson_opened",
                            profileId = spaceMapViewModel.uiState.value.currentProfile?.id,
                            domain = domain,
                            details = mapOf("lessonId" to lessonId)
                        )
                        navController.navigate(Screen.LessonIntro.createRoute(lessonId))
                    },
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            composable(Screen.LessonIntro.route) { backStackEntry ->
                val lessonId = backStackEntry.arguments?.getString("lessonId") ?: ""
                LessonIntroScreen(
                    lessonId = lessonId,
                    viewModel = lessonViewModel,
                    onStartLesson = { stageId ->
                        AuditLog.record(
                            action = "lesson_started",
                            profileId = spaceMapViewModel.uiState.value.currentProfile?.id,
                            details = buildMap {
                                put("lessonId", lessonId)
                                stageId?.let { put("stageId", it) }
                            }
                        )
                        navController.navigate(Screen.LessonPlayer.createRoute(lessonId, stageId))
                    },
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            composable(Screen.LessonPlayer.route) { backStackEntry ->
                val lessonId = backStackEntry.arguments?.getString("lessonId") ?: ""
                val stageId = backStackEntry.arguments?.getString("stageId")
                    ?.takeUnless { it == Screen.LessonPlayer.WHOLE_LESSON }
                LessonPlayerScreen(
                    lessonId = lessonId,
                    stageIdV2 = stageId,
                    viewModel = lessonViewModel,
                    onOpenLetterPractice = { activityId ->
                        writingViewModel.setLessonLetterPracticeActivity(activityId)
                        writingViewModel.setSelectedGrade(1)
                        writingViewModel.setDifficultyStars(null)
                        writingViewModel.setSubcategoryId("letter_tracing")
                        navController.navigate(Screen.Writing.route)
                    },
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            composable(Screen.Math.route) {
                MathScreen(viewModel = mathViewModel, onNavigateBack = { navController.popBackStack() })
            }

            composable(Screen.Writing.route) {
                WritingScreen(viewModel = writingViewModel, onNavigateBack = { navController.popBackStack() })
            }

            composable(Screen.Reading.route) {
                ReadingScreen(viewModel = readingViewModel, onNavigateBack = { navController.popBackStack() })
            }

            composable(Screen.StoryLibrary.route) {
                StoryLibraryScreen(
                    viewModel = storyViewModel,
                    onBookSelected = { bookId ->
                        navController.navigate(Screen.StoryBook.createRoute(bookId))
                    },
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            composable(Screen.StoryBook.route) { backStackEntry ->
                val bookId = backStackEntry.arguments?.getString("bookId") ?: ""
                StoryBookScreen(
                    bookId = bookId,
                    viewModel = storyViewModel,
                    onStorySelected = { storyId ->
                        navController.navigate(Screen.StoryReader.createRoute(storyId))
                    },
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            composable(Screen.StoryReader.route) { backStackEntry ->
                val storyId = backStackEntry.arguments?.getString("storyId") ?: ""
                StoryReaderScreen(
                    storyId = storyId,
                    viewModel = storyViewModel,
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            composable(Screen.Puzzle.route) {
                PuzzleScreen(viewModel = puzzleViewModel, onNavigateBack = { navController.popBackStack() })
            }

            composable(Screen.OrbitBuilder.route) {
                OrbitBuilderScreen(
                    viewModel = orbitBuilderViewModel,
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            composable(Screen.Tasks.route) {
                val parentUiState by parentViewModel.uiState.collectAsState()
                val spaceMapUiState by spaceMapViewModel.uiState.collectAsState()
                val tasksUiState by tasksViewModel.uiState.collectAsState()
                val taskLocale = spaceMapUiState.currentProfile?.appLanguage ?: "nl-BE"
                LaunchedEffect(parentUiState.teacherId, taskLocale) {
                    tasksViewModel.load(parentUiState.teacherId, taskLocale)
                }
                TasksScreen(
                    teacherId = parentUiState.teacherId,
                    locale = taskLocale,
                    tasks = tasksUiState.tasks,
                    isLoading = parentUiState.isLoading || tasksUiState.isLoading,
                    loadFailed = tasksUiState.loadFailed,
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            composable(Screen.ParentDashboard.route) {
                var showPinGate by remember { mutableStateOf(true) }
                val parentUiState by parentViewModel.uiState.collectAsState()
                val spaceMapUiState by spaceMapViewModel.uiState.collectAsState()
                if (showPinGate) {
                    ParentPinGate(
                        correctPin = parentUiState.parentPin ?: "1234",
                        onPinCorrect = { showPinGate = false },
                        onCancel = { navController.popBackStack() }
                    )
                } else {
                    ParentDashboardScreen(
                        viewModel = parentViewModel,
                        curriculumManifest = spaceMapUiState.curriculumManifest,
                        onNavigateBack = { navController.popBackStack() },
                        onProfileDeleted = {
                            navController.navigate(Screen.ProfileSelection.route) {
                                popUpTo(Screen.SpaceMap.route) { inclusive = true }
                            }
                        }
                    )
                }
            }
        }
    }
}
