package com.nuvio.tv.ui.navigation

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import com.nuvio.tv.ui.theme.NuvioMotion

/**
 * Live TV only build: the app's navigation (Live TV guide, search, Movies & Series, Sports,
 * Recordings and Live TV settings). Nuvio's own pages (Home, details, search, its player…) are gone.
 */
@Composable
fun NuvioNavHost(
    navController: NavHostController,
    startDestination: String = Screen.LiveTv.route
) {
    LiveTvNavGraph(navController, startDestination)
    // "Remind me" messages, shown on top of whatever screen you're on.
    com.nuvio.tv.livetv.reminders.LiveTvReminderHost(navController)
    // "Loading channels…" pill while things download.
    com.nuvio.tv.livetv.ui.LiveTvLoadingHost(navController)
    // Sports reminders and scoring alerts, on top of whatever screen you're on.
    com.nuvio.tv.livetv.sports.SportsHost(navController)
    // Pinned game scoreboards in the corner while something plays.
    com.nuvio.tv.livetv.sports.SportsPinsHost(navController)
}

/** Picking a page from the side menu: one copy of each page, each keeping its place. */
fun navigateToTopLevel(navController: NavHostController, route: String) {
    if (navController.currentDestination?.route == route) return
    runCatching {
        navController.navigate(route) {
            popUpTo(navController.graph.startDestinationId) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }
}

@Composable
private fun LiveTvNavGraph(navController: NavHostController, startDestination: String) {
    NavHost(
        navController = navController,
        startDestination = startDestination,
        enterTransition = { fadeIn(animationSpec = tween(NuvioMotion.tokens.durations.medium)) },
        exitTransition = { fadeOut(animationSpec = tween(NuvioMotion.tokens.durations.medium)) },
        popEnterTransition = { fadeIn(animationSpec = tween(NuvioMotion.tokens.durations.medium)) },
        popExitTransition = { fadeOut(animationSpec = tween(NuvioMotion.tokens.durations.medium)) }
    ) {
        composable(Screen.LiveTv.route) {
            com.nuvio.tv.livetv.ui.LiveTvGuideScreen(
                onOpenFullscreen = {
                    navController.navigate(Screen.LiveTvPlayer.route) { launchSingleTop = true }
                },
                onOpenSettings = { navController.navigate(Screen.LiveTvSettings.route) },
                onOpenRecordings = { navController.navigate(Screen.Recordings.route) { launchSingleTop = true } },
                onOpenMultiview = { navController.navigate(Screen.LiveTvMultiview.route) { launchSingleTop = true } },
                onOpenSportsMultiview = { panes, channelKey ->
                    com.nuvio.tv.livetv.sports.SportsWatchLaunch.request(panes, channelKey)
                    navController.navigate(Screen.SportsMultiview.route) { launchSingleTop = true }
                },
                // "Find…" and the guide's Search button open Live TV's own search page.
                onFindInNuvio = { navController.navigate(Screen.Search.route) { launchSingleTop = true } },
                onReturnToSports = { navigateToTopLevel(navController, Screen.Sports.route) },
                // A recording played full screen: Back goes to the screen it was picked on.
                onReturnTo = { route ->
                    if (!navController.popBackStack(route, inclusive = false)) {
                        navController.navigate(route) { launchSingleTop = true }
                    }
                }
            )
        }

        composable(Screen.Search.route) {
            com.nuvio.tv.livetv.only.LiveTvSearchScreen(
                onOpenLiveTv = { com.nuvio.tv.livetv.sports.openLiveTvFullscreen(navController) }
            )
        }

        composable(Screen.OnDemand.route) {
            com.nuvio.tv.livetv.only.VodScreen(
                onPlay = { navController.navigate(Screen.VodPlayer.route) { launchSingleTop = true } }
            )
        }

        composable(Screen.VodPlayer.route) {
            com.nuvio.tv.livetv.only.VodPlayerScreen(onClose = { navController.popBackStack() })
        }

        composable(Screen.Recordings.route) {
            com.nuvio.tv.livetv.record.RecordingsScreen(
                onPlay = { _, _, _, recordingId, fromStart ->
                    com.nuvio.tv.livetv.record.RecordingLaunch.open(recordingId, fromStart, Screen.Recordings.route)
                    navController.navigate(Screen.LiveTvPlayer.route) { launchSingleTop = true }
                }
            )
        }

        composable(Screen.Sports.route) { sportsEntry ->
            // A Sports page that isn't the one on screen (left behind by a page switch that got
            // cut short) is closed once it's been off screen longer than a page fade.
            val currentEntry by navController.currentBackStackEntryAsState()
            val onScreen = currentEntry?.id == sportsEntry.id
            var sportsShown by remember { mutableStateOf(true) }
            LaunchedEffect(onScreen) {
                if (onScreen) sportsShown = true
                else {
                    kotlinx.coroutines.delay(1_500)
                    sportsShown = false
                }
            }
            if (sportsShown) com.nuvio.tv.livetv.sports.SportsScreen(
                onOpenLive = { com.nuvio.tv.livetv.sports.openLiveTvFullscreen(navController) },
                onOpenMultiview = { channelKey ->
                    com.nuvio.tv.livetv.sports.SportsWatchLaunch.request(2, channelKey)
                    navController.navigate(Screen.SportsMultiview.route) { launchSingleTop = true }
                },
                onReturnTo = { route ->
                    if (route == Screen.LiveTvMultiview.route || route == Screen.SportsMultiview.route) {
                        if (!navController.popBackStack(route, inclusive = false)) {
                            runCatching { navController.navigate(route) { launchSingleTop = true } }
                        }
                    } else {
                        navigateToTopLevel(navController, route)
                    }
                }
            )
        }

        composable(Screen.LiveTvMultiview.route) {
            com.nuvio.tv.livetv.multiview.MultiviewScreen(
                onBack = { navController.popBackStack() },
                onOpenGame = { id ->
                    com.nuvio.tv.livetv.sports.SportsLaunch.openEvent(
                        id, com.nuvio.tv.livetv.sports.SportsLaunch.Origin(Screen.LiveTvMultiview.route, "Multiview")
                    )
                    navController.navigate(Screen.Sports.route) { launchSingleTop = true }
                }
            )
        }

        composable(Screen.SportsMultiview.route) {
            com.nuvio.tv.livetv.multiview.MultiviewScreen(
                onBack = { navController.popBackStack() },
                startOnSports = true,
                onOpenGame = { id ->
                    com.nuvio.tv.livetv.sports.SportsLaunch.openEvent(
                        id, com.nuvio.tv.livetv.sports.SportsLaunch.Origin(Screen.SportsMultiview.route, "Multiview")
                    )
                    navController.navigate(Screen.Sports.route) { launchSingleTop = true }
                },
                onSingle = {
                    // Return directly to the guide.
                    if (!navController.popBackStack(Screen.LiveTv.route, inclusive = false)) {
                        navController.navigate(Screen.LiveTv.route) {
                            popUpTo(Screen.SportsMultiview.route) { inclusive = true }
                            launchSingleTop = true
                            restoreState = false
                        }
                    }
                },
            )
        }

        composable(Screen.LiveTvPlayer.route) {
            // Full screen happens inside the guide (one video view that just grows), so this
            // route hands over to the guide in full screen.
            LaunchedEffect(Unit) {
                com.nuvio.tv.livetv.ui.LiveTvFullscreen.request()
                if (!navController.popBackStack(Screen.LiveTv.route, inclusive = false)) {
                    navController.navigate(Screen.LiveTv.route) {
                        popUpTo(Screen.LiveTvPlayer.route) { inclusive = true }
                        launchSingleTop = true
                    }
                }
            }
        }

        composable(Screen.LiveTvSettings.route) {
            com.nuvio.tv.ui.screens.settings.LiveTvSettingsScreen(
                onBack = { navController.popBackStack() }
            )
        }
    }
}
