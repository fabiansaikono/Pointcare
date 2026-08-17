package be.saikono.poincare.ui.tasks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import be.saikono.poincare.repository.HomeworkTaskRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class TasksUiState(
    val tasks: List<HomeworkTaskUi> = emptyList(),
    val isLoading: Boolean = false,
    val loadFailed: Boolean = false
)

class TasksViewModel(private val repository: HomeworkTaskRepository) : ViewModel() {
    private val _uiState = MutableStateFlow(TasksUiState())
    val uiState: StateFlow<TasksUiState> = _uiState.asStateFlow()
    private var loadJob: Job? = null
    private var loadedKey: Pair<String, String>? = null

    fun load(teacherId: String, locale: String) {
        val key = teacherId.trim() to locale
        if (key == loadedKey && !_uiState.value.loadFailed) return
        loadedKey = key
        loadJob?.cancel()
        if (key.first.isBlank()) {
            _uiState.value = TasksUiState()
            return
        }
        loadJob = viewModelScope.launch {
            _uiState.value = TasksUiState(isLoading = true)
            _uiState.value = runCatching { repository.tasksForTeacher(key.first, locale) }
                .fold(
                    onSuccess = { tasks ->
                        TasksUiState(
                            tasks = tasks.map { task ->
                                HomeworkTaskUi(
                                    id = task.id,
                                    title = task.title,
                                    description = task.description,
                                    dueLabel = task.dueLabel,
                                    completed = task.completed
                                )
                            }
                        )
                    },
                    onFailure = { TasksUiState(loadFailed = true) }
                )
        }
    }
}
