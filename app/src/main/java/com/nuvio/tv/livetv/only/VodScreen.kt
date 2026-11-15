package com.nuvio.tv.livetv.only

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.nuvio.tv.R
import com.nuvio.tv.livetv.ondemand.OnDemandDatabase
import com.nuvio.tv.livetv.ondemand.VodEpisode
import com.nuvio.tv.livetv.ondemand.VodItem
import com.nuvio.tv.livetv.ondemand.VodKind
import com.nuvio.tv.ui.theme.NuvioTheme

/**
 * Live TV only build: Movies & Series from your provider (Xtream / Stalker logins with
 * "Import On Demand" on). Left: Movies / Series and the provider's categories. Right: posters.
 * A movie plays straight away; a series opens its episodes.
 */
@Composable
fun VodScreen(
    onPlay: () -> Unit,
    viewModel: VodViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val firstFocus = remember { FocusRequester() }
    // The title you opened last gets the highlight back (after a movie, or closing a series).
    val restoreFocus = remember { FocusRequester() }
    val gridState = rememberLazyGridState()
    val seriesOpen = state.series != null
    LaunchedEffect(seriesOpen) {
        if (seriesOpen) return@LaunchedEffect
        kotlinx.coroutines.delay(60)
        val restored = viewModel.lastOpenedUid != null && runCatching { restoreFocus.requestFocus() }.isSuccess
        if (!restored) runCatching { firstFocus.requestFocus() }
    }

    Box(Modifier.fillMaxSize().background(NuvioTheme.colors.Background)) {
        // While a series' episodes are open, only that panel is on screen: nothing behind it can
        // take the highlight (Down used to slide into the categories under the panel).
        if (!seriesOpen) Row(Modifier.fillMaxSize().padding(start = 32.dp, top = 28.dp, end = 24.dp)) {
            // ---- Movies / Series and categories
            Column(Modifier.width(260.dp).fillMaxHeight()) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Pill(
                        stringResource(R.string.livetv_only_vod_movies),
                        selected = state.kind == VodKind.MOVIE,
                        modifier = Modifier.focusRequester(firstFocus)
                    ) { viewModel.selectKind(VodKind.MOVIE) }
                    Pill(stringResource(R.string.livetv_only_vod_series), selected = state.kind == VodKind.SERIES) {
                        viewModel.selectKind(VodKind.SERIES)
                    }
                }
                Spacer(Modifier.height(12.dp))
                SearchBox(
                    value = state.query,
                    hint = stringResource(
                        if (state.kind == VodKind.MOVIE) R.string.livetv_only_vod_search_movies else R.string.livetv_only_vod_search_series
                    ),
                    onChange = viewModel::setQuery
                )
                Spacer(Modifier.height(12.dp))
                LazyColumn(verticalArrangement = Arrangement.spacedBy(2.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
                    item(key = "all") {
                        ListRow(stringResource(R.string.livetv_only_vod_all), null, selected = state.category == null && state.query.isBlank()) {
                            viewModel.selectCategory(null)
                        }
                    }
                    items(state.categories, key = { it.uid }) { c ->
                        ListRow(c.name, c.count.toString(), selected = state.category?.uid == c.uid && state.query.isBlank()) {
                            viewModel.selectCategory(c)
                        }
                    }
                }
            }
            Spacer(Modifier.width(24.dp))
            // ---- Posters
            Box(Modifier.weight(1f).fillMaxHeight()) {
                when {
                    !state.hasContent && !state.loading -> EmptyNote(stringResource(R.string.livetv_only_vod_empty))
                    state.items.isEmpty() && !state.loading && state.query.isNotBlank() ->
                        EmptyNote(stringResource(R.string.livetv_only_vod_no_results, state.query.trim()))
                    state.items.isEmpty() && !state.loading -> EmptyNote(stringResource(R.string.livetv_only_vod_nothing_here))
                    else -> PosterGrid(
                        items = state.items,
                        gridState = gridState,
                        restoreUid = viewModel.lastOpenedUid,
                        restoreFocus = restoreFocus,
                        onEnd = viewModel::loadMore,
                        onOpen = { item ->
                            viewModel.lastOpenedUid = item.uid
                            if (item.kind == VodKind.MOVIE) viewModel.playMovie(item, onPlay) else viewModel.openSeries(item)
                        }
                    )
                }
            }
        }

        state.series?.let { series ->
            EpisodesPanel(
                series = series,
                episodes = state.episodes,
                loading = state.episodesLoading,
                lastEpisodeKey = viewModel.lastEpisodeKey,
                onPlay = { ep ->
                    viewModel.lastEpisodeKey = episodeKey(ep)
                    viewModel.playEpisode(series, ep, onPlay)
                },
                onClose = { viewModel.lastEpisodeKey = null; viewModel.closeSeries() }
            )
        }

        state.message?.let { msg ->
            LaunchedEffect(msg) {
                kotlinx.coroutines.delay(3500)
                viewModel.clearMessage()
            }
            Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.BottomCenter) {
                Text(
                    msg,
                    color = Color.White,
                    fontSize = 15.sp,
                    modifier = Modifier
                        .background(Color(0xE6222222), RoundedCornerShape(10.dp))
                        .padding(horizontal = 18.dp, vertical = 10.dp)
                )
            }
        }
    }
}

