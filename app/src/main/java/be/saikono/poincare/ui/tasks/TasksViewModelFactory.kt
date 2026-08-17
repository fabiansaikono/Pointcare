package be.saikono.poincare.ui.tasks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import be.saikono.poincare.repository.HomeworkTaskRepository

class TasksViewModelFactory(
    private val repository: HomeworkTaskRepository
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(TasksViewModel::class.java)) {
            "Unsupported ViewModel ${modelClass.name}."
        }
        return TasksViewModel(repository) as T
    }
}
