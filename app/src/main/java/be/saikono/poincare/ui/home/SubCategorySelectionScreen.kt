package be.saikono.poincare.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import be.saikono.poincare.R
import be.saikono.poincare.data.content.PoincareArticle
import be.saikono.poincare.data.content.PoincareContentParser
import be.saikono.poincare.model.ChildProfile
import be.saikono.poincare.model.LevelProgress
import be.saikono.poincare.model.LearningDomain
import be.saikono.poincare.model.SkillLevel
import be.saikono.poincare.model.SubcategoryCatalog
import be.saikono.poincare.model.formatVisibleNumbers
import be.saikono.poincare.model.gradeNumber
import be.saikono.poincare.model.isSubcategoryEnabled
import be.saikono.poincare.model.levelProgressFor
import be.saikono.poincare.model.selectedGradesFor
import be.saikono.poincare.model.selectedGradesForPuzzle
import be.saikono.poincare.domain.curriculum.manifest.CurriculumManifest
import be.saikono.poincare.ui.components.CurriculumAssetIcon
import be.saikono.poincare.ui.components.MainCategoryHeader
import be.saikono.poincare.ui.components.NextLessonOption
import be.saikono.poincare.ui.components.NextLessonOverviewDialog
import be.saikono.poincare.ui.components.systemNavigationMargin
import be.saikono.poincare.ui.galaxy.GalaxyLessonNodeUi
import be.saikono.poincare.ui.galaxy.GalaxyNodeState
import be.saikono.poincare.ui.spaceMap.SpaceBackground
import be.saikono.poincare.ui.theme.*

data class SubCategoryOption(
    val id: String,
    val nameNl: String,
    val nameEn: String,
    val nameFr: String,
    val targetId: String? = null
) {
    fun localized(locale: String): String = when (languageOf(locale)) {
        "en" -> nameEn
        "fr" -> nameFr
        else -> nameNl
    }
}

data class SubCategory(
    val id: String,
    val nameNl: String,
    val iconResId: Int,
    val exerciseCount: Int? = 100,
    val accentColor: Color = DeepSpaceBlue,
    val options: List<SubCategoryOption> = emptyList(),
    val gradeSequences: Map<Int, Int> = emptyMap(),
    val columnSpan: Int = 1,
    val iconAssetPath: String? = null
)

private data class PendingGradeChoice(
    val subcategory: SubCategory,
    val targetId: String,
    val grades: List<Int>
)

private data class PendingDifficultyChoice(
    val targetId: String,
    val grade: Int
)

internal const val SUBCATEGORY_SCREEN_TOP_PADDING_DP = 20
internal const val SUBCATEGORY_SCREEN_BOTTOM_PADDING_DP = 6
internal const val SUBCATEGORY_FIRST_ROW_TOP_PADDING_DP = 28
internal const val SUBCATEGORY_GRID_BOTTOM_PADDING_DP = 6

internal fun automaticPuzzleGrade(selectedGrades: List<Int>, fallbackGrade: Int?): Int =
    selectedGrades.firstOrNull { it in 1..6 } ?: fallbackGrade?.takeIf { it in 1..6 } ?: 1

