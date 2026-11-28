package com.nuvio.tv.livetv.data

import okhttp3.ConnectionPool
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * The one base client that Live TV, On Demand, Sports, Calendar and both syncs build their own
 * clients from (with [OkHttpClient.newBuilder], so each keeps its own timeouts and settings).
 * They all share one connection pool and one set of threads, instead of each feature keeping
 * its own: fewer idle connections and threads on small boxes, and a connection one feature
 * opened (to GitHub, ESPN, the sync server…) can be reused by another.
 *
 * A connection is only shared between clients with the same settings for that address
 * (DNS, TLS…), so the playlist client's relaxed TLS ([ForgivingTls]) stays with that client.
 * Playback (the VPN client, the player's own connections) is not part of this.
 */
internal object ForkHttp {
    /** Kept a little bigger than OkHttp's default (5) because several features share it. */
    private const val IDLE_CONNECTIONS = 8
    private const val KEEP_ALIVE_MINUTES = 5L

    val base: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectionPool(ConnectionPool(IDLE_CONNECTIONS, KEEP_ALIVE_MINUTES, TimeUnit.MINUTES))
            .addInterceptor(HttpsToHttpFallback)
            .build()
    }

    /** A client for one feature: the shared pool and threads, with that feature's own settings. */
    fun builder(): OkHttpClient.Builder = base.newBuilder()
}

/**
 * Many IPTV servers only speak plain http on their port, and a link typed with "https://"
 * then fails with "Unable to parse TLS packet header". When https fails that way, try the
 * same address with http.
 */
internal object HttpsToHttpFallback : okhttp3.Interceptor {
    override fun intercept(chain: okhttp3.Interceptor.Chain): okhttp3.Response {
        val req = chain.request()
        return try {
            chain.proceed(req)
        } catch (e: javax.net.ssl.SSLException) {
            if (!req.url.isHttps || !looksLikePlainHttp(e)) throw e
            // A custom port (":8080") is kept; the https default 443 becomes http's 80.
            val b = req.url.newBuilder().scheme("http")
            b.port(if (req.url.port == 443) 80 else req.url.port)
            chain.proceed(req.newBuilder().url(b.build()).build())
        }
    }

    private fun looksLikePlainHttp(e: Throwable): Boolean {
        var t: Throwable? = e
        while (t != null) {
            val m = t.message.orEmpty().lowercase()
            if ("tls packet header" in m || "not an ssl/tls record" in m || "wrong version number" in m ||
                "unrecognized ssl message" in m || "unexpected message" in m) return true
            t = t.cause
        }
        return false
    }
}
