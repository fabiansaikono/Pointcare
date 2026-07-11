package be.saikono.poincare.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import be.saikono.poincare.data.settings.SettingsDataStore
import be.saikono.poincare.repository.ContentRepository
import be.saikono.poincare.repository.ProfileRepository

class ProfileViewModelFactory(
    private val profileRepository: ProfileRepository,
    private val settingsDataStore: SettingsDataStore,
    private val contentRepository: ContentRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ProfileViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return ProfileViewModel(profileRepository, settingsDataStore, contentRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
