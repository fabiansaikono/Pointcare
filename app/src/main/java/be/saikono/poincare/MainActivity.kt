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
import androidx.room.Room
import be.saikono.poincare.data.audio.AudioService
import be.saikono.poincare.data.audit.AuditLog
import be.saikono.poincare.data.local.DatabaseMigrations
import be.saikono.poincare.data.local.PoincareDatabase
import be.saikono.poincare.data.settings.SettingsDataStore
import be.saikono.poincare.domain.adaptive.AdaptiveDifficultyEngine
import be.saikono.poincare.model.LearningDomain
import be.saikono.poincare.model.LocalAppLanguage
import be.saikono.poincare.model.SkillLevel
import be.saikono.poincare.model.isMainCategoryEnabled
import be.saikono.poincare.model.levelProgressFor
import be.saikono.poincare.repository.AssetContentRepository
import be.saikono.poincare.repository.LocalProfileRepository
import be.saikono.poincare.repository.LocalProgressRepository
import be.saikono.poincare.ui.Screen
import be.saikono.poincare.ui.home.SubCategorySelectionScreen
import be.saikono.poincare.ui.math.MathScreen
import be.saikono.poincare.ui.math.MathViewModel
import be.saikono.poincare.ui.math.MathViewModelFactory
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

            val database = Room.databaseBuilder(
                applicationContext,
                PoincareDatabase::class.java,
                "poincare-db"
            )
                .addMigrations(DatabaseMigrations.MIGRATION_4_5, DatabaseMigrations.MIGRATION_5_6)
                .fallbackToDestructiveMigration()
                .build()

            val profileRepository = LocalProfileRepository(database.profileDao())
            val progressRepository = LocalProgressRepository(database.progressDao())
            val contentRepository = AssetContentRepository(applicationContext)
            val settingsDataStore = SettingsDataStore(applicationContext)
            val audioService = AudioService(applicationContext, settingsDataStore, profileRepository)
            val syncManager = SyncManager(applicationContext)
            val adaptiveEngine = AdaptiveDifficultyEngine()

            val profileViewModelFactory = ProfileViewModelFactory(profileRepository, settingsDataStore, contentRepository)
            val spaceMapViewModelFactory = SpaceMapViewModelFactory(profileRepository, progressRepository, settingsDataStore, contentRepository)
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
                            puzzleViewModelFactory,
                            parentViewModelFactory
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
    puzzleViewModelFactory: PuzzleViewModelFactory,
    parentViewModelFactory: ParentViewModelFactory
) {
    val navController = rememberNavController()
    val profileViewModel: ProfileViewModel = viewModel(factory = profileViewModelFactory)
    val spaceMapViewModel: SpaceMapViewModel = viewModel(factory = spaceMapViewModelFactory)
    val mathViewModel: MathViewModel = viewModel(factory = mathViewModelFactory)
    val writingViewModel: WritingViewModel = viewModel(factory = writingViewModelFactory)
    val readingViewModel: ReadingViewModel = viewModel(factory = readingViewModelFactory)
    val puzzleViewModel: PuzzleViewModel = viewModel(factory = puzzleViewModelFactory)
    val orbitBuilderViewModel: OrbitBuilderViewModel = viewModel()
    val parentViewModel: ParentViewModel = viewModel(factory = parentViewModelFactory)

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
                    onProfileCreated = { navController.navigate(Screen.SpaceMap.route) }
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
                    onSwitchProfile = { navController.navigate(Screen.ProfileSelection.route) }
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

                SubCategorySelectionScreen(
                    domain = domain,
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
                    subcategoryCompletions = spaceMapUiState.subcategoryCompletions,
                    worldLevelCompletions = spaceMapUiState.worldLevelCompletions,
                    subcategoryAvailability = spaceMapUiState.subcategoryAvailability,
                    profile = spaceMapUiState.currentProfile,
                    onSubCategorySelected = { subId, stars ->
                        val route = when (domain) {
                            LearningDomain.MATH.name -> {
                                mathViewModel.setDifficultyStars(stars.takeIf { it in 1..3 })
                                mathViewModel.setSubcategoryId(subId)
                                Screen.Math.route
                            }
                            LearningDomain.READING.name -> {
                                readingViewModel.setWorldLevel(null)
                                readingViewModel.setDifficultyStars(stars.takeIf { it in 1..3 })
                                readingViewModel.setSubcategoryId(subId)
                                Screen.Reading.route
                            }
                            LearningDomain.WORLD_KNOWLEDGE.name -> {
                                readingViewModel.setWorldLevel(stars.coerceIn(1, 6))
                                readingViewModel.setDifficultyStars(null)
                                readingViewModel.setSubcategoryId(subId)
                                Screen.Reading.route
                            }
                            LearningDomain.WRITING.name -> {
                                writingViewModel.setDifficultyStars(stars.takeIf { it in 1..3 })
                                writingViewModel.setSubcategoryId(subId)
                                Screen.Writing.route
                            }
                            LearningDomain.GEOMETRY.name -> {
                                mathViewModel.setDifficultyStars(stars.takeIf { it in 1..3 })
                                mathViewModel.setSubcategoryId(subId)
                                Screen.Math.route
                            }
                            LearningDomain.PUZZLE.name -> {
                                    puzzleViewModel.setDifficultyStars(stars.takeIf { it in 1..3 })
                                    puzzleViewModel.setSubcategoryId(subId)
                                    Screen.Puzzle.route
                                }
                            else -> Screen.SpaceMap.route
                        }
                        AuditLog.record(
                            action = "subcategory_opened",
                            profileId = spaceMapUiState.currentProfile?.id,
                            domain = domain,
                            subcategoryId = subId,
                            details = mapOf("selection" to stars.toString())
                        )
                        navController.navigate(route)
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

            composable(Screen.Puzzle.route) {
                PuzzleScreen(viewModel = puzzleViewModel, onNavigateBack = { navController.popBackStack() })
            }

            composable(Screen.OrbitBuilder.route) {
                OrbitBuilderScreen(
                    viewModel = orbitBuilderViewModel,
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            composable(Screen.ParentDashboard.route) {
                var showPinGate by remember { mutableStateOf(true) }
                val parentUiState by parentViewModel.uiState.collectAsState()
                if (showPinGate) {
                    ParentPinGate(
                        correctPin = parentUiState.parentPin ?: "1234",
                        onPinCorrect = { showPinGate = false },
                        onCancel = { navController.popBackStack() }
                    )
                } else {
                    ParentDashboardScreen(
                        viewModel = parentViewModel,
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

