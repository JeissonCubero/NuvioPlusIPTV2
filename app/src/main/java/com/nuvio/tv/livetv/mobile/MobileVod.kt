package com.nuvio.tv.livetv.mobile

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.nuvio.tv.R
import com.nuvio.tv.livetv.ondemand.OnDemandDatabase
import com.nuvio.tv.livetv.ondemand.VodEpisode
import com.nuvio.tv.livetv.ondemand.VodItem
import com.nuvio.tv.livetv.ondemand.VodKind
import com.nuvio.tv.livetv.only.VodViewModel

/**
 * Phone: Movies & Series. Movies / Series switch, a search box, categories as chips, posters in a
 * grid. Tap a movie to play it, a series to see its episodes.
 */
@Composable
fun MobileVodScreen(onPlay: () -> Unit, vm: VodViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()

    val series = state.series
    if (series != null) {
        EpisodesList(
            series = series,
            episodes = state.episodes,
            loading = state.episodesLoading,
            onBack = vm::closeSeries,
            onPlay = { ep -> vm.playEpisode(series, ep, onPlay) }
        )
        return
    }

    Column(Modifier.fillMaxSize()) {
        Text(
            stringResource(R.string.livetv_only_menu_vod),
            color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 16.dp, top = 14.dp, bottom = 8.dp)
        )
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            listOf(VodKind.MOVIE to R.string.livetv_only_vod_movies, VodKind.SERIES to R.string.livetv_only_vod_series)
                .forEachIndexed { i, (kind, label) ->
                    SegmentedButton(
                        selected = state.kind == kind,
                        onClick = { vm.selectKind(kind) },
                        shape = SegmentedButtonDefaults.itemShape(i, 2),
                        colors = SegmentedButtonDefaults.colors(
                            activeContainerColor = Color.White, activeContentColor = Color.Black,
                            inactiveContainerColor = Color.Transparent, inactiveContentColor = Color.White
                        )
                    ) { Text(stringResource(label)) }
                }
        }
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            value = state.query,
            onValueChange = vm::setQuery,
            singleLine = true,
            placeholder = {
                Text(stringResource(if (state.kind == VodKind.MOVIE) R.string.livetv_only_vod_search_movies else R.string.livetv_only_vod_search_series))
            },
            leadingIcon = { Icon(Icons.Default.Search, null) },
            trailingIcon = {
                if (state.query.isNotEmpty()) IconButton(onClick = { vm.setQuery("") }) { Icon(Icons.Default.Close, null) }
            },
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White, unfocusedTextColor = Color.White,
                focusedBorderColor = Color.White, unfocusedBorderColor = Color(0xFF3A3A42),
                cursorColor = Color.White
            ),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
        )
        Spacer(Modifier.height(8.dp))
        if (state.query.isBlank()) {
            LazyRow(contentPadding = PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item(key = "all") {
                    Chip(stringResource(R.string.livetv_only_vod_all), state.category == null) { vm.selectCategory(null) }
                }
                items(state.categories, key = { it.uid }) { c ->
                    Chip(c.name, state.category?.uid == c.uid) { vm.selectCategory(c) }
                }
            }
            Spacer(Modifier.height(6.dp))
        }
        when {
            !state.hasContent && !state.loading -> EmptyNote(stringResource(R.string.livetv_only_vod_empty))
            state.items.isEmpty() && !state.loading && state.query.isNotBlank() ->
                EmptyNote(stringResource(R.string.livetv_only_vod_no_results, state.query.trim()))
            state.items.isEmpty() && !state.loading -> EmptyNote(stringResource(R.string.livetv_only_vod_nothing_here))
            else -> {
                val grid = rememberLazyGridState()
                val nearEnd by remember {
                    derivedStateOf {
                        val last = grid.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                        last >= grid.layoutInfo.totalItemsCount - 12
                    }
                }
                LaunchedEffect(nearEnd, state.items.size) { if (nearEnd) vm.loadMore() }
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(108.dp),
                    state = grid,
                    contentPadding = PaddingValues(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(state.items, key = { it.uid }) { item ->
                        Poster(item) {
                            if (item.kind == VodKind.MOVIE) vm.playMovie(item, onPlay) else vm.openSeries(item)
                        }
                    }
                }
            }
        }
    }

    state.message?.let { msg ->
        LaunchedEffect(msg) { kotlinx.coroutines.delay(3500); vm.clearMessage() }
        Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.BottomCenter) {
            Text(msg, color = Color.White, fontSize = 14.sp,
                modifier = Modifier.background(Color(0xE6222222), RoundedCornerShape(10.dp)).padding(horizontal = 16.dp, vertical = 10.dp))
        }
    }
}

