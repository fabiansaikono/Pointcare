package be.saikono.poincare.ui.puzzle

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import be.saikono.poincare.ui.theme.CockpitGray
import be.saikono.poincare.ui.theme.DeepSpaceBlue
import be.saikono.poincare.ui.theme.NebulaPurple
import be.saikono.poincare.ui.theme.PlanetBlue
import be.saikono.poincare.ui.theme.PlanetGold
import be.saikono.poincare.ui.theme.StarWhite
import kotlin.math.roundToInt

@Composable
fun ConstellationsPuzzleGame(
    grade: Int,
    difficultyStars: Int,
    exerciseLanguage: String,
    onComplete: () -> Unit,
    missionKey: Any? = null,
    modifier: Modifier = Modifier,
    onConnectionAttempt: (ConstellationConnectionAttempt) -> Unit = {},
    onSpeakText: ((String) -> Unit)? = null
) {
    val copy = remember(exerciseLanguage) { constellationsCopy(exerciseLanguage) }
    var missionSeed by remember(grade, difficultyStars, exerciseLanguage, missionKey) { mutableStateOf(System.currentTimeMillis()) }
    var puzzle by remember(grade, difficultyStars, exerciseLanguage, missionKey, missionSeed) {
        mutableStateOf(
            ConstellationPuzzleGenerator.generate(
                grade = grade,
                difficultyStars = difficultyStars,
                seed = missionSeed
            )
        )
    }
    var selectedEdges by remember(puzzle.id) { mutableStateOf<Set<ConstellationEdge>>(emptySet()) }
    var firstSelectedStarId by remember(puzzle.id) { mutableStateOf<String?>(null) }
    var focusedEdge by remember(puzzle.id) { mutableStateOf<ConstellationEdge?>(null) }
    var feedback by remember(puzzle.id) { mutableStateOf<String?>(null) }
    var completionReported by remember(puzzle.id) { mutableStateOf(false) }

    val orderedRequiredEdges = puzzle.connections.map { ConstellationEdge.of(it.fromStarId, it.toStarId) }
    val requiredEdges = orderedRequiredEdges.toSet()
    val activeGoalEdge = focusedEdge?.takeIf { it in requiredEdges && it !in selectedEdges }
        ?: orderedRequiredEdges.firstOrNull { it !in selectedEdges }
    val isComplete = requiredEdges.isNotEmpty() && selectedEdges.containsAll(requiredEdges)

    LaunchedEffect(isComplete, puzzle.id) {
        if (isComplete && !completionReported) {
            completionReported = true
            feedback = copy.completed
            onComplete()
        }
    }

    fun newMission() {
        missionSeed = System.nanoTime()
    }

    fun resetPath() {
        selectedEdges = emptySet()
        firstSelectedStarId = null
        focusedEdge = null
        feedback = null
        completionReported = false
    }

    fun showTip() {
        val missing = activeGoalEdge ?: orderedRequiredEdges.firstOrNull { it !in selectedEdges }
        focusedEdge = missing
        feedback = if (missing == null) copy.completed else constellationTipForNextEdge(puzzle, missing, exerciseLanguage)
    }

    fun onGoalClicked(edge: ConstellationEdge) {
        if (edge in selectedEdges || edge !in requiredEdges) return
        focusedEdge = edge
        firstSelectedStarId = null
        feedback = constellationTipForNextEdge(puzzle, edge, exerciseLanguage)
    }

    fun starHasOpenRequiredConnection(starId: String, edges: Set<ConstellationEdge> = selectedEdges): Boolean = requiredEdges.any { edge ->
        (edge.a == starId || edge.b == starId) && edge !in edges
    }

    fun connectStars(fromStarId: String, toStarId: String): Boolean {
        if (isComplete || fromStarId == toStarId) return false

        val edge = ConstellationEdge.of(fromStarId, toStarId)
        val edgeIsRequired = edge in requiredEdges
        val alreadyConnected = edge in selectedEdges
        val newSelectedEdges = if (edgeIsRequired && !alreadyConnected) selectedEdges + edge else selectedEdges
        selectedEdges = newSelectedEdges

        val completedAfterConnection = newSelectedEdges.containsAll(requiredEdges)
        val nextMissingEdge = orderedRequiredEdges.firstOrNull { it !in newSelectedEdges }
        if (edgeIsRequired && !alreadyConnected) {
            focusedEdge = nextMissingEdge
        }

        feedback = when {
            alreadyConnected -> copy.alreadyConnected
            edgeIsRequired -> if (completedAfterConnection) copy.completed else copy.correctConnection
            else -> copy.wrongConnection
        }

        onConnectionAttempt(
            ConstellationConnectionAttempt(
                puzzleId = puzzle.id,
                constellationId = puzzle.templateId,
                fromStarId = fromStarId,
                toStarId = toStarId,
                correct = edgeIsRequired && !alreadyConnected,
                completed = completedAfterConnection,
                selectedConnectionCount = newSelectedEdges.size,
                requiredConnectionCount = requiredEdges.size
            )
        )
        return edgeIsRequired && !alreadyConnected
    }

    fun onConnectionDrawn(fromStarId: String, toStarId: String) {
        connectStars(fromStarId, toStarId)
        firstSelectedStarId = null
    }

    fun onStarTapped(starId: String) {
        if (isComplete) return
        val first = firstSelectedStarId
        if (first == null) {
            firstSelectedStarId = starId
            val edgeForStar = activeGoalEdge?.takeIf { it.a == starId || it.b == starId }
                ?: orderedRequiredEdges.firstOrNull { edge ->
                    edge !in selectedEdges && (edge.a == starId || edge.b == starId)
                }
            focusedEdge = edgeForStar
            feedback = edgeForStar?.let { constellationPartnerHint(puzzle, starId, it, exerciseLanguage) } ?: copy.firstStarSelected
            return
        }
        if (first == starId) {
            firstSelectedStarId = null
            feedback = null
            return
        }
        connectStars(first, starId)
        firstSelectedStarId = null
    }

    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE

    if (isLandscape) {
        Row(
            modifier = modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ConstellationsSidePanel(
                puzzle = puzzle,
                copy = copy,
                exerciseLanguage = exerciseLanguage,
                feedback = feedback,
                selectedEdges = selectedEdges,
                activeGoalEdge = activeGoalEdge,
                isComplete = isComplete,
                onMissionClicked = { onSpeakText?.invoke(puzzle.mission.get(exerciseLanguage)) },
                onGoalClicked = ::onGoalClicked,
                onNewMission = ::newMission,
                onResetPath = ::resetPath,
                onTip = ::showTip,
                modifier = Modifier
                    .weight(0.40f)
                    .fillMaxHeight()
            )
            ConstellationCanvasCard(
                puzzle = puzzle,
                selectedEdges = selectedEdges,
                focusedEdge = activeGoalEdge,
                firstSelectedStarId = firstSelectedStarId,
                isComplete = isComplete,
                onStarTapped = ::onStarTapped,
                onConnectionDrawn = ::onConnectionDrawn,
                modifier = Modifier
                    .weight(0.60f)
                    .fillMaxHeight()
            )
        }
    } else {
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            ConstellationsHeader(
                puzzle = puzzle,
                copy = copy,
                exerciseLanguage = exerciseLanguage,
                feedback = feedback,
                isComplete = isComplete,
                onMissionClicked = { onSpeakText?.invoke(puzzle.mission.get(exerciseLanguage)) },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))
            ConstellationGoalsPanel(
                puzzle = puzzle,
                selectedEdges = selectedEdges,
                activeGoalEdge = activeGoalEdge,
                exerciseLanguage = exerciseLanguage,
                onGoalClicked = ::onGoalClicked,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 132.dp)
            )
            Spacer(modifier = Modifier.height(10.dp))
            ConstellationCanvasCard(
                puzzle = puzzle,
                selectedEdges = selectedEdges,
                focusedEdge = activeGoalEdge,
                firstSelectedStarId = firstSelectedStarId,
                isComplete = isComplete,
                onStarTapped = ::onStarTapped,
                onConnectionDrawn = ::onConnectionDrawn,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            )
            Spacer(modifier = Modifier.height(12.dp))
            ConstellationsBottomControls(
                copy = copy,
                isComplete = isComplete,
                onTip = ::showTip,
                onResetPath = ::resetPath,
                onNewMission = ::newMission,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun ConstellationsSidePanel(
    puzzle: ConstellationPuzzle,
    copy: ConstellationsCopy,
    exerciseLanguage: String,
    feedback: String?,
    selectedEdges: Set<ConstellationEdge>,
    activeGoalEdge: ConstellationEdge?,
    isComplete: Boolean,
    onMissionClicked: () -> Unit,
    onGoalClicked: (ConstellationEdge) -> Unit,
    onNewMission: () -> Unit,
    onResetPath: () -> Unit,
    onTip: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.SpaceBetween,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        ConstellationsHeader(
            puzzle = puzzle,
            copy = copy,
            exerciseLanguage = exerciseLanguage,
            feedback = feedback,
            isComplete = isComplete,
            onMissionClicked = onMissionClicked,
            modifier = Modifier.fillMaxWidth()
        )
        ConstellationGoalsPanel(
            puzzle = puzzle,
            selectedEdges = selectedEdges,
            activeGoalEdge = activeGoalEdge,
            exerciseLanguage = exerciseLanguage,
            onGoalClicked = onGoalClicked,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        )
        ConstellationsBottomControls(
            copy = copy,
            isComplete = isComplete,
            onTip = onTip,
            onResetPath = onResetPath,
            onNewMission = onNewMission,
            modifier = Modifier.fillMaxWidth()
        )
    }
}


