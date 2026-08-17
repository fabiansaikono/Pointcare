package be.saikono.poincare.data.tasks

import kotlinx.serialization.Serializable

@Serializable
data class HomeworkTaskTranslation(
    val title: String,
    val description: String = "",
    val dueLabel: String? = null
)

@Serializable
data class HomeworkTaskRecord(
    val id: String,
    val teacherId: String,
    val order: Int = 0,
    val translations: Map<String, HomeworkTaskTranslation>,
    val completed: Boolean = false,
    val enabled: Boolean = true
)

@Serializable
data class HomeworkTaskCatalogue(
    val schemaVersion: Int = 1,
    val tasks: List<HomeworkTaskRecord> = emptyList()
)

data class HomeworkTask(
    val id: String,
    val title: String,
    val description: String,
    val dueLabel: String?,
    val completed: Boolean
)

internal fun normalizedTeacherId(value: String): String = value.trim().uppercase()

internal fun HomeworkTaskRecord.localized(locale: String): HomeworkTaskTranslation {
    val normalizedLocale = locale.replace('_', '-')
    val language = normalizedLocale.substringBefore('-').lowercase()
    return translations[normalizedLocale]
        ?: translations.entries.firstOrNull { (key, _) -> key.substringBefore('-').lowercase() == language }?.value
        ?: translations["nl-BE"]
        ?: translations.values.first()
}

fun HomeworkTaskCatalogue.tasksForTeacher(teacherId: String, locale: String): List<HomeworkTask> {
    val normalizedTeacher = normalizedTeacherId(teacherId)
    if (normalizedTeacher.isBlank()) return emptyList()
    return tasks
        .asSequence()
        .filter(HomeworkTaskRecord::enabled)
        .filter { task -> normalizedTeacherId(task.teacherId) == normalizedTeacher }
        .sortedWith(compareBy<HomeworkTaskRecord> { it.order }.thenBy { it.id })
        .map { task ->
            val text = task.localized(locale)
            HomeworkTask(
                id = task.id,
                title = text.title,
                description = text.description,
                dueLabel = text.dueLabel,
                completed = task.completed
            )
        }
        .toList()
}
