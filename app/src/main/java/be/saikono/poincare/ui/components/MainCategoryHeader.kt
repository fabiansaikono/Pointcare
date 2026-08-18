package be.saikono.poincare.ui.components

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.text.style.TextOverflow
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
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack, modifier = Modifier.size(40.dp)) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = mainCategoryHeaderText("back", locale),
                tint = StarWhite
            )
        }
        Spacer(Modifier.width(4.dp))
        Text(
            text = title,
            color = StarWhite,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        Spacer(Modifier.width(4.dp))
        onNextLesson?.let { action ->
            OutlinedButton(
                onClick = action,
                modifier = Modifier.height(32.dp),
                shape = RoundedCornerShape(999.dp),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp)
            ) {
                Text(
                    text = mainCategoryHeaderText("nextLesson", locale),
                    color = PlanetBlue,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        Text(
            text = "${progressPercent.coerceIn(0, 100)}%",
            color = StarWhite.copy(alpha = 0.72f),
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.End
        )
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
