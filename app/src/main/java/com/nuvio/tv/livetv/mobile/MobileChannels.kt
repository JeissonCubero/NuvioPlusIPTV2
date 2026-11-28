package com.nuvio.tv.livetv.mobile

import android.app.Activity
import android.content.pm.ActivityInfo
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.nuvio.tv.R
import com.nuvio.tv.livetv.model.ChannelGroup
import com.nuvio.tv.livetv.model.LiveChannel
import com.nuvio.tv.livetv.ui.LiveTvViewModel

/** One Live TV view model for the phone's screens (list, player, search share what's playing). */
@Composable
internal fun mobileLiveTvViewModel(): LiveTvViewModel {
    val activity = LocalContext.current.findActivity() as ComponentActivity
    return hiltViewModel(viewModelStoreOwner = activity)
}

internal fun android.content.Context.findActivity(): Activity? {
    var c: android.content.Context? = this
    while (c is android.content.ContextWrapper) {
        if (c is Activity) return c
        c = c.baseContext
    }
    return null
}

/** Turns the phone sideways while a player is open; back to how it was when it closes. */
@Composable
internal fun LandscapeWhileShown() {
    val activity = LocalContext.current.findActivity() ?: return
    DisposableEffect(activity) {
        val before = activity.requestedOrientation
        activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        val window = activity.window
        val controller = androidx.core.view.WindowCompat.getInsetsController(window, window.decorView)
        controller.systemBarsBehavior =
            androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())
        onDispose {
            controller.show(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            activity.requestedOrientation = before
        }
    }
}

/**
 * Phone: groups as chips across the top, channels below with what's on now.
 * Tap a channel to watch it; hold it to add it to (or take it out of) Favorites.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MobileChannelsScreen(onPlay: () -> Unit) {
    val vm = mobileLiveTvViewModel()
    val ui by vm.uiState.collectAsStateWithLifecycle()
    val user by vm.userState.collectAsStateWithLifecycle()
    val now by vm.now.collectAsStateWithLifecycle()
    val programs by vm.programs.collectAsStateWithLifecycle()
    val playback by vm.playback.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var pinFor by remember { mutableStateOf<ChannelGroup?>(null) }
    val listState = rememberLazyListState()
    LaunchedEffect(ui.selectedGroupId) { listState.scrollToItem(0) }

    // Nothing plays while the list is on screen (the phone has no preview window).
    LaunchedEffect(Unit) { if (vm.playback.state.value.channelKey != null) vm.playback.stop() }

    Column(Modifier.fillMaxSize()) {
        Text(
            "Live TV",
            color = Color.White,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 16.dp, top = 14.dp, bottom = 8.dp)
        )
        if (!ui.hasSources) {
            EmptyNote(context.getString(R.string.livetv_only_mobile_no_lists))
            return@Column
        }
        LazyRow(
            contentPadding = PaddingValues(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(ui.groups, key = { it.id }) { g ->
                FilterChip(
                    selected = g.id == ui.selectedGroupId,
                    onClick = {
                        if (g.locked && g.id !in com.nuvio.tv.livetv.parental.ParentalControls.unlocked.value) pinFor = g else vm.selectGroup(g.id)
                    },
                    label = { Text(g.title, maxLines = 1) },
                    leadingIcon = if (g.locked) ({ Icon(Icons.Default.Lock, null, Modifier.size(16.dp)) }) else null,
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Color.White,
                        selectedLabelColor = Color.Black,
                        selectedLeadingIconColor = Color.Black,
                        labelColor = Color(0xFFDDDDE2)
                    )
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        if (ui.channels.isEmpty()) {
            EmptyNote(context.getString(R.string.livetv_only_mobile_no_channels))
        }
        LazyColumn(state = listState, contentPadding = PaddingValues(bottom = 16.dp)) {
            items(ui.channels, key = { it.key }) { ch ->
                val program = remember(programs, now, ch.key) { vm.currentProgram(ch.key, now) }
                ChannelRow(
                    channel = ch,
                    programTitle = program?.title,
                    progress = program?.let { ((now - it.startMs).toFloat() / (it.stopMs - it.startMs).coerceAtLeast(1)).coerceIn(0f, 1f) },
                    favorite = ch.key in user.favorites,
                    playing = playback.channelKey == ch.key,
                    onClick = {
                        vm.preview(ch, watchedIn = ui.selectedGroupId)
                        onPlay()
                    },
                    onLongClick = {
                        val wasFav = ch.key in user.favorites
                        vm.toggleFavorite(ch)
                        Toast.makeText(
                            context,
                            context.getString(if (wasFav) R.string.livetv_only_mobile_fav_removed else R.string.livetv_only_mobile_fav_added, ch.name),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                )
            }
        }
    }

    pinFor?.let { group ->
        PinDialog(
            onDismiss = { pinFor = null },
            onPin = { pin ->
                if (vm.unlockGroup(group, pin)) { vm.selectGroup(group.id); pinFor = null; true } else false
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ChannelRow(
    channel: LiveChannel,
    programTitle: String?,
    progress: Float?,
    favorite: Boolean,
    playing: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .background(if (playing) Color.White.copy(alpha = 0.06f) else Color.Transparent)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(width = 64.dp, height = 40.dp).clip(RoundedCornerShape(8.dp)).background(Color(0xFF1E1E22)),
            contentAlignment = Alignment.Center
        ) {
            if (!channel.logo.isNullOrBlank()) {
                AsyncImage(model = channel.logo, contentDescription = null, modifier = Modifier.padding(4.dp).fillMaxSize())
            } else {
                Text(channel.name.take(3).uppercase(), color = Color(0xFF9A9AA2), fontSize = 12.sp)
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${channel.number}  ${channel.name}",
                    color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Medium,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false)
                )
                if (favorite) {
                    Spacer(Modifier.width(6.dp))
                    Icon(Icons.Default.Star, null, tint = Color(0xFFFFC94D), modifier = Modifier.size(15.dp))
                }
            }
            if (programTitle != null) {
                Text(programTitle, color = Color(0xFFA8A8B0), fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (progress != null) {
                    Spacer(Modifier.height(4.dp))
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(2.dp)),
                        color = Color.White,
                        trackColor = Color.White.copy(alpha = 0.15f)
                    )
                }
            }
        }
    }
}

@Composable
internal fun EmptyNote(text: String) {
    Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(text, color = Color(0xFFA8A8B0), fontSize = 15.sp)
    }
}

@Composable
private fun PinDialog(onDismiss: () -> Unit, onPin: (String) -> Boolean) {
    var pin by remember { mutableStateOf("") }
    var wrong by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("PIN") },
        text = {
            OutlinedTextField(
                value = pin,
                onValueChange = { pin = it.filter(Char::isDigit).take(8); wrong = false },
                singleLine = true,
                isError = wrong,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.NumberPassword)
            )
        },
        confirmButton = { TextButton(onClick = { wrong = !onPin(pin) }) { Text("OK") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(androidx.compose.ui.res.stringResource(android.R.string.cancel)) } }
    )
}
