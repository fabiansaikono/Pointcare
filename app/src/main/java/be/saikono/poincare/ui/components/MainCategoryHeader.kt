package be.saikono.poincare.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import be.saikono.poincare.ui.theme.PlanetBlue
import be.saikono.poincare.ui.theme.StarWhite

@Composable
fun MainCategoryHeader(
    title: String,
    progressPercent: Int,
    locale: String,
    onBack: () -> Unit,
    onNextLesson: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = mainCategoryHeaderText("back", locale),
                    tint = StarWhite
                )
            }
            Spacer(Modifier.width(8.dp))
            Text(
                text = title,
                color = StarWhite,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(end = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End),
            verticalAlignment = Alignment.CenterVertically
        ) {
            onNextLesson?.let { action ->
                OutlinedButton(
                    onClick = action,
                    shape = RoundedCornerShape(999.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = mainCategoryHeaderText("nextLesson", locale),
                        color = PlanetBlue,
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
            Text(
                text = "${progressPercent.coerceIn(0, 100)}%",
                color = StarWhite.copy(alpha = 0.72f),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.End
            )
        }
    }
}

internal fun mainCategoryHeaderText(key: String, locale: String): String = when (
    locale.substringBefore('-').substringBefore('_').lowercase()
) {
    "fr" -> when (key) {
        "back" -> "Retour"
        "nextLesson" -> "Leçon suivante"
        else -> key
    }
    "en" -> when (key) {
        "back" -> "Back"
        "nextLesson" -> "Next lesson"
        else -> key
    }
    else -> when (key) {
        "back" -> "Terug"
        "nextLesson" -> "Volgende les"
        else -> key
    }
}
