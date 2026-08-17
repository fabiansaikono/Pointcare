package be.saikono.poincare.ui.puzzle

import androidx.compose.foundation.layout.PaddingValues
import android.content.res.Configuration
import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import be.saikono.poincare.R
import be.saikono.poincare.ui.theme.DeepSpaceBlue
import be.saikono.poincare.ui.theme.PlanetBlue
import be.saikono.poincare.ui.theme.PlanetGold
import be.saikono.poincare.ui.theme.PlanetGreen
import be.saikono.poincare.ui.theme.PlanetRed
import be.saikono.poincare.ui.theme.StarWhite
import kotlinx.coroutines.isActive
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt
import kotlin.random.Random
import kotlin.math.PI
import kotlin.math.sin
import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import com.caverock.androidsvg.SVG

@Composable
fun MothershipDockGame(
    readingGrade: Int,
    mathGrade: Int,
    difficultyStars: Int,
    exerciseLanguage: String,
    onComplete: (MothershipDockRunResult) -> Unit,
    modifier: Modifier = Modifier
) {
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }

    val sessionKey = "$readingGrade|$mathGrade|$difficultyStars|$exerciseLanguage"
    val session = remember(sessionKey) {
        MothershipDockSessionStore.getOrCreate(
            key = sessionKey,
            readingGrade = readingGrade,
            mathGrade = mathGrade,
            difficultyStars = difficultyStars,
            exerciseLanguage = exerciseLanguage
        )
    }


    val outcome = session.outcomeName?.let { DockOutcome.valueOf(it) }
    val nowMs = System.currentTimeMillis()
    var firingPointer by remember(sessionKey) { mutableStateOf<Offset?>(null) }

    fun currentHitForBeam(targetOffset: Offset): Pair<DockVessel?, Offset> {
        val origin = mothershipBeamOrigin(canvasSize, isLandscape)
        val rayTarget = extendedRayTarget(origin, targetOffset, canvasSize)
        val hit = firstVesselHitByBeam(
            origin = origin,
            target = rayTarget,
            vessels = session.vessels,
            canvasSize = canvasSize,
            isLandscape = isLandscape
        ) ?: hitVesselAtTap(
            tapOffset = targetOffset,
            vessels = session.vessels,
            canvasSize = canvasSize,
            isLandscape = isLandscape
        )
        return hit to rayTarget
    }

    fun applyContinuousLaser(targetOffset: Offset, deltaMs: Float) {
        if (session.outcomeName != null || canvasSize.width <= 0 || canvasSize.height <= 0) return

        val origin = mothershipBeamOrigin(canvasSize, isLandscape)
        val (hit, rayTarget) = currentHitForBeam(targetOffset)
        val eventTime = System.currentTimeMillis()

        if (hit != null) {
            val center = vesselCenter(hit, canvasSize, isLandscape)
            val impactPoint = closestPointOnSegment(center, origin, rayTarget)
            val damagePerMs = beamDamagePerMsForImpact(hit, impactPoint, canvasSize, isLandscape)
            if (damagePerMs > 0f) {
                val damage = damagePerMs * deltaMs
                session.vessels = session.vessels.map { vessel ->
                    if (vessel.id == hit.id) vessel.copy(
                        lifePoints = (vessel.lifePoints - damage).coerceAtLeast(0f),
                        glowUntilMs = if (vessel.isFriendly) eventTime + 420L else vessel.glowUntilMs
                    ) else vessel
                }
                val updated = session.vessels.firstOrNull { it.id == hit.id }
                if (updated != null && updated.lifePoints <= 0f) {
                    session.explosions = session.explosions + DockExplosion(center.x, center.y, eventTime, updated.isFriendly)
                    session.vessels = session.vessels.map { vessel -> if (vessel.id == updated.id) vessel.copy(alive = false) else vessel }
                    if (updated.isFriendly) {
                        session.outcomeName = DockOutcome.FriendlyShot.name
                    } else {
                        session.enemiesDestroyed += 1
                    }
                }
            }
        }
    }

    LaunchedEffect(sessionKey, session.refreshNonce, canvasSize, isLandscape) {
        if (canvasSize.width <= 0 || canvasSize.height <= 0) return@LaunchedEffect
        var previousFrame = 0L
        while (isActive) {
            withFrameNanos { frameNanos ->
                if (previousFrame == 0L) {
                    previousFrame = frameNanos
                    return@withFrameNanos
                }
                val deltaSeconds = ((frameNanos - previousFrame) / 1_000_000_000f).coerceIn(0f, 0.05f)
                previousFrame = frameNanos
                val frameNow = System.currentTimeMillis()

                if (session.outcomeName == null) {
                    val baseTravelSeconds = when (difficultyStars.coerceIn(1, 4)) {
                        1 -> 5f
                        2 -> 4.2f
                        3 -> 3.6f
                        else -> 3.1f
                    } * if (isLandscape) 1.60f else 1.15f
                    val travelDistancePx = startEdgeToFinishLineDistance(canvasSize, isLandscape)
                    val baseSpeedPxPerSecond = if (baseTravelSeconds > 0f) travelDistancePx / baseTravelSeconds else 0f
                    session.vessels = session.vessels.map { vessel ->
                        if (!vessel.alive) vessel else vessel.copy(progressPx = vessel.progressPx + baseSpeedPxPerSecond * vessel.speedPxPerSecond * deltaSeconds)
                    }

                    firingPointer?.let { target ->
                        applyContinuousLaser(target, deltaSeconds * 1000f)
                    } ?: run {
                        session.beam = null
                    }

                    val screenRect = Rect(0f, 0f, canvasSize.width.toFloat(), canvasSize.height.toFloat())
                    val newlySeenIds = session.vessels
                        .filter { vessel -> vessel.alive && vesselBounds(vessel, canvasSize, isLandscape).overlaps(screenRect) }
                        .map { vessel -> vessel.id }
                        .toSet()
                    if (newlySeenIds.isNotEmpty()) {
                        session.seenShipIds = session.seenShipIds + newlySeenIds
                    }

                    val dockedVessel = session.vessels.firstOrNull { vessel ->
                        vessel.alive && hasVesselCrossedFinishLine(vessel, canvasSize, isLandscape)
                    }
                    if (dockedVessel != null) {
                        val eventTime = System.currentTimeMillis()
                        if (dockedVessel.isFriendly) {
                            session.vessels = session.vessels.map { vessel ->
                                if (vessel.id == dockedVessel.id) vessel.copy(alive = false) else vessel
                            }
                            session.friendlyDockedCount += 1
                            if (session.friendlyDockedCount >= 3) {
                                session.outcomeName = DockOutcome.Win.name
                            }
                        } else {
                            if (session.shieldCharges > 0) {
                                session.shieldCharges -= 1
                                val center = vesselCenter(dockedVessel, canvasSize, isLandscape)
                                session.explosions = session.explosions + DockExplosion(center.x, center.y, eventTime, false)
                                session.vessels = session.vessels.map { vessel ->
                                    if (vessel.id == dockedVessel.id) vessel.copy(alive = false) else vessel
                                }
                            } else {
                                session.outcomeName = DockOutcome.EnemyDocked.name
                            }
                        }
                    }
                }

                if (firingPointer == null) {
                    session.beam = null
                }
                session.explosions = session.explosions.filter { frameNow - it.createdAtMs < 520L }
            }
        }
    }

    fun emitCompletion(finalOutcome: DockOutcome) {
        if (session.completionSent) return
        session.completionSent = true
        onComplete(
            MothershipDockRunResult(
                docked = finalOutcome == DockOutcome.Win,
                elapsedMs = System.currentTimeMillis() - session.startTimeMs,
                shotsFired = session.shotsFired,
                enemiesDestroyed = session.enemiesDestroyed,
                friendlyHit = finalOutcome == DockOutcome.FriendlyShot,
                enemyDocked = finalOutcome == DockOutcome.EnemyDocked,
                target = session.challenge.target,
                mode = session.challenge.mode.storageValue
            )
        )
    }

    LaunchedEffect(session.outcomeName) {
        val finalOutcome = session.outcomeName?.let { DockOutcome.valueOf(it) } ?: return@LaunchedEffect
        firingPointer = null
        session.beam = null
        if (finalOutcome != DockOutcome.Win) {
            emitCompletion(finalOutcome)
        }
    }


    fun restartGame() {
        val newChallenge = MothershipDockGenerator.create(
            readingGrade = readingGrade,
            mathGrade = mathGrade,
            difficultyStars = difficultyStars,
            locale = exerciseLanguage,
            seed = Random.nextInt()
        )
        session.challenge = newChallenge
        session.vessels = createInitialDockVessels(newChallenge, difficultyStars)
        session.outcomeName = null
        session.shotsFired = 0
        session.enemiesDestroyed = 0
        session.friendlyDockedCount = 0
        session.shieldCharges = 1
        session.startTimeMs = System.currentTimeMillis()
        session.completionSent = false
        session.seenShipIds = emptySet()
        session.beam = null
        session.explosions = emptyList()
        session.refreshNonce += 1
    }

    val minDim = min(canvasSize.width, canvasSize.height).toFloat().coerceAtLeast(1f)
    val mothershipSizePx = minDim * if (isLandscape) 0.66f else 0.60f
    val vesselSizePx = minDim * if (isLandscape) 0.40f else 0.36f
    val mothershipCenter = mothershipCenter(canvasSize, isLandscape, mothershipSizePx)




    val liveBeam = firingPointer?.let { targetOffset ->
        val origin = mothershipBeamOrigin(canvasSize, isLandscape)
        val (hit, rayTarget) = currentHitForBeam(targetOffset)
        val hitKind = when {
            hit == null -> BeamHitKind.None
            hit.isFriendly -> BeamHitKind.Friendly
            else -> BeamHitKind.Enemy
        }
        DockBeam(
            origin = origin,
            target = rayTarget,
            createdAtMs = nowMs,
            hitKind = hitKind
        )
    }


    Box(
        modifier = modifier
            .fillMaxSize()
            .background(DeepSpaceBlue)
            .onSizeChanged { newSize ->
                if (newSize.width > 0 && newSize.height > 0) canvasSize = newSize
            }
            .pointerInput(sessionKey, session.refreshNonce, canvasSize, isLandscape, session.outcomeName) {
                if (session.outcomeName != null) return@pointerInput
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    down.consume()
                    firingPointer = down.position
                    session.shotsFired += 1
                    try {
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: event.changes.firstOrNull()
                            if (change == null) break
                            firingPointer = change.position
                            change.consume()
                            if (!change.pressed) break
                        }
                    } finally {
                        firingPointer = null
                        session.beam = null
                    }
                }
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawDockStars(session.challenge.starSeed)
            liveBeam?.let { drawDockBeam(it, nowMs) }
            session.explosions.forEach { drawDockExplosion(it, nowMs) }
        }

        if (canvasSize.width > 0 && canvasSize.height > 0) {
            if (session.shieldCharges > 0) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    drawCircle(
                        color = StarWhite.copy(alpha = 0.06f),
                        radius = mothershipSizePx * 0.42f,
                        center = mothershipCenter
                    )
                    drawCircle(
                        color = StarWhite.copy(alpha = 0.14f),
                        radius = mothershipSizePx * 0.36f,
                        center = mothershipCenter,
                        style = Stroke(width = mothershipSizePx * 0.028f)
                    )
                }
            }
            ShipSprite(
                assetPath = if (isLandscape) "ships/p2m02.svg" else "ships/p2m01.svg",
                label = session.challenge.target,
                center = mothershipCenter,
                sizePx = mothershipSizePx,
                labelScale = 0.120f,
                isFriendly = true,
                isMothership = true,
                lifePoints = 0f,
                maxLifePoints = 0f,
                glowUntilMs = 0L,
                nowMs = nowMs
            )

            session.vessels.forEach { vessel ->
                if (vessel.alive) {
                    ShipSprite(
                        assetPath = "ships/p2v01.svg",
                        label = vessel.label,
                        center = vesselCenter(vessel, canvasSize, isLandscape),
                        sizePx = vesselSizePx,
                        labelScale = 0.095f,
                        isFriendly = vessel.isFriendly,
                        isMothership = false,
                        lifePoints = vessel.lifePoints,
                        maxLifePoints = vessel.maxLifePoints,
                        glowUntilMs = vessel.glowUntilMs,
                        nowMs = nowMs
                    )
                }
            }
        }

        DockMissionHud(
            mode = session.challenge.mode,
            locale = exerciseLanguage,
            enemiesDestroyed = session.enemiesDestroyed,
            friendlyDockedCount = session.friendlyDockedCount,
            friendlyGoal = 3,
            modifier = Modifier
                .align(Alignment.TopStart)
                .fillMaxWidth()
                .padding(start = 10.dp, top = 10.dp, end = 10.dp)
        )

        outcome?.let { finalOutcome ->
            DockOutcomePanel(
                outcome = finalOutcome,
                locale = exerciseLanguage,
                onRetry = { restartGame() },
                onNextMission = {
                    emitCompletion(DockOutcome.Win)
                    restartGame()
                },
                modifier = Modifier.align(Alignment.Center)
            )
        }
    }
}

