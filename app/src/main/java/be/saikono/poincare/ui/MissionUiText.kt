package be.saikono.poincare.ui

// POINCARE_REPAIR_V29: localized mission completion label
fun missionCompleteGoBackText(locale: String): String = when (locale.substringBefore('-').substringBefore('_').lowercase()) {
    "fr" -> "Mission terminée. Retour."
    "en" -> "Mission complete. Go back."
    else -> "Missie voltooid. Ga terug."
}
