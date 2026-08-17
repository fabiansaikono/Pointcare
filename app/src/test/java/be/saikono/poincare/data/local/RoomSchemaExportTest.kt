package be.saikono.poincare.data.local

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class RoomSchemaExportTest {
    @Test
    fun `current Room schema is exported and includes lesson progress and attempts`() {
        val relative = "schemas/be.saikono.poincare.data.local.PoincareDatabase/11.json"
        val schema = listOf(File(relative), File("app/$relative"))
            .firstOrNull { it.isFile }
            ?: error("Missing exported Room schema: $relative")
        val json = schema.readText()

        assertTrue(Regex(""""version"\s*:\s*11""").containsMatchIn(json))
        assertTrue(Regex(""""tableName"\s*:\s*"profiles"""").containsMatchIn(json))
        assertTrue(Regex(""""fieldPath"\s*:\s*"selectedGrades"""").containsMatchIn(json))
        assertTrue(Regex(""""tableName"\s*:\s*"lesson_progress"""").containsMatchIn(json))
        assertTrue(Regex(""""tableName"\s*:\s*"lesson_attempts"""").containsMatchIn(json))
        assertTrue(Regex(""""tableName"\s*:\s*"lesson_stage_progress"""").containsMatchIn(json))
        assertTrue(Regex(""""tableName"\s*:\s*"lesson_assessment_attempts"""").containsMatchIn(json))
        assertTrue(Regex(""""tableName"\s*:\s*"lesson_question_attempts"""").containsMatchIn(json))
        assertTrue(Regex(""""tableName"\s*:\s*"lesson_skill_error_memory"""").containsMatchIn(json))
        assertTrue(json.contains("index_lesson_progress_child_lesson"))
        assertTrue(json.contains("index_lesson_attempts_child_lesson_sequence"))
        assertTrue(json.contains("index_lesson_attempts_sync_id"))
    }
}
