package com.dice3d.app.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.dice3d.app.data.AppSettings
import com.dice3d.app.data.DarkModePreference
import com.dice3d.app.data.DiceType
import com.dice3d.app.data.SettingsRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val repo = SettingsRepository(application)

    val settings: StateFlow<AppSettings> = repo.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())

    fun updateDiceCount(count: Int) {
        viewModelScope.launch { repo.updateDiceCount(count.coerceIn(1, 10)) }
    }

    fun updateDiceType(type: DiceType) {
        viewModelScope.launch { repo.updateDiceType(type) }
    }

    fun updateDiceColor(color: Long) {
        viewModelScope.launch { repo.updateDiceColor(color) }
    }

    fun updateShowSum(show: Boolean) {
        viewModelScope.launch { repo.updateShowSum(show) }
    }

    fun updateSoundEnabled(enabled: Boolean) {
        viewModelScope.launch { repo.updateSoundEnabled(enabled) }
    }

    fun updateHapticEnabled(enabled: Boolean) {
        viewModelScope.launch { repo.updateHapticEnabled(enabled) }
    }

    fun updateSimSpeed(speed: Float) {
        viewModelScope.launch { repo.updateSimSpeed(speed.coerceIn(0.1f, 5.0f)) }
    }

    fun updateDarkMode(mode: DarkModePreference) {
        viewModelScope.launch { repo.updateDarkMode(mode) }
    }

    fun updateGyroEnabled(enabled: Boolean) {
        viewModelScope.launch { repo.updateGyroEnabled(enabled) }
    }

    fun resetSimSpeed() {
        viewModelScope.launch { repo.updateSimSpeed(1.0f) }
    }
}
