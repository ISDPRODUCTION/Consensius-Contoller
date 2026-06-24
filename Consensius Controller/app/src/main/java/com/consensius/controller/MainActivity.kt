package com.consensius.controller

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.consensius.controller.datastore.SettingsDataStore
import com.consensius.controller.navigation.Screen
import com.consensius.controller.network.WebSocketManager
import com.consensius.controller.ui.connection.ConnectionScreen
import com.consensius.controller.ui.controller.ControllerScreen
import com.consensius.controller.ui.home.HomeScreen
import com.consensius.controller.ui.profiles.ProfilesScreen
import com.consensius.controller.ui.settings.SettingsScreen
import com.consensius.controller.ui.splash.SplashScreen
import com.consensius.controller.ui.theme.ConsensiusControllerTheme

class MainActivity : ComponentActivity() {

    private lateinit var webSocketManager: WebSocketManager
    private lateinit var settingsDataStore: SettingsDataStore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Keep screen active during usage
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // Init Datastore
        settingsDataStore = SettingsDataStore(applicationContext)

        setContent {
            val coroutineScope = rememberCoroutineScope()
            // Keep WebSocketManager scope bound to composition / active application activity scope
            webSocketManager = remember { WebSocketManager(coroutineScope) }
            
            ConsensiusControllerTheme {
                val navController = rememberNavController()

                NavHost(
                    navController = navController,
                    startDestination = Screen.Splash.route
                ) {
                    composable(Screen.Splash.route) {
                        SplashScreen(
                            settingsDataStore = settingsDataStore,
                            webSocketManager = webSocketManager,
                            onNavigateToHome = {
                                navController.navigate(Screen.Home.route) {
                                    popUpTo(Screen.Splash.route) { inclusive = true }
                                }
                            }
                        )
                    }

                    composable(Screen.Home.route) {
                        HomeScreen(
                            webSocketManager = webSocketManager,
                            onNavigateToConnection = { navController.navigate(Screen.Connection.route) },
                            onNavigateToProfiles = { navController.navigate(Screen.Profiles.route) },
                            onNavigateToSettings = { navController.navigate(Screen.Settings.route) },
                            onNavigateToController = { navController.navigate(Screen.Controller.route) }
                        )
                    }

                    composable(Screen.Connection.route) {
                        ConnectionScreen(
                            settingsDataStore = settingsDataStore,
                            webSocketManager = webSocketManager,
                            onNavigateBack = { navController.popBackStack() }
                        )
                    }

                    composable(Screen.Profiles.route) {
                        ProfilesScreen(
                            settingsDataStore = settingsDataStore,
                            onNavigateBack = { navController.popBackStack() }
                        )
                    }

                    composable(Screen.Settings.route) {
                        SettingsScreen(
                            settingsDataStore = settingsDataStore,
                            onNavigateBack = { navController.popBackStack() }
                        )
                    }

                    composable(Screen.Controller.route) {
                        ControllerScreen(
                            webSocketManager = webSocketManager,
                            settingsDataStore = settingsDataStore,
                            onNavigateBack = { navController.popBackStack() }
                        )
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::webSocketManager.isInitialized) {
            webSocketManager.disconnect()
        }
    }
}
