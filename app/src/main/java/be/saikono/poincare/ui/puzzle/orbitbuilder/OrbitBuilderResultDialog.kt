package be.saikono.poincare.ui.puzzle.orbitbuilder

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import be.saikono.poincare.R
import be.saikono.poincare.ui.theme.DeepSpaceBlue
import be.saikono.poincare.ui.theme.PlanetGold
import be.saikono.poincare.ui.theme.PlanetRed
import be.saikono.poincare.ui.theme.StarWhite

@Composable
fun OrbitBuilderResultDialog(
    state: OrbitBuilderUiState,
    onRetry: () -> Unit,
    onNextMission: () -> Unit,
    onReset: () -> Unit
) {
    val result = state.simulationResult ?: return
    val isSuccess = state.phase == OrbitBuilderPhase.SUCCESS
    val title = if (isSuccess) {
        stringResource(R.string.orbit_builder_mission_complete)
    } else {
        stringResource(R.string.orbit_builder_try_again)
    }
    val reason = if (isSuccess) {
        stringResource(R.string.orbit_builder_success_with_stars, result.stars)
    } else {
        failureMessage(result.failureReason)
    }

    AlertDialog(
        onDismissRequest = { if (!isSuccess) onRetry() },
        title = { Text(title) },
        text = {
            Text(
                text = "$reason\n\n" +
                    stringResource(R.string.orbit_builder_final_value, formatOrbitNumber(result.finalValue))
            )
        },
        confirmButton = {
            Button(
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                onClick = { if (isSuccess) onNextMission() else onRetry() },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isSuccess) PlanetGold else PlanetRed,
                    contentColor = if (isSuccess) DeepSpaceBlue else StarWhite
                )
            ) {
                Text(
                    text = if (isSuccess) {
                        stringResource(R.string.orbit_builder_next_mission)
                    } else {
                        stringResource(R.string.orbit_builder_try_again)
                    }
                )
            }
        },
        containerColor = DeepSpaceBlue,
        titleContentColor = StarWhite,
        textContentColor = StarWhite
    )
}

@Composable
private fun failureMessage(reason: OrbitFailureReason?): String = when (reason) {
    OrbitFailureReason.ASTEROID -> stringResource(R.string.orbit_builder_fail_asteroid)
    OrbitFailureReason.LEFT_MAP -> stringResource(R.string.orbit_builder_fail_left_map)
    OrbitFailureReason.WRONG_VALUE -> stringResource(R.string.orbit_builder_fail_wrong_value)
    OrbitFailureReason.LOOP -> stringResource(R.string.orbit_builder_fail_loop)
    OrbitFailureReason.INVALID_DIVISION -> stringResource(R.string.orbit_builder_fail_invalid_division)
    OrbitFailureReason.NO_PATH, null -> stringResource(R.string.orbit_builder_fail_no_path)
}