@Composable
private fun PosterGrid(
    items: List<VodItem>,
    gridState: androidx.compose.foundation.lazy.grid.LazyGridState,
    restoreUid: String?,
    restoreFocus: FocusRequester,
    onEnd: () -> Unit,
    onOpen: (VodItem) -> Unit
) {
    val nearEnd by remember {
        derivedStateOf {
            val last = gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            last >= gridState.layoutInfo.totalItemsCount - 12
        }
    }
    LaunchedEffect(nearEnd, items.size) { if (nearEnd) onEnd() }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(150.dp),
        state = gridState,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(top = 4.dp, bottom = 32.dp, end = 8.dp)
    ) {
        itemsIndexed(items, key = { _, it -> it.uid }) { _, item ->
            PosterCard(item, if (item.uid == restoreUid) restoreFocus else null) { onOpen(item) }
        }
    }
}

@Composable
private fun PosterCard(item: VodItem, focusRequester: FocusRequester?, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(10.dp)
    Column(
        Modifier
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .onFocusChanged { focused = it.isFocused }
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .clip(shape)
                .background(Color(0xFF1E1E1E))
                .border(if (focused) 3.dp else 0.dp, if (focused) NuvioTheme.colors.FocusRing else Color.Transparent, shape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                OnDemandDatabase.displayTitle(item.name),
                color = Color(0xFFBBBBBB),
                fontSize = 13.sp,
                maxLines = 4,
                modifier = Modifier.padding(10.dp)
            )
            if (!item.icon.isNullOrBlank()) {
                AsyncImage(
                    model = item.icon,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            OnDemandDatabase.displayTitle(item.name),
            color = if (focused) NuvioTheme.colors.TextPrimary else NuvioTheme.colors.TextSecondary,
            fontSize = 13.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        item.year?.let { Text(it.toString(), color = NuvioTheme.colors.TextSecondary, fontSize = 11.sp) }
    }
}

@Composable
private fun EpisodesPanel(
    series: VodItem,
    episodes: List<VodEpisode>,
    loading: Boolean,
    lastEpisodeKey: String?,
    onPlay: (VodEpisode) -> Unit,
    onClose: () -> Unit
) {
    BackHandler(onBack = onClose)
    val seasons = remember(episodes) { episodes.map { it.season }.distinct() }
    // Back from an episode: its season, with the highlight on it.
    val lastEpisode = remember(episodes, lastEpisodeKey) { episodes.firstOrNull { episodeKey(it) == lastEpisodeKey } }
    var season by remember(seasons) { mutableStateOf(lastEpisode?.season ?: seasons.firstOrNull()) }
    val firstFocus = remember { FocusRequester() }
    val episodeFocus = remember { FocusRequester() }
    LaunchedEffect(seasons.isNotEmpty()) {
        if (seasons.isEmpty()) return@LaunchedEffect
        kotlinx.coroutines.delay(60)
        val onEpisode = lastEpisode != null && runCatching { episodeFocus.requestFocus() }.isSuccess
        if (!onEpisode) runCatching { firstFocus.requestFocus() }
    }

    Box(Modifier.fillMaxSize().background(NuvioTheme.colors.Background)) {
        Row(Modifier.fillMaxSize().padding(40.dp)) {
            Column(Modifier.width(220.dp)) {
                AsyncImage(
                    model = series.icon,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.width(200.dp).aspectRatio(2f / 3f).clip(RoundedCornerShape(10.dp))
                )
                Spacer(Modifier.height(12.dp))
                Text(OnDemandDatabase.displayTitle(series.name), color = Color.White, fontSize = 18.sp, maxLines = 3)
            }
            Spacer(Modifier.width(28.dp))
            Column(Modifier.weight(1f)) {
                if (loading) {
                    Text(stringResource(R.string.livetv_only_vod_loading_episodes), color = NuvioTheme.colors.TextSecondary, fontSize = 16.sp)
                    return@Column
                }
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(seasons, key = { it }) { s ->
                        Pill(
                            stringResource(R.string.livetv_only_vod_season, s),
                            selected = s == season,
                            modifier = if (s == seasons.first()) Modifier.focusRequester(firstFocus) else Modifier
                        ) { season = s }
                    }
                }
                Spacer(Modifier.height(16.dp))
                LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    items(episodes.filter { it.season == season }, key = { episodeKey(it) }) { ep ->
                        ListRow(
                            "${ep.episode}. " + ep.title.ifBlank { "Episode ${ep.episode}" },
                            null,
                            selected = lastEpisode != null && episodeKey(ep) == episodeKey(lastEpisode),
                            height = 52.dp,
                            focusRequester = if (lastEpisode != null && episodeKey(ep) == episodeKey(lastEpisode)) episodeFocus else null
                        ) { onPlay(ep) }
                    }
                }
            }
        }
    }
}

