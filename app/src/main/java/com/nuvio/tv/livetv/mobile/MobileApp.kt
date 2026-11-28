package com.nuvio.tv.livetv.mobile

import android.app.UiModeManager
import android.content.Context
import android.content.res.Configuration
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.nuvio.tv.R

/** True on a phone or tablet (anything that isn't a TV): the app shows its touch screens there. */
fun isTouchDevice(context: Context): Boolean {
    val ui = context.getSystemService(Context.UI_MODE_SERVICE) as? UiModeManager
    if (ui?.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION) return false
    val pm = context.packageManager
    if (pm.hasSystemFeature("android.software.leanback") && !pm.hasSystemFeature("android.hardware.touchscreen")) return false
    return pm.hasSystemFeature("android.hardware.touchscreen")
}

internal object MobileRoutes {
    const val TV = "m_tv"
    const val VOD = "m_vod"
    const val SEARCH = "m_search"
    const val SETTINGS = "m_settings"
    const val PLAYER = "m_player"
    const val VOD_PLAYER = "m_vod_player"
}

private data class Tab(val route: String, val label: Int, val icon: ImageVector)

/**
 * Live TV on a phone: a bottom bar with TV, Movies & Series, Search and Settings, over the same
 * lists, guide, catalog and players the TV version uses.
 */
@Composable
fun MobileApp() {
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route
    val tabs = listOf(
        Tab(MobileRoutes.TV, R.string.livetv_only_menu_live_tv, Icons.Default.LiveTv),
        Tab(MobileRoutes.VOD, R.string.livetv_only_menu_vod, Icons.Default.Movie),
        Tab(MobileRoutes.SEARCH, R.string.livetv_only_menu_search, Icons.Default.Search),
        Tab(MobileRoutes.SETTINGS, R.string.livetv_only_menu_settings, Icons.Default.Settings)
    )
    val fullScreen = route == MobileRoutes.PLAYER || route == MobileRoutes.VOD_PLAYER

    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Color.White,
            onPrimary = Color.Black,
            background = Color(0xFF0B0B0D),
            surface = Color(0xFF141417),
            surfaceVariant = Color(0xFF1E1E22),
            secondaryContainer = Color(0xFF2A2A30)
        )
    ) {
        Scaffold(
            containerColor = Color(0xFF0B0B0D),
            bottomBar = {
                if (!fullScreen) {
                    NavigationBar(containerColor = Color(0xFF141417)) {
                        tabs.forEach { t ->
                            NavigationBarItem(
                                selected = route == t.route,
                                onClick = {
                                    if (route != t.route) nav.navigate(t.route) {
                                        popUpTo(MobileRoutes.TV) { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                icon = { Icon(t.icon, contentDescription = null) },
                                label = { Text(stringResource(t.label)) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = Color.Black,
                                    indicatorColor = Color.White,
                                    unselectedIconColor = Color(0xFFB0B0B8),
                                    unselectedTextColor = Color(0xFFB0B0B8),
                                    selectedTextColor = Color.White
                                )
                            )
                        }
                    }
                }
            }
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(if (fullScreen) androidx.compose.foundation.layout.PaddingValues() else padding)) {
                NavHost(nav, startDestination = MobileRoutes.TV) {
                    composable(MobileRoutes.TV) {
                        MobileChannelsScreen(onPlay = { nav.navigate(MobileRoutes.PLAYER) { launchSingleTop = true } })
                    }
                    composable(MobileRoutes.PLAYER) {
                        MobilePlayerScreen(onClose = { nav.popBackStack() })
                    }
                    composable(MobileRoutes.VOD) {
                        MobileVodScreen(onPlay = { nav.navigate(MobileRoutes.VOD_PLAYER) { launchSingleTop = true } })
                    }
                    composable(MobileRoutes.VOD_PLAYER) {
                        LandscapeWhileShown()
                        com.nuvio.tv.livetv.only.VodPlayerScreen(onClose = { nav.popBackStack() })
                    }
                    composable(MobileRoutes.SEARCH) {
                        com.nuvio.tv.livetv.only.LiveTvSearchScreen(
                            onOpenLiveTv = { nav.navigate(MobileRoutes.PLAYER) { launchSingleTop = true } }
                        )
                    }
                    composable(MobileRoutes.SETTINGS) {
                        com.nuvio.tv.ui.screens.settings.LiveTvSettingsScreen(onBack = { nav.popBackStack() })
                    }
                }
                com.nuvio.tv.livetv.only.UpdateHost(manualCheck = 0)
            }
        }
    }
}
