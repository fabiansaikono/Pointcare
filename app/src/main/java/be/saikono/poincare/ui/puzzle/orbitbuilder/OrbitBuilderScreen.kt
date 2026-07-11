package be.saikono.poincare.ui.puzzle.orbitbuilder

import androidx.compose.foundation.layout.PaddingValues
import android.content.res.Configuration
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import be.saikono.poincare.R
import be.saikono.poincare.ui.components.DifficultyStars
import be.saikono.poincare.ui.components.systemNavigationMargin
import be.saikono.poincare.ui.spaceMap.SpaceBackground
import be.saikono.poincare.ui.theme.CockpitGray
import be.saikono.poincare.ui.theme.DeepSpaceBlue
import be.saikono.poincare.ui.theme.NebulaPurple
import be.saikono.poincare.ui.theme.PlanetBlue
import be.saikono.poincare.ui.theme.PlanetGold
import be.saikono.poincare.ui.theme.PlanetRed
import be.saikono.poincare.ui.theme.StarWhite
import kotlinx.coroutines.delay
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun OrbitBuilderScreen(
    viewModel: OrbitBuilderViewModel,
    onNavigateBack: () -> Unit
) {
    OrbitBuilderGameContent(
        viewModel = viewModel,
        selectedStars = null,
        selectedGenerationDifficulty = null,
        selectedGrade = null,
        onNavigateBack = onNavigateBack,
        onMissionComplete = { _, _ -> },
        showChrome = true,
        modifier = Modifier.fillMaxSize()
    )
}

