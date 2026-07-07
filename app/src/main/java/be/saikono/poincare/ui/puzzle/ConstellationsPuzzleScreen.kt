package be.saikono.poincare.ui.puzzle

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
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
import be.saikono.poincare.ui.theme.PlanetRed
import be.saikono.poincare.ui.theme.StarWhite
import kotlin.math.roundToInt

@Composable
fun ConstellationsPuzzleGame(
    grade: Int,
    difficultyStars: Int,
    exerciseLanguage: String,
    onComplete: () -> Unit,
    modifier: Modifier = Modifier,
    onConnectionAttempt: (ConstellationConnectionAttempt) -> Unit = {},
    onSpeakText: ((String) -> Unit)? = null
) {
    val copy = remember(exerciseLanguage) { constellationsCopy(exerciseLanguage) }
    var missionSeed by remember(grade, difficultyStars) { mutableStateOf(System.currentTimeMillis()) }
    var puzzle by remember(grade, difficultyStars, missionSeed) {
        mutableStateOf(
            ConstellationPuzzleGenerator.generate(
                grade = grade,
                difficultyStars = difficultyStars,
                seed = missionSeed
            )
        )
    }
    var firstSelectedStarId by remember(puzzle.id) { mutableStateOf<String?>(null) }
    var selectedEdges by remember(puzzle.id) { mutableStateOf<Set<ConstellationEdge>>(emptySet()) }
    var highlightedTip by remember(puzzle.id) { mutableStateOf<ConstellationEdge?>(null) }
    var feedback by remember(puzzle.id) { mutableStateOf<String?>(null) }
    var completionReported by remember(puzzle.id) { mutableStateOf(false) }

    val requiredEdges = puzzle.requiredEdges
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
        firstSelectedStarId = null
        selectedEdges = emptySet()
        highlightedTip = null
        feedback = null
        completionReported = false
    }

    fun showTip() {
        val missing = requiredEdges.firstOrNull { it !in selectedEdges }
        highlightedTip = missing
        feedback = if (missing == null) copy.completed else copy.tipConnect
    }

    fun onStarTapped(star: ConstellationStar) {
        if (isComplete) return
        highlightedTip = null
        val first = firstSelectedStarId
        if (first == null) {
            firstSelectedStarId = star.id
            feedback = copy.firstStarSelected
            return
        }
        if (first == star.id) {
            firstSelectedStarId = null
            feedback = null
            return
        }

        val edge = ConstellationEdge.of(first, star.id)
        val edgeIsRequired = edge in requiredEdges
        val alreadyConnected = edge in selectedEdges
        val newSelectedEdges = if (edgeIsRequired && !alreadyConnected) selectedEdges + edge else selectedEdges
        selectedEdges = newSelectedEdges
        firstSelectedStarId = if (edgeIsRequired && !alreadyConnected) star.id else null

        val completedAfterTap = newSelectedEdges.containsAll(requiredEdges)
        feedback = when {
            alreadyConnected -> copy.alreadyConnected
            edgeIsRequired -> if (completedAfterTap) copy.completed else copy.correctConnection
            else -> copy.wrongConnection
        }

        onConnectionAttempt(
            ConstellationConnectionAttempt(
                puzzleId = puzzle.id,
                constellationId = puzzle.templateId,
                fromStarId = first,
                toStarId = star.id,
                correct = edgeIsRequired && !alreadyConnected,
                completed = completedAfterTap,
                selectedConnectionCount = newSelectedEdges.size,
                requiredConnectionCount = requiredEdges.size
            )
        )
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
            ConstellationsInfoPanel(
                puzzle = puzzle,
                copy = copy,
                exerciseLanguage = exerciseLanguage,
                feedback = feedback,
                isComplete = isComplete,
                onMissionClicked = { onSpeakText?.invoke(puzzle.mission.get(exerciseLanguage)) },
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
                highlightedTip = highlightedTip,
                firstSelectedStarId = firstSelectedStarId,
                isComplete = isComplete,
                difficultyStars = difficultyStars,
                copy = copy,
                onStarTapped = ::onStarTapped,
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
            ConstellationsInfoPanel(
                puzzle = puzzle,
                copy = copy,
                exerciseLanguage = exerciseLanguage,
                feedback = feedback,
                isComplete = isComplete,
                onMissionClicked = { onSpeakText?.invoke(puzzle.mission.get(exerciseLanguage)) },
                onNewMission = ::newMission,
                onResetPath = ::resetPath,
                onTip = ::showTip,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(0.38f)
            )
            Spacer(modifier = Modifier.height(14.dp))
            ConstellationCanvasCard(
                puzzle = puzzle,
                selectedEdges = selectedEdges,
                highlightedTip = highlightedTip,
                firstSelectedStarId = firstSelectedStarId,
                isComplete = isComplete,
                difficultyStars = difficultyStars,
                copy = copy,
                onStarTapped = ::onStarTapped,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(0.62f)
            )
        }
    }
}

