package be.saikono.poincare.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics

/** Compact clipboard/checklist glyph used for homework and teacher tasks. */
@Composable
fun HomeworkTasksGlyph(
    contentDescription: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier.semantics { this.contentDescription = contentDescription }) {
        val unit = size.minDimension / 24f
        val strokeWidth = 1.7f * unit
        val stroke = Stroke(
            width = strokeWidth,
            cap = StrokeCap.Round,
            join = StrokeJoin.Round
        )
        drawRoundRect(
            color = color,
            topLeft = Offset(4.5f * unit, 3.5f * unit),
            size = androidx.compose.ui.geometry.Size(15f * unit, 17f * unit),
            cornerRadius = CornerRadius(2.2f * unit),
            style = stroke
        )
        drawRoundRect(
            color = color,
            topLeft = Offset(8f * unit, 2f * unit),
            size = androidx.compose.ui.geometry.Size(8f * unit, 4f * unit),
            cornerRadius = CornerRadius(1.5f * unit),
            style = stroke
        )
        listOf(10f, 14f, 18f).forEach { y ->
            val check = Path().apply {
                moveTo(7.3f * unit, (y - 0.2f) * unit)
                lineTo(8.5f * unit, (y + 1f) * unit)
                lineTo(10.4f * unit, (y - 1.2f) * unit)
            }
            drawPath(check, color = color, style = stroke)
            drawLine(
                color = color,
                start = Offset(12.5f * unit, y * unit),
                end = Offset(17f * unit, y * unit),
                strokeWidth = strokeWidth,
                cap = StrokeCap.Round
            )
        }
    }
}
