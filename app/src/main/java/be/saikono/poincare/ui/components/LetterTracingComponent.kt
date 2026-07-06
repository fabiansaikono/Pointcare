package be.saikono.poincare.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import be.saikono.poincare.ui.theme.PlanetGold
import be.saikono.poincare.ui.theme.StarWhite

@Composable
fun LetterTracingComponent(
    targetLetter: String,
    onPathUpdate: (List<Offset>) -> Unit,
    onComplete: () -> Unit,
    modifier: Modifier = Modifier
) {
    var points by remember { mutableStateOf(listOf<Offset>()) }

    Box(
        modifier = modifier
            .background(Color.White.copy(alpha = 0.05f))
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { offset ->
                        points = listOf(offset)
                        onPathUpdate(points)
                    },
                    onDrag = { change, dragAmount ->
                        points = points + change.position
                        onPathUpdate(points)
                    },
                    onDragEnd = {
                        onComplete()
                    }
                )
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            // Draw Guide Lines (Placeholder)
            val middle = size.height / 2
            drawLine(Color.Gray.copy(alpha = 0.3f), Offset(0f, middle - 100.dp.toPx()), Offset(size.width, middle - 100.dp.toPx()))
            drawLine(Color.Gray.copy(alpha = 0.3f), Offset(0f, middle), Offset(size.width, middle))
            drawLine(Color.Gray.copy(alpha = 0.3f), Offset(0f, middle + 100.dp.toPx()), Offset(size.width, middle + 100.dp.toPx()))

            // Draw Target Letter (Simplified placeholder)
            // In a full version, we'd draw an actual font or path here.
            
            // Draw User Path
            if (points.size > 1) {
                val path = Path().apply {
                    moveTo(points.first().x, points.first().y)
                    points.forEach { lineTo(it.x, it.y) }
                }
                drawPath(
                    path = path,
                    color = PlanetGold,
                    style = Stroke(
                        width = 8.dp.toPx(),
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round
                    )
                )
            }
        }
    }
}
