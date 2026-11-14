package com.nuvio.tv

import android.content.Context
import android.content.res.Configuration
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.tv.material3.Text
import com.nuvio.tv.ui.navigation.NuvioNavHost
import com.nuvio.tv.ui.navigation.Screen
import com.nuvio.tv.ui.navigation.navigateToTopLevel
import com.nuvio.tv.ui.theme.NuvioTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.util.Locale
import javax.inject.Inject

/** True while the side menu is open (Live TV and Sports leave Back to it then). */
val LocalSidebarExpanded = compositionLocalOf { false }

/** Opens the side menu from a screen (used by Live TV's and Sports' Back button). */
val LocalOpenSidebar = compositionLocalOf<() -> Unit> { {} }

/** Where the highlight goes when the side menu closes. */
val LocalContentFocusRequester = compositionLocalOf { FocusRequester.Default }

/**
 * Live TV only build: the app's one screen. It holds the Live TV pages and a simple side
 * menu (Live TV, Search, Movies & Series, Sports, Recordings, Settings) in place of Nuvio's.
 *
 * Open so the launcher icon choices (see livetv/branding) can each be their own activity.
 */
@AndroidEntryPoint
open class MainActivity : ComponentActivity() {

    @Inject
    lateinit var liveTvPreferences: com.nuvio.tv.livetv.data.LiveTvPreferences

    @Inject
    lateinit var liveTvRecorder: com.nuvio.tv.livetv.record.LiveTvRecorder

    @Inject
    lateinit var liveTvRecordingScheduler: com.nuvio.tv.livetv.record.LiveTvRecordingScheduler

    @Inject
    lateinit var sportsRepository: com.nuvio.tv.livetv.sports.SportsRepository

    // Set up with the app, so the Live TV player's settings and callbacks are in place
    // whatever screen starts a channel.
    // Movies & Series: imports your providers' catalogs (On Demand) in the background.
    @Inject
    lateinit var onDemandRepository: com.nuvio.tv.livetv.ondemand.OnDemandRepository

    @Inject
    lateinit var liveTvPlayerHooks: com.nuvio.tv.livetv.player.LiveTvPlayerHooks

    /**
     * True while a long press of Back is being handled by a screen: the rest of that press
     * (the key repeating, then letting go) is swallowed here so it doesn't also go back.
     */
    val longPressBackHeld = mutableStateOf(false)

