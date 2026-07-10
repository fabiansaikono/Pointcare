package be.saikono.poincare.ui.puzzle

import be.saikono.poincare.model.LocalizedText

/**
 * A connection is undirected: A-B is the same as B-A.
 */
data class ConstellationEdge(
    val a: String,
    val b: String
) {
    companion object {
        fun of(first: String, second: String): ConstellationEdge {
            return if (first <= second) ConstellationEdge(first, second) else ConstellationEdge(second, first)
        }
    }
}

data class ConstellationConnection(
    val fromStarId: String,
    val toStarId: String
) {
    val edge: ConstellationEdge = ConstellationEdge.of(fromStarId, toStarId)
}

data class ConstellationStar(
    val id: String,
    val name: String,
    val x: Float,
    val y: Float,
    val isTarget: Boolean,
    val order: Int? = null,
    val label: String? = null,
    val brightness: Float = 0.85f,
    /**
     * Stars with the same groupId belong to the same independent line.
     * The player only has to connect neighbouring orders inside the same group.
     */
    val groupId: String? = null,
    val groupSymbol: String? = null,
    val groupRule: LocalizedText? = null
)

data class ConstellationPuzzle(
    val id: String,
    val templateId: String,
    val constellationName: LocalizedText,
    val mission: LocalizedText,
    val fact: LocalizedText,
    val difficultyStars: Int,
    val grade: Int,
    val stars: List<ConstellationStar>,
    val connections: List<ConstellationConnection>,
    val targetStarIds: Set<String>,
    val seed: Long,
    val rules: List<LocalizedText> = emptyList()
) {
    val requiredEdges: Set<ConstellationEdge>
        get() = connections.map { it.edge }.toSet()

    val targetStars: List<ConstellationStar>
        get() = stars.filter { it.isTarget }

    val starsById: Map<String, ConstellationStar>
        get() = stars.associateBy { it.id }
}

data class ConstellationConnectionAttempt(
    val puzzleId: String,
    val constellationId: String,
    val fromStarId: String,
    val toStarId: String,
    val correct: Boolean,
    val completed: Boolean,
    val selectedConnectionCount: Int,
    val requiredConnectionCount: Int
)

data class ConstellationsCopy(
    val title: String,
    val ruleTitle: String,
    val chooseFirst: String,
    val chooseSecond: String,
    val correctConnection: String,
    val wrongConnection: String,
    val alreadyConnected: String,
    val completed: String,
    val tip: String,
    val tipConnect: String,
    val noTipNeeded: String,
    val resetPath: String,
    val nextMission: String,
    val factPrefix: String,
    val progress: String
)

fun constellationsCopy(language: String): ConstellationsCopy {
    return when (language.lowercase()) {
        "fr-be", "fr", "fr-fr" -> ConstellationsCopy(
            title = "Constellations",
            ruleTitle = "Règle",
            chooseFirst = "Choisis une étoile de départ.",
            chooseSecond = "Choisis l'étoile suivante avec le même symbole, du début vers la fin.",
            correctConnection = "Bien joué. Continue ce chemin vers la fin.",
            wrongConnection = "Pas encore. Commence au début de ce chemin et avance valeur par valeur.",
            alreadyConnected = "Cette ligne est déjà faite.",
            completed = "Constellation terminée !",
            tip = "Indice",
            tipConnect = "Indice : relie ces deux prochaines valeurs dans le bon ordre.",
            noTipNeeded = "Tout est déjà relié.",
            resetPath = "Effacer",
            nextMission = "Mission suivante",
            factPrefix = "Info espace",
            progress = "Lignes"
        )
        "en-us", "en-gb", "en" -> ConstellationsCopy(
            title = "Constellations",
            ruleTitle = "Rule",
            chooseFirst = "Choose a starting star.",
            chooseSecond = "Choose the next star with the same symbol, from start to finish.",
            correctConnection = "Good connection. Keep moving toward the end of this path.",
            wrongConnection = "Not yet. Start at the beginning of this path and move value by value.",
            alreadyConnected = "That line is already complete.",
            completed = "Constellation complete!",
            tip = "Hint",
            tipConnect = "Hint: connect these next two values in the correct order.",
            noTipNeeded = "Everything is already connected.",
            resetPath = "Clear",
            nextMission = "Next mission",
            factPrefix = "Space fact",
            progress = "Lines"
        )
        else -> ConstellationsCopy(
            title = "Constellaties",
            ruleTitle = "Regel",
            chooseFirst = "Kies een startster.",
            chooseSecond = "Kies de volgende ster met hetzelfde symbool, van start naar einde.",
            correctConnection = "Goed verbonden. Ga verder naar het einde van dit pad.",
            wrongConnection = "Nog niet. Begin bij de start van dit pad en ga waarde per waarde verder.",
            alreadyConnected = "Die lijn is al klaar.",
            completed = "Constellatie voltooid!",
            tip = "Tip",
            tipConnect = "Tip: verbind deze twee volgende waarden in de juiste volgorde.",
            noTipNeeded = "Alles is al verbonden.",
            resetPath = "Wissen",
            nextMission = "Volgende missie",
            factPrefix = "Ruimtefeitje",
            progress = "Lijnen"
        )
    }
}
