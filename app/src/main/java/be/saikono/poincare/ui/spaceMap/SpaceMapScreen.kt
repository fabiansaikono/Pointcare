package be.saikono.poincare.ui.spaceMap

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.painterResource
import be.saikono.poincare.R
import be.saikono.poincare.model.ChildProfile
import be.saikono.poincare.model.LearningDomain
import be.saikono.poincare.model.LevelProgress
import be.saikono.poincare.model.SkillLevel
import be.saikono.poincare.model.isMainCategoryEnabled
import be.saikono.poincare.model.selectedGradesFor
import be.saikono.poincare.ui.components.CurriculumAssetIcon
import be.saikono.poincare.ui.components.HomeworkTasksGlyph
import be.saikono.poincare.ui.components.systemNavigationMargin
import be.saikono.poincare.ui.theme.*
import kotlin.random.Random

internal const val MAIN_MENU_BOTTOM_PADDING_DP = 8

@Composable
fun SpaceMapScreen(
    viewModel: SpaceMapViewModel,
    onNavigateToMath: () -> Unit,
    onNavigateToReading: () -> Unit,
    onNavigateToWriting: () -> Unit,
    onNavigateToPuzzle: () -> Unit,
    onNavigateToWorldKnowledge: () -> Unit,
    onNavigateToGeometry: () -> Unit,
    onNavigateToParent: () -> Unit,
    onNavigateToTasks: () -> Unit,
    onSwitchProfile: () -> Unit,
    onSwitchInterface: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    SpaceMapContent(
        uiState = uiState,
        onNavigateToMath = onNavigateToMath,
        onNavigateToReading = onNavigateToReading,
        onNavigateToWriting = onNavigateToWriting,
        onNavigateToPuzzle = onNavigateToPuzzle,
        onNavigateToWorldKnowledge = onNavigateToWorldKnowledge,
        onNavigateToGeometry = onNavigateToGeometry,
        onNavigateToParent = onNavigateToParent,
        onNavigateToTasks = onNavigateToTasks,
        onSwitchProfile = onSwitchProfile,
        onSwitchInterface = onSwitchInterface,
        availableGrades = uiState.availableGrades,
        selectedGrade = uiState.selectedGrade,
        onGradeSelected = viewModel::selectGrade
    )
}

