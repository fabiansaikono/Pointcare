package be.saikono.poincare.ui.puzzle

import androidx.compose.foundation.layout.PaddingValues
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import be.saikono.poincare.model.SkillLevel
import be.saikono.poincare.ui.theme.DeepSpaceBlue
import be.saikono.poincare.ui.theme.NebulaPurple
import be.saikono.poincare.ui.theme.PlanetBlue
import be.saikono.poincare.ui.theme.PlanetGold
import be.saikono.poincare.ui.theme.PlanetRed
import be.saikono.poincare.ui.theme.StarWhite

// Visual rocket size stays unchanged. These values only shrink the invisible player hit zone.
// Previous hit range was 0.05f..0.23f, width 0.18. New range is 50% smaller, width 0.09.
private const val GALAXYCON_PLAYER_COLLISION_START = 0.095f
private const val GALAXYCON_PLAYER_COLLISION_END = 0.185f

@Composable
fun GalaxyconGame(
    language: String,
    level: SkillLevel,
    initialStars: Int,
    onComplete: (GalaxyconRunResult) -> Unit,
    onLetterProgressChanged: (Float) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val levelSpec = remember(language, level, initialStars) {
        GalaxyconGenerator.createLevel(language, level, initialStars)
    }
    val locale = normalizeGalaxyconLocale(language)
    val context = LocalContext.current

    DisposableEffect(Unit) {
        val activity = context.findGalaxyconActivity()
        val previousOrientation = activity?.requestedOrientation
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        onDispose {
            if (activity != null && previousOrientation != null) {
                activity.requestedOrientation = previousOrientation
            }
        }
    }

    var runKey by remember { mutableStateOf(0) }
    var playerLane by remember(runKey) { mutableStateOf(1) }
    var lives by remember(runKey) { mutableStateOf(GALAXYCON_START_LIVES) }
    var points by remember(runKey) { mutableStateOf(0) }
    var collisions by remember(runKey) { mutableStateOf(0) }
    var starsCollected by remember(runKey) { mutableStateOf(0) }
    var multipliersCollected by remember(runKey) { mutableStateOf(0) }
    var activeMultiplier by remember(runKey) { mutableStateOf(1) }
    var multiplierRemainingMs by remember(runKey) { mutableStateOf(0L) }
    var invulnerableMs by remember(runKey) { mutableStateOf(0L) }
    var shieldActive by remember(runKey) { mutableStateOf(false) }
    var objects by remember(runKey) { mutableStateOf<List<GalaxyconFlyingObject>>(emptyList()) }
    var currentWordIndex by remember(runKey) { mutableStateOf(0) }
    var currentLetterIndex by remember(runKey) { mutableStateOf(0) }
    var correctLetters by remember(runKey) { mutableStateOf(0) }
    val totalLettersForHeaderProgress = levelSpec.totalRequiredLetters.coerceAtLeast(1)

    LaunchedEffect(correctLetters, totalLettersForHeaderProgress, runKey) {
        onLetterProgressChanged(
            (correctLetters.toFloat() / totalLettersForHeaderProgress.toFloat()).coerceIn(0f, 1f)
        )
    }


    var stickersEarned by remember(runKey) { mutableStateOf<List<String>>(emptyList()) }
    var distanceProgress by remember(runKey) { mutableStateOf(0f) }
    var spawnIndex by remember(runKey) { mutableStateOf(0L) }
    var nextHeartSpawnIndex by remember(runKey) { mutableStateOf(GalaxyconGenerator.nextHeartSpawnIndex(levelSpec, 0L)) }
    var lastSpawnMs by remember(runKey) { mutableStateOf(0L) }
    var startFrameMs by remember(runKey) { mutableStateOf<Long?>(null) }
    var lastFrameMs by remember(runKey) { mutableStateOf<Long?>(null) }
    var ended by remember(runKey) { mutableStateOf(false) }
    var result by remember(runKey) { mutableStateOf<GalaxyconRunResult?>(null) }
    var resultSent by remember(runKey) { mutableStateOf(false) }
    var feedbackText by remember(runKey) { mutableStateOf<String?>(null) }
    var feedbackUntilMs by remember(runKey) { mutableStateOf(0L) }

    val currentWord = levelSpec.words.getOrNull(currentWordIndex)
    val nextRequiredLetter = currentWord?.text?.getOrNull(currentLetterIndex)

    LaunchedEffect(levelSpec.id, runKey) {
        while (!ended) {
            val frameMs = withFrameMillis { it }
            val start = startFrameMs ?: frameMs.also { startFrameMs = it }
            val previous = lastFrameMs ?: frameMs
            val deltaMs = (frameMs - previous).coerceIn(0L, 100L)
            lastFrameMs = frameMs
            val elapsedMs = frameMs - start

            distanceProgress = (elapsedMs.toFloat() / levelSpec.durationMs.toFloat()).coerceIn(0f, 1f)

            if (multiplierRemainingMs > 0L) {
                multiplierRemainingMs = (multiplierRemainingMs - deltaMs).coerceAtLeast(0L)
                if (multiplierRemainingMs == 0L) activeMultiplier = 1
            }
            if (invulnerableMs > 0L) {
                invulnerableMs = (invulnerableMs - deltaMs).coerceAtLeast(0L)
            }
            if (feedbackText != null && frameMs >= feedbackUntilMs) {
                feedbackText = null
            }

            val loopCurrentWord = levelSpec.words.getOrNull(currentWordIndex)
            val loopNextRequiredLetter = loopCurrentWord?.text?.getOrNull(currentLetterIndex)
            if (elapsedMs - lastSpawnMs >= levelSpec.spawnEveryMs && currentWordIndex < levelSpec.words.size) {
                spawnIndex += 1
                lastSpawnMs = elapsedMs
                val newObject = if (spawnIndex >= nextHeartSpawnIndex) {
                    val heartObject = GalaxyconGenerator.createHeartObject(
                        level = levelSpec,
                        spawnIndex = spawnIndex
                    )
                    nextHeartSpawnIndex = GalaxyconGenerator.nextHeartSpawnIndex(levelSpec, spawnIndex)
                    heartObject
                } else {
                    GalaxyconGenerator.createObject(
                        level = levelSpec,
                        nextRequiredLetter = loopNextRequiredLetter,
                        locale = locale,
                        spawnIndex = spawnIndex
                    )
                }
                objects = objects + newObject
            }

            val move = levelSpec.speedFractionPerSecond * (deltaMs.toFloat() / 1000f)
            val movedObjects = objects.map { it.copy(x = it.x - move) }
            val hitObjects = movedObjects.filter { it.lane == playerLane && it.x in GALAXYCON_PLAYER_COLLISION_START..GALAXYCON_PLAYER_COLLISION_END }
            val hitIds = mutableSetOf<Long>()

            hitObjects.forEach { obj ->
                hitIds += obj.id
                val scoreMultiplier = if (multiplierRemainingMs > 0L) activeMultiplier.coerceIn(1, 5) else 1

                when (obj.type) {
                    GalaxyconObjectType.CORRECT_LETTER,
                    GalaxyconObjectType.WRONG_LETTER -> {
                        if (loopNextRequiredLetter != null && obj.label.equals(loopNextRequiredLetter.toString(), ignoreCase = true)) {
                            val earned = 10 * scoreMultiplier
                            points += earned
                            correctLetters += 1
                            val updatedLetterIndex = currentLetterIndex + 1
                            currentLetterIndex = updatedLetterIndex
                            feedbackText = "+$earned ${obj.label}"
                            feedbackUntilMs = frameMs + 900L

                            val word = loopCurrentWord
                            if (word != null && updatedLetterIndex >= word.text.length) {
                                val wordBonus = 50 * scoreMultiplier
                                points += wordBonus
                                stickersEarned = (stickersEarned + word.text.uppercase()).distinct()
                                lives = (lives + 1).coerceAtMost(GALAXYCON_MAX_LIVES)
                                currentWordIndex += 1
                                currentLetterIndex = 0
                                feedbackText = stickerText(locale, word.text.uppercase())
                                feedbackUntilMs = frameMs + 1_600L
                            }
                        } else {
                            activeMultiplier = 1
                            multiplierRemainingMs = 0L
                            collisions += 1
                            if (shieldActive) {
                                shieldActive = false
                                feedbackText = shieldBlockedText(locale)
                            } else if (invulnerableMs == 0L) {
                                lives -= 1
                                invulnerableMs = 1_200L
                                feedbackText = wrongLetterText(locale)
                            }
                            feedbackUntilMs = frameMs + 900L
                        }
                    }

                    GalaxyconObjectType.STAR -> {
                        starsCollected += 1
                        val earned = 5 * scoreMultiplier
                        points += earned
                        feedbackText = "+$earned ★"
                        feedbackUntilMs = frameMs + 900L
                    }

                    GalaxyconObjectType.MULTIPLIER -> {
                        activeMultiplier = obj.multiplier.coerceIn(2, 5)
                        multiplierRemainingMs = GALAXYCON_MULTIPLIER_DURATION_MS
                        multipliersCollected += 1
                        feedbackText = "X${obj.multiplier}"
                        feedbackUntilMs = frameMs + 1_200L
                    }

                    GalaxyconObjectType.DEBRIS -> {
                        activeMultiplier = 1
                        multiplierRemainingMs = 0L
                        collisions += 1
                        if (shieldActive) {
                            shieldActive = false
                            feedbackText = shieldBlockedText(locale)
                        } else if (invulnerableMs == 0L) {
                            lives -= 1
                            invulnerableMs = 1_500L
                            feedbackText = impactText(locale)
                        }
                        feedbackUntilMs = frameMs + 900L
                    }

                    GalaxyconObjectType.SHIELD -> {
                        shieldActive = true
                        feedbackText = shieldReadyText(locale)
                        feedbackUntilMs = frameMs + 1_200L
                    }

                    GalaxyconObjectType.HEART -> {
                        val previousLives = lives
                        lives = (lives + 1).coerceAtMost(GALAXYCON_MAX_LIVES)
                        feedbackText = if (lives > previousLives) heartBonusText(locale) else "♥"
                        feedbackUntilMs = frameMs + 1_000L
                    }
                }
            }

            objects = movedObjects.filter { it.x > -0.18f && it.id !in hitIds }
            val planetReached = currentWordIndex >= levelSpec.words.size && distanceProgress >= 1f
            val outOfLives = lives <= 0
            if (planetReached || outOfLives) {
                ended = true
                val finalResult = calculateGalaxyconResult(
                    level = levelSpec,
                    points = points + if (planetReached) 100 else 0,
                    collisions = collisions,
                    wordsCompleted = currentWordIndex.coerceAtMost(levelSpec.words.size),
                    correctLetters = correctLetters.coerceAtMost(levelSpec.totalRequiredLetters),
                    starsCollected = starsCollected,
                    multipliersCollected = multipliersCollected,
                    planetReached = planetReached,
                    stickersEarned = stickersEarned,
                    elapsedMs = elapsedMs
                )
                result = finalResult
            }
        }
    }

    LaunchedEffect(result, resultSent) {
        val finalResult = result ?: return@LaunchedEffect
        if (!resultSent) {
            resultSent = true
            onComplete(finalResult)
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .fillMaxHeight()
    ) {
BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(24.dp))
                .background(DeepSpaceBlue.copy(alpha = 0.55f))
                .pointerInput(ended) {
                    var totalDrag = Offset.Zero
                    detectDragGestures(
                        onDragStart = { totalDrag = Offset.Zero },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            if (!ended) {
                                totalDrag += dragAmount
                            }
                        },
                        onDragEnd = {
                            if (!ended) {
                                when {
                                    totalDrag.x < -32f -> playerLane = (playerLane - 1).coerceAtLeast(0)
                                    totalDrag.x > 32f -> playerLane = (playerLane + 1).coerceAtMost(2)
                                }
                            }
                            totalDrag = Offset.Zero
                        },
                        onDragCancel = { totalDrag = Offset.Zero }
                    )
                }
        ) {
        val laneSize = maxWidth / 3
        val objectSize = if (maxHeight < 520.dp || maxWidth < 420.dp) 42.dp else 52.dp
        val playerSize = if (maxHeight < 520.dp || maxWidth < 420.dp) 66.dp else 78.dp
        val playerX = laneStart(playerLane, laneSize) + laneSize / 2 - playerSize / 2
        val playerY = maxHeight * 0.86f

        LaneBackgrounds(laneSize = laneSize)

        GalaxyconHud(
            levelSpec = levelSpec,
            locale = locale,
            lives = lives,
            points = points,
            activeMultiplier = activeMultiplier,
            multiplierRemainingMs = multiplierRemainingMs,
            shieldActive = shieldActive,
            currentWord = currentWord,
            currentLetterIndex = currentLetterIndex,
            correctLetters = correctLetters,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .padding(10.dp)
        )

        objects.forEach { obj ->
            val verticalProgress = (1f - obj.x).coerceIn(-0.14f, 1.14f)
            GalaxyconObjectView(
                obj = obj,
                size = objectSize,
                nextRequiredLetter = nextRequiredLetter,
                modifier = Modifier.offset(
                    x = laneStart(obj.lane, laneSize) + laneSize / 2 - objectSize / 2,
                    y = maxHeight * verticalProgress - objectSize / 2
                )
            )
        }

        GalaxyconPlayer(
            invulnerable = invulnerableMs > 0L,
            shieldActive = shieldActive,
            modifier = Modifier
                .offset(x = playerX, y = playerY)
                .size(playerSize)
        )

        feedbackText?.let { message ->
            Surface(
                modifier = Modifier.align(Alignment.Center),
                color = PlanetGold.copy(alpha = 0.92f),
                contentColor = DeepSpaceBlue,
                shape = RoundedCornerShape(18.dp)
            ) {
                Text(
                    text = message,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp)
                )
            }
        }

        result?.let { finalResult ->
            GalaxyconResultCard(
                locale = locale,
                result = finalResult,
                onReplay = { runKey += 1 },
                modifier = Modifier.align(Alignment.Center)
            )
        }
    }
}
}

