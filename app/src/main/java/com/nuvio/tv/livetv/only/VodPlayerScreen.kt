package com.nuvio.tv.livetv.only

import android.view.LayoutInflater
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import com.nuvio.tv.livetv.ui.glass
import com.nuvio.tv.livetv.ui.glassColor
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.focus.onFocusChanged
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
 * Remote: bar hidden: ◀ ▶ seek 10 s (hold: 1 min), OK / ▲ / ▼ show the bar.
 * Bar up: the progress bar has the highlight (◀ ▶ seek, OK play/pause); ▼ goes to ⏯ and subtitles,
 * ▲ back to the progress bar; ▼ again or Back hides the bar. Back with the bar hidden closes the player.
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
    // Subtitles & audio panel (Up opens it).
    var tracksOpen by remember { mutableStateOf(false) }
    var tracksVersion by remember { mutableIntStateOf(0) }

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
            // No falling back to a software video decoder: on a TV that can't play a 4K / HEVC
            // file in hardware, that froze the picture and the whole app. It now says so instead.
            .setEnableDecoderFallback(false)
        // Download-ahead kept to what a cheap TV stick can hold (like Live TV's player).
        val heapMb = MemoryBudget.heapMb.takeIf { it > 0 } ?: 512L
        val bufferBytes = ((heapMb shl 20) * 3 / 10).coerceIn(32L shl 20, 160L shl 20).toInt()
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(25_000, 60_000, 2_000, 4_000)
            // The memory cap wins over "seconds ahead": a 4K file (40-80 Mbit/s) used to fill the
            // memory trying to hold 25 s ahead, and the app was closed by Android.
            .setPrioritizeTimeOverSizeThresholds(false)
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
                // Only video this TV says it can decode; the picture is never asked to go over the
                // screen's size when the title offers smaller versions.
                trackSelectionParameters = trackSelectionParameters.buildUpon()
                    .setExceedRendererCapabilitiesIfNecessary(false)
                    .setViewportSizeToPhysicalDisplaySize(context, true)
                    .build()
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
                val decoder = e.errorCode in listOf(
                    PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
                    PlaybackException.ERROR_CODE_DECODER_QUERY_FAILED,
                    PlaybackException.ERROR_CODE_DECODING_FAILED,
                    PlaybackException.ERROR_CODE_DECODING_FORMAT_EXCEEDS_CAPABILITIES,
                    PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED
                )
                error = if (decoder) context.getString(R.string.livetv_only_vod_cant_decode)
                else context.getString(R.string.livetv_only_vod_play_failed, e.errorCodeName)
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
            override fun onTracksChanged(tracks: androidx.media3.common.Tracks) {
                tracksVersion++
                // A title whose video this TV can't decode (typically 4K / HEVC on a 1080p box):
                // stop rather than play sound over a frozen or black picture.
                val video = tracks.groups.filter { it.type == C.TRACK_TYPE_VIDEO }
                if (video.isNotEmpty() && video.none { g -> (0 until g.length).any { g.isTrackSupported(it) } }) {
                    val f = video.first().getTrackFormat(0)
                    val what = listOfNotNull(
                        if (f.height >= 2000 || f.width >= 3800) "4K" else if (f.height > 0) "${f.height}p" else null,
                        f.sampleMimeType?.substringAfter('/')?.uppercase()
                    ).joinToString(" · ")
                    error = context.getString(R.string.livetv_only_vod_cant_decode_what, what.ifBlank { "?" })
                    player.stop()
                }
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }

    // Time.
    LaunchedEffect(player) {
        while (true) {
            position = player.currentPosition.coerceAtLeast(0L)
            if (duration <= 0L) duration = player.duration.takeIf { it > 0 } ?: 0L
            barTick++
            delay(500)
        }
    }

    // The bar works like Live TV's. Two rows you move between with ▲ ▼: the progress bar
    // (◀ ▶ seek, OK play/pause; it's where the highlight starts) and the buttons
    // (⏯ and subtitles & audio). With the bar hidden, ◀ ▶ seek straight away.
    var barFull by remember { mutableStateOf(true) }
    var spot by remember { mutableStateOf(VodSpot.TIMELINE) }
    val now = System.currentTimeMillis() + (barTick * 0L)
    val idle = now - barShownAt
    LaunchedEffect(barTick) {
        // The bar hides after 6 s without a key (not while paused or showing an error).
        if (barFull && playing && error == null && idle > 6_000) barFull = false
    }
    val peek = !barFull && idle < 2_500
    val barVisible = barFull || peek || !playing || error != null

    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    BackHandler(enabled = !tracksOpen) {
        if (barFull) barFull = false else onClose()
    }

    fun touch() { barShownAt = System.currentTimeMillis() }
    fun seekBy(ms: Long) {
        val end = player.duration.takeIf { it > 0 } ?: Long.MAX_VALUE
        player.seekTo((player.currentPosition + ms).coerceIn(0L, end))
        position = player.currentPosition
        touch()
    }
    fun togglePlay() { player.playWhenReady = !player.playWhenReady; touch() }
    fun openBar(at: VodSpot = VodSpot.TIMELINE) { barFull = true; spot = at; touch() }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(focus)
            .onPreviewKeyEvent { e ->
                // The subtitles & audio panel handles its own keys.
                if (tracksOpen) return@onPreviewKeyEvent false
                if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                val held = e.nativeKeyEvent.repeatCount
                val step = if (held > 10) 60_000L else 10_000L
                when (e.key) {
                    Key.MediaPlayPause, Key.Spacebar -> { togglePlay(); true }
                    Key.MediaPlay -> { player.playWhenReady = true; touch(); true }
                    Key.MediaPause -> { player.playWhenReady = false; touch(); true }
                    Key.MediaFastForward -> { seekBy(step); true }
                    Key.MediaRewind -> { seekBy(-step); true }
                    Key.Menu, Key.Captions -> { tracksOpen = true; true }
                    Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> {
                        when {
                            !barFull -> openBar()
                            spot == VodSpot.TRACKS -> { tracksOpen = true; touch() }
                            else -> togglePlay()
                        }
                        true
                    }
                    Key.DirectionLeft -> {
                        when {
                            !barFull || spot == VodSpot.TIMELINE -> seekBy(-step)
                            spot == VodSpot.TRACKS -> { spot = VodSpot.PLAY; touch() }
                            else -> touch()
                        }
                        true
                    }
                    Key.DirectionRight -> {
                        when {
                            !barFull || spot == VodSpot.TIMELINE -> seekBy(step)
                            spot == VodSpot.PLAY -> { spot = VodSpot.TRACKS; touch() }
                            else -> touch()
                        }
                        true
                    }
                    Key.DirectionUp -> {
                        when {
                            !barFull -> openBar()
                            spot != VodSpot.TIMELINE -> { spot = VodSpot.TIMELINE; touch() }
                            else -> touch()
                        }
                        true
                    }
                    Key.DirectionDown -> {
                        when {
                            !barFull -> openBar()
                            spot == VodSpot.TIMELINE -> { spot = VodSpot.PLAY; touch() }
                            else -> barFull = false
                        }
                        true
                    }
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
            androidx.compose.material3.CircularProgressIndicator(
                color = Color.White,
                strokeWidth = 3.dp,
                modifier = Modifier.align(Alignment.Center).size(40.dp)
            )
        }
        error?.let {
            com.nuvio.tv.livetv.ui.LiveText(
                it,
                size = 15.sp,
                color = Color.White,
                maxLines = 3,
                modifier = Modifier.align(Alignment.Center)
                    .glass(glassColor("darker"))
                    .padding(horizontal = 20.dp, vertical = 12.dp)
            )
        }

        AnimatedVisibility(
            visible = barVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            VodBar(
                playable = playable,
                full = barFull || !playing,
                playing = playing,
                spot = if (barFull) spot else null,
                positionMs = position,
                durationMs = duration
            )
        }

        if (tracksOpen) {
            TracksPanel(
                player = player,
                version = tracksVersion,
                onClose = {
                    tracksOpen = false
                    touch()
                    runCatching { focus.requestFocus() }
                }
            )
        }
    }
}

