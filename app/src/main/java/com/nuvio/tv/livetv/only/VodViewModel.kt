package com.nuvio.tv.livetv.only

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nuvio.tv.livetv.data.LiveTvPreferences
import com.nuvio.tv.livetv.data.LiveTvRepository
import com.nuvio.tv.livetv.ondemand.OnDemandDatabase
import com.nuvio.tv.livetv.ondemand.OnDemandRepository
import com.nuvio.tv.livetv.ondemand.VodCategory
import com.nuvio.tv.livetv.ondemand.VodEpisode
import com.nuvio.tv.livetv.ondemand.VodItem
import com.nuvio.tv.livetv.ondemand.VodKind
import com.nuvio.tv.livetv.parental.ParentalControls
import com.nuvio.tv.livetv.stalker.StalkerGateway
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** What the player opens: a link, the headers it needs, and a title for the top of the screen. */
data class VodPlayable(val url: String, val headers: Map<String, String>, val title: String)

/** Hands the chosen title to the player screen (no need to squeeze links into the route). */
object VodLaunch {
    @Volatile var pending: VodPlayable? = null
}

data class VodUiState(
    val kind: VodKind = VodKind.MOVIE,
    val categories: List<VodCategory> = emptyList(),
    /** null = everything of this kind. */
    val category: VodCategory? = null,
    val items: List<VodItem> = emptyList(),
    val loading: Boolean = true,
    val endReached: Boolean = false,
    val hasContent: Boolean = false,
    /** A series opened to pick an episode. */
    val series: VodItem? = null,
    val episodes: List<VodEpisode> = emptyList(),
    val episodesLoading: Boolean = false,
    val message: String? = null
)

/**
 * Live TV only build: Movies & Series, straight from your Xtream / Stalker logins
 * (what Nuvio + IPTV called On Demand). The import itself is On Demand's.
 */
