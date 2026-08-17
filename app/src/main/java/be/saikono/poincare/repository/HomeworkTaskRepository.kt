package be.saikono.poincare.repository

import android.content.Context
import be.saikono.poincare.data.tasks.HomeworkTask
import be.saikono.poincare.data.tasks.HomeworkTaskCatalogue
import be.saikono.poincare.data.tasks.tasksForTeacher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream

interface HomeworkTaskRepository {
    suspend fun tasksForTeacher(teacherId: String, locale: String): List<HomeworkTask>
}

class AssetHomeworkTaskRepository(private val context: Context) : HomeworkTaskRepository {
    private val json = Json { ignoreUnknownKeys = false }
    @Volatile private var cachedCatalogue: HomeworkTaskCatalogue? = null

    @OptIn(ExperimentalSerializationApi::class)
    private suspend fun catalogue(): HomeworkTaskCatalogue = withContext(Dispatchers.IO) {
        cachedCatalogue ?: context.assets.open(ASSET_PATH).use { input ->
            json.decodeFromStream<HomeworkTaskCatalogue>(input)
        }.also { catalogue ->
            require(catalogue.schemaVersion == SUPPORTED_SCHEMA_VERSION) {
                "Unsupported homework task schema ${catalogue.schemaVersion}."
            }
            cachedCatalogue = catalogue
        }
    }

    override suspend fun tasksForTeacher(teacherId: String, locale: String): List<HomeworkTask> =
        catalogue().tasksForTeacher(teacherId, locale)

    companion object {
        const val ASSET_PATH = "poincare/content/homework-tasks-v1.json"
        const val SUPPORTED_SCHEMA_VERSION = 1
    }
}
