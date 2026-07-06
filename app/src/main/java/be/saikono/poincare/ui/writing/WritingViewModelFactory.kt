package be.saikono.poincare.ui.writing

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import be.saikono.poincare.data.settings.SettingsDataStore
import be.saikono.poincare.data.sync.SyncManager
import be.saikono.poincare.repository.ContentRepository
import be.saikono.poincare.repository.ProfileRepository
import be.saikono.poincare.repository.ProgressRepository

class WritingViewModelFactory(
    private val contentRepository: ContentRepository,
    private val progressRepository: ProgressRepository,
    private val settingsDataStore: SettingsDataStore,
    private val syncManager: SyncManager,
    private val audioService: be.saikono.poincare.data.audio.AudioService,
    private val profileRepository: ProfileRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(WritingViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return WritingViewModel(contentRepository, progressRepository, settingsDataStore, syncManager, audioService, profileRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
