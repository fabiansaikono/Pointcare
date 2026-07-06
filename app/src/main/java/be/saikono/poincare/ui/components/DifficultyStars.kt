package be.saikono.poincare.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight

@Composable
fun DifficultyStars(stars: Int?) {
    val count = stars?.coerceIn(1, 3) ?: return
    Text(
        text = "★".repeat(count),
        color = Color(0xFFFFD54F),
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold
    )
}
