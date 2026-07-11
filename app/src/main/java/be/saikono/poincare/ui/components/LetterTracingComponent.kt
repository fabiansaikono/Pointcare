package be.saikono.poincare.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import be.saikono.poincare.ui.theme.PlanetBlue
import be.saikono.poincare.ui.theme.PlanetGold
import be.saikono.poincare.ui.theme.PlanetRed
import be.saikono.poincare.ui.theme.StarWhite
import kotlinx.coroutines.delay
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin

@Composable
fun SchoolLetterPreview(
    letter: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    val glyph = remember(letter) { SchoolLetterGlyphs.forLetter(letter) }
    Canvas(modifier = modifier) {
        glyph.strokes.forEach { normalizedStroke ->
            val points = normalizedStroke.map { point ->
                Offset(point.x * size.width, point.y * size.height)
            }
            drawPath(
                path = smoothPath(points),
                color = color,
                style = Stroke(
                    width = 2.6.dp.toPx(),
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round
                )
            )
        }
    }
}

@Composable
fun LetterTracingComponent(
    targetLetter: String,
    onPathUpdate: (List<Offset>) -> Unit,
    onComplete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val glyph = remember(targetLetter) { SchoolLetterGlyphs.forLetter(targetLetter) }
    var canvasSize by remember(targetLetter) { mutableStateOf(IntSize.Zero) }
    var currentStrokeIndex by remember(targetLetter) { mutableIntStateOf(0) }
    var currentPoints by remember(targetLetter) { mutableStateOf(emptyList<Offset>()) }
    var rejectedPoints by remember(targetLetter) { mutableStateOf(emptyList<Offset>()) }
    var rejectionVersion by remember(targetLetter) { mutableIntStateOf(0) }

    LaunchedEffect(rejectionVersion) {
        if (rejectionVersion > 0) {
            delay(650)
            rejectedPoints = emptyList()
        }
    }

    fun finishStroke() {
        val stroke = glyph.strokes.getOrNull(currentStrokeIndex) ?: return
        val accepted = traceMatches(
            userPoints = currentPoints,
            targetPoints = stroke.toCanvas(canvasSize),
            canvasSize = canvasSize
        )
        if (accepted) {
            currentPoints = emptyList()
            onPathUpdate(emptyList())
            if (currentStrokeIndex == glyph.strokes.lastIndex) {
                onComplete()
            } else {
                currentStrokeIndex += 1
            }
        } else {
            rejectedPoints = currentPoints
            currentPoints = emptyList()
            onPathUpdate(emptyList())
            rejectionVersion += 1
        }
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(24.dp))
            .background(Color.White.copy(alpha = 0.06f))
            .onSizeChanged { canvasSize = it }
            .pointerInput(targetLetter, currentStrokeIndex, canvasSize) {
                detectDragGestures(
                    onDragStart = { offset ->
                        rejectedPoints = emptyList()
                        currentPoints = listOf(offset)
                        onPathUpdate(currentPoints)
                    },
                    onDrag = { change, _ ->
                        change.consume()
                        currentPoints = currentPoints + change.position
                        onPathUpdate(currentPoints)
                    },
                    onDragCancel = {
                        currentPoints = emptyList()
                        onPathUpdate(emptyList())
                    },
                    onDragEnd = ::finishStroke
                )
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawSchoolRuling()

            glyph.strokes.forEachIndexed { index, normalizedStroke ->
                val strokePoints = normalizedStroke.toCanvas(canvasSize)
                val completed = index < currentStrokeIndex
                drawPath(
                    path = smoothPath(strokePoints),
                    color = if (completed) PlanetGold.copy(alpha = .82f) else StarWhite.copy(alpha = .62f),
                    style = Stroke(
                        width = if (completed) 8.dp.toPx() else 5.dp.toPx(),
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round,
                        pathEffect = if (completed) null else PathEffect.dashPathEffect(
                            floatArrayOf(3.dp.toPx(), 9.dp.toPx())
                        )
                    )
                )

                if (index == currentStrokeIndex && strokePoints.isNotEmpty()) {
                    drawCircle(
                        color = PlanetBlue,
                        radius = 10.dp.toPx(),
                        center = strokePoints.first()
                    )
                    strokePoints.getOrNull(1)?.let { second ->
                        drawDirectionArrow(strokePoints.first(), second, PlanetBlue)
                    }
                }
            }

            if (currentPoints.size > 1) {
                drawPath(
                    path = smoothPath(currentPoints),
                    color = PlanetBlue,
                    style = Stroke(8.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                )
            }
            if (rejectedPoints.size > 1) {
                drawPath(
                    path = smoothPath(rejectedPoints),
                    color = PlanetRed,
                    style = Stroke(9.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                )
            }
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawSchoolRuling() {
    val yPositions = listOf(.12f, .43f, .76f, .94f)
    val lineColors = listOf(
        PlanetRed.copy(alpha = .38f),
        PlanetBlue.copy(alpha = .42f),
        Color(0xFF7DA34B).copy(alpha = .52f),
        Color(0xFF7DA34B).copy(alpha = .30f)
    )
    yPositions.forEachIndexed { index, relativeY ->
        drawLine(
            color = lineColors[index],
            start = Offset(0f, size.height * relativeY),
            end = Offset(size.width, size.height * relativeY),
            strokeWidth = if (index == 2) 2.dp.toPx() else 1.5.dp.toPx()
        )
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawDirectionArrow(
    start: Offset,
    next: Offset,
    color: Color
) {
    val angle = atan2(next.y - start.y, next.x - start.x)
    val arrowStart = start + Offset(cos(angle), sin(angle)) * 14.dp.toPx()
    val arrowEnd = arrowStart + Offset(cos(angle), sin(angle)) * 22.dp.toPx()
    drawLine(color, arrowStart, arrowEnd, 4.dp.toPx(), StrokeCap.Round)
    val wing = 8.dp.toPx()
    drawLine(
        color,
        arrowEnd,
        arrowEnd + Offset(cos(angle + 2.55f), sin(angle + 2.55f)) * wing,
        4.dp.toPx(),
        StrokeCap.Round
    )
    drawLine(
        color,
        arrowEnd,
        arrowEnd + Offset(cos(angle - 2.55f), sin(angle - 2.55f)) * wing,
        4.dp.toPx(),
        StrokeCap.Round
    )
}

private fun List<Offset>.toCanvas(canvasSize: IntSize): List<Offset> = map { point ->
    Offset(point.x * canvasSize.width, point.y * canvasSize.height)
}

private fun smoothPath(points: List<Offset>): Path = Path().apply {
    if (points.isEmpty()) return@apply
    moveTo(points.first().x, points.first().y)
    if (points.size == 2) {
        lineTo(points.last().x, points.last().y)
        return@apply
    }
    for (index in 1 until points.lastIndex) {
        val point = points[index]
        val next = points[index + 1]
        val midpoint = Offset((point.x + next.x) / 2f, (point.y + next.y) / 2f)
        quadraticBezierTo(point.x, point.y, midpoint.x, midpoint.y)
    }
    lineTo(points.last().x, points.last().y)
}

private fun traceMatches(
    userPoints: List<Offset>,
    targetPoints: List<Offset>,
    canvasSize: IntSize
): Boolean {
    if (userPoints.isEmpty() || targetPoints.size < 2 || canvasSize == IntSize.Zero) return false
    val target = samplePolyline(targetPoints)
    val minimumDimension = min(canvasSize.width, canvasSize.height).toFloat()
    val tolerance = minimumDimension * .085f
    val targetLength = polylineLength(target)
    val isDotStroke = targetLength < minimumDimension * .05f

    if (userPoints.first().distanceTo(target.first()) > tolerance * 1.45f) return false
    if (!isDotStroke && userPoints.last().distanceTo(target.last()) > tolerance * 1.65f) return false

    val distances = userPoints.map { user -> target.minOf { user.distanceTo(it) } }
    val averageDistance = distances.average().toFloat()
    val offGuideRatio = distances.count { it > tolerance }.toFloat() / distances.size
    if (averageDistance > tolerance * .78f || offGuideRatio > .28f) return false

    if (isDotStroke) return true
    val userLength = polylineLength(userPoints)
    return userLength >= targetLength * .48f
}

private fun samplePolyline(points: List<Offset>, samplesPerSegment: Int = 12): List<Offset> = buildList {
    points.zipWithNext().forEachIndexed { segmentIndex, (start, end) ->
        for (sample in 0 until samplesPerSegment) {
            if (segmentIndex > 0 && sample == 0) continue
            val fraction = sample.toFloat() / samplesPerSegment
            add(start + (end - start) * fraction)
        }
    }
    add(points.last())
}

private fun polylineLength(points: List<Offset>): Float =
    points.zipWithNext().sumOf { (first, second) -> first.distanceTo(second).toDouble() }.toFloat()

private fun Offset.distanceTo(other: Offset): Float = hypot(x - other.x, y - other.y)