@Composable
fun OrbitBuilderPuzzleGame(
    displayedStars: Int,
    generationDifficulty: Int,
    grade: Int,
    onMissionComplete: (levelId: String, stars: Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val viewModel = remember { OrbitBuilderViewModel() }
    OrbitBuilderGameContent(
        viewModel = viewModel,
        selectedStars = displayedStars,
        selectedGenerationDifficulty = generationDifficulty,
        selectedGrade = grade,
        onNavigateBack = {},
        onMissionComplete = onMissionComplete,
        showChrome = false,
        modifier = modifier
    )
}

@Composable
private fun OrbitBuilderGameContent(
    viewModel: OrbitBuilderViewModel,
    selectedStars: Int?,
    selectedGenerationDifficulty: Int?,
    selectedGrade: Int?,
    onNavigateBack: () -> Unit,
    onMissionComplete: (levelId: String, stars: Int) -> Unit,
    showChrome: Boolean,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsState()
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    var introStage by remember(state.currentLevel.id) { mutableIntStateOf(0) }
    var reportedCompletions by remember { mutableStateOf(emptySet<String>()) }

    LaunchedEffect(selectedStars, selectedGenerationDifficulty, selectedGrade) {
        selectedStars?.let {
            viewModel.setDifficulty(
                displayedStars = it,
                generationDifficulty = selectedGenerationDifficulty ?: it,
                grade = selectedGrade ?: 1
            )
        }
    }

    LaunchedEffect(state.currentLevel.id) {
        introStage = 0
        delay(1700)
        introStage = 1
        delay(1050)
        introStage = 2
    }

    LaunchedEffect(state.launchRunId, state.phase) {
        if (state.phase == OrbitBuilderPhase.LAUNCHING) {
            // Let Compose render the rocket on the first/start dot before it accelerates.
            delay(80)

            while (true) {
                val beforeAdvance = viewModel.uiState.value
                val nextStep = beforeAdvance.simulationResult?.path?.getOrNull(beforeAdvance.launchStepIndex + 1)
                val travelDuration = when {
                    beforeAdvance.launchStepIndex == 0 -> 500L
                    nextStep?.tile is OrbitFixedTile.Gate -> 1000L
                    else -> 220L
                }

                viewModel.advanceLaunchStep()

                val current = viewModel.uiState.value
                if (current.phase != OrbitBuilderPhase.LAUNCHING) break

                delay(travelDuration)
            }
        }
    }

    LaunchedEffect(state.phase, state.currentLevel.id, state.simulationResult?.success) {
        val result = state.simulationResult
        val levelId = state.currentLevel.id
        if (state.phase == OrbitBuilderPhase.SUCCESS && result?.success == true && levelId !in reportedCompletions) {
            reportedCompletions = reportedCompletions + levelId
            onMissionComplete(levelId, result.stars)
        }
    }

    Box(modifier = modifier.fillMaxSize().background(DeepSpaceBlue)) {
        SpaceBackground()
        if (isLandscape) {
            OrbitLandscapeLayout(state, introStage, showChrome, onNavigateBack, viewModel)
        } else {
            OrbitPortraitLayout(state, introStage, showChrome, onNavigateBack, viewModel)
        }
        IntroStartValueOverlay(
            state = state,
            visible = state.phase == OrbitBuilderPhase.PLANNING && state.drawnPath.isEmpty() && introStage == 0,
            modifier = Modifier.align(Alignment.Center)
        )
        if (state.phase == OrbitBuilderPhase.SUCCESS || state.phase == OrbitBuilderPhase.FAILURE) {
            OrbitBuilderResultDialog(
                state = state,
                onRetry = viewModel::retryAfterFailure,
                onNextMission = viewModel::nextMission,
                onReset = { viewModel.resetLevel(clearAttempts = true) }
            )
        }
    }
}

@Composable
private fun OrbitPortraitLayout(
    state: OrbitBuilderUiState,
    introStage: Int,
    showChrome: Boolean,
    onNavigateBack: () -> Unit,
    viewModel: OrbitBuilderViewModel
) {
    Column(
        modifier = Modifier.fillMaxSize()
            .then(if (showChrome) Modifier.systemNavigationMargin() else Modifier)
            .padding(horizontal = 12.dp, vertical = if (showChrome) 8.dp else 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (showChrome) OrbitChromeRow(state = state, onNavigateBack = onNavigateBack)
        StartTargetBar(state = state)
        OrbitStatusPanel(state = state)
        Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.TopCenter) {
            OrbitGrid(
                state = state,
                drawingEnabled = true,
                targetBlink = introStage == 1,
                onStartPath = viewModel::startDrawnPath,
                onExtendPath = viewModel::extendDrawnPath,
                modifier = Modifier.fillMaxWidth()
            )
        }
        DrawPathHint(state = state, introStage = introStage)
        OrbitPrimaryButton(state, introStage, viewModel::launch) { viewModel.clearPath(clearAttempts = false) }
    }
}

@Composable
private fun OrbitLandscapeLayout(
    state: OrbitBuilderUiState,
    introStage: Int,
    showChrome: Boolean,
    onNavigateBack: () -> Unit,
    viewModel: OrbitBuilderViewModel
) {
    Row(
        modifier = Modifier.fillMaxSize()
            .then(if (showChrome) Modifier.systemNavigationMargin() else Modifier)
            .padding(horizontal = 12.dp, vertical = if (showChrome) 8.dp else 2.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(modifier = Modifier.weight(0.40f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (showChrome) OrbitChromeRow(state = state, onNavigateBack = onNavigateBack)
            StartTargetBar(state = state)
            OrbitStatusPanel(state = state)
            DrawPathHint(state = state, introStage = introStage)
            Spacer(modifier = Modifier.weight(1f))
            OrbitPrimaryButton(state, introStage, viewModel::launch) { viewModel.clearPath(clearAttempts = false) }
        }
        Box(modifier = Modifier.weight(0.60f).fillMaxHeight(), contentAlignment = Alignment.Center) {
            OrbitGrid(
                state = state,
                drawingEnabled = true,
                targetBlink = introStage == 1,
                onStartPath = viewModel::startDrawnPath,
                onExtendPath = viewModel::extendDrawnPath,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@Composable
private fun OrbitChromeRow(state: OrbitBuilderUiState, onNavigateBack: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Button(shape = RoundedCornerShape(12.dp), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp), onClick = onNavigateBack, colors = ButtonDefaults.buttonColors(containerColor = CockpitGray, contentColor = StarWhite)) { Text("‹") }
        DifficultyStars(stars = state.currentLevel.difficultyStars)
    }
}

@Composable
private fun StartTargetBar(state: OrbitBuilderUiState) {
    val level = state.currentLevel
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        HorizontalValueCard(Modifier.weight(1f).height(54.dp), stringResource(R.string.orbit_builder_start_value), formatOrbitNumber(level.startValue), PlanetBlue, StarWhite)
        HorizontalValueCard(Modifier.weight(1f).height(54.dp), stringResource(R.string.orbit_builder_target_value), formatOrbitNumber(level.targetValue), PlanetGold, DeepSpaceBlue)
    }
}

@Composable
private fun HorizontalValueCard(modifier: Modifier, label: String, value: String, accent: Color, textColor: Color) {
    Card(modifier = modifier, colors = CardDefaults.cardColors(containerColor = accent.copy(alpha = 0.94f))) {
        Row(modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, color = textColor.copy(alpha = 0.9f), fontSize = 12.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Start, lineHeight = 14.sp, modifier = Modifier.weight(1f))
            Text(value, color = textColor, fontWeight = FontWeight.Black, fontSize = 24.sp, textAlign = TextAlign.End, lineHeight = 26.sp, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun OrbitStatusPanel(state: OrbitBuilderUiState) {
    val level = state.currentLevel
    Card(colors = CardDefaults.cardColors(containerColor = CockpitGray.copy(alpha = 0.9f)), modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(level.title, color = StarWhite, fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 1)
            Text(level.instruction, color = StarWhite.copy(alpha = 0.78f), fontSize = 12.sp, lineHeight = 15.sp, maxLines = 2)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                CompactValueChip(stringResource(R.string.orbit_builder_current_value), formatOrbitNumber(state.currentValue), PlanetGold)
                CompactValueChip(stringResource(R.string.orbit_builder_path_used), state.pathCellsUsed.toString(), PlanetBlue)
            }
        }
    }
}

@Composable
private fun CompactValueChip(label: String, value: String, accent: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, color = StarWhite.copy(alpha = 0.72f), fontSize = 10.sp)
        Text(value, color = accent, fontWeight = FontWeight.Bold, fontSize = 17.sp)
    }
}

@Composable
private fun OrbitGrid(
    state: OrbitBuilderUiState,
    drawingEnabled: Boolean,
    targetBlink: Boolean,
    onStartPath: (Int, Int) -> Unit,
    onExtendPath: (Int, Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val level = state.currentLevel
    var gridSize by remember { mutableStateOf(IntSize.Zero) }
    val aspect = level.columns.toFloat() / level.rows.toFloat()
    val rocketPosition = state.rocketPosition
    val currentDirection = state.simulationResult?.path?.getOrNull(state.launchStepIndex)?.direction ?: directionFromDrawnPath(state.drawnPath) ?: level.initialDirection

    fun offsetToCell(offset: androidx.compose.ui.geometry.Offset): Pair<Int, Int>? {
        if (gridSize.width <= 0 || gridSize.height <= 0) return null
        val cellWidth = gridSize.width.toFloat() / level.columns.toFloat()
        val cellHeight = gridSize.height.toFloat() / level.rows.toFloat()
        val column = (offset.x / cellWidth).toInt()
        val row = (offset.y / cellHeight).toInt()
        return if (row in 0 until level.rows && column in 0 until level.columns) row to column else null
    }

    BoxWithConstraints(modifier = modifier) {
        val maxGridWidth = maxWidth
        val maxGridHeight = maxHeight
        val heightDrivenWidth = maxGridHeight * aspect
        val gridWidth = if (heightDrivenWidth < maxGridWidth) heightDrivenWidth else maxGridWidth
        val cellSize = gridWidth / level.columns
        val rocketSize = cellSize * 0.70f
        val targetX = cellSize * rocketPosition.column + (cellSize - rocketSize) / 2f
        val targetY = cellSize * rocketPosition.row + (cellSize - rocketSize) / 2f
        val currentTile = state.simulationResult?.path?.getOrNull(state.launchStepIndex)?.tile
        val isInitialLaunchMove = state.phase == OrbitBuilderPhase.LAUNCHING && state.launchStepIndex == 1
        val isFlyingOverNumberCell = currentTile is OrbitFixedTile.Gate
        val stepDurationMs = when {
            isInitialLaunchMove -> 500
            isFlyingOverNumberCell -> 1000
            else -> 220
        }
        val stepEasing = when {
            isInitialLaunchMove -> FastOutLinearInEasing
            isFlyingOverNumberCell -> OrbitNumberCellFlyoverEasing
            else -> LinearEasing
        }
        val animatedX by animateDpAsState(targetX, tween(stepDurationMs, easing = stepEasing), label = "orbitRocketX")
        val animatedY by animateDpAsState(targetY, tween(stepDurationMs, easing = stepEasing), label = "orbitRocketY")
        val animatedRotation by animateFloatAsState(directionToRotation(currentDirection), tween(stepDurationMs, easing = stepEasing), label = "orbitRocketRotation")

        Box(
            modifier = Modifier.width(gridWidth).aspectRatio(aspect).clip(RoundedCornerShape(18.dp))
                .background(NebulaPurple.copy(alpha = 0.42f))
                .border(1.dp, PlanetBlue.copy(alpha = 0.35f), RoundedCornerShape(18.dp))
                .onSizeChanged { gridSize = it }
                .pointerInput(drawingEnabled, state.phase, state.currentLevel.id) {
                    if (!drawingEnabled || state.phase != OrbitBuilderPhase.PLANNING) return@pointerInput
                    detectDragGestures(
                        onDragStart = { offset -> offsetToCell(offset)?.let { (row, column) -> onStartPath(row, column) } },
                        onDrag = { change, _ -> offsetToCell(change.position)?.let { (row, column) -> onExtendPath(row, column) } }
                    )
                },
            contentAlignment = Alignment.TopStart
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                repeat(level.rows) { row ->
                    Row(modifier = Modifier.fillMaxWidth()) {
                        repeat(level.columns) { column ->
                            OrbitCell(state, row, column, targetBlink, Modifier.size(cellSize))
                        }
                    }
                }
            }
            OrbitRocketSprite(Modifier.offset(x = animatedX, y = animatedY).size(rocketSize).graphicsLayer { rotationZ = animatedRotation })
        }
    }
}

@Composable
private fun OrbitRocketSprite(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val candidates = listOf("ic_galaxycon_rocket", "galaxycon_rocket", "ic_galaxycon_rocket_retro", "galaxycon_rocket_retro", "ic_galaxycon_ship", "galaxycon_ship", "ic_galaxycon_player", "galaxycon_player", "ic_space_rocket", "space_rocket", "ic_rocket_galaxycon", "rocket_galaxycon", "ic_retro_rocket", "retro_rocket", "ic_orbit_builder_retro_rocket", "ic_rocket", "rocket")
    val resId = remember(context) { candidates.firstNotNullOfOrNull { name -> context.resources.getIdentifier(name, "drawable", context.packageName).takeIf { it != 0 } } }
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        if (resId != null && resId != 0) {
            Image(painter = painterResource(id = resId), contentDescription = "Rocket", contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
        } else {
            Text(text = "🚀", fontSize = 24.sp, modifier = Modifier.fillMaxSize(), textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun OrbitCell(state: OrbitBuilderUiState, row: Int, column: Int, targetBlink: Boolean, modifier: Modifier) {
    val level = state.currentLevel
    val position = OrbitPosition(row, column)
    val fixedTile = level.tileAt(row, column)
    val isStartPathCell = fixedTile == OrbitFixedTile.StartRocket && position in state.drawnPath
    val isInDrawnPath = position in state.drawnPath
    val isPathEnd = state.drawnPath.lastOrNull() == position
    val isRocketHere = state.rocketPosition == position
    val targetAlpha = if (targetBlink) 0.48f else 0.28f
    val backgroundColor = when {
        isRocketHere -> PlanetGold.copy(alpha = 0.16f)
        fixedTile == OrbitFixedTile.TargetPlanet -> PlanetGold.copy(alpha = targetAlpha)
        isPathEnd -> PlanetGold.copy(alpha = 0.24f)
        isInDrawnPath -> PlanetBlue.copy(alpha = 0.32f)
        fixedTile is OrbitFixedTile.Gate -> PlanetBlue.copy(alpha = 0.14f)
        fixedTile == OrbitFixedTile.Asteroid -> PlanetRed.copy(alpha = 0.18f)
        else -> CockpitGray.copy(alpha = 0.68f)
    }
    Box(
        modifier = modifier.padding(2.dp).aspectRatio(1f).clip(RoundedCornerShape(10.dp)).background(backgroundColor)
            .border(if (fixedTile == OrbitFixedTile.TargetPlanet) 2.dp else 1.dp, if (fixedTile == OrbitFixedTile.TargetPlanet) PlanetGold.copy(alpha = if (targetBlink) 1f else 0.82f) else StarWhite.copy(alpha = 0.08f), RoundedCornerShape(10.dp)),
        contentAlignment = Alignment.Center
    ) {
        val label = when {
            fixedTile == OrbitFixedTile.StartRocket && isInDrawnPath -> "•"
            fixedTile == OrbitFixedTile.StartRocket -> ""
            fixedTile == OrbitFixedTile.TargetPlanet -> formatOrbitNumber(level.targetValue)
            fixedTile == OrbitFixedTile.Asteroid -> "☄"
            fixedTile is OrbitFixedTile.Gate -> fixedTile.gate.label
            isInDrawnPath -> "•"
            else -> ""
        }
        Text(label, color = when {
            fixedTile == OrbitFixedTile.Asteroid -> PlanetRed
            fixedTile == OrbitFixedTile.TargetPlanet -> PlanetGold
            fixedTile is OrbitFixedTile.Gate -> PlanetGold
            isInDrawnPath -> PlanetBlue
            else -> StarWhite
        }, fontWeight = FontWeight.Bold, fontSize = when {
            fixedTile == OrbitFixedTile.TargetPlanet -> 14.sp
            label.length > 2 -> 13.sp
            isInDrawnPath -> 28.sp
            else -> 20.sp
        }, lineHeight = 15.sp, textAlign = TextAlign.Center)
    }
}

@Composable
private fun DrawPathHint(state: OrbitBuilderUiState, introStage: Int) {
    val text = when {
        introStage == 0 -> stringResource(R.string.orbit_builder_start_value)
        introStage == 1 -> stringResource(R.string.orbit_builder_target_value)
        state.drawnPath.isEmpty() -> stringResource(R.string.orbit_builder_draw_route)
        !state.hasTouchedTarget -> stringResource(R.string.orbit_builder_draw_to_planet)
        else -> stringResource(R.string.orbit_builder_ready_to_launch)
    }
    Text(text, color = if (state.hasTouchedTarget || introStage < 2) PlanetGold else StarWhite.copy(alpha = 0.82f), textAlign = TextAlign.Center, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, lineHeight = 16.sp, modifier = Modifier.fillMaxWidth())
}

@Composable
private fun OrbitPrimaryButton(state: OrbitBuilderUiState, introStage: Int, onLaunch: () -> Unit, onReset: () -> Unit) {
    val shouldLaunch = state.phase == OrbitBuilderPhase.PLANNING && state.drawnPath.size > 1
    val label = if (shouldLaunch) stringResource(R.string.orbit_builder_launch) else stringResource(R.string.orbit_builder_reset)
    val container = if (shouldLaunch) PlanetBlue else CockpitGray
    val content = if (shouldLaunch) DeepSpaceBlue else StarWhite
    Button(shape = RoundedCornerShape(12.dp), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp), onClick = { if (shouldLaunch) onLaunch() else onReset() }, enabled = state.phase == OrbitBuilderPhase.PLANNING && (shouldLaunch || state.drawnPath.isNotEmpty()), modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = container, contentColor = content, disabledContainerColor = container.copy(alpha = 0.25f), disabledContentColor = content.copy(alpha = 0.55f))) {
        Text(label, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun IntroStartValueOverlay(state: OrbitBuilderUiState, visible: Boolean, modifier: Modifier = Modifier) {
    if (!visible) return
    WhiteValueCircle(value = formatOrbitNumber(state.currentLevel.startValue), modifier = modifier, circleSize = 224, fontSize = 92)
}

@Composable
private fun LaunchValueOverlay(state: OrbitBuilderUiState, modifier: Modifier = Modifier) {
    val currentStep = state.simulationResult?.path?.getOrNull(state.launchStepIndex)
    val gateTile = currentStep?.tile as? OrbitFixedTile.Gate
    val visible = state.phase == OrbitBuilderPhase.LAUNCHING && gateTile != null
    if (!visible || currentStep == null) return
    WhiteValueCircle(value = formatOrbitNumber(currentStep.value), modifier = modifier, circleSize = 224, fontSize = 92)
}

@Composable
private fun WhiteValueCircle(
    value: String,
    modifier: Modifier = Modifier,
    circleSize: Int = 112,
    fontSize: Int = 34
) {
    Box(
        modifier = modifier
            .size(circleSize.dp)
            .clip(CircleShape)
            .background(
                Brush.radialGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.36f),
                        Color.White.copy(alpha = 0.16f),
                        Color.Transparent
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = value,
            color = Color.White,
            fontWeight = FontWeight.Black,
            fontSize = fontSize.sp,
            lineHeight = fontSize.sp,
            textAlign = TextAlign.Center
        )
    }
}

private val OrbitNumberCellFlyoverEasing = Easing { fraction ->
    val f = fraction.coerceIn(0f, 1f)
    if (f < 0.5f) {
        (0.5 * sin(PI * f.toDouble())).toFloat()
    } else {
        (1.0 - 0.5 * cos(PI * (f.toDouble() - 0.5))).toFloat()
    }
}


private fun directionFromDrawnPath(path: List<OrbitPosition>): OrbitDirection? {
    if (path.size < 2) return null
    val a = path[path.lastIndex - 1]
    val b = path.last()
    return when {
        b.row == a.row - 1 && b.column == a.column -> OrbitDirection.UP
        b.row == a.row + 1 && b.column == a.column -> OrbitDirection.DOWN
        b.row == a.row && b.column == a.column - 1 -> OrbitDirection.LEFT
        b.row == a.row && b.column == a.column + 1 -> OrbitDirection.RIGHT
        else -> null
    }
}

private fun directionToRotation(direction: OrbitDirection): Float = when (direction) {
    OrbitDirection.UP -> 0f
    OrbitDirection.RIGHT -> 90f
    OrbitDirection.DOWN -> 180f
    OrbitDirection.LEFT -> 270f
}

fun formatOrbitNumber(value: Int): String {
    val symbols = DecimalFormatSymbols(Locale("nl", "BE")).apply {
        groupingSeparator = '.'
        decimalSeparator = ','
    }
    return DecimalFormat("#,##0", symbols).format(value)
}