@Composable
fun SubCategorySelectionScreen(
    domain: String,
    curriculumManifest: CurriculumManifest? = null,
    currentLevel: SkillLevel,
    categoryProgress: LevelProgress = levelProgressFor(0),
    categoryProgressPercent: Int = 0,
    subcategoryCompletions: Map<String, Int> = emptyMap(),
    subcategoryEarnedXp: Map<String, Int> = emptyMap(),
    subcategoryGradeCompletions: Map<String, Map<Int, Int>> = emptyMap(),
    subcategoryGradeAvailability: Map<String, Set<Int>> = emptyMap(),
    subcategoryLessonProgressPercent: Map<String, Int> = emptyMap(),
    subcategoryLessonProgressPercentByGrade: Map<String, Map<Int, Int>> = emptyMap(),
    subcategoryLessonGradeAvailability: Map<String, Set<Int>> = emptyMap(),
    profile: ChildProfile?,
    onSubCategorySelected: (String, Int, Int?) -> Unit,
    onNavigateToLessons: ((String) -> Unit)? = null,
    nextLessons: List<GalaxyLessonNodeUi> = emptyList(),
    onNextLessonSelected: (String) -> Unit = {},
    onNavigateBack: () -> Unit
) {
    var pendingGradeChoice by remember { mutableStateOf<PendingGradeChoice?>(null) }
    var pendingDifficultyChoice by remember { mutableStateOf<PendingDifficultyChoice?>(null) }
    var pendingMissionChoice by remember { mutableStateOf<SubCategory?>(null) }
    var pendingOptionsSubcategory by remember { mutableStateOf<SubCategory?>(null) }
    var briefingSubcategory by remember { mutableStateOf<SubCategory?>(null) }
    var showNextLessonOverview by remember { mutableStateOf(false) }
    var briefingLocale by remember { mutableStateOf("nl-BE") }
    val context = LocalContext.current
    val articles = remember { loadArticleBriefings(context.assets) }
    val appLanguage = profile?.appLanguage ?: "nl-BE"
    val learningDomain = runCatching { LearningDomain.valueOf(domain) }.getOrNull()
    val selectedGrades = (if (learningDomain == LearningDomain.PUZZLE) {
        profile?.let { child ->
            (child.selectedGradesFor(LearningDomain.MATH) +
                child.selectedGradesFor(LearningDomain.WORLD_KNOWLEDGE)).distinct().sorted()
        }
    } else {
        learningDomain?.let { profile?.selectedGradesFor(it) }
    }).takeIf { !it.isNullOrEmpty() }
        ?: listOfNotNull(currentLevel.gradeNumber())

    fun applicableGrades(targetId: String): List<Int> {
        val targetGrades = if (learningDomain == LearningDomain.PUZZLE) {
            profile?.selectedGradesForPuzzle(targetId).orEmpty()
        } else {
            selectedGrades
        }
        return SubcategoryCatalog.applicableGrades(
            targetId = targetId,
            selectedGrades = targetGrades,
            gradeAvailability = subcategoryGradeAvailability
        )
    }

    fun tileAvailability(targetId: String): SubcategoryTileAvailability =
        resolveSubcategoryTileAvailability(
            targetId = targetId,
            selectedGrades = if (learningDomain == LearningDomain.PUZZLE) {
                profile?.selectedGradesForPuzzle(targetId).orEmpty()
            } else {
                selectedGrades
            },
            extraExerciseGradeAvailability = subcategoryGradeAvailability,
            lessonGradeAvailability = subcategoryLessonGradeAvailability
        )

    fun continueWithGrade(targetId: String, grade: Int) {
        if (domain == LearningDomain.PUZZLE.name) {
            pendingDifficultyChoice = PendingDifficultyChoice(targetId, grade)
        } else {
            onSubCategorySelected(targetId, grade, null)
        }
    }

    fun requestPuzzleDifficulty(subcategory: SubCategory) {
        val sourceGrades = profile?.selectedGradesForPuzzle(subcategory.id).orEmpty()
        pendingDifficultyChoice = PendingDifficultyChoice(
            targetId = subcategory.id,
            grade = automaticPuzzleGrade(sourceGrades, currentLevel.gradeNumber())
        )
    }

    fun requestGrade(subcategory: SubCategory, targetId: String = subcategory.id) {
        val grades = applicableGrades(targetId)
        when (grades.size) {
            0 -> Unit
            1 -> continueWithGrade(targetId, grades.first())
            else -> pendingGradeChoice = PendingGradeChoice(subcategory, targetId, grades)
        }
    }

    val lessonModeAvailable = onNavigateToLessons != null
    val subCategories = getSubCategoriesForDomain(domain, curriculumManifest, appLanguage, selectedGrades).filter { subcategory ->
        profile?.isSubcategoryEnabled(subcategory.id) != false &&
            subcategory.isVisibleForAppLocale(appLanguage) &&
            (lessonModeAvailable || tileAvailability(subcategory.id).visible)
    }

    val domainTitle = curriculumManifest?.categories
        ?.firstOrNull { it.domain == learningDomain }
        ?.title
        ?.get(appLanguage)
        ?: learningDomain?.let { localizedCategoryTitle(it, appLanguage) }
        ?: domain

    if (showNextLessonOverview) {
        NextLessonOverviewDialog(
            lessons = nextLessons.map { lesson ->
                NextLessonOption(
                    id = lesson.id,
                    title = lesson.title,
                    subcategoryTitle = lesson.subcategoryTitle,
                    status = nextLessonStateText(lesson.state, appLanguage)
                )
            },
            locale = appLanguage,
            onLessonSelected = { lessonId ->
                showNextLessonOverview = false
                onNextLessonSelected(lessonId)
            },
            onDismiss = { showNextLessonOverview = false }
        )
    }

    fun startExtraPractice(subcategory: SubCategory) {
        when {
            learningDomain == LearningDomain.PUZZLE -> requestPuzzleDifficulty(subcategory)
            subcategory.options.isNotEmpty() -> pendingOptionsSubcategory = subcategory
            articleForSubcategory(subcategory.id, articles) != null -> pendingMissionChoice = subcategory
            else -> requestGrade(subcategory)
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(DeepSpaceBlue)) {
        SpaceBackground()

        pendingMissionChoice?.let { subcategory ->
            val briefing = articleForSubcategory(subcategory.id, articles)
            AlertDialog(
                onDismissRequest = { pendingMissionChoice = null },
                containerColor = DeepSpaceBlue.copy(alpha = 0.92f),
                titleContentColor = PlanetGold,
                textContentColor = PlanetGold,
                title = { Text(subcategory.localized(appLanguage).nameNl) },
                text = { Text(localizedUiText("briefingChoice", appLanguage)) },
                confirmButton = {
                    Button(
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                        onClick = {
                            pendingMissionChoice = null
                            requestGrade(subcategory)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = DeepSpaceBlue, contentColor = PlanetGold)
                    ) { Text(localizedUiText("startMission", appLanguage)) }
                },
                dismissButton = {
                    if (briefing != null) {
                        TextButton(
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                            onClick = {
                                pendingMissionChoice = null
                                briefingLocale = profile?.supportLanguage?.let(::normalizeBriefingLocale) ?: "nl-BE"
                                briefingSubcategory = subcategory
                            }
                        ) { Text(localizedUiText("missionBriefing", appLanguage)) }
                    }
                }
            )
        }

        briefingSubcategory?.let { subcategory ->
            val briefing = articleForSubcategory(subcategory.id, articles) ?: return@let
            AlertDialog(
                onDismissRequest = { briefingSubcategory = null },
                containerColor = DeepSpaceBlue.copy(alpha = 0.92f),
                titleContentColor = PlanetGold,
                textContentColor = PlanetGold,
                title = { Text(briefing.title(briefingLocale)) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(briefing.summary(briefingLocale), style = MaterialTheme.typography.bodyMedium)
                        briefing.bodyWithoutSummary(briefingLocale).forEach { paragraph ->
                            Text(paragraph, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                },
                confirmButton = {
                    Button(
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                        onClick = {
                            briefingSubcategory = null
                            requestGrade(subcategory)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = DeepSpaceBlue, contentColor = PlanetGold)
                    ) { Text(localizedUiText("startMission", appLanguage)) }
                },
                dismissButton = {
                    TextButton(
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                        onClick = { briefingSubcategory = null }
                    ) { Text(localizedUiText("close", appLanguage)) }
                }
            )
        }

        pendingOptionsSubcategory?.let { subcategory ->
            AlertDialog(
                onDismissRequest = { pendingOptionsSubcategory = null },
                containerColor = DeepSpaceBlue.copy(alpha = 0.92f),
                titleContentColor = PlanetGold,
                textContentColor = PlanetGold,
                title = { Text(localizedUiText("chooseOption", appLanguage)) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        subcategory.options
                            .filter { option ->
                                applicableGrades(option.targetId ?: SubcategoryCatalog.fullFocusId(subcategory.id, option.id)).isNotEmpty()
                            }
                            .forEach { option ->
                            val fullId = option.targetId ?: SubcategoryCatalog.fullFocusId(subcategory.id, option.id)
                            Button(
                                shape = RoundedCornerShape(12.dp),
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                                onClick = {
                                    pendingOptionsSubcategory = null
                                    requestGrade(subcategory, fullId)
                                },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = DeepSpaceBlue,
                                    contentColor = PlanetGold
                                )
                            ) { Text(option.localized(appLanguage), textAlign = TextAlign.Center) }
                        }
                    }
                },
                confirmButton = {},
                dismissButton = {
                    TextButton(
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                        onClick = { pendingOptionsSubcategory = null }
                    ) { Text(localizedUiText("cancel", appLanguage)) }
                }
            )
        }

        pendingGradeChoice?.let { choice ->
            val completedByGrade = subcategoryGradeCompletions[choice.targetId].orEmpty()
            AlertDialog(
                onDismissRequest = { pendingGradeChoice = null },
                containerColor = DeepSpaceBlue.copy(alpha = 0.92f),
                titleContentColor = PlanetGold,
                textContentColor = PlanetGold,
                title = { Text(localizedGradeText("choose", appLanguage)) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        choice.grades.forEach { grade ->
                            val completed = completedByGrade[grade] ?: 0
                            Button(
                                shape = RoundedCornerShape(12.dp),
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                                onClick = {
                                    pendingGradeChoice = null
                                    continueWithGrade(choice.targetId, grade)
                                },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(containerColor = DeepSpaceBlue, contentColor = PlanetGold)
                            ) {
                                Text(
                                    if (completed > 0) "${localizedGradeText("grade", appLanguage)} $grade · $completed ${localizedGradeText("completed", appLanguage)}"
                                    else "${localizedGradeText("grade", appLanguage)} $grade"
                                )
                            }
                        }
                    }
                },
                confirmButton = {},
                dismissButton = {
                    TextButton(
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                        onClick = { pendingGradeChoice = null }
                    ) { Text(localizedUiText("cancel", appLanguage)) }
                }
            )
        }

        pendingDifficultyChoice?.let { choice ->
            AlertDialog(
                onDismissRequest = { pendingDifficultyChoice = null },
                containerColor = DeepSpaceBlue.copy(alpha = 0.92f),
                titleContentColor = PlanetGold,
                textContentColor = PlanetGold,
                title = { Text(localizedUiText("chooseDifficulty", appLanguage)) },
                text = {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        (1..3).forEach { stars ->
                            OutlinedButton(
                                shape = RoundedCornerShape(50),
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                                onClick = {
                                    pendingDifficultyChoice = null
                                    onSubCategorySelected(choice.targetId, choice.grade, stars)
                                },
                                border = BorderStroke(1.dp, PlanetGold),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    containerColor = Color.Transparent,
                                    contentColor = PlanetGold
                                )
                            ) { Text("★".repeat(stars)) }
                        }
                    }
                },
                confirmButton = {},
                dismissButton = {
                    TextButton(
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                        onClick = { pendingDifficultyChoice = null }
                    ) { Text(localizedUiText("cancel", appLanguage)) }
                }
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemNavigationMargin()
                .padding(
                    start = 24.dp,
                    top = SUBCATEGORY_SCREEN_TOP_PADDING_DP.dp,
                    end = 24.dp,
                    bottom = SUBCATEGORY_SCREEN_BOTTOM_PADDING_DP.dp
                ),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            MainCategoryHeader(
                title = domainTitle,
                progressPercent = categoryProgressPercent,
                locale = appLanguage,
                onBack = onNavigateBack,
                onNextLesson = if (nextLessons.isNotEmpty()) {
                    { showNextLessonOverview = true }
                } else {
                    null
                }
            )
            Spacer(modifier = Modifier.height(if (lessonModeAvailable) 10.dp else 20.dp))

            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val isLandscapeGrid = maxWidth > maxHeight
                val tileAspectRatio = if (isLandscapeGrid) 1.15f else 1f
                val gridSpacing = if (isLandscapeGrid) 14.dp else 16.dp
                val gridColumnCount = if (isLandscapeGrid) {
                    4
                } else {
                    ((maxWidth + gridSpacing) / (120.dp + gridSpacing)).toInt().coerceAtLeast(1)
                }
                val defaultTileHeight = (
                    (maxWidth - gridSpacing * (gridColumnCount - 1)) / gridColumnCount
                    ) / tileAspectRatio
                LazyVerticalGrid(
                    columns = if (isLandscapeGrid) GridCells.Fixed(4) else GridCells.Adaptive(minSize = 120.dp),
                    horizontalArrangement = Arrangement.spacedBy(gridSpacing),
                    verticalArrangement = Arrangement.spacedBy(if (isLandscapeGrid) 12.dp else 16.dp),
                    contentPadding = PaddingValues(
                        top = SUBCATEGORY_FIRST_ROW_TOP_PADDING_DP.dp,
                        bottom = SUBCATEGORY_GRID_BOTTOM_PADDING_DP.dp
                    ),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(
                        items = subCategories,
                        key = { it.id },
                        span = { sub -> GridItemSpan(if (sub.columnSpan > 1) maxLineSpan else 1) }
                    ) { sub ->
                        val availability = tileAvailability(sub.id)
                        SubCategoryCard(
                            sub = sub.localized(appLanguage),
                            metricText = subcategoryMetricText(
                                lessonProgressPercent = subcategoryLessonProgressPercent[sub.id]
                                    ?.takeIf { availability.hasLessons },
                                earnedXp = if (learningDomain == LearningDomain.PUZZLE) {
                                    subcategoryEarnedXp[sub.id] ?: 0
                                } else {
                                    null
                                }
                            ),
                            enabled = subcategoryTileEnabled(
                                targetId = sub.id,
                                lessonModeAvailable = lessonModeAvailable,
                                availability = availability
                            ) && isSubcategorySequenceUnlocked(
                                subcategory = sub,
                                subcategories = subCategories,
                                selectedGrades = selectedGrades,
                                lessonGradeAvailability = subcategoryLessonGradeAvailability,
                                lessonProgressPercentByGrade = subcategoryLessonProgressPercentByGrade
                            ),
                            locale = appLanguage,
                            tileAspectRatio = tileAspectRatio,
                            tileHeight = defaultTileHeight.takeIf { sub.columnSpan > 1 }
                        ) {
                            if (learningDomain == LearningDomain.PUZZLE) {
                                if (sub.options.isNotEmpty()) pendingOptionsSubcategory = sub else requestPuzzleDifficulty(sub)
                            } else if (lessonModeAvailable) {
                                requireNotNull(onNavigateToLessons).invoke(sub.id)
                            } else {
                                startExtraPractice(sub)
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun nextLessonStateText(state: GalaxyNodeState, locale: String): String = when (languageOf(locale)) {
    "en" -> when (state) {
        GalaxyNodeState.RECOMMENDED -> "Recommended"
        GalaxyNodeState.IN_PROGRESS -> "In progress"
        else -> "Available"
    }
    "fr" -> when (state) {
        GalaxyNodeState.RECOMMENDED -> "Recommandée"
        GalaxyNodeState.IN_PROGRESS -> "En cours"
        else -> "Disponible"
    }
    else -> when (state) {
        GalaxyNodeState.RECOMMENDED -> "Aanbevolen"
        GalaxyNodeState.IN_PROGRESS -> "Bezig"
        else -> "Beschikbaar"
    }
}

private fun localizedGradeText(key: String, locale: String): String = when (languageOf(locale)) {
    "en" -> when (key) { "choose" -> "Choose a grade"; "grade" -> "Grade"; else -> "completed" }
    "fr" -> when (key) { "choose" -> "Choisis une année"; "grade" -> "Année"; else -> "terminés" }
    else -> when (key) { "choose" -> "Kies een leerjaar"; "grade" -> "Leerjaar"; else -> "voltooid" }
}

private data class MissionBriefing(
    val nlTitle: String,
    val nlSummary: String,
    val nlBody: List<String>,
    val enTitle: String = nlTitle,
    val enSummary: String = nlSummary,
    val enBody: List<String> = nlBody,
    val frTitle: String = nlTitle,
    val frSummary: String = nlSummary,
    val frBody: List<String> = nlBody
) {
    fun title(locale: String): String = when (normalizeBriefingLocale(locale)) {
        "en-US" -> enTitle
        "fr-BE" -> frTitle
        else -> nlTitle
    }

    fun summary(locale: String): String = when (normalizeBriefingLocale(locale)) {
        "en-US" -> enSummary
        "fr-BE" -> frSummary
        else -> nlSummary
    }

    fun body(locale: String): List<String> = when (normalizeBriefingLocale(locale)) {
        "en-US" -> enBody
        "fr-BE" -> frBody
        else -> nlBody
    }

    fun bodyWithoutSummary(locale: String): List<String> {
        val summaryText = summary(locale).trim()
        return body(locale).filterNot { it.trim().equals(summaryText, ignoreCase = true) }.distinct()
    }
}

private fun loadArticleBriefings(assetManager: android.content.res.AssetManager): List<PoincareArticle> = runCatching {
    assetManager.open("poincare/content/articles/poincare-original-math-l4-articles-001.xml").use { stream ->
        PoincareContentParser().parse(stream).articles
    }
}.getOrDefault(emptyList())

private fun articleForSubcategory(subcategoryId: String, articles: List<PoincareArticle>): MissionBriefing? {
    val article = when (subcategoryId) {
        "division-remainder", "multiply-divide", "times-tables" -> articles.firstOrNull { it.skillRef == "MUL-DIV" }
        "fractions" -> articles.firstOrNull { it.skillRef == "FRAC" }
        "money" -> articles.firstOrNull { it.skillRef == "MONEY" }
        else -> null
    } ?: return null
    return MissionBriefing(
        nlTitle = article.title ?: "Mission briefing",
        nlSummary = article.summary.orEmpty(),
        nlBody = article.body,
        enTitle = when (article.skillRef) {
            "MUL-DIV" -> "Division with remainder"
            "FRAC" -> "Comparing fractions"
            "MONEY" -> "Calculating change"
            else -> article.title ?: "Mission briefing"
        },
        enSummary = when (article.skillRef) {
            "MUL-DIV" -> "Sometimes a number cannot be split exactly into equal groups. The part left over is the remainder."
            "FRAC" -> "Fractions with the same denominator can be compared by looking at the numerator."
            "MONEY" -> "You calculate change by subtracting the price from the amount paid."
            else -> article.summary.orEmpty()
        },
        enBody = when (article.skillRef) {
            "MUL-DIV" -> listOf("For 29 ÷ 4, you can make 7 groups of 4. That is 28. There is 1 left over.", "Always check: divisor × quotient + remainder = starting number.")
            "FRAC" -> listOf("When denominators are the same, compare the numerators.", "When numerators are the same, the smaller denominator makes larger pieces.")
            "MONEY" -> listOf("If you pay € 10,00 for something that costs € 7,35, subtract: € 10,00 - € 7,35 = € 2,65.", "You can also count up to the amount paid.")
            else -> article.body
        },
        frTitle = when (article.skillRef) {
            "MUL-DIV" -> "Division avec reste"
            "FRAC" -> "Comparer des fractions"
            "MONEY" -> "Calculer la monnaie"
            else -> article.title ?: "Mission briefing"
        },
        frSummary = when (article.skillRef) {
            "MUL-DIV" -> "Parfois, un nombre ne se partage pas exactement en groupes egaux. Ce qui reste s'appelle le reste."
            "FRAC" -> "Pour comparer des fractions avec le meme denominateur, regarde le numerateur."
            "MONEY" -> "On calcule la monnaie en soustrayant le prix du montant paye."
            else -> article.summary.orEmpty()
        },
        frBody = when (article.skillRef) {
            "MUL-DIV" -> listOf("Pour 29 ÷ 4, on fait 7 groupes de 4. Cela fait 28. Il reste 1.", "Verifie toujours : diviseur × quotient + reste = nombre de depart.")
            "FRAC" -> listOf("Quand les denominateurs sont les memes, compare les numerateurs.", "Quand les numerateurs sont les memes, le plus petit denominateur donne les plus grands morceaux.")
            "MONEY" -> listOf("Si tu paies € 10,00 pour quelque chose qui coute € 7,35, calcule : € 10,00 - € 7,35 = € 2,65.", "Tu peux aussi compter jusqu'au montant paye.")
            else -> article.body
        }
    )
}

private fun normalizeBriefingLocale(locale: String): String = when (locale) {
    "fr", "fr-BE", "fr_BE", "fr-FR", "fr_FR" -> "fr-BE"
    "en", "en-US", "en_US", "en-GB", "en_GB" -> "en-US"
    else -> "nl-BE"
}

@Composable
private fun LessonEntryCard(locale: String, selectedGrades: List<Int>, onClick: () -> Unit) {
    val shape = RoundedCornerShape(18.dp)
    Surface(
        color = DeepSpaceBlue.copy(alpha = .88f),
        contentColor = PlanetGold,
        shape = shape,
        modifier = Modifier.fillMaxWidth().border(1.dp, PlanetGold.copy(alpha = .34f), shape).clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(42.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    localizedLessonEntryText("title", locale),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    localizedLessonEntryText("subtitle", locale),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            if (selectedGrades.distinct().size > 1) {
                Text(
                    selectedGrades.joinToString(" · ") { "${localizedGradeText("grade", locale)} $it" },
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.End
                )
            }
        }
    }
}

@Composable
private fun CategoryProgressBar(progress: LevelProgress) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Level ${progress.level}", color = StarWhite, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            Column(horizontalAlignment = Alignment.End) {
                Text("Level ${progress.level + 1}", color = StarWhite, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                Text(formatVisibleNumbers("${progress.currentLevelPoints}/${progress.pointsNeededForNextLevel}"), color = StarWhite.copy(alpha = 0.7f), style = MaterialTheme.typography.labelSmall)
            }
        }
        LinearProgressIndicator(
            progress = { progress.progressFraction.coerceIn(0f, 1f) },
            color = PlanetBlue,
            trackColor = Color.White.copy(alpha = 0.12f),
            modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp))
        )
    }
}

@Composable
fun SubCategoryCard(
    sub: SubCategory,
    metricText: String? = null,
    enabled: Boolean,
    locale: String,
    tileAspectRatio: Float = 1f,
    tileHeight: Dp? = null,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                tileHeight?.let { Modifier.height(it) }
                    ?: Modifier.aspectRatio(tileAspectRatio)
            )
            .clip(RoundedCornerShape(18.dp))
            .alpha(if (enabled) 1f else 0.4f)
            .clickable(enabled = enabled) { onClick() },
        colors = CardDefaults.cardColors(containerColor = if (enabled) Color.White.copy(alpha = 0.05f) else Color.Gray.copy(alpha = 0.12f))
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            metricText?.let { text ->
                Text(
                    text = text,
                    style = MaterialTheme.typography.titleSmall,
                    color = if (enabled) PlanetGold else StarWhite.copy(alpha = 0.45f),
                    textAlign = TextAlign.End,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.align(Alignment.TopEnd).padding(top = 12.dp, end = 14.dp)
                )
            }

            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                CurriculumAssetIcon(
                    assetPath = sub.iconAssetPath,
                    fallbackIconResId = sub.iconResId,
                    contentDescription = null,
                    modifier = Modifier.size(56.dp)
                )

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = sub.nameNl,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (enabled) StarWhite else StarWhite.copy(alpha = 0.55f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }
        }
    }
}

