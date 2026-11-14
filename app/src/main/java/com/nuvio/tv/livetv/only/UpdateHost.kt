package com.nuvio.tv.livetv.only

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.tv.material3.Text
import com.nuvio.tv.R
import com.nuvio.tv.ui.theme.NuvioTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

private sealed interface UpdateUi {
    data object Idle : UpdateUi
    data object Checking : UpdateUi
    data object UpToDate : UpdateUi
    data class Failed(val message: String) : UpdateUi
    data class Available(val update: AppUpdater.Update) : UpdateUi
    data class Downloading(val update: AppUpdater.Update) : UpdateUi
    /** Downloaded; waiting for "Install unknown apps" or for the user to press Install. */
    data class Ready(val update: AppUpdater.Update, val apk: File, val needsPermission: Boolean) : UpdateUi
}

/**
 * Live TV only build: checks for a new version a little after the app opens (and when
 * "Check for updates" is picked in the menu), and offers to download and install it.
 */
@Composable
fun UpdateHost(manualCheck: Int) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var ui by remember { mutableStateOf<UpdateUi>(UpdateUi.Idle) }
    var progress by remember { mutableFloatStateOf(0f) }
    // "Later": not offered again until the app is opened again.
    var dismissedBuild by remember { mutableStateOf(-1) }

    // Automatic check, once per app open, after the first channel has had time to start.
    LaunchedEffect(Unit) {
        delay(15_000)
        val update = runCatching { AppUpdater.check() }.getOrNull()
        if (update != null && update.build != dismissedBuild && ui == UpdateUi.Idle) ui = UpdateUi.Available(update)
    }
    // From the menu.
    LaunchedEffect(manualCheck) {
        if (manualCheck == 0) return@LaunchedEffect
        ui = UpdateUi.Checking
        ui = runCatching { AppUpdater.check() }.fold(
            onSuccess = { it?.let { u -> UpdateUi.Available(u) } ?: UpdateUi.UpToDate },
            onFailure = { UpdateUi.Failed(context.getString(R.string.livetv_only_update_check_failed)) }
        )
    }
    // Back from Android's "Install unknown apps" screen: install if it was switched on.
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            val s = ui
            if (s is UpdateUi.Ready && s.needsPermission && AppUpdater.canInstall(context)) {
                ui = s.copy(needsPermission = false)
                runCatching { AppUpdater.install(context, s.apk) }
            }
        }
    }

    fun startDownload(update: AppUpdater.Update) {
        ui = UpdateUi.Downloading(update)
        progress = 0f
        scope.launch {
            val apk = runCatching { AppUpdater.download(context, update) { progress = it } }.getOrNull()
            if (apk == null) {
                ui = UpdateUi.Failed(context.getString(R.string.livetv_only_update_download_failed))
                return@launch
            }
            if (AppUpdater.canInstall(context)) {
                ui = UpdateUi.Ready(update, apk, needsPermission = false)
                runCatching { AppUpdater.install(context, apk) }
            } else {
                ui = UpdateUi.Ready(update, apk, needsPermission = true)
            }
        }
    }

    when (val s = ui) {
        UpdateUi.Idle -> Unit
        UpdateUi.Checking -> Notice(stringResource(R.string.livetv_only_update_checking)) {}
        UpdateUi.UpToDate -> {
            LaunchedEffect(Unit) { delay(3_000); ui = UpdateUi.Idle }
            Notice(stringResource(R.string.livetv_only_update_up_to_date, AppUpdater.currentBuild)) {}
        }
        is UpdateUi.Failed -> {
            LaunchedEffect(s) { delay(4_000); ui = UpdateUi.Idle }
            Notice(s.message) {}
        }
        is UpdateUi.Available -> UpdateDialog(
            title = stringResource(R.string.livetv_only_update_available),
            text = stringResource(
                R.string.livetv_only_update_available_text,
                s.update.name,
                (s.update.sizeBytes / (1024 * 1024)).coerceAtLeast(1)
            ),
            primary = stringResource(R.string.livetv_only_update_now),
            onPrimary = { startDownload(s.update) },
            secondary = stringResource(R.string.livetv_only_update_later),
            onSecondary = { dismissedBuild = s.update.build; ui = UpdateUi.Idle }
        )
        is UpdateUi.Downloading -> UpdateDialog(
            title = stringResource(R.string.livetv_only_update_downloading),
            text = "${(progress * 100).toInt()} %",
            primary = null, onPrimary = {},
            secondary = null, onSecondary = {}
        )
        is UpdateUi.Ready -> if (s.needsPermission) {
            UpdateDialog(
                title = stringResource(R.string.livetv_only_update_permission_title),
                text = stringResource(R.string.livetv_only_update_permission_text),
                primary = stringResource(R.string.livetv_only_update_open_settings),
                onPrimary = {
                    if (!AppUpdater.openInstallPermission(context)) {
                        ui = UpdateUi.Failed(context.getString(R.string.livetv_only_update_permission_manual))
                    }
                },
                secondary = stringResource(R.string.livetv_only_update_later),
                onSecondary = { dismissedBuild = s.update.build; ui = UpdateUi.Idle }
            )
        } else {
            UpdateDialog(
                title = stringResource(R.string.livetv_only_update_ready),
                text = stringResource(R.string.livetv_only_update_ready_text),
                primary = stringResource(R.string.livetv_only_update_install),
                onPrimary = { runCatching { AppUpdater.install(context, s.apk) } },
                secondary = stringResource(R.string.livetv_only_update_later),
                onSecondary = { dismissedBuild = s.update.build; ui = UpdateUi.Idle }
            )
        }
    }
}