@Composable
private fun ConstellationGoalsPanel(
    puzzle: ConstellationPuzzle,
    selectedEdges: Set<ConstellationEdge>,
    activeGoalEdge: ConstellationEdge?,
    exerciseLanguage: String,
    onGoalClicked: (ConstellationEdge) -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    val orderedEdges = puzzle.connections.map { ConstellationEdge.of(it.fromStarId, it.toStarId) }
    val completedCount = orderedEdges.count { it in selectedEdges }
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = CockpitGray.copy(alpha = 0.38f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp)
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = constellationGoalTitle(exerciseLanguage, completedCount, orderedEdges.size),
                color = PlanetGold,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            orderedEdges.forEachIndexed { index, edge ->
                val completed = edge in selectedEdges
                val focused = edge == activeGoalEdge && !completed
                val prompt = constellationConnectionPrompt(puzzle, edge, exerciseLanguage)
                Surface(
                    color = when {
                        completed -> CockpitGray.copy(alpha = 0.34f)
                        focused -> PlanetGold.copy(alpha = 0.94f)
                        else -> DeepSpaceBlue.copy(alpha = 0.52f)
                    },
                    contentColor = when {
                        completed -> StarWhite.copy(alpha = 0.48f)
                        focused -> DeepSpaceBlue
                        else -> StarWhite
                    },
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = !completed) { onGoalClicked(edge) }
                ) {
                    Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                        Text(
                            text = if (completed) "✓ ${prompt.main}" else "${index + 1}. ${prompt.main}",
                            color = when {
                                completed -> StarWhite.copy(alpha = 0.48f)
                                focused -> DeepSpaceBlue
                                else -> StarWhite
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = prompt.rule,
                            color = when {
                                completed -> StarWhite.copy(alpha = 0.38f)
                                focused -> DeepSpaceBlue.copy(alpha = 0.78f)
                                else -> StarWhite.copy(alpha = 0.70f)
                            },
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ConstellationsHeader(
    puzzle: ConstellationPuzzle,
    copy: ConstellationsCopy,
    exerciseLanguage: String,
    feedback: String?,
    isComplete: Boolean,
    onMissionClicked: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {

        Text(
            text = puzzle.mission.get(exerciseLanguage),
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onMissionClicked() }
                .semantics { contentDescription = puzzle.mission.get(exerciseLanguage) },
            color = StarWhite,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center
        )

        if (feedback != null) {
            Text(
                text = feedback,
                color = if (isComplete) PlanetGold else PlanetBlue,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }

        if (isComplete) {
            Surface(
                color = CockpitGray.copy(alpha = 0.60f),
                contentColor = StarWhite,
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "${copy.factPrefix}: ${puzzle.fact.get(exerciseLanguage)}",
                    color = PlanetGold,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
                )
            }
        }
    }
}

@Composable
private fun ConstellationsBottomControls(
    copy: ConstellationsCopy,
    isComplete: Boolean,
    onTip: () -> Unit,
    onResetPath: () -> Unit,
    onNewMission: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (isComplete) return

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Button(
            onClick = onTip,
            enabled = true,
            colors = ButtonDefaults.buttonColors(
                containerColor = PlanetBlue,
                contentColor = StarWhite
            ),
            shape = RoundedCornerShape(999.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
        ) {
            Text(copy.tip, color = StarWhite, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun ConstellationCanvasCard(
    puzzle: ConstellationPuzzle,
    selectedEdges: Set<ConstellationEdge>,
    focusedEdge: ConstellationEdge?,
    firstSelectedStarId: String?,
    isComplete: Boolean,
    onStarTapped: (String) -> Unit,
    onConnectionDrawn: (String, String) -> Unit,
    modifier: Modifier = Modifier
) {
    var activeDragStarId by remember(puzzle.id) { mutableStateOf<String?>(null) }
    var dragPosition by remember(puzzle.id) { mutableStateOf<Offset?>(null) }

    val infiniteTransition = rememberInfiniteTransition()
    val completePulse by infiniteTransition.animateFloat(
        initialValue = 0.72f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 650),
            repeatMode = RepeatMode.Reverse
        )
    )
    val completeScale by infiniteTransition.animateFloat(
        initialValue = 0.94f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 650),
            repeatMode = RepeatMode.Reverse
        )
    )

    Card(
        modifier = modifier,
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = CockpitGray.copy(alpha = 0.42f))
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            DeepSpaceBlue.copy(alpha = 0.95f),
                            NebulaPurple.copy(alpha = 0.55f),
                            DeepSpaceBlue.copy(alpha = 0.98f)
                        )
                    )
                )
        ) {
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(8.dp)
            ) {
                val density = LocalDensity.current
                val widthPx = with(density) { maxWidth.toPx() }
                val heightPx = with(density) { maxHeight.toPx() }
                val hasLongLabels = puzzle.stars.any { it.isTarget && ((it.label ?: it.order?.toString()).orEmpty().length > 3) }
                val starSize = when {
                    maxWidth < 380.dp || maxHeight < 360.dp -> if (hasLongLabels) 44.dp else 38.dp
                    hasLongLabels -> 52.dp
                    else -> 44.dp
                }
                val starSizePx = with(density) { starSize.toPx() }
                val touchRadius = starSizePx * 0.92f
                val requiredEdges = puzzle.requiredEdges

                fun starHasOpenRequiredConnection(starId: String): Boolean = requiredEdges.any { edge ->
                    (edge.a == starId || edge.b == starId) && edge !in selectedEdges
                }

                fun starIsPartOfSelectedPath(starId: String): Boolean = selectedEdges.any { edge ->
                    edge.a == starId || edge.b == starId
                }

                fun starIsFocusedGoal(starId: String): Boolean = focusedEdge?.let { edge ->
                    edge !in selectedEdges && (edge.a == starId || edge.b == starId)
                } == true

                fun pointFor(starId: String): Offset? {
                    val star = puzzle.stars.firstOrNull { it.id == starId } ?: return null
                    return Offset(star.x * widthPx, star.y * heightPx)
                }

                fun nearestStar(position: Offset): ConstellationStar? = puzzle.stars
                    .map { star ->
                        val center = Offset(star.x * widthPx, star.y * heightPx)
                        val dx = center.x - position.x
                        val dy = center.y - position.y
                        star to (dx * dx + dy * dy)
                    }
                    .minByOrNull { it.second }
                    ?.takeIf { it.second <= touchRadius * touchRadius }
                    ?.first

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(puzzle.id, selectedEdges, isComplete, widthPx, heightPx) {
                            detectDragGestures(
                                onDragStart = { startOffset ->
                                    if (isComplete) {
                                        activeDragStarId = null
                                        dragPosition = null
                                    } else {
                                        val startStar = nearestStar(startOffset)
                                        activeDragStarId = startStar?.id
                                        dragPosition = startStar?.let { startOffset }
                                    }
                                },
                                onDrag = { change, _ ->
                                    if (activeDragStarId != null) {
                                        dragPosition = change.position
                                        change.consume()
                                    }
                                },
                                onDragEnd = {
                                    val from = activeDragStarId
                                    val end = dragPosition?.let { nearestStar(it) }
                                    if (from != null && end != null && from != end.id) {
                                        onConnectionDrawn(from, end.id)
                                    }
                                    activeDragStarId = null
                                    dragPosition = null
                                },
                                onDragCancel = {
                                    activeDragStarId = null
                                    dragPosition = null
                                }
                            )
                        }
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        if (isComplete) {
                            requiredEdges.forEach { edge ->
                                val from = pointFor(edge.a)
                                val to = pointFor(edge.b)
                                if (from != null && to != null) {
                                    drawLine(
                                        color = StarWhite.copy(alpha = 0.12f + completePulse * 0.18f),
                                        start = from,
                                        end = to,
                                        strokeWidth = 13f,
                                        cap = StrokeCap.Round
                                    )
                                }
                            }
                        }

                        selectedEdges.forEach { edge ->
                            val from = pointFor(edge.a)
                            val to = pointFor(edge.b)
                            if (from != null && to != null) {
                                drawLine(
                                    color = StarWhite.copy(alpha = if (isComplete) 0.92f + completePulse * 0.08f else 0.88f),
                                    start = from,
                                    end = to,
                                    strokeWidth = if (isComplete) 7f else 5f,
                                    cap = StrokeCap.Round
                                )
                            }
                        }

                        val activeStart = activeDragStarId?.let { pointFor(it) }
                        val activeEnd = dragPosition
                        if (!isComplete && activeStart != null && activeEnd != null) {
                            drawLine(
                                color = PlanetBlue.copy(alpha = 0.78f),
                                start = activeStart,
                                end = activeEnd,
                                strokeWidth = 5f,
                                cap = StrokeCap.Round
                            )
                        }
                    }

                    puzzle.stars.forEach { star ->
                        val label = star.label ?: star.order?.toString().orEmpty()
                        val isSelected = star.id == activeDragStarId || star.id == firstSelectedStarId
                        val isConnected = starIsPartOfSelectedPath(star.id)
                        val isFocusedGoal = starIsFocusedGoal(star.id)
                        val starColor = when {
                            isSelected -> PlanetBlue
                            isFocusedGoal -> PlanetGold
                            isComplete && star.isTarget -> PlanetGold
                            isConnected -> StarWhite
                            star.isTarget -> StarWhite
                            else -> StarWhite.copy(alpha = 0.42f)
                        }
                        val textColor = when {
                            isSelected -> StarWhite
                            isFocusedGoal || isConnected || isComplete || star.isTarget -> DeepSpaceBlue
                            else -> DeepSpaceBlue.copy(alpha = 0.55f)
                        }
                        val starAlpha = when {
                            isComplete && star.isTarget -> (0.70f + completePulse * 0.30f).coerceIn(0.70f, 1f)
                            else -> (0.68f + star.brightness * 0.24f).coerceIn(0.55f, 0.98f)
                        }
                        val starScale = if (isComplete && star.isTarget) completeScale else 1f
                        val x = (star.x * widthPx - starSizePx / 2f).roundToInt()
                        val y = (star.y * heightPx - starSizePx / 2f).roundToInt()

                        Box(
                            modifier = Modifier
                                .offset { IntOffset(x, y) }
                                .size(starSize)
                                .graphicsLayer {
                                    scaleX = starScale
                                    scaleY = starScale
                                    alpha = starAlpha
                                }
                                .clip(CircleShape)
                                .background(starColor)
                                .clickable { onStarTapped(star.id) }
                                .semantics {
                                    contentDescription = if (star.name.isBlank()) "Star" else star.name
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (star.isTarget && label.isNotBlank()) label else "✦",
                                color = textColor,
                                fontSize = when {
                                    !star.isTarget -> 16.sp
                                    label.length >= 5 -> 9.sp
                                    label.length >= 4 -> 10.sp
                                    label.length >= 3 -> 12.sp
                                    else -> 15.sp
                                },
                                fontWeight = FontWeight.Black,
                                textAlign = TextAlign.Center,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }
    }
}



