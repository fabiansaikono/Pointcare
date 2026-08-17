package be.saikono.poincare.ui.story

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import be.saikono.poincare.data.audio.AudioService
import be.saikono.poincare.data.settings.SettingsDataStore
import be.saikono.poincare.repository.ProfileRepository
import be.saikono.poincare.repository.StoryRepository

class StoryViewModelFactory(
    private val storyRepository: StoryRepository,
    private val settingsDataStore: SettingsDataStore,
    private val profileRepository: ProfileRepository,
    private val audioService: AudioService
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(StoryViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return StoryViewModel(storyRepository, settingsDataStore, profileRepository, audioService) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