/** A short message at the bottom of the screen. */
@Composable
private fun Notice(text: String, @Suppress("UNUSED_PARAMETER") onDone: () -> Unit) {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.BottomCenter) {
        Text(
            text,
            color = Color.White,
            fontSize = 15.sp,
            modifier = Modifier
                .background(Color(0xE6222222), RoundedCornerShape(10.dp))
                .padding(horizontal = 18.dp, vertical = 10.dp)
        )
    }
}

@Composable
private fun UpdateDialog(
    title: String,
    text: String,
    primary: String?,
    onPrimary: () -> Unit,
    secondary: String?,
    onSecondary: () -> Unit
) {
    Dialog(
        onDismissRequest = { if (secondary != null) onSecondary() },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        val first = remember { FocusRequester() }
        LaunchedEffect(primary) { if (primary != null) runCatching { first.requestFocus() } }
        Column(
            Modifier
                .width(520.dp)
                .background(NuvioTheme.colors.BackgroundElevated, RoundedCornerShape(18.dp))
                .padding(28.dp)
        ) {
            Text(title, color = Color.White, fontSize = 22.sp)
            Spacer(Modifier.height(12.dp))
            Text(text, color = NuvioTheme.colors.TextSecondary, fontSize = 16.sp)
            if (primary != null || secondary != null) {
                Spacer(Modifier.height(24.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (primary != null) DialogButton(primary, Modifier.focusRequester(first), onPrimary)
                    if (secondary != null) DialogButton(secondary, Modifier, onSecondary)
                }
            }
        }
    }
}

@Composable
private fun DialogButton(label: String, modifier: Modifier, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(50)
    Box(
        modifier
            .onFocusChanged { focused = it.isFocused }
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
            .clip(shape)
            .background(if (focused) Color.White else Color.White.copy(alpha = 0.1f))
            .border(if (focused) 0.dp else 1.dp, Color.White.copy(alpha = 0.25f), shape)
            .padding(horizontal = 22.dp, vertical = 12.dp)
    ) {
        Text(label, color = if (focused) Color.Black else Color.White, fontSize = 16.sp)
    }
}
