package com.nuvio.tv.livetv.only

import android.view.LayoutInflater
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import androidx.tv.material3.Text
import com.nuvio.tv.R
import com.nuvio.tv.ui.screens.settings.MemoryBudget
import kotlinx.coroutines.delay

/**
 * Live TV only build: plays a movie or an episode from Movies & Series.
 * Remote: OK = play / pause, Left / Right = back / forward 10 s (hold to go faster),
 * Up / Down = show the bar, Back = close.
 *
 * The picture is Live TV's own player view (the app's player layout has no built-in controls,
 * so PlayerView's controller can't be used), with a small bar drawn on top.
 */
@OptIn(UnstableApi::class)
@Composable
fun VodPlayerScreen(onClose: () -> Unit) {
    val playable = remember { VodLaunch.pending }
    if (playable == null) {
        LaunchedEffect(Unit) { onClose() }
        return
    }
    val context = LocalContext.current
    var error by remember { mutableStateOf<String?>(null) }
    var playing by remember { mutableStateOf(true) }
    var buffering by remember { mutableStateOf(true) }
    var position by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var barShownAt by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var barTick by remember { mutableIntStateOf(0) }
    val focus = remember { FocusRequester() }

    val player = remember {
        val ua = playable.headers["User-Agent"]
        val http = DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(20_000)
            .setReadTimeoutMs(30_000)
            .setDefaultRequestProperties(playable.headers.filterKeys { !it.equals("User-Agent", ignoreCase = true) })
            .apply { if (!ua.isNullOrBlank()) setUserAgent(ua) }
        val renderers = DefaultRenderersFactory(context)
            // ffmpeg for AC3 / E-AC3 / DTS audio the TV can't play itself (same as Live TV).
            .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)
            .setEnableDecoderFallback(true)
        // Download-ahead kept to what a cheap TV stick can hold (like Live TV's player).
        val heapMb = MemoryBudget.heapMb.takeIf { it > 0 } ?: 512L
        val bufferBytes = ((heapMb shl 20) * 3 / 10).coerceIn(32L shl 20, 160L shl 20).toInt()
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(25_000, 60_000, 2_000, 4_000)
            .setPrioritizeTimeOverSizeThresholds(true)
            .setTargetBufferBytes(bufferBytes)
            .build()
        ExoPlayer.Builder(context, renderers)
            .setMediaSourceFactory(DefaultMediaSourceFactory(http))
            .setLoadControl(loadControl)
            .setAudioAttributes(
                AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(),
                true
            )
            .build()
            .apply {
                setMediaItem(MediaItem.fromUri(playable.url))
                playWhenReady = true
                prepare()
            }
    }

    // Live TV's preview (or full screen) stops while a movie plays: one picture at a time.
    LaunchedEffect(Unit) {
        runCatching {
            dagger.hilt.android.EntryPointAccessors
                .fromApplication(context.applicationContext, VodPlayerEntryPoint::class.java)
                .liveTvPlayback().stop()
        }
    }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlayerError(e: PlaybackException) {
                error = "Couldn't play this title (${e.errorCodeName})."
                barShownAt = System.currentTimeMillis()
            }
            override fun onPlaybackStateChanged(state: Int) {
                buffering = state == Player.STATE_BUFFERING
                if (state == Player.STATE_READY) {
                    error = null
                    duration = player.duration.takeIf { it > 0 } ?: 0L
                }
                if (state == Player.STATE_ENDED) onClose()
            }
            override fun onIsPlayingChanged(isPlaying: Boolean) { playing = isPlaying }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }

    // Time and bar visibility.
    LaunchedEffect(player) {
        while (true) {
            position = player.currentPosition.coerceAtLeast(0L)
            if (duration <= 0L) duration = player.duration.takeIf { it > 0 } ?: 0L
            barTick++
            delay(500)
        }
    }
    val barVisible = !playing || error != null || barTick < 0 || System.currentTimeMillis() - barShownAt < 4_000

    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    BackHandler { onClose() }

    fun showBar() { barShownAt = System.currentTimeMillis() }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(focus)
            .onPreviewKeyEvent { e ->
                if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (e.key) {
                    Key.DirectionCenter, Key.Enter, Key.NumPadEnter, Key.MediaPlayPause, Key.Spacebar -> {
                        player.playWhenReady = !player.playWhenReady; showBar(); true
                    }
                    Key.MediaPlay -> { player.playWhenReady = true; showBar(); true }
                    Key.MediaPause -> { player.playWhenReady = false; showBar(); true }
                    Key.DirectionRight, Key.MediaFastForward -> {
                        // Held down: bigger jumps the longer it's held.
                        val step = if (e.nativeKeyEvent.repeatCount > 10) 60_000L else 10_000L
                        val end = player.duration.takeIf { it > 0 } ?: Long.MAX_VALUE
                        player.seekTo((player.currentPosition + step).coerceAtMost(end))
                        showBar(); true
                    }
                    Key.DirectionLeft, Key.MediaRewind -> {
                        val step = if (e.nativeKeyEvent.repeatCount > 10) 60_000L else 10_000L
                        player.seekTo((player.currentPosition - step).coerceAtLeast(0L))
                        showBar(); true
                    }
                    Key.DirectionUp, Key.DirectionDown -> { showBar(); true }
                    else -> false
                }
            }
            .focusable()
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                (LayoutInflater.from(ctx).inflate(R.layout.live_tv_surface_player_view, null) as PlayerView).apply {
                    this.player = player
                    keepScreenOn = true
                }
            },
            onRelease = { it.player = null }
        )

        if (buffering && error == null) {
            Text("Loading…", color = Color.White, fontSize = 16.sp, modifier = Modifier.align(Alignment.Center))
        }
        error?.let {
            Text(
                it,
                color = Color.White,
                fontSize = 16.sp,
                modifier = Modifier.align(Alignment.Center)
                    .background(Color(0xCC000000), RoundedCornerShape(10.dp))
                    .padding(horizontal = 18.dp, vertical = 12.dp)
            )
        }

        AnimatedVisibility(
            visible = barVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xE6000000))))
                    .padding(horizontal = 48.dp, vertical = 28.dp)
            ) {
                Text(playable.title, color = Color.White, fontSize = 20.sp, maxLines = 1)
                Spacer(Modifier.height(14.dp))
                val fraction = if (duration > 0) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f
                Box(
                    Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp))
                        .background(Color.White.copy(alpha = 0.25f))
                ) {
                    Box(Modifier.fillMaxWidth(fraction).height(6.dp).background(Color.White))
                }
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(if (playing) "❚❚" else "▶", color = Color.White, fontSize = 18.sp)
                    Spacer(Modifier.width(14.dp))
                    Text(
                        "${clock(position)} / ${if (duration > 0) clock(duration) else "--:--"}",
                        color = Color.White.copy(alpha = 0.85f),
                        fontSize = 15.sp
                    )
                    Spacer(Modifier.weight(1f))
                    Text("◀ ▶  10 s     OK  ▶/❚❚", color = Color.White.copy(alpha = 0.55f), fontSize = 13.sp)
                }
            }
        }
    }
}

@dagger.hilt.EntryPoint
@dagger.hilt.InstallIn(dagger.hilt.components.SingletonComponent::class)
interface VodPlayerEntryPoint {
    fun liveTvPlayback(): com.nuvio.tv.livetv.player.LiveTvPlaybackController
}

private fun clock(ms: Long): String {
    val total = ms / 1000
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}