@Composable
private fun GalaxyconHud(
    levelSpec: GalaxyconLevelSpec,
    locale: String,
    lives: Int,
    points: Int,
    activeMultiplier: Int,
    multiplierRemainingMs: Long,
    shieldActive: Boolean,
    currentWord: GalaxyconWord?,
    currentLetterIndex: Int,
    correctLetters: Int,
    modifier: Modifier = Modifier
) {
    val multiplierProgress = (multiplierRemainingMs.toFloat() / GALAXYCON_MULTIPLIER_DURATION_MS.toFloat()).coerceIn(0f, 1f)
    val multiplierSeconds = if (multiplierRemainingMs > 0L) {
        ((multiplierRemainingMs + 999L) / 1000L).coerceIn(0L, 15L)
    } else {
        0L
    }
    val heartString = buildString {
        repeat(lives.coerceIn(0, GALAXYCON_MAX_LIVES)) { append("♥") }
        repeat((GALAXYCON_MAX_LIVES - lives).coerceIn(0, GALAXYCON_MAX_LIVES)) { append("♡") }
    }
    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.08f)),
        shape = RoundedCornerShape(18.dp),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${localizedGalaxyconText("points", locale)}: $points",
                    color = StarWhite,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = heartString,
                    color = PlanetRed,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = levelSpec.planetName,
                        color = StarWhite.copy(alpha = 0.82f),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                    if (shieldActive) {
                        Text(
                            text = localizedGalaxyconText("shield", locale),
                            color = StarWhite.copy(alpha = 0.86f),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Row(
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "X$activeMultiplier",
                        color = PlanetGold,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Black
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "${multiplierSeconds}s",
                        color = PlanetGold,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    LinearProgressIndicator(
                        progress = { multiplierProgress },
                        color = PlanetGold,
                        trackColor = StarWhite.copy(alpha = 0.12f),
                        modifier = Modifier
                            .width(92.dp)
                            .height(5.dp)
                            .clip(RoundedCornerShape(5.dp))
                    )
                }
            }

            val wordText = currentWord?.text?.uppercase().orEmpty()
            Text(
                text = if (wordText.isBlank()) {
                    localizedGalaxyconText("flyToPlanet", locale)
                } else {
                    wordProgress(wordText, currentLetterIndex)
                },
                color = PlanetGold,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )

            if (currentWord != null) {
                Text(
                    text = currentWord.hint(locale),
                    color = StarWhite.copy(alpha = 0.78f),
                    style = MaterialTheme.typography.labelMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }

        }
    }
}