internal fun subcategoryMetricText(lessonProgressPercent: Int?, earnedXp: Int?): String? {
    if (earnedXp != null) return "${earnedXp.coerceAtLeast(0)} XP"
    return lessonProgressPercent?.let { "${it.coerceIn(0, 100)}%" }
}

internal data class SubcategoryTileAvailability(
    val lessonGrades: List<Int>,
    val extraExerciseGrades: List<Int>
) {
    val hasLessons: Boolean get() = lessonGrades.isNotEmpty()
    val hasExtraExercises: Boolean get() = extraExerciseGrades.isNotEmpty()
    val visible: Boolean get() = hasLessons || hasExtraExercises
}

internal fun subcategoryTileEnabled(
    targetId: String,
    lessonModeAvailable: Boolean,
    availability: SubcategoryTileAvailability
): Boolean = when {
    targetId == "stories" || targetId == "language.stories" -> true
    targetId.startsWith("logic.") -> true
    // Puzzle screens generate a valid board at runtime and therefore do not
    // require a matching lesson or static exercise record in the content index.
    targetId in SubcategoryCatalog.puzzleIds -> true
    lessonModeAvailable -> availability.hasLessons
    else -> availability.hasExtraExercises
}

internal fun resolveSubcategoryTileAvailability(
    targetId: String,
    selectedGrades: List<Int>,
    extraExerciseGradeAvailability: Map<String, Set<Int>>,
    lessonGradeAvailability: Map<String, Set<Int>>
): SubcategoryTileAvailability = SubcategoryTileAvailability(
    lessonGrades = SubcategoryCatalog.applicableGrades(
        targetId = targetId,
        selectedGrades = selectedGrades,
        gradeAvailability = lessonGradeAvailability
    ),
    extraExerciseGrades = SubcategoryCatalog.applicableGrades(
        targetId = targetId,
        selectedGrades = selectedGrades,
        gradeAvailability = extraExerciseGradeAvailability
    )
)

