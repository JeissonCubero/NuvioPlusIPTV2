package com.nuvio.vpn.android;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import okhttp3.Call;
import okhttp3.OkHttpClient;
import okhttp3.Request;

/** Live TV only build: no VPN, so requests use the client they were given. */
public final class VpnHttpClient implements Call.Factory {
    private final OkHttpClient baseline;

    public VpnHttpClient(OkHttpClient baseline) { this.baseline = baseline; }

    public static VpnHttpClient wrap(OkHttpClient baseline) { return new VpnHttpClient(baseline); }

    @Override public Call newCall(Request request) { return baseline.newCall(request); }

    public static HttpURLConnection openConnection(URL address) throws IOException {
        return (HttpURLConnection) address.openConnection();
    }

    public static HttpURLConnection openConnection(URL address, OkHttpClient baseline) throws IOException {
        return (HttpURLConnection) address.openConnection();
    }
}