@Composable
private fun LaneBackgrounds(laneSize: Dp) {
    Row(modifier = Modifier.fillMaxSize()) {
        repeat(3) {
            Box(
                modifier = Modifier
                    .width(laneSize)
                    .fillMaxHeight()
                    .background(Color.Transparent)
            )
        }
    }
}

@Composable
private fun GalaxyconPlayer(
    invulnerable: Boolean,
    shieldActive: Boolean,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val center = Offset(w * 0.5f, h * 0.52f)

            if (shieldActive) {
                drawCircle(StarWhite.copy(alpha = 0.10f), radius = w * 0.58f, center = center)
                drawCircle(StarWhite.copy(alpha = 0.22f), radius = w * 0.50f, center = center, style = Stroke(width = w * 0.045f))
                drawCircle(PlanetBlue.copy(alpha = 0.18f), radius = w * 0.42f, center = center, style = Stroke(width = w * 0.025f))
            }
            if (invulnerable) {
                drawCircle(PlanetRed.copy(alpha = 0.18f), radius = w * 0.50f, center = center)
            }

            val flameOuter = Path().apply {
                moveTo(w * 0.40f, h * 0.76f)
                cubicTo(w * 0.48f, h * 1.05f, w * 0.57f, h * 1.05f, w * 0.64f, h * 0.76f)
                close()
            }
            drawPath(flameOuter, PlanetRed.copy(alpha = 0.92f))
            val flameInner = Path().apply {
                moveTo(w * 0.46f, h * 0.76f)
                cubicTo(w * 0.51f, h * 0.96f, w * 0.56f, h * 0.96f, w * 0.59f, h * 0.76f)
                close()
            }
            drawPath(flameInner, PlanetGold.copy(alpha = 0.96f))

            val body = Path().apply {
                moveTo(w * 0.50f, h * 0.02f)
                cubicTo(w * 0.70f, h * 0.18f, w * 0.76f, h * 0.44f, w * 0.66f, h * 0.74f)
                cubicTo(w * 0.59f, h * 0.82f, w * 0.41f, h * 0.82f, w * 0.34f, h * 0.74f)
                cubicTo(w * 0.24f, h * 0.44f, w * 0.30f, h * 0.18f, w * 0.50f, h * 0.02f)
                close()
            }
            drawPath(body, StarWhite.copy(alpha = 0.96f))

            val nose = Path().apply {
                moveTo(w * 0.50f, h * 0.02f)
                cubicTo(w * 0.62f, h * 0.12f, w * 0.68f, h * 0.25f, w * 0.69f, h * 0.34f)
                cubicTo(w * 0.58f, h * 0.29f, w * 0.42f, h * 0.29f, w * 0.31f, h * 0.34f)
                cubicTo(w * 0.32f, h * 0.25f, w * 0.38f, h * 0.12f, w * 0.50f, h * 0.02f)
                close()
            }
            drawPath(nose, PlanetRed.copy(alpha = 0.95f))

            val leftFin = Path().apply {
                moveTo(w * 0.35f, h * 0.56f)
                lineTo(w * 0.12f, h * 0.82f)
                lineTo(w * 0.38f, h * 0.74f)
                close()
            }
            val rightFin = Path().apply {
                moveTo(w * 0.65f, h * 0.56f)
                lineTo(w * 0.88f, h * 0.82f)
                lineTo(w * 0.62f, h * 0.74f)
                close()
            }
            drawPath(leftFin, PlanetGold.copy(alpha = 0.94f))
            drawPath(rightFin, PlanetGold.copy(alpha = 0.94f))

            drawRoundRect(
                color = PlanetBlue.copy(alpha = 0.76f),
                topLeft = Offset(w * 0.40f, h * 0.46f),
                size = Size(w * 0.20f, h * 0.20f),
                cornerRadius = CornerRadius(w * 0.08f, w * 0.08f)
            )
            drawCircle(StarWhite.copy(alpha = 0.88f), radius = w * 0.035f, center = Offset(w * 0.46f, h * 0.52f))
            drawLine(
                color = NebulaPurple.copy(alpha = 0.55f),
                start = Offset(w * 0.37f, h * 0.39f),
                end = Offset(w * 0.63f, h * 0.39f),
                strokeWidth = w * 0.035f,
                cap = StrokeCap.Round
            )
            drawLine(
                color = NebulaPurple.copy(alpha = 0.40f),
                start = Offset(w * 0.39f, h * 0.69f),
                end = Offset(w * 0.61f, h * 0.69f),
                strokeWidth = w * 0.03f,
                cap = StrokeCap.Round
            )
        }
    }
}