    override fun attachBaseContext(newBase: Context) {
        val tag = LocaleCache.localeTag.takeIf { it != LocaleCache.UNSET }
        if (!tag.isNullOrEmpty()) {
            val locale = Locale.forLanguageTag(tag)
            Locale.setDefault(locale)
            val config = Configuration(newBase.resources.configuration)
            config.setLocale(locale)
            super.attachBaseContext(newBase.createConfigurationContext(config))
        } else {
            super.attachBaseContext(newBase)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        // A fresh open of the app (not a rebuild of the same screen), so "Start in Full Screen"
        // runs again even if Android kept the app running in the background.
        if (savedInstanceState == null) com.nuvio.tv.livetv.ui.LiveTvAppOpens.count++
        // Start reading the saved channel list and guide as the app opens.
        lifecycleScope.launch { runCatching { com.nuvio.tv.livetv.startup.LiveTvPreload.start(this@MainActivity) } }
        // There is no Home to wait for: background work may start once the first page is up.
        runCatching { com.nuvio.tv.livetv.startup.AppSettled.markDrawn() }
        // "Open the App On": kept where the first screen can read it instantly.
        lifecycleScope.launch {
            runCatching {
                kotlinx.coroutines.flow.combine(liveTvPreferences.settings, sportsRepository.settings) { st, sp -> st to sp.showInSidebar }
                    .collect { (st, sportsOn) ->
                        com.nuvio.tv.livetv.startup.StartPage.save(
                            this@MainActivity,
                            com.nuvio.tv.livetv.startup.StartPage.routeFor(st, sportsOn)
                        )
                    }
            }
        }
        // Once, after updating to this version: watch full screen first, the guide only when asked
        // for (Back from full screen, or Left for the channel list on top of the picture).
        // Both switches stay in Settings → Live TV.
        val onceFlags = getSharedPreferences("livetv_only", MODE_PRIVATE)
        if (!onceFlags.getBoolean("fullscreen_first_v1", false)) {
            lifecycleScope.launch {
                runCatching {
                    liveTvPreferences.updateSettings { it.copy(autoPlayLastChannel = true, openFullscreenOnSelect = true) }
                    onceFlags.edit().putBoolean("fullscreen_first_v1", true).apply()
                }
            }
        }
        // Scheduled recordings (sets their alarms; series look for new airings).
        runCatching { liveTvRecordingScheduler.start() }
        runCatching { onDemandRepository.start() }
        window?.setBackgroundDrawable(ColorDrawable(android.graphics.Color.BLACK))

        val startRoute = when (com.nuvio.tv.livetv.startup.StartPage.cachedRoute(this)) {
            Screen.Sports.route -> Screen.Sports.route
            else -> Screen.LiveTv.route
        }

        setContent {
            NuvioTheme {
                LiveTvShell(
                    startRoute = startRoute,
                    sportsInMenu = sportsRepository.settings.collectAsState().value.showInSidebar,
                    longPressBackHeld = longPressBackHeld,
                    onExit = { finish() }
                )
            }
        }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        // Remember when the remote was last used (the launcher rows use it).
        if (event.action == KeyEvent.ACTION_DOWN) {
            com.nuvio.tv.livetv.home.LiveTvHomeFocus.lastKeyAt = android.os.SystemClock.uptimeMillis()
        }
        if (event.keyCode == KeyEvent.KEYCODE_BACK && longPressBackHeld.value) {
            if (event.action == KeyEvent.ACTION_UP) longPressBackHeld.value = false
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onStart() {
        super.onStart()
        // Make sure the launcher rows exist once the app has loaded its channels.
        runCatching { com.nuvio.tv.livetv.home.LiveTvLauncherRows.refresh(applicationContext, afterMs = 6000) }
    }

    override fun onStop() {
        super.onStop()
        // Bring the launcher's Live TV rows up to date.
        runCatching { com.nuvio.tv.livetv.home.LiveTvLauncherRows.refresh(applicationContext) }
    }
}

private data class MenuEntry(val route: String, val labelRes: Int)

/** Menu item that checks for a new version instead of opening a page. */
private const val UPDATE_ROUTE = "__check_updates"

@Composable
private fun LiveTvShell(
    startRoute: String,
    sportsInMenu: Boolean,
    longPressBackHeld: MutableState<Boolean>,
    onExit: () -> Unit
) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    var menuOpen by remember { mutableStateOf(false) }
    // Bumped by "Check for updates" in the menu.
    var manualUpdateCheck by remember { mutableStateOf(0) }
    val contentFocus = remember { FocusRequester() }

    val entries = buildList {
        add(MenuEntry(Screen.LiveTv.route, R.string.livetv_only_menu_live_tv))
        add(MenuEntry(Screen.Search.route, R.string.livetv_only_menu_search))
        add(MenuEntry(Screen.OnDemand.route, R.string.livetv_only_menu_vod))
        if (sportsInMenu) add(MenuEntry(Screen.Sports.route, R.string.livetv_only_menu_sports))
        add(MenuEntry(Screen.Recordings.route, R.string.livetv_only_menu_recordings))
        add(MenuEntry(Screen.LiveTvSettings.route, R.string.livetv_only_menu_settings))
        add(MenuEntry(UPDATE_ROUTE, R.string.livetv_only_menu_updates))
    }

    CompositionLocalProvider(
        LocalSidebarExpanded provides menuOpen,
        LocalContentFocusRequester provides contentFocus,
        LocalOpenSidebar provides { menuOpen = true },
        com.nuvio.tv.livetv.ui.LocalSideMenuFit provides true
    ) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            NuvioNavHost(navController = navController, startDestination = startRoute)
            com.nuvio.tv.livetv.only.UpdateHost(manualCheck = manualUpdateCheck)

            AnimatedVisibility(
                visible = menuOpen,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.55f))
                )
            }
            AnimatedVisibility(
                visible = menuOpen,
                enter = slideInHorizontally { -it } + fadeIn(),
                exit = slideOutHorizontally { -it } + fadeOut()
            ) {
                SideMenu(
                    entries = entries,
                    currentRoute = currentRoute,
                    longPressBackHeld = longPressBackHeld,
                    onPick = { route ->
                        menuOpen = false
                        if (route == UPDATE_ROUTE) manualUpdateCheck++
                        else navigateToTopLevel(navController, route)
                    },
                    onClose = { menuOpen = false },
                    onExit = onExit
                )
            }
        }
    }
}