private object MothershipDockSessionStore {
    private val sessions = mutableMapOf<String, DockGameSession>()

    fun getOrCreate(
        key: String,
        readingGrade: Int,
        mathGrade: Int,
        difficultyStars: Int,
        exerciseLanguage: String
    ): DockGameSession {
        return sessions.getOrPut(key) {
            val challenge = MothershipDockGenerator.create(
                readingGrade = readingGrade,
                mathGrade = mathGrade,
                difficultyStars = difficultyStars,
                locale = exerciseLanguage,
                seed = Random.nextInt()
            )
            DockGameSession(
                challenge = challenge,
                vessels = createInitialDockVessels(challenge, difficultyStars)
            )
        }
    }

    fun clear(key: String) {
        sessions.remove(key)
    }
}

private class DockGameSession(
    challenge: DockChallenge,
    vessels: List<DockVessel>
) {
    var challenge by mutableStateOf(challenge)
    var vessels by mutableStateOf(vessels)
    var outcomeName by mutableStateOf<String?>(null)
    var shotsFired by mutableIntStateOf(0)
    var enemiesDestroyed by mutableIntStateOf(0)
    var friendlyDockedCount by mutableIntStateOf(0)
    var shieldCharges by mutableIntStateOf(1)
    var startTimeMs by mutableLongStateOf(System.currentTimeMillis())
    var completionSent by mutableStateOf(false)
    var refreshNonce by mutableIntStateOf(0)
    var seenShipIds by mutableStateOf<Set<Int>>(emptySet())
    var beam by mutableStateOf<DockBeam?>(null)
    var explosions by mutableStateOf<List<DockExplosion>>(emptyList())
}

