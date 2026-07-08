package be.saikono.poincare.ui.puzzle

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
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
import kotlin.math.roundToInt

@Composable
fun GalaxyconGame(
    language: String,
    level: SkillLevel,
    initialStars: Int,
    onComplete: (GalaxyconRunResult) -> Unit,
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
    var objects by remember(runKey) { mutableStateOf(emptyList<GalaxyconFlyingObject>()) }
    var currentWordIndex by remember(runKey) { mutableStateOf(0) }
    var currentLetterIndex by remember(runKey) { mutableStateOf(0) }
    var correctLetters by remember(runKey) { mutableStateOf(0) }
    var stickersEarned by remember(runKey) { mutableStateOf(emptyList<String>()) }
    var distanceProgress by remember(runKey) { mutableStateOf(0f) }
    var spawnIndex by remember(runKey) { mutableStateOf(0L) }
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
    val controlsUseLandscape = false

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
                objects = objects + GalaxyconGenerator.createObject(
                    level = levelSpec,
                    nextRequiredLetter = loopNextRequiredLetter,
                    locale = locale,
                    spawnIndex = spawnIndex
                )
            }

            val move = levelSpec.speedFractionPerSecond * (deltaMs.toFloat() / 1000f)
            val movedObjects = objects.map { it.copy(x = it.x - move) }
            val hitObjects = movedObjects.filter { it.lane == playerLane && it.x in 0.08f..0.22f }
            val hitIds = mutableSetOf<Long>()

            hitObjects.forEach { obj ->
                hitIds += obj.id
                val scoreMultiplier = if (multiplierRemainingMs > 0L) activeMultiplier.coerceIn(1, 5) else 1
                when (obj.type) {
                    GalaxyconObjectType.CORRECT_LETTER,
                    GalaxyconObjectType.WRONG_LETTER -> {
                        if (loopNextRequiredLetter != null && obj.label.equals(loopNextRequiredLetter.toString(), ignoreCase = true)) {
                            points += 10 * scoreMultiplier
                            correctLetters += 1
                            currentLetterIndex += 1
                            feedbackText = "+${10 * scoreMultiplier} ${obj.label}"
                            feedbackUntilMs = frameMs + 900L

                            val word = loopCurrentWord
                            if (word != null && currentLetterIndex >= word.text.length) {
                                points += 50 * scoreMultiplier
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
                            feedbackText = wrongLetterText(locale)
                            feedbackUntilMs = frameMs + 900L
                        }
                    }

                    GalaxyconObjectType.STAR -> {
                        starsCollected += 1
                        points += 5 * scoreMultiplier
                        feedbackText = "+${5 * scoreMultiplier} ★"
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
                        if (invulnerableMs == 0L) {
                            lives -= 1
                            collisions += 1
                            invulnerableMs = 1_500L
                            feedbackText = impactText(locale)
                            feedbackUntilMs = frameMs + 900L
                        }
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

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .fillMaxHeight()
            .clip(RoundedCornerShape(24.dp))
            .background(DeepSpaceBlue.copy(alpha = 0.55f))
            .pointerInput(ended, controlsUseLandscape) {
                val isLandscapeGesture = controlsUseLandscape
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
                            val laneSwipe = if (isLandscapeGesture) totalDrag.y else totalDrag.x
                            when {
                                laneSwipe < -32f -> playerLane = (playerLane - 1).coerceAtLeast(0)
                                laneSwipe > 32f -> playerLane = (playerLane + 1).coerceAtMost(2)
                            }
                        }
                        totalDrag = Offset.Zero
                    },
                    onDragCancel = { totalDrag = Offset.Zero }
                )
            }
    ) {
        val isLandscape = false
        val laneSize = if (isLandscape) maxHeight / 3 else maxWidth / 3
        val objectSize = if (maxHeight < 360.dp || maxWidth < 420.dp) 42.dp else 52.dp
        val playerSize = if (maxHeight < 360.dp || maxWidth < 420.dp) 56.dp else 68.dp
        val playerX = if (isLandscape) maxWidth * 0.12f else laneStart(playerLane, laneSize) + laneSize / 2 - playerSize / 2
        val playerY = if (isLandscape) laneStart(playerLane, laneSize) + laneSize / 2 - playerSize / 2 else maxHeight * 0.78f
        GalaxyconHud(
            levelSpec = levelSpec,
            locale = locale,
            lives = lives,
            points = points,
            activeMultiplier = activeMultiplier,
            multiplierRemainingMs = multiplierRemainingMs,
            currentWord = currentWord,
            currentLetterIndex = currentLetterIndex,
            distanceProgress = distanceProgress,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .padding(10.dp)
        )

        LaneBackgrounds(laneSize = laneSize, isLandscape = isLandscape)

        GalaxyconPlayer(
            invulnerable = invulnerableMs > 0L,
            modifier = Modifier
                .offset(x = playerX, y = playerY)
                .size(playerSize)
        )

        objects.forEach { obj ->
            GalaxyconObjectView(
                obj = obj,
                size = objectSize,
                nextRequiredLetter = nextRequiredLetter,
                modifier = Modifier.offset(
                    x = if (isLandscape) maxWidth * obj.x else laneStart(obj.lane, laneSize) + laneSize / 2 - objectSize / 2,
                    y = if (isLandscape) laneStart(obj.lane, laneSize) + laneSize / 2 - objectSize / 2 else maxHeight * (1f - obj.x) - objectSize / 2
                )
            )
        }

        AnimatedVisibility(
            visible = feedbackText != null,
            modifier = Modifier.align(Alignment.Center)
        ) {
            Surface(
                color = PlanetGold.copy(alpha = 0.92f),
                contentColor = DeepSpaceBlue,
                shape = RoundedCornerShape(18.dp)
            ) {
                Text(
                    text = feedbackText.orEmpty(),
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
                onReplay = {
                    runKey += 1
                },
                modifier = Modifier.align(Alignment.Center)
            )
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
    currentWord: GalaxyconWord?,
    currentLetterIndex: Int,
    distanceProgress: Float,
    modifier: Modifier = Modifier
) {
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
                    text = levelSpec.planetName,
                    color = StarWhite,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "♥".repeat(lives.coerceAtLeast(0)),
                    color = PlanetRed,
                    style = MaterialTheme.typography.titleMedium
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${localizedGalaxyconText("points", locale)}: $points",
                    color = StarWhite,
                    style = MaterialTheme.typography.labelLarge
                )
                val multiplierSeconds = ((multiplierRemainingMs / 1000f).roundToInt()).coerceIn(0, 15)
                Text(
                    text = if (activeMultiplier > 1) {
                        "X$activeMultiplier · ${multiplierSeconds}s"
                    } else {
                        localizedGalaxyconText("multiplierReady", locale)
                    },
                    color = if (activeMultiplier > 1) PlanetGold else StarWhite.copy(alpha = 0.65f),
                    style = if (activeMultiplier > 1) MaterialTheme.typography.labelSmall else MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )
            }
            if (activeMultiplier > 1) {
                LinearProgressIndicator(
                    progress = { (multiplierRemainingMs.toFloat() / GALAXYCON_MULTIPLIER_DURATION_MS.toFloat()).coerceIn(0f, 1f) },
                    color = PlanetGold,
                    trackColor = StarWhite.copy(alpha = 0.12f),
                    modifier = Modifier
                        .fillMaxWidth(0.55f)
                        .height(4.dp)
                        .clip(RoundedCornerShape(4.dp))
                )
            }

            val wordText = currentWord?.text?.uppercase().orEmpty()
            Text(
                text = if (wordText.isBlank()) localizedGalaxyconText("flyToPlanet", locale) else wordProgress(wordText, currentLetterIndex),
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
            LinearProgressIndicator(
                progress = { distanceProgress.coerceIn(0f, 1f) },
                color = PlanetBlue,
                trackColor = StarWhite.copy(alpha = 0.12f),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(7.dp)
                    .clip(RoundedCornerShape(4.dp))
            )
        }
    }
}