@Composable
private fun Chip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label, maxLines = 1) },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = Color.White, selectedLabelColor = Color.Black, labelColor = Color(0xFFDDDDE2)
        )
    )
}

@Composable
private fun Poster(item: VodItem, onClick: () -> Unit) {
    Column(Modifier.clickable(onClick = onClick)) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(2f / 3f).clip(RoundedCornerShape(8.dp)).background(Color(0xFF1E1E22)),
            contentAlignment = Alignment.Center
        ) {
            Text(OnDemandDatabase.displayTitle(item.name), color = Color(0xFF9A9AA2), fontSize = 12.sp, maxLines = 4,
                modifier = Modifier.padding(8.dp))
            if (!item.icon.isNullOrBlank()) {
                AsyncImage(model = item.icon, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(OnDemandDatabase.displayTitle(item.name), color = Color.White, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        item.year?.let { Text(it.toString(), color = Color(0xFF9A9AA2), fontSize = 11.sp) }
    }
}

@Composable
private fun EpisodesList(
    series: VodItem,
    episodes: List<VodEpisode>,
    loading: Boolean,
    onBack: () -> Unit,
    onPlay: (VodEpisode) -> Unit
) {
    BackHandler(onBack = onBack)
    val seasons = remember(episodes) { episodes.map { it.season }.distinct() }
    var season by androidx.compose.runtime.remember(seasons) { androidx.compose.runtime.mutableStateOf(seasons.firstOrNull()) }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = Color.White) }
            Text(OnDemandDatabase.displayTitle(series.name), color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Row(Modifier.padding(horizontal = 16.dp)) {
            AsyncImage(model = series.icon, contentDescription = null, contentScale = ContentScale.Crop,
                modifier = Modifier.width(90.dp).aspectRatio(2f / 3f).clip(RoundedCornerShape(8.dp)))
            Spacer(Modifier.width(12.dp))
            Column {
                series.year?.let { Text(it.toString(), color = Color(0xFFA8A8B0), fontSize = 13.sp) }
                Text(stringResource(R.string.livetv_only_mobile_episodes_count, episodes.size), color = Color(0xFFA8A8B0), fontSize = 13.sp)
            }
        }
        Spacer(Modifier.height(10.dp))
        if (loading) {
            EmptyNote(stringResource(R.string.livetv_only_vod_loading_episodes))
            return@Column
        }
        LazyRow(contentPadding = PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(seasons, key = { it }) { s ->
                Chip(stringResource(R.string.livetv_only_vod_season, s), s == season) { season = s }
            }
        }
        LazyColumn(contentPadding = PaddingValues(bottom = 16.dp)) {
            items(episodes.filter { it.season == season }, key = { "${it.season}:${it.episode}:${it.id}" }) { ep ->
                Text(
                    "${ep.episode}. " + ep.title.ifBlank { "Episode ${ep.episode}" },
                    color = Color.White, fontSize = 15.sp, maxLines = 2, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth().clickable { onPlay(ep) }.padding(horizontal = 16.dp, vertical = 14.dp)
                )
            }
        }
    }
}