@Composable
fun SpaceMapContent(
    uiState: SpaceMapUiState,
    onNavigateToMath: () -> Unit,
    onNavigateToReading: () -> Unit,
    onNavigateToWriting: () -> Unit,
    onNavigateToPuzzle: () -> Unit,
    onNavigateToWorldKnowledge: () -> Unit,
    onNavigateToGeometry: () -> Unit,
    onNavigateToParent: () -> Unit,
    onNavigateToTasks: () -> Unit = {},
    onSwitchProfile: () -> Unit,
    onSwitchInterface: () -> Unit = {},
    availableGrades: List<Int> = uiState.availableGrades,
    selectedGrade: Int? = uiState.selectedGrade,
    onGradeSelected: (Int) -> Unit = {}
) {
    val profile = uiState.currentProfile
    val reducedMotion = uiState.animationSettings.reducedMotionEnabled
    val haptic = LocalHapticFeedback.current
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
    val appLanguage = profile?.appLanguage ?: "nl"

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DeepSpaceBlue)
    ) {
        SpaceBackground()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 800.dp) // Constraint for tablets
                .align(Alignment.TopCenter)
                .systemNavigationMargin(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header
            ProfileHeader(
                profile = profile,
                onSwitchProfile = onSwitchProfile,
                onNavigateToParent = onNavigateToParent,
                onNavigateToTasks = onNavigateToTasks,
                onSwitchInterface = onSwitchInterface,
                switchInterfaceDescription = be.saikono.poincare.ui.galaxy.localizedGalaxyString(
                    LocalContext.current,
                    R.string.galaxy_open_interface,
                    appLanguage
                ),
                locale = appLanguage,
                landscapeLevelProgress = if (isLandscape) uiState.overallProgress else null,
                availableGrades = availableGrades,
                selectedGrade = selectedGrade,
                onGradeSelected = onGradeSelected
            )

            if (!isLandscape) {
                BoxWithConstraints(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 6.dp), contentAlignment = Alignment.Center) {
                val logoSize = if (maxHeight < 620.dp) 56.dp else 64.dp
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Image(
                    painter = painterResource(id = R.drawable.square_logo),
                    contentDescription = "Poincaré Logo",
                    modifier = Modifier
                        .size(logoSize)
                )
                    LevelProgressSummary(
                        progress = uiState.overallProgress,
                        locale = appLanguage,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            }

            Spacer(modifier = Modifier.height(if (isLandscape) 4.dp else 8.dp))

            // Main Map Area
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(3f, fill = true),
                contentAlignment = Alignment.Center
            ) {
                BoxWithConstraints(contentAlignment = Alignment.Center) {
                    val horizontalPlanetGap = if (isLandscape) 14.dp else 16.dp
                    val verticalPlanetGap = if (isLandscape) 8.dp else 22.dp
                    val planetSize = if (isLandscape) 96.dp else 112.dp
                    val iconSize = if (isLandscape) 82.dp else 98.dp
                    val planetSlotWidth = if (isLandscape) 112.dp else 156.dp
                    val menuItems = listOf(
                        MainMenuItem(
                            name = mainCategoryLabel(LearningDomain.READING, appLanguage, uiState.curriculumManifest),
                            iconResId = R.drawable.icon_07_language_person,
                            iconAssetPath = mainCategoryIconAssetPath(LearningDomain.READING, uiState.curriculumManifest),
                            delayMillis = 0,
                            enabled = profile?.let { child -> selectedGrade?.let { it in child.selectedGradesFor(LearningDomain.READING) } == true && child.isMainCategoryEnabled(LearningDomain.READING) } == true,
                            progressPercent = uiState.categoryLessonProgressPercent[LearningDomain.READING],
                            onClick = onNavigateToReading
                        ) to LearningDomain.READING,
                        MainMenuItem(
                            name = mainCategoryLabel(LearningDomain.WRITING, appLanguage, uiState.curriculumManifest),
                            iconResId = R.drawable.icon_01_abc_pencil,
                            iconAssetPath = mainCategoryIconAssetPath(LearningDomain.WRITING, uiState.curriculumManifest),
                            delayMillis = 250,
                            enabled = profile?.let { child -> selectedGrade?.let { it in child.selectedGradesFor(LearningDomain.WRITING) } == true && child.isMainCategoryEnabled(LearningDomain.WRITING) } == true,
                            progressPercent = uiState.categoryLessonProgressPercent[LearningDomain.WRITING],
                            onClick = onNavigateToWriting
                        ) to LearningDomain.WRITING,
                        MainMenuItem(
                            name = mainCategoryLabel(LearningDomain.MATH, appLanguage, uiState.curriculumManifest),
                            iconResId = R.drawable.icon_02_calculator,
                            iconAssetPath = mainCategoryIconAssetPath(LearningDomain.MATH, uiState.curriculumManifest),
                            delayMillis = 500,
                            enabled = profile?.let { child -> selectedGrade?.let { it in child.selectedGradesFor(LearningDomain.MATH) } == true && child.isMainCategoryEnabled(LearningDomain.MATH) } == true,
                            progressPercent = uiState.categoryLessonProgressPercent[LearningDomain.MATH],
                            onClick = onNavigateToMath
                        ) to LearningDomain.MATH,
                        MainMenuItem(
                            name = mainCategoryLabel(LearningDomain.GEOMETRY, appLanguage, uiState.curriculumManifest),
                            iconResId = R.drawable.icon_04_cube,
                            iconAssetPath = mainCategoryIconAssetPath(LearningDomain.GEOMETRY, uiState.curriculumManifest),
                            delayMillis = 750,
                            enabled = profile?.let { child -> selectedGrade?.let { it in child.selectedGradesFor(LearningDomain.GEOMETRY) } == true && child.isMainCategoryEnabled(LearningDomain.GEOMETRY) } == true,
                            progressPercent = uiState.categoryLessonProgressPercent[LearningDomain.GEOMETRY],
                            onClick = onNavigateToGeometry
                        ) to LearningDomain.GEOMETRY,
                        MainMenuItem(
                            name = mainCategoryLabel(LearningDomain.WORLD_KNOWLEDGE, appLanguage, uiState.curriculumManifest),
                            iconResId = R.drawable.icon_08_space_planet,
                            iconAssetPath = mainCategoryIconAssetPath(LearningDomain.WORLD_KNOWLEDGE, uiState.curriculumManifest),
                            delayMillis = 1000,
                            enabled = profile?.let { child -> selectedGrade?.let { it in child.selectedGradesFor(LearningDomain.WORLD_KNOWLEDGE) } == true && child.isMainCategoryEnabled(LearningDomain.WORLD_KNOWLEDGE) } == true,
                            progressPercent = uiState.categoryLessonProgressPercent[LearningDomain.WORLD_KNOWLEDGE],
                            onClick = onNavigateToWorldKnowledge
                        ) to LearningDomain.WORLD_KNOWLEDGE,
                        MainMenuItem(
                            name = mainCategoryLabel(LearningDomain.PUZZLE, appLanguage, uiState.curriculumManifest),
                            iconResId = R.drawable.icon_06_brain_network,
                            iconAssetPath = mainCategoryIconAssetPath(LearningDomain.PUZZLE, uiState.curriculumManifest),
                            delayMillis = 1250,
                            enabled = profile != null,
                            progressPercent = null,
                            onClick = onNavigateToPuzzle
                        ) to LearningDomain.PUZZLE
                    ).map { it.first }
                    Column(verticalArrangement = Arrangement.spacedBy(verticalPlanetGap), horizontalAlignment = Alignment.CenterHorizontally) {
                        menuItems.chunked(if (isLandscape) 6 else 2).forEach { rowItems ->
                            Row(horizontalArrangement = Arrangement.spacedBy(horizontalPlanetGap), verticalAlignment = Alignment.Top) {
                                rowItems.forEach { item ->
                                    PlanetButton(
                                        item = item,
                                        reducedMotion = reducedMotion,
                                        planetSize = planetSize,
                                        iconSize = iconSize,
                                        itemSlotWidth = planetSlotWidth,
                                        hapticEnabled = uiState.animationSettings.hapticFeedbackEnabled,
                                        performHaptic = { haptic.performHapticFeedback(HapticFeedbackType.LongPress) }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(MAIN_MENU_BOTTOM_PADDING_DP.dp))
        }
    }
}

internal fun mainCategoryLabel(
    domain: LearningDomain,
    locale: String,
    manifest: be.saikono.poincare.domain.curriculum.manifest.CurriculumManifest?
): String {
    manifest?.categories?.firstOrNull { it.domain == domain }?.title?.get(locale)?.let { return it }
    val language = when (locale) {
        "fr", "fr-BE", "fr_BE", "fr-FR", "fr_FR" -> "fr"
        "en", "en-US", "en_US", "en-GB", "en_GB" -> "en"
        else -> "nl"
    }
    return when (domain) {
        LearningDomain.READING -> when (language) {
            "en" -> "Reading & listening"
            "fr" -> "Lecture et écoute"
            else -> "Lezen & luisteren"
        }
        LearningDomain.WRITING -> when (language) {
            "en" -> "Spelling & grammar"
            "fr" -> "Orthographe et grammaire"
            else -> "Spelling & grammatica"
        }
        LearningDomain.MATH -> when (language) {
            "en" -> "Numbers"
            "fr" -> "Nombres"
            else -> "Getallen"
        }
        LearningDomain.GEOMETRY -> when (language) {
            "en" -> "Measurement & geometry"
            "fr" -> "Mesures et géométrie"
            else -> "Meten & meetkunde"
        }
        LearningDomain.WORLD_KNOWLEDGE -> when (language) {
            "en" -> "World knowledge"
            "fr" -> "Le Monde"
            else -> "Wereldkennis"
        }
        LearningDomain.PUZZLE -> when (language) {
            "en" -> "Puzzles"
            "fr" -> "Puzzles"
            else -> "Puzzels"
        }
    }
}

private fun mainCategoryIconAssetPath(
    domain: LearningDomain,
    manifest: be.saikono.poincare.domain.curriculum.manifest.CurriculumManifest?
): String? = manifest?.categories?.firstOrNull { it.domain == domain }?.iconAssetPath

@Composable
fun ProfileHeader(
    profile: ChildProfile?,
    onSwitchProfile: () -> Unit,
    onNavigateToParent: () -> Unit,
    onNavigateToTasks: (() -> Unit)? = null,
    onSwitchInterface: (() -> Unit)? = null,
    switchInterfaceToTiles: Boolean = false,
    switchInterfaceDescription: String = "",
    locale: String = "nl-BE",
    landscapeLevelProgress: LevelProgress? = null,
    availableGrades: List<Int> = emptyList(),
    selectedGrade: Int? = availableGrades.firstOrNull(),
    onGradeSelected: (Int) -> Unit = {}
) {
    val compactHeader = LocalConfiguration.current.screenWidthDp < 400
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                horizontal = if (compactHeader) 12.dp else 24.dp,
                vertical = if (compactHeader) 14.dp else 24.dp
            ),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(if (compactHeader) 44.dp else 48.dp)
                    .clip(CircleShape)
                    .background(NebulaPurple)
                    .semantics { contentDescription = menuText("switchProfile", locale) }
                    .clickable(onClickLabel = menuText("switchProfile", locale)) { onSwitchProfile() },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = profile?.name?.take(1)?.uppercase() ?: "?",
                    color = StarWhite,
                    style = MaterialTheme.typography.titleLarge
                )
            }
            GradeSelectorPill(
                grades = availableGrades,
                selectedGrade = selectedGrade,
                locale = locale,
                onGradeSelected = onGradeSelected
            )
            onNavigateToTasks?.let { openTasks ->
                IconButton(
                    onClick = openTasks,
                    modifier = Modifier.size(if (compactHeader) 44.dp else 48.dp)
                ) {
                    HomeworkTasksGlyph(
                        contentDescription = menuText("tasks", locale),
                        color = StarWhite.copy(alpha = 0.78f),
                        modifier = Modifier.size(30.dp)
                    )
                }
            }
        }

        landscapeLevelProgress?.let { levelProgress ->
            Row(
                modifier = Modifier.weight(1f).padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Image(
                    painter = painterResource(id = R.drawable.square_logo),
                    contentDescription = "Poincaré Logo",
                    modifier = Modifier.size(48.dp)
                )
                LevelProgressSummary(
                    progress = levelProgress,
                    locale = locale,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(if (compactHeader) 8.dp else 16.dp)) {
            onSwitchInterface?.let { switchInterface ->
                IconButton(
                    onClick = switchInterface,
                    modifier = Modifier.size(48.dp)
                ) {
                    InterfaceToggleGlyph(
                        contentDescription = switchInterfaceDescription,
                        showTileGlyph = switchInterfaceToTiles,
                        modifier = Modifier.size(32.dp)
                    )
                }
            }
            IconButton(
                onClick = onNavigateToParent,
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = menuText("parentSettings", locale),
                    tint = StarWhite.copy(alpha = 0.6f),
                    modifier = Modifier.size(32.dp)
                )
            }
        }
    }
}