@Composable
private fun LaneBackgrounds(laneSize: Dp, isLandscape: Boolean) {
    if (isLandscape) {
        Column(modifier = Modifier.fillMaxSize()) {
            repeat(3) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(laneSize)
                        .background(Color.Transparent)
                )
            }
        }
    } else {
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
}

@Composable
private fun GalaxyconPlayer(invulnerable: Boolean, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "🚀",
            style = MaterialTheme.typography.displaySmall,
            textAlign = TextAlign.Center,
            modifier = Modifier.background(
                if (invulnerable) PlanetRed.copy(alpha = 0.18f) else Color.Transparent,
                RoundedCornerShape(18.dp)
            )
        )
    }
}

@Composable
private fun GalaxyconPlanet(name: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val planet = Path().apply {
                moveTo(w * 0.48f, h * 0.04f)
                cubicTo(w * 0.78f, h * 0.02f, w * 0.98f, h * 0.25f, w * 0.92f, h * 0.52f)
                cubicTo(w * 0.88f, h * 0.83f, w * 0.56f, h * 1.00f, w * 0.26f, h * 0.88f)
                cubicTo(w * 0.02f, h * 0.78f, w * 0.02f, h * 0.32f, w * 0.22f, h * 0.16f)
                cubicTo(w * 0.30f, h * 0.09f, w * 0.38f, h * 0.05f, w * 0.48f, h * 0.04f)
                close()
            }
            drawPath(planet, PlanetGold.copy(alpha = 0.90f))
            drawLine(
                color = DeepSpaceBlue.copy(alpha = 0.24f),
                start = Offset(w * 0.12f, h * 0.58f),
                end = Offset(w * 0.88f, h * 0.42f),
                strokeWidth = w * 0.10f,
                cap = androidx.compose.ui.graphics.StrokeCap.Round
            )
            drawCircle(StarWhite.copy(alpha = 0.18f), radius = w * 0.10f, center = Offset(w * 0.35f, h * 0.28f))
        }
        Text(
            text = name.take(3).uppercase(),
            color = DeepSpaceBlue,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold
        )
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
                val isCurrentCorrectLetter = nextRequiredLetter != null && obj.label.equals(nextRequiredLetter.toString(), ignoreCase = true)
                Text(
                    text = obj.label,
                    color = if (isCurrentCorrectLetter) PlanetGold else StarWhite,
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

        GalaxyconObjectType.STAR,
        GalaxyconObjectType.MULTIPLIER -> {
            val color = if (obj.type == GalaxyconObjectType.MULTIPLIER) PlanetBlue.copy(alpha = 0.92f) else PlanetGold.copy(alpha = 0.95f)
            Box(
                modifier = modifier
                    .size(size)
                    .clip(RoundedCornerShape(16.dp))
                    .background(color),
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
        "collisions" -> "Collisions"
        "stickers" -> "Stickers earned"
        "replay" -> "Replay"
        "multiplierReady" -> "X1"
        else -> key
    }

    "fr-BE" -> when (key) {
        "points" -> "Points"
        "flyToPlanet" -> "Va vers la planete !"
        "planetReached" -> "Planete atteinte !"
        "tryAgain" -> "Presque !"
        "words" -> "Mots"
        "collisions" -> "Collisions"
        "stickers" -> "Stickers gagnes"
        "replay" -> "Rejouer"
        "multiplierReady" -> "X1"
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
        "multiplierReady" -> "X1"
        else -> key
    }
}

private fun stickerText(locale: String, sticker: String): String = when (normalizeGalaxyconLocale(locale)) {
    "en-US" -> "Sticker: $sticker"
    "fr-BE" -> "Sticker : $sticker"
    else -> "Sticker: $sticker"
}

private fun wrongLetterText(locale: String): String = when (normalizeGalaxyconLocale(locale)) {
    "en-US" -> "Not this letter"
    "fr-BE" -> "Pas cette lettre"
    else -> "Niet deze letter"
}

private fun impactText(locale: String): String = when (normalizeGalaxyconLocale(locale)) {
    "en-US" -> "Watch out!"
    "fr-BE" -> "Attention !"
    else -> "Opletten!"
}
