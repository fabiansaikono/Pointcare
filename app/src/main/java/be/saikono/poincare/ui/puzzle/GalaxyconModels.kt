package be.saikono.poincare.ui.puzzle

import be.saikono.poincare.model.SkillLevel
import java.util.Locale
import kotlin.math.max
import kotlin.math.round
import kotlin.random.Random

const val GALAXYCON_SUBCATEGORY_ID = "galaxycon"
const val GALAXYCON_START_LIVES = 3
const val GALAXYCON_MAX_LIVES = 3
const val GALAXYCON_MULTIPLIER_DURATION_MS = 15_000L
const val GALAXYCON_HEART_BONUS_MIN_OBJECTS = 40L
const val GALAXYCON_HEART_BONUS_MAX_OBJECTS = 60L

data class GalaxyconWord(
    val id: String,
    val text: String,
    val hintNl: String,
    val hintEn: String,
    val hintFr: String,
    val difficulty: Int
) {
    fun hint(locale: String): String = when (normalizeGalaxyconLocale(locale)) {
        "en-US" -> hintEn
        "fr-BE" -> hintFr
        else -> hintNl
    }
}

data class GalaxyconLevelSpec(
    val id: String,
    val title: String,
    val planetName: String,
    val words: List<GalaxyconWord>,
    val difficultyStars: Int,
    val speedFractionPerSecond: Float,
    val spawnEveryMs: Long,
    val durationMs: Long,
    val maxExpectedCollisions: Int,
    val seed: Int
) {
    val totalRequiredLetters: Int = words.sumOf { it.text.length }
}

enum class GalaxyconObjectType {
    CORRECT_LETTER,
    WRONG_LETTER,
    STAR,
    MULTIPLIER,
    DEBRIS,
    SHIELD,
    HEART
}

data class GalaxyconFlyingObject(
    val id: Long,
    val type: GalaxyconObjectType,
    val lane: Int,
    /**
     * Travel coordinate. Objects spawn above the screen at 1.08 and move down to 0.
     * The screen maps this to a top-to-bottom vertical position with 1f - x.
     */
    val x: Float,
    val label: String,
    val multiplier: Int = 1
)

data class GalaxyconRunResult(
    val scorePercent: Float,
    val displayScore: String,
    val points: Int,
    val collisions: Int,
    val wordsCompleted: Int,
    val totalWords: Int,
    val correctLetters: Int,
    val totalLetters: Int,
    val starsCollected: Int,
    val multipliersCollected: Int,
    val planetReached: Boolean,
    val stickersEarned: List<String>,
    val elapsedMs: Long
)

object GalaxyconGenerator {
    fun createLevel(
        locale: String,
        level: SkillLevel,
        difficultyStars: Int
    ): GalaxyconLevelSpec {
        val stars = difficultyStars.coerceIn(1, 4)
        val normalizedLocale = normalizeGalaxyconLocale(locale)
        val allWords = wordsFor(normalizedLocale)
        val exactDifficultyWords = allWords.filter { it.difficulty == stars }
        val fallbackWords = allWords.filter { it.difficulty <= stars }
        val needed = when (stars) {
            1 -> 3
            2 -> 4
            3 -> 5
            else -> 6
        }
        val seed = 31 * normalizedLocale.hashCode() + 17 * level.name.hashCode() + stars
        val selectedPool = if (exactDifficultyWords.size >= needed) exactDifficultyWords else fallbackWords
        val words = selectedPool
            .shuffled(Random(seed))
            .sortedWith(
                compareByDescending<GalaxyconWord> { if (stars >= 2 && it.difficulty == stars) 1 else 0 }
                    .thenBy { it.text.length }
                    .thenBy { it.difficulty }
            )
            .take(needed)

        val planet = when (stars) {
            1 -> "Lumora"
            2 -> "Nebulix"
            3 -> "Orbinox"
            else -> "Quasarion"
        }

        return GalaxyconLevelSpec(
            id = "galaxycon-${normalizedLocale.lowercase(Locale.US)}-${level.name.lowercase(Locale.US)}-$stars",
            title = "Galaxycon",
            planetName = planet,
            words = words,
            difficultyStars = stars,
            speedFractionPerSecond = when (stars) {
                1 -> 0.155f
                2 -> 0.195f
                3 -> 0.225f
                else -> 0.255f
            },
            spawnEveryMs = when (stars) {
                1 -> 1_450L
                2 -> 980L
                3 -> 850L
                else -> 720L
            },
            durationMs = when (stars) {
                1 -> 55_000L
                2 -> 65_000L
                3 -> 75_000L
                else -> 85_000L
            },
            maxExpectedCollisions = when (stars) {
                1 -> 8
                2 -> 8
                3 -> 9
                else -> 10
            },
            seed = 73 * normalizedLocale.hashCode() + 19 * level.name.hashCode() + stars
        )
    }

