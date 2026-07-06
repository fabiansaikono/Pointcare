package be.saikono.poincare.ui.components

import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import be.saikono.poincare.data.content.PoincareExercise
import be.saikono.poincare.data.icons.PoincareIcon
import be.saikono.poincare.data.icons.PoincareIconResolver

@Composable
fun PoincareIcon(
    icon: PoincareIcon?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color = Color.Unspecified
) {
    val context = LocalContext.current
    val resolver = remember { PoincareIconResolver.fromAssets(context) }
    Image(
        painter = painterResource(id = resolver.resolveDrawableRes(context, icon)),
        contentDescription = contentDescription,
        modifier = modifier
    )
}

@Composable
fun PoincareTopicIcon(topic: String, contentDescription: String?, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val resolver = remember { PoincareIconResolver.fromAssets(context) }
    Image(painterResource(resolver.resolveTopic(context, topic)), contentDescription, modifier)
}

@Composable
fun PoincareSkillIcon(skillRef: String, contentDescription: String?, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val resolver = remember { PoincareIconResolver.fromAssets(context) }
    Image(painterResource(resolver.resolveSkill(context, skillRef)), contentDescription, modifier)
}

@Composable
fun PoincareUiIcon(key: String, contentDescription: String?, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val resolver = remember { PoincareIconResolver.fromAssets(context) }
    Image(painterResource(resolver.resolveUi(context, key)), contentDescription, modifier)
}

@Composable
fun PoincareExerciseIcon(exercise: PoincareExercise, contentDescription: String?, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val resolver = remember { PoincareIconResolver.fromAssets(context) }
    Image(painterResource(resolver.resolveExercise(context, exercise)), contentDescription, modifier)
}