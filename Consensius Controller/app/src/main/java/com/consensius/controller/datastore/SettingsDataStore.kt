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
import com.consensius.controller.model.ButtonConfig
import com.consensius.controller.model.ButtonElementConfig
import com.consensius.controller.model.CanvasElement
import com.consensius.controller.model.ControllerProfile
import com.consensius.controller.model.DpadElementConfig
import com.consensius.controller.model.DpadMapping
import com.consensius.controller.model.ElementSize
import com.consensius.controller.model.ElementType
import com.consensius.controller.model.JoystickElementConfig
import com.consensius.controller.model.JoystickType
import com.consensius.controller.model.ProfilePage
import com.consensius.controller.model.RightJoystickMode
import com.consensius.controller.model.TouchpadElementConfig
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "consensius_settings")

class SettingsDataStore(private val context: Context) {

    private val gson = GsonBuilder().create()

    companion object {
        val SERVER_IP           = stringPreferencesKey("server_ip")
        val SERVER_PORT         = intPreferencesKey("server_port")
        val DEADZONE            = floatPreferencesKey("deadzone")
        val SENSITIVITY         = floatPreferencesKey("sensitivity")
        val MOUSE_SENSITIVITY   = floatPreferencesKey("mouse_sensitivity")
        val SEND_FPS            = intPreferencesKey("send_fps")
        val DARK_MODE           = booleanPreferencesKey("dark_mode")
        val SELECTED_PROFILE_ID = stringPreferencesKey("selected_profile_id")
        val PROFILES_JSON       = stringPreferencesKey("profiles_json")
    }

    // ─── Connection ──────────────────────────────────────────────────────────

