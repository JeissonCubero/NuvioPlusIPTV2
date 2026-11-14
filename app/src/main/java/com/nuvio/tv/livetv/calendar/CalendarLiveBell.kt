package com.nuvio.tv.livetv.calendar

import androidx.compose.runtime.Composable
import com.nuvio.tv.livetv.model.EpgProgram
import com.nuvio.tv.livetv.model.LiveChannel

/**
 * The bell for a show in Live TV (follow a show / put a movie on the Calendar).
 * Live TV only build: there is no Calendar, so no bell is offered.
 */
data class LiveBell(
    val on: Boolean,
    val label: String,
    val value: String,
    val isMovie: Boolean,
    val toggle: () -> Unit
)

@Suppress("UNUSED_PARAMETER")
@Composable
fun rememberLiveBell(program: EpgProgram?, channel: LiveChannel?): LiveBell? = null