private data class ConstellationPrompt(val main: String, val rule: String)

private fun constellationGoalTitle(locale: String, completed: Int, total: Int): String = when (normalizeGalaxyconLocale(locale)) {
    "en-US" -> "Connections $completed/$total"
    "fr-BE" -> "Lignes $completed/$total"
    else -> "Verbindingen $completed/$total"
}

private fun constellationConnectionPrompt(
    puzzle: ConstellationPuzzle,
    edge: ConstellationEdge,
    locale: String
): ConstellationPrompt {
    val labels = orderedLabelsForEdge(puzzle, edge)
    val left = labels.first
    val right = labels.second
    return ConstellationPrompt(
        main = "$left ↔ $right",
        rule = constellationShortRuleText(left, right, locale)
    )
}


private fun constellationShortRuleText(left: String, right: String, locale: String): String {
    val a = evaluateConstellationLabel(left)
    val b = evaluateConstellationLabel(right)
    val normalized = normalizeGalaxyconLocale(locale)
    if (a != null && b != null) {
        if (kotlin.math.abs(a - b) < 0.0001f) {
            return when (normalized) {
                "en-US" -> "same value"
                "fr-BE" -> "même valeur"
                else -> "zelfde waarde"
            }
        }
        val low = minOf(a, b)
        val high = maxOf(a, b)
        val diff = high - low
        val multiplier = if (low != 0f) high / low else null
        val wholeMultiplier = multiplier?.roundToInt()
        if (wholeMultiplier != null && wholeMultiplier in 2..12 && kotlin.math.abs(multiplier - wholeMultiplier) < 0.0001f) {
            return "${formatConstellationNumber(low)} × $wholeMultiplier = ${formatConstellationNumber(high)}"
        }
        return "${formatConstellationNumber(low)} + ${formatConstellationNumber(diff)} = ${formatConstellationNumber(high)}"
    }
    return when (normalized) {
        "en-US" -> "same pattern"
        "fr-BE" -> "même modèle"
        else -> "zelfde patroon"
    }
}