@Composable
private fun SideMenu(
    entries: List<MenuEntry>,
    currentRoute: String?,
    longPressBackHeld: MutableState<Boolean>,
    onPick: (String) -> Unit,
    onClose: () -> Unit,
    onExit: () -> Unit
) {
    val requesters = remember(entries.size) { List(entries.size) { FocusRequester() } }
    val selected = entries.indexOfFirst { it.route == currentRoute }.coerceAtLeast(0)
    // The highlighted item. Up/Down are handled here (not by Android's focus search), so the
    // highlight can't slip to the page under the menu: one press = one item.
    var focusedIndex by remember { mutableStateOf(selected) }
    LaunchedEffect(Unit) { runCatching { requesters[selected].requestFocus() } }
    fun moveTo(index: Int) {
        val target = index.coerceIn(0, entries.lastIndex)
        focusedIndex = target
        runCatching { requesters[target].requestFocus() }
    }
    // Back in the menu: leave the app (like Nuvio's own menu on its first page).
    BackHandler(enabled = !longPressBackHeld.value) { onExit() }

    Column(
        Modifier
            .fillMaxHeight()
            .width(280.dp)
            .background(NuvioTheme.colors.BackgroundElevated)
            .padding(horizontal = 16.dp, vertical = 32.dp)
            .onPreviewKeyEvent { e ->
                if (e.type != KeyEventType.KeyDown) {
                    // Swallow the matching key-ups too, so the page underneath never sees them.
                    return@onPreviewKeyEvent e.key == Key.DirectionUp || e.key == Key.DirectionDown ||
                        e.key == Key.DirectionLeft || e.key == Key.DirectionRight
                }
                when (e.key) {
                    Key.DirectionDown -> { moveTo(focusedIndex + 1); true }
                    Key.DirectionUp -> { moveTo(focusedIndex - 1); true }
                    Key.DirectionLeft -> true
                    // Right closes the menu and goes back to the page.
                    Key.DirectionRight -> { onClose(); true }
                    else -> false
                }
            },
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = stringResource(R.string.app_name),
            color = NuvioTheme.colors.TextPrimary,
            fontSize = 22.sp,
            modifier = Modifier.padding(start = 12.dp, bottom = 20.dp)
        )
        entries.forEachIndexed { i, entry ->
            MenuItem(
                label = stringResource(entry.labelRes),
                active = i == selected,
                focusRequester = requesters[i],
                onFocused = { focusedIndex = i },
                onClick = { onPick(entry.route) }
            )
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun MenuItem(
    label: String,
    active: Boolean,
    focusRequester: FocusRequester,
    onFocused: () -> Unit,
    onClick: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(12.dp)
    val background = when {
        focused -> NuvioTheme.colors.FocusBackground
        active -> Color.White.copy(alpha = 0.08f)
        else -> Color.Transparent
    }
    Box(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(background)
            .border(if (focused) 2.dp else 0.dp, if (focused) NuvioTheme.colors.FocusRing else Color.Transparent, shape)
            .focusRequester(focusRequester)
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onFocused()
            }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 16.dp, vertical = 14.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Text(
            text = label,
            color = if (focused || active) NuvioTheme.colors.TextPrimary else NuvioTheme.colors.TextSecondary,
            fontSize = 18.sp
        )
    }
}

/** The app's language, read before the first screen is made (see NuvioApplication). */
object LocaleCache {
    const val UNSET = "__UNSET__"

    @Volatile
    var localeTag: String = UNSET
}