@Composable
private fun rememberSvgBitmap(assetPath: String, sizePx: Int): ImageBitmap? {
    val context = LocalContext.current
    return remember(assetPath, sizePx) {
        runCatching {
            context.assets.open(assetPath).use { input ->
                val svg = SVG.getFromInputStream(input)
                val bitmap = Bitmap.createBitmap(sizePx.coerceAtLeast(8), sizePx.coerceAtLeast(8), Bitmap.Config.ARGB_8888)
                val canvas = AndroidCanvas(bitmap)
                svg.setDocumentWidth(sizePx.toFloat())
                svg.setDocumentHeight(sizePx.toFloat())
                svg.renderToCanvas(canvas)
                bitmap.asImageBitmap()
            }
        }.getOrNull()
    }
}

@Composable
private fun ShipSprite(
    assetPath: String,
    label: String,
    center: Offset,
    sizePx: Float,
    labelScale: Float,
    isFriendly: Boolean,
    isMothership: Boolean,
    lifePoints: Float,
    maxLifePoints: Float,
    glowUntilMs: Long,
    nowMs: Long
) {
    val density = LocalDensity.current
    val sizeDp = with(density) { sizePx.toDp() }
    val letterCount = label.count { it.isLetter() }
    val lengthScale = when {
        letterCount <= 5 -> 1f
        letterCount == 6 -> 0.86f
        letterCount == 7 -> 0.76f
        else -> 0.68f
    }
    val textSp = ((sizePx * labelScale * lengthScale) / density.density).sp
    val leftPx = (center.x - sizePx / 2f).toInt()
    val topPx = (center.y - sizePx / 2f).toInt()
    val glowProgress = ((glowUntilMs - nowMs).coerceAtLeast(0L).toFloat() / 420f).coerceIn(0f, 1f)
    val bitmap = rememberSvgBitmap(assetPath, sizePx.toInt())
    val glowAlpha = if (glowProgress > 0f) {
        val phase = ((nowMs % 260L).toFloat() / 260f) * (2f * PI.toFloat())
        (0.18f + 0.10f * sin(phase)).coerceIn(0.08f, 0.28f) * glowProgress
    } else 0f

    Box(
        modifier = Modifier
            .offset { IntOffset(leftPx, topPx) }
            .size(sizeDp)
    ) {
        if (glowAlpha > 0f && !isMothership && isFriendly) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val center = Offset(size.width / 2f, size.height / 2f)
                val radius = size.minDimension * 0.54f
                drawCircle(
                    brush = Brush.radialGradient(
                        colorStops = arrayOf(
                            0.00f to Color.Transparent,
                            0.42f to PlanetRed.copy(alpha = glowAlpha * 0.35f),
                            0.66f to PlanetRed.copy(alpha = glowAlpha),
                            0.84f to PlanetRed.copy(alpha = glowAlpha * 0.55f),
                            1.00f to Color.Transparent
                        ),
                        center = center,
                        radius = radius
                    ),
                    radius = radius,
                    center = center
                )
                drawCircle(
                    color = PlanetRed.copy(alpha = glowAlpha * 0.65f),
                    radius = size.minDimension * 0.43f,
                    center = center,
                    style = Stroke(width = size.minDimension * 0.035f)
                )
            }
        }

        bitmap?.let {
            Image(
                bitmap = it,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize()
            )
        }

        val labelCenterX = sizePx * 0.50f
        val labelCenterY = sizePx * 0.50f
        val labelWidthPx = if (isMothership) sizePx * 0.52f else sizePx * 0.80f
        val labelHeightPx = if (isMothership) sizePx * 0.18f else sizePx * 0.18f

        Box(
            modifier = Modifier
                .offset {
                    IntOffset(
                        (labelCenterX - labelWidthPx / 2f).toInt(),
                        (labelCenterY - labelHeightPx / 2f).toInt()
                    )
                }
                .size(with(density) { labelWidthPx.toDp() }, with(density) { labelHeightPx.toDp() })
        ) {
            Text(
                text = label.uppercase(),
                color = StarWhite,
                fontWeight = FontWeight.ExtraBold,
                fontSize = textSp,
                lineHeight = textSp,
                letterSpacing = 0.25.sp,
                textAlign = TextAlign.Center,
                maxLines = 1,
                modifier = Modifier.align(Alignment.Center)
            )
        }

        if (!isMothership && maxLifePoints > 0f) {
            val ratio = (lifePoints / maxLifePoints).coerceIn(0f, 1f)
            Canvas(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .offset { IntOffset(0, -(sizePx * 0.10f).toInt()) }
                    .padding(bottom = 6.dp)
                    .size(with(density) { (sizePx * 0.55f).toDp() }, 6.dp)
            ) {
                drawRoundRect(
                    color = Color.Black.copy(alpha = 0.45f),
                    cornerRadius = CornerRadius(size.height / 2f, size.height / 2f)
                )
                drawRoundRect(
                    color = PlanetRed,
                    size = androidx.compose.ui.geometry.Size(size.width * ratio, size.height),
                    cornerRadius = CornerRadius(size.height / 2f, size.height / 2f)
                )
            }
        }
    }
}