private fun constellationPartnerHint(
    puzzle: ConstellationPuzzle,
    selectedStarId: String,
    edge: ConstellationEdge,
    locale: String
): String {
    val selected = puzzle.stars.firstOrNull { it.id == selectedStarId }
    val partnerId = if (edge.a == selectedStarId) edge.b else edge.a
    val partner = puzzle.stars.firstOrNull { it.id == partnerId }
    val selectedLabel = selected?.label?.takeIf { it.isNotBlank() } ?: selected?.order?.toString().orEmpty()
    val partnerLabel = partner?.label?.takeIf { it.isNotBlank() } ?: partner?.order?.toString().orEmpty()
    return when (normalizeGalaxyconLocale(locale)) {
        "en-US" -> "Now connect $selectedLabel with $partnerLabel."
        "fr-BE" -> "Maintenant, relie $selectedLabel avec $partnerLabel."
        else -> "Verbind nu $selectedLabel met $partnerLabel."
    }
}

private fun orderedLabelsForEdge(puzzle: ConstellationPuzzle, edge: ConstellationEdge): Pair<String, String> {
    val first = puzzle.stars.firstOrNull { it.id == edge.a }
    val second = puzzle.stars.firstOrNull { it.id == edge.b }
    val ordered = listOfNotNull(first, second).sortedBy { it.order ?: Int.MAX_VALUE }
    val left = ordered.getOrNull(0)?.label?.takeIf { it.isNotBlank() } ?: ordered.getOrNull(0)?.order?.toString().orEmpty()
    val right = ordered.getOrNull(1)?.label?.takeIf { it.isNotBlank() } ?: ordered.getOrNull(1)?.order?.toString().orEmpty()
    return left to right
}

