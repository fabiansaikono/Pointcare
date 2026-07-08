package be.saikono.poincare.ui.puzzle

import be.saikono.poincare.model.LocalizedText

/**
 * Runtime data for the generated Constellations puzzle.
 *
 * Coordinates use a normalized 0f..1f system so the same puzzle can be drawn on any screen size.
 * The star patterns are simplified educational connect-the-star versions of real constellations
 * or well-known asterisms inside real constellations.
 */
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
    val seed: Long
) {
    val requiredEdges: Set<ConstellationEdge>
        get() = connections.map { ConstellationEdge.of(it.fromStarId, it.toStarId) }.toSet()

    fun targetStarsInOrder(): List<ConstellationStar> = targetStarIds.mapNotNull { id -> stars.firstOrNull { it.id == id } }
}

data class ConstellationStar(
    val id: String,
    val name: String,
    val x: Float,
    val y: Float,
    val isTarget: Boolean,
    val order: Int? = null,
    val label: String? = order?.toString(),
    val brightness: Float = 1f
)

data class ConstellationConnection(
    val fromStarId: String,
    val toStarId: String
)

data class ConstellationEdge(
    val a: String,
    val b: String
) {
    companion object {
        fun of(first: String, second: String): ConstellationEdge =
            if (first <= second) ConstellationEdge(first, second) else ConstellationEdge(second, first)
    }
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
    val tapMissionToHear: String,
    val newMission: String,
    val nextMission: String,
    val resetPath: String,
    val tip: String,
    val factPrefix: String,
    val selectedPrefix: String,
    val firstStarSelected: String,
    val correctConnection: String,
    val wrongConnection: String,
    val alreadyConnected: String,
    val completed: String,
    val tipConnect: String,
    val startHere: String
)

fun constellationsCopy(language: String): ConstellationsCopy = when (language) {
    "fr", "fr-BE", "fr_BE", "fr-FR", "fr_FR" -> ConstellationsCopy(
        title = "Constellations",
        tapMissionToHear = "",
        newMission = "Nouvelle mission",
        nextMission = "Mission suivante",
        resetPath = "Effacer le chemin",
        tip = "Indice",
        factPrefix = "Info",
        selectedPrefix = "Sélection",
        firstStarSelected = "Tape une deuxième étoile ou glisse vers elle.",
        correctConnection = "Bien joué ! Cette ligne fait partie de la constellation.",
        wrongConnection = "Cette ligne ne fait pas partie de la constellation.",
        alreadyConnected = "Cette ligne est déjà connectée.",
        completed = "Constellation trouvée !",
        tipConnect = "Indice avec les valeurs",
        startHere = "Commence ici"
    )

    "en", "en-US", "en_US", "en-GB", "en_GB" -> ConstellationsCopy(
        title = "Constellations",
        tapMissionToHear = "",
        newMission = "New mission",
        nextMission = "Next mission",
        resetPath = "Clear path",
        tip = "Tip",
        factPrefix = "Fact",
        selectedPrefix = "Selected",
        firstStarSelected = "Tap a second star or drag to it.",
        correctConnection = "Nice! That line belongs to the constellation.",
        wrongConnection = "That line is not part of the constellation.",
        alreadyConnected = "That line is already connected.",
        completed = "Constellation found!",
        tipConnect = "Tip with the values",
        startHere = "Start here"
    )

    else -> ConstellationsCopy(
        title = "Constellations",
        tapMissionToHear = "",
        newMission = "Nieuwe missie",
        nextMission = "Volgende missie",
        resetPath = "Pad wissen",
        tip = "Tip",
        factPrefix = "Weetje",
        selectedPrefix = "Geselecteerd",
        firstStarSelected = "Tik een tweede ster aan of sleep ernaartoe.",
        correctConnection = "Goed zo! Die lijn hoort bij de constellatie.",
        wrongConnection = "Die lijn hoort niet bij de constellatie.",
        alreadyConnected = "Die lijn is al verbonden.",
        completed = "Constellatie gevonden!",
        tipConnect = "Tip met de waarden",
        startHere = "Start hier"
    )
}