internal fun isSubcategorySequenceUnlocked(
    subcategory: SubCategory,
    subcategories: List<SubCategory>,
    selectedGrades: Collection<Int>,
    lessonGradeAvailability: Map<String, Set<Int>>,
    lessonProgressPercentByGrade: Map<String, Map<Int, Int>>
): Boolean {
    val relevantGrades = selectedGrades.filter { grade ->
        grade in lessonGradeAvailability[subcategory.id].orEmpty()
    }
    if (relevantGrades.isEmpty()) return true
    return relevantGrades.any { grade ->
        val sequence = subcategory.gradeSequences[grade] ?: return@any true
        if ((lessonProgressPercentByGrade[subcategory.id]?.get(grade) ?: 0) > 0) return@any true
        subcategories
            .asSequence()
            .filter { prior -> (prior.gradeSequences[grade] ?: Int.MAX_VALUE) < sequence }
            .filter { prior -> grade in lessonGradeAvailability[prior.id].orEmpty() }
            .all { prior -> (lessonProgressPercentByGrade[prior.id]?.get(grade) ?: 0) == 100 }
    }
}

private fun SubCategory.localized(locale: String): SubCategory = copy(nameNl = localizedSubcategoryName(id, locale) ?: nameNl)

private fun languageOf(locale: String): String = when (locale) {
    "fr", "fr-BE", "fr_BE", "fr-FR", "fr_FR" -> "fr"
    "en", "en-US", "en_US", "en-GB", "en_GB" -> "en"
    else -> "nl"
}

