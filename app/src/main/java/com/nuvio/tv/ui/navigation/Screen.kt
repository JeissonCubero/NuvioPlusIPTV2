package com.nuvio.tv.ui.navigation

/**
 * Live TV only build: the app's pages. Home, Search, Library, Calendar and On Demand are kept
 * only as names (Live TV code still compares routes with them); they are not in the navigation.
 */
sealed class Screen(val route: String) {
    data object Home : Screen("home")
    data object Search : Screen("search")
    data object Library : Screen("library")
    data object Settings : Screen("settings")
    data object LiveTv : Screen("live_tv")
    data object LiveTvPlayer : Screen("live_tv_player")
    data object LiveTvMultiview : Screen("live_tv_multiview")
    data object SportsMultiview : Screen("sports_multiview")
    data object LiveTvSettings : Screen("live_tv_settings")
    data object OnDemand : Screen("on_demand")
    /** Recorded shows. */
    data object Recordings : Screen("recordings")
    data object Calendar : Screen("calendar")
    /** Scores, schedules and where to watch. */
    data object Sports : Screen("sports")
}
