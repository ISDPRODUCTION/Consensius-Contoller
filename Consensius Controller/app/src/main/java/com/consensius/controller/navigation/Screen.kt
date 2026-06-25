package com.consensius.controller.navigation

sealed class Screen(val route: String) {
    object Splash     : Screen("splash")
    object Home       : Screen("home")
    object Connection : Screen("connection")
    object Controller : Screen("controller")
    object Settings   : Screen("settings")
    object Profiles   : Screen("profiles")
    object ProfileEditor : Screen("profile_editor/{profileId}") {
        fun createRoute(profileId: String) = "profile_editor/$profileId"
        const val NEW = "new"
    }
}
