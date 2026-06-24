package com.consensius.controller.datastore

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

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "consensius_settings")

class SettingsDataStore(private val context: Context) {

    companion object {
        val SERVER_IP = stringPreferencesKey("server_ip")
        val SERVER_PORT = intPreferencesKey("server_port")
        
        val DEADZONE = floatPreferencesKey("deadzone")
        val SENSITIVITY = floatPreferencesKey("sensitivity")
        val SEND_FPS = intPreferencesKey("send_fps")
        val DARK_MODE = booleanPreferencesKey("dark_mode")
        
        val SELECTED_PROFILE = stringPreferencesKey("selected_profile")
    }

    // Connection getters
    val serverIpFlow: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[SERVER_IP] ?: "192.168.1.100"
    }

    val serverPortFlow: Flow<Int> = context.dataStore.data.map { preferences ->
        preferences[SERVER_PORT] ?: 8765
    }

    // Settings getters
    val deadzoneFlow: Flow<Float> = context.dataStore.data.map { preferences ->
        preferences[DEADZONE] ?: 0.1f
    }

    val sensitivityFlow: Flow<Float> = context.dataStore.data.map { preferences ->
        preferences[SENSITIVITY] ?: 1.0f
    }

    val sendFpsFlow: Flow<Int> = context.dataStore.data.map { preferences ->
        preferences[SEND_FPS] ?: 60
    }

    val darkModeFlow: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[DARK_MODE] ?: true
    }

    // Profile getter
    val selectedProfileFlow: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[SELECTED_PROFILE] ?: "Mobile Legends"
    }

    // Setters
    suspend fun saveConnectionSettings(ip: String, port: Int) {
        context.dataStore.edit { preferences ->
            preferences[SERVER_IP] = ip
            preferences[SERVER_PORT] = port
        }
    }

    suspend fun saveSettings(deadzone: Float, sensitivity: Float, sendFps: Int, darkMode: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[DEADZONE] = deadzone
            preferences[SENSITIVITY] = sensitivity
            preferences[SEND_FPS] = sendFps
            preferences[DARK_MODE] = darkMode
        }
    }

    suspend fun saveSelectedProfile(profileName: String) {
        context.dataStore.edit { preferences ->
            preferences[SELECTED_PROFILE] = profileName
        }
    }
}
