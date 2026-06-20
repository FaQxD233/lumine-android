package com.moi.lumine.ui

sealed class Screen(val route: String) {
    object Home : Screen("home")
    object Subscriptions : Screen("subscriptions")
    object Rules : Screen("rules")
    object RuleDetail : Screen("rule_detail/{type}") {
        fun createRoute(type: String) = "rule_detail/$type"
    }
    object Settings : Screen("settings")
    object AppProxy : Screen("app_proxy")
    object Logs : Screen("logs")
    object Stats : Screen("stats")
}
