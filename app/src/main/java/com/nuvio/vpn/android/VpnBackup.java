package com.nuvio.vpn.android;

import android.content.Context;

/** Live TV only build: no VPN profiles to back up; the sync cache is stored as plain JSON. */
public final class VpnBackup {
    private VpnBackup() {}

    public static final String KEY = "vpn_profiles_v1";

    public static String prepareExport(Context context, String legacySnapshot) throws Exception {
        return legacySnapshot == null ? "" : legacySnapshot;
    }

    public static String prepareExport(String legacySnapshot) throws Exception {
        return legacySnapshot == null ? "" : legacySnapshot;
    }

    public static void restoreSetup(Context context, String encoded) throws Exception {}

    public static void restoreSetup(String json) throws Exception {}

    public static String sealCache(String json) throws Exception { return json; }

    public static String openCache(String json) throws Exception { return json; }
}
