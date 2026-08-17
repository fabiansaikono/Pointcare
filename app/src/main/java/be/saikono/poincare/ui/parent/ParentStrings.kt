package be.saikono.poincare.ui.parent

import androidx.compose.runtime.Composable
import be.saikono.poincare.model.LocalAppLanguage

// POINCARE_REPAIR_V29: parent-localization
@Composable
fun parentText(key: String): String = parentTextFor(key, LocalAppLanguage.current)

@Composable
fun parentDeleteConfirmation(name: String): String = when (parentLanguage(LocalAppLanguage.current)) {
    "fr" -> "Voulez-vous vraiment supprimer le compte de $name ? Cette action est irréversible."
    "en" -> "Are you sure you want to delete $name's account? This cannot be undone."
    else -> "Weet je zeker dat je het account van $name wilt verwijderen? Dit kan niet ongedaan worden gemaakt."
}

@Composable
fun parentSubcategoryText(id: String, fallback: String): String {
    val language = parentLanguage(LocalAppLanguage.current)
    if (id == "review") {
        return when (language) {
            "en" -> "Review"
            "fr" -> "Réviser"
            else -> "Herhalen"
        }
    }
    val translated = when (language) {
        "en" -> mapOf(
            "addition" to "Addition", "subtraction" to "Subtraction", "multiplication" to "Multiplication",
            "division" to "Division", "mixed_arithmetic" to "Mixed operations", "number_sequences" to "Number sequences",
            "fractions" to "Fractions", "money" to "Money", "time" to "Time", "measurement" to "Measurement",
            "compare_size" to "Bigger and smaller", "compare_weight" to "Heavier and lighter", "compare_height" to "Higher and lower",
            "compare_length" to "Longer and shorter", "compare_distance" to "Farther and closer",
            "geometry_shapes" to "Shapes and geometry", "reading" to "Reading", "listening" to "Listening",
            "writing" to "Writing", "spelling" to "Spelling", "sentence_order" to "Sentence order", "grammar" to "Grammar",
            "question_words" to "Question words",
            "space" to "Space", "nature" to "Nature", "geography" to "Geography", "science" to "Science",
            "history" to "History", "everyday_life" to "Everyday life", "sudoku" to "Sudoku",
            "math-crosswords" to "Math crossword", "word-seeker" to "Word seeker", "mothership-dock" to "Mothership dock",
            "galaxycon" to "Galaxycon", "calculator-puzzle" to "Calculator puzzle", "constellations" to "Constellations",
            "orbit-builder" to "Orbit Builder"
        )[id]
        "fr" -> mapOf(
            "addition" to "Addition", "subtraction" to "Soustraction", "multiplication" to "Multiplication",
            "division" to "Division", "mixed_arithmetic" to "Opérations mixtes", "number_sequences" to "Suites de nombres",
            "fractions" to "Fractions", "money" to "Argent", "time" to "Heure", "measurement" to "Mesures",
            "compare_size" to "Plus grand et plus petit", "compare_weight" to "Plus lourd et plus léger", "compare_height" to "Plus haut et plus bas",
            "compare_length" to "Plus long et plus court", "compare_distance" to "Plus loin et plus près",
            "geometry_shapes" to "Formes et géométrie", "reading" to "Lecture", "listening" to "Écoute",
            "writing" to "Écriture", "spelling" to "Orthographe", "sentence_order" to "Ordre des phrases", "grammar" to "Grammaire",
            "question_words" to "Mots interrogatifs",
            "space" to "Espace", "nature" to "Nature", "geography" to "Géographie", "science" to "Science",
            "history" to "Histoire", "everyday_life" to "Vie quotidienne", "sudoku" to "Sudoku",
            "math-crosswords" to "Mots croisés de calcul", "word-seeker" to "Cherche-mots", "mothership-dock" to "Vaisseau mère",
            "galaxycon" to "Galaxycon", "calculator-puzzle" to "Puzzle calculatrice", "constellations" to "Constellations",
            "orbit-builder" to "Constructeur d’orbite"
        )[id]
        else -> null
    }
    return translated ?: fallback
}

private fun parentLanguage(locale: String): String = when (locale.substringBefore('-').substringBefore('_').lowercase()) {
    "fr" -> "fr"
    "en" -> "en"
    else -> "nl"
}

