package com.nuvio.vpn.android;

import android.content.Context;
import com.nuvio.vpn.core.ProtectionMode;
import java.io.IOException;
import java.util.concurrent.CompletableFuture;

/**
 * Live TV only build: the VPN is gone (it had no settings screen left). This keeps the calls Live TV
 * makes and answers "no VPN": every request goes out over the normal connection.
 */
public final class VpnPlaybackProtection {
    private VpnPlaybackProtection() {}

    public static volatile java.util.function.Consumer<String> diagnostics;
    private static volatile int activeUserProfile = 1;

    public static int activeUserProfile() { return activeUserProfile; }
    public static synchronized void setActiveUserProfile(int id) { if (id > 0) activeUserProfile = id; }
    public static synchronized void initializeActiveUserProfile(int id) { if (id > 0) activeUserProfile = id; }
    public static void removeUserProfile(int id) throws IOException {}

    /** Same identity for a portal's control and media requests: always the plain connection. */
    public static String requireUniformHttpRouting() throws IOException { return ProtectionMode.OFF.name() + ":-:0"; }

    public static AutoCloseable observeSettings(Runnable listener) { return () -> {}; }
    public static AutoCloseable observeBackupChanges(Runnable listener) { return () -> {}; }

    public static void beforeRequest(String source) throws IOException {}
    public static void beforeRequest(String source, java.util.function.BooleanSupplier cancelled) throws IOException {}

    public static CompletableFuture<Void> prepare(Context context, String source) {
        return CompletableFuture.completedFuture(null);
    }

    /** No VPN: native players connect directly (null = no proxy). */
    public static CompletableFuture<NativeProxy> proxyForNativePlayer(Context context) {
        return CompletableFuture.completedFuture(null);
    }

    public static final class NativeProxy implements AutoCloseable {
        private NativeProxy() {}
        public String address() { return ""; }
        @Override public void close() {}
    }
}
