package com.consensius.controller

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.consensius.controller.datastore.SettingsDataStore
import com.consensius.controller.navigation.Screen
import com.consensius.controller.network.WebSocketManager
import com.consensius.controller.ui.connection.ConnectionScreen
import com.consensius.controller.ui.controller.ControllerScreen
import com.consensius.controller.ui.home.HomeScreen
import com.consensius.controller.ui.profiles.ProfileEditorScreen
import com.consensius.controller.ui.profiles.ProfilesScreen
import com.consensius.controller.ui.settings.SettingsScreen
import com.consensius.controller.ui.splash.SplashScreen
import com.consensius.controller.ui.theme.ConsensiusControllerTheme

class MainActivity : ComponentActivity() {

    // Bug #1 fix: WebSocketManager at Activity level with lifecycleScope
    private lateinit var webSocketManager: WebSocketManager
    private lateinit var settingsDataStore: SettingsDataStore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Keep screen on
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        settingsDataStore = SettingsDataStore(applicationContext)

        // Bug #1: WebSocketManager lives in Activity scope, not Compose scope
        webSocketManager = WebSocketManager(lifecycleScope)

        setContent {
            // Bug #4: dark mode from DataStore applied immediately
            val darkMode by settingsDataStore.darkModeFlow.collectAsState(initial = true)

            ConsensiusControllerTheme(darkTheme = darkMode) {
                val navController = rememberNavController()

                NavHost(
                    navController = navController,
                    startDestination = Screen.Splash.route,
                    enterTransition = {
                        fadeIn(tween(220)) + slideIntoContainer(
                            AnimatedContentTransitionScope.SlideDirection.Start,
                            tween(220)
                        )
                    },
                    exitTransition = {
                        fadeOut(tween(180))
                    },
                    popEnterTransition = {
                        fadeIn(tween(220))
                    },
                    popExitTransition = {
                        fadeOut(tween(180)) + slideOutOfContainer(
                            AnimatedContentTransitionScope.SlideDirection.End,
                            tween(220)
                        )
                    }
                ) {
                    composable(Screen.Splash.route) {
                        SplashScreen(
                            settingsDataStore = settingsDataStore,
                            webSocketManager  = webSocketManager,
                            onNavigateToHome  = {
                                navController.navigate(Screen.Home.route) {
                                    popUpTo(Screen.Splash.route) { inclusive = true }
                                }
                            }
                        )
                    }

                    composable(Screen.Home.route) {
                        HomeScreen(
                            webSocketManager       = webSocketManager,
                            settingsDataStore      = settingsDataStore,
                            onNavigateToConnection = { navController.navigate(Screen.Connection.route) },
                            onNavigateToProfiles   = { navController.navigate(Screen.Profiles.route) },
                            onNavigateToSettings   = { navController.navigate(Screen.Settings.route) },
                            onNavigateToController = { navController.navigate(Screen.Controller.route) }
                        )
                    }

                    composable(Screen.Connection.route) {
                        ConnectionScreen(
                            settingsDataStore = settingsDataStore,
                            webSocketManager  = webSocketManager,
                            onNavigateBack    = { navController.popBackStack() }
                        )
                    }

                    composable(Screen.Profiles.route) {
                        ProfilesScreen(
                            settingsDataStore  = settingsDataStore,
                            onNavigateBack     = { navController.popBackStack() },
                            onNavigateToEditor = { profileId ->
                                navController.navigate(Screen.ProfileEditor.createRoute(profileId))
                            }
                        )
                    }

                    composable(Screen.ProfileEditor.route) { backStackEntry ->
                        val profileId = backStackEntry.arguments?.getString("profileId")
                        ProfileEditorScreen(
                            profileId         = profileId,
                            settingsDataStore = settingsDataStore,
                            onNavigateBack    = { navController.popBackStack() }
                        )
                    }

                    composable(Screen.Settings.route) {
                        SettingsScreen(
                            settingsDataStore = settingsDataStore,
                            onNavigateBack    = { navController.popBackStack() }
                        )
                    }

                    composable(Screen.Controller.route) {
                        ControllerScreen(
                            webSocketManager  = webSocketManager,
                            settingsDataStore = settingsDataStore,
                            onNavigateBack    = { navController.popBackStack() }
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