private fun localizedCategoryTitle(domain: LearningDomain, locale: String): String = when (domain) {
    LearningDomain.READING -> when (languageOf(locale)) { "en" -> "Reading & listening"; "fr" -> "Lecture et écoute"; else -> "Lezen & luisteren" }
    LearningDomain.WRITING -> when (languageOf(locale)) { "en" -> "Spelling & grammar"; "fr" -> "Orthographe et grammaire"; else -> "Spelling & grammatica" }
    LearningDomain.MATH -> when (languageOf(locale)) { "en" -> "Numbers"; "fr" -> "Nombres"; else -> "Wiskunde" }
    LearningDomain.PUZZLE -> when (languageOf(locale)) { "en" -> "Puzzles"; "fr" -> "Puzzles"; else -> "Puzzels" }
    LearningDomain.WORLD_KNOWLEDGE -> when (languageOf(locale)) { "en" -> "World knowledge"; "fr" -> "Le Monde"; else -> "Wereldkennis" }
    LearningDomain.GEOMETRY -> when (languageOf(locale)) { "en" -> "Measurement & geometry"; "fr" -> "Mesures et géométrie"; else -> "Meten & meetkunde" }
}

private fun localizedUiText(key: String, locale: String): String = when (languageOf(locale)) {
    "en" -> when (key) {
        "continuePractice" -> "Continue practising"
        "briefingChoice" -> "Read the explanation first or start right away?"
        "startMission" -> "Start mission"
        "missionBriefing" -> "Mission briefing"
        "close" -> "Close"
        "chooseDifficulty" -> "Choose difficulty"
        "chooseOption" -> "Choose an option"
        "cancel" -> "Cancel"
        "back" -> "Back"
        "chooseMission" -> "Choose your mission"
        "completed" -> "completed"
        else -> key
    }
    "fr" -> when (key) {
        "continuePractice" -> "Continuer à s'exercer"
        "briefingChoice" -> "Lire d'abord l'explication ou commencer tout de suite ?"
        "startMission" -> "Commencer la mission"
        "missionBriefing" -> "Briefing de mission"
        "close" -> "Fermer"
        "chooseDifficulty" -> "Choisis la difficulté"
        "chooseOption" -> "Choisis une option"
        "cancel" -> "Annuler"
        "back" -> "Retour"
        "chooseMission" -> "Choisis ta mission"
        "completed" -> "terminé"
        else -> key
    }
    else -> when (key) {
        "continuePractice" -> "Ga verder met oefenen"
        "briefingChoice" -> "Wil je eerst de uitleg lezen of meteen starten?"
        "startMission" -> "Naar de missie"
        "missionBriefing" -> "Mission briefing"
        "close" -> "Sluiten"
        "chooseDifficulty" -> "Kies moeilijkheid"
        "chooseOption" -> "Kies een onderdeel"
        "cancel" -> "Annuleren"
        "back" -> "Terug"
        "chooseMission" -> "Kies je missie"
        "completed" -> "voltooid"
        else -> key
    }
}

private fun localizedLessonEntryText(key: String, locale: String): String = when (languageOf(locale)) {
    "en" -> when (key) {
        "title" -> "Continue with lessons"
        "subtitle" -> "Learn step by step and save your scores"
        "extraPractice" -> "Extra practice"
        else -> key
    }
    "fr" -> when (key) {
        "title" -> "Continuer avec les leçons"
        "subtitle" -> "Apprends étape par étape et enregistre tes résultats"
        "extraPractice" -> "Exercices supplémentaires"
        else -> key
    }
    else -> when (key) {
        "title" -> "Ga verder met lessen"
        "subtitle" -> "Leer stap voor stap en bewaar je scores"
        "extraPractice" -> "Extra oefenen"
        else -> key
    }
}