    fun createObject(
        level: GalaxyconLevelSpec,
        nextRequiredLetter: Char?,
        locale: String,
        spawnIndex: Long
    ): GalaxyconFlyingObject {
        val rng = Random(level.seed + spawnIndex.toInt() * 97)
        val lane = rng.nextInt(0, 3)
        val roll = rng.nextFloat()
        val stars = level.difficultyStars.coerceIn(1, 4)

        val correctChance = if (nextRequiredLetter != null) {
            when (stars) {
                1 -> 0.34f
                2 -> 0.30f
                3 -> 0.26f
                else -> 0.22f
            }
        } else {
            0f
        }
        val wrongChance = when (stars) {
            1 -> 0.12f
            2 -> 0.15f
            3 -> 0.17f
            else -> 0.20f
        }
        val debrisChance = when (stars) {
            1 -> 0.27f
            2 -> 0.38f
            3 -> 0.43f
            else -> 0.48f
        }
        val starChance = when (stars) {
            1 -> 0.13f
            2 -> 0.10f
            3 -> 0.08f
            else -> 0.06f
        }
        val multiplierChance = when (stars) {
            1 -> 0.06f
            2 -> 0.05f
            3 -> 0.04f
            else -> 0.03f
        }

        val type = when {
            roll < correctChance -> GalaxyconObjectType.CORRECT_LETTER
            roll < correctChance + wrongChance -> GalaxyconObjectType.WRONG_LETTER
            roll < correctChance + wrongChance + debrisChance -> GalaxyconObjectType.DEBRIS
            roll < correctChance + wrongChance + debrisChance + starChance -> GalaxyconObjectType.STAR
            roll < correctChance + wrongChance + debrisChance + starChance + multiplierChance -> GalaxyconObjectType.MULTIPLIER
            else -> GalaxyconObjectType.SHIELD
        }

        return when (type) {
            GalaxyconObjectType.CORRECT_LETTER -> GalaxyconFlyingObject(
                id = spawnIndex,
                type = type,
                lane = lane,
                x = 1.08f,
                label = nextRequiredLetter?.uppercaseChar()?.toString().orEmpty()
            )

            GalaxyconObjectType.WRONG_LETTER -> GalaxyconFlyingObject(
                id = spawnIndex,
                type = type,
                lane = lane,
                x = 1.08f,
                label = randomWrongLetter(rng, nextRequiredLetter, locale).toString()
            )

            GalaxyconObjectType.STAR -> GalaxyconFlyingObject(
                id = spawnIndex,
                type = type,
                lane = lane,
                x = 1.08f,
                label = "★"
            )

            GalaxyconObjectType.MULTIPLIER -> {
                val multiplier = listOf(2, 3, 4, 5)[rng.nextInt(4)]
                GalaxyconFlyingObject(
                    id = spawnIndex,
                    type = type,
                    lane = lane,
                    x = 1.08f,
                    label = "X$multiplier",
                    multiplier = multiplier
                )
            }

            GalaxyconObjectType.DEBRIS -> GalaxyconFlyingObject(
                id = spawnIndex,
                type = type,
                lane = lane,
                x = 1.08f,
                label = listOf("☄", "◆", "◾", "⬟")[rng.nextInt(4)]
            )

            GalaxyconObjectType.SHIELD -> GalaxyconFlyingObject(
                id = spawnIndex,
                type = type,
                lane = lane,
                x = 1.08f,
                label = "SH"
            )

            GalaxyconObjectType.HEART -> GalaxyconFlyingObject(
                id = spawnIndex,
                type = type,
                lane = lane,
                x = 1.08f,
                label = "♥"
            )
        }
    }