private fun parentTextFor(key: String, locale: String): String {
    val values = when (parentLanguage(locale)) {
        "en" -> mapOf(
            "dashboard" to "Parent Dashboard", "deleteProfileTitle" to "Delete profile", "delete" to "Delete", "cancel" to "Cancel",
            "save" to "Save", "discardChanges" to "Discard changes", "unsavedChangesTitle" to "Unsaved changes",
            "unsavedChangesMessage" to "Do you want to save your changes before leaving?",
            "confirmParentPin" to "Enter the parent PIN again", "incorrectPin" to "Incorrect PIN",
            "profileSettings" to "Profile settings", "astronautName" to "Astronaut name", "birthYear" to "Year of birth",
            "teacherId" to "Teacher ID", "teacherIdDescription" to "ID provided by the teacher",
            "educationLevels" to "Grade per category", "reading" to "Reading & listening", "writing" to "Spelling & grammar", "math" to "Mathematics",
            "geometry" to "Measurement & geometry", "worldKnowledge" to "World knowledge", "puzzles" to "Puzzles",
            "showMixedExercises" to "Show mixed extra exercises", "mixedExerciseHint" to "These exercises can award points, but do not count towards mission progress.",
            "giftedMode" to "Hard Mode", "hardModeDescription" to "Generated exercises are one level harder", "deleteAccount" to "Delete account", "categories" to "Categories",
            "communication" to "Reading & listening", "numbers" to "Numbers", "languageSettings" to "Language settings", "language" to "Language",
            "appSettings" to "App settings", "animations" to "Enable animations", "reducedMotion" to "Reduced motion",
            "lessonSettings" to "Lesson settings", "masteryThreshold" to "Minimum test score",
            "masteryThresholdDescription" to "Choose the score needed to pass a test. The default is 75%.",
            "generalMasteryThreshold" to "General minimum test score", "categoryThresholds" to "Per main category",
            "differentThreshold" to "Use a different test threshold", "usesGeneralThreshold" to "Uses the general threshold",
            "soundEffects" to "Sound effects", "soundVolume" to "Sound-effects volume", "voiceVolume" to "Voice volume",
            "haptics" to "Haptic feedback", "systemStatus" to "System status", "progressStorage" to "Progress", "localOnly" to "Stored locally", "localStorageDescription" to "Progress is stored securely on this device",
            "gradeSelectionHint" to "Select one or more grades. At least one grade must remain selected.",
            "chooseGrade" to "Choose a grade", "grade" to "Grade", "completedShort" to "completed",
            "createProfileTitle" to "Start a new mission", "startMission" to "Start mission"
        )
        "fr" -> mapOf(
            "dashboard" to "Tableau de bord parent", "deleteProfileTitle" to "Supprimer le profil", "delete" to "Supprimer", "cancel" to "Annuler",
            "save" to "Enregistrer", "discardChanges" to "Annuler les modifications", "unsavedChangesTitle" to "Modifications non enregistrées",
            "unsavedChangesMessage" to "Voulez-vous enregistrer vos modifications avant de quitter ?",
            "confirmParentPin" to "Saisissez à nouveau le code PIN parent", "incorrectPin" to "Code PIN incorrect",
            "profileSettings" to "Paramètres du profil", "astronautName" to "Nom de l’astronaute", "birthYear" to "Année de naissance",
            "teacherId" to "Identifiant de l’enseignant", "teacherIdDescription" to "Identifiant fourni par l’enseignant",
            "educationLevels" to "Année par catégorie", "reading" to "Lecture et écoute", "writing" to "Orthographe et grammaire", "math" to "Mathématiques",
            "geometry" to "Mesures et géométrie", "worldKnowledge" to "Connaissance du monde", "puzzles" to "Puzzles",
            "showMixedExercises" to "Afficher les exercices supplémentaires mixtes", "mixedExerciseHint" to "Ces exercices peuvent donner des points, mais ne comptent pas dans la progression de la mission.",
            "giftedMode" to "Mode difficile", "hardModeDescription" to "Les exercices générés sont plus difficiles d’un niveau", "deleteAccount" to "Supprimer le compte", "categories" to "Catégories",
            "communication" to "Lecture et écoute", "numbers" to "Nombres", "languageSettings" to "Paramètres de langue", "language" to "Langue",
            "appSettings" to "Paramètres de l’application", "animations" to "Activer les animations", "reducedMotion" to "Mouvements réduits",
            "lessonSettings" to "Paramètres des leçons", "masteryThreshold" to "Score minimum au test",
            "masteryThresholdDescription" to "Choisissez le score requis pour réussir un test. La valeur par défaut est 75 %.",
            "generalMasteryThreshold" to "Score minimum général", "categoryThresholds" to "Par catégorie principale",
            "differentThreshold" to "Utiliser un seuil différent", "usesGeneralThreshold" to "Utilise le seuil général",
            "soundEffects" to "Effets sonores", "soundVolume" to "Volume des effets sonores", "voiceVolume" to "Volume de la voix",
            "haptics" to "Retour haptique", "systemStatus" to "État du système", "progressStorage" to "Progression", "localOnly" to "Stockée localement", "localStorageDescription" to "La progression est stockée en sécurité sur cet appareil",
            "gradeSelectionHint" to "Sélectionnez une ou plusieurs années. Au moins une année doit rester sélectionnée.",
            "chooseGrade" to "Choisis une année", "grade" to "Année", "completedShort" to "terminés",
            "createProfileTitle" to "Commencer une nouvelle mission", "startMission" to "Démarrer la mission"
        )
        else -> mapOf(
            "dashboard" to "Ouder Dashboard", "deleteProfileTitle" to "Profiel verwijderen", "delete" to "Verwijderen", "cancel" to "Annuleren",
            "save" to "Opslaan", "discardChanges" to "Wijzigingen annuleren", "unsavedChangesTitle" to "Niet-opgeslagen wijzigingen",
            "unsavedChangesMessage" to "Wil je de wijzigingen opslaan voordat je teruggaat?",
            "confirmParentPin" to "Voer de ouderpincode opnieuw in", "incorrectPin" to "Onjuiste pincode",
            "profileSettings" to "Profielinstellingen", "astronautName" to "Naam van de astronaut", "birthYear" to "Geboortejaar",
            "teacherId" to "ID van de leerkracht", "teacherIdDescription" to "ID die je van de leerkracht kreeg",
            "educationLevels" to "Leerjaar per categorie", "reading" to "Lezen & luisteren", "writing" to "Spelling & grammatica", "math" to "Wiskunde",
            "geometry" to "Meten & meetkunde", "worldKnowledge" to "Wereldkennis", "puzzles" to "Puzzels",
            "showMixedExercises" to "Gemengde extra oefeningen tonen", "mixedExerciseHint" to "Deze oefeningen kunnen punten geven, maar tellen niet mee voor voortgang per missie.",
            "giftedMode" to "Moeilijke modus", "hardModeDescription" to "Gegenereerde oefeningen zijn één niveau moeilijker", "deleteAccount" to "Account verwijderen", "categories" to "Categorieën",
            "communication" to "Lezen & luisteren", "numbers" to "Getallen", "languageSettings" to "Taalinstellingen", "language" to "Taal",
            "appSettings" to "App-instellingen", "animations" to "Animaties inschakelen", "reducedMotion" to "Minder beweging",
            "lessonSettings" to "Lesinstellingen", "masteryThreshold" to "Minimumscore voor toetsen",
            "masteryThresholdDescription" to "Kies de score die nodig is om een toets te behalen. Standaard is dit 75%.",
            "generalMasteryThreshold" to "Algemene minimumscore voor toetsen", "categoryThresholds" to "Per hoofdcategorie",
            "differentThreshold" to "Afwijkende toetsdrempel", "usesGeneralThreshold" to "Gebruikt de algemene toetsdrempel",
            "soundEffects" to "Geluidseffecten", "soundVolume" to "Volume geluidseffecten", "voiceVolume" to "Volume stem",
            "haptics" to "Haptische feedback", "systemStatus" to "Systeemstatus", "progressStorage" to "Voortgang", "localOnly" to "Lokaal bewaard", "localStorageDescription" to "De voortgang wordt veilig op dit toestel bewaard",
            "gradeSelectionHint" to "Selecteer één of meer leerjaren. Minstens één leerjaar moet geselecteerd blijven.",
            "chooseGrade" to "Kies een leerjaar", "grade" to "Leerjaar", "completedShort" to "voltooid",
            "createProfileTitle" to "Nieuwe missie starten", "startMission" to "Start missie"
        )
    }
    return values[key] ?: key
}
