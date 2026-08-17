package be.saikono.poincare.ui.tasks

import be.saikono.poincare.data.tasks.HomeworkTaskCatalogue
import be.saikono.poincare.data.tasks.HomeworkTaskRecord
import be.saikono.poincare.data.tasks.HomeworkTaskTranslation
import be.saikono.poincare.data.tasks.tasksForTeacher
import be.saikono.poincare.repository.AssetHomeworkTaskRepository
import java.io.File
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

private val strictTaskJson = Json { ignoreUnknownKeys = false }

class TasksScreenTest {
    @Test
    fun `task page copy follows the selected app language`() {
        assertEquals("Huiswerk & taken", tasksText("title", "nl-BE"))
        assertEquals("Teacher ID", tasksText("teacherId", "en-US"))
        assertEquals("Aucune tâche pour le moment", tasksText("empty", "fr-BE"))
    }

    @Test
    fun `catalogue filters normalized teacher ID and localizes tasks in stable order`() {
        val catalogue = HomeworkTaskCatalogue(
            tasks = listOf(
                task(id = "later", teacherId = "teacher-7", order = 2, nlTitle = "Later", enTitle = "Later EN"),
                task(id = "other", teacherId = "teacher-8", order = 1, nlTitle = "Andere", enTitle = "Other"),
                task(id = "first", teacherId = "TEACHER-7", order = 1, nlTitle = "Eerst", enTitle = "First")
            )
        )

        val tasks = catalogue.tasksForTeacher("  teacher-7  ", "en-US")

        assertEquals(listOf("first", "later"), tasks.map { it.id })
        assertEquals(listOf("First", "Later EN"), tasks.map { it.title })
    }

    @Test
    fun `versioned asset catalogue is parseable and demo flow is data backed`() {
        val asset = sequenceOf(
            File("src/main/assets/${AssetHomeworkTaskRepository.ASSET_PATH}"),
            File("app/src/main/assets/${AssetHomeworkTaskRepository.ASSET_PATH}")
        ).first(File::isFile)
        val catalogue = strictTaskJson.decodeFromString<HomeworkTaskCatalogue>(asset.readText(Charsets.UTF_8))

        assertEquals(AssetHomeworkTaskRepository.SUPPORTED_SCHEMA_VERSION, catalogue.schemaVersion)
        assertEquals(2, catalogue.tasksForTeacher("demo-poincare", "nl-BE").size)
        assertFalse(catalogue.tasksForTeacher("onbekende-leerkracht", "nl-BE").isNotEmpty())
    }

    private fun task(
        id: String,
        teacherId: String,
        order: Int,
        nlTitle: String,
        enTitle: String
    ) = HomeworkTaskRecord(
        id = id,
        teacherId = teacherId,
        order = order,
        translations = mapOf(
            "nl-BE" to HomeworkTaskTranslation(title = nlTitle),
            "en-US" to HomeworkTaskTranslation(title = enTitle)
        )
    )
}