@Composable
private fun GradeSelectorPill(
    grades: List<Int>,
    selectedGrade: Int?,
    locale: String,
    onGradeSelected: (Int) -> Unit
) {
    val normalizedGrades = grades.filter { it in 1..6 }.distinct().sorted()
    val activeGrade = selectedGrade?.takeIf { it in normalizedGrades } ?: normalizedGrades.firstOrNull()
    if (activeGrade == null) return
    var expanded by remember { mutableStateOf(false) }
    val description = menuText("chooseGrade", locale)

    Box {
        Surface(
            shape = RoundedCornerShape(999.dp),
            color = PlanetBlue.copy(alpha = 0.16f),
            contentColor = StarWhite,
            border = androidx.compose.foundation.BorderStroke(1.dp, PlanetBlue.copy(alpha = 0.72f)),
            modifier = Modifier
                .heightIn(min = 40.dp)
                .then(
                    if (normalizedGrades.size > 1) {
                        Modifier.clickable(onClickLabel = description) { expanded = true }
                    } else {
                        Modifier
                    }
                )
                .semantics { contentDescription = "$description: L$activeGrade" }
        ) {
            Row(
                modifier = Modifier.padding(start = 14.dp, end = if (normalizedGrades.size > 1) 6.dp else 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text("L$activeGrade", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                if (normalizedGrades.size > 1) {
                    Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                }
            }
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            normalizedGrades.forEach { grade ->
                DropdownMenuItem(
                    text = { Text("L$grade") },
                    onClick = {
                        expanded = false
                        onGradeSelected(grade)
                    },
                    leadingIcon = if (grade == activeGrade) {
                        { Icon(Icons.Default.Check, contentDescription = null) }
                    } else null
                )
            }
        }
    }
}

@Composable
private fun InterfaceToggleGlyph(
    contentDescription: String,
    showTileGlyph: Boolean,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier.semantics { this.contentDescription = contentDescription }) {
        val colour = StarWhite.copy(alpha = 0.72f)
        if (showTileGlyph) {
            val tileSize = size.minDimension * 0.28f
            val gap = size.minDimension * 0.12f
            val start = (size.minDimension - tileSize * 2f - gap) / 2f
            repeat(2) { row ->
                repeat(2) { column ->
                    drawRoundRect(
                        color = colour,
                        topLeft = androidx.compose.ui.geometry.Offset(
                            start + column * (tileSize + gap),
                            start + row * (tileSize + gap)
                        ),
                        size = androidx.compose.ui.geometry.Size(tileSize, tileSize),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(tileSize * 0.18f),
                        style = Stroke(width = 1.8.dp.toPx())
                    )
                }
            }
            return@Canvas
        }
        val points = listOf(
            androidx.compose.ui.geometry.Offset(size.width * 0.22f, size.height * 0.28f),
            androidx.compose.ui.geometry.Offset(size.width * 0.72f, size.height * 0.20f),
            androidx.compose.ui.geometry.Offset(size.width * 0.80f, size.height * 0.70f),
            androidx.compose.ui.geometry.Offset(size.width * 0.30f, size.height * 0.78f)
        )
        drawLine(colour, points[0], points[1], strokeWidth = 1.5.dp.toPx())
        drawLine(colour, points[1], points[2], strokeWidth = 1.5.dp.toPx())
        drawLine(colour, points[2], points[3], strokeWidth = 1.5.dp.toPx())
        drawLine(colour, points[3], points[0], strokeWidth = 1.5.dp.toPx())
        points.forEach { point ->
            drawCircle(colour, radius = 3.5.dp.toPx(), center = point)
        }
    }
}

private fun menuText(key: String, locale: String): String = when (locale) {
    "en", "en-US", "en_US", "en-GB", "en_GB" -> when (key) {
        "switchProfile" -> "Switch profile"
        "switchProfileHint" -> "Tap to switch"
        "chooseGrade" -> "Choose grade"
        "tasks" -> "Homework and tasks"
        "parentSettings" -> "Parent settings"
        "lessonProgress" -> "Lesson progress"
        "level" -> "Level"
        "points" -> "points"
        else -> key
    }
    "fr", "fr-BE", "fr_BE", "fr-FR", "fr_FR" -> when (key) {
        "switchProfile" -> "Changer de profil"
        "switchProfileHint" -> "Appuie pour changer"
        "chooseGrade" -> "Choisir l’année"
        "tasks" -> "Devoirs et tâches"
        "parentSettings" -> "Parametres parent"
        "lessonProgress" -> "Progression des lecons"
        "level" -> "Niveau"
        "points" -> "points"
        else -> key
    }
    else -> when (key) {
        "switchProfile" -> "Wissel profiel"
        "switchProfileHint" -> "Tik om te wisselen"
        "chooseGrade" -> "Kies leerjaar"
        "tasks" -> "Huiswerk en taken"
        "parentSettings" -> "Ouder-instellingen"
        "lessonProgress" -> "Voortgang lessen"
        "level" -> "Niveau"
        "points" -> "punten"
        else -> key
    }
}

@Composable
fun SpaceBackground() {
    val starCount = 100
    val stars = remember {
        List(starCount) {
            StarData(
                x = Random.nextFloat(),
                y = Random.nextFloat(),
                size = Random.nextFloat() * 2f + 1f,
                alpha = Random.nextFloat() * 0.5f + 0.5f
            )
        }
    }

    Canvas(modifier = Modifier.fillMaxSize().semantics { contentDescription = "Een rustige achtergrond met sterren" }) {
        stars.forEach { star ->
            drawCircle(
                color = StarWhite.copy(alpha = star.alpha),
                radius = star.size,
                center = androidx.compose.ui.geometry.Offset(
                    x = star.x * size.width,
                    y = star.y * size.height
                )
            )
        }
    }
}

data class StarData(val x: Float, val y: Float, val size: Float, val alpha: Float)

private data class MainMenuItem(
    val name: String,
    val iconResId: Int,
    val iconAssetPath: String?,
    val delayMillis: Int,
    val enabled: Boolean,
    val progressPercent: Int?,
    val onClick: () -> Unit
)

@Composable
internal fun LevelProgressSummary(
    progress: LevelProgress,
    locale: String,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                "${menuText("level", locale)} ${progress.level}",
                color = PlanetGold,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                "${menuText("level", locale)} ${progress.level + 1}",
                color = StarWhite.copy(alpha = .72f),
                style = MaterialTheme.typography.labelLarge
            )
        }
        LinearProgressIndicator(
            progress = { progress.progressFraction.coerceIn(0f, 1f) },
            color = PlanetGold,
            trackColor = Color.White.copy(alpha = .12f),
            modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp))
        )
        Text(
            "${progress.currentLevelPoints}/${progress.pointsNeededForNextLevel} ${menuText("points", locale)}",
            color = StarWhite.copy(alpha = .68f),
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.align(Alignment.End)
        )
    }
}