@Composable
private fun DockMissionHud(
    mode: DockMode,
    locale: String,
    enemiesDestroyed: Int,
    friendlyDockedCount: Int,
    friendlyGoal: Int,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.fillMaxWidth()) {
        Text(
            text = "${localizedDockText("ships", locale)} $friendlyDockedCount/$friendlyGoal",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.ExtraBold,
            color = PlanetGold,
            modifier = Modifier.align(Alignment.TopStart)
        )
        Text(
            text = "⚡ $enemiesDestroyed",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.ExtraBold,
            color = PlanetBlue,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(end = 8.dp)
        )
    }
}

@Composable
private fun DockOutcomePanel(
    outcome: DockOutcome,
    locale: String,
    onRetry: () -> Unit,
    onNextMission: () -> Unit,
    modifier: Modifier = Modifier
) {
    val won = outcome == DockOutcome.Win
    Surface(
        color = if (won) PlanetGreen.copy(alpha = 0.94f) else PlanetRed.copy(alpha = 0.94f),
        contentColor = StarWhite,
        shape = RoundedCornerShape(24.dp),
        shadowElevation = 8.dp,
        modifier = modifier.fillMaxWidth(0.82f)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = when (outcome) {
                    DockOutcome.Win -> localizedDockText("win", locale)
                    DockOutcome.FriendlyShot -> localizedDockText("friendlyShot", locale)
                    DockOutcome.EnemyDocked -> localizedDockText("enemyDocked", locale)
                },
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            if (won) {
                Button(
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                    onClick = onNextMission,
                    colors = ButtonDefaults.buttonColors(containerColor = PlanetBlue, contentColor = DeepSpaceBlue)
                ) {
                    Text(localizedDockText("nextMission", locale), fontWeight = FontWeight.Bold)
                }
            } else {
                Text(
                    text = localizedDockText("tryAgainHint", locale),
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center
                )
                Button(
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                    onClick = onRetry,
                    colors = ButtonDefaults.buttonColors(containerColor = DeepSpaceBlue.copy(alpha = .88f), contentColor = PlanetGold)
                ) {
                    Text(localizedDockText("retry", locale), fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

private data class DockChallenge(
    val target: String,
    val mode: DockMode,
    val vessels: List<DockVesselTemplate>,
    val starSeed: Int
) {
    val enemyCount: Int get() = vessels.count { !it.isFriendly }
}

private data class DockVesselTemplate(
    val label: String,
    val answer: String,
    val isFriendly: Boolean
)

private data class DockVessel(
    val id: Int,
    val label: String,
    val answer: String,
    val isFriendly: Boolean,
    val laneFraction: Float,
    val progressPx: Float,
    val spawnOffsetPx: Float,
    val speedPxPerSecond: Float,
    val lifePoints: Float,
    val maxLifePoints: Float,
    val glowUntilMs: Long = 0L,
    val alive: Boolean = true
)


private data class DockBeam(
    val origin: Offset,
    val target: Offset,
    val createdAtMs: Long,
    val hitKind: BeamHitKind
)

private enum class BeamHitKind {
    Enemy,
    Friendly,
    None
}

private data class DockExplosion(
    val x: Float,
    val y: Float,
    val createdAtMs: Long,
    val friendly: Boolean
)

private enum class DockMode(val storageValue: String) {
    Words("words"),
    Numbers("numbers")
}

private enum class DockOutcome {
    Win,
    FriendlyShot,
    EnemyDocked
}

private object MothershipDockGenerator {
    fun create(
        readingGrade: Int,
        mathGrade: Int,
        difficultyStars: Int,
        locale: String,
        seed: Int
    ): DockChallenge {
        val random = Random(seed)
        val stars = difficultyStars.coerceIn(1, 4)
        val allowWords = readingGrade <= 2
        val mode = if (allowWords && random.nextFloat() < 0.55f) DockMode.Words else DockMode.Numbers
        return when (mode) {
            DockMode.Words -> createWordChallenge(locale, stars, random)
            DockMode.Numbers -> createNumberChallenge(mathGrade, stars, random)
        }
    }

    private fun totalEnemyCount(stars: Int, random: Random): Int {
        return when (stars) {
            1 -> random.nextInt(6, 13)   // 3 friendly ships x 2..4 enemies
            2 -> random.nextInt(9, 16)   // 3 friendly ships x 3..5 enemies
            3 -> random.nextInt(12, 19) // 3 friendly ships x 4..6 enemies
            else -> random.nextInt(18, 25) // Hard Mode level 4: 3 friendly ships x 6..8 enemies
        }
    }

    private fun createWordChallenge(locale: String, stars: Int, random: Random): DockChallenge {
        val words = wordsFor(locale, stars).filter { it.length <= 8 }.shuffled(random)
        val target = words.first()
        val enemyCount = totalEnemyCount(stars, random)
        val enemyPool = buildList {
            while (size < enemyCount) {
                addAll(words.drop(1))
            }
        }.take(enemyCount)
        val templates = buildList {
            add(DockVesselTemplate(label = target, answer = target, isFriendly = true))
            enemyPool.forEach { enemy ->
                add(DockVesselTemplate(label = enemy, answer = enemy, isFriendly = false))
            }
        }.shuffled(random)
        return DockChallenge(target = target, mode = DockMode.Words, vessels = templates, starSeed = random.nextInt())
    }

    private fun createNumberChallenge(mathGrade: Int, stars: Int, random: Random): DockChallenge {
        val target = when (stars) {
            1 -> random.nextInt(6, 31)
            2 -> random.nextInt(18, 101)
            3 -> random.nextInt(30, 181)
            else -> random.nextInt(120, 1_001)
        }
        val enemyCount = totalEnemyCount(stars, random)
        val expressions = mutableListOf<DockVesselTemplate>()
        expressions += DockVesselTemplate(
            label = expressionForResult(target, mathGrade, stars, random),
            answer = target.toString(),
            isFriendly = true
        )
        val usedAnswers = mutableSetOf(target)
        repeat(enemyCount) {
            var wrong: Int
            do {
                val spread = when (stars) {
                    1 -> 9
                    2 -> 18
                    3 -> 28
                    else -> 80
                }
                val delta = random.nextInt(-spread, spread + 1).takeIf { it != 0 } ?: 3
                wrong = (target + delta).coerceAtLeast(2)
            } while (!usedAnswers.add(wrong))
            expressions += DockVesselTemplate(
                label = expressionForResult(wrong, mathGrade, stars, random),
                answer = wrong.toString(),
                isFriendly = false
            )
        }
        return DockChallenge(
            target = target.toString(),
            mode = DockMode.Numbers,
            vessels = expressions.shuffled(random),
            starSeed = random.nextInt()
        )
    }

    private fun wordsFor(locale: String, stars: Int): List<String> {
        val language = when (locale) {
            "fr", "fr-BE", "fr_BE", "fr-FR", "fr_FR" -> "fr"
            "en", "en-US", "en_US", "en-GB", "en_GB" -> "en"
            else -> "nl"
        }
        val candidates = when (language) {
            "en" -> when (stars) {
                1 -> listOf("sun", "moon", "ship", "star", "map", "ring", "mars", "dust", "port", "beam")
                2 -> listOf("orbit", "comet", "pilot", "laser", "rocket", "planet", "signal", "meteor", "sensor", "target")
                3 -> listOf("galaxy", "station", "captain", "asteroid", "mission", "oxygen", "gravity", "hangar", "quantum", "reactor")
                else -> listOf("asteroid", "universe", "eclipse", "shuttle", "capsule", "orbital", "starship", "airlock", "thruster", "sunflare")
            }
            "fr" -> when (stars) {
                1 -> listOf("soleil", "lune", "base", "etoile", "mars", "carte", "fusee", "port", "rayon", "dock")
                2 -> listOf("orbite", "comete", "pilote", "laser", "planete", "signal", "meteor", "cosmos", "cible", "module")
                3 -> listOf("galaxie", "station", "capitaine", "asteroide", "mission", "oxygene", "gravite", "hangar", "reacteur", "quantum")
                else -> listOf("meteore", "univers", "eclipse", "navette", "capsule", "orbital", "cosmos", "module", "moteur", "lumiere")
            }
            else -> when (stars) {
                1 -> listOf("zon", "maan", "schip", "ster", "mars", "kaart", "ring", "stof", "poort", "straal")
                2 -> listOf("baan", "komeet", "piloot", "laser", "raket", "planeet", "signaal", "meteoor", "doel", "sensor")
                3 -> listOf("melkweg", "station", "kapitein", "asteroide", "missie", "zuurstof", "zwaarte", "hangar", "reactor", "quantum")
                else -> listOf("meteoor", "heelal", "eclips", "shuttle", "capsule", "baanvlak", "sterbaan", "ruimte", "komeet", "sterrijk")
            }
        }
        return candidates.filter { it.length <= 8 }
    }

    private fun expressionForResult(result: Int, mathGrade: Int, stars: Int, random: Random): String {
        val attempts = buildList {
            addAll(additionExpressions(result, stars, random))
            addAll(subtractionExpressions(result, stars, random))
            if (mathGrade >= 2 || stars >= 2) addAll(multiplicationExpressions(result, stars, random))
            if (stars >= 3) addAll(compactDoubleStepExpressions(result, random))
        }.filter { it.length <= if (stars >= 4) 8 else 5 }
        return attempts.randomOrNull() ?: result.toString().take(5)
    }

    private fun additionExpressions(result: Int, stars: Int, random: Random): List<String> {
        if (result <= 2) return listOf("1+1")
        val maxLeft = when (stars) {
            1 -> result - 1
            2 -> max(1, result - 5)
            3 -> max(1, result - 10)
            else -> max(1, result - 25)
        }
        val a = random.nextInt(1, maxLeft + 1).coerceAtMost(result - 1)
        val b = result - a
        val a2 = (result - random.nextInt(1, min(12, result))).coerceAtLeast(1)
        return listOf("$a+$b", "$a2+${result - a2}")
    }

    private fun subtractionExpressions(result: Int, stars: Int, random: Random): List<String> {
        val b = when (stars) {
            1 -> random.nextInt(1, 10)
            2 -> random.nextInt(2, 16)
            3 -> random.nextInt(3, 26)
            else -> random.nextInt(20, 101)
        }
        val a = result + b
        return listOf("$a-$b")
    }

    private fun multiplicationExpressions(result: Int, stars: Int, random: Random): List<String> {
        val factorRange = when (stars) {
            1 -> 2..5
            2 -> 2..9
            3 -> 2..12
            else -> 6..20
        }
        val factors = factorRange.filter { it != 0 && result % it == 0 }
        if (factors.isEmpty()) return emptyList()
        val factor = factors.random(random)
        return listOf("${result / factor}x$factor", "${factor}x${result / factor}")
    }

    private fun compactDoubleStepExpressions(result: Int, random: Random): List<String> {
        if (result < 10) return emptyList()
        val base = random.nextInt(2, min(12, result - 1))
        val plus = result - base
        return listOf("$base+$plus")
    }
}


private fun createInitialDockVessels(challenge: DockChallenge, difficultyStars: Int): List<DockVessel> {
    val stars = difficultyStars.coerceIn(1, 4)
    val friendly = challenge.vessels.firstOrNull { it.isFriendly }
    val enemies = challenge.vessels.filter { !it.isFriendly }.shuffled()

    val orderedTemplates = buildList {
        if (enemies.isNotEmpty()) add(enemies[0])
        if (enemies.size > 1) add(enemies[1])
        var enemyIndex = 2
        repeat(3) {
            if (friendly != null) add(friendly)
            val enemiesPerFriendly = when (stars) {
                1 -> Random.nextInt(2, 5)
                2 -> Random.nextInt(3, 6)
                3 -> Random.nextInt(4, 7)
                else -> Random.nextInt(6, 9)
            }
            repeat(enemiesPerFriendly) {
                enemies.getOrNull(enemyIndex)?.let { add(it) }
                enemyIndex += 1
            }
        }
        while (enemyIndex < enemies.size) {
            add(enemies[enemyIndex])
            enemyIndex += 1
        }
    }.ifEmpty { challenge.vessels }

    return orderedTemplates.mapIndexed { index, template ->
        val maxLife = if (template.isFriendly) 500f else 200f
        DockVessel(
            id = index,
            label = template.label,
            answer = template.answer,
            isFriendly = template.isFriendly,
            laneFraction = 0.5f,
            progressPx = 0f,
            spawnOffsetPx = index * 470f,
            speedPxPerSecond = 1f,
            lifePoints = maxLife,
            maxLifePoints = maxLife,
            glowUntilMs = 0L,
            alive = true
        )
    }
}

private fun DrawScope.drawDockStars(seed: Int) {
    val random = Random(seed)
    repeat(95) {
        val x = random.nextFloat() * size.width
        val y = random.nextFloat() * size.height
        val radius = random.nextFloat() * 1.7f + 0.45f
        drawCircle(StarWhite.copy(alpha = random.nextFloat() * 0.45f + 0.2f), radius, Offset(x, y))
    }
}

private fun DrawScope.drawDockBeam(beam: DockBeam, nowMs: Long) {
    val age = (nowMs - beam.createdAtMs).coerceAtLeast(0L)
    val alpha = (1f - age / 220f).coerceIn(0f, 1f)
    val beamColor = when (beam.hitKind) {
        BeamHitKind.Enemy -> PlanetGreen
        BeamHitKind.Friendly -> PlanetRed
        BeamHitKind.None -> StarWhite
    }

    drawLine(
        color = beamColor.copy(alpha = 0.16f * alpha),
        start = beam.origin,
        end = beam.target,
        strokeWidth = 32f
    )
    drawLine(
        color = beamColor.copy(alpha = 0.30f * alpha),
        start = beam.origin,
        end = beam.target,
        strokeWidth = 18f
    )
    drawLine(
        color = beamColor.copy(alpha = 0.90f * alpha),
        start = beam.origin,
        end = beam.target,
        strokeWidth = 5f
    )
    drawLine(
        color = StarWhite.copy(alpha = 0.70f * alpha),
        start = beam.origin,
        end = beam.target,
        strokeWidth = 2f
    )
    drawCircle(beamColor.copy(alpha = 0.9f * alpha), radius = 8f, center = beam.target)
}

private fun DrawScope.drawDockExplosion(explosion: DockExplosion, nowMs: Long) {
    val age = (nowMs - explosion.createdAtMs).coerceAtLeast(0L)
    val progress = (age / 520f).coerceIn(0f, 1f)
    val color = if (explosion.friendly) PlanetRed else PlanetGold
    val center = Offset(explosion.x, explosion.y)
    drawCircle(color.copy(alpha = 0.72f * (1f - progress)), radius = 10f + 26f * progress, center = center, style = Stroke(width = 4f))
    drawCircle(StarWhite.copy(alpha = 0.42f * (1f - progress)), radius = 6f + 12f * progress, center = center)
}

private fun mothershipCenter(size: IntSize, isLandscape: Boolean, mothershipSizePx: Float): Offset {
    return if (isLandscape) {
        Offset(mothershipSizePx * 0.35f, size.height / 2f)
    } else {
        Offset(size.width / 2f, mothershipSizePx * 0.34f)
    }
}

private fun mothershipDockRect(size: IntSize, isLandscape: Boolean): Rect {
    val line = finishLineCoordinate(size, isLandscape)
    val minDim = min(size.width, size.height).toFloat().coerceAtLeast(1f)
    val reach = minDim * 0.025f
    return if (isLandscape) {
        Rect(line - reach, 0f, line + reach, size.height.toFloat())
    } else {
        Rect(0f, line - reach, size.width.toFloat(), line + reach)
    }
}

private fun mothershipBeamOrigin(size: IntSize, isLandscape: Boolean): Offset {
    val minDim = min(size.width, size.height).toFloat().coerceAtLeast(1f)
    val mothershipSizePx = minDim * if (isLandscape) 0.66f else 0.60f
    val center = mothershipCenter(size, isLandscape, mothershipSizePx)
    return if (isLandscape) {
        Offset(center.x + mothershipSizePx * 0.47f, center.y - mothershipSizePx * 0.01f)
    } else {
        Offset(center.x, center.y + mothershipSizePx * 0.31f)
    }
}

private fun finishLineCoordinate(size: IntSize, isLandscape: Boolean): Float {
    val minDim = min(size.width, size.height).toFloat().coerceAtLeast(1f)
    val mothershipSizePx = minDim * if (isLandscape) 0.66f else 0.60f
    val center = mothershipCenter(size, isLandscape, mothershipSizePx)
    return if (isLandscape) {
        center.x + mothershipSizePx * 0.32f
    } else {
        center.y + mothershipSizePx * 0.32f
    }
}

private fun startEdgeToFinishLineDistance(size: IntSize, isLandscape: Boolean): Float {
    val finish = finishLineCoordinate(size, isLandscape)
    return if (isLandscape) {
        (size.width.toFloat() - finish).coerceAtLeast(1f)
    } else {
        (size.height.toFloat() - finish).coerceAtLeast(1f)
    }
}

private fun extendedRayTarget(origin: Offset, tap: Offset, size: IntSize): Offset {
    val dx = tap.x - origin.x
    val dy = tap.y - origin.y
    if (dx == 0f && dy == 0f) return tap

    val candidates = mutableListOf<Float>()
    if (dx > 0f) candidates += (size.width - origin.x) / dx
    if (dx < 0f) candidates += (0f - origin.x) / dx
    if (dy > 0f) candidates += (size.height - origin.y) / dy
    if (dy < 0f) candidates += (0f - origin.y) / dy

    val scale = candidates.filter { it > 0f }.minOrNull()?.coerceAtLeast(1f) ?: 1f
    return Offset(origin.x + dx * scale, origin.y + dy * scale)
}

private fun vesselLaneFraction(vessel: DockVessel, isLandscape: Boolean): Float {
    return if (isLandscape) {
        val lanes = listOf(0.26f, 0.64f)
        lanes[vessel.id % lanes.size]
    } else {
        val lanes = listOf(0.18f, 0.44f, 0.70f, 0.86f)
        lanes[vessel.id % lanes.size]
    }
}

private fun vesselCenter(vessel: DockVessel, canvasSize: IntSize, isLandscape: Boolean): Offset {
    val laneFraction = vesselLaneFraction(vessel, isLandscape)
    return if (isLandscape) {
        Offset(
            x = canvasSize.width + 220f + vessel.spawnOffsetPx - vessel.progressPx,
            y = canvasSize.height * laneFraction
        )
    } else {
        Offset(
            x = canvasSize.width * laneFraction,
            y = canvasSize.height + 220f + vessel.spawnOffsetPx - vessel.progressPx
        )
    }
}

private fun hasVesselCrossedFinishLine(vessel: DockVessel, canvasSize: IntSize, isLandscape: Boolean): Boolean {
    val center = vesselCenter(vessel, canvasSize, isLandscape)
    val vesselSizePx = min(canvasSize.width, canvasSize.height).toFloat().coerceAtLeast(1f) * if (isLandscape) 0.40f else 0.36f
    val finishLine = finishLineCoordinate(canvasSize, isLandscape)
    return if (isLandscape) {
        val vesselLeft = center.x - vesselSizePx / 2f
        vesselLeft <= finishLine
    } else {
        val vesselTop = center.y - vesselSizePx / 2f
        vesselTop <= finishLine
    }
}

private fun closestPointOnSegment(point: Offset, a: Offset, b: Offset): Offset {
    val ab = b - a
    val ap = point - a
    val abLen2 = ab.x * ab.x + ab.y * ab.y
    if (abLen2 <= 0.0001f) return a
    val t = ((ap.x * ab.x + ap.y * ab.y) / abLen2).coerceIn(0f, 1f)
    return Offset(a.x + ab.x * t, a.y + ab.y * t)
}

private fun beamDamagePerMsForImpact(vessel: DockVessel, impactPoint: Offset, canvasSize: IntSize, isLandscape: Boolean): Float {
    val bounds = vesselBounds(vessel, canvasSize, isLandscape)
    return if (isLandscape) {
        val insetX = bounds.width * 0.05f
        val insetY = bounds.height * 0.05f
        val inner = Rect(bounds.left + insetX, bounds.top + insetY, bounds.right - insetX, bounds.bottom - insetY)
        if (inner.containsPoint(impactPoint)) 1f else 0f
    } else {
        if (!bounds.containsPoint(impactPoint)) return 0f
        val rel = ((impactPoint.x - bounds.left) / bounds.width).coerceIn(0f, 1f)
        when {
            rel < 0.10f -> 0.25f
            rel < 0.30f -> 0.5f
            rel < 0.70f -> 1f
            rel < 0.90f -> 0.5f
            else -> 0.25f
        }
    }
}

private fun vesselFootprintScale(vessel: DockVessel): Float {
    return if (vessel.isFriendly) 0.5f else 1f
}

private fun vesselBounds(vessel: DockVessel, canvasSize: IntSize, isLandscape: Boolean): Rect {
    val center = vesselCenter(vessel, canvasSize, isLandscape)
    val vesselSizePx = min(canvasSize.width, canvasSize.height).toFloat().coerceAtLeast(1f) * if (isLandscape) 0.40f else 0.36f
    val half = vesselSizePx * vesselFootprintScale(vessel) / 2f
    return Rect(center.x - half, center.y - half, center.x + half, center.y + half)
}

private fun firstVesselHitByBeam(
    origin: Offset,
    target: Offset,
    vessels: List<DockVessel>,
    canvasSize: IntSize,
    isLandscape: Boolean
): DockVessel? {
    val vesselSizePx = min(canvasSize.width, canvasSize.height).toFloat().coerceAtLeast(1f) * if (isLandscape) 0.40f else 0.36f
    return vessels
        .filter { it.alive }
        .mapNotNull { vessel ->
            val center = vesselCenter(vessel, canvasSize, isLandscape)
            val hitRadius = vesselSizePx * 0.62f * vesselFootprintScale(vessel)
            val distance = distancePointToSegment(center, origin, target)
            if (distance <= hitRadius) {
                vessel to distanceBetween(origin, center)
            } else {
                null
            }
        }
        .minByOrNull { it.second }
        ?.first
}

private fun hitVesselAtTap(
    tapOffset: Offset,
    vessels: List<DockVessel>,
    canvasSize: IntSize,
    isLandscape: Boolean
): DockVessel? {
    val baseTouchPadding = min(canvasSize.width, canvasSize.height).toFloat().coerceAtLeast(1f) * 0.07f
    return vessels
        .filter { it.alive }
        .mapNotNull { vessel ->
            val bounds = vesselBounds(vessel, canvasSize, isLandscape)
            val touchPadding = baseTouchPadding * vesselFootprintScale(vessel)
            val expanded = Rect(
                bounds.left - touchPadding,
                bounds.top - touchPadding,
                bounds.right + touchPadding,
                bounds.bottom + touchPadding
            )
            if (expanded.containsPoint(tapOffset)) {
                val center = vesselCenter(vessel, canvasSize, isLandscape)
                vessel to distanceBetween(center, tapOffset)
            } else {
                null
            }
        }
        .minByOrNull { it.second }
        ?.first
}

private fun distancePointToSegment(point: Offset, start: Offset, end: Offset): Float {
    val dx = end.x - start.x
    val dy = end.y - start.y
    if (dx == 0f && dy == 0f) return distanceBetween(point, start)
    val t = (((point.x - start.x) * dx + (point.y - start.y) * dy) / (dx * dx + dy * dy)).coerceIn(0f, 1f)
    val projection = Offset(start.x + t * dx, start.y + t * dy)
    return distanceBetween(point, projection)
}

private fun Rect.containsPoint(point: Offset): Boolean {
    return point.x >= left && point.x <= right && point.y >= top && point.y <= bottom
}

private fun Rect.overlaps(other: Rect): Boolean {
    return left < other.right && right > other.left && top < other.bottom && bottom > other.top
}

private fun distanceBetween(a: Offset, b: Offset): Float {
    return sqrt((a.x - b.x).pow(2) + (a.y - b.y).pow(2))
}

private fun localizedDockText(key: String, locale: String): String {
    val language = when (locale) {
        "fr", "fr-BE", "fr_BE", "fr-FR", "fr_FR" -> "fr"
        "en", "en-US", "en_US", "en-GB", "en_GB" -> "en"
        else -> "nl"
    }
    return when (language) {
        "en" -> when (key) {
            "wordMode" -> "WORDS"
            "numberMode" -> "NUMBERS"
            "ships" -> "SHIPS"
            "win" -> "Three friendly vessels docked!"
            "friendlyShot" -> "You shot the friendly vessel."
            "enemyDocked" -> "An enemy reached the mothership."
            "tryAgainHint" -> "Shoot enemies, protect friendly vessels, and let 3 friendly vessels dock."
            "retry" -> "Retry"
            "nextMission" -> "Next mission"
            else -> key
        }
        "fr" -> when (key) {
            "wordMode" -> "MOTS"
            "numberMode" -> "NOMBRES"
            "ships" -> "VAISSEAUX"
            "win" -> "Trois vaisseaux amis sont arrivés !"
            "friendlyShot" -> "Tu as touché le vaisseau ami."
            "enemyDocked" -> "Un ennemi a atteint le vaisseau mère."
            "tryAgainHint" -> "Tire sur les ennemis, protège les alliés et laisse 3 vaisseaux amis arriver."
            "retry" -> "Réessayer"
            "nextMission" -> "Mission suivante"
            else -> key
        }
        else -> when (key) {
            "wordMode" -> "WOORDEN"
            "numberMode" -> "GETALLEN"
            "ships" -> "SCHEPEN"
            "win" -> "Drie vriendelijke schepen zijn aangekomen!"
            "friendlyShot" -> "Je raakte het vriendelijke schip."
            "enemyDocked" -> "Een vijand bereikte het moederschip."
            "tryAgainHint" -> "Schiet op vijanden, bescherm de vriendelijke schepen en laat 3 vriendelijke schepen dokken."
            "retry" -> "Opnieuw"
            "nextMission" -> "Volgende missie"
            else -> key
        }
    }
}
