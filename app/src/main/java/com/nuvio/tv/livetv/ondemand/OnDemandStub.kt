package com.nuvio.tv.livetv.ondemand

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

// Live TV only build: On Demand (movies and series from Xtream logins) is not part of this app.
// These stand in for it so Live TV's settings and loading pill keep working: nothing is imported.

data class OnDemandStatus(val loading: Boolean = false, val message: String? = null, val silent: Boolean = false)

@Singleton
class OnDemandRepository @Inject constructor() {
    private val _hasContent = MutableStateFlow(false)
    val hasContent: StateFlow<Boolean> = _hasContent.asStateFlow()

    private val _status = MutableStateFlow(OnDemandStatus())
    val status: StateFlow<OnDemandStatus> = _status.asStateFlow()

    fun refreshSoon() = Unit

    fun refreshNow() = Unit

    @Suppress("UNUSED_PARAMETER")
    fun refreshNow(playlistId: String) = Unit
}

object OnDemandStreams {
    const val GROUP_NAME = "On Demand"
    val offeredInStreams: Boolean get() = false
}

/** How On Demand lists are sorted (the bar above the posters). */
enum class VodSort(val label: String) {
    DEFAULT("Provider's Order"),
    NAME_AZ("A–Z"),
    NAME_ZA("Z–A"),
    NEWEST("Newest Release"),
    OLDEST("Oldest Release"),
    RECENTLY_ADDED("Recently Added"),
    RATING("Highest Rated"),
    SHUFFLE("Shuffle");

    /**
     * The SQL order. Titles with no year (or no rating) go at the end, so they don't fill the
     * top. [seed] picks the Shuffle order, which stays the same while you scroll.
     */
    internal fun orderBy(fallback: String, seed: Long = 0): String = when (this) {
        DEFAULT -> fallback
        // Numbers and symbols sort before letters, so A–Z is #–Z and Z–A ends with them.
        NAME_AZ -> "norm ASC"
        NAME_ZA -> "norm DESC"
        NEWEST -> "year IS NULL, year DESC, added DESC"
        OLDEST -> "year IS NULL, year ASC, added ASC"
        RECENTLY_ADDED -> "added DESC"
        RATING -> "(rating IS NULL OR rating = '' OR CAST(rating AS REAL) <= 0), CAST(rating AS REAL) DESC, norm ASC"
        SHUFFLE -> "((rowid * 1103515245 + ${seed.coerceIn(1, 2_000_000_000)}) % 2147483647)"
    }

    companion object {
        /** A saved choice: "NAME_AZ", or "SHUFFLE:<seed>" (each pick of Shuffle is a new order). */
        fun parse(saved: String?): Pair<VodSort, Long>? {
            if (saved.isNullOrBlank()) return null
            val name = saved.substringBefore(':')
            val sort = entries.firstOrNull { it.name == name } ?: return null
            return sort to (saved.substringAfter(':', "").toLongOrNull() ?: 1L)
        }

        fun encode(sort: VodSort): String =
            if (sort == SHUFFLE) "SHUFFLE:" + (1L + kotlin.random.Random.nextLong(1_999_999_999L)) else sort.name
    }
}