internal fun localizedSubcategoryName(id: String, locale: String): String? {
    val conceptualName = when (id) {
        "stories" -> Triple("Verhalen", "Stories", "Histoires")
        "vocabulary" -> Triple("Woordenschat Frans", "French vocabulary", "Vocabulaire français")
        "vocabulary_en" -> Triple("Woordenschat Engels", "English vocabulary", "Vocabulaire anglais")
        "temperature" -> Triple("Temperatuur", "Temperature", "Température")
        "traffic_safety" -> Triple("Verkeer, mobiliteit & veiligheid", "Traffic, mobility & safety", "Circulation, mobilité et sécurité")
        "speaking" -> Triple("Spreken & presenteren", "Speaking & presenting", "Expression orale & présentation")
        "statistics" -> Triple("Statistiek & gegevens", "Statistics & data", "Statistiques & données")
        "compare_size" -> Triple("Vergelijken, ordenen & schatten", "Comparing, ordering & estimating", "Comparer, ordonner et estimer")
        "nature" -> Triple("Natuur & levende wereld", "Nature & the living world", "Nature et monde vivant")
        "geography" -> Triple("Aardrijkskunde & ruimte", "Geography & space", "Géographie et espace")
        "science" -> Triple("Wetenschap & onderzoek", "Science & inquiry", "Sciences et recherche")
        "technology" -> Triple("Techniek & ontwerpen", "Technology & design", "Technologie et conception")
        "history" -> Triple("Geschiedenis & tijd", "History & time", "Histoire et temps")
        "human_body" -> Triple("Mens, lichaam & gezondheid", "People, body & health", "Personne, corps et santé")
        "citizenship_society" -> Triple("Samenleving, burgerschap & sociale vaardigheden", "Society, citizenship & social skills", "Société, citoyenneté et compétences sociales")
        "environment_sustainability" -> Triple("Milieu & duurzaamheid", "Environment & sustainability", "Environnement et durabilité")
        "everyday_life" -> Triple("Dagelijks leven & zelfredzaamheid", "Daily life & independence", "Vie quotidienne et autonomie")
        else -> null
    }
    conceptualName?.let { names ->
        return when (languageOf(locale)) {
            "en" -> names.second
            "fr" -> names.third
            else -> names.first
        }
    }
    return when (languageOf(locale)) {
    "en" -> mapOf(
        "addition" to "Addition", "subtraction" to "Subtraction", "multiplication" to "Multiplication", "division" to "Division", "mixed_arithmetic" to "Mixed operations", "number_sequences" to "Number sequences", "statistics" to "Statistics & data", "fractions" to "Fractions", "time" to "Time", "money" to "Money", "measurement" to "Measurement", "compare_size" to "Bigger and smaller", "compare_weight" to "Heavier and lighter", "compare_height" to "Higher and lower", "compare_length" to "Longer and shorter", "compare_distance" to "Farther and closer", "geometry_shapes" to "Shapes and geometry", "reading" to "Reading", "listening" to "Listening", "speaking" to "Speaking & presenting", "dictation" to "Dictation", "vocabulary" to "Vocabulary", "translation_nl_to_en" to "Dutch to English", "translation_nl_to_fr" to "Dutch to French", "translation_en_to_nl" to "English to Dutch", "translation_en_to_fr" to "English to French", "translation_fr_to_nl" to "French to Dutch", "translation_fr_to_en" to "French to English", "letter_tracing" to "Write letters", "writing" to "Writing", "spelling" to "Spelling", "sentence_order" to "Sentence order", "grammar" to "Grammar", "question_words" to "Question words", "singular_plural" to "Singular and plural", "conjugations" to "Conjugations", "verbs" to "Verbs", "past_tense" to "Past tenses", "future_tense" to "Future tenses", "space" to "Space", "nature" to "Nature", "geography" to "Geography", "science" to "Science", "history" to "History", "everyday_life" to "Everyday life", "animals" to "Animals", "human_body" to "Human body", "plants" to "Plants", "weather_climate" to "Weather and climate", "technology" to "Technology", "arts_culture" to "Arts and culture", "citizenship_society" to "Citizenship and society", "environment_sustainability" to "Environment and sustainability", "sudoku" to "Sudoku", "math-crosswords" to "Math crossword", "word-seeker" to "Word seeker", "mothership-dock" to "Mothership dock", "galaxycon" to "Galaxycon", "calculator-puzzle" to "Calculator puzzle", "constellations" to "Constellations",
            "orbit-builder" to "Orbit Builder"
    )[id]
    "fr" -> mapOf(
        "addition" to "Addition", "subtraction" to "Soustraction", "multiplication" to "Multiplication", "division" to "Division", "mixed_arithmetic" to "Operations mixtes", "number_sequences" to "Suites de nombres", "statistics" to "Statistiques & données", "fractions" to "Fractions", "time" to "Heure", "money" to "Argent", "measurement" to "Mesures", "compare_size" to "Plus grand et plus petit", "compare_weight" to "Plus lourd et plus léger", "compare_height" to "Plus haut et plus bas", "compare_length" to "Plus long et plus court", "compare_distance" to "Plus loin et plus près", "geometry_shapes" to "Formes et géométrie", "reading" to "Lecture", "listening" to "Ecoute", "speaking" to "Expression orale & présentation", "dictation" to "Dictée", "vocabulary" to "Vocabulaire", "translation_nl_to_en" to "Neerlandais vers anglais", "translation_nl_to_fr" to "Neerlandais vers francais", "translation_en_to_nl" to "Anglais vers neerlandais", "translation_en_to_fr" to "Anglais vers francais", "translation_fr_to_nl" to "Francais vers neerlandais", "translation_fr_to_en" to "Francais vers anglais", "letter_tracing" to "Écrire les lettres", "writing" to "Ecriture", "spelling" to "Orthographe", "sentence_order" to "Ordre des phrases", "grammar" to "Grammaire", "question_words" to "Mots interrogatifs", "singular_plural" to "Singulier et pluriel", "conjugations" to "Conjugaisons", "verbs" to "Verbes", "past_tense" to "Temps du passé", "future_tense" to "Temps du futur", "space" to "Espace", "nature" to "Nature", "geography" to "Geographie", "science" to "Science", "history" to "Histoire", "everyday_life" to "Vie quotidienne", "animals" to "Animaux", "human_body" to "Corps humain", "plants" to "Plantes", "weather_climate" to "Météo et climat", "technology" to "Technologie", "arts_culture" to "Arts et culture", "citizenship_society" to "Citoyenneté et société", "environment_sustainability" to "Environnement et durabilité", "sudoku" to "Sudoku", "math-crosswords" to "Mots croises de calcul", "word-seeker" to "Cherche-mots", "mothership-dock" to "Mothership dock", "mothership-dock" to "Vaisseau mere", "galaxycon" to "Galaxycon","calculator-puzzle" to "Puzzle calculatrice", "constellations" to "Constellations",
            "orbit-builder" to "Constructeur d’orbite"
    )[id]
    else -> LearningDomain.entries
        .asSequence()
        .flatMap { getSubCategoriesForDomain(it.name).asSequence() }
        .firstOrNull { it.id == id }
        ?.nameNl
    }
}

/**
 * Builds the learner-facing category list from the same manifest used by the
 * curriculum web app. The legacy overload below remains available for editor
 * utilities and migration tests, but the app navigation always supplies a
 * manifest and therefore keeps the canonical IDs and ordering intact.
 */
fun getSubCategoriesForDomain(
    domain: String,
    manifest: CurriculumManifest?,
    locale: String,
    selectedGrades: Collection<Int>? = null
): List<SubCategory> {
    val learningDomain = runCatching { LearningDomain.valueOf(domain) }.getOrNull() ?: return emptyList()
    // Puzzles are standalone runtime experiences, not classic curriculum
    // lessons. Keep their direct catalogue even though the manifest category
    // intentionally has no Theorie/Oefen/Toets subcategories.
    if (learningDomain == LearningDomain.PUZZLE) return getSubCategoriesForDomain(domain)
    val category = manifest?.categories?.firstOrNull { it.domain == learningDomain }
        ?: return getSubCategoriesForDomain(domain)
    val applicableGrades = selectedGrades?.filter { it in 1..6 }?.toSet().orEmpty()
    return category.subcategories
        .mapIndexed { index, subcategory -> index to subcategory }
        .sortedWith(compareBy<Pair<Int, be.saikono.poincare.domain.curriculum.manifest.CurriculumSubcategoryManifest>> { (_, subcategory) ->
            if (learningDomain == LearningDomain.READING && subcategory.id == "language.stories") 0 else 1
        }.thenBy { (_, subcategory) ->
            subcategory.gradeSequences
                .filter { applicableGrades.isEmpty() || it.grade in applicableGrades }
                .minOfOrNull { it.sequence }
                ?: Int.MAX_VALUE
        }.thenBy { it.first })
        .map { (_, subcategory) ->
            SubCategory(
                id = subcategory.id,
                nameNl = subcategory.title.get(locale),
                iconResId = canonicalSubcategoryIcon(subcategory.id, learningDomain),
                exerciseCount = null,
                options = emptyList(),
                gradeSequences = subcategory.gradeSequences.associate { it.grade to it.sequence },
                columnSpan = if (subcategory.id == "language.stories") 2 else 1,
                iconAssetPath = subcategory.iconAssetPath
            )
        }
}