    fun createHeartObject(
        level: GalaxyconLevelSpec,
        spawnIndex: Long
    ): GalaxyconFlyingObject {
        val rng = Random(level.seed + spawnIndex.toInt() * 131 + 41)
        return GalaxyconFlyingObject(
            id = spawnIndex,
            type = GalaxyconObjectType.HEART,
            lane = rng.nextInt(0, 3),
            x = 1.08f,
            label = "♥"
        )
    }

    fun nextHeartSpawnIndex(level: GalaxyconLevelSpec, previousHeartSpawnIndex: Long): Long {
        val salt = if (previousHeartSpawnIndex == 0L) 11 else previousHeartSpawnIndex.toInt() * 17
        val rng = Random(level.seed + salt)
        val interval = rng.nextLong(GALAXYCON_HEART_BONUS_MIN_OBJECTS, GALAXYCON_HEART_BONUS_MAX_OBJECTS + 1L)
        return previousHeartSpawnIndex + interval
    }

    private fun randomWrongLetter(rng: Random, nextRequiredLetter: Char?, locale: String): Char {
        val alphabet = when (normalizeGalaxyconLocale(locale)) {
            "fr-BE" -> "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
            else -> "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
        }
        var candidate = alphabet[rng.nextInt(alphabet.length)]
        repeat(8) {
            if (nextRequiredLetter == null || !candidate.equals(nextRequiredLetter, ignoreCase = true)) {
                return candidate
            }
            candidate = alphabet[rng.nextInt(alphabet.length)]
        }
        return if (nextRequiredLetter?.uppercaseChar() == 'A') 'B' else 'A'
    }

