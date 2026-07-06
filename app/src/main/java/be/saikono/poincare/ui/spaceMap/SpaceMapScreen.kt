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
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
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
import be.saikono.poincare.ui.components.systemNavigationMargin
import be.saikono.poincare.ui.theme.*
import kotlin.random.Random

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
    onSwitchProfile: () -> Unit
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
        onSwitchProfile = onSwitchProfile
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
    onSwitchProfile: () -> Unit
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
                locale = appLanguage,
                landscapeProgress = if (isLandscape) uiState.overallProgress else null
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
                    ProgressStatusBar(
                        progress = uiState.overallProgress,
                        color = PlanetGold,
                        showLabels = true,
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
                    val planetGap = if (isLandscape) 8.dp else 16.dp
                    val planetSize = if (isLandscape) 96.dp else 112.dp
                    val iconSize = if (isLandscape) 82.dp else 98.dp
                    val menuItems = listOf(
                        MainMenuItem(
                            name = mainCategoryLabel(LearningDomain.READING, appLanguage),
                            color = PlanetBlue,
                            iconResId = R.drawable.icon_07_language_person,
                            delayMillis = 0,
                            enabled = profile?.readingLevel?.let { it != SkillLevel.NOT_APPLICABLE } == true && profile?.isMainCategoryEnabled(LearningDomain.READING) == true,
                            progress = uiState.categoryProgress[LearningDomain.READING],
                            onClick = onNavigateToReading
                        ) to LearningDomain.READING,
                        MainMenuItem(
                            name = mainCategoryLabel(LearningDomain.WRITING, appLanguage),
                            color = NebulaPurple,
                            iconResId = R.drawable.icon_01_abc_pencil,
                            delayMillis = 250,
                            enabled = profile?.writingLevel?.let { it != SkillLevel.NOT_APPLICABLE } == true && profile?.isMainCategoryEnabled(LearningDomain.WRITING) == true,
                            progress = uiState.categoryProgress[LearningDomain.WRITING],
                            onClick = onNavigateToWriting
                        ) to LearningDomain.WRITING,
                        MainMenuItem(
                            name = mainCategoryLabel(LearningDomain.MATH, appLanguage),
                            color = PlanetGold,
                            iconResId = R.drawable.icon_02_calculator,
                            delayMillis = 500,
                            enabled = profile?.mathLevel?.let { it != SkillLevel.NOT_APPLICABLE } == true && profile?.isMainCategoryEnabled(LearningDomain.MATH) == true,
                            progress = uiState.categoryProgress[LearningDomain.MATH],
                            onClick = onNavigateToMath
                        ) to LearningDomain.MATH,
                        MainMenuItem(
                            name = mainCategoryLabel(LearningDomain.GEOMETRY, appLanguage),
                            color = NebulaPurple,
                            iconResId = R.drawable.icon_04_cube,
                            delayMillis = 750,
                            enabled = profile?.mathLevel?.let { it != SkillLevel.NOT_APPLICABLE } == true && profile?.isMainCategoryEnabled(LearningDomain.GEOMETRY) == true,
                            progress = uiState.categoryProgress[LearningDomain.GEOMETRY],
                            onClick = onNavigateToGeometry
                        ) to LearningDomain.GEOMETRY,
                        MainMenuItem(
                            name = mainCategoryLabel(LearningDomain.WORLD_KNOWLEDGE, appLanguage),
                            color = PlanetBlue,
                            iconResId = R.drawable.icon_08_space_planet,
                            delayMillis = 1000,
                            enabled = profile?.readingLevel?.let { it != SkillLevel.NOT_APPLICABLE } == true && profile?.isMainCategoryEnabled(LearningDomain.WORLD_KNOWLEDGE) == true,
                            progress = uiState.categoryProgress[LearningDomain.WORLD_KNOWLEDGE],
                            onClick = onNavigateToWorldKnowledge
                        ) to LearningDomain.WORLD_KNOWLEDGE,
                        MainMenuItem(
                            name = mainCategoryLabel(LearningDomain.PUZZLE, appLanguage),
                            color = PlanetRed,
                            iconResId = R.drawable.icon_06_brain_network,
                            delayMillis = 1250,
                            enabled = profile?.puzzleLevel?.let { it != SkillLevel.NOT_APPLICABLE } == true && profile?.isMainCategoryEnabled(LearningDomain.PUZZLE) == true,
                            progress = uiState.categoryProgress[LearningDomain.PUZZLE],
                            onClick = onNavigateToPuzzle
                        ) to LearningDomain.PUZZLE
                    ).map { it.first }
                    Column(verticalArrangement = Arrangement.spacedBy(planetGap), horizontalAlignment = Alignment.CenterHorizontally) {
                        menuItems.chunked(if (isLandscape) 6 else 2).forEach { rowItems ->
                            Row(horizontalArrangement = Arrangement.spacedBy(planetGap), verticalAlignment = Alignment.CenterVertically) {
                                rowItems.forEach { item ->
                                    PlanetButton(
                                        item = item,
                                        reducedMotion = reducedMotion,
                                        planetSize = planetSize,
                                        iconSize = iconSize,
                                        hapticEnabled = uiState.animationSettings.hapticFeedbackEnabled,
                                        performHaptic = { haptic.performHapticFeedback(HapticFeedbackType.LongPress) }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

private fun mainCategoryLabel(domain: LearningDomain, locale: String): String {
    val language = when (locale) {
        "fr", "fr-BE", "fr_BE", "fr-FR", "fr_FR" -> "fr"
        "en", "en-US", "en_US", "en-GB", "en_GB" -> "en"
        else -> "nl"
    }
    return when (domain) {
        LearningDomain.READING -> when (language) {
            "en" -> "Communicate"
            "fr" -> "Communiquer"
            else -> "Communicatie"
        }
        LearningDomain.WRITING -> when (language) {
            "en" -> "Writing"
            "fr" -> "Ecriture"
            else -> "Schrijven"
        }
        LearningDomain.MATH -> when (language) {
            "en" -> "Numbers"
            "fr" -> "Nombres"
            else -> "Getallen"
        }
        LearningDomain.GEOMETRY -> when (language) {
            "en" -> "Geometry"
            "fr" -> "Geometrie"
            else -> "Meetkunde"
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

@Composable
fun ProfileHeader(
    profile: ChildProfile?,
    onSwitchProfile: () -> Unit,
    onNavigateToParent: () -> Unit,
    locale: String = "nl-BE",
    landscapeProgress: LevelProgress? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable(onClickLabel = menuText("switchProfile", locale)) { onSwitchProfile() }
                .padding(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(NebulaPurple),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = profile?.name?.take(1)?.uppercase() ?: "?",
                    color = StarWhite,
                    style = MaterialTheme.typography.titleLarge
                )
            }
            Column {
                Text(
                    text = profile?.name ?: "Astronaut",
                    color = StarWhite,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = menuText("switchProfileHint", locale),
                    color = StarWhite.copy(alpha = 0.6f),
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }

        landscapeProgress?.let { progress ->
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
                ProgressStatusBar(
                    progress = progress,
                    color = PlanetGold,
                    showLabels = true,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
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

private fun menuText(key: String, locale: String): String = when (locale) {
    "en", "en-US", "en_US", "en-GB", "en_GB" -> when (key) {
        "switchProfile" -> "Switch profile"
        "switchProfileHint" -> "Tap to switch"
        "parentSettings" -> "Parent settings"
        else -> key
    }
    "fr", "fr-BE", "fr_BE", "fr-FR", "fr_FR" -> when (key) {
        "switchProfile" -> "Changer de profil"
        "switchProfileHint" -> "Appuie pour changer"
        "parentSettings" -> "Parametres parent"
        else -> key
    }
    else -> when (key) {
        "switchProfile" -> "Wissel profiel"
        "switchProfileHint" -> "Tik om te wisselen"
        "parentSettings" -> "Ouder-instellingen"
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
    val color: Color,
    val iconResId: Int,
    val delayMillis: Int,
    val enabled: Boolean,
    val progress: LevelProgress?,
    val onClick: () -> Unit
)

@Composable
private fun ProgressStatusBar(
    progress: LevelProgress,
    color: Color,
    showLabels: Boolean,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (showLabels) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Level ${progress.level}", color = StarWhite, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                Column(horizontalAlignment = Alignment.End) {
                    Text("Level ${progress.level + 1}", color = StarWhite, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                    Text("${progress.currentLevelPoints}/${progress.pointsNeededForNextLevel}", color = StarWhite.copy(alpha = 0.7f), style = MaterialTheme.typography.labelSmall)
                }
            }
        }
        LinearProgressIndicator(
            progress = { progress.progressFraction.coerceIn(0f, 1f) },
            color = color,
            trackColor = Color.White.copy(alpha = 0.12f),
            modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp))
        )
    }
}

@Composable
private fun PlanetButton(
    item: MainMenuItem,
    reducedMotion: Boolean,
    planetSize: Dp = 156.dp,
    iconSize: Dp = 136.dp,
    hapticEnabled: Boolean,
    performHaptic: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "PlanetFloat")
    val offsetY by if (reducedMotion) {
        remember { mutableStateOf(0f) }
    } else {
        infiniteTransition.animateFloat(
            initialValue = -4f,
            targetValue = 4f,
            animationSpec = infiniteRepeatable(
                animation = tween(5200, delayMillis = item.delayMillis, easing = LinearOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "Y"
        )
    }
    val offsetX by if (reducedMotion) {
        remember { mutableStateOf(0f) }
    } else {
        infiniteTransition.animateFloat(
            initialValue = -2f,
            targetValue = 2f,
            animationSpec = infiniteRepeatable(
                animation = tween(6400, delayMillis = item.delayMillis + 300, easing = LinearOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "X"
        )
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .offset(x = offsetX.dp, y = offsetY.dp)
            .widthIn(min = planetSize)
    ) {
        Box(
            modifier = Modifier
                .size(planetSize)
                .clip(CircleShape)
                .alpha(if (item.enabled) 1f else 0.35f)
                .background(if (item.enabled) item.color.copy(alpha = 0.08f) else Color.Gray.copy(alpha = 0.12f))
                .semantics { contentDescription = if (item.enabled) "Planeet ${item.name}: tik om missies te starten" else "Planeet ${item.name}: geen leerjaar ingesteld" }
                .clickable(enabled = item.enabled, onClickLabel = "Start ${item.name} missie") {
                    if (hapticEnabled) performHaptic()
                    item.onClick()
                },
            contentAlignment = Alignment.Center
        ) {
            // Subtle atmosphere effect
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.White.copy(alpha = 0.04f), shape = CircleShape)
            )
            
            // Icon
            Icon(
                painter = androidx.compose.ui.res.painterResource(id = item.iconResId),
                contentDescription = null,
                tint = Color.Unspecified,
                modifier = Modifier.size(iconSize)
            )
        }
        Text(
            text = item.name,
            color = if (item.enabled) StarWhite else StarWhite.copy(alpha = 0.45f),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Medium
        )
        item.progress?.let { progress ->
            ProgressStatusBar(
                progress = progress,
                color = item.color,
                showLabels = false,
                modifier = Modifier.width(planetSize)
            )
        }
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
