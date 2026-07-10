package be.saikono.poincare.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import be.saikono.poincare.ui.theme.CockpitGray
import be.saikono.poincare.ui.theme.DeepSpaceBlue
import be.saikono.poincare.ui.theme.PlanetGold
import be.saikono.poincare.ui.theme.StarWhite

// POINCARE_REPAIR_V30: compact-number-pad
@Composable
fun CustomNumberPad(
    onNumberClick: (String) -> Unit,
    onDeleteClick: () -> Unit,
    onConfirmClick: () -> Unit,
    modifier: Modifier = Modifier,
    hapticEnabled: Boolean = true,
    compact: Boolean = false
) {
    val haptic = LocalHapticFeedback.current

    Column(
        modifier = modifier
            .background(CockpitGray, shape = RoundedCornerShape(24.dp))
            .padding(if (compact) 10.dp else 16.dp)
            .semantics { contentDescription = "Getallen toetsenbord" },
        verticalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 12.dp)
    ) {
        val rows = listOf(
            listOf("1", "2", "3"),
            listOf("4", "5", "6"),
            listOf("7", "8", "9"),
            listOf("DEL", "0", "OK")
        )

        rows.forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 12.dp)
            ) {
                row.forEach { item ->
                    NumberButton(
                        text = item,
                        onClick = {
                            if (hapticEnabled) {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            }
                            when (item) {
                                "DEL" -> onDeleteClick()
                                "OK" -> onConfirmClick()
                                else -> onNumberClick(item)
                            }
                        },
                        modifier = Modifier.weight(1f),
                        compact = compact
                    )
                }
            }
        }
    }
}

@Composable
fun NumberButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false
) {
    val isAction = text == "DEL" || text == "OK"
    val backgroundColor = if (isAction && text == "OK") PlanetGold else Color.White.copy(alpha = 0.1f)
    val contentColor = if (isAction && text == "OK") DeepSpaceBlue else StarWhite

    Box(
        modifier = modifier
            .heightIn(min = if (compact) 44.dp else 60.dp)
            .aspectRatio(if (compact) 1.9f else 1.5f)
            .clip(RoundedCornerShape(16.dp))
            .background(backgroundColor)
            .clickable(onClickLabel = when(text) {
                "DEL" -> "Verwijder laatste cijfer"
                "OK" -> "Bevestig antwoord"
                else -> "Voer cijfer $text in"
            }) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        when (text) {
            "DEL" -> Icon(Icons.Default.Delete, contentDescription = "Wissen", tint = contentColor)
            "OK" -> Icon(Icons.Default.Check, contentDescription = "Bevestigen", tint = contentColor)
            else -> Text(
                text = text,
                style = MaterialTheme.typography.headlineMedium,
                color = contentColor
            )
        }
    }
}
