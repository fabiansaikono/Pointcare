package be.saikono.poincare.ui.math

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import be.saikono.poincare.data.audio.AudioService
import be.saikono.poincare.data.settings.SettingsDataStore
import be.saikono.poincare.data.sync.SyncManager
import be.saikono.poincare.domain.adaptive.AdaptiveDifficultyEngine
import be.saikono.poincare.repository.ContentRepository
import be.saikono.poincare.repository.ProfileRepository
import be.saikono.poincare.repository.ProgressRepository

class MathViewModelFactory(
    private val contentRepository: ContentRepository,
    private val progressRepository: ProgressRepository,
    private val settingsDataStore: SettingsDataStore,
    private val syncManager: SyncManager,
    private val adaptiveEngine: AdaptiveDifficultyEngine,
    private val audioService: AudioService,
    private val profileRepository: ProfileRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MathViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return MathViewModel(contentRepository, progressRepository, settingsDataStore, syncManager, adaptiveEngine, audioService, profileRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
