package be.saikono.poincare.ui

fun localizedExerciseText(key: String, locale: String): String = when (locale) {
    "en", "en-US", "en_US", "en-GB", "en_GB" -> when (key) {
        "backToMap" -> "Back to map"
        "nextMission" -> "Next mission"
        "nextMissionReady" -> "Great! Tap for the next mission"
        "noMissions" -> "No missions found."
        "answer" -> "Answer"
        "check" -> "Check"
        "yourAnswer" -> "Your answer"
        "empty" -> "empty"
        "unknownType" -> "Unknown mission type"
        else -> key
    }
    "fr", "fr-BE", "fr_BE", "fr-FR", "fr_FR" -> when (key) {
        "backToMap" -> "Retour a la carte"
        "nextMission" -> "Mission suivante"
        "nextMissionReady" -> "Super ! Appuie pour la mission suivante"
        "noMissions" -> "Aucune mission trouvee."
        "answer" -> "Reponse"
        "check" -> "Verifier"
        "yourAnswer" -> "Ta reponse"
        "empty" -> "vide"
        "unknownType" -> "Type de mission inconnu"
        else -> key
    }
    else -> when (key) {
        "backToMap" -> "Terug naar Kaart"
        "nextMission" -> "Volgende Missie"
        "nextMissionReady" -> "Super! Tik hier voor de volgende missie"
        "noMissions" -> "Geen missies gevonden."
        "answer" -> "Antwoord"
        "check" -> "Controleer"
        "yourAnswer" -> "Jouw antwoord"
        "empty" -> "leeg"
        "unknownType" -> "Onbekend missietype"
        else -> key
    }
}