private fun constellationTipForNextEdge(
    puzzle: ConstellationPuzzle,
    edge: ConstellationEdge,
    locale: String
): String {
    val labels = orderedLabelsForEdge(puzzle, edge)
    val left = labels.first
    val right = labels.second
    val rule = constellationRuleText(left, right, locale)
    return when (normalizeGalaxyconLocale(locale)) {
        "en-US" -> "Tip: connect $left with $right. $rule"
        "fr-BE" -> "Indice : relie $left avec $right. $rule"
        else -> "Tip: verbind $left met $right. $rule"
    }
}

private fun constellationRuleText(left: String, right: String, locale: String): String {
    val a = evaluateConstellationLabel(left)
    val b = evaluateConstellationLabel(right)
    val normalized = normalizeGalaxyconLocale(locale)
    if (a != null && b != null) {
        if (kotlin.math.abs(a - b) < 0.0001f) {
            return when (normalized) {
                "en-US" -> "They have the same value."
                "fr-BE" -> "Ils ont la même valeur."
                else -> "Ze hebben dezelfde waarde."
            }
        }
        val low = minOf(a, b)
        val high = maxOf(a, b)
        val diff = high - low
        val multiplier = if (low != 0f) high / low else null
        val wholeMultiplier = multiplier?.roundToInt()
        if (wholeMultiplier != null && wholeMultiplier in 2..12 && kotlin.math.abs(multiplier - wholeMultiplier) < 0.0001f) {
            return when (normalized) {
                "en-US" -> "Rule: ${formatConstellationNumber(low)} × $wholeMultiplier = ${formatConstellationNumber(high)}."
                "fr-BE" -> "Règle : ${formatConstellationNumber(low)} × $wholeMultiplier = ${formatConstellationNumber(high)}."
                else -> "Regel: ${formatConstellationNumber(low)} × $wholeMultiplier = ${formatConstellationNumber(high)}."
            }
        }
        return when (normalized) {
            "en-US" -> "Rule: ${formatConstellationNumber(low)} + ${formatConstellationNumber(diff)} = ${formatConstellationNumber(high)}."
            "fr-BE" -> "Règle : ${formatConstellationNumber(low)} + ${formatConstellationNumber(diff)} = ${formatConstellationNumber(high)}."
            else -> "Regel: ${formatConstellationNumber(low)} + ${formatConstellationNumber(diff)} = ${formatConstellationNumber(high)}."
        }
    }
    return when (normalized) {
        "en-US" -> "Look for the two values that follow the same pattern."
        "fr-BE" -> "Cherche les deux valeurs qui suivent le même modèle."
        else -> "Zoek de twee waarden die hetzelfde patroon volgen."
    }
}