private fun canonicalSubcategoryIcon(id: String, domain: LearningDomain): Int = when (id) {
    "language.early_literacy", "language.technical_reading", "language.reading_comprehension",
    "language.reading_strategies_information", "language.literature_experience", "language.stories" -> R.drawable.ic_topic_reading
    "language.listening_comprehension", "language.speaking_interaction", "language.listening_speaking" -> R.drawable.ic_topic_listening
    "language.writing_expression", "letter_tracing" -> R.drawable.ic_topic_writing
    "language.dictation", "spelling.phonology_letter_sound", "spelling.rules_word_patterns",
    "spelling.capitals_punctuation", "spelling.word_formation_vocabulary" -> R.drawable.ic_topic_spelling
    "language.vocabulary_french", "language.vocabulary_english" -> R.drawable.ic_topic_vocabulary
    "spelling.parts_of_speech", "spelling.sentence_structure", "spelling.language_reflection_variation" -> R.drawable.ic_topic_grammar
    "spelling.verbs_tenses" -> R.drawable.ic_language_verbs_mono
    "math.number_place_value", "math.tables_graphs_statistics" -> R.drawable.ic_math_sequences_new
    "math.addition_subtraction", "math.addition" -> R.drawable.ic_math_addition_new
    "math.subtraction" -> R.drawable.ic_math_subtraction_new
    "math.mixed_calculations" -> R.drawable.ic_math_mixed_new
    "math.multiplication_division" -> R.drawable.ic_math_multiplication_new
    "math.fractions", "math.decimals", "math.ratios_percentages" -> R.drawable.ic_math_fractions_new
    "math.money_financial_literacy" -> R.drawable.ic_math_money_new
    "math.problem_solving" -> R.drawable.ic_math_mixed_new
    "geometry.time_calendar" -> R.drawable.ic_topic_time
    "geometry.shapes_lines_angles", "geometry.spatial_orientation_coordinates", "geometry.patterns_orientation",
    "geometry.symmetry_patterns_transformations" -> R.drawable.ic_topic_shapes
    "geometry.compare_order_estimate", "geometry.length_perimeter_scale", "geometry.area",
    "geometry.capacity_volume", "geometry.mass_weight", "geometry.weight_volume", "geometry.temperature" -> R.drawable.ic_topic_measure
    "world.nature_living_world" -> R.drawable.ic_world_nature_mono
    "world.science_inquiry" -> R.drawable.ic_world_science_mono
    "world.technology_design", "world.digital_media_literacy" -> R.drawable.ic_world_technology_mono
    "world.geography_space" -> R.drawable.ic_world_geography_mono
    "world.history_time" -> R.drawable.ic_world_history_mono
    "world.body_health" -> R.drawable.ic_world_human_body_mono
    "world.citizenship_social", "world.traffic_mobility_safety" -> R.drawable.ic_world_citizenship_society_mono
    "world.environment_sustainability" -> R.drawable.ic_world_environment_sustainability_mono
    "world.daily_life_self_reliance" -> R.drawable.ic_world_everyday_life_mono
    else -> when (domain) {
        LearningDomain.READING -> R.drawable.ic_topic_reading
        LearningDomain.WRITING -> R.drawable.ic_topic_writing
        LearningDomain.MATH -> R.drawable.ic_math_mixed_new
        LearningDomain.GEOMETRY -> R.drawable.ic_topic_shapes
        LearningDomain.WORLD_KNOWLEDGE -> R.drawable.ic_world_science_mono
        LearningDomain.PUZZLE -> R.drawable.ic_puzzle_sudoku_mono
    }
}

fun getSubCategoriesForDomain(domain: String): List<SubCategory> {
    return when (domain) {
        LearningDomain.MATH.name -> listOf(
            SubCategory("addition", "Optellen", R.drawable.ic_math_addition_new, null),
            SubCategory("subtraction", "Aftrekken", R.drawable.ic_math_subtraction_new, null),
            SubCategory("multiplication", "Vermenigvuldigen", R.drawable.ic_math_multiplication_new, null),
            SubCategory("division", "Delen", R.drawable.ic_math_division_new, null),
            SubCategory("mixed_arithmetic", "Gemengde bewerkingen", R.drawable.ic_math_mixed_new, null),
            SubCategory("number_sequences", "Getallenreeksen", R.drawable.ic_math_sequences_new, null),
            SubCategory("statistics", "Statistiek & gegevens", R.drawable.ic_math_sequences_new, null),
            SubCategory("fractions", "Breuken", R.drawable.ic_math_fractions_new, null),
            SubCategory("money", "Geld", R.drawable.ic_math_money_new, null),
        )
        LearningDomain.GEOMETRY.name -> listOf(
            SubCategory("time", "Tijd", R.drawable.ic_topic_time),
            SubCategory("measurement", "Meten", R.drawable.ic_topic_measure),
            SubCategory("temperature", "Temperatuur", R.drawable.ic_topic_measure),
            SubCategory(
                "compare_size",
                "Vergelijken, ordenen & schatten",
                R.drawable.ic_topic_measure,
                options = measurementComparisonOptions()
            ),
            SubCategory("geometry_shapes", "Vormen en meetkunde", R.drawable.ic_topic_shapes)
        )
        LearningDomain.READING.name -> listOf(
            SubCategory("stories", "Verhalen", R.drawable.ic_topic_reading, columnSpan = 2),
            SubCategory("reading", "Lezen", R.drawable.ic_topic_reading),
            SubCategory("listening", "Luisteren", R.drawable.ic_topic_listening),
            SubCategory("speaking", "Spreken & presenteren", R.drawable.ic_topic_listening),
            SubCategory("writing", "Schrijven", R.drawable.ic_topic_writing),
            SubCategory("dictation", "Dictee", R.drawable.ic_topic_spelling, null),
            SubCategory("vocabulary", "Woordenschat Frans", R.drawable.ic_topic_vocabulary),
            SubCategory("vocabulary_en", "Woordenschat Engels", R.drawable.ic_topic_vocabulary),
            SubCategory("translation_nl_to_en", "Nederlands naar Engels", R.drawable.ic_topic_vocabulary),
            SubCategory("translation_nl_to_fr", "Nederlands naar Frans", R.drawable.ic_topic_vocabulary),
            SubCategory("translation_en_to_nl", "Engels naar Nederlands", R.drawable.ic_topic_vocabulary),
            SubCategory("translation_en_to_fr", "Engels naar Frans", R.drawable.ic_topic_vocabulary),
            SubCategory("translation_fr_to_nl", "Frans naar Nederlands", R.drawable.ic_topic_vocabulary),
            SubCategory("translation_fr_to_en", "Frans naar Engels", R.drawable.ic_topic_vocabulary)
        )
        LearningDomain.WORLD_KNOWLEDGE.name -> listOf(
            SubCategory("space", "Ruimte", R.drawable.ic_world_space_mono),
            SubCategory("nature", "Natuur & levende wereld", R.drawable.ic_world_nature_mono, options = worldNatureOptions()),
            SubCategory("animals", "Dieren", R.drawable.ic_world_animals_mono),
            SubCategory("plants", "Planten", R.drawable.ic_world_plants_mono),
            SubCategory("weather_climate", "Weer en klimaat", R.drawable.ic_world_weather_climate_mono),
            SubCategory("science", "Wetenschap & onderzoek", R.drawable.ic_world_science_mono),
            SubCategory("technology", "Techniek & ontwerpen", R.drawable.ic_world_technology_mono),
            SubCategory("geography", "Aardrijkskunde & ruimte", R.drawable.ic_world_geography_mono, options = worldGeographyOptions()),
            SubCategory("history", "Geschiedenis & tijd", R.drawable.ic_world_history_mono),
            SubCategory("human_body", "Mens, lichaam & gezondheid", R.drawable.ic_world_human_body_mono),
            SubCategory("citizenship_society", "Samenleving, burgerschap & sociale vaardigheden", R.drawable.ic_world_citizenship_society_mono),
            SubCategory("traffic_safety", "Verkeer, mobiliteit & veiligheid", R.drawable.ic_world_citizenship_society_mono),
            SubCategory("environment_sustainability", "Milieu & duurzaamheid", R.drawable.ic_world_environment_sustainability_mono),
            SubCategory("everyday_life", "Dagelijks leven & zelfredzaamheid", R.drawable.ic_world_everyday_life_mono),
            SubCategory("arts_culture", "Kunst en cultuur", R.drawable.ic_world_arts_culture_mono),
        )
        LearningDomain.WRITING.name -> listOf(
            SubCategory("letter_tracing", "Letters schrijven", R.drawable.ic_topic_writing, null),
            SubCategory("writing", "Schrijven", R.drawable.ic_topic_writing),
            SubCategory("spelling", "Spelling", R.drawable.ic_topic_spelling),
            SubCategory("sentence_order", "Zinsvolgorde", R.drawable.ic_topic_sentence_structure),
            SubCategory("grammar", "Grammatica", R.drawable.ic_topic_grammar),
            SubCategory("question_words", "Vraagwoorden", R.drawable.ic_topic_grammar),
            SubCategory("singular_plural", "Enkelvoud en meervoud", R.drawable.ic_language_singular_plural_mono, options = singularPluralOptions()),
            SubCategory("conjugations", "Vervoegingen", R.drawable.ic_language_conjugations_mono, options = conjugationOptions()),
            SubCategory("verbs", "Werkwoorden", R.drawable.ic_language_verbs_mono, options = verbOptions()),
            SubCategory("past_tense", "Verleden tijden", R.drawable.ic_language_past_tense_mono, options = pastTenseOptions()),
            SubCategory("future_tense", "Toekomende tijden", R.drawable.ic_language_future_tense_mono, options = futureTenseOptions())
        )
        LearningDomain.PUZZLE.name -> listOf(
            SubCategory("sudoku", "Sudoku", R.drawable.ic_puzzle_sudoku_mono, null, iconAssetPath = "poincare/curriculum/icons/spacetiles/runtime-puzzle-sudoku.svg"),
            SubCategory("math-crosswords", "Rekenkruiswoord", R.drawable.ic_puzzle_math_crossword_mono, null, iconAssetPath = "poincare/curriculum/icons/spacetiles/runtime-puzzle-math-crosswords.svg"),
            SubCategory("word-seeker", "Word seeker", R.drawable.ic_puzzle_word_seeker_mono, null, iconAssetPath = "poincare/curriculum/icons/spacetiles/runtime-puzzle-word-seeker.svg"),
            SubCategory("mothership-dock", "Moederschip", R.drawable.ic_puzzle_mothership_mono, null, iconAssetPath = "poincare/curriculum/icons/spacetiles/runtime-puzzle-mothership-dock.svg"),
            SubCategory("galaxycon", "Galaxycon", R.drawable.ic_puzzle_galaxycon_mono, null, iconAssetPath = "poincare/curriculum/icons/spacetiles/runtime-puzzle-galaxycon.svg"),
            SubCategory("calculator-puzzle", "Rekenmachine", R.drawable.ic_puzzle_calculator_mono, null, iconAssetPath = "poincare/curriculum/icons/spacetiles/runtime-puzzle-calculator.svg"),
            SubCategory("constellations", "Constellations", R.drawable.ic_puzzle_constellations_mono, null, iconAssetPath = "poincare/curriculum/icons/spacetiles/runtime-puzzle-constellations.svg"),
            SubCategory("orbit-builder", "Orbitbouwer", R.drawable.ic_puzzle_orbit_builder_mono, null, iconAssetPath = "poincare/curriculum/icons/spacetiles/runtime-puzzle-orbit-builder.svg")
        )
        else -> emptyList()
    }
}

