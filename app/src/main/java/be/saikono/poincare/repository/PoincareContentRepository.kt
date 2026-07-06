package be.saikono.poincare.repository

import android.content.Context
import android.util.Log
import be.saikono.poincare.data.content.PoincareContentPack
import be.saikono.poincare.data.content.PoincareContentParser
import be.saikono.poincare.data.content.PoincareExercise
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

class PoincareContentRepository(
    private val context: Context,
    private val parser: PoincareContentParser = PoincareContentParser()
) {
    private var cachedPacks: List<PoincareContentPack>? = null

    suspend fun loadContentPacks(): List<PoincareContentPack> = withContext(Dispatchers.IO) {
        cachedPacks ?: loadBundledPacks().also { cachedPacks = it }
    }

    suspend fun loadExercises(): List<PoincareExercise> = loadContentPacks().flatMap { it.exercises }.distinctBy { it.id }
    suspend fun getExerciseById(id: String): PoincareExercise? = loadExercises().firstOrNull { it.id == id }
    suspend fun getExercisesByTopic(topic: String): List<PoincareExercise> = loadExercises().filter {
        it.topic.equals(topic, ignoreCase = true) || it.subcategoryId.equals(topic, ignoreCase = true)
    }
    suspend fun getExercisesBySkill(skillRef: String): List<PoincareExercise> = loadExercises().filter { it.skillRef.equals(skillRef, ignoreCase = true) }
    suspend fun getExercisesByGrade(grade: Int): List<PoincareExercise> = loadExercises().filter { it.grade == grade }
    suspend fun getExercisesByDifficulty(difficulty: Int): List<PoincareExercise> = loadExercises().filter { it.difficultyStars == difficulty || it.difficulty == difficulty }
    suspend fun getExercisesByMainCategory(mainCategory: String): List<PoincareExercise> = loadExercises().filter { it.mainCategory == mainCategory }
    suspend fun getExercisesBySubcategory(subcategoryId: String): List<PoincareExercise> = loadExercises().filter { it.subcategoryId == subcategoryId }
    suspend fun queryExercises(
        mainCategory: String? = null,
        subcategoryId: String? = null,
        grade: Int? = null,
        difficultyStars: Int? = null,
        locale: String? = null
    ): List<PoincareExercise> = loadExercises().filter { exercise ->
        (mainCategory == null || exercise.mainCategory == mainCategory) &&
            (subcategoryId == null || exercise.subcategoryId == subcategoryId) &&
            (grade == null || exercise.grade == grade) &&
            (difficultyStars == null || exercise.difficultyStars == difficultyStars || exercise.difficulty == difficultyStars) &&
            (locale == null || exercise.translations.containsKey(normalizeLocale(locale)) || exercise.translations.containsKey("nl-BE"))
    }

    private fun loadBundledPacks(): List<PoincareContentPack> {
        val contentFiles = discoverContentFiles()
        return contentFiles.mapNotNull { fileName ->
            val path = "poincare/content/$fileName"
            try {
                context.assets.open(path).use(parser::parse)
            } catch (e: IOException) {
                Log.e(TAG, "Failed to open content pack asset: $path", e)
                null
            } catch (e: Exception) {
                Log.e(TAG, "Failed to parse content pack asset: $path", e)
                null
            }
        }
    }

    private fun discoverContentFiles(): List<String> {
        val discovered = runCatching {
            context.assets.list("poincare/content")
                .orEmpty()
                .filter { fileName ->
                    fileName.startsWith("poincare-exercises-") &&
                        fileName.endsWith(".xml") &&
                        fileName != "poincare-exercises-index.xml"
                }
                .sorted()
        }.getOrElse { error ->
            Log.e(TAG, "Failed to list poincare content assets", error)
            emptyList()
        }
        return discovered.ifEmpty { CONTENT_FILES }
    }

    private fun assetExists(path: String): Boolean = try {
        context.assets.open(path).close()
        true
    } catch (e: IOException) {
        false
    }

    private fun normalizeLocale(locale: String): String = when (locale) {
        "nl", "nl_BE" -> "nl-BE"
        "en", "en_US" -> "en-US"
        "fr", "fr_BE", "fr-FR", "fr_FR" -> "fr-BE"
        else -> locale
    }

    companion object {
        private const val TAG = "PoincareContent"

        private val CONTENT_FILES = listOf(
            "poincare-exercises-math.xml",
            "poincare-exercises-language.xml",
            "poincare-exercises-logic-puzzles.xml",
            "poincare-exercises-world-knowledge.xml",
            "poincare-exercises-geometry.xml"
        )
    }
}