@Composable
private fun ConstellationsInfoPanel(
    puzzle: ConstellationPuzzle,
    copy: ConstellationsCopy,
    exerciseLanguage: String,
    feedback: String?,
    isComplete: Boolean,
    onMissionClicked: () -> Unit,
    onNewMission: () -> Unit,
    onResetPath: () -> Unit,
    onTip: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.SpaceEvenly,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = copy.title,
            style = MaterialTheme.typography.headlineSmall,
            color = PlanetGold,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .clickable { onMissionClicked() }
                .semantics { contentDescription = puzzle.mission.get(exerciseLanguage) },
            color = PlanetGold,
            contentColor = DeepSpaceBlue,
            shape = RoundedCornerShape(20.dp)
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = puzzle.mission.get(exerciseLanguage),
                    color = DeepSpaceBlue,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = copy.tapMissionToHear,
                    color = DeepSpaceBlue.copy(alpha = 0.80f),
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center
                )
            }
        }

        if (feedback != null) {
            Surface(
                color = if (isComplete) PlanetGold else PlanetBlue.copy(alpha = 0.35f),
                contentColor = if (isComplete) DeepSpaceBlue else StarWhite,
                shape = RoundedCornerShape(999.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = feedback,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
                    color = if (isComplete) DeepSpaceBlue else StarWhite,
                    textAlign = TextAlign.Center,
                    fontWeight = FontWeight.SemiBold
                )
            }
        } else {
            Spacer(modifier = Modifier.height(38.dp))
        }

        if (isComplete) {
            Surface(
                color = CockpitGray.copy(alpha = 0.65f),
                contentColor = StarWhite,
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "${copy.factPrefix}: ${puzzle.fact.get(exerciseLanguage)}",
                        color = PlanetGold,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = onTip,
                colors = ButtonDefaults.buttonColors(
                    containerColor = PlanetBlue,
                    contentColor = StarWhite
                ),
                shape = RoundedCornerShape(999.dp),
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
            ) {
                Text(copy.tip, color = StarWhite, fontWeight = FontWeight.Bold)
            }
            Button(
                onClick = if (isComplete) onNewMission else onResetPath,
                colors = ButtonDefaults.buttonColors(
                    containerColor = PlanetGold,
                    contentColor = DeepSpaceBlue
                ),
                shape = RoundedCornerShape(999.dp),
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
            ) {
                Text(
                    text = if (isComplete) copy.nextMission else copy.resetPath,
                    color = DeepSpaceBlue,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
            }
        }

        OutlinedButton(
            onClick = onNewMission,
            colors = ButtonDefaults.outlinedButtonColors(contentColor = PlanetGold),
            shape = RoundedCornerShape(999.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(46.dp)
        ) {
            Text(copy.newMission, color = PlanetGold, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun ConstellationCanvasCard(
    puzzle: ConstellationPuzzle,
    selectedEdges: Set<ConstellationEdge>,
    highlightedTip: ConstellationEdge?,
    firstSelectedStarId: String?,
    isComplete: Boolean,
    difficultyStars: Int,
    copy: ConstellationsCopy,
    onStarTapped: (ConstellationStar) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = CockpitGray.copy(alpha = 0.42f))
    ) {
        BoxWithConstraints(
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
                .padding(8.dp)
        ) {
            val density = LocalDensity.current
            val widthPx = with(density) { maxWidth.toPx() }
            val heightPx = with(density) { maxHeight.toPx() }
            val starSize = if (maxWidth < 380.dp || maxHeight < 360.dp) 34.dp else 44.dp
            val starSizePx = with(density) { starSize.toPx() }
            val requiredEdges = puzzle.requiredEdges

            Canvas(modifier = Modifier.fillMaxSize()) {
                fun pointFor(starId: String): Offset? {
                    val star = puzzle.stars.firstOrNull { it.id == starId } ?: return null
                    return Offset(star.x * size.width, star.y * size.height)
                }

                selectedEdges.forEach { edge ->
                    val from = pointFor(edge.a)
                    val to = pointFor(edge.b)
                    if (from != null && to != null) {
                        drawLine(
                            color = PlanetGold.copy(alpha = if (isComplete) 0.98f else 0.86f),
                            start = from,
                            end = to,
                            strokeWidth = if (isComplete) 7f else 5f,
                            cap = StrokeCap.Round
                        )
                    }
                }

                highlightedTip?.let { edge ->
                    val from = pointFor(edge.a)
                    val to = pointFor(edge.b)
                    if (from != null && to != null) {
                        drawLine(
                            color = PlanetBlue.copy(alpha = 0.92f),
                            start = from,
                            end = to,
                            strokeWidth = 6f,
                            cap = StrokeCap.Round
                        )
                    }
                }

                if (isComplete) {
                    requiredEdges.forEach { edge ->
                        val from = pointFor(edge.a)
                        val to = pointFor(edge.b)
                        if (from != null && to != null) {
                            drawLine(
                                color = StarWhite.copy(alpha = 0.20f),
                                start = from,
                                end = to,
                                strokeWidth = 12f,
                                cap = StrokeCap.Round
                            )
                        }
                    }
                }
            }

            puzzle.stars.forEach { star ->
                val isSelected = star.id == firstSelectedStarId
                val isConnected = selectedEdges.any { edge -> edge.a == star.id || edge.b == star.id }
                val isTipStar = highlightedTip?.let { it.a == star.id || it.b == star.id } == true
                val shouldShowOrder = difficultyStars == 1 && star.isTarget
                val shouldShowName = isComplete && star.isTarget
                val starColor = when {
                    isTipStar -> PlanetBlue
                    isSelected -> PlanetRed
                    isConnected || isComplete -> PlanetGold
                    star.isTarget -> StarWhite
                    else -> StarWhite.copy(alpha = 0.42f)
                }
                val textColor = when {
                    isTipStar -> StarWhite
                    isSelected -> StarWhite
                    isConnected || isComplete -> DeepSpaceBlue
                    star.isTarget -> DeepSpaceBlue
                    else -> DeepSpaceBlue.copy(alpha = 0.55f)
                }
                val x = (star.x * widthPx - starSizePx / 2f).roundToInt()
                val y = (star.y * heightPx - starSizePx / 2f).roundToInt()

                Box(
                    modifier = Modifier
                        .offset { IntOffset(x, y) }
                        .size(starSize)
                        .clip(CircleShape)
                        .background(starColor.copy(alpha = (0.68f + star.brightness * 0.24f).coerceIn(0.55f, 0.98f)))
                        .clickable { onStarTapped(star) }
                        .semantics {
                            contentDescription = if (star.name.isBlank()) "Star" else star.name
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = when {
                            shouldShowOrder -> star.order?.toString().orEmpty()
                            shouldShowName -> star.name.take(2).uppercase()
                            else -> "✦"
                        },
                        color = textColor,
                        fontSize = if (shouldShowName) 10.sp else 16.sp,
                        fontWeight = FontWeight.Black,
                        textAlign = TextAlign.Center
                    )
                }
            }

            if (firstSelectedStarId != null && !isComplete) {
                val selectedName = puzzle.stars.firstOrNull { it.id == firstSelectedStarId }?.name.orEmpty()
                Surface(
                    color = PlanetBlue.copy(alpha = 0.55f),
                    contentColor = StarWhite,
                    shape = RoundedCornerShape(999.dp),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(10.dp)
                ) {
                    Text(
                        text = if (selectedName.isBlank()) copy.selectedPrefix else "${copy.selectedPrefix}: $selectedName",
                        color = StarWhite,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
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
