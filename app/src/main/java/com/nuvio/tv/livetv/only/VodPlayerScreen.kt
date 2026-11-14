package com.nuvio.tv.livetv.only

import android.view.KeyEvent
import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import androidx.tv.material3.Text

/**
 * Live TV only build: plays a movie or an episode from Movies & Series. The usual remote keys
 * work (OK shows the bar, left/right seek, play/pause); Back closes it.
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

    val player = remember {
        val ua = playable.headers["User-Agent"]
        val http = DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(20_000)
            .setReadTimeoutMs(30_000)
            .setDefaultRequestProperties(playable.headers.filterKeys { !it.equals("User-Agent", ignoreCase = true) })
            .apply { if (!ua.isNullOrBlank()) setUserAgent(ua) }
        val renderers = DefaultRenderersFactory(context)
            // ffmpeg for AC3 / E-AC3 / DTS audio the TV can't play itself.
            .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER)
            .setEnableDecoderFallback(true)
        ExoPlayer.Builder(context, renderers)
            .setMediaSourceFactory(DefaultMediaSourceFactory(http))
            .build()
            .apply {
                setMediaItem(MediaItem.fromUri(playable.url))
                playWhenReady = true
                prepare()
            }
    }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlayerError(e: PlaybackException) {
                error = "Couldn't play this title (${e.errorCodeName})."
            }
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_READY) error = null
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }

    BackHandler { onClose() }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                PlayerView(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                    this.player = player
                    useController = true
                    controllerShowTimeoutMs = 4000
                    setShowNextButton(false)
                    setShowPreviousButton(false)
                    keepScreenOn = true
                    isFocusable = true
                    isFocusableInTouchMode = true
                    // Remote: any key wakes the bar; Play/Pause works even while it's hidden.
                    setOnKeyListener { _, keyCode, event ->
                        if (event.action != KeyEvent.ACTION_DOWN) return@setOnKeyListener false
                        when (keyCode) {
                            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, KeyEvent.KEYCODE_MEDIA_PLAY, KeyEvent.KEYCODE_MEDIA_PAUSE -> {
                                player.playWhenReady = !player.playWhenReady; showController(); true
                            }
                            KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> { player.seekForward(); showController(); true }
                            KeyEvent.KEYCODE_MEDIA_REWIND -> { player.seekBack(); showController(); true }
                            KeyEvent.KEYCODE_BACK -> false
                            else -> {
                                if (!isControllerFullyVisible) { showController(); true } else false
                            }
                        }
                    }
                    requestFocus()
                }
            }
        )
        Text(
            playable.title,
            color = Color.White,
            fontSize = 16.sp,
            modifier = Modifier.align(Alignment.TopStart).padding(24.dp)
                .background(Color(0x99000000)).padding(horizontal = 10.dp, vertical = 6.dp)
        )
        error?.let {
            Text(
                it,
                color = Color.White,
                fontSize = 16.sp,
                modifier = Modifier.align(Alignment.Center)
                    .background(Color(0xCC000000)).padding(horizontal = 18.dp, vertical = 12.dp)
            )
        }
    }
}