    private fun wordsFor(locale: String): List<GalaxyconWord> = when (normalizeGalaxyconLocale(locale)) {
        "en-US" -> listOf(
            GalaxyconWord("galaxycon_en_sun", "sun", "De zon geeft licht.", "The sun gives light.", "Le soleil donne de la lumiere.", 1),
            GalaxyconWord("galaxycon_en_moon", "moon", "Je ziet hem vaak 's nachts.", "You often see it at night.", "On la voit souvent la nuit.", 1),
            GalaxyconWord("galaxycon_en_star", "star", "Een lichtpunt in de ruimte.", "A bright point in space.", "Un point lumineux dans l'espace.", 1),
            GalaxyconWord("galaxycon_en_mars", "mars", "Een rode planeet.", "A red planet.", "Une planete rouge.", 1),
            GalaxyconWord("galaxycon_en_comet", "comet", "Een ijzige ruimtereiziger.", "An icy space traveler.", "Un voyageur glace de l'espace.", 2),
            GalaxyconWord("galaxycon_en_rocket", "rocket", "Hij vliegt naar de ruimte.", "It flies into space.", "Elle vole vers l'espace.", 2),
            GalaxyconWord("galaxycon_en_planet", "planet", "Een wereld die rond een ster draait.", "A world that moves around a star.", "Un monde qui tourne autour d'une etoile.", 2),
            GalaxyconWord("galaxycon_en_orbit", "orbit", "Een baan rond een planeet.", "A path around a planet.", "Un chemin autour d'une planete.", 2),
            GalaxyconWord("galaxycon_en_saturn", "saturn", "Een planeet met ringen.", "A planet with rings.", "Une planete avec des anneaux.", 2),
            GalaxyconWord("galaxycon_en_galaxy", "galaxy", "Een groep sterren.", "A group of stars.", "Un groupe d'etoiles.", 3),
            GalaxyconWord("galaxycon_en_astronaut", "astronaut", "Iemand die naar de ruimte reist.", "A person who travels to space.", "Une personne qui voyage dans l'espace.", 3),
            GalaxyconWord("galaxycon_en_meteor", "meteor", "Een vallende ruimtesteen.", "A falling space rock.", "Une pierre de l'espace qui tombe.", 3),
            GalaxyconWord("galaxycon_en_telescope", "telescope", "Je kijkt ermee naar sterren.", "You use it to look at stars.", "On l'utilise pour regarder les etoiles.", 3),
            GalaxyconWord("galaxycon_en_universe", "universe", "Alles in de ruimte samen.", "Everything in space together.", "Tout l'espace ensemble.", 3)
        )

        "fr-BE" -> listOf(
            GalaxyconWord("galaxycon_fr_sol", "sol", "De grond onder je voeten.", "The ground under your feet.", "Le sol sous tes pieds.", 1),
            GalaxyconWord("galaxycon_fr_lune", "lune", "Je ziet haar vaak 's nachts.", "You often see it at night.", "On la voit souvent la nuit.", 1),
            GalaxyconWord("galaxycon_fr_mars", "mars", "Een rode planeet.", "A red planet.", "Une planete rouge.", 1),
            GalaxyconWord("galaxycon_fr_ciel", "ciel", "Daar zie je sterren.", "You see stars there.", "On y voit les etoiles.", 1),
            GalaxyconWord("galaxycon_fr_etoile", "etoile", "Een lichtpunt in de ruimte.", "A bright point in space.", "Un point lumineux dans l'espace.", 2),
            GalaxyconWord("galaxycon_fr_fusee", "fusee", "Ze vliegt naar de ruimte.", "It flies into space.", "Elle vole vers l'espace.", 2),
            GalaxyconWord("galaxycon_fr_planete", "planete", "Een wereld die rond een ster draait.", "A world that moves around a star.", "Un monde qui tourne autour d'une etoile.", 2),
            GalaxyconWord("galaxycon_fr_comete", "comete", "Een ijzige ruimtereiziger.", "An icy space traveler.", "Un voyageur glace de l'espace.", 2),
            GalaxyconWord("galaxycon_fr_orbite", "orbite", "Een baan rond een planeet.", "A path around a planet.", "Un chemin autour d'une planete.", 3),
            GalaxyconWord("galaxycon_fr_galaxie", "galaxie", "Een groep sterren.", "A group of stars.", "Un groupe d'etoiles.", 3),
            GalaxyconWord("galaxycon_fr_meteore", "meteore", "Een vallende ruimtesteen.", "A falling space rock.", "Une pierre de l'espace qui tombe.", 3),
            GalaxyconWord("galaxycon_fr_astronaute", "astronaute", "Iemand die naar de ruimte reist.", "A person who travels to space.", "Une personne qui voyage dans l'espace.", 3),
            GalaxyconWord("galaxycon_fr_univers", "univers", "Alles in de ruimte samen.", "Everything in space together.", "Tout l'espace ensemble.", 3)
        )

        else -> listOf(
            GalaxyconWord("galaxycon_nl_zon", "zon", "De zon geeft licht.", "The sun gives light.", "Le soleil donne de la lumiere.", 1),
            GalaxyconWord("galaxycon_nl_maan", "maan", "Je ziet hem vaak 's nachts.", "You often see it at night.", "On la voit souvent la nuit.", 1),
            GalaxyconWord("galaxycon_nl_ster", "ster", "Een lichtpunt in de ruimte.", "A bright point in space.", "Un point lumineux dans l'espace.", 1),
            GalaxyconWord("galaxycon_nl_mars", "mars", "Een rode planeet.", "A red planet.", "Une planete rouge.", 1),
            GalaxyconWord("galaxycon_nl_raket", "raket", "Hij vliegt naar de ruimte.", "It flies into space.", "Elle vole vers l'espace.", 2),
            GalaxyconWord("galaxycon_nl_planeet", "planeet", "Een wereld die rond een ster draait.", "A world that moves around a star.", "Un monde qui tourne autour d'une etoile.", 2),
            GalaxyconWord("galaxycon_nl_baan", "baan", "Een route rond een planeet.", "A path around a planet.", "Un chemin autour d'une planete.", 2),
            GalaxyconWord("galaxycon_nl_komeet", "komeet", "Een ijzige ruimtereiziger.", "An icy space traveler.", "Un voyageur glace de l'espace.", 2),
            GalaxyconWord("galaxycon_nl_melkweg", "melkweg", "Onze grote groep sterren.", "Our large group of stars.", "Notre grand groupe d'etoiles.", 3),
            GalaxyconWord("galaxycon_nl_astronaut", "astronaut", "Iemand die naar de ruimte reist.", "A person who travels to space.", "Une personne qui voyage dans l'espace.", 3),
            GalaxyconWord("galaxycon_nl_meteoor", "meteoor", "Een vallende ruimtesteen.", "A falling space rock.", "Une pierre de l'espace qui tombe.", 3),
            GalaxyconWord("galaxycon_nl_sterren", "sterren", "Lichtjes aan de hemel.", "Lights in the sky.", "Des lumieres dans le ciel.", 3),
            GalaxyconWord("galaxycon_nl_telescoop", "telescoop", "Je kijkt ermee naar sterren.", "You use it to look at stars.", "On l'utilise pour regarder les etoiles.", 3)
        )
    }
}

