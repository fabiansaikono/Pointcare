package be.saikono.poincare.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import be.saikono.poincare.ui.theme.CockpitGray
import be.saikono.poincare.ui.theme.DeepSpaceBlue
import be.saikono.poincare.ui.theme.PlanetBlue
import be.saikono.poincare.ui.theme.PlanetGold
import be.saikono.poincare.ui.theme.PlanetRed
import be.saikono.poincare.ui.theme.StarWhite

private data class KeyboardKey(
    val label: String,
    val value: String = label.lowercase(),
    val action: KeyboardAction = KeyboardAction.CHARACTER,
    val span: Int = 1
)

private enum class KeyboardAction { CHARACTER, BACKSPACE, SUBMIT }

@Composable
fun AzertyBeKeyboard(
    locale: String,
    onCharacter: (String) -> Unit,
    onBackspace: () -> Unit,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    enabled: Boolean = true,
    wrongKey: String? = null,
    hintKey: String? = null,
    hintSequence: Long = 0L,
    revealedKeys: Set<String> = emptySet()
) {
    val haptic = LocalHapticFeedback.current
    val language = locale.substringBefore('-').substringBefore('_').lowercase()
    val hintAlpha = remember { Animatable(0f) }
    LaunchedEffect(hintSequence) {
        if (hintKey != null) {
            hintAlpha.snapTo(0f)
            hintAlpha.animateTo(0.30f, tween(500))
            hintAlpha.animateTo(0f, tween(500))
        }
    }
    val spaceLabel = when (language) { "fr" -> "espace"; "en" -> "space"; else -> "spatie" }
    val rows = listOf(
        "AZERTYUIOP".map { KeyboardKey(it.toString()) },
        "QSDFGHJKLM".map { KeyboardKey(it.toString()) },
        "WXCVBN".map { KeyboardKey(it.toString()) } + listOf(
            KeyboardKey("-"),
            KeyboardKey("⌫", action = KeyboardAction.BACKSPACE, span = 3)
        ),
        listOf(
            KeyboardKey("é"),
            KeyboardKey("è"),
            KeyboardKey("à"),
            KeyboardKey("ô"),
            KeyboardKey(spaceLabel, value = " ", span = 3),
            KeyboardKey("✓", action = KeyboardAction.SUBMIT, span = 3)
        )
    )

    Column(
        modifier = modifier
            .background(CockpitGray, RoundedCornerShape(20.dp))
            .padding(if (compact) 7.dp else 10.dp)
            .semantics { contentDescription = "Belgisch AZERTY-toetsenbord" },
        verticalArrangement = Arrangement.spacedBy(if (compact) 5.dp else 7.dp)
    ) {
        val keySpacing = if (compact) 4.dp else 5.dp
        rows.forEach { row ->
            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                val cellWidth = (maxWidth - keySpacing * 9f) / 10f
                Row(horizontalArrangement = Arrangement.spacedBy(keySpacing)) {
                    row.forEach { key ->
                        val isSubmit = key.action == KeyboardAction.SUBMIT
                        val keyDescription = when (key.action) {
                            KeyboardAction.BACKSPACE -> when (language) {
                                "fr" -> "Effacer la dernière lettre"
                                "en" -> "Delete last letter"
                                else -> "Wis de laatste letter"
                            }
                            KeyboardAction.SUBMIT -> when (language) {
                                "fr" -> "Valider la réponse"
                                "en" -> "Check answer"
                                else -> "Controleer antwoord"
                            }
                            KeyboardAction.CHARACTER -> if (key.value == " ") spaceLabel else key.label
                        }
                        Text(
                            text = key.label,
                            color = if (isSubmit) DeepSpaceBlue else StarWhite,
                            style = if (key.label.length == 1) MaterialTheme.typography.titleMedium else MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            modifier = Modifier
                                .width(cellWidth * key.span.toFloat() + keySpacing * (key.span - 1).toFloat())
                                .height(if (compact) 39.dp else 47.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    when {
                                        !enabled -> Color.Gray.copy(alpha = .20f)
                                        key.action == KeyboardAction.CHARACTER && key.value.equals(wrongKey, ignoreCase = true) -> PlanetRed
                                        key.action == KeyboardAction.CHARACTER && key.value.equals(hintKey, ignoreCase = true) -> PlanetBlue.copy(alpha = hintAlpha.value)
                                        key.action == KeyboardAction.CHARACTER && revealedKeys.any { it.equals(key.value, ignoreCase = true) } -> PlanetBlue.copy(alpha = .20f)
                                        isSubmit -> PlanetGold
                                        else -> Color.White.copy(alpha = .10f)
                                    }
                                )
                                .clickable(enabled = enabled) {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    when (key.action) {
                                        KeyboardAction.CHARACTER -> onCharacter(key.value)
                                        KeyboardAction.BACKSPACE -> onBackspace()
                                        KeyboardAction.SUBMIT -> onSubmit()
                                    }
                                }
                                .semantics { contentDescription = keyDescription }
                                .padding(top = if (compact) 9.dp else 12.dp)
                        )
                    }
                }
            }
        }
    }
}
