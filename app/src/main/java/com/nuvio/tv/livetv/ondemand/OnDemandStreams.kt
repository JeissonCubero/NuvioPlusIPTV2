package com.nuvio.tv.livetv.ondemand

import android.content.Context
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

// Live TV only build: Nuvio's stream lists are gone, so these switches only record what the
// settings say (the repository still updates them).
object OnDemandStreams {
    const val GROUP_NAME = "On Demand"
    @Volatile var offeredInStreams: Boolean = false
    @Volatile var autoPlayAllowed: Boolean = false
    @Volatile var tabPosition: String = "FIRST"
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface OnDemandEntryPoint {
    fun onDemandRepository(): OnDemandRepository
    fun liveTvPreferences(): com.nuvio.tv.livetv.data.LiveTvPreferences
}

fun onDemandRepository(context: Context): OnDemandRepository =
    EntryPointAccessors.fromApplication(context.applicationContext, OnDemandEntryPoint::class.java).onDemandRepository()