fun calculateGalaxyconResult(
    level: GalaxyconLevelSpec,
    points: Int,
    collisions: Int,
    wordsCompleted: Int,
    correctLetters: Int,
    starsCollected: Int,
    multipliersCollected: Int,
    planetReached: Boolean,
    stickersEarned: List<String>,
    elapsedMs: Long
): GalaxyconRunResult {
    val wordScore = percent(wordsCompleted, level.words.size)
    val letterScore = percent(correctLetters, level.totalRequiredLetters)
    val collisionScore = (100f - percent(collisions, level.maxExpectedCollisions)).coerceIn(0f, 100f)
    val bonusTarget = max(1, level.words.size * 35)
    val bonusScore = percent(starsCollected * 5 + multipliersCollected * 20, bonusTarget)
    val planetScore = if (planetReached) 100f else 0f
    val finalScore = (
        wordScore * 0.40f +
            letterScore * 0.20f +
            collisionScore * 0.20f +
            bonusScore * 0.10f +
            planetScore * 0.10f
        ).coerceIn(0f, 100f)
    val rounded = round(finalScore * 10f) / 10f
    return GalaxyconRunResult(
        scorePercent = rounded,
        displayScore = String.format(Locale.US, "%.1f%%", rounded),
        points = points,
        collisions = collisions,
        wordsCompleted = wordsCompleted,
        totalWords = level.words.size,
        correctLetters = correctLetters,
        totalLetters = level.totalRequiredLetters,
        starsCollected = starsCollected,
        multipliersCollected = multipliersCollected,
        planetReached = planetReached,
        stickersEarned = stickersEarned.distinct(),
        elapsedMs = elapsedMs
    )
}

fun normalizeGalaxyconLocale(locale: String): String = when (locale) {
    "fr", "fr-BE", "fr_BE", "fr-FR", "fr_FR" -> "fr-BE"
    "en", "en-US", "en_US", "en-GB", "en_GB" -> "en-US"
    else -> "nl-BE"
}

private fun percent(value: Int, maxValue: Int): Float {
    if (maxValue <= 0) return 0f
    return ((value.toFloat() / maxValue.toFloat()) * 100f).coerceIn(0f, 100f)
}