    val serverIpFlow: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[SERVER_IP] ?: "192.168.1.100"
    }

    val serverPortFlow: Flow<Int> = context.dataStore.data.map { prefs ->
        prefs[SERVER_PORT] ?: 8765
    }

    suspend fun saveConnectionSettings(ip: String, port: Int) {
        context.dataStore.edit { prefs ->
            prefs[SERVER_IP] = ip
            prefs[SERVER_PORT] = port
        }
    }

    // ─── Controller Settings ─────────────────────────────────────────────────

    val deadzoneFlow: Flow<Float> = context.dataStore.data.map { prefs ->
        prefs[DEADZONE] ?: 0.1f
    }

    val sensitivityFlow: Flow<Float> = context.dataStore.data.map { prefs ->
        prefs[SENSITIVITY] ?: 1.0f
    }

    val mouseSensitivityFlow: Flow<Float> = context.dataStore.data.map { prefs ->
        prefs[MOUSE_SENSITIVITY] ?: 1.5f
    }

    val sendFpsFlow: Flow<Int> = context.dataStore.data.map { prefs ->
        prefs[SEND_FPS] ?: 60
    }

    val darkModeFlow: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[DARK_MODE] ?: true
    }

    suspend fun saveSettings(
        deadzone: Float,
        sensitivity: Float,
        mouseSensitivity: Float,
        sendFps: Int,
        darkMode: Boolean
    ) {
        context.dataStore.edit { prefs ->
            prefs[DEADZONE]          = deadzone
            prefs[SENSITIVITY]       = sensitivity
            prefs[MOUSE_SENSITIVITY] = mouseSensitivity
            prefs[SEND_FPS]          = sendFps
            prefs[DARK_MODE]         = darkMode
        }
    }

    // ─── Profiles ────────────────────────────────────────────────────────────

    val selectedProfileIdFlow: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[SELECTED_PROFILE_ID] ?: ""
    }

    val profilesFlow: Flow<List<ControllerProfile>> = context.dataStore.data.map { prefs ->
        val json = prefs[PROFILES_JSON] ?: ""
        if (json.isBlank()) {
            defaultProfiles()
        } else {
            try {
                val type = object : TypeToken<List<ControllerProfile>>() {}.type
                gson.fromJson(json, type) ?: defaultProfiles()
            } catch (e: Exception) {
                defaultProfiles()
            }
        }
    }

    suspend fun saveProfiles(profiles: List<ControllerProfile>) {
        val json = gson.toJson(profiles)
        context.dataStore.edit { prefs ->
            prefs[PROFILES_JSON] = json
        }
    }

    suspend fun saveSelectedProfileId(profileId: String) {
        context.dataStore.edit { prefs ->
            prefs[SELECTED_PROFILE_ID] = profileId
        }
    }

    suspend fun saveProfile(profile: ControllerProfile) {
        context.dataStore.edit { store ->
            val existingJson = store[PROFILES_JSON] ?: ""
            val existing: MutableList<ControllerProfile> = if (existingJson.isBlank()) {
                defaultProfiles().toMutableList()
            } else {
                try {
                    val type = object : TypeToken<List<ControllerProfile>>() {}.type
                    (gson.fromJson(existingJson, type) as? List<ControllerProfile>)?.toMutableList()
                        ?: defaultProfiles().toMutableList()
                } catch (e: Exception) {
                    defaultProfiles().toMutableList()
                }
            }
            val index = existing.indexOfFirst { it.id == profile.id }
            if (index >= 0) existing[index] = profile else existing.add(profile)
            store[PROFILES_JSON] = gson.toJson(existing)
        }
    }

    suspend fun deleteProfile(profileId: String) {
        context.dataStore.edit { store ->
            val existingJson = store[PROFILES_JSON] ?: ""
            val existing: MutableList<ControllerProfile> = if (existingJson.isBlank()) {
                defaultProfiles().toMutableList()
            } else {
                try {
                    val type = object : TypeToken<List<ControllerProfile>>() {}.type
                    (gson.fromJson(existingJson, type) as? List<ControllerProfile>)?.toMutableList()
                        ?: defaultProfiles().toMutableList()
                } catch (e: Exception) {
                    defaultProfiles().toMutableList()
                }
            }
            existing.removeAll { it.id == profileId }
            store[PROFILES_JSON] = gson.toJson(existing)
        }
    }

    // ─── Defaults ────────────────────────────────────────────────────────────

    private fun defaultProfiles(): List<ControllerProfile> = listOf(
        ControllerProfile(
            id = "default-ml",
            name = "Mobile Legends",
            pages = listOf(
                ProfilePage(
                    id = "ml-page-1",
                    name = "Page 1",
                    elements = listOf(
                        CanvasElement(id = "ml-dpad", type = ElementType.DPAD, label = "DPAD",
                            x = 0.18f, y = 0.55f, size = ElementSize.L,
                            dpadConfig = DpadElementConfig("w","s","a","d")),
                        CanvasElement(id = "ml-js-left", type = ElementType.JOYSTICK, label = "Move",
                            x = 0.10f, y = 0.75f, size = ElementSize.L,
                            joystickConfig = JoystickElementConfig(JoystickType.MOVEMENT)),
                        CanvasElement(id = "ml-js-right", type = ElementType.JOYSTICK, label = "Aim",
                            x = 0.65f, y = 0.75f, size = ElementSize.M,
                            joystickConfig = JoystickElementConfig(JoystickType.SKILL_AIM, "")),
                        CanvasElement(id = "ml-s1", type = ElementType.BUTTON, label = "S1",
                            x = 0.72f, y = 0.35f, size = ElementSize.M,
                            buttonConfig = ButtonElementConfig("k")),
                        CanvasElement(id = "ml-s2", type = ElementType.BUTTON, label = "S2",
                            x = 0.80f, y = 0.55f, size = ElementSize.M,
                            buttonConfig = ButtonElementConfig("j")),
                        CanvasElement(id = "ml-s3", type = ElementType.BUTTON, label = "S3",
                            x = 0.88f, y = 0.35f, size = ElementSize.M,
                            buttonConfig = ButtonElementConfig("u")),
                        CanvasElement(id = "ml-atk", type = ElementType.BUTTON, label = "ATK",
                            x = 0.80f, y = 0.75f, size = ElementSize.L,
                            buttonConfig = ButtonElementConfig("f")),
                        CanvasElement(id = "ml-spl", type = ElementType.BUTTON, label = "SPL",
                            x = 0.65f, y = 0.65f, size = ElementSize.S,
                            buttonConfig = ButtonElementConfig("e")),
                    )
                )
            )
        ),
        ControllerProfile(
            id = "default-pubg",
            name = "PUBG Mobile",
            pages = listOf(
                ProfilePage(
                    id = "pubg-page-1",
                    name = "Page 1",
                    elements = listOf(
                        CanvasElement(id = "pubg-dpad", type = ElementType.DPAD, label = "DPAD",
                            x = 0.18f, y = 0.55f, size = ElementSize.L,
                            dpadConfig = DpadElementConfig("w","s","a","d")),
                        CanvasElement(id = "pubg-js-left", type = ElementType.JOYSTICK, label = "Move",
                            x = 0.10f, y = 0.75f, size = ElementSize.L,
                            joystickConfig = JoystickElementConfig(JoystickType.MOVEMENT)),
                        CanvasElement(id = "pubg-tp", type = ElementType.TOUCHPAD, label = "TP",
                            x = 0.50f, y = 0.50f, size = ElementSize.L,
                            touchpadConfig = TouchpadElementConfig(1.5f, "mouse1", "mouse2")),
                        CanvasElement(id = "pubg-fire", type = ElementType.BUTTON, label = "FIRE",
                            x = 0.82f, y = 0.60f, size = ElementSize.L,
                            buttonConfig = ButtonElementConfig("f")),
                        CanvasElement(id = "pubg-ads", type = ElementType.BUTTON, label = "ADS",
                            x = 0.72f, y = 0.40f, size = ElementSize.M,
                            buttonConfig = ButtonElementConfig("mouse2")),
                        CanvasElement(id = "pubg-reload", type = ElementType.BUTTON, label = "RLD",
                            x = 0.65f, y = 0.25f, size = ElementSize.M,
                            buttonConfig = ButtonElementConfig("r")),
                        CanvasElement(id = "pubg-jump", type = ElementType.BUTTON, label = "JUMP",
                            x = 0.88f, y = 0.30f, size = ElementSize.M,
                            buttonConfig = ButtonElementConfig("space")),
                        CanvasElement(id = "pubg-crouch", type = ElementType.BUTTON, label = "CRCH",
                            x = 0.88f, y = 0.75f, size = ElementSize.M,
                            buttonConfig = ButtonElementConfig("c")),
                    )
                )
            )
        )
    )
}
