package com.nuvio.tv.livetv.mobile

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay

/**
 * Phone player for a channel. Tap: show / hide the controls. Swipe up / down: next / previous
 * channel in the list you came from. Turned sideways, full screen.
 */
@Composable
fun MobilePlayerScreen(onClose: () -> Unit) {
    LandscapeWhileShown()
    val vm = mobileLiveTvViewModel()
    val playback by vm.playback.state.collectAsStateWithLifecycle()
    val user by vm.userState.collectAsStateWithLifecycle()
    val now by vm.now.collectAsStateWithLifecycle()
    val programs by vm.programs.collectAsStateWithLifecycle()
    var controls by remember { mutableStateOf(true) }
    var shownAt by remember { mutableLongStateOf(System.currentTimeMillis()) }
    fun show() { controls = true; shownAt = System.currentTimeMillis() }

    val channel = vm.playback.playingChannel?.takeIf { it.key == playback.channelKey }
    val program = remember(programs, now, channel?.key) { channel?.let { vm.currentProgram(it.key, now) } }

    com.nuvio.tv.livetv.ui.PauseLiveTvInBackground(vm.playback)
    // Controls hide after 4 s.
    LaunchedEffect(controls, shownAt) {
        if (controls) { delay(4_000); if (System.currentTimeMillis() - shownAt >= 3_900) controls = false }
    }
    fun close() { vm.playback.stop(); onClose() }
    BackHandler { close() }
    fun zap(dir: Int) {
        vm.zap(playback.channelKey, dir)?.let { vm.preview(it) }
        show()
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(Unit) {
                detectTapGestures(onTap = { if (controls) controls = false else show() })
            }
            .pointerInput(Unit) {
                var total = 0f
                detectVerticalDragGestures(
                    onDragStart = { total = 0f },
                    onDragEnd = {
                        if (total < -120f) zap(1) else if (total > 120f) zap(-1)
                    },
                    onVerticalDrag = { _, dy -> total += dy }
                )
            }
    ) {
        com.nuvio.tv.livetv.ui.LivePlayerSurface(
            player = vm.playback.player,
            modifier = Modifier.fillMaxSize(),
            useSurfaceView = true,
            onAttached = { vm.playback.onSurfaceAttached() },
            pictureShape = vm.pictureShapeOf(playback.channelKey)
        )

        if (playback.isBuffering && playback.error == null) {
            CircularProgressIndicator(color = Color.White, modifier = Modifier.align(Alignment.Center).size(42.dp))
        }
        playback.error?.let {
            Text(
                it,
                color = Color.White,
                fontSize = 14.sp,
                modifier = Modifier.align(Alignment.Center).background(Color(0xCC000000), androidx.compose.foundation.shape.RoundedCornerShape(10.dp))
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            )
        }

        AnimatedVisibility(controls, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize()) {
                // Top: back, channel, program, favorite.
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(Brush.verticalGradient(listOf(Color(0xCC000000), Color.Transparent)))
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { close() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = Color.White)
                    }
                    Column(Modifier.weight(1f)) {
                        Text(
                            channel?.let { "${it.number}  ${it.name}" }.orEmpty(),
                            color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold,
                            maxLines = 1, overflow = TextOverflow.Ellipsis
                        )
                        program?.let {
                            Text(it.title, color = Color(0xFFCFCFD6), fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    channel?.let { ch ->
                        val fav = ch.key in user.favorites
                        IconButton(onClick = { vm.toggleFavorite(ch); show() }) {
                            Icon(if (fav) Icons.Default.Star else Icons.Default.StarBorder, null,
                                tint = if (fav) Color(0xFFFFC94D) else Color.White)
                        }
                    }
                }
                // Middle: previous channel · play/pause · next channel.
                Row(
                    Modifier.align(Alignment.Center),
                    horizontalArrangement = Arrangement.spacedBy(28.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RoundIcon(Icons.Default.KeyboardArrowUp, 48) { zap(-1) }
                    RoundIcon(if (playback.userPaused) Icons.Default.PlayArrow else Icons.Default.Pause, 64) {
                        vm.playback.togglePause(); show()
                    }
                    RoundIcon(Icons.Default.KeyboardArrowDown, 48) { zap(1) }
                }
                // Bottom: how far into the show.
                program?.let { p ->
                    val frac = ((now - p.startMs).toFloat() / (p.stopMs - p.startMs).coerceAtLeast(1)).coerceIn(0f, 1f)
                    Column(
                        Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xCC000000))))
                            .padding(horizontal = 20.dp, vertical = 14.dp)
                    ) {
                        Box(Modifier.fillMaxWidth().height(4.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.2f))) {
                            Box(Modifier.fillMaxWidth(frac).height(4.dp).clip(CircleShape).background(Color.White))
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "${clock(p.startMs)} – ${clock(p.stopMs)}   ·   swipe ↑ ↓ to change channel",
                            color = Color(0xFFB8B8C0), fontSize = 12.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RoundIcon(icon: androidx.compose.ui.graphics.vector.ImageVector, sizeDp: Int, onClick: () -> Unit) {
    Box(
        Modifier
            .size(sizeDp.dp)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.45f))
            .pointerInput(Unit) { detectTapGestures(onTap = { onClick() }) },
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, null, tint = Color.White, modifier = Modifier.size((sizeDp / 2).dp))
    }
}

private fun clock(ms: Long): String =
    java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(java.util.Date(ms))

@Suppress("unused")
private val keepWidth = Modifier.width(0.dp)