@Composable
private fun Pill(label: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(50)
    Box(
        modifier
            .clip(shape)
            .background(
                when {
                    focused -> Color.White
                    selected -> Color.White.copy(alpha = 0.18f)
                    else -> Color.White.copy(alpha = 0.06f)
                }
            )
            .onFocusChanged { focused = it.isFocused }
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 10.dp)
    ) {
        Text(label, color = if (focused) Color.Black else Color.White, fontSize = 15.sp)
    }
}

@Composable
private fun ListRow(
    label: String,
    trailing: String?,
    selected: Boolean,
    height: Dp = 44.dp,
    focusRequester: FocusRequester? = null,
    onClick: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(10.dp)
    Row(
        Modifier
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .fillMaxWidth()
            .height(height)
            .clip(shape)
            .background(
                when {
                    focused -> NuvioTheme.colors.FocusBackground
                    selected -> Color.White.copy(alpha = 0.08f)
                    else -> Color.Transparent
                }
            )
            .border(if (focused) 2.dp else 0.dp, if (focused) NuvioTheme.colors.FocusRing else Color.Transparent, shape)
            .onFocusChanged { focused = it.isFocused }
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            color = if (focused || selected) NuvioTheme.colors.TextPrimary else NuvioTheme.colors.TextSecondary,
            fontSize = 15.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        trailing?.let { Text(it, color = NuvioTheme.colors.TextSecondary, fontSize = 12.sp) }
    }
}

@Composable
private fun EmptyNote(text: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text, color = NuvioTheme.colors.TextSecondary, fontSize = 16.sp, modifier = Modifier.padding(48.dp))
    }
}

private fun episodeKey(ep: VodEpisode): String = "${ep.season}:${ep.episode}:${ep.id}"

/**
 * Search box. On a TV, a text field takes the keyboard as soon as the highlight lands on it, so
 * this is a button: OK opens the typing, and the keyboard's Search / Done (or Back) ends it.
 */
@Composable
private fun SearchBox(value: String, hint: String, onChange: (String) -> Unit) {
    var focused by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf(false) }
    // After typing, the highlight goes back to the search button.
    var returnFocus by remember { mutableStateOf(false) }
    var hadFieldFocus by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(10.dp)
    val keyboard = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    val fieldFocus = remember { FocusRequester() }
    val buttonFocus = remember { FocusRequester() }
    fun stopEditing() {
        keyboard?.hide()
        returnFocus = true
        editing = false
    }
    val boxModifier = Modifier
        .fillMaxWidth()
        .background(Color.White.copy(alpha = if (focused || editing) 0.14f else 0.06f), shape)
        .border(if (focused || editing) 2.dp else 0.dp, if (focused || editing) NuvioTheme.colors.FocusRing else Color.Transparent, shape)
    if (editing) {
        BackHandler { stopEditing() }
        LaunchedEffect(Unit) {
            hadFieldFocus = false
            // Wait a frame so the field is on screen before it takes the highlight.
            kotlinx.coroutines.delay(50)
            runCatching { fieldFocus.requestFocus() }
            kotlinx.coroutines.delay(50)
            keyboard?.show()
        }
        androidx.compose.foundation.text.BasicTextField(
            value = value,
            onValueChange = onChange,
            singleLine = true,
            textStyle = androidx.compose.ui.text.TextStyle(color = Color.White, fontSize = 15.sp),
            cursorBrush = androidx.compose.ui.graphics.SolidColor(Color.White),
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Search),
            keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                onSearch = { stopEditing() },
                onDone = { stopEditing() }
            ),
            modifier = boxModifier
                .focusRequester(fieldFocus)
                .onFocusChanged {
                    // The field reports "not focused" when it first appears: only leaving it
                    // after it had the highlight ends the typing.
                    if (it.isFocused) hadFieldFocus = true
                    else if (hadFieldFocus && editing) { hadFieldFocus = false; editing = false }
                }
                .padding(horizontal = 14.dp, vertical = 12.dp),
            decorationBox = { inner ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("🔍", fontSize = 14.sp)
                    Spacer(Modifier.width(8.dp))
                    Box(Modifier.weight(1f)) {
                        if (value.isEmpty()) Text(hint, color = NuvioTheme.colors.TextSecondary, fontSize = 15.sp, maxLines = 1)
                        inner()
                    }
                }
            }
        )
    } else {
        LaunchedEffect(Unit) {
            if (returnFocus) {
                returnFocus = false
                kotlinx.coroutines.delay(30)
                runCatching { buttonFocus.requestFocus() }
            }
        }
        Row(
            boxModifier
                .focusRequester(buttonFocus)
                .onFocusChanged { focused = it.isFocused }
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { editing = true }
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("🔍", fontSize = 14.sp)
            Spacer(Modifier.width(8.dp))
            Text(
                value.ifEmpty { hint },
                color = if (value.isEmpty()) NuvioTheme.colors.TextSecondary else Color.White,
                fontSize = 15.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            if (value.isNotEmpty()) Text("✕", color = NuvioTheme.colors.TextSecondary, fontSize = 14.sp)
        }
    }
}
