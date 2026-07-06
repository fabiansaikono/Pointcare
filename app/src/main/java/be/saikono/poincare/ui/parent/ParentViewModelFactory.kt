package be.saikono.poincare.ui.parent

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import be.saikono.poincare.data.audio.AudioService
import be.saikono.poincare.data.settings.SettingsDataStore
import be.saikono.poincare.repository.ProfileRepository
import be.saikono.poincare.repository.ProgressRepository

class ParentViewModelFactory(
    private val profileRepository: ProfileRepository,
    private val progressRepository: ProgressRepository,
    private val settingsDataStore: SettingsDataStore,
    private val audioService: AudioService
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ParentViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return ParentViewModel(profileRepository, progressRepository, settingsDataStore, audioService) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
