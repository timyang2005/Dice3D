package com.dice3d.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "dice_settings")

object SettingsKeys {
    val DICE_COUNT = intPreferencesKey("dice_count")
    val DICE_TYPE = stringPreferencesKey("dice_type")
    val DICE_COLOR = intPreferencesKey("dice_color")
    val SHOW_SUM = booleanPreferencesKey("show_sum")
    val SOUND_ENABLED = booleanPreferencesKey("sound_enabled")
    val HAPTIC_ENABLED = booleanPreferencesKey("haptic_enabled")
    val SIM_SPEED = floatPreferencesKey("sim_speed")
    val DARK_MODE = stringPreferencesKey("dark_mode")
    val GYRO_ENABLED = booleanPreferencesKey("gyro_enabled")
}

enum class DarkModePreference(val key: String) {
    FOLLOW_SYSTEM("follow_system"),
    LIGHT("light"),
    DARK("dark")
}

data class AppSettings(
    val diceCount: Int = 1,
    val diceType: DiceType = DiceType.D6,
    val diceColor: Long = 0xFFFFFFFF,
    val showSum: Boolean = true,
    val soundEnabled: Boolean = true,
    val hapticEnabled: Boolean = true,
    val simSpeed: Float = 1.0f,
    val darkMode: DarkModePreference = DarkModePreference.FOLLOW_SYSTEM,
    val gyroEnabled: Boolean = true
)

class SettingsRepository(private val context: Context) {

    val settings: Flow<AppSettings> = context.dataStore.data.map { prefs ->
        AppSettings(
            diceCount = prefs[SettingsKeys.DICE_COUNT] ?: 1,
            diceType = try {
                DiceType.valueOf(prefs[SettingsKeys.DICE_TYPE] ?: "D6")
            } catch (_: Exception) {
                DiceType.D6
            },
            diceColor = (prefs[SettingsKeys.DICE_COLOR] ?: 0xFFFFFFFF.toInt()).toLong(),
            showSum = prefs[SettingsKeys.SHOW_SUM] ?: true,
            soundEnabled = prefs[SettingsKeys.SOUND_ENABLED] ?: true,
            hapticEnabled = prefs[SettingsKeys.HAPTIC_ENABLED] ?: true,
            simSpeed = prefs[SettingsKeys.SIM_SPEED] ?: 1.0f,
            darkMode = try {
                DarkModePreference.valueOf(
                    prefs[SettingsKeys.DARK_MODE] ?: DarkModePreference.FOLLOW_SYSTEM.name
                )
            } catch (_: Exception) {
                DarkModePreference.FOLLOW_SYSTEM
            },
            gyroEnabled = prefs[SettingsKeys.GYRO_ENABLED] ?: true
        )
    }

    suspend fun updateDiceCount(count: Int) {
        context.dataStore.edit { it[SettingsKeys.DICE_COUNT] = count }
    }

    suspend fun updateDiceType(type: DiceType) {
        context.dataStore.edit { it[SettingsKeys.DICE_TYPE] = type.name }
    }

    suspend fun updateDiceColor(color: Long) {
        context.dataStore.edit { it[SettingsKeys.DICE_COLOR] = color.toInt() }
    }

    suspend fun updateShowSum(show: Boolean) {
        context.dataStore.edit { it[SettingsKeys.SHOW_SUM] = show }
    }

    suspend fun updateSoundEnabled(enabled: Boolean) {
        context.dataStore.edit { it[SettingsKeys.SOUND_ENABLED] = enabled }
    }

    suspend fun updateHapticEnabled(enabled: Boolean) {
        context.dataStore.edit { it[SettingsKeys.HAPTIC_ENABLED] = enabled }
    }

    suspend fun updateSimSpeed(speed: Float) {
        context.dataStore.edit { it[SettingsKeys.SIM_SPEED] = speed }
    }

    suspend fun updateDarkMode(mode: DarkModePreference) {
        context.dataStore.edit { it[SettingsKeys.DARK_MODE] = mode.name }
    }

    suspend fun updateGyroEnabled(enabled: Boolean) {
        context.dataStore.edit { it[SettingsKeys.GYRO_ENABLED] = enabled }
    }
}
