package be.saikono.poincare.ui.components

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import be.saikono.poincare.ui.theme.PlanetBlue
import be.saikono.poincare.ui.theme.StarWhite
import kotlinx.coroutines.delay

@Composable
fun TapToTranslateText(
    text: String,
    translation: String,
    onTranslationUsed: () -> Unit,
    onClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var showTranslation by remember { mutableStateOf(false) }

    LaunchedEffect(showTranslation) {
        if (showTranslation) {
            delay(3000) // Show for 3 seconds
            showTranslation = false
        }
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (showTranslation) PlanetBlue.copy(alpha = 0.2f) else Color.White.copy(alpha = 0.05f))
            .clickable { 
                onClick()
                if (!showTranslation) {
                    showTranslation = true
                    onTranslationUsed()
                }
            }
            .padding(horizontal = 24.dp, vertical = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        AnimatedContent(
            targetState = showTranslation,
            transitionSpec = {
                fadeIn() togetherWith fadeOut()
            },
            label = "Translate"
        ) { isShowing ->
            if (isShowing) {
                Text(
                    text = translation,
                    style = MaterialTheme.typography.headlineSmall,
                    color = PlanetBlue,
                    fontWeight = FontWeight.Bold
                )
            } else {
                Text(
                    text = text,
                    style = MaterialTheme.typography.headlineSmall,
                    color = StarWhite
                )
            }
        }
    }
}