@Composable
private fun PlanetButton(
    item: MainMenuItem,
    reducedMotion: Boolean,
    planetSize: Dp = 156.dp,
    iconSize: Dp = 136.dp,
    itemSlotWidth: Dp = planetSize,
    hapticEnabled: Boolean,
    performHaptic: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "PlanetFloat")
    val hoverEasing = CubicBezierEasing(0.45f, 0f, 0.55f, 1f)
    val offsetY by if (reducedMotion) {
        remember { mutableStateOf(0f) }
    } else {
        infiniteTransition.animateFloat(
            initialValue = -4f,
            targetValue = 4f,
            animationSpec = infiniteRepeatable(
                animation = tween(4800, delayMillis = item.delayMillis, easing = hoverEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "Y"
        )
    }
    val offsetX by if (reducedMotion) {
        remember { mutableStateOf(0f) }
    } else {
        infiniteTransition.animateFloat(
            initialValue = -4f,
            targetValue = 4f,
            animationSpec = infiniteRepeatable(
                animation = tween(6800, delayMillis = item.delayMillis + 300, easing = hoverEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "X"
        )
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .width(itemSlotWidth)
            .offset(x = offsetX.dp, y = offsetY.dp)
    ) {
        Box(modifier = Modifier.size(planetSize)) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .semantics { contentDescription = if (item.enabled) "Planeet ${item.name}: tik om missies te starten" else "Planeet ${item.name}: geen leerjaar ingesteld" }
                    .clickable(enabled = item.enabled, onClickLabel = "Start ${item.name} missie") {
                        if (hapticEnabled) performHaptic()
                        item.onClick()
                    },
                contentAlignment = Alignment.Center
            ) {
                CurriculumAssetIcon(
                    assetPath = item.iconAssetPath,
                    fallbackIconResId = item.iconResId,
                    contentDescription = null,
                    modifier = Modifier
                        .size(iconSize)
                        .alpha(if (item.enabled) 1f else 0.35f)
                )
            }

            item.progressPercent?.let { progressPercent ->
                Text(
                    text = "${progressPercent.coerceIn(0, 100)}%",
                    color = PlanetGold,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = 8.dp, y = (-10).dp)
                )
            }
        }
        Text(
            text = item.name,
            color = if (item.enabled) StarWhite else StarWhite.copy(alpha = 0.45f),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
            maxLines = 2,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
        )
    }
}

@Preview(showBackground = true)
@Composable
fun SpaceMapPreview() {
    PoincareTheme {
        SpaceMapContent(
            uiState = SpaceMapUiState(
                currentProfile = ChildProfile(
                    id = "1",
                    name = "Henri",
                    avatarId = "1",
                    rocketColorId = "1",
                    birthYear = 2018,
                    readingLevel = SkillLevel.FIRST_GRADE,
                    writingLevel = SkillLevel.FIRST_GRADE,
                    mathLevel = SkillLevel.FIRST_GRADE,
                    geometryLevel = SkillLevel.FIRST_GRADE,
                    worldKnowledgeLevel = SkillLevel.FIRST_GRADE,
                    puzzleLevel = SkillLevel.FIRST_GRADE
                ),
                isLoading = false
            ),
            onNavigateToMath = {},
            onNavigateToReading = {},
            onNavigateToWriting = {},
            onNavigateToPuzzle = {},
            onNavigateToWorldKnowledge = {},
            onNavigateToGeometry = {},
            onNavigateToParent = {},
            onSwitchProfile = {}
        )
    }
}
