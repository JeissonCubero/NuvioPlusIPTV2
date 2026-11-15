package com.nuvio.vpn.android;

import androidx.media3.datasource.HttpDataSource;

/** Live TV only build: no VPN, so playback always uses the normal data source. */
public final class VpnDataSources {
    private VpnDataSources() {}

    public static HttpDataSource.Factory route(HttpDataSource.Factory baseline, HttpDataSource.Factory httpOnly) {
        return baseline;
    }
}