/** Where the highlight is on the bar. */
private enum class VodSpot { TIMELINE, PLAY, TRACKS }

/**
 * The player's bar, in Live TV's style but compact: a slim glass card at the bottom with a small
 * poster, the title and what it is, the progress bar, and ⏯ · subtitles under it.
 * [full] = false: just the title and the progress (after a seek with the bar hidden).
 */
@Composable
private fun VodBar(
    playable: VodPlayable,
    full: Boolean,
    playing: Boolean,
    spot: VodSpot?,
    positionMs: Long,
    durationMs: Long
) {
    val colors = com.nuvio.tv.ui.theme.NuvioTheme.colors
    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 56.dp, vertical = 20.dp)
            .glass(glassColor("standard"), RoundedCornerShape(16.dp))
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (full && !playable.poster.isNullOrBlank()) {
                coil3.compose.AsyncImage(
                    model = playable.poster,
                    contentDescription = null,
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                    modifier = Modifier
                        .height(64.dp)
                        .aspectRatio(2f / 3f)
                        .clip(RoundedCornerShape(6.dp))
                )
                Spacer(Modifier.width(14.dp))
            }
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    com.nuvio.tv.livetv.ui.LiveText(
                        playable.title, size = 15.sp, weight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                        color = colors.TextPrimary, modifier = Modifier.weight(1f, fill = false)
                    )
                    if (playable.subtitle.isNotBlank()) {
                        Spacer(Modifier.width(10.dp))
                        com.nuvio.tv.livetv.ui.LiveText(playable.subtitle, size = 12.sp, color = colors.TextSecondary)
                    }
                }
                Spacer(Modifier.height(6.dp))
                VodTimeline(positionMs, durationMs, grabbed = spot == VodSpot.TIMELINE)
                if (full) {
                    Spacer(Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        VodRoundButton(
                            if (playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                            spot == VodSpot.PLAY
                        )
                        Spacer(Modifier.width(8.dp))
                        VodRoundButton(Icons.Default.ClosedCaption, spot == VodSpot.TRACKS)
                        Spacer(Modifier.width(14.dp))
                        com.nuvio.tv.livetv.ui.LiveText(
                            when (spot) {
                                VodSpot.TIMELINE -> "◀ ▶ seek · OK " + (if (playing) "pause" else "play") + " · ▼ buttons"
                                VodSpot.PLAY -> if (playing) "Pause" else "Play"
                                VodSpot.TRACKS -> "Subtitles & audio"
                                null -> ""
                            },
                            size = 11.sp,
                            color = colors.TextSecondary
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun VodRoundButton(icon: androidx.compose.ui.graphics.vector.ImageVector, focused: Boolean) {
    Box(
        Modifier
            .size(28.dp)
            .clip(CircleShape)
            .background(if (focused) Color.White else Color.White.copy(alpha = 0.12f)),
        contentAlignment = Alignment.Center
    ) {
        androidx.compose.material3.Icon(
            icon,
            contentDescription = null,
            tint = if (focused) Color(0xFF111111) else Color.White,
            modifier = Modifier.size(16.dp)
        )
    }
}

@Composable
private fun VodTimeline(positionMs: Long, durationMs: Long, grabbed: Boolean) {
    val colors = com.nuvio.tv.ui.theme.NuvioTheme.colors
    val frac = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
    Row(verticalAlignment = Alignment.CenterVertically) {
        com.nuvio.tv.livetv.ui.LiveText(clock(positionMs), size = 11.sp, color = if (grabbed) Color.White else colors.TextSecondary)
        Spacer(Modifier.width(10.dp))
        androidx.compose.foundation.layout.BoxWithConstraints(Modifier.weight(1f).height(14.dp)) {
            val w = maxWidth
            val barH = if (grabbed) 5.dp else 4.dp
            Box(Modifier.align(Alignment.CenterStart).fillMaxWidth().height(barH).clip(RoundedCornerShape(3.dp)).background(Color.White.copy(alpha = 0.18f)))
            Box(Modifier.align(Alignment.CenterStart).width(w * frac).height(barH).clip(RoundedCornerShape(3.dp)).background(Color.White))
            if (grabbed) {
                // The highlight is on the bar: a handle where you are (◀ ▶ move it).
                Box(
                    Modifier.align(Alignment.CenterStart).offset(x = w * frac - 7.dp).size(14.dp)
                        .clip(CircleShape).background(Color.White)
                )
            }
        }
        Spacer(Modifier.width(10.dp))
        com.nuvio.tv.livetv.ui.LiveText(
            if (durationMs > 0) "-" + clock((durationMs - positionMs).coerceAtLeast(0L)) else "--:--",
            size = 11.sp,
            color = colors.TextSecondary
        )
    }
}

/** One choice in the subtitles & audio panel. */
private data class TrackChoice(val label: String, val selected: Boolean, val pick: () -> Unit)

/**
 * Right-side panel: Subtitles (Off + every subtitle the title has) and Audio (every language).
 * Up / Down move, OK picks, Back or Left closes.
 */
@OptIn(UnstableApi::class)
@Composable
private fun TracksPanel(player: ExoPlayer, version: Int, onClose: () -> Unit) {
    BackHandler(onBack = onClose)
    val sections = remember(version) { trackSections(player) }
    val flat = sections.flatMap { it.second }
    val requesters = remember(flat.size) { List(flat.size) { FocusRequester() } }
    var index by remember(flat.size) { mutableIntStateOf(flat.indexOfFirst { it.selected }.coerceAtLeast(0)) }
    LaunchedEffect(flat.size) {
        if (flat.isNotEmpty()) runCatching { requesters[index.coerceIn(0, flat.lastIndex)].requestFocus() }
    }
    fun move(to: Int) {
        if (flat.isEmpty()) return
        index = to.coerceIn(0, flat.lastIndex)
        runCatching { requesters[index].requestFocus() }
    }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.CenterEnd) {
        Column(
            Modifier
                .width(380.dp)
                .fillMaxHeight()
                .background(Color(0xF0101010))
                .padding(horizontal = 20.dp, vertical = 28.dp)
                .onPreviewKeyEvent { e ->
                    if (e.type != KeyEventType.KeyDown) {
                        return@onPreviewKeyEvent e.key == Key.DirectionUp || e.key == Key.DirectionDown ||
                            e.key == Key.DirectionLeft || e.key == Key.DirectionRight
                    }
                    when (e.key) {
                        Key.DirectionDown -> { move(index + 1); true }
                        Key.DirectionUp -> { move(index - 1); true }
                        Key.DirectionLeft -> { onClose(); true }
                        Key.DirectionRight -> true
                        Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> {
                            flat.getOrNull(index)?.pick?.invoke(); onClose(); true
                        }
                        else -> false
                    }
                }
                .verticalScroll(rememberScrollState())
        ) {
            if (flat.isEmpty()) {
                Text("This title has no subtitles or other audio.", color = Color.White, fontSize = 15.sp)
            }
            var i = 0
            sections.forEach { (title, choices) ->
                Text(title, color = Color.White.copy(alpha = 0.6f), fontSize = 13.sp, modifier = Modifier.padding(top = 10.dp, bottom = 6.dp))
                choices.forEach { c ->
                    val myIndex = i++
                    TrackRow(c, requesters[myIndex], onFocused = { index = myIndex })
                }
            }
        }
    }
}

@Composable
private fun TrackRow(choice: TrackChoice, focusRequester: FocusRequester, onFocused: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Row(
        Modifier
            .fillMaxWidth()
            .focusRequester(focusRequester)
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onFocused()
            }
            .focusable()
            .clip(RoundedCornerShape(10.dp))
            .background(if (focused) Color.White else Color.Transparent)
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            if (choice.selected) "✓" else "",
            color = if (focused) Color.Black else Color.White,
            fontSize = 15.sp,
            modifier = Modifier.width(24.dp)
        )
        Text(choice.label, color = if (focused) Color.Black else Color.White, fontSize = 15.sp, maxLines = 1)
    }
}

/** The title's subtitle and audio tracks, as choices that switch the player when picked. */
@OptIn(UnstableApi::class)
private fun trackSections(player: ExoPlayer): List<Pair<String, List<TrackChoice>>> {
    val groups = player.currentTracks.groups
    val params = player.trackSelectionParameters
    val textOff = params.disabledTrackTypes.contains(C.TRACK_TYPE_TEXT)
    val subs = mutableListOf<TrackChoice>()
    val audio = mutableListOf<TrackChoice>()
    val anyTextSelected = groups.any { it.type == C.TRACK_TYPE_TEXT && it.isSelected }
    subs += TrackChoice("Off", selected = textOff || !anyTextSelected) {
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .clearOverridesOfType(C.TRACK_TYPE_TEXT)
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
            .build()
    }
    groups.forEach { g ->
        if (g.type != C.TRACK_TYPE_TEXT && g.type != C.TRACK_TYPE_AUDIO) return@forEach
        for (t in 0 until g.length) {
            if (!g.isTrackSupported(t)) continue
            val label = trackLabel(g.getTrackFormat(t), if (g.type == C.TRACK_TYPE_TEXT) subs.size else audio.size + 1)
            val selected = g.isTrackSelected(t) && !(g.type == C.TRACK_TYPE_TEXT && textOff)
            val choice = TrackChoice(label, selected) {
                player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
                    .setTrackTypeDisabled(g.type, false)
                    .setOverrideForType(androidx.media3.common.TrackSelectionOverride(g.mediaTrackGroup, t))
                    .build()
            }
            if (g.type == C.TRACK_TYPE_TEXT) subs += choice else audio += choice
        }
    }
    val out = mutableListOf<Pair<String, List<TrackChoice>>>()
    if (subs.size > 1) out += "Subtitles" to subs
    if (audio.size > 1) out += "Audio" to audio
    return out
}

private fun trackLabel(f: androidx.media3.common.Format, n: Int): String {
    val lang = f.language?.takeIf { it.isNotBlank() && it != "und" }?.let {
        runCatching { java.util.Locale.forLanguageTag(it).getDisplayLanguage(java.util.Locale.getDefault()) }
            .getOrNull()?.takeIf { d -> d.isNotBlank() }?.replaceFirstChar { c -> c.titlecase() } ?: it
    }
    val name = f.label?.takeIf { it.isNotBlank() }
    val extra = buildList {
        if (f.channelCount > 0) add(if (f.channelCount >= 6) "5.1" else if (f.channelCount == 2) "Stereo" else "${f.channelCount} ch")
        if ((f.selectionFlags and C.SELECTION_FLAG_FORCED) != 0) add("Forced")
    }
    val base = listOfNotNull(lang, name?.takeIf { it != lang }).joinToString(" · ").ifBlank { "Track $n" }
    return if (extra.isEmpty()) base else "$base (${extra.joinToString(", ")})"
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
