package be.saikono.poincare.ui.components

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.composed
import androidx.compose.ui.unit.dp

fun Modifier.systemNavigationMargin(): Modifier = composed {
    val orientation = LocalConfiguration.current.orientation
    val configuration = LocalConfiguration.current
    if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
        padding(end = (configuration.screenWidthDp * 0.05f).dp)
    } else {
        padding(bottom = (configuration.screenHeightDp * 0.05f).dp)
    }
}
