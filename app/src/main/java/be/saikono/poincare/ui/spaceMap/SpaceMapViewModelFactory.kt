package be.saikono.poincare.ui.spaceMap

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import be.saikono.poincare.data.settings.SettingsDataStore
import be.saikono.poincare.repository.ContentRepository
import be.saikono.poincare.repository.LessonProgressRepository
import be.saikono.poincare.repository.ProfileRepository
import be.saikono.poincare.repository.ProgressRepository

class SpaceMapViewModelFactory(
    private val context: Context,
    private val profileRepository: ProfileRepository,
    private val progressRepository: ProgressRepository,
    private val lessonProgressRepository: LessonProgressRepository,
    private val settingsDataStore: SettingsDataStore,
    private val contentRepository: ContentRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(SpaceMapViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return SpaceMapViewModel(context, profileRepository, progressRepository, lessonProgressRepository, settingsDataStore, contentRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
