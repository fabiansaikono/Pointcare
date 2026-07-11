package be.saikono.poincare.ui.puzzle

import androidx.compose.foundation.layout.PaddingValues
import android.content.res.Configuration
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.width
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
    var selectedEdges by remember(puzzle.id) { mutableStateOf<Set<ConstellationEdge>>(emptySet()) }
    var selectedStarId by remember(puzzle.id) { mutableStateOf<String?>(null) }
    var highlightedTip by remember(puzzle.id) { mutableStateOf<ConstellationEdge?>(null) }
    var feedback by remember(puzzle.id) { mutableStateOf<String?>(copy.chooseFirst) }
    var completionReported by remember(puzzle.id) { mutableStateOf(false) }

    val requiredEdges = puzzle.requiredEdges
    val isComplete = requiredEdges.isNotEmpty() && selectedEdges.containsAll(requiredEdges)
    val showGroupSymbols = puzzle.targetStars.mapNotNull { it.groupId }.distinct().size > 1

    fun onePathChooseSecondText(): String = when (exerciseLanguage.lowercase()) {
        "fr-be", "fr", "fr-fr" -> "Choisis l'étoile suivante, du début vers la fin. Le chemin peut tourner."
        "en-us", "en-gb", "en" -> "Choose the next star, from start to finish. The path may turn."
        else -> "Kies de volgende ster, van start naar einde. Het pad mag draaien."
    }

    fun onePathCorrectConnectionText(): String = when (exerciseLanguage.lowercase()) {
        "fr-be", "fr", "fr-fr" -> "Bien joué. Continue vers la fin de la suite."
        "en-us", "en-gb", "en" -> "Good connection. Continue toward the end of the sequence."
        else -> "Goed verbonden. Ga verder naar het einde van de reeks."
    }

    fun onePathWrongConnectionText(): String = when (exerciseLanguage.lowercase()) {
        "fr-be", "fr", "fr-fr" -> "Pas encore. Commence au début et avance étape par étape vers la fin."
        "en-us", "en-gb", "en" -> "Not yet. Start at the beginning and move step by step toward the end."
        else -> "Nog niet. Begin bij de start en verbind stap voor stap naar het einde."
    }

    fun onePathTipConnectText(): String = when (exerciseLanguage.lowercase()) {
        "fr-be", "fr", "fr-fr" -> "Indice : relie ces deux prochaines valeurs, même si le chemin tourne."
        "en-us", "en-gb", "en" -> "Hint: connect these next two values, even if the path turns."
        else -> "Tip: verbind deze twee volgende waarden, ook als het pad draait."
    }

    fun startAtBeginningText(): String = when (exerciseLanguage.lowercase()) {
        "fr-be", "fr", "fr-fr" -> "Commence par la première valeur de cette suite. L'ordre des suites A, B et C n'a pas d'importance."
        "en-us", "en-gb", "en" -> "Start with the first value of this sequence. The order of A, B and C does not matter."
        else -> "Start met de eerste waarde van deze reeks. De volgorde van A, B en C maakt niet uit."
    }

    LaunchedEffect(isComplete, puzzle.id) {
        if (isComplete && !completionReported) {
            completionReported = true
            selectedStarId = null
            highlightedTip = null
            feedback = copy.completed
            onComplete()
        }
    }

    fun newMission() {
        missionSeed = System.nanoTime()
    }

    fun resetPath() {
        selectedEdges = emptySet()
        selectedStarId = null
        highlightedTip = null
        feedback = copy.chooseFirst
        completionReported = false
    }

    fun edgeLabel(edge: ConstellationEdge): String {
        val from = puzzle.starsById[edge.a]
        val to = puzzle.starsById[edge.b]
        val first = listOfNotNull(if (showGroupSymbols) from?.groupSymbol else null, from?.label).joinToString(" ").trim()
        val second = listOfNotNull(if (showGroupSymbols) to?.groupSymbol else null, to?.label).joinToString(" ").trim()
        val rule = from?.groupRule?.get(exerciseLanguage) ?: to?.groupRule?.get(exerciseLanguage)
        return listOfNotNull("$first → $second", rule?.let { "($it)" }).joinToString(" ")
    }

    fun nextConnectionForGroup(groupId: String?): ConstellationConnection? {
        return puzzle.connections
            .filter { connection ->
                puzzle.starsById[connection.fromStarId]?.groupId == groupId &&
                    puzzle.starsById[connection.toStarId]?.groupId == groupId
            }
            .sortedBy { connection -> puzzle.starsById[connection.fromStarId]?.order ?: Int.MAX_VALUE }
            .firstOrNull { connection -> connection.edge !in selectedEdges }
    }

    fun firstMissingConnection(): ConstellationConnection? {
        val groupIds = puzzle.targetStars
            .sortedWith(compareBy<ConstellationStar> { it.groupId.orEmpty() }.thenBy { it.order ?: Int.MAX_VALUE })
            .map { it.groupId }
            .distinct()
        return groupIds.firstNotNullOfOrNull { groupId -> nextConnectionForGroup(groupId) }
    }

    fun isExpectedStartStar(starId: String): Boolean {
        val star = puzzle.starsById[starId] ?: return false
        return nextConnectionForGroup(star.groupId)?.fromStarId == starId
    }

    fun isNextConnectionInOrder(fromStarId: String, toStarId: String): Boolean {
        val from = puzzle.starsById[fromStarId] ?: return false
        val to = puzzle.starsById[toStarId] ?: return false
        if (from.groupId != to.groupId) return false
        val next = nextConnectionForGroup(from.groupId) ?: return false
        return next.fromStarId == fromStarId && next.toStarId == toStarId
    }

    fun connectionLabel(connection: ConstellationConnection): String {
        return edgeLabel(ConstellationEdge(connection.fromStarId, connection.toStarId))
    }

    fun showTip() {
        val missing = firstMissingConnection()
        highlightedTip = missing?.edge
        selectedStarId = missing?.fromStarId
        feedback = if (missing == null) copy.noTipNeeded else "${if (showGroupSymbols) copy.tipConnect else onePathTipConnectText()} ${connectionLabel(missing)}"
    }

    fun submitConnection(fromStarId: String, toStarId: String) {
        if (isComplete || fromStarId == toStarId) return
        highlightedTip = null
        selectedStarId = null

        val edge = ConstellationEdge.of(fromStarId, toStarId)
        val alreadyConnected = edge in selectedEdges
        val edgeIsNextInOrder = isNextConnectionInOrder(fromStarId, toStarId)
        val newSelectedEdges = if (edgeIsNextInOrder && !alreadyConnected) selectedEdges + edge else selectedEdges
        selectedEdges = newSelectedEdges

        val completedAfterConnection = newSelectedEdges.containsAll(requiredEdges)
        feedback = when {
            alreadyConnected -> copy.alreadyConnected
            edgeIsNextInOrder -> if (completedAfterConnection) copy.completed else if (showGroupSymbols) copy.correctConnection else onePathCorrectConnectionText()
            else -> if (showGroupSymbols) startAtBeginningText() else onePathWrongConnectionText()
        }

        onConnectionAttempt(
            ConstellationConnectionAttempt(
                puzzleId = puzzle.id,
                constellationId = puzzle.templateId,
                fromStarId = fromStarId,
                toStarId = toStarId,
                correct = edgeIsNextInOrder && !alreadyConnected,
                completed = completedAfterConnection,
                selectedConnectionCount = newSelectedEdges.size,
                requiredConnectionCount = requiredEdges.size
            )
        )
    }

    fun onStarTapped(star: ConstellationStar) {
        if (isComplete) return
        if (!star.isTarget) {
            feedback = if (showGroupSymbols) startAtBeginningText() else onePathWrongConnectionText()
            selectedStarId = null
            highlightedTip = null
            return
        }

        val current = selectedStarId
        highlightedTip = null
        when {
            current == null -> {
                if (isExpectedStartStar(star.id)) {
                    selectedStarId = star.id
                    feedback = if (showGroupSymbols) copy.chooseSecond else onePathChooseSecondText()
                } else {
                    selectedStarId = null
                    feedback = if (showGroupSymbols) startAtBeginningText() else onePathWrongConnectionText()
                }
            }
            current == star.id -> {
                selectedStarId = null
                feedback = copy.chooseFirst
            }
            isExpectedStartStar(star.id) && !isNextConnectionInOrder(current, star.id) -> {
                selectedStarId = star.id
                feedback = if (showGroupSymbols) copy.chooseSecond else onePathChooseSecondText()
            }
            else -> submitConnection(current, star.id)
        }
    }

    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

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
                selectedEdges = selectedEdges,
                feedback = feedback,
                isComplete = isComplete,
                onMissionClicked = { onSpeakText?.invoke(puzzle.mission.get(exerciseLanguage)) },
                onNewMission = ::newMission,
                onResetPath = ::resetPath,
                onTip = ::showTip,
                modifier = Modifier
                    .weight(0.42f)
                    .fillMaxHeight()
            )
            ConstellationCanvasCard(
                puzzle = puzzle,
                selectedEdges = selectedEdges,
                selectedStarId = selectedStarId,
                highlightedTip = highlightedTip,
                isComplete = isComplete,
                onStarTapped = ::onStarTapped,
                onConnectionDrawn = ::submitConnection,
                modifier = Modifier
                    .weight(0.58f)
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
                selectedEdges = selectedEdges,
                feedback = feedback,
                isComplete = isComplete,
                onMissionClicked = { onSpeakText?.invoke(puzzle.mission.get(exerciseLanguage)) },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(10.dp))
            ConstellationCanvasCard(
                puzzle = puzzle,
                selectedEdges = selectedEdges,
                selectedStarId = selectedStarId,
                highlightedTip = highlightedTip,
                isComplete = isComplete,
                onStarTapped = ::onStarTapped,
                onConnectionDrawn = ::submitConnection,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            )
            Spacer(modifier = Modifier.height(10.dp))
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
    selectedEdges: Set<ConstellationEdge>,
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
        verticalArrangement = Arrangement.SpaceBetween,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        ConstellationsHeader(
            puzzle = puzzle,
            copy = copy,
            exerciseLanguage = exerciseLanguage,
            selectedEdges = selectedEdges,
            feedback = feedback,
            isComplete = isComplete,
            onMissionClicked = onMissionClicked,
            modifier = Modifier.fillMaxWidth()
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
private fun ConstellationsHeader(
    puzzle: ConstellationPuzzle,
    copy: ConstellationsCopy,
    exerciseLanguage: String,
    selectedEdges: Set<ConstellationEdge>,
    feedback: String?,
    isComplete: Boolean,
    onMissionClicked: () -> Unit,
    modifier: Modifier = Modifier
) {
    val total = puzzle.requiredEdges.size
    val done = selectedEdges.size.coerceAtMost(total)

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = copy.title,
            style = MaterialTheme.typography.headlineSmall,
            color = PlanetGold,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Text(
            text = puzzle.constellationName.get(exerciseLanguage),
            color = StarWhite.copy(alpha = 0.82f),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center
        )
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
        Surface(
            color = CockpitGray.copy(alpha = 0.58f),
            contentColor = StarWhite,
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp),
                horizontalAlignment = Alignment.Start
            ) {
                Text(
                    text = "${copy.progress}: $done / $total",
                    color = PlanetGold,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold
                )
                puzzle.rules.forEach { rule ->
                    Text(
                        text = rule.get(exerciseLanguage),
                        color = StarWhite,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
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
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Button(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
            onClick = onTip,
            enabled = !isComplete,
            colors = ButtonDefaults.buttonColors(
                containerColor = PlanetBlue,
                contentColor = StarWhite,
                disabledContainerColor = PlanetBlue.copy(alpha = 0.28f),
                disabledContentColor = StarWhite.copy(alpha = 0.55f)
            ),
            shape = RoundedCornerShape(999.dp),
            modifier = Modifier
                .weight(1f)
                .height(48.dp)
        ) {
            Text(copy.tip, color = StarWhite, fontWeight = FontWeight.Bold)
        }
        Button(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
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
}

@Composable
private fun ConstellationCanvasCard(
    puzzle: ConstellationPuzzle,
    selectedEdges: Set<ConstellationEdge>,
    selectedStarId: String?,
    highlightedTip: ConstellationEdge?,
    isComplete: Boolean,
    onStarTapped: (ConstellationStar) -> Unit,
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
                val hasLongLabels = puzzle.stars.any { it.isTarget && (it.label.orEmpty().length > 3) }
                val starSize = when {
                    maxWidth < 380.dp || maxHeight < 360.dp -> if (hasLongLabels) 50.dp else 44.dp
                    hasLongLabels -> 62.dp
                    else -> 54.dp
                }
                val starSizePx = with(density) { starSize.toPx() }
                val touchRadius = starSizePx * 1.18f
                val requiredEdges = puzzle.requiredEdges
                val starsById = puzzle.starsById

                fun nextConnectionForGroup(groupId: String?): ConstellationConnection? {
                    return puzzle.connections
                        .filter { connection ->
                            starsById[connection.fromStarId]?.groupId == groupId &&
                                starsById[connection.toStarId]?.groupId == groupId
                        }
                        .sortedBy { connection -> starsById[connection.fromStarId]?.order ?: Int.MAX_VALUE }
                        .firstOrNull { connection -> connection.edge !in selectedEdges }
                }

                fun starIsExpectedStart(starId: String): Boolean {
                    val star = starsById[starId] ?: return false
                    return nextConnectionForGroup(star.groupId)?.fromStarId == starId
                }

                val candidateStarIds = selectedStarId
                    ?.let { id ->
                        val star = starsById[id]
                        val next = star?.let { nextConnectionForGroup(it.groupId) }
                        if (next?.fromStarId == id) setOf(next.toStarId) else emptySet<String>()
                    }
                    .orEmpty()
                val tipStarIds = highlightedTip
                    ?.let { setOf(it.a, it.b) }
                    .orEmpty()
                val showGroupSymbols = puzzle.targetStars.mapNotNull { it.groupId }.distinct().size > 1

                fun starIsPartOfSelectedPath(starId: String): Boolean = selectedEdges.any { edge ->
                    edge.a == starId || edge.b == starId
                }

                fun pointFor(starId: String): Offset? {
                    val star = starsById[starId] ?: return null
                    return Offset(star.x * widthPx, star.y * heightPx)
                }

                fun nearestStar(position: Offset): ConstellationStar? = puzzle.stars
                    .filter { it.isTarget }
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
                                        val startStar = nearestStar(startOffset)?.takeIf { starIsExpectedStart(it.id) }
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
                        val label = star.label.orEmpty()
                        val isSelected = star.id == selectedStarId || star.id == activeDragStarId
                        val isCandidate = star.id in candidateStarIds
                        val isConnected = starIsPartOfSelectedPath(star.id)
                        val shouldContinueFromStar = !isComplete && starIsExpectedStart(star.id)
                        val isTipStar = star.id in tipStarIds
                        val isStartStar = star.isTarget && star.order == 1
                        val startNeedsAttention = isStartStar && shouldContinueFromStar && !isConnected
                        val starColor = when {
                            !star.isTarget -> StarWhite.copy(alpha = 0.42f)
                            isSelected -> PlanetGold
                            isCandidate || isTipStar || shouldContinueFromStar -> PlanetBlue
                            isComplete -> PlanetGold
                            isConnected -> StarWhite
                            else -> StarWhite
                        }
                        val textColor = when {
                            !star.isTarget -> DeepSpaceBlue.copy(alpha = 0.50f)
                            isSelected -> DeepSpaceBlue
                            isCandidate || isTipStar || shouldContinueFromStar -> StarWhite
                            isConnected || isComplete -> DeepSpaceBlue
                            else -> DeepSpaceBlue
                        }
                        val starAlpha = when {
                            isComplete && star.isTarget -> (0.70f + completePulse * 0.30f).coerceIn(0.70f, 1f)
                            !star.isTarget -> (0.30f + star.brightness * 0.30f).coerceIn(0.25f, 0.62f)
                            else -> (0.70f + star.brightness * 0.24f).coerceIn(0.62f, 0.98f)
                        }
                        val starScale = when {
                            isComplete && star.isTarget -> completeScale
                            isSelected || isCandidate || isTipStar -> 1.08f
                            else -> 1f
                        }
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
                                .border(
                                    width = if (startNeedsAttention) 2.dp else 0.dp,
                                    color = if (startNeedsAttention) PlanetGold.copy(alpha = 0.92f) else StarWhite.copy(alpha = 0f),
                                    shape = CircleShape
                                )
                                .clickable(enabled = star.isTarget && !isComplete) { onStarTapped(star) }
                                .semantics {
                                    contentDescription = if (star.isTarget) {
                                        listOfNotNull(if (showGroupSymbols) star.groupSymbol else null, star.label).joinToString(" ")
                                    } else {
                                        "Background star"
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            if (star.isTarget) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center,
                                    modifier = Modifier.padding(horizontal = 3.dp)
                                ) {
                                    if (showGroupSymbols) {
                                        Text(
                                            text = star.groupSymbol.orEmpty(),
                                            color = textColor.copy(alpha = 0.78f),
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Black,
                                            lineHeight = 9.sp,
                                            textAlign = TextAlign.Center,
                                            maxLines = 1
                                        )
                                    }
                                    Text(
                                        text = label,
                                        color = textColor,
                                        fontSize = when {
                                            label.length >= 6 -> 8.sp
                                            label.length >= 5 -> 9.sp
                                            label.length >= 4 -> 10.sp
                                            label.length >= 3 -> 12.sp
                                            else -> 15.sp
                                        },
                                        fontWeight = FontWeight.Black,
                                        lineHeight = 14.sp,
                                        textAlign = TextAlign.Center,
                                        maxLines = 1
                                    )
                                }
                            } else {
                                Text(
                                    text = "✦",
                                    color = textColor,
                                    fontSize = 16.sp,
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
            grade = 4,
            difficultyStars = 3,
            exerciseLanguage = "nl-BE",
            onComplete = {},
            modifier = Modifier.fillMaxSize()
        )
    }
}
