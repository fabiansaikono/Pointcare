package be.saikono.poincare.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import be.saikono.poincare.ui.theme.DeepSpaceBlue
import be.saikono.poincare.ui.theme.PlanetGold
import be.saikono.poincare.ui.theme.StarWhite

@Composable
fun GradeMultiSelector(
    label: String,
    selectedGrades: Set<Int>,
    onSelectedGradesChanged: (Set<Int>) -> Unit,
    supportingText: String? = null,
    modifier: Modifier = Modifier
) {
    val normalized = selectedGrades.filter { it in 1..6 }.toSet().ifEmpty { setOf(1) }
    Column(modifier = modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Text(text = label, style = MaterialTheme.typography.labelLarge, color = StarWhite)
        supportingText?.let {
            Spacer(modifier = Modifier.height(2.dp))
            Text(text = it, style = MaterialTheme.typography.bodySmall, color = StarWhite.copy(alpha = 0.65f))
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            (1..6).forEach { grade ->
                val selected = grade in normalized
                FilterChip(
                    selected = selected,
                    onClick = {
                        val updated = if (selected) {
                            if (normalized.size == 1) normalized else normalized - grade
                        } else {
                            normalized + grade
                        }
                        onSelectedGradesChanged(updated)
                    },
                    label = { Text(grade.toString()) },
                    shape = RoundedCornerShape(12.dp),
                    colors = FilterChipDefaults.filterChipColors(
                        labelColor = StarWhite,
                        selectedLabelColor = DeepSpaceBlue,
                        selectedContainerColor = PlanetGold
                    ),
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}
