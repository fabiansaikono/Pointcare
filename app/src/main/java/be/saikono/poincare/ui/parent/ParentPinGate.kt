package be.saikono.poincare.ui.parent

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import be.saikono.poincare.ui.components.CustomNumberPad
import be.saikono.poincare.ui.components.systemNavigationMargin
import be.saikono.poincare.ui.theme.DeepSpaceBlue
import be.saikono.poincare.ui.theme.PlanetGold
import be.saikono.poincare.ui.theme.StarWhite

@Composable
fun ParentPinGate(
    correctPin: String,
    onPinCorrect: () -> Unit,
    onCancel: () -> Unit
) {
    var enteredPin by remember { mutableStateOf("") }
    var isError by remember { mutableStateOf(false) }

    fun confirmPin() {
        if (enteredPin == correctPin) {
            isError = false
            onPinCorrect()
        } else {
            isError = true
            enteredPin = ""
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DeepSpaceBlue.copy(alpha = 0.95f))
            .systemNavigationMargin()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(32.dp)
        ) {
            Text(
                text = "Alleen voor ouders\nVoer je PIN in",
                style = MaterialTheme.typography.headlineSmall,
                color = StarWhite,
                textAlign = TextAlign.Center
            )

            if (isError) {
                Text(
                    text = "Deze pincode klopt niet.",
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center
                )
            }

            // PIN Display
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                repeat(4) { index ->
                    val char = enteredPin.getOrNull(index)
                    Text(
                        text = if (char != null) "●" else "○",
                        style = MaterialTheme.typography.displayMedium,
                        color = PlanetGold
                    )
                }
            }

            CustomNumberPad(
                onNumberClick = { 
                    if (enteredPin.length < 4) {
                        enteredPin += it
                        isError = false
                    }
                    if (enteredPin.length == 4) confirmPin()
                },
                onDeleteClick = {
                    if (enteredPin.isNotEmpty()) enteredPin = enteredPin.dropLast(1)
                    isError = false
                },
                onConfirmClick = { confirmPin() },
                modifier = Modifier.fillMaxWidth()
            )

            TextButton(onClick = onCancel) {
                Text("Annuleren", color = StarWhite.copy(alpha = 0.6f))
            }
        }
    }
}
