package be.saikono.poincare.ui.reading

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import be.saikono.poincare.data.audio.AudioService
import be.saikono.poincare.data.settings.SettingsDataStore
import be.saikono.poincare.data.sync.SyncManager
import be.saikono.poincare.repository.ContentRepository
import be.saikono.poincare.repository.ProgressRepository

class ReadingViewModelFactory(
    private val contentRepository: ContentRepository,
    private val progressRepository: ProgressRepository,
    private val settingsDataStore: SettingsDataStore,
    private val syncManager: SyncManager,
    private val audioService: AudioService,
    private val profileRepository: be.saikono.poincare.repository.ProfileRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ReadingViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return ReadingViewModel(contentRepository, progressRepository, settingsDataStore, syncManager, audioService, profileRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