@Composable
private fun GalaxyconObjectView(
    obj: GalaxyconFlyingObject,
    size: Dp,
    nextRequiredLetter: Char?,
    modifier: Modifier = Modifier
) {
    when (obj.type) {
        GalaxyconObjectType.CORRECT_LETTER,
        GalaxyconObjectType.WRONG_LETTER -> {
            Box(
                modifier = modifier.size(size),
                contentAlignment = Alignment.Center
            ) {
                val isCurrentCorrectLetter = nextRequiredLetter != null &&
                    obj.label.equals(nextRequiredLetter.toString(), ignoreCase = true)
                Text(
                    text = obj.label,
                    color = if (isCurrentCorrectLetter) PlanetGold else StarWhite.copy(alpha = 0.82f),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Black,
                    textAlign = TextAlign.Center
                )
            }
        }

        GalaxyconObjectType.DEBRIS -> {
            Box(
                modifier = modifier.size(size),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val w = this.size.width
                    val h = this.size.height
                    val asteroid = Path().apply {
                        moveTo(w * 0.50f, h * 0.04f)
                        lineTo(w * 0.78f, h * 0.14f)
                        lineTo(w * 0.96f, h * 0.44f)
                        lineTo(w * 0.82f, h * 0.78f)
                        lineTo(w * 0.54f, h * 0.96f)
                        lineTo(w * 0.20f, h * 0.84f)
                        lineTo(w * 0.05f, h * 0.54f)
                        lineTo(w * 0.16f, h * 0.20f)
                        close()
                    }
                    drawPath(asteroid, Color(0xFF8A7B6A).copy(alpha = 0.94f))
                    drawCircle(DeepSpaceBlue.copy(alpha = 0.24f), radius = w * 0.09f, center = Offset(w * 0.34f, h * 0.36f))
                    drawCircle(DeepSpaceBlue.copy(alpha = 0.20f), radius = w * 0.06f, center = Offset(w * 0.62f, h * 0.62f))
                    drawCircle(StarWhite.copy(alpha = 0.16f), radius = w * 0.05f, center = Offset(w * 0.62f, h * 0.24f))
                }
            }
        }

        GalaxyconObjectType.STAR -> {
            Box(
                modifier = modifier
                    .size(size)
                    .clip(CircleShape)
                    .background(PlanetGold.copy(alpha = 0.92f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = obj.label,
                    color = DeepSpaceBlue,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
            }
        }

        GalaxyconObjectType.MULTIPLIER -> {
            Box(
                modifier = modifier
                    .size(size)
                    .clip(CircleShape)
                    .background(NebulaPurple.copy(alpha = 0.50f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = obj.label,
                    color = PlanetGold,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Black,
                    textAlign = TextAlign.Center
                )
            }
        }

        GalaxyconObjectType.SHIELD -> {
            Box(
                modifier = modifier.size(size),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val w = this.size.width
                    val h = this.size.height
                    drawCircle(StarWhite.copy(alpha = 0.16f), radius = w * 0.46f, center = Offset(w * 0.50f, h * 0.50f))
                    drawCircle(PlanetBlue.copy(alpha = 0.20f), radius = w * 0.40f, center = Offset(w * 0.50f, h * 0.50f), style = Stroke(width = w * 0.04f))
                    val shield = Path().apply {
                        moveTo(w * 0.50f, h * 0.08f)
                        cubicTo(w * 0.70f, h * 0.16f, w * 0.82f, h * 0.22f, w * 0.84f, h * 0.24f)
                        cubicTo(w * 0.82f, h * 0.58f, w * 0.69f, h * 0.82f, w * 0.50f, h * 0.94f)
                        cubicTo(w * 0.31f, h * 0.82f, w * 0.18f, h * 0.58f, w * 0.16f, h * 0.24f)
                        cubicTo(w * 0.18f, h * 0.22f, w * 0.30f, h * 0.16f, w * 0.50f, h * 0.08f)
                        close()
                    }
                    drawPath(shield, StarWhite.copy(alpha = 0.82f))
                    drawLine(
                        color = PlanetBlue.copy(alpha = 0.70f),
                        start = Offset(w * 0.50f, h * 0.22f),
                        end = Offset(w * 0.50f, h * 0.74f),
                        strokeWidth = w * 0.04f,
                        cap = StrokeCap.Round
                    )
                    drawLine(
                        color = PlanetGold.copy(alpha = 0.62f),
                        start = Offset(w * 0.34f, h * 0.42f),
                        end = Offset(w * 0.66f, h * 0.42f),
                        strokeWidth = w * 0.035f,
                        cap = StrokeCap.Round
                    )
                }
            }
        }

        GalaxyconObjectType.HEART -> {
            Box(
                modifier = modifier
                    .size(size)
                    .clip(CircleShape)
                    .background(PlanetRed.copy(alpha = 0.45f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "♥",
                    color = PlanetGold,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Black,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun GalaxyconResultCard(
    locale: String,
    result: GalaxyconRunResult,
    onReplay: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = PlanetGold.copy(alpha = 0.96f)),
        shape = RoundedCornerShape(24.dp),
        modifier = modifier
            .fillMaxWidth(0.88f)
            .padding(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = if (result.planetReached) localizedGalaxyconText("planetReached", locale) else localizedGalaxyconText("tryAgain", locale),
                color = DeepSpaceBlue,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Text(
                text = result.displayScore,
                color = DeepSpaceBlue,
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "${localizedGalaxyconText("points", locale)}: ${result.points}",
                color = DeepSpaceBlue,
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = "${localizedGalaxyconText("words", locale)}: ${result.wordsCompleted}/${result.totalWords} · ${localizedGalaxyconText("collisions", locale)}: ${result.collisions}",
                color = DeepSpaceBlue,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center
            )
            if (result.stickersEarned.isNotEmpty()) {
                Text(
                    text = localizedGalaxyconText("stickers", locale),
                    color = DeepSpaceBlue,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = result.stickersEarned.joinToString(" · "),
                    color = DeepSpaceBlue,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
            }
            Button(
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                onClick = onReplay,
                colors = ButtonDefaults.buttonColors(containerColor = DeepSpaceBlue, contentColor = PlanetGold)
            ) {
                Text(localizedGalaxyconText("replay", locale), fontWeight = FontWeight.Bold)
            }
        }
    }
}

private tailrec fun Context.findGalaxyconActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findGalaxyconActivity()
    else -> null
}

private fun laneStart(lane: Int, laneSize: Dp): Dp = laneSize * lane.toFloat()

private fun wordProgress(word: String, currentLetterIndex: Int): String = word
    .mapIndexed { index, char -> if (index < currentLetterIndex) char.toString() else "_" }
    .joinToString(" ")

private fun localizedGalaxyconText(key: String, locale: String): String = when (normalizeGalaxyconLocale(locale)) {
    "en-US" -> when (key) {
        "points" -> "Points"
        "flyToPlanet" -> "Fly to the planet!"
        "planetReached" -> "Planet reached!"
        "tryAgain" -> "Almost there!"
        "words" -> "Words"
        "collisions" -> "Hits"
        "stickers" -> "Stickers earned"
        "replay" -> "Replay"
        "shield" -> "Shield"
        else -> key
    }

    "fr-BE" -> when (key) {
        "points" -> "Points"
        "flyToPlanet" -> "Va vers la planete !"
        "planetReached" -> "Planete atteinte !"
        "tryAgain" -> "Presque !"
        "words" -> "Mots"
        "collisions" -> "Chocs"
        "stickers" -> "Stickers gagnes"
        "replay" -> "Rejouer"
        "shield" -> "Bouclier"
        else -> key
    }

    else -> when (key) {
        "points" -> "Punten"
        "flyToPlanet" -> "Vlieg naar de planeet!"
        "planetReached" -> "Planeet bereikt!"
        "tryAgain" -> "Bijna gelukt!"
        "words" -> "Woorden"
        "collisions" -> "Botsingen"
        "stickers" -> "Stickers verdiend"
        "replay" -> "Opnieuw"
        "shield" -> "Schild"
        else -> key
    }
}

private fun stickerText(locale: String, sticker: String): String = when (normalizeGalaxyconLocale(locale)) {
    "en-US" -> "Sticker: $sticker"
    "fr-BE" -> "Sticker : $sticker"
    else -> "Sticker: $sticker"
}

private fun wrongLetterText(locale: String): String = when (normalizeGalaxyconLocale(locale)) {
    "en-US" -> "Wrong letter!"
    "fr-BE" -> "Mauvaise lettre !"
    else -> "Verkeerde letter!"
}

private fun impactText(locale: String): String = when (normalizeGalaxyconLocale(locale)) {
    "en-US" -> "Watch out!"
    "fr-BE" -> "Attention !"
    else -> "Opletten!"
}

private fun shieldReadyText(locale: String): String = when (normalizeGalaxyconLocale(locale)) {
    "en-US" -> "Shield ready!"
    "fr-BE" -> "Bouclier pret !"
    else -> "Schild actief!"
}

private fun shieldBlockedText(locale: String): String = when (normalizeGalaxyconLocale(locale)) {
    "en-US" -> "Shield blocked it!"
    "fr-BE" -> "Bouclier utilise !"
    else -> "Schild gebruikt!"
}

private fun heartBonusText(locale: String): String = when (normalizeGalaxyconLocale(locale)) {
    "en-US" -> "+1 heart!"
    "fr-BE" -> "+1 coeur !"
    else -> "+1 hartje!"
}
