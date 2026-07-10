package be.saikono.poincare.ui.parent

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import be.saikono.poincare.model.LocalAppLanguage
import be.saikono.poincare.ui.components.CustomNumberPad
import be.saikono.poincare.ui.components.systemNavigationMargin
import be.saikono.poincare.ui.theme.DeepSpaceBlue
import be.saikono.poincare.ui.theme.PlanetGold
import be.saikono.poincare.ui.theme.StarWhite

// POINCARE_REPAIR_V30: compact, non-clipping landscape parent PIN gate
@Composable
fun ParentPinGate(
    correctPin: String,
    onPinCorrect: () -> Unit,
    onCancel: () -> Unit
) {
    var enteredPin by remember { mutableStateOf("") }
    var isError by remember { mutableStateOf(false) }
    val appLanguage = LocalAppLanguage.current

    fun confirmPin() {
        if (enteredPin == correctPin) {
            isError = false
            onPinCorrect()
        } else {
            isError = true
            enteredPin = ""
        }
    }

    fun addDigit(digit: String) {
        if (enteredPin.length >= 4) return
        enteredPin += digit
        isError = false
        if (enteredPin.length == 4) confirmPin()
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(DeepSpaceBlue.copy(alpha = 0.97f))
            .systemNavigationMargin()
            .padding(horizontal = 18.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        val isLandscape = maxWidth > maxHeight
        val shortLandscape = isLandscape && maxHeight < 420.dp

        if (isLandscape) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 940.dp)
                    .heightIn(max = if (shortLandscape) 330.dp else 380.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(if (shortLandscape) 18.dp else 32.dp)
            ) {
                ParentPinHeader(
                    enteredPin = enteredPin,
                    isError = isError,
                    appLanguage = appLanguage,
                    compact = true,
                    onCancel = onCancel,
                    modifier = Modifier.weight(0.82f)
                )

                Box(
                    modifier = Modifier
                        .weight(1.18f)
                        .fillMaxHeight(),
                    contentAlignment = Alignment.Center
                ) {
                    CustomNumberPad(
                        onNumberClick = ::addDigit,
                        onDeleteClick = {
                            if (enteredPin.isNotEmpty()) enteredPin = enteredPin.dropLast(1)
                            isError = false
                        },
                        onConfirmClick = ::confirmPin,
                        modifier = Modifier
                            .fillMaxWidth()
                            .widthIn(max = 430.dp),
                        compact = true
                    )
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 480.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                ParentPinHeader(
                    enteredPin = enteredPin,
                    isError = isError,
                    appLanguage = appLanguage,
                    compact = false,
                    onCancel = onCancel
                )
                CustomNumberPad(
                    onNumberClick = ::addDigit,
                    onDeleteClick = {
                        if (enteredPin.isNotEmpty()) enteredPin = enteredPin.dropLast(1)
                        isError = false
                    },
                    onConfirmClick = ::confirmPin,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun ParentPinHeader(
    enteredPin: String,
    isError: Boolean,
    appLanguage: String,
    compact: Boolean,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 16.dp)
    ) {
        Text(
            text = parentPinText("title", appLanguage),
            style = MaterialTheme.typography.headlineSmall,
            color = StarWhite,
            textAlign = TextAlign.Center
        )

        if (isError) {
            Text(
                text = parentPinText("error", appLanguage),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(if (compact) 10.dp else 16.dp)) {
            repeat(4) { index ->
                Text(
                    text = if (enteredPin.getOrNull(index) != null) "●" else "○",
                    style = if (compact) MaterialTheme.typography.headlineLarge
                    else MaterialTheme.typography.displayMedium,
                    color = PlanetGold
                )
            }
        }

        TextButton(onClick = onCancel) {
            Text(parentPinText("cancel", appLanguage), color = StarWhite.copy(alpha = 0.72f))
        }
    }
}

private fun parentPinText(key: String, locale: String): String {
    val language = locale.substringBefore('-').substringBefore('_').lowercase()
    return when (language) {
        "fr" -> when (key) {
            "title" -> "Réservé aux parents\nSaisissez votre code PIN"
            "error" -> "Ce code PIN est incorrect."
            "cancel" -> "Annuler"
            else -> key
        }
        "en" -> when (key) {
            "title" -> "Parents only\nEnter your PIN"
            "error" -> "That PIN is incorrect."
            "cancel" -> "Cancel"
            else -> key
        }
        else -> when (key) {
            "title" -> "Alleen voor ouders\nVoer je pincode in"
            "error" -> "Deze pincode klopt niet."
            "cancel" -> "Annuleren"
            else -> key
        }
    }
}