private fun singularPluralOptions() = listOf(
    SubCategoryOption("regular", "Regelmatige meervouden", "Regular plurals", "Pluriels réguliers"),
    SubCategoryOption("apostrophe", "Meervoud met apostrof", "Apostrophe plurals", "Pluriels avec apostrophe"),
    SubCategoryOption("f_v", "f en v", "f and v", "f et v"),
    SubCategoryOption("s_z", "s en z", "s and z", "s et z"),
    SubCategoryOption("irregular", "Onregelmatige meervouden", "Irregular plurals", "Pluriels irréguliers")
)

private fun measurementComparisonOptions() = listOf(
    SubCategoryOption("size", "Groter en kleiner", "Bigger and smaller", "Plus grand et plus petit", "compare_size"),
    SubCategoryOption("weight", "Zwaarder en lichter", "Heavier and lighter", "Plus lourd et plus léger", "compare_weight"),
    SubCategoryOption("height", "Hoger en lager", "Higher and lower", "Plus haut et plus bas", "compare_height"),
    SubCategoryOption("length", "Langer en korter", "Longer and shorter", "Plus long et plus court", "compare_length"),
    SubCategoryOption("distance", "Verder en dichterbij", "Farther and closer", "Plus loin et plus près", "compare_distance")
)

private fun worldNatureOptions() = listOf(
    SubCategoryOption("nature", "Natuur", "Nature", "Nature", "nature"),
    SubCategoryOption("animals", "Dieren", "Animals", "Animaux", "animals"),
    SubCategoryOption("plants", "Planten", "Plants", "Plantes", "plants")
)

private fun worldGeographyOptions() = listOf(
    SubCategoryOption("geography", "Aardrijkskunde", "Geography", "Géographie", "geography"),
    SubCategoryOption("space", "Ruimte", "Space", "Espace", "space"),
    SubCategoryOption("weather_climate", "Weer en klimaat", "Weather and climate", "Météo et climat", "weather_climate")
)

private fun conjugationOptions() = listOf(
    SubCategoryOption("first_person", "Ik-vorm", "First person", "Première personne"),
    SubCategoryOption("second_person", "Jij-vorm", "Second person", "Deuxième personne"),
    SubCategoryOption("third_person", "Hij/zij-vorm", "Third person", "Troisième personne"),
    SubCategoryOption("plural_persons", "Meervoudsvormen", "Plural persons", "Personnes au pluriel"),
    SubCategoryOption("inversion", "Inversie", "Inversion", "Inversion")
)

private fun verbOptions() = listOf(
    SubCategoryOption("infinitive", "Infinitief", "Infinitive", "Infinitif"),
    SubCategoryOption("stem", "Stam", "Stem", "Radical"),
    SubCategoryOption("finite_verb", "Persoonsvorm", "Finite verb", "Verbe conjugué"),
    SubCategoryOption("verb_or_noun", "Werkwoord of naamwoord", "Verb or noun", "Verbe ou nom"),
    SubCategoryOption("strong_weak", "Sterke en zwakke werkwoorden", "Strong and weak verbs", "Verbes réguliers et irréguliers")
)

private fun pastTenseOptions() = listOf(
    SubCategoryOption("imperfect", "Onvoltooid verleden tijd", "Simple past", "Imparfait"),
    SubCategoryOption("perfect", "Voltooid verleden tijd", "Present perfect", "Passé composé"),
    SubCategoryOption("kofschip", "'t kofschip", "Past-tense spelling", "Orthographe du passé"),
    SubCategoryOption("irregular_past", "Onregelmatige verleden tijd", "Irregular past", "Passé irrégulier"),
    SubCategoryOption("past_sentence", "Zinnen in het verleden", "Past-tense sentences", "Phrases au passé")
)

private fun futureTenseOptions() = listOf(
    SubCategoryOption("will_future", "Zullen + infinitief", "Will future", "Futur simple"),
    SubCategoryOption("going_to", "Gaan + infinitief", "Going to", "Futur proche"),
    SubCategoryOption("present_future", "Tegenwoordige tijd met toekomst", "Present for future", "Présent à valeur de futur"),
    SubCategoryOption("future_word_order", "Woordvolgorde in de toekomst", "Future word order", "Ordre des mots au futur"),
    SubCategoryOption("future_time_signal", "Tijdsignalen", "Future time signals", "Indicateurs de temps")
)

private fun SubCategory.isVisibleForAppLocale(appLanguage: String): Boolean {
    val sourceLocale = when (id) {
        "translation_nl_to_en", "translation_nl_to_fr" -> "nl-BE"
        "translation_en_to_nl", "translation_en_to_fr" -> "en-US"
        "translation_fr_to_nl", "translation_fr_to_en" -> "fr-BE"
        else -> return true
    }
    return normalizeBriefingLocale(appLanguage) == sourceLocale
}