private fun evaluateConstellationLabel(label: String): Float? {
    val clean = label.trim().replace(',', '.')
    clean.toFloatOrNull()?.let { return it }
    if ('/' in clean) {
        val parts = clean.split('/')
        val numerator = parts.getOrNull(0)?.toFloatOrNull()
        val denominator = parts.getOrNull(1)?.toFloatOrNull()
        if (numerator != null && denominator != null && denominator != 0f) return numerator / denominator
    }
    if ('×' in clean) {
        val parts = clean.split('×')
        val left = parts.getOrNull(0)?.toFloatOrNull()
        val right = parts.getOrNull(1)?.toFloatOrNull()
        if (left != null && right != null) return left * right
    }
    if ('+' in clean) {
        val parts = clean.split('+')
        val left = parts.getOrNull(0)?.toFloatOrNull()
        val right = parts.getOrNull(1)?.toFloatOrNull()
        if (left != null && right != null) return left + right
    }
    val minusIndex = clean.indexOf('-', startIndex = 1)
    if (minusIndex > 0) {
        val left = clean.substring(0, minusIndex).toFloatOrNull()
        val right = clean.substring(minusIndex + 1).toFloatOrNull()
        if (left != null && right != null) return left - right
    }
    return null
}

private fun formatConstellationNumber(value: Float): String {
    val rounded = value.roundToInt()
    return if (kotlin.math.abs(value - rounded) < 0.0001f) rounded.toString() else "%.1f".format(java.util.Locale.US, value)
}

@Preview(showBackground = true, widthDp = 420, heightDp = 760)
@Composable
private fun ConstellationsPuzzleGamePreview() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DeepSpaceBlue)
    ) {
        ConstellationsPuzzleGame(
            grade = 3,
            difficultyStars = 2,
            exerciseLanguage = "nl-BE",
            onComplete = {},
            modifier = Modifier.fillMaxSize()
        )
    }
}