@HiltViewModel
class VodViewModel @Inject constructor(
    private val repo: OnDemandRepository,
    private val prefs: LiveTvPreferences,
    private val stalker: StalkerGateway
) : ViewModel() {

    private val _state = MutableStateFlow(VodUiState())
    val state: StateFlow<VodUiState> = _state.asStateFlow()
    private var loadJob: Job? = null

    init {
        viewModelScope.launch {
            repo.hasContent.collect { has ->
                _state.update { it.copy(hasContent = has) }
                if (has && _state.value.categories.isEmpty()) reload()
                if (!has) _state.update { it.copy(loading = false) }
            }
        }
        // New import finished (playlist refreshed): show what's new.
        viewModelScope.launch {
            var first = true
            repo.version.collect {
                if (first) { first = false; return@collect }
                reload()
            }
        }
    }

    fun selectKind(kind: VodKind) {
        if (kind == _state.value.kind) return
        _state.update { it.copy(kind = kind, category = null, categories = emptyList(), items = emptyList()) }
        reload()
    }

    fun selectCategory(category: VodCategory?) {
        if (category?.uid == _state.value.category?.uid && _state.value.items.isNotEmpty()) return
        _state.update { it.copy(category = category, items = emptyList(), endReached = false) }
        loadItems(reset = true)
    }

    fun loadMore() {
        val s = _state.value
        if (s.loading || s.endReached) return
        loadItems(reset = false)
    }

    private fun reload() {
        viewModelScope.launch {
            val kind = _state.value.kind
            val hidden = hiddenAndLocked()
            val cats = repo.categories(kind).filter { it.uid !in hidden && it.count > 0 }
            _state.update { s ->
                s.copy(categories = cats, category = s.category?.takeIf { c -> cats.any { it.uid == c.uid } })
            }
            loadItems(reset = true)
        }
    }

    /** Categories hidden in Live TV's settings, plus locked ones (parental PIN). */
    private suspend fun hiddenAndLocked(): Set<String> {
        val user = prefs.userState.first()
        val settings = prefs.settings.first()
        val out = HashSet(user.vodHiddenCategories)
        repo.categories(_state.value.kind).forEach { c ->
            if (ParentalControls.isLocked("vod:${c.uid}", c.name, settings, user.lockedGroups)) out += c.uid
        }
        return out
    }

    private fun loadItems(reset: Boolean) {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            val s = _state.value
            _state.update { it.copy(loading = true) }
            val hidden = hiddenAndLocked()
            val offset = if (reset) 0 else s.items.size
            val page = repo.items(
                kind = s.kind, category = s.category, hidden = hidden, newestFirst = s.category == null,
                limit = PAGE, offset = offset
            )
            _state.update {
                it.copy(
                    items = if (reset) page else it.items + page,
                    loading = false,
                    endReached = page.size < PAGE
                )
            }
        }
    }

    fun openSeries(item: VodItem) {
        _state.update { it.copy(series = item, episodes = emptyList(), episodesLoading = true) }
        viewModelScope.launch {
            val info = runCatching { repo.info(item) }.getOrNull()
            val episodes = info?.episodes.orEmpty().sortedWith(compareBy({ it.season }, { it.episode }))
            _state.update {
                it.copy(
                    episodes = episodes,
                    episodesLoading = false,
                    message = if (episodes.isEmpty()) "No episodes found for this series." else null
                )
            }
        }
    }

    fun closeSeries() = _state.update { it.copy(series = null, episodes = emptyList(), message = null) }

    fun clearMessage() = _state.update { it.copy(message = null) }

    /** Gets the link for a movie and hands it to the player; [onReady] opens the player. */
    fun playMovie(item: VodItem, onReady: () -> Unit) {
        viewModelScope.launch {
            val playable = runCatching { resolveMovie(item) }.getOrNull()
            if (playable == null) {
                _state.update { it.copy(message = "Couldn't get this title from your provider.") }
            } else {
                VodLaunch.pending = playable
                onReady()
            }
        }
    }

    fun playEpisode(series: VodItem, episode: VodEpisode, onReady: () -> Unit) {
        viewModelScope.launch {
            val playable = runCatching { resolveEpisode(series, episode) }.getOrNull()
            if (playable == null) {
                _state.update { it.copy(message = "Couldn't get this episode from your provider.") }
            } else {
                VodLaunch.pending = playable
                onReady()
            }
        }
    }

    private suspend fun headersFor(playlistId: String): Map<String, String> {
        val ua = prefs.playlists.first().firstOrNull { it.id == playlistId }?.userAgent.orEmpty()
        return mapOf("User-Agent" to ua.ifBlank { LiveTvRepository.DEFAULT_UA })
    }

    private suspend fun resolveMovie(item: VodItem): VodPlayable? {
        val title = OnDemandDatabase.displayTitle(item.name)
        item.stalkerRef?.let { ref ->
            val s = stalker.resolve(item.playlistId, ref)
            return VodPlayable(s.url, headersFor(item.playlistId) + s.headers, title)
        }
        val url = repo.movieUrl(item) ?: return null
        return VodPlayable(url, headersFor(item.playlistId), title)
    }

    private suspend fun resolveEpisode(series: VodItem, ep: VodEpisode): VodPlayable? {
        val title = "${OnDemandDatabase.displayTitle(series.name)} · S${ep.season}E${ep.episode}" +
            ep.title.takeIf { it.isNotBlank() }?.let { " · $it" }.orEmpty()
        val playlistId = ep.playlistId.ifBlank { series.playlistId }
        ep.stalkerRef?.let { ref ->
            val s = stalker.resolve(playlistId, ref)
            return VodPlayable(s.url, headersFor(playlistId) + s.headers, title)
        }
        val url = ep.url.takeIf { it.isNotBlank() } ?: return null
        return VodPlayable(url, headersFor(playlistId), title)
    }

    private companion object {
        const val PAGE = 120
    }
}
