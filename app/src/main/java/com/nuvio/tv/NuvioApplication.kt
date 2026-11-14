package com.nuvio.tv

import android.app.ActivityManager
import android.app.Application
import android.content.Context
import android.os.Build
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.bitmapFactoryMaxParallelism
import coil3.disk.DiskCache
import coil3.gif.AnimatedImageDecoder
import coil3.gif.GifDecoder
import coil3.memory.MemoryCache
import coil3.request.allowHardware
import coil3.request.crossfade
import coil3.svg.SvgDecoder
import com.nuvio.tv.core.network.IPv4FirstDns
import com.nuvio.tv.core.network.NoDelaySocketFactory
import com.nuvio.tv.core.network.imageNetworkFetcherFactory
import com.nuvio.tv.ui.screens.settings.MemoryBudget
import dagger.hilt.android.HiltAndroidApp
import okhttp3.OkHttpClient
import okio.Path.Companion.toOkioPath
import java.util.concurrent.TimeUnit

/** Live TV only build: the app. Sets up the image loader, the VPN report lines and the language. */
@HiltAndroidApp
class NuvioApplication : Application(), SingletonImageLoader.Factory {

    override fun onCreate() {
        // Before super.onCreate(): Hilt creates injected services there, and the budget is read once.
        MemoryBudget.init(this)
        super.onCreate()
        // VPN progress lines go into the start-up report.
        com.nuvio.vpn.android.VpnPlaybackProtection.diagnostics =
            java.util.function.Consumer { line -> com.nuvio.tv.livetv.model.LiveTvLoadReport.add(line) }
        // Load locale synchronously so it's available before Activity.attachBaseContext.
        val tag = getSharedPreferences("app_locale", Context.MODE_PRIVATE)
            .getString("locale_tag", null)
        LocaleCache.localeTag = tag ?: ""
    }

    override fun newImageLoader(context: Context): ImageLoader {
        val imageOkHttpClient by lazy {
            val imageDispatcher = okhttp3.Dispatcher().apply {
                maxRequests = 32
                maxRequestsPerHost = 16
            }
            OkHttpClient.Builder()
                .dispatcher(imageDispatcher)
                .dns(IPv4FirstDns())
                .socketFactory(NoDelaySocketFactory)
                .connectTimeout(4, TimeUnit.SECONDS)
                .readTimeout(5, TimeUnit.SECONDS)
                .callTimeout(8, TimeUnit.SECONDS)
                .followRedirects(true)
                .followSslRedirects(true)
                .build()
        }

        return ImageLoader.Builder(this)
            .components {
                if (Build.VERSION.SDK_INT >= 28) {
                    add(AnimatedImageDecoder.Factory())
                } else {
                    add(GifDecoder.Factory())
                }
                add(SvgDecoder.Factory())
                // Images still load behind a DNS VPN (see ImageConnectivity).
                add(imageNetworkFetcherFactory(callFactory = { imageOkHttpClient }))
            }
            .memoryCache {
                val activityManager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
                val memoryInfo = ActivityManager.MemoryInfo()
                activityManager.getMemoryInfo(memoryInfo)
                val totalRamMb = memoryInfo.totalMem / (1024 * 1024)
                val cachePercent = when {
                    totalRamMb <= 2048 -> 0.15
                    totalRamMb <= 3072 -> 0.20
                    else -> 0.25
                }
                val budgetHeapBytes = MemoryBudget.heapMb * 1024L * 1024L
                val builder = MemoryCache.Builder()
                if (budgetHeapBytes < Runtime.getRuntime().maxMemory()) {
                    builder.maxSizeBytes((budgetHeapBytes * cachePercent).toLong())
                } else {
                    builder.maxSizePercent(context, cachePercent)
                }
                builder.build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache").toOkioPath())
                    .maxSizeBytes(200L * 1024 * 1024)
                    .build()
            }
            .crossfade(false)
            .precision(coil3.size.Precision.INEXACT)
            .allowHardware(false)
            .bitmapFactoryMaxParallelism(4)
            .build()
    }
}
