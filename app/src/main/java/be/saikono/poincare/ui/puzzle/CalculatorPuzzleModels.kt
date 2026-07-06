package be.saikono.poincare.ui.puzzle

data class CalculatorPuzzleChallenge(
    val id: String,
    val expression: String,
    val result: Double,
    val alternatives: List<String>,
    val mathGrade: Int,
    val difficultyStars: Int
)

data class CalculatorPuzzleAttempt(
    val challengeId: String,
    val targetExpression: String,
    val targetResult: Double,
    val submittedExpression: String,
    val submittedResult: Double?,
    val isCorrect: Boolean,
    val attemptNumber: Int
)

enum class CalculatorPuzzleMode {
    CREATE_QUESTION,
    ANSWER_QUESTION
}

data class CalculatorPuzzleCopy(
    val title: String,
    val subtitleCreate: String,
    val subtitleAnswer: String,
    val noQuestion: String,
    val target: String,
    val result: String,
    val randomExercise: String,
    val newQuestion: String,
    val tip: String,
    val showSolution: String,
    val realSolution: String,
    val alternatives: String,
    val invalidExpression: String,
    val questionCreated: String,
    val correct: String,
    val wrong: String,
    val attempts: String
)

fun calculatorPuzzleCopy(language: String): CalculatorPuzzleCopy {
    val normalized = language.lowercase()
    return when {
        normalized.startsWith("fr") -> CalculatorPuzzleCopy(
            title = "Puzzle calculatrice",
            subtitleCreate = "Crée ton propre exercice avec la calculatrice. Appuie sur = pour transformer ton calcul en question.",
            subtitleAnswer = "Construis une autre équation avec le même résultat, puis appuie sur =.",
            noQuestion = "Aucune question pour le moment. Crée une question ou lance un exercice aléatoire.",
            target = "Question",
            result = "Résultat",
            randomExercise = "Exercice aléatoire",
            newQuestion = "Créer une question",
            tip = "Indice",
            showSolution = "Voir la solution",
            realSolution = "Solution réelle",
            alternatives = "Autre équation possible",
            invalidExpression = "Ce calcul n'est pas valide.",
            questionCreated = "Question créée. Trouve maintenant une autre équation avec le même résultat.",
            correct = "Correct ! Même résultat.",
            wrong = "Pas encore. Essaie une autre équation.",
            attempts = "Réponses"
        )

        normalized.startsWith("en") -> CalculatorPuzzleCopy(
            title = "Calculator puzzle",
            subtitleCreate = "Create your own exercise with the calculator. Press = to turn your calculation into a question.",
            subtitleAnswer = "Build another equation with the same result, then press =.",
            noQuestion = "No question yet. Create one or start a random exercise.",
            target = "Question",
            result = "Result",
            randomExercise = "Random exercise",
            newQuestion = "Create question",
            tip = "Tip",
            showSolution = "Show solution",
            realSolution = "Real solution",
            alternatives = "Alternative equation",
            invalidExpression = "This calculation is not valid.",
            questionCreated = "Question created. Now find another equation with the same result.",
            correct = "Correct! Same result.",
            wrong = "Not yet. Try another equation.",
            attempts = "Answers"
        )

        else -> CalculatorPuzzleCopy(
            title = "Calculatorpuzzel",
            subtitleCreate = "Maak je eigen oefening met de calculator. Druk op = om je berekening als vraag te bewaren.",
            subtitleAnswer = "Maak een andere vergelijking met hetzelfde resultaat en druk op =.",
            noQuestion = "Nog geen vraag. Maak er zelf een of start een willekeurige oefening.",
            target = "Vraag",
            result = "Resultaat",
            randomExercise = "Willekeurige oefening",
            newQuestion = "Vraag maken",
            tip = "Tip",
            showSolution = "Toon oplossing",
            realSolution = "Echte oplossing",
            alternatives = "Andere vergelijking",
            invalidExpression = "Deze berekening is niet geldig.",
            questionCreated = "Vraag gemaakt. Zoek nu een andere vergelijking met hetzelfde resultaat.",
            correct = "Juist! Zelfde resultaat.",
            wrong = "Nog niet. Probeer een andere vergelijking.",
            attempts = "Antwoorden"
        )
    }
}
