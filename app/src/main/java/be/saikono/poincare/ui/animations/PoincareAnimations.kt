package be.saikono.poincare.ui.animations

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp

@Composable
fun MissionTransition(
    visible: Boolean,
    reducedMotion: Boolean,
    content: @Composable () -> Unit
) {
    if (reducedMotion) {
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(animationSpec = tween(300)),
            exit = fadeOut(animationSpec = tween(300))
        ) {
            content()
        }
    } else {
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(animationSpec = tween(500)) + scaleIn(initialScale = 0.92f, animationSpec = tween(500)),
            exit = fadeOut(animationSpec = tween(300)) + scaleOut(targetScale = 0.92f, animationSpec = tween(300))
        ) {
            content()
        }
    }
}

@Composable
fun CorrectAnswerPulse(
    trigger: Any?,
    reducedMotion: Boolean,
    content: @Composable (Modifier) -> Unit
) {
    val scale = remember { Animatable(1f) }
    
    LaunchedEffect(trigger) {
        if (trigger != null && !reducedMotion) {
            scale.animateTo(
                targetValue = 1.05f,
                animationSpec = tween(200, easing = FastOutSlowInEasing)
            )
            scale.animateTo(
                targetValue = 1f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessLow
                )
            )
        }
    }

    content(Modifier.graphicsLayer(scaleX = scale.value, scaleY = scale.value))
}

@Composable
fun IncorrectAnswerShake(
    trigger: Any?,
    reducedMotion: Boolean,
    content: @Composable (Modifier) -> Unit
) {
    val offsetX = remember { Animatable(0f) }

    LaunchedEffect(trigger) {
        if (trigger != null && !reducedMotion) {
            repeat(3) {
                offsetX.animateTo(
                    targetValue = 10f,
                    animationSpec = tween(50, easing = LinearEasing)
                )
                offsetX.animateTo(
                    targetValue = -10f,
                    animationSpec = tween(50, easing = LinearEasing)
                )
            }
            offsetX.animateTo(
                targetValue = 0f,
                animationSpec = spring(stiffness = Spring.StiffnessHigh)
            )
        }
    }

    content(Modifier.offset(x = offsetX.value.dp))
}

@Composable
fun HintPanelSlide(
    visible: Boolean,
    reducedMotion: Boolean,
    content: @Composable () -> Unit
) {
    AnimatedVisibility(
        visible = visible,
        enter = if (reducedMotion) fadeIn() else slideInHorizontally { it } + fadeIn(),
        exit = if (reducedMotion) fadeOut() else slideOutHorizontally { it } + fadeOut()
    ) {
        content()
    }
}
